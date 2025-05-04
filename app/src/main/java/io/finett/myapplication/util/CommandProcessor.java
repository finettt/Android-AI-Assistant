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
import java.util.Date;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Locale;
import io.finett.myapplication.CameraActivity;

public class CommandProcessor {
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

    public boolean processCommand(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }

        command = command.toLowerCase().trim();
        
        // Проверяем телефонные команды
        if (processPhoneCommand(command)) {
            return true;
        }
        
        // Проверяем SMS команды
        if (processSmsCommand(command)) {
            return true;
        }
        
        // Проверяем браузерные команды
        if (processBrowserCommand(command)) {
            return true;
        }
        
        // Сначала проверяем контекстные команды
        if (processContextInfoCommand(command)) {
            return true;
        }
        
        // Проверяем команды календаря
        if (processCalendarCommand(command)) {
            return true;
        }
        
        // Проверяем команды карт и навигации
        if (processMapsCommand(command)) {
            return true;
        }

        // Проверяем Wi-Fi команды
        for (String pattern : wifiPatterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(command).matches()) {
                return processWifiCommand(command);
            }
        }
        
        // Проверяем Bluetooth команды
        for (String pattern : bluetoothPatterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(command).matches()) {
                return processBluetoothCommand(command);
            }
        }

        // Проверяем Location/GPS команды
        for (String pattern : locationPatterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(command).matches()) {
                return processLocationCommand(command);
            }
        }
        
        // Проверяем команды громкости
        for (String pattern : volumePatterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(command).matches()) {
                return processVolumeCommand(command);
            }
        }
        
        // Проверяем команды настроек
        for (String pattern : settingsPatterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(command).matches()) {
                return processSettingsCommand(command);
            }
        }

        // Команды для камеры
        for (String pattern : cameraPatterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(command).matches()) {
                startCameraActivity();
                return true;
            }
        }

        return false;
    }
    
    private boolean processPhoneCommand(String command) {
        // Проверяем шаблоны для телефонных звонков
        Pattern phoneNamePattern = Pattern.compile(".*(?:позвони|набери|вызови|звонок) ([a-zA-Zа-яА-Я\\s]+).*", Pattern.CASE_INSENSITIVE);
        Pattern phoneNumberPattern = Pattern.compile(".*(?:позвони|набери|вызови|звонок) (?:на номер )?([0-9\\+]+).*", Pattern.CASE_INSENSITIVE);
        
        // Улучшенный шаблон для распознавания звонков родственникам
        Pattern phoneRelationPattern = Pattern.compile(".*(?:позвони|набери|вызови|звонок) (?:моей|моему|моим|мою|моё|моего|нашей|нашему|нашим)? ([a-zA-Zа-яА-Я\\s]+).*", Pattern.CASE_INSENSITIVE);
        
        Matcher nameMatcherPhone = phoneNamePattern.matcher(command);
        Matcher numberMatcherPhone = phoneNumberPattern.matcher(command);
        Matcher relationMatcherPhone = phoneRelationPattern.matcher(command);
        
        // Сначала проверяем номер телефона (наиболее точное совпадение)
        if (numberMatcherPhone.matches()) {
            String phoneNumber = numberMatcherPhone.group(1);
            if (communicationManager != null) {
                communicationManager.makePhoneCall(phoneNumber);
                textToSpeech.speak("Звоню на номер " + phoneNumber, TextToSpeech.QUEUE_FLUSH, null, null);
                return true;
            }
        }
        // Затем проверяем отношения/имена
        else if (nameMatcherPhone.matches() || relationMatcherPhone.matches()) {
            String contactQuery = nameMatcherPhone.matches() 
                                ? nameMatcherPhone.group(1) 
                                : relationMatcherPhone.group(1);
                                
            contactQuery = contactQuery.trim();
            
            if (communicationManager != null) {
                communicationManager.makePhoneCall(contactQuery);
                
                // Если это отношение, которое мы уже знаем, используем его в сообщении
                String relation = communicationManager.getContactNlpProcessor().extractRelationFromText(contactQuery);
                if (relation != null && communicationManager.hasRelationMapping(relation)) {
                    textToSpeech.speak("Звоню " + relation, TextToSpeech.QUEUE_FLUSH, null, null);
                } else {
                    textToSpeech.speak("Набираю " + contactQuery, TextToSpeech.QUEUE_FLUSH, null, null);
                }
                return true;
            }
        }
        
        return false;
    }
    
    private boolean processSmsCommand(String command) {
        // Проверяем шаблоны для SMS сообщений
        Pattern smsNamePattern = Pattern.compile(".*(?:отправь|напиши|написать|создай) (?:смс|sms|сообщение) ([a-zA-Zа-яА-Я\\s]+) (?:с текстом|текст) (.+).*", Pattern.CASE_INSENSITIVE);
        Pattern smsNumberPattern = Pattern.compile(".*(?:отправь|напиши|написать|создай) (?:смс|sms|сообщение) ([0-9\\+]+) (?:с текстом|текст) (.+).*", Pattern.CASE_INSENSITIVE);
        
        // Улучшенный шаблон для распознавания SMS родственникам
        Pattern smsRelationPattern = Pattern.compile(".*(?:отправь|напиши|написать|создай) (?:смс|sms|сообщение) (?:моей|моему|моим|мою|моё|моего|нашей|нашему|нашим)? ([a-zA-Zа-яА-Я\\s]+) (?:с текстом|текст) (.+).*", Pattern.CASE_INSENSITIVE);
        
        Matcher nameMatcher = smsNamePattern.matcher(command);
        Matcher numberMatcher = smsNumberPattern.matcher(command);
        Matcher relationMatcher = smsRelationPattern.matcher(command);
        
        // Сначала проверяем номер телефона (наиболее точное совпадение)
        if (numberMatcher.matches()) {
            String phoneNumber = numberMatcher.group(1);
            String message = numberMatcher.group(2);
            if (communicationManager != null) {
                communicationManager.sendSms(phoneNumber, message);
                textToSpeech.speak("Отправляю SMS на номер " + phoneNumber, TextToSpeech.QUEUE_FLUSH, null, null);
                return true;
            }
        }
        // Затем проверяем отношения/имена
        else if (nameMatcher.matches() || relationMatcher.matches()) {
            String contactQuery = nameMatcher.matches() 
                               ? nameMatcher.group(1) 
                               : relationMatcher.group(1);
                               
            String message = nameMatcher.matches() 
                          ? nameMatcher.group(2) 
                          : relationMatcher.group(2);
                          
            contactQuery = contactQuery.trim();
            
            if (communicationManager != null) {
                communicationManager.sendSms(contactQuery, message);
                
                // Если это отношение, которое мы уже знаем, используем его в сообщении
                String relation = communicationManager.getContactNlpProcessor().extractRelationFromText(contactQuery);
                if (relation != null && communicationManager.hasRelationMapping(relation)) {
                    textToSpeech.speak("Отправляю сообщение " + relation, TextToSpeech.QUEUE_FLUSH, null, null);
                } else {
                    textToSpeech.speak("Отправляю сообщение " + contactQuery, TextToSpeech.QUEUE_FLUSH, null, null);
                }
                return true;
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
} 