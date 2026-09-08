# Workout Launcher

Minimal Android app for a Samsung Galaxy S21.

## Behavior
- First launch: choose an MP3 using Android's file picker.
- The URI permission is saved persistently.
- Later taps on the Workout icon immediately start the chosen MP3.
- Playback runs in a foreground media service so it continues while YouTube is open.
- The app deliberately does not request Android audio focus. This is intended to work with Samsung SoundAssistant > Multi sound so Workout and YouTube/YouTube Music can play together.
- The playback notification has a Stop action.

## Build
Open this folder in Android Studio and choose Build > Build APK(s), or run `./gradlew assembleDebug` after adding a Gradle wrapper.

## Samsung setup
After installation, open SoundAssistant > Multi sound and enable the `Workout` app (or enable all apps).
