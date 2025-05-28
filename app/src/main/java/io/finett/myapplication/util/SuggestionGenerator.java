package io.finett.myapplication.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.finett.myapplication.BuildConfig;
import io.finett.myapplication.MainActivity;
import io.finett.myapplication.api.ApiClient;
import io.finett.myapplication.api.OpenRouterApi;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Класс для генерации подсказок на основе текущего контекста чата
 * с использованием легкой модели OpenRouter API и структурированного вывода JSON
 */
public class SuggestionGenerator {
    private static final String TAG = "SuggestionGenerator";
    
    // Модель должна поддерживать структурированный вывод JSON
    // Используем Llama 3.3 8B, который должен хорошо справляться с такой простой задачей
    private static final String SUGGESTIONS_MODEL = "meta-llama/llama-3.3-8b-instruct:free"; 
    
    private final Context context;
    private final OpenRouterApi openRouterApi;
    private final Executor executor;
    private final SuggestionCallback callback;
    
    public interface SuggestionCallback {
        void onSuggestionsGenerated(List<String> suggestions);
    }
    
    public SuggestionGenerator(Context context, SuggestionCallback callback) {
        this.context = context;
        this.callback = callback;
        this.openRouterApi = ApiClient.getOpenRouterClient().create(OpenRouterApi.class);
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    /**
     * Генерирует подсказки на основе контекста сообщений
     * 
     * @param messageContext Контекст сообщений (последний ответ бота)
     */
    public void generateSuggestions(String messageContext) {
        // Запускаем в отдельном потоке, чтобы не блокировать UI
        executor.execute(() -> {
            // Получаем API ключ
            String apiKey = getApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                Log.e(TAG, "API ключ не установлен");
                return;
            }
            
            // Создаем запрос к API
            Map<String, Object> body = new HashMap<>();
            body.put("model", SUGGESTIONS_MODEL);
            
            // Формируем запрос для генерации подсказок
            ArrayList<Map<String, Object>> messages = new ArrayList<>();
            
            // Системное сообщение с инструкцией для модели
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", 
                "Ты - ассистент для создания подсказок в чате. " +
                "Твоя задача - предугадать, что пользователь может спросить далее на основе последнего сообщения ассистента. " +
                "Подсказки должны быть на русском языке, краткими (до 40 символов) и представлять собой возможные следующие вопросы или запросы пользователя. " +
                "Включи предполагаемые дальнейшие вопросы, уточнения, просьбы или запросы, которые логически следуют из контекста. " +
                "Создай 3-5 вариантов подсказок в зависимости от контекста. " +
                "Твой ответ должен быть в формате JSON с массивом подсказок.");
            messages.add(systemMessage);
            
            // Сообщение пользователя с контекстом для генерации подсказок
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", "Последнее сообщение ассистента в чате: \"" + messageContext + "\"\n\nСоздай список возможных следующих сообщений пользователя в этой беседе в формате JSON с полем 'suggestions', содержащим массив строк.");
            messages.add(userMessage);
            
            body.put("messages", messages);
            body.put("max_tokens", 250); // Увеличиваем лимит токенов для Llama
            body.put("temperature", 0.7); // Средняя креативность
            
            // Добавляем JSON Schema для структурированного ответа
            Map<String, Object> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_schema");
            
            // Создаем JSON Schema для ответа
            Map<String, Object> jsonSchema = new HashMap<>();
            jsonSchema.put("name", "suggestions");
            jsonSchema.put("strict", true);
            
            // Определяем схему для структурированного ответа
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            
            // Определяем свойства объекта
            Map<String, Object> properties = new HashMap<>();
            
            // Свойство suggestions - массив строк
            Map<String, Object> suggestionsProperty = new HashMap<>();
            suggestionsProperty.put("type", "array");
            
            // Элементы массива - строки
            Map<String, Object> itemsProperty = new HashMap<>();
            itemsProperty.put("type", "string");
            itemsProperty.put("description", "Краткий возможный вопрос или запрос пользователя на русском языке");
            
            suggestionsProperty.put("items", itemsProperty);
            suggestionsProperty.put("description", "Список возможных следующих сообщений пользователя");
            
            // Добавляем свойство в объект properties
            properties.put("suggestions", suggestionsProperty);
            
            // Добавляем properties в schema
            schema.put("properties", properties);
            
            // Указываем обязательные свойства
            List<String> required = new ArrayList<>();
            required.add("suggestions");
            schema.put("required", required);
            
            // Запрещаем дополнительные свойства
            schema.put("additionalProperties", false);
            
            // Добавляем schema в jsonSchema
            jsonSchema.put("schema", schema);
            
            // Добавляем jsonSchema в responseFormat
            responseFormat.put("json_schema", jsonSchema);
            
            // Добавляем responseFormat в body запроса
            body.put("response_format", responseFormat);

            // Отправляем запрос к API
            openRouterApi.getChatCompletion(
                    "Bearer " + apiKey,
                    "https://github.com/finett/android-assistant",
                    "Android Assistant",
                    body
            ).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            // Обрабатываем ответ
                            Map<String, Object> responseBody = response.body();
                            ArrayList<Map<String, Object>> choices = (ArrayList<Map<String, Object>>) responseBody.get("choices");
                            
                            if (choices != null && !choices.isEmpty()) {
                                Map<String, Object> choice = choices.get(0);
                                if (choice.containsKey("message")) {
                                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                                    if (message.containsKey("content")) {
                                        String content = message.get("content").toString();
                                        Log.d(TAG, "Получен ответ от Llama: " + content);
                                        
                                        // Парсим JSON со структурированным ответом
                                        List<String> suggestions = parseJsonSuggestions(content);
                                        
                                        // Вызываем колбэк с полученными подсказками
                                        if (callback != null && !suggestions.isEmpty()) {
                                            callback.onSuggestionsGenerated(suggestions);
                                            return;
                                        }
                                    }
                                }
                            }
                            
                            // Если не удалось получить подсказки, используем запасной вариант
                            if (callback != null) {
                                callback.onSuggestionsGenerated(getDefaultSuggestions(messageContext));
                            }
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка при обработке ответа: " + e.getMessage());
                            if (callback != null) {
                                callback.onSuggestionsGenerated(getDefaultSuggestions(messageContext));
                            }
                        }
                    } else {
                        Log.e(TAG, "Ошибка API: " + (response.code()));
                        try {
                            if (response.errorBody() != null) {
                                Log.e(TAG, "Ошибка: " + response.errorBody().string());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка при чтении ошибки: " + e.getMessage());
                        }
                        
                        if (callback != null) {
                            callback.onSuggestionsGenerated(getDefaultSuggestions(messageContext));
                        }
                    }
                }
                
                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    Log.e(TAG, "Ошибка сети: " + t.getMessage());
                    if (callback != null) {
                        callback.onSuggestionsGenerated(getDefaultSuggestions(messageContext));
                    }
                }
            });
        });
    }
    
    /**
     * Парсит подсказки из структурированного JSON ответа
     */
    private List<String> parseJsonSuggestions(String content) {
        List<String> suggestions = new ArrayList<>();
        
        try {
            // Парсим JSON ответ
            JSONObject jsonObject = new JSONObject(content);
            
            // Проверяем наличие массива suggestions
            if (jsonObject.has("suggestions")) {
                JSONArray suggestionsArray = jsonObject.getJSONArray("suggestions");
                
                // Извлекаем каждую подсказку из массива
                for (int i = 0; i < suggestionsArray.length(); i++) {
                    String suggestion = suggestionsArray.getString(i).trim();
                    if (!suggestion.isEmpty() && suggestion.length() <= 50) {
                        suggestions.add(suggestion);
                    }
                }
                
                // Ограничиваем количество подсказок
                if (suggestions.size() > 5) {
                    suggestions = suggestions.subList(0, 5);
                }
                
                return suggestions;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Ошибка при парсинге JSON: " + e.getMessage() + ", содержимое: " + content);
            // Если не удалось распарсить как JSON, пробуем старый метод
            return parseSuggestions(content);
        }
        
        // Если не удалось извлечь подсказки из JSON, пробуем старый метод
        return parseSuggestions(content);
    }
    
    /**
     * Парсит подсказки из обычного текстового ответа (резервный метод)
     */
    private List<String> parseSuggestions(String content) {
        List<String> suggestions = new ArrayList<>();
        
        // Пытаемся найти и извлечь JSON даже если он внутри другого текста
        try {
            int startIdx = content.indexOf("{");
            int endIdx = content.lastIndexOf("}");
            
            if (startIdx >= 0 && endIdx > startIdx) {
                String jsonStr = content.substring(startIdx, endIdx + 1);
                JSONObject jsonObject = new JSONObject(jsonStr);
                
                if (jsonObject.has("suggestions")) {
                    JSONArray suggestionsArray = jsonObject.getJSONArray("suggestions");
                    
                    for (int i = 0; i < suggestionsArray.length(); i++) {
                        String suggestion = suggestionsArray.getString(i).trim();
                        if (!suggestion.isEmpty() && suggestion.length() <= 50) {
                            suggestions.add(suggestion);
                        }
                    }
                    
                    if (!suggestions.isEmpty()) {
                        // Ограничиваем количество подсказок
                        if (suggestions.size() > 5) {
                            suggestions = suggestions.subList(0, 5);
                        }
                        return suggestions;
                    }
                }
            }
        } catch (JSONException e) {
            Log.d(TAG, "Не удалось извлечь JSON из текста: " + e.getMessage());
        }
        
        // Разбиваем ответ по разделителю
        String[] parts = content.split("\\|");
        for (String part : parts) {
            String suggestion = part.trim();
            if (!suggestion.isEmpty() && suggestion.length() <= 50) {
                suggestions.add(suggestion);
            }
        }
        
        // Если не удалось разбить по разделителю, пробуем по строкам
        if (suggestions.isEmpty()) {
            String[] lines = content.split("\\n");
            for (String line : lines) {
                // Удаляем маркеры списка и прочие символы
                String suggestion = line.replaceAll("^\\s*[-*\\d]+\\.?\\s*", "").trim();
                if (!suggestion.isEmpty() && suggestion.length() <= 50) {
                    suggestions.add(suggestion);
                }
            }
        }
        
        // Ограничиваем количество подсказок
        if (suggestions.size() > 5) {
            suggestions = suggestions.subList(0, 5);
        }
        
        return suggestions;
    }
    
    /**
     * Возвращает стандартные подсказки на основе контекста
     */
    private List<String> getDefaultSuggestions(String messageContext) {
        List<String> suggestions = new ArrayList<>();
        
        String lowerContext = messageContext.toLowerCase();
        
        if (lowerContext.contains("игр") || lowerContext.contains("игра")) {
            suggestions.add("Расскажи правила игры");
            suggestions.add("Давай попробуем сыграть");
            suggestions.add("Какие еще игры ты знаешь?");
        }
        
        if (lowerContext.contains("погод") || lowerContext.contains("дожд") || 
            lowerContext.contains("температур") || lowerContext.contains("тепл") || 
            lowerContext.contains("холод")) {
            suggestions.add("А что будет завтра?");
            suggestions.add("Когда закончатся дожди?");
            suggestions.add("Какая погода в Москве?");
        }
        
        if (lowerContext.contains("функц") || lowerContext.contains("возможност") || 
            lowerContext.contains("умеешь") || lowerContext.contains("можешь")) {
            suggestions.add("Покажи пример");
            suggestions.add("Расскажи подробнее");
            suggestions.add("Какие еще есть функции?");
        }
        
        if (lowerContext.contains("новост") || lowerContext.contains("событи")) {
            suggestions.add("Что еще произошло?");
            suggestions.add("Расскажи о технологиях");
            suggestions.add("Какие новости спорта?");
        }
        
        if (lowerContext.contains("фильм") || lowerContext.contains("кино") ||
            lowerContext.contains("сериал") || lowerContext.contains("смотре")) {
            suggestions.add("Где это можно посмотреть?");
            suggestions.add("Кто играет главные роли?");
            suggestions.add("Расскажи сюжет");
        }
        
        if (lowerContext.contains("истор") || lowerContext.contains("факт")) {
            suggestions.add("Расскажи подробнее");
            suggestions.add("Это действительно правда?");
            suggestions.add("Знаешь другие интересные факты?");
        }
        
        if (lowerContext.contains("объясни") || lowerContext.contains("расскажи") || 
            lowerContext.contains("опиши") || lowerContext.contains("что такое")) {
            suggestions.add("Можешь простыми словами?");
            suggestions.add("Приведи примеры");
            suggestions.add("Где это применяется?");
        }
        
        // Общие вопросы/уточнения
        if (suggestions.isEmpty()) {
            suggestions.add("Расскажи подробнее");
            suggestions.add("Приведи пример");
            suggestions.add("Как это использовать?");
            suggestions.add("Что ещё ты знаешь об этом?");
        }
        
        return suggestions;
    }
    
    /**
     * Получение API ключа с учетом настроек сборки
     */
    private String getApiKey() {
        // Проверяем флаг, использовать ли хардкод ключ из BuildConfig
        if (BuildConfig.USE_HARDCODED_KEY) {
            Log.d(TAG, "Используется встроенный API ключ OpenRouter из BuildConfig");
            return BuildConfig.DEFAULT_OPENROUTER_API_KEY;
        }
        
        // Получаем сохраненный пользовательский ключ
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String apiKey = prefs.getString(MainActivity.API_KEY_PREF, null);
        
        // Если API ключ не установлен и используем ключ по умолчанию
        if (apiKey == null || apiKey.isEmpty()) {
            Log.w(TAG, "Пользовательский ключ не установлен, используется встроенный API ключ OpenRouter из BuildConfig");
            return BuildConfig.DEFAULT_OPENROUTER_API_KEY;
        }
        
        return apiKey;
    }
} 