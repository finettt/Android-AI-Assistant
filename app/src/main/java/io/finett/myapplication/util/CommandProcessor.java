package io.finett.myapplication.util;

import android.app.Activity;
import android.speech.tts.TextToSpeech;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CommandProcessor {
    private final CommunicationManager communicationManager;
    private final TextToSpeech textToSpeech;
    private final ContactsManager contactsManager;
    private final BrowserHelper browserHelper;
    private final Map<String, List<String>> commandPatterns;
    private List<String> callPatterns;
    private List<String> smsPatterns;
    private List<String> webPatterns;
    
    public CommandProcessor(Activity activity, TextToSpeech textToSpeech, 
                          CommunicationManager communicationManager,
                          ContactsManager contactsManager,
                          BrowserHelper browserHelper) {
        this.communicationManager = communicationManager;
        this.textToSpeech = textToSpeech;
        this.contactsManager = contactsManager;
        this.browserHelper = browserHelper;
        this.commandPatterns = initializeCommandPatterns();
    }
    
    private Map<String, List<String>> initializeCommandPatterns() {
        Map<String, List<String>> patterns = new HashMap<>();
        
        // Шаблоны для звонков
        callPatterns = new ArrayList<>();
        callPatterns.add("(позвони|набери|вызови|звонок)\\s+(?:на номер\\s+)?([+]?\\d+|[а-яё]+)");
        callPatterns.add("(свяжись|соединись)\\s+(?:с\\s+)?([+]?\\d+|[а-яё]+)");
        callPatterns.add("(звонок|вызов)\\s+(?:для\\s+)?([+]?\\d+|[а-яё]+)");
        patterns.put("call", callPatterns);
        
        // Шаблоны для SMS
        smsPatterns = new ArrayList<>();
        smsPatterns.add("(отправь|напиши|отправить)\\s+(?:смс|sms|сообщение)\\s+(?:на номер\\s+)?([+]?\\d+|[а-яё]+)\\s+(?:с текстом\\s+)?(.+)");
        smsPatterns.add("(сообщи|передай)\\s+(?:для\\s+)?([+]?\\d+|[а-яё]+)\\s+(?:что\\s+)?(.+)");
        smsPatterns.add("(смс|сообщение)\\s+(?:для\\s+)?([+]?\\d+|[а-яё]+)\\s+(?:текст\\s+)?(.+)");
        patterns.put("sms", smsPatterns);
        
        // Шаблоны для веб-команд
        webPatterns = new ArrayList<>();
        webPatterns.add("(открой|найди|поиск|покажи|загрузи)\\s+(.+)");
        webPatterns.add("(зайди|перейди)\\s+(?:на\\s+)?(.+)");
        webPatterns.add("(?:открой|покажи)\\s+(?:сайт|страницу)\\s+(.+)");
        patterns.put("web", webPatterns);
        
        return patterns;
    }
    
    public boolean processCommand(String command) {
        System.out.println("Processing command: " + command.toLowerCase());
        command = command.toLowerCase();
        
        // Try call patterns
        for (String pattern : callPatterns) {
            System.out.println("Trying call pattern: " + pattern);
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(command);
            if (m.find()) {
                String nameOrNumber = m.group(2);
                System.out.println("Found name or number: " + nameOrNumber);
                
                if (nameOrNumber.matches("[+]?\\d+")) {
                    communicationManager.makePhoneCall(nameOrNumber);
                    textToSpeech.speak("Набираю номер " + nameOrNumber, TextToSpeech.QUEUE_FLUSH, null, null);
                    return true;
                } else {
                    String normalizedName = normalizeContactName(nameOrNumber);
                    System.out.println("Normalized name: " + normalizedName);
                    List<ContactsManager.Contact> contacts = contactsManager.findContactsByPartialName(normalizedName);
                    System.out.println("Found contacts: " + contacts);
                    if (!contacts.isEmpty()) {
                        ContactsManager.Contact contact = contacts.get(0);
                        communicationManager.makePhoneCall(contact.phoneNumber);
                        textToSpeech.speak("Набираю " + contact.name, TextToSpeech.QUEUE_FLUSH, null, null);
                        return true;
                    }
                }
            }
        }
        
        // Try SMS patterns
        for (String pattern : smsPatterns) {
            System.out.println("Trying SMS pattern: " + pattern);
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(command);
            if (m.find()) {
                String nameOrNumber = m.group(2);
                String message = m.group(3);
                System.out.println("Found name or number: " + nameOrNumber + ", message: " + message);
                
                if (nameOrNumber.matches("[+]?\\d+")) {
                    communicationManager.sendSms(nameOrNumber, message);
                    textToSpeech.speak("Отправляю сообщение на номер " + nameOrNumber, TextToSpeech.QUEUE_FLUSH, null, null);
                    return true;
                } else {
                    String normalizedName = normalizeContactName(nameOrNumber);
                    System.out.println("Normalized name: " + normalizedName);
                    List<ContactsManager.Contact> contacts = contactsManager.findContactsByPartialName(normalizedName);
                    System.out.println("Found contacts: " + contacts);
                    if (!contacts.isEmpty()) {
                        ContactsManager.Contact contact = contacts.get(0);
                        communicationManager.sendSms(contact.phoneNumber, message);
                        textToSpeech.speak("Отправляю сообщение " + contact.name, TextToSpeech.QUEUE_FLUSH, null, null);
                        return true;
                    }
                }
            }
        }
        
        // Try web patterns
        for (String pattern : webPatterns) {
            System.out.println("Trying web pattern: " + pattern);
            Pattern webPattern = Pattern.compile(pattern);
            Matcher webMatcher = webPattern.matcher(command);
            if (webMatcher.find()) {
                String query = webMatcher.group(2);
                browserHelper.processWebCommand(command);
                return true;
            }
        }
        
        System.out.println("No pattern matched");
        return false;
    }
    
    private String normalizeContactName(String name) {
        // Remove case and trailing characters
        return name.toLowerCase().replaceAll("[^а-яё]", "");
    }
} 