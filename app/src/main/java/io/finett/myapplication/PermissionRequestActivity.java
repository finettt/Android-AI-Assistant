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
            proceedToMainActivity();
        } else {
            statusTextView.setText(R.string.permissions_needed);
            grantPermissionsButton.setVisibility(View.VISIBLE);
            
            // Показываем список отсутствующих разрешений
            StringBuilder permissionText = new StringBuilder();
            permissionText.append(getString(R.string.permissions_needed)).append("\n\n");
            
            for (String permission : missingPermissions) {
                permissionText.append("• ").append(getReadablePermissionName(permission)).append("\n");
            }
            
            statusTextView.setText(permissionText.toString());
        }
    }

    private List<String> getMissingPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        
        return missingPermissions;
    }

    private void requestAppPermissions() {
        List<String> missingPermissions = getMissingPermissions();
        
        if (!missingPermissions.isEmpty()) {
            // Добавим отладочный вывод для проверки
            Log.d("PermissionRequest", "Запрашиваем разрешения: " + missingPermissions);
            
            // Проверяем нужно ли показывать объяснение
            boolean shouldShowRationale = false;
            for (String permission : missingPermissions) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    shouldShowRationale = true;
                    Log.d("PermissionRequest", "Нужно объяснение для: " + permission);
                    break;
                }
            }
            
            // Если пользователь ранее отказал, но не выбрал "Больше не спрашивать",
            // показываем диалог с объяснением, почему нужны разрешения
            if (shouldShowRationale) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.permission_rationale_title)
                        .setMessage(R.string.permission_rationale_message)
                        .setPositiveButton(R.string.ok, (dialog, which) -> {
                            // Повторно запрашиваем разрешения после объяснения
                            ActivityCompat.requestPermissions(
                                    PermissionRequestActivity.this,
                                    missingPermissions.toArray(new String[0]),
                                    PERMISSION_REQUEST_CODE
                            );
                        })
                        .create()
                        .show();
            } else {
                // Непосредственно запрашиваем разрешения
                ActivityCompat.requestPermissions(
                        this,
                        missingPermissions.toArray(new String[0]),
                        PERMISSION_REQUEST_CODE
                );
            }
        } else {
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
                    
                    // Проверяем, установил ли пользователь галочку "Больше не спрашивать"
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                        somePermissionPermanentlyDenied = true;
                    }
                }
            }
            
            if (allPermissionsGranted) {
                Toast.makeText(this, R.string.permissions_granted_toast, Toast.LENGTH_SHORT).show();
                proceedToMainActivity();
            } else {
                if (somePermissionPermanentlyDenied) {
                    // Если пользователь отказал с галочкой "Больше не спрашивать", предлагаем перейти в настройки
                    showSettingsDialog();
                } else {
                    Toast.makeText(this, R.string.permissions_denied_toast, Toast.LENGTH_LONG).show();
                    // Обновляем статус и объяснение
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
                return getString(R.string.permission_location);
            case Manifest.permission.READ_EXTERNAL_STORAGE:
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
            default:
                return permission.substring(permission.lastIndexOf('.') + 1);
        }
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.permissions_permanently_denied_title)
            .setMessage(R.string.permissions_permanently_denied_message)
            .setPositiveButton(R.string.settings, (dialog, which) -> {
                // Открываем настройки приложения
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            })
            .setNegativeButton(R.string.cancel, (dialog, which) -> {
                // Просто обновляем состояние экрана с разрешениями
                checkPermissions();
            })
            .setCancelable(false)
            .create()
            .show();
    }

    private List<String> getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        
        // Базовые разрешения, нужные на всех версиях
        permissions.add(Manifest.permission.CAMERA);
        permissions.add(Manifest.permission.RECORD_AUDIO);
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        
        // Добавляем разрешения для работы с календарем
        permissions.add(Manifest.permission.READ_CALENDAR);
        permissions.add(Manifest.permission.WRITE_CALENDAR);
        
        // Разрешения для хранилища в зависимости от версии Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: используем гранулярные разрешения для медиа
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Ниже Android 11 используем старый способ с READ_EXTERNAL_STORAGE
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        
        // Разрешение на отправку уведомлений для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        
        return permissions;
    }
} 