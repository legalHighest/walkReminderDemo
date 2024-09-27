package com.ihuatek.walktips;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.LongDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.location.LocationListenerCompat;

import java.util.List;

public class PhoneUsageActivity extends AppCompatActivity {

    LocationManager locationManager;
    private final int requestLocationCode = 1001;
    private final int requestCoreLocationCode = 1002;
    TextView gpsPower;
    Button checkButton;
    String TAG = "PhoneUsageActivity";

    @SuppressLint({"ServiceCast", "MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_usage);
        gpsPower = findViewById(R.id.gps_power);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, requestLocationCode);
        }
        checkLocationPermission();
        // 检查 GPS 是否开启
        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!isGpsEnabled) {
            Log.d(TAG, "请开启 GPS");
        } else {
            startLocationUpdates();
        }
        checkButton = findViewById(R.id.checkButton);
        checkButton.setOnClickListener(v -> {
            startLocationUpdates();
        });
    }


    LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // 当位置发生变化时调用此方法

            gpsPower.setText("信号强度：　"+location.getAccuracy());
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


    private void startLocationUpdates() {
        boolean ifCheck = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "if permission: " + ifCheck);
        if (ifCheck) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener, getMainLooper());
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(locationListener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == requestLocationCode) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "LOCATION　权限被赋予");
                startLocationUpdates();
            } else {
                Log.d(TAG, "LOCATION　权限被拒绝");
            }
        }
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 权限未被授予，申请权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, requestLocationCode);
        } else {
            // 权限已被授予，打印信息
            Log.d(TAG, "定位权限已获得");
            startLocationUpdates();
        }
    }


}