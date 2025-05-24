package io.finett.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
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
    private Callback mCurrentCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Служба распознавания речи создана");
        
        // Инициализируем распознаватель речи
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new InternalRecognitionListener());
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при создании распознавателя речи", e);
        }
    }

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback callback) {
        Log.d(TAG, "Начало прослушивания в службе распознавания речи");
        
        mCurrentCallback = callback;
        
        if (speechRecognizer == null) {
            Log.e(TAG, "SpeechRecognizer не инициализирован");
            try {
                callback.error(SpeechRecognizer.ERROR_CLIENT);
            } catch (RemoteException e) {
                Log.e(TAG, "Ошибка при отправке кода ошибки", e);
            }
            return;
        }
        
        try {
            // Настраиваем параметры распознавания
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                   RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            
            // Добавляем оригинальные extras
            if (recognizerIntent != null && recognizerIntent.getExtras() != null) {
                intent.putExtras(recognizerIntent.getExtras());
            }
            
            // Запускаем распознавание
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при запуске распознавания: " + e.getMessage(), e);
            try {
                callback.error(SpeechRecognizer.ERROR_CLIENT);
            } catch (RemoteException re) {
                Log.e(TAG, "Ошибка при отправке кода ошибки", re);
            }
        }
    }

    @Override
    protected void onCancel(Callback callback) {
        Log.d(TAG, "Отмена распознавания речи");
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
        }
        
        try {
            callback.error(SpeechRecognizer.ERROR_CLIENT);
        } catch (RemoteException e) {
            Log.e(TAG, "Ошибка при отправке сигнала отмены", e);
        }
    }

    @Override
    protected void onStopListening(Callback callback) {
        Log.d(TAG, "Остановка прослушивания");
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
        
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
    
    /**
     * Внутренний класс для обработки событий распознавания речи
     */
    private class InternalRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "Готов к распознаванию речи");
            if (mCurrentCallback != null) {
                try {
                    mCurrentCallback.readyForSpeech(params);
                } catch (RemoteException e) {
                    Log.e(TAG, "Ошибка при отправке сигнала готовности", e);
                }
            }
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "Начало речи");
            if (mCurrentCallback != null) {
                try {
                    mCurrentCallback.beginningOfSpeech();
                } catch (RemoteException e) {
                    Log.e(TAG, "Ошибка при отправке сигнала начала речи", e);
                }
            }
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            if (mCurrentCallback != null) {
                try {
                    mCurrentCallback.rmsChanged(rmsdB);
                } catch (RemoteException e) {
                    Log.e(TAG, "Ошибка при отправке изменения RMS", e);
                }
            }
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            if (mCurrentCallback != null) {
                try {
                    mCurrentCallback.bufferReceived(buffer);
                } catch (RemoteException e) {
                    Log.e(TAG, "Ошибка при отправке буфера", e);
                }
            }
        }

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "Конец речи");
            if (mCurrentCallback != null) {
                try {
                    mCurrentCallback.endOfSpeech();
                } catch (RemoteException e) {
                    Log.e(TAG, "Ошибка при отправке сигнала окончания речи", e);
                }
            }
        }

        @Override
        public void onError(int error) {
            Log.d(TAG, "Ошибка распознавания: " + error);
            if (mCurrentCallback != null) {
                try {
                    mCurrentCallback.error(error);
                } catch (RemoteException e) {
                    Log.e(TAG, "Ошибка при отправке кода ошибки", e);
                }
            }
        }

        @Override
        public void onResults(Bundle results) {
            Log.d(TAG, "Получены результаты распознавания");
            if (mCurrentCallback != null) {
                try {
                    mCurrentCallback.results(results);
                } catch (RemoteException e) {
                    Log.e(TAG, "Ошибка при отправке результатов", e);
                }
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            if (mCurrentCallback != null) {
                try {
                    mCurrentCallback.partialResults(partialResults);
                } catch (RemoteException e) {
                    Log.e(TAG, "Ошибка при отправке частичных результатов", e);
                }
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            if (mCurrentCallback != null) {
                try {
                    mCurrentCallback.endOfSpeech();
                } catch (RemoteException e) {
                    Log.e(TAG, "Ошибка при отправке события", e);
                }
            }
        }
    }
} 