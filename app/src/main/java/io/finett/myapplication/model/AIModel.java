package io.finett.myapplication.model;

public class AIModel {
    private String name;
    private String id;
    private String description;
    private boolean isRecommended;

    public static final AIModel[] AVAILABLE_MODELS = {
        new AIModel("Claude 3 Haiku", "anthropic/claude-3-haiku-20240307", false),
        new AIModel("Claude 3 Sonnet", "anthropic/claude-3-sonnet-20240229", false),
        new AIModel("Gemini Pro", "google/gemini-pro", true),
        new AIModel("Qwen 2 7B", "qwen/qwen2-7b", false),
        new AIModel("Qwen 3 235B", "qwen/qwen3-235b-a22b:free", true),
        new AIModel("Mistral 7B", "mistralai/mistral-7b-instruct-v0.1", false),
        new AIModel("Mixtral 8x7B", "mistralai/mixtral-8x7b-instruct-v0.1", false),
        new AIModel("LLaMA 2 13B", "meta-llama/llama-2-13b-chat", false),
        new AIModel("LLaMA 2 70B", "meta-llama/llama-2-70b-chat", false)
    };

    public AIModel(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isRecommended = false;
    }
    
    public AIModel(String name, String id, boolean isRecommended) {
        this.name = name;
        this.id = id;
        this.description = "";
        this.isRecommended = isRecommended;
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
    
    public boolean isRecommended() {
        return isRecommended;
    }

    @Override
    public String toString() {
        return name + (isRecommended ? " (рекомендуется)" : "");
    }
} 