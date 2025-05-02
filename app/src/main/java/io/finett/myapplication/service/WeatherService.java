package io.finett.myapplication.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import io.finett.myapplication.BuildConfig;
import io.finett.myapplication.api.ApiClient;
import io.finett.myapplication.api.GeocodingApi;
import io.finett.myapplication.api.WeatherApi;
import io.finett.myapplication.model.GeocodingResponse;
import io.finett.myapplication.model.OpenMeteoResponse;
import io.finett.myapplication.model.WeatherData;
import io.finett.myapplication.model.WeatherbitResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;

public class WeatherService {
    private static final String TAG = "WeatherService";
    private static final String DEFAULT_CITY = "Moscow"; // Город по умолчанию
    private static final String DEFAULT_UNITS = "metric"; // Метрическая система по умолчанию
    private static final String WEATHER_API_PROVIDER = "weather_api_provider"; // Ключ для хранения выбранного API
    private static final String API_PROVIDER_OPENWEATHERMAP = "openweathermap";
    private static final String API_PROVIDER_WEATHERBIT = "weatherbit";
    private static final String API_PROVIDER_OPENMETEO = "openmeteo";
    
    // Координаты Москвы для запасного варианта
    private static final double DEFAULT_LATITUDE = 55.7558;
    private static final double DEFAULT_LONGITUDE = 37.6173;
    private static final String DEFAULT_TIMEZONE = "Europe/Moscow";
    
    private final WeatherApi weatherApi;
    private final WeatherApi weatherbitApi;
    private final WeatherApi openMeteoApi;
    private final GeocodingApi geocodingApi;
    private final Context context;
    
    public interface WeatherCallback {
        void onWeatherDataReceived(WeatherData weatherData);
        void onWeatherError(String errorMessage);
    }
    
    public WeatherService(Context context) {
        this.context = context;
        this.weatherApi = ApiClient.getWeatherApi();
        this.weatherbitApi = ApiClient.getWeatherbitApi();
        this.openMeteoApi = ApiClient.getOpenMeteoApi();
        this.geocodingApi = ApiClient.getGeocodingApi();
    }
    
    private String getApiKey() {
        // Проверяем флаг, использовать ли хардкод ключ из BuildConfig
        if (BuildConfig.USE_HARDCODED_KEY) {
            Log.d(TAG, "Используется встроенный API ключ из BuildConfig");
            return BuildConfig.DEFAULT_WEATHER_API_KEY;
        }
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String apiKey = prefs.getString("weather_api_key", "");
        
        // Если API ключ не установлен, используем ключ по умолчанию из BuildConfig
        if (apiKey == null || apiKey.isEmpty() || "YOUR_OPENWEATHERMAP_API_KEY".equals(apiKey)) {
            Log.w(TAG, "Пользовательский ключ не установлен, используется встроенный API ключ из BuildConfig");
            return BuildConfig.DEFAULT_WEATHER_API_KEY;
        }
        
        return apiKey;
    }
    
    public void getCurrentWeather(WeatherCallback callback) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String city = prefs.getString("weather_city", DEFAULT_CITY);
        
        // Проверяем, указан ли город
        if (city == null || city.isEmpty()) {
            city = DEFAULT_CITY;
            Log.w(TAG, "Город не указан, используется город по умолчанию: " + DEFAULT_CITY);
        }
        
        String apiProvider = prefs.getString(WEATHER_API_PROVIDER, API_PROVIDER_OPENMETEO);
        Log.d(TAG, "Используем поставщика погоды: " + apiProvider);
        
