package io.finett.myapplication.util;

import android.app.Activity;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import io.finett.myapplication.CameraActivity;

public class CommandProcessor {
    private final Activity activity;
    private final TextToSpeech textToSpeech;
    private final Map<String, List<String>> commandPatterns;
    private List<String> cameraPatterns;
    private static final int CAMERA_REQUEST_CODE = 100;
    private OnImageAnalysisResultListener imageAnalysisResultListener;
    
    public interface OnImageAnalysisResultListener {
        void onImageAnalysisResult(String result);
    }
    
    public CommandProcessor(Activity activity, TextToSpeech textToSpeech, 
            OnImageAnalysisResultListener listener) {
        this.activity = activity;
        this.textToSpeech = textToSpeech;
        this.imageAnalysisResultListener = listener;
        this.commandPatterns = initializeCommandPatterns();
    }

    private Map<String, List<String>> initializeCommandPatterns() {
        Map<String, List<String>> patterns = new HashMap<>();
        
        // Шаблоны для камеры
        cameraPatterns = new ArrayList<>();
        cameraPatterns.add(".*что (?:сейчас )?(?:видно|изображено) на камер[еу].*");
        cameraPatterns.add(".*посмотри (?:что|что-нибудь) через камер[уы].*");
        cameraPatterns.add(".*опиши (?:что|что-нибудь) (?:видишь )?(?:на|через) камер[уы].*");
        patterns.put("camera", cameraPatterns);
        
        return patterns;
    }

    public boolean processCommand(String command) {
        command = command.toLowerCase();

        // Проверяем команды для камеры
        for (String pattern : cameraPatterns) {
            if (command.matches(pattern)) {
                startCameraActivity();
                return true;
            }
        }

        return false;
    }

    private void startCameraActivity() {
        Intent intent = new Intent(activity, CameraActivity.class);
        activity.startActivityForResult(intent, CAMERA_REQUEST_CODE);
    }
    
    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            String analysisResult = data.getStringExtra("analysis_result");
            if (analysisResult != null && imageAnalysisResultListener != null) {
                imageAnalysisResultListener.onImageAnalysisResult(analysisResult);
            }
        }
    }
} 