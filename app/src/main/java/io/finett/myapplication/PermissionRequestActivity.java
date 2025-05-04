package io.finett.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PermissionRequestActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 123;
    private TextView statusTextView;
    private Button grantPermissionsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_request);

        statusTextView = findViewById(R.id.permissionStatusText);
        grantPermissionsButton = findViewById(R.id.grantPermissionsButton);

        grantPermissionsButton.setOnClickListener(v -> requestAppPermissions());

        checkPermissions();
    }

    private void checkPermissions() {
        List<String> missingPermissions = getMissingPermissions();
        
        if (missingPermissions.isEmpty()) {
            statusTextView.setText(R.string.permissions_granted);
            grantPermissionsButton.setVisibility(View.GONE);
            Log.d("PermissionRequest", "Все необходимые разрешения предоставлены, переходим в основное приложение");
            proceedToMainActivity();
        } else {
            Log.d("PermissionRequest", "Отсутствующие разрешения (" + missingPermissions.size() + "): " + missingPermissions);
            statusTextView.setText(R.string.permissions_needed);
            grantPermissionsButton.setVisibility(View.VISIBLE);
            
            // Показываем список отсутствующих разрешений
            StringBuilder permissionText = new StringBuilder();
            permissionText.append(getString(R.string.permissions_needed)).append("\n\n");
            
            for (String permission : missingPermissions) {
                String readableName = getReadablePermissionName(permission);
                permissionText.append("• ").append(readableName).append("\n");
                Log.d("PermissionRequest", "Нужно разрешение: " + permission + " (" + readableName + ")");
            }
            
            statusTextView.setText(permissionText.toString());
        }
    }

    private List<String> getMissingPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        // Используем Set для исключения возможных дубликатов разрешений
        List<String> uniqueRequiredPermissions = new ArrayList<>(
            new HashSet<>(getRequiredPermissions()));
        
        for (String permission : uniqueRequiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        
        return missingPermissions;
    }

    private void requestAppPermissions() {
        List<String> missingPermissions = getMissingPermissions();
        
        if (!missingPermissions.isEmpty()) {
            // Добавим подробный отладочный вывод
            Log.d("PermissionRequest", "Запрашиваем разрешения (всего " + missingPermissions.size() + "): " + missingPermissions);
            
            // Проверяем, есть ли разрешения, которые требуют объяснения
            List<String> permissionsNeedingRationale = new ArrayList<>();
            for (String permission : missingPermissions) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    permissionsNeedingRationale.add(permission);
                    Log.d("PermissionRequest", "Требуется объяснение для: " + permission);
                }
            }
            
            // Если пользователь ранее отказал и нужно показать объяснение
            if (!permissionsNeedingRationale.isEmpty()) {
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.permission_rationale_title)
                        .setMessage(R.string.permission_rationale_message)
                        .setPositiveButton(R.string.ok, (d, which) -> {
                            // Повторно запрашиваем разрешения после объяснения
                            Log.d("PermissionRequest", "Пользователь согласился после объяснения, запрашиваем разрешения");
                            ActivityCompat.requestPermissions(
                                    PermissionRequestActivity.this,
                                    missingPermissions.toArray(new String[0]),
                                    PERMISSION_REQUEST_CODE
                            );
                        })
                        .create();
                
                // Установка белого цвета для кнопок после показа диалога
                dialog.setOnShowListener(dialogInterface -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.white));
                });
                
                dialog.show();
            } else {
                // Непосредственно запрашиваем разрешения
                Log.d("PermissionRequest", "Непосредственно запрашиваем разрешения без объяснения");
                ActivityCompat.requestPermissions(
                        this,
                        missingPermissions.toArray(new String[0]),
                        PERMISSION_REQUEST_CODE
                );
            }
        } else {
            Log.d("PermissionRequest", "Все разрешения уже предоставлены, переходим в MainActivity");
            proceedToMainActivity();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            boolean somePermissionPermanentlyDenied = false;
            List<String> deniedPermissions = new ArrayList<>();
            
            // Проверяем результаты всех разрешений
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    deniedPermissions.add(permissions[i]);
                    
                    // Добавляем отладочный вывод
                    Log.d("PermissionRequest", "Отказано в разрешении: " + permissions[i]);
                    
                    // Проверяем, установил ли пользователь галочку "Больше не спрашивать"
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                        somePermissionPermanentlyDenied = true;
                        Log.d("PermissionRequest", "Пользователь установил 'Больше не спрашивать' для: " + permissions[i]);
                    }
                } else {
                    // Добавляем отладочный вывод для успешного разрешения
                    Log.d("PermissionRequest", "Разрешение предоставлено: " + permissions[i]);
                }
            }
            
            if (allPermissionsGranted) {
                Toast.makeText(this, R.string.permissions_granted_toast, Toast.LENGTH_SHORT).show();
                Log.d("PermissionRequest", "Все разрешения получены, переходим в MainActivity");
                proceedToMainActivity();
            } else {
                if (somePermissionPermanentlyDenied) {
                    // Если пользователь отказал с галочкой "Больше не спрашивать", предлагаем перейти в настройки
                    Log.d("PermissionRequest", "Предлагаем перейти в настройки для включения разрешений");
                    showSettingsDialog();
                } else {
                    Toast.makeText(this, R.string.permissions_denied_toast, Toast.LENGTH_LONG).show();
                    // Обновляем статус и объяснение
                    Log.d("PermissionRequest", "Отказано в некоторых разрешениях, обновляем экран");
                    statusTextView.setText(R.string.permissions_required_explanation);
                    // Обновляем экран
                    checkPermissions();
                }
            }
        }
    }

    private void proceedToMainActivity() {
        // Determine if this is the first launch to show onboarding
        SharedPreferences prefs = getSharedPreferences("AlanPrefs", MODE_PRIVATE);
        boolean isFirstTime = OnboardingActivity.shouldShowOnboarding(prefs);
        
        Intent intent;
        if (isFirstTime) {
            // First time users go to onboarding
            intent = new Intent(this, OnboardingActivity.class);
        } else {
            // Returning users go straight to the main activity
            intent = new Intent(this, MainActivity.class);
        }
        
        startActivity(intent);
        finish();
    }

    private String getReadablePermissionName(String permission) {
        switch (permission) {
            case Manifest.permission.CAMERA:
                return getString(R.string.permission_camera);
            case Manifest.permission.RECORD_AUDIO:
                return getString(R.string.permission_microphone);
            case Manifest.permission.ACCESS_FINE_LOCATION:
            case Manifest.permission.ACCESS_COARSE_LOCATION:
                return getString(R.string.permission_location);
            case Manifest.permission.READ_EXTERNAL_STORAGE:
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                return getString(R.string.permission_storage);
            case Manifest.permission.READ_MEDIA_IMAGES:
            case Manifest.permission.READ_MEDIA_VIDEO:
            case Manifest.permission.READ_MEDIA_AUDIO:
                return getString(R.string.permission_media);
            case Manifest.permission.POST_NOTIFICATIONS:
                return getString(R.string.permission_notifications);
            case Manifest.permission.READ_CALENDAR:
            case Manifest.permission.WRITE_CALENDAR:
                return getString(R.string.permission_calendar);
            case Manifest.permission.READ_CONTACTS:
                return "Доступ к контактам";
            case Manifest.permission.CALL_PHONE:
                return "Совершение звонков";
            case Manifest.permission.SEND_SMS:
                return "Отправка SMS";
            case Manifest.permission.READ_PHONE_STATE:
                return "Доступ к состоянию телефона";
            case Manifest.permission.BLUETOOTH:
            case Manifest.permission.BLUETOOTH_ADMIN:
            case Manifest.permission.BLUETOOTH_CONNECT:
                return "Доступ к Bluetooth";
            default:
                return permission.substring(permission.lastIndexOf('.') + 1);
        }
    }

    private void showSettingsDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.permissions_permanently_denied_title)
            .setMessage(R.string.permissions_permanently_denied_message)
            .setPositiveButton(R.string.settings, (d, which) -> {
                // Открываем настройки приложения
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            })
            .setNegativeButton(R.string.cancel, (d, which) -> {
                // Просто обновляем состояние экрана с разрешениями
                checkPermissions();
            })
            .setCancelable(false)
            .create();
            
        // Установка белого цвета для кнопок после показа диалога
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.white));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.white));
        });
        
        dialog.show();
    }

    private List<String> getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        
        // Базовые разрешения, нужные на всех версиях
        permissions.add(Manifest.permission.CAMERA);
        permissions.add(Manifest.permission.RECORD_AUDIO);
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        
        // Разрешения для работы с контактами и коммуникациями
        permissions.add(Manifest.permission.READ_CONTACTS);
        permissions.add(Manifest.permission.CALL_PHONE);
        permissions.add(Manifest.permission.SEND_SMS);
        permissions.add(Manifest.permission.READ_PHONE_STATE);
        
        // Разрешения для работы с календарем
        permissions.add(Manifest.permission.READ_CALENDAR);
        permissions.add(Manifest.permission.WRITE_CALENDAR);
        
        // Разрешения для хранилища в зависимости от версии Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: используем гранулярные разрешения для медиа
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            
            // Разрешение на отправку уведомлений для Android 13+
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Ниже Android 11 используем старый способ с READ_EXTERNAL_STORAGE
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        
        // Разрешения для Bluetooth на Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            // BLUETOOTH_SCAN исключаем, если не требуется активное
            // сканирование Bluetooth устройств, т.к. оно требует
            // дополнительных разрешений на местоположение
            // permissions.add(Manifest.permission.BLUETOOTH_SCAN);
        } else {
            // Для Android 11 и ниже используем старые разрешения Bluetooth
            permissions.add(Manifest.permission.BLUETOOTH);
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }
        
        return permissions;
    }
} 