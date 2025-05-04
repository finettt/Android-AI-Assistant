package io.finett.myapplication;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.finett.myapplication.adapter.ChatAdapter;
import io.finett.myapplication.adapter.ChatsAdapter;
import io.finett.myapplication.api.ApiClient;
import io.finett.myapplication.api.OpenRouterApi;
import io.finett.myapplication.databinding.ActivityMainBinding;
import io.finett.myapplication.model.AIModel;
import io.finett.myapplication.model.Chat;
import io.finett.myapplication.model.ChatMessage;
import io.finett.myapplication.util.ImageUtil;
import io.finett.myapplication.util.StorageUtil;
import io.finett.myapplication.util.UserManager;
import io.finett.myapplication.util.AccessibilityManager;
import io.finett.myapplication.service.WeatherService;
import io.finett.myapplication.model.WeatherData;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import io.finett.myapplication.base.BaseAccessibilityActivity;
import com.google.gson.Gson;

public class MainActivity extends BaseAccessibilityActivity implements 
        ChatsAdapter.OnChatClickListener, 
        ChatAdapter.OnAttachmentClickListener,
        ChatAdapter.OnMessageActionListener {
    private ActivityMainBinding binding;
    private ChatAdapter chatAdapter;
    private ChatsAdapter chatsAdapter;
    private OpenRouterApi openRouterApi;
    public static final String PREFS_NAME = "ChatAppPrefs";
    public static final String API_KEY_PREF = "api_key";
    private String apiKey;
    private Chat currentChat;
    private Uri currentPhotoUri;
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int VOICE_ACTIVATION_PERMISSION_CODE = 124;
    private List<AIModel> availableModels = Arrays.asList(
            new AIModel("Claude 3 Haiku", "anthropic/claude-3-haiku-20240307", false),
            new AIModel("Claude 3 Sonnet", "anthropic/claude-3-sonnet-20240229", false),
            new AIModel("Gemini Pro", "google/gemini-pro", true),
            new AIModel("Qwen2.5 VL 32B Instruct", "qwen/qwen2.5-vl-32b-instruct:free", false),
            new AIModel("Qwen 3 235B", "qwen/qwen3-235b-a22b:free", true),
            new AIModel("Mistral 7B", "mistralai/mistral-7b-instruct-v0.1", false),
            new AIModel("Mixtral 8x7B", "mistralai/mixtral-8x7b-instruct-v0.1", false),
            new AIModel("QwQ 32B RpR v1", "arliai/qwq-32b-arliai-rpr-v1:free", false),
            new AIModel("Phi 4 Reasoning", "microsoft/phi-4-reasoning:free", false)
    );
    private List<Chat> chats = new ArrayList<>();
    private UserManager userManager;
    private AccessibilityManager accessibilityManager;
    private BroadcastReceiver settingsReceiver;
    private WeatherService weatherService;

    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    handleAttachment(uri, ChatMessage.AttachmentType.FILE);
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && currentPhotoUri != null) {
                    handleAttachment(currentPhotoUri, ChatMessage.AttachmentType.IMAGE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        openRouterApi = ApiClient.getOpenRouterApi();
        accessibilityManager = new AccessibilityManager(this, null);
        userManager = new UserManager(this);
        
        // Verify all required permissions are granted
        if (!checkRequiredPermissions()) {
            // If permissions are missing, redirect to permission activity
            startActivity(new Intent(this, PermissionRequestActivity.class));
            finish();
            return;
        }
        
        setupToolbar();
        setupRecyclerViews();
        setupNewChatButton();
        setupMessageInput();
        setupAttachButton();
        loadSavedChats();
        setupApi();
        setupSettingsReceiver();
        setupAccessibilityButtons();
        setupVoiceActivationButton();
        
        // Получаем API ключ с учетом настройки BUILD_CONFIG
        apiKey = getApiKey();
        
        // Запрашиваем API ключ только если он не задан и не используется хардкодный ключ
        if (apiKey == null && !BuildConfig.USE_HARDCODED_KEY) {
            showApiKeyDialog();
        }

        // Проверяем, зарегистрирован ли пользователь, если нет - регистрируем с дефолтным именем
        if (!userManager.isRegistered()) {
            // Регистрируем пользователя автоматически с дефолтным именем
            userManager.registerUser("Пользователь");
        }

        // Применяем текущие настройки доступности
        applyAccessibilitySettings();

        weatherService = new WeatherService(this);
        
        // Check for wake phrase intent
        handleIntent(getIntent());
    }

    private boolean checkRequiredPermissions() {
        // Проверяем только критические разрешения, которые абсолютно необходимы
        // для минимальной работы приложения. Остальные будут запрошены на 
        // экране разрешений PermissionRequestActivity
        String[] criticalPermissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        };
        
        for (String permission : criticalPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != 
                    PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        
        return true;
    }

    private void loadSavedChats() {
        chats = StorageUtil.loadChats(this);
        if (chatsAdapter != null) {
            chatsAdapter.setChats(chats);
        }

        // Загружаем последний активный чат
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String lastActiveChatId = prefs.getString("last_active_chat_id", null);
        
        if (lastActiveChatId != null && !chats.isEmpty()) {
            // Ищем чат по ID
            for (Chat chat : chats) {
                if (chat.getId().equals(lastActiveChatId)) {
                    currentChat = chat;
                    chatAdapter.setMessages(chat.getMessages());
                    updateToolbarTitle();
                    break;
                }
            }
        } else if (!chats.isEmpty()) {
            // Если нет сохраненного активного чата, берем первый из списка
            currentChat = chats.get(0);
            chatAdapter.setMessages(currentChat.getMessages());
            updateToolbarTitle();
        }
    }

    private void saveChats() {
        StorageUtil.saveChats(this, chats);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> binding.drawerLayout.open());
    }

    private void setupRecyclerViews() {
        // Настройка списка сообщений
        chatAdapter = new ChatAdapter(this, this);
        binding.chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.chatRecyclerView.setAdapter(chatAdapter);

        // Настройка списка чатов
        chatsAdapter = new ChatsAdapter(this);
        binding.chatsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.chatsRecyclerView.setAdapter(chatsAdapter);
    }

    private void setupNewChatButton() {
        binding.newChatButton.setOnClickListener(v -> showNewChatDialog());
        binding.voiceChatButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, VoiceChatActivity.class);
            startActivity(intent);
            binding.drawerLayout.close();
        });
        binding.accessibilitySettingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AccessibilitySettingsActivity.class);
            startActivity(intent);
            binding.drawerLayout.close();
        });
        binding.chatSettingsButton.setOnClickListener(v -> {
            showChatSettingsDialog();
            binding.drawerLayout.close();
        });
    }

    private void showNewChatDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Новый чат");

        // Создаем layout для диалога
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 0);

        // Поле для названия чата
        TextInputLayout titleLayout = new TextInputLayout(this);
        TextInputEditText titleInput = new TextInputEditText(this);
        titleInput.setHint("Название чата");
        titleLayout.addView(titleInput);
        layout.addView(titleLayout);

        // Спиннер для выбора модели
        TextInputLayout modelLayout = new TextInputLayout(this);
        modelLayout.setHint("Выберите модель");
        ArrayAdapter<AIModel> modelAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_dropdown_item, availableModels);
        android.widget.Spinner modelSpinner = new android.widget.Spinner(this);
        modelSpinner.setAdapter(modelAdapter);
        modelLayout.addView(modelSpinner);
        layout.addView(modelLayout);

        builder.setView(layout);

        builder.setPositiveButton("Создать", (dialog, which) -> {
            String title = titleInput.getText().toString().trim();
            if (title.isEmpty()) {
                title = "Новый чат";
            }
            AIModel selectedModel = (AIModel) modelSpinner.getSelectedItem();
            createNewChat(title, selectedModel.getId());
        });

        builder.setNegativeButton("Отмена", null);
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.white));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.white));
        });
        dialog.show();
    }

    private void createNewChat(String title, String modelId) {
        currentChat = new Chat(title, modelId);
        chats.add(0, currentChat);
        chatsAdapter.setChats(chats);
        binding.drawerLayout.close();
        updateToolbarTitle();
        chatAdapter.clear();
        saveChats();
    }

    private void updateToolbarTitle() {
        if (currentChat != null) {
            binding.toolbar.setTitle(currentChat.getTitle());
            String modelName = availableModels.stream()
                .filter(m -> m.getId().equals(currentChat.getModelId()))
                .findFirst()
                .map(AIModel::getName)
                .orElse(currentChat.getModelId());
            binding.toolbar.setSubtitle(modelName);
        } else {
            binding.toolbar.setTitle(getString(R.string.app_name));
            binding.toolbar.setSubtitle(null);
        }
    }

    @Override
    public void onChatClick(Chat chat) {
        currentChat = chat;
        chatAdapter.setMessages(chat.getMessages());
        binding.drawerLayout.close();
        updateToolbarTitle();
        
        // Сохраняем ID активного чата
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString("last_active_chat_id", chat.getId());
        editor.apply();
    }

    private void showApiKeyDialog() {
        TextInputLayout inputLayout = new TextInputLayout(this);
        TextInputEditText input = new TextInputEditText(this);
        input.setHint("Введите API ключ OpenRouter");
        inputLayout.addView(input);
        inputLayout.setPadding(32, 16, 32, 0);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("API Ключ")
                .setMessage("Пожалуйста, введите ваш API ключ OpenRouter")
                .setView(inputLayout)
                .setCancelable(false)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String key = input.getText().toString().trim();
                    if (!key.isEmpty()) {
                        saveApiKey(key);
                    } else {
                        showApiKeyDialog();
                    }
                });
                
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.white));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.white));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(getResources().getColor(R.color.white));
        });
        dialog.show();
    }

    private void saveApiKey(String key) {
        // Сохраняем ключ только если не используем хардкодный ключ из BuildConfig
        if (!BuildConfig.USE_HARDCODED_KEY) {
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            editor.putString(API_KEY_PREF, key);
            editor.apply();
        }
        apiKey = key;
    }

    private void setupMessageInput() {
        binding.sendButton.setOnClickListener(v -> sendMessage());
        binding.messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    private void setupApi() {
        openRouterApi = ApiClient.getOpenRouterClient().create(OpenRouterApi.class);
    }

    private void sendMessage() {
        // Обновляем apiKey перед отправкой, чтобы учесть возможное изменение настроек
        apiKey = getApiKey();
        
        if (apiKey == null) {
            showError("API ключ не установлен");
            showApiKeyDialog();
            return;
        }

        if (currentChat == null) {
            showError("Создайте новый чат");
            showNewChatDialog();
            return;
        }

        String message = binding.messageInput.getText().toString().trim();
        if (message.isEmpty()) return;

        // Добавляем сообщение пользователя в чат
        ChatMessage userMessage = new ChatMessage(message, true);
        chatAdapter.addMessage(userMessage);
        currentChat.addMessage(userMessage);
        binding.messageInput.setText("");
        
        // Показываем индикатор загрузки
        chatAdapter.setLoading(true);
        
        // Проверяем, является ли сообщение запросом о погоде
        if (isWeatherRequest(message)) {
            processWeatherRequest(message);
            return;
        }

        // Создаем запрос к API
        Map<String, Object> body = new HashMap<>();
        body.put("model", currentChat.getModelId());
        
        ArrayList<Map<String, Object>> messages = new ArrayList<>();

        // Добавляем все предыдущие сообщения для контекста
        for (ChatMessage chatMessage : currentChat.getMessages()) {
            if (!chatMessage.hasAttachment()) {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("role", chatMessage.isUserMessage() ? "user" : "assistant");
                
                // Создаем массив контента для текстового сообщения
                ArrayList<Map<String, Object>> content = new ArrayList<>();
                Map<String, Object> textContent = new HashMap<>();
                textContent.put("type", "text");
                textContent.put("text", chatMessage.getText());
                content.add(textContent);
                
                messageMap.put("content", content);
                messages.add(messageMap);
            }
        }
        
        body.put("messages", messages);
        // Отправляем запрос
        openRouterApi.getChatCompletion(
                "Bearer " + apiKey,
                "https://github.com/your-username/your-repo",
                "Android Chat App",
                body
        ).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                // Скрываем индикатор загрузки
                runOnUiThread(() -> chatAdapter.setLoading(false));
                
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        Log.d("MainActivity", "Response body: " + new Gson().toJson(response.body()));
                        Map<String, Object> responseBody = response.body();
                        ArrayList<Map<String, Object>> choices = (ArrayList<Map<String, Object>>) responseBody.get("choices");
                        
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> choice = choices.get(0);
                            if (choice.containsKey("message")) {
                                Object messageObj = choice.get("message");
                                String content = null;
                                
                                // Handle different message format types
                                if (messageObj instanceof Map) {
                                    Map<String, Object> message = (Map<String, Object>) messageObj;
                                    
                                    // Try to get content from different formats
                                    if (message.containsKey("content")) {
                                        Object contentObj = message.get("content");
                                        
                                        if (contentObj instanceof String) {
                                            // Simple string content
                                            content = (String) contentObj;
                                        } else if (contentObj instanceof ArrayList) {
                                            // Content as array of objects with text fields
                                            ArrayList<Map<String, Object>> contentList = (ArrayList<Map<String, Object>>) contentObj;
                                            StringBuilder sb = new StringBuilder();
                                            
                                            for (Map<String, Object> contentItem : contentList) {
                                                if (contentItem.containsKey("type") && contentItem.get("type").equals("text") 
                                                    && contentItem.containsKey("text")) {
                                                    sb.append(contentItem.get("text").toString());
                                                }
                                            }
                                            
                                            content = sb.toString();
                                        }
                                    }
                                }
                                
                                if (content != null && !content.isEmpty()) {
                                    ChatMessage botMessage = new ChatMessage(content, false);
                                    runOnUiThread(() -> {
                                        chatAdapter.addMessage(botMessage);
                                        currentChat.addMessage(botMessage);
                                        saveChats();
                                    });
                                } else {
                                    showError("Не удалось извлечь текст из ответа сервера");
                                }
                            } else if (choice.containsKey("delta")) {
                                // Handle streaming response format
                                Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                                if (delta.containsKey("content")) {
                                    String content = (String) delta.get("content");
                                    if (content != null && !content.isEmpty()) {
                                        ChatMessage botMessage = new ChatMessage(content, false);
                                        runOnUiThread(() -> {
                                            chatAdapter.addMessage(botMessage);
                                            currentChat.addMessage(botMessage);
                                            saveChats();
                                        });
                                    }
                                } else {
                                    showError("В ответе отсутствует содержимое");
                                }
                            } else {
                                showError("Некорректный формат ответа от сервера (отсутствует message или delta)");
                            }
                        } else {
                            showError("Пустой ответ от сервера (отсутствуют choices)");
                        }
                    } catch (Exception e) {
                        Log.e("MainActivity", "Ошибка при обработке ответа: " + e.getMessage(), e);
                        showError("Ошибка при обработке ответа: " + e.getMessage());
                    }
                } else {
                    try {
                        if (response.errorBody() != null) {
                            String errorBody = response.errorBody().string();
                            showError("Ошибка сервера: " + errorBody);
                        } else {
                            showError("Ошибка сервера: " + response.code());
                        }
                        if (response.code() == 401) {
                            runOnUiThread(() -> {
                                apiKey = null;
                                showApiKeyDialog();
                            });
                        }
                    } catch (Exception e) {
                        showError("Ошибка при обработке ответа сервера");
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Скрываем индикатор загрузки
                runOnUiThread(() -> chatAdapter.setLoading(false));
                showError("Ошибка сети");
            }
        });
    }

    private boolean isWeatherRequest(String message) {
        message = message.toLowerCase();
        return (message.contains("погода") || message.contains("температура") || message.contains("weather")) &&
               (message.contains("какая") || message.contains("какой") || message.contains("узнай") || 
                message.contains("скажи") || message.contains("покажи") || message.contains("what"));
    }
    
    private void processWeatherRequest(String message) {
        // Извлекаем город из запроса, если он указан
        String city = extractCityFromMessage(message);
        
        if (city != null) {
            // Если город указан явно, используем его
            weatherService.getCurrentWeather(city, new WeatherService.WeatherCallback() {
                @Override
                public void onWeatherDataReceived(WeatherData weatherData) {
                    String weatherResponse = weatherService.formatWeatherResponse(weatherData);
                    addBotResponseMessage(weatherResponse);
                }

                @Override
                public void onWeatherError(String errorMessage) {
                    addBotResponseMessage("Не удалось получить данные о погоде: " + errorMessage);
                }
            });
        } else {
            // Если город не указан, используем город по умолчанию
            weatherService.getCurrentWeather(new WeatherService.WeatherCallback() {
                @Override
                public void onWeatherDataReceived(WeatherData weatherData) {
                    String weatherResponse = weatherService.formatWeatherResponse(weatherData);
                    addBotResponseMessage(weatherResponse);
                }

                @Override
                public void onWeatherError(String errorMessage) {
                    addBotResponseMessage("Не удалось получить данные о погоде: " + errorMessage);
                }
            });
        }
    }
    
    private String extractCityFromMessage(String message) {
        message = message.toLowerCase();
        
        // Паттерны для определения города
        String[] patterns = {
            "в городе ([\\wа-яА-Я\\-]+)",
            "в ([\\wа-яА-Я\\-]+)",
            "город ([\\wа-яА-Я\\-]+)",
            "для ([\\wа-яА-Я\\-]+)",
            "погода ([\\wа-яА-Я\\-]+)",
            "([\\wа-яА-Я\\-]+) погода"
        };
        
        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(message);
            if (m.find()) {
                String city = m.group(1);
                // Убираем предлоги, если они попали в название города
                String[] prepositions = {"в", "на", "для", "о", "об", "про"};
                for (String prep : prepositions) {
                    if (city.startsWith(prep + " ")) {
                        city = city.substring(prep.length() + 1);
                    }
                }
                return city;
            }
        }
        
        return null;
    }
    
    private void addBotResponseMessage(String text) {
        ChatMessage botMessage = new ChatMessage(text, false);
        runOnUiThread(() -> {
            chatAdapter.setLoading(false);
            chatAdapter.addMessage(botMessage);
            currentChat.addMessage(botMessage);
            saveChats();
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private void setupAttachButton() {
        binding.attachButton.setOnClickListener(v -> showAttachmentDialog());
    }

    private void showAttachmentDialog() {
        String[] options = {"Сделать фото", "Выбрать файл"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Прикрепить")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            checkCameraPermissionAndLaunch();
                            break;
                        case 1:
                            filePickerLauncher.launch("*/*");
                            break;
                    }
                })
                .show();
    }

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Вместо запроса разрешения напрямую, перенаправляем на экран разрешений
            startActivity(new Intent(this, PermissionRequestActivity.class));
        } else {
            launchCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                // Перенаправляем на экран разрешений, так как пользователь отказал в разрешении
                startActivity(new Intent(this, PermissionRequestActivity.class));
            }
        } else if (requestCode == VOICE_ACTIVATION_PERMISSION_CODE) {
            boolean allPermissionsGranted = true;
            
            // Проверяем все разрешения
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) {
                        allPermissionsGranted = false;
                        Log.d("MainActivity", "Microphone permission denied!");
                    }
                } else {
                    Log.d("MainActivity", "Permission granted: " + permissions[i]);
                }
            }
            
            if (allPermissionsGranted) {
                // Включаем голосовую активацию
                startVoiceActivationService();
            } else {
                // Перенаправляем на экран разрешений
                startActivity(new Intent(this, PermissionRequestActivity.class));
                
                // Отключаем настройку голосовой активации
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putBoolean("voice_activation_enabled", false);
                editor.apply();
                
                // Обновляем состояние кнопки
                updateVoiceActivationButtonState();
            }
        }
    }

    private void launchCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String imageFileName = "JPEG_" + timeStamp + "_";
                File storageDir = getExternalFilesDir(null);
                photoFile = File.createTempFile(imageFileName, ".jpg", storageDir);
            } catch (IOException ex) {
                showError("Ошибка при создании файла");
                return;
            }

            if (photoFile != null) {
                currentPhotoUri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        photoFile);
                cameraLauncher.launch(currentPhotoUri);
            }
        }
    }

    private void handleAttachment(Uri uri, ChatMessage.AttachmentType type) {
        if (currentChat == null) {
            showError("Создайте новый чат");
            showNewChatDialog();
            return;
        }

        if (type == ChatMessage.AttachmentType.IMAGE) {
            showImageCaptionDialog(uri);
        } else {
            addAttachmentMessage("", uri, type);
        }
    }

    private void showImageCaptionDialog(Uri imageUri) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_caption, null);
        ImageView previewImage = dialogView.findViewById(R.id.previewImage);
        TextInputEditText captionInput = dialogView.findViewById(R.id.captionInput);

        Glide.with(this)
                .load(imageUri)
                .into(previewImage);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Добавить подпись")
                .setView(dialogView)
                .setPositiveButton("Отправить", (dialog, which) -> {
                    String caption = captionInput.getText().toString().trim();
                    addAttachmentMessage(caption, imageUri, ChatMessage.AttachmentType.IMAGE);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void addAttachmentMessage(String caption, Uri uri, ChatMessage.AttachmentType type) {
        ChatMessage message = new ChatMessage(caption, true, uri.toString(), type);
        chatAdapter.addMessage(message);
        currentChat.addMessage(message);
        binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        saveChats();

        if (type == ChatMessage.AttachmentType.IMAGE) {
            String base64Image = ImageUtil.uriToBase64(this, uri);
            if (base64Image != null) {
                sendImageToAPI(caption, base64Image);
            }
        }
    }

    private void sendImageToAPI(String caption, String base64Image) {
        if (apiKey == null) {
            showError("API ключ не установлен");
            showApiKeyDialog();
            return;
        }

        // Показываем индикатор загрузки
        chatAdapter.setLoading(true);
        
        // Создаем запрос к API
        Map<String, Object> body = new HashMap<>();
        body.put("model", currentChat.getModelId());
        
        ArrayList<Map<String, Object>> messages = new ArrayList<>();

        // Добавляем все предыдущие сообщения для контекста
        for (ChatMessage chatMessage : currentChat.getMessages()) {
            if (!chatMessage.hasAttachment()) {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("role", chatMessage.isUserMessage() ? "user" : "assistant");
                
                // Создаем массив контента для текстового сообщения
                ArrayList<Map<String, Object>> content = new ArrayList<>();
                Map<String, Object> textContent = new HashMap<>();
                textContent.put("type", "text");
                textContent.put("text", chatMessage.getText());
                content.add(textContent);
                
                messageMap.put("content", content);
                messages.add(messageMap);
            }
        }
        
        // Добавляем новое сообщение с изображением
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("role", "user");
        
        ArrayList<Map<String, Object>> content = new ArrayList<>();
        
        // Добавляем текстовую часть, если есть подпись
        if (!TextUtils.isEmpty(caption)) {
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", caption);
            content.add(textContent);
        }
        
        // Добавляем изображение
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        Map<String, String> imageUrl = new HashMap<>();
        imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
        imageContent.put("image_url", imageUrl);
        content.add(imageContent);
        
        messageMap.put("content", content);
        messages.add(messageMap);
        
        body.put("messages", messages);
        
        // Отправляем запрос
        openRouterApi.getChatCompletion(
                "Bearer " + apiKey,
                "https://github.com/your-username/your-repo",
                "Android Chat App",
                body
        ).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                // Скрываем индикатор загрузки
                runOnUiThread(() -> chatAdapter.setLoading(false));
                
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        Log.d("MainActivity", "Response body: " + new Gson().toJson(response.body()));
                        Map<String, Object> responseBody = response.body();
                        ArrayList<Map<String, Object>> choices = (ArrayList<Map<String, Object>>) responseBody.get("choices");
                        
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> choice = choices.get(0);
                            if (choice.containsKey("message")) {
                                Object messageObj = choice.get("message");
                                String content = null;
                                
                                // Handle different message format types
                                if (messageObj instanceof Map) {
                                    Map<String, Object> message = (Map<String, Object>) messageObj;
                                    
                                    // Try to get content from different formats
                                    if (message.containsKey("content")) {
                                        Object contentObj = message.get("content");
                                        
                                        if (contentObj instanceof String) {
                                            // Simple string content
                                            content = (String) contentObj;
                                        } else if (contentObj instanceof ArrayList) {
                                            // Content as array of objects with text fields
                                            ArrayList<Map<String, Object>> contentList = (ArrayList<Map<String, Object>>) contentObj;
                                            StringBuilder sb = new StringBuilder();
                                            
                                            for (Map<String, Object> contentItem : contentList) {
                                                if (contentItem.containsKey("type") && contentItem.get("type").equals("text") 
                                                    && contentItem.containsKey("text")) {
                                                    sb.append(contentItem.get("text").toString());
                                                }
                                            }
                                            
                                            content = sb.toString();
                                        }
                                    }
                                }
                                
                                if (content != null && !content.isEmpty()) {
                                    ChatMessage botMessage = new ChatMessage(content, false);
                                    runOnUiThread(() -> {
                                        chatAdapter.addMessage(botMessage);
                                        currentChat.addMessage(botMessage);
                                        saveChats();
                                    });
                                } else {
                                    showError("Не удалось извлечь текст из ответа сервера");
                                }
                            } else if (choice.containsKey("delta")) {
                                // Handle streaming response format
                                Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                                if (delta.containsKey("content")) {
                                    String content = (String) delta.get("content");
                                    if (content != null && !content.isEmpty()) {
                                        ChatMessage botMessage = new ChatMessage(content, false);
                                        runOnUiThread(() -> {
                                            chatAdapter.addMessage(botMessage);
                                            currentChat.addMessage(botMessage);
                                            saveChats();
                                        });
                                    }
                                } else {
                                    showError("В ответе отсутствует содержимое");
                                }
                            } else {
                                showError("Некорректный формат ответа от сервера (отсутствует message или delta)");
                            }
                        } else {
                            showError("Пустой ответ от сервера (отсутствуют choices)");
                        }
                    } catch (Exception e) {
                        Log.e("MainActivity", "Ошибка при обработке ответа: " + e.getMessage(), e);
                        showError("Ошибка при обработке ответа: " + e.getMessage());
                    }
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Ошибка сервера";
                        showError(errorBody);
                    } catch (Exception e) {
                        showError("Ошибка при обработке ответа сервера");
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Скрываем индикатор загрузки
                runOnUiThread(() -> chatAdapter.setLoading(false));
                showError("Ошибка сети: " + t.getMessage());
            }
        });
    }

    @Override
    public void onAttachmentClick(ChatMessage message) {
        if (message.hasAttachment()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(message.getAttachmentUri()));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(intent);
            } catch (Exception e) {
                showError("Не удалось открыть файл");
            }
        }
    }

    @Override
    public void onEditMessage(ChatMessage message, int position) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_message, null);
        TextInputEditText input = dialogView.findViewById(R.id.messageInput);
        input.setText(message.getText());

        new MaterialAlertDialogBuilder(this)
                .setTitle("Редактировать сообщение")
                .setView(dialogView)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String newContent = input.getText().toString().trim();
                    if (!newContent.isEmpty()) {
                        message.setText(newContent);
                        chatAdapter.updateMessage(position);
                        saveChats();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    @Override
    public void onDeleteMessage(ChatMessage message, int position) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Удалить сообщение")
                .setMessage("Вы уверены, что хотите удалить это сообщение?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    currentChat.getMessages().remove(position);
                    chatAdapter.removeMessage(position);
                    saveChats();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showRegistrationDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_registration, null);
        TextInputEditText nameInput = dialogView.findViewById(R.id.nameInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton(R.string.action_save, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String name = nameInput.getText().toString().trim();
                if (TextUtils.isEmpty(name)) {
                    nameInput.setError(getString(R.string.error_empty_name));
                    return;
                }
                
                userManager.registerUser(name);
                Toast.makeText(this, 
                    getString(R.string.welcome_message, name),
                    Toast.LENGTH_LONG).show();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void setupSettingsReceiver() {
        settingsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("io.finett.myapplication.SETTINGS_UPDATED".equals(intent.getAction())) {
                    applyAccessibilitySettings();
                }
            }
        };
        registerReceiver(
            settingsReceiver, 
            new IntentFilter("io.finett.myapplication.SETTINGS_UPDATED"),
            Context.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    protected void onAccessibilitySettingsApplied() {
        // Обновляем адаптеры после применения настроек
        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
        if (chatsAdapter != null) {
            chatsAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (settingsReceiver != null) {
            unregisterReceiver(settingsReceiver);
        }
    }

    private void setupAccessibilityButtons() {
        // Настраиваем кнопку переключения темы
        binding.toggleThemeButton.setOnClickListener(v -> {
            showThemeSelectionDialog();
        });
    }

    private void showThemeSelectionDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Выбор темы");

        // Получаем текущую тему
        String currentTheme = accessibilityManager.getAppTheme();
        boolean isHighContrast = accessibilityManager.isHighContrastEnabled();
        
        // Создаем массив опций
        String[] options = {
                getString(R.string.theme_light), 
                getString(R.string.theme_dark),
                getString(R.string.theme_system),
                getString(R.string.high_contrast_theme)
        };
        
        // Определяем выбранный пункт
        int checkedItem;
        if (isHighContrast) {
            checkedItem = 3; // Высококонтрастная тема
        } else {
            switch (currentTheme) {
                case "light":
                    checkedItem = 0;
                    break;
                case "dark":
                    checkedItem = 1;
                    break;
                case "system":
                default:
                    checkedItem = 2;
                    break;
            }
        }
        
        builder.setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
            // Обрабатываем выбор
            switch (which) {
                case 0: // Светлая тема
                    accessibilityManager.setHighContrast(false);
                    accessibilityManager.setAppTheme("light");
                    break;
                case 1: // Темная тема
                    accessibilityManager.setHighContrast(false);
                    accessibilityManager.setAppTheme("dark");
                    break;
                case 2: // Системная тема
                    accessibilityManager.setHighContrast(false);
                    accessibilityManager.setAppTheme("system");
                    break;
                case 3: // Высококонтрастная тема
                    accessibilityManager.setHighContrast(true);
                    break;
            }
            
            // Тема будет применена автоматически через broadcast
            // при вызове setAppTheme или setHighContrast
            
            dialog.dismiss();
        });
        
        builder.setNegativeButton("Отмена", null);
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.white));
        });
        dialog.show();
    }

    private void setupVoiceActivationButton() {
        // Добавляем кнопку для включения/выключения голосовой активации
        binding.voiceActivationButton.setOnClickListener(v -> {
            if (isVoiceActivationServiceRunning()) {
                stopVoiceActivationService();
            } else {
                // Запускаем сервис без проверки разрешений
                startVoiceActivationService();
            }
        });
        
        // Обновляем состояние кнопки
        updateVoiceActivationButtonState();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private boolean checkVoiceActivationPermissions() {
        // Всегда возвращаем true, не будем запрашивать разрешения активно
        return true;
    }

    private void updateVoiceActivationButtonState() {
        boolean isRunning = isVoiceActivationServiceRunning();
        binding.voiceActivationButton.setTextColor(
                ContextCompat.getColor(this, 
                isRunning ? R.color.active_color : R.color.inactive_color));
        binding.voiceActivationButton.setText(
                isRunning ? "Отключить голосовую активацию" : "Включить голосовую активацию");
    }
    
    private boolean isVoiceActivationServiceRunning() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean("voice_activation_enabled", false);
    }
    
    private void startVoiceActivationService() {
        if (!isVoiceActivationServiceRunning()) {
            // Проверяем разрешение на запись звука
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                    != PackageManager.PERMISSION_GRANTED) {
                // Вместо прямого запроса разрешений, перенаправляем на экран разрешений
                startActivity(new Intent(this, PermissionRequestActivity.class));
                return;
            }
            
            // Проверяем разрешение на уведомления для Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                        != PackageManager.PERMISSION_GRANTED) {
                    // Перенаправляем на экран разрешений
                    startActivity(new Intent(this, PermissionRequestActivity.class));
                    return;
                }
            }
            
            Intent serviceIntent = new Intent(this, VoiceActivationService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            
            // Сохраняем состояние сервиса
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            editor.putBoolean("voice_activation_enabled", true);
            editor.apply();
            
            updateVoiceActivationButtonState();
        }
    }
    
    private void stopVoiceActivationService() {
        Intent serviceIntent = new Intent(this, VoiceActivationService.class);
        stopService(serviceIntent);
        
        // Сохраняем состояние сервиса
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean("voice_activation_enabled", false);
        editor.apply();
        
        updateVoiceActivationButtonState();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateVoiceActivationButtonState();
    }

    private void showChatSettingsDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Настройки чата");
        
        // Создаем layout для диалога
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_chat_settings, null);
        
        // Задаем адаптер для списка моделей
        ArrayAdapter<AIModel> modelAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_dropdown_item, availableModels);
        
        android.widget.Spinner defaultModelSpinner = dialogView.findViewById(R.id.defaultModelSpinner);
        defaultModelSpinner.setAdapter(modelAdapter);
        
        // Загружаем текущие настройки
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentModelId = prefs.getString("default_model_id", availableModels.get(0).getId());
        
        // Находим позицию текущей модели
        for (int i = 0; i < availableModels.size(); i++) {
            if (availableModels.get(i).getId().equals(currentModelId)) {
                defaultModelSpinner.setSelection(i);
                break;
            }
        }
        
        // Настраиваем переключатель сохранения истории
        com.google.android.material.switchmaterial.SwitchMaterial saveHistorySwitch = 
            dialogView.findViewById(R.id.saveHistorySwitch);
        saveHistorySwitch.setChecked(prefs.getBoolean("save_chat_history", true));
        
        // Задаем поле для API ключа
        TextInputEditText apiKeyInput = dialogView.findViewById(R.id.apiKeyInput);
        apiKeyInput.setText(apiKey);
        
        builder.setView(dialogView);
        
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            // Сохраняем настройки
            SharedPreferences.Editor editor = prefs.edit();
            
            // Сохраняем выбранную модель
            AIModel selectedModel = (AIModel) defaultModelSpinner.getSelectedItem();
            editor.putString("default_model_id", selectedModel.getId());
            
            // Сохраняем настройку сохранения истории
            editor.putBoolean("save_chat_history", saveHistorySwitch.isChecked());
            
            // Сохраняем API ключ
            String newApiKey = apiKeyInput.getText().toString().trim();
            if (!TextUtils.isEmpty(newApiKey)) {
                editor.putString(API_KEY_PREF, newApiKey);
                apiKey = newApiKey;
            }
            
            editor.apply();
            
            Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("Отмена", null);
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.white));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.white));
        });
        dialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_weather_settings) {
            Intent intent = new Intent(this, WeatherSettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_voice_activation_settings) {
            Intent intent = new Intent(this, VoiceActivationSettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_chat_settings) {
            showChatSettingsDialog();
            return true;
        } else if (id == R.id.action_contact_relations) {
            Intent intent = new Intent(this, io.finett.myapplication.util.ContactRelationActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_accessibility_settings) {
            Intent intent = new Intent(this, AccessibilitySettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_about_me) {
            Intent intent = new Intent(this, AboutMeActivity.class);
            startActivity(intent);
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Handle wake phrase intents
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null) {
            boolean fromWakePhrase = intent.getBooleanExtra("FROM_WAKE_PHRASE", false);
            if (fromWakePhrase) {
                Log.d("MainActivity", "Started from wake phrase");
                
                // Make sure the activity is brought to the front
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setShowWhenLocked(true);
                    setTurnScreenOn(true);
                }
                
                // Optionally, could start voice input or perform other actions
            }
        }
    }

    /**
     * Получение API ключа с учетом настроек сборки
     */
    private String getApiKey() {
        // Проверяем флаг, использовать ли хардкод ключ из BuildConfig
        if (BuildConfig.USE_HARDCODED_KEY) {
            Log.d("MainActivity", "Используется встроенный API ключ OpenRouter из BuildConfig");
            return BuildConfig.DEFAULT_OPENROUTER_API_KEY;
        }
        
        // Получаем сохраненный пользовательский ключ
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String apiKey = prefs.getString(API_KEY_PREF, null);
        
        // Если API ключ не установлен и используем ключ по умолчанию
        if (apiKey == null || apiKey.isEmpty()) {
            Log.w("MainActivity", "Пользовательский ключ не установлен, используется встроенный API ключ OpenRouter из BuildConfig");
            return BuildConfig.DEFAULT_OPENROUTER_API_KEY;
        }
        
        return apiKey;
    }
}