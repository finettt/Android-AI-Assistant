package io.finett.myapplication.adapter;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import io.finett.myapplication.R;
import io.finett.myapplication.model.ChatMessage;
import android.view.accessibility.AccessibilityManager;
import androidx.core.content.ContextCompat;

public class VoiceChatAdapter extends RecyclerView.Adapter<VoiceChatAdapter.MessageViewHolder> {
    private final List<ChatMessage> messages = new ArrayList<>();
    private final Context context;
    private final AccessibilityManager accessibilityManager;

    public VoiceChatAdapter(Context context) {
        this.context = context;
        this.accessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_voice_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.messageText.setText(message.getContent());
        
        // Настройка внешнего вида сообщения
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        
        if (message.isUser()) {
            params.gravity = Gravity.END;
            if (accessibilityManager.isEnabled()) {
                holder.messageCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.high_contrast_background));
                holder.messageText.setTextColor(ContextCompat.getColor(context, R.color.high_contrast_user_message));
            } else {
                holder.messageCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.message_user_background));
                holder.messageText.setTextColor(ContextCompat.getColor(context, R.color.message_user_text));
            }
        } else {
            params.gravity = Gravity.START;
            if (accessibilityManager.isEnabled()) {
                holder.messageCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.high_contrast_background));
                holder.messageText.setTextColor(ContextCompat.getColor(context, R.color.high_contrast_ai_message));
            } else {
                holder.messageCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.message_ai_background));
                holder.messageText.setTextColor(ContextCompat.getColor(context, R.color.message_ai_text));
            }
        }
        
        holder.messageCard.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void clearMessages() {
        messages.clear();
        notifyDataSetChanged();
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
} 