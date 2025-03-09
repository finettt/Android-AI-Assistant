package io.finett.myapplication.util;

import android.content.Context;
import android.content.SharedPreferences;

public class UserManager {
    private static final String PREFS_NAME = "UserPrefs";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_IS_REGISTERED = "is_registered";
    
    private final SharedPreferences preferences;
    
    public UserManager(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public void registerUser(String name) {
        preferences.edit()
                .putString(KEY_USER_NAME, name)
                .putBoolean(KEY_IS_REGISTERED, true)
                .apply();
    }
    
    public String getUserName() {
        return preferences.getString(KEY_USER_NAME, null);
    }
    
    public boolean isRegistered() {
        return preferences.getBoolean(KEY_IS_REGISTERED, false);
    }
    
    public void clearUserData() {
        preferences.edit().clear().apply();
    }
} 