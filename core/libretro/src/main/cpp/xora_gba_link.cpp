/**
 * Dual (or more) libmgba cores sharing GBASIOLockstepCoordinator.
 *
 * Stock libretro mGBA has no lockstep and does not export SIO. Poking
 * SIOMULTI over Nakama cannot look like a GBA cable. Each device instead
 * runs every player's GBA locally; the cable stays in-process. Netplay
 * only copies joypad bits so both phones stay on the same buttons.
 *
 * Sleep/wake must park a real mCoreThread, the same way desktop mGBA's
 * "New multiplayer window" does. A cooperative runLoop on one thread
 * desyncs player->asleep and SIGSEGVs the moment the second player sits.
 */

#include "xora_gba_link.h"

#ifndef USE_PTHREADS
#error "xora_gba_link.cpp must be compiled with -DUSE_PTHREADS so GBASIOLockstepCoordinator matches libmgba"
#endif

#include <mgba/core/config.h>
#include <mgba/core/core.h>
#include <mgba/core/lockstep.h>
#include <mgba/core/log.h>
#include <mgba/core/sync.h>
#include <mgba/core/thread.h>
#include <mgba-util/threading.h>
#include <mgba/gba/interface.h>
#include <mgba/internal/gba/input.h>
#include <mgba/internal/gba/sio/lockstep.h>
#include <mgba-util/image.h>

#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "XoraGbaLink"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int kMaxPlayers = 4;
constexpr int kGbaWidth = GBA_VIDEO_HORIZONTAL_PIXELS;
constexpr int kGbaHeight = GBA_VIDEO_VERTICAL_PIXELS;

struct LockstepUser {
    mLockstepThreadUser d{};
    int slot = 0;
};

struct Player {
    mCore* core = nullptr;
    mCoreThread thread{};
    GBASIOLockstepDriver driver{};
    LockstepUser user{};
    std::vector<mColor> video;
    mAVStream stream{};
    bool audioTap = false;
    bool threadLive = false;
};

struct Session {
    GBASIOLockstepCoordinator coordinator{};
    Player players[kMaxPlayers];
    int nPlayers = 0;
    int localSlot = 0;
    bool coordinatorLive = false;
};

std::mutex g_lock;
Session g_session;
std::atomic<bool> g_active{false};
mLogger g_logger{};

int requested_id_cb(mLockstepUser* user) {
    return reinterpret_cast<LockstepUser*>(user)->slot;
}

void mgba_log(mLogger*, int category, enum mLogLevel level, const char* format, va_list args) {
    android_LogPriority prio = ANDROID_LOG_INFO;
    if (level & mLOG_FATAL) prio = ANDROID_LOG_ERROR;
    else if (level & mLOG_ERROR) prio = ANDROID_LOG_ERROR;
    else if (level & mLOG_WARN) prio = ANDROID_LOG_WARN;
    else if (level & mLOG_DEBUG) prio = ANDROID_LOG_DEBUG;
    char buf[512];
    vsnprintf(buf, sizeof(buf), format, args);
    const char* name = mLogCategoryName(category);
    __android_log_print(prio, "mGBA", "%s: %s", name ? name : "mgba", buf);
}

Player* player_from_stream(mAVStream* stream) {
    if (!stream) return nullptr;
    for (int i = 0; i < g_session.nPlayers; ++i) {
        if (&g_session.players[i].stream == stream) return &g_session.players[i];
    }
    return nullptr;
}

void post_audio_frame(mAVStream* stream, int16_t left, int16_t right) {
    Player* player = player_from_stream(stream);
    if (!player || !player->audioTap) return;
    const int16_t pair[2] = {left, right};
    xora_host_push_stereo_s16(pair, 2);
}

uint32_t retro_to_gba_keys(uint16_t buttons) {
    uint32_t keys = 0;
    if (buttons & (1u << 8)) keys |= 1u << GBA_KEY_A;
    if (buttons & (1u << 0)) keys |= 1u << GBA_KEY_B;
    if (buttons & (1u << 2)) keys |= 1u << GBA_KEY_SELECT;
    if (buttons & (1u << 3)) keys |= 1u << GBA_KEY_START;
    if (buttons & (1u << 7)) keys |= 1u << GBA_KEY_RIGHT;
    if (buttons & (1u << 6)) keys |= 1u << GBA_KEY_LEFT;
    if (buttons & (1u << 4)) keys |= 1u << GBA_KEY_UP;
    if (buttons & (1u << 5)) keys |= 1u << GBA_KEY_DOWN;
    if (buttons & (1u << 11)) keys |= 1u << GBA_KEY_R;
    if (buttons & (1u << 10)) keys |= 1u << GBA_KEY_L;
    return keys;
}

