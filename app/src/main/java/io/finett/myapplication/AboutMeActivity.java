package io.finett.myapplication;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputEditText;

import io.finett.myapplication.util.UserManager;

public class AboutMeActivity extends AppCompatActivity {

    private TextView userNameTextView;
    private TextView apiKeyTextView;
    private ImageButton copyApiKeyButton;
    private Button editProfileButton;
    private UserManager userManager;
    private String apiKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_me);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("О себе");

        userNameTextView = findViewById(R.id.userNameTextView);
        apiKeyTextView = findViewById(R.id.apiKeyTextView);
        copyApiKeyButton = findViewById(R.id.copyApiKeyButton);
        editProfileButton = findViewById(R.id.editProfileButton);

        userManager = new UserManager(this);

        loadUserData();

        copyApiKeyButton.setOnClickListener(v -> copyApiKeyToClipboard());
        editProfileButton.setOnClickListener(v -> showEditProfileDialog());
    }

    private void loadUserData() {
        // Загрузка имени пользователя
        String userName = userManager.getUserName();
        if (userName != null && !userName.isEmpty()) {
            userNameTextView.setText(userName);
        } else {
            userNameTextView.setText("Не указано");
        }

        // Загрузка API ключа
        apiKey = getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            if (BuildConfig.USE_HARDCODED_KEY) {
                apiKeyTextView.setText(maskApiKey(apiKey) + " (встроенный ключ)");
            } else {
                apiKeyTextView.setText(maskApiKey(apiKey));
            }
        } else {
            apiKeyTextView.setText("Не указан");
        }
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() <= 8) {
            return key;
        }
        // Маскируем часть ключа для безопасности, показывая только начало и конец
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }

    private void copyApiKeyToClipboard() {
        if (apiKey != null && !apiKey.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("API Key", apiKey);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "API ключ скопирован", Toast.LENGTH_SHORT).show();
        }
    }

    private void showEditProfileDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Редактировать данные");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);
        builder.setView(view);

        TextInputEditText userNameEditText = view.findViewById(R.id.userNameEditText);
        TextInputEditText apiKeyEditText = view.findViewById(R.id.apiKeyEditText);

        // Заполняем текущими данными
        userNameEditText.setText(userManager.getUserName());
        apiKeyEditText.setText(apiKey);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String newUserName = userNameEditText.getText().toString().trim();
            String newApiKey = apiKeyEditText.getText().toString().trim();

            // Сохраняем новое имя пользователя
            if (!newUserName.isEmpty() && !newUserName.equals(userManager.getUserName())) {
                userManager.registerUser(newUserName);
            }

            // Сохраняем новый API ключ
            if (!newApiKey.isEmpty() && !newApiKey.equals(apiKey)) {
                saveApiKey(newApiKey);
            }

            // Обновляем отображаемые данные
            loadUserData();
        });

        builder.setNegativeButton("Отмена", null);
        
        // Создаем и настраиваем диалог для установки белого цвета текста на кнопках
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.white));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.white));
        });
        dialog.show();
    }

    private void saveApiKey(String key) {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(MainActivity.API_KEY_PREF, key);
        editor.apply();
        apiKey = key;
    }

    private String getApiKey() {
        // Проверяем флаг, использовать ли хардкод ключ из BuildConfig
        if (BuildConfig.USE_HARDCODED_KEY) {
            return BuildConfig.DEFAULT_OPENROUTER_API_KEY;
        }
        
        // Получаем сохраненный пользовательский ключ
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        String apiKey = prefs.getString(MainActivity.API_KEY_PREF, null);
        
        // Если API ключ не установлен, показываем встроенный ключ
        if (apiKey == null || apiKey.isEmpty()) {
            return BuildConfig.DEFAULT_OPENROUTER_API_KEY;
        }
        
        return apiKey;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 