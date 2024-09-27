package com.ihuatek.walktips.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.nfc.Tag;
import android.os.Build;
import android.util.Log;


import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Permission Tools
 */
@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public class PermissionUtil {
    //　Permission request code
    public static final int PERMISSION_REQUEST_CODE = 100;
    //　Permissions Array
    public static String[] permissions = new String[]{
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.USE_FINGERPRINT,
            Manifest.permission.ACCESS_FINE_LOCATION,
    };

    private static String TAG = "PermissionUtil";

    /**
     * request permissions
     *
     * @param context
     * @param permissions
     * @return
     */
    public static boolean checkAndRequestPermissions(Context context, String[] permissions) {
        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (!allGranted) {
            ActivityCompat.requestPermissions((Activity) context, permissions, PERMISSION_REQUEST_CODE);
        }
        return allGranted;
    }

    /**
     * Check whether all permissions are obtained
     *
     * @param context
     * @param permissions
     * @return
     */
    public static boolean hasAllPermissions(Context context, String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "permission: " + permission);
                return false;
            }
        }
        return true;
    }
}
