#define DISCORDPP_IMPLEMENTATION
#include "discord_bridge.h"
#include <android/log.h>
#include <cstring>
#include <optional>
#include <vector>

#define LOG_TAG "SoraDiscord"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {
void ClearPendingJniException(JNIEnv* env) {
    if (env && env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}
}  // namespace

DiscordBridge g_discordBridge;

jclass DiscordBridge::DiscordSocialSdkBridgeClass_ = nullptr;
jmethodID DiscordBridge::onNativeStatusChangedMethod_ = nullptr;
jmethodID DiscordBridge::onNativeTokensReceivedMethod_ = nullptr;
jmethodID DiscordBridge::onNativeFriendsUpdatedMethod_ = nullptr;
jmethodID DiscordBridge::onNativePresenceResultMethod_ = nullptr;
jmethodID DiscordBridge::onNativeAuthErrorMethod_ = nullptr;
jmethodID DiscordBridge::onNativeMessagesUpdatedMethod_ = nullptr;
jmethodID DiscordBridge::onNativeMessageSendResultMethod_ = nullptr;
jmethodID DiscordBridge::onNativeCurrentUserMethod_ = nullptr;

DiscordBridge::DiscordBridge()
    : client_(nullptr),
      ready_(false),
      authorized_(false),
      appId_(0),
      javaVm_(nullptr),
      currentUserId_(0) {
    LOGI("DiscordBridge constructed");
}

DiscordBridge::~DiscordBridge() {
    LOGI("DiscordBridge destructor");
    Destroy();
}

bool DiscordBridge::Init(int64_t appId) {
    LOGI("Init called with appId=%lld", (long long)appId);
    std::lock_guard<std::mutex> lock(mutex_);
    if (client_) {
        LOGI("Init: client already exists, destroying first");
        DestroyUnlocked();
    }

    appId_ = appId;
    try {
        client_ = new discordpp::Client();
        LOGI("Init: Client created, setting appId and callback");
        client_->SetApplicationId(static_cast<uint64_t>(appId));
        client_->SetStatusChangedCallback(
            [this](discordpp::Client::Status status,
                   discordpp::Client::Error error,
                   int32_t errorDetail) {
                // Never hold mutex_ across JNI — Java may call back into SetActivity/Connect.
                bool fireReadyFriends = false;
                bool fireAuthError = false;
                std::string authErrorMsg;
                bool readyCopy = false;
                bool authorizedCopy = false;
                std::string friendsPayload;
                std::string currentUserIdStr;
                {
                    std::lock_guard<std::mutex> lock(mutex_);
                    const char* statusStr = "Unknown";
                    switch (status) {
                        case discordpp::Client::Status::Connecting:    statusStr = "Connecting"; break;
                        case discordpp::Client::Status::Connected:     statusStr = "Connected"; break;
                        case discordpp::Client::Status::Ready:         statusStr = "Ready"; break;
                        case discordpp::Client::Status::Disconnected:  statusStr = "Disconnected"; break;
                        case discordpp::Client::Status::Reconnecting:  statusStr = "Reconnecting"; break;
                        case discordpp::Client::Status::Disconnecting: statusStr = "Disconnecting"; break;
                        case discordpp::Client::Status::HttpWait:      statusStr = "HttpWait"; break;
                    }
                    const char* errorStr = "None";
                    switch (error) {
                        case discordpp::Client::Error::None:              errorStr = "None"; break;
                        case discordpp::Client::Error::ConnectionFailed:  errorStr = "ConnectionFailed"; break;
                        case discordpp::Client::Error::UnexpectedClose:   errorStr = "UnexpectedClose"; break;
                        case discordpp::Client::Error::ConnectionCanceled: errorStr = "ConnectionCanceled"; break;
                    }
                    LOGI("StatusChanged: status=%s(%d) error=%s(%d) errorDetail=%d ready_=%s authorized_=%s",
                         statusStr, static_cast<int>(status),
                         errorStr, static_cast<int>(error),
                         errorDetail,
                         ready_ ? "true" : "false",
                         authorized_ ? "true" : "false");
                    if (status == discordpp::Client::Status::Ready) {
                        ready_ = true;
                        LOGI("STATUS: Ready! Connection established");
                        CaptureCurrentUserUnlocked();
                        RegisterMessageCallbacksUnlocked();
                        fireReadyFriends = true;
                        friendsPayload = BuildFriendsPayloadUnlocked();
                        if (currentUserId_ != 0) {
                            currentUserIdStr = std::to_string(currentUserId_);
                        }
                    } else if (status == discordpp::Client::Status::Disconnected) {
                        if (ready_) {
                            LOGW("STATUS: Disconnected while previously ready (err=%s)", errorStr);
                        }
                        ready_ = false;
                        if (error == discordpp::Client::Error::ConnectionFailed ||
                            error == discordpp::Client::Error::UnexpectedClose) {
                            authorized_ = false;
                            fireAuthError = true;
                            authErrorMsg = std::string("Disconnected: ") + errorStr;
                        }
                    } else if (status == discordpp::Client::Status::Disconnecting) {
                        LOGI("STATUS: Disconnecting...");
                    } else if (status == discordpp::Client::Status::Reconnecting) {
                        LOGW("STATUS: Reconnecting...");
                    } else if (status == discordpp::Client::Status::HttpWait) {
                        LOGI("STATUS: HttpWait (rate limited?)");
                    } else if (status == discordpp::Client::Status::Connecting) {
                        LOGI("STATUS: Connecting...");
                    } else if (status == discordpp::Client::Status::Connected) {
                        LOGI("STATUS: Connected (not yet ready)");
                    }
                    readyCopy = ready_;
                    authorizedCopy = authorized_;
                }
                if (fireAuthError) {
                    FireNativeAuthError(authErrorMsg);
                }
                if (fireReadyFriends) {
                    if (!currentUserIdStr.empty()) {
                        FireNativeCurrentUser(currentUserIdStr);
                    }
                    if (!friendsPayload.empty()) {
                        FireNativeFriendsCallback(friendsPayload);
                    }
                }
                FireNativeStatusCallback(static_cast<int>(status), readyCopy, authorizedCopy);
            });
        client_->SetRelationshipGroupsUpdatedCallback(
            [this](uint64_t /*userId*/) {
                std::string payload;
                {
                    std::lock_guard<std::mutex> lock(mutex_);
                    if (!ready_ || !client_) return;
                    payload = BuildFriendsPayloadUnlocked();
                }
                if (!payload.empty()) {
                    FireNativeFriendsCallback(payload);
                }
            });
        RegisterMessageCallbacksUnlocked();
        LOGI("Init: success");
        return true;
    } catch (const std::exception& e) {
        LOGE("Init failed with exception: %s", e.what());
        delete client_;
        client_ = nullptr;
        return false;
    } catch (...) {
        LOGE("Init failed with unknown exception");
        delete client_;
        client_ = nullptr;
        return false;
    }
}

