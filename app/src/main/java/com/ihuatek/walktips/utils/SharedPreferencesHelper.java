package com.ihuatek.walktips.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesHelper {
    private static final String PREFERENCES_FILE = "my_preferences";
    private static final String BOOLEAN_KEY = "boolean_key";

    /**
     * Local storage of boolean values
     * @param context
     * @param value
     */
    public static void saveBoolean(Context context, boolean value) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(BOOLEAN_KEY, value);
        editor.apply();
    }

    /**
     * Get the boolean value stored locally
     * @param context
     * @return
     */
    public static boolean getBoolean(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(BOOLEAN_KEY, false); // 默认值为false，可以根据需要更改
    }
}
