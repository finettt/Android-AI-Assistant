package io.finett.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Класс для работы с настройками ассистента
 */
public class AssistantSettings {
    private static final String TAG = "AssistantSettings";
    private static final String PREFS_NAME = "assistant_settings";
    
    // Ключи для SharedPreferences
    private static final String KEY_ANIMATION_ENABLED = "animation_enabled";
    private static final String KEY_AUTO_DISMISS_TIMEOUT = "auto_dismiss_timeout";
    private static final String KEY_ASSISTANT_THEME = "assistant_theme";
    private static final String KEY_LAUNCH_COUNT = "launch_count";
    private static final String KEY_LAST_LAUNCH_TIME = "last_launch_time";
    private static final String KEY_AUTO_VOLUME_REDUCTION = "auto_volume_reduction";
    private static final String KEY_AUTO_LAUNCH_VOICE_CHAT = "auto_launch_voice_chat";
    
    /**
     * Проверяет, включены ли анимации в ассистенте
     * 
     * @param context Контекст приложения
     * @return true, если анимации включены
     */
    public static boolean isAnimationEnabled(Context context) {
        if (context == null) {
            return true; // По умолчанию анимации включены
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getBoolean(KEY_ANIMATION_ENABLED, true);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при получении настройки анимации", e);
            return true;
        }
    }
    
    /**
     * Устанавливает, включены ли анимации в ассистенте
     * 
     * @param context Контекст приложения
     * @param enabled true, если анимации должны быть включены
     */
    public static void setAnimationEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        
        try {
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putBoolean(KEY_ANIMATION_ENABLED, enabled);
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при сохранении настройки анимации", e);
        }
    }
    
    /**
     * Получает время автоматического закрытия ассистента в миллисекундах
     * 
     * @param context Контекст приложения
     * @return Время в миллисекундах (по умолчанию 20000 мс)
     */
    public static int getAutoDismissTimeout(Context context) {
        if (context == null) {
            return 20000; // По умолчанию 20 секунд
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getInt(KEY_AUTO_DISMISS_TIMEOUT, 20000);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при получении времени автоматического закрытия", e);
            return 20000;
        }
    }
    
    /**
     * Устанавливает время автоматического закрытия ассистента в миллисекундах
     * 
     * @param context Контекст приложения
     * @param timeout Время в миллисекундах
     */
    public static void setAutoDismissTimeout(Context context, int timeout) {
        if (context == null) {
            return;
        }
        
        try {
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putInt(KEY_AUTO_DISMISS_TIMEOUT, timeout);
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при сохранении времени автоматического закрытия", e);
        }
    }
    
    /**
     * Увеличивает счетчик запусков ассистента
     * 
     * @param context Контекст приложения
     * @return Новое значение счетчика
     */
    public static int incrementLaunchCount(Context context) {
        if (context == null) {
            return 0;
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int currentCount = prefs.getInt(KEY_LAUNCH_COUNT, 0);
            int newCount = currentCount + 1;
            
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(KEY_LAUNCH_COUNT, newCount);
            editor.putLong(KEY_LAST_LAUNCH_TIME, System.currentTimeMillis());
            editor.apply();
            
            return newCount;
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при увеличении счетчика запусков", e);
            return 0;
        }
    }
    
    /**
     * Получает количество запусков ассистента
     * 
     * @param context Контекст приложения
     * @return Количество запусков
     */
    public static int getLaunchCount(Context context) {
        if (context == null) {
            return 0;
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getInt(KEY_LAUNCH_COUNT, 0);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при получении количества запусков", e);
            return 0;
        }
    }
    
    /**
     * Получает время последнего запуска ассистента
     * 
     * @param context Контекст приложения
     * @return Время в миллисекундах или 0, если ассистент еще не запускался
     */
    public static long getLastLaunchTime(Context context) {
        if (context == null) {
            return 0;
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getLong(KEY_LAST_LAUNCH_TIME, 0);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при получении времени последнего запуска", e);
            return 0;
        }
    }
    
    /**
     * Проверяет, включено ли автоматическое снижение громкости при прослушивании
     * 
     * @param context Контекст приложения
     * @return true, если автоматическое снижение громкости включено
     */
    public static boolean isAutoVolumeReductionEnabled(Context context) {
        if (context == null) {
            return true; // По умолчанию включено
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getBoolean(KEY_AUTO_VOLUME_REDUCTION, true);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при получении настройки автоматического снижения громкости", e);
            return true;
        }
    }
    
    /**
     * Устанавливает, включено ли автоматическое снижение громкости при прослушивании
     * 
     * @param context Контекст приложения
     * @param enabled true, если автоматическое снижение громкости должно быть включено
     */
    public static void setAutoVolumeReductionEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        
        try {
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putBoolean(KEY_AUTO_VOLUME_REDUCTION, enabled);
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при сохранении настройки автоматического снижения громкости", e);
        }
    }
    
    /**
     * Проверяет, включен ли автоматический запуск голосового чата при запуске системного ассистента
     * 
     * @param context Контекст приложения
     * @return true, если автоматический запуск голосового чата включен
     */
    public static boolean isAutoLaunchVoiceChatEnabled(Context context) {
        if (context == null) {
            return false; // По умолчанию отключено
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getBoolean(KEY_AUTO_LAUNCH_VOICE_CHAT, false);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при получении настройки автоматического запуска голосового чата", e);
            return false;
        }
    }
    
    /**
     * Устанавливает, включен ли автоматический запуск голосового чата при запуске системного ассистента
     * 
     * @param context Контекст приложения
     * @param enabled true, если автоматический запуск голосового чата должен быть включен
     */
    public static void setAutoLaunchVoiceChatEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        
        try {
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putBoolean(KEY_AUTO_LAUNCH_VOICE_CHAT, enabled);
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при сохранении настройки автоматического запуска голосового чата", e);
        }
    }
    
    /**
     * Записывает запуск ассистента и обновляет статистику использования
     * 
     * @param context Контекст приложения
     * @return Новое значение счетчика запусков
     */
    public static int recordLaunch(Context context) {
        if (context == null) {
            return 0;
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int currentCount = prefs.getInt(KEY_LAUNCH_COUNT, 0);
            int newCount = currentCount + 1;
            
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(KEY_LAUNCH_COUNT, newCount);
            editor.putLong(KEY_LAST_LAUNCH_TIME, System.currentTimeMillis());
            editor.apply();
            
            Log.d(TAG, "Записан запуск ассистента. Всего запусков: " + newCount);
            return newCount;
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при записи запуска ассистента", e);
            return 0;
        }
    }
} 