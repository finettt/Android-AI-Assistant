package io.finett.myapplication.util;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.finett.myapplication.CameraActivity;
import io.finett.myapplication.R;

public class CommandProcessor {
    private static final String TAG = "CommandProcessor";
    
    private final Context context;
    private final Activity activity;
    private final TextToSpeech textToSpeech;
    private final Map<String, List<String>> commandPatterns;
    private List<String> cameraPatterns;
    private List<String> wifiPatterns;
    private List<String> bluetoothPatterns;
    private List<String> locationPatterns;
    private List<String> volumePatterns;
    private List<String> settingsPatterns;
    private List<String> contextInfoPatterns;
    private List<String> calendarPatterns;
    private List<String> mapsPatterns;
    private List<String> phonePatterns;
    private List<String> smsPatterns;
    private List<String> browserPatterns;
    private static final int CAMERA_REQUEST_CODE = 100;
    private OnImageAnalysisResultListener imageAnalysisResultListener;
    private SystemInteractionManager systemManager;
    private ContextInfoProvider contextInfoProvider;
    private CalendarManager calendarManager;
    private MapsManager mapsManager;
    private OnCommandProcessedListener commandProcessedListener;
    private CommunicationManager communicationManager;
    private BrowserHelper browserHelper;
    
    // Паттерны для контекстной информации
    private Pattern timePattern;
    private Pattern datePattern;
    private Pattern dayOfWeekPattern;
    private Pattern locationPattern;
    private Pattern weatherPattern;
    
    public interface OnImageAnalysisResultListener {
        void onImageAnalysisResult(String result);
    }
    
    public interface OnCommandProcessedListener {
        void onCommandProcessed(String command, String response);
    }
    
    public CommandProcessor(Activity activity, TextToSpeech textToSpeech, 
            OnImageAnalysisResultListener imageListener) {
        this(activity, textToSpeech, imageListener, null);
    }
    
    public CommandProcessor(Activity activity, TextToSpeech textToSpeech, 
            OnImageAnalysisResultListener imageListener,
            OnCommandProcessedListener commandListener) {
        this.activity = activity;
        this.context = activity;
        this.textToSpeech = textToSpeech;
        this.imageAnalysisResultListener = imageListener;
        this.commandProcessedListener = commandListener;
        this.systemManager = new SystemInteractionManager(activity);
        this.contextInfoProvider = new ContextInfoProvider(activity);
        this.calendarManager = new CalendarManager(activity);
        this.mapsManager = new MapsManager(activity);
        this.communicationManager = new CommunicationManager(activity);
        this.browserHelper = new BrowserHelper(activity);
        this.commandPatterns = initializeCommandPatterns();
    }
    
    public CommandProcessor(Activity activity, TextToSpeech textToSpeech, 
            CommunicationManager communicationManager, ContactsManager contactsManager, 
            BrowserHelper browserHelper) {
        this.activity = activity;
        this.context = activity;
        this.textToSpeech = textToSpeech;
        this.communicationManager = communicationManager;
        this.browserHelper = browserHelper;
        this.systemManager = new SystemInteractionManager(activity);
        this.contextInfoProvider = new ContextInfoProvider(activity);
        this.calendarManager = new CalendarManager(activity);
        this.mapsManager = new MapsManager(activity);
        this.commandPatterns = initializeCommandPatterns();
    }
    
    public void setCommandProcessedListener(OnCommandProcessedListener listener) {
        this.commandProcessedListener = listener;
    }

