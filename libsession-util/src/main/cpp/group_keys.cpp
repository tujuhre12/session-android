#include "group_keys.h"
#include "group_info.h"
#include "group_members.h"

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_00024Companion_newInstance(JNIEnv *env,
                                                                                        jobject thiz,
                                                                                        jbyteArray user_secret_key,
                                                                                        jbyteArray group_public_key,
                                                                                        jbyteArray group_secret_key,
                                                                                        jbyteArray initial_dump,
                                                                                        jobject info_jobject,
                                                                                        jobject members_jobject) {
    std::lock_guard lock{util::util_mutex_};
    auto user_key_bytes = util::ustring_from_bytes(env, user_secret_key);
    auto pub_key_bytes = util::ustring_from_bytes(env, group_public_key);
    std::optional<session::ustring> secret_key_optional{std::nullopt};
    std::optional<session::ustring> initial_dump_optional{std::nullopt};
    if (group_secret_key != nullptr) {
        auto secret_key_bytes = util::ustring_from_bytes(env, group_secret_key);
        secret_key_optional = secret_key_bytes;
    }
    if (initial_dump != nullptr) {
        auto initial_dump_bytes = util::ustring_from_bytes(env, initial_dump);
        initial_dump_optional = initial_dump_bytes;
    }

    auto info = ptrToInfo(env, info_jobject);
    auto members = ptrToMembers(env, members_jobject);

    auto* keys = new session::config::groups::Keys(user_key_bytes,
                                                   pub_key_bytes,
                                                   secret_key_optional,
                                                   initial_dump_optional,
                                                   *info,
                                                   *members);

    jclass groupKeysConfig = env->FindClass("network/loki/messenger/libsession_util/GroupKeysConfig");
    jmethodID constructor = env->GetMethodID(groupKeysConfig, "<init>", "(J)V");
    jobject newConfig = env->NewObject(groupKeysConfig, constructor, reinterpret_cast<jlong>(keys));

    return newConfig;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_groupKeys(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto config = ptrToKeys(env, thiz);
    auto keys = config->group_keys();
    jclass stack = env->FindClass("java/util/Stack");
    jmethodID init = env->GetMethodID(stack, "<init>", "()V");
    jobject our_stack = env->NewObject(stack, init);
    jmethodID push = env->GetMethodID(stack, "push", "(Ljava/lang/Object;)Ljava/lang/Object;");
    for (auto& key : keys) {
        auto key_bytes = util::bytes_from_ustring(env, key.data());
        env->CallObjectMethod(our_stack, push, key_bytes);
    }
    return our_stack;
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_loadKey(JNIEnv *env, jobject thiz,
                                                                     jbyteArray data,
                                                                     jbyteArray msg_id,
                                                                     jlong timestamp_ms,
                                                                     jobject info_jobject,
                                                                     jobject members_jobject) {
    std::lock_guard lock{util::util_mutex_};
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
    std::lock_guard lock{util::util_mutex_};
    auto keys = ptrToKeys(env, thiz);
    return keys->needs_rekey();
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_pendingKey(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
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
    std::lock_guard lock{util::util_mutex_};
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
    std::lock_guard lock{util::util_mutex_};
    auto keys = ptrToKeys(env, thiz);
    auto info = ptrToInfo(env, info_jobject);
    auto members = ptrToMembers(env, members_jobject);
    auto rekey = keys->rekey(*info, *members);
    auto rekey_bytes = util::bytes_from_ustring(env, rekey.data());
    return rekey_bytes;
}