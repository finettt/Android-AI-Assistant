package io.finett.myapplication.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import io.finett.myapplication.model.SystemPrompt;

public class PromptManager {
    private static final String PREFS_NAME = "PromptsPrefs";
    private static final String PROMPTS_KEY = "prompts";
    private static final String ACTIVE_PROMPT_KEY = "active_prompt";
    
    private final SharedPreferences preferences;
    private final Gson gson;
    
    public PromptManager(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }
    
    public void savePrompt(SystemPrompt prompt) {
        List<SystemPrompt> prompts = getPrompts();
        prompts.add(prompt);
        savePrompts(prompts);
    }
    
    public List<SystemPrompt> getPrompts() {
        String json = preferences.getString(PROMPTS_KEY, "[]");
        Type type = new TypeToken<List<SystemPrompt>>(){}.getType();
        return gson.fromJson(json, type);
    }
    
    private void savePrompts(List<SystemPrompt> prompts) {
        String json = gson.toJson(prompts);
        preferences.edit().putString(PROMPTS_KEY, json).apply();
    }
    
    public void setActivePrompt(SystemPrompt prompt) {
        List<SystemPrompt> prompts = getPrompts();
        for (SystemPrompt p : prompts) {
            p.setActive(false);
        }
        prompt.setActive(true);
        savePrompts(prompts);
        
        String promptJson = gson.toJson(prompt);
        preferences.edit().putString(ACTIVE_PROMPT_KEY, promptJson).apply();
    }
    
    public SystemPrompt getActivePrompt() {
        String json = preferences.getString(ACTIVE_PROMPT_KEY, null);
        if (json == null) {
            return getDefaultPrompt();
        }
        return gson.fromJson(json, SystemPrompt.class);
    }
    
    public void deletePrompt(SystemPrompt prompt) {
        List<SystemPrompt> prompts = getPrompts();
        prompts.remove(prompt);
        savePrompts(prompts);
    }
    
    private SystemPrompt getDefaultPrompt() {
        return new SystemPrompt(
            "Вы - полезный голосовой ассистент. Отвечайте кратко и по делу.",
            "Стандартный промпт"
        );
    }
} 