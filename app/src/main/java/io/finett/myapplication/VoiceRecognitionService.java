package io.finett.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import android.util.Log;
import java.util.ArrayList;
import android.os.RemoteException;

/**
 * Служба распознавания речи для системного ассистента
 */
public class VoiceRecognitionService extends RecognitionService {
    private static final String TAG = "VoiceRecognitionSvc";
    private SpeechRecognizer speechRecognizer;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Служба распознавания речи создана");
        
        // Инициализируем распознаватель речи
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при создании распознавателя речи", e);
        }
    }

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback callback) {
        Log.d(TAG, "Начало прослушивания в службе распознавания речи");
        
        if (speechRecognizer == null) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt("error_code", 5); // ERROR_CLIENT = 5
            try {
                callback.error(5); // Передаем код ошибки как int
            } catch (RemoteException e) {
                Log.e(TAG, "Ошибка при отправке кода ошибки", e);
            }
            return;
        }
        
        // Здесь должна быть реализация распознавания речи
        // В реальном приложении нужно подключиться к активности VoiceChatActivity или MainActivity
        
        // Для тестирования просто отправляем пустой результат
        ArrayList<String> results = new ArrayList<>();
        results.add("Тестирование системного ассистента");
        
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, results);
        try {
            callback.results(bundle);
        } catch (RemoteException e) {
            Log.e(TAG, "Ошибка при отправке результатов", e);
        }
    }

    @Override
    protected void onCancel(Callback callback) {
        Log.d(TAG, "Отмена распознавания речи");
        try {
            callback.error(8); // ERROR_RECOGNIZER_BUSY = 8
        } catch (RemoteException e) {
            Log.e(TAG, "Ошибка при отправке кода отмены", e);
        }
    }

    @Override
    protected void onStopListening(Callback callback) {
        Log.d(TAG, "Остановка прослушивания");
        try {
            callback.endOfSpeech();
        } catch (RemoteException e) {
            Log.e(TAG, "Ошибка при отправке сигнала окончания речи", e);
        }
    }

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.onDestroy();
        Log.d(TAG, "Служба распознавания речи уничтожена");
    }
} 