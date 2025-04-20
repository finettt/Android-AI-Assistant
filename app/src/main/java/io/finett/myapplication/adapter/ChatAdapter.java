package io.finett.myapplication.adapter;

import android.content.Context;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.finett.myapplication.MainActivity;
import io.finett.myapplication.R;
import io.finett.myapplication.model.ChatMessage;
import io.finett.myapplication.util.AccessibilityManager;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<ChatMessage> messages = new ArrayList<>();
    private final Context context;
    private final OnAttachmentClickListener attachmentClickListener;
    private final OnMessageActionListener messageActionListener;
    private final AccessibilityManager accessibilityManager;
    
    private boolean isLoading = false;
    private static final int VIEW_TYPE_MESSAGE = 0;
    private static final int VIEW_TYPE_LOADING = 1;

    public ChatAdapter(Context context, OnAttachmentClickListener attachmentClickListener) {
        this.context = context;
        this.attachmentClickListener = attachmentClickListener;
        this.messageActionListener = (OnMessageActionListener) context;
        this.accessibilityManager = new AccessibilityManager(context, null);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_LOADING) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_loading_message, parent, false);
            return new LoadingViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_message, parent, false);
            return new MessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MessageViewHolder) {
            bindMessageViewHolder((MessageViewHolder) holder, position);
        } else if (holder instanceof LoadingViewHolder) {
            // No binding needed for loading placeholder
        }
    }
    
    private void bindMessageViewHolder(MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        
        // Применяем настройки доступности к тексту
        accessibilityManager.applyTextSize(holder.messageText);
        if (accessibilityManager.isHighContrastEnabled()) {
            holder.messageText.setBackgroundColor(0xFF000000);
            holder.messageText.setTextColor(0xFFFFFFFF);
        }

        // Устанавливаем выравнивание в зависимости от отправителя
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        
        if (message.isUserMessage()) {
            params.gravity = Gravity.END;
            holder.messageContainer.setBackgroundResource(R.drawable.bg_message_user);
        } else {
            params.gravity = Gravity.START;
            holder.messageContainer.setBackgroundResource(R.drawable.bg_message_ai);
        }
        holder.messageContainer.setLayoutParams(params);

        holder.messageText.setText(message.getText());
        
        // Отображение отметки о редактировании
        holder.editedMark.setVisibility(message.isEdited() ? View.VISIBLE : View.GONE);
        
        // Если есть вложение
        if (message.hasAttachment()) {
            holder.attachmentImage.setVisibility(View.VISIBLE);
            if (message.getAttachmentType() == ChatMessage.AttachmentType.IMAGE) {
                Glide.with(context)
                        .load(message.getAttachmentUri())
                        .into(holder.attachmentImage);
            } else {
                holder.attachmentImage.setImageResource(R.drawable.ic_file);
            }
            holder.attachmentImage.setOnClickListener(v -> {
                if (attachmentClickListener != null) {
                    attachmentClickListener.onAttachmentClick(message);
                }
            });
        } else {
            holder.attachmentImage.setVisibility(View.GONE);
        }

        // Добавляем обработку долгого нажатия для редактирования/удаления
        holder.itemView.setOnLongClickListener(v -> {
            if (messageActionListener != null && message.isUserMessage()) {
                showMessageActions(message, position, holder.itemView);
            }
            return true;
        });

        // Озвучиваем сообщение, если включена голосовая обратная связь
        if (!message.isUserMessage() && accessibilityManager.isTtsFeedbackEnabled()) {
            accessibilityManager.speak(message.getText());
        }

        // Вибрируем при новом сообщении, если включена вибрация
        if (position == messages.size() - 1 && accessibilityManager.isVibrationFeedbackEnabled()) {
            accessibilityManager.vibrate();
        }
    }

    private void showMessageActions(ChatMessage message, int position, View anchor) {
        PopupMenu popup = new PopupMenu(context, anchor);
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

    @Override
    public int getItemCount() {
        return messages.size() + (isLoading ? 1 : 0);
    }
    
    @Override
    public int getItemViewType(int position) {
        return (position == messages.size() && isLoading) ? VIEW_TYPE_LOADING : VIEW_TYPE_MESSAGE;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages.clear();
        if (messages != null) {
            this.messages.addAll(messages);
        }
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
        scrollToLastItem();
    }

    public void updateMessage(int position) {
        notifyItemChanged(position);
    }

    public void removeMessage(int position) {
        messages.remove(position);
        notifyItemRemoved(position);
    }

    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }
    
    public void setLoading(boolean loading) {
        if (this.isLoading != loading) {
            this.isLoading = loading;
            if (loading) {
                notifyItemInserted(messages.size());
                scrollToLastItem();
            } else {
                notifyItemRemoved(messages.size());
            }
        }
    }
    
    private void scrollToLastItem() {
        if (context instanceof AppCompatActivity) {
            RecyclerView recyclerView = ((AppCompatActivity) context).findViewById(R.id.chatRecyclerView);
            if (recyclerView != null) {
                recyclerView.smoothScrollToPosition(getItemCount() - 1);
            }
        }
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout messageContainer;
        TextView messageText;
        TextView editedMark;
        ImageView attachmentImage;
        TextView imageCaption;
        LinearLayout fileAttachmentLayout;
        TextView fileNameText;

        MessageViewHolder(View itemView) {
            super(itemView);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            messageText = itemView.findViewById(R.id.message_text);
            editedMark = itemView.findViewById(R.id.editedMark);
            attachmentImage = itemView.findViewById(R.id.attachment_image);
            imageCaption = itemView.findViewById(R.id.imageCaption);
            fileAttachmentLayout = itemView.findViewById(R.id.fileAttachmentLayout);
            fileNameText = itemView.findViewById(R.id.fileNameText);
        }
    }
    
    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        LoadingViewHolder(View itemView) {
            super(itemView);
        }
    }

    public interface OnAttachmentClickListener {
        void onAttachmentClick(ChatMessage message);
    }

    public interface OnMessageActionListener {
        void onEditMessage(ChatMessage message, int position);
        void onDeleteMessage(ChatMessage message, int position);
    }
} 