# Workout Player v1.1

Minimal Android workout-audio player designed for a Samsung Galaxy S21.

## Behavior
- First launch: choose an MP3 using Android's file picker.
- The selected MP3 is remembered persistently.
- Opening the app does **not** start playback automatically; tap Play when ready.
- Playback continues in the background while YouTube/YouTube Music is open.
- Play / Pause / Resume.
- Skip backward 15 seconds and forward 15 seconds.
- Seek bar with elapsed and total time.
- Stop button in the app.
- Notification controls for -15s, Pause/Resume, +15s and Stop.
- Change the selected workout MP3 at any time.
- The app deliberately does not request Android audio focus. This is intended to work with Samsung SoundAssistant > Multi sound so Workout and YouTube/YouTube Music can play together.

## Samsung setup
After installation, open SoundAssistant > Multi sound and enable the `Workout` app (or enable all apps).

## Build APK with GitHub Actions
1. Upload/replace these files in the existing GitHub repository.
2. Open **Actions > Build Workout APK**.
3. Click **Run workflow**.
4. When it completes, download the **Workout-apk** artifact.
5. Extract `app-debug.apk` and install it on the phone.

The package/application ID is unchanged (`com.levent.workout`), so the new APK should install as an update over the previous debug build when built by GitHub Actions with the same standard debug signing setup. Your previously selected MP3 should remain saved.
