package io.finett.myapplication;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

/**
 * Активность настроек системного ассистента
 */
public class AssistantSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // Добавляем фрагмент настроек
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new AssistantSettingsFragment())
                .commit();
                
        // Настраиваем панель действий
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("Настройки ассистента");
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Закрываем активность при нажатии на кнопку "Назад"
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Фрагмент настроек системного ассистента
     */
    public static class AssistantSettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            // Загружаем настройки из XML
            setPreferencesFromResource(R.xml.assistant_preferences, rootKey);
            
            // Настраиваем обработчики для элементов настроек
            setupPreferences();
        }
        
        /**
         * Настраивает обработчики для элементов настроек
         */
        private void setupPreferences() {
            // Получаем ссылки на элементы настроек
            SwitchPreferenceCompat animationPref = findPreference("animation_enabled");
            SwitchPreferenceCompat volumeReductionPref = findPreference("auto_volume_reduction");
            SeekBarPreference autoDismissTimeoutPref = findPreference("auto_dismiss_timeout");
            SwitchPreferenceCompat autoLaunchVoiceChatPref = findPreference("auto_launch_voice_chat");
            
            // Устанавливаем значения из хранилища настроек
            if (animationPref != null) {
                animationPref.setChecked(AssistantSettings.isAnimationEnabled(getContext()));
                animationPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enabled = (Boolean) newValue;
                    AssistantSettings.setAnimationEnabled(getContext(), enabled);
                    return true;
                });
            }
            
            if (volumeReductionPref != null) {
                volumeReductionPref.setChecked(AssistantSettings.isAutoVolumeReductionEnabled(getContext()));
                volumeReductionPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enabled = (Boolean) newValue;
                    AssistantSettings.setAutoVolumeReductionEnabled(getContext(), enabled);
                    return true;
                });
            }
            
            if (autoDismissTimeoutPref != null) {
                int timeoutSec = AssistantSettings.getAutoDismissTimeout(getContext()) / 1000;
                autoDismissTimeoutPref.setValue(timeoutSec);
                autoDismissTimeoutPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    int timeoutSeconds = (Integer) newValue;
                    AssistantSettings.setAutoDismissTimeout(getContext(), timeoutSeconds * 1000);
                    return true;
                });
            }
            
            if (autoLaunchVoiceChatPref != null) {
                autoLaunchVoiceChatPref.setChecked(AssistantSettings.isAutoLaunchVoiceChatEnabled(getContext()));
                autoLaunchVoiceChatPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enabled = (Boolean) newValue;
                    AssistantSettings.setAutoLaunchVoiceChatEnabled(getContext(), enabled);
                    return true;
                });
            }
        }
    }
} 