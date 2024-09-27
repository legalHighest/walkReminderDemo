package com.ihuatek.walktips;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.logging.Logger;

public class StepActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor stepDetector;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private boolean isPhoneActive = false;
    private int stepCount = 0; // 步数
    private double stepLength = 0.6; // 每一步的长度（米）
    private TextView stepCountTextView;
    private TextView distanceTextView; // 显示距离的 TextView
    private NotificationManager notificationManager;
    private static final long NO_MOVEMENT_THRESHOLD_MS = 3000; // 2秒
    private Handler handler = new Handler();
    private Runnable noMovementRunnable;
    private static final float PITCH_THRESHOLD = 10.0f; // 俯仰角阈值
    private float[] gravity;
    private float[] geomagnetic;
    private String TAG = "StepActivity";
    private final int limitStepCount = 50;
    private  float accuracy=-1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_step);
        stepCountTextView = findViewById(R.id.stepCountTextView); // 获取 TextView 显示步数
        distanceTextView = findViewById(R.id.distanceTextView); // 获取 TextView 显示距离
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        noMovementRunnable = new Runnable() {
            @Override
            public void run() {
                // 用户停止移动，清零步数
                resetStepCount();
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (stepDetector != null) {
            sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        handler.removeCallbacks(noMovementRunnable); // 清除计时器
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values;
        }

        // 计算方向
        if (gravity != null && geomagnetic != null) {
            float[] R = new float[9];
            float[] I = new float[9];
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                float pitch = (float) Math.toDegrees(orientation[1]); // 俯仰角
                Log.d(TAG, "Math.abs(pitch): " + Math.abs(pitch));
                // 判断设备是否在玩手机（如俯仰角接近水平）
                if (Math.abs(pitch) > PITCH_THRESHOLD && Math.abs(pitch) < 90) {
                    isPhoneActive = true; // 可能在玩手机
                } else {
                    isPhoneActive = false; // 可能不在玩手机
                    resetStepCount();
                }
            }
        }
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            // 每当检测到一步时，步数加一
            Log.d("StepActivity", "isWifiConnected: " + isWifiConnected() + " isDeviceUnlocked: " + isDeviceUnlocked() + "isPhoneActive: " + isPhoneActive);
            if (!isWifiConnected() && isDeviceUnlocked() && isPhoneActive) {
                stepCount++;
                stepCountTextView.setText("步数: " + stepCount);

                // 计算并显示距离
                double distance = stepCount * stepLength; // 计算总距离
                distanceTextView.setText(String.format("距离: %.2f 米", distance)); // 显示到 UI

            }
            // 当步数达到 100 时，弹出提示并清零
            if (stepCount >= limitStepCount) {
                createNotificationChannel();
                showToast("请不要走路玩手机");
                resetStepCount(); // 清零步数
            }

            // 重置定时器
            handler.removeCallbacks(noMovementRunnable);
            handler.postDelayed(noMovementRunnable, NO_MOVEMENT_THRESHOLD_MS); // 启动停止移动计时器
        }
    }

    /**
     * 清空步数
     */
    private void resetStepCount() {
        stepCount = 0; // 清零步数
        stepLength = 0;
        stepCountTextView.setText("步数: " + stepCount); // 更新显示
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * 发通知
     *
     * @param message
     */
    private void showToast(String message) {
        final String CHANNEL_ID = "detox_notification_channel";
        final int notificationId = 0;
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.detox_logo) // Set your notification icon here
                .setContentTitle("kids")
                .setContentText(message)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify(notificationId, builder.build());
    }

    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final String CHANNEL_ID = "detox_notification_channel";
            CharSequence name = "Detox Channel";
            String description = "Channel for detox notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH; // 设置为高重要性
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * 是否连接wifi
     *
     * @return
     */
    private boolean isWifiConnected() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            // 兼容旧版本
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected();
        }
    }

    /**
     * 是否解锁手机
     *
     * @return
     */
    private boolean isDeviceUnlocked() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return keyguardManager != null && !keyguardManager.isKeyguardLocked();
    }
}