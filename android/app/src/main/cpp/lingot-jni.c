/*
 * lingot, a musical instrument tuner.
 *
 * Copyright (C) 2004-2020  Iban Cereijo.
 * Copyright (C) 2004-2008  Jairo Chapela.

 *
 * This file is part of lingot.
 *
 * lingot is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * lingot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with lingot; if not, write to the Free Software Foundation,
 * Inc. 51 Franklin St, Fifth Floor, Boston, MA  02110-1301, USA.
 */

// JNI bridge between the Android UI (org.nongnu.lingot.LingotNative) and the
// tuner core. It plays the role of lingot-gui-mainframe.c on the desktop: it
// owns the config and the core, and filters the results for the gauge.
// All the functions are expected to be called from the UI thread.

#include <jni.h>
#include <math.h>
#include <string.h>

#include "lingot-audio.h"
#include "lingot-audio-aaudio.h"
#include "lingot-config.h"
#include "lingot-config-scale.h"
#include "lingot-core.h"
#include "lingot-filter.h"
#include "lingot-msg.h"

// same values as the desktop UI defaults (lingot-io-ui-settings.c)
#define GAUGE_SAMPLING_RATE         60.0 // Hz, rate at which poll() is expected to be called
#define GAUGE_ADAPTATION_CONSTANT   150.0
#define GAUGE_DAMPING_CONSTANT      15.0

#define SAMPLE_RATE 48000 // native rate of most Android devices

static int audio_registered = 0;
static int created = 0;

static lingot_config_t conf;
static lingot_core_t core;
static lingot_filter_t gauge_filter;
static lingot_filter_t freq_filter;

static LINGOT_FLT gauge_pos = 0.0;
static LINGOT_FLT frequency = 0.0;
static int closest_note_index = 0;

static void lingot_jni_destroy(void) {
    if (created) {
        lingot_core_thread_stop(&core);
        lingot_core_destroy(&core);
        lingot_filter_destroy(&gauge_filter);
        lingot_filter_destroy(&freq_filter);
        lingot_config_destroy(&conf);
        created = 0;
    }
}

static void lingot_jni_filters_new(void) {

    // see lingot_gui_mainframe_create() for the derivation of the gauge filter.
    const double gauge_rate = GAUGE_SAMPLING_RATE;
    const LINGOT_FLT k = GAUGE_ADAPTATION_CONSTANT;
    const LINGOT_FLT q = GAUGE_DAMPING_CONSTANT;
    const LINGOT_FLT gauge_filter_a[] = { k + gauge_rate * (q + gauge_rate),
                                          -gauge_rate * (q + 2.0 * gauge_rate),
                                          gauge_rate * gauge_rate };
    const LINGOT_FLT gauge_filter_b[] = { k };
    lingot_filter_new(&gauge_filter, 2, 0, gauge_filter_a, gauge_filter_b);
    gauge_pos = lingot_filter_filter_sample(&gauge_filter, conf.gauge_rest_value);

    const LINGOT_FLT freq_filter_a[] = { 1.0, -0.5 };
    const LINGOT_FLT freq_filter_b[] = { 0.5 };
    lingot_filter_new(&freq_filter, 1, 0, freq_filter_a, freq_filter_b);

    frequency = 0.0;
    closest_note_index = 0;
}

