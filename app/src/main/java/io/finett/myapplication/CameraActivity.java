package io.finett.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import io.finett.myapplication.api.ApiClient;
import io.finett.myapplication.api.OpenRouterApi;
import io.finett.myapplication.base.BaseAccessibilityActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CameraActivity extends BaseAccessibilityActivity implements TextToSpeech.OnInitListener {
    private ImageCapture imageCapture;
    private TextToSpeech textToSpeech;
    private OpenRouterApi openRouterApi;
    private String apiKey;
    private static final String MODEL_ID = "google/gemini-2.0-flash-001";
    private CircularProgressIndicator progressIndicator;
    private FloatingActionButton captureButton;
    private PreviewView previewView;
    private Handler handler;
    private static final int CAPTURE_DELAY = 1500; // 1.5 секунды

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        apiKey = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                .getString(MainActivity.API_KEY_PREF, null);
        openRouterApi = ApiClient.getClient().create(OpenRouterApi.class);
        textToSpeech = new TextToSpeech(this, this);
        handler = new Handler(Looper.getMainLooper());

        progressIndicator = findViewById(R.id.progressIndicator);
        captureButton = findViewById(R.id.captureButton);
        previewView = findViewById(R.id.previewView);
        
        captureButton.setOnClickListener(v -> captureImage());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.CAMERA}, 100);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

                // Настраиваем интерфейс в зависимости от режима съемки
                if (accessibilityManager.isAutoCaptureEnabled()) {
                    // Автоматическая съемка - скрываем кнопку и запускаем таймер
                    captureButton.setVisibility(View.GONE);
                    progressIndicator.setVisibility(View.VISIBLE);
                    
                    // Уведомляем пользователя о том, что фото будет сделано автоматически
                    Toast.makeText(this, "Фото будет сделано автоматически через 1.5 секунды", 
                            Toast.LENGTH_SHORT).show();
                    
                    // Запускаем съемку через заданное время
                    handler.postDelayed(this::captureImage, CAPTURE_DELAY);
                } else {
                    // Ручная съемка - показываем кнопку
                    captureButton.setVisibility(View.VISIBLE);
                    progressIndicator.setVisibility(View.GONE);
                    
                    // Уведомляем пользователя о необходимости нажать кнопку
                    Toast.makeText(this, "Нажмите кнопку для съемки фото", 
                            Toast.LENGTH_SHORT).show();
                }

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            }
        }
    }

    private void captureImage() {
        if (imageCapture == null) return;

        // Показываем индикатор прогресса и блокируем кнопку
        progressIndicator.setVisibility(View.VISIBLE);
        captureButton.setEnabled(false);

        File photoFile = new File(getExternalCacheDir(), "photo.jpg");
        ImageCapture.OutputFileOptions outputOptions = 
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        progressIndicator.setVisibility(View.GONE);
                        analyzeImage(photoFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        progressIndicator.setVisibility(View.GONE);
                        captureButton.setEnabled(true);
                        exception.printStackTrace();
                        showError("Ошибка при съемке фото");
                    }
                });
    }

    private void analyzeImage(File imageFile) {
        try {
            String base64Image = convertImageToBase64(imageFile);
            analyzeImage(base64Image);
        } catch (IOException e) {
            showError("Ошибка при обработке изображения");
            hideProgress();
        }
    }

    private String convertImageToBase64(File file) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        byte[] imageBytes = baos.toByteArray();
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes);
    }

    private void analyzeImage(String base64Image) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", MODEL_ID);

        List<Map<String, Object>> messages = new ArrayList<>();
        
        // Системное сообщение
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", Arrays.asList(
            Map.of(
                "type", "text",
                "text", "Ты - помощник, который анализирует изображения. " +
                        "Описывай то, что видишь на изображении, кратко и по-русски. " +
                        "Старайся уложиться в 2-3 предложения."
            )
        ));
        messages.add(systemMessage);

        // Сообщение с изображением
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", Arrays.asList(
            Map.of(
                "type", "text",
                "text", "Что изображено на этой фотографии?"
            ),
            Map.of(
                "type", "image_url",
                "image_url", base64Image
            )
        ));
        messages.add(userMessage);
        
        body.put("messages", messages);

        openRouterApi.getChatCompletion(
                "Bearer " + apiKey,
                "https://github.com/your-username/your-repo",
                "Android Voice Chat App",
                body
        ).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, 
                    Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        ArrayList<Map<String, Object>> choices = 
                                (ArrayList<Map<String, Object>>) response.body().get("choices");
                        Map<String, Object> choice = choices.get(0);
                        Map<String, String> message = (Map<String, String>) choice.get("message");
                        String content = message.get("content");
                        
                        // Возвращаем результат в VoiceChatActivity
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("analysis_result", content);
                        setResult(RESULT_OK, resultIntent);
                        
                        // Проговариваем результат и закрываем активность
                        speakResult(content);
                    } catch (Exception e) {
                        showError("Ошибка при обработке ответа");
                        hideProgress();
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                } else {
                    showError("Ошибка при получении ответа");
                    hideProgress();
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                showError("Ошибка сети");
                hideProgress();
                setResult(RESULT_CANCELED);
                finish();
            }
        });
    }

    private void speakResult(String text) {
        if (textToSpeech != null) {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "description");
            textToSpeech.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {}

                @Override
                public void onDone(String utteranceId) {
                    finish();
                }

                @Override
                public void onError(String utteranceId) {
                    finish();
                }
            });
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
    }

    private void hideProgress() {
        runOnUiThread(() -> {
            progressIndicator.setVisibility(View.GONE);
            
            // Восстанавливаем состояние кнопки, если не используется автоматическая съемка
            if (!accessibilityManager.isAutoCaptureEnabled()) {
                captureButton.setVisibility(View.VISIBLE);
                captureButton.setEnabled(true);
            }
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            
            // В случае ошибки сбрасываем состояние интерфейса
            if (!accessibilityManager.isAutoCaptureEnabled()) {
                captureButton.setVisibility(View.VISIBLE);
                captureButton.setEnabled(true);
            }
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(new java.util.Locale("ru"));
            if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                showError("Русский язык не поддерживается для синтеза речи");
            }
        } else {
            showError("Ошибка инициализации синтеза речи");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
} 