package io.finett.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import com.google.android.material.button.MaterialButton;

public class AccessibilitySettingsActivity extends AppCompatActivity {
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

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.accessibility_preferences, rootKey);
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