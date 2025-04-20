package io.finett.myapplication.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import io.finett.myapplication.model.Chat;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StorageUtil {
    private static final String PREFS_NAME = "ChatAppPrefs";
    private static final String CHATS_KEY = "saved_chats";
    private static final Gson gson = createGson();

    private static Gson createGson() {
        return new GsonBuilder()
            .registerTypeAdapter(Long.class, new LongTypeAdapter())
            .create();
    }

    private static class LongTypeAdapter implements JsonSerializer<Long>, JsonDeserializer<Long> {
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        @Override
        public JsonElement serialize(Long src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src);
        }

        @Override
        public Long deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            try {
                if (json.isJsonPrimitive()) {
                    JsonPrimitive primitive = json.getAsJsonPrimitive();
                    if (primitive.isNumber()) {
                        return primitive.getAsLong();
                    } else if (primitive.isString()) {
                        String value = primitive.getAsString();
                        try {
                            // Try to parse as a time string first
                            Date date = dateFormat.parse(value);
                            return date.getTime();
                        } catch (ParseException e) {
                            // If not a time string, try to parse as a number
                            return Long.parseLong(value);
                        }
                    }
                }
                throw new JsonParseException("Expected number or time string but was " + json);
            } catch (NumberFormatException e) {
                throw new JsonParseException("Invalid number format: " + json, e);
            }
        }
    }

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
            try {
                Type type = new TypeToken<ArrayList<Chat>>(){}.getType();
                return gson.fromJson(json, type);
            } catch (Exception e) {
                // If there's an error loading chats, return an empty list
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }
} 