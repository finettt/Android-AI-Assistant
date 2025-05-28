package io.finett.myapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.finett.myapplication.util.MessageAnimator;

/**
 * Адаптер для отображения сообщений в системном ассистенте
 */
public class SystemAssistantMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_ASSISTANT = 2;
    private static final int VIEW_TYPE_LOADING = 3;

    private final Context context;
    private final List<AssistantMessage> messages;
    private boolean isLoading = false;

    public SystemAssistantMessageAdapter(Context context) {
        this.context = context;
        this.messages = new ArrayList<>();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == VIEW_TYPE_USER) {
            View view = inflater.inflate(R.layout.item_user_message, parent, false);
            return new UserMessageViewHolder(view);
        } else if (viewType == VIEW_TYPE_ASSISTANT) {
            View view = inflater.inflate(R.layout.item_assistant_message, parent, false);
            return new AssistantMessageViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_loading_message, parent, false);
            return new LoadingViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (isLoading && position == messages.size()) {
            // Loading placeholder, nothing to bind
            return;
        }

        AssistantMessage message = messages.get(position);
        if (holder instanceof UserMessageViewHolder) {
            UserMessageViewHolder userHolder = (UserMessageViewHolder) holder;
            userHolder.messageText.setText(message.getText());
            userHolder.timeText.setText(formatTime(message.getTimestamp()));
        } else if (holder instanceof AssistantMessageViewHolder) {
            AssistantMessageViewHolder assistantHolder = (AssistantMessageViewHolder) holder;
            assistantHolder.messageText.setText(message.getText());
            assistantHolder.sourceText.setText(message.getSource());
            assistantHolder.timeText.setText(formatTime(message.getTimestamp()));
        }
        
        // Применяем анимацию появления новых сообщений
        MessageAnimator.animate(holder.itemView, position, context);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        // Очищаем анимацию при откреплении вида
        holder.itemView.clearAnimation();
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public int getItemCount() {
        return messages.size() + (isLoading ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        if (isLoading && position == messages.size()) {
            return VIEW_TYPE_LOADING;
        }
        return messages.get(position).isUser() ? VIEW_TYPE_USER : VIEW_TYPE_ASSISTANT;
    }

    public void addMessage(AssistantMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    /**
     * Удаляет сообщение из списка
     * @param message сообщение для удаления
     */
    public void removeMessage(AssistantMessage message) {
        int position = messages.indexOf(message);
        if (position != -1) {
            messages.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void setMessages(List<AssistantMessage> messages) {
        this.messages.clear();
        if (messages != null) {
            this.messages.addAll(messages);
        }
        // Сбрасываем состояние анимации
        MessageAnimator.resetAnimationState();
        notifyDataSetChanged();
    }

    public void setLoading(boolean loading) {
        if (this.isLoading != loading) {
            this.isLoading = loading;
            if (loading) {
                notifyItemInserted(messages.size());
            } else {
                notifyItemRemoved(messages.size());
            }
        }
    }

    public void clear() {
        messages.clear();
        // Сбрасываем состояние анимации
        MessageAnimator.resetAnimationState();
        notifyDataSetChanged();
    }

    private String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    // ViewHolder классы
    private static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView timeText;

        UserMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
        }
    }

    private static class AssistantMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView sourceText;
        TextView timeText;

        AssistantMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            sourceText = itemView.findViewById(R.id.sourceText);
            timeText = itemView.findViewById(R.id.timeText);
        }
    }

    private static class LoadingViewHolder extends RecyclerView.ViewHolder {
        LoadingViewHolder(View itemView) {
            super(itemView);
        }
    }
} 