package io.finett.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.finett.myapplication.api.ApiClient;
import io.finett.myapplication.api.OpenRouterApi;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CameraActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private ImageCapture imageCapture;
    private TextToSpeech textToSpeech;
    private OpenRouterApi openRouterApi;
    private String apiKey;
    private static final String MODEL_ID = "google/gemini-2.0-flash-001";
    private com.google.android.material.progressindicator.CircularProgressIndicator progressIndicator;
    private FloatingActionButton captureButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        apiKey = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                .getString(MainActivity.API_KEY_PREF, null);
        openRouterApi = ApiClient.getClient().create(OpenRouterApi.class);
        textToSpeech = new TextToSpeech(this, this);

        progressIndicator = findViewById(R.id.progressIndicator);
        captureButton = findViewById(R.id.captureButton);
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
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
                Preview preview = new Preview.Builder().build();
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                PreviewView previewView = findViewById(R.id.preview);
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

                // Показываем индикатор прогресса
                progressIndicator.setVisibility(View.VISIBLE);
                captureButton.setEnabled(false);

                // Запускаем автоматическую съемку через 1 секунду
                new android.os.Handler().postDelayed(() -> {
                    if (!isFinishing()) {
                        captureImage();
                    }
                }, 1000); // 1000 миллисекунд = 1 секунда

            } catch (Exception e) {
                Toast.makeText(this, "Ошибка инициализации камеры", 
                        Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureImage() {
        if (imageCapture == null) return;

        File photoFile = new File(getExternalFilesDir(null), "photo.jpg");
        ImageCapture.OutputFileOptions outputOptions = 
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        try {
                            String base64Image = convertImageToBase64(photoFile);
                            analyzeImage(base64Image);
                        } catch (IOException e) {
                            showError("Ошибка при обработке изображения");
                            hideProgress();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        showError("Ошибка при съемке фото");
                        hideProgress();
                    }
                });
    }

    private void hideProgress() {
        runOnUiThread(() -> {
            progressIndicator.setVisibility(View.GONE);
            captureButton.setEnabled(true);
        });
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

    private void showError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                showError("Необходимо разрешение на использование камеры");
                finish();
            }
        }
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
    }
} 