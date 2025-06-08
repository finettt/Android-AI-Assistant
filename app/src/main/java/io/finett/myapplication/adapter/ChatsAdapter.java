package io.finett.myapplication.adapter;

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

import io.finett.myapplication.R;
import io.finett.myapplication.model.Chat;

public class ChatsAdapter extends RecyclerView.Adapter<ChatsAdapter.ChatViewHolder> {
    private List<Chat> chats = new ArrayList<>();
    private OnChatClickListener listener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    public interface OnChatClickListener {
        void onChatClick(Chat chat);
    }

    public ChatsAdapter(OnChatClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        Chat chat = chats.get(position);
        holder.titleText.setText(chat.getTitle());
        holder.modelText.setText(chat.getModelId());
        holder.dateText.setText(dateFormat.format(new Date(chat.getTimestamp())));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChatClick(chat);
            }
        });
    }

    @Override
    public int getItemCount() {
        return chats.size();
    }

    public void setChats(List<Chat> chats) {
        this.chats = chats;
        notifyDataSetChanged();
    }

    public void addChat(Chat chat) {
        chats.add(0, chat);
        notifyItemInserted(0);
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView modelText;
        TextView dateText;

        ChatViewHolder(View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.chatTitle);
            modelText = itemView.findViewById(R.id.modelName);
            dateText = itemView.findViewById(R.id.chatDate);
        }
    }
}