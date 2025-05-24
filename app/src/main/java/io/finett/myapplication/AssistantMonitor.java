package io.finett.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Класс для мониторинга запуска системного ассистента
 */
public class AssistantMonitor {
    private static final String TAG = "AssistantMonitor";
    private static final String ACTION_ASSIST_STARTED = "io.finett.myapplication.ACTION_ASSIST_STARTED";
    private static final String PREFS_NAME = "assistant_monitor";
    private static final String KEY_LAST_ASSIST_TIME = "last_assist_time";
    private static final long ASSIST_CHECK_TIMEOUT = 5000; // 5 секунд для проверки запуска
    
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
                
                // Сохраняем время последнего запуска ассистента
                if (context != null) {
                    try {
                        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        prefs.edit().putLong(KEY_LAST_ASSIST_TIME, System.currentTimeMillis()).apply();
                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка при сохранении времени запуска ассистента", e);
                    }
                }
                
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
            // Сохраняем время запуска в SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putLong(KEY_LAST_ASSIST_TIME, System.currentTimeMillis()).apply();
            
            // Отправляем широковещательное сообщение
            Intent intent = new Intent(ACTION_ASSIST_STARTED);
            context.sendBroadcast(intent);
            Log.d(TAG, "Отправлено уведомление о запуске ассистента");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при отправке уведомления о запуске ассистента", e);
        }
    }
    
    /**
     * Проверяет, был ли запущен системный ассистент в последние секунды
     * 
     * @return true, если ассистент был запущен недавно
     */
    public static boolean wasAssistantLaunched() {
        try {
            // Проверяем, запускался ли ассистент в последние ASSIST_CHECK_TIMEOUT миллисекунд
            // Этот метод не зависит от контекста, так как может вызываться из разных мест
            
            // Так как метод статический и работает без контекста, используем глобальное
            // статическое поле для хранения времени последнего запуска ассистента
            long currentTime = System.currentTimeMillis();
            
            // Проверяем последнее время запуска (хранится статически)
            if (lastAssistStartTime > 0) {
                boolean recentlyStarted = (currentTime - lastAssistStartTime) < ASSIST_CHECK_TIMEOUT;
                if (recentlyStarted) {
                    Log.d(TAG, "Ассистент был запущен недавно (проверка по времени)");
                    return true;
                }
            }
            
            Log.d(TAG, "Ассистент не был запущен недавно");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при проверке запуска ассистента", e);
            return false;
        }
    }
    
    // Статическое поле для хранения времени последнего запуска ассистента
    private static long lastAssistStartTime = 0;
    
    /**
     * Обновляет время последнего запуска ассистента
     * (используется внутри приложения при запуске ассистента)
     */
    public static void updateLastAssistStartTime() {
        lastAssistStartTime = System.currentTimeMillis();
        Log.d(TAG, "Обновлено время последнего запуска ассистента: " + lastAssistStartTime);
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