package io.finett.myapplication.util;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CalendarManager {
    private static final String TAG = "CalendarManager";
    private static final int CALENDAR_PERMISSION_REQUEST_CODE = 127;
    
    private final Context context;
    private final Activity activity;
    
    public CalendarManager(Activity activity) {
        this.context = activity.getApplicationContext();
        this.activity = activity;
    }
    
    /**
     * Проверяет наличие разрешений на работу с календарем
     */
    public boolean checkCalendarPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR},
                    CALENDAR_PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }
    
    /**
     * Получает события календаря на сегодня
     */
    public List<String> getTodayEvents() {
        return getEvents(new Date(), DateUtils.DAY_IN_MILLIS);
    }
    
    /**
     * Получает события календаря на ближайшие дни
     */
    public List<String> getUpcomingEvents(int days) {
        return getEvents(new Date(), DateUtils.DAY_IN_MILLIS * days);
    }
    
    /**
     * Получает события календаря на указанную дату
     */
    public List<String> getEventsForDate(Date date) {
        Calendar startTime = Calendar.getInstance();
        startTime.setTime(date);
        startTime.set(Calendar.HOUR_OF_DAY, 0);
        startTime.set(Calendar.MINUTE, 0);
        startTime.set(Calendar.SECOND, 0);
        
        Calendar endTime = Calendar.getInstance();
        endTime.setTime(date);
        endTime.set(Calendar.HOUR_OF_DAY, 23);
        endTime.set(Calendar.MINUTE, 59);
        endTime.set(Calendar.SECOND, 59);
        
        return getEvents(startTime.getTime(), endTime.getTimeInMillis() - startTime.getTimeInMillis());
    }
    
    /**
     * Получает список событий из календаря
     */
    private List<String> getEvents(Date startDate, long durationMillis) {
        List<String> events = new ArrayList<>();
        
        if (!checkCalendarPermission()) {
            return events;
        }
        
        long startMillis = startDate.getTime();
        long endMillis = startMillis + durationMillis;
        
        String[] projection = new String[]{
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION
        };
        
        String selection = CalendarContract.Events.DTSTART + " >= ? AND " +
                CalendarContract.Events.DTSTART + " <= ?";
        String[] selectionArgs = new String[]{
                String.valueOf(startMillis),
                String.valueOf(endMillis)
        };
        
        try {
            ContentResolver cr = context.getContentResolver();
            Uri uri = CalendarContract.Events.CONTENT_URI;
            
            Cursor cursor = cr.query(uri, projection, selection, selectionArgs,
                    CalendarContract.Events.DTSTART + " ASC");
            
            if (cursor != null && cursor.moveToFirst()) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
                
                do {
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE));
                    long start = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART));
                    String location = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION));
                    
                    StringBuilder eventInfo = new StringBuilder();
                    eventInfo.append(dateFormat.format(new Date(start)))
                           .append(" - ")
                           .append(title);
                    
                    if (!TextUtils.isEmpty(location)) {
                        eventInfo.append(", место: ").append(location);
                    }
                    
                    events.add(eventInfo.toString());
                } while (cursor.moveToNext());
                
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting calendar events: " + e.getMessage());
        }
        
        return events;
    }
    
    /**
     * Создает новое событие в календаре
     */
    public boolean createEvent(String title, String description, Date startDate, Date endDate, String location) {
        if (!checkCalendarPermission()) {
            return false;
        }
        
        long startMillis = startDate.getTime();
        long endMillis = endDate.getTime();
        
        ContentResolver cr = context.getContentResolver();
        ContentValues values = new ContentValues();
        
        values.put(CalendarContract.Events.DTSTART, startMillis);
        values.put(CalendarContract.Events.DTEND, endMillis);
        values.put(CalendarContract.Events.TITLE, title);
        values.put(CalendarContract.Events.DESCRIPTION, description);
        values.put(CalendarContract.Events.CALENDAR_ID, getDefaultCalendarId());
        values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        
        if (!TextUtils.isEmpty(location)) {
            values.put(CalendarContract.Events.EVENT_LOCATION, location);
        }
        
        try {
            Uri uri = cr.insert(CalendarContract.Events.CONTENT_URI, values);
            return uri != null;
        } catch (Exception e) {
            Log.e(TAG, "Error creating calendar event: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Открывает календарь без указания даты
     */
    public void openCalendar() {
        Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
        Intent intent = new Intent(Intent.ACTION_VIEW, builder.build());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
    
    /**
     * Открывает календарь для просмотра конкретной даты
     */
    public void openCalendarForDate(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        
        Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
        builder.appendPath("time");
        ContentUris.appendId(builder, calendar.getTimeInMillis());
        Intent intent = new Intent(Intent.ACTION_VIEW, builder.build());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
    
    /**
     * Открывает создание нового события в календаре с указанием параметров
     */
    public void openNewEventForm(String title, Date startDate, Date endDate) {
        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startDate.getTime())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endDate.getTime());
        
        activity.startActivity(intent);
    }
    
    /**
     * Открывает создание нового события в календаре без указания параметров
     */
    public void openNewEventForm() {
        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI);
        
        activity.startActivity(intent);
    }
    
    /**
     * Получает ID календаря по умолчанию
     */
    private long getDefaultCalendarId() {
        long calendarId = 1; // ID по умолчанию, обычно 1 это основной календарь
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return calendarId;
        }
        
        String[] projection = new String[]{CalendarContract.Calendars._ID};
        String selection = CalendarContract.Calendars.VISIBLE + " = ? AND " +
                CalendarContract.Calendars.IS_PRIMARY + " = ?";
        String[] selectionArgs = new String[]{"1", "1"};
        
        try {
            Uri uri = CalendarContract.Calendars.CONTENT_URI;
            Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                calendarId = cursor.getLong(0);
                cursor.close();
                return calendarId;
            }
            
            // Если не нашли основной календарь, берем первый доступный
            cursor = context.getContentResolver().query(uri, projection, 
                    CalendarContract.Calendars.VISIBLE + " = 1", null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                calendarId = cursor.getLong(0);
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting default calendar ID: " + e.getMessage());
        }
        
        return calendarId;
    }
    
    /**
     * Парсит дату из текста команды
     */
    public Date parseDateFromText(String text) {
        Calendar calendar = Calendar.getInstance();
        
        // Проверка на "сегодня", "завтра", "послезавтра"
        if (text.toLowerCase().contains("сегодня")) {
            return calendar.getTime();
        } else if (text.toLowerCase().contains("завтра")) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            return calendar.getTime();
        } else if (text.toLowerCase().contains("послезавтра")) {
            calendar.add(Calendar.DAY_OF_YEAR, 2);
            return calendar.getTime();
        }
        
        // Проверка на дни недели
        String[] daysOfWeek = {"понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"};
        for (int i = 0; i < daysOfWeek.length; i++) {
            if (text.toLowerCase().contains(daysOfWeek[i])) {
                int currentDay = calendar.get(Calendar.DAY_OF_WEEK);
                int targetDay = i + 2; // В Calendar понедельник = 2, воскресенье = 1
                
                if (targetDay == 8) targetDay = 1; // Для воскресенья
                
                int daysToAdd = (targetDay - currentDay + 7) % 7;
                if (daysToAdd == 0) daysToAdd = 7; // Если сегодня этот день, то берем следующую неделю
                
                calendar.add(Calendar.DAY_OF_YEAR, daysToAdd);
                return calendar.getTime();
            }
        }
        
        // Проверка на конкретную дату (например, "5 мая")
        Pattern datePattern = Pattern.compile("(\\d{1,2})\\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)");
        Matcher matcher = datePattern.matcher(text.toLowerCase());
        
        if (matcher.find()) {
            int day = Integer.parseInt(matcher.group(1));
            String month = matcher.group(2);
            
            String[] months = {"января", "февраля", "марта", "апреля", "мая", "июня", 
                              "июля", "августа", "сентября", "октября", "ноября", "декабря"};
            
            int monthNum = -1;
            for (int i = 0; i < months.length; i++) {
                if (months[i].equals(month)) {
                    monthNum = i;
                    break;
                }
            }
            
            if (monthNum != -1 && day > 0 && day <= 31) {
                calendar.set(Calendar.DAY_OF_MONTH, day);
                calendar.set(Calendar.MONTH, monthNum);
                
                // Если дата уже прошла, берем следующий год
                if (calendar.getTime().before(new Date())) {
                    calendar.add(Calendar.YEAR, 1);
                }
                
                return calendar.getTime();
            }
        }
        
        // Если не смогли распознать дату, возвращаем текущую
        return calendar.getTime();
    }
    
    /**
     * Парсит время из текста команды
     */
    public Date parseTimeFromText(String text, Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        
        // Шаблон для времени в формате "ЧЧ:ММ" или "Ч:ММ"
        Pattern timePattern = Pattern.compile("(\\d{1,2}):(\\d{2})");
        Matcher matcher = timePattern.matcher(text);
        
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = Integer.parseInt(matcher.group(2));
            
            if (hour >= 0 && hour < 24 && minute >= 0 && minute < 60) {
                calendar.set(Calendar.HOUR_OF_DAY, hour);
                calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);
                return calendar.getTime();
            }
        }
        
        // Шаблон для времени в формате "в N часов"
        Pattern hourPattern = Pattern.compile("в\\s+(\\d{1,2})\\s+час");
        matcher = hourPattern.matcher(text.toLowerCase());
        
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            
            if (hour >= 0 && hour < 24) {
                calendar.set(Calendar.HOUR_OF_DAY, hour);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                return calendar.getTime();
            }
        }
        
        // Если не смогли распознать время, устанавливаем время по умолчанию (12:00)
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        
        return calendar.getTime();
    }
} 