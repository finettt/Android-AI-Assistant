package io.finett.myapplication;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Вспомогательный класс для запуска ассистента из различных компонентов приложения
 */
public class AssistantLauncher {
    private static final String TAG = "AssistantLauncher";
    
    /**
     * Запускает ассистента через активность
     * 
     * @param context Контекст приложения
     */
    public static void launchAssistant(Context context) {
        if (context == null) {
            Log.e(TAG, "Не удалось запустить ассистента: контекст равен null");
            return;
        }
        
        try {
            Intent intent = new Intent(context, AssistantActivity.class);
            intent.setAction(Intent.ACTION_ASSIST);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("LAUNCHED_BY_ASSISTANT_LAUNCHER", true);
            
            Log.d(TAG, "Запускаем AssistantActivity");
            context.startActivity(intent);
            Log.d(TAG, "AssistantActivity запущена успешно");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при запуске AssistantActivity: " + e.getMessage(), e);
        }
    }
    
    /**
     * Запускает системный ассистент через ACTION_ASSIST
     * 
     * @param context Контекст приложения
     */
    public static void launchSystemAssistant(Context context) {
        if (context == null) {
            Log.e(TAG, "Не удалось запустить системного ассистента: контекст равен null");
            return;
        }
        
        try {
            Intent intent = new Intent(Intent.ACTION_ASSIST);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            Log.d(TAG, "Запускаем системного ассистента");
            context.startActivity(intent);
            Log.d(TAG, "Системный ассистент запущен успешно");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при запуске системного ассистента: " + e.getMessage(), e);
        }
    }
    
    /**
     * Запускает новую активность системного ассистента с прозрачным фоном
     * 
     * @param context Контекст приложения
     */
    public static void launchSystemAssistantActivity(Context context) {
        if (context == null) {
            Log.e(TAG, "Не удалось запустить SystemAssistantActivity: контекст равен null");
            return;
        }
        
        try {
            Intent intent = new Intent(context, SystemAssistantActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.setAction("android.intent.action.SYSTEM_ASSIST");
            intent.putExtra("LAUNCHED_BY_ASSISTANT_LAUNCHER", true);
            
            Log.d(TAG, "Запускаем SystemAssistantActivity");
            context.startActivity(intent);
            Log.d(TAG, "SystemAssistantActivity запущена успешно");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при запуске SystemAssistantActivity: " + e.getMessage(), e);
        }
    }
    
    /**
     * Проверяет, запущена ли активность из ассистента
     * 
     * @param intent Интент, с которым была запущена активность
     * @return true, если активность запущена как ассистент
     */
    public static boolean isLaunchedAsAssistant(Intent intent) {
        if (intent == null) {
            return false;
        }
        
        String action = intent.getAction();
        boolean isAssist = Intent.ACTION_ASSIST.equals(action);
        
        // Логгируем информацию об интенте для отладки
        Log.d(TAG, "Intent action: " + action);
        if (intent.getExtras() != null) {
            for (String key : intent.getExtras().keySet()) {
                try {
                    Log.d(TAG, "Intent extra: " + key + " = " + intent.getExtras().get(key));
                } catch (Exception e) {
                    Log.d(TAG, "Ошибка при получении значения extra: " + key);
                }
            }
        }
        
        Log.d(TAG, "isLaunchedAsAssistant() = " + isAssist);
        return isAssist;
    }
    
    /**
     * Проверяет, запущена ли активность из системного жеста свайпа
     * 
     * @param intent Интент, с которым была запущена активность
     * @return true, если активность запущена через системный жест свайпа
     */
    public static boolean isLaunchedFromSystemGesture(Intent intent) {
        if (intent == null) {
            return false;
        }
        
        String action = intent.getAction();
        boolean isSystemAssist = "android.intent.action.SYSTEM_ASSIST".equals(action);
        
        // Проверяем наличие экстра-параметров, указывающих на запуск от системного жеста
        boolean hasAssistGestureExtra = intent.hasExtra("android.intent.extra.ASSIST_GESTURE");
        boolean hasInputDeviceExtra = intent.hasExtra("android.intent.extra.ASSIST_INPUT_DEVICE_ID");
        boolean hasInputHintsExtra = intent.hasExtra("android.intent.extra.ASSIST_INPUT_HINT_KEYBOARD");
        
        // Логгируем информацию об интенте для отладки
        Log.d(TAG, "Intent action for system gesture check: " + action);
        Log.d(TAG, "Has gesture extra: " + hasAssistGestureExtra);
        Log.d(TAG, "Has input device extra: " + hasInputDeviceExtra);
        Log.d(TAG, "Has input hints extra: " + hasInputHintsExtra);
        
        boolean isSystemGesture = isSystemAssist || hasAssistGestureExtra || 
                                 (Intent.ACTION_ASSIST.equals(action) && (hasInputDeviceExtra || hasInputHintsExtra));
        
        Log.d(TAG, "isLaunchedFromSystemGesture() = " + isSystemGesture);
        return isSystemGesture;
    }
} 