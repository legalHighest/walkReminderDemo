package com.ihuatek.walktips.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.security.PublicKey;

public class WalkRemindService extends Service implements SensorEventListener {
    private static final String TAG = "StepCounterService";

    private final IBinder binder = new WalkRemindBinder();
    private NotificationManager notificationManager;
    private SensorManager sensorManager;
    private Sensor stepDetector;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private Handler handler;

    //　步数相关
    private int stepCount = 0;   // 步数
    private Runnable noMovementRunnable; //停止移动线程
    private static final long NO_MOVEMENT_THRESHOLD_MS = 3000; // 3秒

    //　手机方向相关
    private final int limitStepCount = 30;
    private float[] gravity = new float[3]; //　重力
    private float[] geomagnetic = new float[3];
    private float[] accelData = new float[3];
    private static final float PITCH_THRESHOLD = 10.0f; // 俯仰角阈值


    // 位置精度相关
    LocationManager locationManager; //位置管理对象
    LocationListener locationListener; // 位置监听器
    float locationAccuracy = -1; //　根据gps获取位置精度
    float outdoorThreshold = 20.0f; //户外精度阈值

    private boolean isPhoneActive = false; // 用于判断用户是否在玩手机

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "on Create StepCounterService");
        handler = new Handler();
        //　获取通知管理对象
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        //　获取传感器管理对象
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        //　获取步测器管理对象
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        // 获取加速度传感器和陀螺仪
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        // 获取位置管理器对象
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        // 初始化位置监听
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                // 当位置发生变化时调用此方法
                locationAccuracy = location.getAccuracy();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                // 当提供者状态发生变化时调用此方法
            }

            @Override
            public void onProviderEnabled(String provider) {
                // 当提供者被启用时调用此方法
            }

            @Override
            public void onProviderDisabled(String provider) {
                // 当提供者被禁用时调用此方法
            }
        };
        noMovementRunnable = new Runnable() {
            @Override
            public void run() {
                // 用户停止移动，清零步数
                resetStepCount();
            }
        };
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind Service");
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "WalkRemindService START");
        registerSensors();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterSensors();
        removeLocationListener();
    }

    public void registerSensors() {
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

    public void unregisterSensors() {
        sensorManager.unregisterListener(this);
        handler.removeCallbacksAndMessages(null); // 清除所有计时器
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        //　加速计
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values;
            accelData = event.values.clone();
            checkPhoneHold();
        }
        //　螺旋仪
        //if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
        //    geomagnetic = event.values;
        //}
        //if (gravity != null && geomagnetic != null) {
        //    handleOrientation(gravity, geomagnetic);
        //}
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            handleStepDetection();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void checkPhoneHold() {
        double pitch = Math.atan2(accelData[0], Math.sqrt(accelData[1] * accelData[1] + accelData[2] * accelData[2])) * (180 / Math.PI);
        double roll = Math.atan2(accelData[1], Math.sqrt(accelData[0] * accelData[0] + accelData[2] * accelData[2])) * (180 / Math.PI);
        Log.d(TAG, "持有手机: " + Math.abs(pitch) + " : " + Math.abs(roll));
        // 判断手机是否被持有
        if (Math.abs(pitch) < limitStepCount && Math.abs(roll) < limitStepCount) {
            isPhoneActive = true; // 可能在玩手机
        } else {
            isPhoneActive = false; // 可能在玩手机
        }
    }

    private void handleOrientation(float[] gravity, float[] geomagnetic) {
        // 计算方向
        float[] R = new float[9];
        float[] I = new float[9];
        if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
            float[] orientation = new float[3];
            SensorManager.getOrientation(R, orientation);
            float pitch = (float) Math.toDegrees(orientation[1]); // 俯仰角
            Log.d(TAG, "Math.abs(pitch): " + Math.abs(pitch));
            // 判断设备是否在玩手机（如俯仰角接近水平）
            if (Math.abs(pitch) > PITCH_THRESHOLD && Math.abs(pitch) < 60) {
                isPhoneActive = true; // 可能在玩手机
            } else {
                isPhoneActive = false; // 可能不在玩手机
                resetStepCount();
            }
        }
    }

    /**
     * 计算步数到达限定步数弹出通知
     */
    private void handleStepDetection() {
        if (isDeviceUnlocked() && isPhoneActive) {
            //　计算步数
            stepCount++;
        }
        Log.d(TAG, "isWifiConnected: " + isWifiConnected() + " isDeviceUnlocked: " + isDeviceUnlocked() + " isPhoneActive: " + isPhoneActive + " allStepCount: " + stepCount);
        // 当步数达到 limitStepCount 时，弹出提示并清零
        if (stepCount >= limitStepCount) {
            showNotification("请不要走路玩手机");
            resetStepCount(); // 清零步数
        }
        // 重置定时器
        handler.removeCallbacks(noMovementRunnable);
        handler.postDelayed(noMovementRunnable, NO_MOVEMENT_THRESHOLD_MS); // 启动停止移动计时器
    }

    private void resetStepCount() {
        stepCount = 0; // 清零步数
    }


    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String CHANNEL_ID = "detox_notification_channel";
            CharSequence name = "Detox Channel";
            String description = "Channel for detox notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH; // 设置为高重要性
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * 显示通知
     *
     * @param message
     */
    private void showNotification(String message) {
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, "detox_notification_channel")
                .setSmallIcon(com.ihuatek.walktips.R.drawable.detox_logo) // Set your notification icon here
                .setContentTitle("警告")
                .setContentText(message)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();
        notificationManager.notify(0, notification);
    }

    /**
     * 判断是否链接wifi
     *
     * @return true or false (连接/不连接)
     */
    private boolean isWifiConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected();
        }
    }

    /**
     * 判断手机是否解锁屏幕
     *
     * @return
     */
    private boolean isDeviceUnlocked() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean isLocked = (keyguardManager != null && !keyguardManager.isKeyguardLocked());
        if (!isLocked) {
            resetStepCount();
        }
        return isLocked;
    }

    /**
     * 获取数量
     *
     * @return
     */
    public int getStepCount() {
        return stepCount;
    }


    /**
     * 　注册位置改变监听器获取位置信息
     */
    @SuppressLint("MissingPermission")
    public void startLocationUpdates() {
        if (isGpsEnabled()) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener, getMainLooper());
        }
    }

    /**
     * 移除位置监听
     */
    public void removeLocationListener() {
        locationManager.removeUpdates(locationListener);
    }

    public  boolean isGpsEnabled(){
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    public class WalkRemindBinder extends Binder {
        public WalkRemindService getService() {
            // 返回当前的 MyBoundService 实例，客户端可以调用公共方法
            return WalkRemindService.this;
        }
    }
}