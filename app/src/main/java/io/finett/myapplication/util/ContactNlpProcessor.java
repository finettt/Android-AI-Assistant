package io.finett.myapplication.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Класс для обработки запросов к контактам с использованием NLP подхода
 */
public class ContactNlpProcessor {
    private static final String CONTACT_PREFS = "contact_nlp_prefs";
    private static final String RELATION_MAPPING_KEY = "relation_mapping";
    private static final String USAGE_COUNT_KEY = "usage_count";
    private static final String NAME_VARIANTS_KEY = "name_variants";
    
    private final Context context;
    private final ContactsManager contactsManager;
    private Map<String, String> relationToContactMap;
    private Map<String, Integer> contactUsageCount;
    private Map<String, Set<String>> contactNameVariants; // Варианты имен для каждого контакта
    
    // Минимальный порог сходства для нечеткого поиска
    private static final double MIN_SIMILARITY_THRESHOLD = 0.7;
    
    // Список родственных отношений на русском языке
    private static final List<String> KNOWN_RELATIONS = Arrays.asList(
            "мама", "маме", "мамой", "папа", "папе", "папой", "отец", "отцу", "отцом",
            "брат", "брату", "братом", "сестра", "сестре", "сестрой", "муж", "мужу", "мужем",
            "жена", "жене", "женой", "сын", "сыну", "сыном", "дочь", "дочери", "дочерью",
            "бабушка", "бабушке", "бабушкой", "дедушка", "дедушке", "дедушкой",
            "тетя", "тете", "тетей", "дядя", "дяде", "дядей", "племянник", "племянница",
            "друг", "другу", "другом", "подруга", "подруге", "подругой", "коллега", "коллеге", "коллегой",
            "шеф", "шефу", "шефом", "босс", "боссу", "боссом", "начальник", "начальнику", "начальником",
            "доктор", "доктору", "доктором", "врач", "врачу", "врачом"
    );
    
    public ContactNlpProcessor(Context context, ContactsManager contactsManager) {
        this.context = context;
        this.contactsManager = contactsManager;
        loadRelationMappings();
        initializeContactNameVariants();
    }
    
    /**
     * Загружает сохраненные сопоставления отношений с контактами и статистику использования
     */
    private void loadRelationMappings() {
        SharedPreferences prefs = context.getSharedPreferences(CONTACT_PREFS, Context.MODE_PRIVATE);
        
        relationToContactMap = new HashMap<>();
        String mappingJson = prefs.getString(RELATION_MAPPING_KEY, "");
        if (!TextUtils.isEmpty(mappingJson)) {
            try {
                relationToContactMap = StringMapSerializer.deserialize(mappingJson);
            } catch (Exception e) {
                relationToContactMap = new HashMap<>();
            }
        }
        
        contactUsageCount = new HashMap<>();
        String usageCountJson = prefs.getString(USAGE_COUNT_KEY, "");
        if (!TextUtils.isEmpty(usageCountJson)) {
            try {
                Map<String, String> stringMap = StringMapSerializer.deserialize(usageCountJson);
                for (Map.Entry<String, String> entry : stringMap.entrySet()) {
                    try {
                        contactUsageCount.put(entry.getKey(), Integer.parseInt(entry.getValue()));
                    } catch (NumberFormatException e) {
                        contactUsageCount.put(entry.getKey(), 0);
                    }
                }
            } catch (Exception e) {
                contactUsageCount = new HashMap<>();
            }
        }
        
        // Загружаем варианты имен
        contactNameVariants = new HashMap<>();
        String nameVariantsJson = prefs.getString(NAME_VARIANTS_KEY, "");
        if (!TextUtils.isEmpty(nameVariantsJson)) {
            try {
                Map<String, String> serializedMap = StringMapSerializer.deserialize(nameVariantsJson);
                for (Map.Entry<String, String> entry : serializedMap.entrySet()) {
                    String contactName = entry.getKey();
                    String variantsStr = entry.getValue();
                    Set<String> variants = new HashSet<>(Arrays.asList(variantsStr.split("\\|")));
                    contactNameVariants.put(contactName, variants);
                }
            } catch (Exception e) {
                contactNameVariants = new HashMap<>();
            }
        }
    }
    
