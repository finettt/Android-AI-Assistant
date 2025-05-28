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
import android.graphics.Color;
import io.finett.myapplication.R;

public class CommunicationManager {
    private static final int PERMISSION_REQUEST_CALL = 101;
    private static final int PERMISSION_REQUEST_SMS = 102;
    
    private final Context context;
    private final Activity activity;
    private final ContactsManager contactsManager;
    private final ContactNlpProcessor contactNlpProcessor;
    
    public CommunicationManager(Activity activity) {
        this.activity = activity;
        this.context = activity;
        this.contactsManager = new ContactsManager(activity);
        
        // Проверяем разрешение на доступ к контактам
        boolean hasContactsPermission = ContextCompat.checkSelfPermission(context, 
                Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        
        if (!hasContactsPermission) {
            android.util.Log.d("CommunicationManager", "Нет разрешения на доступ к контактам при инициализации");
            // Запрашиваем разрешение
            ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.READ_CONTACTS},
                103 // ContactsManager.PERMISSION_REQUEST_CONTACTS
            );
        } else {
            android.util.Log.d("CommunicationManager", "Разрешение на доступ к контактам предоставлено при инициализации");
        }
        
        this.contactNlpProcessor = new ContactNlpProcessor(context, contactsManager);
    }
    
    public boolean checkCallPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            // Перенаправляем на экран разрешений
            activity.startActivity(new Intent(context, io.finett.myapplication.PermissionRequestActivity.class));
            return false;
        }
        return true;
    }
    
    public boolean checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            // Перенаправляем на экран разрешений
            activity.startActivity(new Intent(context, io.finett.myapplication.PermissionRequestActivity.class));
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
        
        // Используем NLP поиск для нахождения контакта
        List<ContactsManager.Contact> contacts = contactNlpProcessor.findContactsByRelationOrName(nameOrNumber);
        
        if (contacts.isEmpty()) {
            Toast.makeText(context, "Контакт не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (contacts.size() == 1) {
            showCallConfirmation(contacts.get(0), contacts.get(0).phoneNumber);
        } else {
            showContactSelectionDialog(contacts, contact -> {
                // Запоминаем выбор пользователя для будущих поисков
                String relation = contactNlpProcessor.extractRelationFromText(nameOrNumber);
                if (relation != null) {
                    contactNlpProcessor.addRelationMapping(relation, contact.name);
                }
                showCallConfirmation(contact, contact.phoneNumber);
            });
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
        
        // Используем NLP поиск для нахождения контакта
        List<ContactsManager.Contact> contacts = contactNlpProcessor.findContactsByRelationOrName(nameOrNumber);
        
        if (contacts.isEmpty()) {
            Toast.makeText(context, "Контакт не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (contacts.size() == 1) {
            showSmsConfirmation(contacts.get(0), contacts.get(0).phoneNumber, message);
        } else {
            showContactSelectionDialog(contacts, contact -> {
                // Запоминаем выбор пользователя для будущих поисков
                String relation = contactNlpProcessor.extractRelationFromText(nameOrNumber);
                if (relation != null) {
                    contactNlpProcessor.addRelationMapping(relation, contact.name);
                }
                showSmsConfirmation(contact, contact.phoneNumber, message);
            });
        }
    }
    
    /**
     * Отображает диалог для связывания отношения с контактом
     */
    public void showRelationMappingDialog(String relation, List<ContactsManager.Contact> contacts) {
        if (contacts.isEmpty()) {
            Toast.makeText(context, "Нет контактов для связывания", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] items = new String[contacts.size()];
        for (int i = 0; i < contacts.size(); i++) {
            ContactsManager.Contact contact = contacts.get(i);
            items[i] = contact.name + " (" + contact.phoneNumber + ")";
        }
        
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Связать '" + relation + "' с контактом")
                .setItems(items, (d, which) -> {
                    ContactsManager.Contact contact = contacts.get(which);
                    contactNlpProcessor.addRelationMapping(relation, contact.name);
                    Toast.makeText(context, "Связь успешно создана", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .create();
        
        // Установка белого цвета для кнопок после показа диалога
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
        });
        
        dialog.show();
    }
    
    private void showContactSelectionDialog(List<ContactsManager.Contact> contacts, ContactSelectedCallback callback) {
        String[] items = new String[contacts.size()];
        for (int i = 0; i < contacts.size(); i++) {
            ContactsManager.Contact contact = contacts.get(i);
            items[i] = contact.name + " (" + contact.phoneNumber + ")";
        }
        
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Выберите контакт")
                .setItems(items, (d, which) -> callback.onContactSelected(contacts.get(which)))
                .setNegativeButton("Отмена", null)
                .create();
        
        // Установка белого цвета для кнопок после показа диалога
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
        });
        
        dialog.show();
    }
    
    private void showCallConfirmation(ContactsManager.Contact contact, String phoneNumber) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Подтверждение звонка")
                .setMessage("Вы действительно хотите позвонить " + 
                        (contact != null ? contact.name + " на номер " + phoneNumber : "на номер " + phoneNumber) + "?")
                .setPositiveButton("Да", (d, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_CALL);
                        intent.setData(Uri.parse("tel:" + phoneNumber));
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(context, "Не удалось совершить звонок", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Нет", null)
                .create();
        
        // Установка белого цвета для кнопок после показа диалога
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
        });
        
        dialog.show();
    }
    
    private void showSmsConfirmation(ContactsManager.Contact contact, String phoneNumber, String message) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Подтверждение отправки SMS")
                .setMessage("Отправить сообщение " + 
                        (contact != null ? contact.name + " на номер " + phoneNumber : "на номер " + phoneNumber) +
                        "?\n\nТекст: " + message)
                .setPositiveButton("Да", (d, which) -> {
                    try {
                        SmsManager smsManager = SmsManager.getDefault();
                        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                        Toast.makeText(context, "SMS отправлено", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(context, "Не удалось отправить SMS", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Нет", null)
                .create();
        
        // Установка белого цвета для кнопок после показа диалога
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
        });
        
        dialog.show();
    }
    
    /**
     * Проверяет наличие связи между отношением и контактом
     */
    public boolean hasRelationMapping(String relation) {
        return contactNlpProcessor != null && 
               contactNlpProcessor.extractRelationFromText(relation) != null;
    }
    
    /**
     * Получить NLP-процессор контактов
     */
    public ContactNlpProcessor getContactNlpProcessor() {
        return contactNlpProcessor;
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