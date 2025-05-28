package io.finett.myapplication.model;

public class SystemPrompt {
    private String text;
    private String description;
    private boolean isActive;
    private boolean isDefault;
    private String id;

    public SystemPrompt() {
        this.id = String.valueOf(System.currentTimeMillis());
    }

    public SystemPrompt(String text, String description) {
        this();
        this.text = text;
        this.description = description;
    }

    public SystemPrompt(String text, String description, boolean isActive, boolean isDefault) {
        this(text, description);
        this.isActive = isActive;
        this.isDefault = isDefault;
    }

    public SystemPrompt(String id, String text, String description, boolean isActive, boolean isDefault) {
        this(text, description, isActive, isDefault);
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Возвращает содержимое системного промпта
     * @return текст системного промпта
     */
    public String getContent() {
        return getText();
    }
} 