package io.finett.myapplication.adapter;

import android.animation.AnimatorInflater;
import android.animation.Animator;
import android.content.Context;
import android.content.res.Configuration;
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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class VoiceChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<ChatMessage> messages = new ArrayList<>();
    private final Context context;
    private final AccessibilityManager accessibilityManager;
    private final Markwon markwon;

    private boolean isLoading = false;
    private static final int VIEW_TYPE_MESSAGE = 0;
    private static final int VIEW_TYPE_LOADING = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final String TAG = "VoiceChatAdapter";

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
            MessageViewHolder messageHolder = (MessageViewHolder) holder;
            ChatMessage message = messages.get(position);

            messageHolder.messageText.setText(message.getText());

            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) messageHolder.messageCard.getLayoutParams();

            try {

                boolean isNightMode = (context.getResources().getConfiguration().uiMode 
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

                Log.d(TAG, "onBindViewHolder: isNightMode = " + isNightMode + " for position " + position);

                if (message.isUser()) {
                    layoutParams.gravity = Gravity.END;

                    if (isNightMode) {

                        messageHolder.messageCard.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.message_user_background));
                        messageHolder.messageText.setTextColor(
                            ContextCompat.getColor(context, R.color.message_user_text));
                        Log.d(TAG, "Applied dark theme colors for user message");
                    } else {

                        messageHolder.messageCard.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.message_user_background_light));
                        messageHolder.messageText.setTextColor(
                            ContextCompat.getColor(context, R.color.message_user_text_light));
                        Log.d(TAG, "Applied light theme colors for user message");
                    }
                } else {
                    layoutParams.gravity = Gravity.START;

                    if (isNightMode) {

                        messageHolder.messageCard.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.message_ai_background));
                        messageHolder.messageText.setTextColor(
                            ContextCompat.getColor(context, R.color.message_ai_text));
                        Log.d(TAG, "Applied dark theme colors for assistant message");
                    } else {

                        messageHolder.messageCard.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.message_ai_background_light));
                        messageHolder.messageText.setTextColor(
                            ContextCompat.getColor(context, R.color.message_ai_text_light));
                        Log.d(TAG, "Applied light theme colors for assistant message");
                    }
                }
            } catch (Exception e) {

                Log.e(TAG, "Error setting message colors: " + e.getMessage());

                boolean isNightMode = (context.getResources().getConfiguration().uiMode 
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

                if (isNightMode) {

                    if (message.isUser()) {
                        messageHolder.messageCard.setCardBackgroundColor(0xFF1976D2); 
                        messageHolder.messageText.setTextColor(0xFFFFFFFF); 
                    } else {
                        messageHolder.messageCard.setCardBackgroundColor(0xFF558B2F); 
                        messageHolder.messageText.setTextColor(0xFFFFFFFF); 
                    }
                } else {

                    if (message.isUser()) {
                        messageHolder.messageCard.setCardBackgroundColor(0xFFBBDEFB); 
                        messageHolder.messageText.setTextColor(0xFF212121); 
                    } else {
                        messageHolder.messageCard.setCardBackgroundColor(0xFFC4F2F5); 
                        messageHolder.messageText.setTextColor(0xFF212121); 
                    }
                }
            }

            messageHolder.messageCard.setLayoutParams(layoutParams);

            messageHolder.typingCursor.setVisibility(View.GONE);

        } else if (holder instanceof LoadingViewHolder) {

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

    public void removeMessage(ChatMessage message) {
        int position = messages.indexOf(message);
        if (position != -1) {
            messages.remove(position);
            notifyItemRemoved(position);
        }
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

    public void refreshColors() {

        notifyDataSetChanged();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final CardView messageCard;
        final TextView messageText;
        final View typingCursor;

        MessageViewHolder(View itemView) {
            super(itemView);
            messageCard = itemView.findViewById(R.id.messageCard);
            messageText = itemView.findViewById(R.id.messageText);
            typingCursor = itemView.findViewById(R.id.typingCursor);
        }
    }

    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        LoadingViewHolder(View itemView) {
            super(itemView);
        }
    }
}