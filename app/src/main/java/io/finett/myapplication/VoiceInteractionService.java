package io.finett.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.app.ActivityOptions;
import android.os.IBinder;

/**
 * Служба голосового взаимодействия для работы приложения в качестве системного ассистента
 */
public class VoiceInteractionService extends android.service.voice.VoiceInteractionService {
    private static final String TAG = "VoiceInteractionSvc";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Служба голосового взаимодействия создана");
        
        // При создании службы пробуем запустить ассистента, это может помочь при системном вызове
        startAssistantDirectly();
    }

    @Override
    public void onReady() {
        super.onReady();
        Log.d(TAG, "Служба голосового взаимодействия готова");
        
        // Пробуем запустить ассистента и когда служба готова
        startAssistantDirectly();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Получен запрос на запуск службы голосового взаимодействия");
        
        // При запуске через startService также пробуем запустить ассистента
        startAssistantDirectly();
        
        return super.onStartCommand(intent, flags, startId);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind вызван с интентом: " + intent);
        return super.onBind(intent);
    }
    
    /**
     * Вспомогательный метод для запуска активности ассистента напрямую
     */
    private void startAssistantDirectly() {
        try {
            Log.d(TAG, "Пытаемся запустить SystemAssistantActivity напрямую из VoiceInteractionService");
            
            // Используем новый класс AssistantLauncher для запуска системного ассистента
            AssistantLauncher.launchSystemAssistantActivity(this);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при прямом запуске SystemAssistantActivity", e);
            
            // Если не удалось запустить SystemAssistantActivity, пробуем запустить обычную AssistantActivity
            try {
                Log.d(TAG, "Пробуем запустить обычную AssistantActivity как резервный вариант");
                AssistantLauncher.launchAssistant(this);
            } catch (Exception ex) {
                Log.e(TAG, "Не удалось запустить даже обычную AssistantActivity", ex);
            }
        }
    }

    @Override
    public void onShutdown() {
        super.onShutdown();
        Log.d(TAG, "Служба голосового взаимодействия завершена");
    }
} 