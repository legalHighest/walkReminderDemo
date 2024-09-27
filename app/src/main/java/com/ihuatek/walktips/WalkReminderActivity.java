package com.ihuatek.walktips;


import static com.ihuatek.walktips.utils.PermissionUtil.permissions;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ihuatek.walktips.databinding.ActivityWalkReminderBinding;
import com.ihuatek.walktips.service.WalkRemindService;
import com.ihuatek.walktips.utils.PermissionUtil;
import com.ihuatek.walktips.utils.SharedPreferencesHelper;


public class WalkReminderActivity extends AppCompatActivity {
    private Handler handler = new Handler();
    private Runnable updateRunnable;
    private String TAG = "WalkReminderActivity";
    WalkRemindService walkRemindService;
    boolean isBound = false;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            WalkRemindService.WalkRemindBinder binder = (WalkRemindService.WalkRemindBinder) service;
            walkRemindService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    ActivityWalkReminderBinding binding;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWalkReminderBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        EdgeToEdge.enable(this);
        setContentView(view);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.switchCheck.setChecked(SharedPreferencesHelper.getBoolean(this));

        // Check whether you have permission, if not, actively apply for permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionUtil.hasAllPermissions(WalkReminderActivity.this, permissions)) {
                PermissionUtil.checkAndRequestPermissions(WalkReminderActivity.this, permissions);
            } else binding.switchCheck.setEnabled(true);
        }
        // Bind walking reminder service
        Intent intent = new Intent(this, WalkRemindService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        binding.switchCheck.setOnClickListener(v -> {
            if (!walkRemindService.isGpsEnabled()) {
                Toast.makeText(this, "Please turn on GPS", Toast.LENGTH_SHORT).show();
                binding.switchCheck.setChecked(false);
            }
        });
        binding.switchCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Save switch status value
                SharedPreferencesHelper.saveBoolean(WalkReminderActivity.this, isChecked);
                //　Register and unbind walking reminder listener according to the switch
                if (isBound) {
                    if (isChecked) {
                        walkRemindService.registerSensors();
                        walkRemindService.startLocationUpdates();
                        startUpdatingValue();
                    } else {
                        walkRemindService.unregisterSensors();
                        walkRemindService.removeLocationListener();
                        stopUpdatingValue();
                    }
                }
                binding.switchLabel.setText(isChecked ? R.string.turn_on : R.string.turn_off);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    /**
     * Update step count data in real time
     */
    private void startUpdatingValue() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isBound && walkRemindService != null) {
                    // Get real-time values from the Service and update the UI
                    int currentValue = walkRemindService.getStepCount();
                    binding.stepCount.setText(String.valueOf(currentValue));
                }
                // Delay 1 second and execute again
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(updateRunnable);
    }

    // Stop updating tasks
    private void stopUpdatingValue() {
        handler.removeCallbacks(updateRunnable);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionUtil.PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            // Check if all permissions are granted
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            // If all permissions are granted
            if (allGranted) {
                Log.d(TAG, "All permissions granted");
                // Execute functions that require permissions
                binding.switchCheck.setEnabled(true);
                // Check if the user has turned on GPS, if not prompt the user to turn it on
                if (!walkRemindService.isGpsEnabled()) {
                    Toast.makeText(this, "Please turn on GPS", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "Permission denied");
                // Handling Permission Denied Situations
                binding.switchCheck.setEnabled(false);
            }
        }
    }
}