    private Map<String, List<String>> initializeCommandPatterns() {
        Map<String, List<String>> patterns = new HashMap<>();
        
        // Шаблоны для телефонных звонков
        phonePatterns = new ArrayList<>();
        phonePatterns.add(".*(?:позвони|набери|вызови|звонок) (?:на номер )?([a-zA-Zа-яА-Я0-9\\+]+).*");
        phonePatterns.add(".*(?:позвони|набери|вызови|звонок) ([a-zA-Zа-яА-Я]+).*");
        patterns.put("phone", phonePatterns);
        
        // Шаблоны для SMS
        smsPatterns = new ArrayList<>();
        smsPatterns.add(".*(?:отправь|напиши|написать|создай) (?:смс|sms|сообщение) ([a-zA-Zа-яА-Я0-9\\+]+) (?:с текстом|текст) (.+).*");
        smsPatterns.add(".*(?:отправь|напиши|написать|создай) (?:смс|sms|сообщение) ([a-zA-Zа-яА-Я]+) (?:с текстом|текст) (.+).*");
        patterns.put("sms", smsPatterns);
        
        // Шаблоны для браузера
        browserPatterns = new ArrayList<>();
        browserPatterns.add(".*(?:открой|запусти|загрузи|покажи) ((?:https?://)?(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(?:/[\\w-./]*)?).*");
        browserPatterns.add(".*(?:найди|поиск|ищи|искать) (в интернете )?(.+).*");
        browserPatterns.add(".*(?:найди|поиск|ищи|искать) (.+).*");
        patterns.put("browser", browserPatterns);
        
        // Шаблоны для камеры
        cameraPatterns = new ArrayList<>();
        cameraPatterns.add(".*что (?:сейчас )?(?:видно|изображено) на камер[еу].*");
        cameraPatterns.add(".*посмотри (?:что|что-нибудь) через камер[уы].*");
        cameraPatterns.add(".*опиши (?:что|что-нибудь) (?:видишь )?(?:на|через) камер[уы].*");
        cameraPatterns.add(".*что изображено на камере.*");
        patterns.put("camera", cameraPatterns);
        
        // Шаблоны для Wi-Fi
        wifiPatterns = new ArrayList<>();
        wifiPatterns.add(".*(?:включи|активируй|запусти) (?:вай[ \\-]фай|wi[ \\-]fi).*");
        wifiPatterns.add(".*(?:выключи|отключи|деактивируй) (?:вай[ \\-]фай|wi[ \\-]fi).*");
        wifiPatterns.add(".*проверь (?:статус )?(?:вай[ \\-]фай|wi[ \\-]fi).*");
        patterns.put("wifi", wifiPatterns);
        
        // Шаблоны для Bluetooth
        bluetoothPatterns = new ArrayList<>();
        bluetoothPatterns.add(".*(?:включи|активируй|запусти) (?:блютуз|bluetooth).*");
        bluetoothPatterns.add(".*(?:выключи|отключи|деактивируй) (?:блютуз|bluetooth).*");
        bluetoothPatterns.add(".*проверь (?:статус )?(?:блютуз|bluetooth).*");
        patterns.put("bluetooth", bluetoothPatterns);
        
        // Шаблоны для GPS/Локации
        locationPatterns = new ArrayList<>();
        locationPatterns.add(".*(?:включи|активируй|запусти) (?:геолокацию|gps|местоположение|локацию).*");
        locationPatterns.add(".*(?:выключи|отключи|деактивируй) (?:геолокацию|gps|местоположение|локацию).*");
        locationPatterns.add(".*проверь (?:статус )?(?:геолокации|gps|местоположения|локации).*");
        patterns.put("location", locationPatterns);
        
        // Шаблоны для громкости
        volumePatterns = new ArrayList<>();
        volumePatterns.add(".*(?:увеличь|прибавь|подними|сделай громче) (?:звук|громкость).*");
        volumePatterns.add(".*(?:уменьши|убавь|понизь|сделай тише) (?:звук|громкость).*");
        patterns.put("volume", volumePatterns);
        
        // Шаблоны для настроек
        settingsPatterns = new ArrayList<>();
        settingsPatterns.add(".*(?:открой|запусти|покажи) (?:настройки|параметры).*");
        settingsPatterns.add(".*(?:открой|запусти|покажи) (?:настройки|параметры) (?:уведомлений|оповещений).*");
        patterns.put("settings", settingsPatterns);
        
        // Шаблоны для запросов контекстной информации
        contextInfoPatterns = new ArrayList<>();
        contextInfoPatterns.add(".*(?:скажи|скольк[^а-я]|который) (?:час|время)(?:[^а-я]|$).*");
        contextInfoPatterns.add(".*какое сейчас время.*");
        contextInfoPatterns.add(".*какая сегодня дата.*");
        contextInfoPatterns.add(".*какой сегодня день.*");
        contextInfoPatterns.add(".*где (?:я|мы) (?:сейчас|находимся).*");
        contextInfoPatterns.add(".*(?:в каком|какой) (?:городе|стране) (?:я|мы) (?:сейчас|находимся).*");
        contextInfoPatterns.add(".*(?:какая|узнай|скажи) (?:погода|температура) (?:сейчас|на улице).*");
        patterns.put("contextInfo", contextInfoPatterns);
        
        // Шаблоны для календаря
        calendarPatterns = new ArrayList<>();
        calendarPatterns.add(".*(?:какие|какое) (?:событи[яе]|мероприяти[яе]|встреч[иа]) (?:запланированы|у меня|сегодня).*");
        calendarPatterns.add(".*(?:покажи|открой|проверь) (?:мой|мои) (?:календарь|встречи|событи[яе]).*");
        calendarPatterns.add(".*(?:создай|добавь|запиши) (?:событие|встречу|мероприятие) (?:в календарь|в календаре).*");
        calendarPatterns.add(".*(?:что|какие встречи) у меня (завтра|послезавтра|на этой неделе).*");
        calendarPatterns.add(".*(?:что|что-нибудь) запланировано (?:на|в) (.+).*");
        patterns.put("calendar", calendarPatterns);
        
        // Шаблоны для карт и навигации
        mapsPatterns = new ArrayList<>();
        mapsPatterns.add(".*(?:покажи|открой|найди) (?:на карте|карту) (.+).*");
        mapsPatterns.add(".*(?:как|маршрут|проложи маршрут) (?:добраться|доехать|дойти) (?:до|в|на) (.+).*");
        mapsPatterns.add(".*(?:найди|покажи) (?:рядом|поблизости|близко|неподалеку) (.+).*");
        mapsPatterns.add(".*(?:где находится|где расположен|как найти) (.+).*");
        patterns.put("maps", mapsPatterns);
        
        // Инициализация паттернов контекстной информации
        timePattern = Pattern.compile(".*(?:который час|сколько времени|текущее время|время сейчас).*", Pattern.CASE_INSENSITIVE);
        datePattern = Pattern.compile(".*(?:какое сегодня число|какая сегодня дата|дата сегодня|сегодняшняя дата).*", Pattern.CASE_INSENSITIVE);
        dayOfWeekPattern = Pattern.compile(".*(?:какой сегодня день|день недели).*", Pattern.CASE_INSENSITIVE);
        locationPattern = Pattern.compile(".*(?:где я|мое местоположение|где мы находимся|где я нахожусь|какой город).*", Pattern.CASE_INSENSITIVE);
        weatherPattern = Pattern.compile(".*(?:погода|какая погода|прогноз погоды|температура).*", Pattern.CASE_INSENSITIVE);
        
        return patterns;
    }

