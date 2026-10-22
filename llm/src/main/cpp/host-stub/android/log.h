// Host stand-in for <android/log.h>, used only by the JVM test of the bridge (CI, Linux).
#pragma once

#include <cstdio>

#define ANDROID_LOG_WARN 5
#define ANDROID_LOG_ERROR 6

inline int __android_log_write(int, const char * tag, const char * text) {
    return std::fprintf(stderr, "%s: %s", tag, text);
}
