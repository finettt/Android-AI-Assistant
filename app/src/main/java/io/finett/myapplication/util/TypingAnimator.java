package io.finett.myapplication.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import io.finett.myapplication.adapter.VoiceChatAdapter;
import io.finett.myapplication.model.ChatMessage;

/**
 * Класс для создания анимации печатания текста в сообщениях
 */
public class TypingAnimator {
    private static final String TAG = "TypingAnimator";
    private static final int TYPING_DELAY_MS = 30; // Задержка между добавлением символов
    private static final int TYPING_RANDOM_FACTOR = 20; // Случайная задержка для естественности
    
    private final Handler handler;
    private final VoiceChatAdapter adapter;
    private ChatMessage targetMessage;
    private String fullContent;
    private int currentPosition;
    private Runnable typingRunnable;
    private boolean isAnimating;
    private TypingCompletionListener completionListener;
    
    public interface TypingCompletionListener {
        void onTypingCompleted(ChatMessage message);
    }
    
    public TypingAnimator(VoiceChatAdapter adapter) {
        this.adapter = adapter;
        this.handler = new Handler(Looper.getMainLooper());
        this.isAnimating = false;
    }
    
    /**
     * Запускает анимацию печатания для сообщения
     * @param message сообщение, для которого запускается анимация
     * @param fullText полный текст, который должен быть напечатан
     */
    public void startTypingAnimation(ChatMessage message, String fullText) {
        if (isAnimating) {
            stopTypingAnimation();
        }
        
        if (message == null || fullText == null) {
            Log.e(TAG, "Сообщение или текст равны null");
            return;
        }
        
        targetMessage = message;
        fullContent = fullText;
        currentPosition = 0;
        isAnimating = true;
        
        // Устанавливаем пустой текст в сообщении вначале
        targetMessage.setText("");
        
        // Создаем и запускаем runnable для анимации
        typingRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentPosition < fullContent.length()) {
                    // Добавляем следующий символ
                    currentPosition++;
                    String currentText = fullContent.substring(0, currentPosition);
                    targetMessage.setText(currentText);
                    
                    // Обновляем адаптер
                    adapter.notifyDataSetChanged();
                    
                    // Вычисляем случайную задержку для естественности
                    int randomDelay = TYPING_DELAY_MS + (int)(Math.random() * TYPING_RANDOM_FACTOR);
                    
                    // Планируем следующий шаг анимации
                    handler.postDelayed(this, randomDelay);
                } else {
                    // Анимация завершена
                    isAnimating = false;
                    if (completionListener != null) {
                        completionListener.onTypingCompleted(targetMessage);
                    }
                }
            }
        };
        
        // Запускаем анимацию
        handler.post(typingRunnable);
    }
    
    /**
     * Останавливает текущую анимацию печатания
     */
    public void stopTypingAnimation() {
        if (isAnimating && typingRunnable != null) {
            handler.removeCallbacks(typingRunnable);
            isAnimating = false;
            
            // Устанавливаем полный текст для сообщения
            if (targetMessage != null && fullContent != null) {
                targetMessage.setText(fullContent);
                adapter.notifyDataSetChanged();
            }
        }
    }
    
    /**
     * Проверяет, идет ли анимация в данный момент
     */
    public boolean isAnimating() {
        return isAnimating;
    }
    
    /**
     * Устанавливает слушатель завершения анимации
     */
    public void setCompletionListener(TypingCompletionListener listener) {
        this.completionListener = listener;
    }
} 