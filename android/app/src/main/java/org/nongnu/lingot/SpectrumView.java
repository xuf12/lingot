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
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

/**
 * Spectrum view, port of lingot-gui-spectrum.c. Pixel sizes of the desktop version
 * (line widths, dashes, offsets) are scaled by the display density.
 */
final class SpectrumView extends View {

    private static final double MIN_DB = 0;
    private static final double MAX_DB = 52;

    // grid scale factors, in kHz and dB. The smallest one respecting the minimum grid size is used.
    private static final double[] SCALES = {0.01, 0.05, 0.1, 0.2, 0.5, 1, 2, 4, 11, 22};
    private static final int[] DB_SCALES = {5, 10, 20, 25, 50, 75, 100};

    private static final int BACKGROUND_COLOR = 0xff0f330f;
    private static final int GRID_COLOR = 0xff8f8f8f;
    private static final int SPECTRUM_COLOR = 0xff21ff21;
    private static final int TARGET_COLOR = 0xff3bd4ff;
    private static final int FUNDAMENTAL_COLOR = 0xffff2121;
    private static final int THRESHOLD_COLOR = 0xffffff33;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Rect textBounds = new Rect();
    private final DashPathEffect dash;
    private final float density;

    private float[] spd = new float[0];
    private int spdSize;
    private double maxFrequency;
    private double frequency;
    private double targetFrequency;
    private double minSNR;

    // layout computed by drawBackground()
    private float leftMargin;
    private float topMargin;
    private float bottomMargin;
    private float innerX;
    private float innerY;
    private double dbDensity;

    SpectrumView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        dash = new DashPathEffect(new float[]{5 * density, 5 * density}, 0);
        textPaint.setColor(GRID_COLOR);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    /** spdSize == 0 means the core is not running, only the grid is drawn. */
    void setSpectrum(float[] spd, int spdSize, double maxFrequency, double frequency,
                     double targetFrequency, double minSNR) {
        this.spd = spd;
        this.spdSize = spdSize;
        this.maxFrequency = maxFrequency;
        this.frequency = frequency;
        this.targetFrequency = targetFrequency;
        this.minSNR = minSNR;
    }

    private static double inBounds(double value) {
        return Math.max(MIN_DB, Math.min(MAX_DB, value)) - MIN_DB;
    }

    // same rules as lingot_gui_spectrum_format_frequency(), freq in kHz
    private static String formatFrequency(double freq) {
        if (freq == 0.0) {
            return "0 Hz";
        } else if (Math.floor(freq) == freq) {
            return String.format(Locale.US, "%.0f kHz", freq);
        } else if (Math.floor(10 * freq) == 10 * freq) {
            return freq <= 1000.0 ? String.format(Locale.US, "%.0f Hz", 1e3 * freq)
                    : String.format(Locale.US, "%.1f kHz", freq);
        }
        return freq <= 100.0 ? String.format(Locale.US, "%.0f Hz", 1e3 * freq)
                : String.format(Locale.US, "%.2f kHz", freq);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawBackground(canvas);

        if (spdSize < 2 || maxFrequency <= 0) {
            return;
        }

        final int maxIndex = spdSize;
        final double indexDensity = innerX / maxIndex;
        final float x0 = leftMargin;
        final float y0 = getHeight() - bottomMargin;

        canvas.save();
        canvas.translate(x0, y0);
        canvas.clipRect(density, -(innerY - density), innerX - density, -density);

        // filled spectrum, smoothed with cubic Bezier curves
        path.reset();
        double y = -dbDensity * inBounds(spd[0]);
        path.moveTo(0, 0);
        path.lineTo(0, (float) y);
        double yp1 = -dbDensity * inBounds(spd[1]);
        double ym1;
        double dydxm1 = 0;
        final double dx = 0.4;
        for (int i = 1; i < maxIndex - 1; i++) {
            final double x = indexDensity * i;
            ym1 = y;
            y = yp1;
            yp1 = -dbDensity * inBounds(spd[i + 1]);
            final double dydx = (yp1 - ym1) / (2 * indexDensity);
            final double x1 = x - (1 - dx) * indexDensity;
            final double x2 = x - dx * indexDensity;
            final double y1 = ym1 + dydxm1 * dx;
            final double y2 = y - dydx * dx;
            dydxm1 = dydx;
            path.cubicTo((float) x1, (float) y1, (float) x2, (float) y2, (float) x, (float) y);
        }
        y = -dbDensity * inBounds(spd[maxIndex - 1]);
        path.lineTo((float) (indexDensity * maxIndex), (float) y);
        path.lineTo((float) (indexDensity * maxIndex), 0);
        path.close();
        paint.setPathEffect(null);
        paint.setColor(SPECTRUM_COLOR);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth(density);
        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.STROKE);
        if (frequency != 0.0) {
            // target frequency mark with a blue vertical line
            paint.setColor(TARGET_COLOR);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(density);
            float x = (float) (innerX * targetFrequency / maxFrequency);
            canvas.drawLine(x, 0, x, -innerY, paint);

            // fundamental frequency mark with a red dashed line
            paint.setPathEffect(dash);
            paint.setColor(FUNDAMENTAL_COLOR);
            paint.setStrokeWidth(2 * density);
            x = (float) (innerX * frequency / maxFrequency);
            canvas.drawLine(x, 0, x, -innerY, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }

        // noise threshold
        paint.setPathEffect(dash);
        paint.setColor(THRESHOLD_COLOR);
        paint.setStrokeWidth(density);
        final float yMin = (float) (-dbDensity * inBounds(minSNR));
        canvas.drawLine(0, yMin, (float) (indexDensity * (maxIndex - 2)), yMin, paint);
        paint.setPathEffect(null);

        canvas.restore();
    }

