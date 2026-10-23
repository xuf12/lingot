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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {

    private static final int REQUEST_RECORD_AUDIO = 1;
    private static final long POLL_PERIOD_MS = Math.round(1000.0 / LingotNative.GAUGE_SAMPLING_RATE);
    private static final long RESTART_DELAY_MS = 1000;
    private static final String PREF_RANGE = "range";
    private static final String PREF_STROBE = "strobe";
    private static final String PREF_SPECTRUM = "spectrum";

    // GTK (Adwaita) like colors for the window and the frames
    private static final int WINDOW_COLOR = 0xfff6f5f4;
    private static final int FRAME_BORDER_COLOR = 0xffcdc7c2;
    private static final int TEXT_COLOR = 0xff2e3436;

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
    private final double[] result = new double[5];
    private final float[] spectrum = new float[4096];

    private float density;
    private LinearLayout panels;
    private View deviationFrame;
    private View toneFrame;
    private View spectrumFrame;
    private GaugeView gaugeView;
    private StrobeDiscView strobeDiscView;
    private ToneView toneView;
    private SpectrumView spectrumView;
    private TextView statusView;

    private int range;
    private boolean showStrobeDisc;
    private boolean showSpectrum;
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
        density = getResources().getDisplayMetrics().density;

        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        range = preferences.getInt(PREF_RANGE, 0);
        if (range < 0 || range >= RANGES.length) {
            range = 0;
        }
        showStrobeDisc = preferences.getBoolean(PREF_STROBE, false);
        showSpectrum = preferences.getBoolean(PREF_SPECTRUM, true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(WINDOW_COLOR);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // edge-to-edge is enforced from Android 15, keep clear of the system bars
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            });
        }

        root.addView(createToolbar());

        statusView = new TextView(this);
        statusView.setTextColor(0xffc64600);
        statusView.setPadding(dp(16), dp(4), dp(16), 0);
        statusView.setVisibility(View.GONE);
        root.addView(statusView);

        gaugeView = new GaugeView(this);
        strobeDiscView = new StrobeDiscView(this, LingotNative.GAUGE_SAMPLING_RATE);
        FrameLayout deviation = new FrameLayout(this);
        deviation.addView(gaugeView);
        deviation.addView(strobeDiscView);
        toneView = new ToneView(this);
        spectrumView = new SpectrumView(this);

        deviationFrame = createFrame(R.string.frame_deviation, deviation);
        toneFrame = createFrame(R.string.frame_tone, toneView);
        spectrumFrame = createFrame(R.string.frame_spectrum, spectrumView);

        panels = new LinearLayout(this);
        panels.setOrientation(LinearLayout.VERTICAL);
        panels.setPadding(dp(8), 0, dp(8), dp(8));
        root.addView(panels, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        updateViews();
        layoutPanels(getResources().getConfiguration().orientation);

        setContentView(root);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        layoutPanels(newConfig.orientation);
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    private View createToolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), dp(4), dp(4), 0);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(20);
        title.setTextColor(TEXT_COLOR);
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

        // "View" menu, as in the desktop application
        TextView menuButton = new TextView(this);
        menuButton.setText("⋮");
        menuButton.setTextSize(24);
        menuButton.setTextColor(TEXT_COLOR);
        menuButton.setGravity(Gravity.CENTER);
        menuButton.setContentDescription(getString(R.string.menu_view));
        menuButton.setOnClickListener(this::showViewMenu);
        toolbar.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        return toolbar;
    }

    private void showViewMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        menu.add(1, 1, 0, R.string.view_gauge).setChecked(!showStrobeDisc);
        menu.add(1, 2, 1, R.string.view_strobe_disc).setChecked(showStrobeDisc);
        menu.setGroupCheckable(1, true, true);
        menu.add(2, 3, 2, R.string.view_spectrum).setCheckable(true).setChecked(showSpectrum);
        popup.setOnMenuItemClickListener((MenuItem item) -> {
            if (item.getItemId() == 3) {
                showSpectrum = !showSpectrum;
            } else {
                showStrobeDisc = item.getItemId() == 2;
            }
            getPreferences(MODE_PRIVATE).edit()
                    .putBoolean(PREF_STROBE, showStrobeDisc)
                    .putBoolean(PREF_SPECTRUM, showSpectrum)
                    .apply();
            updateViews();
            layoutPanels(getResources().getConfiguration().orientation);
            return true;
        });
        popup.show();
    }

    private void updateViews() {
        gaugeView.setVisibility(showStrobeDisc ? View.GONE : View.VISIBLE);
        strobeDiscView.setVisibility(showStrobeDisc ? View.VISIBLE : View.GONE);
    }

    /** A titled frame with a border, like a GtkFrame. */
    private View createFrame(int titleId, View content) {
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleId);
        title.setTextColor(TEXT_COLOR);
        title.setTextSize(13);
        title.setPadding(dp(6), dp(8), dp(6), dp(2));
        frame.addView(title);

        FrameLayout box = new FrameLayout(this);
        GradientDrawable border = new GradientDrawable();
        border.setColor(0xffffffff);
        border.setStroke(Math.max(1, dp(1)), FRAME_BORDER_COLOR);
        border.setCornerRadius(dp(3));
        box.setBackground(border);
        box.setPadding(dp(1), dp(1), dp(1), dp(1));
        box.setClipToOutline(true);
        box.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return frame;
    }

    /**
     * Portrait: deviation, tone and spectrum stacked. Landscape: deviation and tone
     * side by side above the spectrum, as in the desktop main window.
     */
    private void layoutPanels(int orientation) {
        detach(deviationFrame);
        detach(toneFrame);
        detach(spectrumFrame);
        panels.removeAllViews();

        final boolean landscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (landscape) {
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.addView(deviationFrame, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3));
            LinearLayout.LayoutParams toneParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2);
            toneParams.leftMargin = dp(8);
            top.addView(toneFrame, toneParams);
            panels.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3));
        } else {
            panels.addView(deviationFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 4));
            panels.addView(toneFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2.5f));
        }
        if (showSpectrum) {
            panels.addView(spectrumFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0,
                    landscape ? 2 : 3.5f));
        }
    }

    private static void detach(View view) {
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
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

        final String note = LingotNative.poll(result);
        final double frequency = result[0];
        final double gaugePos = result[1];
        final int octave = (int) result[2];

        // the strobe disc integrates the error, it must be advanced on every sample
        strobeDiscView.setError(gaugePos);
        gaugeView.setGauge(gaugePos, LingotNative.gaugeRange());
        toneView.setTone(note, octave, frequency, gaugePos);
        final int n = LingotNative.spectrum(spectrum);
        spectrumView.setSpectrum(spectrum, n, LingotNative.spectrumMaxFrequency(), result[3], result[4],
                LingotNative.minOverallSNR());

        if (showStrobeDisc) {
            strobeDiscView.invalidate();
        } else {
            gaugeView.invalidate();
        }
        toneView.invalidate();
        if (showSpectrum) {
            spectrumView.invalidate();
        }

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
