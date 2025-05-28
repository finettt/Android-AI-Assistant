package io.finett.myapplication.util;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.util.Log;

public class ContactsManager {
    private static final int PERMISSION_REQUEST_CONTACTS = 103;
    
    private final Context context;
    private final Activity activity;
    
    public static class Contact {
        public final String name;
        public final String phoneNumber;
        
        public Contact(String name, String phoneNumber) {
            this.name = name;
            this.phoneNumber = phoneNumber;
        }
    }
    
    public ContactsManager(Activity activity) {
        this.activity = activity;
        this.context = activity;
    }
    
    public boolean checkContactsPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            // Показываем сообщение пользователю
            Toast.makeText(context, "Для доступа к контактам необходимо предоставить разрешение", Toast.LENGTH_LONG).show();
            
            Log.d("ContactsManager", "Запрашиваем разрешение на доступ к контактам");
            
            // Запрашиваем разрешение напрямую
            if (activity != null) {
                ActivityCompat.requestPermissions(
                    activity, 
                    new String[]{Manifest.permission.READ_CONTACTS}, 
                    PERMISSION_REQUEST_CONTACTS
                );
            } else {
                // Если activity недоступно, перенаправляем на экран разрешений
                try {
                    Intent intent = new Intent(context, io.finett.myapplication.PermissionRequestActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Log.d("ContactsManager", "Перенаправляем на PermissionRequestActivity");
                } catch (Exception e) {
                    Log.e("ContactsManager", "Ошибка при запуске PermissionRequestActivity: " + e.getMessage());
                }
            }
            return false;
        }
        
        Log.d("ContactsManager", "Разрешение на доступ к контактам уже предоставлено");
        return true;
    }
    
    public Contact findContactByName(String name) {
        if (!checkContactsPermission()) {
            return null;
        }
        
        name = name.toLowerCase();
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                },
                null,
                null,
                null
        );
        
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String contactName = cursor.getString(cursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    if (contactName != null && contactName.toLowerCase().contains(name)) {
                        String phoneNumber = cursor.getString(cursor.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                        return new Contact(contactName, phoneNumber);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }
    
    public List<Contact> searchContacts(String query) {
        List<Contact> results = new ArrayList<>();
        if (!checkContactsPermission()) {
            Log.d("ContactsManager", "Нет разрешения на доступ к контактам");
            return results;
        }
        
        query = query.toLowerCase();
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                },
                null,
                null,
                null
        );
        
        if (cursor != null) {
            try {
                Log.d("ContactsManager", "Найдено контактов: " + cursor.getCount());
                int count = 0;
                while (cursor.moveToNext()) {
                    String contactName = cursor.getString(cursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    if (contactName != null && contactName.toLowerCase().contains(query)) {
                        String phoneNumber = cursor.getString(cursor.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                        results.add(new Contact(contactName, phoneNumber));
                        count++;
                    }
                }
                Log.d("ContactsManager", "Добавлено контактов в результаты: " + count);
            } finally {
                cursor.close();
            }
        } else {
            Log.d("ContactsManager", "Cursor is null, не удалось получить контакты");
        }
        return results;
    }
    
    public List<Contact> findContactsByPartialName(String partialName) {
        List<Contact> matches = new ArrayList<>();
        if (!checkContactsPermission()) {
            Log.d("ContactsManager", "Нет разрешения на доступ к контактам");
            return matches;
        }
        
        partialName = partialName.toLowerCase().trim();
        String[] nameParts = partialName.split("\\s+");
        
        Log.d("ContactsManager", "Поиск контактов по имени: " + partialName);
        
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                },
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        );
        
        if (cursor != null) {
            try {
                Log.d("ContactsManager", "Найдено контактов: " + cursor.getCount());
                Map<String, ContactMatch> uniqueContacts = new HashMap<>();
                
                while (cursor.moveToNext()) {
                    String contactName = cursor.getString(cursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    if (contactName == null) continue;
                    
                    String contactNameLower = contactName.toLowerCase();
                    double matchScore = calculateMatchScore(contactNameLower, partialName, nameParts);
                    
                    // Если есть хоть какое-то совпадение
                    if (matchScore > 0) {
                        String phoneNumber = cursor.getString(cursor.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                        Contact contact = new Contact(contactName, phoneNumber);
                        
                        // Сохраняем контакт и его оценку совпадения
                        uniqueContacts.put(contactNameLower, new ContactMatch(contact, matchScore));
                        Log.d("ContactsManager", "Найден контакт: " + contactName + " с оценкой: " + matchScore);
                    }
                }
                
                // Сортируем контакты по оценке совпадения (от высокой к низкой)
                List<ContactMatch> sortedMatches = new ArrayList<>(uniqueContacts.values());
                Collections.sort(sortedMatches, (a, b) -> Double.compare(b.score, a.score));
                
                // Извлекаем только контакты из отсортированного списка
                for (ContactMatch match : sortedMatches) {
                    matches.add(match.contact);
                }
                
                Log.d("ContactsManager", "Всего найдено подходящих контактов: " + matches.size());
            } finally {
                cursor.close();
            }
        } else {
            Log.d("ContactsManager", "Cursor is null, не удалось получить контакты");
        }
        return matches;
    }
    
    /**
     * Вспомогательный класс для хранения контакта и оценки совпадения
     */
    private static class ContactMatch {
        final Contact contact;
        final double score;
        
        ContactMatch(Contact contact, double score) {
            this.contact = contact;
            this.score = score;
        }
    }
    
    /**
     * Рассчитывает оценку совпадения имени контакта с поисковым запросом
     */
    private double calculateMatchScore(String contactName, String query, String[] queryParts) {
        double score = 0;
        
        // Проверяем точное совпадение (максимальный приоритет)
        if (contactName.equals(query)) {
            return 1.0;
        }
        
        // Проверяем, начинается ли имя контакта с запроса
        if (contactName.startsWith(query)) {
            score += 0.8;
        }
        // Проверяем, содержит ли имя контакта запрос
        else if (contactName.contains(query)) {
            score += 0.6;
        }
        
        // Проверяем совпадение отдельных частей запроса
        int matchedParts = 0;
        for (String part : queryParts) {
            if (contactName.contains(part)) {
                matchedParts++;
                
                // Дополнительные баллы, если часть находится в начале слова
                String[] contactParts = contactName.split("\\s+");
                for (String contactPart : contactParts) {
                    if (contactPart.startsWith(part)) {
                        score += 0.1;
                        break;
                    }
                }
            }
        }
        
        // Добавляем баллы за количество совпавших частей
        if (queryParts.length > 0) {
            score += 0.3 * ((double) matchedParts / queryParts.length);
        }
        
        // Если запрос короткий (1-2 символа), снижаем оценку для избежания ложных совпадений
        if (query.length() <= 2 && score < 0.8) {
            score *= 0.7;
        }
        
        return Math.min(1.0, score); // Максимальная оценка - 1.0
    }
    
    public Contact findBestMatchingContact(String partialName) {
        List<Contact> matches = findContactsByPartialName(partialName);
        if (matches.isEmpty()) {
            return null;
        }
        
        // Возвращаем первый контакт из отсортированного списка (с наивысшей оценкой)
        return matches.get(0);
    }
    
    public static boolean handlePermissionResult(int requestCode, String[] permissions,
                                               int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CONTACTS) {
            return grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
        return false;
    }
} 