    /**
     * Обрабатывает команду пользователя
     * @param command команда пользователя
     * @return true если команда была обработана, false в противном случае
     */
    public boolean processCommand(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }
        
        // Преобразуем команду к нижнему регистру для удобства сравнения
        String normalizedCommand = command.toLowerCase();
        
        // Команды для звонка
        if (normalizedCommand.startsWith("позвони") || 
            normalizedCommand.startsWith("набери") || 
            normalizedCommand.startsWith("вызов") ||
            normalizedCommand.contains("позвонить")) {
            
            // Ищем контакт или номер телефона
            String phoneNumber = extractPhoneNumber(normalizedCommand);
            String contactName = extractContactName(normalizedCommand);
            
            if (phoneNumber != null) {
                // Используем подтверждение для звонка
                processCallCommandWithConfirmation(phoneNumber, contactName);
                return true;
            } else {
                commandProcessedListener.onCommandProcessed(
                    command,
                    "Не удалось распознать номер телефона или контакт в команде"
                );
                return true;
            }
        }
        
        // Команды для отправки SMS
        if (normalizedCommand.startsWith("отправь сообщение") || 
            normalizedCommand.startsWith("отправить сообщение") || 
            normalizedCommand.startsWith("напиши сообщение") ||
            normalizedCommand.startsWith("отправь смс") || 
            normalizedCommand.startsWith("написать смс")) {
            
            // Ищем контакт или номер телефона и текст сообщения
            String phoneNumber = extractPhoneNumber(normalizedCommand);
            String contactName = extractContactName(normalizedCommand);
            String messageText = extractMessageText(normalizedCommand);
            
            if (phoneNumber != null && messageText != null) {
                // Используем подтверждение для SMS
                processSmsCommandWithConfirmation(phoneNumber, contactName, messageText);
                return true;
            } else {
                commandProcessedListener.onCommandProcessed(
                    command,
                    "Не удалось распознать номер телефона или текст сообщения в команде"
                );
                return true;
            }
        }
        
        // Команды для запуска приложений
        if (normalizedCommand.startsWith("открой") || 
            normalizedCommand.startsWith("запусти") || 
            normalizedCommand.startsWith("открыть") || 
            normalizedCommand.startsWith("запустить")) {
            
            // Извлекаем название приложения из команды
            String appName = extractAppName(normalizedCommand);
            
            if (appName != null && !appName.isEmpty()) {
                return launchApp(appName);
            }
        }
        
        // Сначала проверяем контекстные команды
        if (processContextInfoCommand(normalizedCommand)) {
            return true;
        }
        
        // Проверяем команды календаря
        if (processCalendarCommand(normalizedCommand)) {
            return true;
        }
        
        // Проверяем команды карт и навигации
        if (processMapsCommand(normalizedCommand)) {
            return true;
        }