    /**
     * Инициализирует варианты имен для контактов
     */
    private void initializeContactNameVariants() {
        // Если варианты еще не загружены, создаем их
        if (contactNameVariants == null) {
            contactNameVariants = new HashMap<>();
        }
        
        // Получаем все контакты
        List<ContactsManager.Contact> allContacts = contactsManager.searchContacts("");
        
        Log.d("ContactNlpProcessor", "Загружено контактов для создания вариантов имен: " + allContacts.size());
        
        // Создаем варианты имен для каждого контакта, если еще не созданы
        for (ContactsManager.Contact contact : allContacts) {
            if (!contactNameVariants.containsKey(contact.name)) {
                Set<String> variants = generateNameVariants(contact.name);
                contactNameVariants.put(contact.name, variants);
                Log.d("ContactNlpProcessor", "Создано " + variants.size() + " вариантов для контакта " + contact.name);
            }
        }
        
        // Если контактов нет, но есть сохраненные варианты имен, используем их
        if (allContacts.isEmpty() && !contactNameVariants.isEmpty()) {
            Log.d("ContactNlpProcessor", "Контакты не загружены, но используем " + contactNameVariants.size() + " сохраненных вариантов имен");
        } else if (allContacts.isEmpty()) {
            Log.d("ContactNlpProcessor", "Контакты не загружены и нет сохраненных вариантов имен");
        }
        
        // Сохраняем варианты
        saveNameVariants();
    }
    
    /**
     * Генерирует варианты имен для контакта
     */
    private Set<String> generateNameVariants(String fullName) {
        Set<String> variants = new HashSet<>();
        variants.add(fullName.toLowerCase());
        
        // Разбиваем полное имя на части
        String[] parts = fullName.split("\\s+");
        
        // Добавляем каждую часть имени как отдельный вариант
        for (String part : parts) {
            variants.add(part.toLowerCase());
        }
        
        // Если имя состоит из двух или более частей, добавляем комбинации
        if (parts.length >= 2) {
            // Имя + Фамилия
            variants.add((parts[0] + " " + parts[parts.length-1]).toLowerCase());
            
            // Инициалы + Фамилия
            if (parts[0].length() > 0) {
                variants.add((parts[0].charAt(0) + ". " + parts[parts.length-1]).toLowerCase());
            }
            
            // Только имя
            variants.add(parts[0].toLowerCase());
            
            // Только фамилия
            variants.add(parts[parts.length-1].toLowerCase());
            
            // Генерируем падежные и уменьшительно-ласкательные формы для имени
            String firstName = parts[0].toLowerCase();
            generateCaseVariants(firstName, variants);
            
            // Генерируем падежные и уменьшительно-ласкательные формы для фамилии
            String lastName = parts[parts.length-1].toLowerCase();
            generateCaseVariants(lastName, variants);
        }
        
        return variants;
    }
    
    /**
     * Генерирует падежные и уменьшительно-ласкательные формы для имени
     */
    private void generateCaseVariants(String name, Set<String> variants) {
        // Правила для русских имен (расширенные)
        
        // Уменьшительно-ласкательные формы для распространенных имен
        Map<String, Set<String>> diminutives = new HashMap<>();
        diminutives.put("александр", new HashSet<>(Arrays.asList("саша", "саня", "шура", "алекс", "сашка", "сашенька")));
        diminutives.put("алексей", new HashSet<>(Arrays.asList("леша", "лёша", "лёха", "алёша", "алёха", "лёшка", "лёшенька")));
        diminutives.put("анна", new HashSet<>(Arrays.asList("аня", "анечка", "анюта", "нюша", "анька", "аннушка")));
        diminutives.put("дмитрий", new HashSet<>(Arrays.asList("дима", "димка", "димон", "митя", "димочка")));
        diminutives.put("екатерина", new HashSet<>(Arrays.asList("катя", "катюша", "катенька", "катерина", "катька")));
        diminutives.put("елена", new HashSet<>(Arrays.asList("лена", "ленка", "леночка", "еленка", "ленусик")));
        diminutives.put("иван", new HashSet<>(Arrays.asList("ваня", "ванька", "ванечка", "иванушка")));
        diminutives.put("мария", new HashSet<>(Arrays.asList("маша", "машка", "машенька", "маня", "манечка", "маруся")));
        diminutives.put("михаил", new HashSet<>(Arrays.asList("миша", "мишка", "мишаня", "мишутка", "михась")));
        diminutives.put("наталья", new HashSet<>(Arrays.asList("наташа", "ната", "натали", "наталка", "наташенька")));
        diminutives.put("николай", new HashSet<>(Arrays.asList("коля", "колька", "колян", "николаша", "николенька")));
        diminutives.put("ольга", new HashSet<>(Arrays.asList("оля", "олька", "оленька", "олечка")));
        diminutives.put("сергей", new HashSet<>(Arrays.asList("серёга", "серёжа", "серж", "серёженька", "серый")));
        diminutives.put("татьяна", new HashSet<>(Arrays.asList("таня", "танька", "танечка", "танюша")));
        
        // Добавляем уменьшительно-ласкательные формы, если имя есть в словаре
        for (Map.Entry<String, Set<String>> entry : diminutives.entrySet()) {
            if (name.equals(entry.getKey())) {
                variants.addAll(entry.getValue());
                
                // Также добавляем падежные формы для каждой уменьшительной формы
                for (String diminutive : entry.getValue()) {
                    addCaseForms(diminutive, variants);
                }
            }
        }
        
        // Добавляем падежные формы для исходного имени
        addCaseForms(name, variants);
    }
    
