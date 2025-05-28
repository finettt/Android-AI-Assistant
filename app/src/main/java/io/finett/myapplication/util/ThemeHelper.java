package io.finett.myapplication.util;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import androidx.appcompat.app.AppCompatDelegate;
import android.util.Log;

/**
 * Вспомогательный класс для работы с темами приложения
 */
public class ThemeHelper {
    private static final String TAG = "ThemeHelper";
    
    /**
     * Проверяет, использует ли система темную тему
     * @param context контекст приложения
     * @return true если система использует темную тему, false в противном случае
     */
    public static boolean isSystemInDarkMode(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & 
                Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }
    
    /**
     * Применяет тему в соответствии с настройками пользователя
     * @param accessibilityManager менеджер доступности с настройками темы
     */
    public static void applyTheme(AccessibilityManager accessibilityManager) {
        String theme = accessibilityManager.getAppTheme();
        boolean isHighContrast = accessibilityManager.isHighContrastEnabled();
        
        Log.d(TAG, "Applying theme: " + theme + ", high contrast: " + isHighContrast);
        
        if (isHighContrast) {
            // Для высококонтрастной темы всегда используем темную тему
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            Log.d(TAG, "Applied high contrast theme (dark mode)");
        } else {
            switch (theme) {
                case "light":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    Log.d(TAG, "Applied light theme");
                    break;
                case "dark":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    Log.d(TAG, "Applied dark theme");
                    break;
                case "system":
                default:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                        Log.d(TAG, "Applied system theme (Android Q+)");
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
                        Log.d(TAG, "Applied auto battery theme (pre-Android Q)");
                    }
                    break;
            }
        }
    }
} 