package io.finett.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.finett.myapplication.adapter.ChatAdapter;
import io.finett.myapplication.api.ApiClient;
import io.finett.myapplication.api.OpenRouterApi;
import io.finett.myapplication.databinding.ActivityVoiceChatBinding;
import io.finett.myapplication.model.ChatMessage;
import io.finett.myapplication.util.CommunicationManager;
import io.finett.myapplication.util.PromptManager;
import io.finett.myapplication.model.SystemPrompt;
import io.finett.myapplication.util.CommandProcessor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VoiceChatActivity extends AppCompatActivity implements RecognitionListener, 
        ChatAdapter.OnAttachmentClickListener, TextToSpeech.OnInitListener {
    private ActivityVoiceChatBinding binding;
    private ChatAdapter chatAdapter;
    private OpenRouterApi openRouterApi;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean isListening = false;
    private boolean isSpeaking = false;
    private static final String MODEL_ID = "anthropic/claude-3-opus";
    private String apiKey;
    private static final int PERMISSION_REQUEST_CODE = 123;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVoiceChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        promptManager = new PromptManager(this);
        apiKey = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                .getString(MainActivity.API_KEY_PREF, null);

        setupToolbar();
        setupRecyclerView();
        setupVoiceButton();
        setupApi();
        checkPermissionAndInitRecognizer();
        initTextToSpeech();
        binding.toolbar.inflateMenu(R.menu.voice_chat_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_manage_prompts) {
                showPromptManagementDialog();
                return true;
            }
            return false;
        });
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
        } else if (CommunicationManager.handlePermissionResult(requestCode, permissions, grantResults)) {
            // Разрешение для звонка или SMS получено
            Toast.makeText(this, "Разрешение получено", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(this);
        binding.chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.chatRecyclerView.setAdapter(chatAdapter);
    }

    private void setupVoiceButton() {
        binding.voiceButton.setOnClickListener(v -> {
            if (isSpeaking) {
                if (textToSpeech != null) {
                    textToSpeech.stop();
                    isSpeaking = false;
                }
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                checkPermissionAndInitRecognizer();
            } else if (!isListening) {
                startListening();
            } else {
                stopListening();
            }
        });
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
            
            // Инициализируем CommandProcessor после успешной инициализации TextToSpeech
            commandProcessor = new CommandProcessor(this, textToSpeech);
        } else {
            showError("Ошибка инициализации синтеза речи");
        }
    }

    private void startListening() {
        if (isListening) return;
        
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);

        try {
            speechRecognizer.startListening(intent);
            isListening = true;
            isProcessingSpeech = false;
            lastVoiceTime = System.currentTimeMillis();
            binding.voiceButton.setImageResource(R.drawable.ic_stop);
        } catch (Exception e) {
            showError("Не удалось запустить распознавание речи");
        }
    }

    private void stopListening() {
        if (!isListening) return;
        
        binding.voiceButton.removeCallbacks(speechTimeoutRunnable);
        speechRecognizer.stopListening();
        isListening = false;
        isProcessingSpeech = false;
        binding.voiceButton.setImageResource(R.drawable.ic_mic);
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
        binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

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
            systemTextContent.put("text", activePrompt.getContent());
            systemContent.add(systemTextContent);
            systemMessage.put("content", systemContent);
            messages.add(systemMessage);
        }
        
        // Добавляем сообщение пользователя
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("role", "user");
        ArrayList<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", message);
        content.add(textContent);
        messageMap.put("content", content);
        messages.add(messageMap);
        
        body.put("messages", messages);

        openRouterApi.getChatCompletion(
                "Bearer " + apiKey,
                "https://github.com/your-username/your-repo",
                "Android Voice Chat App",
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
                            binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                            speakText(content);
                        });
                    } catch (Exception e) {
                        showError("Ошибка при обработке ответа");
                    }
                } else {
                    showError("Ошибка при получении ответа");
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                showError("Ошибка сети");
            }
        });
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
            binding.voiceButton.postDelayed(speechTimeoutRunnable, SPEECH_TIMEOUT_MILLIS);
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
        if (error == SpeechRecognizer.ERROR_NO_MATCH || 
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            // Игнорируем эти ошибки, так как они часто возникают при нормальной работе
            stopListening();
            if (!isSpeaking) {
                startListening(); // Перезапускаем прослушивание, если не идет озвучка
            }
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
            binding.voiceButton.removeCallbacks(speechTimeoutRunnable);
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
            // Пробуем обработать как команду
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
    protected void onPause() {
        super.onPause();
        stopListening();
        if (textToSpeech != null) {
            textToSpeech.stop();
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
            contentInput.setText(prompt.getContent());
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
                prompt.setContent(content);
                promptManager.savePrompt(prompt);
            }
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }
} 