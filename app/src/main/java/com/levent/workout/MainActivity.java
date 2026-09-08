package com.levent.workout;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 1001;
    private static final int NOTIFY_PERMISSION = 1002;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("workout", MODE_PRIVATE);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFY_PERMISSION);
        }

        String saved = prefs.getString("audio_uri", null);
        if (saved == null) {
            showSetup();
        } else {
            startWorkout(Uri.parse(saved));
            finish();
        }
    }

    private void showSetup() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Workout");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        layout.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(this);
        info.setText("Choose your workout MP3 once. Future taps on the Workout icon will start it immediately.");
        info.setTextSize(17);
        info.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2);
        infoParams.setMargins(0, pad, 0, pad);
        layout.addView(info, infoParams);

        Button choose = new Button(this);
        choose.setText("Choose workout MP3");
        choose.setOnClickListener(v -> pickAudio());
        layout.addView(choose, new LinearLayout.LayoutParams(-1, -2));

        setContentView(layout);
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
            prefs.edit().putString("audio_uri", uri.toString()).apply();
            Toast.makeText(this, "Workout audio saved", Toast.LENGTH_SHORT).show();
            startWorkout(uri);
            finish();
        }
    }

    private void startWorkout(Uri uri) {
        Intent service = new Intent(this, WorkoutService.class);
        service.setAction(WorkoutService.ACTION_PLAY);
        service.setData(uri);
        startForegroundService(service);
    }
}
