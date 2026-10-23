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
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Analogue gauge, port of lingot-gui-gauge.c (same normalized dimensions and colors). */
final class GaugeView extends View {

    // normalized dimensions (relative to the gauge height)
    private static final double GAUGE_CENTER_Y = 0.94;
    private static final double CENTS_BAR_STROKE = 0.025;
    private static final double CENTS_BAR_RADIUS = 0.75;
    private static final double CENTS_BAR_MAJOR_TIC_RADIUS = 0.04;
    private static final double CENTS_BAR_MINOR_TIC_RADIUS = 0.03;
    private static final double CENTS_BAR_MAJOR_TIC_STROKE = 0.03;
    private static final double CENTS_BAR_MINOR_TIC_STROKE = 0.01;
    private static final double CENTS_TEXT_SIZE = 0.09;
    private static final double FREQUENCY_BAR_STROKE = 0.025;
    private static final double FREQUENCY_BAR_RADIUS = 0.78;
    private static final double FREQUENCY_BAR_MAJOR_TIC_RADIUS = 0.04;
    private static final double OK_BAR_STROKE = 0.07;
    private static final double OK_BAR_RADIUS = 0.48;
    private static final double GAUGE_LENGTH = 0.85;
    private static final double GAUGE_LENGTH_BACK = 0.08;
    private static final double GAUGE_CENTER_RADIUS = 0.045;
    private static final double GAUGE_STROKE = 0.012;
    private static final double GAUGE_SHADOW_OFFSET_X = 0.015;
    private static final double GAUGE_SHADOW_OFFSET_Y = 0.01;

    private static final double OVERTURE_ANGLE = Math.toRadians(65.0);

    // colors
    private static final int CENTS_BAR_COLOR = 0xff333355;
    private static final int FREQUENCY_BAR_COLOR = 0xff555533;
    private static final int OK_COLOR = 0xff99dd99;
    private static final int KO_COLOR = 0xffddaaaa;
    private static final int GAUGE_COLOR = 0xffaa3333;
    private static final int GAUGE_SHADOW_COLOR = 0x77000000;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private final Rect textBounds = new Rect();

    private double gaugePos;
    private double gaugeRange = 100.0;

