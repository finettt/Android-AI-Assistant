package io.finett.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Broadcast receiver для получения уведомлений о закрытии VoiceChatActivity
 * и восстановления прослушивания wake-фразы
 */
public class VoiceChatClosedReceiver extends BroadcastReceiver {
    private static final String TAG = "VoiceChatClosedReceiver";
    public static final String ACTION_VOICE_CHAT_CLOSED = "io.finett.myapplication.ACTION_VOICE_CHAT_CLOSED";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Получен сигнал: " + action);

        if (ACTION_VOICE_CHAT_CLOSED.equals(action)) {
            Log.d(TAG, "VoiceChatActivity закрыта, восстанавливаем службу распознавания wake-фразы");
            
            // Запускаем сервис для восстановления прослушивания
            Intent serviceIntent = new Intent(context, VoiceActivationService.class);
            serviceIntent.putExtra("RESTART_LISTENING", true);
            
            // Запускаем сервис в зависимости от версии Android
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
} 