    /**
     * Добавляет падежные формы для имени
     */
    private void addCaseForms(String name, Set<String> variants) {
        // Правила для русских имен (расширенные)
        if (name.endsWith("й")) {
            // Илья -> Илье, Ильи, Илью, Ильёй
            variants.add(name.substring(0, name.length()-1) + "е");
            variants.add(name.substring(0, name.length()-1) + "и");
            variants.add(name.substring(0, name.length()-1) + "ю");
            variants.add(name.substring(0, name.length()-1) + "ей");
            variants.add(name.substring(0, name.length()-1) + "ём");
        } else if (name.endsWith("я")) {
            // Катя -> Кате, Кати, Катю, Катей
            variants.add(name.substring(0, name.length()-1) + "е");
            variants.add(name.substring(0, name.length()-1) + "и");
            variants.add(name.substring(0, name.length()-1) + "ю");
            variants.add(name.substring(0, name.length()-1) + "ей");
        } else if (name.endsWith("а")) {
            // Миша -> Мише, Миши, Мишу, Мишей
            variants.add(name.substring(0, name.length()-1) + "е");
            variants.add(name.substring(0, name.length()-1) + "и");
            variants.add(name.substring(0, name.length()-1) + "у");
            variants.add(name.substring(0, name.length()-1) + "ой");
            variants.add(name.substring(0, name.length()-1) + "ей");
        } else if (name.endsWith("ь")) {
            // Игорь -> Игоря, Игорю, Игорем
            variants.add(name.substring(0, name.length()-1) + "я");
            variants.add(name.substring(0, name.length()-1) + "ю");
            variants.add(name.substring(0, name.length()-1) + "ем");
        } else {
            // Иван -> Ивана, Ивану, Иваном
            variants.add(name + "а");
            variants.add(name + "у");
            variants.add(name + "ом");
            variants.add(name + "е");
            
            // Для имен на твердую согласную, которые могут иметь мягкую форму
            if (name.matches(".*[бвгджзклмнпрстфх]$")) {
                variants.add(name + "ем");
            }
        }
    }
    
