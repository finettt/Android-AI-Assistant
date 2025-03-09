package io.finett.myapplication.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
    private OnMessageActionListener messageActionListener;

    public interface OnAttachmentClickListener {
        void onAttachmentClick(ChatMessage message);
    }

    public interface OnMessageActionListener {
        void onEditMessage(ChatMessage message, int position);
        void onDeleteMessage(ChatMessage message, int position);
    }

    public ChatAdapter(OnAttachmentClickListener attachmentListener, OnMessageActionListener actionListener) {
        this.attachmentClickListener = attachmentListener;
        this.messageActionListener = actionListener;
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
            holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.message_user_text));
            containerParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            ((LinearLayout.LayoutParams) holder.messageContainer.getLayoutParams())
                .gravity = Gravity.END;
        } else {
            holder.messageContainer.setBackgroundResource(R.drawable.bg_message_ai);
            holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.message_ai_text));
            containerParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            ((LinearLayout.LayoutParams) holder.messageContainer.getLayoutParams())
                .gravity = Gravity.START;
        }
        holder.messageContainer.setLayoutParams(containerParams);

        // Показываем текст сообщения, если он есть
        if (!message.getContent().isEmpty()) {
            holder.messageText.setVisibility(View.VISIBLE);
            holder.messageText.setText(message.getContent());
            holder.editedMark.setVisibility(message.isEdited() ? View.VISIBLE : View.GONE);
        } else {
            holder.messageText.setVisibility(View.GONE);
            holder.editedMark.setVisibility(View.GONE);
        }

        // Настраиваем контекстное меню для сообщений пользователя
        if (message.isUser()) {
            holder.messageContainer.setOnLongClickListener(v -> {
                showMessageContextMenu(v, message, holder.getAdapterPosition());
                return true;
            });
        } else {
            holder.messageContainer.setOnLongClickListener(null);
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

    private void showMessageContextMenu(View view, ChatMessage message, int position) {
        PopupMenu popup = new PopupMenu(view.getContext(), view);
        popup.inflate(R.menu.menu_message_context);
        
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_edit) {
                if (messageActionListener != null) {
                    messageActionListener.onEditMessage(message, position);
                }
                return true;
            } else if (itemId == R.id.action_delete) {
                if (messageActionListener != null) {
                    messageActionListener.onDeleteMessage(message, position);
                }
                return true;
            }
            return false;
        });
        
        popup.show();
    }

    public void updateMessage(int position) {
        notifyItemChanged(position);
    }

    public void removeMessage(int position) {
        messages.remove(position);
        notifyItemRemoved(position);
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
        TextView editedMark;
        ImageView attachedImage;
        TextView imageCaption;
        LinearLayout fileAttachmentLayout;
        TextView fileNameText;

        MessageViewHolder(View itemView) {
            super(itemView);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            messageText = itemView.findViewById(R.id.messageText);
            editedMark = itemView.findViewById(R.id.editedMark);
            attachedImage = itemView.findViewById(R.id.attachedImage);
            imageCaption = itemView.findViewById(R.id.imageCaption);
            fileAttachmentLayout = itemView.findViewById(R.id.fileAttachmentLayout);
            fileNameText = itemView.findViewById(R.id.fileNameText);
        }
    }
} 