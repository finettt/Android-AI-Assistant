package io.finett.myapplication.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

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
        
        // Создаем варианты имен для каждого контакта, если еще не созданы
        for (ContactsManager.Contact contact : allContacts) {
            if (!contactNameVariants.containsKey(contact.name)) {
                Set<String> variants = generateNameVariants(contact.name);
                contactNameVariants.put(contact.name, variants);
            }
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
            
            // Генерируем простые падежные формы для имени
            String firstName = parts[0].toLowerCase();
            generateCaseVariants(firstName, variants);
            
            // Генерируем простые падежные формы для фамилии
            String lastName = parts[parts.length-1].toLowerCase();
            generateCaseVariants(lastName, variants);
        }
        
        return variants;
    }
    
    /**
     * Генерирует простые падежные формы для имени
     */
    private void generateCaseVariants(String name, Set<String> variants) {
        // Правила для русских имен (упрощенные)
        if (name.endsWith("й")) {
            // Илья -> Илье, Ильи
            variants.add(name.substring(0, name.length()-1) + "е");
            variants.add(name.substring(0, name.length()-1) + "и");
            variants.add(name.substring(0, name.length()-1) + "ю");
            variants.add(name.substring(0, name.length()-1) + "ей");
        } else if (name.endsWith("а")) {
            // Миша -> Мише, Миши
            variants.add(name.substring(0, name.length()-1) + "е");
            variants.add(name.substring(0, name.length()-1) + "и");
            variants.add(name.substring(0, name.length()-1) + "у");
            variants.add(name.substring(0, name.length()-1) + "ой");
        } else if (name.endsWith("ь")) {
            // Игорь -> Игоря, Игорю
            variants.add(name.substring(0, name.length()-1) + "я");
            variants.add(name.substring(0, name.length()-1) + "ю");
            variants.add(name.substring(0, name.length()-1) + "ем");
        } else {
            // Иван -> Ивана, Ивану, Иваном
            variants.add(name + "а");
            variants.add(name + "у");
            variants.add(name + "ом");
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
        Map<ContactsManager.Contact, Double> matchScores = new HashMap<>();
        List<ContactsManager.Contact> allContacts = contactsManager.searchContacts("");
        
        partialName = partialName.toLowerCase().trim();
        
        for (ContactsManager.Contact contact : allContacts) {
            // Проверяем через варианты имени
            Set<String> variants = contactNameVariants.getOrDefault(contact.name, new HashSet<>());
            
            // Максимальная оценка сходства для всех вариантов имени
            double maxScore = 0;
            
            // Проверяем точное вхождение
            if (variants.contains(partialName)) {
                maxScore = 1.0;
            } else {
                // Проверяем, содержит ли какой-либо вариант частичное имя
                for (String variant : variants) {
                    if (variant.contains(partialName)) {
                        // Оценка сходства по длине
                        double score = (double) partialName.length() / variant.length();
                        maxScore = Math.max(maxScore, score);
                    }
                }
                
                // Если не нашли по вхождению, используем расстояние Левенштейна
                if (maxScore < MIN_SIMILARITY_THRESHOLD) {
                    for (String variant : variants) {
                        double score = calculateSimilarity(partialName, variant);
                        maxScore = Math.max(maxScore, score);
                    }
                }
            }
            
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
                return contact;
            }
        }
        
        // Если не удалось найти по отношению, ищем по имени с нечетким сопоставлением
        ContactsManager.Contact contact = findContactByPartialNameFuzzy(text);
        if (contact != null) {
            return contact;
        }
        
        // Если не удалось найти по нечеткому поиску, используем обычный поиск
        List<ContactsManager.Contact> contacts = contactsManager.findContactsByPartialName(text);
        if (!contacts.isEmpty()) {
            // Сортируем контакты по частоте использования
            sortContactsByUsage(contacts);
            incrementContactUsage(contacts.get(0).name);
            return contacts.get(0);
        }
        
        return null;
    }
    
    /**
     * Находит список контактов по отношению или имени
     */
    public List<ContactsManager.Contact> findContactsByRelationOrName(String text) {
        List<ContactsManager.Contact> results = new ArrayList<>();
        
        // Пытаемся извлечь отношение из текста
        String relation = extractRelationFromText(text);
        
        if (relation != null && relationToContactMap.containsKey(relation)) {
            // Если найдено отношение и оно сопоставлено с контактом
            String contactName = relationToContactMap.get(relation);
            List<ContactsManager.Contact> contacts = contactsManager.findContactsByPartialName(contactName);
            
            if (!contacts.isEmpty()) {
                // Сортируем контакты по частоте использования
                sortContactsByUsage(contacts);
                return contacts;
            }
        }
        
        // Ищем контакты по нечеткому совпадению
        Map<ContactsManager.Contact, Double> matchScores = new HashMap<>();
        List<ContactsManager.Contact> allContacts = contactsManager.searchContacts("");
        
        String partialName = text.toLowerCase().trim();
        
        for (ContactsManager.Contact contact : allContacts) {
            // Проверяем через варианты имени
            Set<String> variants = contactNameVariants.getOrDefault(contact.name, new HashSet<>());
            
            // Максимальная оценка сходства для всех вариантов имени
            double maxScore = 0;
            
            // Проверяем точное вхождение
            if (variants.contains(partialName)) {
                maxScore = 1.0;
            } else {
                // Проверяем, содержит ли какой-либо вариант частичное имя
                for (String variant : variants) {
                    if (variant.contains(partialName)) {
                        // Оценка сходства по длине
                        double score = (double) partialName.length() / variant.length();
                        maxScore = Math.max(maxScore, score);
                    }
                }
                
                // Если не нашли по вхождению, используем расстояние Левенштейна
                if (maxScore < MIN_SIMILARITY_THRESHOLD) {
                    for (String variant : variants) {
                        double score = calculateSimilarity(partialName, variant);
                        maxScore = Math.max(maxScore, score);
                    }
                }
            }
            
            // Если нашли достаточно похожий вариант, добавляем в результаты
            if (maxScore >= MIN_SIMILARITY_THRESHOLD) {
                matchScores.put(contact, maxScore);
            }
        }
        
        // Добавляем все подходящие контакты в результат
        results.addAll(matchScores.keySet());
        
        // Если не нашли по нечеткому поиску, используем обычный поиск
        if (results.isEmpty()) {
            results = contactsManager.findContactsByPartialName(text);
        }
        
        // Сортируем контакты по частоте использования и оценке сходства
        if (!results.isEmpty()) {
            sortContactsByUsageAndSimilarity(results, matchScores);
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
        text = text.toLowerCase().trim();
        
        // Проверяем наличие отношения в тексте
        if (extractRelationFromText(text) != null) {
            return true;
        }
        
        // Проверяем нечеткое совпадение с именами контактов
        ContactsManager.Contact contact = findContactByPartialNameFuzzy(text);
        if (contact != null) {
            return true;
        }
        
        // Ищем контакты по имени
        List<ContactsManager.Contact> contacts = contactsManager.findContactsByPartialName(text);
        return !contacts.isEmpty();
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