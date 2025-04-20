package io.finett.myapplication.model;

import android.net.Uri;
import java.util.Date;

public class ChatMessage {
    private String text;
    private boolean isUserMessage;
    private long timestamp;
    private String attachmentUri;
    private AttachmentType attachmentType;
    private boolean isEdited;

    public enum AttachmentType {
        NONE,
        IMAGE,
        FILE
    }

    public ChatMessage(String text, boolean isUserMessage) {
        this.text = text;
        this.isUserMessage = isUserMessage;
        this.timestamp = new Date().getTime();
        this.attachmentType = AttachmentType.NONE;
        this.isEdited = false;
    }

    public ChatMessage(String text, boolean isUserMessage, String attachmentUri, AttachmentType attachmentType) {
        this(text, isUserMessage);
        this.attachmentUri = attachmentUri;
        this.attachmentType = attachmentType;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        this.isEdited = true;
    }

    public boolean isUserMessage() {
        return isUserMessage;
    }

    public void setUserMessage(boolean userMessage) {
        isUserMessage = userMessage;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getAttachmentUri() {
        return attachmentUri;
    }

    public AttachmentType getAttachmentType() {
        return attachmentType;
    }

    public boolean hasAttachment() {
        return attachmentType != AttachmentType.NONE;
    }

    public boolean isEdited() {
        return isEdited;
    }
} 