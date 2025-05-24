package io.finett.myapplication;

import android.content.Context;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;
import android.app.assist.AssistStructure;
import android.app.assist.AssistContent;

/**
 * Служба сессии голосового взаимодействия
 */
public class VoiceInteractionSessionService extends android.service.voice.VoiceInteractionSessionService {
    private static final String TAG = "VoiceInteractionSessSvc";

    @Override
    public VoiceInteractionSession onNewSession(Bundle args) {
        Log.d(TAG, "Создание новой сессии голосового взаимодействия с аргументами: " + (args != null ? args.toString() : "null"));
        
        return new AlanVoiceInteractionSession(this);
    }

    /**
     * Класс сессии голосового взаимодействия
     */
    private static class AlanVoiceInteractionSession extends VoiceInteractionSession {
        private static final String TAG = "AlanVoiceSession";

        public AlanVoiceInteractionSession(Context context) {
            super(context);
            Log.d(TAG, "Сессия создана с контекстом: " + (context != null ? context.getClass().getSimpleName() : "null"));
        }

        @Override
        public void onCreate() {
            super.onCreate();
            Log.d(TAG, "onCreate сессии вызван");
        }

        @Override
        public void onShow(Bundle args, int showFlags) {
            super.onShow(args, showFlags);
            Log.d(TAG, "Сессия отображена с флагами: " + showFlags);
            Log.d(TAG, "Аргументы: " + (args != null ? args.toString() : "null"));
            
            // Обновляем время последнего запуска ассистента
            AssistantMonitor.updateLastAssistStartTime();
            
            // Запускаем активность системного ассистента вместо отображения стандартного UI
            Context context = getContext();
            if (context != null) {
                try {
                    // Отправляем уведомление о запуске ассистента
                    AssistantMonitor.notifyAssistantStarted(context);
                    
                    // Записываем запуск ассистента в статистику
                    AssistantSettings.recordLaunch(context);
                    
                    // Используем вспомогательный класс для запуска системного ассистента
                    Log.d(TAG, "Запуск SystemAssistantActivity из сессии через AssistantLauncher");
                    AssistantLauncher.launchSystemAssistantActivity(context);
                    Log.d(TAG, "SystemAssistantActivity запущена успешно из сессии");
                    
                    // НЕ закрываем сессию, так как это может мешать работе ассистента
                    // hide();
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка при запуске SystemAssistantActivity: " + e.getMessage(), e);
                    
                    // Пробуем запустить обычную версию ассистента как запасной вариант
                    try {
                        Log.d(TAG, "Запуск AssistantActivity как запасной вариант");
                        AssistantLauncher.launchAssistant(context);
                    } catch (Exception ex) {
                        Log.e(TAG, "Не удалось запустить даже обычную AssistantActivity", ex);
                    }
                }
            } else {
                Log.e(TAG, "Контекст равен null, не могу запустить активность");
            }
        }

        @Override
        public void onHide() {
            super.onHide();
            Log.d(TAG, "Сессия скрыта");
        }

        @Override
        public void onHandleAssist(Bundle data, AssistStructure structure, AssistContent content) {
            super.onHandleAssist(data, structure, content);
            Log.d(TAG, "Обработка ассистента");
            Log.d(TAG, "Данные: " + (data != null ? data.toString() : "null"));
            Log.d(TAG, "Структура: " + (structure != null ? "присутствует" : "null"));
            Log.d(TAG, "Контент: " + (content != null ? "присутствует" : "null"));
        }

        @Override
        public void onHandleScreenshot(android.graphics.Bitmap screenshot) {
            super.onHandleScreenshot(screenshot);
            Log.d(TAG, "Получен снимок экрана: " + (screenshot != null ? screenshot.getWidth() + "x" + screenshot.getHeight() : "null"));
        }
        
        @Override
        public void onDestroy() {
            super.onDestroy();
            Log.d(TAG, "onDestroy сессии вызван");
        }
    }
} 