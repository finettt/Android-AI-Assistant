package io.finett.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.animation.AnimatorInflater;
import android.animation.Animator;
import android.widget.Toast;
import android.location.Address;
import android.location.Location;
import android.widget.EditText;
import android.util.Log;
import android.content.SharedPreferences;
import android.widget.TextView;
import java.util.Random;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.appbar.MaterialToolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.finett.myapplication.adapter.ChatAdapter;
import io.finett.myapplication.adapter.VoiceChatAdapter;
import io.finett.myapplication.api.ApiClient;
import io.finett.myapplication.api.OpenRouterApi;
import io.finett.myapplication.databinding.ActivityVoiceChatBinding;
import io.finett.myapplication.model.ChatMessage;
import io.finett.myapplication.util.CommunicationManager;
import io.finett.myapplication.util.PromptManager;
import io.finett.myapplication.model.SystemPrompt;
import io.finett.myapplication.util.CommandProcessor;
import io.finett.myapplication.base.BaseAccessibilityActivity;
import io.finett.myapplication.util.ContextInfoProvider;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.google.gson.Gson;
import android.app.AlarmManager;
import android.app.PendingIntent;

public class VoiceChatActivity extends BaseAccessibilityActivity implements TextToSpeech.OnInitListener, RecognitionListener {
    private ActivityVoiceChatBinding binding;
    private VoiceChatAdapter chatAdapter;
    private OpenRouterApi openRouterApi;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean isListening = false;
    private boolean isSpeaking = false;
    private static final String MODEL_ID = "qwen/qwen3-235b-a22b:free";
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private String apiKey;
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int PERMISSION_LOCATION_REQUEST_CODE = 125;
    private static final int PERMISSION_BLUETOOTH_REQUEST_CODE = 127;
    private static final long SPEECH_TIMEOUT_MILLIS = 1500; // 1.5 секунды тишины для завершения
    private boolean isProcessingSpeech = false;
    private long lastVoiceTime = 0;
    
    // Добавляем поле для хранения текущего сообщения пользователя
    private ChatMessage currentUserMessage = null;
    
    // Добавляем переменную для хранения последнего частичного результата
    private String lastPartialResult = "";
    
