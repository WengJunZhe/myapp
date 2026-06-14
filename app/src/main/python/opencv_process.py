import cv2
import numpy as np

def canny_from_image_bytes(image_bytes):
    # 1. 解碼與旋轉
    data = np.frombuffer(bytes(image_bytes), dtype=np.uint8)
    img = cv2.imdecode(data, cv2.IMREAD_COLOR)
    if img is None: return None
    
    img = cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE)
    img = cv2.resize(img, (240, 320))
    height, width = img.shape[:2]

    # 2. 膚色偵測 (HSV)
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    # 稍微縮減範圍以減少背景干擾
    lower_skin = np.array([0, 30, 60], dtype=np.uint8)
    upper_skin = np.array([20, 255, 255], dtype=np.uint8)
    mask = cv2.inRange(hsv, lower_skin, upper_skin)

    # 3. 形態學優化：先腐蝕去小點，再強力膨脹連結手掌
    kernel = np.ones((3, 3), np.uint8)
    mask = cv2.erode(mask, kernel, iterations=1)
    mask = cv2.dilate(mask, kernel, iterations=4) 
    mask = cv2.GaussianBlur(mask, (5, 5), 0)

    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    
    result_text = "Ready"
    
    if len(contours) > 0:
        max_cnt = max(contours, key=cv2.contourArea)
        
        if cv2.contourArea(max_cnt) > 3000:
            # 計算重心 (Moments)
            M = cv2.moments(max_cnt)
            if M["m00"] != 0:
                cx = int(M["m10"] / M["m00"])
                cy = int(M["m01"] / M["m00"])
                cv2.circle(img, (cx, cy), 5, [255, 0, 255], -1) # 紫色點為中心

            # 計算凸包與缺陷
            hull = cv2.convexHull(max_cnt, returnPoints=False)
            defects = cv2.convexityDefects(max_cnt, hull)
            
            valid_finger_gaps = 0
            
            if defects is not None:
                for i in range(defects.shape[0]):
                    s, e, f, d = defects[i, 0]
                    start = tuple(max_cnt[s][0])
                    end = tuple(max_cnt[e][0])
                    far = tuple(max_cnt[f][0])
                    
                    # --- 優化過濾邏輯 ---
                    
                    # 1. 角度計算
                    a = np.sqrt((end[0] - start[0])**2 + (end[1] - start[1])**2)
                    b = np.sqrt((far[0] - start[0])**2 + (far[1] - start[1])**2)
                    c = np.sqrt((end[0] - far[0])**2 + (end[1] - far[1])**2)
                    angle = np.arccos((b**2 + c**2 - a**2) / (2*b*c)) * 57
                    
                    # 2. 指縫必須在重心上方 (排除手腕)
                    # 在直立畫面中，cy 是中心，如果 far[1] > cy + 20，通常是手腕處的凹陷
                    is_not_wrist = far[1] < (cy + 20)
                    
                    # 3. 深度過濾 (d 是谷底到橡皮筋的距離)
                    # 數值 3500 是一個經驗值，代表夠深的溝
                    if angle <= 80 and d > 3500 and is_not_wrist:
                        valid_finger_gaps += 1
                        cv2.circle(img, far, 7, [0, 0, 255], -1)
                        cv2.line(img, start, end, [0, 255, 0], 2)

                # 4. 判定邏輯優化
                if valid_finger_gaps == 0:
                    result_text = "ROCK"
                elif valid_finger_gaps == 1:
                    result_text = "SCISSORS"
                elif valid_finger_gaps >= 2:
                    result_text = "PAPER"
                else:
                    result_text = "Wait..."

            cv2.drawContours(img, [max_cnt], -1, (255, 255, 0), 1)
            
    cv2.putText(img, f"Result: {result_text}", (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 0, 0), 2)

    _, buf = cv2.imencode(".png", img)
    return buf.tobytes()
