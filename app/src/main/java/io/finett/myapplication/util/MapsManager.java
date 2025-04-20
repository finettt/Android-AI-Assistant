package io.finett.myapplication.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MapsManager {
    private static final String TAG = "MapsManager";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 128;
    
    private final Context context;
    private final Activity activity;
    
    public MapsManager(Activity activity) {
        this.context = activity.getApplicationContext();
        this.activity = activity;
    }
    
    /**
     * Проверяет наличие разрешений на использование местоположения
     */
    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }
    
    /**
     * Получает текущее местоположение устройства
     */
    public Location getCurrentLocation() {
        if (!checkLocationPermission()) {
            return null;
        }
        
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location lastKnownLocation = null;
        
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            
            if (lastKnownLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Error getting location: " + e.getMessage());
        }
        
        return lastKnownLocation;
    }
    
    /**
     * Получает адрес по координатам
     */
    public Address getAddressFromLocation(Location location) {
        if (location == null) {
            return null;
        }
        
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        Address address = null;
        
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                address = addresses.get(0);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting address from location: " + e.getMessage());
        }
        
        return address;
    }
    
    /**
     * Получает координаты по адресу
     */
    public Location getLocationFromAddress(String addressString) {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(addressString, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                Location location = new Location("GeoCoder");
                location.setLatitude(address.getLatitude());
                location.setLongitude(address.getLongitude());
                return location;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting location from address: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Открывает карты с указанным местоположением
     */
    public void openMap(double latitude, double longitude, String label) {
        Uri gmmIntentUri = Uri.parse("geo:" + latitude + "," + longitude + "?q=" + latitude + "," + longitude + "(" + Uri.encode(label) + ")");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        
        if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
            activity.startActivity(mapIntent);
        } else {
            // Если Google Maps не установлен, открываем в браузере
            Uri browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, browserUri);
            activity.startActivity(browserIntent);
        }
    }
    
    /**
     * Открывает карты с указанным адресом
     */
    public void openMapWithAddress(String address) {
        Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        
        if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
            activity.startActivity(mapIntent);
        } else {
            // Если Google Maps не установлен, открываем в браузере
            Uri browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(address));
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, browserUri);
            activity.startActivity(browserIntent);
        }
    }
    
    /**
     * Строит маршрут от текущего местоположения до указанного адреса или координат
     */
    public void getDirections(String destination) {
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(destination));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        
        if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
            activity.startActivity(mapIntent);
        } else {
            // Если Google Maps не установлен, открываем в браузере
            Location location = getCurrentLocation();
            String origin = "";
            
            if (location != null) {
                origin = location.getLatitude() + "," + location.getLongitude();
            }
            
            Uri browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=" + origin + "&destination=" + Uri.encode(destination) + "&travelmode=driving");
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, browserUri);
            activity.startActivity(browserIntent);
        }
    }
    
    /**
     * Строит маршрут между двумя точками
     */
    public void getDirectionsBetween(String origin, String destination) {
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(destination) + "&origin=" + Uri.encode(origin));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        
        if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
            activity.startActivity(mapIntent);
        } else {
            // Если Google Maps не установлен, открываем в браузере
            Uri browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=" + Uri.encode(origin) + "&destination=" + Uri.encode(destination) + "&travelmode=driving");
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, browserUri);
            activity.startActivity(browserIntent);
        }
    }
    
    /**
     * Ищет места поблизости (рестораны, кафе, достопримечательности и т.д.)
     */
    public void searchNearbyPlaces(String query) {
        Location location = getCurrentLocation();
        String latlng = "";
        
        if (location != null) {
            latlng = location.getLatitude() + "," + location.getLongitude();
            Uri gmmIntentUri = Uri.parse("geo:" + latlng + "?q=" + Uri.encode(query));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            
            if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
                activity.startActivity(mapIntent);
                return;
            }
        }
        
        // Если Google Maps не установлен или местоположение не доступно
        Uri browserUri = Uri.parse("https://www.google.com/maps/search/" + Uri.encode(query));
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, browserUri);
        activity.startActivity(browserIntent);
    }
    
    /**
     * Освобождает ресурсы, используемые MapsManager
     */
    public void cleanup() {
        // На данный момент здесь нечего очищать, поскольку мы не храним никаких ресурсов, 
        // требующих явного освобождения. Этот метод добавлен для совместимости с интерфейсом
        // и может быть расширен в будущем при необходимости.
    }
} 