void DiscordBridge::Authorize() {
    LOGI("Authorize called (client_=%s, authorized_=%s)",
         client_ ? "exists" : "null",
         authorized_ ? "true" : "false");
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) {
        LOGE("Authorize: no client, aborting");
        return;
    }
    if (authorized_) {
        if (!ready_ && client_) {
            LOGI("Authorize: already authorized but not Ready — Connect()");
            try {
                client_->Connect();
            } catch (const std::exception& e) {
                LOGE("Authorize Connect threw: %s", e.what());
                FireNativeAuthError(e.what());
            } catch (...) {
                LOGE("Authorize Connect threw unknown");
                FireNativeAuthError("Connect failed");
            }
        } else {
            LOGW("Authorize: already authorized+ready, aborting");
        }
        return;
    }

    try {
        authorized_ = false;
        ready_ = false;

        LOGI("Authorize: creating code verifier");
        auto verifier = client_->CreateAuthorizationCodeVerifier();
        LOGI("Authorize: PKCE verifier created (challenge method=S256)");

        discordpp::AuthorizationArgs args;
        args.SetClientId(static_cast<uint64_t>(appId_));
        // Communication scopes include presence + DM messaging for in-launcher chat.
        auto scopes = discordpp::Client::GetDefaultCommunicationScopes();
        LOGI("Authorize: communication scopes=%s", scopes.c_str());
        args.SetScopes(scopes);

        discordpp::AuthorizationCodeChallenge challenge;
        challenge.SetChallenge(verifier.Challenge().Challenge());
        challenge.SetMethod(discordpp::AuthenticationCodeChallengeMethod::S256);
        args.SetCodeChallenge(challenge);

        LOGI("Authorize: calling client_->Authorize()...");
        client_->Authorize(
            std::move(args),
            [this, ver = std::move(verifier)](
                discordpp::ClientResult result,
                std::string code,
                std::string redirectUri
            ) mutable {
                if (!result.Successful()) {
                    LOGE("Authorize callback FAILED: err=%s errCode=%d retryable=%s",
                         result.Error().c_str(),
                         result.ErrorCode(),
                         result.Retryable() ? "true" : "false");
                    FireNativeAuthError(result.Error());
                    return;
                }
                LOGI("Authorize callback SUCCEEDED");
                LOGI("Authorize: redirectUri=%s codeLen=%zu",
                     redirectUri.c_str(), code.size());
                LOGI("Authorize: exchanging code for token...");
                DoGetToken(std::move(code), std::move(redirectUri), ver.Verifier());
            }
        );
        LOGI("Authorize: client_->Authorize() returned (async flow started)");
        LOGI("Authorize: expected mobile redirect discord-%lld:/authorize/callback",
             (long long)appId_);
    } catch (const std::exception& e) {
        LOGE("Authorize threw exception: %s", e.what());
    } catch (...) {
        LOGE("Authorize threw unknown exception");
    }
}

void DiscordBridge::DoGetToken(
    std::string code, std::string redirectUri, std::string codeVerifier
) {
    LOGI("DoGetToken: exchanging authorization code for token");
    if (!client_) {
        LOGE("DoGetToken: no client");
        return;
    }
    try {
        LOGI("DoGetToken: calling client_->GetToken()...");
        client_->GetToken(
            static_cast<uint64_t>(appId_),
            code,
            codeVerifier,
            redirectUri,
            [this](discordpp::ClientResult result,
                   std::string accessToken,
                   std::string refreshToken,
                   discordpp::AuthorizationTokenType tokenType,
                   int32_t expiresIn,
                   std::string scopes) {
                if (!result.Successful()) {
                    LOGE("GetToken FAILED: err=%s errCode=%d",
                         result.Error().c_str(), result.ErrorCode());
                    FireNativeAuthError(result.Error());
                    return;
                }
                LOGI("GetToken SUCCEEDED: tokenType=%d expiresIn=%d scopes=%s",
                     static_cast<int>(tokenType), expiresIn, scopes.c_str());
                FireNativeTokensCallback(accessToken, refreshToken, expiresIn);
                LOGI("GetToken: calling UpdateToken...");
                client_->UpdateToken(
                    tokenType, accessToken,
                    [this](discordpp::ClientResult r) {
                        if (!r.Successful()) {
                            LOGE("UpdateToken FAILED: err=%s errCode=%d",
                                 r.Error().c_str(), r.ErrorCode());
                            FireNativeAuthError(r.Error());
                            return;
                        }
                        authorized_ = true;
                        LOGI("UpdateToken SUCCEEDED, calling Connect...");
                        client_->Connect();
                        LOGI("Connect called");
                    }
                );
            }
        );
    } catch (const std::exception& e) {
        LOGE("DoGetToken threw exception: %s", e.what());
        FireNativeAuthError(e.what());
    } catch (...) {
        LOGE("DoGetToken threw unknown exception");
        FireNativeAuthError("GetToken failed");
    }
}

void DiscordBridge::FireNativeStatusCallback(int statusCode, bool ready, bool authorized) {
    if (!javaVm_) return;
    JNIEnv* env = nullptr;
    int getEnvStat = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    bool needsDetach = false;
    if (getEnvStat == JNI_EDETACHED) {
        JavaVMAttachArgs args = {JNI_VERSION_1_6, "DiscordCallback", nullptr};
        if (javaVm_->AttachCurrentThread(&env, &args) != JNI_OK) return;
        needsDetach = true;
    } else if (getEnvStat != JNI_OK) {
        return;
    }

    if (DiscordSocialSdkBridgeClass_ && onNativeStatusChangedMethod_) {
        env->CallStaticVoidMethod(DiscordSocialSdkBridgeClass_, onNativeStatusChangedMethod_, statusCode, ready, authorized);
        ClearPendingJniException(env);
    }

    if (needsDetach) {
        javaVm_->DetachCurrentThread();
    }
}

