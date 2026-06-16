import cv2
import numpy as np
import os
import sys

# ─────────────────────────────────────────────
#  整合 Kociemba 演算法路徑
# ─────────────────────────────────────────────
# 使用者將演算法放在 D:/AndroidStudioProject/myapp/app/src/main/python/kociemba/kociemba/
# 我們將該路徑加入 sys.path 以便 import
base_dir = os.path.dirname(os.path.abspath(__file__))
kociemba_path = os.path.join(base_dir, "kociemba")
if kociemba_path not in sys.path:
    sys.path.append(kociemba_path)

try:
    import kociemba
except ImportError:
    kociemba = None

# ─────────────────────────────────────────────
#  顏色定義與辨識
# ─────────────────────────────────────────────
COLOR_RANGES = {
    'W': ([0,   0,  150], [180,  65, 255]),   # 白
    'Y': ([20,  75,  75], [38,  255, 255]),   # 黃
    'R': ([0,   75,  75], [10,  255, 255]),   # 紅1
    'R2':([165, 75,  75], [180, 255, 255]),   # 紅2
    'O': ([10,  75,  75], [20,  255, 255]),   # 橙
    'G': ([40,  60,  60], [85,  255, 255]),   # 綠
    'B': ([95,  75,  75], [130, 255, 255]),   # 藍
}

def classify_color(bgr_patch):
    hsv = cv2.cvtColor(np.uint8([[bgr_patch]]), cv2.COLOR_BGR2HSV)[0][0]
    h, s, v = int(hsv[0]), int(hsv[1]), int(hsv[2])
    if v > 150 and s < 65: return 'W'
    for name, (lo, hi) in COLOR_RANGES.items():
        if name == 'W': continue
        if lo[0] <= h <= hi[0] and lo[1] <= s <= hi[1] and lo[2] <= v <= hi[2]:
            return name.rstrip('2')
    return 'U'

