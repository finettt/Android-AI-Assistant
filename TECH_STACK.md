# Alan AI Assistant - Technical Stack

This document provides a comprehensive overview of the technologies used in the Alan AI Assistant Android application.

## Core Technologies

### Programming Languages
- **Java** - Primary programming language for application development
- **XML** - Used for layouts and resource definitions

### Android SDK & Core Components
- **Minimum SDK** - Android 7.0 (API Level 24)
- **Target SDK** - Android 14 (API Level 34)
- **Compile SDK** - Android 14 (API Level 34)
- **Gradle** - Build automation tool (latest version)
- **View Binding** - Used for safe view access
- **Java 17** - Source / target compatibility (desugared for API 24)

## Libraries & Frameworks

### UI/UX
- **Material Design Components** - Modern UI elements and styling
- **ConstraintLayout** - Flexible layout system
- **AppCompat** - Backward compatibility for newer Android features
- **Animations** - Core animation and graphics-android libraries
- **Chrome Custom Tabs** - In-app web browsing

### Networking
- **Retrofit 2.9.0** - Type-safe HTTP client
- **OkHttp 4.11.0** - HTTP client for network operations
- **Gson Converter** - JSON serialization/deserialization

### Image Processing
- **Glide 4.16.0** - Image loading and caching library

### Camera
- **CameraX 1.3.2** - Jetpack camera library (core, camera2, lifecycle, view)

### Permissions & Privacy
- **AndroidX Activity 1.8.1** – Modern `ActivityResultLauncher` API for runtime permissions
- **Google Accompanist Permissions 0.33.2** – Declarative permission handling

### NLP & Contact Search
- **Custom fuzzy-match utility** – Damerau-Levenshtein implementation for contact name resolution

### Data Management
- **Lifecycle Components 2.7.0** - ViewModel and LiveData
- **SharedPreferences** - Local data storage
- **Preference 1.2.1** - Settings screens

### Text Processing
- **Markwon 4.6.2** - Markdown rendering library
  - core
  - html
  - image

### Voice & Accessibility
- **SpeechRecognizer** – Built-in speech recognition (inline & overlay)
- **TextToSpeech** – Built-in speech synthesis
- **Accessibility Service** – Custom service for gesture activation & screen reading
- **Emoji2 1.4.0** – Compatible emoji rendering across Android versions

### External APIs
- **OpenRouter API** - AI model integration (Qwen 3)
- **Weather API** - Weather information services
- **Geocoding API** - Location services

## Testing
- **JUnit** - Unit testing framework
- **Espresso** - UI testing
- **AndroidX Test** - Android testing utilities

## Build Variants
- **Debug** – Development build with logging
- **Release** – Optimized, proguard-minified build
- **Internal** – Demo build with embedded API key & relaxed permission checks

## Security
- **External Properties File** - Secure API key storage
- **BuildConfig** - Runtime configuration for development/production environments

## Architecture Components
- **Services** - Background processing
- **Broadcast Receivers** - System event handling
- **Activities** - User interface screens
- **Adapters** - Data binding for UI components
- **Models** - Data structures
- **Utilities** - Helper functions

## Codebase Statistics (v1.4.0)

| Metric | Value |
|--------|-------|
| Java source files | **68** |
| Total Java LOC | **18 190** |
| Activities | **14** |
| Services | **5** |
| Broadcast Receivers | **3** |
| Adapter / Model / Util classes | ~**40** |
| XML layout resources | **29** |
| Total string resources (values*) | 400+ |
| Gradle modules | 1 (single–module app) |

> LOC counted with PowerShell `Get-ChildItem ... | Get-Content | Measure-Object -Line` across `app/src/main/java/io/finett/myapplication`.

The project overall weighs **≈2 MB** of source code (Java + XML) and compiles to a **~35 MB** APK in *release* mode. 