void DiscordBridge::FireNativeTokensCallback(
    const std::string& access, const std::string& refresh, int32_t expiresIn
) {
    if (!javaVm_ || !DiscordSocialSdkBridgeClass_ || !onNativeTokensReceivedMethod_) return;
    JNIEnv* env = nullptr;
    int getEnvStat = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    bool needsDetach = false;
    if (getEnvStat == JNI_EDETACHED) {
        JavaVMAttachArgs args = {JNI_VERSION_1_6, "DiscordTokens", nullptr};
        if (javaVm_->AttachCurrentThread(&env, &args) != JNI_OK) return;
        needsDetach = true;
    } else if (getEnvStat != JNI_OK) {
        return;
    }

    jstring jAccess = env->NewStringUTF(access.c_str());
    jstring jRefresh = env->NewStringUTF(refresh.c_str());
    env->CallStaticVoidMethod(
        DiscordSocialSdkBridgeClass_,
        onNativeTokensReceivedMethod_,
        jAccess,
        jRefresh,
        static_cast<jint>(expiresIn)
    );
    ClearPendingJniException(env);
    if (jAccess) env->DeleteLocalRef(jAccess);
    if (jRefresh) env->DeleteLocalRef(jRefresh);

    if (needsDetach) {
        javaVm_->DetachCurrentThread();
    }
}

void DiscordBridge::FireNativeFriendsCallback(const std::string& payload) {
    if (!javaVm_ || !DiscordSocialSdkBridgeClass_ || !onNativeFriendsUpdatedMethod_) return;
    JNIEnv* env = nullptr;
    int getEnvStat = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    bool needsDetach = false;
    if (getEnvStat == JNI_EDETACHED) {
        JavaVMAttachArgs args = {JNI_VERSION_1_6, "DiscordFriends", nullptr};
        if (javaVm_->AttachCurrentThread(&env, &args) != JNI_OK) return;
        needsDetach = true;
    } else if (getEnvStat != JNI_OK) {
        return;
    }

    jstring jPayload = env->NewStringUTF(payload.c_str());
    env->CallStaticVoidMethod(
        DiscordSocialSdkBridgeClass_,
        onNativeFriendsUpdatedMethod_,
        jPayload
    );
    ClearPendingJniException(env);
    if (jPayload) env->DeleteLocalRef(jPayload);

    if (needsDetach) {
        javaVm_->DetachCurrentThread();
    }
}

void DiscordBridge::FireNativePresenceResult(bool ok, const std::string& message) {
    if (!javaVm_ || !DiscordSocialSdkBridgeClass_ || !onNativePresenceResultMethod_) return;
    JNIEnv* env = nullptr;
    int getEnvStat = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    bool needsDetach = false;
    if (getEnvStat == JNI_EDETACHED) {
        JavaVMAttachArgs args = {JNI_VERSION_1_6, "DiscordPresence", nullptr};
        if (javaVm_->AttachCurrentThread(&env, &args) != JNI_OK) return;
        needsDetach = true;
    } else if (getEnvStat != JNI_OK) {
        return;
    }

    jstring jMessage = env->NewStringUTF(message.c_str());
    env->CallStaticVoidMethod(
        DiscordSocialSdkBridgeClass_,
        onNativePresenceResultMethod_,
        ok ? JNI_TRUE : JNI_FALSE,
        jMessage
    );
    ClearPendingJniException(env);
    if (jMessage) env->DeleteLocalRef(jMessage);

    if (needsDetach) {
        javaVm_->DetachCurrentThread();
    }
}

void DiscordBridge::FireNativeAuthError(const std::string& message) {
    if (!javaVm_ || !DiscordSocialSdkBridgeClass_ || !onNativeAuthErrorMethod_) return;
    JNIEnv* env = nullptr;
    int getEnvStat = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    bool needsDetach = false;
    if (getEnvStat == JNI_EDETACHED) {
        JavaVMAttachArgs args = {JNI_VERSION_1_6, "DiscordAuthError", nullptr};
        if (javaVm_->AttachCurrentThread(&env, &args) != JNI_OK) return;
        needsDetach = true;
    } else if (getEnvStat != JNI_OK) {
        return;
    }

    jstring jMessage = env->NewStringUTF(message.c_str());
    env->CallStaticVoidMethod(
        DiscordSocialSdkBridgeClass_,
        onNativeAuthErrorMethod_,
        jMessage
    );
    ClearPendingJniException(env);
    if (jMessage) env->DeleteLocalRef(jMessage);

    if (needsDetach) {
        javaVm_->DetachCurrentThread();
    }
}

std::string DiscordBridge::BuildFriendsPayloadUnlocked() {
    if (!client_ || !ready_) return {};
    std::string out;
    auto appendGroup = [&](discordpp::RelationshipGroupType group, const char* label) {
        try {
            auto relationships = client_->GetRelationshipsByGroup(group);
            for (const auto& rel : relationships) {
                if (rel.DiscordRelationshipType() != discordpp::RelationshipType::Friend &&
                    rel.GameRelationshipType() != discordpp::RelationshipType::Friend) {
                    continue;
                }
                std::string name;
                std::string avatarUrl;
                auto user = rel.User();
                if (user) {
                    name = user->DisplayName();
                    if (name.empty()) name = user->Username();
                    try {
                        avatarUrl = user->AvatarUrl(
                            discordpp::UserHandle::AvatarType::Png,
                            discordpp::UserHandle::AvatarType::Png);
                    } catch (...) {
                        avatarUrl.clear();
                    }
                    if (avatarUrl.empty()) {
                        auto hashOpt = user->Avatar();
                        if (hashOpt && !hashOpt->empty()) {
                            avatarUrl = "https://cdn.discordapp.com/avatars/";
                            avatarUrl += std::to_string(user->Id());
                            avatarUrl += '/';
                            avatarUrl += *hashOpt;
                            avatarUrl += ".png";
                        }
                    }
                }
                if (name.empty()) name = std::to_string(rel.Id());
                // Escape tabs/newlines in display names / URLs.
                for (char& c : name) {
                    if (c == '\t' || c == '\n' || c == '\r') c = ' ';
                }
                for (char& c : avatarUrl) {
                    if (c == '\t' || c == '\n' || c == '\r') c = ' ';
                }
                out += std::to_string(rel.Id());
                out += '\t';
                out += name;
                out += '\t';
                out += label;
                out += '\t';
                out += avatarUrl;
                out += '\n';
            }
        } catch (const std::exception& e) {
            LOGW("BuildFriendsPayload: group %s threw: %s", label, e.what());
        } catch (...) {
            LOGW("BuildFriendsPayload: group %s threw unknown", label);
        }
    };
    appendGroup(discordpp::RelationshipGroupType::OnlinePlayingGame, "online_game");
    appendGroup(discordpp::RelationshipGroupType::OnlineElsewhere, "online_elsewhere");
    appendGroup(discordpp::RelationshipGroupType::Offline, "offline");
    return out;
}

