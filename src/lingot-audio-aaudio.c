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

#ifdef HAVE_CONFIG_H
#  include "config.h"
#endif

// Android capture backend (AAudio, API level >= 28 for the input preset).

#ifdef AAUDIO

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <aaudio/AAudio.h>

#include "lingot-audio.h"
#include "lingot-defs-internal.h"
#include "lingot-audio-aaudio.h"
#include "lingot-i18n.h"
#include "lingot-msg.h"

// The blocking read must return well before the 500 ms watchdog in
// lingot_audio_stop() expires, since threads cannot be cancelled on Android.
#define LINGOT_AAUDIO_READ_TIMEOUT_NS 100000000LL

typedef struct {
    AAudioStream* stream;
    float* buffer;
} lingot_audio_handler_aaudio_t;

static void lingot_audio_aaudio_error(const char* message, aaudio_result_t result) {
    char buff[512];
    snprintf(buff, sizeof(buff), "%s\n%s", message, AAudio_convertResultToText(result));
    lingot_msg_add_error_with_code(buff, (int) result);
}

// releases the backend resources, also used on initialization failures, where
// the destroy callback is not invoked since the audio system is set to -1.
static void lingot_audio_aaudio_release(lingot_audio_handler_t* audio) {
    lingot_audio_handler_aaudio_t* audio_aa = (lingot_audio_handler_aaudio_t*) audio->audio_handler_extra;
    if (audio_aa) {
        if (audio_aa->stream) {
            AAudioStream_requestStop(audio_aa->stream);
            AAudioStream_close(audio_aa->stream);
            audio_aa->stream = NULL;
        }
        free(audio_aa->buffer);
        free(audio->audio_handler_extra);
        audio->audio_handler_extra = NULL;
    }
}

void lingot_audio_aaudio_new(lingot_audio_handler_t* audio, const char* device, int sample_rate) {

    (void) device; // only the default input device is supported
    strcpy(audio->device, "");

    audio->audio_handler_extra = malloc(sizeof(lingot_audio_handler_aaudio_t));
    lingot_audio_handler_aaudio_t* audio_aa = (lingot_audio_handler_aaudio_t*) audio->audio_handler_extra;
    audio_aa->stream = NULL;
    audio_aa->buffer = NULL;

    AAudioStreamBuilder* builder = NULL;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        lingot_audio_aaudio_error(_("Error creating AAudio stream builder."), result);
        lingot_audio_aaudio_release(audio);
        audio->audio_system = -1;
        return;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(builder, 1);
    if (sample_rate > 0) {
        AAudioStreamBuilder_setSampleRate(builder, sample_rate);
    }
    // unprocessed-like capture: no automatic gain control nor noise suppression
    AAudioStreamBuilder_setInputPreset(builder, AAUDIO_INPUT_PRESET_VOICE_RECOGNITION);

    result = AAudioStreamBuilder_openStream(builder, &audio_aa->stream);
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK) {
        lingot_audio_aaudio_error(_("Error opening AAudio input stream."), result);
        audio_aa->stream = NULL;
        lingot_audio_aaudio_release(audio);
        audio->audio_system = -1;
        return;
    }

    audio->real_sample_rate = (unsigned int) AAudioStream_getSampleRate(audio_aa->stream);
    audio->bytes_per_sample = sizeof(float);

    if (audio->real_sample_rate >= 44100) {
        audio->read_buffer_size_samples = 2048;
    } else if (audio->real_sample_rate >= 22050) {
        audio->read_buffer_size_samples = 1024;
    } else {
        audio->read_buffer_size_samples = 512;
    }

    audio_aa->buffer = malloc(audio->read_buffer_size_samples * sizeof(float));

    result = AAudioStream_requestStart(audio_aa->stream);
    if (result != AAUDIO_OK) {
        lingot_audio_aaudio_error(_("Error starting AAudio input stream."), result);
        lingot_audio_aaudio_release(audio);
        audio->audio_system = -1;
    }
}

void lingot_audio_aaudio_destroy(lingot_audio_handler_t* audio) {
    lingot_audio_aaudio_release(audio);
}

int lingot_audio_aaudio_read(lingot_audio_handler_t* audio) {

    lingot_audio_handler_aaudio_t* audio_aa = (lingot_audio_handler_aaudio_t*) audio->audio_handler_extra;

    aaudio_result_t result = AAudioStream_read(audio_aa->stream, audio_aa->buffer,
                                               (int32_t) audio->read_buffer_size_samples,
                                               LINGOT_AAUDIO_READ_TIMEOUT_NS);
    if (result < 0) {
        lingot_audio_aaudio_error(_("Read from audio interface failed."), result);
        return -1;
    }

    int i;
    for (i = 0; i < result; i++) {
        audio->flt_read_buffer[i] = audio_aa->buffer[i] * FLT_SAMPLE_SCALE;
    }

    return (int) result;
}

int lingot_audio_aaudio_get_audio_system_properties(lingot_audio_system_properties_t* properties) {

    properties->forced_sample_rate = 0;
    properties->n_sample_rates = 2;
    properties->sample_rates[0] = 44100;
    properties->sample_rates[1] = 48000;

    properties->n_devices = 1;
    properties->devices = (const char**) malloc(sizeof(char*));
    char buff[512];
    snprintf(buff, sizeof(buff), "%s <default>", _("Default Device"));
    properties->devices[0] = _strdup(buff);

    return 0;
}

int lingot_audio_aaudio_register(void)
{
    return lingot_audio_system_register("AAudio",
                                        lingot_audio_aaudio_new,
                                        lingot_audio_aaudio_destroy,
                                        NULL,
                                        NULL,
                                        NULL,
                                        lingot_audio_aaudio_read,
                                        lingot_audio_aaudio_get_audio_system_properties);
}

#else

int lingot_audio_aaudio_register(void)
{
    return 0;
}

#endif
