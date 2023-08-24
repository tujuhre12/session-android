#include <jni.h>
#include "group_info.h"
#include "session/config/groups/info.hpp"


extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_newInstance__Ljava_lang_String_2(
        JNIEnv *env, jobject thiz, jstring pub_key_hex) {
    std::lock_guard guard{util::util_mutex_};
    auto pub_key_string = util::ustring_from_jstring(env, pub_key_hex);
    auto group_info = session::config::groups::Info(pub_key_string, std::nullopt, std::nullopt);
    // TODO
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