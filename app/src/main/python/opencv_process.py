import cv2
import numpy as np
import os
import sys

# ─────────────────────────────────────────────
#  整合 Kociemba 演算法路徑
# ─────────────────────────────────────────────
base_dir = os.path.dirname(os.path.abspath(__file__))
kociemba_path = os.path.join(base_dir, "kociemba")
if kociemba_path not in sys.path:
    sys.path.append(kociemba_path)

try:
    import kociemba
except ImportError:
    kociemba = None

# ─────────────────────────────────────────────
#  魔術方塊顏色定義（HSV 範圍）
# ─────────────────────────────────────────────
COLOR_RANGES = {
    'W': ([0,   0,  160], [180,  50, 255]),   # 白
    'O': ([10,  80,  80], [23,  255, 255]),   # 橙 (調廣一點減少誤判為黃)
    'Y': ([24,  80,  80], [38,  255, 255]),   # 黃
    'R': ([0,   80,  80], [9,   255, 255]),   # 紅（低段）
    'R2':([165, 80,  80], [180, 255, 255]),   # 紅（高段）
    'G': ([40,  60,  60], [85,  255, 255]),   # 綠
    'B': ([90,  60,  60], [130, 255, 255]),   # 藍
}

def classify_color(bgr_patch):
    hsv = cv2.cvtColor(np.uint8([[bgr_patch]]), cv2.COLOR_BGR2HSV)[0][0]
    h, s, v = int(hsv[0]), int(hsv[1]), int(hsv[2])
    
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
    if w_orig > h_orig:
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
        if area < 15000:
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

                if row == 1 and col == 1:
                    offset = cell // 4
                    samples = [
                        _avg_patch(warped, cx - offset, cy - offset, 5),
                        _avg_patch(warped, cx + offset, cy - offset, 5),
                        _avg_patch(warped, cx - offset, cy + offset, 5),
                        _avg_patch(warped, cx + offset, cy + offset, 5)
                    ]
                    patch_bgr = np.median(samples, axis=0)
                    center_color = classify_color(patch_bgr)
                    color = center_color
                else:
                    patch_bgr = _avg_patch(warped, cx, cy, 14)
                    color = classify_color(patch_bgr)

                colors_9.append(color)
                if color != 'U':
                    ok_count += 1

        if expected_center_color and center_color != "U" and center_color != expected_center_color:
            error_msg = f"中心顏色錯誤！請掃描{_color_name_zh(expected_center_color)}面"
            confidence = 0.0
        else:
            confidence = ok_count / 9.0

        cv2.polylines(overlay, [cube_quad.astype(np.int32)], True, (0, 255, 0), 2)

        Minv = cv2.getPerspectiveTransform(dst, cube_quad)
        for i, color in enumerate(colors_9):
            row, col = i // 3, i % 3
            cx, cy = col * cell + cell // 2, row * cell + cell // 2
            pt = cv2.perspectiveTransform(np.float32([[[cx, cy]]]), Minv)[0][0]
            cv2.circle(overlay, (int(pt[0]), int(pt[1])), 8, _color_bgr(color), -1)
            cv2.circle(overlay, (int(pt[0]), int(pt[1])), 8, (0, 0, 0), 1)

        if colors_9:
            cv2.putText(overlay, "".join(colors_9), (10, h-50), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
            cv2.putText(overlay, f"Confidence: {confidence*100:.0f}%", (10, h-20), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
    else:
        cv2.putText(overlay, "Align the Cube", (140, 320), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (200, 200, 200), 2)

    result = cv2.addWeighted(img, 0.4, overlay, 0.6, 0)
    _, buf = cv2.imencode(".png", result)
    return buf.tobytes(), colors_9, confidence, error_msg


def _rotate_face_string(s, times=1):
    res = list(s)
    for _ in range(times % 4):
        tmp = list(res)
        res[0], res[1], res[2] = tmp[6], tmp[3], tmp[0]
        res[3], res[4], res[5] = tmp[7], tmp[4], tmp[1]
        res[6], res[7], res[8] = tmp[8], tmp[5], tmp[2]
    return "".join(res)

def solve_cube(face_map):
    if kociemba is None:
        return "", "找不到 Kociemba 演算法檔案"

    try:
        raw = {k: str(face_map.get(k)) for k in ['U', 'L', 'F', 'R', 'B', 'D']}
        
        # 固定顏色對應 (白頂橘前基準)
        color_to_pos = {
            'W': 'U', # 白 -> Up
            'O': 'F', # 橘 -> Front
            'G': 'R', # 綠 -> Right
            'Y': 'D', # 黃 -> Down
            'B': 'L', # 藍 -> Left
            'R': 'B'  # 紅 -> Back
        }

        def to_kociemba(face_str):
            return "".join(color_to_pos.get(c, 'U') for c in face_str)

        # 組合順序: U, R, F, D, L, B
        u_face = _rotate_face_string(to_kociemba(raw['U']), 0) 
        r_face = _rotate_face_string(to_kociemba(raw['F']), 0) 
        f_face = _rotate_face_string(to_kociemba(raw['L']), 0) 
        d_face = _rotate_face_string(to_kociemba(raw['D']), 3)
        l_face = _rotate_face_string(to_kociemba(raw['B']), 0) 
        b_face = _rotate_face_string(to_kociemba(raw['R']), 0) 

        combined = u_face + r_face + f_face + d_face + l_face + b_face
        
        # 1. 優先驗證顏色數量
        counts = {c: combined.count(c) for c in "URFDLB"}
        if any(v != 9 for v in counts.values()):
            return "", "顏色數量不正確"

        # 檢查是否已解好
        solved_string = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB"
        if combined == solved_string:
            return "", "" # 0步

        # 2. 嘗試求解，若失敗則回報拍攝順序錯誤
        try:
            solution = kociemba.solve(combined)
            return solution, ""
        except:
            return "", "拍攝順序錯誤"
    except Exception:
        return "", "拍攝順序錯誤"


# ── 工具函式 ──────────────────────────────────

def _order_pts(pts):
    rect = np.zeros((4, 2), dtype=np.float32)
    s = pts.sum(axis=1)
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
    patch = img[max(0, cy-r):min(h, cy+r), max(0, cx-r):min(w, cx+r)]
    if patch.size == 0:
        return np.array([128, 128, 128])
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

def _encode(img):
    blank = np.zeros((640, 480, 3), np.uint8)
    _, buf = cv2.imencode(".png", blank)
    return buf.tobytes()

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
