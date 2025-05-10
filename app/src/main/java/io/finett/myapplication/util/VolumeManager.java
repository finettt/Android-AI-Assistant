package io.finett.myapplication.util;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import io.finett.myapplication.AssistantSettings;

/**
 * Класс для управления громкостью медиа при прослушивании голосовых команд
 */
public class VolumeManager {
    private static final String TAG = "VolumeManager";
    
    // Уровень громкости при распознавании (30% от максимальной)
    private static final float RECOGNITION_VOLUME_LEVEL = 0.3f;
    
    private final Context context;
    private final AudioManager audioManager;
    
    // Хранит оригинальную громкость для восстановления
    private int originalVolume;
    private boolean isVolumeReduced = false;
    
    public VolumeManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }
    
    /**
     * Снижает громкость для лучшего распознавания речи
     * @return true, если громкость была снижена
     */
    public boolean reduceVolumeForRecognition() {
        // Проверяем, включена ли опция автоматического снижения громкости
        if (!AssistantSettings.isAutoVolumeReductionEnabled(context)) {
            Log.d(TAG, "Автоматическое снижение громкости отключено в настройках");
            return false;
        }
        
        try {
            // Сохраняем текущую громкость
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            
            // Получаем максимальную громкость
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            
            // Вычисляем новую громкость (30% от максимальной)
            int reducedVolume = Math.max(1, (int) (maxVolume * RECOGNITION_VOLUME_LEVEL));
            
            // Снижаем громкость только если текущая громкость выше целевой
            if (originalVolume > reducedVolume) {
                // Устанавливаем новую громкость без звукового оповещения
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 
                        reducedVolume, 
                        0 /* без звукового эффекта */);
                
                isVolumeReduced = true;
                Log.d(TAG, "Громкость снижена с " + originalVolume + " до " + reducedVolume);
                return true;
            } else {
                Log.d(TAG, "Громкость уже достаточно низкая (" + originalVolume + "), снижение не требуется");
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при снижении громкости: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Восстанавливает исходную громкость
     */
    public void restoreVolume() {
        if (!isVolumeReduced) {
            return; // Громкость не была снижена
        }
        
        try {
            // Восстанавливаем оригинальную громкость без звукового оповещения
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 
                    originalVolume, 
                    0 /* без звукового эффекта */);
            
            Log.d(TAG, "Громкость восстановлена до " + originalVolume);
            isVolumeReduced = false;
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при восстановлении громкости: " + e.getMessage(), e);
        }
    }
    
    /**
     * Проверяет, была ли громкость снижена
     */
    public boolean isVolumeReduced() {
        return isVolumeReduced;
    }
} 