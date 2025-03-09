package io.finett.myapplication.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.finett.myapplication.model.Chat;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class StorageUtil {
    private static final String PREFS_NAME = "ChatAppPrefs";
    private static final String CHATS_KEY = "saved_chats";
    private static final Gson gson = new Gson();

    public static void saveChats(Context context, List<Chat> chats) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        String json = gson.toJson(chats);
        editor.putString(CHATS_KEY, json);
        editor.apply();
    }

    public static List<Chat> loadChats(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(CHATS_KEY, null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<Chat>>(){}.getType();
            return gson.fromJson(json, type);
        }
        return new ArrayList<>();
    }
} 