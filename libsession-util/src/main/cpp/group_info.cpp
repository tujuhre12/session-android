#include <jni.h>
#include "group_info.h"


extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_newInstance__Ljava_lang_String_2(
        JNIEnv *env, jobject thiz, jstring pub_key_hex) {
    auto pub_key = env->GetStringUTFChars(pub_key_hex, nullptr);

    env->ReleaseStringUTFChars(pub_key_hex, pub_key);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_newInstance__Ljava_lang_String_2_3B(
        JNIEnv *env, jobject thiz, jstring pub_key_hex, jbyteArray secret_key) {
    // TODO: implement newInstance()
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_newInstance__Ljava_lang_String_2_3B_3B(
        JNIEnv *env, jobject thiz, jstring pub_key_hex, jbyteArray secret_key,
        jbyteArray initial_dump) {
    // TODO: implement newInstance()
}