void publish_local_frame(Session& session) {
    Player& local = session.players[session.localSlot];
    if (local.video.size() < static_cast<size_t>(kGbaWidth * kGbaHeight)) return;
    std::vector<uint32_t> argb(static_cast<size_t>(kGbaWidth * kGbaHeight));
    for (int i = 0; i < kGbaWidth * kGbaHeight; ++i) {
        const mColor p = local.video[static_cast<size_t>(i)];
        const uint32_t r = p & 0xFFu;
        const uint32_t g = (p >> 8) & 0xFFu;
        const uint32_t b = (p >> 16) & 0xFFu;
        argb[static_cast<size_t>(i)] = 0xFF000000u | (r << 16) | (g << 8) | b;
    }
    xora_host_publish_frame_argb(kGbaWidth, kGbaHeight, argb.data());
}

void interrupt_all(Session& session) {
    for (int i = 0; i < session.nPlayers; ++i) {
        if (session.players[i].threadLive) mCoreThreadInterrupt(&session.players[i].thread);
    }
}

void continue_all(Session& session) {
    for (int i = 0; i < session.nPlayers; ++i) {
        if (session.players[i].threadLive) mCoreThreadContinue(&session.players[i].thread);
    }
}

void stop_unlocked() {
    g_active.store(false, std::memory_order_release);

    for (int i = 0; i < g_session.nPlayers; ++i) {
        Player& player = g_session.players[i];
        if (player.threadLive && player.thread.impl) {
            mCoreThreadEnd(&player.thread);
        }
    }
    for (int i = 0; i < g_session.nPlayers; ++i) {
        Player& player = g_session.players[i];
        if (player.threadLive && player.thread.impl) {
            mCoreThreadJoin(&player.thread);
        }
        player.threadLive = false;
        player.thread = {};
        if (player.core) {
            player.core->setAVStream(player.core, nullptr);
            player.core->setPeripheral(player.core, mPERIPH_GBA_LINK_PORT, nullptr);
            player.core->deinit(player.core);
            player.core = nullptr;
        }
        player.video.clear();
        player.audioTap = false;
        player.user = {};
        player.driver = {};
        player.stream = {};
    }
    if (g_session.coordinatorLive) {
        GBASIOLockstepCoordinatorDeinit(&g_session.coordinator);
        g_session.coordinatorLive = false;
    }
    g_session = {};
}

bool start_unlocked(const char* rom_path, int players, int local_slot, std::string& error) {
    stop_unlocked();
    if (!rom_path || !rom_path[0]) {
        error = "GBA Game Link needs a ROM path";
        return false;
    }
    const int n = std::clamp(players, 2, kMaxPlayers);
    const int local = std::clamp(local_slot, 0, n - 1);

    if (!g_logger.log) {
        g_logger.log = mgba_log;
        g_logger.filter = nullptr;
        mLogSetDefaultLogger(&g_logger);
    }

    GBASIOLockstepCoordinatorInit(&g_session.coordinator);
    g_session.coordinatorLive = true;
    g_session.nPlayers = n;
    g_session.localSlot = local;

    for (int i = 0; i < n; ++i) {
        Player& player = g_session.players[i];
        player.core = mCoreCreate(mPLATFORM_GBA);
        if (!player.core) {
            error = "mCoreCreate(GBA) failed";
            stop_unlocked();
            return false;
        }
        if (!player.core->init(player.core)) {
            error = "mGBA init failed";
            stop_unlocked();
            return false;
        }
        mCoreInitConfig(player.core, "xora");
        mCoreConfigSetIntValue(&player.core->config, "hwaccelVideo", 0);
        mCoreConfigSetIntValue(&player.core->config, "threadedVideo", 0);
        // Kirby / Pokemon poll SIO inside an idle loop. "remove" skips that poll
        // and the game sits on "Please connect the Game Link cable" forever.
        mCoreConfigSetValue(&player.core->config, "idleOptimization", "ignore");
        player.core->loadConfig(player.core, &player.core->config);
        player.core->opts.skipBios = true;
        player.core->opts.audioSync = false;
        player.core->opts.videoSync = false;
        player.core->opts.volume = 0x100;
        player.core->opts.rewindEnable = false;
        if (!mCoreLoadFile(player.core, rom_path)) {
            error = std::string("mGBA could not load ROM: ") + rom_path;
            stop_unlocked();
            return false;
        }
        player.video.assign(static_cast<size_t>(kGbaWidth * kGbaHeight), 0);
        player.core->setVideoBuffer(player.core, player.video.data(), kGbaWidth);
        player.core->setAudioBufferSize(player.core, 2048);

        player.audioTap = (i == local);
        player.stream = {};
        player.stream.postAudioFrame = post_audio_frame;
        player.core->setAVStream(player.core, &player.stream);

        player.thread = {};
        player.thread.core = player.core;
        player.thread.logger.logger = &g_logger;
        if (!mCoreThreadStart(&player.thread)) {
            error = "mGBA thread failed to start";
            stop_unlocked();
            return false;
        }
        player.threadLive = true;
        if (mCoreThreadHasCrashed(&player.thread)) {
            error = "mGBA thread crashed while loading";
            stop_unlocked();
            return false;
        }
    }

    // Same sequence as Qt MultiplayerController::attachGame: pause every
    // core, plug the lockstep cable, then reboot so both carts see it.
    interrupt_all(g_session);
    for (int i = 0; i < n; ++i) {
        Player& player = g_session.players[i];
        mLockstepThreadUserInit(&player.user.d, &player.thread);
        player.user.slot = i;
        player.user.d.d.requestedId = requested_id_cb;
        GBASIOLockstepDriverCreate(&player.driver, &player.user.d.d);
        GBASIOLockstepCoordinatorAttach(&g_session.coordinator, &player.driver);
        player.core->setPeripheral(player.core, mPERIPH_GBA_LINK_PORT, &player.driver.d);
    }
    continue_all(g_session);
    for (int i = 0; i < n; ++i) {
        Player& player = g_session.players[i];
        mCoreThreadReset(&player.thread);
        if (player.thread.impl) {
            // Pace only the on-screen GBA off vsync. The hidden partner is
            // gated by SIO lockstep; waiting 50ms per frame for its video
            // desyncs the cable and Kirby never sees a child GBA.
            mCoreSyncSetVideoSync(&player.thread.impl->sync, i == local);
            MutexLock(&player.thread.impl->sync.audioBufferMutex);
            player.thread.impl->sync.audioWait = false;
            MutexUnlock(&player.thread.impl->sync.audioBufferMutex);
        }
    }

    g_active.store(true, std::memory_order_release);
    const unsigned rate = g_session.players[local].core->audioSampleRate(
        g_session.players[local].core);
    xora_host_set_timing(59.7275, rate > 1 ? static_cast<double>(rate) : 32768.0);
    ALOGI("GBA lockstep: %d mCoreThreads, local P%d, cable is in-process mGBA", n, local + 1);
    return true;
}

