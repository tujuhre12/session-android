#include "group_keys.h"
#include "group_info.h"
#include "group_members.h"

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_00024Companion_newInstance(JNIEnv *env,
                                                                                        jobject thiz,
                                                                                        jbyteArray public_key,
                                                                                        jbyteArray secret_key,
                                                                                        jbyteArray initial_dump,
                                                                                        jobject info,
                                                                                        jobject members) {
    // TODO: implement newInstance()
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_groupKeys(JNIEnv *env, jobject thiz) {
    // TODO: implement groupKeys()
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_loadKey(JNIEnv *env, jobject thiz,
                                                                     jbyteArray data,
                                                                     jbyteArray msg_id,
                                                                     jlong timestamp_ms,
                                                                     jobject info_jobject,
                                                                     jobject members_jobject) {
    auto keys = ptrToKeys(env, thiz);
    auto data_bytes = util::ustring_from_bytes(env, data);
    auto msg_bytes = util::ustring_from_bytes(env, msg_id);
    auto info = ptrToInfo(env, info_jobject);
    auto members = ptrToMembers(env, members_jobject);
    keys->load_key_message(data_bytes, timestamp_ms, *info, *members);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_needsRekey(JNIEnv *env, jobject thiz) {
    auto keys = ptrToKeys(env, thiz);
    return keys->needs_rekey();
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_pendingKey(JNIEnv *env, jobject thiz) {
    auto keys = ptrToKeys(env, thiz);
    auto pending = keys->pending_key();
    if (!pending) {
        return nullptr;
    }
    auto pending_bytes = util::bytes_from_ustring(env, *pending);
    return pending_bytes;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_pendingPush(JNIEnv *env,
                                                                         jobject thiz) {
    auto keys = ptrToKeys(env, thiz);
    auto pending = keys->pending_config();
    if (!pending) {
        return nullptr;
    }
    auto pending_bytes = util::bytes_from_ustring(env, *pending);
    return pending_bytes;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_rekey(JNIEnv *env, jobject thiz,
                                                                   jobject info_jobject, jobject members_jobject) {
    auto keys = ptrToKeys(env, thiz);
    auto info = ptrToInfo(env, info_jobject);
    auto members = ptrToMembers(env, members_jobject);
    auto rekey = keys->rekey(*info, *members);
    auto rekey_bytes = util::bytes_from_ustring(env, rekey.data());
    return rekey_bytes;
}