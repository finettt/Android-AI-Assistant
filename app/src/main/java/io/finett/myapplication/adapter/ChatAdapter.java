package io.finett.myapplication.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.finett.myapplication.R;
import io.finett.myapplication.model.ChatMessage;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {
    private List<ChatMessage> messages = new ArrayList<>();
    private OnAttachmentClickListener attachmentClickListener;

    public interface OnAttachmentClickListener {
        void onAttachmentClick(ChatMessage message);
    }

    public ChatAdapter(OnAttachmentClickListener listener) {
        this.attachmentClickListener = listener;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        
        // Настраиваем стиль сообщения
        ViewGroup.LayoutParams containerParams = holder.messageContainer.getLayoutParams();
        if (message.isUser()) {
            holder.messageContainer.setBackgroundResource(R.drawable.bg_message_user);
            containerParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            holder.messageContainer.setLayoutParams(containerParams);
        } else {
            holder.messageContainer.setBackgroundResource(R.drawable.bg_message_bot);
            containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            holder.messageContainer.setLayoutParams(containerParams);
        }

        // Показываем текст сообщения, если он есть
        if (!message.getContent().isEmpty()) {
            holder.messageText.setVisibility(View.VISIBLE);
            holder.messageText.setText(message.getContent());
        } else {
            holder.messageText.setVisibility(View.GONE);
        }

        // Обрабатываем вложения
        if (message.hasAttachment()) {
            switch (message.getAttachmentType()) {
                case IMAGE:
                    holder.attachedImage.setVisibility(View.VISIBLE);
                    holder.fileAttachmentLayout.setVisibility(View.GONE);
                    holder.imageCaption.setVisibility(
                            message.getContent().isEmpty() ? View.GONE : View.VISIBLE);
                    
                    if (!message.getContent().isEmpty()) {
                        holder.imageCaption.setText(message.getContent());
                    }
                    
                    Glide.with(holder.attachedImage)
                            .load(Uri.parse(message.getAttachmentUri()))
                            .into(holder.attachedImage);
                    
                    holder.attachedImage.setOnClickListener(v -> {
                        if (attachmentClickListener != null) {
                            attachmentClickListener.onAttachmentClick(message);
                        }
                    });
                    break;
                    
                case FILE:
                    holder.attachedImage.setVisibility(View.GONE);
                    holder.imageCaption.setVisibility(View.GONE);
                    holder.fileAttachmentLayout.setVisibility(View.VISIBLE);
                    String fileName = new File(Uri.parse(message.getAttachmentUri()).getPath()).getName();
                    holder.fileNameText.setText(fileName);
                    holder.fileAttachmentLayout.setOnClickListener(v -> {
                        if (attachmentClickListener != null) {
                            attachmentClickListener.onAttachmentClick(message);
                        }
                    });
                    break;
                    
                default:
                    holder.attachedImage.setVisibility(View.GONE);
                    holder.imageCaption.setVisibility(View.GONE);
                    holder.fileAttachmentLayout.setVisibility(View.GONE);
            }
        } else {
            holder.attachedImage.setVisibility(View.GONE);
            holder.imageCaption.setVisibility(View.GONE);
            holder.fileAttachmentLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = new ArrayList<>(messages);
        notifyDataSetChanged();
    }

    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout messageContainer;
        TextView messageText;
        ImageView attachedImage;
        TextView imageCaption;
        LinearLayout fileAttachmentLayout;
        TextView fileNameText;

        MessageViewHolder(View itemView) {
            super(itemView);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            messageText = itemView.findViewById(R.id.messageText);
            attachedImage = itemView.findViewById(R.id.attachedImage);
            imageCaption = itemView.findViewById(R.id.imageCaption);
            fileAttachmentLayout = itemView.findViewById(R.id.fileAttachmentLayout);
            fileNameText = itemView.findViewById(R.id.fileNameText);
        }
    }
} 