void apply_pads(Session& session) {
    for (int i = 0; i < session.nPlayers; ++i) {
        Player& player = session.players[i];
        if (!player.core || !player.threadLive) continue;
        player.core->setKeys(player.core, retro_to_gba_keys(xora_host_pad_buttons(i)));
    }
}

void run_frame_unlocked() {
    Session& session = g_session;
    if (session.nPlayers < 2) return;

    for (int i = 0; i < session.nPlayers; ++i) {
        Player& player = session.players[i];
        if (player.threadLive && mCoreThreadHasCrashed(&player.thread)) {
            ALOGE("GBA lockstep: core %d crashed; leaving the libretro GBA running", i);
            stop_unlocked();
            return;
        }
    }

    interrupt_all(session);
    apply_pads(session);
    continue_all(session);

    Player& local = session.players[session.localSlot];
    if (local.threadLive && local.thread.impl) {
        mCoreSyncWaitFrameStart(&local.thread.impl->sync);
        publish_local_frame(session);
        mCoreSyncWaitFrameEnd(&local.thread.impl->sync);
    } else {
        publish_local_frame(session);
    }
}

}  // namespace

bool xora_gba_link_start(const char* rom_path, int players, int local_slot, std::string& error) {
    std::lock_guard<std::mutex> lock(g_lock);
    return start_unlocked(rom_path, players, local_slot, error);
}

void xora_gba_link_stop() {
    std::lock_guard<std::mutex> lock(g_lock);
    stop_unlocked();
}

bool xora_gba_link_active() {
    return g_active.load(std::memory_order_acquire);
}

void xora_gba_link_run_frame() {
    std::lock_guard<std::mutex> lock(g_lock);
    if (!g_active.load(std::memory_order_relaxed)) return;
    run_frame_unlocked();
}

void xora_gba_link_reset() {
    std::lock_guard<std::mutex> lock(g_lock);
    if (!g_active.load(std::memory_order_relaxed)) return;
    for (int i = 0; i < g_session.nPlayers; ++i) {
        Player& player = g_session.players[i];
        if (player.threadLive) mCoreThreadReset(&player.thread);
    }
}

double xora_gba_link_fps() {
    return 59.7275;
}

double xora_gba_link_sample_rate() {
    std::lock_guard<std::mutex> lock(g_lock);
    if (!g_active.load(std::memory_order_relaxed) || !g_session.players[g_session.localSlot].core) {
        return 32768.0;
    }
    const unsigned rate = g_session.players[g_session.localSlot].core->audioSampleRate(
        g_session.players[g_session.localSlot].core);
    return rate > 1 ? static_cast<double>(rate) : 32768.0;
}
