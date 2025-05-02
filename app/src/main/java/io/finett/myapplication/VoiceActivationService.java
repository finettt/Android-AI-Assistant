package io.finett.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoiceActivationService extends Service {
    private static final String TAG = "VoiceActivationService";
    private static final String WAKE_PHRASE = "привет алан";
    private static final String WAKE_PHRASE_ALT = "алан";
    private static final String WAKE_PHRASE_ALT2 = "привет! алан";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "VoiceActivationChannel";
    private static final String KEY_WAKE_PHRASES = "wake_phrases";
    private static final String KEY_WAKE_SENSITIVITY = "wake_sensitivity";
    private static final String KEY_OPEN_VOICE_CHAT = "open_voice_chat_on_wake";
    private static final String KEY_PLAY_SOUND = "play_activation_sound";
    private static final String KEY_WAKE_SCREEN = "wake_screen_on_activation";
    private static final float DEFAULT_CONFIDENCE_THRESHOLD = 0.5f;
    
    // Список стандартных фраз активации
    private static final ArrayList<String> DEFAULT_WAKE_PHRASES = new ArrayList<>(
            Arrays.asList(
                    WAKE_PHRASE,         // привет алан 
                    WAKE_PHRASE_ALT,     // алан
                    WAKE_PHRASE_ALT2,    // привет! алан
                    "привет алан!",
                    "эй алан",
                    "алан привет"
            )
    );
    
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private final AtomicBoolean isListening = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable restartRecognition = this::startVoiceRecognition;
    private PowerManager.WakeLock wakeLock;
    private SoundPool soundPool;
    private int activationSoundId = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initializeSpeechRecognizer();
        initSoundPool();
        
        try {
            // Получаем WakeLock для предотвращения засыпания процесса
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, 
                        "VoiceActivation:WakeLock");
                if (!wakeLock.isHeld()) {
                    wakeLock.acquire(10*60*1000L /*10 минут*/);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring wakelock", e);
        }
    }

    private void initSoundPool() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                
                soundPool = new SoundPool.Builder()
                        .setMaxStreams(1)
                        .setAudioAttributes(audioAttributes)
                        .build();
            } else {
                soundPool = new SoundPool(1, AudioManager.STREAM_SYSTEM, 0);
            }
            
            // Загрузка звука активации (должен быть добавлен в res/raw)
            activationSoundId = soundPool.load(this, R.raw.activation_sound, 1);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing sound pool", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            // First create notification
            Notification notification = createNotification();
            
            // Properly start as foreground service based on API level
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // For Android 13+ (API 33+), explicitly specify foreground service type
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                } else {
                    // For Android 10+ (API 29-32)
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                }
            } else {
                // For Android 9 and below
                startForeground(NOTIFICATION_ID, notification);
            }

            // After successfully starting foreground service, start recognition
            startVoiceRecognition();
            
            // Check and acquire WakeLock if needed
            if (wakeLock == null || !wakeLock.isHeld()) {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                if (powerManager != null) {
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, 
                            "VoiceActivation:WakeLock");
                    wakeLock.acquire(10*60*1000L /*10 minutes*/);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground service", e);
        }
        
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopVoiceRecognition();
        
        try {
            // Освобождаем WakeLock при завершении сервиса
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                wakeLock = null;
            }
            
            // Освобождаем звуковые ресурсы
            if (soundPool != null) {
                soundPool.release();
                soundPool = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing resources", e);
        }
        
        super.onDestroy();
    }
    
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            // Перезапускаем сервис, если приложение было закрыто
            Intent intent = new Intent(this, VoiceActivationService.class);
            PendingIntent pendingIntent = PendingIntent.getService(this, 1, intent, 
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
            
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID + 1, createRestartNotification(pendingIntent));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restarting service", e);
        }
        
        super.onTaskRemoved(rootIntent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice Activation",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Used for voice assistant activation");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        // Создаем интент для выключения голосовой активации
        Intent stopIntent = new Intent(this, VoiceActivationReceiver.class);
        stopIntent.setAction("STOP_VOICE_ACTIVATION");
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Алан - голосовой ассистент активен")
                .setContentText("Скажите \"Привет, Алан!\" для активации")
                .setSmallIcon(R.drawable.ic_mic)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_stop, "Выключить", stopPendingIntent)
                .setOngoing(true)
                .build();
    }
    
    private Notification createRestartNotification(PendingIntent intent) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Перезапуск голосового ассистента")
                .setContentText("Нажмите для перезапуска")
                .setSmallIcon(R.drawable.ic_mic)
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build();
    }

    private void initializeSpeechRecognizer() {
        try {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.destroy();
                } catch (Exception e) {
                    Log.e(TAG, "Error destroying old speech recognizer", e);
                }
                speechRecognizer = null;
            }
            
            // Проверяем, доступно ли распознавание речи на устройстве
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Log.e(TAG, "Speech recognition is not available on this device!");
                return;
            }
            
            Log.d(TAG, "Creating new SpeechRecognizer instance");
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            
            // Проверяем, создался ли объект
            if (speechRecognizer == null) {
                Log.e(TAG, "Failed to create SpeechRecognizer instance");
                return;
            }
            
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening.set(true);
                    Log.d(TAG, "Ready for speech");
                }
    
                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "Speech beginning");
                }
    
                @Override
                public void onRmsChanged(float rmsdB) {
                    // Отображаем уровень громкости для диагностики работы микрофона
                    if (rmsdB > 0) {
                        Log.v(TAG, "RMS changed: " + rmsdB);
                    }
                }
    
                @Override
                public void onBufferReceived(byte[] buffer) {
                    Log.d(TAG, "Buffer received: " + (buffer != null ? buffer.length : 0) + " bytes");
                }
    
                @Override
                public void onEndOfSpeech() {
                    isListening.set(false);
                    Log.d(TAG, "Speech ended");
                }
    
                @Override
                public void onError(int error) {
                    isListening.set(false);
                    String errorMessage = getErrorMessage(error);
                    Log.e(TAG, "Speech recognition error: " + errorMessage + " (code " + error + ")");
                    
                    // Более длительная задержка для некоторых типов ошибок
                    int delay = 1000;
                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        delay = 3000;
                    } else if (error == SpeechRecognizer.ERROR_NETWORK || 
                               error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                        delay = 5000;
                    }
                    
                    handler.postDelayed(restartRecognition, delay);
                }
    
                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        Log.d(TAG, "Got " + matches.size() + " speech recognition results");
                        for (String match : matches) {
                            Log.d(TAG, "Recognition result: " + match);
                        }
                        processVoiceInput(matches);
                    } else {
                        Log.d(TAG, "No recognition results");
                    }
                    
                    // Restart recognition immediately
                    handler.removeCallbacks(restartRecognition);
                    handler.post(restartRecognition);
                }
    
                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        // Проверяем частичные результаты на наличие ключевой фразы
                        for (String text : matches) {
                            Log.d(TAG, "Partial: " + text);
                            
                            // Нормализуем текст
                            String lowerText = text.toLowerCase().replace("!", "").replace(".", "").replace("?", "");
                            
                            // Проверяем по всем фразам активации
                            for (String phrase : DEFAULT_WAKE_PHRASES) {
                                if (lowerText.contains(phrase)) {
                                    processWakePhrase();
                                    return;
                                }
                            }
                            
                            // Дополнительно проверяем пользовательские фразы
                            SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
                            String customWakePhrases = prefs.getString(KEY_WAKE_PHRASES, "");
                            if (customWakePhrases != null && !customWakePhrases.isEmpty()) {
                                String[] phrases = customWakePhrases.toLowerCase().split(",");
                                for (String phrase : phrases) {
                                    String trimmed = phrase.trim();
                                    if (!trimmed.isEmpty() && lowerText.contains(trimmed)) {
                                        processWakePhrase();
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
    
                @Override
                public void onEvent(int eventType, Bundle params) {
                    Log.d(TAG, "Recognition event: " + eventType);
                }
            });
    
            Log.d(TAG, "Creating recognizer intent");
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            
            // Устанавливаем бесконечное прослушивание
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4000);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
            
            Log.d(TAG, "SpeechRecognizer initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing speech recognizer", e);
        }
    }

    private void startVoiceRecognition() {
        try {
            if (speechRecognizer == null) {
                Log.e(TAG, "Speech recognizer is null, trying to initialize");
                initializeSpeechRecognizer();
                if (speechRecognizer == null) {
                    Log.e(TAG, "Failed to initialize speech recognizer");
                    return;
                }
            }
            
            if (!isListening.get()) {
                if (SpeechRecognizer.isRecognitionAvailable(this)) {
                    Log.d(TAG, "Starting speech recognition");
                    speechRecognizer.startListening(recognizerIntent);
                    Log.d(TAG, "Voice recognition started");
                } else {
                    Log.e(TAG, "Speech recognition is not available");
                }
            } else {
                Log.d(TAG, "Already listening, not starting again");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting speech recognition", e);
            
            // Пересоздаем распознаватель в случае ошибки
            try {
                Log.d(TAG, "Reinitializing speech recognizer");
                speechRecognizer = null;
                initializeSpeechRecognizer();
                handler.postDelayed(restartRecognition, 3000);
            } catch (Exception e2) {
                Log.e(TAG, "Error reinitializing speech recognizer", e2);
            }
        }
    }

    private void stopVoiceRecognition() {
        try {
            if (speechRecognizer != null) {
                speechRecognizer.cancel();
                speechRecognizer.destroy();
                Log.d(TAG, "Voice recognition stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping speech recognition", e);
        }
        handler.removeCallbacks(restartRecognition);
    }

    private void processVoiceInput(ArrayList<String> matches) {
        try {
            boolean wakeWordDetected = false;
            String detectedPhrase = "";
            
            // Загружаем пользовательские wake-фразы из настроек
            SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
            String customWakePhrases = prefs.getString(KEY_WAKE_PHRASES, "");
            float sensitivityThreshold = prefs.getFloat(KEY_WAKE_SENSITIVITY, DEFAULT_CONFIDENCE_THRESHOLD);
            
            // Создаем список фраз для проверки
            ArrayList<String> phrasesToCheck = new ArrayList<>(DEFAULT_WAKE_PHRASES);
            
            // Добавляем пользовательские фразы, если они настроены
            if (customWakePhrases != null && !customWakePhrases.isEmpty()) {
                String[] phrases = customWakePhrases.toLowerCase().split(",");
                for (String phrase : phrases) {
                    String trimmed = phrase.trim();
                    if (!trimmed.isEmpty() && !phrasesToCheck.contains(trimmed)) {
                        phrasesToCheck.add(trimmed);
                    }
                }
            }
            
            // Проверяем каждую фразу из распознанных на совпадение с wake-фразами
            for (String text : matches) {
                Log.d(TAG, "Recognized: " + text);
                String lowerText = text.toLowerCase().replace("!", "").replace(".", "").replace("?", "");
                
                for (String phrase : phrasesToCheck) {
                    // Проверяем точное соответствие или содержание фразы
                    if (lowerText.equals(phrase) || lowerText.contains(phrase)) {
                        wakeWordDetected = true;
                        detectedPhrase = phrase;
                        break;
                    }
                }
                
                if (wakeWordDetected) break;
            }
            
            if (wakeWordDetected) {
                Log.d(TAG, "Wake phrase detected: " + detectedPhrase);
                processWakePhrase();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing voice input", e);
        }
    }
    
    private void processWakePhrase() {
        try {
            Log.d(TAG, "Wake phrase detected! Processing...");
            // Останавливаем распознавание на короткое время
            stopVoiceRecognition();
            
            handler.post(() -> {
                try {
                    // Подаем звуковой сигнал о распознавании
                    playActivationSound();
                    
                    // Используем PowerManager для включения экрана, если это настроено
                    SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
                    boolean wakeScreen = prefs.getBoolean(KEY_WAKE_SCREEN, true);
                    
                    if (wakeScreen) {
                        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                        if (powerManager != null) {
                            PowerManager.WakeLock screenWakeLock = powerManager.newWakeLock(
                                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                                    "VoiceActivation:ScreenWakeLock");
                            
                            // Включаем экран
                            screenWakeLock.acquire(10000); // Удерживаем экран активным 10 секунд
                            
                            // Освобождаем блокировку экрана через некоторое время
                            handler.postDelayed(() -> {
                                if (screenWakeLock.isHeld()) {
                                    screenWakeLock.release();
                                }
                            }, 10000);
                        }
                    }
                    
                    // Запускаем нужную активность в зависимости от настроек
                    Intent intent;
                    boolean openVoiceChat = prefs.getBoolean(KEY_OPEN_VOICE_CHAT, true);
                    
                    if (openVoiceChat) {
                        // Запуск VoiceChatActivity
                        intent = new Intent(VoiceActivationService.this, VoiceChatActivity.class);
                    } else {
                        // Запуск MainActivity
                        intent = new Intent(VoiceActivationService.this, MainActivity.class);
                    }
                    
                    // Добавляем флаги для запуска активности поверх других окон
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                                   Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                                   Intent.FLAG_ACTIVITY_SINGLE_TOP |
                                   Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    
                    // Добавляем флаг, что активность запущена по ключевой фразе
                    intent.putExtra("FROM_WAKE_PHRASE", true);
                    
                    // Запускаем активность
                    startActivity(intent);
                    
                    // Полностью останавливаем сервис, чтобы не было конфликта с микрофоном в VoiceChatActivity
                    if (openVoiceChat) {
                        stopSelf();
                    } else {
                        // Если не открываем голосовой чат, перезапускаем распознавание через 3 секунды
                        handler.postDelayed(() -> {
                            try {
                                initializeSpeechRecognizer();
                                startVoiceRecognition();
                            } catch (Exception ex) {
                                Log.e(TAG, "Error restarting voice recognition", ex);
                            }
                        }, 3000);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error launching activity", e);
                    // Восстанавливаем распознавание в случае ошибки
                    handler.postDelayed(() -> {
                        try {
                            initializeSpeechRecognizer();
                            startVoiceRecognition();
                        } catch (Exception ex) {
                            Log.e(TAG, "Error restarting voice recognition after error", ex);
                        }
                    }, 5000);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error processing wake phrase", e);
        }
    }
    
    private void playActivationSound() {
        try {
            // Проверяем настройки звука
            SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
            boolean playSounds = prefs.getBoolean(KEY_PLAY_SOUND, true);
            
            if (playSounds && soundPool != null && activationSoundId != -1) {
                soundPool.play(activationSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing activation sound", e);
        }
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No match found";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "RecognitionService busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech input";
            default:
                return "Unknown error";
        }
    }
} 