void DiscordBridge::SetActivity(
    int activityType,
    const char* name, const char* state, const char* details,
    int64_t startSecs, int64_t endSecs,
    const char* largeImage, const char* largeText,
    const char* smallImage, const char* smallText,
    const char* button1Label, const char* button1Url,
    const char* button2Label, const char* button2Url
) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) { LOGW("SetActivity: no client, skipping"); return; }
    // Mobile docs: presence requires Connect()/Ready. RPC-without-auth is desktop-only.
    if (!ready_) {
        LOGW("SetActivity: not Ready — skipping (RPC-without-auth is desktop-only)");
        FireNativePresenceResult(false, "Connect required (RPC-without-auth is desktop-only)");
        return;
    }
    LOGI("SetActivity: type=%d name=%s state=%s details=%s startSecs=%lld",
         activityType,
         name ? name : "null", state ? state : "null", details ? details : "null",
         (long long)startSecs);

    try {
        discordpp::Activity activity;
        activity.SetType(static_cast<discordpp::ActivityTypes>(activityType));
        if (appId_ > 0) {
            activity.SetApplicationId(std::optional<uint64_t>(static_cast<uint64_t>(appId_)));
        }
        if (name) activity.SetName(std::string(name));
        if (state) activity.SetState(std::string(state));
        if (details) activity.SetDetails(std::string(details));

        if (startSecs > 0 || endSecs > 0) {
            discordpp::ActivityTimestamps ts;
            if (startSecs > 0) ts.SetStart(static_cast<uint64_t>(startSecs));
            if (endSecs > 0) ts.SetEnd(static_cast<uint64_t>(endSecs));
            activity.SetTimestamps(std::move(ts));
        }

        // Only attach assets when keys are present — empty assets can confuse Discord clients.
        const bool hasAssets = (largeImage && largeImage[0]) || (largeText && largeText[0]) ||
                               (smallImage && smallImage[0]) || (smallText && smallText[0]);
        if (hasAssets) {
            discordpp::ActivityAssets assets;
            if (largeImage && largeImage[0]) assets.SetLargeImage(std::string(largeImage));
            if (largeText && largeText[0]) assets.SetLargeText(std::string(largeText));
            if (smallImage && smallImage[0]) assets.SetSmallImage(std::string(smallImage));
            if (smallText && smallText[0]) assets.SetSmallText(std::string(smallText));
            activity.SetAssets(std::move(assets));
        }

        if (button1Label && button1Url && strlen(button1Label) > 0 && strlen(button1Url) > 0) {
            discordpp::ActivityButton btn1;
            btn1.SetLabel(std::string(button1Label));
            btn1.SetUrl(std::string(button1Url));
            activity.AddButton(std::move(btn1));
        }
        if (button2Label && button2Url && strlen(button2Label) > 0 && strlen(button2Url) > 0) {
            discordpp::ActivityButton btn2;
            btn2.SetLabel(std::string(button2Label));
            btn2.SetUrl(std::string(button2Url));
            activity.AddButton(std::move(btn2));
        }

        LOGI("SetActivity: calling client_->UpdateRichPresence...");
        client_->UpdateRichPresence(
            std::move(activity),
            [this](discordpp::ClientResult r) {
                if (!r.Successful()) {
                    LOGE("SetActivity: UpdateRichPresence FAILED: err=%s errCode=%d retryable=%s",
                         r.Error().c_str(), r.ErrorCode(),
                         r.Retryable() ? "true" : "false");
                    FireNativePresenceResult(false, r.Error());
                } else {
                    LOGI("SetActivity: UpdateRichPresence succeeded");
                    FireNativePresenceResult(true, "ok");
                }
            }
        );
    } catch (const std::exception& e) {
        LOGE("SetActivity threw exception: %s", e.what());
        FireNativePresenceResult(false, e.what());
    } catch (...) {
        LOGE("SetActivity threw unknown exception");
        FireNativePresenceResult(false, "unknown");
    }
}

void DiscordBridge::SetOnlineStatus(int statusType) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) { LOGW("SetOnlineStatus: no client, skipping"); return; }
    if (!ready_) { LOGW("SetOnlineStatus: not ready, skipping"); return; }
    LOGI("SetOnlineStatus: setting status to %d", statusType);
    try {
        client_->SetOnlineStatus(
            static_cast<discordpp::StatusType>(statusType),
            [](discordpp::ClientResult r) {
                if (!r.Successful()) {
                    LOGE("SetOnlineStatus: FAILED: err=%s errCode=%d",
                         r.Error().c_str(), r.ErrorCode());
                } else {
                    LOGI("SetOnlineStatus: succeeded");
                }
            }
        );
    } catch (const std::exception& e) {
        LOGE("SetOnlineStatus threw exception: %s", e.what());
    } catch (...) {
        LOGE("SetOnlineStatus threw unknown exception");
    }
}

void DiscordBridge::Clear() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) {
        LOGW("Clear: no client, skipping");
        return;
    }
    // ClearRichPresence / empty UpdateRichPresence without Ready hits desktop-only RPC.
    if (!ready_) {
        LOGI("Clear: not Ready — nothing to clear remotely");
        return;
    }
    LOGI("Clear: ClearRichPresence");
    try {
        client_->ClearRichPresence();
    } catch (const std::exception& e) {
        LOGE("Clear threw exception: %s", e.what());
    } catch (...) {
        LOGE("Clear threw unknown exception");
    }
}

