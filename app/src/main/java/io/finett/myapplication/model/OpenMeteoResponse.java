package io.finett.myapplication.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class OpenMeteoResponse {
    @SerializedName("latitude")
    private double latitude;
    
    @SerializedName("longitude")
    private double longitude;
    
    @SerializedName("timezone")
    private String timezone;
    
    @SerializedName("current")
    private CurrentWeather current;
    
    @SerializedName("hourly")
    private HourlyWeather hourly;
    
    public double getLatitude() {
        return latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public String getTimezone() {
        return timezone;
    }
    
    public CurrentWeather getCurrent() {
        return current;
    }
    
    public HourlyWeather getHourly() {
        return hourly;
    }
    
    public static class CurrentWeather {
        @SerializedName("time")
        private String time;
        
        @SerializedName("temperature_2m")
        private double temperature;
        
        @SerializedName("relative_humidity_2m")
        private Double humidity; // Optional
        
        @SerializedName("wind_speed_10m")
        private double windSpeed;
        
        public String getTime() {
            return time;
        }
        
        public double getTemperature() {
            return temperature;
        }
        
        public Double getHumidity() {
            return humidity;
        }
        
        public double getWindSpeed() {
            return windSpeed;
        }
    }
    
    public static class HourlyWeather {
        @SerializedName("time")
        private List<String> time;
        
        @SerializedName("temperature_2m")
        private List<Double> temperature;
        
        @SerializedName("relative_humidity_2m")
        private List<Integer> humidity;
        
        @SerializedName("wind_speed_10m")
        private List<Double> windSpeed;
        
        public List<String> getTime() {
            return time;
        }
        
        public List<Double> getTemperature() {
            return temperature;
        }
        
        public List<Integer> getHumidity() {
            return humidity;
        }
        
        public List<Double> getWindSpeed() {
            return windSpeed;
        }
    }
} 