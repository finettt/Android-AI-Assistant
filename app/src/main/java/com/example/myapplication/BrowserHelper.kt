package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import java.util.regex.Pattern

class BrowserHelper(private val context: Context) {
    companion object {
        private const val CHROME_PACKAGE = "com.android.chrome"
        private val URL_PATTERN = Pattern.compile(
            "((https?|ftp)://)?[a-z0-9-]+(\\.[a-z0-9-]+)+([/?].*)?",
            Pattern.CASE_INSENSITIVE
        )
    }

    fun extractUrlFromText(text: String): String? {
        val matcher = URL_PATTERN.matcher(text)
        return if (matcher.find()) {
            var url = matcher.group()
            if (!url.startsWith("http", ignoreCase = true)) {
                url = "https://$url"
            }
            url
        } else null
    }

    fun openInChrome(url: String) {
        try {
            val uri = Uri.parse(url)
            if (isChromeInstalled()) {
                // Пытаемся открыть в Chrome Custom Tabs
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                customTabsIntent.intent.setPackage(CHROME_PACKAGE)
                customTabsIntent.launchUrl(context, uri)
            } else {
                // Если Chrome не установлен, открываем в браузере по умолчанию
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isChromeInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(CHROME_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
} 