package com.example.helloandroid;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CubeSolver";

    private static final String[] FACE_KEYS  = {"U", "L", "F", "R", "B", "D"};
    private static final String[] FACE_COLORS= {"W", "O", "G", "R", "B", "Y"};
    private static final String[] FACE_NAMES = {
            "白色面 (U·上)\n[橘色面對自己]",
            "橘色面 (L·左)\n[黃色面對自己]",
            "綠色面 (F·前)\n[黃色面對自己]",
            "紅色面 (R·右)\n[黃色面對自己]",
            "藍色面 (B·後)\n[黃色面對自己]",
            "黃色面 (D·下)\n[綠色面對自己]"
    };

    private enum ScanState { IDLE, SCANNING, DONE }
    private ScanState state = ScanState.IDLE;
    private int currentFaceIndex = 0;

    private final Map<String, String> confirmedFaces = new HashMap<>();
    private String currentColors9    = null;
    private float  currentConfidence = 0f;

    // UI
    private PreviewView previewView;
    private ImageView   resultView;
    private TextView    tvFaceName, tvSolution, tvMoveCount;
    private ProgressBar confidenceBar;
    private Button      btnStart, btnConfirm, btnRescan, btnRestart;
    private CardView    hintCard;
    private ScrollView  solutionPanel;
    private View[]      dots;

    // Camera / Python
    private Python   py;
    private PyObject pyModule;
    private ProcessCameraProvider cameraProvider;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean isProcessing = false;

    private final ActivityResultLauncher<String> cameraPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else Toast.makeText(this, "需要攝影機權限", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView   = findViewById(R.id.previewView);
        resultView    = findViewById(R.id.resultView);
        tvFaceName    = findViewById(R.id.tvFaceName);
        tvSolution    = findViewById(R.id.tvSolution);
        tvMoveCount   = findViewById(R.id.tvMoveCount);
        confidenceBar = findViewById(R.id.confidenceBar);
        btnStart      = findViewById(R.id.btnStart);
        btnConfirm    = findViewById(R.id.btnConfirm);
        btnRescan     = findViewById(R.id.btnRescan);
        btnRestart    = findViewById(R.id.btnRestart);
        hintCard      = findViewById(R.id.hintCard);
        solutionPanel = findViewById(R.id.solutionPanel);
        dots = new View[]{
                findViewById(R.id.dot0), findViewById(R.id.dot1),
                findViewById(R.id.dot2), findViewById(R.id.dot3),
                findViewById(R.id.dot4), findViewById(R.id.dot5)
        };

        setIdleUI();

        btnStart.setOnClickListener(v -> beginScan());
        btnConfirm.setOnClickListener(v -> confirmCurrentFace());
        btnRescan.setOnClickListener(v -> rescanCurrentFace());
        btnRestart.setOnClickListener(v -> resetAll());

        if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
        py       = Python.getInstance();
        pyModule = py.getModule("opencv_process");

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // ── UI 狀態 ───────────────────────────────────────────────

    private void setIdleUI() {
        btnStart.setVisibility(View.VISIBLE);
        btnConfirm.setVisibility(View.GONE);
        btnRescan.setVisibility(View.GONE);
        solutionPanel.setVisibility(View.GONE);
        hintCard.setVisibility(View.VISIBLE);
        confidenceBar.setVisibility(View.VISIBLE);
        tvFaceName.setText("按下「開始掃描」\n開始辨識魔術方塊");
        confidenceBar.setProgress(0);
        resetDots();
    }

    private void setScanningUI(int faceIndex) {
        btnStart.setVisibility(View.GONE);
        btnConfirm.setVisibility(View.VISIBLE);
        btnRescan.setVisibility(View.VISIBLE);
        btnRescan.setText(faceIndex == 0 ? "重新掃描" : "回上一步");
        solutionPanel.setVisibility(View.GONE);
        hintCard.setVisibility(View.VISIBLE);
        confidenceBar.setVisibility(View.VISIBLE);
        tvFaceName.setText("第 " + (faceIndex + 1) + "/6 面\n" + FACE_NAMES[faceIndex]);
    }

    private void setResultUI(String solution, int moves) {
        solutionPanel.setVisibility(View.VISIBLE);
        hintCard.setVisibility(View.GONE);
        btnStart.setVisibility(View.GONE);
        btnConfirm.setVisibility(View.GONE);
        btnRescan.setVisibility(View.GONE);
        confidenceBar.setVisibility(View.GONE);

        String[] steps = solution.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.length; i++) {
            sb.append(steps[i]);
            if ((i + 1) % 5 == 0 && i != steps.length - 1) sb.append("\n");
            else if (i != steps.length - 1) sb.append("  ");
        }
        tvSolution.setText(sb.toString());
        tvMoveCount.setText("共 " + moves + " 步");
    }

    private void resetDots() {
        for (View dot : dots) dot.setBackgroundResource(R.drawable.dot_inactive);
    }

    private void markDotDone(int index) {
        if (index >= 0 && index < dots.length)
            dots[index].setBackgroundResource(R.drawable.dot_active);
    }

    // ── 掃描流程 ──────────────────────────────────────────────

    private void beginScan() {
        confirmedFaces.clear();
        currentFaceIndex  = 0;
        currentColors9    = null;
        currentConfidence = 0f;
        state = ScanState.SCANNING;
        resetDots();
        setScanningUI(0);
        Toast.makeText(this, "將 " + FACE_NAMES[0] + " 對準框內", Toast.LENGTH_SHORT).show();
    }

    private void confirmCurrentFace() {
        if (currentColors9 == null) {
            Toast.makeText(this, "尚未偵測到方塊，請重新對準", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentConfidence < 0.6f) {
            Toast.makeText(this,
                    String.format("信心度不足 (%.0f%%)，請調整角度", currentConfidence * 100),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 確保沒有未識別的顏色 'U'
        if (currentColors9 == null || currentColors9.contains("U")) {
            Toast.makeText(this, "仍有色塊未辨識清楚，請調整角度", Toast.LENGTH_SHORT).show();
            return;
        }

        confirmedFaces.put(FACE_KEYS[currentFaceIndex], currentColors9);
        markDotDone(currentFaceIndex);
        currentFaceIndex++;
        currentColors9    = null;
        currentConfidence = 0f;
        runOnUiThread(() -> confidenceBar.setProgress(0));

        if (currentFaceIndex >= 6) {
            state = ScanState.DONE;
            stopCameraAnalysis();
            solveCube();
        } else {
            setScanningUI(currentFaceIndex);
            Toast.makeText(this, "請翻到 " + FACE_NAMES[currentFaceIndex], Toast.LENGTH_LONG).show();
        }
    }

    private void rescanCurrentFace() {
        if (state == ScanState.DONE) {
            // 如果求解失敗後點重掃，回到最後一面 (Index 5)
            state = ScanState.SCANNING;
            currentFaceIndex = 5;
            confirmedFaces.remove(FACE_KEYS[currentFaceIndex]);
            dots[currentFaceIndex].setBackgroundResource(R.drawable.dot_inactive);
            setScanningUI(currentFaceIndex);
            startCamera(); // 重新開啟相機分析
        } else if (currentFaceIndex > 0) {
            // 掃描中點重掃，回到上一面
            currentFaceIndex--;
            confirmedFaces.remove(FACE_KEYS[currentFaceIndex]);
            dots[currentFaceIndex].setBackgroundResource(R.drawable.dot_inactive);
            setScanningUI(currentFaceIndex);
        }

        currentColors9    = null;
        currentConfidence = 0f;
        runOnUiThread(() -> confidenceBar.setProgress(0));
        Toast.makeText(this, "請重掃：" + FACE_NAMES[currentFaceIndex], Toast.LENGTH_SHORT).show();
    }

    private void resetAll() {
        confirmedFaces.clear();
        currentFaceIndex  = 0;
        currentColors9    = null;
        currentConfidence = 0f;
        state = ScanState.IDLE;
        setIdleUI();
        startCamera();
    }

    // ── Kociemba 求解 ─────────────────────────────────────────

    private void solveCube() {
        tvSolution.setText("計算中…");
        tvMoveCount.setText("");
        solutionPanel.setVisibility(View.VISIBLE);
        hintCard.setVisibility(View.GONE);
        btnStart.setVisibility(View.GONE);
        btnConfirm.setVisibility(View.GONE);
        btnRescan.setVisibility(View.GONE);
        confidenceBar.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                PyObject result = pyModule.callAttr("solve_cube", confirmedFaces);
                List<PyObject> items = result.asList();
                String solution = items.get(0).toString();
                String errorMsg = items.get(1).toString();

                runOnUiThread(() -> {
                    if (!errorMsg.isEmpty()) {
                        tvSolution.setText("解法錯誤：\n" + errorMsg);
                        tvMoveCount.setText("請確認六面顏色是否正確");
                        // 允許使用者點擊「重掃」回到最後一面檢查
                        btnRescan.setVisibility(View.VISIBLE);
                        btnRescan.setText("回上一步檢查");
                    } else {
                        int moves = solution.trim().isEmpty() ? 0
                                : solution.trim().split("\\s+").length;
                        setResultUI(solution, moves);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Solve error", e);
                runOnUiThread(() -> tvSolution.setText("求解失敗：\n" + e.getMessage()));
            }
        }).start();
    }

    // ── CameraX ──────────────────────────────────────────────

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCamera();
            } catch (Exception e) {
                Log.e(TAG, "Camera init failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        if (cameraProvider == null) return;
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(480, 640))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        analysis.setAnalyzer(cameraExecutor, image -> {
            if (isProcessing || state != ScanState.SCANNING) {
                image.close();
                return;
            }
            isProcessing = true;
            try {
                byte[] bytes = yuvToJpegBytes(image);
                image.close();
                if (bytes == null) { isProcessing = false; return; }

                String expectedColor = (state == ScanState.SCANNING) ? FACE_COLORS[currentFaceIndex] : null;
                PyObject pyResult = pyModule.callAttr("detect_cube_face", (Object) bytes, expectedColor);
                List<PyObject> items = pyResult.asList();

                byte[]   pngBytes  = items.get(0).toJava(byte[].class);
                PyObject pyColors  = items.get(1);
                float    confidence = items.get(2).toJava(Float.class);
                String   errorMsg   = items.get(3).toString();

                String colors9 = null;
                if (pyColors != null && !pyColors.toString().equals("None")) {
                    StringBuilder sb = new StringBuilder();
                    for (PyObject c : pyColors.asList()) sb.append(c.toString());
                    colors9 = sb.toString();
                }

                final Bitmap bmp        = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.length);
                final String finalColors = colors9;
                final float  finalConf   = confidence;
                final String finalError  = errorMsg;

                runOnUiThread(() -> {
                    if (bmp != null) resultView.setImageBitmap(bmp);
                    
                    if (!finalError.isEmpty()) {
                        // 如果有錯誤訊息（例如中心顏色不對），在上方卡片顯示
                        tvFaceName.setText(finalError);
                        tvFaceName.setTextColor(0xFFFF5722); // 橘紅色警告
                        currentColors9 = null;
                        currentConfidence = 0f;
                        confidenceBar.setProgress(0);
                    } else {
                        // 正常掃描狀態
                        tvFaceName.setTextColor(0xFFFFFFFF); // 白色字體
                        if (state == ScanState.SCANNING) {
                            tvFaceName.setText("第 " + (currentFaceIndex + 1) + "/6 面\n" + FACE_NAMES[currentFaceIndex]);
                        }

                        if (finalColors != null && finalConf > 0) {
                            currentColors9    = finalColors;
                            currentConfidence = finalConf;
                            confidenceBar.setProgress((int)(finalConf * 100));
                        } else {
                            currentColors9 = null;
                            currentConfidence = 0f;
                            confidenceBar.setProgress(0);
                        }
                    }

                    isProcessing = false;
                });
            } catch (Exception e) {
                Log.e(TAG, "Frame processing error", e);
                isProcessing = false;
                image.close();
            }
        });

        cameraProvider.bindToLifecycle(this,
                CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
    }

    private void stopCameraAnalysis() {
        if (cameraProvider != null) cameraProvider.unbindAll();
    }

    // ── YUV → JPEG ───────────────────────────────────────────

    private byte[] yuvToJpegBytes(ImageProxy image) {
        int w = image.getWidth(), h = image.getHeight();
        ImageProxy.PlaneProxy[] planes = image.getPlanes();

        ByteBuffer yBuf = planes[0].getBuffer();
        ByteBuffer uBuf = planes[1].getBuffer();
        ByteBuffer vBuf = planes[2].getBuffer();

        int ySize = yBuf.remaining();
        int vSize = vBuf.remaining();
        byte[] nv21 = new byte[w * h * 3 / 2];
        yBuf.get(nv21, 0, ySize);

        if (planes[1].getPixelStride() == 2) {
            vBuf.get(nv21, ySize, vSize);
        } else {
            byte[] uArr = new byte[uBuf.remaining()];
            byte[] vArr = new byte[vBuf.remaining()];
            uBuf.get(uArr);
            vBuf.get(vArr);
            int uvLen = Math.min(uArr.length, vArr.length);
            for (int i = 0; i < uvLen && ySize + i * 2 + 1 < nv21.length; i++) {
                nv21[ySize + i * 2]     = vArr[i];
                nv21[ySize + i * 2 + 1] = uArr[i];
            }
        }

        YuvImage yuvImg = new YuvImage(nv21, ImageFormat.NV21, w, h, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImg.compressToJpeg(new Rect(0, 0, w, h), 75, out);
        return out.toByteArray();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}