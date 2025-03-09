package io.finett.myapplication.model;

public class AIModel {
    private String id;
    private String name;
    private String description;

    public static final AIModel[] AVAILABLE_MODELS = {
        new AIModel("mistralai/mistral-7b-instruct", "Mistral 7B", "Быстрая и эффективная модель"),
        new AIModel("anthropic/claude-2", "Claude 2", "Мощная модель с широким контекстом"),
        new AIModel("google/gemma-7b-it", "Gemma 7B", "Новая модель от Google"),
        new AIModel("meta-llama/llama-2-70b-chat", "LLaMA 2 70B", "Большая модель с высокой точностью")
    };

    public AIModel(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name;
    }
} 