#include <baresip.h>
#include <android/log.h>

#ifndef BARESIP_LOGGER_H
#define BARESIP_LOGGER_H

#define LOG_TAG "Baresip Lib"

#define LOGD(...) \
    if (log_level_get() < LEVEL_INFO) \
        ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))

#define LOGI(...) \
    if (log_level_get() < LEVEL_WARN) \
        ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))

#define LOGW(...) \
    if (log_level_get() < LEVEL_ERROR) \
        ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))

#define LOGE(...) \
    if (log_level_get() <= LEVEL_ERROR) \
        ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))

#endif //BARESIP_LOGGER_H
