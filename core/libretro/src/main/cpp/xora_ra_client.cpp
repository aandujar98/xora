/**
 * RetroAchievements (rcheevos rc_client) glue for the XOrA Libretro host.
 * HTTP is performed on the Kotlin side via a GlobalRef listener.
 */
#include "xora_ra_memory.h"

#include "rc_client.h"
#include "rc_error.h"

#include <android/log.h>
#include <jni.h>

#include <chrono>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>

#define LOG_TAG "XoraRA"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

JavaVM* g_jvm = nullptr;
// recursive: sync HTTP callbacks may re-enter begin_load_game / do_frame paths.
std::recursive_mutex g_ra_mutex;
rc_client_t* g_client = nullptr;
jobject g_bridge = nullptr;  // GlobalRef to LibretroRaBridge
jmethodID g_mid_http = nullptr;
jmethodID g_mid_unlock = nullptr;
jmethodID g_mid_status = nullptr;
bool g_logged_in = false;
bool g_game_loaded = false;
bool g_hardcore_wanted = false;
std::string g_pending_hash;
/** One-shot Connect login2 JSON from Kotlin — consumed by the next login server_call. */
std::string g_queued_login_json;

JNIEnv* env_for_current_thread() {
    if (!g_jvm) return nullptr;
    JNIEnv* env = nullptr;
    const jint rc = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (rc == JNI_OK) return env;
    if (rc == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) == 0) return env;
    }
    return nullptr;
}

uint32_t read_memory(uint32_t address, uint8_t* buffer, uint32_t num_bytes, rc_client_t*) {
    return xora_host_memory_read(address, buffer, num_bytes);
}

void notify_status(const char* message) {
    if (!message || !g_bridge || !g_mid_status) return;
    JNIEnv* env = env_for_current_thread();
    if (!env) return;
    jstring jmsg = env->NewStringUTF(message);
    if (!jmsg) return;
    env->CallVoidMethod(g_bridge, g_mid_status, jmsg);
    env->DeleteLocalRef(jmsg);
}

void load_game_callback(int result, const char* error_message, rc_client_t* client, void* userdata);

void server_call(
    const rc_api_request_t* request,
    rc_client_server_callback_t callback,
    void* callback_data,
    rc_client_t*
) {
    rc_api_server_response_t response{};
    response.http_status_code = RC_API_SERVER_RESPONSE_CLIENT_ERROR;
    response.body = "";
    response.body_length = 0;

    if (!request || !request->url || !g_bridge || !g_mid_http) {
        if (callback) callback(&response, callback_data);
        return;
    }

    JNIEnv* env = env_for_current_thread();
    if (!env) {
        if (callback) callback(&response, callback_data);
        return;
    }

    jstring jurl = env->NewStringUTF(request->url);
    jstring jpost = request->post_data ? env->NewStringUTF(request->post_data) : nullptr;
    jstring jct = request->content_type ? env->NewStringUTF(request->content_type) : nullptr;

    // Prefer a Kotlin-validated login2 body so rcheevos never re-hits Cloudflare for sign-in.
    const bool is_login =
        request->post_data && std::strstr(request->post_data, "r=login2") != nullptr;
    std::string body_storage;
    if (is_login && !g_queued_login_json.empty()) {
        body_storage = g_queued_login_json;
        g_queued_login_json.clear();
        response.http_status_code = 200;
        ALOGI("RA login: using queued Connect login2 JSON (%zu bytes)", body_storage.size());
        if (jurl) env->DeleteLocalRef(jurl);
        if (jpost) env->DeleteLocalRef(jpost);
        if (jct) env->DeleteLocalRef(jct);
        response.body = body_storage.c_str();
        response.body_length = body_storage.size();
        if (callback) callback(&response, callback_data);
        return;
    }

    auto* jresult = reinterpret_cast<jobjectArray>(
        env->CallObjectMethod(g_bridge, g_mid_http, jurl, jpost, jct)
    );
    env->DeleteLocalRef(jurl);
    if (jpost) env->DeleteLocalRef(jpost);
    if (jct) env->DeleteLocalRef(jct);

    if (jresult && !env->ExceptionCheck()) {
        const jsize len = env->GetArrayLength(jresult);
        if (len >= 2) {
            auto status_obj = reinterpret_cast<jobject>(env->GetObjectArrayElement(jresult, 0));
            auto body_obj = reinterpret_cast<jstring>(env->GetObjectArrayElement(jresult, 1));
            if (status_obj) {
                jclass int_cls = env->FindClass("java/lang/Integer");
                jmethodID int_value = env->GetMethodID(int_cls, "intValue", "()I");
                response.http_status_code = env->CallIntMethod(status_obj, int_value);
                env->DeleteLocalRef(int_cls);
                env->DeleteLocalRef(status_obj);
            }
            if (body_obj) {
                const char* chars = env->GetStringUTFChars(body_obj, nullptr);
                if (chars) {
                    body_storage.assign(chars);
                    env->ReleaseStringUTFChars(body_obj, chars);
                }
                env->DeleteLocalRef(body_obj);
            }
        }
        env->DeleteLocalRef(jresult);
    } else if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    response.body = body_storage.c_str();
    response.body_length = body_storage.size();
    if (response.http_status_code < 200 || response.http_status_code >= 300) {
        ALOGW(
            "RA HTTP %d url=%s body=%.160s",
            response.http_status_code,
            request->url ? request->url : "?",
            body_storage.c_str()
        );
    }
    if (callback) callback(&response, callback_data);
}

