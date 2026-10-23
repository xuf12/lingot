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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

/** Draws the gauge, the detected note and the spectrum (cf. lingot-gui-gauge.c, lingot-gui-spectrum.c). */
final class TunerView extends View {

    // same dB range as the desktop spectrum view
    private static final float SPECTRUM_MIN_DB = 0f;
    private static final float SPECTRUM_MAX_DB = 52f;

    // the needle sweeps +/- this angle (degrees) around the vertical
    private static final float GAUGE_HALF_ANGLE = 60f;

    // tolerance considered "in tune", in cents
    private static final double IN_TUNE_CENTS = 5.0;

    private static final int COLOR_BACKGROUND = 0xff101418;
    private static final int COLOR_FOREGROUND = 0xffe8eaed;
    private static final int COLOR_DIM = 0xff7f8790;
    private static final int COLOR_IN_TUNE = 0xff4caf50;
    private static final int COLOR_NEEDLE = 0xffef5350;
    private static final int COLOR_SPECTRUM = 0xff42a5f5;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float density;

    private String note;
    private int octave;
    private double frequency;
    private double gaugePos;
    private double gaugeRange = 100.0;
    private float[] spectrum = new float[0];
    private int spectrumSize;
    private double spectrumMaxFrequency;

    TunerView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(COLOR_FOREGROUND);
    }

    void setResult(String note, int octave, double frequency, double gaugePos, double gaugeRange) {
        this.note = note;
        this.octave = octave;
        this.frequency = frequency;
        this.gaugePos = gaugePos;
        this.gaugeRange = gaugeRange;
    }

    void setSpectrum(float[] spectrum, int size, double maxFrequency) {
        this.spectrum = spectrum;
        this.spectrumSize = size;
        this.spectrumMaxFrequency = maxFrequency;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(COLOR_BACKGROUND);

        final float w = getWidth();
        final float h = getHeight();
        final float pad = 16 * density;

        // portrait: gauge on top, spectrum below; landscape: side by side
        if (h >= w) {
            final float split = h * 0.62f;
            drawTuner(canvas, pad, pad, w - pad, split);
            drawSpectrum(canvas, pad, split + pad, w - pad, h - pad);
        } else {
            final float split = w * 0.55f;
            drawTuner(canvas, pad, pad, split, h - pad);
            drawSpectrum(canvas, split + pad, pad, w - pad, h - pad);
        }
    }

    private void drawTuner(Canvas canvas, float left, float top, float right, float bottom) {
        final float width = right - left;
        final float height = bottom - top;
        final float cx = left + width / 2;
        final float radius = Math.min(width / 2, height * 0.55f);
        final float cy = top + radius + 8 * density;
        final double halfRange = gaugeRange / 2;

        // scale arc
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3 * density);
        paint.setColor(COLOR_DIM);
        canvas.drawArc(rect, -90 - GAUGE_HALF_ANGLE, 2 * GAUGE_HALF_ANGLE, false, paint);

        // in tune zone
        final float tuneAngle = (float) (IN_TUNE_CENTS / halfRange) * GAUGE_HALF_ANGLE;
        paint.setStrokeWidth(8 * density);
        paint.setColor(COLOR_IN_TUNE);
        canvas.drawArc(rect, -90 - tuneAngle, 2 * tuneAngle, false, paint);

        // ticks every 10 cents, labelled every 25 cents
        textPaint.setTextSize(12 * density);
        textPaint.setTypeface(Typeface.DEFAULT);
        textPaint.setColor(COLOR_DIM);
        paint.setStrokeWidth(2 * density);
        paint.setColor(COLOR_DIM);
        for (int cents = (int) -halfRange; cents <= halfRange; cents += 5) {
            final boolean major = cents % 10 == 0;
            final boolean labelled = cents % 25 == 0;
            if (!major && !labelled) {
                continue;
            }
            final double angle = Math.toRadians(cents / halfRange * GAUGE_HALF_ANGLE);
            final float len = (labelled ? 14 : 8) * density;
            final float sx = (float) Math.sin(angle);
            final float sy = (float) -Math.cos(angle);
            canvas.drawLine(cx + sx * (radius - len), cy + sy * (radius - len),
                    cx + sx * radius, cy + sy * radius, paint);
            if (labelled) {
                final float lr = radius - len - 12 * density;
                canvas.drawText(cents == 0 ? "0" : String.format(Locale.US, "%+d", cents),
                        cx + sx * lr, cy + sy * lr + 4 * density, textPaint);
            }
        }

        // needle
        final boolean detected = note != null;
        final double clamped = Math.max(-halfRange, Math.min(halfRange, gaugePos));
        final double needleAngle = Math.toRadians(clamped / halfRange * GAUGE_HALF_ANGLE);
        final boolean inTune = detected && Math.abs(gaugePos) <= IN_TUNE_CENTS;
        paint.setStrokeWidth(4 * density);
        paint.setColor(inTune ? COLOR_IN_TUNE : COLOR_NEEDLE);
        canvas.drawLine(cx, cy, cx + (float) Math.sin(needleAngle) * radius * 0.92f,
                cy - (float) Math.cos(needleAngle) * radius * 0.92f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, 7 * density, paint);

        // note, frequency and error labels (cf. lingot_gui_mainframe_draw_labels)
        float y = cy + 0.1f * radius + 64 * density;
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(64 * density);
        textPaint.setColor(inTune ? COLOR_IN_TUNE : COLOR_FOREGROUND);
        if (detected) {
            final float noteWidth = textPaint.measureText(note);
            canvas.drawText(note, cx, y, textPaint);
            textPaint.setTextSize(24 * density);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(Integer.toString(octave), cx + noteWidth / 2 + 2 * density, y, textPaint);
            textPaint.setTextAlign(Paint.Align.CENTER);
        } else {
            textPaint.setColor(COLOR_DIM);
            canvas.drawText("---", cx, y, textPaint);
        }

        y += 36 * density;
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setTextSize(18 * density);
        textPaint.setColor(COLOR_FOREGROUND);
        if (detected) {
            canvas.drawText(String.format(Locale.US, "f = %6.2f Hz    e = %+5.1f ¢", frequency, gaugePos),
                    cx, y, textPaint);
        } else {
            textPaint.setColor(COLOR_DIM);
            canvas.drawText("f = ---.-- Hz    e = ---.- ¢", cx, y, textPaint);
        }
    }

    private void drawSpectrum(Canvas canvas, float left, float top, float right, float bottom) {
        final float labelHeight = 16 * density;
        final float plotBottom = bottom - labelHeight;
        final float plotHeight = plotBottom - top;
        final float plotWidth = right - left;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1 * density);
        paint.setColor(COLOR_DIM);
        canvas.drawRect(left, top, right, plotBottom, paint);

        textPaint.setTypeface(Typeface.DEFAULT);
        textPaint.setTextSize(11 * density);
        textPaint.setColor(COLOR_DIM);

        // frequency grid
        if (spectrumMaxFrequency > 0) {
            final double step = niceStep(spectrumMaxFrequency / 5);
            for (double f = step; f < spectrumMaxFrequency; f += step) {
                final float x = left + (float) (f / spectrumMaxFrequency) * plotWidth;
                canvas.drawLine(x, top, x, plotBottom, paint);
                canvas.drawText(formatFrequency(f), x, bottom - 2 * density, textPaint);
            }
        }

        if (spectrumSize < 2) {
            return;
        }

        path.reset();
        for (int i = 0; i < spectrumSize; i++) {
            float db = Math.max(SPECTRUM_MIN_DB, Math.min(SPECTRUM_MAX_DB, spectrum[i]));
            final float x = left + plotWidth * i / (spectrumSize - 1);
            final float y = plotBottom - plotHeight * (db - SPECTRUM_MIN_DB) / (SPECTRUM_MAX_DB - SPECTRUM_MIN_DB);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        paint.setColor(COLOR_SPECTRUM);
        paint.setStrokeWidth(1.5f * density);
        canvas.drawPath(path, paint);

        // detected fundamental frequency
        if (note != null && frequency < spectrumMaxFrequency) {
            final float x = left + (float) (frequency / spectrumMaxFrequency) * plotWidth;
            paint.setColor(COLOR_NEEDLE);
            canvas.drawLine(x, top, x, plotBottom, paint);
        }
    }

    private static double niceStep(double raw) {
        final double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        final double residual = raw / magnitude;
        if (residual > 5) {
            return 10 * magnitude;
        } else if (residual > 2) {
            return 5 * magnitude;
        } else if (residual > 1) {
            return 2 * magnitude;
        }
        return magnitude;
    }

    private static String formatFrequency(double f) {
        return f >= 1000 ? String.format(Locale.US, "%.1f kHz", f / 1000) : String.format(Locale.US, "%.0f Hz", f);
    }
}
