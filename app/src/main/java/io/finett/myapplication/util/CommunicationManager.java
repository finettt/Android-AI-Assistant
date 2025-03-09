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
import java.util.List;

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
        
        // Если это номер телефона, звоним сразу
        if (nameOrNumber.matches("\\+?\\d+")) {
            showCallConfirmation(null, nameOrNumber);
            return;
        }
        
        // Ищем контакты по имени
        List<ContactsManager.Contact> contacts = contactsManager.findContactsByPartialName(nameOrNumber);
        
        if (contacts.isEmpty()) {
            Toast.makeText(context, "Контакт не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (contacts.size() == 1) {
            showCallConfirmation(contacts.get(0), contacts.get(0).phoneNumber);
        } else {
            showContactSelectionDialog(contacts, this::showCallConfirmation);
        }
    }
    
    public void sendSms(String nameOrNumber, String message) {
        if (!checkSmsPermission()) {
            return;
        }
        
        // Если это номер телефона, отправляем сразу
        if (nameOrNumber.matches("\\+?\\d+")) {
            showSmsConfirmation(null, nameOrNumber, message);
            return;
        }
        
        // Ищем контакты по имени
        List<ContactsManager.Contact> contacts = contactsManager.findContactsByPartialName(nameOrNumber);
        
        if (contacts.isEmpty()) {
            Toast.makeText(context, "Контакт не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (contacts.size() == 1) {
            showSmsConfirmation(contacts.get(0), contacts.get(0).phoneNumber, message);
        } else {
            showContactSelectionDialog(contacts, contact -> 
                    showSmsConfirmation(contact, contact.phoneNumber, message));
        }
    }
    
    private void showContactSelectionDialog(List<ContactsManager.Contact> contacts, ContactSelectedCallback callback) {
        String[] items = new String[contacts.size()];
        for (int i = 0; i < contacts.size(); i++) {
            ContactsManager.Contact contact = contacts.get(i);
            items[i] = contact.name + " (" + contact.phoneNumber + ")";
        }
        
        new AlertDialog.Builder(activity)
                .setTitle("Выберите контакт")
                .setItems(items, (dialog, which) -> callback.onContactSelected(contacts.get(which)))
                .setNegativeButton("Отмена", null)
                .show();
    }
    
    private void showCallConfirmation(ContactsManager.Contact contact, String phoneNumber) {
        new AlertDialog.Builder(activity)
                .setTitle("Подтверждение звонка")
                .setMessage("Вы действительно хотите позвонить " + 
                        (contact != null ? contact.name + " (" + phoneNumber + ")" : "на номер " + phoneNumber) + "?")
                .setPositiveButton("Да", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_CALL);
                        intent.setData(Uri.parse("tel:" + phoneNumber));
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(context, "Не удалось совершить звонок", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Нет", null)
                .show();
    }
    
    private void showSmsConfirmation(ContactsManager.Contact contact, String phoneNumber, String message) {
        new AlertDialog.Builder(activity)
                .setTitle("Подтверждение отправки SMS")
                .setMessage("Отправить сообщение " + 
                        (contact != null ? contact.name + " (" + phoneNumber + ")" : "на номер " + phoneNumber) +
                        "?\n\nТекст: " + message)
                .setPositiveButton("Да", (dialog, which) -> {
                    try {
                        SmsManager smsManager = SmsManager.getDefault();
                        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                        Toast.makeText(context, "SMS отправлено", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(context, "Не удалось отправить SMS", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Нет", null)
                .show();
    }
    
    private interface ContactSelectedCallback {
        void onContactSelected(ContactsManager.Contact contact);
    }
    
    public static boolean handlePermissionResult(int requestCode, String[] permissions,
                                               int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CALL || requestCode == PERMISSION_REQUEST_SMS) {
            return grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
        return ContactsManager.handlePermissionResult(requestCode, permissions, grantResults);
    }
} 