package io.finett.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Broadcast receiver for handling voice activation service control commands
 */
public class VoiceActivationReceiver extends BroadcastReceiver {
    private static final String TAG = "VoiceActivationReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        if ("STOP_VOICE_ACTIVATION".equals(action)) {
            Log.d(TAG, "Stopping voice activation service");
            Intent serviceIntent = new Intent(context, VoiceActivationService.class);
            context.stopService(serviceIntent);
        } else if ("START_VOICE_ACTIVATION".equals(action)) {
            Log.d(TAG, "Starting voice activation service");
            Intent serviceIntent = new Intent(context, VoiceActivationService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } else if ("RESTART_VOICE_ACTIVATION".equals(action)) {
            Log.d(TAG, "Restarting voice activation service");
            // Stop service first
            Intent stopIntent = new Intent(context, VoiceActivationService.class);
            context.stopService(stopIntent);
            
            // Wait a moment and start again
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Log.e(TAG, "Thread interrupted while waiting to restart service", e);
            }
            
            // Start service
            Intent startIntent = new Intent(context, VoiceActivationService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent);
            } else {
                context.startService(startIntent);
            }
        }
    }
} 