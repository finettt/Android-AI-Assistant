package io.finett.myapplication.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import androidx.preference.PreferenceManager;

public class AccessibilityManager {
    private static final String TAG = "AccessibilityManager";
    private static final float BASE_TEXT_SIZE = 16f; // базовый размер текста в SP
    private final Context context;
    private final SharedPreferences preferences;
    private final TextToSpeech textToSpeech;
    private final Vibrator vibrator;

    public AccessibilityManager(Context context, TextToSpeech textToSpeech) {
        this.context = context;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.textToSpeech = textToSpeech;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public boolean isAutoCaptureEnabled() {
        return preferences.getBoolean("auto_capture", false);
    }

    public boolean isHighContrastEnabled() {
        return preferences.getBoolean("high_contrast", false);
    }

    public boolean isTtsFeedbackEnabled() {
        return preferences.getBoolean("tts_feedback", true);
    }

    public boolean isVibrationFeedbackEnabled() {
        return preferences.getBoolean("vibration_feedback", false);
    }

    public float getSpeechRate() {
        return preferences.getInt("speech_rate", 10) / 10.0f;
    }

    public void applyTextSize(TextView textView) {
        String textSize = preferences.getString("text_size", "normal");
        float scaleFactor;
        switch (textSize) {
            case "very_small":
                scaleFactor = 0.8f;
                break;
            case "small":
                scaleFactor = 0.9f;
                break;
            case "large":
                scaleFactor = 1.2f;
                break;
            case "very_large":
                scaleFactor = 1.4f;
                break;
            default:
                scaleFactor = 1.0f;
                break;
        }
        
        // Получаем текущий размер текста в SP
        float currentSize = textView.getTextSize() / textView.getContext().getResources().getDisplayMetrics().scaledDensity;
        // Если текущий размер не установлен или равен 0, используем BASE_TEXT_SIZE
        float baseSize = currentSize > 0 ? currentSize : BASE_TEXT_SIZE;
        float newSize = baseSize * scaleFactor;
        
        Log.d(TAG, "Applying text size: " + textSize + ", base size: " + baseSize + "sp, scale: " + scaleFactor + ", final size: " + newSize + "sp");
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSize);
    }

    public void applyHighContrast(View view) {
        if (isHighContrastEnabled()) {
            view.setBackgroundColor(0xFF000000); // Черный фон
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(0xFFFFFFFF); // Белый текст
            }
        }
    }

    public void speak(String text) {
        if (isTtsFeedbackEnabled() && textToSpeech != null) {
            textToSpeech.setSpeechRate(getSpeechRate());
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    public void vibrate() {
        if (isVibrationFeedbackEnabled() && vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }
} 