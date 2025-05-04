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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            // Перенаправляем на экран разрешений
            activity.startActivity(new Intent(context, io.finett.myapplication.PermissionRequestActivity.class));
            return false;
        }
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
                while (cursor.moveToNext()) {
                    String contactName = cursor.getString(cursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    if (contactName != null && contactName.toLowerCase().contains(query)) {
                        String phoneNumber = cursor.getString(cursor.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                        results.add(new Contact(contactName, phoneNumber));
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return results;
    }
    
    public List<Contact> findContactsByPartialName(String partialName) {
        List<Contact> matches = new ArrayList<>();
        if (!checkContactsPermission()) {
            return matches;
        }
        
        partialName = partialName.toLowerCase().trim();
        String[] nameParts = partialName.split("\\s+");
        
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
                Map<String, Contact> uniqueContacts = new HashMap<>();
                
                while (cursor.moveToNext()) {
                    String contactName = cursor.getString(cursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    if (contactName == null) continue;
                    
                    contactName = contactName.toLowerCase();
                    boolean allPartsMatch = true;
                    
                    // Проверяем, содержит ли имя контакта все части искомого имени
                    for (String part : nameParts) {
                        if (!contactName.contains(part)) {
                            allPartsMatch = false;
                            break;
                        }
                    }
                    
                    if (allPartsMatch) {
                        String phoneNumber = cursor.getString(cursor.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                        Contact contact = new Contact(
                                cursor.getString(cursor.getColumnIndex(
                                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)),
                                phoneNumber
                        );
                        // Используем имя как ключ для уникальности
                        uniqueContacts.put(contactName, contact);
                    }
                }
                
                matches.addAll(uniqueContacts.values());
            } finally {
                cursor.close();
            }
        }
        return matches;
    }
    
    public Contact findBestMatchingContact(String partialName) {
        List<Contact> matches = findContactsByPartialName(partialName);
        if (matches.isEmpty()) {
            return null;
        }
        
        // Если есть точное совпадение, возвращаем его
        String lowercasePartialName = partialName.toLowerCase().trim();
        for (Contact contact : matches) {
            if (contact.name.toLowerCase().equals(lowercasePartialName)) {
                return contact;
            }
        }
        
        // Иначе возвращаем первое наиболее подходящее совпадение
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