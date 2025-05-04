package io.finett.myapplication.util;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.finett.myapplication.R;

/**
 * Активность для управления связями между отношениями и контактами
 */
public class ContactRelationActivity extends AppCompatActivity {
    
    private ContactNlpProcessor contactNlpProcessor;
    private ContactsManager contactsManager;
    private ListView relationListView;
    private Button addRelationButton;
    private RelationAdapter adapter;
    private List<RelationItem> relationItems;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_relation);
        
        // Инициализация менеджеров
        contactsManager = new ContactsManager(this);
        contactNlpProcessor = new ContactNlpProcessor(this, contactsManager);
        
        // Инициализация UI
        relationListView = findViewById(R.id.relation_list);
        addRelationButton = findViewById(R.id.add_relation_button);
        
        // Настройка ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Управление отношениями");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        // Загрузка списка отношений
        loadRelations();
        
        // Настройка кнопки добавления
        addRelationButton.setOnClickListener(v -> showAddRelationDialog());
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Загружает список отношений и их связи с контактами
     */
    private void loadRelations() {
        relationItems = new ArrayList<>();
        
        // Получаем все известные отношения
        Set<String> relations = contactNlpProcessor.getAllKnownRelations();
        
        // Для каждого отношения пытаемся найти связанный контакт
        for (String relation : relations) {
            String contactName = "";
            ContactsManager.Contact contact = contactNlpProcessor.findContactByRelationOrName(relation);
            if (contact != null) {
                contactName = contact.name;
            }
            
            relationItems.add(new RelationItem(relation, contactName));
        }
        
        // Создаем и устанавливаем адаптер
        adapter = new RelationAdapter(this, relationItems);
        relationListView.setAdapter(adapter);
        
        // Настраиваем обработчик нажатия на элемент списка
        relationListView.setOnItemClickListener((parent, view, position, id) -> {
            RelationItem item = relationItems.get(position);
            showRelationOptionsDialog(item);
        });
    }
    
    /**
     * Показывает диалог для добавления нового отношения
     */
    private void showAddRelationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_relation, null);
        
        EditText relationEditText = dialogView.findViewById(R.id.relation_edit_text);
        
        builder.setTitle("Добавить новое отношение")
                .setView(dialogView)
                .setPositiveButton("Добавить", (dialog, which) -> {
                    String relation = relationEditText.getText().toString().trim();
                    if (!TextUtils.isEmpty(relation)) {
                        // Показываем диалог выбора контакта
                        showContactSelectionForRelation(relation);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
    
    /**
     * Показывает диалог выбора контакта для отношения
     */
    private void showContactSelectionForRelation(String relation) {
        // Получаем список всех контактов
        List<ContactsManager.Contact> contacts = contactsManager.searchContacts("");
        
        if (contacts.isEmpty()) {
            Toast.makeText(this, "Нет доступных контактов", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Создаем список имен для отображения
        String[] contactNames = new String[contacts.size()];
        for (int i = 0; i < contacts.size(); i++) {
            ContactsManager.Contact contact = contacts.get(i);
            contactNames[i] = contact.name + " (" + contact.phoneNumber + ")";
        }
        
        // Показываем диалог выбора
        new AlertDialog.Builder(this)
                .setTitle("Выберите контакт для '" + relation + "'")
                .setItems(contactNames, (dialog, which) -> {
                    ContactsManager.Contact selectedContact = contacts.get(which);
                    
                    // Добавляем связь
                    contactNlpProcessor.addRelationMapping(relation, selectedContact.name);
                    
                    // Обновляем список
                    relationItems.add(new RelationItem(relation, selectedContact.name));
                    adapter.notifyDataSetChanged();
                    
                    Toast.makeText(this, "Отношение добавлено", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
    
    /**
     * Показывает диалог с опциями для существующего отношения
     */
    private void showRelationOptionsDialog(RelationItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Отношение: " + item.relation)
                .setItems(new String[]{"Изменить связь", "Удалить отношение"}, (dialog, which) -> {
                    switch (which) {
                        case 0: // Изменить связь
                            showContactSelectionForRelation(item.relation);
                            break;
                        case 1: // Удалить отношение
                            contactNlpProcessor.removeRelationMapping(item.relation);
                            relationItems.remove(item);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(this, "Отношение удалено", Toast.LENGTH_SHORT).show();
                            break;
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
    
    /**
     * Модель данных для элемента списка отношений
     */
    private static class RelationItem {
        String relation;
        String contactName;
        
        RelationItem(String relation, String contactName) {
            this.relation = relation;
            this.contactName = contactName;
        }
    }
    
    /**
     * Адаптер для списка отношений
     */
    private static class RelationAdapter extends ArrayAdapter<RelationItem> {
        
        private final LayoutInflater inflater;
        
        public RelationAdapter(Context context, List<RelationItem> items) {
            super(context, 0, items);
            this.inflater = LayoutInflater.from(context);
        }
        
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            ViewHolder holder;
            
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_relation, parent, false);
                holder = new ViewHolder();
                holder.relationText = convertView.findViewById(R.id.relation_text);
                holder.contactText = convertView.findViewById(R.id.contact_text);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            
            RelationItem item = getItem(position);
            if (item != null) {
                holder.relationText.setText(item.relation);
                holder.contactText.setText(TextUtils.isEmpty(item.contactName) ? 
                        "Нет связанного контакта" : item.contactName);
            }
            
            return convertView;
        }
        
        private static class ViewHolder {
            TextView relationText;
            TextView contactText;
        }
    }
} 