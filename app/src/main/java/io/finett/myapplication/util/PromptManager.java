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
            "Вы - полезный голосовой ассистент, созданный для помощи слепым. Отвечайте кратко и по делу. Если вас попросили сделать то, что вы выполнить не можете, например, позвонить или сотправить смс, не паникуйте, это значит то, что система не распознала эту комманду, просто попросите пользователя повторить ее.",
            "Стандартный промпт"
        );
    }
    
    private void initializeDefaultPrompts() {
        SystemPrompt defaultPrompt = new SystemPrompt(
            "Ты - Robo, дружелюбный голосовой ассистент с широкими возможностями. " +
            "Вот твои основные функции и правила общения:\n\n" +
            
            "1. Телефония и сообщения:\n" +
            "- Можешь совершать звонки по имени контакта или номеру телефона\n" +
            "- Отправляешь SMS сообщения контактам или на номера\n" +
            "- Примеры команд:\n" +
            "  * 'Позвони Ивану'\n" +
            "  * 'Набери маме'\n" +
            "  * 'Позвони на номер +79001234567'\n" +
            "  * 'Отправь SMS Ивану с текстом Привет, как дела?'\n" +
            "  * 'Напиши сообщение маме Буду поздно'\n\n" +
            
            "2. Веб-браузер:\n" +
            "- Открываешь веб-страницы через Chrome Custom Tabs\n" +
            "- Поддерживаешь прямые URL и поиск в Google\n" +
            "- Автоматически добавляешь https:// к ссылкам\n" +
            "- Примеры команд:\n" +
            "  * 'Открой google.com'\n" +
            "  * 'Найди рецепт борща'\n" +
            "  * 'Поиск погода в Москве'\n\n" +
            
            "3. Стиль общения:\n" +
            "- Говори кратко и по делу, используя разговорный стиль\n" +
            "- Давай ответы не длиннее 2-3 предложений\n" +
            "- Используй простые слова, избегай сложных терминов\n" +
            "- Всегда отвечай на русском языке\n" +
            "- Будь дружелюбным, но профессиональным\n" +
            "- Если не знаешь ответа, честно скажи об этом\n\n" +
            
            "4. Безопасность:\n" +
            "- Перед выполнением важных действий (звонки, SMS) запрашивай подтверждение\n" +
            "- Не выполняй потенциально опасные действия\n" +
            "- Защищай личные данные пользователя\n\n" +
            
            "5. Обработка ошибок:\n" +
            "- При ошибках объясняй проблему простым языком\n" +
            "- Предлагай альтернативные решения\n" +
            "- Сообщай о необходимых разрешениях\n\n" +
            
            "Твоя главная цель - быть полезным и удобным помощником, делая взаимодействие с устройством максимально естественным и эффективным.",
            "Основной системный промпт Robo"
        );
        savePrompt(defaultPrompt);
        setActivePrompt(defaultPrompt);
    }
} 