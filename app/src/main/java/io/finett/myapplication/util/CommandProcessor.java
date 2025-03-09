package io.finett.myapplication.util;

import android.app.Activity;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandProcessor {
    private final CommunicationManager communicationManager;
    
    public CommandProcessor(Activity activity) {
        this.communicationManager = new CommunicationManager(activity);
    }
    
    public boolean processCommand(String command) {
        command = command.toLowerCase();
        
        // Проверяем команду звонка
        Pattern callPattern = Pattern.compile("(позвони|набери|вызови|звонок)\\s+(?:на номер\\s+)?(\\+?\\d+)");
        Matcher callMatcher = callPattern.matcher(command);
        if (callMatcher.find()) {
            String phoneNumber = callMatcher.group(2);
            communicationManager.makePhoneCall(phoneNumber);
            return true;
        }
        
        // Проверяем команду отправки SMS
        Pattern smsPattern = Pattern.compile("(отправь|напиши|отправить)\\s+(?:смс|сообщение)\\s+(?:на номер\\s+)?(\\+?\\d+)\\s+(?:с текстом\\s+)?(.+)");
        Matcher smsMatcher = smsPattern.matcher(command);
        if (smsMatcher.find()) {
            String phoneNumber = smsMatcher.group(2);
            String message = smsMatcher.group(3);
            communicationManager.sendSms(phoneNumber, message);
            return true;
        }
        
        return false;
    }
} 