package io.finett.myapplication.util;

/**
 * Интерфейс для проигрывания музыки или звуков
 */
public interface MusicPlayer {
    
    /**
     * Метод для проигрывания музыки или звука
     * @param url URL или путь к звуковому файлу
     */
    void play(String url);
    
    /**
     * Остановка проигрывания
     */
    void stop();
    
    /**
     * Установка слушателя событий
     * @param listener слушатель событий проигрывателя
     */
    void setListener(MusicControlListener listener);
    
    /**
     * Освобождение ресурсов
     */
    void release();
} 