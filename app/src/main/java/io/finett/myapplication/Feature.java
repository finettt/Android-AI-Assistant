package io.finett.myapplication;

public class Feature {
    private String title;
    private String description;
    private int iconResourceId;
    
    public Feature(String title, String description, int iconResourceId) {
        this.title = title;
        this.description = description;
        this.iconResourceId = iconResourceId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getDescription() {
        return description;
    }
    
    public int getIconResourceId() {
        return iconResourceId;
    }
} 