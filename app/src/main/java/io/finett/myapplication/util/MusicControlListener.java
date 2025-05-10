package io.finett.myapplication.util;

/**
 * Интерфейс для слушателя событий музыкального проигрывателя
 */
public interface MusicControlListener {
    
    /**
     * Вызывается при начале воспроизведения
     */
    void onStart();
    
    /**
     * Вызывается при завершении воспроизведения
     */
    void onComplete();
    
    /**
     * Вызывается при ошибке воспроизведения
     * @param errorMessage сообщение об ошибке
     */
    void onError(String errorMessage);
} 