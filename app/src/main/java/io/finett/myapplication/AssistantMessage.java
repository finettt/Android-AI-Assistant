package io.finett.myapplication;

/**
 * Модель сообщения в интерфейсе ассистента
 */
public class AssistantMessage {
    private String text;
    private String source;
    private boolean isUser;
    private long timestamp;

    /**
     * Создаёт новое сообщение ассистента
     * 
     * @param text Текст сообщения
     * @param source Источник сообщения (Алан, Пользователь и т.д.)
     * @param isUser true если сообщение от пользователя, false если от ассистента
     */
    public AssistantMessage(String text, String source, boolean isUser) {
        this.text = text;
        this.source = source;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis();
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isUser() {
        return isUser;
    }

    public void setUser(boolean user) {
        isUser = user;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Устанавливает текст сообщения (алиас для setText для совместимости с кодом)
     * @param message новый текст сообщения
     */
    public void setMessage(String message) {
        this.text = message;
    }
} 