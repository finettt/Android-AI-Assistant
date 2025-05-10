package io.finett.myapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Адаптер для отображения сообщений в интерфейсе ассистента
 */
public class AssistantMessageAdapter extends RecyclerView.Adapter<AssistantMessageAdapter.MessageViewHolder> {
    
    private List<AssistantMessage> messages;
    private Context context;
    
    public AssistantMessageAdapter(Context context) {
        this.context = context;
        this.messages = new ArrayList<>();
    }
    
    /**
     * Добавляет новое сообщение в список и прокручивает к нему
     */
    public void addMessage(AssistantMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }
    
    /**
     * Очищает список сообщений
     */
    public void clearMessages() {
        int size = messages.size();
        messages.clear();
        notifyItemRangeRemoved(0, size);
    }
    
    /**
     * Заменяет все сообщения в списке
     */
    public void setMessages(List<AssistantMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_assistant_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        AssistantMessage message = messages.get(position);
        holder.messageText.setText(message.getText());
        holder.messageSource.setText(message.getSource());
        
        // Можно настроить разный вид для сообщений пользователя и ассистента
        if (message.isUser()) {
            holder.itemView.setAlpha(0.8f);
        } else {
            holder.itemView.setAlpha(1.0f);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView messageSource;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            messageSource = itemView.findViewById(R.id.sourceText);
        }
    }
} 