        // Проверяем Wi-Fi команды
        for (String pattern : wifiPatterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(normalizedCommand).matches()) {
                return processWifiCommand(normalizedCommand);
            }
        }
        
        // Проверяем Bluetooth команды
        for (String pattern : bluetoothPatterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(normalizedCommand).matches()) {
                return processBluetoothCommand(normalizedCommand);
            }
        }

        // Проверяем Location/GPS команды
        for (String pattern : locationPatterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(normalizedCommand).matches()) {
                return processLocationCommand(normalizedCommand);
            }
        }
        
        // Проверяем команды громкости
        for (String pattern : volumePatterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(normalizedCommand).matches()) {
                return processVolumeCommand(normalizedCommand);
            }
        }
        
        // Проверяем команды настроек
        for (String pattern : settingsPatterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(normalizedCommand).matches()) {
                return processSettingsCommand(normalizedCommand);
            }
        }

        // Команды для камеры
        for (String pattern : cameraPatterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(normalizedCommand).matches()) {
                startCameraActivity();
                return true;
            }
        }

        return false;
    }
    
    /**
     * Извлекает название приложения из команды
     * @param command команда пользователя
     * @return название приложения или null, если не удалось извлечь
     */
    private String extractAppName(String command) {
        // Шаблоны команд для запуска приложений
        String[] patterns = {
            "открой (.*)",
            "открыть (.*)",
            "запусти (.*)",
            "запустить (.*)"
        };
        
        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(command);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        
        // Если не нашли по шаблонам, пробуем извлечь все после первого слова
        String[] words = command.split("\\s+", 2);
        if (words.length > 1) {
            return words[1].trim();
        }
        
        return null;
    }
    
    /**
     * Обрабатывает команды для звонков
     * @param command команда пользователя
     * @return true если команда была обработана, false в противном случае
     */
    private boolean processPhoneCommand(String command) {
        // Шаблоны для команд звонков
        String[] phonePatterns = {
            "позвони (.*)",
            "набери (.*)",
            "вызов (.*)",
            "позвонить (.*)"
        };
        
        for (String pattern : phonePatterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(command);
            if (m.find()) {
                String contact = m.group(1).trim();
                
                // Извлекаем номер телефона или имя контакта
                String phoneNumber = extractPhoneNumber(command);
                String contactName = extractContactName(command);
                
                if (phoneNumber != null) {
                    // Используем метод с подтверждением
                    processCallCommandWithConfirmation(phoneNumber, contactName);
                    return true;
                } else {
                    commandProcessedListener.onCommandProcessed(
                        command,
                        "Не удалось распознать номер телефона или контакт в команде"
                    );
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Обрабатывает команды для отправки SMS
     * @param command команда пользователя
     * @return true если команда была обработана, false в противном случае
     */
    private boolean processSmsCommand(String command) {
        // Шаблоны для SMS команд
        String[] smsPatterns = {
            "отправь сообщение (.*)",
            "отправить сообщение (.*)",
            "напиши сообщение (.*)",
            "отправь смс (.*)",
            "написать смс (.*)"
        };
        
        for (String pattern : smsPatterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(command);
            if (m.find()) {
                String recipient = m.group(1).trim();
                
                // Извлекаем номер телефона или имя контакта и текст сообщения
                String phoneNumber = extractPhoneNumber(command);
                String contactName = extractContactName(command);
                String messageText = extractMessageText(command);
                
                if (phoneNumber != null && messageText != null) {
                    // Используем метод с подтверждением
                    processSmsCommandWithConfirmation(phoneNumber, contactName, messageText);
                    return true;
                } else {
                    commandProcessedListener.onCommandProcessed(
                        command,
                        "Не удалось распознать номер телефона или текст сообщения в команде"
                    );
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private boolean processBrowserCommand(String command) {
        // Проверяем шаблоны для браузера
        Pattern openUrlPattern = Pattern.compile(".*(?:открой|запусти|загрузи|покажи) ((?:https?://)?(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(?:/[\\w-./]*)?).*", Pattern.CASE_INSENSITIVE);
        Pattern searchPattern = Pattern.compile(".*(?:найди|поиск|ищи|искать) (в интернете )?(.+).*", Pattern.CASE_INSENSITIVE);
        
        Matcher urlMatcher = openUrlPattern.matcher(command);
        Matcher searchMatcher = searchPattern.matcher(command);
        
        if (urlMatcher.matches()) {
            String url = urlMatcher.group(1);
            if (browserHelper != null) {
                browserHelper.processWebCommand(command);
                textToSpeech.speak("Открываю " + url, TextToSpeech.QUEUE_FLUSH, null, null);
                return true;
            }
        } else if (searchMatcher.matches()) {
            String query = searchMatcher.group(2);
            if (browserHelper != null) {
                browserHelper.processWebCommand(command);
                textToSpeech.speak("Ищу в интернете: " + query, TextToSpeech.QUEUE_FLUSH, null, null);
                return true;
            }
        }
        
        return false;
    }
    
    private boolean processContextInfoCommand(String command) {
        // Запрос времени
        if (timePattern.matcher(command).matches() || datePattern.matcher(command).matches() || 
            dayOfWeekPattern.matcher(command).matches()) {
            
            String timeInfo = contextInfoProvider.getFormattedTime();
            String response = "Сейчас " + timeInfo;
            
            if (commandProcessedListener != null) {
                commandProcessedListener.onCommandProcessed(command, response);
            }
            return true;
        }
        
        // Запрос местоположения
        if (locationPattern.matcher(command).matches()) {
            String locationInfo = contextInfoProvider.getFormattedLocation();
            String response;
            
            if (locationInfo.equals("неизвестно")) {
                response = "Я не могу определить ваше текущее местоположение. Возможно, нужно включить GPS или предоставить разрешение на доступ к местоположению.";
            } else {
                response = "Вы находитесь в " + locationInfo;
            }
            
            if (commandProcessedListener != null) {
                commandProcessedListener.onCommandProcessed(command, response);
            }
            return true;
        }
        
        // Запрос погоды (заглушка)
        if (weatherPattern.matcher(command).matches()) {
            String locationInfo = contextInfoProvider.getFormattedLocation();
            String response;
            
            if (locationInfo.equals("неизвестно")) {
                response = "Для отображения прогноза погоды необходимо определить ваше местоположение.";
            } else {
                response = "К сожалению, у меня нет доступа к данным о погоде в " + locationInfo + " в данный момент.";
            }
            
            if (commandProcessedListener != null) {
                commandProcessedListener.onCommandProcessed(command, response);
            }
            return true;
        }
        
        return false;
    }
    
    private boolean processCalendarCommand(String command) {
        // Проверяем разрешения календаря
        if (!calendarManager.checkCalendarPermission()) {
            notifyCommandProcessed(command, "Для работы с календарем необходимо предоставить разрешение.");
            return true;
        }
        
        // Команда "Какие события сегодня"
        if (command.matches(".*(?:какие|какое) (?:событи[яе]|мероприяти[яе]|встреч[иа]) (?:запланированы|у меня|сегодня).*") ||
            command.matches(".*(?:что у меня|что-нибудь|планы) сегодня.*")) {
            
            List<String> events = calendarManager.getTodayEvents();
            String response;
            
            if (events.isEmpty()) {
                response = "На сегодня у вас нет запланированных событий.";
            } else {
                response = "На сегодня у вас запланировано: " + String.join(", ", events);
            }
            
            notifyCommandProcessed(command, response);
            return true;
        }
        
        // Команда "Покажи мой календарь"
        if (command.matches(".*(?:покажи|открой|проверь) (?:мой|мои) (?:календарь|встречи|событи[яе]).*")) {
            calendarManager.openCalendar();
            notifyCommandProcessed(command, "Открываю календарь.");
            return true;
        }
        
        // Команда "Что у меня завтра/на этой неделе"
        if (command.matches(".*(?:что|какие встречи) у меня (завтра|послезавтра|на этой неделе).*")) {
            int days = 1;
            if (command.contains("завтра")) {
                days = 1;
            } else if (command.contains("послезавтра")) {
                days = 2;
            } else if (command.contains("на этой неделе")) {
                days = 7;
            }
            
            List<String> events = calendarManager.getUpcomingEvents(days);
            String timeFrame = days == 1 ? "завтра" : (days == 2 ? "послезавтра" : "на ближайшие " + days + " дней");
            String response;
            
            if (events.isEmpty()) {
                response = "У вас нет запланированных событий на " + timeFrame + ".";
            } else {
                response = "Вот ваши события на " + timeFrame + ": " + String.join(", ", events);
            }
            
            notifyCommandProcessed(command, response);
            return true;
        }
        
        // Команда "Создай событие в календаре"
        if (command.matches(".*(?:создай|добавь|запиши) (?:событие|встречу|мероприятие) (?:в календарь|в календаре).*")) {
            calendarManager.openNewEventForm();
            notifyCommandProcessed(command, "Открываю форму создания нового события.");
            return true;
        }
        
        // Команда "Что запланировано на [дата]"
        Pattern plannedPattern = Pattern.compile(".*(?:что|что-нибудь) запланировано (?:на|в) (.+).*");
        Matcher plannedMatcher = plannedPattern.matcher(command);
        
        if (plannedMatcher.matches()) {
            String dateText = plannedMatcher.group(1);
            Date date = calendarManager.parseDateFromText(dateText);
            
            if (date != null) {
                List<String> events = calendarManager.getEventsForDate(date);
                SimpleDateFormat sdf = new SimpleDateFormat("d MMMM", Locale.getDefault());
                String formattedDate = sdf.format(date);
                String response;
                
                if (events.isEmpty()) {
                    response = "На " + formattedDate + " у вас нет запланированных событий.";
                } else {
                    response = "На " + formattedDate + " у вас запланировано: " + String.join(", ", events);
                }
                
                notifyCommandProcessed(command, response);
            } else {
                notifyCommandProcessed(command, "Извините, я не смог распознать указанную дату.");
            }
            return true;
        }
        
        return false;
    }
    
    private boolean processMapsCommand(String command) {
        // Проверяем разрешения на местоположение
        if (!mapsManager.checkLocationPermission()) {
            notifyCommandProcessed(command, "Для работы с картами необходимо предоставить разрешение на доступ к местоположению.");
            return true;
        }
        
        // Команда "Покажи на карте [место]"
        Pattern showOnMapPattern = Pattern.compile(".*(?:покажи|открой|найди) (?:на карте|карту) (.+).*");
        Matcher showOnMapMatcher = showOnMapPattern.matcher(command);
        
        if (showOnMapMatcher.matches()) {
            String place = showOnMapMatcher.group(1);
            mapsManager.openMapWithAddress(place);
            notifyCommandProcessed(command, "Открываю на карте: " + place);
            return true;
        }
        
        // Команда "Как добраться до [место]"
        Pattern directionsPattern = Pattern.compile(".*(?:как|маршрут|проложи маршрут) (?:добраться|доехать|дойти) (?:до|в|на) (.+).*");
        Matcher directionsMatcher = directionsPattern.matcher(command);
        
        if (directionsMatcher.matches()) {
            String destination = directionsMatcher.group(1);
            mapsManager.getDirections(destination);
            notifyCommandProcessed(command, "Прокладываю маршрут до: " + destination);
            return true;
        }
        
        // Команда "Найди поблизости [место]"
        Pattern nearbyPattern = Pattern.compile(".*(?:найди|покажи) (?:рядом|поблизости|близко|неподалеку) (.+).*");
        Matcher nearbyMatcher = nearbyPattern.matcher(command);
        
        if (nearbyMatcher.matches()) {
            String placeType = nearbyMatcher.group(1);
            mapsManager.searchNearbyPlaces(placeType);
            notifyCommandProcessed(command, "Ищу поблизости: " + placeType);
            return true;
        }
        
        // Команда "Где находится [место]"
        Pattern whereIsPattern = Pattern.compile(".*(?:где находится|где расположен|как найти) (.+).*");
        Matcher whereIsMatcher = whereIsPattern.matcher(command);
        
        if (whereIsMatcher.matches()) {
            String place = whereIsMatcher.group(1);
            mapsManager.openMapWithAddress(place);
            notifyCommandProcessed(command, "Ищу на карте: " + place);
            return true;
        }
        
        return false;
    }
    
    private void notifyCommandProcessed(String command, String response) {
        if (commandProcessedListener != null) {
            commandProcessedListener.onCommandProcessed(command, response);
        }
    }

    public void startCameraActivity() {
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
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        if (contextInfoProvider != null) {
            contextInfoProvider.cleanup();
        }
        
        if (calendarManager != null) {
            // Освобождаем ресурсы календаря, если они есть
        }
        
        if (mapsManager != null) {
            mapsManager.cleanup();
        }
    }

    private boolean processWifiCommand(String command) {
        String response;
        
        if (command.contains("включи") || command.contains("активируй") || command.contains("запусти")) {
            boolean success = systemManager.toggleWifi(true);
            response = success ? "Включаю Wi-Fi" : "Не удалось включить Wi-Fi";
        } else if (command.contains("выключи") || command.contains("отключи") || command.contains("деактивируй")) {
            boolean success = systemManager.toggleWifi(false);
            response = success ? "Выключаю Wi-Fi" : "Не удалось выключить Wi-Fi";
        } else if (command.contains("проверь")) {
            boolean isEnabled = systemManager.isWifiEnabled();
            response = isEnabled ? "Wi-Fi включен" : "Wi-Fi выключен";
        } else {
            response = "Не удалось распознать команду для Wi-Fi";
            return false;
        }
        
        if (commandProcessedListener != null) {
            commandProcessedListener.onCommandProcessed(command, response);
        }
        return true;
    }
    
    private boolean processBluetoothCommand(String command) {
        String response;
        
        if (command.contains("включи") || command.contains("активируй") || command.contains("запусти")) {
            boolean success = systemManager.toggleBluetooth(true);
            response = success ? "Включаю Bluetooth" : "Не удалось включить Bluetooth";
        } else if (command.contains("выключи") || command.contains("отключи") || command.contains("деактивируй")) {
            boolean success = systemManager.toggleBluetooth(false);
            response = success ? "Выключаю Bluetooth" : "Не удалось выключить Bluetooth";
        } else if (command.contains("проверь")) {
            boolean isEnabled = systemManager.isBluetoothEnabled();
            response = isEnabled ? "Bluetooth включен" : "Bluetooth выключен";
        } else {
            response = "Не удалось распознать команду для Bluetooth";
            return false;
        }
        
        if (commandProcessedListener != null) {
            commandProcessedListener.onCommandProcessed(command, response);
        }
        return true;
    }
    
    private boolean processLocationCommand(String command) {
        String response;
        
        if (command.contains("включи") || command.contains("активируй") || command.contains("запусти")) {
            boolean success = systemManager.toggleLocation(true);
            response = success ? "Открываю настройки местоположения" : "Не удалось открыть настройки местоположения";
        } else if (command.contains("выключи") || command.contains("отключи") || command.contains("деактивируй")) {
            boolean success = systemManager.toggleLocation(false);
            response = success ? "Открываю настройки местоположения" : "Не удалось открыть настройки местоположения";
        } else if (command.contains("проверь")) {
            boolean isEnabled = systemManager.isLocationEnabled();
            response = isEnabled ? "Местоположение включено" : "Местоположение выключено";
        } else {
            response = "Не удалось распознать команду для местоположения";
            return false;
        }
        
        if (commandProcessedListener != null) {
            commandProcessedListener.onCommandProcessed(command, response);
        }
        return true;
    }
    
    private boolean processVolumeCommand(String command) {
        String response;
        
        if (command.contains("увеличь") || command.contains("прибавь") || 
                command.contains("подними") || command.contains("громче")) {
            boolean success = systemManager.adjustVolume(true);
            response = success ? "Увеличиваю громкость" : "Не удалось увеличить громкость";
        } else if (command.contains("уменьши") || command.contains("убавь") || 
                command.contains("понизь") || command.contains("тише")) {
            boolean success = systemManager.adjustVolume(false);
            response = success ? "Уменьшаю громкость" : "Не удалось уменьшить громкость";
        } else {
            response = "Не удалось распознать команду для громкости";
            return false;
        }
        
        if (commandProcessedListener != null) {
            commandProcessedListener.onCommandProcessed(command, response);
        }
        return true;
    }
    
    private boolean processSettingsCommand(String command) {
        String response;
        
        if (command.contains("уведомлений") || command.contains("оповещений")) {
            boolean success = systemManager.openNotificationSettings();
            response = success ? "Открываю настройки уведомлений" : "Не удалось открыть настройки уведомлений";
        } else {
            boolean success = systemManager.openSettings();
            response = success ? "Открываю настройки" : "Не удалось открыть настройки";
        }
        
        if (commandProcessedListener != null) {
            commandProcessedListener.onCommandProcessed(command, response);
        }
        return true;
    }

    /**
     * Запускает приложение по его названию
     * @param appName название приложения для запуска
     * @return true если приложение найдено и запущено, false в противном случае
     */
    public boolean launchApp(String appName) {
        try {
            // Получаем PackageManager для поиска приложений
            PackageManager packageManager = context.getPackageManager();
            
            // Получаем список всех установленных приложений
            List<ApplicationInfo> installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            
            // Нормализуем имя приложения для поиска
            String normalizedAppName = appName.toLowerCase().trim();
            
            // Создаем список соответствий для поиска наиболее подходящего приложения
            Map<String, ApplicationInfo> appMatches = new HashMap<>();
            
            for (ApplicationInfo appInfo : installedApps) {
                // Получаем имя приложения
                String currentAppName = packageManager.getApplicationLabel(appInfo).toString().toLowerCase();
                
                // Проверяем, содержит ли имя приложения искомую строку
                if (currentAppName.contains(normalizedAppName) || 
                    normalizedAppName.contains(currentAppName)) {
                    appMatches.put(currentAppName, appInfo);
                }
            }
            
            // Если найдены соответствия
            if (!appMatches.isEmpty()) {
                // Находим наиболее точное соответствие
                String bestMatch = "";
                ApplicationInfo bestApp = null;
                
                for (Map.Entry<String, ApplicationInfo> entry : appMatches.entrySet()) {
                    if (bestMatch.isEmpty() || entry.getKey().length() < bestMatch.length()) {
                        bestMatch = entry.getKey();
                        bestApp = entry.getValue();
                    }
                }
                
                if (bestApp != null) {
                    // Получаем Intent для запуска приложения
                    Intent launchIntent = packageManager.getLaunchIntentForPackage(bestApp.packageName);
                    
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(launchIntent);
                        
                        // Сообщаем об успешном запуске
                        commandProcessedListener.onCommandProcessed(
                            "открой " + appName,
                            "Открываю " + packageManager.getApplicationLabel(bestApp).toString()
                        );
                        return true;
                    }
                }
            }
            
            // Если не нашли приложение или не смогли запустить
            commandProcessedListener.onCommandProcessed(
                "открой " + appName,
                "Не удалось найти приложение \"" + appName + "\" или запустить его"
            );
            
        } catch (Exception e) {
            Log.e(TAG, "Error launching app: " + e.getMessage());
            commandProcessedListener.onCommandProcessed(
                "открой " + appName,
                "Произошла ошибка при попытке запуска приложения: " + e.getMessage()
            );
        }
        
        return false;
    }

    /**
     * Показывает диалог подтверждения с голосовым запросом
     * @param title заголовок диалога
     * @param message сообщение диалога
     * @param confirmAction действие при подтверждении
     */
    private void showVoiceConfirmationDialog(String title, String message, Runnable confirmAction) {
        if (context instanceof Activity) {
            Activity activityContext = (Activity) context;
            
            // Создаем диалог подтверждения
            AlertDialog.Builder builder = new AlertDialog.Builder(activityContext);
            builder.setTitle(title);
            builder.setMessage(message);
            
            // Создаем переменную для отслеживания состояния распознавания
            final boolean[] isListening = {false};
            final boolean[] dialogShowing = {true};
            
            // Создаем SpeechRecognizer для распознавания ответа
            SpeechRecognizer speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            
            // Добавляем кнопки
            builder.setPositiveButton("Да", (dialog, which) -> {
                if (speechRecognizer != null) {
                    speechRecognizer.destroy();
                }
                confirmAction.run();
            });
            
            builder.setNegativeButton("Нет", (dialog, which) -> {
                if (speechRecognizer != null) {
                    speechRecognizer.destroy();
                }
                // Отменяем действие
                if (textToSpeech != null) {
                    HashMap<String, String> params = new HashMap<>();
                    params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "cancelAction");
                    textToSpeech.speak("Действие отменено", TextToSpeech.QUEUE_FLUSH, params);
                }
            });
            
            // Показываем диалог
            AlertDialog dialog = builder.create();
            dialog.setOnDismissListener(dialogInterface -> {
                dialogShowing[0] = false;
                if (speechRecognizer != null) {
                    speechRecognizer.destroy();
                }
            });
            dialog.show();
            
            // Озвучиваем вопрос
            if (textToSpeech != null) {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "confirmQuestion");
                
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {}
                    
                    @Override
                    public void onDone(String utteranceId) {
                        if ("confirmQuestion".equals(utteranceId) && dialogShowing[0]) {
                            // Начинаем слушать ответ после озвучивания вопроса
                            activityContext.runOnUiThread(() -> {
                                if (!isListening[0] && dialogShowing[0]) {
                                    startListeningForConfirmation(speechRecognizer, confirmAction, dialog);
                                    isListening[0] = true;
                                }
                            });
                        }
                    }
                    
                    @Override
                    public void onError(String utteranceId) {}
                });
                
                textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, params);
            }
        }
    }
    
    /**
     * Запускает распознавание речи для подтверждения действия
     */
    private void startListeningForConfirmation(SpeechRecognizer speechRecognizer, Runnable confirmAction, AlertDialog dialog) {
        try {
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {}
                
                @Override
                public void onBeginningOfSpeech() {}
                
                @Override
                public void onRmsChanged(float rmsdB) {}
                
                @Override
                public void onBufferReceived(byte[] buffer) {}
                
                @Override
                public void onEndOfSpeech() {}
                
                @Override
                public void onError(int error) {
                    // При ошибке просто продолжаем показывать диалог
                }
                
                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String response = matches.get(0).toLowerCase();
                        
                        // Проверяем ответ на подтверждение
                        if (response.contains("да") || response.contains("подтверждаю") || 
                            response.contains("согласен") || response.contains("выполняй")) {
                            dialog.dismiss();
                            confirmAction.run();
                        } 
                        // Проверяем ответ на отказ
                        else if (response.contains("нет") || response.contains("отмена") || 
                                 response.contains("отменить") || response.contains("не надо")) {
                            dialog.dismiss();
                            // Отменяем действие
                            if (textToSpeech != null) {
                                HashMap<String, String> params = new HashMap<>();
                                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "cancelAction");
                                textToSpeech.speak("Действие отменено", TextToSpeech.QUEUE_FLUSH, params);
                            }
                        }
                        // Если ответ не распознан, продолжаем показывать диалог
                    }
                }
                
                @Override
                public void onPartialResults(Bundle partialResults) {}
                
                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
            
            // Запускаем распознавание
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
            speechRecognizer.startListening(intent);
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting speech recognition for confirmation: " + e.getMessage());
        }
    }

    /**
     * Обрабатывает команду звонка с голосовым подтверждением
     */
    public void processCallCommandWithConfirmation(String phoneNumber, String contactName) {
        String confirmMessage = "Вы действительно хотите позвонить " + 
                               (contactName != null ? contactName : "по номеру " + phoneNumber) + "?";
        
        showVoiceConfirmationDialog("Подтверждение звонка", confirmMessage, () -> {
            makePhoneCall(phoneNumber);
        });
    }
    
    /**
     * Обрабатывает команду отправки SMS с голосовым подтверждением
     */
    public void processSmsCommandWithConfirmation(String phoneNumber, String contactName, String message) {
        String confirmMessage = "Вы действительно хотите отправить сообщение " + 
                               (contactName != null ? contactName : "на номер " + phoneNumber) + "?";
        
        showVoiceConfirmationDialog("Подтверждение сообщения", confirmMessage, () -> {
            sendSms(phoneNumber, message);
        });
    }

    /**
     * Выполняет звонок на указанный номер телефона
     * @param phoneNumber номер телефона
     */
    private void makePhoneCall(String phoneNumber) {
        try {
            // Проверяем разрешение на звонки
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                // Запрашиваем разрешение, если его нет
                if (activity != null) {
                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CALL_PHONE}, 100);
                    commandProcessedListener.onCommandProcessed(
                        "звонок",
                        "Для совершения звонка необходимо разрешение"
                    );
                }
                return;
            }
            
            // Создаем Intent для звонка
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Запускаем звонок
            context.startActivity(callIntent);
            
            // Сообщаем пользователю
            commandProcessedListener.onCommandProcessed(
                "звонок",
                "Звоню на номер " + phoneNumber
            );
            
        } catch (Exception e) {
            Log.e(TAG, "Error making phone call: " + e.getMessage());
            commandProcessedListener.onCommandProcessed(
                "звонок",
                "Ошибка при совершении звонка: " + e.getMessage()
            );
        }
    }
    
    /**
     * Отправляет SMS на указанный номер телефона
     * @param phoneNumber номер телефона
     * @param message текст сообщения
     */
    private void sendSms(String phoneNumber, String message) {
        try {
            // Проверяем разрешение на отправку SMS
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                // Запрашиваем разрешение, если его нет
                if (activity != null) {
                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.SEND_SMS}, 101);
                    commandProcessedListener.onCommandProcessed(
                        "сообщение",
                        "Для отправки SMS необходимо разрешение"
                    );
                }
                return;
            }
            
            // Используем Intent для отправки SMS через системное приложение
            Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
            smsIntent.setData(Uri.parse("smsto:" + phoneNumber));
            smsIntent.putExtra("sms_body", message);
            smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Запускаем приложение для SMS
            context.startActivity(smsIntent);
            
            // Сообщаем пользователю
            commandProcessedListener.onCommandProcessed(
                "сообщение",
                "Отправляю сообщение на номер " + phoneNumber
            );
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending SMS: " + e.getMessage());
            commandProcessedListener.onCommandProcessed(
                "сообщение",
                "Ошибка при отправке SMS: " + e.getMessage()
            );
        }
    }

    /**
     * Извлекает номер телефона из команды
     * @param command команда пользователя
     * @return номер телефона или null, если не удалось извлечь
     */
    private String extractPhoneNumber(String command) {
        // Шаблон для номера телефона
        Pattern phoneNumberPattern = Pattern.compile(".*?(?:номер|телефон|номеру|телефону)?\\s*([+]?[0-9]{1,3}?[\\s-]?\\(?[0-9]{3}\\)?[\\s-]?[0-9]{3}[\\s-]?[0-9]{2}[\\s-]?[0-9]{2}).*", Pattern.CASE_INSENSITIVE);
        Matcher matcher = phoneNumberPattern.matcher(command);
        
        if (matcher.find()) {
            return matcher.group(1).replaceAll("[\\s-]", "");
        }
        
        // Если не нашли номер телефона, проверяем наличие контакта
        String contactName = extractContactName(command);
        if (contactName != null) {
            // Здесь должен быть код для поиска номера телефона по имени контакта
            // Для примера просто возвращаем номер телефона
            return "+79001234567";
        }
        
        return null;
    }
    
    /**
     * Извлекает имя контакта из команды
     * @param command команда пользователя
     * @return имя контакта или null, если не удалось извлечь
     */
    private String extractContactName(String command) {
        // Шаблоны для имени контакта
        String[] contactPatterns = {
            ".*?(?:позвони|набери|вызов|звонок)\\s+(?:на)?\\s*([a-zA-Zа-яА-Я\\s]+).*",
            ".*?(?:отправь|напиши|написать|создай)\\s+(?:смс|sms|сообщение)\\s+(?:для)?\\s*([a-zA-Zа-яА-Я\\s]+).*"
        };
        
        for (String patternStr : contactPatterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(command);
            
            if (matcher.find()) {
                String contact = matcher.group(1).trim();
                
                // Исключаем фразы, которые не являются именами контактов
                if (contact.matches("(?i).*(?:на номер|по номеру|с текстом|текст).*")) {
                    continue;
                }
                
                return contact;
            }
        }
        
        return null;
    }
    
    /**
     * Извлекает текст сообщения из команды
     * @param command команда пользователя
     * @return текст сообщения или null, если не удалось извлечь
     */
    private String extractMessageText(String command) {
        // Шаблоны для текста сообщения
        String[] messagePatterns = {
            ".*?(?:с текстом|текст|содержанием|содержание)\\s+(.+)",
            ".*?(?:отправь|напиши|написать|создай)\\s+(?:смс|sms|сообщение)\\s+(?:[a-zA-Zа-яА-Я\\s0-9+]+)\\s+(?:с текстом|текст|содержанием|содержание)?\\s+(.+)"
        };
        
        for (String patternStr : messagePatterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(command);
            
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        
        return null;
    }
} 