void event_handler(const rc_client_event_t* event, rc_client_t*) {
    if (!event) return;
    switch (event->type) {
        case RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED: {
            if (!event->achievement || !g_bridge || !g_mid_unlock) break;
            JNIEnv* env = env_for_current_thread();
            if (!env) break;

            char badge_url[256]{};
            rc_client_achievement_get_image_url(
                event->achievement,
                RC_CLIENT_ACHIEVEMENT_STATE_UNLOCKED,
                badge_url,
                sizeof(badge_url)
            );

            const bool hardcore =
                (event->achievement->unlocked & RC_CLIENT_ACHIEVEMENT_UNLOCKED_HARDCORE) != 0;

            jstring jtitle = env->NewStringUTF(event->achievement->title ? event->achievement->title : "");
            jstring jdesc = env->NewStringUTF(
                event->achievement->description ? event->achievement->description : ""
            );
            jstring jbadge = env->NewStringUTF(badge_url);
            env->CallVoidMethod(
                g_bridge,
                g_mid_unlock,
                static_cast<jint>(event->achievement->id),
                jtitle,
                jdesc,
                static_cast<jint>(event->achievement->points),
                jbadge,
                hardcore ? JNI_TRUE : JNI_FALSE
            );
            env->DeleteLocalRef(jtitle);
            env->DeleteLocalRef(jdesc);
            env->DeleteLocalRef(jbadge);
            break;
        }
        case RC_CLIENT_EVENT_SERVER_ERROR:
            if (event->server_error && event->server_error->error_message) {
                ALOGW("RA server error: %s", event->server_error->error_message);
                notify_status(event->server_error->error_message);
            }
            break;
        default:
            break;
    }
}

void login_callback(int result, const char* error_message, rc_client_t* client, void*) {
    if (result == RC_OK) {
        g_logged_in = true;
        ALOGI("RA login ok");
        notify_status("RetroAchievements: signed in");
        if (!g_pending_hash.empty() && client) {
            const std::string hash = g_pending_hash;
            g_pending_hash.clear();
            rc_client_begin_load_game(client, hash.c_str(), load_game_callback, nullptr);
        }
    } else {
        g_logged_in = false;
        g_pending_hash.clear();
        ALOGE("RA login failed: %s", error_message ? error_message : rc_error_str(result));
        std::string msg = "RA login failed";
        if (error_message && error_message[0]) {
            msg += ": ";
            msg += error_message;
        }
        notify_status(msg.c_str());
    }
}

