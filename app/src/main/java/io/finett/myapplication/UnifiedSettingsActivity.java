package io.finett.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.button.MaterialButton;

import io.finett.myapplication.base.BaseAccessibilityActivity;

public class UnifiedSettingsActivity extends BaseAccessibilityActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new UnifiedSettingsFragment())
                    .commit();
        }
        
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("Настройки");
        }

        MaterialButton applyButton = findViewById(R.id.apply_button);
        applyButton.setOnClickListener(v -> applySettings());
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void applySettings() {
        // Отправляем широковещательное сообщение о том, что настройки были обновлены
        Intent broadcastIntent = new Intent("io.finett.myapplication.SETTINGS_UPDATED");
        sendBroadcast(broadcastIntent);
        
        // Показываем сообщение об успешном применении
        Toast.makeText(this, "Настройки применены", Toast.LENGTH_SHORT).show();
        
        // Применяем настройки доступности
        onAccessibilitySettingsApplied();
        
        // Закрываем активность
        finish();
    }
    
    public static class UnifiedSettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.unified_preferences, rootKey);
            
            // Инициализация настроек API
            initApiSettings();
            
            // Инициализация настроек доступности
            initAccessibilitySettings();
        }
        
        private void initApiSettings() {
            // Настройка API ключа
            EditTextPreference apiKeyPreference = findPreference("api_key");
            if (apiKeyPreference != null) {
                // Загружаем текущий API ключ
                SharedPreferences prefs = requireContext().getSharedPreferences("ApiPrefs", MODE_PRIVATE);
                String apiKey = prefs.getString("api_key", "");
                
                if (apiKey.isEmpty()) {
                    // Пробуем загрузить из старого хранилища
                    apiKey = requireContext().getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                            .getString(MainActivity.API_KEY_PREF, "");
                }
                
                apiKeyPreference.setText(apiKey);
                
                apiKeyPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    String key = (String) newValue;
                    // Сохраняем API ключ
                    SharedPreferences.Editor editor = requireContext().getSharedPreferences(
                            "ApiPrefs", MODE_PRIVATE).edit();
                    editor.putString("api_key", key);
                    editor.apply();
                    return true;
                });
            }
        }
        
        private void initAccessibilitySettings() {
            // Настройки доступности
            ListPreference themePreference = findPreference("app_theme");
            if (themePreference != null) {
                themePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    // Применяем тему
                    String theme = (String) newValue;
                    // Сохраняем выбранную тему
                    SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(
                            requireContext()).edit();
                    editor.putString("app_theme", theme);
                    editor.apply();
                    
                    // Перезапускаем активность для применения темы
                    requireActivity().recreate();
                    return true;
                });
            }
        }
        
        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }
        
        @Override
        public void onPause() {
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
            super.onPause();
        }
        
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // При изменении настроек, обновляем UI если нужно
        }
    }
} 