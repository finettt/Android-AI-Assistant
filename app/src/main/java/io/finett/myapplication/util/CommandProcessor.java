package io.finett.myapplication.util;

import android.app.Activity;
import android.speech.tts.TextToSpeech;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandProcessor {
    private final CommunicationManager communicationManager;
    private final TextToSpeech textToSpeech;
    private final ContactsManager contactsManager;
    
    public CommandProcessor(Activity activity, TextToSpeech textToSpeech) {
        this.communicationManager = new CommunicationManager(activity);
        this.textToSpeech = textToSpeech;
        this.contactsManager = new ContactsManager(activity);
    }
    
    public boolean processCommand(String command) {
        command = command.toLowerCase();
        
        // Проверяем команду звонка
        Pattern callPattern = Pattern.compile("(позвони|набери|вызови|звонок)\\s+(?:на номер\\s+)?(\\+?\\d+|[а-яё\\s]+)");
        Matcher callMatcher = callPattern.matcher(command);
        if (callMatcher.find()) {
            String nameOrNumber = callMatcher.group(2);
            
            if (nameOrNumber.matches("\\+?\\d+")) {
                speakResponse("Набираю номер " + formatPhoneNumber(nameOrNumber));
            } else {
                List<ContactsManager.Contact> contacts = contactsManager.findContactsByPartialName(nameOrNumber);
                if (contacts.isEmpty()) {
                    speakResponse("Контакт " + nameOrNumber + " не найден");
                    return true;
                } else if (contacts.size() == 1) {
                    speakResponse("Набираю " + contacts.get(0).name);
                } else {
                    speakResponse("Найдено несколько контактов с похожим именем. Выберите нужный контакт на экране.");
                }
            }
            
            communicationManager.makePhoneCall(nameOrNumber);
            return true;
        }
        
        // Проверяем команду отправки SMS
        Pattern smsPattern = Pattern.compile("(отправь|напиши|отправить)\\s+(?:смс|сообщение)\\s+(?:на номер\\s+)?(\\+?\\d+|[а-яё\\s]+)\\s+(?:с текстом\\s+)?(.+)");
        Matcher smsMatcher = smsPattern.matcher(command);
        if (smsMatcher.find()) {
            String nameOrNumber = smsMatcher.group(2);
            String message = smsMatcher.group(3);
            
            if (nameOrNumber.matches("\\+?\\d+")) {
                speakResponse("Отправляю сообщение на номер " + formatPhoneNumber(nameOrNumber));
            } else {
                List<ContactsManager.Contact> contacts = contactsManager.findContactsByPartialName(nameOrNumber);
                if (contacts.isEmpty()) {
                    speakResponse("Контакт " + nameOrNumber + " не найден");
                    return true;
                } else if (contacts.size() == 1) {
                    speakResponse("Отправляю сообщение " + contacts.get(0).name);
                } else {
                    speakResponse("Найдено несколько контактов с похожим именем. Выберите нужный контакт на экране.");
                }
            }
            
            communicationManager.sendSms(nameOrNumber, message);
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