def detect_cube_face(image_bytes, expected_center_color=None):
    data = np.frombuffer(bytes(image_bytes), dtype=np.uint8)
    img = cv2.imdecode(data, cv2.IMREAD_COLOR)
    if img is None: return _encode(None), None, 0.0, ""
    
    h_orig, w_orig = img.shape[:2]
    if w_orig > h_orig: img = cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE)
    img = cv2.resize(img, (480, 640))
    h, w = img.shape[:2]
    overlay = img.copy()

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(blur, 30, 100)
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5))
    closed = cv2.morphologyEx(edges, cv2.MORPH_CLOSE, kernel, iterations=2)
    contours, _ = cv2.findContours(closed, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    cube_quad, best_area = None, 0
    for cnt in contours:
        area = cv2.contourArea(cnt)
        if area < 15000: continue
        peri = cv2.arcLength(cnt, True)
        approx = cv2.approxPolyDP(cnt, 0.04 * peri, True)
        if len(approx) == 4:
            pts = approx.reshape(4, 2).astype(np.float32)
            sides = [np.linalg.norm(pts[i] - pts[(i+1)%4]) for i in range(4)]
            if max(sides)/(min(sides)+1e-5) < 2.5 and area > best_area:
                best_area, cube_quad = area, pts

    colors_9, confidence, error_msg = None, 0.0, ""
    if cube_quad is not None:
        dst_size, cube_quad = 300, _order_pts(cube_quad)
        dst = np.float32([[0,0],[dst_size,0],[dst_size,dst_size],[0,dst_size]])
        M = cv2.getPerspectiveTransform(cube_quad, dst)
        warped = cv2.warpPerspective(img, M, (dst_size, dst_size))
        
        colors_9, cell, ok_count, center_color = [], dst_size // 3, 0, "U"
        for row in range(3):
            for col in range(3):
                cx, cy = col * cell + cell // 2, row * cell + cell // 2
                if row == 1 and col == 1:
                    off = cell // 4
                    samples = [_avg_patch(warped, cx-off, cy-off, 5), _avg_patch(warped, cx+off, cy-off, 5),
                               _avg_patch(warped, cx-off, cy+off, 5), _avg_patch(warped, cx+off, cy+off, 5)]
                    color = center_color = classify_color(np.median(samples, axis=0))
                else:
                    color = classify_color(_avg_patch(warped, cx, cy, 14))
                colors_9.append(color)
                if color != 'U': ok_count += 1
        
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
            cv2.circle(overlay, (int(pt[0]), int(pt[1])), 6, _color_bgr(color), -1)
            cv2.circle(overlay, (int(pt[0]), int(pt[1])), 6, (0,0,0), 1)

        # 補回左下角的文字顯示
        if not error_msg and colors_9:
            cv2.putText(overlay, "".join(colors_9), (10, h - 50), 
                        cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
            cv2.putText(overlay, f"Confidence: {confidence*100:.0f}%", (10, h - 20), 
                        cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
    else:
        cv2.putText(overlay, "Align the Cube", (140, 320), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (200, 200, 200), 2)

    result = cv2.addWeighted(img, 0.5, overlay, 0.5, 0)
    _, buf = cv2.imencode(".png", result)
    return buf.tobytes(), colors_9, confidence, error_msg

# ─────────────────────────────────────────────
#  求解核心
# ─────────────────────────────────────────────

def _rotate90(s, times=1):
    res = list(s)
    for _ in range(times % 4):
        tmp = list(res)
        res[0], res[1], res[2] = tmp[6], tmp[3], tmp[0]
        res[3], res[4], res[5] = tmp[7], tmp[4], tmp[1]
        res[6], res[7], res[8] = tmp[8], tmp[5], tmp[2]
    return "".join(res)

def solve_cube(face_map):
    """
    face_map 鍵值來自 Java (U, L, F, R, B, D)
    對應中心顏色：U=白, L=橘, F=綠, R=紅, B=藍, D=黃
    
    目標基準：白色朝上(U)、橘色對自己(Front)
    對應 Kociemba 位元映射：
    U (Up)    = 白色面 = Java 的 U
    F (Front)  = 橘色面 = Java 的 L
    R (Right)  = 綠色面 = Java 的 F
    D (Down)   = 黃色面 = Java 的 D
    L (Left)   = 藍色面 = Java 的 B
    B (Back)   = 紅色面 = Java 的 R
    """
    if kociemba is None:
        return "", "找不到 Kociemba 演算法檔案"

    try:
        raw = {k: str(face_map.get(k)) for k in ['U', 'L', 'F', 'R', 'B', 'D']}
        
        # 建立中心塊映射：將顏色字元 ('W', 'O'...) 映射到方位字元 ('U', 'R', 'F', 'D', 'L', 'B')
        # 基於「白頂橘前」的基準：
        color_to_pos = {
            raw['U'][4]: 'U', # 白色面中心 -> U
            raw['F'][4]: 'R', # 綠色面中心 -> R
            raw['L'][4]: 'F', # 橘色面中心 -> F
            raw['D'][4]: 'D', # 黃色面中心 -> D
            raw['B'][4]: 'L', # 藍色面中心 -> L
            raw['R'][4]: 'B'  # 紅色面中心 -> B
        }

        def to_kociemba_notation(face_str):
            return "".join(color_to_pos.get(c, 'U') for c in face_str)

        # 旋轉校正：
        # U 面 (白色)：原本掃描時橘色朝前，剛好符合 Kociemba 對 U 的定義 (F 在下邊)。不需要轉。
        u_face = to_kociemba_notation(raw['U'])
        
        # 其他面根據掃描姿勢可能需要旋轉，但若只是翻轉 90 度，
        # 通常直接按順序拼接即可。
        r_face = to_kociemba_notation(raw['F']) # 綠色
        f_face = to_kociemba_notation(raw['L']) # 橘色
        d_face = to_kociemba_notation(raw['D']) # 黃色
        l_face = to_kociemba_notation(raw['B']) # 藍色
        b_face = to_kociemba_notation(raw['R']) # 紅色

        # Kociemba 標準 54 字元順序: U, R, F, D, L, B
        combined = u_face + r_face + f_face + d_face + l_face + b_face
        
        if len(combined) != 54:
            return "", f"資料長度不對: {len(combined)}"

        solution = kociemba.solve(combined)
        return solution, ""
    except Exception as e:
        return "", f"求解失敗: {str(e)}"

# ── 工具函式 ──────────────────────────────────
def _order_pts(pts):
    rect = np.zeros((4, 2), dtype=np.float32)
    s, diff = pts.sum(axis=1), np.diff(pts, axis=1)
    rect[0], rect[2] = pts[np.argmin(s)], pts[np.argmax(s)]
    rect[1], rect[3] = pts[np.argmin(diff)], pts[np.argmax(diff)]
    return rect

def _avg_patch(img, cx, cy, r=10):
    patch = img[max(0,cy-r):min(img.shape[0],cy+r), max(0,cx-r):min(img.shape[1],cx+r)]
    return np.median(patch, axis=(0,1)) if patch.size > 0 else np.array([128,128,128])

def _color_bgr(name):
    return {'W':(255,255,255),'Y':(0,255,255),'R':(0,0,200),'O':(0,128,255),'G':(0,180,0),'B':(200,0,0)}.get(name,(128,128,128))

def _color_name_zh(name):
    return {'W':"白色",'Y':"黃色",'R':"紅色",'O':"橘色",'G':"綠色",'B':"藍色"}.get(name,"未知")

def _encode(img):
    blank = np.zeros((640, 480, 3), np.uint8)
    _, buf = cv2.imencode(".png", blank)
    return buf.tobytes()
