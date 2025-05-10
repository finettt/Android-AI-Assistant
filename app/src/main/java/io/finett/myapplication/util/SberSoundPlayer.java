package io.finett.myapplication.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

public class SberSoundPlayer implements MusicPlayer {
    private static final String TAG = "SberSoundPlayer";
    private MediaPlayer mediaPlayer;
    private MusicControlListener listener;
    private Context context;
    
    public SberSoundPlayer(Context context) {
        this.context = context;
        initializeMediaPlayer();
    }
    
    private void initializeMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();
                
        mediaPlayer.setAudioAttributes(attributes);
        
        mediaPlayer.setOnPreparedListener(mp -> {
            if (listener != null) {
                listener.onStart();
            }
            mp.start();
        });
        
        mediaPlayer.setOnCompletionListener(mp -> {
            if (listener != null) {
                listener.onComplete();
            }
        });
        
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            if (listener != null) {
                listener.onError("Ошибка воспроизведения: код " + what);
            }
            return false;
        });
    }
    
    @Override
    public void play(String url) {
        if (mediaPlayer == null) {
            initializeMediaPlayer();
        }
        
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при загрузке аудио: " + e.getMessage());
            if (listener != null) {
                listener.onError("Ошибка при загрузке аудио: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void stop() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }
    
    @Override
    public void setListener(MusicControlListener listener) {
        this.listener = listener;
    }
    
    @Override
    public void release() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
} 