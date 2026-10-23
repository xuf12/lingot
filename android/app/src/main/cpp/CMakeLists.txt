cmake_minimum_required(VERSION 3.22)

project(lingot C)

# The tuner core is shared with the desktop application.
set(LINGOT_SRC ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../src)

add_library(lingot SHARED
    ${LINGOT_SRC}/lingot-audio.c
    ${LINGOT_SRC}/lingot-audio-aaudio.c
    ${LINGOT_SRC}/lingot-config.c
    ${LINGOT_SRC}/lingot-config-scale.c
    ${LINGOT_SRC}/lingot-core.c
    ${LINGOT_SRC}/lingot-defs-internal.c
    ${LINGOT_SRC}/lingot-fft.c
    ${LINGOT_SRC}/lingot-filter.c
    ${LINGOT_SRC}/lingot-msg.c
    ${LINGOT_SRC}/lingot-signal.c
    lingot-jni.c
)

target_include_directories(lingot PRIVATE ${LINGOT_SRC})

# AAudio is the only audio system on Android; no FFTW, no gettext.
target_compile_definitions(lingot PRIVATE AAUDIO)

target_compile_options(lingot PRIVATE -Wall -Wextra -Wno-unused-parameter)

target_link_libraries(lingot aaudio log m)
