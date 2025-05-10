package io.finett.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.util.Log;

/**
 * Класс для мониторинга запуска системного ассистента
 */
public class AssistantMonitor {
    private static final String TAG = "AssistantMonitor";
    private static final String ACTION_ASSIST_STARTED = "io.finett.myapplication.ACTION_ASSIST_STARTED";
    
    /**
     * Приемник широковещательных сообщений для мониторинга запуска ассистента
     */
    private static class AssistantBroadcastReceiver extends BroadcastReceiver {
        private AssistantCallback callback;
        
        public AssistantBroadcastReceiver(AssistantCallback callback) {
            this.callback = callback;
        }
        
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_ASSIST_STARTED.equals(intent.getAction())) {
                Log.d(TAG, "Получено сообщение о запуске ассистента");
                if (callback != null) {
                    callback.onAssistantStarted();
                }
            }
        }
    }
    
    /**
     * Интерфейс для обратного вызова при запуске ассистента
     */
    public interface AssistantCallback {
        void onAssistantStarted();
    }
    
    /**
     * Уведомляет о запуске ассистента
     * 
     * @param context Контекст приложения
     */
    public static void notifyAssistantStarted(Context context) {
        if (context == null) {
            return;
        }
        
        try {
            Intent intent = new Intent(ACTION_ASSIST_STARTED);
            context.sendBroadcast(intent);
            Log.d(TAG, "Отправлено уведомление о запуске ассистента");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при отправке уведомления о запуске ассистента", e);
        }
    }
    
    /**
     * Регистрирует слушателя событий запуска ассистента
     * 
     * @param context Контекст приложения
     * @param callback Обратный вызов для уведомления о запуске ассистента
     * @return Зарегистрированный приемник или null при ошибке
     */
    public static BroadcastReceiver registerAssistantListener(Context context, AssistantCallback callback) {
        if (context == null || callback == null) {
            return null;
        }
        
        try {
            IntentFilter filter = new IntentFilter(ACTION_ASSIST_STARTED);
            AssistantBroadcastReceiver receiver = new AssistantBroadcastReceiver(callback);
            context.registerReceiver(receiver, filter);
            Log.d(TAG, "Зарегистрирован слушатель запуска ассистента");
            return receiver;
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при регистрации слушателя запуска ассистента", e);
            return null;
        }
    }
    
    /**
     * Отменяет регистрацию слушателя событий запуска ассистента
     * 
     * @param context Контекст приложения
     * @param receiver Приемник, полученный при регистрации
     */
    public static void unregisterAssistantListener(Context context, BroadcastReceiver receiver) {
        if (context == null || receiver == null) {
            return;
        }
        
        try {
            context.unregisterReceiver(receiver);
            Log.d(TAG, "Отменена регистрация слушателя запуска ассистента");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при отмене регистрации слушателя запуска ассистента", e);
        }
    }
} 