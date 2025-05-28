package io.finett.myapplication.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class ContextInfoProvider {
    private static final String TAG = "ContextInfoProvider";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 126;
    private static final String PREFS_NAME = "UserInfoPrefs";
    private static final String USER_NAME_KEY = "user_name";

    private final Context context;
    private final Activity activity;
    private Location lastKnownLocation;
    private Address lastKnownAddress;
    private LocationListener locationListener;
    private OnContextInfoUpdatedListener listener;

    public interface OnContextInfoUpdatedListener {
        void onLocationUpdated(Location location, Address address);
    }

    public ContextInfoProvider(Activity activity) {
        this.context = activity.getApplicationContext();
        this.activity = activity;
        setupLocationListener();
    }

    public void setOnContextInfoUpdatedListener(OnContextInfoUpdatedListener listener) {
        this.listener = listener;
    }

    /**
     * Get a map containing all available context information
     * @return Map of context information
     */
    public Map<String, String> getAllContextInfo() {
        Map<String, String> contextInfo = new HashMap<>();
        
        // Add user information
        contextInfo.put("user_name", getUserName());
        
        // Add system time and date
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", new Locale("ru"));
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", new Locale("ru"));
        SimpleDateFormat dayOfWeekFormat = new SimpleDateFormat("EEEE", new Locale("ru"));
        Date currentDate = new Date();
        
        contextInfo.put("current_date", dateFormat.format(currentDate));
        contextInfo.put("current_time", timeFormat.format(currentDate));
        contextInfo.put("day_of_week", dayOfWeekFormat.format(currentDate));
        
        // Add timezone info
        TimeZone tz = TimeZone.getDefault();
        contextInfo.put("timezone", tz.getDisplayName(false, TimeZone.SHORT, new Locale("ru")));
        contextInfo.put("timezone_offset", String.format("%+d часов", tz.getRawOffset() / 3600000));
        
        // Add device info
        contextInfo.put("device_model", Build.MODEL);
        contextInfo.put("android_version", Build.VERSION.RELEASE);
        
        // Add location info if available
        updateLocationInfo();
        if (lastKnownLocation != null) {
            contextInfo.put("latitude", String.valueOf(lastKnownLocation.getLatitude()));
            contextInfo.put("longitude", String.valueOf(lastKnownLocation.getLongitude()));
            
            if (lastKnownAddress != null) {
                contextInfo.put("city", lastKnownAddress.getLocality());
                contextInfo.put("country", lastKnownAddress.getCountryName());
                contextInfo.put("address", lastKnownAddress.getAddressLine(0));
            }
        }
        
        return contextInfo;
    }

    /**
     * Get a formatted string with context information for the model
     * @return Formatted context information string
     */
    public String getFormattedContextInfo() {
        Map<String, String> info = getAllContextInfo();
        StringBuilder result = new StringBuilder();
        
        // Time and date
        result.append("Текущее время: ").append(info.get("current_time")).append("\n");
        result.append("Текущая дата: ").append(info.get("current_date")).append("\n");
        result.append("День недели: ").append(info.get("day_of_week")).append("\n");
        
        // User info
        String userName = info.get("user_name");
        if (userName != null && !userName.isEmpty()) {
            result.append("Имя пользователя: ").append(userName).append("\n");
        }
        
        // Location info
        if (info.containsKey("city") && info.get("city") != null) {
            result.append("Город: ").append(info.get("city")).append("\n");
        }
        if (info.containsKey("country") && info.get("country") != null) {
            result.append("Страна: ").append(info.get("country")).append("\n");
        }
        
        return result.toString();
    }

    /**
     * Save user name to preferences
     * @param name User name
     */
    public void saveUserName(String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(USER_NAME_KEY, name).apply();
    }

    /**
     * Get saved user name
     * @return User name or empty string if not set
     */
    public String getUserName() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(USER_NAME_KEY, "");
    }

    /**
     * Request and update location information
     */
    public void updateLocationInfo() {
        if (!checkLocationPermissions()) {
            return;
        }

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        try {
            // Try to get last known location first
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (lastKnownLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            // Request location updates
            if (locationListener != null) {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, 10000, 10, locationListener);
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER, 10000, 10, locationListener);
                }
            }

            // Get address from last known location
            if (lastKnownLocation != null) {
                getAddressFromLocation(lastKnownLocation);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception: " + e.getMessage());
        }
    }

    /**
     * Set up location listener to receive updates
     */
    private void setupLocationListener() {
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                lastKnownLocation = location;
                getAddressFromLocation(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        if (locationListener != null) {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            locationManager.removeUpdates(locationListener);
        }
    }

    /**
     * Convert location to address using Geocoder
     * @param location Location to convert
     */
    private void getAddressFromLocation(Location location) {
        if (location == null) return;
        
        try {
            Geocoder geocoder = new Geocoder(context, new Locale("ru"));
            List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(), location.getLongitude(), 1);
            
            if (addresses != null && !addresses.isEmpty()) {
                lastKnownAddress = addresses.get(0);
                if (listener != null) {
                    listener.onLocationUpdated(location, lastKnownAddress);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting address: " + e.getMessage());
        }
    }

    /**
     * Check for location permissions and request if needed
     * @return true if permissions granted
     */
    private boolean checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    /**
     * Получает форматированные данные о времени
     */
    public String getFormattedTime() {
        Calendar now = Calendar.getInstance();
        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat sdfDate = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        SimpleDateFormat sdfDayOfWeek = new SimpleDateFormat("EEEE", new Locale("ru"));
        
        String currentTime = sdfTime.format(now.getTime());
        String currentDate = sdfDate.format(now.getTime());
        String dayOfWeek = sdfDayOfWeek.format(now.getTime());
        
        return currentTime + ", " + currentDate + ", " + dayOfWeek;
    }
    
    /**
     * Получает форматированные данные о местоположении
     */
    public String getFormattedLocation() {
        if (lastKnownAddress == null) {
            return "неизвестно";
        }
        
        StringBuilder locationInfo = new StringBuilder();
        
        if (lastKnownAddress.getLocality() != null) {
            locationInfo.append(lastKnownAddress.getLocality());
        }
        
        if (lastKnownAddress.getAdminArea() != null) {
            if (locationInfo.length() > 0) {
                locationInfo.append(", ");
            }
            locationInfo.append(lastKnownAddress.getAdminArea());
        }
        
        if (lastKnownAddress.getCountryName() != null) {
            if (locationInfo.length() > 0) {
                locationInfo.append(", ");
            }
            locationInfo.append(lastKnownAddress.getCountryName());
        }
        
        if (locationInfo.length() == 0) {
            return "неизвестно";
        }
        
        return locationInfo.toString();
    }

    /**
     * Получает информацию о погоде
     * @return Строка с информацией о погоде или "неизвестно" если не удалось получить
     */
    public String getWeatherInfo() {
        // Заглушка для метода погоды
        // В реальном приложении здесь будет запрос к API погоды
        return "Сегодня ясно, температура около 20 градусов";
    }
} 