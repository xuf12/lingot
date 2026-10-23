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
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

/**
 * Frequency, note and error labels, port of lingot_gui_mainframe_draw_labels().
 * The three labels are stacked and spread over the available height, as in the
 * desktop "Tone" frame.
 */
final class ToneView extends View {

    // Pango font sizes are in points, at 96 dpi a point is 4/3 of a pixel
    private static final float POINTS_TO_PIXELS = 4f / 3f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private String note;
    private int octave;
    private double frequency;
    private double error;

    ToneView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        paint.setColor(0xff000000);
    }

    void setTone(String note, int octave, double frequency, double error) {
        this.note = note;
        this.octave = octave;
        this.frequency = frequency;
        this.error = error;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final boolean detected = note != null && frequency != 0.0;
        final String noteString = detected ? note : "---";
        final String errorString = detected ? String.format(Locale.US, "e = %+2.0f cents", error) : "e = ---";
        final String freqString = detected ? String.format(Locale.US, "f = %6.2f Hz", frequency) : "f = ---";
        final String octaveString = detected ? Integer.toString(octave) : "";

        // desktop sizes depend on the frame width (in pixels, here in dp), but the
        // labels must also fit in a short frame
        final float widthDp = getWidth() / density;
        final float rowHeight = getHeight() / 3f;
        final float smallSize = Math.min(density * POINTS_TO_PIXELS * (9 + widthDp / 80), 0.5f * rowHeight);
        final float bigSize = Math.min(density * POINTS_TO_PIXELS * (10 + widthDp / 22), 1.4f * rowHeight);
        final float cx = getWidth() / 2f;

        // the three labels expand equally over the height
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(smallSize);
        drawCentered(canvas, freqString, cx, 0.5f * rowHeight);
        drawCentered(canvas, errorString, cx, 2.5f * rowHeight);

        // note with the octave as a subscript, centered as a whole
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(bigSize);
        final float noteWidth = paint.measureText(noteString);
        final Paint.FontMetrics noteMetrics = paint.getFontMetrics();
        paint.setTextSize(0.75f * bigSize);
        final float octaveWidth = paint.measureText(octaveString);
        final float left = cx - (noteWidth + octaveWidth) / 2f;
        final float baseline = 1.5f * rowHeight - (noteMetrics.ascent + noteMetrics.descent) / 2f;

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(bigSize);
        canvas.drawText(noteString, left, baseline, paint);
        paint.setTextSize(0.75f * bigSize);
        canvas.drawText(octaveString, left + noteWidth, baseline + 0.2f * bigSize, paint);
    }

    private void drawCentered(Canvas canvas, String text, float x, float y) {
        final Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(text, x, y - (metrics.ascent + metrics.descent) / 2f, paint);
    }
}
