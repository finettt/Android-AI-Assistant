package io.finett.myapplication.util;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.webkit.URLUtil;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class BrowserHelper {
    private final Activity activity;
    private static final String CHROME_PACKAGE = "com.android.chrome";
    private static final Pattern URL_PATTERN = Pattern.compile(
        "^(https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/[\\w-./]*)?$"
    );
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}"
    );

    public BrowserHelper(Activity activity) {
        this.activity = activity;
    }

    public void processWebCommand(String command) {
        command = command.toLowerCase().trim();
        String query = extractQuery(command);
        
        if (query == null || query.isEmpty()) {
            return;
        }

        if (isWebUrl(query)) {
            openUrl(ensureHttps(query));
        } else if (containsDomain(query)) {
            // Если текст содержит домен, но не является полным URL
            openUrl(ensureHttps(extractDomain(query)));
        } else {
            searchInGoogle(query);
        }
    }

    private String extractQuery(String command) {
        // Удаляем ключевые слова из команды
        String[] keywords = {
            "открой", "найди", "поиск", "покажи", "загрузи",
            "зайди на", "перейди на", "сайт", "страницу"
        };
        
        String query = command;
        for (String keyword : keywords) {
            if (command.startsWith(keyword)) {
                query = command.substring(keyword.length()).trim();
                break;
            }
        }
        
        // Удаляем дополнительные слова
        query = query.replaceAll("(?i)(сайт|страницу|на сайте|на странице)\\s+", "");
        return query;
    }

    private boolean isWebUrl(String text) {
        // Проверяем, является ли текст URL-адресом
        if (URLUtil.isValidUrl(text)) {
            return true;
        }
        
        // Проверяем с помощью регулярного выражения
        Matcher matcher = URL_PATTERN.matcher(text);
        return matcher.matches();
    }

    private boolean containsDomain(String text) {
        Matcher matcher = DOMAIN_PATTERN.matcher(text);
        return matcher.find();
    }

    private String extractDomain(String text) {
        Matcher matcher = DOMAIN_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return text;
    }

    private String ensureHttps(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }

    private void openUrl(String url) {
        try {
            if (isChromeAvailable()) {
                // Используем Chrome Custom Tabs
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                builder.setToolbarColor(ContextCompat.getColor(activity, android.R.color.white));
                builder.setShowTitle(true);
                builder.setInstantAppsEnabled(true);
                
                // Добавляем анимацию
                builder.setStartAnimations(activity, android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                builder.setExitAnimations(activity, android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                
                CustomTabsIntent customTabsIntent = builder.build();
                customTabsIntent.intent.setPackage(CHROME_PACKAGE);
                customTabsIntent.launchUrl(activity, Uri.parse(url));
            } else {
                // Используем браузер по умолчанию
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                activity.startActivity(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Если что-то пошло не так, пробуем открыть в браузере по умолчанию
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(intent);
        }
    }

    private void searchInGoogle(String query) {
        String searchUrl = "https://www.google.com/search?q=" + Uri.encode(query);
        openUrl(searchUrl);
    }

    private boolean isChromeAvailable() {
        try {
            activity.getPackageManager().getPackageInfo(CHROME_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
} 