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
        Log.d(TAG, "Voice interaction service created");
        
        // При создании службы пробуем запустить ассистента, это может помочь при системном вызове
        startAssistantDirectly();
    }

    @Override
    public void onReady() {
        super.onReady();
        Log.d(TAG, "Voice interaction service ready");
        
        // Пробуем запустить ассистента и когда служба готова
        startAssistantDirectly();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Voice interaction service start command received");
        
        // При запуске через startService также пробуем запустить ассистента
        startAssistantDirectly();
        
        return super.onStartCommand(intent, flags, startId);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind called with intent: " + intent);
        return super.onBind(intent);
    }
    
    /**
     * Вспомогательный метод для запуска активности ассистента напрямую
     */
    private void startAssistantDirectly() {
        try {
            Log.d(TAG, "Trying to launch SystemAssistantActivity directly from VoiceInteractionService");
            
            // Используем новый класс AssistantLauncher для запуска системного ассистента
            AssistantLauncher.launchSystemAssistantActivity(this);
        } catch (Exception e) {
            Log.e(TAG, "Error launching SystemAssistantActivity", e);
            
            // Если не удалось запустить SystemAssistantActivity, пробуем запустить обычную AssistantActivity
            try {
                Log.d(TAG, "Trying to launch regular AssistantActivity as fallback");
                AssistantLauncher.launchAssistant(this);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to launch even regular AssistantActivity", ex);
            }
        }
    }

    @Override
    public void onShutdown() {
        super.onShutdown();
        Log.d(TAG, "Voice interaction service shutdown");
    }
} 