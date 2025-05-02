package io.finett.myapplication.api;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String OPENROUTER_BASE_URL = "https://openrouter.ai/";
    private static final String WEATHER_BASE_URL = "https://api.openweathermap.org/data/2.5/";
    private static final String WEATHERBIT_BASE_URL = "https://api.weatherbit.io/v2.0/";
    private static final String OPEN_METEO_BASE_URL = "https://api.open-meteo.com/";
    private static final String GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/";
    
    private static Retrofit openRouterRetrofit = null;
    private static Retrofit weatherRetrofit = null;
    private static Retrofit weatherbitRetrofit = null;
    private static Retrofit openMeteoRetrofit = null;
    private static Retrofit geocodingRetrofit = null;

    public static Retrofit getOpenRouterClient() {
        if (openRouterRetrofit == null) {
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(interceptor)
                    .build();

            openRouterRetrofit = new Retrofit.Builder()
                    .baseUrl(OPENROUTER_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return openRouterRetrofit;
    }
    
    public static Retrofit getWeatherClient() {
        if (weatherRetrofit == null) {
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(interceptor)
                    .build();

            weatherRetrofit = new Retrofit.Builder()
                    .baseUrl(WEATHER_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return weatherRetrofit;
    }
    
    public static Retrofit getWeatherbitClient() {
        if (weatherbitRetrofit == null) {
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(interceptor)
                    .build();

            weatherbitRetrofit = new Retrofit.Builder()
                    .baseUrl(WEATHERBIT_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return weatherbitRetrofit;
    }
    
    public static Retrofit getOpenMeteoClient() {
        if (openMeteoRetrofit == null) {
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(interceptor)
                    .build();

            openMeteoRetrofit = new Retrofit.Builder()
                    .baseUrl(OPEN_METEO_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return openMeteoRetrofit;
    }
    
    public static Retrofit getGeocodingClient() {
        if (geocodingRetrofit == null) {
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(interceptor)
                    .build();

            geocodingRetrofit = new Retrofit.Builder()
                    .baseUrl(GEOCODING_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return geocodingRetrofit;
    }
    
    public static OpenRouterApi getOpenRouterApi() {
        return getOpenRouterClient().create(OpenRouterApi.class);
    }
    
    public static WeatherApi getWeatherApi() {
        return getWeatherClient().create(WeatherApi.class);
    }
    
    public static WeatherApi getWeatherbitApi() {
        return getWeatherbitClient().create(WeatherApi.class);
    }
    
    public static WeatherApi getOpenMeteoApi() {
        return getOpenMeteoClient().create(WeatherApi.class);
    }
    
    public static GeocodingApi getGeocodingApi() {
        return getGeocodingClient().create(GeocodingApi.class);
    }
} 