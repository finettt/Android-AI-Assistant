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
import io.finett.myapplication.util.AccessibilityManager;

public class VoiceChatAdapter extends RecyclerView.Adapter<VoiceChatAdapter.MessageViewHolder> {
    private final List<ChatMessage> messages = new ArrayList<>();
    private final Context context;
    private final AccessibilityManager accessibilityManager;

    public VoiceChatAdapter(Context context) {
        this.context = context;
        this.accessibilityManager = new AccessibilityManager(context, null);
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
        
        // Применяем настройки доступности к тексту
        accessibilityManager.applyTextSize(holder.messageText);
        
        // Устанавливаем текст сообщения
        holder.messageText.setText(message.getContent());
        
        // Устанавливаем выравнивание и цвет фона в зависимости от отправителя
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        
        if (message.isUser()) {
            holder.messageCard.setCardBackgroundColor(context.getColor(R.color.message_user_background));
            params.gravity = Gravity.END;
        } else {
            holder.messageCard.setCardBackgroundColor(context.getColor(R.color.message_ai_background));
            params.gravity = Gravity.START;
        }
        holder.messageCard.setLayoutParams(params);
        
        // Применяем высокий контраст, если включено
        if (accessibilityManager.isHighContrastEnabled()) {
            holder.messageCard.setCardBackgroundColor(0xFF000000);
            holder.messageText.setTextColor(0xFFFFFFFF);
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

    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        CardView messageCard;
        TextView messageText;

        MessageViewHolder(View itemView) {
            super(itemView);
            messageCard = itemView.findViewById(R.id.messageCard);
            messageText = itemView.findViewById(R.id.message_text);
        }
    }
} 