    private void drawBackground(Canvas canvas) {
        final int sizeX = getWidth();
        final int sizeY = getHeight();

        // the desktop font size is 8 + height / 30 pixels, here in dp
        textPaint.setTextSize(density * (8 + sizeY / density / 30));

        final String minLabel = String.format(Locale.US, "%.0f", MIN_DB);
        final String maxLabel = String.format(Locale.US, "%.0f", MAX_DB);
        textPaint.getTextBounds(maxLabel, 0, maxLabel.length(), textBounds);
        int labelWidth = textBounds.width();
        int labelHeight = textBounds.height();
        textPaint.getTextBounds(minLabel, 0, minLabel.length(), textBounds);
        if (textBounds.width() > labelWidth) {
            labelWidth = textBounds.width();
            labelHeight = textBounds.height();
        }

        // margins
        bottomMargin = 1.6f * labelHeight;
        topMargin = bottomMargin;
        leftMargin = labelWidth * 1.5f;
        float rightMargin = Math.min(0.03f * sizeX, 0.8f * leftMargin);
        innerX = sizeX - leftMargin - rightMargin;
        innerY = sizeY - bottomMargin - topMargin;

        textPaint.getTextBounds("000 Hz", 0, 6, textBounds);
        final float minimumGridWidth = 1.5f * textBounds.width();
        final float minimumGridHeight = 3.0f * textBounds.height();

        canvas.drawColor(BACKGROUND_COLOR);

        paint.setStyle(Paint.Style.STROKE);
        paint.setPathEffect(null);
        paint.setColor(GRID_COLOR);
        paint.setStrokeWidth(density);
        paint.setStrokeCap(Paint.Cap.BUTT);
        canvas.drawLine(leftMargin, sizeY - bottomMargin, leftMargin + innerX, sizeY - bottomMargin, paint);

        // frequency grid
        if (maxFrequency > 0) {
            int i = 0;
            while (i < SCALES.length - 1 && 1e3 * SCALES[i] * innerX / maxFrequency <= minimumGridWidth) {
                i++;
            }
            final double scale = SCALES[i];
            final double gridWidth = 1e3 * scale * innerX / maxFrequency;
            double freq = 0.0;
            for (double x = 0.0; x <= innerX; x += gridWidth) {
                final float gx = (float) (leftMargin + x);
                canvas.drawLine(gx, topMargin, gx, topMargin + innerY + 3 * density, paint);
                final String label = formatFrequency(freq);
                textPaint.getTextBounds(label, 0, label.length(), textBounds);
                canvas.drawText(label, gx + 6 * density - textBounds.width() / 2f - textBounds.left,
                        sizeY - 0.5f * bottomMargin - textBounds.exactCenterY(), textPaint);
                freq += scale;
            }
        }

        // dB grid
        dbDensity = innerY / (MAX_DB - MIN_DB);
        textPaint.getTextBounds("dB", 0, 2, textBounds);
        canvas.drawText("dB", leftMargin - textBounds.left, 0.5f * topMargin - textBounds.exactCenterY(), textPaint);

        int i = 0;
        while (i < DB_SCALES.length - 1 && DB_SCALES[i] * dbDensity <= minimumGridHeight) {
            i++;
        }
        final int dbScale = DB_SCALES[i];
        int i0 = (int) Math.ceil(MIN_DB / dbScale);
        if (MIN_DB < 0.0) {
            i0--;
        }
        final int i1 = (int) Math.ceil(MAX_DB / dbScale);
        for (i = i0; i <= i1; i++) {
            final double y = dbDensity * (i * dbScale - MIN_DB);
            if (y < 0.0 || y > innerY) {
                continue;
            }
            final String label = Integer.toString(i * dbScale);
            textPaint.getTextBounds(label, 0, label.length(), textBounds);
            final float gy = (float) (sizeY - bottomMargin - y);
            canvas.drawText(label, 0.45f * leftMargin - textBounds.width() / 2f - textBounds.left,
                    gy - textBounds.exactCenterY(), textPaint);
            canvas.drawLine(leftMargin - 3 * density, gy, leftMargin + innerX, gy, paint);
        }
    }
}
