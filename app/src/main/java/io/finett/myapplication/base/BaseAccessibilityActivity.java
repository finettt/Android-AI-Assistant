package io.finett.myapplication.base;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import io.finett.myapplication.R;
import io.finett.myapplication.util.AccessibilityManager;

public abstract class BaseAccessibilityActivity extends AppCompatActivity {
    protected AccessibilityManager accessibilityManager;
    private BroadcastReceiver settingsReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        accessibilityManager = new AccessibilityManager(this, null);
        setupSettingsReceiver();
        setupEdgeToEdge();
    }

    /**
     * Настраивает edge-to-edge отображение (контент отображается под системными панелями)
     */
    private void setupEdgeToEdge() {
        // Отключаем edge-to-edge режим, возвращаемся к обычному отображению
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        
        // Возвращаем обычные цвета для системных панелей
        getWindow().setStatusBarColor(getResources().getColor(R.color.primary_dark, getTheme()));
        getWindow().setNavigationBarColor(getResources().getColor(R.color.primary_dark, getTheme()));
    }

    private void setupSettingsReceiver() {
        settingsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("io.finett.myapplication.SETTINGS_UPDATED".equals(intent.getAction())) {
                    applyAccessibilitySettings();
                }
            }
        };
        registerReceiver(
            settingsReceiver, 
            new IntentFilter("io.finett.myapplication.SETTINGS_UPDATED"),
            Context.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyAccessibilitySettings();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (settingsReceiver != null) {
            unregisterReceiver(settingsReceiver);
        }
    }

    protected void applyAccessibilitySettings() {
        // Получаем выбранную тему
        String appTheme = accessibilityManager.getAppTheme();
        boolean isHighContrast = accessibilityManager.isHighContrastEnabled();
        
        // Применяем тему в зависимости от настроек
        int themeId;
        int statusBarColor;
        int navigationBarColor;
        
        if (isHighContrast) {
            themeId = R.style.Theme_MyApplication_HighContrast;
            statusBarColor = getResources().getColor(R.color.primary_dark_high_contrast, getTheme());
            navigationBarColor = getResources().getColor(R.color.primary_dark_high_contrast, getTheme());
        } else {
            switch (appTheme) {
                case "light":
                    themeId = R.style.Theme_MyApplication_Light;
                    statusBarColor = getResources().getColor(R.color.primary_dark_light, getTheme());
                    navigationBarColor = getResources().getColor(R.color.primary_dark_light, getTheme());
                    break;
                case "dark":
                    themeId = R.style.Theme_MyApplication;
                    statusBarColor = getResources().getColor(R.color.primary_dark, getTheme());
                    navigationBarColor = getResources().getColor(R.color.primary_dark, getTheme());
                    break;
                case "system":
                default:
                    // При выборе системной темы используем настройки DayNight
                    themeId = R.style.Theme_MyApplication;
                    statusBarColor = getResources().getColor(R.color.primary_dark, getTheme());
                    navigationBarColor = getResources().getColor(R.color.primary_dark, getTheme());
                    break;
            }
        }
        
        // Устанавливаем тему
        setTheme(themeId);
        
        // Обновляем цвета системных панелей
        getWindow().setStatusBarColor(statusBarColor);
        getWindow().setNavigationBarColor(navigationBarColor);
        
        // Обновляем цвет иконок в зависимости от яркости фона статусбара 
        if (appTheme.equals("light") && !isHighContrast) {
            // Тёмные иконки для светлой темы
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().getInsetsController().setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                
                getWindow().getInsetsController().setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decorView = getWindow().getDecorView();
                int flags = decorView.getSystemUiVisibility();
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                decorView.setSystemUiVisibility(flags);
            }
        } else {
            // Светлые иконки для тёмной темы
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().getInsetsController().setSystemBarsAppearance(
                    0, 
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                
                getWindow().getInsetsController().setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decorView = getWindow().getDecorView();
                int flags = decorView.getSystemUiVisibility();
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                decorView.setSystemUiVisibility(flags);
            }
        }
        
        // Форсируем перерисовку интерфейса
        ViewGroup contentView = findViewById(android.R.id.content);
        if (contentView != null) {
            contentView.invalidate();
            contentView.requestLayout();
        }
        
        // Обновляем контент после смены темы
        View rootView = getWindow().getDecorView().getRootView();
        applyAccessibilitySettingsToViewHierarchy(rootView);
        onAccessibilitySettingsApplied();
    }

    private void applyAccessibilitySettingsToViewHierarchy(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                applyAccessibilitySettingsToViewHierarchy(child);
            }
        }
        
        if (view instanceof TextView) {
            accessibilityManager.applyTextSize((TextView) view);
        }
        
        // Примечание: не устанавливаем цвета напрямую, так как это делает тема
    }

    // Метод для переопределения в дочерних активностях
    protected void onAccessibilitySettingsApplied() {
        // По умолчанию ничего не делаем
    }

    /**
     * Перезапускает активность с плавной анимацией для корректной смены темы
     * Используйте этот метод, если необходимо полностью перезагрузить интерфейс
     */
    protected void recreateActivityWithTheme() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
} 