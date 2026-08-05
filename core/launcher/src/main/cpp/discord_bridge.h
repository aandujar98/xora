#pragma once
#include "discordpp.h"
#include <jni.h>
#include <string>
#include <mutex>
#include <atomic>

class DiscordBridge {
public:
    DiscordBridge();
    ~DiscordBridge();

    bool Init(int64_t appId);
    void Authorize();
    void Shutdown();
    void SetTokenAndConnect(const char* token);
    void RefreshToken(const char* refreshToken);
    void Connect();
    void RefreshFriends();
    void SetActivity(
        int activityType,
        const char* name, const char* state, const char* details,
        int64_t startSecs, int64_t endSecs,
        const char* largeImage, const char* largeText,
        const char* smallImage, const char* smallText,
        const char* button1Label, const char* button1Url,
        const char* button2Label, const char* button2Url
    );
    void SetOnlineStatus(int statusType);
    void Clear();
    void RunCallbacks();
    bool IsReady() const { return ready_; }
    bool IsAuthorized() const { return authorized_; }
    void SetJavaVM(JavaVM* vm);
    void Destroy();

    static jclass GetDiscordSocialSdkBridgeClass() { return DiscordSocialSdkBridgeClass_; }
    static jmethodID GetOnNativeStatusChangedMethod() { return onNativeStatusChangedMethod_; }
    static void SetDiscordSocialSdkBridgeClass(JNIEnv* env, jclass clazz) {
        if (DiscordSocialSdkBridgeClass_) env->DeleteGlobalRef(DiscordSocialSdkBridgeClass_);
        DiscordSocialSdkBridgeClass_ = clazz;
    }
    static void SetOnNativeStatusChangedMethod(jmethodID method) { onNativeStatusChangedMethod_ = method; }
    static void SetOnNativeTokensReceivedMethod(jmethodID method) { onNativeTokensReceivedMethod_ = method; }
    static void SetOnNativeFriendsUpdatedMethod(jmethodID method) { onNativeFriendsUpdatedMethod_ = method; }
    static void SetOnNativePresenceResultMethod(jmethodID method) { onNativePresenceResultMethod_ = method; }
    static void SetOnNativeAuthErrorMethod(jmethodID method) { onNativeAuthErrorMethod_ = method; }

private:
    void DestroyUnlocked();
    void DoGetToken(std::string code, std::string redirectUri, std::string codeVerifier);
    void FireNativeStatusCallback(int statusCode, bool ready, bool authorized);
    void FireNativeTokensCallback(const std::string& access, const std::string& refresh, int32_t expiresIn);
    void FireNativeFriendsCallback(const std::string& payload);
    void FireNativePresenceResult(bool ok, const std::string& message);
    void FireNativeAuthError(const std::string& message);
    std::string BuildFriendsPayloadUnlocked();

    discordpp::Client* client_;
    std::atomic<bool> ready_;
    std::atomic<bool> authorized_;
    mutable std::mutex mutex_;
    int64_t appId_;
    JavaVM* javaVm_;

    static jclass DiscordSocialSdkBridgeClass_;
    static jmethodID onNativeStatusChangedMethod_;
    static jmethodID onNativeTokensReceivedMethod_;
    static jmethodID onNativeFriendsUpdatedMethod_;
    static jmethodID onNativePresenceResultMethod_;
    static jmethodID onNativeAuthErrorMethod_;
};

extern DiscordBridge g_discordBridge;
