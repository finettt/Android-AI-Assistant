package io.finett.myapplication.model;

import android.net.Uri;

public class ChatMessage {
    private String content;
    private boolean isUser;
    private long timestamp;
    private String attachmentUri;
    private AttachmentType attachmentType;
    private boolean isEdited;

    public enum AttachmentType {
        NONE,
        IMAGE,
        FILE
    }

    public ChatMessage(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis();
        this.attachmentType = AttachmentType.NONE;
        this.isEdited = false;
    }

    public ChatMessage(String content, boolean isUser, String attachmentUri, AttachmentType attachmentType) {
        this(content, isUser);
        this.attachmentUri = attachmentUri;
        this.attachmentType = attachmentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.isEdited = true;
    }

    public boolean isUser() {
        return isUser;
    }

    public long getTimestamp() {
        return timestamp;
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