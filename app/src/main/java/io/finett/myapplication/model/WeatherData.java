package io.finett.myapplication.model;

import java.util.List;
import java.util.ArrayList;

public class WeatherData {
    private Coordinates coord;
    private List<Weather> weather;
    private String base;
    private Main main;
    private int visibility;
    private Wind wind;
    private Clouds clouds;
    private long dt;
    private Sys sys;
    private int timezone;
    private long id;
    private String name;
    private int cod;

    public WeatherData() {
        // Default constructor for creating instances manually
        this.weather = new ArrayList<>();
    }

    public static class Coordinates {
        private double lon;
        private double lat;

        public double getLon() {
            return lon;
        }

        public double getLat() {
            return lat;
        }
    }

    public static class Weather {
        private long id;
        private String main;
        private String description;
        private String icon;

        public Weather() {
            // Default constructor
        }

        public long getId() {
            return id;
        }

        public String getMain() {
            return main;
        }

        public String getDescription() {
            return description;
        }

        public String getIcon() {
            return icon;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public void setMain(String main) {
            this.main = main;
        }
        
        public void setIcon(String icon) {
            this.icon = icon;
        }
        
        public void setId(long id) {
            this.id = id;
        }
    }

    // Alias for Main to match the code in WeatherService
    public static class MainData extends Main {
    }

    public static class Main {
        private double temp;
        private double feels_like;
        private double temp_min;
        private double temp_max;
        private int pressure;
        private int humidity;

        public Main() {
            // Default constructor
        }

        public double getTemp() {
            return temp;
        }

        public double getFeelsLike() {
            return feels_like;
        }

        public double getTempMin() {
            return temp_min;
        }

        public double getTempMax() {
            return temp_max;
        }

        public int getPressure() {
            return pressure;
        }

        public int getHumidity() {
            return humidity;
        }
        
        public void setTemp(double temp) {
            this.temp = temp;
        }
        
        public void setFeelsLike(double feelsLike) {
            this.feels_like = feelsLike;
        }
        
        public void setHumidity(int humidity) {
            this.humidity = humidity;
        }
    }

    // Alias for Wind to match the code in WeatherService
    public static class WindData extends Wind {
    }

    public static class Wind {
        private double speed;
        private int deg;
        private double gust;

        public Wind() {
            // Default constructor
        }

        public double getSpeed() {
            return speed;
        }

        public int getDeg() {
            return deg;
        }

        public double getGust() {
            return gust;
        }
        
        public void setSpeed(double speed) {
            this.speed = speed;
        }
        
        public void setDeg(int deg) {
            this.deg = deg;
        }
        
        public void setGust(double gust) {
            this.gust = gust;
        }
    }

    public static class Clouds {
        private int all;

        public int getAll() {
            return all;
        }
    }

    public static class Sys {
        private int type;
        private long id;
        private String country;
        private long sunrise;
        private long sunset;

        public int getType() {
            return type;
        }

        public long getId() {
            return id;
        }

        public String getCountry() {
            return country;
        }

        public long getSunrise() {
            return sunrise;
        }

        public long getSunset() {
            return sunset;
        }
    }

    public Coordinates getCoord() {
        return coord;
    }

    public List<Weather> getWeather() {
        return weather;
    }

    public String getBase() {
        return base;
    }

    public Main getMain() {
        return main;
    }

    public int getVisibility() {
        return visibility;
    }

    public Wind getWind() {
        return wind;
    }

    public Clouds getClouds() {
        return clouds;
    }

    public long getDt() {
        return dt;
    }

    public Sys getSys() {
        return sys;
    }

    public int getTimezone() {
        return timezone;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCod() {
        return cod;
    }
    
    // Add setter methods
    public void setName(String name) {
        this.name = name;
    }
    
    public void setMain(Main main) {
        this.main = main;
    }
    
    public void setWind(Wind wind) {
        this.wind = wind;
    }
    
    public void setWeather(List<Weather> weather) {
        this.weather = weather;
    }
} 