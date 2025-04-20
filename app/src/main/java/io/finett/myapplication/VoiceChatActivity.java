package io.finett.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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

public class VoiceChatActivity extends BaseAccessibilityActivity implements TextToSpeech.OnInitListener, RecognitionListener {
    private ActivityVoiceChatBinding binding;
    private VoiceChatAdapter chatAdapter;
    private OpenRouterApi openRouterApi;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean isListening = false;
    private boolean isSpeaking = false;
    private static final String MODEL_ID = "openai/gpt-3.5-turbo";
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private String apiKey;
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int PERMISSION_LOCATION_REQUEST_CODE = 125;
    private static final int PERMISSION_BLUETOOTH_REQUEST_CODE = 127;
    private static final long SPEECH_TIMEOUT_MILLIS = 1500; // 1.5 секунды тишины для завершения
    private boolean isProcessingSpeech = false;
    private long lastVoiceTime = 0;
    
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_chat);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Голосовой чат");
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.voice_chat_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_manage_prompts) {
                showPromptManagementDialog();
                return true;
            }
            return false;
        });

        promptManager = new PromptManager(this);
        
        // Сначала проверяем новое хранилище для API ключа
        apiKey = getSharedPreferences("ApiPrefs", MODE_PRIVATE)
                .getString("api_key", null);
        
        // Если ключ не найден, пробуем старое хранилище
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                    .getString(MainActivity.API_KEY_PREF, null);
        }

        recyclerView = findViewById(R.id.voice_chat_recycler_view);
        micButton = findViewById(R.id.voice_chat_mic_button);
        cameraButton = findViewById(R.id.voice_chat_camera_button);

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
        
        // Пробуем узнать имя пользователя, если не сохранено - спрашиваем
        String userName = contextInfoProvider.getUserName();
        if (userName.isEmpty()) {
            showUserNameDialog();
        } else if (apiKey == null || apiKey.isEmpty()) {
            // Если имя есть, но нет API ключа, запрашиваем ключ
            showApiKeyDialog();
        }
    }

    private void showUserNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Как вас зовут?");

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                contextInfoProvider.saveUserName(name);
                
                // После получения имени показываем диалог для ввода API ключа
                showApiKeyDialog();
            }
        });
        
        builder.setCancelable(false);
        builder.show();
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
                // Сохраняем API ключ
                SharedPreferences prefs = getSharedPreferences("ApiPrefs", MODE_PRIVATE);
                prefs.edit().putString("api_key", key).apply();
                apiKey = key;
                
                // Добавляем приветственное сообщение только после ввода API ключа
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE);
        } else {
            initSpeechRecognizer();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initSpeechRecognizer();
            } else {
                showError("Необходимо разрешение на использование микрофона");
                finish();
            }
        } else if (requestCode == PERMISSION_LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Разрешение на локацию получено, обновляем информацию о местоположении
                contextInfoProvider.updateLocationInfo();
                ChatMessage botMessage = new ChatMessage("Разрешение на использование местоположения получено", false);
                chatAdapter.addMessage(botMessage);
                speakText(botMessage.getText());
            } else {
                ChatMessage botMessage = new ChatMessage("Для работы с местоположением необходимо разрешение", false);
                chatAdapter.addMessage(botMessage);
                speakText(botMessage.getText());
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
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new VoiceChatAdapter(this);
        recyclerView.setAdapter(chatAdapter);
    }

    private void setupMicButton() {
        micButton.setOnClickListener(v -> {
            if (isSpeaking) {
                // Если идет речь, останавливаем ее
                if (textToSpeech != null) {
                    textToSpeech.stop();
                    isSpeaking = false;
                }
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                // Если нет разрешения, запрашиваем его
                checkPermissionAndInitRecognizer();
            } else if (isListening) {
                // Если слушаем команды, останавливаем
                stopListening();
            } else {
                // Если не слушаем, начинаем слушать команды
                startListening();
            }
        });
    }

    private void setupCameraButton() {
        cameraButton.setOnClickListener(v -> startCameraActivity());
    }

    private void setupApi() {
        openRouterApi = ApiClient.getClient().create(OpenRouterApi.class);
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(this);
        } else {
            Toast.makeText(this, "Распознавание речи недоступно", Toast.LENGTH_SHORT).show();
            finish();
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
                "Ты русскоязычный голосовой помощник. Ты общаешься с пользователем через голосовой интерфейс.\n" +
                "Ответы должны быть короткими, дружелюбными и на русском языке.\n" +
                "Используй простой разговорный язык.\n" +
                "Не используй эмодзи или сложные термины.\n" +
                "Если у тебя есть контекстная информация о пользователе (имя, время суток, местоположение), используй её в своих ответах.\n" +
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
                
                "Пожалуйста, отвечай дружелюбно и лаконично, всегда на русском языке.";
        
        // Load from settings if available
        SharedPreferences sharedPreferences = getSharedPreferences("app_settings", MODE_PRIVATE);
        return sharedPreferences.getString("system_prompt", defaultPrompt);
    }

    private void startListening() {
        if (isListening) return;
        
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
        
        try {
            speechRecognizer.startListening(intent);
            isListening = true;
            isProcessingSpeech = false;
            lastVoiceTime = System.currentTimeMillis();
            micButton.setImageResource(R.drawable.ic_stop);
            
            // Запускаем таймер для проверки тишины
            micButton.postDelayed(speechTimeoutRunnable, SPEECH_TIMEOUT_MILLIS);
        } catch (Exception e) {
            showError("Не удалось запустить распознавание речи: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopListening() {
        if (!isListening) return;
        
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
        ChatMessage userMessage = new ChatMessage(message, true);
        chatAdapter.addMessage(userMessage);
        chatAdapter.setLoading(true);
        recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        
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
            ArrayList<Map<String, Object>> systemContent = new ArrayList<>();
            Map<String, Object> systemTextContent = new HashMap<>();
            systemTextContent.put("type", "text");
            systemTextContent.put("text", activePrompt.getText());
            systemContent.add(systemTextContent);
            systemMessage.put("content", systemContent);
            messages.add(systemMessage);
        }
        
        // Добавляем сообщение пользователя с контекстной информацией
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("role", "user");
        ArrayList<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        
        // Получаем контекстную информацию
        String time = contextInfoProvider.getFormattedTime();
        String location = contextInfoProvider.getFormattedLocation();
        String userName = contextInfoProvider.getUserName();
        
        // Форматируем сообщение с добавлением контекстной информации после запроса
        StringBuilder enhancedMessage = new StringBuilder(message);
        enhancedMessage.append("\n<time>").append(time).append("</time>");
        enhancedMessage.append("\n<geo>").append(location).append("</geo>");
        if (!userName.isEmpty()) {
            enhancedMessage.append("\n<user>").append(userName).append("</user>");
        }
        
        textContent.put("text", enhancedMessage.toString());
        content.add(textContent);
        messageMap.put("content", content);
        messages.add(messageMap);
        
        body.put("messages", messages);
        body.put("max_tokens", 1000);
        
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
                    
                    // Парсим JSON ответ
                    String messageContent = parseResponse(response.toString());
                    
                    // Добавляем сообщение от бота в UI
                    runOnUiThread(() -> {
                        chatAdapter.setLoading(false);
                        ChatMessage botMessage = new ChatMessage(messageContent, false);
                        chatAdapter.addMessage(botMessage);
                        speakText(messageContent);
                    });
                    
                } else {
                    // Обработка ошибки
                    runOnUiThread(() -> {
                        chatAdapter.setLoading(false);
                        ChatMessage errorMessage = new ChatMessage(
                                "Произошла ошибка при отправке запроса (код " + responseCode + ")", false);
                        chatAdapter.addMessage(errorMessage);
                    });
                }
                
            } catch (Exception e) {
                e.printStackTrace();
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
                com.google.android.material.snackbar.Snackbar.make(
                    rootView,
                    message,
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show();
            }
        });
    }

    @Override
    public void onReadyForSpeech(Bundle params) {}

    @Override
    public void onBeginningOfSpeech() {}

    @Override
    public void onRmsChanged(float rmsdB) {
        if (rmsdB > 1.0f) { // Есть звук
            isProcessingSpeech = true;
            lastVoiceTime = System.currentTimeMillis();
        } else { // Тишина
            isProcessingSpeech = false;
            micButton.removeCallbacks(speechTimeoutRunnable);
            micButton.postDelayed(speechTimeoutRunnable, SPEECH_TIMEOUT_MILLIS);
        }
    }

    @Override
    public void onBufferReceived(byte[] buffer) {}

    @Override
    public void onEndOfSpeech() {
        stopListening();
    }

    @Override
    public void onError(int error) {
        // Обычная обработка ошибок для режима слушания команд
        if (error == SpeechRecognizer.ERROR_NO_MATCH || 
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            // Игнорируем эти ошибки, так как они часто возникают при нормальной работе
            stopListening();
            return;
        }

        String message;
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Ошибка записи аудио";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Ошибка клиента";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Недостаточно прав";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Ошибка сети";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Таймаут сети";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "Распознаватель занят";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "Ошибка сервера";
                break;
            default:
                message = "Ошибка распознавания";
                break;
        }
        
        final String errorMessage = message;
        runOnUiThread(() -> {
            micButton.removeCallbacks(speechTimeoutRunnable);
            if (error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                Toast.makeText(VoiceChatActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
            stopListening();
        });
    }

    @Override
    public void onResults(Bundle results) {
        List<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String text = matches.get(0);
            
            // Обычное распознавание команд
            // Обрабатываем сначала как команду
            if (!commandProcessor.processCommand(text)) {
                // Если это не команда, отправляем в API
                sendMessageToAPI(text);
            }
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {}

    @Override
    public void onEvent(int eventType, Bundle params) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (contextInfoProvider != null) {
            contextInfoProvider.cleanup();
        }
        if (commandProcessor != null) {
            commandProcessor.cleanup();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
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
            JSONArray choices = jsonObject.getJSONArray("choices");
            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.getJSONObject("message");
            return message.getString("content");
        } catch (JSONException e) {
            e.printStackTrace();
            return "Не удалось прочитать ответ от сервера.";
        }
    }

    private void saveMessages() {
        SharedPreferences prefs = getSharedPreferences("ChatMessages", MODE_PRIVATE);
        Gson gson = new Gson();
        String json = gson.toJson(messagesList);
        prefs.edit().putString("messages_list", json).apply();
    }
} 