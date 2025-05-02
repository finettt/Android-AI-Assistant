package io.finett.myapplication.api;

import io.finett.myapplication.model.WeatherData;
import io.finett.myapplication.model.WeatherbitResponse;
import io.finett.myapplication.model.OpenMeteoResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherApi {
    // OpenWeatherMap API
    @GET("weather")
    Call<WeatherData> getCurrentWeather(
            @Query("q") String city,
            @Query("units") String units,
            @Query("appid") String apiKey
    );
    
    // Weatherbit API
    @GET("current")
    Call<WeatherbitResponse> getWeatherbitCurrent(
            @Query("city") String city,
            @Query("units") String units,
            @Query("key") String apiKey
    );
    
    // Open-Meteo API
    @GET("v1/forecast")
    Call<OpenMeteoResponse> getOpenMeteoWeather(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("current") String currentParams,
            @Query("hourly") String hourlyParams,
            @Query("timezone") String timezone
    );
} 