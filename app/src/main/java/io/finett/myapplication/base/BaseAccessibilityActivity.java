package io.finett.myapplication.base;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import io.finett.myapplication.util.AccessibilityManager;

public abstract class BaseAccessibilityActivity extends AppCompatActivity {
    protected AccessibilityManager accessibilityManager;
    private BroadcastReceiver settingsReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        accessibilityManager = new AccessibilityManager(this, null);
        setupSettingsReceiver();
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
        
        if (accessibilityManager.isHighContrastEnabled()) {
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(0xFFFFFFFF);
                view.setBackgroundColor(0xFF000000);
            }
        }
    }

    // Метод для переопределения в дочерних активностях
    protected void onAccessibilitySettingsApplied() {
        // По умолчанию ничего не делаем
    }
} 