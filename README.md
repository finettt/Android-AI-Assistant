# Robo - Your AI Assistant

An intelligent voice assistant for Android with advanced camera capabilities and accessibility features. The application provides a convenient interface for voice control of your device, making calls, sending messages, and analyzing images.

## Features

### 🎤 Voice Control
- Voice command recognition in Russian
- Natural dialogues with the assistant
- Automatic silence detection
- Voice feedback

### 📞 Telephony and Messages
- Voice dialing
- Contact search by name
- SMS messaging
- Safety confirmation dialogs

### 🌐 Web Browser
- Built-in web page viewing via Chrome Custom Tabs
- Support for direct URLs and search queries
- Automatic link formatting
- Fallback to default browser

### 📸 Camera Features
- Real-time object analysis
- Automatic photo capture
- Voice description of recognized objects
- Customizable shooting modes

### ♿ Accessibility
- High contrast mode
- Adjustable text size
- Voice and vibration feedback
- Screen reader optimized structure

## Installation

### Requirements
- Android 7.0 (API 24) or higher
- Minimum 100 MB free space
- Internet access
- Microphone, camera, and contacts permissions

### Via Android Studio
1. Clone the repository:
```bash
git clone https://github.com/finettt/Android-AI-Assistan.git
```

2. Open the project in Android Studio

3. Sync Gradle files

4. Run on device or emulator:
   - Run > Run 'app'
   - Or use Shift + F10

### Via Command Line
```bash
# Build debug version
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

## Usage

### Basic Voice Commands

#### Calls
- "Позвони [имя контакта]"
- "Набери [номер телефона]"
- "Вызови [имя контакта]"

#### SMS
- "Отправь SMS [имя контакта] с текстом [сообщение]"
- "Напиши сообщение [имя контакта] [текст]"

#### Web
- "Открой [url]" - direct URL navigation
- "Найди [запрос]" - Google search
- "Поиск [запрос]" - Google search

#### Camera
- "Включи камеру"
- "Что сейчас видно на камере?"
- "Опиши что видишь"

## Development

### Project Structure
```
app/
├── src/
│   ├── main/
│   │   ├── java/io/finett/myapplication/
│   │   │   ├── MainActivity.java
│   │   │   ├── VoiceChatActivity.java
│   │   │   ├── CameraActivity.java
│   │   │   └── ...
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   ├── values/
│   │   │   └── ...
│   │   └── AndroidManifest.xml
│   ├── test/
│   └── androidTest/
└── build.gradle
```

### Technologies
- Java for core development
- Jetpack components (ViewModel, LiveData)
- CameraX for camera operations
- Retrofit for network requests
- Material Design components

### Project Cleanup
For deep project cleanup, use:
- Windows: `clean-project.bat`
- Linux/macOS: `clean-project.sh`

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a Pull Request

## License

MIT License. See [LICENSE](LICENSE) file for details.

## Contacts

- Telegram: [@finet_f](https://t.me/finet_f)
- GitHub: [github.com/finettt](https://github.com/finettt)

## Changelog

Full changelog is available in [DOCUMENTATION.md](DOCUMENTATION.md#history-of-changes)
