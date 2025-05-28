package io.finett.myapplication;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import io.finett.myapplication.util.AccessibilityManager;
import io.finett.myapplication.util.ThemeHelper;

/**
 * Основной класс приложения для инициализации глобальных компонентов
 */
public class MyApplication extends Application {
    private static final String TAG = "MyApplication";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Инициализируем тему приложения при запуске
        initializeTheme();
    }
    
    /**
     * Инициализирует тему приложения в соответствии с настройками
     */
    private void initializeTheme() {
        // Получаем настройки доступности
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        AccessibilityManager accessibilityManager = new AccessibilityManager(this, null);
        
        // Получаем настройки темы
        String theme = accessibilityManager.getAppTheme();
        boolean isHighContrast = accessibilityManager.isHighContrastEnabled();
        boolean isDarkTheme = theme.equals("dark") || 
                             (theme.equals("system") && ThemeHelper.isSystemInDarkMode(this));
        
        Log.d(TAG, "Initializing app theme: " + theme + 
              ", isDarkTheme: " + isDarkTheme + 
              ", isHighContrast: " + isHighContrast);
        
        // Применяем тему в соответствии с настройками
        if (isHighContrast) {
            // Для высококонтрастной темы всегда используем темную тему
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            Log.d(TAG, "Applied high contrast theme (dark mode)");
        } else {
            switch (theme) {
                case "light":
                    // Принудительно применяем светлую тему
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    Log.d(TAG, "Applied light theme");
                    
                    // Сохраняем настройку для всех компонентов
                    preferences.edit().putString("app_theme", "light").apply();
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