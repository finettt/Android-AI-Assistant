# Alan - Your AI Assistant

An intelligent voice assistant for Android with advanced camera capabilities and accessibility features. The application provides a convenient interface for voice control of your device, making calls, sending messages, and analyzing images.

## Features

### 🎤 Voice Control
- Voice command recognition in English and Russian
- Natural dialogues with the assistant
- Automatic silence detection
- Voice feedback

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

### 🌍 Multilingual Support
- Complete English and Russian localization
- Language detection based on device settings
- Easy to add more languages through resource files

### 📞 Telephony and Messages
- Voice dialing
- Contact search by name
- SMS messaging
- Safety confirmation dialogs

## Installation

### Requirements
- Android 7.0 (API 24) or higher
- Minimum 100 MB free space
- Internet access
- Microphone, camera, and contacts permissions

### Setting Up API Keys

Before building the project, you need to set up API keys:

1. Run the setup script:
   - Windows: `setup-dev.bat`
   - Linux/MacOS: `./setup-dev.sh`

2. The script will create a `secrets.properties` file with templates for API keys.

3. Edit the created file, specifying your real API keys.

### Via Android Studio
1. Clone the repository:
```bash
git clone https://github.com/finettt/Android-AI-Assistant.git
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
- English: "Call [contact name]"
- Russian: "Позвони [имя контакта]"

#### SMS
- English: "Send SMS to [contact name] with text [message]"
- Russian: "Отправь SMS [имя контакта] с текстом [сообщение]"

#### Web
- English: "Open [url]", "Search for [query]"
- Russian: "Открой [url]", "Найди [запрос]"

#### Camera
- English: "Turn on camera", "What do you see?"
- Russian: "Включи камеру", "Что сейчас видно на камере?"

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
│   │   │   ├── values/          # Default (English) resources
│   │   │   ├── values-ru/       # Russian resources
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

## API Keys and Security

### Secure Storage of API Keys

To ensure the security of API keys required for the application's operation:

1. **Local Storage of Keys** — all API keys are stored in a local file `secrets.properties`, which is not included in the version control system.

2. **Setting Up the Development Environment**:
   
   a) Copy the `secrets.properties.example` file to `secrets.properties` in the project root directory:
   ```
   # OpenRouter API key (obtain from https://openrouter.ai)
   OPENROUTER_API_KEY=your_openrouter_api_key
   
   # Weather API key (obtain from weather service provider)
   WEATHER_API_KEY=your_weather_api_key
   ```
   
   b) In the `secrets.properties` file, replace placeholders with your real API keys
   
   c) Important: the `secrets.properties` file is included in `.gitignore` and should not be committed to the repository

3. **Key Security**:
   - Never store real API keys in files that may be committed to Git
   - Do not directly add API keys to the application code
   - Do not use hardcoded key values in build.gradle
   
### Demo Mode

For demonstration purposes, the application can be built using built-in keys:

```bash
./gradlew assembleInternal
```

In this mode, the application:
- Installs as a separate application with the suffix "(Demo)" in the name
- Uses built-in demonstration API keys
- Does not require user input of API keys

### Standard Build with Your API Keys

```bash
./gradlew assembleDebug
```
