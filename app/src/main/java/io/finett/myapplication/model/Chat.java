package io.finett.myapplication.model;

import java.util.ArrayList;
import java.util.List;

public class Chat {
    private String id;
    private String title;
    private String modelId;
    private List<ChatMessage> messages;
    private long timestamp;

    public Chat(String title, String modelId) {
        this.id = String.valueOf(System.currentTimeMillis());
        this.title = title;
        this.modelId = modelId;
        this.messages = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
    }

    public long getTimestamp() {
        return timestamp;
    }
} 