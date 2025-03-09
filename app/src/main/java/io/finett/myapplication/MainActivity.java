package io.finett.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements ChatsAdapter.OnChatClickListener, ChatAdapter.OnAttachmentClickListener {
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
        new AIModel("mistralai/mistral-7b-instruct", "Mistral 7B", "Быстрая и эффективная модель"),
        new AIModel("anthropic/claude-2", "Claude 2", "Мощная модель с широким контекстом"),
        new AIModel("google/gemma-7b-it", "Gemma 7B", "Новая модель от Google"),
        new AIModel("meta-llama/llama-2-70b-chat", "Llama 2 70B", "Большая модель с высокой точностью")
    );
    private List<Chat> chats = new ArrayList<>();

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

        loadSavedChats();
        setupToolbar();
        setupRecyclerViews();
        setupMessageInput();
        setupApi();
        setupNewChatButton();
        setupAttachButton();
        
        // Проверяем наличие API ключа
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        apiKey = prefs.getString(API_KEY_PREF, null);
        
        if (apiKey == null) {
            showApiKeyDialog();
        }
    }

    private void loadSavedChats() {
        chats = StorageUtil.loadChats(this);
        if (chatsAdapter != null) {
            chatsAdapter.setChats(chats);
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
        chatAdapter = new ChatAdapter(this);
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
        builder.show();
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
            binding.toolbar.setSubtitle(
                availableModels.stream()
                    .filter(m -> m.getId().equals(currentChat.getModelId()))
                    .findFirst()
                    .map(AIModel::getName)
                    .orElse("")
            );
        } else {
            binding.toolbar.setTitle("Чат");
            binding.toolbar.setSubtitle(null);
        }
    }

    @Override
    public void onChatClick(Chat chat) {
        currentChat = chat;
        chatAdapter.setMessages(chat.getMessages());
        binding.drawerLayout.close();
        updateToolbarTitle();
    }

    private void showApiKeyDialog() {
        TextInputLayout inputLayout = new TextInputLayout(this);
        TextInputEditText input = new TextInputEditText(this);
        input.setHint("Введите API ключ OpenRouter");
        inputLayout.addView(input);
        inputLayout.setPadding(32, 16, 32, 0);

        new MaterialAlertDialogBuilder(this)
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
                })
                .show();
    }

    private void saveApiKey(String key) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString(API_KEY_PREF, key);
        editor.apply();
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
        openRouterApi = ApiClient.getClient().create(OpenRouterApi.class);
    }

    private void sendMessage() {
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

        ChatMessage userMessage = new ChatMessage(message, true);
        chatAdapter.addMessage(userMessage);
        currentChat.addMessage(userMessage);
        binding.messageInput.setText("");

        // Создаем запрос к API
        Map<String, Object> body = new HashMap<>();
        body.put("model", currentChat.getModelId());
        
        ArrayList<Map<String, Object>> messages = new ArrayList<>();

        // Добавляем все предыдущие сообщения для контекста
        for (ChatMessage chatMessage : currentChat.getMessages()) {
            if (!chatMessage.hasAttachment()) {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("role", chatMessage.isUser() ? "user" : "assistant");
                
                // Создаем массив контента для текстового сообщения
                ArrayList<Map<String, Object>> content = new ArrayList<>();
                Map<String, Object> textContent = new HashMap<>();
                textContent.put("type", "text");
                textContent.put("text", chatMessage.getContent());
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
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        ArrayList<Map<String, Object>> choices = (ArrayList<Map<String, Object>>) response.body().get("choices");
                        Map<String, Object> choice = choices.get(0);
                        Map<String, String> message = (Map<String, String>) choice.get("message");
                        String content = message.get("content");
                        
                        ChatMessage botMessage = new ChatMessage(content, false);
                        runOnUiThread(() -> {
                            chatAdapter.addMessage(botMessage);
                            currentChat.addMessage(botMessage);
                            binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                            saveChats();
                        });
                    } catch (Exception e) {
                        showError("Ошибка при обработке ответа");
                    }
                } else {
                    showError("Ошибка при получении ответа");
                    if (response.code() == 401) {
                        runOnUiThread(() -> {
                            apiKey = null;
                            showApiKeyDialog();
                        });
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                showError("Ошибка сети");
            }
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
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    PERMISSION_REQUEST_CODE);
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
                showError("Необходимо разрешение на использование камеры");
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
        Map<String, Object> body = new HashMap<>();
        body.put("model", currentChat.getModelId());
        
        ArrayList<Map<String, Object>> messages = new ArrayList<>();
        
        // Создаем сообщение пользователя с изображением
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        
        // Создаем массив контента
        ArrayList<Map<String, Object>> content = new ArrayList<>();
        
        // Добавляем текст, если есть подпись
        if (!caption.isEmpty()) {
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
        imageUrl.put("detail", "auto");
        imageContent.put("image_url", imageUrl);
        content.add(imageContent);
        
        userMessage.put("content", content);
        messages.add(userMessage);
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
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        ArrayList<Map<String, Object>> choices = (ArrayList<Map<String, Object>>) response.body().get("choices");
                        Map<String, Object> choice = choices.get(0);
                        Map<String, String> message = (Map<String, String>) choice.get("message");
                        String content = message.get("content");
                        
                        ChatMessage botMessage = new ChatMessage(content, false);
                        runOnUiThread(() -> {
                            chatAdapter.addMessage(botMessage);
                            currentChat.addMessage(botMessage);
                            binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                            saveChats();
                        });
                    } catch (Exception e) {
                        showError("Ошибка при обработке ответа");
                    }
                } else {
                    showError("Ошибка при получении ответа");
                    if (response.code() == 401) {
                        runOnUiThread(() -> {
                            apiKey = null;
                            showApiKeyDialog();
                        });
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                showError("Ошибка сети");
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
}