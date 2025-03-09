package io.finett.myapplication.util;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

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
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    PERMISSION_REQUEST_CONTACTS);
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
    
    public static boolean handlePermissionResult(int requestCode, String[] permissions,
                                               int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CONTACTS) {
            return grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
        return false;
    }
} 