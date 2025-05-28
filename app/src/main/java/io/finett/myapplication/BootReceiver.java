package io.finett.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.preference.PreferenceManager;
import android.util.Log;

/**
 * Basic broadcast receiver for handling system boot events
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        // App is started after boot, can be used for future system-level initializations
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) || 
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            Log.d(TAG, "System boot or app update detected");
            
            // Проверяем, включена ли голосовая активация
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean voiceActivationEnabled = prefs.getBoolean("voice_activation_enabled", false);
            
            if (voiceActivationEnabled) {
                // Запускаем сервис голосовой активации только если он включен в настройках
            startVoiceActivationService(context);
            } else {
                Log.d(TAG, "Voice activation disabled, skipping service start");
            }
            
            // Проверяем настройку автозапуска ассистента
            SharedPreferences chatPrefs = context.getSharedPreferences("ChatAppPrefs", Context.MODE_PRIVATE);
            boolean autoStartAssistant = chatPrefs.getBoolean("autostart_assistant", false);
            
            if (autoStartAssistant && voiceActivationEnabled) {
                Log.d(TAG, "Auto-start assistant enabled, starting services...");
                
                // Запускаем сервис голосового взаимодействия
                try {
                    Intent serviceIntent = new Intent(context, VoiceInteractionService.class);
                    context.startService(serviceIntent);
                    Log.d(TAG, "VoiceInteractionService started");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start VoiceInteractionService: " + e.getMessage());
                }
            } else {
                Log.d(TAG, "Auto-start assistant disabled or voice activation disabled, skipping service launch");
            }
        }
    }
    
    /**
     * Запускает сервис голосовой активации
     * @param context Контекст приложения
     */
    private void startVoiceActivationService(Context context) {
        try {
            // Запускаем сервис голосовой активации
            Log.d(TAG, "Starting voice activation service on boot");
            Intent serviceIntent = new Intent(context, VoiceActivationService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
                Log.d(TAG, "VoiceActivationService started using startForegroundService");
            } else {
                context.startService(serviceIntent);
                Log.d(TAG, "VoiceActivationService started using startService");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start VoiceActivationService: " + e.getMessage());
        }
    }
} 