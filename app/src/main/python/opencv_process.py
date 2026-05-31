import cv2
import numpy as np

def canny_from_image_bytes(image_bytes):
    # 1. 解碼圖片
    data = np.frombuffer(bytes(image_bytes), dtype=np.uint8)
    img = cv2.imdecode(data, cv2.IMREAD_COLOR)
    if img is None: 
        return None

    # --- 關鍵修正：順時針旋轉 90 度，讓畫面變直的 ---
    img = cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE)

    # 為了效能，縮小處理尺寸 (直向畫面)
    img = cv2.resize(img, (240, 320))

    # 2. HSV 色彩空間轉換 (偵測膚色)
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    
    # 定義膚色範圍 (建議在光線充足處測試)
    # 這裡的數值可以根據實際環境調整
    lower_skin = np.array([0, 20, 70], dtype=np.uint8)
    upper_skin = np.array([20, 255, 255], dtype=np.uint8)
    mask = cv2.inRange(hsv, lower_skin, upper_skin)

    # 3. 形態學處理：去除小噪點並平滑化
    kernel = np.ones((3, 3), np.uint8)
    mask = cv2.erode(mask, kernel, iterations=1)
    mask = cv2.dilate(mask, kernel, iterations=2)
    mask = cv2.GaussianBlur(mask, (5, 5), 0)

    # 4. 尋找輪廓
    contours, _ = cv2.findContours(mask, cv2.RETR_TREE, cv2.CHAIN_APPROX_SIMPLE)
    
    result_text = "Wait..."
    
    if len(contours) > 0:
        # 找出面積最大的輪廓（假設就是手）
        max_cnt = max(contours, key=cv2.contourArea)
        
        if cv2.contourArea(max_cnt) > 2000:
            # 5. 計算凸包與凸缺陷
            hull = cv2.convexHull(max_cnt, returnPoints=False)
            defects = cv2.convexityDefects(max_cnt, hull)
            
            finger_count = 0
            
            if defects is not None:
                for i in range(defects.shape[0]):
                    s, e, f, d = defects[i, 0]
                    start = tuple(max_cnt[s][0])
                    end = tuple(max_cnt[e][0])
                    far = tuple(max_cnt[f][0])
                    
                    # 餘弦定理計算夾角 (手指縫夾角通常很小)
                    a = np.sqrt((end[0] - start[0])**2 + (end[1] - start[1])**2)
                    b = np.sqrt((far[0] - start[0])**2 + (far[1] - start[1])**2)
                    c = np.sqrt((end[0] - far[0])**2 + (end[1] - far[1])**2)
                    angle = np.arccos((b**2 + c**2 - a**2) / (2*b*c)) * 57
                    
                    # 深度(d)必須夠大，且角度小於 90 度才算指縫
                    if angle <= 90 and d > 3000:
                        finger_count += 1
                        cv2.circle(img, far, 5, [0, 0, 255], -1) # 在指縫標記紅點
                
                # 6. 判定邏輯 (以指縫數量決定)
                if finger_count == 0:
                    result_text = "ROCK"
                elif finger_count == 1:
                    result_text = "SCISSORS"
                elif finger_count >= 3:
                    result_text = "PAPER"
                else:
                    result_text = "Analyzing..."

            # 畫出綠色輪廓
            cv2.drawContours(img, [max_cnt], -1, (0, 255, 0), 2)
            
    # 將文字寫在畫面左上方
    cv2.putText(img, result_text, (20, 40), cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 0, 0), 2)

    # 7. 編碼回傳
    _, buf = cv2.imencode(".png", img)
    return buf.tobytes()
