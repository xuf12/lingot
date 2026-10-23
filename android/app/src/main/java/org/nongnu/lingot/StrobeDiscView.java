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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Strobe disc, port of lingot-gui-strobe-disc.c. The disc turns at a speed
 * proportional to the tuning error, and stands still when in tune.
 */
final class StrobeDiscView extends View {

    private final Bitmap image;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final double computationRate;

    private double angle; // radians

    StrobeDiscView(Context context, double computationRate) {
        super(context);
        this.computationRate = computationRate;
        image = BitmapFactory.decodeResource(context.getResources(), R.drawable.lingot_strobe);
    }

    /** Advances the disc, must be called at the computation rate given in the constructor. */
    void setError(double error) {
        angle += 1e-1 * error / computationRate;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xffffffff);

        final double areaWidth = getWidth();
        final double areaHeight = getHeight();
        final int imageWidth = image.getWidth();
        final int imageHeight = image.getHeight();

        canvas.save();
        canvas.translate((float) (areaWidth / 2.0), (float) (1.5 * areaHeight));
        canvas.rotate((float) Math.toDegrees(angle));
        final double s = 1.5 * (areaWidth + areaHeight) / (imageWidth + imageHeight)
                + 0.3 * areaHeight / areaWidth;
        canvas.scale((float) s, (float) s);
        canvas.translate(-imageWidth / 2f + 1f, -imageHeight / 2f + 1f);
        canvas.drawBitmap(image, 0, 0, paint);
        canvas.restore();
    }
}
