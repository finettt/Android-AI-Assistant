package io.finett.myapplication.model;

public class SystemPrompt {
    private String content;
    private String description;
    private boolean isActive;

    public SystemPrompt(String content, String description) {
        this.content = content;
        this.description = description;
        this.isActive = false;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
} 