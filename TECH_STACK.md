# Alan AI Assistant - Technical Stack

This document provides a comprehensive overview of the technologies used in the Alan AI Assistant Android application.

## Core Technologies

### Programming Languages
- **Java** - Primary programming language for application development
- **XML** - Used for layouts and resource definitions

### Android SDK & Core Components
- **Minimum SDK** - Android 7.0 (API Level 24)
- **Target SDK** - Android 15 (API Level 35)
- **Compile SDK** - Android 15 (API Level 35)
- **Gradle** - Build automation tool (latest version)
- **View Binding** - Used for safe view access
- **Java 11** - Java compatibility level

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
- **CameraX 1.3.1** - Jetpack camera library
  - camera-core
  - camera-camera2
  - camera-lifecycle
  - camera-view

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
- **SpeechRecognizer** - Android's built-in speech recognition
- **TextToSpeech** - Android's built-in text-to-speech
- **Accessibility Services** - Custom implementation for accessibility features

### External APIs
- **OpenRouter API** - AI model integration (Qwen 3)
- **Weather API** - Weather information services
- **Geocoding API** - Location services

## Testing
- **JUnit** - Unit testing framework
- **Espresso** - UI testing
- **AndroidX Test** - Android testing utilities

## Build Variants
- **Debug** - Standard development build
- **Release** - Production-ready build with optimizations
- **Internal** - Demo build with built-in API keys

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