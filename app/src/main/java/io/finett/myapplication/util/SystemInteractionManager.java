package io.finett.myapplication.util;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Manages interactions with system settings and hardware
 */
public class SystemInteractionManager {
    private final Activity activity;
    private static final int REQUEST_ENABLE_BT = 101;
    private static final int PERMISSION_LOCATION_REQUEST_CODE = 124;
    private static final int PERMISSION_BLUETOOTH_REQUEST_CODE = 125;
    private static final String TAG = "SystemInteractionMgr";

    public SystemInteractionManager(Activity activity) {
        this.activity = activity;
    }

    /**
     * Check for necessary permissions for location
     * @return true if permission is granted
     */
    private boolean checkLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, 
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                        PERMISSION_LOCATION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    /**
     * Check for necessary permissions for Bluetooth
     * @return true if permission is granted
     */
    private boolean checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, 
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 
                        PERMISSION_BLUETOOTH_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    /**
     * Toggle WiFi on/off
     * @param enable true to enable, false to disable
     * @return true if successful, false otherwise
     */
    public boolean toggleWifi(boolean enable) {
        try {
            // On Android 10 (API 29) and above, can't toggle WiFi directly
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                activity.startActivity(panelIntent);
                Toast.makeText(activity, enable ? "Включите Wi-Fi в настройках" : "Выключите Wi-Fi в настройках", 
                        Toast.LENGTH_LONG).show();
                return true;
            } else {
                WifiManager wifiManager = (WifiManager) activity.getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);
                return wifiManager.setWifiEnabled(enable);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle WiFi: " + e.getMessage());
            return false;
        }
    }

    /**
     * Toggle Bluetooth on/off
     * @param enable true to enable, false to disable
     * @return true if successful, false otherwise
     */
    public boolean toggleBluetooth(boolean enable) {
        if (!checkBluetoothPermissions()) {
            return false;
        }
        
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null) {
                Toast.makeText(activity, "Bluetooth не поддерживается на этом устройстве", 
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            
            if (enable) {
                if (!bluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
                return true;
            } else {
                if (bluetoothAdapter.isEnabled()) {
                    return bluetoothAdapter.disable();
                }
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle Bluetooth: " + e.getMessage());
            return false;
        }
    }

    /**
     * Toggle GPS/Location on/off
     * @param enable true to enable, false to disable
     * @return true if request was sent, false otherwise
     */
    public boolean toggleLocation(boolean enable) {
        if (!checkLocationPermissions()) {
            return false;
        }
        
        try {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            activity.startActivity(intent);
            Toast.makeText(activity, enable ? "Включите местоположение в настройках" : 
                    "Выключите местоположение в настройках", Toast.LENGTH_LONG).show();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to open location settings: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if location/GPS is enabled
     * @return true if enabled, false otherwise
     */
    public boolean isLocationEnabled() {
        if (!checkLocationPermissions()) {
            return false;
        }
        
        LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    /**
     * Check if WiFi is enabled
     * @return true if enabled, false otherwise
     */
    public boolean isWifiEnabled() {
        WifiManager wifiManager = (WifiManager) activity.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        return wifiManager.isWifiEnabled();
    }

    /**
     * Check if Bluetooth is enabled
     * @return true if enabled, false otherwise
     */
    public boolean isBluetoothEnabled() {
        if (!checkBluetoothPermissions()) {
            return false;
        }
        
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    /**
     * Adjust system volume
     * @param increase true to increase volume, false to decrease
     * @return true if successful
     */
    public boolean adjustVolume(boolean increase) {
        try {
            AudioManager audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
            audioManager.adjustVolume(
                    increase ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to adjust volume: " + e.getMessage());
            return false;
        }
    }

    /**
     * Open system settings
     * @return true if successful
     */
    public boolean openSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to open settings: " + e.getMessage());
            return false;
        }
    }

    /**
     * Open notification settings
     * @return true if successful
     */
    public boolean openNotificationSettings() {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
            } else {
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:" + activity.getPackageName()));
            }
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to open notification settings: " + e.getMessage());
            return false;
        }
    }
} 