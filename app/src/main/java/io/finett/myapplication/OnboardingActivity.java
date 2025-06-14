package io.finett.myapplication;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class OnboardingActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AlanPrefs";
    private static final String KEY_FIRST_TIME = "isFirstTime";
    private static final String USER_INFO_PREFS = "UserInfoPrefs";
    private static final String USER_NAME_KEY = "user_name";
    private static final String API_PREFS = "ApiPrefs";
    private static final String API_KEY = "api_key";
    private static final int NOTIFICATION_PERMISSION_CODE = 1;
    private static final int RECORD_AUDIO_PERMISSION_CODE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        
        // Настраиваем список возможностей приложения
        setupFeaturesList();
        
        // Настраиваем кнопку "Начать"
        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(v -> {
            // Если используем хардкодный ключ, не запрашиваем имя пользователя и API ключ
            if (BuildConfig.USE_HARDCODED_KEY) {
                // Автоматически регистрируем пользователя с дефолтным именем
                SharedPreferences prefs = getSharedPreferences(USER_INFO_PREFS, MODE_PRIVATE);
                prefs.edit().putString(USER_NAME_KEY, "Пользователь").apply();
                
                // Отмечаем, что онбординг показан и переходим к основному экрану
                markOnboardingAsShown();
                startMainActivity();
            } else {
                // Если не хардкодный ключ, запрашиваем имя пользователя
                showUserNameDialog();
            }
        });
    }

    private void showUserNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Как вас зовут?");
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        
        builder.setPositiveButton("Сохранить", new DialogInterface.OnClickListener() { 
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) {
                    // Сохраняем имя пользователя
                    SharedPreferences prefs = getSharedPreferences(USER_INFO_PREFS, MODE_PRIVATE);
                    prefs.edit().putString(USER_NAME_KEY, name).apply();
                    
                    // После получения имени показываем диалог для API ключа
                    showApiKeyDialog(name);
                }
            }
        });
        
        builder.setCancelable(false);
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.white));
            }
        });
        dialog.show();
    }
    
    private void showApiKeyDialog(final String userName) {
        // Диалог ввода API ключа отключен – приложение использует встроенный ключ.
        android.util.Log.i("OnboardingActivity", "API-key dialog suppressed – using embedded key");
        // Отмечаем, что онбординг был показан, и переходим к основной активности.
        markOnboardingAsShown();
        startMainActivity();
    }

    private void markOnboardingAsShown() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_FIRST_TIME, false);
        editor.apply();
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish(); // Закрываем экран онбординга, чтобы пользователь не мог вернуться назад
    }

    // Статический метод для проверки, нужно ли показывать онбординг
    public static boolean shouldShowOnboarding(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_FIRST_TIME, true);
    }

    private void setupFeaturesList() {
        List<Feature> features = new ArrayList<>();
        
        // Приоритет отдается системным функциям
        features.add(new Feature(
                "Управление устройством",
                "Управляйте Wi-Fi, Bluetooth, громкостью и другими настройками с помощью голоса",
                R.drawable.ic_settings
        ));
        
        features.add(new Feature(
                "Звонки и контакты",
                "Совершайте звонки и управляйте контактами с помощью голоса",
                R.drawable.ic_call
        ));
        
        features.add(new Feature(
                "Время и локация",
                "Получайте информацию о времени и вашем местоположении",
                R.drawable.ic_access_time
        ));
        
        features.add(new Feature(
                "Календарь и планирование",
                "Проверяйте события, открывайте календарь и создавайте новые записи",
                R.drawable.ic_event
        ));
        
        features.add(new Feature(
                "Карты и навигация",
                "Открывайте карты, прокладывайте маршруты и ищите места поблизости",
                R.drawable.ic_map
        ));
        
        // Затем идут функции языковой модели
        features.add(new Feature(
                "Анализ изображений",
                "Анализируйте и описывайте изображения с камеры",
                R.drawable.ic_camera
        ));
        
        features.add(new Feature(
                "Диалог и помощь",
                "Задавайте вопросы и получайте полезную информацию",
                R.drawable.ic_chat
        ));
        
        FeatureAdapter adapter = new FeatureAdapter(features);
        RecyclerView featuresRecyclerView = findViewById(R.id.featuresRecyclerView);
        featuresRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        featuresRecyclerView.setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
} 