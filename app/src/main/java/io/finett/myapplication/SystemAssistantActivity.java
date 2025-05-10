package io.finett.myapplication;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.finett.myapplication.api.ApiClient;
import io.finett.myapplication.api.OpenRouterApi;
import io.finett.myapplication.util.CommandProcessor;
import io.finett.myapplication.util.VolumeManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Активность системного ассистента с прозрачным фоном и фиолетовой рамкой
 */
public class SystemAssistantActivity extends AppCompatActivity implements TextToSpeech.OnInitListener, CommandProcessor.OnCommandProcessedListener {
    private static final String TAG = "SystemAssistantActivity";
    private static final int REQUEST_SPEECH_RECOGNITION = 100;
    private static final String PREFS_NAME = "ChatAppPrefs";
    private static final String API_KEY_PREF = "api_key";
    
    private Handler handler;
    private Runnable autoDismissRunnable;
    
    private ImageView systemAssistantMic;
    private TextView systemAssistantPromptText;
    private RecyclerView messagesRecyclerView;
    private SystemAssistantMessageAdapter messageAdapter;
    private ImageView closeButton;
    
    private ObjectAnimator pulseAnimator;
    private ValueAnimator rotateAnimator;
    
    private OpenRouterApi openRouterApi;
    private String apiKey;
    
    protected TextToSpeech textToSpeech;
    protected CommandProcessor commandProcessor;
    
    // Поле для хранения SpeechRecognizer
    private SpeechRecognizer speechRecognizer;
    