void load_game_callback(int result, const char* error_message, rc_client_t* client, void*) {
    if (result == RC_OK) {
        g_game_loaded = true;
        const rc_client_game_t* game = rc_client_get_game_info(client);
        rc_client_user_game_summary_t summary{};
        rc_client_get_user_game_summary(client, &summary);
        char buf[192];
        if (game && game->title) {
            std::snprintf(
                buf,
                sizeof(buf),
                "RA: %s — %u / %u",
                game->title,
                summary.num_unlocked_achievements,
                summary.num_core_achievements
            );
        } else {
            std::snprintf(
                buf,
                sizeof(buf),
                "RA: %u / %u unlocked",
                summary.num_unlocked_achievements,
                summary.num_core_achievements
            );
        }
        ALOGI("%s", buf);
        notify_status(buf);
    } else {
        g_game_loaded = false;
        ALOGE("RA load game failed: %s", error_message ? error_message : rc_error_str(result));
        std::string msg = "RA: no achievements for this ROM";
        if (error_message && error_message[0]) {
            msg = "RA: ";
            msg += error_message;
        }
        notify_status(msg.c_str());
    }
}

void destroy_client_unlocked() {
    if (g_client) {
        rc_client_unload_game(g_client);
        rc_client_destroy(g_client);
        g_client = nullptr;
    }
    g_logged_in = false;
    g_game_loaded = false;
    xora_host_memory_destroy();
}

