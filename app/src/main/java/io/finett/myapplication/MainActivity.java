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
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.HorizontalScrollView;

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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
import io.finett.myapplication.util.SuggestionGenerator;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import io.finett.myapplication.base.BaseAccessibilityActivity;
import com.google.gson.Gson;
// Используем правильный путь к AssistantLauncher
// import io.finett.myapplication.util.AssistantLauncher;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import okhttp3.ResponseBody;

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
    private BroadcastReceiver assistantLaunchReceiver;
    private HorizontalScrollView suggestionsScrollView;
    private ChipGroup suggestionsChipGroup;
    private SuggestionGenerator suggestionGenerator;

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
            startActivity(new Intent(this, PermissionRequestActivity.class));
            finish();
            return;
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_CONTACTS},
                103 // ContactsManager.PERMISSION_REQUEST_CONTACTS
            );
            Log.d("MainActivity", "Requesting contacts permission");
        } else {
            Log.d("MainActivity", "Contacts permission already granted");
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
        
        // Автоматически запускаем VoiceActivationService при старте приложения
        startVoiceActivationService();
        
        // Check for wake phrase intent
        handleIntent(getIntent());

        // Регистрируем слушателя событий запуска ассистента
        registerAssistantLaunchListener();

        setupViews();
        
        // Инициализируем генератор подсказок
        suggestionGenerator = new SuggestionGenerator(this, suggestions -> {
            Log.d("MainActivity", "Получен callback с подсказками: " + suggestions.size());
            runOnUiThread(() -> updateSuggestionsUI(suggestions));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        String message = binding.messageInput.getText().toString().trim();
        if (message.isEmpty()) {
            return;
        }

        // Очищаем поле ввода
        binding.messageInput.setText("");

        // Если текущий чат не выбран, создаем новый
        if (currentChat == null) {
            createNewChat("Новый чат", availableModels.get(0).getId());
        }

        // Добавляем сообщение пользователя
        ChatMessage userMessage = new ChatMessage(message, true);
        chatAdapter.addMessage(userMessage);
        currentChat.addMessage(userMessage);
        saveChats();
        
        // Показываем индикатор загрузки
        chatAdapter.setLoading(true);
        
        // Проверяем, не запрос ли это о погоде
        if (isWeatherRequest(message)) {
            processWeatherRequest(message);
            return;
        }

        // Если ключ API не настроен, показываем диалог
        if (apiKey == null || apiKey.isEmpty()) {
            chatAdapter.setLoading(false);
            showApiKeyDialog();
            return;
        }

        // Подготавливаем запрос
        Map<String, Object> body = new HashMap<>();
        body.put("model", currentChat.getModelId());
        body.put("temperature", 0.7);
        body.put("top_p", 0.95);
        body.put("max_tokens", 800);
        
        // Убираем параметр для стриминга
        body.put("stream", false);

        // Создаем массив сообщений
        List<Map<String, Object>> messages = new ArrayList<>();

        // Добавляем системный промпт
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "Ты полезный AI ассистент. Отвечай кратко и информативно.");
        messages.add(systemMessage);

        // Добавляем историю сообщений (максимум 10 последних сообщений)
        int historySize = Math.min(currentChat.getMessages().size(), 10);
        for (int i = currentChat.getMessages().size() - historySize; i < currentChat.getMessages().size(); i++) {
            ChatMessage historyMessage = currentChat.getMessages().get(i);
            Map<String, Object> messageMap = new HashMap<>();
            messageMap.put("role", historyMessage.isUser() ? "user" : "assistant");
            
            // Проверяем, есть ли у сообщения вложения
            if (historyMessage.hasAttachment()) {
                List<Map<String, Object>> content = new ArrayList<>();
                
                // Добавляем текст
                Map<String, Object> textContent = new HashMap<>();
                textContent.put("type", "text");
                textContent.put("text", historyMessage.getText());
                content.add(textContent);
                
                messageMap.put("content", content);
            } else {
                messageMap.put("content", historyMessage.getText());
            }
            
            messages.add(messageMap);
        }
        
        body.put("messages", messages);

        // Используем API для получения ответа (без стриминга)
        Call<Map<String, Object>> call = openRouterApi.getChatCompletion(
                "Bearer " + apiKey,
                "https://github.com/your-username/your-repo",
                "Android Chat App",
                body
        );
        
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        chatAdapter.setLoading(false);
                        try {
                            String errorBody = response.errorBody() != null ? response.errorBody().string() : "Ошибка сервера";
                            showError("Ошибка API: " + response.code() + " " + errorBody);
                            
                            if (response.code() == 401) {
                                apiKey = null;
                                showApiKeyDialog();
                            }
                        } catch (Exception e) {
                            showError("Ошибка при обработке ответа сервера: " + e.getMessage());
                        }
                    });
                    return;
                }
                
                try {
                    // Обрабатываем ответ
                    Map<String, Object> responseBody = response.body();
                    Log.d("MainActivity", "Получен ответ от API: " + responseBody);
                    
                    if (responseBody != null && responseBody.containsKey("choices")) {
                        ArrayList<Map<String, Object>> choices = (ArrayList<Map<String, Object>>) responseBody.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> choice = choices.get(0);
                            if (choice.containsKey("message")) {
                                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                                if (message.containsKey("content")) {
                                    String content = (String) message.get("content");
                                    Log.d("MainActivity", "Извлечено содержимое ответа: " + content);
                                    
                                    // Добавляем сообщение ассистента в чат
                                    runOnUiThread(() -> {
                                        try {
                                            chatAdapter.setLoading(false);
                                            
                                            if (content != null && !content.isEmpty()) {
                                                ChatMessage botMessage = new ChatMessage(content, false);
                                                Log.d("MainActivity", "Добавляем сообщение в чат: " + content);
                                                chatAdapter.addMessage(botMessage);
                                                currentChat.addMessage(botMessage);
                                                
                                                // Убедимся, что прокрутка работает
                                                binding.chatRecyclerView.post(() -> {
                                                    try {
                                                        binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                                                    } catch (Exception e) {
                                                        Log.e("MainActivity", "Ошибка при прокрутке: " + e.getMessage(), e);
                                                    }
                                                });
                                                
                                                // Сохраняем чаты
                                                saveChats();
                                                
                                                // Генерируем подсказки на основе ответа
                                                generateSuggestions(content);
                                            } else {
                                                Log.e("MainActivity", "Пустой ответ от модели");
                                                showError("Модель вернула пустой ответ");
                                            }
                                        } catch (Exception e) {
                                            Log.e("MainActivity", "Ошибка при обновлении UI: " + e.getMessage(), e);
                                            showError("Ошибка при обновлении UI: " + e.getMessage());
                                        }
                                    });
                                    return;
                                }
                            }
                        }
                    }
                    
                    // Если не удалось извлечь ответ
                    runOnUiThread(() -> {
                        chatAdapter.setLoading(false);
                        showError("Не удалось получить ответ от модели");
                    });
                    
                } catch (Exception e) {
                    Log.e("MainActivity", "Ошибка при обработке ответа: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        chatAdapter.setLoading(false);
                        showError("Ошибка при обработке ответа: " + e.getMessage());
                    });
                }
            }
            
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e("MainActivity", "Ошибка сети: " + t.getMessage());
                runOnUiThread(() -> {
                    chatAdapter.setLoading(false);
                    showError("Ошибка сети: " + t.getMessage());
                });
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
            
            // Генерируем подсказки на основе ответа бота
            generateSuggestions(text);
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
                Toast.makeText(this, "Разрешение на камеру отклонено", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 103) { // ContactsManager.PERMISSION_REQUEST_CONTACTS
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешение на доступ к контактам предоставлено", Toast.LENGTH_SHORT).show();
                // Обновляем интерфейс или выполняем действие, требующее доступа к контактам
            } else {
                Toast.makeText(this, "Разрешение на доступ к контактам отклонено. Некоторые функции будут недоступны.", Toast.LENGTH_LONG).show();
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
        // Если ключ API не настроен, показываем диалог
        if (apiKey == null || apiKey.isEmpty()) {
            chatAdapter.setLoading(false);
            showApiKeyDialog();
            return;
        }

        // Показываем индикатор загрузки
        chatAdapter.setLoading(true);
        
        // Подготавливаем запрос
        Map<String, Object> body = new HashMap<>();
        body.put("model", currentChat.getModelId());
        body.put("temperature", 0.7);
        body.put("top_p", 0.95);
        body.put("max_tokens", 800);
        
        // Убираем параметр для стриминга
        body.put("stream", false);

        // Создаем массив сообщений
        List<Map<String, Object>> messages = new ArrayList<>();

        // Добавляем системный промпт для обработки изображений
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "Ты помощник, который может анализировать изображения. Опиши содержимое изображения подробно.");
        messages.add(systemMessage);

        // Добавляем сообщение пользователя с изображением
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("role", "user");
        
        // Создаем контент сообщения
        List<Map<String, Object>> content = new ArrayList<>();
        
        // Добавляем текст (подпись к изображению)
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", caption);
        content.add(textContent);
        
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
        
        // Показываем индикатор загрузки
        chatAdapter.setLoading(true);
        
        // Используем API для получения ответа (без стриминга)
        Call<Map<String, Object>> call = openRouterApi.getChatCompletion(
                "Bearer " + apiKey,
                "https://github.com/your-username/your-repo",
                "Android Chat App",
                body
        );
        
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        chatAdapter.setLoading(false);
                        try {
                            String errorBody = response.errorBody() != null ? response.errorBody().string() : "Ошибка сервера";
                            showError("Ошибка API: " + response.code() + " " + errorBody);
                            
                            if (response.code() == 401) {
                                apiKey = null;
                                showApiKeyDialog();
                            }
                        } catch (Exception e) {
                            showError("Ошибка при обработке ответа сервера: " + e.getMessage());
                        }
                    });
                    return;
                }
                
                try {
                    // Обрабатываем ответ
                    Map<String, Object> responseBody = response.body();
                    Log.d("MainActivity", "Получен ответ от API для изображения: " + responseBody);
                    
                    if (responseBody != null && responseBody.containsKey("choices")) {
                        ArrayList<Map<String, Object>> choices = (ArrayList<Map<String, Object>>) responseBody.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> choice = choices.get(0);
                            if (choice.containsKey("message")) {
                                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                                if (message.containsKey("content")) {
                                    String content = (String) message.get("content");
                                    Log.d("MainActivity", "Извлечено содержимое ответа для изображения: " + content);
                                    
                                    // Добавляем сообщение ассистента в чат
                                    runOnUiThread(() -> {
                                        try {
                                            chatAdapter.setLoading(false);
                                            
                                            if (content != null && !content.isEmpty()) {
                                                ChatMessage botMessage = new ChatMessage(content, false);
                                                Log.d("MainActivity", "Добавляем сообщение в чат (изображение): " + content);
                                                chatAdapter.addMessage(botMessage);
                                                currentChat.addMessage(botMessage);
                                                
                                                // Убедимся, что прокрутка работает
                                                binding.chatRecyclerView.post(() -> {
                                                    try {
                                                        binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                                                    } catch (Exception e) {
                                                        Log.e("MainActivity", "Ошибка при прокрутке: " + e.getMessage(), e);
                                                    }
                                                });
                                                
                                                // Сохраняем чаты
                                                saveChats();
                                                
                                                // Генерируем подсказки на основе ответа
                                                generateSuggestions(content);
                                            } else {
                                                Log.e("MainActivity", "Пустой ответ от модели для изображения");
                                                showError("Модель вернула пустой ответ для изображения");
                                            }
                                        } catch (Exception e) {
                                            Log.e("MainActivity", "Ошибка при обновлении UI (изображение): " + e.getMessage(), e);
                                            showError("Ошибка при обновлении UI: " + e.getMessage());
                                        }
                                    });
                                    return;
                                }
                            }
                        }
                    }
                    
                    // Если не удалось извлечь ответ
                    runOnUiThread(() -> {
                        chatAdapter.setLoading(false);
                        showError("Не удалось получить ответ от модели для изображения");
                    });
                    
                } catch (Exception e) {
                    Log.e("MainActivity", "Ошибка при обработке ответа для изображения: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        chatAdapter.setLoading(false);
                        showError("Ошибка при обработке ответа: " + e.getMessage());
                    });
                }
            }
            
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e("MainActivity", "Ошибка сети при отправке изображения: " + t.getMessage());
                runOnUiThread(() -> {
                    chatAdapter.setLoading(false);
                    showError("Ошибка сети: " + t.getMessage());
                });
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
        // Отменяем регистрацию слушателя запуска ассистента
        if (assistantLaunchReceiver != null) {
            io.finett.myapplication.AssistantMonitor.unregisterAssistantListener(this, assistantLaunchReceiver);
            assistantLaunchReceiver = null;
        }
        
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
        
        // Опции для выбора
        String[] options = {"Светлая тема", "Темная тема", "Системная тема", "Высококонтрастная тема"};
        
        // Устанавливаем отмеченный пункт в зависимости от текущей темы
        int checkedItem = 2; // По умолчанию - системная тема
        if (accessibilityManager != null) {
            boolean isHighContrast = accessibilityManager.isHighContrastEnabled();
            if (isHighContrast) {
                checkedItem = 3; // Высококонтрастная
            } else {
                String currentTheme = accessibilityManager.getAppTheme();
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

    /**
     * Показывает диалог настроек чата
     */
    private void showChatSettingsDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Настройки чата");
        
        // Создаем layout для диалога
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 0);
        
        // Получаем текущие настройки
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Переключатель звуковых уведомлений
        CheckBox soundNotificationsCheckbox = new CheckBox(this);
        soundNotificationsCheckbox.setText("Звуковые уведомления");
        boolean soundNotificationsEnabled = prefs.getBoolean("sound_notifications_enabled", true);
        soundNotificationsCheckbox.setChecked(soundNotificationsEnabled);
        layout.addView(soundNotificationsCheckbox);
        
        // Переключатель автопрокрутки
        CheckBox autoscrollCheckbox = new CheckBox(this);
        autoscrollCheckbox.setText("Автоматическая прокрутка");
        boolean autoscrollEnabled = prefs.getBoolean("autoscroll_enabled", true);
        autoscrollCheckbox.setChecked(autoscrollEnabled);
        layout.addView(autoscrollCheckbox);
        
        // Переключатель режима компактного отображения сообщений
        CheckBox compactModeCheckbox = new CheckBox(this);
        compactModeCheckbox.setText("Компактный режим");
        boolean compactModeEnabled = prefs.getBoolean("compact_mode_enabled", false);
        compactModeCheckbox.setChecked(compactModeEnabled);
        layout.addView(compactModeCheckbox);
        
        // Переключатель сохранения истории
        CheckBox saveHistoryCheckbox = new CheckBox(this);
        saveHistoryCheckbox.setText("Сохранять историю чатов");
        boolean saveHistoryEnabled = prefs.getBoolean("save_history_enabled", true);
        saveHistoryCheckbox.setChecked(saveHistoryEnabled);
        layout.addView(saveHistoryCheckbox);
        
        // Добавляем layout в диалог
        builder.setView(layout);
        
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            // Сохраняем настройки
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("sound_notifications_enabled", soundNotificationsCheckbox.isChecked());
            editor.putBoolean("autoscroll_enabled", autoscrollCheckbox.isChecked());
            editor.putBoolean("compact_mode_enabled", compactModeCheckbox.isChecked());
            editor.putBoolean("save_history_enabled", saveHistoryCheckbox.isChecked());
            editor.apply();
            
            // Применяем настройки к текущему чату
            if (chatAdapter != null) {
                chatAdapter.setCompactMode(compactModeCheckbox.isChecked());
                chatAdapter.setAutoscrollEnabled(autoscrollCheckbox.isChecked());
                chatAdapter.notifyDataSetChanged();
            }
            
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
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        
        if (itemId == R.id.action_unified_settings) {
            Intent intent = new Intent(this, UnifiedSettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_assistant_settings) {
            // Открытие настроек системного ассистента
            openAssistantSettings();
            return true;
        } else if (itemId == R.id.action_accessibility_settings) {
            Intent intent = new Intent(this, AccessibilitySettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_chat_settings) {
            showChatSettingsDialog();
            return true;
        } else if (itemId == R.id.action_contact_relations) {
            Intent intent = new Intent(this, io.finett.myapplication.util.ContactRelationActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_about_me) {
            Intent intent = new Intent(this, AboutMeActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_voice_activation_settings) {
            Intent intent = new Intent(this, VoiceActivationSettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_weather_settings) {
            Intent intent = new Intent(this, WeatherSettingsActivity.class);
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

    /**
     * Открывает системные настройки для выбора приложения-ассистента по умолчанию
     */
    private void openAssistantSettings() {
        try {
            // Отображаем статистику запусков ассистента
            int launchCount = io.finett.myapplication.AssistantSettings.getLaunchCount(this);
            long lastLaunchTime = io.finett.myapplication.AssistantSettings.getLastLaunchTime(this);
            
            String lastLaunchStr = "Никогда";
            if (lastLaunchTime > 0) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault());
                lastLaunchStr = sdf.format(new java.util.Date(lastLaunchTime));
            }
            
            // Показываем диалог с инструкциями перед открытием системных настроек
            showAssistantSetupInstructionsDialog();
            
            Toast.makeText(this, 
                    "Статистика ассистента:\n" +
                    "Количество запусков: " + launchCount + "\n" +
                    "Последний запуск: " + lastLaunchStr,
                    Toast.LENGTH_LONG).show();
            
            // Для Android 5.0 (API 21) и выше
            Intent intent = new Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("MainActivity", "Ошибка при открытии настроек ассистента", e);
            try {
                // Альтернативный вариант - открыть общие настройки приложений
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS);
                startActivity(intent);
                
                Toast.makeText(this, 
                        "Найдите Алан в списке приложений и установите его как ассистент по умолчанию",
                        Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                Log.e("MainActivity", "Не удалось открыть настройки приложений", e2);
                Toast.makeText(this, 
                        "Не удалось открыть настройки. Перейдите в Настройки -> Приложения -> Приложения по умолчанию -> Цифровой помощник",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Показывает диалог с инструкциями по настройке системного ассистента
     */
    private void showAssistantSetupInstructionsDialog() {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Настройка системного ассистента");
        
        // Создаем ScrollView для прокрутки длинного текста
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        int padding = (int) (getResources().getDisplayMetrics().density * 16);
        textView.setPadding(padding, padding, padding, padding);
        
        String instructions = 
                "Для установки приложения в качестве ассистента по умолчанию:\n\n" +
                "1. В открывшихся настройках найдите пункт \"Приложение для голосового ввода\" " +
                "или \"Цифровой помощник\"\n\n" +
                "2. Выберите \"Алан\" из списка доступных ассистентов\n\n" +
                "3. На некоторых устройствах может потребоваться дополнительное подтверждение\n\n" +
                "4. После активации, вы можете вызвать ассистента:\n" +
                "   • Зажав кнопку Home (на устройствах с кнопками)\n" +
                "   • Свайпом из нижнего угла (на устройствах с жестами)\n" +
                "   • Произнеся \"Привет, Алан\" (если включена голосовая активация)\n\n" +
                "5. Для более удобного использования рекомендуется также настроить автоматический запуск при включении устройства в настройках приложения\n\n" +
                "Примечание: на некоторых устройствах (особенно Huawei, Xiaomi) могут быть дополнительные ограничения, требующие отключения оптимизации батареи для приложения.";
        
        textView.setText(instructions);
        scrollView.addView(textView);
        builder.setView(scrollView);
        
        builder.setPositiveButton("Продолжить", (dialog, which) -> {
            dialog.dismiss();
        });
        
        builder.setNegativeButton("Отмена", (dialog, which) -> {
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Регистрирует слушателя событий запуска ассистента
     */
    private void registerAssistantLaunchListener() {
        // Регистрируем слушателя через AssistantMonitor
        assistantLaunchReceiver = io.finett.myapplication.AssistantMonitor.registerAssistantListener(
                this, 
                () -> {
                    // Обработка события запуска ассистента
                    Log.d("MainActivity", "Получено уведомление о запуске ассистента");
                    
                    // Показываем короткое сообщение
                    runOnUiThread(() -> {
                        Toast.makeText(
                                MainActivity.this, 
                                "Ассистент запущен!", 
                                Toast.LENGTH_SHORT).show();
                    });
                });
    }

    /**
     * Запускает сервис голосовой активации, если он не запущен
     */
    private void startVoiceActivationService() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean voiceActivationEnabled = prefs.getBoolean("voice_activation_enabled", false);
            
            if (voiceActivationEnabled) {
                Log.d("MainActivity", "Автоматический запуск сервиса голосовой активации");
                Intent serviceIntent = new Intent(this, VoiceActivationService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Ошибка при запуске сервиса голосовой активации", e);
        }
    }

    private void setupViews() {
        // Настройка подсказок
        suggestionsScrollView = findViewById(R.id.suggestionsScrollView);
        suggestionsChipGroup = findViewById(R.id.suggestionsChipGroup);
        
        if (suggestionsScrollView == null) {
            Log.e("MainActivity", "ОШИБКА: suggestionsScrollView не найден в layout!");
        } else {
            // Установим видимость контейнера подсказок по умолчанию - VISIBLE
            suggestionsScrollView.setVisibility(View.VISIBLE);
            // Задаем фоновый цвет программно для уверенности, что он применен
            suggestionsScrollView.setBackgroundResource(R.drawable.suggestions_background);
            Log.d("MainActivity", "Контейнер подсказок инициализирован и видим");
        }
        
        if (suggestionsChipGroup == null) {
            Log.e("MainActivity", "ОШИБКА: suggestionsChipGroup не найден в layout!");
        }
    }

    /**
     * Генерирует подсказки на основе контекста чата
     */
    private void generateSuggestions(String lastBotMessage) {
        Log.d("MainActivity", "Генерация подсказок для текста: " + (lastBotMessage.length() > 50 ? lastBotMessage.substring(0, 50) + "..." : lastBotMessage));
        
        // Очищаем предыдущие подсказки и показываем индикатор загрузки
        runOnUiThread(() -> {
            suggestionsChipGroup.removeAllViews();
            
            // Добавляем "загрузочный" чип
            Chip loadingChip = new Chip(this);
            loadingChip.setText("Генерирую подсказки...");
            loadingChip.setClickable(false);
            loadingChip.setCheckable(false);
            suggestionsChipGroup.addView(loadingChip);
            
            // Показываем контейнер с подсказками
            suggestionsScrollView.setVisibility(View.VISIBLE);
            Log.d("MainActivity", "Показан индикатор загрузки подсказок");
        });
        
        // Запускаем генерацию подсказок
        suggestionGenerator.generateSuggestions(lastBotMessage);
    }
    
    /**
     * Обновляет UI с новыми подсказками
     */
    private void updateSuggestionsUI(List<String> suggestions) {
        Log.d("MainActivity", "Получены подсказки: " + suggestions.size());
        
        // Убедимся, что выполняем на главном потоке
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> updateSuggestionsUI(suggestions));
            return;
        }
        
        try {
            // Очищаем текущие подсказки
            suggestionsChipGroup.removeAllViews();
            
            if (suggestions != null && !suggestions.isEmpty()) {
                // Отображаем контейнер с подсказками
                suggestionsScrollView.setVisibility(View.VISIBLE);
                
                // Создаем чипы для каждой подсказки
                for (String suggestion : suggestions) {
                    if (suggestion == null || suggestion.trim().isEmpty()) {
                        continue;
                    }
                    
                    Log.d("MainActivity", "Добавление подсказки: " + suggestion);
                    
                    Chip chip = new Chip(this);
                    chip.setText(suggestion);
                    chip.setClickable(true);
                    chip.setCheckable(false);
                    
                    // Обработчик нажатия на подсказку
                    chip.setOnClickListener(v -> {
                        // Отправляем выбранную подсказку как сообщение пользователя
                        binding.messageInput.setText(suggestion);
                        sendMessage();
                    });
                    
                    suggestionsChipGroup.addView(chip);
                }
                
                // Проверяем видимость после добавления
                if (suggestionsScrollView.getVisibility() != View.VISIBLE) {
                    Log.w("MainActivity", "Контейнер подсказок не виден после обновления!");
                    suggestionsScrollView.setVisibility(View.VISIBLE);
                }
                
                // Прокручиваем к началу, чтобы показать первую подсказку
                suggestionsScrollView.fullScroll(HorizontalScrollView.FOCUS_LEFT);
                
            } else {
                // Скрываем контейнер, если подсказок нет
                Log.d("MainActivity", "Нет подсказок, скрываем контейнер");
                suggestionsScrollView.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Ошибка при обновлении подсказок: " + e.getMessage(), e);
            // Пытаемся восстановиться - скрываем контейнер
            suggestionsScrollView.setVisibility(View.GONE);
        }
    }
}