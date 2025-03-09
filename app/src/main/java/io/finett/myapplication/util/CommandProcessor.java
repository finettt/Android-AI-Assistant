package io.finett.myapplication.util;

import android.app.Activity;
import android.speech.tts.TextToSpeech;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandProcessor {
    private final CommunicationManager communicationManager;
    private final TextToSpeech textToSpeech;
    
    public CommandProcessor(Activity activity, TextToSpeech textToSpeech) {
        this.communicationManager = new CommunicationManager(activity);
        this.textToSpeech = textToSpeech;
    }
    
    public boolean processCommand(String command) {
        command = command.toLowerCase();
        
        // Проверяем команду звонка
        Pattern callPattern = Pattern.compile("(позвони|набери|вызови|звонок)\\s+(?:на номер\\s+)?(\\+?\\d+|[а-яё\\s]+)");
        Matcher callMatcher = callPattern.matcher(command);
        if (callMatcher.find()) {
            String nameOrNumber = callMatcher.group(2);
            communicationManager.makePhoneCall(nameOrNumber);
            speakResponse("Набираю " + nameOrNumber);
            return true;
        }
        
        // Проверяем команду отправки SMS
        Pattern smsPattern = Pattern.compile("(отправь|напиши|отправить)\\s+(?:смс|сообщение)\\s+(?:на номер\\s+)?(\\+?\\d+|[а-яё\\s]+)\\s+(?:с текстом\\s+)?(.+)");
        Matcher smsMatcher = smsPattern.matcher(command);
        if (smsMatcher.find()) {
            String nameOrNumber = smsMatcher.group(2);
            String message = smsMatcher.group(3);
            communicationManager.sendSms(nameOrNumber, message);
            speakResponse("Отправляю сообщение " + nameOrNumber);
            return true;
        }
        
        return false;
    }
    
    private void speakResponse(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
    
    private String formatPhoneNumber(String number) {
        // Форматируем номер телефона для более удобного произношения
        StringBuilder formatted = new StringBuilder();
        for (char digit : number.toCharArray()) {
            formatted.append(digit).append(" ");
        }
        return formatted.toString().trim();
    }
} 