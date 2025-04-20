package io.finett.myapplication.adapter;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import io.finett.myapplication.R;
import io.finett.myapplication.model.ChatMessage;
import android.view.accessibility.AccessibilityManager;
import androidx.core.content.ContextCompat;
import io.noties.markwon.Markwon;

public class VoiceChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<ChatMessage> messages = new ArrayList<>();
    private final Context context;
    private final AccessibilityManager accessibilityManager;
    private final Markwon markwon;
    
    private boolean isLoading = false;
    private static final int VIEW_TYPE_MESSAGE = 0;
    private static final int VIEW_TYPE_LOADING = 1;

    public VoiceChatAdapter(Context context) {
        this.context = context;
        this.accessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        this.markwon = Markwon.create(context);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_LOADING) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_loading_message, parent, false);
            return new LoadingViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_voice_message, parent, false);
            return new MessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MessageViewHolder) {
            ChatMessage message = messages.get(position);
            MessageViewHolder messageHolder = (MessageViewHolder) holder;
            
            // Применяем Markdown форматирование для сообщений ассистента
            if (message.isUserMessage()) {
                messageHolder.messageText.setText(message.getText());
            } else {
                markwon.setMarkdown(messageHolder.messageText, message.getText());
            }
            
            // Настройка внешнего вида сообщения
            if (message.isUserMessage()) {
                messageHolder.messageCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.user_message_background));
                LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) messageHolder.messageCard.getLayoutParams();
                layoutParams.gravity = Gravity.END;
                messageHolder.messageCard.setLayoutParams(layoutParams);
            } else {
                messageHolder.messageCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.assistant_message_background));
                LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) messageHolder.messageCard.getLayoutParams();
                layoutParams.gravity = Gravity.START;
                messageHolder.messageCard.setLayoutParams(layoutParams);
            }
            
            // Доступность для screen readers
            if (accessibilityManager != null && accessibilityManager.isEnabled()) {
                messageHolder.messageText.setContentDescription(
                    (message.isUserMessage() ? "Вы: " : "Ассистент: ") + message.getText());
            }
        } else if (holder instanceof LoadingViewHolder) {
            // No binding needed for loading placeholder
        }
    }

    @Override
    public int getItemCount() {
        return messages.size() + (isLoading ? 1 : 0);
    }
    
    @Override
    public int getItemViewType(int position) {
        return (position == messages.size() && isLoading) ? VIEW_TYPE_LOADING : VIEW_TYPE_MESSAGE;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
        scrollToLastItem();
    }

    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
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
            RecyclerView recyclerView = ((AppCompatActivity) context).findViewById(R.id.voice_chat_recycler_view);
            if (recyclerView != null) {
                recyclerView.smoothScrollToPosition(getItemCount() - 1);
            }
        }
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final CardView messageCard;
        final TextView messageText;

        MessageViewHolder(View itemView) {
            super(itemView);
            messageCard = itemView.findViewById(R.id.messageCard);
            messageText = itemView.findViewById(R.id.messageText);
        }
    }
    
    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        LoadingViewHolder(View itemView) {
            super(itemView);
        }
    }
} 