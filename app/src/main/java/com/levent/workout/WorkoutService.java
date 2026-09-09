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
    public static final String ACTION_PAUSE = "com.levent.workout.PAUSE";
    public static final String ACTION_RESUME = "com.levent.workout.RESUME";
    public static final String ACTION_STOP = "com.levent.workout.STOP";
    public static final String ACTION_SEEK_BACK = "com.levent.workout.SEEK_BACK";
    public static final String ACTION_SEEK_FORWARD = "com.levent.workout.SEEK_FORWARD";
    public static final String ACTION_SEEK_TO = "com.levent.workout.SEEK_TO";
    public static final String EXTRA_POSITION_MS = "position_ms";

    private static final String CHANNEL_ID = "workout_playback";
    private static final int NOTIFICATION_ID = 10;
    private static final int SEEK_AMOUNT_MS = 15_000;

    private MediaPlayer player;
    private Uri currentUri;

    private static volatile boolean preparedState = false;
    private static volatile boolean playingState = false;
    private static volatile int positionState = 0;
    private static volatile int durationState = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Workout playback",
                NotificationManager.IMPORTANCE_LOW
        );
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) return START_NOT_STICKY;
        String action = intent.getAction();

        switch (action) {
            case ACTION_PLAY:
                Uri uri = intent.getData();
                if (uri != null) {
                    currentUri = uri;
                    startForeground(NOTIFICATION_ID, buildNotification("Preparing..."));
                    play(uri);
                }
                break;
            case ACTION_PAUSE:
                pausePlayback();
                break;
            case ACTION_RESUME:
                resumePlayback();
                break;
            case ACTION_SEEK_BACK:
                seekBy(-SEEK_AMOUNT_MS);
                break;
            case ACTION_SEEK_FORWARD:
                seekBy(SEEK_AMOUNT_MS);
                break;
            case ACTION_SEEK_TO:
                seekTo(intent.getIntExtra(EXTRA_POSITION_MS, 0));
                break;
            case ACTION_STOP:
                stopPlayback();
                break;
        }

        updateState();
        return START_NOT_STICKY;
    }

    private void play(Uri uri) {
        stopPlayerOnly();
        preparedState = false;
        playingState = false;
        positionState = 0;
        durationState = 0;

        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());

            // Deliberately do not request audio focus. Samsung SoundAssistant Multi sound
            // can then keep YouTube/YouTube Music and Workout audible together.
            player.setDataSource(this, uri);
            player.setOnPreparedListener(mp -> {
                preparedState = true;
                durationState = mp.getDuration();
                mp.start();
                playingState = true;
                updateState();
                updateNotification();
            });
            player.setOnCompletionListener(mp -> stopPlayback());
            player.setOnErrorListener((mp, what, extra) -> {
                stopPlayback();
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            stopPlayback();
        }
    }

    private void pausePlayback() {
        if (player != null && preparedState && player.isPlaying()) {
            player.pause();
            updateState();
            updateNotification();
        }
    }

    private void resumePlayback() {
        if (player != null && preparedState && !player.isPlaying()) {
            player.start();
            updateState();
            updateNotification();
        }
    }

    private void seekBy(int deltaMs) {
        if (player == null || !preparedState) return;
        int target = player.getCurrentPosition() + deltaMs;
        seekTo(target);
    }

    private void seekTo(int targetMs) {
        if (player == null || !preparedState) return;
        int duration = Math.max(player.getDuration(), 0);
        int target = Math.max(0, Math.min(targetMs, duration));
        player.seekTo(target);
        positionState = target;
        durationState = duration;
        updateNotification();
    }

    private void updateState() {
        if (player != null && preparedState) {
            try {
                playingState = player.isPlaying();
                positionState = player.getCurrentPosition();
                durationState = player.getDuration();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private void updateNotification() {
        if (player == null || !preparedState) return;
        updateState();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(playingState ? "Playing" : "Paused"));
    }

    private Notification buildNotification(String status) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        PendingIntent backPending = servicePendingIntent(ACTION_SEEK_BACK, 2);
        PendingIntent playPausePending = servicePendingIntent(playingState ? ACTION_PAUSE : ACTION_RESUME, 3);
        PendingIntent forwardPending = servicePendingIntent(ACTION_SEEK_FORWARD, 4);
        PendingIntent stopPending = servicePendingIntent(ACTION_STOP, 5);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.levent.workout.R.drawable.ic_workout)
                .setContentTitle("Workout")
                .setContentText(status)
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "-15s", backPending).build())
                .addAction(new Notification.Action.Builder(null, playingState ? "Pause" : "Resume", playPausePending).build())
                .addAction(new Notification.Action.Builder(null, "+15s", forwardPending).build())
                .addAction(new Notification.Action.Builder(null, "Stop", stopPending).build())
                .build();
    }

    private PendingIntent servicePendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, WorkoutService.class).setAction(action);
        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private void stopPlayerOnly() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
        preparedState = false;
        playingState = false;
        positionState = 0;
        durationState = 0;
    }

    private void stopPlayback() {
        stopPlayerOnly();
        currentUri = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopPlayerOnly();
        super.onDestroy();
    }

    public static boolean isPrepared() {
        return preparedState;
    }

    public static boolean isPlaying() {
        return playingState;
    }

    public static int getPositionMs() {
        return positionState;
    }

    public static int getDurationMs() {
        return durationState;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