void DiscordBridge::Shutdown() {
    LOGI("Shutdown called (ready_=%s, authorized_=%s, client_=%s)",
         ready_ ? "true" : "false",
         authorized_ ? "true" : "false",
         client_ ? "exists" : "null");
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) {
        LOGW("Shutdown: no client, nothing to do");
        return;
    }
    try {
        LOGI("Shutdown: calling client_->Disconnect()...");
        client_->Disconnect();
        LOGI("Shutdown: client_->Disconnect() returned");
    } catch (const std::exception& e) {
        LOGE("Shutdown threw exception: %s", e.what());
    } catch (...) {
        LOGE("Shutdown threw unknown exception");
    }
    ready_ = false;
    authorized_ = false;
    LOGI("Shutdown: complete (ready_=false, authorized_=false)");
}

void DiscordBridge::SetTokenAndConnect(const char* token) {
    LOGI("SetTokenAndConnect: token=%s, ready_=%s, authorized_=%s",
         token ? "provided" : "null",
         ready_ ? "true" : "false",
         authorized_ ? "true" : "false");
    if (!token) { LOGE("SetTokenAndConnect: null token"); return; }
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) { LOGE("SetTokenAndConnect: no client"); return; }
    if (authorized_ && ready_) {
        LOGW("SetTokenAndConnect: already authorized+ready, skipping");
        return;
    }
    try {
        LOGI("SetTokenAndConnect: calling client_->UpdateToken(Bearer, token_len=%zu)...",
             strlen(token));
        client_->UpdateToken(
            discordpp::AuthorizationTokenType::Bearer,
            std::string(token),
            [this](discordpp::ClientResult result) {
                if (result.Successful()) {
                    std::lock_guard<std::mutex> lk(mutex_);
                    authorized_ = true;
                    LOGI("SetTokenAndConnect: UpdateToken succeeded, calling Connect");
                    if (client_ && !ready_) {
                        client_->Connect();
                    }
                } else {
                    LOGE("SetTokenAndConnect: UpdateToken FAILED: err=%s errCode=%d retryable=%s",
                         result.Error().c_str(), result.ErrorCode(),
                         result.Retryable() ? "true" : "false");
                    FireNativeAuthError(result.Error());
                }
            }
        );
        LOGI("SetTokenAndConnect: UpdateToken initiated (async)");
    } catch (const std::exception& e) {
        LOGE("SetTokenAndConnect threw exception: %s", e.what());
    } catch (...) {
        LOGE("SetTokenAndConnect threw unknown exception");
    }
}

void DiscordBridge::RefreshToken(const char* refreshToken) {
    if (!refreshToken) { LOGE("RefreshToken: null"); return; }
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) { LOGE("RefreshToken: no client"); return; }
    try {
        LOGI("RefreshToken: calling client_->RefreshToken...");
        client_->RefreshToken(
            static_cast<uint64_t>(appId_),
            std::string(refreshToken),
            [this](discordpp::ClientResult result,
                   std::string accessToken,
                   std::string newRefreshToken,
                   discordpp::AuthorizationTokenType tokenType,
                   int32_t expiresIn,
                   std::string /*scopes*/) {
                if (!result.Successful()) {
                    LOGE("RefreshToken FAILED: err=%s errCode=%d",
                         result.Error().c_str(), result.ErrorCode());
                    FireNativeAuthError(result.Error());
                    return;
                }
                LOGI("RefreshToken SUCCEEDED expiresIn=%d", expiresIn);
                FireNativeTokensCallback(accessToken, newRefreshToken, expiresIn);
                client_->UpdateToken(
                    tokenType, accessToken,
                    [this](discordpp::ClientResult r) {
                        if (!r.Successful()) {
                            LOGE("RefreshToken UpdateToken FAILED: %s", r.Error().c_str());
                            return;
                        }
                        authorized_ = true;
                        if (client_ && !ready_) {
                            client_->Connect();
                        }
                    }
                );
            }
        );
    } catch (const std::exception& e) {
        LOGE("RefreshToken threw: %s", e.what());
    } catch (...) {
        LOGE("RefreshToken threw unknown");
    }
}

void DiscordBridge::RefreshFriends() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_ || !ready_) {
        LOGW("RefreshFriends: not ready");
        return;
    }
    FireNativeFriendsCallback(BuildFriendsPayloadUnlocked());
}

std::string DiscordBridge::EscapeTsvField(std::string value) {
    for (char& c : value) {
        if (c == '\t' || c == '\n' || c == '\r') c = ' ';
    }
    return value;
}

std::string DiscordBridge::FormatMessageLine(const discordpp::MessageHandle& msg) {
    std::string line;
    line += std::to_string(msg.Id());
    line += '\t';
    line += std::to_string(msg.AuthorId());
    line += '\t';
    line += std::to_string(msg.RecipientId());
    line += '\t';
    line += std::to_string(msg.SentTimestamp());
    line += '\t';
    line += msg.SentFromGame() ? "1" : "0";
    line += '\t';
    line += EscapeTsvField(msg.Content());
    return line;
}

void DiscordBridge::CaptureCurrentUserUnlocked() {
    if (!client_) return;
    try {
        auto user = client_->GetCurrentUserV2();
        if (user) {
            currentUserId_ = user->Id();
            LOGI("CaptureCurrentUser: id=%llu", (unsigned long long)currentUserId_);
        }
    } catch (const std::exception& e) {
        LOGW("CaptureCurrentUser threw: %s", e.what());
    } catch (...) {
        LOGW("CaptureCurrentUser threw unknown");
    }
}

void DiscordBridge::RegisterMessageCallbacksUnlocked() {
    if (!client_) return;
    try {
        client_->SetMessageCreatedCallback(
            [this](uint64_t messageId) {
                uint64_t peerId = 0;
                {
                    std::lock_guard<std::mutex> lock(mutex_);
                    if (!client_ || !ready_) return;
                    CaptureCurrentUserUnlocked();
                    auto handle = client_->GetMessageHandle(messageId);
                    if (!handle) {
                        LOGW("MessageCreated: no handle for %llu", (unsigned long long)messageId);
                        return;
                    }
                    const uint64_t authorId = handle->AuthorId();
                    const uint64_t recipientId = handle->RecipientId();
                    if (currentUserId_ != 0 && authorId == currentUserId_) {
                        peerId = recipientId;
                    } else {
                        peerId = authorId;
                    }
                    LOGI("MessageCreated: id=%llu peer=%llu author=%llu",
                         (unsigned long long)messageId,
                         (unsigned long long)peerId,
                         (unsigned long long)authorId);
                }
                if (peerId != 0) {
                    LoadUserMessages(peerId, 50);
                }
            });
        LOGI("RegisterMessageCallbacks: SetMessageCreatedCallback ok");
    } catch (const std::exception& e) {
        LOGE("RegisterMessageCallbacks threw: %s", e.what());
    } catch (...) {
        LOGE("RegisterMessageCallbacks threw unknown");
    }
}

