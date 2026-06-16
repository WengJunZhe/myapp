import cv2
import numpy as np
import collections

# Polyfill for rubik-solver compatibility with Python 3.10+
try:
    from collections import abc
    collections.Iterable = abc.Iterable
    collections.Mapping = abc.Mapping
    collections.MutableMapping = abc.MutableMapping
    collections.Callable = abc.Callable
except ImportError:
    pass

# ─────────────────────────────────────────────
#  魔術方塊顏色定義（HSV 範圍）
# ─────────────────────────────────────────────
COLOR_RANGES = {
    'W': ([0,   0,  160], [180,  50, 255]),   # 白
    'Y': ([20,  80,  80], [35,  255, 255]),   # 黃
    'R': ([0,   80,  80], [10,  255, 255]),   # 紅（低段）
    'R2':([165, 80,  80], [180, 255, 255]),   # 紅（高段）
    'O': ([10,  80,  80], [20,  255, 255]),   # 橙
    'G': ([40,  60,  60], [85,  255, 255]),   # 綠
    'B': ([90,  60,  60], [130, 255, 255]),   # 藍
}

def classify_color(bgr_patch):
    hsv = cv2.cvtColor(np.uint8([[bgr_patch]]), cv2.COLOR_BGR2HSV)[0][0]
    h, s, v = int(hsv[0]), int(hsv[1]), int(hsv[2])
    
    # 增加對白色的判斷寬容度，並優先檢查白色
    # 白色通常有極高的亮度 (V) 和較低的飽和度 (S)
    if v > 150 and s < 60:
        return 'W'
        
    for name, (lo, hi) in COLOR_RANGES.items():
        if name == 'W':
            continue
        if lo[0] <= h <= hi[0] and lo[1] <= s <= hi[1] and lo[2] <= v <= hi[2]:
            return name.rstrip('2')
    return 'U'


