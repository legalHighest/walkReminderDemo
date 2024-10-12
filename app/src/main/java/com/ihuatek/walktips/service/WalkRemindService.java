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

import com.ihuatek.walktips.R;

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

    //　Associated with the number of steps
    private int stepCount = 0;   // Step count
    private Runnable noMovementRunnable; //Stop moving threads
    private static final long NO_MOVEMENT_THRESHOLD_MS = 3000; // 3s

    //　Related to phone orientation
    private final int limitStepCount = 30;
    private float[] gravity = new float[3]; //　gravity
    private float[] geomagnetic = new float[3];
    private float[] accelData = new float[3];
    private static final float PITCH_THRESHOLD = 30; // Pitch angle threshold


    // Related to positional accuracy
    LocationManager locationManager; //　Location management objects
    LocationListener locationListener; // Position listeners
    float locationAccuracy = -1; //　Get position accuracy based on GPS
    float outdoorThreshold = 20.0f; //　Outdoor accuracy thresholds

    private boolean isPhoneActive = false; // It is used to determine whether the user is playing with the phone

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

    /**
     * Judge the angle of the phone
     */
    private void checkPhoneHold() {
        double pitch = Math.atan2(accelData[0], Math.sqrt(accelData[1] * accelData[1] + accelData[2] * accelData[2])) * (180 / Math.PI);
        double roll = Math.atan2(accelData[1], Math.sqrt(accelData[0] * accelData[0] + accelData[2] * accelData[2])) * (180 / Math.PI);
        Log.d(TAG, "Possess a mobile phone: " + Math.abs(pitch) + " : " + Math.abs(roll));
        // Determine if the phone is in possession
        if (Math.abs(pitch) < PITCH_THRESHOLD && Math.abs(roll) < PITCH_THRESHOLD) {
            isPhoneActive = true; // Probably playing with a phone
        } else {
            isPhoneActive = false; // Probably not playing on the phone
        }
    }

    //private void handleOrientation(float[] gravity, float[] geomagnetic) {
    //    // 计算方向
    //    float[] R = new float[9];
    //    float[] I = new float[9];
    //    if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
    //        float[] orientation = new float[3];
    //        SensorManager.getOrientation(R, orientation);
    //        float pitch = (float) Math.toDegrees(orientation[1]); // 俯仰角
    //        Log.d(TAG, "Math.abs(pitch): " + Math.abs(pitch));
    //        // 判断设备是否在玩手机（如俯仰角接近水平）
    //        if (Math.abs(pitch) > PITCH_THRESHOLD && Math.abs(pitch) < 60) {
    //            isPhoneActive = true; // 可能在玩手机
    //        } else {
    //            isPhoneActive = false; // 可能不在玩手机
    //            resetStepCount();
    //        }
    //    }
    //}

    /**
     * A pop-up notification pops up when the number of steps is counted
     */
    private void handleStepDetection() {
        if (isDeviceUnlocked() && isPhoneActive) {
            //　计算步数
            stepCount++;
        }
        Log.d(TAG,  " isDeviceUnlocked: " + isDeviceUnlocked() + " isPhoneActive: " + isPhoneActive + " allStepCount: " + stepCount);
        // 当步数达到 limitStepCount 时，弹出提示并清零
        if (stepCount >= limitStepCount) {
            showNotification(getResources().getString(R.string.tips_value));
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
     * Create a notification channel
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
     * Notifications are displayed
     *
     * @param message
     */
    private void showNotification(String message) {
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, "detox_notification_channel")
                .setSmallIcon(com.ihuatek.walktips.R.drawable.detox_logo) // Set your notification icon here
                .setContentTitle(getResources().getString(R.string.tips_title))
                .setContentText(getResources().getString(R.string.tips_value))
                .setContentText(message)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();
        notificationManager.notify(0, notification);
    }



    /**
     * Tell if your phone is unlocked or not
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
     * Get the step count
     *
     * @return
     */
    public int getStepCount() {
        return stepCount;
    }


    /**
     * 　Register a listener to change the location and obtain the location information
     */
    @SuppressLint("MissingPermission")
    public void startLocationUpdates() {
        if (isGpsEnabled()) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener, getMainLooper());
        }
    }

    /**
     * Remove location listeners
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