void DiscordBridge::LoadUserMessagesUnlocked(uint64_t recipientId, int32_t limit) {
    if (!client_ || !ready_) {
        LOGW("LoadUserMessages: not ready");
        return;
    }
    const int32_t effectiveLimit = limit > 0 ? limit : 50;
    LOGI("LoadUserMessages: recipient=%llu limit=%d",
         (unsigned long long)recipientId, effectiveLimit);
    try {
        client_->GetUserMessagesWithLimit(
            recipientId,
            effectiveLimit,
            [this, recipientId](discordpp::ClientResult result,
                                std::vector<discordpp::MessageHandle> messages) {
                if (!result.Successful()) {
                    LOGE("LoadUserMessages FAILED: err=%s errCode=%d",
                         result.Error().c_str(), result.ErrorCode());
                    FireNativeMessagesUpdated(std::to_string(recipientId), "");
                    return;
                }
                // SDK returns newest-first; reverse to chronological (oldest first).
                std::string payload;
                for (auto it = messages.rbegin(); it != messages.rend(); ++it) {
                    payload += FormatMessageLine(*it);
                    payload += '\n';
                }
                LOGI("LoadUserMessages: peer=%llu count=%zu",
                     (unsigned long long)recipientId, messages.size());
                FireNativeMessagesUpdated(std::to_string(recipientId), payload);
            });
    } catch (const std::exception& e) {
        LOGE("LoadUserMessages threw: %s", e.what());
        FireNativeMessagesUpdated(std::to_string(recipientId), "");
    } catch (...) {
        LOGE("LoadUserMessages threw unknown");
        FireNativeMessagesUpdated(std::to_string(recipientId), "");
    }
}

void DiscordBridge::SendUserMessage(uint64_t recipientId, const char* content) {
    if (!content) {
        FireNativeMessageSendResult(false, "Empty message", std::to_string(recipientId), "0");
        return;
    }
    std::string body(content);
    std::string syncError;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!client_ || !ready_) {
            LOGW("SendUserMessage: not ready");
            syncError = "Discord not ready";
        } else {
            LOGI("SendUserMessage: recipient=%llu len=%zu",
                 (unsigned long long)recipientId, body.size());
            try {
                client_->SendUserMessage(
                    recipientId,
                    body,
                    [this, recipientId](discordpp::ClientResult result, uint64_t messageId) {
                        if (!result.Successful()) {
                            LOGE("SendUserMessage FAILED: err=%s errCode=%d",
                                 result.Error().c_str(), result.ErrorCode());
                            FireNativeMessageSendResult(
                                false,
                                result.Error(),
                                std::to_string(recipientId),
                                "0");
                            return;
                        }
                        LOGI("SendUserMessage ok messageId=%llu", (unsigned long long)messageId);
                        FireNativeMessageSendResult(
                            true,
                            "",
                            std::to_string(recipientId),
                            std::to_string(messageId));
                    });
            } catch (const std::exception& e) {
                LOGE("SendUserMessage threw: %s", e.what());
                syncError = e.what();
            } catch (...) {
                LOGE("SendUserMessage threw unknown");
                syncError = "Send failed";
            }
        }
    }
    if (!syncError.empty()) {
        FireNativeMessageSendResult(false, syncError, std::to_string(recipientId), "0");
    }
}

void DiscordBridge::LoadUserMessages(uint64_t recipientId, int32_t limit) {
    std::lock_guard<std::mutex> lock(mutex_);
    LoadUserMessagesUnlocked(recipientId, limit);
}

void DiscordBridge::SetShowingChat(bool showing) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) {
        LOGW("SetShowingChat: no client");
        return;
    }
    LOGI("SetShowingChat: %s", showing ? "true" : "false");
    try {
        client_->SetShowingChat(showing);
    } catch (const std::exception& e) {
        LOGE("SetShowingChat threw: %s", e.what());
    } catch (...) {
        LOGE("SetShowingChat threw unknown");
    }
}

void DiscordBridge::FireNativeMessagesUpdated(
    const std::string& recipientId, const std::string& payload
) {
    if (!javaVm_ || !DiscordSocialSdkBridgeClass_ || !onNativeMessagesUpdatedMethod_) return;
    JNIEnv* env = nullptr;
    int getEnvStat = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    bool needsDetach = false;
    if (getEnvStat == JNI_EDETACHED) {
        JavaVMAttachArgs args = {JNI_VERSION_1_6, "DiscordMessages", nullptr};
        if (javaVm_->AttachCurrentThread(&env, &args) != JNI_OK) return;
        needsDetach = true;
    } else if (getEnvStat != JNI_OK) {
        return;
    }

    jstring jRecipient = env->NewStringUTF(recipientId.c_str());
    jstring jPayload = env->NewStringUTF(payload.c_str());
    env->CallStaticVoidMethod(
        DiscordSocialSdkBridgeClass_,
        onNativeMessagesUpdatedMethod_,
        jRecipient,
        jPayload
    );
    ClearPendingJniException(env);
    if (jRecipient) env->DeleteLocalRef(jRecipient);
    if (jPayload) env->DeleteLocalRef(jPayload);

    if (needsDetach) {
        javaVm_->DetachCurrentThread();
    }
}

