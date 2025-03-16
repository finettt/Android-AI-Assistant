# Project Documentation "Robo - Your AI Assistant"

## Table of Contents
1. [Introduction](#introduction)
2. [Project Overview](#project-overview)
3. [Project Setup](#project-setup)
4. [Project Structure](#project-structure)
   * [Root Directory](#root-directory)
   * [App Directory](#app-directory)
   * [Source Code (src)](#source-code-src)
   * [Java Code (java)](#java-code-java)
   * [Resources (res)](#resources-res)
   * [AndroidManifest.xml](#androidmanifestxml)
   * [Tests](#tests)
5. [Features](#features)
   * [Voice Control](#voice-control)
   * [Telephony and Messages](#telephony-and-messages)
   * [Web Browser](#web-browser)
   * [Camera Operations](#camera-operations)
   * [Accessibility](#accessibility)
6. [Technical Details](#technical-details)
   * [Architecture](#architecture)
   * [Technologies and Libraries](#technologies-and-libraries)
   * [API Integration](#api-integration)
   * [Permission System](#permission-system)
   * [Important Classes](#important-classes)
7. [Build and Run](#build-and-run)
8. [Contributing](#contributing)
9. [License](#license)
10. [Contacts](#contacts)
11. [Change History](#change-history)
12. [Future Plans](#future-plans)

## Introduction

"Robo - Your AI Assistant" is an Android application with a voice assistant in the form of a friendly robot, providing a wide range of capabilities for voice control of the device. The application allows making calls, sending SMS messages, opening web pages, and analyzing images, with special attention to accessibility for users with disabilities.

## Project Overview

The application is developed using modern Android practices and includes:
- Voice control with support for the Russian language
- Integration with system functions (calls, SMS)
- Working with the web browser through Chrome Custom Tabs
- Image analysis
- Special accessibility features for people with disabilities (high contrast mode, text size adjustment, etc.)

## Project Setup

### Requirements
- Android Studio Arctic Fox or newer
- JDK 11+
- Android SDK (minimum API 24)
- Gradle 7.0+

### Installation
1. Clone the repository
2. Open the project in Android Studio
3. Synchronize Gradle files
4. Run the application on a device or emulator

### System Requirements
- Android 7.0 (API 24) or higher
- Internet access
- Microphone
- Camera (for image analysis functions)
- Access to contacts (for call and SMS functions)

## Project Structure

Below is the complete project structure with descriptions of all files and directories.

### Root Directory

The root directory of the project contains the following files and subdirectories:

#### Configuration Files

- **build.gradle** - Root Gradle script for the project, containing global settings for all project modules
- **settings.gradle** - Gradle settings file, determines which modules are included in the project
- **gradle.properties** - Properties and parameters for Gradle build
- **gradlew** - Bash script to run Gradle Wrapper in Unix-like systems
- **gradlew.bat** - Batch script to run Gradle Wrapper in Windows
- **.gitignore** - File defining which files and directories Git should ignore

#### Project Cleanup Scripts

- **clean.gradle** - Gradle script for deep project cleanup, deletes all generated files and caches
- **clean-project.sh** - Bash script to run cleanup tasks in Unix-like systems
- **clean-project.bat** - Batch script to run cleanup tasks in Windows

#### Documentation

- **README.md** - Main file with project description, installation instructions, and usage
- **LICENSE** - File with license text (MIT)
- **CHECKLIST.md** - List of tasks and checks for the project
- **DOCUMENTATION.md** - Extended project documentation
- **CURSOR.md** - Internal file for tracking changes and tasks (not saved in Git)
- **PROJECT_DOCUMENTATION.md** - Detailed project structure documentation

#### Directories

- **app/** - Main application module, contains all code and resources
- **gradle/** - Contains Gradle Wrapper files
- **.gradle/** - Gradle cache and working files (created automatically)
- **.git/** - Git version control system directory (created automatically)
- **.idea/** - Android Studio settings and files directory (created automatically)

### App Directory

The `app/` directory is the main application module and contains the following files and subdirectories:

#### Configuration Files

- **build.gradle** - Gradle script for the application module, defines dependencies, build settings, and application configuration
- **proguard-rules.pro** - ProGuard rules for code optimization and obfuscation in release builds
- **.gitignore** - Specific for the app module Git ignore rules

#### Directories

- **src/** - Application source code and resources
- **build/** - Generated build files (created automatically)

### Source Code (src)

The `app/src/` directory contains the following subdirectories:

- **main/** - Main application source code and resources
- **test/** - Unit tests
- **androidTest/** - Instrumented tests (for testing on a device)

### Java Code (java)

Project structure in tree format:

```
app/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── io/finett/myapplication/
│   │   │       ├── MainActivity.java                 # Main application screen
│   │   │       ├── VoiceChatActivity.java            # Voice assistant activity
│   │   │       ├── CameraActivity.java               # Camera activity and image analysis
│   │   │       ├── AccessibilitySettingsActivity.java # Accessibility settings
│   │   │       ├── adapter/                          # RecyclerView adapters
│   │   │       ├── api/                              # API interfaces and clients
│   │   │       ├── base/                             # Base classes
│   │   │       ├── model/                            # Data models
│   │   │       ├── util/                             # Utility classes
│   │   │       └── viewmodel/                        # ViewModel classes
│   │   ├── res/                                      # Application resources
│   │   │   ├── drawable/                             # Images and icons
│   │   │   ├── layout/                               # XML layouts of screens
│   │   │   ├── values/                               # Strings, colors, styles
│   │   │   ├── values-night/                         # Resources for night theme
│   │   │   └── xml/                                  # XML configurations
│   │   └── AndroidManifest.xml                       # Application manifest
│   ├── test/                                         # Unit tests
│   └── androidTest/                                  # Instrumented tests
└── build.gradle                                      # Build configuration
```

The `app/src/main/java/io/finett/myapplication/` directory contains the application Java code, structured as follows:

#### Main Activity Classes

- **MainActivity.java** - Main application screen, contains the main user interface
- **VoiceChatActivity.java** - Activity for voice communication with the assistant
- **CameraActivity.java** - Activity for working with the camera and image analysis
- **AccessibilitySettingsActivity.java** - Accessibility settings screen

#### Component Directories

- **adapter/** - Adapters for RecyclerView and other UI components
- **api/** - Interfaces and clients for working with APIs
- **base/** - Base classes and abstractions
- **model/** - Data models (POJO classes)
- **util/** - Utility classes and helper functions
- **viewmodel/** - ViewModel classes for MVVM architecture

#### Adapter Directory

Contains adapters for RecyclerView and other UI components:

- **VoiceChatAdapter.java** - Adapter for displaying messages in chat
- **SettingsAdapter.java** - Adapter for displaying accessibility settings

#### Api Directory

Contains classes for working with external APIs:

- **ApiClient.java** - Client for interacting with the assistant API
- **OpenRouterApi.java** - Retrofit interface for OpenRouter API
- **MessageRequest.java** - Class for forming requests to the API
- **MessageResponse.java** - Class for processing responses from the API

#### Base Directory

Contains base classes:

- **BaseActivity.java** - Base class for all activities
- **BaseAccessibilityActivity.java** - Base class for accessibility supporting activities

#### Model Directory

Contains data models:

- **ChatMessage.java** - Chat message model
- **Contact.java** - Phone functions contact model
- **RecognizedObject.java** - Camera recognized object model
- **AccessibilitySettings.java** - Accessibility settings model

#### Util Directory

Contains utility classes:

- **TextToSpeechHelper.java** - Helper class for text-to-speech synthesis
- **SpeechRecognitionHelper.java** - Helper class for speech recognition
- **ContactsHelper.java** - Helper class for working with contacts
- **BrowserHelper.java** - Helper class for working with Chrome Custom Tabs
- **PermissionHelper.java** - Helper class for requesting permissions
- **AccessibilityManager.java** - Accessibility functions manager

#### Viewmodel Directory

Contains ViewModel classes:

- **VoiceChatViewModel.java** - ViewModel for voice chat screen
- **CameraViewModel.java** - ViewModel for camera screen
- **SettingsViewModel.java** - ViewModel for settings screen

### Resources (res)

The `app/src/main/res/` directory contains the application resources:

#### Layout Directory

Contains XML layout files for activities and fragments:

- **activity_main.xml** - Main screen layout
- **activity_voice_chat.xml** - Voice chat screen layout
- **activity_camera.xml** - Camera screen layout
- **activity_accessibility_settings.xml** - Accessibility settings screen layout
- **item_chat_message_user.xml** - User message item layout
- **item_chat_message_assistant.xml** - Assistant message item layout
- **item_setting.xml** - Settings item layout

#### Values Directory

Contains XML files with various resources:

- **strings.xml** - String resources
- **colors.xml** - Color definitions
- **themes.xml** - Application theme definitions
- **styles.xml** - Style definitions
- **dimens.xml** - Size definitions
- **arrays.xml** - Value arrays

#### Values-night Directory

Contains XML files with resources for night/dark theme:

- **colors.xml** - Color definitions for dark theme
- **themes.xml** - Theme definitions for dark theme

#### Drawable Directory

Contains graphical resources and XML descriptions:

- **robot_background.xml** - Robot background image
- **mic_button_background.xml** - Microphone button background
- **message_background_user.xml** - User message background
- **message_background_assistant.xml** - Assistant message background
- **ic_camera.xml** - Camera icon
- **ic_mic.xml** - Microphone icon
- **ic_settings.xml** - Settings icon
- **ic_send.xml** - Send icon
- **rounded_corner.xml** - Rounded corners shape

#### Xml Directory

Contains XML configuration files:

- **backup_rules.xml** - Rules for data backup
- **data_extraction_rules.xml** - Data extraction rules
- **file_paths.xml** - FileProvider path definitions
- **accessibility_service_config.xml** - Accessibility service configuration

#### mipmap Directories

Contain application icons in different resolutions:

- **mipmap-mdpi/** - Icons for screens with low resolution
- **mipmap-hdpi/** - Icons for screens with high resolution
- **mipmap-xhdpi/** - Icons for screens with very high resolution
- **mipmap-xxhdpi/** - Icons for screens with very-very high resolution
- **mipmap-xxxhdpi/** - Icons for screens with maximum resolution
- **mipmap-anydpi-v26/** - Adaptive icons for Android 8.0+

#### Menu Directory

Contains XML menu files:

- **main_menu.xml** - Main screen menu
- **overflow_menu.xml** - Additional menu

### AndroidManifest.xml

The `app/src/main/AndroidManifest.xml` file is a key file for Android application and contains:

- Application package declaration (`io.finett.myapplication`)
- Version and required SDK information
- Permission declarations (RECORD_AUDIO, READ_CONTACTS, CALL_PHONE, SEND_SMS, READ_PHONE_STATE, CAMERA, INTERNET, VIBRATE)
- Application activity declarations
- Theme and screen orientation settings
- Service and content provider declarations
- Application metadata

### Tests

#### Test Directory

The `app/src/test/` directory contains unit tests for the application:

- **java/io/finett/myapplication/** - Unit tests for various components

#### androidTest Directory

The `app/src/androidTest/` directory contains instrumented tests:

- **java/io/finett/myapplication/** - Instrumented tests for user interface and integration tests

## Features

### Voice Control

#### Command Recognition and Processing
- Using Android Speech Recognition API
- Support for the Russian language
- Automatic recording stop when silent (3 seconds)
- Optimized speech command processing

#### Voice Response
- Using TextToSpeech for speech synthesis
- Customizable speech speed
- Visual indication of recording and playback process

#### Example Voice Commands
- "Позвони [имя контакта]" - Making a call
- "Отправь SMS [имя контакта] с текстом [сообщение]" - Sending a message
- "Открой [url]" - Opening a web page
- "Найди [запрос]" - Searching in Google
- "Включи камеру" - Starting the camera for analysis
- "Посмотри что на камере" - Analyzing objects through the camera

### Telephony and Messages

#### Phone Calls
- Voice dialing
- Contact search by name
- Call confirmation before making a call
- Handling various command formulations

#### SMS Messages
- Sending SMS to a number or contact
- Supporting arbitrary message text
- Checking and confirming before sending
- Protection against accidental sending

### Web Browser

#### Integration with Chrome Custom Tabs
- Built-in web page viewing
- Application context preservation
- Fast page loading

#### URL and Search Query Handling
- Support for direct URLs ("open google.com")
- Support for search queries ("find a borch recipe")
- Automatic addition of https:// to links
- Fallback to default browser if Chrome is not installed

### Camera Operations

#### Image Analysis
- Integration with CameraX for working with the camera
- Using ML models for object recognition
- Voice description of recognized objects

#### Special Camera Features
- Automatic shooting when an object is detected
- Voice prompts when working with the camera
- Vibro-feedback

### Accessibility

#### Accessibility Settings
- Text size adjustment (very small, small, normal, large, very large)
- High contrast mode
- Voice feedback
- Vibro-feedback

#### System Themes
- Regular theme
- High contrast theme for people with visual impairments
- Switching through settings or menu
- Saving selected theme

#### Interface Adaptation
- Special structure for screen readers
- Descriptive labels for UI elements
- Logical navigation with keyboard

## Technical Details

### Architecture

The application uses MVVM architecture (Model-View-ViewModel):
- **Model**: Classes in the `model` package
- **View**: XML layouts and Activity classes
- **ViewModel**: Classes in the `viewmodel` package for handling business logic

### Technologies and Libraries

- **Programming Language**: Java
- **Jetpack Components**:
  - ViewModel
  - LiveData
  - ViewBinding
  - Preferences
- **Network**:
  - Retrofit for API requests
  - OkHttp for HTTP client
  - Gson for JSON serialization/deserialization
- **UI**:
  - Material Design components
  - RecyclerView for displaying lists
  - CardView for message cards
  - ConstraintLayout for flexible layout
- **Multimedia**:
  - CameraX for working with the camera
  - TextToSpeech for speech synthesis
  - SpeechRecognizer for speech recognition
- **Images**:
  - Glide for image loading and caching
- **Web**:
  - Chrome Custom Tabs for built-in web browser
- **Data Storage**:
  - SharedPreferences for settings
  - Serialization/deserialization for chat history storage

### API Integration

The application integrates with OpenRouter API for chat request processing:
- Using Retrofit for API calls
- Sending and receiving messages through RESTful API
- Error handling and retry attempts
- Support for multi-modal messages (text + images)

### Permission System

The application requests the following permissions:
- `RECORD_AUDIO`: For speech recognition
- `READ_CONTACTS`: For access to contacts
- `CALL_PHONE`: For making calls
- `SEND_SMS`: For sending messages
- `READ_PHONE_STATE`: For working with telephony
- `CAMERA`: For access to the camera
- `INTERNET`: For working with the network
- `VIBRATE`: For vibro-feedback

### Important Classes

#### MainActivity.java

Main application class, responsible for:
- Initializing the user interface
- Handling basic user interactions
- Navigating between different parts of the application
- Handling permissions and settings

#### VoiceChatActivity.java

Voice chat activity class, responsible for:
- Recognizing voice commands
- Sending requests to the assistant API
- Displaying messages in chat
- Speech synthesis for assistant responses
- Handling commands for calls, SMS, and browser

#### CameraActivity.java

Camera activity class, responsible for:
- Working with CameraX API
- Capturing images from the camera
- Sending images for analysis
- Displaying recognized objects
- Supporting automatic shooting in accessibility mode

#### AccessibilitySettingsActivity.java

Class for managing accessibility settings, responsible for:
- Text size adjustment
- High contrast mode activation/deactivation
- Voice and vibro-feedback settings
- Saving user preferences

#### AccessibilityManager.java

Class for managing accessibility functions, responsible for:
- Applying accessibility settings in all activities
- Text scaling
- Switching between regular and high contrast themes
- Providing feedback to the user

#### CommandProcessor.java

Class for processing voice commands, responsible for:
- Recognizing different types of commands (calls, SMS, browser)
- Extracting parameters from command text
- Performing corresponding actions
- Generating assistant responses

## Build and Run

### Build via Android Studio
1. Open the project in Android Studio
2. Select Build > Make Project to build the project
3. Select Run > Run 'app' to run on a device or emulator

### Build via Command Line
```bash
# Build debug version
./gradlew assembleDebug

# Build release version
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

### Release Build Configuration
To create a release version, you need to:
1. Create a keystore file for signing the application
2. Specify signing parameters in the `gradle.properties` file or via environment variables
3. Run build via `./gradlew assembleRelease`

## Contributing

### Change Contribution Process
1. Fork the repository
2. Create a branch for new functionality (`git checkout -b feature/amazing-feature`)
3. Make changes and commit them (`git commit -m 'Add some amazing feature'`)
4. Push changes to your fork (`git push origin feature/amazing-feature`)
5. Create Pull Request in the main repository

### Code Formatting Rules
- Follow Java formatting standards
- Document public API methods and classes
- Write unit tests for new functionality
- Monitor performance and resource usage

## License

The project is distributed under the MIT license. See the LICENSE file for additional information.

## Contacts

- Telegram: [@finet_f](https://t.me/finet_f)
- GitHub: [github.com/finettt](https://github.com/finettt)

## Change History

### 16.03.2024: Creation of Complete Project Documentation
1. Created comprehensive documentation file:
   - PROJECT_DOCUMENTATION.md with detailed description of all files and directories
   - Structured description of each component
   - Explanation of the purpose of all key files
   - Application architecture description
   
2. Improved overall documentation:
   - Merged content into DOCUMENTATION.md
   - Detailed description of application resources
   - Documented all packages and modules
   - Added configuration file documentation

### 16.03.2024: ActionBar and Toolbar Conflict Resolution
1. Changed application parent theme:
   - Replaced Theme.MaterialComponents.Light.DarkActionBar with Theme.MaterialComponents.Light.NoActionBar
   - Removed duplicated ActionBar when using Toolbar
   - Fixed application launch issue

2. Affected files:
   - `values/themes.xml` - changed parent style
   - `MainActivity.java` - left unchanged as Toolbar setup is correct

### 16.03.2024: Application Color Scheme Update
1. Changed application color scheme to purple-magenta:
   - Main color: #6200EE (saturated purple)
   - Dark main color: #3700B3 (dark purple)
   - Accent color: #FF03DAC5 (turquoise)
   - Application background: #F7F2FA (light purple)
   
2. Updated message colors:
   - User message background: #EDE7F6 (light purple)
   - User message text: #4A148C (deep purple)

3. Affected files:
   - `values/colors.xml` - changed color values

### 16.03.2024: Application Dark Theme Addition
1. Implemented full-fledged application dark theme:
   - Main color of dark theme: #BB86FC (light purple)
   - Dark background: #121212 (almost black)
   - Interface surfaces: #1F1F1F (dark gray)
   - Text: #E6E6E6 (light gray)
   
2. Configured message colors in dark theme:
   - User message background: #311B92 (deep purple)
   - Assistant message background: #1F1F1F (dark gray)
   - Message text: #D1C4E9 (light purple)

3. Affected files:
   - `values/colors.xml` - added new colors for dark theme
   - `values-night/colors.xml` - created file with color overrides
   - `values-night/themes.xml` - updated dark theme configuration

### 16.03.2024: Improved Automatic Shooting Logic
1. Changed behavior based on accessibility settings:
   - When automatic shooting is enabled:
     - Photo is taken automatically after 1.5 seconds
     - Shooting button hides
     - Progress indicator shows
     - User is notified about upcoming shooting
   - When automatic shooting is disabled:
     - User must press the button to shoot
     - Shooting button is visible
     - Progress indicator shows only during analysis
     - User is notified about the need to press the button

2. Improved error handling:
   - When shooting or analysis fails, interface state is restored
   - Shooting button becomes active again in case of error
   - Informative error messages are displayed

3. Affected files:
   - `CameraActivity.java` - changed camera working logic

### 16.03.2024: Added Project Cleanup Scripts
1. Created Gradle script for full project cleanup:
   - Deleting all build directories
   - Deleting Gradle cache (.gradle)
   - Deleting IDE caches (.idea/caches, .idea/libraries)
   - Deleting generated files and local settings

2. Added helper scripts for running:
   - Windows: clean-project.bat
   - Linux/macOS: clean-project.sh
   
3. Script features:
   - Interactive mode with confirmation (cleanAllInteractive)
   - Detailed cleanup process diagnostics
   - Checking for necessary files
   - Integration with standard clean task

4. Affected files:
   - `clean.gradle` - main Gradle cleanup script
   - `clean-project.bat` - script for Windows
   - `clean-project.sh` - script for Linux/macOS

### 16.03.2024: Improved Project Cleanup Scripts
1. Fixed problem with inability to delete .gradle files:
   - Added error handling for all deletion operations
   - Implemented protective logic for skipping blocked files
   - Added informative warnings for user

2. Created new interactive scripts for running:
   - `clean-project-fixed.bat` for Windows
   - `clean-project-fixed.sh` for Linux/macOS
   - Interactive menu for selecting cleanup mode

3. Added function for generating full cleanup scripts without Gradle:
   - `full-clean.bat` for Windows
   - `full-clean.sh` for Linux/macOS
   - These scripts can be run when Android Studio and Gradle are fully closed
   - Allow deleting all files, even those blocked by running Gradle

4. Updated documentation with instructions for all cleanup options

5. Affected files:
   - `clean.gradle` - added error handling and new generateFullCleanScript task
   - `clean-project-fixed.bat` - new improved script for Windows
   - `clean-project-fixed.sh` - new improved script for Linux/macOS
   - `README-clean.md` - updated documentation

## Future Plans

Planned project improvements include:
- Adding visited link history
- Supporting other browsers
- Improving URL recognition in voice commands
- Adding bookmarks and favorite sites
- Adding additional voice commands
- Improving object recognition
- Expanding accessibility settings