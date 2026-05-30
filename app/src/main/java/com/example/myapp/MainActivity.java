package com.example.myapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private ImageView imgOriginal;
    private ImageView imgResult;
    private TextView txtStatus;
    private byte[] inputImageBytes;
    private Python py;

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    try {
                        inputImageBytes = readAllBytesFromUri(uri);
                        showOriginalImage(inputImageBytes);
                        imgResult.setImageDrawable(null);
                        txtStatus.setText("Image picked: " + inputImageBytes.length + " bytes");
                    } catch (Exception e) {
                        txtStatus.setText("Pick image failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgOriginal = findViewById(R.id.imgOriginal);
        imgResult = findViewById(R.id.imgResult);
        txtStatus = findViewById(R.id.txtStatus);

        Button btnLoadResource = findViewById(R.id.btnLoadResource);
        Button btnPythonReadImage = findViewById(R.id.btnPythonReadImage);
        Button btnPickImage = findViewById(R.id.btnPickImage);
        Button btnProcessImage = findViewById(R.id.btnProcessImage);

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        py = Python.getInstance();

        btnLoadResource.setOnClickListener(v -> loadImageFromResource());
        btnPythonReadImage.setOnClickListener(v -> loadImageFromPython());
        btnPickImage.setOnClickListener(v -> pickImage());
        btnProcessImage.setOnClickListener(v -> processImageWithPython());
    }

    private void loadImageFromResource() {
        try {
            // 注意：請確認 res/raw 下有 test.jpg 或 test.png
            InputStream inputStream = getResources().openRawResource(R.raw.test);
            inputImageBytes = readAllBytes(inputStream);
            showOriginalImage(inputImageBytes);
            imgResult.setImageDrawable(null);
            txtStatus.setText("Loaded from res/raw: " + inputImageBytes.length + " bytes");
        } catch (Exception e) {
            txtStatus.setText("Load resource failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadImageFromPython() {
        try {
            PyObject module = py.getModule("local_image");
            PyObject result = module.callAttr("read_local_image");
            inputImageBytes = result.toJava(byte[].class);
            showOriginalImage(inputImageBytes);
            imgResult.setImageDrawable(null);
            txtStatus.setText("Python loaded local image: " + inputImageBytes.length + " bytes");
        } catch (Exception e) {
            txtStatus.setText("Python read failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void processImageWithPython() {
        if (inputImageBytes == null) {
            txtStatus.setText("Please load an image first.");
            return;
        }
        txtStatus.setText("Processing with OpenCV...");
        new Thread(() -> {
            try {
                PyObject module = py.getModule("opencv_process");
                PyObject result = module.callAttr("canny_from_image_bytes", inputImageBytes);
                byte[] outPng = result.toJava(byte[].class);
                Bitmap outBitmap = BitmapFactory.decodeByteArray(outPng, 0, outPng.length);
                runOnUiThread(() -> {
                    imgResult.setImageBitmap(outBitmap);
                    txtStatus.setText("OpenCV Process Done");
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> txtStatus.setText("Python Error: " + e.getMessage()));
            }
        }).start();
    }

    private void showOriginalImage(byte[] bytes) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        imgOriginal.setImageBitmap(bitmap);
    }

    private byte[] readAllBytesFromUri(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        return readAllBytes(inputStream);
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}
