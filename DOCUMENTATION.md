# Project Documentation

## Table of Contents
1. [Introduction](#introduction)
2. [Project Overview](#project-overview)
3. [Project Setup](#project-setup)
4. [Code Structure](#code-structure)
5. [Features](#features)
6. [Technical Details](#technical-details)
7. [Build and Run](#build-and-run)
8. [Contributing](#contributing)
9. [License](#license)
10. [Contact](#contact)

## Introduction
Robo - Your AI Assistant - это Android-приложение с голосовым ассистентом, предоставляющее широкий спектр возможностей для голосового управления устройством.

## Project Overview
Приложение разработано с использованием современных Android-практик и включает в себя:
- Голосовое управление с поддержкой русского языка
- Интеграцию с системными функциями (звонки, SMS)
- Работу с веб-браузером через Chrome Custom Tabs
- Систему напоминаний и будильников
- Специальный режим для слабовидящих

## Project Setup
### Prerequisites
- Android Studio Arctic Fox или новее
- JDK 11+
- Android SDK (минимальная версия API 24)
- Gradle 7.0+

### Installation
1. Клонировать репозиторий
2. Открыть проект в Android Studio
3. Синхронизировать Gradle файлы

## Code Structure
```
app/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/myapplication2/
│   │   │       ├── activities/         # Activity classes handling UI interaction
│   │   │       ├── adapters/           # Adapter classes for RecyclerViews etc.
│   │   │       ├── helpers/            # Helper classes (e.g., AlarmHelper.java, BrowserHelper.kt, AccessibilityManager.kt)
│   │   │       ├── models/             # Data models representing application data
│   │   │       └── services/           # Service classes for business logic, background tasks
│   │   ├── res/                      # Layouts, images, and other resources
│   │   └── AndroidManifest.xml       # Application manifest with essential declarations
│   ├── test/                         # Unit tests for testing business logic
│   └── androidTest/                  # Instrumentation tests for device/emulator execution
└── build.gradle                     # Gradle build file for the app module
└── proguard-rules.pro               # Proguard configuration for code obfuscation
```

## Features

### Голосовое управление
- Распознавание голосовых команд на русском языке
- Голосовой ответ с использованием Text-to-Speech
- Автоматическая остановка записи при тишине
- Визуальная индикация процесса записи

### Телефония и сообщения
- Голосовой набор номера
- Поиск контактов по имени
- Отправка SMS сообщений
- Подтверждение действий перед выполнением

### Веб-браузер
- Интеграция Chrome Custom Tabs
- Голосовой поиск в интернете
- Открытие веб-страниц по URL
- Поддержка поисковых запросов

### Система напоминаний
- Создание будильников голосом
- Управление напоминаниями
- Гибкая система уведомлений
- Повторяющиеся напоминания

### Специальные возможности
- Режим для слабовидящих
- Увеличенные элементы интерфейса
- Голосовые подсказки
- Настраиваемые жесты

## Technical Details

### Используемые технологии
- Kotlin/Java
- Android Jetpack Components
- Room Database
- WorkManager
- Chrome Custom Tabs
- Android Speech Recognition API
- TextToSpeech

### Разрешения
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.READ_CONTACTS"/>
<uses-permission android:name="android.permission.CALL_PHONE"/>
<uses-permission android:name="android.permission.SEND_SMS"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.SET_ALARM"/>
```

## Build and Run
1. Откройте проект в Android Studio
2. Убедитесь, что все зависимости установлены
3. Выберите целевое устройство (эмулятор или реальное устройство)
4. Нажмите "Run" (⇧F10)

### Сборка через командную строку
```bash
./gradlew assembleDebug
```

## Contributing
1. Создайте fork репозитория
2. Создайте feature branch
3. Внесите изменения
4. Отправьте pull request

### Правила оформления кода
- Следуйте Android Code Style Guidelines
- Добавляйте комментарии на английском языке
- Пишите unit-тесты для новой функциональности

## License
 - MIT

## Contact
- Telegram: [@finet_f](https://t.me/finet_f)