    private VolumeManager volumeManager;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "onCreate() вызван");
        
        // Проверяем, запущены ли мы через системный жест свайпа
        boolean isFromSystemGesture = AssistantLauncher.isLaunchedFromSystemGesture(getIntent());
        Log.d(TAG, "Запущено через системный жест свайпа: " + isFromSystemGesture);
        
        // Проверяем, нужно ли автоматически запустить голосовой чат
        boolean shouldLaunchVoiceChat = AssistantSettings.isAutoLaunchVoiceChatEnabled(this);
        
        // Если настройка включена и активность запущена через системный жест,
        // сразу запускаем голосовой чат и завершаем эту активность
        if (shouldLaunchVoiceChat && isFromSystemGesture) {
            Log.d(TAG, "Автоматический запуск голосового чата");
            startVoiceChatActivity();
            finish();
            return;
        }
        
        // Настраиваем окно - полностью прозрачное с фиолетовой рамкой
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        // Настройка прозрачного окна, но с сохранением возможности обработки касаний
        Window window = getWindow();
        
        // Очищаем проблемные флаги, которые могут блокировать касания
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        
        // Устанавливаем флаги для прозрачности без блокировки касаний
        window.setFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
        );
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        );
        
        // Убираем любые затемнения фона
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        
        // Устанавливаем полностью прозрачный фон
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        
        // Настраиваем обработку касаний через атрибуты
        WindowManager.LayoutParams params = window.getAttributes();
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        window.setAttributes(params);
        
        setContentView(R.layout.activity_system_assistant);
        
        Log.d(TAG, "setContentView выполнен");
        
        // Инициализируем API
        openRouterApi = ApiClient.getOpenRouterApi();
        
        // Получаем API ключ
        apiKey = getApiKey();
        
        // Инициализируем TextToSpeech
        textToSpeech = new TextToSpeech(this, this);
        
        // Инициализируем VolumeManager
        volumeManager = new VolumeManager(this);
        
        // Инициализируем UI компоненты
        initViews();
        
        // Настраиваем анимации для кнопки микрофона
        setupAnimations();
        
        handler = new Handler(Looper.getMainLooper());
        autoDismissRunnable = this::finish;
        
        // Устанавливаем автоматическое закрытие через заданное в настройках время
        int dismissDelayMs = AssistantSettings.getAutoDismissTimeout(this);
        handler.postDelayed(autoDismissRunnable, dismissDelayMs);
        
        // Добавляем приветственное сообщение
        addWelcomeMessage();
        
        // Увеличиваем счетчик запусков ассистента
        int launchCount = AssistantSettings.incrementLaunchCount(this);
        Log.d(TAG, "Количество запусков ассистента: " + launchCount);
        
        // Уведомляем о запуске ассистента
        AssistantMonitor.notifyAssistantStarted(this);
        
        Log.d(TAG, "onCreate() завершен");
        
        // Автоматически запускаем распознавание речи через увеличенную задержку
        // Это позволит системе завершить инициализацию
        handler.postDelayed(() -> {
            // Запускаем только если активность все еще активна
            if (!isFinishing()) {
                startDirectVoiceRecognition();
            }
        }, 1500); // Увеличиваем до 1.5 секунды
    }
    
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "TextToSpeech успешно инициализирован");
            int result = textToSpeech.setLanguage(Locale.getDefault());
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Язык не поддерживается");
            } else {
                // Инициализируем CommandProcessor после успешной инициализации TextToSpeech
                commandProcessor = new CommandProcessor(this, textToSpeech, null, this);
                
                // Произносим приветствие после инициализации
                welcomeGreeting();
            }
        } else {
            Log.e(TAG, "Ошибка инициализации TextToSpeech");
        }
    }
    
    /**
     * Добавляет приветственное сообщение
     */
    private void addWelcomeMessage() {
        // Небольшая задержка для эффекта
        handler.postDelayed(() -> {
            AssistantMessage welcomeMessage = new AssistantMessage(
                    "Привет! Чем я могу помочь? Вы можете управлять устройством с помощью голосовых команд или просто задавать вопросы.", 
                    "Qwen", 
                    false);
            messageAdapter.addMessage(welcomeMessage);
            messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
            
            // Приветственное сообщение озвучивается в onInit
        }, 500);
    }
    
    /**
     * Произносит приветствие
     */
    private void welcomeGreeting() {
        // Используем краткое приветствие
        String greeting = "Привет! Чем я могу помочь?";
        speak(greeting);
    }
    
    /**
     * Инициализирует UI компоненты
     */
    private void initViews() {
        systemAssistantMic = findViewById(R.id.systemAssistantMic);
        systemAssistantPromptText = findViewById(R.id.systemAssistantPromptText);
        messagesRecyclerView = findViewById(R.id.systemAssistantMessagesRecyclerView);
        closeButton = findViewById(R.id.systemAssistantCloseButton);
        
        // Настраиваем RecyclerView
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new SystemAssistantMessageAdapter(this);
        messagesRecyclerView.setAdapter(messageAdapter);
        
        // Настраиваем обработчик нажатия на кнопку микрофона
        systemAssistantMic.setOnClickListener(v -> startVoiceRecognition());
        
        // Настраиваем обработчик нажатия на кнопку закрытия
        closeButton.setOnClickListener(v -> finish());
        
        // Добавляем обработчик долгого нажатия на кнопку микрофона для открытия настроек
        systemAssistantMic.setOnLongClickListener(v -> {
            openAssistantSettings();
            return true;
        });
    }
    
    /**
     * Открывает настройки системного ассистента
     */
    private void openAssistantSettings() {
        try {
            // Вместо открытия отдельной активности настроек запускаем полноэкранный голосовой чат
            startVoiceChatActivity();
            Log.d(TAG, "Запущен голосовой чат вместо настроек ассистента");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при открытии голосового чата: " + e.getMessage(), e);
        }
    }
    
    /**
     * Настраивает анимации для кнопки микрофона
     */
    private void setupAnimations() {
        // Проверяем настройки анимации
        if (!AssistantSettings.isAnimationEnabled(this)) {
            Log.d(TAG, "Анимации отключены в настройках");
            return;
        }
        
        // Анимация пульсации
        pulseAnimator = ObjectAnimator.ofFloat(systemAssistantMic, "scaleX", 1f, 1.1f, 1f);
        pulseAnimator.setDuration(1500);
        pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseAnimator.setInterpolator(new LinearInterpolator());
        
        // Создаем такую же анимацию для scaleY
        ObjectAnimator pulseYAnimator = ObjectAnimator.ofFloat(systemAssistantMic, "scaleY", 1f, 1.1f, 1f);
        pulseYAnimator.setDuration(1500);
        pulseYAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseYAnimator.setInterpolator(new LinearInterpolator());
        
        // Анимация вращения градиента
        try {
            Drawable background = systemAssistantMic.getBackground();
            if (background instanceof GradientDrawable) {
                rotateAnimator = ValueAnimator.ofInt(0, 360);
                rotateAnimator.setDuration(10000);
                rotateAnimator.setRepeatCount(ValueAnimator.INFINITE);
                rotateAnimator.setInterpolator(new LinearInterpolator());
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при настройке анимации вращения", e);
        }
        
        // Запускаем анимации
        pulseAnimator.start();
        pulseYAnimator.start();
        if (rotateAnimator != null) {
            rotateAnimator.start();
        }
    }
    
    /**
     * Запускает голосовое распознавание через системную активность
     * Используется как резервный метод, если прямой метод не работает
     */
    private void startVoiceRecognition() {
        // Показываем анимацию прослушивания
        if (pulseAnimator != null) {
            pulseAnimator.setDuration(800);
        }
        
        // Меняем текст подсказки
        systemAssistantPromptText.setText("Слушаю...");
        
        // Добавляем индикатор прослушивания вместо стандартного UI Google
        addListeningIndicator();
        
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        
        // Устанавливаем язык - попробуем использовать русский или язык системы
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true);
        
        // Настраиваем параметры для лучшего распознавания
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false); // Разрешаем онлайн распознавание
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, ""); // Пустая подсказка
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        
        // Скрываем стандартный UI Google
        intent.putExtra("android.speech.extra.HIDE_UI", true);
        intent.putExtra("android.speech.extra.HIDE_PARTIAL_RESULTS", false);
        
        // Дополнительные параметры для скрытия UI на разных устройствах
        intent.putExtra("android.speech.extra.SUPPRESSED_UI", true);
        intent.putExtra("android.speech.extra.NO_UI", true);
        
        // Флаг для запуска активности как диалога, а не полноэкранной активности
        intent.putExtra("android.speech.extra.USE_DIALOG", false);
        
        // Устанавливаем специальный флаг, который может помочь скрыть стандартный UI
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            
        // Снижаем громкость для лучшего распознавания
        volumeManager.reduceVolumeForRecognition();
            
        try {
            Log.d(TAG, "Запуск активности распознавания речи...");
            startActivityForResult(intent, REQUEST_SPEECH_RECOGNITION);
            Log.d(TAG, "Активность распознавания речи запущена успешно");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка запуска распознавания речи: " + e.getMessage(), e);
            
            // Удаляем индикатор прослушивания
            removeListeningIndicator();
            
            // Показываем сообщение об ошибке "Нет сервисов распознавания речи"
            AssistantMessage errorMessage = new AssistantMessage(
                    "Не удалось запустить распознавание речи. Проверьте, установлены ли сервисы Google на вашем устройстве.", 
                    "Система", 
                    false);
            messageAdapter.addMessage(errorMessage);
            messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
            
            // Возвращаем анимацию и текст в исходное состояние
            resetUIState();
        }
    }
    
    // Таймер для анимации точек при распознавании
    private Handler typingHandler = new Handler(Looper.getMainLooper());
    private Runnable typingAnimation;
    private AssistantMessage listeningMessage;
    
    /**
     * Добавляет индикатор прослушивания в виде сообщения с точками
     */
    private void addListeningIndicator() {
        // Удаляем предыдущий индикатор, если он есть
        removeListeningIndicator();
        
        // Создаем новое сообщение для индикатора прослушивания
        listeningMessage = new AssistantMessage("...", "Слушаю", true);
        messageAdapter.addMessage(listeningMessage);
        messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        
        // Создаем анимацию точек (имитация печати)
        final String[] dots = {".  ", ".. ", "..."};
        final int[] index = {0};
        
        typingAnimation = new Runnable() {
            @Override
            public void run() {
                if (listeningMessage != null) {
                    listeningMessage.setMessage(dots[index[0]]);
                    messageAdapter.notifyDataSetChanged();
                    index[0] = (index[0] + 1) % dots.length;
                    typingHandler.postDelayed(this, 500); // Обновляем каждые 500мс
                }
            }
        };
        
        // Запускаем анимацию
        typingHandler.post(typingAnimation);
    }
    
    /**
     * Удаляет индикатор прослушивания
     */
    private void removeListeningIndicator() {
        // Останавливаем анимацию, если она запущена
        if (typingAnimation != null) {
            typingHandler.removeCallbacks(typingAnimation);
        }
        
        // Удаляем сообщение индикатора, если оно есть
        if (listeningMessage != null) {
            messageAdapter.removeMessage(listeningMessage);
            listeningMessage = null;
        }
    }
    
    /**
     * Возвращает UI в исходное состояние
     */
    private void resetUIState() {
        if (pulseAnimator != null) {
            pulseAnimator.setDuration(1500);
        }
        systemAssistantPromptText.setText("Говорите...");
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_SPEECH_RECOGNITION) {
            // Удаляем индикатор прослушивания
            removeListeningIndicator();
            
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
                    
                    // Проверяем, является ли команда специфичной для ассистента
                    if (processAssistantSpecificCommand(recognizedText)) {
                        // Не запускаем новый цикл распознавания автоматически, дождемся завершения ответа
                        // Перезапуск будет происходить только после окончания воспроизведения ответа
                    }
                    // Проверяем, является ли команда системной
                    else if (commandProcessor != null && commandProcessor.processCommand(recognizedText)) {
                        // Не запускаем новый цикл распознавания автоматически, дождемся завершения ответа
                        // Перезапуск будет происходить только после окончания воспроизведения ответа
                    } else {
                        // Если это не системная команда, отправляем запрос к API
                        sendMessageToApi(recognizedText);
                    }
                    return;
                }
            }
            
            // Если распознавание не удалось или был отменено пользователем,
            // автоматически перезапускаем распознавание после небольшой задержки
            handler.postDelayed(() -> startDirectVoiceRecognition(), 1000);
        } else if (commandProcessor != null) {
            // Передаем результат в CommandProcessor для обработки
            commandProcessor.handleActivityResult(requestCode, resultCode, data);
        }
        
        // Восстанавливаем громкость
        if (volumeManager != null) {
            volumeManager.restoreVolume();
        }
    }
    
    /**
     * Показывает сообщение об ошибке (в лог, без вывода пользователю)
     */
    private void logError(String message) {
        Log.e(TAG, message);
    }
    
    /**
     * Отправляет сообщение пользователя к API
     */
    private void sendMessageToApi(String message) {
        if (apiKey == null) {
            logError("API ключ не установлен");
            // В случае ошибки активируем прослушивание снова
            handler.postDelayed(() -> startDirectVoiceRecognition(), 1000);
            return;
        }
        
        // Показываем индикатор загрузки
        messageAdapter.setLoading(true);
        
        // Создаем запрос к API
        Map<String, Object> body = new HashMap<>();
        body.put("model", "qwen/qwen3-235b-a22b:free"); // используем Qwen 3 модель
        
        ArrayList<Map<String, Object>> messages = new ArrayList<>();
        
        // Добавляем сообщение пользователя
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", message);
        messages.add(userMessage);
        
        body.put("messages", messages);
        
        // Отправляем запрос
        openRouterApi.getChatCompletion(
                "Bearer " + apiKey,
                "Alan AI Assistant",
                "Android Chat App",
                body
        ).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                // Скрываем индикатор загрузки
                runOnUiThread(() -> messageAdapter.setLoading(false));
                
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        Map<String, Object> responseBody = response.body();
                        ArrayList<Map<String, Object>> choices = (ArrayList<Map<String, Object>>) responseBody.get("choices");
                        
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> choice = choices.get(0);
                            if (choice.containsKey("message")) {
                                Map<String, Object> messageObj = (Map<String, Object>) choice.get("message");
                                String content = (String) messageObj.get("content");
                                
                                if (content != null && !content.isEmpty()) {
                                    AssistantMessage botMessage = new AssistantMessage(content, "Qwen", false);
                                    runOnUiThread(() -> {
                                        messageAdapter.addMessage(botMessage);
                                        messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
                                        
                                        // Воспроизводим текст с помощью TextToSpeech
                                        // Прослушивание будет активировано после завершения речи
                                        speak(content);
                                    });
                                } else {
                                    logError("Не удалось получить ответ от сервера");
                                    // Перезапускаем распознавание в случае ошибки
                                    handler.postDelayed(() -> startDirectVoiceRecognition(), 1000);
                                }
                            } else {
                                logError("Неправильный формат ответа");
                                // Перезапускаем распознавание в случае ошибки
                                handler.postDelayed(() -> startDirectVoiceRecognition(), 1000);
                            }
                        } else {
                            logError("Пустой ответ от сервера");
                            // Перезапускаем распознавание в случае ошибки
                            handler.postDelayed(() -> startDirectVoiceRecognition(), 1000);
                        }
                    } catch (Exception e) {
                        logError("Ошибка при обработке ответа: " + e.getMessage());
                        // Перезапускаем распознавание в случае ошибки
                        handler.postDelayed(() -> startDirectVoiceRecognition(), 1000);
                    }
                } else {
                    logError("Ошибка сервера: " + response.code());
                    // Перезапускаем распознавание в случае ошибки
                    handler.postDelayed(() -> startDirectVoiceRecognition(), 1000);
                }
            }
            
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Скрываем индикатор загрузки
                runOnUiThread(() -> messageAdapter.setLoading(false));
                logError("Ошибка сети: " + t.getMessage());
                // Перезапускаем распознавание в случае ошибки сети
                handler.postDelayed(() -> startDirectVoiceRecognition(), 1000);
            }
        });
    }
    
    /**
     * Озвучивает текст с помощью TextToSpeech
     */
    private void speak(String text) {
        if (textToSpeech != null) {
            // Добавим слушатель для отслеживания окончания речи
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    // При начале речи ничего не делаем
                }

                @Override
                public void onDone(String utteranceId) {
                    // После завершения речи запускаем новый цикл распознавания
                    handler.post(() -> startDirectVoiceRecognition());
                }

                @Override
                public void onError(String utteranceId) {
                    // В случае ошибки также запускаем новый цикл распознавания
                    handler.post(() -> startDirectVoiceRecognition());
                }
            });
            
            // Используем HashMap для передачи параметров (включая ID высказывания)
            HashMap<String, String> params = new HashMap<>();
            String utteranceId = "SystemAssistant-" + System.currentTimeMillis();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
    }
    
    /**
     * Обработчик завершения выполнения команды
     */
    @Override
    public void onCommandProcessed(String command, String response) {
        // Добавляем ответ в список сообщений
        if (response != null && !response.isEmpty()) {
            AssistantMessage responseMessage = new AssistantMessage(response, "Система", false);
            runOnUiThread(() -> {
                messageAdapter.addMessage(responseMessage);
                messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
                
                // Озвучиваем ответ - прослушивание будет активировано после завершения речи
                speak(response);
            });
        } else {
            // Если ответа нет, активируем прослушивание снова
            handler.postDelayed(() -> startDirectVoiceRecognition(), 1000);
        }
    }
    
    /**
     * Обрабатывает специфические для системного ассистента команды
     * @param command Команда пользователя
     * @return true, если команда была обработана, false в противном случае
     */
    private boolean processAssistantSpecificCommand(String command) {
        String lowercaseCommand = command.toLowerCase();
        
        // Команда для вызова пункта управления и закрытия ассистента
        if (lowercaseCommand.contains("пункт управления") || 
            lowercaseCommand.contains("центр управления") || 
            lowercaseCommand.contains("панель управления") ||
            lowercaseCommand.contains("control center") ||
            lowercaseCommand.contains("открой шторку") ||
            lowercaseCommand.contains("открыть шторку") ||
            lowercaseCommand.contains("покажи шторку") ||
            lowercaseCommand.contains("показать шторку") ||
            lowercaseCommand.contains("быстрые настройки") ||
            lowercaseCommand.contains("настройки экрана")) {
            
            Log.d(TAG, "Вызов пункта управления и закрытие ассистента");
            
            // Короткое сообщение
            String responseText = "Открываю панель управления";
            
            // Добавляем сообщение в интерфейс
            AssistantMessage responseMessage = new AssistantMessage(responseText, "Система", false);
            messageAdapter.addMessage(responseMessage);
            messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
            
            // Для короткого сообщения сразу произносим и не ждем окончания речи
            if (textToSpeech != null) {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "control_center");
                textToSpeech.speak(responseText, TextToSpeech.QUEUE_FLUSH, params);
            }
            
            // Запускаем пункт управления (в Android можно открыть быстрые настройки)
            openControlCenter();
            
            // Закрываем ассистента с небольшой задержкой, чтобы успеть произнести сообщение
            handler.postDelayed(() -> finish(), 800);
            return true;
        }

        // Команда для запуска полного голосового чата
        if (lowercaseCommand.contains("открой голосовой чат") || 
            lowercaseCommand.contains("открыть голосовой чат") ||
            lowercaseCommand.contains("полный чат") ||
            lowercaseCommand.contains("полноэкранный режим") ||
            lowercaseCommand.contains("запусти алана") ||
            lowercaseCommand.contains("запустить алана") ||
            lowercaseCommand.contains("открой алана") ||
            lowercaseCommand.contains("open voice chat") ||
            lowercaseCommand.contains("full screen mode")) {
            
            Log.d(TAG, "Запуск голосового чата в полноэкранном режиме");
            
            // Короткое сообщение
            String responseText = "Открываю полноэкранный режим";
            
            // Добавляем сообщение в интерфейс
            AssistantMessage responseMessage = new AssistantMessage(responseText, "Система", false);
            messageAdapter.addMessage(responseMessage);
            messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
            
            // Произносим сообщение
            if (textToSpeech != null) {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "fullscreen_mode");
                textToSpeech.speak(responseText, TextToSpeech.QUEUE_FLUSH, params);
            }
            
            // Запускаем полноэкранный голосовой чат
            startVoiceChatActivity();
            
            // Закрываем системного ассистента с задержкой, чтобы успеть произнести сообщение
            handler.postDelayed(() -> finish(), 800);
            return true;
        }
        
        // Команда для открытия настроек ассистента
        if (lowercaseCommand.contains("настройки ассистента") ||
            lowercaseCommand.contains("открой настройки") ||
            lowercaseCommand.contains("открыть настройки") ||
            lowercaseCommand.contains("параметры ассистента") ||
            lowercaseCommand.contains("assistant settings") ||
            lowercaseCommand.contains("open settings")) {
            
            Log.d(TAG, "Запуск голосового чата вместо настроек ассистента");
            
            // Короткое сообщение
            String responseText = "Открываю полноэкранный режим";
            
            // Добавляем сообщение в интерфейс
            AssistantMessage responseMessage = new AssistantMessage(responseText, "Система", false);
            messageAdapter.addMessage(responseMessage);
            messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
            
            // Произносим сообщение
            if (textToSpeech != null) {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "settings");
                textToSpeech.speak(responseText, TextToSpeech.QUEUE_FLUSH, params);
            }
            
            // Открываем голосовой чат
            openAssistantSettings();
            
            // Закрываем системного ассистента с задержкой, чтобы успеть произнести сообщение
            handler.postDelayed(() -> finish(), 800);
            return true;
        }
        
        // Команда для показа справки
        if (lowercaseCommand.contains("что ты умеешь") || 
            lowercaseCommand.contains("помощь") || 
            lowercaseCommand.contains("справка") ||
            lowercaseCommand.contains("команды")) {
            
            String helpText = "Я могу выполнять различные команды:\n" +
                    "- Управлять Wi-Fi и Bluetooth\n" +
                    "- Включать и выключать местоположение\n" +
                    "- Управлять громкостью\n" +
                    "- Показывать время, дату\n" +
                    "- Открывать веб-сайты\n" +
                    "- Звонить контактам\n" +
                    "- Открыть полноэкранный режим\n" +
                    "- И многое другое\n\n" +
                    "Просто скажите, что нужно сделать!";
            
            AssistantMessage helpMessage = new AssistantMessage(helpText, "Система", false);
            messageAdapter.addMessage(helpMessage);
            messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
            speak(helpText);
            return true;
        }
        
        // Команда для закрытия ассистента
        if (lowercaseCommand.contains("закрой") || 
            lowercaseCommand.contains("закрыть") || 
            lowercaseCommand.contains("выйти") ||
            lowercaseCommand.contains("выход")) {
            
            String exitText = "До свидания!";
            // Для команды закрытия не нужно перезапускать прослушивание
            if (textToSpeech != null) {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "exit");
                textToSpeech.speak(exitText, TextToSpeech.QUEUE_FLUSH, params);
            }
            
            // Небольшая задержка перед закрытием
            handler.postDelayed(() -> finish(), 1500);
            return true;
        }
        
        return false;
    }
    
    /**
     * Открывает пункт управления (центр управления)
     */
    private void openControlCenter() {
        try {
            // Пытаемся открыть шторку уведомлений/быстрых настроек
            @SuppressWarnings("deprecation")
            Object statusBarService = getSystemService("statusbar");
            Class<?> statusBarManager = Class.forName("android.app.StatusBarManager");
            
            // Сначала пробуем метод для Android 11+ (expandSettingsPanel)
            try {
                Method expandSettingsPanel = statusBarManager.getMethod("expandSettingsPanel");
                expandSettingsPanel.invoke(statusBarService);
                Log.d(TAG, "Открыт пункт управления через expandSettingsPanel");
                return;
            } catch (Exception e) {
                Log.d(TAG, "Не удалось использовать expandSettingsPanel: " + e.getMessage());
            }
            
            // Пробуем метод для старых версий Android (expandNotificationsPanel)
            try {
                Method expandNotificationsPanel = statusBarManager.getMethod("expandNotificationsPanel");
                expandNotificationsPanel.invoke(statusBarService);
                Log.d(TAG, "Открыт пункт управления через expandNotificationsPanel");
                return;
            } catch (Exception e) {
                Log.d(TAG, "Не удалось использовать expandNotificationsPanel: " + e.getMessage());
            }
            
            // Еще один метод (collapsePanels) для более старых версий
            try {
                Method collapsePanels = statusBarManager.getMethod("collapsePanels");
                collapsePanels.invoke(statusBarService);
                Log.d(TAG, "Выполнено collapsePanels");
            } catch (Exception e) {
                Log.d(TAG, "Не удалось использовать collapsePanels: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при открытии пункта управления: " + e.getMessage(), e);
        }
    }
    
    /**
     * Запускает активность полноэкранного голосового чата
     */
    private void startVoiceChatActivity() {
        try {
            Intent intent = new Intent(this, VoiceChatActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            Log.d(TAG, "VoiceChatActivity запущена успешно");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при запуске VoiceChatActivity: " + e.getMessage(), e);
            
            // Показываем сообщение об ошибке, если VoiceChatActivity не может быть запущена
            AssistantMessage errorMessage = new AssistantMessage(
                    "Не удалось запустить голосовой чат. Пожалуйста, попробуйте позже.", 
                    "Система", 
                    false);
            messageAdapter.addMessage(errorMessage);
            messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        }
    }
    
    /**
     * Получает API ключ из настроек
     */
    private String getApiKey() {
        // Проверяем, используется ли встроенный ключ из BuildConfig
        if (BuildConfig.USE_HARDCODED_KEY) {
            return BuildConfig.DEFAULT_OPENROUTER_API_KEY;
        }
        
        // Получаем сохраненный пользовательский ключ
        String apiKey = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(API_KEY_PREF, null);
        
        // Если API ключ не установлен, используем ключ по умолчанию
        if (apiKey == null || apiKey.isEmpty()) {
            return BuildConfig.DEFAULT_OPENROUTER_API_KEY;
        }
        
        return apiKey;
    }
    
    /**
     * Статический метод для запуска активности системного ассистента
     */
    public static void startSystemAssistant(Context context) {
        if (context == null) {
            return;
        }
        
        try {
            Intent intent = new Intent(context, SystemAssistantActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
            Log.d(TAG, "SystemAssistantActivity запущена через статический метод");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при запуске SystemAssistantActivity: " + e.getMessage(), e);
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
        
        // Очищаем TextToSpeech
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        
        // Очищаем CommandProcessor
        if (commandProcessor != null) {
            commandProcessor.cleanup();
        }
        
        // Очищаем SpeechRecognizer
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        
        // Восстанавливаем громкость если она была снижена
        if (volumeManager != null && volumeManager.isVolumeReduced()) {
            volumeManager.restoreVolume();
        }
        
        super.onDestroy();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // Останавливаем прослушивание при переходе в фон
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        
        // Восстанавливаем громкость если она была снижена
        if (volumeManager != null && volumeManager.isVolumeReduced()) {
            volumeManager.restoreVolume();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Возобновляем прослушивание при возвращении в приложение, 
        // если оно было активно ранее
        if (isFinishing()) {
            return;
        }
        
        // Запускаем прослушивание с небольшой задержкой
        handler.postDelayed(() -> startDirectVoiceRecognition(), 1000);
    }

    /**
     * Запускает голосовое распознавание напрямую через SpeechRecognizer
     * Этот метод может работать лучше, чем запуск через активность
     */
    private void startDirectVoiceRecognition() {
        // Показываем анимацию прослушивания
        if (pulseAnimator != null) {
            pulseAnimator.setDuration(800);
        }
        
        // Меняем текст подсказки
        systemAssistantPromptText.setText("Слушаю...");
        
        // Проверяем доступность распознавания речи в отдельном потоке
        new Thread(() -> {
            boolean isRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(this);
            
            // Переключаемся на UI поток для обновления интерфейса
            runOnUiThread(() -> {
                if (!isRecognitionAvailable) {
                    Log.e(TAG, "Распознавание речи недоступно на данном устройстве");
                    
                    // Альтернативный метод распознавания через intent
                    startVoiceRecognition();
                    return;
                }
                
                // Проверяем разрешение на запись аудио без вывода сообщения пользователю
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "Отсутствует разрешение на запись аудио");
                        
                        // Эту ошибку нужно показать, так как нужно разрешение пользователя
                        AssistantMessage errorMessage = new AssistantMessage(
                                "Для работы распознавания речи необходимо разрешение на запись аудио. Пожалуйста, предоставьте это разрешение в настройках.", 
                                "Система", 
                                false);
                        messageAdapter.addMessage(errorMessage);
                        messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
                        resetUIState();
                        
                        // Запрашиваем разрешение
                        requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 1234);
                        return;
                    }
                }
                
                // Добавляем индикатор прослушивания
                addListeningIndicator();
                
                // Создаем SpeechRecognizer
                try {
                    // Уничтожаем старый распознаватель, если он есть
                    if (speechRecognizer != null) {
                        speechRecognizer.destroy();
                    }
                    
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                    Log.d(TAG, "SpeechRecognizer создан успешно");
                } catch (Exception e) {
                    // Тихо логируем ошибку создания распознавателя
                    Log.e(TAG, "Не удалось создать SpeechRecognizer: " + e.getMessage(), e);
                    
                    // Удаляем индикатор прослушивания
                    removeListeningIndicator();
                    
                    // Возвращаем анимацию и текст в исходное состояние
                    resetUIState();
                    
                    // Пробуем альтернативный метод
                    startVoiceRecognition();
                    return;
                }
                
                // Создаем слушатель результатов распознавания
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        Log.d(TAG, "Готов к распознаванию речи");
                    }
        
                    @Override
                    public void onBeginningOfSpeech() {
                        Log.d(TAG, "Начало речи");
                    }
        
                    @Override
                    public void onRmsChanged(float rmsdB) {
                        // Можно использовать для отображения уровня громкости
                    }
        
                    @Override
                    public void onBufferReceived(byte[] buffer) {
                        Log.d(TAG, "Получен буфер");
                    }
        
                    @Override
                    public void onEndOfSpeech() {
                        Log.d(TAG, "Конец речи");
                    }
        
                    @Override
                    public void onError(int error) {
                        String errorMessage;
                        boolean shouldRetry = true;
                        boolean showErrorMessage = false; // По умолчанию не показываем сообщения об ошибках
                        
                        switch (error) {
                            case SpeechRecognizer.ERROR_AUDIO:
                                errorMessage = "Ошибка записи аудио";
                                break;
                            case SpeechRecognizer.ERROR_CLIENT:
                                errorMessage = "Ошибка клиента";
                                break;
                            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                                errorMessage = "Недостаточно прав для распознавания речи";
                                shouldRetry = false;
                                showErrorMessage = true; // Эту ошибку показываем, так как нужны разрешения
                                break;
                            case SpeechRecognizer.ERROR_NETWORK:
                                errorMessage = "Ошибка сети";
                                // Пробуем использовать автономное распознавание при ошибке сети
                                startVoiceRecognition();
                                shouldRetry = false;
                                break;
                            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                                errorMessage = "Таймаут сети";
                                break;
                            case SpeechRecognizer.ERROR_NO_MATCH:
                                errorMessage = "Речь не распознана";
                                // При ошибке распознавания переключаемся на intent-based метод
                                startVoiceRecognition();
                                shouldRetry = false;
                                break;
                            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                                errorMessage = "Распознаватель занят";
                                handler.postDelayed(() -> startDirectVoiceRecognition(), 1000);
                                shouldRetry = false;
                                break;
                            case SpeechRecognizer.ERROR_SERVER:
                                errorMessage = "Ошибка сервера распознавания";
                                // При ошибке сервера переключаемся на intent-based метод
                                startVoiceRecognition();
                                shouldRetry = false;
                                break;
                            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                                errorMessage = "Таймаут распознавания речи";
                                break;
                            default:
                                errorMessage = "Ошибка распознавания речи (код " + error + ")";
                                break;
                        }
                        
                        // Логируем ошибку, но не показываем пользователю
                        Log.e(TAG, "Ошибка распознавания речи: " + errorMessage);
                        
                        // Удаляем индикатор прослушивания
                        removeListeningIndicator();
                        
                        // Показываем сообщение об ошибке в UI только если это критическая ошибка
                        if (showErrorMessage) {
                            AssistantMessage assistantErrorMessage = new AssistantMessage(
                                    "Ошибка распознавания речи: " + errorMessage, 
                                    "Ошибка", 
                                    false);
                            messageAdapter.addMessage(assistantErrorMessage);
                            messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
                        }
                        
                        // Возвращаем анимацию и текст в исходное состояние
                        resetUIState();
                        
                        // Восстанавливаем громкость
                        volumeManager.restoreVolume();
                        
                        // Повторяем попытку распознавания через небольшую задержку
                        if (shouldRetry) {
                            handler.postDelayed(() -> startDirectVoiceRecognition(), 2000);
                        }
                    }
        
                    @Override
                    public void onResults(Bundle results) {
                        // Удаляем индикатор прослушивания
                        removeListeningIndicator();
                        
                        // Возвращаем анимацию и текст в исходное состояние
                        resetUIState();
                        
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String recognizedText = matches.get(0);
                            Log.d(TAG, "Распознанный текст: " + recognizedText);
                            
                            // Проверяем, не пустой ли текст
                            if (recognizedText.trim().isEmpty()) {
                                Log.d(TAG, "Получен пустой текст, перезапускаем распознавание");
                                handler.postDelayed(() -> startDirectVoiceRecognition(), 500);
                                return;
                            }
                            
                            // Добавляем сообщение пользователя
                            AssistantMessage userMessage = new AssistantMessage(
                                    recognizedText, 
                                    "Вы", 
                                    true);
                            messageAdapter.addMessage(userMessage);
                            messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
                            
                            // Проверяем, является ли команда специфичной для ассистента
                            if (processAssistantSpecificCommand(recognizedText)) {
                                // Не запускаем новый цикл распознавания автоматически, дождемся завершения ответа
                            }
                            // Проверяем, является ли команда системной
                            else if (commandProcessor != null && commandProcessor.processCommand(recognizedText)) {
                                // Не запускаем новый цикл распознавания автоматически, дождемся завершения ответа
                            } else {
                                // Если это не системная команда, отправляем запрос к API
                                sendMessageToApi(recognizedText);
                            }
                        } else {
                            Log.e(TAG, "Результаты распознавания пусты");
                            handler.postDelayed(() -> startDirectVoiceRecognition(), 500);
                        }
                        
                        // Восстанавливаем громкость
                        volumeManager.restoreVolume();
                    }
        
                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String text = matches.get(0);
                            Log.d(TAG, "Промежуточный результат: " + text);
                            
                            // Обновляем текст индикатора прослушивания, чтобы показать пользователю,
                            // что его речь распознается
                            if (listeningMessage != null && !text.trim().isEmpty()) {
                                listeningMessage.setMessage("" + text);
                                messageAdapter.notifyDataSetChanged();
                            }
                        }
                    }
        
                    @Override
                    public void onEvent(int eventType, Bundle params) {
                        Log.d(TAG, "Событие распознавания: " + eventType);
                    }
                });
                
                // Настраиваем параметры распознавания
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU");
                intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                
                // Повышаем чувствительность распознавания для первого запуска
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500);
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000);
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
                
                // Начинаем распознавание
                try {
                    speechRecognizer.startListening(intent);
                    Log.d(TAG, "Распознавание запущено успешно");
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка при запуске распознавания: " + e.getMessage(), e);
                    
                    // Удаляем индикатор прослушивания
                    removeListeningIndicator();
                    
                    // Возвращаем анимацию и текст в исходное состояние
                    resetUIState();
                    
                    // Пробуем альтернативный метод
                    startVoiceRecognition();
                }
            });
        }).start();
    }
    
    /**
     * Метод для обработки результатов запроса разрешений
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 1234) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Разрешение получено, запускаем распознавание
                startDirectVoiceRecognition();
            } else {
                // Разрешение не получено, показываем сообщение
                AssistantMessage errorMessage = new AssistantMessage(
                        "Без разрешения на запись аудио голосовое управление работать не будет.", 
                        "Система", 
                        false);
                messageAdapter.addMessage(errorMessage);
                messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
            }
        }
    }

    /**
     * Преопределяем метод запуска активности, чтобы перехватить и модифицировать запуск активности распознавания речи
     */
    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        // Проверяем, является ли это запуском распознавания речи
        if (requestCode == REQUEST_SPEECH_RECOGNITION) {
            Log.d(TAG, "Перехват запуска активности распознавания речи");
            
            // Установим дополнительные параметры для скрытия UI
            Bundle extras = new Bundle();
            
            // Параметры для скрытия UI Google
            extras.putBoolean("android.speech.extra.HIDE_UI", true);
            extras.putBoolean("android.speech.extra.SUPPRESS_UI", true);
            extras.putBoolean("android.speech.extra.NO_UI", true);
            extras.putBoolean("android.speech.extra.SUPPRESS_DIALOGS", true);
            
            // Дополнительные флаги для лучшей работы
            extras.putInt("calling_package_ui_id", 0);
            extras.putString("display_string", "");
            
            // Установим флаги активности
            intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_HISTORY);
            
            // Объединяем с существующими extras
            intent.putExtras(extras);
        }
        
        // Продолжаем стандартный запуск активности
        super.startActivityForResult(intent, requestCode);
    }
} 




