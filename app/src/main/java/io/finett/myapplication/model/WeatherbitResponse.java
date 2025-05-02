package io.finett.myapplication.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class WeatherbitResponse {
    @SerializedName("data")
    private List<WeatherbitData> data;
    
    @SerializedName("count")
    private int count;
    
    public List<WeatherbitData> getData() {
        return data;
    }
    
    public int getCount() {
        return count;
    }
    
    public static class WeatherbitData {
        @SerializedName("city_name")
        private String cityName;
        
        @SerializedName("temp")
        private double temp;
        
        @SerializedName("app_temp")
        private double feelsLike;
        
        @SerializedName("rh")
        private int humidity;
        
        @SerializedName("wind_spd")
        private double windSpeed;
        
        @SerializedName("weather")
        private WeatherbitWeather weather;
        
        public String getCityName() {
            return cityName;
        }
        
        public double getTemp() {
            return temp;
        }
        
        public double getFeelsLike() {
            return feelsLike;
        }
        
        public int getHumidity() {
            return humidity;
        }
        
        public double getWindSpeed() {
            return windSpeed;
        }
        
        public WeatherbitWeather getWeather() {
            return weather;
        }
    }
    
    public static class WeatherbitWeather {
        @SerializedName("description")
        private String description;
        
        @SerializedName("icon")
        private String icon;
        
        @SerializedName("code")
        private int code;
        
        public String getDescription() {
            return description;
        }
        
        public String getIcon() {
            return icon;
        }
        
        public int getCode() {
            return code;
        }
    }
} 