        if (API_PROVIDER_OPENMETEO.equals(apiProvider)) {
            getOpenMeteoWeather(city, callback);
        } else if (API_PROVIDER_WEATHERBIT.equals(apiProvider)) {
            getWeatherbitWeather(city, callback);
        } else {
            // По умолчанию используем OpenWeatherMap
            getOpenWeatherMapWeather(city, callback);
        }
    }
    
    private void getOpenWeatherMapWeather(String city, WeatherCallback callback) {
        String units = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("weather_units", DEFAULT_UNITS);
        String apiKey = getApiKey();
        
        Log.d(TAG, "Запрос погоды OpenWeatherMap для города: " + city);
        
        weatherApi.getCurrentWeather(city, units, apiKey).enqueue(new Callback<WeatherData>() {
            @Override
            public void onResponse(Call<WeatherData> call, Response<WeatherData> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onWeatherDataReceived(response.body());
                } else {
                    String errorMessage;
                    if (response.code() == 404) {
                        errorMessage = "Город '" + city + "' не найден. Проверьте написание.";
                    } else {
                        errorMessage = "Ошибка получения данных: " + response.code();
                    }
                    Log.e(TAG, errorMessage);
                    callback.onWeatherError(errorMessage);
                }
            }
            
            @Override
            public void onFailure(Call<WeatherData> call, Throwable t) {
                String errorMessage = "Ошибка сети: " + t.getMessage();
                Log.e(TAG, errorMessage, t);
                callback.onWeatherError(errorMessage);
            }
        });
    }
    
    private void getWeatherbitWeather(String city, WeatherCallback callback) {
        String units = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("weather_units", DEFAULT_UNITS);
        String apiKey = getApiKey(); // Используем тот же ключ API для простоты (можно добавить отдельный позже)
        
        Log.d(TAG, "Запрос погоды Weatherbit для города: " + city);
        
        weatherbitApi.getWeatherbitCurrent(city, units, apiKey).enqueue(new Callback<WeatherbitResponse>() {
            @Override
            public void onResponse(Call<WeatherbitResponse> call, Response<WeatherbitResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null 
                        && !response.body().getData().isEmpty()) {
                    
                    // Конвертируем данные Weatherbit в формат WeatherData
                    WeatherbitResponse.WeatherbitData data = response.body().getData().get(0);
                    WeatherData weatherData = convertWeatherbitToWeatherData(data, city);
                    callback.onWeatherDataReceived(weatherData);
                } else {
                    String errorMessage;
                    if (response.code() == 404) {
                        errorMessage = "Город '" + city + "' не найден. Проверьте написание.";
                    } else {
                        errorMessage = "Ошибка получения данных: " + response.code();
                    }
                    Log.e(TAG, errorMessage);
                    callback.onWeatherError(errorMessage);
                }
            }
            
            @Override
            public void onFailure(Call<WeatherbitResponse> call, Throwable t) {
                String errorMessage = "Ошибка сети: " + t.getMessage();
                Log.e(TAG, errorMessage, t);
                callback.onWeatherError(errorMessage);
            }
        });
    }
    
    private WeatherData convertWeatherbitToWeatherData(WeatherbitResponse.WeatherbitData data, String city) {
        WeatherData weatherData = new WeatherData();
        weatherData.setName(data.getCityName() != null ? data.getCityName() : city);
        
        // Создаем объекты внутренних классов
        WeatherData.MainData mainData = new WeatherData.MainData();
        mainData.setTemp(data.getTemp());
        mainData.setFeelsLike(data.getFeelsLike());
        mainData.setHumidity(data.getHumidity());
        weatherData.setMain(mainData);
        
        WeatherData.WindData windData = new WeatherData.WindData();
        windData.setSpeed(data.getWindSpeed());
        weatherData.setWind(windData);
        
        // Добавляем информацию о погоде
        if (data.getWeather() != null) {
            List<WeatherData.Weather> weatherList = new ArrayList<>();
            WeatherData.Weather weather = new WeatherData.Weather();
            weather.setDescription(data.getWeather().getDescription());
            weatherList.add(weather);
            weatherData.setWeather(weatherList);
        }
        
        return weatherData;
    }
    
    private void getOpenMeteoWeather(String city, WeatherCallback callback) {
        Log.d(TAG, "Запрос погоды Open-Meteo для города: " + city);
        
        // Используем более надежные параметры для геокодинга
        // Увеличиваем count до 5, чтобы получить больше вариантов
        // Добавляем параметр для прямого вывода в JSON
        geocodingApi.searchLocation(
            city, 
            5, // Возвращаем до 5 результатов
            "ru", // Русский язык
            "json" // Формат JSON
        ).enqueue(new Callback<GeocodingResponse>() {
            @Override
            public void onResponse(Call<GeocodingResponse> call, Response<GeocodingResponse> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        Log.d(TAG, "Получен ответ от геокодинг сервиса: " + response.body().getGenerationTimeMs() + "мс");
                        
                        if (response.body().getResults() != null && !response.body().getResults().isEmpty()) {
                            GeocodingResponse.GeocodingResult location = response.body().getResults().get(0);
                            Log.d(TAG, "Найден город: " + location.getName() + 
                                ", страна: " + location.getCountry() + 
                                ", координаты: " + location.getLatitude() + ", " + location.getLongitude());
                            
                            getOpenMeteoWeatherByCoordinates(
                                location.getLatitude(),
                                location.getLongitude(),
                                location.getName(),
                                location.getTimezone() != null ? location.getTimezone() : DEFAULT_TIMEZONE,
                                callback
                            );
                        } else {
                            // Нет результатов поиска
                            Log.e(TAG, "Геокодинг вернул пустой список результатов для города: " + city);
                            useFallbackWeather(city, callback, "Геокодинг не нашел город");
                        }
                    } else {
                        // Ошибка запроса
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Нет тела ответа";
                        Log.e(TAG, "Ошибка геокодинга: код " + response.code() + ", ответ: " + errorBody);
                        useFallbackWeather(city, callback, "Ошибка геокодинга: " + response.code());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Исключение при обработке ответа геокодинга", e);
                    useFallbackWeather(city, callback, "Ошибка обработки ответа геокодинга: " + e.getMessage());
                }
            }
            
            @Override
            public void onFailure(Call<GeocodingResponse> call, Throwable t) {
                Log.e(TAG, "Ошибка сети при геокодинге: " + t.getMessage(), t);
                useFallbackWeather(city, callback, "Ошибка сети");
            }
        });
    }
    
    /**
     * Используем запасной вариант погоды для Москвы, если геокодинг не сработал
     */
    private void useFallbackWeather(String originalCity, WeatherCallback callback, String reason) {
        Log.w(TAG, "Используем запасной вариант погоды (Москва) из-за: " + reason);
        
        // Пробуем OpenWeatherMap как запасной вариант
        String units = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("weather_units", DEFAULT_UNITS);
        String apiKey = getApiKey();
        
        // Сначала пробуем запрос с оригинальным названием города
        weatherApi.getCurrentWeather(originalCity, units, apiKey).enqueue(new Callback<WeatherData>() {
            @Override
            public void onResponse(Call<WeatherData> call, Response<WeatherData> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "Запасной OpenWeatherMap успешно получил погоду для: " + originalCity);
                    callback.onWeatherDataReceived(response.body());
                } else {
                    // Если не удалось с оригинальным городом, пробуем Москву
                    Log.d(TAG, "Пробуем получить погоду для Москвы через OpenWeatherMap");
                    weatherApi.getCurrentWeather(DEFAULT_CITY, units, apiKey).enqueue(new Callback<WeatherData>() {
                        @Override
                        public void onResponse(Call<WeatherData> call, Response<WeatherData> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                Log.d(TAG, "Получена погода для Москвы через OpenWeatherMap");
                                callback.onWeatherDataReceived(response.body());
                            } else {
                                // Если и это не удалось, пробуем Open-Meteo с координатами Москвы
                                Log.d(TAG, "Пробуем получить погоду через Open-Meteo для Москвы");
                                getOpenMeteoWeatherByCoordinates(
                                    DEFAULT_LATITUDE, 
                                    DEFAULT_LONGITUDE,
                                    "Москва (по умолчанию)",
                                    DEFAULT_TIMEZONE,
                                    callback
                                );
                            }
                        }
                        
                        @Override
                        public void onFailure(Call<WeatherData> call, Throwable t) {
                            Log.e(TAG, "Ошибка запасного OpenWeatherMap для Москвы", t);
                            getOpenMeteoWeatherByCoordinates(
                                DEFAULT_LATITUDE, 
                                DEFAULT_LONGITUDE,
                                "Москва (по умолчанию)",
                                DEFAULT_TIMEZONE,
                                callback
                            );
                        }
                    });
                }
            }
            
            @Override
            public void onFailure(Call<WeatherData> call, Throwable t) {
                Log.e(TAG, "Ошибка запасного OpenWeatherMap", t);
                // Пробуем Open-Meteo в последнюю очередь
                getOpenMeteoWeatherByCoordinates(
                    DEFAULT_LATITUDE, 
                    DEFAULT_LONGITUDE,
                    "Москва (по умолчанию)",
                    DEFAULT_TIMEZONE,
                    callback
                );
            }
        });
    }
    
    private void getOpenMeteoWeatherByCoordinates(GeocodingResponse.GeocodingResult location, WeatherCallback callback) {
        getOpenMeteoWeatherByCoordinates(
            location.getLatitude(),
            location.getLongitude(),
            location.getName(),
            location.getTimezone(),
            callback
        );
    }
    
    private void getOpenMeteoWeatherByCoordinates(
            double latitude, 
            double longitude, 
            String cityName,
            String timezone,
            WeatherCallback callback) {
        
        Log.d(TAG, "Запрос погоды Open-Meteo для координат: " + latitude + ", " + longitude);
        
        String currentParams = "temperature_2m,relative_humidity_2m,wind_speed_10m";
        String hourlyParams = "temperature_2m,relative_humidity_2m,wind_speed_10m";
        
        openMeteoApi.getOpenMeteoWeather(latitude, longitude, currentParams, hourlyParams, timezone)
                .enqueue(new Callback<OpenMeteoResponse>() {
            @Override
            public void onResponse(Call<OpenMeteoResponse> call, Response<OpenMeteoResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCurrent() != null) {
                    // Конвертируем данные Open-Meteo в формат WeatherData
                    WeatherData weatherData = convertOpenMeteoToWeatherData(response.body(), cityName);
                    callback.onWeatherDataReceived(weatherData);
                } else {
                    String errorMessage = "Ошибка получения данных о погоде для " + cityName;
                    Log.e(TAG, errorMessage);
                    callback.onWeatherError(errorMessage);
                }
            }
            
            @Override
            public void onFailure(Call<OpenMeteoResponse> call, Throwable t) {
                String errorMessage = "Ошибка сети: " + t.getMessage();
                Log.e(TAG, errorMessage, t);
                callback.onWeatherError(errorMessage);
            }
        });
    }
    
    private WeatherData convertOpenMeteoToWeatherData(OpenMeteoResponse data, String cityName) {
        WeatherData weatherData = new WeatherData();
        weatherData.setName(cityName);
        
        // Создаем объекты внутренних классов
        WeatherData.MainData mainData = new WeatherData.MainData();
        mainData.setTemp(data.getCurrent().getTemperature());
        mainData.setFeelsLike(data.getCurrent().getTemperature()); // Open-Meteo не дает ощущаемую температуру в текущих данных
        
        // Влажность может быть не указана
        Double humidity = data.getCurrent().getHumidity();
        mainData.setHumidity(humidity != null ? humidity.intValue() : 0);
        
        weatherData.setMain(mainData);
        
        WeatherData.WindData windData = new WeatherData.WindData();
        windData.setSpeed(data.getCurrent().getWindSpeed());
        weatherData.setWind(windData);
        
        // Добавляем общее описание погоды
        List<WeatherData.Weather> weatherList = new ArrayList<>();
        WeatherData.Weather weather = new WeatherData.Weather();
        
        // Определяем описание погоды на основе температуры
        String description = getWeatherDescription(data.getCurrent().getTemperature());
        weather.setDescription(description);
        
        weatherList.add(weather);
        weatherData.setWeather(weatherList);
        
        return weatherData;
    }
    
    private String getWeatherDescription(double temperature) {
        if (temperature < 0) {
            return "Морозно";
        } else if (temperature < 10) {
            return "Холодно";
        } else if (temperature < 20) {
            return "Прохладно";
        } else if (temperature < 25) {
            return "Комфортно";
        } else if (temperature < 30) {
            return "Тепло";
        } else {
            return "Жарко";
        }
    }
    
    public void getCurrentWeather(String city, WeatherCallback callback) {
        // Проверяем, указан ли город
        if (city == null || city.isEmpty()) {
            city = DEFAULT_CITY;
            Log.w(TAG, "Указанный город пустой, используется город по умолчанию: " + DEFAULT_CITY);
        }
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String apiProvider = prefs.getString(WEATHER_API_PROVIDER, API_PROVIDER_OPENMETEO);
        
        if (API_PROVIDER_OPENMETEO.equals(apiProvider)) {
            getOpenMeteoWeather(city, callback);
        } else if (API_PROVIDER_WEATHERBIT.equals(apiProvider)) {
            getWeatherbitWeather(city, callback);
        } else {
            getOpenWeatherMapWeather(city, callback);
        }
    }
    
    public String formatWeatherResponse(WeatherData weatherData) {
        if (weatherData == null || weatherData.getMain() == null || weatherData.getWeather() == null 
                || weatherData.getWeather().isEmpty()) {
            return "Данные о погоде недоступны";
        }
        
        String city = weatherData.getName();
        double temp = weatherData.getMain().getTemp();
        double feelsLike = weatherData.getMain().getFeelsLike();
        String description = weatherData.getWeather().get(0).getDescription();
        int humidity = weatherData.getMain().getHumidity();
        double windSpeed = weatherData.getWind() != null ? weatherData.getWind().getSpeed() : 0;
        
        StringBuilder result = new StringBuilder();
        result.append("Текущая погода в ").append(city).append(":\n");
        result.append("Температура: ").append(String.format("%.1f", temp)).append("°C\n");
        result.append("Ощущается как: ").append(String.format("%.1f", feelsLike)).append("°C\n");
        result.append("Описание: ").append(description).append("\n");
        result.append("Влажность: ").append(humidity).append("%\n");
        result.append("Скорость ветра: ").append(String.format("%.1f", windSpeed)).append(" м/с");
        
        return result.toString();
    }
} 