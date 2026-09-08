package com.levent.workout;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;

public class WorkoutService extends Service {
    public static final String ACTION_PLAY = "com.levent.workout.PLAY";
    public static final String ACTION_STOP = "com.levent.workout.STOP";
    private static final String CHANNEL_ID = "workout_playback";
    private static final int NOTIFICATION_ID = 10;
    private MediaPlayer player;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Workout playback", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopPlayback();
            return START_NOT_STICKY;
        }

        Uri uri = intent != null ? intent.getData() : null;
        if (uri == null) return START_NOT_STICKY;

        startForeground(NOTIFICATION_ID, buildNotification());
        play(uri);
        return START_NOT_STICKY;
    }

    private void play(Uri uri) {
        stopPlayerOnly();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            // Intentionally do NOT request audio focus. On Samsung with SoundAssistant Multi Sound,
            // this allows YouTube/YouTube Music and Workout to remain audible together.
            player.setDataSource(this, uri);
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp -> stopPlayback());
            player.setOnErrorListener((mp, what, extra) -> { stopPlayback(); return true; });
            player.prepareAsync();
        } catch (Exception e) {
            stopPlayback();
        }
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, WorkoutService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 2, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.levent.workout.R.drawable.ic_workout)
                .setContentTitle("Workout")
                .setContentText("Workout audio is playing")
                .setContentIntent(openPending)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Stop", stopPending).build())
                .build();
    }

    private void stopPlayerOnly() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    private void stopPlayback() {
        stopPlayerOnly();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopPlayerOnly();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