def detect_cube_face(image_bytes, expected_center_color=None):
    data = np.frombuffer(bytes(image_bytes), dtype=np.uint8)
    img = cv2.imdecode(data, cv2.IMREAD_COLOR)
    if img is None:
        return _encode(None), None, 0.0, ""

    h_orig, w_orig = img.shape[:2]
    if w_orig > h_orig:          # 只有真的拿到橫圖才旋轉
        img = cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE)
    img = cv2.resize(img, (480, 640))
    h, w = img.shape[:2]
    overlay = img.copy()

    gray   = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    blur   = cv2.GaussianBlur(gray, (5, 5), 0)
    edges  = cv2.Canny(blur, 30, 100)
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5))
    closed = cv2.morphologyEx(edges, cv2.MORPH_CLOSE, kernel, iterations=2)

    contours, _ = cv2.findContours(closed, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    cube_quad = None
    best_area = 0

    for cnt in contours:
        area = cv2.contourArea(cnt)
        if area < 20000:
            continue
        peri   = cv2.arcLength(cnt, True)
        approx = cv2.approxPolyDP(cnt, 0.04 * peri, True)
        if len(approx) == 4:
            pts   = approx.reshape(4, 2).astype(np.float32)
            sides = _quad_sides(pts)
            ratio = max(sides) / (min(sides) + 1e-5)
            if ratio < 2.5 and area > best_area:
                best_area = area
                cube_quad = pts

    colors_9   = None
    confidence = 0.0
    error_msg  = ""

    if cube_quad is not None:
        dst_size  = 300
        cube_quad = _order_pts(cube_quad)
        dst = np.float32([[0,0],[dst_size,0],[dst_size,dst_size],[0,dst_size]])
        M   = cv2.getPerspectiveTransform(cube_quad, dst)
        warped = cv2.warpPerspective(img, M, (dst_size, dst_size))

        colors_9 = []
        cell     = dst_size // 3
        ok_count = 0
        center_color = "U"

        for row in range(3):
            for col in range(3):
                cx = col * cell + cell // 2
                cy = row * cell + cell // 2
                
                # 特殊處理中心塊 (row=1, col=1)，避開可能的大面積 Logo
                if row == 1 and col == 1:
                    # 改用「環狀採樣」：在中心點周圍取 4 個角落點，避開正中央的 Logo
                    offset = cell // 4
                    samples = [
                        _avg_patch(warped, cx - offset, cy - offset, 5),
                        _avg_patch(warped, cx + offset, cy - offset, 5),
                        _avg_patch(warped, cx - offset, cy + offset, 5),
                        _avg_patch(warped, cx + offset, cy + offset, 5)
                    ]
                    # 取這四個點的中位數
                    patch_bgr = np.median(samples, axis=0)
                    center_color = classify_color(patch_bgr)
                    color = center_color
                else:
                    patch_bgr = _avg_patch(warped, cx, cy, 14)
                    color = classify_color(patch_bgr)

                colors_9.append(color)
                if color != 'U':
                    ok_count += 1

        # 防呆機制：檢查中心塊顏色
        if expected_center_color and center_color != "U" and center_color != expected_center_color:
            # error_msg 保持中文，因為它會傳回 Java 端顯示在 TextView
            error_msg = f"中心顏色錯誤！請掃描{_color_name_zh(expected_center_color)}面"
            # 畫面上顯示的文字改為英文，避免問號
            cv2.putText(overlay, "WRONG FACE!",
                    (10, h-80), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2)
            confidence = 0.0
        else:
            confidence = ok_count / 9.0

        cv2.polylines(overlay, [cube_quad.astype(np.int32)], True, (0, 255, 0), 3)

        Minv = cv2.getPerspectiveTransform(dst, cube_quad)
        for row in range(3):
            for col in range(3):
                corners_dst = np.float32([
                    [col*cell,       row*cell],
                    [(col+1)*cell,   row*cell],
                    [(col+1)*cell,   (row+1)*cell],
                    [col*cell,       (row+1)*cell],
                ]).reshape(-1, 1, 2)
                corners_src = cv2.perspectiveTransform(corners_dst, Minv)
                cv2.polylines(overlay, [corners_src.astype(np.int32)], True, (255, 255, 0), 1)

                cx = col * cell + cell // 2
                cy = row * cell + cell // 2
                pt_dst = np.float32([[[cx, cy]]])
                pt_src = cv2.perspectiveTransform(pt_dst, Minv)[0][0]
                color_name = colors_9[row * 3 + col]
                dot_color  = _color_bgr(color_name)
                cv2.circle(overlay, (int(pt_src[0]), int(pt_src[1])), 8, dot_color, -1)
                cv2.circle(overlay, (int(pt_src[0]), int(pt_src[1])), 8, (0, 0, 0), 1)

        if error_msg:
             # 這裡之前重複繪製了錯誤訊息，已經整合到上方邏輯
             pass

        cv2.putText(overlay, f"Confidence: {confidence*100:.0f}%",
                    (10, h-20), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
        
        # 只有在偵測到魔術方塊且沒有錯誤時才顯示顏色字串
        if colors_9 and not error_msg:
             cv2.putText(overlay, "".join(colors_9),
                        (10, h-50), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
    else:
        margin    = 60
        guide_pts = np.array([
            [margin, margin], [w-margin, margin],
            [w-margin, h-margin], [margin, h-margin],
        ], dtype=np.int32)
        _draw_dashed_rect(overlay, guide_pts, (128, 128, 128), 2)
        cv2.putText(overlay, "Align the Cube",
                    (w//2-100, h//2), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (200, 200, 200), 2)

    result = cv2.addWeighted(img, 0.4, overlay, 0.6, 0)
    _, buf = cv2.imencode(".png", result)
    return buf.tobytes(), colors_9, confidence, error_msg


def _rotate90(s):
    # 012   630
    # 345 -> 741
    # 678   852
    return s[6]+s[3]+s[0] + s[7]+s[4]+s[1] + s[8]+s[5]+s[2]

def solve_cube(face_colors_dict):
    try:
        from rubik.cube import Cube
        from rubik.solve import Solver
        
        # 將 Java HashMap 轉換為 Python dict
        raw_faces = {}
        for key in ['U', 'L', 'F', 'R', 'B', 'D']:
            val = face_colors_dict.get(key)
            raw_faces[key] = str(val) if val else "UUUUUUUUU"

        # 建立顏色映射 (根據中心塊顏色決定該面代表哪個方位)
        color_map = {raw_faces[k][4]: k for k in raw_faces}
        
        def to_face_str(s):
            return "".join(color_map.get(c, 'U') for c in s)

        # 根據您的拍攝姿勢進行旋轉校正：
        # 1. 白色面 (U): 橘色面對自己 -> 需順時針轉 1 次校正
        u = _rotate90(to_face_str(raw_faces['U']))
        
        # 2. 黃色面 (D): 綠色面對自己 -> 需旋轉 180 度 (轉 2 次) 校正
        d = _rotate90(_rotate90(to_face_str(raw_faces['D'])))
        
        # 3. 側面 (L, F, R, B): 均以黃色為底，符合標準視角，不需旋轉
        l = to_face_str(raw_faces['L'])
        f = to_face_str(raw_faces['F'])
        r = to_face_str(raw_faces['R'])
        b = to_face_str(raw_faces['B'])

        # 組合為 rubik-cube 要求的 54 字元格式 (U, L-F-R-B rows, D)
        cube_str = (
            u +
            l[0:3] + f[0:3] + r[0:3] + b[0:3] +
            l[3:6] + f[3:6] + r[3:6] + b[3:6] +
            l[6:9] + f[6:9] + r[6:9] + b[6:9] +
            d
        )
        
        cube_str = (
            u +
            l[0:3] + f[0:3] + r[0:3] + b[0:3] +
            l[3:6] + f[3:6] + r[3:6] + b[3:6] +
            l[6:9] + f[6:9] + r[6:9] + b[6:9] +
            d
        )
        
        c = Cube(cube_str)
        solver = Solver(c)
        solver.solve()
        
        # Convert internal move notation (e.g., 'Li') to standard (e.g., "L'")
        def convert_move(m):
            if m.endswith('i'):
                return m[0] + "'"
            return m
            
        solution_str = " ".join(convert_move(m) for m in solver.moves)
        return solution_str, ""
    except ImportError:
        return "", "魔術方塊解法套件 (rubik-cube) 未安裝"
    except Exception as e:
        return "", str(e)


# ── 工具函式 ──────────────────────────────────

def _encode(img):
    blank = np.zeros((640, 480, 3), np.uint8)
    _, buf = cv2.imencode(".png", blank)
    return buf.tobytes()

def _order_pts(pts):
    pts  = pts.astype(np.float32)
    rect = np.zeros((4, 2), dtype=np.float32)
    s    = pts.sum(axis=1)
    diff = np.diff(pts, axis=1)
    rect[0] = pts[np.argmin(s)]
    rect[2] = pts[np.argmax(s)]
    rect[1] = pts[np.argmin(diff)]
    rect[3] = pts[np.argmax(diff)]
    return rect

def _quad_sides(pts):
    n = len(pts)
    return [np.linalg.norm(pts[i] - pts[(i+1) % n]) for i in range(n)]

def _avg_patch(img, cx, cy, r=10):
    h, w = img.shape[:2]
    x1, y1 = max(0, cx-r), max(0, cy-r)
    x2, y2 = min(w, cx+r), min(h, cy+r)
    patch = img[y1:y2, x1:x2]
    if patch.size == 0:
        return np.array([128, 128, 128])
    # 使用中位數 (Median) 而非平均值 (Mean)
    # 這樣可以有效忽略掉中心塊上的小面積 Logo (如 GAN 的藍色 Logo)
    return np.median(patch, axis=(0, 1))

def _color_bgr(name):
    return {
        'W': (255, 255, 255), 'Y': (0, 255, 255),
        'R': (0, 0, 200),     'O': (0, 128, 255),
        'G': (0, 180, 0),     'B': (200, 0, 0),
        'U': (128, 128, 128),
    }.get(name, (128, 128, 128))

def _color_name_zh(name):
    return {
        'W': "白色", 'Y': "黃色",
        'R': "紅色", 'O': "橘色",
        'G': "綠色", 'B': "藍色",
    }.get(name, "未知")

def _draw_dashed_rect(img, pts, color, thickness):
    n = len(pts)
    for i in range(n):
        _draw_dashed_line(img, tuple(pts[i]), tuple(pts[(i+1) % n]), color, thickness)

def _draw_dashed_line(img, p1, p2, color, thickness, gap=10):
    dist = int(np.linalg.norm(np.array(p2) - np.array(p1)))
    if dist == 0:
        return
    for i in range(0, dist, gap * 2):
        t1 = i / dist
        t2 = min(i + gap, dist) / dist
        x1 = int(p1[0] + t1*(p2[0]-p1[0])); y1 = int(p1[1] + t1*(p2[1]-p1[1]))
        x2 = int(p1[0] + t2*(p2[0]-p1[0])); y2 = int(p1[1] + t2*(p2[1]-p1[1]))
        cv2.line(img, (x1, y1), (x2, y2), color, thickness)