bool ensure_client_unlocked() {
    if (g_client) return true;
    g_client = rc_client_create(read_memory, server_call);
    if (!g_client) return false;
    rc_client_set_event_handler(g_client, event_handler);
    rc_client_set_hardcore_enabled(g_client, g_hardcore_wanted ? 1 : 0);
    rc_client_set_unofficial_enabled(g_client, 0);
    rc_client_set_get_time_millisecs_function(
        g_client,
        [](const rc_client_t*) -> rc_clock_t {
            using namespace std::chrono;
            return static_cast<rc_clock_t>(
                duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count()
            );
        }
    );
    return true;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeRaAttach(
    JNIEnv* env,
    jclass,
    jobject bridge
) {
    std::lock_guard<std::recursive_mutex> lock(g_ra_mutex);
    if (g_bridge) {
        env->DeleteGlobalRef(g_bridge);
        g_bridge = nullptr;
    }
    if (!bridge) return;
    g_bridge = env->NewGlobalRef(bridge);
    jclass cls = env->GetObjectClass(bridge);
    g_mid_http = env->GetMethodID(
        cls,
        "performHttp",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/Object;"
    );
    g_mid_unlock = env->GetMethodID(
        cls,
        "onAchievementUnlocked",
        "(ILjava/lang/String;Ljava/lang/String;ILjava/lang/String;Z)V"
    );
    g_mid_status = env->GetMethodID(cls, "onStatus", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cls);
    ensure_client_unlocked();
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeRaDetach(JNIEnv* env, jclass) {
    std::lock_guard<std::recursive_mutex> lock(g_ra_mutex);
    destroy_client_unlocked();
    g_queued_login_json.clear();
    if (g_bridge) {
        env->DeleteGlobalRef(g_bridge);
        g_bridge = nullptr;
    }
    g_mid_http = nullptr;
    g_mid_unlock = nullptr;
    g_mid_status = nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeRaQueueLoginResponse(
    JNIEnv* env,
    jclass,
    jstring login_json
) {
    std::lock_guard<std::recursive_mutex> lock(g_ra_mutex);
    g_queued_login_json.clear();
    if (!login_json) return;
    const char* json = env->GetStringUTFChars(login_json, nullptr);
    if (json) {
        g_queued_login_json.assign(json);
        env->ReleaseStringUTFChars(login_json, json);
        ALOGI("RA queued login JSON (%zu bytes)", g_queued_login_json.size());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeRaLogin(
    JNIEnv* env,
    jclass,
    jstring username,
    jstring token
) {
    std::lock_guard<std::recursive_mutex> lock(g_ra_mutex);
    if (!ensure_client_unlocked() || !username || !token) return;
    const char* user = env->GetStringUTFChars(username, nullptr);
    const char* tok = env->GetStringUTFChars(token, nullptr);
    if (user && tok) {
        rc_client_begin_login_with_token(g_client, user, tok, login_callback, nullptr);
    }
    if (user) env->ReleaseStringUTFChars(username, user);
    if (tok) env->ReleaseStringUTFChars(token, tok);
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeRaSetHardcore(
    JNIEnv*,
    jclass,
    jboolean enabled
) {
    std::lock_guard<std::recursive_mutex> lock(g_ra_mutex);
    g_hardcore_wanted = enabled == JNI_TRUE;
    if (g_client) {
        rc_client_set_hardcore_enabled(g_client, g_hardcore_wanted ? 1 : 0);
        ALOGI("RA hardcore %s", g_hardcore_wanted ? "on" : "off");
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeRaInitMemory(
    JNIEnv*,
    jclass,
    jint console_id
) {
    std::lock_guard<std::recursive_mutex> lock(g_ra_mutex);
    const int ok = xora_host_memory_init(static_cast<uint32_t>(console_id));
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeRaLoadGame(
    JNIEnv* env,
    jclass,
    jstring md5_hex
) {
    std::lock_guard<std::recursive_mutex> lock(g_ra_mutex);
    if (!ensure_client_unlocked() || !md5_hex) return;
    const char* hash = env->GetStringUTFChars(md5_hex, nullptr);
    if (!hash) return;
    if (!g_logged_in) {
        g_pending_hash = hash;
        // Status stays on "signing in…" from Kotlin until login_callback fires.
        // Avoid a permanent "waiting for sign-in" if login already failed.
        ALOGI("RA load queued until login completes (hash=%s)", hash);
        env->ReleaseStringUTFChars(md5_hex, hash);
        return;
    }
    rc_client_begin_load_game(g_client, hash, load_game_callback, nullptr);
    env->ReleaseStringUTFChars(md5_hex, hash);
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeRaUnloadGame(JNIEnv*, jclass) {
    std::lock_guard<std::recursive_mutex> lock(g_ra_mutex);
    if (g_client) {
        rc_client_unload_game(g_client);
        g_game_loaded = false;
    }
    xora_host_memory_destroy();
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeRaDoFrame(JNIEnv*, jclass) {
    // Called every emulator frame; client isn't thread-safe.
    std::lock_guard<std::recursive_mutex> lock(g_ra_mutex);
    if (g_client && g_game_loaded) {
        rc_client_do_frame(g_client);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeRaIdle(JNIEnv*, jclass) {
    std::lock_guard<std::recursive_mutex> lock(g_ra_mutex);
    if (g_client) rc_client_idle(g_client);
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeRaReset(JNIEnv*, jclass) {
    std::lock_guard<std::recursive_mutex> lock(g_ra_mutex);
    if (g_client) rc_client_reset(g_client);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeRaSummary(JNIEnv* env, jclass) {
    std::lock_guard<std::recursive_mutex> lock(g_ra_mutex);
    if (!g_client || !g_game_loaded) return nullptr;
    rc_client_user_game_summary_t summary{};
    rc_client_get_user_game_summary(g_client, &summary);
    const rc_client_game_t* game = rc_client_get_game_info(g_client);
    char buf[192];
    if (game && game->title) {
        std::snprintf(
            buf,
            sizeof(buf),
            "%s — %u / %u",
            game->title,
            summary.num_unlocked_achievements,
            summary.num_core_achievements
        );
    } else {
        std::snprintf(
            buf,
            sizeof(buf),
            "%u / %u unlocked",
            summary.num_unlocked_achievements,
            summary.num_core_achievements
        );
    }
    return env->NewStringUTF(buf);
}
