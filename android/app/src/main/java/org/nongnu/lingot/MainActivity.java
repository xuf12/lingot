/*
 * lingot, a musical instrument tuner.
 *
 * This file is part of lingot.
 *
 * lingot is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package org.nongnu.lingot;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {

    private static final int REQUEST_RECORD_AUDIO = 1;
    private static final long POLL_PERIOD_MS = Math.round(1000.0 / LingotNative.GAUGE_SAMPLING_RATE);
    private static final long RESTART_DELAY_MS = 1000;
    private static final String PREF_RANGE = "range";

    /** Instrument frequency ranges, the first one is the desktop default (E2 - E4). */
    private static final int[] RANGE_NAMES = {
            R.string.range_guitar, R.string.range_bass, R.string.range_violin, R.string.range_wide,
    };
    private static final double[][] RANGES = {
            {82.407, 329.6276},
            {41.203, 97.999},
            {196.0, 659.255},
            {65.406, 1046.502},
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final double[] result = new double[3];
    private final float[] spectrum = new float[4096];

    private TunerView tunerView;
    private TextView statusView;
    private int range;
    private boolean resumed;
    private boolean tuning;
    private long restartAt;
    private String lastMessage;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            handler.postDelayed(this, POLL_PERIOD_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        range = getPreferences(MODE_PRIVATE).getInt(PREF_RANGE, 0);
        if (range < 0 || range >= RANGES.length) {
            range = 0;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff101418);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // edge-to-edge is enforced from Android 15, keep clear of the system bars
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            });
        }

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        final int pad = Math.round(8 * getResources().getDisplayMetrics().density);
        toolbar.setPadding(2 * pad, pad, 2 * pad, 0);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(20);
        title.setTextColor(0xffe8eaed);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        String[] names = new String[RANGE_NAMES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = getString(RANGE_NAMES[i]);
        }
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(range);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != range) {
                    range = position;
                    getPreferences(MODE_PRIVATE).edit().putInt(PREF_RANGE, range).apply();
                    if (tuning) {
                        startTuner();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        toolbar.addView(spinner);
        root.addView(toolbar);

        statusView = new TextView(this);
        statusView.setTextColor(0xffffb74d);
        statusView.setPadding(2 * pad, pad, 2 * pad, 0);
        statusView.setVisibility(View.GONE);
        root.addView(statusView);

        tunerView = new TunerView(this);
        root.addView(tunerView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startTuner();
        } else {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        stopTuner();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQUEST_RECORD_AUDIO) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (resumed) {
                startTuner();
            }
        } else {
            showStatus(getString(R.string.permission_denied));
        }
    }

    private void startTuner() {
        handler.removeCallbacks(ticker);
        tuning = true;
        restartAt = 0;
        lastMessage = null;
        if (LingotNative.start(RANGES[range][0], RANGES[range][1])) {
            showStatus(null);
        } else {
            scheduleRestart();
        }
        dispatchMessages();
        handler.post(ticker);
    }

    private void stopTuner() {
        tuning = false;
        handler.removeCallbacks(ticker);
        LingotNative.stop();
    }

    private void scheduleRestart() {
        restartAt = SystemClock.uptimeMillis() + RESTART_DELAY_MS;
        showStatus(getString(R.string.audio_unavailable));
    }

    private void tick() {
        if (!tuning) {
            return;
        }

        // the audio stream may be interrupted (e.g. an input device is plugged
        // or another app takes the microphone), try to reopen it periodically.
        if (restartAt == 0 && !LingotNative.isRunning()) {
            scheduleRestart();
        } else if (restartAt != 0 && SystemClock.uptimeMillis() >= restartAt) {
            restartAt = 0;
            if (LingotNative.start(RANGES[range][0], RANGES[range][1])) {
                showStatus(null);
            } else {
                scheduleRestart();
            }
        }

        String note = LingotNative.poll(result);
        tunerView.setResult(note, (int) result[2], result[0], result[1], LingotNative.gaugeRange());
        int n = LingotNative.spectrum(spectrum);
        tunerView.setSpectrum(spectrum, n, LingotNative.spectrumMaxFrequency());
        tunerView.invalidate();

        dispatchMessages();
    }

    private void dispatchMessages() {
        String message;
        while ((message = LingotNative.popMessage()) != null) {
            // do not repeat the same error on every restart attempt
            if (!message.equals(lastMessage)) {
                lastMessage = message;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showStatus(String text) {
        statusView.setText(text);
        statusView.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }
}