    /**
     * Сохраняет варианты имен
     */
    private void saveNameVariants() {
        Map<String, String> serializedMap = new HashMap<>();
        
        for (Map.Entry<String, Set<String>> entry : contactNameVariants.entrySet()) {
            String contactName = entry.getKey();
            Set<String> variants = entry.getValue();
            String variantsStr = TextUtils.join("|", variants);
            serializedMap.put(contactName, variantsStr);
        }
        
        SharedPreferences prefs = context.getSharedPreferences(CONTACT_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String json = StringMapSerializer.serialize(serializedMap);
        editor.putString(NAME_VARIANTS_KEY, json);
        editor.apply();
    }
    
    /**
     * Добавляет новый вариант имени для контакта
     */
    public void addNameVariant(String contactName, String variant) {
        Set<String> variants = contactNameVariants.getOrDefault(contactName, new HashSet<>());
        variants.add(variant.toLowerCase());
        contactNameVariants.put(contactName, variants);
        saveNameVariants();
    }
    
    /**
     * Сохраняет сопоставления отношений с контактами
     */
    private void saveRelationMappings() {
        SharedPreferences prefs = context.getSharedPreferences(CONTACT_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        try {
            // Сериализация Map в строку JSON
            String mappingJson = StringMapSerializer.serialize(relationToContactMap);
            editor.putString(RELATION_MAPPING_KEY, mappingJson);
            
            // Конвертируем Map<String, Integer> в Map<String, String> для сериализации
            Map<String, String> stringUsageCount = new HashMap<>();
            for (Map.Entry<String, Integer> entry : contactUsageCount.entrySet()) {
                stringUsageCount.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            String usageCountJson = StringMapSerializer.serialize(stringUsageCount);
            editor.putString(USAGE_COUNT_KEY, usageCountJson);
            
            editor.apply();
        } catch (Exception e) {
            // Обработка ошибок сериализации
        }
    }
    
    /**
     * Находит наиболее похожий контакт по частичному имени
     */
    public ContactsManager.Contact findContactByPartialNameFuzzy(String partialName) {
        if (partialName == null || partialName.trim().isEmpty()) {
            return null;
        }
        
        Map<ContactsManager.Contact, Double> matchScores = new HashMap<>();
        List<ContactsManager.Contact> allContacts = contactsManager.searchContacts("");
        
        partialName = partialName.toLowerCase().trim();
        
        // Разбиваем поисковый запрос на части (для поиска по нескольким словам)
        String[] searchParts = partialName.split("\\s+");
        
        for (ContactsManager.Contact contact : allContacts) {
            // Проверяем через варианты имени
            Set<String> variants = contactNameVariants.getOrDefault(contact.name, new HashSet<>());
            
            // Максимальная оценка сходства для всех вариантов имени
            double maxScore = 0;
            
            // Проверяем точное вхождение
            if (variants.contains(partialName)) {
                maxScore = 1.0;
            } else {
                // Проверяем, содержит ли какой-либо вариант все части поискового запроса
                for (String variant : variants) {
                    // Вычисляем оценку для каждого варианта
                    double variantScore = calculateVariantMatchScore(variant, partialName, searchParts);
                    maxScore = Math.max(maxScore, variantScore);
                }
                
                // Проверяем, содержит ли номер телефона контакта часть поискового запроса
                // (для случаев, когда пользователь ищет по номеру)
                if (maxScore < MIN_SIMILARITY_THRESHOLD && contact.phoneNumber != null) {
                    String normalizedPhone = contact.phoneNumber.replaceAll("[^0-9]", "");
                    String normalizedSearch = partialName.replaceAll("[^0-9]", "");
                    
                    if (!normalizedSearch.isEmpty() && normalizedPhone.contains(normalizedSearch)) {
                        double score = 0.7 + (0.3 * normalizedSearch.length() / normalizedPhone.length());
                        maxScore = Math.max(maxScore, score);
                    }
                }
            }
            
            // Учитываем частоту использования для повышения оценки часто используемых контактов
            int usageCount = contactUsageCount.getOrDefault(contact.name, 0);
            double usageBonus = Math.min(0.1, usageCount * 0.01); // Максимум +0.1 к оценке
            maxScore += usageBonus;
            
            // Если нашли достаточно похожий вариант, добавляем в результаты
            if (maxScore >= MIN_SIMILARITY_THRESHOLD) {
                matchScores.put(contact, maxScore);
            }
        }
        
        // Если нашли хотя бы один контакт, возвращаем наиболее похожий
        if (!matchScores.isEmpty()) {
            // Находим контакт с максимальной оценкой сходства
            ContactsManager.Contact bestMatch = Collections.max(
                    matchScores.entrySet(),
                    Map.Entry.comparingByValue()
            ).getKey();
            
            // Увеличиваем счетчик использования для лучшего совпадения
            incrementContactUsage(bestMatch.name);
            
            return bestMatch;
        }
        
        return null;
    }
    
    /**
     * Вычисляет оценку совпадения варианта имени с поисковым запросом
     */
    private double calculateVariantMatchScore(String variant, String query, String[] queryParts) {
        double score = 0;
        
        // Проверяем, начинается ли вариант с запроса (высокий приоритет)
        if (variant.startsWith(query)) {
            score += 0.8;
        } 
        // Проверяем, содержит ли вариант полный запрос
        else if (variant.contains(query)) {
            score += 0.6;
            
            // Дополнительные баллы, если запрос находится в начале слова
            String[] variantParts = variant.split("\\s+");
            for (String part : variantParts) {
                if (part.startsWith(query)) {
                    score += 0.1;
                    break;
                }
            }
        }
        
        // Проверяем совпадение отдельных частей запроса
        int matchedParts = 0;
        double partMatchScore = 0;
        
        for (String part : queryParts) {
            if (part.length() <= 1) continue; // Пропускаем слишком короткие части
            
            if (variant.contains(part)) {
                matchedParts++;
                
                // Вычисляем вес части относительно длины варианта
                double partWeight = (double) part.length() / variant.length();
                partMatchScore += partWeight;
                
                // Дополнительные баллы, если часть находится в начале слова
                String[] variantParts = variant.split("\\s+");
                for (String varPart : variantParts) {
                    if (varPart.startsWith(part)) {
                        partMatchScore += 0.1;
                        break;
                    }
                }
            }
        }
        
        // Если все части запроса найдены, добавляем баллы
        if (matchedParts == queryParts.length && queryParts.length > 0) {
            score = Math.max(score, 0.7); // Минимальная оценка 0.7, если все части найдены
            
            // Добавляем баллы за совпадение частей
            score += 0.2 * (partMatchScore / queryParts.length);
        } 
        // Если найдены не все части, но хотя бы одна
        else if (matchedParts > 0 && queryParts.length > 0) {
            double partialScore = 0.5 * ((double) matchedParts / queryParts.length);
            partialScore += 0.2 * (partMatchScore / queryParts.length);
            
            score = Math.max(score, partialScore);
        }
        
        // Если запрос короткий (1-2 символа), снижаем оценку для избежания ложных совпадений
        if (query.length() <= 2 && score < 0.8) {
            score *= 0.7;
        }
        
        // Используем расстояние Левенштейна для улучшения оценки при похожих строках
        if (score < MIN_SIMILARITY_THRESHOLD) {
            double levenshteinScore = calculateSimilarity(query, variant);
            
            // Если строки очень похожи по Левенштейну, повышаем оценку
            if (levenshteinScore > 0.8) {
                score = Math.max(score, levenshteinScore - 0.1); // Немного снижаем оценку Левенштейна
            }
        }
        
        return Math.min(1.0, score); // Максимальная оценка - 1.0
    }
    
    /**
     * Вычисляет сходство между двумя строками (на основе расстояния Левенштейна)
     */
    private double calculateSimilarity(String s1, String s2) {
        int distance = levenshteinDistance(s1, s2);
        int maxLength = Math.max(s1.length(), s2.length());
        
        // Нормализуем расстояние к диапазону [0, 1]
        return 1.0 - (double) distance / maxLength;
    }
    
    /**
     * Вычисляет расстояние Левенштейна между двумя строками
     */
    private int levenshteinDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();
        
        int[][] dp = new int[m + 1][n + 1];
        
        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        
        return dp[m][n];
    }
    
    /**
     * Добавляет соответствие между отношением и контактом
     */
    public void addRelationMapping(String relation, String contactName) {
        relationToContactMap.put(relation.toLowerCase(), contactName);
        saveRelationMappings();
    }
    
    /**
     * Удаляет соответствие между отношением и контактом
     */
    public void removeRelationMapping(String relation) {
        relationToContactMap.remove(relation.toLowerCase());
        saveRelationMappings();
    }
    
    /**
     * Увеличивает счетчик использования контакта
     */
    public void incrementContactUsage(String contactName) {
        Integer count = contactUsageCount.getOrDefault(contactName, 0);
        contactUsageCount.put(contactName, count + 1);
        saveRelationMappings();
    }
    
    /**
     * Извлекает отношение из текста запроса
     */
    public String extractRelationFromText(String text) {
        text = text.toLowerCase().trim();
        
        // Проверяем наличие известных отношений в тексте
        for (String relation : KNOWN_RELATIONS) {
            if (text.contains(relation)) {
                return relation;
            }
        }
        
        // Если не нашли известных отношений, проверяем пользовательские
        for (String relation : relationToContactMap.keySet()) {
            if (text.contains(relation)) {
                return relation;
            }
        }
        
        return null;
    }
    
    /**
     * Находит контакт по отношению или имени
     */
    public ContactsManager.Contact findContactByRelationOrName(String text) {
        // Пытаемся извлечь отношение из текста
        String relation = extractRelationFromText(text);
        
        if (relation != null && relationToContactMap.containsKey(relation)) {
            // Если найдено отношение и оно сопоставлено с контактом
            String contactName = relationToContactMap.get(relation);
            ContactsManager.Contact contact = contactsManager.findBestMatchingContact(contactName);
            
            if (contact != null) {
                // Увеличиваем счетчик использования
                incrementContactUsage(contact.name);
                Log.d("ContactNlpProcessor", "Найден контакт по отношению: " + relation + " -> " + contact.name);
                return contact;
            } else {
                Log.d("ContactNlpProcessor", "Отношение найдено, но контакт не найден: " + relation + " -> " + contactName);
            }
        }
        
        // Если не удалось найти по отношению, ищем по имени с нечетким сопоставлением
        ContactsManager.Contact contact = findContactByPartialNameFuzzy(text);
        if (contact != null) {
            Log.d("ContactNlpProcessor", "Найден контакт по нечеткому поиску: " + contact.name);
            return contact;
        }
        
        // Если не удалось найти по нечеткому поиску, используем обычный поиск
        List<ContactsManager.Contact> contacts = contactsManager.findContactsByPartialName(text);
        if (!contacts.isEmpty()) {
            // Сортируем контакты по частоте использования
            sortContactsByUsage(contacts);
            incrementContactUsage(contacts.get(0).name);
            Log.d("ContactNlpProcessor", "Найден контакт по обычному поиску: " + contacts.get(0).name);
            return contacts.get(0);
        }
        
        Log.d("ContactNlpProcessor", "Контакт не найден для запроса: " + text);
        return null;
    }
    
    /**
     * Находит список контактов по отношению или имени
     */
    public List<ContactsManager.Contact> findContactsByRelationOrName(String text) {
        List<ContactsManager.Contact> results = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return results;
        }
        
        text = text.toLowerCase().trim();
        
        // Проверяем, содержит ли запрос ключевые слова для поиска контакта
        boolean isContactQuery = containsContactKeywords(text);
        
        // Пытаемся извлечь отношение из текста
        String relation = extractRelationFromText(text);
        
        if (relation != null && relationToContactMap.containsKey(relation)) {
            // Если найдено отношение и оно сопоставлено с контактом
            String contactName = relationToContactMap.get(relation);
            List<ContactsManager.Contact> contacts = contactsManager.findContactsByPartialName(contactName);
            
            if (!contacts.isEmpty()) {
                // Сортируем контакты по частоте использования
                sortContactsByUsage(contacts);
                
                // Увеличиваем счетчик использования для найденных контактов
                for (ContactsManager.Contact contact : contacts) {
                    incrementContactUsage(contact.name);
                }
                
                return contacts;
            }
        }
        
        // Извлекаем потенциальное имя из запроса, удаляя ключевые слова
        String nameQuery = extractNameFromQuery(text);
        
        // Ищем контакты по нечеткому совпадению
        Map<ContactsManager.Contact, Double> matchScores = new HashMap<>();
        List<ContactsManager.Contact> allContacts = contactsManager.searchContacts("");
        
        String partialName = nameQuery.toLowerCase().trim();
        
        // Если запрос очень короткий (1-2 символа), требуем более высокую точность совпадения
        double minThreshold = MIN_SIMILARITY_THRESHOLD;
        if (partialName.length() <= 2) {
            minThreshold = 0.85; // Повышаем порог для коротких запросов
        }
        
        // Разбиваем поисковый запрос на части (для поиска по нескольким словам)
        String[] searchParts = partialName.split("\\s+");
        
        for (ContactsManager.Contact contact : allContacts) {
            // Проверяем через варианты имени
            Set<String> variants = contactNameVariants.getOrDefault(contact.name, new HashSet<>());
            
            // Максимальная оценка сходства для всех вариантов имени
            double maxScore = 0;
            
            // Проверяем точное вхождение
            if (variants.contains(partialName)) {
                maxScore = 1.0;
            } else {
                // Проверяем, содержит ли какой-либо вариант все части поискового запроса
                for (String variant : variants) {
                    boolean allPartsMatch = true;
                    double partialScore = 0;
                    
                    for (String part : searchParts) {
                        if (!variant.contains(part)) {
                            allPartsMatch = false;
                            break;
                        }
                        // Добавляем частичную оценку для каждой найденной части
                        partialScore += (double) part.length() / variant.length();
                    }
                    
                    if (allPartsMatch) {
                        // Нормализуем оценку по количеству частей
                        double score = partialScore / searchParts.length;
                        maxScore = Math.max(maxScore, score);
                    }
                }
                
                // Проверяем, содержит ли какой-либо вариант частичное имя
                if (maxScore < minThreshold) {
                for (String variant : variants) {
                    if (variant.contains(partialName)) {
                        // Оценка сходства по длине
                        double score = (double) partialName.length() / variant.length();
                            // Повышаем оценку для совпадений в начале слова
                            if (variant.startsWith(partialName)) {
                                score += 0.2;
                            }
                        maxScore = Math.max(maxScore, score);
                        }
                    }
                }
                
                // Если не нашли по вхождению, используем расстояние Левенштейна
                if (maxScore < minThreshold) {
                    for (String variant : variants) {
                        double score = calculateSimilarity(partialName, variant);
                        // Повышаем оценку для коротких имен, чтобы учесть уменьшительные формы
                        if (variant.length() <= 5 && score >= 0.6) {
                            score += 0.1;
                        }
                        maxScore = Math.max(maxScore, score);
                    }
                }
                
                // Проверяем, содержит ли номер телефона контакта часть поискового запроса
                if (maxScore < minThreshold && contact.phoneNumber != null) {
                    String normalizedPhone = contact.phoneNumber.replaceAll("[^0-9]", "");
                    String normalizedSearch = partialName.replaceAll("[^0-9]", "");
                    
                    if (!normalizedSearch.isEmpty() && normalizedPhone.contains(normalizedSearch)) {
                        double score = 0.7 + (0.3 * normalizedSearch.length() / normalizedPhone.length());
                        maxScore = Math.max(maxScore, score);
                    }
                }
            }
            
            // Учитываем частоту использования для повышения оценки часто используемых контактов
            int usageCount = contactUsageCount.getOrDefault(contact.name, 0);
            double usageBonus = Math.min(0.1, usageCount * 0.01); // Максимум +0.1 к оценке
            maxScore += usageBonus;
            
            // Если нашли достаточно похожий вариант, добавляем в результаты
            if (maxScore >= minThreshold) {
                matchScores.put(contact, maxScore);
            }
        }
        
        // Добавляем все подходящие контакты в результат
        results.addAll(matchScores.keySet());
        
        // Если не нашли по нечеткому поиску, используем обычный поиск
        if (results.isEmpty() && isContactQuery) {
            results = contactsManager.findContactsByPartialName(nameQuery);
        }
        
        // Сортируем контакты по частоте использования и оценке сходства
        if (!results.isEmpty()) {
            sortContactsByUsageAndSimilarity(results, matchScores);
            
            // Увеличиваем счетчик использования для найденных контактов
            // (но только для первых 3, чтобы не искажать статистику)
            int count = Math.min(3, results.size());
            for (int i = 0; i < count; i++) {
                incrementContactUsage(results.get(i).name);
            }
        }
        
        return results;
    }
    
    /**
     * Сортирует контакты по частоте использования
     */
    private void sortContactsByUsage(List<ContactsManager.Contact> contacts) {
        Collections.sort(contacts, (c1, c2) -> {
            int count1 = contactUsageCount.getOrDefault(c1.name, 0);
            int count2 = contactUsageCount.getOrDefault(c2.name, 0);
            return Integer.compare(count2, count1); // В порядке убывания
        });
    }
    
    /**
     * Сортирует контакты по частоте использования и оценке сходства
     */
    private void sortContactsByUsageAndSimilarity(List<ContactsManager.Contact> contacts, Map<ContactsManager.Contact, Double> similarityScores) {
        Collections.sort(contacts, (c1, c2) -> {
            int count1 = contactUsageCount.getOrDefault(c1.name, 0);
            int count2 = contactUsageCount.getOrDefault(c2.name, 0);
            
            // Если частота использования сильно различается, используем её для сортировки
            if (Math.abs(count1 - count2) > 3) {
                return Integer.compare(count2, count1);
            }
            
            // Иначе используем оценку сходства
            double score1 = similarityScores.getOrDefault(c1, 0.0);
            double score2 = similarityScores.getOrDefault(c2, 0.0);
            return Double.compare(score2, score1);
        });
    }
    
    /**
     * Возвращает список известных отношений, включая пользовательские
     */
    public Set<String> getAllKnownRelations() {
        Set<String> relations = new HashSet<>(KNOWN_RELATIONS);
        relations.addAll(relationToContactMap.keySet());
        return relations;
    }
    
    /**
     * Проверяет, содержит ли текст запрос к контакту
     */
    public boolean containsContactRequest(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        text = text.toLowerCase().trim();
        
        // Проверяем наличие ключевых слов для поиска контакта
        if (containsContactKeywords(text)) {
            return true;
        }
        
        // Проверяем наличие отношения в тексте
        if (extractRelationFromText(text) != null) {
            return true;
        }
        
        // Проверяем наличие цифр (возможно, это номер телефона)
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.length() >= 4) {  // Если в тексте 4+ цифр, вероятно это номер телефона
            return true;
        }
        
        // Проверяем наличие команд для звонка или отправки сообщений
        String[] callCommands = {
            "позвони", "набери", "звони", "вызов", "звонок", "позвонить", "набрать",
            "call", "dial", "phone"
        };
        
        String[] messageCommands = {
            "напиши", "сообщение", "отправь", "смс", "написать", "отправить",
            "text", "message", "sms", "send"
        };
        
        // Проверяем команды звонка
        for (String cmd : callCommands) {
            if (text.contains(cmd)) {
        // Проверяем нечеткое совпадение с именами контактов
        ContactsManager.Contact contact = findContactByPartialNameFuzzy(text);
        if (contact != null) {
                    return true;
                }
                
                // Если команда звонка находится в начале текста, скорее всего это запрос к контакту
                if (text.startsWith(cmd) || text.startsWith("please " + cmd) || 
                    text.startsWith("пожалуйста " + cmd)) {
                    return true;
                }
            }
        }
        
        // Проверяем команды сообщений
        for (String cmd : messageCommands) {
            if (text.contains(cmd)) {
                // Проверяем нечеткое совпадение с именами контактов
                ContactsManager.Contact contact = findContactByPartialNameFuzzy(text);
                if (contact != null) {
                    return true;
                }
                
                // Если команда сообщения находится в начале текста, скорее всего это запрос к контакту
                if (text.startsWith(cmd) || text.startsWith("please " + cmd) || 
                    text.startsWith("пожалуйста " + cmd)) {
                    return true;
                }
            }
        }
        
        // Проверяем нечеткое совпадение с именами контактов
        ContactsManager.Contact contact = findContactByPartialNameFuzzy(text);
        if (contact != null) {
            // Если нашли контакт с высокой степенью уверенности (> 0.8), считаем это запросом к контакту
            return true;
        }
        
        // Ищем контакты по имени
        List<ContactsManager.Contact> contacts = contactsManager.findContactsByPartialName(text);
        if (!contacts.isEmpty()) {
            // Если нашли точное совпадение, считаем это запросом к контакту
            return true;
        }
        
        // Проверяем, является ли текст коротким (1-2 слова) и содержит ли он имя
        String[] words = text.split("\\s+");
        if (words.length <= 2) {
            // Проверяем, похож ли текст на имя (начинается с заглавной буквы)
            String originalText = text.trim();
            if (!originalText.isEmpty() && Character.isUpperCase(originalText.charAt(0))) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Предлагает сопоставление для нового отношения
     */
    public void suggestRelationMapping(String relation, String contactName) {
        if (!relationToContactMap.containsKey(relation.toLowerCase())) {
            addRelationMapping(relation, contactName);
        }
    }
    
    /**
     * Проверяет, содержит ли запрос ключевые слова для поиска контакта
     */
    private boolean containsContactKeywords(String text) {
        String[] keywords = {
            "контакт", "найти", "позвонить", "набрать", "телефон", "номер", 
            "связаться", "написать", "отправить", "сообщение", "звонок", "вызов"
        };
        
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Извлекает имя контакта из запроса, удаляя ключевые слова
     */
    private String extractNameFromQuery(String text) {
        // Удаляем распространенные фразы для поиска контактов
        String[] phrasesToRemove = {
            "найти контакт", "найти номер", "позвонить", "набрать номер", 
            "найти", "телефон", "контакт", "номер телефона", "связаться с", 
            "написать", "отправить сообщение", "сообщение для"
        };
        
        String result = text;
        for (String phrase : phrasesToRemove) {
            result = result.replace(phrase, "");
        }
        
        return result.trim();
    }
    
    /**
     * Класс для сериализации Map<String, String> в JSON строку и обратно
     */
    private static class StringMapSerializer {
        public static String serialize(Map<String, String> map) {
            if (map == null || map.isEmpty()) {
                return "{}";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            
            boolean first = true;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(escape(entry.getKey())).append("\":");
                sb.append("\"").append(escape(entry.getValue())).append("\"");
                first = false;
            }
            
            sb.append("}");
            return sb.toString();
        }
        
        public static Map<String, String> deserialize(String json) {
            Map<String, String> map = new HashMap<>();
            
            if (json == null || json.equals("{}")) {
                return map;
            }
            
            // Убираем фигурные скобки
            json = json.substring(1, json.length() - 1);
            
            if (json.isEmpty()) {
                return map;
            }
            
            // Разбиваем на пары ключ-значение
            int index = 0;
            while (index < json.length()) {
                // Находим начало ключа
                int keyStart = json.indexOf("\"", index) + 1;
                if (keyStart == 0) break;
                
                // Находим конец ключа
                int keyEnd = json.indexOf("\"", keyStart);
                if (keyEnd == -1) break;
                
                // Находим начало значения
                int valueStart = json.indexOf("\"", keyEnd + 1) + 1;
                if (valueStart == 0) break;
                
                // Находим конец значения
                int valueEnd = findClosingQuote(json, valueStart);
                if (valueEnd == -1) break;
                
                String key = unescape(json.substring(keyStart, keyEnd));
                String value = unescape(json.substring(valueStart, valueEnd));
                
                map.put(key, value);
                
                // Переходим к следующей паре
                index = valueEnd + 1;
                
                // Пропускаем запятую
                index = json.indexOf(",", index);
                if (index == -1) break;
                index++;
            }
            
            return map;
        }
        
        private static int findClosingQuote(String json, int start) {
            for (int i = start; i < json.length(); i++) {
                if (json.charAt(i) == '\"' && (i == start || json.charAt(i - 1) != '\\')) {
                    return i;
                }
            }
            return -1;
        }
        
        private static String escape(String str) {
            return str.replace("\\", "\\\\").replace("\"", "\\\"");
        }
        
        private static String unescape(String str) {
            return str.replace("\\\"", "\"").replace("\\\\", "\\");
        }
    }
} 