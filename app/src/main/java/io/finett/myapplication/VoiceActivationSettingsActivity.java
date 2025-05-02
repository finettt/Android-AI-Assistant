package io.finett.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

public class VoiceActivationSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new VoiceActivationSettingsFragment())
                    .commit();
        }
        
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("Настройки голосовой активации");
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    public static class VoiceActivationSettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        private SwitchPreferenceCompat voiceActivationEnabledPreference;
        
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.voice_activation_preferences, rootKey);
            
            // Получаем ссылки на элементы настроек
            voiceActivationEnabledPreference = findPreference("voice_activation_enabled");
            
            // Устанавливаем обработчики для особых настроек
            final SeekBarPreference sensitivityPreference = findPreference("wake_sensitivity");
            if (sensitivityPreference != null) {
                sensitivityPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    // Конвертируем значение SeekBar (0-100) в коэффициент чувствительности (0.0-1.0)
                    float sensitivity = ((Integer) newValue).floatValue() / 100f;
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
                    prefs.edit().putFloat("wake_sensitivity", sensitivity).apply();
                    return true;
                });
            }
            
            // Обработчик для переключения сервиса голосовой активации
            final SwitchPreferenceCompat voiceActivationSwitch = findPreference("voice_activation_enabled");
            if (voiceActivationSwitch != null) {
                // Загружаем текущее состояние
                SharedPreferences prefs = requireContext().getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
                boolean isEnabled = prefs.getBoolean("voice_activation_enabled", false);
                voiceActivationSwitch.setChecked(isEnabled);
                
                voiceActivationSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enabled = (Boolean) newValue;
                    
                    if (enabled) {
                        // Запускаем сервис
                        Intent serviceIntent = new Intent(requireContext(), VoiceActivationService.class);
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            requireContext().startForegroundService(serviceIntent);
                        } else {
                            requireContext().startService(serviceIntent);
                        }
                    } else {
                        // Останавливаем сервис
                        Intent serviceIntent = new Intent(requireContext(), VoiceActivationService.class);
                        requireContext().stopService(serviceIntent);
                    }
                    
                    // Сохраняем состояние
                    SharedPreferences.Editor editor = requireContext().getSharedPreferences(
                            MainActivity.PREFS_NAME, MODE_PRIVATE).edit();
                    editor.putBoolean("voice_activation_enabled", enabled);
                    editor.apply();
                    
                    return true;
                });
            }
        }
        
        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
            
            // Обновляем состояние переключателя
            if (voiceActivationEnabledPreference != null) {
                SharedPreferences prefs = requireContext().getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
                boolean isEnabled = prefs.getBoolean("voice_activation_enabled", false);
                voiceActivationEnabledPreference.setChecked(isEnabled);
            }
        }
        
        @Override
        public void onPause() {
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
            super.onPause();
        }
        
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // При изменении настроек, обновляем UI если нужно
            if ("voice_activation_enabled".equals(key) && voiceActivationEnabledPreference != null) {
                boolean isEnabled = sharedPreferences.getBoolean(key, false);
                voiceActivationEnabledPreference.setChecked(isEnabled);
            }
            
            // Уведомляем сервис об изменении настроек
            Intent intent = new Intent("io.finett.myapplication.VOICE_SETTINGS_UPDATED");
            requireContext().sendBroadcast(intent);
        }
    }
} 