void DiscordBridge::FireNativeMessageSendResult(
    bool ok,
    const std::string& errorMessage,
    const std::string& recipientId,
    const std::string& messageId
) {
    if (!javaVm_ || !DiscordSocialSdkBridgeClass_ || !onNativeMessageSendResultMethod_) return;
    JNIEnv* env = nullptr;
    int getEnvStat = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    bool needsDetach = false;
    if (getEnvStat == JNI_EDETACHED) {
        JavaVMAttachArgs args = {JNI_VERSION_1_6, "DiscordSendResult", nullptr};
        if (javaVm_->AttachCurrentThread(&env, &args) != JNI_OK) return;
        needsDetach = true;
    } else if (getEnvStat != JNI_OK) {
        return;
    }

    jstring jError = env->NewStringUTF(errorMessage.c_str());
    jstring jRecipient = env->NewStringUTF(recipientId.c_str());
    jstring jMessageId = env->NewStringUTF(messageId.c_str());
    env->CallStaticVoidMethod(
        DiscordSocialSdkBridgeClass_,
        onNativeMessageSendResultMethod_,
        ok ? JNI_TRUE : JNI_FALSE,
        jError,
        jRecipient,
        jMessageId
    );
    ClearPendingJniException(env);
    if (jError) env->DeleteLocalRef(jError);
    if (jRecipient) env->DeleteLocalRef(jRecipient);
    if (jMessageId) env->DeleteLocalRef(jMessageId);

    if (needsDetach) {
        javaVm_->DetachCurrentThread();
    }
}

void DiscordBridge::FireNativeCurrentUser(const std::string& userId) {
    if (!javaVm_ || !DiscordSocialSdkBridgeClass_ || !onNativeCurrentUserMethod_) return;
    JNIEnv* env = nullptr;
    int getEnvStat = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    bool needsDetach = false;
    if (getEnvStat == JNI_EDETACHED) {
        JavaVMAttachArgs args = {JNI_VERSION_1_6, "DiscordCurrentUser", nullptr};
        if (javaVm_->AttachCurrentThread(&env, &args) != JNI_OK) return;
        needsDetach = true;
    } else if (getEnvStat != JNI_OK) {
        return;
    }

    jstring jUserId = env->NewStringUTF(userId.c_str());
    env->CallStaticVoidMethod(
        DiscordSocialSdkBridgeClass_,
        onNativeCurrentUserMethod_,
        jUserId
    );
    ClearPendingJniException(env);
    if (jUserId) env->DeleteLocalRef(jUserId);

    if (needsDetach) {
        javaVm_->DetachCurrentThread();
    }
}

void DiscordBridge::Connect() {
    LOGI("Connect called (ready_=%s, authorized_=%s)",
         ready_ ? "true" : "false", authorized_ ? "true" : "false");
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) { LOGE("Connect: no client"); return; }
    if (ready_) {
        LOGW("Connect: already ready, skipping");
        return;
    }
    try {
        LOGI("Connect: calling client_->Connect()...");
        client_->Connect();
        LOGI("Connect: client_->Connect() returned (async)");
    } catch (const std::exception& e) {
        LOGE("Connect threw exception: %s", e.what());
    } catch (...) {
        LOGE("Connect threw unknown exception");
    }
}

void DiscordBridge::RunCallbacks() {
    try {
        discordpp::RunCallbacks();
    } catch (const std::exception& e) {
        LOGE("RunCallbacks threw exception: %s", e.what());
    } catch (...) {
        LOGE("RunCallbacks threw unknown exception");
    }
}

void DiscordBridge::SetJavaVM(JavaVM* vm) {
    javaVm_ = vm;
}

void DiscordBridge::DestroyUnlocked() {
    ready_ = false;
    authorized_ = false;
    currentUserId_ = 0;
    if (client_) {
        try {
            LOGI("DestroyUnlocked: disconnecting client...");
            client_->Disconnect();
            LOGI("DestroyUnlocked: disconnected");
        } catch (const std::exception& e) {
            LOGW("DestroyUnlocked: Disconnect threw: %s (ignored)", e.what());
        } catch (...) {
            LOGW("DestroyUnlocked: Disconnect threw unknown (ignored)");
        }
        LOGI("DestroyUnlocked: deleting client...");
        delete client_;
        client_ = nullptr;
        LOGI("DestroyUnlocked: client deleted successfully");
    } else {
        LOGW("DestroyUnlocked: no client to destroy");
    }
    // Keep javaVm_ so late async callbacks can still attach safely after teardown.
}

