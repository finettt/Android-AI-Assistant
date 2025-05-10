package io.finett.myapplication;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * Активность системного ассистента с фиолетовой рамкой
 */
public class AssistantActivity extends AppCompatActivity {
    private static final String TAG = "AssistantActivity";
    private static final int REQUEST_SPEECH_RECOGNITION = 100;
    
    private Handler handler;
    private Runnable autoDismissRunnable;
    
    private ImageView assistantCircle;
    private TextView promptText;
    private RecyclerView messagesRecyclerView;
    private AssistantMessageAdapter messageAdapter;
    
    private ObjectAnimator pulseAnimator;
    private ValueAnimator rotateAnimator;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "onCreate() вызван");
        
        // Настраиваем окно - полупрозрачное с фиолетовой рамкой
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
        );
        
        setContentView(R.layout.activity_assistant);
        
        Log.d(TAG, "setContentView выполнен");
        
        // Инициализируем UI компоненты
        initViews();
        
        // Настраиваем анимации для круга ассистента
        setupAnimations();
        
        // Увеличиваем счетчик запусков ассистента
        int launchCount = AssistantSettings.incrementLaunchCount(this);
        Log.d(TAG, "Количество запусков ассистента: " + launchCount);
        
        // Получаем время автоматического закрытия из настроек
        int dismissDelayMs = AssistantSettings.getAutoDismissTimeout(this);
        
        handler = new Handler(Looper.getMainLooper());
        autoDismissRunnable = this::finish;
        
        // Устанавливаем автоматическое закрытие через dismissDelayMs мс
        handler.postDelayed(autoDismissRunnable, dismissDelayMs);
        
        // Добавляем приветственное сообщение
        addWelcomeMessage();
        
        // Проверяем, вызвана ли активность через кнопку ассистента
        if (isLaunchedFromAssistant()) {
            Log.d(TAG, "Активность запущена как системный ассистент");
            // Уведомляем о запуске ассистента
            AssistantMonitor.notifyAssistantStarted(this);
        } else {
            Log.d(TAG, "Активность запущена из приложения");
        }
        
        // Настраиваем обработчик нажатия на круг ассистента
        assistantCircle.setOnClickListener(v -> startVoiceRecognition());
        
        Log.d(TAG, "onCreate() завершен");
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart() вызван");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() вызван");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() вызван");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop() вызван");
    }
    
    /**
     * Инициализирует UI компоненты
     */
    private void initViews() {
        assistantCircle = findViewById(R.id.assistantCircle);
        promptText = findViewById(R.id.assistantPromptText);
        messagesRecyclerView = findViewById(R.id.assistantMessagesRecyclerView);
        
        // Настраиваем RecyclerView
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new AssistantMessageAdapter(this);
        messagesRecyclerView.setAdapter(messageAdapter);
    }
    
    /**
     * Настраивает анимации для круга ассистента
     */
    private void setupAnimations() {
        // Проверяем настройки анимации
        if (!AssistantSettings.isAnimationEnabled(this)) {
            Log.d(TAG, "Анимации отключены в настройках");
            return;
        }
        
        // Анимация пульсации
        pulseAnimator = ObjectAnimator.ofFloat(assistantCircle, "scaleX", 1f, 1.1f, 1f);
        pulseAnimator.setDuration(1500);
        pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseAnimator.setInterpolator(new LinearInterpolator());
        
        // Создаем такую же анимацию для scaleY
        ObjectAnimator pulseYAnimator = ObjectAnimator.ofFloat(assistantCircle, "scaleY", 1f, 1.1f, 1f);
        pulseYAnimator.setDuration(1500);
        pulseYAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseYAnimator.setInterpolator(new LinearInterpolator());
        
        // Анимация вращения градиента (для сложной анимации потребуется custom drawable)
        try {
            Drawable background = assistantCircle.getBackground();
            if (background instanceof GradientDrawable) {
                rotateAnimator = ValueAnimator.ofInt(0, 360);
                rotateAnimator.setDuration(10000);
                rotateAnimator.setRepeatCount(ValueAnimator.INFINITE);
                rotateAnimator.setInterpolator(new LinearInterpolator());
                rotateAnimator.addUpdateListener(animation -> {
                    // Код для вращения градиента
                    // Примечание: для правильной работы требуется кастомный drawable
                    // Упрощенная версия не будет работать с GradientDrawable
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up rotation animation", e);
        }
        
        // Запускаем анимации
        pulseAnimator.start();
        pulseYAnimator.start();
        if (rotateAnimator != null) {
            rotateAnimator.start();
        }
    }
    
    /**
     * Добавляет приветственное сообщение
     */
    private void addWelcomeMessage() {
        // Небольшая задержка для эффекта
        handler.postDelayed(() -> {
            AssistantMessage welcomeMessage = new AssistantMessage(
                    "Привет! Чем я могу помочь?", 
                    "Алан", 
                    false);
            messageAdapter.addMessage(welcomeMessage);
            messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        }, 500);
    }
    
    /**
     * Проверяет, запущена ли активность из ассистента
     */
    private boolean isLaunchedFromAssistant() {
        Intent intent = getIntent();
        // Используем вспомогательный класс для проверки
        return AssistantLauncher.isLaunchedAsAssistant(intent);
    }
    
    /**
     * Статический метод для запуска ассистента из других компонентов приложения
     */
    public static void startAssistant(Context context) {
        // Делегируем работу классу AssistantLauncher
        AssistantLauncher.launchAssistant(context);
    }
    
    /**
     * Запускает голосовое распознавание
     */
    private void startVoiceRecognition() {
        // Показываем анимацию прослушивания
        pulseAnimator.setDuration(800);
        
        // Меняем текст подсказки
        promptText.setText("Слушаю...");
        
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите что-нибудь...");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        
        try {
            startActivityForResult(intent, REQUEST_SPEECH_RECOGNITION);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка запуска распознавания речи", e);
            
            // Показываем сообщение об ошибке
            AssistantMessage errorMessage = new AssistantMessage(
                    "Не удалось запустить распознавание речи. Пожалуйста, проверьте настройки.", 
                    "Ошибка", 
                    false);
            messageAdapter.addMessage(errorMessage);
            messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
            
            // Возвращаем анимацию и текст в исходное состояние
            resetUIState();
        }
    }
    
    /**
     * Возвращает UI в исходное состояние
     */
    private void resetUIState() {
        pulseAnimator.setDuration(1500);
        promptText.setText("Нажмите, чтобы говорить");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_SPEECH_RECOGNITION) {
            // Возвращаем UI в исходное состояние в любом случае
            resetUIState();
            
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) {
                    String recognizedText = results.get(0);
                    Log.d(TAG, "Распознанный текст: " + recognizedText);
                    
                    // Добавляем сообщение пользователя
                    AssistantMessage userMessage = new AssistantMessage(
                            recognizedText, 
                            "Вы", 
                            true);
                    messageAdapter.addMessage(userMessage);
                    messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
                    
                    // Небольшая задержка перед "ответом" ассистента
                    handler.postDelayed(() -> {
                        // В реальном приложении здесь будет обработка запроса к API
                        String response = processUserRequest(recognizedText);
                        
                        // Добавляем ответ ассистента
                        AssistantMessage assistantResponse = new AssistantMessage(
                                response, 
                                "Алан", 
                                false);
                        messageAdapter.addMessage(assistantResponse);
                        messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
                    }, 800);
                }
            }
        }
    }
    
    /**
     * Обрабатывает запрос пользователя и возвращает ответ
     * В реальном приложении здесь будет запрос к AI API
     */
    private String processUserRequest(String request) {
        // Простая заглушка для демонстрации
        request = request.toLowerCase();
        
        if (request.contains("привет") || request.contains("здравствуй")) {
            return "Привет! Чем я могу помочь?";
        } else if (request.contains("погода")) {
            return "Сейчас на улице хорошая погода. Температура около 20 градусов.";
        } else if (request.contains("время")) {
            return "Сейчас " + java.text.SimpleDateFormat.getTimeInstance().format(new java.util.Date());
        } else if (request.contains("спасибо")) {
            return "Всегда рад помочь!";
        } else {
            return "Я не совсем понимаю, что вы имеете в виду. Можете перефразировать?";
        }
    }

    @Override
    protected void onDestroy() {
        // Отменяем анимации
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
        if (rotateAnimator != null) {
            rotateAnimator.cancel();
        }
        
        // Отменяем автоматическое закрытие при уничтожении активности
        if (handler != null && autoDismissRunnable != null) {
            handler.removeCallbacks(autoDismissRunnable);
        }
        super.onDestroy();
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
} 