    GaugeView(Context context) {
        super(context);
        textPaint.setColor(0xff000000);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    void setGauge(double gaugePos, double gaugeRange) {
        this.gaugePos = gaugePos;
        this.gaugeRange = gaugeRange;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int width = getWidth();
        double height = getHeight();

        double cx = width / 2.0;
        double cy = height * GAUGE_CENTER_Y;
        if (width < 1.6 * height) {
            height = width / 1.6;
            cy = 0.5 * (getHeight() - height) + height * GAUGE_CENTER_Y;
        }

        drawBackground(canvas, cx, cy, height);

        // needle and its shadow
        final double angle = 2.0 * (gaugePos / gaugeRange) * OVERTURE_ANGLE;
        final double length = height * GAUGE_LENGTH;
        final double lengthBack = height * GAUGE_LENGTH_BACK;
        final double centerRadius = height * GAUGE_CENTER_RADIUS;
        final double sx = cx + height * GAUGE_SHADOW_OFFSET_X;
        final double sy = cy + height * GAUGE_SHADOW_OFFSET_Y;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeWidth((float) (height * GAUGE_STROKE));
        paint.setColor(GAUGE_SHADOW_COLOR);
        drawTic(canvas, sx, sy, -lengthBack, -0.99 * centerRadius, angle);
        drawTic(canvas, sx, sy, 0.99 * centerRadius, length, angle);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle((float) sx, (float) sy, (float) centerRadius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(GAUGE_COLOR);
        drawTic(canvas, cx, cy, -lengthBack, length, angle);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle((float) cx, (float) cy, (float) centerRadius, paint);
    }

    private void drawBackground(Canvas canvas, double cx, double cy, double height) {
        final double centsBarRadius = height * CENTS_BAR_RADIUS;
        final double centsBarMajorTicRadius = centsBarRadius - height * CENTS_BAR_MAJOR_TIC_RADIUS;
        final double centsBarMinorTicRadius = centsBarRadius - height * CENTS_BAR_MINOR_TIC_RADIUS;
        final double frequencyBarRadius = height * FREQUENCY_BAR_RADIUS;
        final double frequencyBarMajorTicRadius = frequencyBarRadius + height * FREQUENCY_BAR_MAJOR_TIC_RADIUS;
        final double okBarRadius = height * OK_BAR_RADIUS;
        final float overtureDegrees = (float) Math.toDegrees(OVERTURE_ANGLE);

        canvas.drawColor(0xffffffff);

        // ok/ko bar
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeWidth((float) (height * OK_BAR_STROKE));
        paint.setColor(KO_COLOR);
        drawArc(canvas, cx, cy, okBarRadius, overtureDegrees);
        paint.setColor(OK_COLOR);
        drawArc(canvas, cx, cy, okBarRadius, 0.1f * overtureDegrees);

        // cents bar
        paint.setStrokeWidth((float) (height * CENTS_BAR_STROKE));
        paint.setColor(CENTS_BAR_COLOR);
        drawArc(canvas, cx, cy, centsBarRadius, 1.05f * overtureDegrees);

        // cent tics: at most 20 minor divisions, rounded to 1, 2, 5 or 10 times a power of 10
        final int maxMinorDivisions = 20;
        double centsPerMinorDivision = gaugeRange / maxMinorDivisions;
        final double base = Math.pow(10.0, Math.floor(Math.log10(centsPerMinorDivision)));
        double normalized = centsPerMinorDivision / base;
        if (normalized >= 6.0) {
            normalized = 10.0;
        } else if (normalized >= 2.5) {
            normalized = 5.0;
        } else if (normalized >= 1.2) {
            normalized = 2.0;
        } else {
            normalized = 1.0;
        }
        centsPerMinorDivision = normalized * base;
        final double centsPerMajorDivision = 5.0 * centsPerMinorDivision;

        // minor tics
        paint.setStrokeWidth((float) (height * CENTS_BAR_MINOR_TIC_STROKE));
        int maxIndex = (int) Math.floor(0.5 * gaugeRange / centsPerMinorDivision);
        double angleStep = 2.0 * OVERTURE_ANGLE * centsPerMinorDivision / gaugeRange;
        for (int index = -maxIndex; index <= maxIndex; index++) {
            drawTic(canvas, cx, cy, centsBarMinorTicRadius, centsBarRadius, index * angleStep);
        }

        // major tics
        maxIndex = (int) Math.floor(0.5 * gaugeRange / centsPerMajorDivision);
        angleStep = 2.0 * OVERTURE_ANGLE * centsPerMajorDivision / gaugeRange;
        paint.setStrokeWidth((float) (height * CENTS_BAR_MAJOR_TIC_STROKE));
        for (int index = -maxIndex; index <= maxIndex; index++) {
            drawTic(canvas, cx, cy, centsBarMajorTicRadius, centsBarRadius, index * angleStep);
        }

        // cents text
        textPaint.setTextSize((float) (height * CENTS_TEXT_SIZE));
        drawCenteredText(canvas, "cent", (float) cx, (float) (cy - 0.81 * centsBarMajorTicRadius));
        for (int index = -maxIndex; index <= maxIndex; index++) {
            final int cents = (int) (index * centsPerMajorDivision);
            canvas.save();
            canvas.translate((float) cx, (float) cy);
            canvas.rotate((float) Math.toDegrees(index * angleStep));
            drawCenteredText(canvas, (cents > 0 ? "+" : "") + cents, 0f, (float) (-0.92 * centsBarMajorTicRadius));
            canvas.restore();
        }

        // frequency bar
        paint.setStrokeWidth((float) (height * FREQUENCY_BAR_STROKE));
        paint.setColor(FREQUENCY_BAR_COLOR);
        drawArc(canvas, cx, cy, frequencyBarRadius, 1.05f * overtureDegrees);
        drawTic(canvas, cx, cy, frequencyBarMajorTicRadius, frequencyBarRadius, 0.0);
    }

    // arc symmetric around the vertical, halfAngle in degrees
    private void drawArc(Canvas canvas, double cx, double cy, double radius, float halfAngle) {
        arcRect.set((float) (cx - radius), (float) (cy - radius), (float) (cx + radius), (float) (cy + radius));
        canvas.drawArc(arcRect, -90f - halfAngle, 2f * halfAngle, false, paint);
    }

    // radial segment between two radii, angle in radians from the vertical
    private void drawTic(Canvas canvas, double cx, double cy, double radius1, double radius2, double angle) {
        final double sin = Math.sin(angle);
        final double cos = Math.cos(angle);
        canvas.drawLine((float) (cx + radius1 * sin), (float) (cy - radius1 * cos),
                (float) (cx + radius2 * sin), (float) (cy - radius2 * cos), paint);
    }

    // text centered both horizontally and vertically around (x, y), as with cairo text extents
    private void drawCenteredText(Canvas canvas, String text, float x, float y) {
        textPaint.getTextBounds(text, 0, text.length(), textBounds);
        canvas.drawText(text, x, y - textBounds.exactCenterY(), textPaint);
    }
}
