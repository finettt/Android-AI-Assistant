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
import android.content.pm.ResolveInfo;
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
import android.service.voice.VoiceInteractionSessionService;
import android.util.Log;
import android.widget.Toast;
import android.Manifest;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.core.content.PermissionChecker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoiceActivationService extends Service {
    private static final String TAG = "VoiceActivationService";
    private static final String WAKE_PHRASE = "привет алан";
    private static final String WAKE_PHRASE_ALT = "алан";
    private static final String WAKE_PHRASE_ALT2 = "привет! алан";
    private static final String PRIMARY_WAKE_PHRASE = "привет! алан";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "VoiceActivationChannel";
    private static final String KEY_WAKE_PHRASES = "wake_phrases";
    private static final String KEY_WAKE_SENSITIVITY = "wake_sensitivity";
    private static final String KEY_OPEN_VOICE_CHAT = "open_voice_chat_on_wake";
    private static final String KEY_PLAY_SOUND = "play_activation_sound";
    private static final String KEY_WAKE_SCREEN = "wake_screen_on_activation";
    private static final String KEY_LAUNCH_SYSTEM_ASSISTANT = "launch_system_assistant";
    private static final float DEFAULT_CONFIDENCE_THRESHOLD = 0.5f;
    
    // NLP параметры
    private static final float SIMILARITY_THRESHOLD = 0.7f;      // Порог похожести для нечеткого сопоставления
    private static final int MAX_EDIT_DISTANCE = 3;             // Максимальное расстояние Левенштейна для слов
    private static final float WORD_MATCH_THRESHOLD = 0.65f;     // Минимальное совпадение для слов в фразе
    
    // Константы для таймеров
    private static final long WAKE_LOCK_TIMEOUT = 3 * 60 * 60 * 1000L; // 3 часа
    private static final long RECOGNITION_RESTART_INTERVAL = 45 * 1000L; // 45 секунд
    private static final long RECOGNITION_ERROR_RESTART_DELAY = 2000L; // 2 секунды
    private static final long MICROPHONE_CHECK_INTERVAL = 20 * 1000L; // 20 секунд
    
    // Константы для AudioRecord
    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    
    // Список стандартных фраз активации
    private static final ArrayList<String> DEFAULT_WAKE_PHRASES = new ArrayList<>(
            Arrays.asList(
                    PRIMARY_WAKE_PHRASE,  // привет! алан (основная фраза)
                    WAKE_PHRASE,          // привет алан 
                    WAKE_PHRASE_ALT,      // алан
                    WAKE_PHRASE_ALT2,     // привет! алан
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
    private long lastRecognitionStartTime = 0; // Время последнего запуска распознавания
    private AudioRecord audioRecord;
    private Thread microphoneMonitorThread;
    private volatile boolean isMonitoringMicrophone = false;
    
    // Отдельный таймер для регулярного перезапуска распознавания
    private final Runnable periodicRecognitionRestart = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Периодический перезапуск распознавания (превентивная мера)");
            restartSpeechRecognition();
            // Планируем следующий перезапуск
            handler.postDelayed(this, RECOGNITION_RESTART_INTERVAL);
        }
    };
    
    // Проверка состояния микрофона
    private final Runnable microphoneCheckTask = new Runnable() {
        @Override
        public void run() {
            checkMicrophoneState();
            // Планируем следующую проверку
            handler.postDelayed(this, MICROPHONE_CHECK_INTERVAL);
        }
    };

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
                    wakeLock.acquire(WAKE_LOCK_TIMEOUT);
                    Log.d(TAG, "WakeLock acquired for " + (WAKE_LOCK_TIMEOUT / 1000 / 60) + " minutes");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring wakelock", e);
        }
        
        // Запускаем таймер для регулярного перезапуска распознавания
        handler.postDelayed(periodicRecognitionRestart, RECOGNITION_RESTART_INTERVAL);
        
        // Запускаем проверку состояния микрофона
        handler.postDelayed(microphoneCheckTask, MICROPHONE_CHECK_INTERVAL);
        
        // Запускаем фоновый мониторинг микрофона
        startMicrophoneMonitoring();
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
            // Проверяем, если это запрос на перезапуск прослушивания после закрытия VoiceChatActivity
            if (intent != null && intent.getBooleanExtra("RESTART_LISTENING", false)) {
                Log.d(TAG, "Получен запрос на перезапуск прослушивания после закрытия голосового чата");
                // Создаем уведомление, но не перезапускаем все компоненты
                Notification notification = createNotification();
                startForeground(NOTIFICATION_ID, notification);
                
                // Восстанавливаем прослушивание
                startVoiceRecognition();
                return START_STICKY;
            }
            
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
                    wakeLock.acquire(WAKE_LOCK_TIMEOUT);
                    Log.d(TAG, "WakeLock re-acquired for " + (WAKE_LOCK_TIMEOUT / 1000 / 60) + " minutes");
                }
            }
            
            // Запускаем таймер для регулярного перезапуска распознавания, если он еще не запущен
            handler.removeCallbacks(periodicRecognitionRestart);
            handler.postDelayed(periodicRecognitionRestart, RECOGNITION_RESTART_INTERVAL);
            
            // Запускаем проверку состояния микрофона
            handler.removeCallbacks(microphoneCheckTask);
            handler.postDelayed(microphoneCheckTask, MICROPHONE_CHECK_INTERVAL);
            
            // Запускаем фоновый мониторинг микрофона, если он еще не запущен
            startMicrophoneMonitoring();
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
        // Останавливаем таймер периодического перезапуска
        handler.removeCallbacks(periodicRecognitionRestart);
        handler.removeCallbacks(microphoneCheckTask);
        
        // Останавливаем мониторинг микрофона
        stopMicrophoneMonitoring();
        
        stopVoiceRecognition();
        
        try {
            // Освобождаем WakeLock при завершении сервиса
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                wakeLock = null;
                Log.d(TAG, "WakeLock released");
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
                speechRecognizer.destroy();
            }
            
            Log.d(TAG, "Initializing speech recognizer");
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening.set(true);
                    Log.d(TAG, "Ready for speech");
                }
    
                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started");
                }
    
                @Override
                public void onRmsChanged(float rmsdB) {
                    // Здесь можно отображать уровень громкости, если нужно
                    // Log.v(TAG, "RMS changed: " + rmsdB);
                    
                    // Выводим уровень громкости каждые 10 вызовов (чтобы не засорять логи)
                    if (rmsdB > 1.0f) {  // Показываем только если громкость выше фонового шума
                        Log.d(TAG, "🎤 Уровень громкости: " + rmsdB);
                    }
                }
    
                @Override
                public void onBufferReceived(byte[] buffer) {
                    Log.d(TAG, "Buffer received: " + (buffer != null ? buffer.length : 0) + " bytes");
                }
    
                @Override
                public void onEndOfSpeech() {
                    // НЕ устанавливаем isListening в false, чтобы избежать перезапуска
                    // Просто логируем событие и планируем перезапуск
                    Log.d(TAG, "Speech ended, перезапускаем распознавание");
                    handler.removeCallbacks(restartRecognition);
                    handler.postDelayed(restartRecognition, 300);
                }
    
                @Override
                public void onError(int error) {
                    String errorMessage = getErrorMessage(error);
                    Log.e(TAG, "Speech recognition error: " + errorMessage + " (code " + error + ")");
                    
                    // Всегда перезапускаем распознавание, но с разной задержкой
                    isListening.set(false);
                    
                    // Используем разные задержки в зависимости от типа ошибки
                    int delay = 1000;
                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        delay = 3000;
                    } else if (error == SpeechRecognizer.ERROR_NETWORK || 
                               error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                        delay = 5000; // Более долгая задержка для сетевых ошибок
                    } else if (error == SpeechRecognizer.ERROR_NO_MATCH || 
                               error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        delay = 500; // Быстрый перезапуск для ошибок несовпадения/таймаута
                    }
                    
                    Log.d(TAG, "Перезапуск распознавания через " + delay + " мс");
                    handler.removeCallbacks(restartRecognition);
                    handler.postDelayed(restartRecognition, delay);
                }
    
                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        Log.d(TAG, "Got " + matches.size() + " speech recognition results");
                        for (String match : matches) {
                            // Добавляем эмодзи для лучшей видимости в логах
                            Log.d(TAG, "🗣️ Распознано: " + match);
                        }
                        processVoiceInput(matches);
                    } else {
                        Log.d(TAG, "No recognition results");
                    }
                    
                    // Не перезапускаем распознавание, а продолжаем слушать
                    // Нужно лишь проверить, если вдруг распознавание остановилось
                    if (!isListening.get()) {
                        Log.d(TAG, "Распознавание остановилось, перезапускаем...");
                        handler.removeCallbacks(restartRecognition);
                        handler.postDelayed(restartRecognition, 300);
                    }
                }
    
                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        // Проверяем частичные результаты на наличие ключевой фразы
                        for (String text : matches) {
                            // Выводим все частичные результаты в логи с эмодзи для лучшей видимости
                            Log.d(TAG, "👂 Partial: " + text);
                            
                            // Нормализуем текст
                            String lowerText = text.toLowerCase().replace("!", "").replace(".", "").replace("?", "");
                            
                            // Первым делом проверяем основную вейк-фразу
                            if (lowerText.contains(PRIMARY_WAKE_PHRASE.toLowerCase())) {
                                Log.d(TAG, "Primary wake phrase detected in partial results: " + PRIMARY_WAKE_PHRASE);
                                processWakePhrase();
                                return;
                            }
                            
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
                    handler.postDelayed(restartRecognition, RECOGNITION_ERROR_RESTART_DELAY);
                    return;
                }
            }
            
            if (!isListening.get()) {
                if (SpeechRecognizer.isRecognitionAvailable(this)) {
                    Log.d(TAG, "Starting speech recognition");
                    
                    // Настраиваем распознаватель на непрерывную работу
                    recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
                    
                    // Устанавливаем очень длительные интервалы тишины перед завершением распознавания
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 30000);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 30000);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 30000);
                    
                    speechRecognizer.startListening(recognizerIntent);
                    isListening.set(true);
                    lastRecognitionStartTime = System.currentTimeMillis();
                    Log.d(TAG, "Voice recognition started в непрерывном режиме (" + lastRecognitionStartTime + ")");
                } else {
                    Log.e(TAG, "Speech recognition is not available");
                    handler.postDelayed(restartRecognition, RECOGNITION_ERROR_RESTART_DELAY);
                }
            } else {
                Log.d(TAG, "Already listening, checking if restart needed");
                
                // Проверяем, нужно ли принудительно перезапустить распознавание
                long currentTime = System.currentTimeMillis();
                long elapsedTime = currentTime - lastRecognitionStartTime;
                
                if (elapsedTime > RECOGNITION_RESTART_INTERVAL) {
                    Log.d(TAG, "Force restarting recognition after " + (elapsedTime / 1000) + " seconds");
                    restartSpeechRecognition();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting speech recognition", e);
            
            // Пересоздаем распознаватель в случае ошибки
            try {
                Log.d(TAG, "Reinitializing speech recognizer");
                speechRecognizer = null;
                initializeSpeechRecognizer();
                handler.postDelayed(restartRecognition, RECOGNITION_ERROR_RESTART_DELAY);
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
            
            // Добавляем подробный вывод в лог для диагностики
            Log.d(TAG, "📝 Обработка распознанного ввода, количество фраз: " + matches.size());
            
            // Загружаем пользовательские wake-фразы из настроек
            SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
            String customWakePhrases = prefs.getString(KEY_WAKE_PHRASES, "");
            float sensitivityThreshold = prefs.getFloat(KEY_WAKE_SENSITIVITY, DEFAULT_CONFIDENCE_THRESHOLD);
            
            // Создаем список фраз для проверки
            ArrayList<String> phrasesToCheck = new ArrayList<>(DEFAULT_WAKE_PHRASES);
            
            // Выводим список фраз активации для диагностики
            Log.d(TAG, "📋 Проверяем следующие фразы активации:");
            for (String phrase : DEFAULT_WAKE_PHRASES) {
                Log.d(TAG, "   - " + phrase);
            }
            
            // Добавляем пользовательские фразы, если они настроены
            if (customWakePhrases != null && !customWakePhrases.isEmpty()) {
                String[] phrases = customWakePhrases.toLowerCase().split(",");
                for (String phrase : phrases) {
                    String trimmed = phrase.trim();
                    if (!trimmed.isEmpty() && !phrasesToCheck.contains(trimmed)) {
                        phrasesToCheck.add(trimmed);
                        Log.d(TAG, "   - " + trimmed + " (пользовательская)");
                    }
                }
            }
            
            // Главную вейк-фразу всегда проверяем первой
            if (!phrasesToCheck.contains(PRIMARY_WAKE_PHRASE.toLowerCase())) {
                phrasesToCheck.add(0, PRIMARY_WAKE_PHRASE.toLowerCase());
            }
            
            // Проверяем каждую фразу из распознанных на совпадение с wake-фразами
            for (String text : matches) {
                Log.d(TAG, "🔎 Проверяем распознанную фразу: " + text);
                String lowerText = text.toLowerCase().replace("!", "").replace(".", "").replace("?", "");
                
                // Первая проверка - точное совпадение или содержание фразы
                for (String phrase : phrasesToCheck) {
                    // Проверяем точное соответствие или содержание фразы
                    if (lowerText.equals(phrase) || lowerText.contains(phrase)) {
                        wakeWordDetected = true;
                        detectedPhrase = phrase;
                        Log.d(TAG, "✅ НАЙДЕНО ТОЧНОЕ СОВПАДЕНИЕ с фразой активации: " + phrase);
                        break;
                    }
                }
                
                // Если точное совпадение не найдено, используем нечеткое сопоставление
                if (!wakeWordDetected) {
                    float bestMatchScore = 0;
                    String bestMatchPhrase = null;
                    
                    for (String phrase : phrasesToCheck) {
                        float similarityScore = calculatePhraseSimilarity(lowerText, phrase);
                        Log.d(TAG, "📊 Нечеткое сопоставление с '" + phrase + "': " + similarityScore);
                        
                        if (similarityScore >= SIMILARITY_THRESHOLD && similarityScore > bestMatchScore) {
                            bestMatchScore = similarityScore;
                            bestMatchPhrase = phrase;
                        }
                    }
                    
                    if (bestMatchPhrase != null) {
                        wakeWordDetected = true;
                        detectedPhrase = bestMatchPhrase;
                        Log.d(TAG, "✅ НАЙДЕНО НЕЧЕТКОЕ СОВПАДЕНИЕ с фразой: " + bestMatchPhrase + " (совпадение: " + bestMatchScore + ")");
                    }
                }
                
                if (wakeWordDetected) break;
            }
            
            if (wakeWordDetected) {
                Log.d(TAG, "🔔 Wake phrase detected: " + detectedPhrase);
                processWakePhrase();
            } else {
                Log.d(TAG, "❌ Фраза активации не обнаружена в распознанном тексте");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing voice input", e);
        }
    }
    
    /**
     * Рассчитывает степень похожести между двумя фразами
     * @param inputText Входной текст
     * @param referencePhrase Эталонная фраза для сравнения
     * @return Оценка похожести от 0.0 до 1.0
     */
    private float calculatePhraseSimilarity(String inputText, String referencePhrase) {
        try {
            // Если одна из строк пустая, возвращаем 0
            if (inputText == null || referencePhrase == null || 
                inputText.isEmpty() || referencePhrase.isEmpty()) {
                return 0;
            }
            
            // Проверка на вхождение слов эталонной фразы во входной текст
            String[] referenceWords = referencePhrase.split("\\s+");
            String[] inputWords = inputText.split("\\s+");
            
            // Если эталонная фраза - одно слово, используем оценку по Левенштейну
            if (referenceWords.length == 1) {
                return calculateWordSimilarity(referencePhrase, inputText);
            }
            
            // Подсчитываем, сколько слов из эталонной фразы присутствует во входной фразе
            int matchedWords = 0;
            for (String refWord : referenceWords) {
                if (refWord.length() <= 2) continue; // Пропускаем короткие слова
                
                float bestWordMatchScore = 0;
                for (String inputWord : inputWords) {
                    if (inputWord.length() <= 2) continue; // Пропускаем короткие слова
                    
                    float wordSimilarity = calculateWordSimilarity(refWord, inputWord);
                    if (wordSimilarity > bestWordMatchScore) {
                        bestWordMatchScore = wordSimilarity;
                    }
                }
                
                if (bestWordMatchScore >= WORD_MATCH_THRESHOLD) {
                    matchedWords++;
                }
            }
            
            // Вычисляем общую оценку на основе доли совпавших слов
            float matchRatio = (float) matchedWords / referenceWords.length;
            
            // Особая обработка для фраз с ключевым словом "алан"
            if (referencePhrase.contains("алан")) {
                // Если фраза содержит ключевое слово "алан", проверяем наличие хотя бы его
                boolean hasKeyword = false;
                for (String inputWord : inputWords) {
                    if (calculateWordSimilarity("алан", inputWord) >= WORD_MATCH_THRESHOLD) {
                        hasKeyword = true;
                        break;
                    }
                }
                
                // Если ключевого слова нет, понижаем оценку
                if (!hasKeyword) {
                    matchRatio *= 0.5f;
                } else if (matchedWords == 1 && hasKeyword) {
                    // Если нашли только ключевое слово, но оно есть, даем минимальный приемлемый рейтинг
                    matchRatio = Math.max(matchRatio, 0.7f);
                }
            }
            
            return matchRatio;
        } catch (Exception e) {
            Log.e(TAG, "Error calculating phrase similarity", e);
            return 0;
        }
    }
    
    /**
     * Рассчитывает степень похожести между двумя словами с использованием алгоритма Левенштейна
     * @param word1 Первое слово
     * @param word2 Второе слово
     * @return Оценка похожести от 0.0 до 1.0
     */
    private float calculateWordSimilarity(String word1, String word2) {
        // Если слова идентичны
        if (word1.equals(word2)) return 1.0f;
        
        // Если одно слово содержит другое
        if (word1.contains(word2) || word2.contains(word1)) {
            // Возвращаем соотношение длины более короткого слова к длине более длинного
            return (float) Math.min(word1.length(), word2.length()) / 
                   Math.max(word1.length(), word2.length());
        }
        
        // Вычисляем расстояние Левенштейна
        int distance = calculateLevenshteinDistance(word1, word2);
        
        // Преобразуем расстояние в оценку похожести
        // Чем меньше расстояние относительно длины более длинного слова, тем выше похожесть
        float maxLength = Math.max(word1.length(), word2.length());
        
        // Если расстояние слишком большое, возвращаем низкую оценку
        if (distance > MAX_EDIT_DISTANCE) {
            return 0.0f;
        }
        
        return 1.0f - (distance / maxLength);
    }
    
    /**
     * Вычисляет расстояние Левенштейна между двумя строками
     * @param str1 Первая строка
     * @param str2 Вторая строка
     * @return Расстояние Левенштейна
     */
    private int calculateLevenshteinDistance(String str1, String str2) {
        int[][] dp = new int[str1.length() + 1][str2.length() + 1];
        
        for (int i = 0; i <= str1.length(); i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= str2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= str1.length(); i++) {
            for (int j = 1; j <= str2.length(); j++) {
                int cost = (str1.charAt(i - 1) == str2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        
        return dp[str1.length()][str2.length()];
    }
    
    private void processWakePhrase() {
        try {
            Log.d(TAG, "Wake phrase detected! Processing...");
            
            // НЕ останавливаем распознавание, а просто ставим флаг
            // Это позволит продолжить слушать после обработки
            if (speechRecognizer != null) {
                // Только временно останавливаем, чтобы не мешать другим компонентам
                speechRecognizer.cancel();
                isListening.set(false);
            }
            
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
                    
                    // Проверяем, нужно ли запустить системного ассистента
                    boolean launchSystemAssistant = prefs.getBoolean(KEY_LAUNCH_SYSTEM_ASSISTANT, true);
                    
                    if (launchSystemAssistant) {
                        // Запускаем системного ассистента
                        launchSystemAssistant();
                    } else {
                        // Запускаем приложение по старой логике
                        boolean openVoiceChat = prefs.getBoolean(KEY_OPEN_VOICE_CHAT, true);
                        Intent intent;
                        
                        if (openVoiceChat) {
                            // Запуск VoiceChatActivity
                            intent = new Intent(VoiceActivationService.this, VoiceChatActivity.class);
                            // Добавляем флаги для запуска активности поверх других окон
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                                          Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                                          Intent.FLAG_ACTIVITY_SINGLE_TOP |
                                          Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            
                            // Добавляем флаг, что активность запущена по ключевой фразе
                            intent.putExtra("FROM_WAKE_PHRASE", true);
                            
                            // Запускаем активность
                            startActivity(intent);
                            
                            // Останавливаем сервис на время работы голосового чата, чтобы не конфликтовать с микрофоном
                            Log.d(TAG, "Временно останавливаем сервис пока открыт голосовой чат");
                            stopVoiceRecognition();
                            
                            // НЕ перезапускаем распознавание сразу,
                            // VoiceChatActivity уведомит нас когда будет закрыта через VoiceChatClosedReceiver
                        } else {
                            // Запуск MainActivity
                            intent = new Intent(VoiceActivationService.this, MainActivity.class);
                            // Добавляем флаги для запуска активности поверх других окон
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                                          Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                                          Intent.FLAG_ACTIVITY_SINGLE_TOP |
                                          Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            
                            // Добавляем флаг, что активность запущена по ключевой фразе
                            intent.putExtra("FROM_WAKE_PHRASE", true);
                            
                            // Запускаем активность
                            startActivity(intent);
                            
                            // Перезапускаем распознавание через 3 секунды
                            handler.postDelayed(() -> {
                                try {
                                    // Если сервис все еще запущен, возобновляем прослушивание
                                    if (!isListening.get()) {
                                        startVoiceRecognition();
                                    }
                                } catch (Exception ex) {
                                    Log.e(TAG, "Error restarting voice recognition", ex);
                                }
                            }, 3000);
                        }
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error launching activity", e);
                    // Восстанавливаем распознавание в случае ошибки
                    handler.postDelayed(() -> {
                        try {
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
    
    /**
     * Запускает системного ассистента (нашу версию ассистента)
     */
    private void launchSystemAssistant() {
        try {
            Log.d(TAG, "🚀 Запуск приложения в режиме ассистента");
            
            // Статистика использования ассистента
            try {
                io.finett.myapplication.AssistantSettings.recordLaunch(this);
            } catch (Exception e) {
                Log.e(TAG, "Error recording assistant launch", e);
            }
            
            // Создаем интент для запуска нашего приложения как ассистента
            Intent assistIntent = new Intent(this, VoiceInteractionSessionService.class);
            assistIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startService(assistIntent);
            
            // Также запускаем дополнительно наше приложение в режиме ассистента через Intent.ACTION_ASSIST
            // с правильно настроенными флагами, имитирующими запуск от системы
            Intent intent = new Intent(Intent.ACTION_ASSIST);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("android.intent.extra.ASSIST_PACKAGE", getPackageName());
            intent.putExtra("android.intent.extra.ASSIST_UID", android.os.Process.myUid());
            intent.putExtra("from_wake_phrase", true);
            intent.putExtra("as_assistant", true);
            
            // Проверяем, что интент можно запустить
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                Log.d(TAG, "Запущен ассистент через ACTION_ASSIST");
            } else {
                // Создаем явный интент на MainActivity но с флагами для ассистентного режима
                Intent explicitIntent = new Intent(this, MainActivity.class);
                explicitIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                explicitIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                explicitIntent.putExtra("from_wake_phrase", true);
                explicitIntent.putExtra("as_assistant", true);
                startActivity(explicitIntent);
                Log.d(TAG, "Запущен MainActivity в режиме ассистента");
            }
            
            // Также запускаем диалог для настройки нашего приложения как ассистента по умолчанию,
            // если оно ещё не установлено как ассистент
            if (!isDefaultAssistant()) {
                // Показываем это с короткой задержкой после запуска приложения
                handler.postDelayed(() -> {
                    showDefaultAssistantDialog();
                }, 2000);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка запуска в режиме ассистента: " + e.getMessage());
            
            // Аварийный запуск просто как MainActivity
            try {
                Intent fallbackIntent = new Intent(this, MainActivity.class);
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                fallbackIntent.putExtra("FROM_WAKE_PHRASE", true);
                fallbackIntent.putExtra("EMERGENCY_LAUNCH", true);
                startActivity(fallbackIntent);
                Log.d(TAG, "Аварийный запуск MainActivity");
            } catch (Exception e2) {
                Log.e(TAG, "Критическая ошибка запуска: " + e2.getMessage());
            }
        }
    }
    
    /**
     * Проверяет, установлено ли наше приложение ассистентом по умолчанию
     */
    private boolean isDefaultAssistant() {
        try {
            // Получаем информацию о текущем ассистенте
            PackageManager pm = getPackageManager();
            ResolveInfo resolveInfo = pm.resolveActivity(
                    new Intent(Intent.ACTION_ASSIST), 
                    PackageManager.MATCH_DEFAULT_ONLY);
            
            if (resolveInfo != null) {
                String currentAssistant = resolveInfo.activityInfo.packageName;
                return getPackageName().equals(currentAssistant);
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при проверке статуса ассистента: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Показывает диалог настройки нашего приложения в качестве ассистента по умолчанию
     */
    private void showDefaultAssistantDialog() {
        try {
            // Создаем интент для открытия настроек ассистента
            Intent settingsIntent = new Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS);
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Создаем нотификацию, которая предложит установить наше приложение ассистентом
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_mic)
                    .setContentTitle("Установите Алан ассистентом по умолчанию")
                    .setContentText("Нажмите для настройки")
                    .setContentIntent(PendingIntent.getActivity(
                            this, 102, settingsIntent, PendingIntent.FLAG_IMMUTABLE))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true);
            
            NotificationManager notificationManager = 
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID + 102, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка показа диалога настройки ассистента: " + e.getMessage());
        }
    }

    /**
     * Воспроизводит звук активации
     */
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

    /**
     * Принудительный перезапуск распознавания речи
     */
    private void restartSpeechRecognition() {
        try {
            Log.d(TAG, "Принудительный перезапуск распознавания речи");
            
            // Останавливаем текущее распознавание, если оно активно
            if (speechRecognizer != null) {
                speechRecognizer.cancel();
                // Не уничтожаем, чтобы не создавать новый объект
            }
            
            isListening.set(false);
            
            // Запускаем распознавание с небольшой задержкой
            handler.removeCallbacks(restartRecognition);
            handler.postDelayed(restartRecognition, 500);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при перезапуске распознавания речи", e);
            
            // В случае ошибки пересоздаем распознаватель полностью
            try {
                speechRecognizer = null;
                initializeSpeechRecognizer();
                handler.postDelayed(restartRecognition, 1000);
            } catch (Exception e2) {
                Log.e(TAG, "Критическая ошибка при перезапуске распознавания", e2);
            }
        }
    }

    /**
     * Запускает фоновый мониторинг микрофона для поддержания его активного состояния
     */
    private void startMicrophoneMonitoring() {
        if (isMonitoringMicrophone) {
            return; // Мониторинг уже запущен
        }
        
        try {
            // Проверяем разрешение на запись звука
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != 
                    PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "No permission to record audio");
                return;
            }
            
            isMonitoringMicrophone = true;
            
            // Создаем и запускаем поток для мониторинга микрофона
            microphoneMonitorThread = new Thread(() -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                Log.d(TAG, "Microphone monitoring thread started");
                
                byte[] buffer = new byte[BUFFER_SIZE];
                
                while (isMonitoringMicrophone) {
                    try {
                        // Создаем AudioRecord для проверки микрофона
                        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                            if (audioRecord != null) {
                                audioRecord.release();
                            }
                            
                            audioRecord = new AudioRecord(
                                AUDIO_SOURCE,
                                SAMPLE_RATE,
                                CHANNEL_CONFIG,
                                AUDIO_FORMAT,
                                BUFFER_SIZE
                            );
                            
                            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                                Log.e(TAG, "Failed to initialize AudioRecord");
                                Thread.sleep(5000);
                                continue;
                            }
                        }
                        
                        // Запускаем запись на короткое время
                        if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                            audioRecord.startRecording();
                        }
                        
                        // Читаем данные из микрофона
                        int bytesRead = audioRecord.read(buffer, 0, BUFFER_SIZE);
                        
                        if (bytesRead > 0) {
                            // Микрофон работает нормально
                            // Log.v(TAG, "Microphone is active, read " + bytesRead + " bytes");
                        } else {
                            Log.w(TAG, "Failed to read from microphone: " + bytesRead);
                        }
                        
                        // Останавливаем запись
                        audioRecord.stop();
                        
                        // Проверяем состояние распознавания
                        if (!isListening.get()) {
                            handler.post(() -> {
                                Log.d(TAG, "Voice recognition not active, restarting from microphone monitor");
                                restartSpeechRecognition();
                            });
                        }
                        
                        // Делаем паузу перед следующей проверкой
                        Thread.sleep(5000);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in microphone monitoring", e);
                        try {
                            // Освобождаем ресурсы в случае ошибки
                            if (audioRecord != null) {
                                audioRecord.release();
                                audioRecord = null;
                            }
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                
                // Освобождаем ресурсы при выходе из цикла
                if (audioRecord != null) {
                    audioRecord.release();
                    audioRecord = null;
                }
                
                Log.d(TAG, "Microphone monitoring thread stopped");
            });
            
            microphoneMonitorThread.start();
            Log.d(TAG, "Started microphone monitoring");
        } catch (Exception e) {
            Log.e(TAG, "Error starting microphone monitoring", e);
            isMonitoringMicrophone = false;
        }
    }
    
    /**
     * Останавливает фоновый мониторинг микрофона
     */
    private void stopMicrophoneMonitoring() {
        isMonitoringMicrophone = false;
        
        try {
            if (microphoneMonitorThread != null) {
                microphoneMonitorThread.interrupt();
                microphoneMonitorThread = null;
            }
            
            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
            }
            
            Log.d(TAG, "Stopped microphone monitoring");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping microphone monitoring", e);
        }
    }
    
    /**
     * Проверяет состояние микрофона и распознавания
     */
    private void checkMicrophoneState() {
        try {
            Log.d(TAG, "⏱️ Checking microphone and recognition state");
            
            // Проверяем, активно ли распознавание
            if (!isListening.get()) {
                Log.d(TAG, "🔄 Recognition is not active, restarting");
                restartSpeechRecognition();
                return;
            }
            
            // Проверяем, не слишком ли давно было последнее распознавание
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - lastRecognitionStartTime;
            
            if (elapsedTime > RECOGNITION_RESTART_INTERVAL) {
                Log.d(TAG, "⚠️ Recognition has been running for " + (elapsedTime / 1000) + " seconds, restarting");
                restartSpeechRecognition();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking microphone state", e);
        }
    }
} 