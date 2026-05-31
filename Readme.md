# Android CameraX + Python (OpenCV) 即時影像串流處理器

這個專案展示了如何結合 **Android CameraX ImageAnalysis** 與 **Chaquopy**，實現即時的影像串流處理。系統會自動擷取相機每一幀，透過 Python 的 OpenCV 進行 Canny 邊緣檢測，並連續顯示處理結果。

## 進階功能特點
- **即時串流分析 (ImageAnalysis)**：不再需要手動按鈕，App 啟動後自動進行連續影像處理。
- **動態狀態鎖 (isProcessing)**：實作自適應跳幀機制。當 Python 正在運算時自動跳過新產生的幀，防止記憶體堆積與 App 閃退。
- **高效格式轉換**：將相機原始的 YUV_420_888 格式在硬體層級轉換為 JPEG 位元組流，優化傳遞給 Python 的效率。
- **效能優化**：預設使用 480x640 解析度進行串流運算，在保持辨識度的同時最大化每秒幀數 (FPS)。

## 技術架構
1. **CameraX Analyzer**：使用 `setAnalyzer` 綁定背景執行緒。
2. **格式轉換**：`yuvToJpegBytes()` 將影像轉為 Python OpenCV 可讀的格式。
3. **Python 橋接**：透過 Chaquopy 呼叫 `opencv_process.py` 進行影像演算法運算。
4. **UI 同步**：透過 `runOnUiThread` 實現處理結果的連續更新顯示。

## Python 依賴項目
text opencv-python-headless numpy
## 使用說明
- 開啟 App 並允許相機權限。
- App 會自動開始即時處理，預覽畫面下方會即時顯示經過 Canny 濾波後的邊緣影像。