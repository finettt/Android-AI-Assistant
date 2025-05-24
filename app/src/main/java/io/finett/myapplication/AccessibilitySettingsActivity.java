package io.finett.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import io.finett.myapplication.base.BaseAccessibilityActivity;

public class AccessibilitySettingsActivity extends BaseAccessibilityActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Настройки доступности");
        }

        MaterialButton applyButton = findViewById(R.id.apply_button);
        applyButton.setOnClickListener(v -> applySettings());
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.accessibility_preferences, rootKey);
            
            // Настраиваем обработчик для настройки системного ассистента
            Preference systemAssistantSetup = findPreference("system_assistant_setup");
            if (systemAssistantSetup != null) {
                systemAssistantSetup.setOnPreferenceClickListener(preference -> {
                    showAssistantSetupInstructionsDialog();
                    return true;
                });
            }
            
            // Настраиваем переключатель автозапуска
            SwitchPreferenceCompat autoLaunchPref = findPreference("auto_launch_assistant");
            if (autoLaunchPref != null) {
                autoLaunchPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enabled = (Boolean) newValue;
                    // Сохраняем настройку в глобальные настройки приложения
                    SharedPreferences prefs = getActivity().getSharedPreferences("ChatAppPrefs", 
                            getActivity().MODE_PRIVATE);
                    prefs.edit().putBoolean("autostart_assistant", enabled).apply();
                    
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
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // Обработка изменений настроек здесь при необходимости
        }
        
        /**
         * Показывает диалог с инструкциями по настройке системного ассистента
         * и переходит в системные настройки для выбора ассистента
         */
        private void showAssistantSetupInstructionsDialog() {
            if (getActivity() == null) return;
            
            AlertDialog.Builder builder = new MaterialAlertDialogBuilder(getActivity());
            builder.setTitle("Настройка системного ассистента");
            
            // Создаем ScrollView для прокрутки длинного текста
            ScrollView scrollView = new ScrollView(getActivity());
            TextView textView = new TextView(getActivity());
            int padding = (int) (getResources().getDisplayMetrics().density * 16);
            textView.setPadding(padding, padding, padding, padding);
            
            String instructions = 
                    "Для установки приложения в качестве ассистента по умолчанию:\n\n" +
                    "1. В открывшихся настройках найдите пункт \"Приложение для голосового ввода\" " +
                    "или \"Цифровой помощник\"\n\n" +
                    "2. Выберите \"Алан\" из списка доступных ассистентов\n\n" +
                    "3. На некоторых устройствах может потребоваться дополнительное подтверждение\n\n" +
                    "4. После активации, вы можете вызвать ассистента:\n" +
                    "   • Зажав кнопку Home (на устройствах с кнопками)\n" +
                    "   • Свайпом из нижнего угла (на устройствах с жестами)\n" +
                    "   • Произнеся \"Привет, Алан\" (если включена голосовая активация)\n\n" +
                    "5. Для более удобного использования рекомендуется также настроить автоматический запуск при включении устройства в настройках приложения\n\n" +
                    "Примечание: на некоторых устройствах (особенно Huawei, Xiaomi) могут быть дополнительные ограничения, требующие отключения оптимизации батареи для приложения.";
            
            textView.setText(instructions);
            scrollView.addView(textView);
            builder.setView(scrollView);
            
            builder.setPositiveButton("Продолжить", (dialog, which) -> {
                dialog.dismiss();
                // Открываем системные настройки для выбора ассистента
                openAssistantSystemSettings();
            });
            
            builder.setNegativeButton("Отмена", (dialog, which) -> {
                dialog.dismiss();
            });
            
            AlertDialog dialog = builder.create();
            dialog.show();
        }
        
        /**
         * Открывает системные настройки для выбора приложения-ассистента
         */
        private void openAssistantSystemSettings() {
            try {
                Intent intent = new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                // Альтернативный вариант, если первый метод не сработал
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
                    startActivity(intent);
                    
                    Toast.makeText(getActivity(), 
                            "Найдите Алан в списке приложений и установите его как ассистент по умолчанию",
                            Toast.LENGTH_LONG).show();
                } catch (Exception e2) {
                    Toast.makeText(getActivity(), 
                            "Не удалось открыть настройки. Перейдите в Настройки -> Приложения -> Приложения по умолчанию -> Цифровой помощник",
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void applySettings() {
        // Отправляем широковещательное сообщение о изменении настроек
        Intent intent = new Intent("io.finett.myapplication.SETTINGS_UPDATED");
        sendBroadcast(intent);

        // Показываем сообщение об успешном применении
        Toast.makeText(this, "Настройки применены", Toast.LENGTH_SHORT).show();

        // Закрываем активность
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
} 