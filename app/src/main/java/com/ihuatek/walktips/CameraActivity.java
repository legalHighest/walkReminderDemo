package com.ihuatek.walktips;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST_CODE = 100;
    private TextureView textureView;
    private Button captureButton;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_camera);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

    }

    private void detectFaces(Bitmap bitmap) {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        FaceDetection.getClient(options)
                .process(image)
                .addOnSuccessListener(faces -> {
                    for (Face face : faces) {
                        float leftEyeOpenProbability = face.getLeftEyeOpenProbability();
                        float rightEyeOpenProbability = face.getRightEyeOpenProbability();

                        // 判断眼睛是否对着屏幕
                        if (leftEyeOpenProbability > 0.5 && rightEyeOpenProbability > 0.5) {
                            Toast.makeText(this, "Eyes are looking at the screen!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Eyes are not looking at the screen.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FaceDetection", "Face detection failed", e);
                });
    }
}