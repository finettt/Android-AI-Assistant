package io.finett.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class VoiceActivationReceiver extends BroadcastReceiver {
    private static final String TAG = "VoiceActivationRcvr";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        if ("STOP_VOICE_ACTIVATION".equals(action)) {
            // Выключаем сервис
            Intent serviceIntent = new Intent(context, VoiceActivationService.class);
            context.stopService(serviceIntent);
            
            // Сохраняем состояние
            SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean("voice_activation_enabled", false).apply();
            
            Log.d(TAG, "Voice activation service stopped");
        }
    }
} 