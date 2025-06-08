package io.finett.myapplication.model;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

public class ChatMessage implements Parcelable {
    private String text;
    private boolean isUserMessage;
    private long timestamp;
    private String attachmentUri;
    private AttachmentType attachmentType;
    private boolean isEdited;
    private String displayText; // Для анимации печатания

    public enum AttachmentType {
        NONE,
        IMAGE,
        FILE
    }

    public ChatMessage(String text, boolean isUserMessage) {
        if (isUserMessage && text != null && !text.isEmpty()) {
            this.text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
        } else {
            this.text = text;
        }
        this.displayText = this.text;
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

    // Конструктор для Parcelable
    protected ChatMessage(Parcel in) {
        text = in.readString();
        displayText = in.readString();
        isUserMessage = in.readByte() != 0;
        timestamp = in.readLong();
        isEdited = in.readByte() != 0;
        attachmentUri = in.readString();
        attachmentType = AttachmentType.values()[in.readInt()];
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(text);
        dest.writeString(displayText);
        dest.writeByte((byte) (isUserMessage ? 1 : 0));
        dest.writeLong(timestamp);
        dest.writeByte((byte) (isEdited ? 1 : 0));
        dest.writeString(attachmentUri);
        dest.writeInt(attachmentType.ordinal());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ChatMessage> CREATOR = new Creator<ChatMessage>() {
        @Override
        public ChatMessage createFromParcel(Parcel in) {
            return new ChatMessage(in);
        }

        @Override
        public ChatMessage[] newArray(int size) {
            return new ChatMessage[size];
        }
    };

    public String getText() {
        return text;
    }
    
    /**
     * Получает текст для отображения (может содержать анимацию печатания)
     */
    public String getDisplayText() {
        return displayText != null ? displayText : text;
    }
    
    /**
     * Устанавливает текст для отображения (для анимации печатания)
     */
    public void setTextForDisplay(String displayText) {
        this.displayText = displayText;
    }

    public void setText(String text) {
        if (isUserMessage && text != null && !text.isEmpty()) {
            this.text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
        } else {
            this.text = text;
        }
        this.displayText = this.text;
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

    /**
     * Проверяет, является ли сообщение пользовательским
     * @return true если сообщение от пользователя, false в противном случае
     */
    public boolean isUser() {
        return isUserMessage();
    }
} 