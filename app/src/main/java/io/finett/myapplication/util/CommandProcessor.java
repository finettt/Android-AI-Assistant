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
    
    public CommandProcessor(Activity activity, TextToSpeech textToSpeech) {
        this.communicationManager = new CommunicationManager(activity);
        this.textToSpeech = textToSpeech;
        this.contactsManager = new ContactsManager(activity);
        this.browserHelper = new BrowserHelper(activity);
        this.commandPatterns = initializeCommandPatterns();
    }
    
    private Map<String, List<String>> initializeCommandPatterns() {
        Map<String, List<String>> patterns = new HashMap<>();
        
        // Шаблоны для звонков
        List<String> callPatterns = new ArrayList<>();
        callPatterns.add("(позвони|набери|вызови|звонок)\\s+(?:на номер\\s+)?(\\+?\\d+|[а-яё\\s]+)");
        callPatterns.add("(свяжись|соединись)\\s+(?:с\\s+)?(\\+?\\d+|[а-яё\\s]+)");
        callPatterns.add("(звонок|вызов)\\s+(?:для\\s+)?(\\+?\\d+|[а-яё\\s]+)");
        patterns.put("call", callPatterns);
        
        // Шаблоны для SMS
        List<String> smsPatterns = new ArrayList<>();
        smsPatterns.add("(отправь|напиши|отправить)\\s+(?:смс|сообщение)\\s+(?:на номер\\s+)?(\\+?\\d+|[а-яё\\s]+)\\s+(?:с текстом\\s+)?(.+)");
        smsPatterns.add("(сообщи|передай)\\s+(?:для\\s+)?(\\+?\\d+|[а-яё\\s]+)\\s+(?:что\\s+)?(.+)");
        smsPatterns.add("(смс|сообщение)\\s+(?:для\\s+)?(\\+?\\d+|[а-яё\\s]+)\\s+(?:текст\\s+)?(.+)");
        patterns.put("sms", smsPatterns);
        
        // Шаблоны для веб-команд
        List<String> webPatterns = new ArrayList<>();
        webPatterns.add("(открой|найди|поиск|покажи|загрузи)\\s+(.+)");
        webPatterns.add("(зайди|перейди)\\s+(?:на\\s+)?(.+)");
        webPatterns.add("(?:открой|покажи)\\s+(?:сайт|страницу)\\s+(.+)");
        patterns.put("web", webPatterns);
        
        return patterns;
    }
    
    public boolean processCommand(String command) {
        command = command.toLowerCase().trim();
        
        // Проверяем все шаблоны звонков
        for (String pattern : commandPatterns.get("call")) {
            Pattern callPattern = Pattern.compile(pattern);
            Matcher callMatcher = callPattern.matcher(command);
            if (callMatcher.find()) {
                String nameOrNumber = callMatcher.group(2);
                handleCallCommand(nameOrNumber);
                return true;
            }
        }
        
        // Проверяем все шаблоны SMS
        for (String pattern : commandPatterns.get("sms")) {
            Pattern smsPattern = Pattern.compile(pattern);
            Matcher smsMatcher = smsPattern.matcher(command);
            if (smsMatcher.find()) {
                String nameOrNumber = smsMatcher.group(2);
                String message = smsMatcher.group(3);
                handleSmsCommand(nameOrNumber, message);
                return true;
            }
        }
        
        // Проверяем веб-команды
        for (String pattern : commandPatterns.get("web")) {
            Pattern webPattern = Pattern.compile(pattern);
            Matcher webMatcher = webPattern.matcher(command);
            if (webMatcher.find()) {
                String query = webMatcher.group(webMatcher.groupCount());
                handleWebCommand(command, query);
                return true;
            }
        }
        
        // Если не нашли точного совпадения, пробуем нечеткое сопоставление
        if (command.contains("звон") || command.contains("набр") || command.contains("вызов")) {
            String nameOrNumber = extractNameOrNumber(command);
            if (nameOrNumber != null) {
                handleCallCommand(nameOrNumber);
                return true;
            }
        }
        
        if (command.contains("смс") || command.contains("сообщ")) {
            String[] parts = command.split("\\s+");
            if (parts.length >= 3) {
                String nameOrNumber = extractNameOrNumber(command);
                if (nameOrNumber != null) {
                    String message = command.substring(command.indexOf(nameOrNumber) + nameOrNumber.length()).trim();
                    handleSmsCommand(nameOrNumber, message);
                    return true;
                }
            }
        }
        
        // Проверяем нечеткое сопоставление для веб-команд
        if (command.contains("сайт") || command.contains("страниц") || 
            command.contains("найди") || command.contains("поиск")) {
            handleWebCommand(command, command);
            return true;
        }
        
        return false;
    }
    
    private String extractNameOrNumber(String command) {
        // Ищем телефонный номер
        Pattern numberPattern = Pattern.compile("\\+?\\d+");
        Matcher numberMatcher = numberPattern.matcher(command);
        if (numberMatcher.find()) {
            return numberMatcher.group();
        }
        
        // Ищем имя (3 или более букв подряд)
        Pattern namePattern = Pattern.compile("[а-яё]{3,}(?:\\s+[а-яё]+)*");
        Matcher nameMatcher = namePattern.matcher(command);
        if (nameMatcher.find()) {
            return nameMatcher.group();
        }
        
        return null;
    }
    
    private void handleCallCommand(String nameOrNumber) {
        if (nameOrNumber.matches("\\+?\\d+")) {
            speakResponse("Набираю номер " + formatPhoneNumber(nameOrNumber));
        } else {
            List<ContactsManager.Contact> contacts = contactsManager.findContactsByPartialName(nameOrNumber);
            if (contacts.isEmpty()) {
                speakResponse("Контакт " + nameOrNumber + " не найден");
                return;
            } else if (contacts.size() == 1) {
                speakResponse("Набираю " + contacts.get(0).name);
            } else {
                speakResponse("Найдено несколько контактов с похожим именем. Выберите нужный контакт на экране.");
            }
        }
        
        communicationManager.makePhoneCall(nameOrNumber);
    }
    
    private void handleSmsCommand(String nameOrNumber, String message) {
        if (nameOrNumber.matches("\\+?\\d+")) {
            speakResponse("Отправляю сообщение на номер " + formatPhoneNumber(nameOrNumber));
        } else {
            List<ContactsManager.Contact> contacts = contactsManager.findContactsByPartialName(nameOrNumber);
            if (contacts.isEmpty()) {
                speakResponse("Контакт " + nameOrNumber + " не найден");
                return;
            } else if (contacts.size() == 1) {
                speakResponse("Отправляю сообщение " + contacts.get(0).name);
            } else {
                speakResponse("Найдено несколько контактов с похожим именем. Выберите нужный контакт на экране.");
            }
        }
        
        communicationManager.sendSms(nameOrNumber, message);
    }
    
    private void handleWebCommand(String command, String query) {
        if (command.contains("найди") || command.contains("поиск")) {
            speakResponse("Ищу информацию о " + query);
        } else {
            speakResponse("Открываю " + query);
        }
        browserHelper.processWebCommand(command);
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