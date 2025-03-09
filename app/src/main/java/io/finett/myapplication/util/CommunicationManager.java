package io.finett.myapplication.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.telephony.SmsManager;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class CommunicationManager {
    private static final int PERMISSION_REQUEST_CALL = 101;
    private static final int PERMISSION_REQUEST_SMS = 102;
    
    private final Context context;
    private final Activity activity;
    private final ContactsManager contactsManager;
    
    public CommunicationManager(Activity activity) {
        this.activity = activity;
        this.context = activity;
        this.contactsManager = new ContactsManager(activity);
    }
    
    public boolean checkCallPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.CALL_PHONE},
                    PERMISSION_REQUEST_CALL);
            return false;
        }
        return true;
    }
    
    public boolean checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.SEND_SMS},
                    PERMISSION_REQUEST_SMS);
            return false;
        }
        return true;
    }
    
    public void makePhoneCall(String nameOrNumber) {
        if (!checkCallPermission()) {
            return;
        }
        
        String displayName = nameOrNumber;
        String phoneNumber = nameOrNumber;
        
        // Пробуем найти контакт по имени
        ContactsManager.Contact contact = contactsManager.findContactByName(nameOrNumber);
        if (contact != null) {
            displayName = contact.name;
            phoneNumber = contact.phoneNumber;
        }
        
        final String finalPhoneNumber = phoneNumber;
        new AlertDialog.Builder(activity)
                .setTitle("Подтверждение звонка")
                .setMessage("Вы действительно хотите позвонить " + 
                        (contact != null ? contact.name + " (" + phoneNumber + ")" : "на номер " + phoneNumber) + "?")
                .setPositiveButton("Да", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_CALL);
                        intent.setData(Uri.parse("tel:" + finalPhoneNumber));
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(context, "Не удалось совершить звонок", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Нет", null)
                .show();
    }
    
    public void sendSms(String nameOrNumber, String message) {
        if (!checkSmsPermission()) {
            return;
        }
        
        String displayName = nameOrNumber;
        String phoneNumber = nameOrNumber;
        
        // Пробуем найти контакт по имени
        ContactsManager.Contact contact = contactsManager.findContactByName(nameOrNumber);
        if (contact != null) {
            displayName = contact.name;
            phoneNumber = contact.phoneNumber;
        }
        
        final String finalPhoneNumber = phoneNumber;
        new AlertDialog.Builder(activity)
                .setTitle("Подтверждение отправки SMS")
                .setMessage("Отправить сообщение " + 
                        (contact != null ? contact.name + " (" + phoneNumber + ")" : "на номер " + phoneNumber) +
                        "?\n\nТекст: " + message)
                .setPositiveButton("Да", (dialog, which) -> {
                    try {
                        SmsManager smsManager = SmsManager.getDefault();
                        smsManager.sendTextMessage(finalPhoneNumber, null, message, null, null);
                        Toast.makeText(context, "SMS отправлено", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(context, "Не удалось отправить SMS", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Нет", null)
                .show();
    }
    
    public static boolean handlePermissionResult(int requestCode, String[] permissions,
                                               int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CALL || requestCode == PERMISSION_REQUEST_SMS) {
            return grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
        return ContactsManager.handlePermissionResult(requestCode, permissions, grantResults);
    }
} 