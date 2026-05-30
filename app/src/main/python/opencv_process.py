import cv2
import numpy as np

def canny_from_image_bytes(image_bytes):
    # 將位元組轉為 numpy 陣列
    data = np.frombuffer(bytes(image_bytes), dtype=np.uint8)
    # 解碼圖片
    img = cv2.imdecode(data, cv2.IMREAD_COLOR)
    if img is None:
        raise RuntimeError("cv2.imdecode failed")
    
    # 轉灰階
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    # Canny 邊緣檢測
    edge = cv2.Canny(gray, 80, 160)
    
    # 將結果編碼回 png 位元組
    ok, buf = cv2.imencode(".png", edge)
    if not ok:
        raise RuntimeError("cv2.imencode failed")
        
    return buf.tobytes()