void DiscordBridge::Destroy() {
    LOGI("Destroy called (ready_=%s, authorized_=%s, client_=%s)",
         ready_ ? "true" : "false",
         authorized_ ? "true" : "false",
         client_ ? "exists" : "null");
    std::lock_guard<std::mutex> lock(mutex_);
    DestroyUnlocked();
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeInit(
    JNIEnv* env, jobject thiz, jlong appId
) {
    JavaVM* vm;
    env->GetJavaVM(&vm);
    g_discordBridge.SetJavaVM(vm);

    jclass localClass = env->FindClass("com/arcadia/shell/launcher/discord/DiscordSocialSdkBridge");
    if (localClass) {
        jclass globalClass = static_cast<jclass>(env->NewGlobalRef(localClass));
        DiscordBridge::SetDiscordSocialSdkBridgeClass(env, globalClass);
        jmethodID statusMethod = env->GetStaticMethodID(localClass, "onNativeStatusChanged", "(IZZ)V");
        if (statusMethod) {
            DiscordBridge::SetOnNativeStatusChangedMethod(statusMethod);
        }
        jmethodID tokensMethod = env->GetStaticMethodID(
            localClass, "onNativeTokensReceived", "(Ljava/lang/String;Ljava/lang/String;I)V");
        if (tokensMethod) {
            DiscordBridge::SetOnNativeTokensReceivedMethod(tokensMethod);
        }
        jmethodID friendsMethod = env->GetStaticMethodID(
            localClass, "onNativeFriendsUpdated", "(Ljava/lang/String;)V");
        if (friendsMethod) {
            DiscordBridge::SetOnNativeFriendsUpdatedMethod(friendsMethod);
        }
        jmethodID presenceMethod = env->GetStaticMethodID(
            localClass, "onNativePresenceResult", "(ZLjava/lang/String;)V");
        if (presenceMethod) {
            DiscordBridge::SetOnNativePresenceResultMethod(presenceMethod);
        }
        jmethodID authErrorMethod = env->GetStaticMethodID(
            localClass, "onNativeAuthError", "(Ljava/lang/String;)V");
        if (authErrorMethod) {
            DiscordBridge::SetOnNativeAuthErrorMethod(authErrorMethod);
        }
        jmethodID messagesMethod = env->GetStaticMethodID(
            localClass, "onNativeMessagesUpdated", "(Ljava/lang/String;Ljava/lang/String;)V");
        if (messagesMethod) {
            DiscordBridge::SetOnNativeMessagesUpdatedMethod(messagesMethod);
        }
        jmethodID sendResultMethod = env->GetStaticMethodID(
            localClass,
            "onNativeMessageSendResult",
            "(ZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
        if (sendResultMethod) {
            DiscordBridge::SetOnNativeMessageSendResultMethod(sendResultMethod);
        }
        jmethodID currentUserMethod = env->GetStaticMethodID(
            localClass, "onNativeCurrentUser", "(Ljava/lang/String;)V");
        if (currentUserMethod) {
            DiscordBridge::SetOnNativeCurrentUserMethod(currentUserMethod);
        }
        env->DeleteLocalRef(localClass);
    }

    return g_discordBridge.Init(static_cast<int64_t>(appId)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeIsAuthorized(
    JNIEnv* env, jobject thiz
) {
    return g_discordBridge.IsAuthorized() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeIsReady(
    JNIEnv* env, jobject thiz
) {
    return g_discordBridge.IsReady() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeDisconnect(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.Shutdown();
}

JNIEXPORT void JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeSetTokenAndConnect(
    JNIEnv* env, jobject thiz, jstring token
) {
    const char* tokenStr = token ? env->GetStringUTFChars(token, nullptr) : nullptr;
    if (tokenStr) {
        g_discordBridge.SetTokenAndConnect(tokenStr);
        env->ReleaseStringUTFChars(token, tokenStr);
    }
}

JNIEXPORT void JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeConnect(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.Connect();
}

JNIEXPORT void JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeSetActivity(
    JNIEnv* env, jobject thiz,
    jint activityType,
    jstring name, jstring state, jstring details,
    jlong startSecs, jlong endSecs,
    jstring largeImage, jstring largeText,
    jstring smallImage, jstring smallText,
    jstring button1Label, jstring button1Url,
    jstring button2Label, jstring button2Url
) {
    const char* cName = name ? env->GetStringUTFChars(name, nullptr) : nullptr;
    const char* cState = state ? env->GetStringUTFChars(state, nullptr) : nullptr;
    const char* cDetails = details ? env->GetStringUTFChars(details, nullptr) : nullptr;
    const char* cLargeImage = largeImage ? env->GetStringUTFChars(largeImage, nullptr) : nullptr;
    const char* cLargeText = largeText ? env->GetStringUTFChars(largeText, nullptr) : nullptr;
    const char* cSmallImage = smallImage ? env->GetStringUTFChars(smallImage, nullptr) : nullptr;
    const char* cSmallText = smallText ? env->GetStringUTFChars(smallText, nullptr) : nullptr;
    const char* cBtn1Label = button1Label ? env->GetStringUTFChars(button1Label, nullptr) : nullptr;
    const char* cBtn1Url = button1Url ? env->GetStringUTFChars(button1Url, nullptr) : nullptr;
    const char* cBtn2Label = button2Label ? env->GetStringUTFChars(button2Label, nullptr) : nullptr;
    const char* cBtn2Url = button2Url ? env->GetStringUTFChars(button2Url, nullptr) : nullptr;

    g_discordBridge.SetActivity(
        static_cast<int>(activityType),
        cName, cState, cDetails,
        static_cast<int64_t>(startSecs), static_cast<int64_t>(endSecs),
        cLargeImage, cLargeText, cSmallImage, cSmallText,
        cBtn1Label, cBtn1Url, cBtn2Label, cBtn2Url
    );

    if (cName) env->ReleaseStringUTFChars(name, cName);
    if (cState) env->ReleaseStringUTFChars(state, cState);
    if (cDetails) env->ReleaseStringUTFChars(details, cDetails);
    if (cLargeImage) env->ReleaseStringUTFChars(largeImage, cLargeImage);
    if (cLargeText) env->ReleaseStringUTFChars(largeText, cLargeText);
    if (cSmallImage) env->ReleaseStringUTFChars(smallImage, cSmallImage);
    if (cSmallText) env->ReleaseStringUTFChars(smallText, cSmallText);
    if (cBtn1Label) env->ReleaseStringUTFChars(button1Label, cBtn1Label);
    if (cBtn1Url) env->ReleaseStringUTFChars(button1Url, cBtn1Url);
    if (cBtn2Label) env->ReleaseStringUTFChars(button2Label, cBtn2Label);
    if (cBtn2Url) env->ReleaseStringUTFChars(button2Url, cBtn2Url);
}

JNIEXPORT void JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeSetOnlineStatus(
    JNIEnv* env, jobject thiz, jint statusType
) {
    g_discordBridge.SetOnlineStatus(static_cast<int>(statusType));
}

JNIEXPORT void JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeClear(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.Clear();
}

JNIEXPORT void JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeRunCallbacks(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.RunCallbacks();
}

JNIEXPORT void JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeDestroy(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.Destroy();
}

JNIEXPORT void JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeAuthorize(
    JNIEnv* env, jobject thiz
) {
    // Authorize() already catches exceptions and fires auth-error callbacks.
    g_discordBridge.Authorize();
}

JNIEXPORT void JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeRefreshToken(
    JNIEnv* env, jobject thiz, jstring refreshToken
) {
    const char* tokenStr = refreshToken ? env->GetStringUTFChars(refreshToken, nullptr) : nullptr;
    if (tokenStr) {
        g_discordBridge.RefreshToken(tokenStr);
        env->ReleaseStringUTFChars(refreshToken, tokenStr);
    }
}

JNIEXPORT void JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeRefreshFriends(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.RefreshFriends();
}

JNIEXPORT void JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeSendUserMessage(
    JNIEnv* env, jobject thiz, jlong recipientId, jstring content
) {
    const char* contentStr = content ? env->GetStringUTFChars(content, nullptr) : nullptr;
    g_discordBridge.SendUserMessage(static_cast<uint64_t>(recipientId), contentStr);
    if (contentStr) env->ReleaseStringUTFChars(content, contentStr);
}

JNIEXPORT void JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeLoadUserMessages(
    JNIEnv* env, jobject thiz, jlong recipientId, jint limit
) {
    g_discordBridge.LoadUserMessages(
        static_cast<uint64_t>(recipientId),
        static_cast<int32_t>(limit)
    );
}

JNIEXPORT void JNICALL
Java_com_arcadia_shell_launcher_discord_DiscordSocialSdkBridge_nativeSetShowingChat(
    JNIEnv* env, jobject thiz, jboolean showing
) {
    g_discordBridge.SetShowingChat(showing == JNI_TRUE);
}

} // extern "C"