    private final Runnable speechTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (isListening && !isProcessingSpeech && 
                    System.currentTimeMillis() - lastVoiceTime > SPEECH_TIMEOUT_MILLIS) {
                stopListening();
            }
        }
    };
    private PromptManager promptManager;
    private CommandProcessor commandProcessor;
    private RecyclerView recyclerView;
    private FloatingActionButton micButton;
    private FloatingActionButton cameraButton;
    private ContextInfoProvider contextInfoProvider;
    private static final String TAG = "VoiceChatActivity";
    private static final int PERMISSION_RECORD_AUDIO = 123;
    private static final int CAMERA_REQUEST_CODE = 124;
    private List<ChatMessage> messagesList = new ArrayList<>();
    private Animator pulsateAnimator;
    private View screenGlowEffect;
    private Animator fadeInAnimator;
    private Animator fadeOutAnimator;
    private Animator borderPulsateAnimator;
    private Handler typingHandler = new Handler(Looper.getMainLooper());
    private Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVoiceChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Release microphone resources that might be in use
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            try {
                // Create temporary recognizer to check microphone state
                SpeechRecognizer tempRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                tempRecognizer.cancel();
                tempRecognizer.destroy();
                
                // Small pause to free resources
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // Ignore
                }
            } catch (Exception e) {
                Log.e(TAG, "Error releasing microphone resources", e);
            }
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Голосовой чат");
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            finish(); // Закрываем активность при нажатии на стрелку назад
        });
        
        toolbar.inflateMenu(R.menu.voice_chat_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_manage_prompts) {
                showPromptManagementDialog();
                return true;
            }
            return false;
        });

        promptManager = new PromptManager(this);
        
        // Получаем API ключ с учетом настроек BuildConfig
        apiKey = getApiKey();

        recyclerView = findViewById(R.id.voice_chat_recycler_view);
        micButton = findViewById(R.id.voice_chat_mic_button);
        cameraButton = findViewById(R.id.voice_chat_camera_button);
        screenGlowEffect = findViewById(R.id.screenGlowEffect);
        
        // Инициализация аниматоров для эффекта свечения экрана
        fadeInAnimator = AnimatorInflater.loadAnimator(this, R.animator.fade_in);
        fadeInAnimator.setTarget(screenGlowEffect);
        
        fadeOutAnimator = AnimatorInflater.loadAnimator(this, R.animator.fade_out);
        fadeOutAnimator.setTarget(screenGlowEffect);
        
        borderPulsateAnimator = AnimatorInflater.loadAnimator(this, R.animator.border_pulsate);
        borderPulsateAnimator.setTarget(screenGlowEffect);

        setupRecyclerView();
        setupMicButton();
        setupCameraButton();
        setupApi();
        setupContextInfo();
        checkPermissionAndInitRecognizer();
        initTextToSpeech();

        commandProcessor = new CommandProcessor(
            this, 
            textToSpeech, 
            result -> {
                // Обработка результатов анализа изображения
                ChatMessage botMessage = new ChatMessage(result, false);
                runOnUiThread(() -> {
                    chatAdapter.addMessage(botMessage);
                    recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                });
            },
            // Обработка системных команд
            (command, response) -> {
                ChatMessage userMessage = new ChatMessage(command, true);
                ChatMessage botMessage = new ChatMessage(response, false);
                runOnUiThread(() -> {
                    chatAdapter.addMessage(userMessage);
                    chatAdapter.addMessage(botMessage);
                    recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                    speakText(response);
                });
            }
        );
        
        // Инициализация аниматора для кнопки микрофона
        pulsateAnimator = AnimatorInflater.loadAnimator(this, R.animator.pulsate);
        pulsateAnimator.setTarget(micButton);
        
        // Проверяем, была ли активность запущена по ключевой фразе
        handleIntent(getIntent());
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Обрабатываем новый интент, если активность уже запущена
        handleIntent(intent);
    }
    
    private void handleIntent(Intent intent) {
        if (intent != null) {
            // Start listening automatically based on preferences
            SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
            boolean autoListen = prefs.getBoolean("voice_chat_auto_listen", true);
            if (autoListen) {
                startListening();
            }
        }
    }

    private void setupContextInfo() {
        contextInfoProvider = new ContextInfoProvider(this);
        contextInfoProvider.setOnContextInfoUpdatedListener(new ContextInfoProvider.OnContextInfoUpdatedListener() {
            @Override
            public void onLocationUpdated(Location location, Address address) {
                // Локация обновлена, можно обновить интерфейс или уведомить пользователя
                Log.d("VoiceChatActivity", "Location updated: " + address.getLocality());
            }
        });
        
        // Пробуем узнать имя пользователя
        String userName = contextInfoProvider.getUserName();
        
        // Проверяем API ключ
        if ((apiKey == null || apiKey.isEmpty()) && !BuildConfig.USE_HARDCODED_KEY) {
            // Если нет API ключа и не используем хардкодный ключ, запрашиваем ключ
            showApiKeyDialog();
        } else if (BuildConfig.USE_HARDCODED_KEY || !(apiKey == null || apiKey.isEmpty())) {
            // Показываем приветственное сообщение с именем пользователя
            ChatMessage welcomeMessage = new ChatMessage(
                    "Привет, " + userName + "! Я - ваш голосовой помощник. Чем могу помочь?", false);
            chatAdapter.addMessage(welcomeMessage);
            speakText(welcomeMessage.getText());
        }
    }

    /**
     * Получение API ключа с учетом настроек сборки
     */
    private String getApiKey() {
        // Проверяем флаг, использовать ли хардкод ключ из BuildConfig
        if (BuildConfig.USE_HARDCODED_KEY) {
            Log.d(TAG, "Используется встроенный API ключ OpenRouter из BuildConfig");
            return BuildConfig.DEFAULT_OPENROUTER_API_KEY;
        }
        
        // Сначала проверяем новое хранилище для API ключа
        String apiKey = getSharedPreferences("ApiPrefs", MODE_PRIVATE)
                .getString("api_key", null);
        
        // Если ключ не найден, пробуем старое хранилище
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                    .getString(MainActivity.API_KEY_PREF, null);
        }
        
        // Если API ключ не установлен, используем ключ по умолчанию
        if (apiKey == null || apiKey.isEmpty()) {
            Log.w(TAG, "Пользовательский ключ не установлен, используется встроенный API ключ OpenRouter из BuildConfig");
            return BuildConfig.DEFAULT_OPENROUTER_API_KEY;
        }
        
        return apiKey;
    }

    private void showApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Введите ваш API ключ");
        builder.setMessage("Для работы с OpenRouter API необходим ключ. Вы можете получить его на сайте openrouter.ai");

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        if (apiKey != null && !apiKey.isEmpty()) {
            input.setText(apiKey);
        }
        builder.setView(input);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String key = input.getText().toString().trim();
            if (!key.isEmpty()) {
                // Сохраняем API ключ только если не используем хардкодный ключ из BuildConfig
                if (!BuildConfig.USE_HARDCODED_KEY) {
                    SharedPreferences prefs = getSharedPreferences("ApiPrefs", MODE_PRIVATE);
                    prefs.edit().putString("api_key", key).apply();
                }
                apiKey = key;
                
                // Добавляем приветственное сообщение с именем пользователя
                String userName = contextInfoProvider.getUserName();
                ChatMessage welcomeMessage = new ChatMessage(
                        "Привет, " + userName + "! Я - ваш голосовой помощник. Чем могу помочь?", false);
                chatAdapter.addMessage(welcomeMessage);
                speakText(welcomeMessage.getText());
            }
        });
        
        builder.setCancelable(false);
        builder.show();
    }

    private void checkPermissionAndInitRecognizer() {
        // Всегда инициализируем распознаватель речи, даже если нет разрешения
        Log.d("VoiceChatActivity", "Initializing speech recognizer regardless of permissions");
        initSpeechRecognizer();
        
        // Проверим, есть ли разрешение, но не будем его запрашивать
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("VoiceChatActivity", "RECORD_AUDIO permission not granted");
        } else {
            Log.d("VoiceChatActivity", "RECORD_AUDIO permission already granted");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        // Для всех запросов разрешений пытаемся продолжить работу,
        // даже если разрешение не было предоставлено
        if (requestCode == PERMISSION_REQUEST_CODE) {
            Log.d("VoiceChatActivity", "Microphone permission result received");
            // Пытаемся инициализировать распознавание в любом случае
            initSpeechRecognizer();
        } else if (requestCode == PERMISSION_LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Разрешение на локацию получено, обновляем информацию о местоположении
                contextInfoProvider.updateLocationInfo();
            }
        } else if (requestCode == PERMISSION_BLUETOOTH_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Разрешение на Bluetooth получено
                ChatMessage botMessage = new ChatMessage("Разрешение на использование Bluetooth получено", false);
                chatAdapter.addMessage(botMessage);
                speakText(botMessage.getText());
            } else {
                ChatMessage botMessage = new ChatMessage("Для работы с Bluetooth необходимо разрешение", false);
                chatAdapter.addMessage(botMessage);
                speakText(botMessage.getText());
            }
        } else if (CommunicationManager.handlePermissionResult(requestCode, permissions, grantResults)) {
            // Разрешение для звонка или SMS получено
            Toast.makeText(this, "Разрешение получено", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupRecyclerView() {
        chatAdapter = new VoiceChatAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(chatAdapter);
        
        // Показываем подсказку, если нет сообщений
        showEmptyState();
    }

    private void setupMicButton() {
        micButton.setOnClickListener(v -> {
            if (!isListening) {
                checkPermissionAndInitRecognizer();
                startListening();
                hideEmptyState();
            } else {
                stopListening();
            }
        });
    }

    private void setupCameraButton() {
        cameraButton.setOnClickListener(v -> startCameraActivity());
        
        // Добавим обработку кнопки назад
        FloatingActionButton backButton = findViewById(R.id.voice_chat_back_button);
        backButton.setOnClickListener(v -> finish());
    }

    private void setupApi() {
        openRouterApi = ApiClient.getOpenRouterClient().create(OpenRouterApi.class);
    }

    private void initSpeechRecognizer() {
        // Сначала уничтожаем старый распознаватель, если он существует
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
                speechRecognizer.destroy();
                speechRecognizer = null;
            } catch (Exception e) {
                Log.e(TAG, "Error destroying old speech recognizer", e);
            }
        }
        
        // Небольшая пауза для освобождения ресурсов
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            // Игнорируем
        }
        
        // Проверяем доступность распознавания речи
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                speechRecognizer.setRecognitionListener(this);
                Log.d(TAG, "Speech recognizer initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error creating speech recognizer", e);
                speechRecognizer = null;
                showError("Ошибка инициализации распознавания речи");
            }
        } else {
            Log.e(TAG, "Speech recognition is not available on this device");
            Toast.makeText(this, "Распознавание речи недоступно на этом устройстве", Toast.LENGTH_SHORT).show();
        }
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(new Locale("ru"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                showError("Русский язык не поддерживается для синтеза речи");
            }
            
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    isSpeaking = true;
                }

                @Override
                public void onDone(String utteranceId) {
                    isSpeaking = false;
                    runOnUiThread(() -> startListening());
                }

                @Override
                public void onError(String utteranceId) {
                    isSpeaking = false;
                    runOnUiThread(() -> startListening());
                }
            });
            
            // Устанавливаем специальный промпт для голосового помощника
            SystemPrompt assistantPrompt = new SystemPrompt(
                getSystemPrompt(),
                "Голосовой помощник Robo"
            );
            promptManager.setActivePrompt(assistantPrompt);
        } else {
            showError("Ошибка инициализации синтеза речи");
        }
    }

    private String getSystemPrompt() {
        String defaultPrompt = 
                "Ты русскоязычный голосовой помощник Алан. Ты общаешься с пользователем через голосовой интерфейс.\n" +
                "Ответы должны быть короткими, дружелюбными и на русском языке.\n" +
                "Используй простой разговорный язык.\n" +
                "Не используй эмодзи или сложные термины.\n" +
                "Если у тебя есть контекстная информация о пользователе (имя, время суток, местоположение), используй её в своих ответах. (но не злоупотребляй этим)\n" +
                "Обращайся к пользователю по имени, если оно известно.\n\n" +
                
                "Ты можешь выполнить следующие команды:\n" +
                "1. Управление системой:\n" +
                "   - Включить/выключить Wi-Fi, Bluetooth, геолокацию\n" +
                "   - Регулировать громкость\n" +
                "   - Открыть системные настройки\n\n" +
                
                "2. Контекстная информация:\n" +
                "   - Сообщить текущее время и дату\n" +
                "   - Сообщить местоположение пользователя\n\n" +
                
                "3. Использование камеры:\n" +
                "   - Анализировать изображение с камеры\n\n" +
                
                "4. Календарь и планирование:\n" +
                "   - Проверять события в календаре (сегодня, завтра, в определенную дату)\n" +
                "   - Открывать календарь\n" +
                "   - Создавать новые события\n\n" +
                
                "5. Карты и навигация:\n" +
                "   - Показывать места на карте\n" +
                "   - Прокладывать маршруты\n" +
                "   - Искать места поблизости\n" +
                "   - Находить информацию о местоположении\n\n" +
                
                "Представляйся как 'Алан' или 'голосовой ассистент Алан'. Твоя основная цель - максимально помочь пользователю.";
        
        // Load from settings if available
        SharedPreferences sharedPreferences = getSharedPreferences("app_settings", MODE_PRIVATE);
        return sharedPreferences.getString("system_prompt", defaultPrompt);
    }

    private void startListening() {
        if (isListening) return;
        
        try {
            // Если распознаватель не инициализирован, пробуем инициализировать его
            if (speechRecognizer == null) {
                Log.d(TAG, "Speech recognizer is null, reinitializing");
                initSpeechRecognizer();
                
                // Если инициализация не удалась, выходим
                if (speechRecognizer == null) {
                    Log.e(TAG, "Failed to initialize speech recognizer");
                    showError("Не удалось инициализировать распознавание речи");
                    
                    // Пытаемся еще раз через небольшую паузу
                    new Handler(Looper.getMainLooper()).postDelayed(this::initSpeechRecognizer, 500);
                    new Handler(Looper.getMainLooper()).postDelayed(this::startListening, 1000);
                    return;
                }
            }
            
            // Создаем и настраиваем интент для распознавания речи
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            
            // Увеличиваем таймаут для лучшей работы
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4000);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
            
            // Сначала отменяем любое текущее распознавание для освобождения ресурсов
            try {
                speechRecognizer.cancel();
            } catch (Exception e) {
                Log.e(TAG, "Error canceling before start", e);
            }
            
            // Очищаем последний частичный результат перед новым распознаванием
            lastPartialResult = "";
            
            // Небольшая пауза для стабильности
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Log.d(TAG, "Starting speech recognition");
                    speechRecognizer.startListening(intent);
                    isListening = true;
                    isProcessingSpeech = false;
                    lastVoiceTime = System.currentTimeMillis();
                    micButton.setImageResource(R.drawable.ic_stop);
                    
                    // Добавляем пустое сообщение пользователя в чат в начале распознавания
                    if (currentUserMessage == null) {
                        currentUserMessage = new ChatMessage("", true);
                        chatAdapter.addMessage(currentUserMessage);
                    }
                    
                    // Запускаем таймер для проверки тишины
                    micButton.postDelayed(speechTimeoutRunnable, SPEECH_TIMEOUT_MILLIS);
                    
                    // Запускаем анимацию микрофона
                    if (!pulsateAnimator.isRunning()) {
                        pulsateAnimator.start();
                        
                        // Показываем эффект свечения экрана
                        screenGlowEffect.setVisibility(View.VISIBLE);
                        fadeInAnimator.start();
                        borderPulsateAnimator.start();
                    }
                    
                    // Дополнительная проверка, что прослушивание действительно запущено
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!isListening) {
                            Log.w(TAG, "Listening didn't start properly, retrying");
                            initSpeechRecognizer();
                            startListening();
                        }
                    }, 1500);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error starting speech recognition in delayed handler", e);
                    isListening = false;
                    showError("Не удалось запустить распознавание речи");
                    micButton.setImageResource(R.drawable.ic_mic);
                    
                    // Удаляем пустое сообщение при ошибке
                    if (currentUserMessage != null && (currentUserMessage.getText() == null || currentUserMessage.getText().isEmpty())) {
                        chatAdapter.removeMessage(currentUserMessage);
                        currentUserMessage = null;
                    }
                    
                    // Пытаемся перезапустить распознаватель
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        // Пробуем полностью заново создать распознаватель
                        if (speechRecognizer != null) {
                            speechRecognizer.destroy();
                            speechRecognizer = null;
                        }
                        initSpeechRecognizer();
                        startListening();
                    }, 1500);
                }
            }, 100);
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting speech recognition", e);
            showError("Не удалось запустить распознавание речи");
            isListening = false;
            micButton.setImageResource(R.drawable.ic_mic);
            
            // Удаляем пустое сообщение при ошибке
            if (currentUserMessage != null && (currentUserMessage.getText() == null || currentUserMessage.getText().isEmpty())) {
                chatAdapter.removeMessage(currentUserMessage);
                currentUserMessage = null;
            }
            
            // Пытаемся перезапустить с задержкой
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                initSpeechRecognizer();
                startListening();
            }, 1500);
        }
    }

    private void stopListening() {
        if (!isListening) return;
        
        // Останавливаем анимацию пульсации при остановке микрофона
        if (pulsateAnimator.isRunning()) {
            pulsateAnimator.cancel();
            micButton.setScaleX(1.0f);
            micButton.setScaleY(1.0f);
            micButton.setAlpha(1.0f);
            
            // Останавливаем анимацию пульсации границ
            if (borderPulsateAnimator.isRunning()) {
                borderPulsateAnimator.cancel();
            }
            
            // Скрываем эффект свечения по краям экрана с анимацией
            if (screenGlowEffect.getVisibility() == View.VISIBLE) {
                fadeOutAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        screenGlowEffect.setVisibility(View.GONE);
                        fadeOutAnimator.removeListener(this);
                    }
                });
                fadeOutAnimator.start();
            }
        }
        
        micButton.removeCallbacks(speechTimeoutRunnable);
        speechRecognizer.stopListening();
        isListening = false;
        isProcessingSpeech = false;
        micButton.setImageResource(R.drawable.ic_mic);
    }

    private void speakText(String text) {
        if (textToSpeech != null && !isSpeaking) {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "messageId");
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
    }

    private void sendMessageToAPI(String message) {
        // Обновляем apiKey перед отправкой, чтобы учесть возможное изменение настроек
        apiKey = getApiKey();
        
        if (apiKey == null) {
            showError("API ключ не установлен");
            showApiKeyDialog();
            return;
        }
        
        // Обратите внимание, что мы уже имеем сообщение пользователя в интерфейсе
        // и не добавляем его повторно
        
        chatAdapter.setLoading(true);
        recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        
        // Добавляем сообщение в историю, если текущее сообщение не задано,
        // создаем новое (это может произойти при прямом вызове sendMessageToAPI)
        ChatMessage userMessage;
        if (currentUserMessage == null) {
            userMessage = new ChatMessage(message, true);
            messagesList.add(userMessage);
        } else {
            // Используем существующее сообщение
            userMessage = currentUserMessage;
            messagesList.add(userMessage);
            // Сбрасываем ссылку на текущее сообщение, так как мы уже отправили его
            currentUserMessage = null;
        }
        
        // Если запрос похож на команду, проверяем через CommandProcessor
        if (commandProcessor.processCommand(message)) {
            chatAdapter.setLoading(false);
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", MODEL_ID);
        
        ArrayList<Map<String, Object>> messages = new ArrayList<>();
        
        // Добавляем системный промпт
        SystemPrompt activePrompt = promptManager.getActivePrompt();
        if (activePrompt != null) {
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", activePrompt.getText());
            messages.add(systemMessage);
        }
        
        // Add previous messages for context (up to last 10 messages)
        int historySize = messagesList.size();
        int startIdx = Math.max(0, historySize - 10);
        for (int i = startIdx; i < historySize - 1; i++) {
            ChatMessage historyMessage = messagesList.get(i);
            Map<String, Object> historyMessageMap = new HashMap<>();
            historyMessageMap.put("role", historyMessage.isUserMessage() ? "user" : "assistant");
            historyMessageMap.put("content", historyMessage.getText());
            messages.add(historyMessageMap);
        }
        
        // Добавляем сообщение пользователя с контекстной информацией
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("role", "user");
        
        // Получаем контекстную информацию
        String time = contextInfoProvider.getFormattedTime();
        String location = contextInfoProvider.getFormattedLocation();
        String userName = contextInfoProvider.getUserName();
        
        // Форматируем сообщение с добавлением контекстной информации после запроса
        StringBuilder enhancedMessage = new StringBuilder(message);
        enhancedMessage.append("\n\nКонтекстная информация:");
        enhancedMessage.append("\nВремя: ").append(time);
        enhancedMessage.append("\nМестоположение: ").append(location);
        if (!userName.isEmpty()) {
            enhancedMessage.append("\nИмя пользователя: ").append(userName);
        }
        
        messageMap.put("content", enhancedMessage.toString());
        messages.add(messageMap);
        
        body.put("messages", messages);
        body.put("max_tokens", 1000);
        body.put("temperature", 0.7);
        body.put("route", "fallback");
        
        // Добавляем API ключ
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("HTTP-Referer", "https://robo-assistant.app");
        headers.put("Content-Type", "application/json");
        
        // Отправка запроса в отдельном потоке
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(API_URL).openConnection();
                connection.setRequestMethod("POST");
                
                // Устанавливаем заголовки
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
                
                connection.setDoOutput(true);
                
                // Преобразуем Map в JSON
                String jsonBody = mapToJson(body);
                Log.d(TAG, "Request JSON: " + jsonBody);
                
                // Отправляем тело запроса
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                // Получаем ответ
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Читаем ответ
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                    }
                    
                    Log.d(TAG, "Response: " + response.toString());
                    
                    // Парсим JSON ответ
                    String messageContent = parseResponse(response.toString());
                    
                    // Добавляем сообщение от бота в UI
                    runOnUiThread(() -> {
                        chatAdapter.setLoading(false);
                        ChatMessage botMessage = new ChatMessage(messageContent, false);
                        chatAdapter.addMessage(botMessage);
                        // Add to message history
                        messagesList.add(botMessage);
                        speakText(messageContent);
                    });
                    
                } else {
                    // Читаем тело ошибки
                    StringBuilder errorResponse = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            errorResponse.append(responseLine.trim());
                        }
                    }
                    
                    Log.e(TAG, "Error response (" + responseCode + "): " + errorResponse.toString());
                    
                    // Обработка ошибки
                    runOnUiThread(() -> {
                        chatAdapter.setLoading(false);
                        ChatMessage errorMessage = new ChatMessage(
                                "Произошла ошибка при отправке запроса (код " + responseCode + "): " + errorResponse.toString(), false);
                        chatAdapter.addMessage(errorMessage);
                    });
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Exception: " + e.getMessage());
                runOnUiThread(() -> {
                    chatAdapter.setLoading(false);
                    ChatMessage errorMessage = new ChatMessage(
                            "Ошибка: " + e.getMessage(), false);
                    chatAdapter.addMessage(errorMessage);
                });
            }
        }).start();
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            View rootView = findViewById(android.R.id.content);
            if (rootView != null) {
                // Используем Snackbar вместо Toast для более ненавязчивых уведомлений
                com.google.android.material.snackbar.Snackbar.make(
                    rootView,
                    message,
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show();
            }
        });
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        Log.d("VoiceChatActivity", "Ready for speech");
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.d("VoiceChatActivity", "Beginning of speech detected");
        
        // Убедимся, что пустое сообщение добавлено, когда пользователь начал говорить
        if (currentUserMessage == null) {
            currentUserMessage = new ChatMessage("", true);
            chatAdapter.addMessage(currentUserMessage);
        }
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        if (rmsdB > 1.0f) { // Есть звук
            isProcessingSpeech = true;
            lastVoiceTime = System.currentTimeMillis();
            
            // Запускаем анимацию пульсации при активном голосе
            if (!pulsateAnimator.isRunning()) {
                micButton.setBackgroundResource(R.drawable.mic_button_normal);
                pulsateAnimator.start();
                
                // Показываем эффект свечения экрана
                screenGlowEffect.setVisibility(View.VISIBLE);
                fadeInAnimator.start();
                borderPulsateAnimator.start();
            }
        } else { // Тишина
            isProcessingSpeech = false;
            
            // Останавливаем анимацию при тишине
            if (pulsateAnimator.isRunning()) {
                pulsateAnimator.cancel();
                // Возвращаем нормальный размер кнопке
                micButton.setScaleX(1.0f);
                micButton.setScaleY(1.0f);
                micButton.setAlpha(1.0f);
                // Возвращаем обычный фон
                micButton.setBackgroundResource(R.drawable.mic_button_normal);
                
                // Останавливаем анимацию пульсации границ
                if (borderPulsateAnimator.isRunning()) {
                    borderPulsateAnimator.cancel();
                }
                
                // Скрываем эффект свечения по краям экрана с анимацией
                if (screenGlowEffect.getVisibility() == View.VISIBLE) {
                    fadeOutAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            screenGlowEffect.setVisibility(View.GONE);
                            fadeOutAnimator.removeListener(this);
                        }
                    });
                    fadeOutAnimator.start();
                }
            }
            
            micButton.removeCallbacks(speechTimeoutRunnable);
            micButton.postDelayed(speechTimeoutRunnable, SPEECH_TIMEOUT_MILLIS);
        }
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.d("VoiceChatActivity", "Buffer received: " + buffer.length + " bytes");
    }

    @Override
    public void onEndOfSpeech() {
        Log.d("VoiceChatActivity", "End of speech detected");
        stopListening();
    }

    @Override
    public void onError(int error) {
        String errorMessage;
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                errorMessage = "Ошибка записи аудио";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                errorMessage = "Ошибка клиента";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                errorMessage = "Недостаточно прав";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                errorMessage = "Ошибка сети";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                errorMessage = "Таймаут сети";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                errorMessage = "Не распознано";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                errorMessage = "Распознаватель занят";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                errorMessage = "Ошибка сервера";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                errorMessage = "Таймаут распознавания";
                break;
            default:
                errorMessage = "Ошибка распознавания";
                break;
        }
        
        Log.e("VoiceChatActivity", "Speech recognition error: " + errorMessage + " (code " + error + ")");
        
        // Обычная обработка ошибок для режима слушания команд
        if (error == SpeechRecognizer.ERROR_NO_MATCH || 
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            // Игнорируем эти ошибки, так как они часто возникают при нормальной работе
            stopListening();
            
            // Удаляем пустое сообщение, если произошла ошибка распознавания
            if (currentUserMessage != null && (currentUserMessage.getText() == null || currentUserMessage.getText().isEmpty())) {
                chatAdapter.removeMessage(currentUserMessage);
                currentUserMessage = null;
            }
            
            return;
        }

        final String finalErrorMessage = errorMessage;
        runOnUiThread(() -> {
            micButton.removeCallbacks(speechTimeoutRunnable);
            if (error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                // Убираем Toast, используем метод showError для менее навязчивых уведомлений
                // только для серьезных ошибок
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || 
                    error == SpeechRecognizer.ERROR_NETWORK) {
                    showError(finalErrorMessage);
                    
                    // Удаляем пустое сообщение при серьезной ошибке
                    if (currentUserMessage != null && (currentUserMessage.getText() == null || currentUserMessage.getText().isEmpty())) {
                        chatAdapter.removeMessage(currentUserMessage);
                        currentUserMessage = null;
                    }
                }
            }
            stopListening();
        });
    }

    @Override
    public void onResults(Bundle results) {
        List<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        
        // Если результаты пустые или отсутствуют, но есть последний частичный результат, используем его
        if ((matches == null || matches.isEmpty() || (matches.get(0) != null && matches.get(0).isEmpty())) 
                && !lastPartialResult.isEmpty()) {
            Log.d(TAG, "Using last partial result as final: '" + lastPartialResult + "'");
            matches = new ArrayList<>();
            matches.add(lastPartialResult);
        } else if (matches != null && !matches.isEmpty()) {
            Log.d(TAG, "Recognized final text: '" + matches.get(0) + "'");
        } else {
            Log.d(TAG, "No speech recognized or empty results");
            
            // Удаляем пустое сообщение, если ничего не распознано
            if (currentUserMessage != null && (currentUserMessage.getText() == null || currentUserMessage.getText().isEmpty())) {
                chatAdapter.removeMessage(currentUserMessage);
                currentUserMessage = null;
            }
            return;
        }
        
        // Очищаем последний частичный результат
        String text = matches.get(0);
        lastPartialResult = "";
        
        // Обновляем текст текущего сообщения пользователя с финальным результатом
        updateTranscriptionText(text);
        
        // Логируем полученный текст
        Log.d(TAG, "Processing recognized text: '" + text + "'");
        
        // Проверяем команду на открытие приложения
        String normalizedText = text.toLowerCase();
        if (normalizedText.startsWith("открой") || 
            normalizedText.startsWith("запусти") || 
            normalizedText.startsWith("открыть") || 
            normalizedText.startsWith("запустить")) {
            
            // Обрабатываем как команду для запуска приложения
            if (commandProcessor.processCommand(text)) {
                // Сбрасываем текущее сообщение пользователя
                currentUserMessage = null;
                return;
            }
        }
        
        // Обрабатываем сначала как команду
        if (!commandProcessor.processCommand(text)) {
            // Если это не команда, отправляем в API
            sendMessageToAPI(text);
        }
        
        // Сбрасываем текущее сообщение пользователя
        currentUserMessage = null;
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        List<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String text = matches.get(0);
            Log.d(TAG, "Partial result: '" + text + "'");
            
            // Сохраняем последний частичный результат
            if (text != null && !text.isEmpty()) {
                lastPartialResult = text;
            }
            
            // Обновляем текст текущего сообщения пользователя
            updateTranscriptionText(text);
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {}

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called");
        
        // Release resources
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // Stop animations when activity is paused
        if (pulsateAnimator.isRunning()) {
            pulsateAnimator.cancel();
            micButton.setScaleX(1.0f);
            micButton.setScaleY(1.0f);
            micButton.setAlpha(1.0f);
            // Restore normal background
            micButton.setBackgroundResource(R.drawable.mic_button_normal);
            
            // Stop border pulsation animation
            if (borderPulsateAnimator.isRunning()) {
                borderPulsateAnimator.cancel();
            }
            
            // Hide glow effect immediately without animation on exit
            screenGlowEffect.setVisibility(View.GONE);
        }
        
        // Clear all typing animations
        typingHandler.removeCallbacksAndMessages(null);
        
        // Reset current user message when leaving activity
        currentUserMessage = null;
        
        micButton.removeCallbacks(speechTimeoutRunnable);
        stopListening();
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Обновляем контекстную информацию при возвращении к активности
        if (contextInfoProvider != null) {
            contextInfoProvider.updateLocationInfo();
        }
    }

    private void showPromptManagementDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Управление промптами");

        List<SystemPrompt> prompts = promptManager.getPrompts();
        String[] promptDescriptions = new String[prompts.size()];
        boolean[] checkedItems = new boolean[prompts.size()];
        
        for (int i = 0; i < prompts.size(); i++) {
            promptDescriptions[i] = prompts.get(i).getDescription();
            checkedItems[i] = prompts.get(i).isActive();
        }

        builder.setMultiChoiceItems(promptDescriptions, checkedItems, (dialog, which, isChecked) -> {
            for (int i = 0; i < checkedItems.length; i++) {
                checkedItems[i] = (i == which && isChecked);
            }
            if (isChecked) {
                promptManager.setActivePrompt(prompts.get(which));
            }
        });

        builder.setPositiveButton("Добавить", (dialog, which) -> showPromptEditDialog(null));
        builder.setNeutralButton("Редактировать", (dialog, which) -> {
            for (int i = 0; i < checkedItems.length; i++) {
                if (checkedItems[i]) {
                    showPromptEditDialog(prompts.get(i));
                    break;
                }
            }
        });
        builder.setNegativeButton("Удалить", (dialog, which) -> {
            for (int i = 0; i < checkedItems.length; i++) {
                if (checkedItems[i]) {
                    promptManager.deletePrompt(prompts.get(i));
                    break;
                }
            }
        });

        builder.show();
    }

    private void showPromptEditDialog(SystemPrompt prompt) {
        View view = getLayoutInflater().inflate(R.layout.dialog_prompt_edit, null);
        TextInputEditText descriptionInput = view.findViewById(R.id.promptDescriptionInput);
        TextInputEditText contentInput = view.findViewById(R.id.promptContentInput);

        if (prompt != null) {
            descriptionInput.setText(prompt.getDescription());
            contentInput.setText(prompt.getText());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(prompt == null ? "Новый промпт" : "Редактирование промпта");
        builder.setView(view);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String description = descriptionInput.getText().toString();
            String content = contentInput.getText().toString();

            if (description.isEmpty() || content.isEmpty()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show();
                return;
            }

            if (prompt == null) {
                SystemPrompt newPrompt = new SystemPrompt(content, description);
                promptManager.savePrompt(newPrompt);
            } else {
                prompt.setDescription(description);
                prompt.setText(content);
                promptManager.savePrompt(prompt);
            }
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Скрываем индикатор загрузки
        chatAdapter.setLoading(false);
        // Проверяем запрос разрешений на Bluetooth
        if (requestCode == 101 && resultCode == RESULT_OK) {
            // Bluetooth был включен успешно
            speakText("Bluetooth успешно включен");
        }
        commandProcessor.handleActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onAccessibilitySettingsApplied() {
        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
    }

    private void startCameraActivity() {
        // Добавляем системное сообщение
        ChatMessage systemMessage = new ChatMessage("Открываю камеру для анализа изображения...", false);
        chatAdapter.addMessage(systemMessage);
        chatAdapter.setLoading(true);
        recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        
        // Запускаем камеру
        commandProcessor.startCameraActivity();
    }

    private void addMessage(String message, boolean isUser) {
        ChatMessage chatMessage = new ChatMessage(message, isUser);
        chatAdapter.addMessage(chatMessage);
        recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        saveMessages();
    }

    private String mapToJson(Map<String, Object> map) {
        JSONObject jsonObject = new JSONObject();
        try {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                jsonObject.put(entry.getKey(), parseObject(entry.getValue()));
            }
            return jsonObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "{}";
        }
    }
    
    private Object parseObject(Object obj) throws JSONException {
        if (obj instanceof Map) {
            JSONObject json = new JSONObject();
            Map<String, Object> map = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                json.put(entry.getKey(), parseObject(entry.getValue()));
            }
            return json;
        } else if (obj instanceof ArrayList) {
            JSONArray json = new JSONArray();
            ArrayList<Object> list = (ArrayList<Object>) obj;
            for (Object item : list) {
                json.put(parseObject(item));
            }
            return json;
        } else {
            return obj;
        }
    }
    
    private String parseResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            
            // Логируем полный ответ для отладки
            Log.d(TAG, "Parsing response: " + jsonResponse);
            
            // Проверка на наличие ошибки
            if (jsonObject.has("error")) {
                JSONObject error = jsonObject.getJSONObject("error");
                return "Ошибка сервера: " + error.optString("message", "Неизвестная ошибка");
            }
            
            JSONArray choices = jsonObject.getJSONArray("choices");
            if (choices.length() == 0) {
                return "Сервер вернул пустой ответ";
            }
            
            JSONObject choice = choices.getJSONObject(0);
            
            // Проверяем наличие сообщения в ответе
            if (choice.has("message")) {
                JSONObject message = choice.getJSONObject("message");
                
                // Проверяем формат content в ответе
                if (message.has("content")) {
                    Object contentObj = message.get("content");
                    
                    // Если content представлен в виде массива (для некоторых моделей)
                    if (contentObj instanceof JSONArray) {
                        JSONArray contentArray = (JSONArray) contentObj;
                        StringBuilder result = new StringBuilder();
                        
                        for (int i = 0; i < contentArray.length(); i++) {
                            JSONObject item = contentArray.getJSONObject(i);
                            if (item.getString("type").equals("text")) {
                                result.append(item.getString("text"));
                            }
                        }
                        
                        return result.toString();
                    } else {
                        // Обычная строка текста
                        return message.getString("content");
                    }
                }
            }
            
            // Если не получается обработать ответ стандартным путем
            return "Получен непонятный формат ответа от сервера. Попробуйте еще раз или свяжитесь с разработчиком.";
            
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(TAG, "JSON parsing error: " + e.getMessage());
            return "Не удалось прочитать ответ от сервера: " + e.getMessage();
        }
    }

    private void saveMessages() {
        SharedPreferences prefs = getSharedPreferences("ChatMessages", MODE_PRIVATE);
        Gson gson = new Gson();
        String json = gson.toJson(messagesList);
        prefs.edit().putString("messages_list", json).apply();
    }

    private void showEmptyState() {
        TextView emptyStateText = findViewById(R.id.empty_state_text);
        if (chatAdapter.getItemCount() == 0) {
            emptyStateText.setVisibility(View.VISIBLE);
        }
    }
    
    private void hideEmptyState() {
        TextView emptyStateText = findViewById(R.id.empty_state_text);
        emptyStateText.setVisibility(View.GONE);
    }

    private void updateTranscriptionText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        // Обновляем текст текущего сообщения пользователя 
        if (currentUserMessage != null) {
            currentUserMessage.setText(text);
            chatAdapter.notifyDataSetChanged();
            recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }
} 