package io.finett.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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
import io.finett.myapplication.util.TypingAnimator;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.google.gson.Gson;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Bundle;
import okhttp3.ResponseBody;
import io.finett.myapplication.util.SuggestionGenerator;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import android.widget.HorizontalScrollView;
import android.view.Window;

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
    
    // Флаг для отслеживания HTTP запросов
    private volatile boolean isHttpRequestInProgress = false;
    
    // Добавляем поле для хранения текущего сообщения пользователя
    private ChatMessage currentUserMessage = null;
    
    // Добавляем переменную для хранения последнего частичного результата
    private String lastPartialResult = "";
    
    // Добавляем флаг для отслеживания явного нажатия кнопки остановки
    private boolean isExplicitlyStopped = false;
    
    // Константа для ключа в SharedPreferences
    private static final String KEY_EXPLICITLY_STOPPED = "mic_explicitly_stopped";
    
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
    private SuggestionGenerator suggestionGenerator;
    private HorizontalScrollView suggestionsScrollView;
    private ChipGroup suggestionsChipGroup;
    
    // Добавляем аниматор печатания текста
    private TypingAnimator typingAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVoiceChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Загружаем сохраненное состояние флага явной остановки микрофона
        isExplicitlyStopped = loadExplicitlyStoppedState();
        
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
        
        // Восстанавливаем сообщения, если есть сохраненное состояние
        if (savedInstanceState != null) {
            ArrayList<ChatMessage> savedMessages = savedInstanceState.getParcelableArrayList("messages");
            if (savedMessages != null && !savedMessages.isEmpty()) {
                messagesList = savedMessages;
                chatAdapter.setMessages(messagesList);
            }
        }
        
        setupMicButton();
        setupCameraButton();
        setupApi();
        setupContextInfo();
        checkPermissionAndInitRecognizer();
        initTextToSpeech();
        setupSuggestions();
        
        // Инициализируем аниматор печатания текста
        typingAnimator = new TypingAnimator(chatAdapter);
        
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
            boolean autoListen = prefs.getBoolean("voice_chat_auto_listen", false);
            
            // Запускаем прослушивание только если автозапуск включен И пользователь явно не остановил микрофон
            if (autoListen && !isExplicitlyStopped) {
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
            // Больше не показываем приветственное сообщение
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
        // Диалог ввода API ключа отключен – приложение использует встроенный ключ.
        android.util.Log.i(TAG, "API-key dialog suppressed – using embedded key");
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
                // Пользователь нажал на кнопку для начала прослушивания
                isExplicitlyStopped = false;
                // Сохраняем состояние в SharedPreferences
                saveExplicitlyStoppedState(false);
                checkPermissionAndInitRecognizer();
                startListening();
                hideEmptyState();
            } else {
                // Пользователь явно нажал на кнопку для остановки прослушивания
                isExplicitlyStopped = true;
                // Сохраняем состояние в SharedPreferences
                saveExplicitlyStoppedState(true);
                stopListening();
            }
        });
        
        // Добавляем обработчик долгого нажатия для сброса состояния флага
        micButton.setOnLongClickListener(v -> {
            // Сбрасываем флаг явной остановки
            isExplicitlyStopped = false;
            saveExplicitlyStoppedState(false);
            
            // Показываем сообщение пользователю
            Toast.makeText(this, "Микрофон разблокирован", Toast.LENGTH_SHORT).show();
            
            // Запускаем микрофон
            if (!isListening) {
                checkPermissionAndInitRecognizer();
                startListening();
                hideEmptyState();
            }
            
            return true; // Возвращаем true, чтобы показать, что событие обработано
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
        try {
            // Сначала очищаем существующие ресурсы
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.cancel();
                    speechRecognizer.destroy();
                    speechRecognizer = null;
                    // Небольшая пауза для освобождения ресурсов
                    Thread.sleep(100);
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка при уничтожении предыдущего распознавателя", e);
                }
            }
            
            // Проверяем доступность распознавания речи
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Log.e(TAG, "Распознавание речи недоступно на этом устройстве");
                showError("Распознавание речи недоступно на этом устройстве");
                return;
            }
            
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            if (speechRecognizer == null) {
                Log.e(TAG, "Не удалось создать SpeechRecognizer");
                showError("Не удалось инициализировать распознавание речи");
                return;
            }
            
            speechRecognizer.setRecognitionListener(this);
            
            // Логируем успешную инициализацию
            Log.d(TAG, "SpeechRecognizer успешно инициализирован");
        } catch (Exception e) {
            Log.e(TAG, "Критическая ошибка при инициализации распознавателя речи: " + e.getMessage(), e);
            showError("Ошибка инициализации распознавания речи: " + e.getMessage());
            
            // Убеждаемся, что ресурсы освобождены
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.destroy();
                } catch (Exception ex) {
                    // Игнорируем ошибки при освобождении
                }
                speechRecognizer = null;
            }
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
                    // Запускаем прослушивание после завершения речи только если пользователь не нажал кнопку остановки
                    if (!isExplicitlyStopped) {
                    runOnUiThread(() -> startListening());
                    }
                }

                @Override
                public void onError(String utteranceId) {
                    isSpeaking = false;
                    // Запускаем прослушивание после ошибки речи только если пользователь не нажал кнопку остановки
                    if (!isExplicitlyStopped) {
                    runOnUiThread(() -> startListening());
                    }
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
        
        // Проверяем, не выполняется ли HTTP запрос
        if (isHttpRequestInProgress) {
            Log.d(TAG, "HTTP request in progress, skipping microphone activation");
            return;
        }
        
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
                
                // Добавляем дополнительную задержку после инициализации
                Log.d(TAG, "Adding extra delay after SpeechRecognizer initialization");
                new Handler(Looper.getMainLooper()).postDelayed(() -> startListeningAfterDelay(), 250);
                return;
            }
            
            // Используем вспомогательный метод для запуска с дополнительными проверками
            startListeningAfterDelay();
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
        try {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping listening", e);
        }
        isListening = false;
        isProcessingSpeech = false;
        micButton.setImageResource(R.drawable.ic_mic);
        
        // Удаляем пустое сообщение, если оно было создано, но осталось пустым
        if (currentUserMessage != null && (currentUserMessage.getText() == null || currentUserMessage.getText().isEmpty())) {
            chatAdapter.removeMessage(currentUserMessage);
            currentUserMessage = null;
        }
        
        // Автоматически перезапускаем прослушивание только если не было явного нажатия на кнопку остановки
        // и нет активного HTTP запроса или синтеза речи
        if (!isExplicitlyStopped) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // Не запускаем прослушивание, если выполняется HTTP запрос или сейчас говорит TTS
                if (!isSpeaking && !isHttpRequestInProgress) {
                startListening();
                } else {
                    Log.d(TAG, "Skipping microphone restart: speaking=" + isSpeaking + 
                               ", httpRequestInProgress=" + isHttpRequestInProgress);
            }
        }, 300);
        } else {
            Log.d(TAG, "Microphone explicitly stopped by user, not restarting automatically");
        }
    }

    private void speakText(String text) {
        if (textToSpeech != null && !isSpeaking) {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "messageId");
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
    }

    private void sendMessageToAPI(String message) {
        try {
            // Если сообщение пустое, не отправляем запрос
            if (message == null || message.trim().isEmpty()) {
                Log.e(TAG, "Попытка отправить пустое сообщение API, отмена");
                isHttpRequestInProgress = false;
                return;
            }
            
            // Обновляем apiKey перед отправкой, чтобы учесть возможное изменение настроек
            apiKey = getApiKey();
            
            if (apiKey == null) {
                showError("API ключ не установлен");
                showApiKeyDialog();
                isHttpRequestInProgress = false;
                return;
            }
            
            // Устанавливаем флаг, что HTTP запрос в процессе
            isHttpRequestInProgress = true;
            
            // Обратите внимание, что мы уже имеем сообщение пользователя в интерфейсе
            // и не добавляем его повторно
            
            runOnUiThread(() -> {
                try {
                    chatAdapter.setLoading(true);
                    recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка при обновлении UI в начале sendMessageToAPI", e);
                }
            });
            
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
            if (commandProcessor != null && commandProcessor.isCommand(message)) {
                String response = commandProcessor.processCommand(message);
                if (response != null) {
                    runOnUiThread(() -> {
                        try {
                            chatAdapter.setLoading(false);
                            ChatMessage botMessage = new ChatMessage(response, false);
                            chatAdapter.addMessage(botMessage);
                            messagesList.add(botMessage);
                            
                            // Запускаем анимацию печатания для сообщения бота
                            typingAnimator.startTypingAnimation(botMessage, response);
                            
                            // Озвучиваем только после завершения анимации
                            typingAnimator.setCompletionListener(completedMessage -> {
                                speakText(response);
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка при обработке команды", e);
                        }
                    });
                    
                    // Сбрасываем флаг HTTP запроса
                    isHttpRequestInProgress = false;
                    return;
                }
            }
            
            // Создаем системный промпт
            SystemPrompt systemPrompt = promptManager.createSystemPrompt(contextInfoProvider);
            
            // Создаем заголовки для запроса
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Authorization", "Bearer " + apiKey);
            headers.put("HTTP-Referer", "https://github.com/finett/android-assistant");
            headers.put("X-Title", "Android Assistant");
            
            // Создаем тело запроса
            Map<String, Object> body = new HashMap<>();
            body.put("model", MODEL_ID);
            
            // Создаем сообщения для API
            List<Map<String, String>> messages = new ArrayList<>();
            
            // Добавляем системный промпт
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt.getContent());
            messages.add(systemMessage);
            
            // Добавляем историю сообщений (максимум 10 последних сообщений)
            int historySize = Math.min(messagesList.size(), 10);
            for (int i = messagesList.size() - historySize; i < messagesList.size(); i++) {
                ChatMessage historyMessage = messagesList.get(i);
                Map<String, String> chatMessage = new HashMap<>();
                chatMessage.put("role", historyMessage.isUser() ? "user" : "assistant");
                chatMessage.put("content", historyMessage.getText());
                messages.add(chatMessage);
            }
            
            body.put("messages", messages);
            
            // Используем обычный запрос вместо стриминга
            body.put("stream", false);

            // Используем API для запроса без стриминга
            Call<Map<String, Object>> call = openRouterApi.getChatCompletion(
                    "Bearer " + apiKey,
                    "https://github.com/finett/android-assistant",
                    "Android Assistant",
                    body
            );
            
            call.enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    try {
                        if (!response.isSuccessful()) {
                            runOnUiThread(() -> {
                                try {
                                    chatAdapter.setLoading(false);
                                    String errorMessage = "Ошибка API: " + response.code();
                                    try {
                                        if (response.errorBody() != null) {
                                            errorMessage += " - " + response.errorBody().string();
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Ошибка при чтении тела ошибки", e);
                                    }
                                    showError(errorMessage);
                                } catch (Exception e) {
                                    Log.e(TAG, "Ошибка при обработке неудачного ответа", e);
                                } finally {
                                    isHttpRequestInProgress = false;
                                }
                            });
                            return;
                        }
                        
                        // Обрабатываем ответ
                        Map<String, Object> responseBody = response.body();
                        if (responseBody != null && responseBody.containsKey("choices")) {
                            ArrayList<Map<String, Object>> choices = (ArrayList<Map<String, Object>>) responseBody.get("choices");
                            if (choices != null && !choices.isEmpty()) {
                                Map<String, Object> choice = choices.get(0);
                                if (choice != null && choice.containsKey("message")) {
                                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                                    if (message != null && message.containsKey("content")) {
                                        String content = (String) message.get("content");
                                        if (content != null) {
                                            final String finalContent = content;
                                            runOnUiThread(() -> {
                                                try {
                                                    chatAdapter.setLoading(false);
                                                    
                                                    // Создаем сообщение бота
                                                    ChatMessage assistantMessage = new ChatMessage("", false);
                                                    chatAdapter.addMessage(assistantMessage);
                                                    messagesList.add(assistantMessage);
                                                    
                                                    // Запускаем анимацию печатания для сообщения бота
                                                    typingAnimator.startTypingAnimation(assistantMessage, finalContent);
                                                    
                                                    // Озвучиваем текст и генерируем подсказки только после завершения анимации
                                                    typingAnimator.setCompletionListener(completedMessage -> {
                                                        // Озвучиваем полный ответ
                                                        speakText(finalContent);
                                                        
                                                        // Генерируем подсказки на основе ответа бота
                                                        generateSuggestions(finalContent);
                                                    });
                                                } catch (Exception e) {
                                                    Log.e(TAG, "Ошибка при обновлении UI с ответом", e);
                                                } finally {
                                                    isHttpRequestInProgress = false;
                                                }
                                            });
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Если не удалось извлечь ответ
                        runOnUiThread(() -> {
                            try {
                                chatAdapter.setLoading(false);
                                showError("Не удалось получить ответ от модели");
                            } catch (Exception e) {
                                Log.e(TAG, "Ошибка при обработке пустого ответа", e);
                            } finally {
                                isHttpRequestInProgress = false;
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Критическая ошибка при обработке ответа: " + e.getMessage(), e);
                        runOnUiThread(() -> {
                            try {
                                chatAdapter.setLoading(false);
                                showError("Критическая ошибка: " + e.getMessage());
                            } catch (Exception ex) {
                                Log.e(TAG, "Ошибка при обработке исключения", ex);
                            } finally {
                                isHttpRequestInProgress = false;
                            }
                        });
                    }
                }
                
                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    Log.e(TAG, "Ошибка сети: " + t.getMessage(), t);
                    runOnUiThread(() -> {
                        try {
                            chatAdapter.setLoading(false);
                            showError("Ошибка сети: " + t.getMessage());
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка при обработке сетевой ошибки", e);
                        } finally {
                            isHttpRequestInProgress = false;
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Критическая ошибка в sendMessageToAPI: " + e.getMessage(), e);
            runOnUiThread(() -> {
                try {
                    chatAdapter.setLoading(false);
                    showError("Ошибка при отправке сообщения: " + e.getMessage());
                } catch (Exception ex) {
                    Log.e(TAG, "Ошибка при обработке исключения", ex);
                }
            });
            isHttpRequestInProgress = false;
        }
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
        
        // Не создаем пустое сообщение при начале речи
        // Сообщение будет создано при получении первых распознанных слов
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
        
        // Если пользователь явно выключил микрофон, не пытаемся его перезапускать
        if (isExplicitlyStopped) {
            Log.d(TAG, "Not continuing recognition after end of speech: microphone explicitly stopped by user");
            isListening = false;
            micButton.setImageResource(R.drawable.ic_mic);
            return;
        }
        
        // Не останавливаем прослушивание, а просто обрабатываем событие конца речи
        // Результаты придут автоматически в onResults
        
        // Меняем иконку на неактивный микрофон, но не останавливаем распознавание полностью
        runOnUiThread(() -> {
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
            // Не меняем состояние isListening, чтобы избежать рекурсивных вызовов
        });
    }

    @Override
    public void onResults(Bundle results) {
        try {
            if (results == null) {
                Log.e(TAG, "Получен null bundle в onResults");
                if (!isExplicitlyStopped) {
                    restartListeningAfterResults();
                }
                return;
            }
            
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
                
                // Автоматически перезапускаем прослушивание только если пользователь не выключил микрофон явно
                if (!isExplicitlyStopped) {
                    restartListeningAfterResults();
                } else {
                    Log.d(TAG, "Not restarting after empty results: microphone explicitly stopped by user");
                }
                return;
            }
            
            // Очищаем последний частичный результат
            String text = "";
            if (matches != null && !matches.isEmpty() && matches.get(0) != null) {
                text = matches.get(0);
            }
            lastPartialResult = "";
            
            // Если текст пустой, не обрабатываем его
            if (text == null || text.isEmpty()) {
                Log.d(TAG, "Пустой результат распознавания речи");
                // Автоматически перезапускаем прослушивание только если пользователь не выключил микрофон явно
                if (!isExplicitlyStopped) {
                    restartListeningAfterResults();
                }
                return;
            }
            
            // Получаем финальный текст для обработки
            final String finalText = text;
            
            // Выполняем операции с UI в основном потоке
            runOnUiThread(() -> {
                try {
                    // Если сообщение еще не создано, создаем его сейчас
                    if (currentUserMessage == null) {
                        currentUserMessage = new ChatMessage(finalText, true);
                        chatAdapter.addMessage(currentUserMessage);
                        recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                    } else {
                        // Обновляем текст текущего сообщения пользователя с финальным результатом
                        updateTranscriptionText(finalText);
                    }
                    
                    // Логируем полученный текст
                    Log.d(TAG, "Processing recognized text: '" + finalText + "'");
                    
                    // Проверяем команду на открытие приложения
                    String normalizedText = finalText.toLowerCase();
                    if (normalizedText.startsWith("открой") || 
                        normalizedText.startsWith("запусти") || 
                        normalizedText.startsWith("открыть") || 
                        normalizedText.startsWith("запустить")) {
                        
                        // Обрабатываем как команду для запуска приложения
                        String response = commandProcessor.processCommand(finalText);
                        if (response != null) {
                            // Добавляем ответ в список сообщений
                            ChatMessage botMessage = new ChatMessage(response, false);
                            chatAdapter.addMessage(botMessage);
                            recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                            
                            // Озвучиваем ответ
                            speakText(response);
                            
                            // Сбрасываем текущее сообщение пользователя
                            currentUserMessage = null;
                            
                            // Автоматически перезапускаем прослушивание только если пользователь не выключил микрофон явно
                            if (!isExplicitlyStopped) {
                                restartListeningAfterResults();
                            } else {
                                Log.d(TAG, "Not restarting after command: microphone explicitly stopped by user");
                            }
                            return;
                        }
                    }
                    
                    // Обрабатываем сначала как команду
                    String response = commandProcessor.processCommand(finalText);
                    if (response != null) {
                        // Добавляем ответ в список сообщений
                        ChatMessage botMessage = new ChatMessage(response, false);
                        chatAdapter.addMessage(botMessage);
                        recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                        
                        // Озвучиваем ответ
                        speakText(response);
                    } else {
                        // Если это не команда, отправляем в API
                        sendMessageToAPI(finalText);
                    }
                    
                    // Сбрасываем текущее сообщение пользователя
                    currentUserMessage = null;
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка при обработке результатов распознавания речи: " + e.getMessage(), e);
                    showError("Ошибка при обработке голоса: " + e.getMessage());
                    currentUserMessage = null;
                }
                
                // Автоматически перезапускаем прослушивание только если пользователь не выключил микрофон явно
                if (!isExplicitlyStopped) {
                    restartListeningAfterResults();
                } else {
                    Log.d(TAG, "Not restarting after processing: microphone explicitly stopped by user");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Критическая ошибка в onResults: " + e.getMessage(), e);
            currentUserMessage = null;
            
            // Автоматически перезапускаем прослушивание только если пользователь не выключил микрофон явно
            if (!isExplicitlyStopped) {
                restartListeningAfterResults();
            }
        }
    }
    
    /**
     * Перезапускает прослушивание после обработки результатов
     */
    private void restartListeningAfterResults() {
        // Не перезапускаем если пользователь явно нажал на кнопку остановки
        if (isExplicitlyStopped) {
            Log.d(TAG, "Not restarting after results: microphone explicitly stopped by user");
            return;
        }
        
        // Задержка перед перезапуском прослушивания
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Проверяем, не говорим ли мы сейчас через TTS и не выполняется ли HTTP запрос
            if (!isSpeaking && !isListening && !isHttpRequestInProgress) {
                startListening();
            } else {
                Log.d(TAG, "Skipping microphone restart after results: speaking=" + isSpeaking + 
                         ", httpRequestInProgress=" + isHttpRequestInProgress);
            }
        }, 500);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        try {
            if (partialResults == null) {
                Log.e(TAG, "Получен null bundle в onPartialResults");
                return;
            }
            
            List<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty() && matches.get(0) != null) {
                String text = matches.get(0);
                Log.d(TAG, "Partial result: '" + text + "'");
                
                // Сохраняем последний частичный результат
                if (text != null && !text.isEmpty()) {
                    lastPartialResult = text;
                    
                    // Выполняем операции с UI в основном потоке
                    runOnUiThread(() -> {
                        try {
                            // Создаем сообщение пользователя, если его еще нет и есть текст для отображения
                            if (currentUserMessage == null) {
                                currentUserMessage = new ChatMessage(text, true);
                                chatAdapter.addMessage(currentUserMessage);
                                recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                            } else {
                                // Обновляем текст текущего сообщения пользователя
                                updateTranscriptionText(text);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка при обновлении UI в onPartialResults: " + e.getMessage(), e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Критическая ошибка в onPartialResults: " + e.getMessage(), e);
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {}

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called");
        
        // Release text-to-speech resources
        try {
            if (textToSpeech != null) {
                textToSpeech.stop();
                textToSpeech.shutdown();
                textToSpeech = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при освобождении ресурсов text-to-speech", e);
        }
        
        // Release speech recognizer resources
        try {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.cancel();
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка при отмене распознавания", e);
                }
                
                try {
                    speechRecognizer.destroy();
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка при уничтожении распознавателя", e);
                }
                
                speechRecognizer = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Критическая ошибка при освобождении ресурсов распознавателя", e);
        }
        
        // Освобождаем другие ресурсы
        try {
            // Отменяем анимации
            if (pulsateAnimator != null && pulsateAnimator.isRunning()) {
                pulsateAnimator.cancel();
            }
            
            if (fadeInAnimator != null && fadeInAnimator.isRunning()) {
                fadeInAnimator.cancel();
            }
            
            if (fadeOutAnimator != null && fadeOutAnimator.isRunning()) {
                fadeOutAnimator.cancel();
            }
            
            if (borderPulsateAnimator != null && borderPulsateAnimator.isRunning()) {
                borderPulsateAnimator.cancel();
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при отмене анимаций", e);
        }
        
        // Останавливаем анимацию печатания при уничтожении активности
        if (typingAnimator != null && typingAnimator.isAnimating()) {
            typingAnimator.stopTypingAnimation();
        }
        
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // Останавливаем анимацию печатания при уходе с экрана
        if (typingAnimator != null && typingAnimator.isAnimating()) {
            typingAnimator.stopTypingAnimation();
        }
        
        // Set explicit stop flag when pausing the activity
        isExplicitlyStopped = true;
        // Сохраняем состояние флага
        saveExplicitlyStoppedState(true);
        
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
        
        // Загружаем сохраненное состояние флага явной остановки микрофона
        isExplicitlyStopped = loadExplicitlyStoppedState();
        
        // Обновляем контекстную информацию при возвращении к активности
        if (contextInfoProvider != null) {
            contextInfoProvider.updateLocationInfo();
        }
        
        // При возвращении в активность проверяем настройку автоматического запуска микрофона
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        boolean autoListen = prefs.getBoolean("voice_chat_auto_listen", false);
        
        if (autoListen && !isExplicitlyStopped) {
            // Запускаем распознавание, если нет других препятствий
            if (!isSpeaking && !isHttpRequestInProgress) {
                startListening();
            }
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

    /**
     * Вспомогательный метод для запуска распознавания после задержки
     */
    private void startListeningAfterDelay() {
        try {
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
                
                // Небольшая пауза между отменой и новым запуском
                Thread.sleep(50);
            } catch (Exception e) {
                Log.e(TAG, "Error canceling before start", e);
            }
            
            // Очищаем последний частичный результат перед новым распознаванием
            lastPartialResult = "";
            
            Log.d(TAG, "Starting speech recognition");
            speechRecognizer.startListening(intent);
            isListening = true;
            isProcessingSpeech = false;
            lastVoiceTime = System.currentTimeMillis();
            micButton.setImageResource(R.drawable.ic_stop);
            
            // Удаляем создание пустого сообщения при начале прослушивания
            // Теперь сообщение будет создаваться только при получении первых слов
            
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
                    fullResetAndRestartRecognition();
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
            
            // Пытаемся перезапустить распознаватель с полной переинициализацией
            fullResetAndRestartRecognition();
        }
    }
    
    /**
     * Полная переинициализация и перезапуск распознавания речи
     * после критической ошибки
     */
    private void fullResetAndRestartRecognition() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "Performing full reset of speech recognition system");
            // Полностью освобождаем ресурсы
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.cancel();
                    speechRecognizer.destroy();
                    speechRecognizer = null;
                } catch (Exception ex) {
                    Log.e(TAG, "Error while destroying recognizer in full reset", ex);
                }
            }
            
            // Ждем перед повторной инициализацией
            try {
                Thread.sleep(300);
            } catch (InterruptedException ie) {
                // Игнорируем
            }
            
            // Инициализируем снова
            initSpeechRecognizer();
            
            // Ждем еще немного перед запуском распознавания
            new Handler(Looper.getMainLooper()).postDelayed(this::startListening, 500);
        }, 1000);
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
        
        // Обновляем состояние
        isListening = false;
        
        // Очищаем UI
        runOnUiThread(() -> {
            micButton.removeCallbacks(speechTimeoutRunnable);
            
            // Удаляем пустое сообщение, если оно было создано, но осталось пустым
            if (currentUserMessage != null && (currentUserMessage.getText() == null || currentUserMessage.getText().isEmpty())) {
                chatAdapter.removeMessage(currentUserMessage);
                currentUserMessage = null;
            }
            
            // Обрабатываем ошибки и перезапускаем распознавание
            if (error == SpeechRecognizer.ERROR_CLIENT) {
                // При ошибке клиента проводим полную переинициализацию
                Log.d(TAG, "Handling ERROR_CLIENT with full reset");
                fullResetAndRestartRecognition();
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                // При ошибке "занято" делаем полный сброс с паузой
                Log.d(TAG, "Handling ERROR_RECOGNIZER_BUSY with full reset");
                fullResetAndRestartRecognition();
            } else if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                // При таймауте или отсутствии совпадений просто перезапускаем, если не было явной остановки
                Log.d(TAG, "Restarting recognition after no match or timeout");
                if (!isExplicitlyStopped) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!isSpeaking) {
                        startListening();
                    }
                }, 300);
                }
            } else {
                // Для других ошибок делаем более длительную паузу и полный сброс
                Log.d(TAG, "Handling other error with delay and full reset");
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || 
                    error == SpeechRecognizer.ERROR_NETWORK) {
                    showError(errorMessage);
                }
                if (!isExplicitlyStopped) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    fullResetAndRestartRecognition();
                }, 1000);
                }
            }
        });
    }

    /**
     * Сохраняет состояние флага явной остановки микрофона
     * @param stopped true, если микрофон был явно остановлен пользователем
     */
    private void saveExplicitlyStoppedState(boolean stopped) {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_EXPLICITLY_STOPPED, stopped).apply();
    }
    
    /**
     * Загружает состояние флага явной остановки микрофона
     * @return true, если микрофон был явно остановлен пользователем
     */
    private boolean loadExplicitlyStoppedState() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_EXPLICITLY_STOPPED, false);
    }

    /**
     * Настраивает UI для подсказок и инициализирует генератор подсказок
     */
    private void setupSuggestions() {
        // Находим view для подсказок
        suggestionsScrollView = findViewById(R.id.suggestionsScrollView);
        suggestionsChipGroup = findViewById(R.id.suggestionsChipGroup);
        
        // Инициализация генератора подсказок
        suggestionGenerator = new SuggestionGenerator(this, suggestions -> {
            runOnUiThread(() -> updateSuggestionsUI(suggestions));
        });
    }
    
    /**
     * Обновляет UI с новыми подсказками
     */
    private void updateSuggestionsUI(List<String> suggestions) {
        if (suggestionsChipGroup == null) return;
        
        // Очищаем текущие подсказки
        suggestionsChipGroup.removeAllViews();
        
        if (!suggestions.isEmpty()) {
            // Отображаем контейнер с подсказками
            suggestionsScrollView.setVisibility(View.VISIBLE);
            
            // Создаем чипы для каждой подсказки
            for (String suggestion : suggestions) {
                Chip chip = new Chip(this);
                chip.setText(suggestion);
                chip.setClickable(true);
                chip.setCheckable(false);
                
                // Обработчик нажатия на подсказку
                chip.setOnClickListener(v -> {
                    // Отправляем выбранную подсказку как сообщение пользователя
                    ChatMessage userMessage = new ChatMessage(suggestion, true);
                    chatAdapter.addMessage(userMessage);
                    messagesList.add(userMessage);
                    suggestionsScrollView.setVisibility(View.GONE);
                    sendMessageToAPI(suggestion);
                });
                
                suggestionsChipGroup.addView(chip);
            }
        } else {
            // Скрываем контейнер, если подсказок нет
            suggestionsScrollView.setVisibility(View.GONE);
        }
    }
    
    /**
     * Генерирует подсказки на основе контекста чата
     */
    private void generateSuggestions(String lastBotMessage) {
        // Показываем индикатор загрузки
        runOnUiThread(() -> {
            if (suggestionsChipGroup != null) {
                suggestionsChipGroup.removeAllViews();
                
                // Добавляем "загрузочный" чип
                Chip loadingChip = new Chip(this);
                loadingChip.setText("Генерирую подсказки...");
                loadingChip.setClickable(false);
                loadingChip.setCheckable(false);
                suggestionsChipGroup.addView(loadingChip);
                
                // Показываем контейнер с подсказками
                suggestionsScrollView.setVisibility(View.VISIBLE);
            }
        });
        
        // Запускаем генерацию подсказок
        suggestionGenerator.generateSuggestions(lastBotMessage);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Сохраняем список сообщений при изменении конфигурации
        if (messagesList != null && !messagesList.isEmpty()) {
            outState.putParcelableArrayList("messages", new ArrayList<>(messagesList));
        }
    }
    
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // Восстановление сообщений происходит в onCreate
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Обновляем UI при изменении темы
        if ((newConfig.uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) != 
            (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)) {
            
            Log.d(TAG, "Тема изменена, обновляем UI");
            
            // Обновляем адаптер, чтобы применить новые цвета
            if (chatAdapter != null) {
                chatAdapter.refreshColors();
            }
            
            // Обновляем цвета кнопок и других элементов UI
            updateUIColors();
        }
    }
    
    private void updateUIColors() {
        // Обновляем цвета элементов интерфейса в соответствии с текущей темой
        boolean isNightMode = (getResources().getConfiguration().uiMode 
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        
        Log.d(TAG, "Обновление цветов UI, ночной режим: " + isNightMode);
        
        // Определяем цвета в зависимости от темы
        int backgroundColor = ContextCompat.getColor(this, 
            isNightMode ? R.color.background : R.color.background_light);
        int primaryColor = ContextCompat.getColor(this,
            isNightMode ? R.color.primary : R.color.primary_light);
        int accentColor = ContextCompat.getColor(this,
            isNightMode ? R.color.accent : R.color.accent_light);
            
        // Обновляем статус-бар и навигационную панель
        Window window = getWindow();
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(primaryColor);
            window.setNavigationBarColor(backgroundColor);
            
            // Устанавливаем светлые/темные иконки в зависимости от темы
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decorView = window.getDecorView();
                int flags = decorView.getSystemUiVisibility();
                if (isNightMode) {
                    // Темная тема - светлые иконки
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    // Светлая тема - темные иконки
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                decorView.setSystemUiVisibility(flags);
            }
        }
        
        // Обновляем цвета кнопок
        if (micButton != null) {
            micButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
        }
        
        if (cameraButton != null) {
            cameraButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        }
        
        // Обновляем фон основного контейнера
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.setBackgroundColor(backgroundColor);
            
            // Находим и обновляем все основные контейнеры
            androidx.constraintlayout.widget.ConstraintLayout mainContainer = 
                findViewById(R.id.main_container);
            if (mainContainer != null) {
                mainContainer.setBackgroundColor(backgroundColor);
            }
            
            // Обновляем фон RecyclerView
            if (recyclerView != null) {
                recyclerView.setBackgroundColor(backgroundColor);
            }
            
            // Обновляем фон контейнера кнопок
            View buttonContainer = findViewById(R.id.button_container);
            if (buttonContainer != null) {
                buttonContainer.setBackgroundColor(backgroundColor);
            }
        }
        
        // Обновляем цвета тулбара
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setBackgroundColor(primaryColor);
        }
        
        // Обновляем цвета подсказок
        HorizontalScrollView suggestionsScrollView = findViewById(R.id.suggestionsScrollView);
        if (suggestionsScrollView != null) {
            suggestionsScrollView.setBackgroundColor(backgroundColor);
        }
        
        // Принудительно перерисовываем весь интерфейс
        View decorView = getWindow().getDecorView();
        decorView.invalidate();
    }
} 