package com.levent.workout;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 1001;
    private static final int NOTIFY_PERMISSION = 1002;

    private SharedPreferences prefs;
    private Uri audioUri;
    private TextView fileNameView;
    private TextView statusView;
    private TextView timeView;
    private SeekBar seekBar;
    private Button playPauseButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean userSeeking = false;

    private final Runnable refreshUi = new Runnable() {
        @Override
        public void run() {
            updatePlaybackUi();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("workout", MODE_PRIVATE);

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFY_PERMISSION);
        }

        String saved = prefs.getString("audio_uri", null);
        if (saved != null) {
            audioUri = Uri.parse(saved);
        }
        showPlayer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshUi);
        handler.post(refreshUi);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshUi);
        super.onPause();
    }

    private void showPlayer() {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (24 * density);
        int smallGap = (int) (10 * density);
        int gap = (int) (18 * density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Workout");
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        fileNameView = new TextView(this);
        fileNameView.setTextSize(16);
        fileNameView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(-1, -2);
        fileParams.setMargins(0, gap, 0, smallGap);
        root.addView(fileNameView, fileParams);

        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setGravity(Gravity.CENTER);
        root.addView(statusView, new LinearLayout.LayoutParams(-1, -2));

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(-1, -2);
        seekParams.setMargins(0, gap, 0, 0);
        root.addView(seekBar, seekParams);

        timeView = new TextView(this);
        timeView.setText("0:00 / 0:00");
        timeView.setTextSize(14);
        timeView.setGravity(Gravity.CENTER);
        root.addView(timeView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(-1, -2);
        controlsParams.setMargins(0, gap, 0, 0);
        root.addView(controls, controlsParams);

        Button backButton = new Button(this);
        backButton.setText("-15s");
        backButton.setOnClickListener(v -> sendSimpleAction(WorkoutService.ACTION_SEEK_BACK));
        controls.addView(backButton, weightedButtonParams());

        playPauseButton = new Button(this);
        playPauseButton.setText("Play");
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        controls.addView(playPauseButton, weightedButtonParams());

        Button forwardButton = new Button(this);
        forwardButton.setText("+15s");
        forwardButton.setOnClickListener(v -> sendSimpleAction(WorkoutService.ACTION_SEEK_FORWARD));
        controls.addView(forwardButton, weightedButtonParams());

        Button stopButton = new Button(this);
        stopButton.setText("Stop");
        stopButton.setOnClickListener(v -> sendSimpleAction(WorkoutService.ACTION_STOP));
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(-1, -2);
        stopParams.setMargins(0, smallGap, 0, 0);
        root.addView(stopButton, stopParams);

        Button chooseButton = new Button(this);
        chooseButton.setText(audioUri == null ? "Choose workout MP3" : "Change workout MP3");
        chooseButton.setOnClickListener(v -> pickAudio());
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(-1, -2);
        chooseParams.setMargins(0, gap, 0, 0);
        root.addView(chooseButton, chooseParams);

        TextView hint = new TextView(this);
        hint.setText("Workout does not start automatically. Tap Play when ready. Use Samsung SoundAssistant > Multi sound to keep YouTube audible at the same time.");
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.setMargins(0, gap, 0, 0);
        root.addView(hint, hintParams);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int duration = WorkoutService.getDurationMs();
                    if (duration > 0) {
                        int requested = (int) ((progress / 1000.0) * duration);
                        timeView.setText(formatTime(requested) + " / " + formatTime(duration));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int duration = WorkoutService.getDurationMs();
                if (duration > 0) {
                    int requested = (int) ((seekBar.getProgress() / 1000.0) * duration);
                    Intent intent = new Intent(MainActivity.this, WorkoutService.class);
                    intent.setAction(WorkoutService.ACTION_SEEK_TO);
                    intent.putExtra(WorkoutService.EXTRA_POSITION_MS, requested);
                    startService(intent);
                }
                userSeeking = false;
            }
        });

        setContentView(root);
        updatePlaybackUi();
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        int margin = (int) (4 * getResources().getDisplayMetrics().density);
        p.setMargins(margin, 0, margin, 0);
        return p;
    }

    private void togglePlayPause() {
        if (audioUri == null) {
            pickAudio();
            return;
        }

        if (WorkoutService.isPlaying()) {
            sendSimpleAction(WorkoutService.ACTION_PAUSE);
        } else if (WorkoutService.isPrepared()) {
            sendSimpleAction(WorkoutService.ACTION_RESUME);
        } else {
            Intent service = new Intent(this, WorkoutService.class);
            service.setAction(WorkoutService.ACTION_PLAY);
            service.setData(audioUri);
            startForegroundService(service);
        }
    }

    private void sendSimpleAction(String action) {
        Intent intent = new Intent(this, WorkoutService.class);
        intent.setAction(action);
        startService(intent);
    }

    private void pickAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_AUDIO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
            audioUri = uri;
            prefs.edit().putString("audio_uri", uri.toString()).apply();
            Toast.makeText(this, "Workout audio selected", Toast.LENGTH_SHORT).show();
            sendSimpleAction(WorkoutService.ACTION_STOP);
            showPlayer();
        }
    }

    private void updatePlaybackUi() {
        if (fileNameView == null) return;

        if (audioUri == null) {
            fileNameView.setText("No workout audio selected");
            statusView.setText("Choose an MP3 to begin");
            playPauseButton.setText("Play");
            seekBar.setEnabled(false);
            return;
        }

        fileNameView.setText(getDisplayName(audioUri));

        boolean prepared = WorkoutService.isPrepared();
        boolean playing = WorkoutService.isPlaying();
        int position = WorkoutService.getPositionMs();
        int duration = WorkoutService.getDurationMs();

        if (playing) {
            statusView.setText("Playing");
            playPauseButton.setText("Pause");
        } else if (prepared) {
            statusView.setText("Paused");
            playPauseButton.setText("Resume");
        } else {
            statusView.setText("Ready");
            playPauseButton.setText("Play");
        }

        seekBar.setEnabled(duration > 0);
        if (!userSeeking) {
            int progress = duration > 0 ? (int) ((position * 1000L) / duration) : 0;
            seekBar.setProgress(progress);
            timeView.setText(formatTime(position) + " / " + formatTime(duration));
        }
    }

    private String getDisplayName(Uri uri) {
        String result = "Workout audio";
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) result = cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }

    private String formatTime(int millis) {
        if (millis < 0) millis = 0;
        int totalSeconds = millis / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }
}
