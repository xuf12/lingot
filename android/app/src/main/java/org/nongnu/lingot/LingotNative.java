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

/** Bindings to the tuner core (lingot-jni.c). Must be used from the UI thread. */
final class LingotNative {

    static {
        System.loadLibrary("lingot");
    }

    /** Rate at which {@link #poll} must be called, the gauge filter is designed for it. */
    static final double GAUGE_SAMPLING_RATE = 60.0;

    private LingotNative() {
    }

    /** (Re)creates the core with the given instrument range and starts capturing audio. */
    static native boolean start(double minFrequency, double maxFrequency);

    static native void stop();

    static native boolean isRunning();

    /**
     * Advances the gauge one step. Fills result (at least 5 elements) with {filtered
     * frequency (Hz), gauge position (cents), octave, unfiltered frequency (Hz), closest
     * note frequency (Hz)}, and returns the closest note name, or null if no pitch is
     * detected.
     */
    static native String poll(double[] result);

    /** Range of the gauge in cents. */
    static native double gaugeRange();

    /** Minimum SNR required to consider a peak, in dB. */
    static native double minOverallSNR();

    /** Copies the latest spectrum (dB) into out, returns the number of bins. */
    static native int spectrum(float[] out);

    /** Frequency of the last spectrum bin, in Hz. */
    static native double spectrumMaxFrequency();

    /** Next pending core message (errors/warnings), or null. */
    static native String popMessage();
}
