package io.finett.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import io.finett.myapplication.base.BaseAccessibilityActivity;

/**
 * Активность настроек голосовой активации
 */
public class VoiceActivationSettingsActivity extends BaseAccessibilityActivity {
    private static final String TAG = "VoiceActivationSettings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Настройки голосовой активации");
        }
        
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new VoiceActivationSettingsFragment())
                .commit();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Перезапускаем сервис с новыми настройками
        if (isVoiceActivationServiceEnabled()) {
            stopVoiceActivationService();
            startVoiceActivationService();
        }
    }
    
    private boolean isVoiceActivationServiceEnabled() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getBoolean("voice_activation_enabled", false);
    }
    
    private void startVoiceActivationService() {
        Intent intent = new Intent(this, VoiceActivationService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Log.d(TAG, "Сервис голосовой активации запущен");
    }
    
    private void stopVoiceActivationService() {
        Intent intent = new Intent(this, VoiceActivationService.class);
        stopService(intent);
        Log.d(TAG, "Сервис голосовой активации остановлен");
    }
    
    /**
     * Фрагмент с настройками голосовой активации
     */
    public static class VoiceActivationSettingsFragment extends PreferenceFragmentCompat implements 
            SharedPreferences.OnSharedPreferenceChangeListener {
        
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.voice_activation_preferences, rootKey);
            
            final SeekBarPreference sensitivityPreference = findPreference("wake_sensitivity");
            if (sensitivityPreference != null) {
                // Устанавливаем значения по умолчанию
                sensitivityPreference.setMin(0);
                sensitivityPreference.setMax(100);
                sensitivityPreference.setValue(50);
                
                sensitivityPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    int sensitivity = (Integer) newValue;
                    // Конвертируем значение в диапазон 0.0 - 1.0
                    float sensitivityValue = sensitivity / 100.0f;
                    
                    // Сохраняем значение как float
                    SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
                    prefs.edit().putFloat("wake_sensitivity", sensitivityValue).apply();
                    
                    return true;
                });
            }
            
            // Получаем текущие wake-фразы
            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            String currentPhrases = prefs.getString("wake_phrases", "");
            
            // Настраиваем поле для ввода wake-фраз
            EditTextPreference wakePhrasesPreference = findPreference("wake_phrases");
            if (wakePhrasesPreference != null) {
                if (currentPhrases.isEmpty()) {
                    // Устанавливаем значения по умолчанию если они не заданы
                    wakePhrasesPreference.setText("привет алан,алан,привет! алан");
                }
                
                wakePhrasesPreference.setSummaryProvider(preference -> {
                    String phrases = ((EditTextPreference) preference).getText();
                    return phrases == null || phrases.isEmpty() 
                            ? "Не задано (используются стандартные фразы)" 
                            : phrases.replace(",", ", ");
                });
            }
            
            // Переключатель включения/выключения сервиса
            SwitchPreferenceCompat enabledPreference = findPreference("voice_activation_enabled");
            if (enabledPreference != null) {
                enabledPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enabled = (Boolean) newValue;
                    
                    try {
                        if (enabled) {
                            // Запускаем сервис голосовой активации
                            Intent intent = new Intent(getActivity(), VoiceActivationService.class);
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                getActivity().startForegroundService(intent);
                            } else {
                                getActivity().startService(intent);
                            }
                            Log.d(TAG, "Сервис голосовой активации запущен из настроек");
                        } else {
                            // Останавливаем сервис голосовой активации
                            Intent intent = new Intent(getActivity(), VoiceActivationService.class);
                            getActivity().stopService(intent);
                            Log.d(TAG, "Сервис голосовой активации остановлен из настроек");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка при изменении состояния сервиса голосовой активации", e);
                    }
                    
                    return true;
                });
            }
        }
        
        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }
        
        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }
        
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            // Можно добавить дополнительную логику при изменении настроек
        }
    }
} 