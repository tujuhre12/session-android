#ifndef SESSION_ANDROID_LOGGING_H
#define SESSION_ANDROID_LOGGING_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// Declaration of the JNI function following the JNI naming convention.
JNIEXPORT void JNICALL Java_network_loki_messenger_libsession_1util_util_Logger_initLogger(JNIEnv* env, jclass clazz);

#ifdef __cplusplus
}
#endif

#endif //SESSION_ANDROID_LOGGING_H