JNIEXPORT jboolean JNICALL
Java_org_nongnu_lingot_LingotNative_start(JNIEnv* env, jclass clazz,
                                          jdouble min_frequency, jdouble max_frequency) {

    if (!audio_registered) {
        lingot_audio_aaudio_register();
        audio_registered = 1;
    }

    lingot_jni_destroy();

    lingot_config_new(&conf);
    lingot_config_restore_default_values(&conf);
    conf.audio_system_index = lingot_audio_system_find_by_name("AAudio");
    conf.sample_rate = SAMPLE_RATE;
    if (min_frequency > 0.0 && max_frequency > min_frequency) {
        conf.min_frequency = min_frequency;
        conf.max_frequency = max_frequency;
    }
    lingot_config_update_internal_params(&conf);

    lingot_jni_filters_new();
    lingot_core_new(&core, &conf);
    created = 1;

    lingot_core_thread_start(&core);
    return lingot_core_thread_is_running(&core) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_org_nongnu_lingot_LingotNative_stop(JNIEnv* env, jclass clazz) {
    lingot_jni_destroy();
}

JNIEXPORT jboolean JNICALL
Java_org_nongnu_lingot_LingotNative_isRunning(JNIEnv* env, jclass clazz) {
    return (created && lingot_core_thread_is_running(&core)) ? JNI_TRUE : JNI_FALSE;
}

// Advances the gauge filter one step (see lingot_gui_mainframe_callback_gauge_sampling).
// result[0] = filtered frequency in Hz (0 if no pitch detected)
// result[1] = gauge position in cents
// result[2] = octave of the closest note
// Returns the closest note name, or null if no pitch is detected.
JNIEXPORT jstring JNICALL
Java_org_nongnu_lingot_LingotNative_poll(JNIEnv* env, jclass clazz, jdoubleArray result) {

    if (!created) {
        return NULL;
    }

    LINGOT_FLT freq = lingot_core_thread_get_result_frequency(&core);

    // ignore continuous component
    if (!lingot_core_thread_is_running(&core) || isnan(freq)
            || (freq <= conf.internal_min_frequency)) {
        frequency = 0.0;
        gauge_pos = lingot_filter_filter_sample(&gauge_filter, conf.gauge_rest_value);
    } else {
        LINGOT_FLT error_cents; // do not use, unfiltered
        frequency = lingot_filter_filter_sample(&freq_filter, freq);
        closest_note_index = lingot_config_scale_get_closest_note_index(
                    &conf.scale, freq, conf.root_frequency_error, &error_cents);
        if (!isnan(error_cents)) {
            gauge_pos = lingot_filter_filter_sample(&gauge_filter, error_cents);
        }
    }

    const jdouble values[] = {
        frequency,
        gauge_pos,
        lingot_config_scale_get_octave(&conf.scale, closest_note_index) + 4
    };
    (*env)->SetDoubleArrayRegion(env, result, 0, 3, values);

    if (frequency == 0.0) {
        return NULL;
    }
    const char* note = conf.scale.note_name[
            lingot_config_scale_get_note_index(&conf.scale, closest_note_index)];
    return (*env)->NewStringUTF(env, note);
}

// global range of the gauge in cents
JNIEXPORT jdouble JNICALL
Java_org_nongnu_lingot_LingotNative_gaugeRange(JNIEnv* env, jclass clazz) {
    return created ? conf.gauge_range : 100.0;
}

// Copies the latest spectrum (in dB) into the given array, returns the number of bins.
JNIEXPORT jint JNICALL
Java_org_nongnu_lingot_LingotNative_spectrum(JNIEnv* env, jclass clazz, jfloatArray out) {

    if (!created || !lingot_core_thread_is_running(&core)) {
        return 0;
    }

    const LINGOT_FLT* spd = lingot_core_thread_get_result_spd(&core);
    jsize n = (jsize) (conf.fft_size / 2);
    jsize capacity = (*env)->GetArrayLength(env, out);
    if (n > capacity) {
        n = capacity;
    }

    jfloat* dst = (*env)->GetFloatArrayElements(env, out, NULL);
    jsize i;
    for (i = 0; i < n; i++) {
        dst[i] = (jfloat) spd[i];
    }
    (*env)->ReleaseFloatArrayElements(env, out, dst, 0);
    return n;
}

// frequency corresponding to the last spectrum bin
JNIEXPORT jdouble JNICALL
Java_org_nongnu_lingot_LingotNative_spectrumMaxFrequency(JNIEnv* env, jclass clazz) {
    return created ? 0.5 * conf.sample_rate / conf.oversampling : 0.0;
}

// Pops a message from the core message queue, null if there are none.
JNIEXPORT jstring JNICALL
Java_org_nongnu_lingot_LingotNative_popMessage(JNIEnv* env, jclass clazz) {
    char message[LINGOT_MSG_MAX_SIZE + 1];
    lingot_msg_type_t type;
    int error_code;
    if (!lingot_msg_pop(message, &type, &error_code)) {
        return NULL;
    }
    return (*env)->NewStringUTF(env, message);
}
