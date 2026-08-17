/**
 * Minimal Libretro host for XOrA: dlopen a core, software video → RGBA buffer,
 * audio batch → short PCM, digital + analog pad via JNI-polled state.
 * Also captures memory maps for RetroAchievements (rcheevos).
 */
#include "libretro.h"
#include "xora_gba_link.h"
#include "xora_hw_gl.h"
#include "xora_ra_memory.h"

#include "rc_libretro.h"

#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <zlib.h>

#include <atomic>
#include <cstdio>
#include <cstdarg>
#include <cstdint>
#include <cstring>
#include <deque>
#include <map>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "XoraLibretro"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

using SetEnvironment = void (*)(retro_environment_t);
using SetVideoRefresh = void (*)(retro_video_refresh_t);
using SetAudioSample = void (*)(retro_audio_sample_t);
using SetAudioSampleBatch = void (*)(retro_audio_sample_batch_t);
using SetInputPoll = void (*)(retro_input_poll_t);
using SetInputState = void (*)(retro_input_state_t);
using InitFn = void (*)();
using DeinitFn = void (*)();
using ApiVersionFn = unsigned (*)();
using GetSystemInfoFn = void (*)(retro_system_info*);
using GetSystemAvInfoFn = void (*)(retro_system_av_info*);
using SetControllerPortDeviceFn = void (*)(unsigned, unsigned);
using ResetFn = void (*)();
using RunFn = void (*)();
using SerializeSizeFn = size_t (*)();
using SerializeFn = bool (*)(void*, size_t);
using UnserializeFn = bool (*)(const void*, size_t);
using LoadGameFn = bool (*)(const retro_game_info*);
using UnloadGameFn = void (*)();
using GetMemoryDataFn = void* (*)(unsigned);
using GetMemorySizeFn = size_t (*)(unsigned);

struct CoreApi {
    void* handle = nullptr;
    SetEnvironment set_environment = nullptr;
    SetVideoRefresh set_video_refresh = nullptr;
    SetAudioSample set_audio_sample = nullptr;
    SetAudioSampleBatch set_audio_sample_batch = nullptr;
    SetInputPoll set_input_poll = nullptr;
    SetInputState set_input_state = nullptr;
    InitFn init = nullptr;
    DeinitFn deinit = nullptr;
    ApiVersionFn api_version = nullptr;
    GetSystemInfoFn get_system_info = nullptr;
    GetSystemAvInfoFn get_system_av_info = nullptr;
    SetControllerPortDeviceFn set_controller_port_device = nullptr;
    ResetFn reset = nullptr;
    RunFn run = nullptr;
    SerializeSizeFn serialize_size = nullptr;
    SerializeFn serialize = nullptr;
    UnserializeFn unserialize = nullptr;
    LoadGameFn load_game = nullptr;
    UnloadGameFn unload_game = nullptr;
    GetMemoryDataFn get_memory_data = nullptr;
    GetMemorySizeFn get_memory_size = nullptr;
};

namespace {

void xora_gba_sio_on_poll();
void xora_gba_sio_reset();
void xora_gba_sio_drop_hook();

CoreApi g_api;
std::mutex g_mutex;
std::string g_system_dir;
std::string g_save_dir;
std::string g_last_error;
/** Kept for the loaded game lifetime — cores may retain path / buffer pointers. */
std::string g_rom_path;
std::vector<uint8_t> g_rom_buffer;
/** Content-info overrides from RETRO_ENVIRONMENT_SET_CONTENT_INFO_OVERRIDE. */
struct ContentOverride {
    std::string extensions;  // pipe-delimited, lower-case
    bool need_fullpath = false;
    bool persistent_data = false;
};
std::vector<ContentOverride> g_content_overrides;
bool g_supports_content_overrides = true;
/** Extended game info for GET_GAME_INFO_EXT (valid during load_game). */
retro_game_info_ext g_game_info_ext{};
std::string g_ext_dir;
std::string g_ext_name;
std::string g_ext_ext;
std::string g_ext_archive_path;
std::string g_ext_archive_file;
bool g_ext_from_archive = false;
bool g_ext_persistent = true;

enum class PixelFmt { Xrgb8888, Rgb565, Xrgb1555 };
/** Libretro default until the core calls SET_PIXEL_FORMAT. */
PixelFmt g_pixel_fmt = PixelFmt::Xrgb1555;

std::vector<uint32_t> g_frame_rgba;
int g_frame_w = 0;
int g_frame_h = 0;
std::mutex g_frame_mutex;

std::vector<int16_t> g_audio;
std::mutex g_audio_mutex;

// Ports 0–1: buttons bitmask (RETRO_DEVICE_ID_JOYPAD_*), axes LX/LY/RX/RY in [-0x7fff, 0x7fff]
std::atomic<uint16_t> g_pad_buttons[4]{{0}, {0}, {0}, {0}};
std::atomic<int16_t> g_axis_lx[4]{{0}, {0}, {0}, {0}};
std::atomic<int16_t> g_axis_ly[4]{{0}, {0}, {0}, {0}};
std::atomic<int16_t> g_axis_rx[4]{{0}, {0}, {0}, {0}};
std::atomic<int16_t> g_axis_ry[4]{{0}, {0}, {0}, {0}};

// Device IDs from SET_CONTROLLER_INFO (core-specific subclasses, not always JOYPAD).
constexpr unsigned kMaxControllerPorts = 4;
unsigned g_port_device[kMaxControllerPorts] = {
    RETRO_DEVICE_JOYPAD, RETRO_DEVICE_JOYPAD, RETRO_DEVICE_JOYPAD, RETRO_DEVICE_JOYPAD};
unsigned g_controller_ports = 0;
bool g_plugging_controllers = false;

// Core options (SET_VARIABLES / GET_VARIABLE). Overrides win over core defaults.
std::mutex g_vars_mutex;
std::map<std::string, std::string> g_var_overrides;
std::map<std::string, std::string> g_var_defaults;
/** Stable storage for pointers returned to cores via GET_VARIABLE. */
std::map<std::string, std::string> g_var_query_cache;
std::atomic<bool> g_vars_updated{false};
std::string g_netplay_username = "Player";

// Libretro netpacket (env 78) — gpSP Game Link / RFU rides this, not a host-IP core option.
retro_netpacket_callback g_netpacket{};
std::atomic<bool> g_netpacket_set{false};
std::atomic<bool> g_netpacket_started{false};
uint16_t g_netpacket_local_id = 0;
struct NetpacketIo {
    uint16_t client_id = 0;
    int flags = 0;
    std::vector<uint8_t> data;
};
std::mutex g_netpacket_io_mutex;
std::deque<NetpacketIo> g_netpacket_incoming;
std::deque<NetpacketIo> g_netpacket_outgoing;

void netpacket_reset_unlocked() {
    if (g_netpacket_started.load(std::memory_order_relaxed) && g_netpacket.stop) {
        g_netpacket.stop();
    }
    g_netpacket_started.store(false, std::memory_order_relaxed);
    g_netpacket_local_id = 0;
    {
        std::lock_guard<std::mutex> lock(g_netpacket_io_mutex);
        g_netpacket_incoming.clear();
        g_netpacket_outgoing.clear();
    }
}

void netpacket_clear_interface() {
    netpacket_reset_unlocked();
    g_netpacket = retro_netpacket_callback{};
    g_netpacket_set.store(false, std::memory_order_relaxed);
}

void RETRO_CALLCONV netpacket_send(int flags, const void* buf, size_t len, uint16_t client_id) {
    if ((!buf || len == 0) && (flags & RETRO_NETPACKET_FLUSH_HINT)) {
        return;
    }
    if (!buf || len == 0) return;
    if (len > 64 * 1024) len = 64 * 1024;
    NetpacketIo packet;
    packet.client_id = client_id;
    packet.flags = flags;
    packet.data.assign(static_cast<const uint8_t*>(buf), static_cast<const uint8_t*>(buf) + len);
    std::lock_guard<std::mutex> lock(g_netpacket_io_mutex);
    if (g_netpacket_outgoing.size() > 256) g_netpacket_outgoing.pop_front();
    g_netpacket_outgoing.push_back(std::move(packet));
}

void netpacket_deliver_incoming() {
    std::deque<NetpacketIo> batch;
    {
        std::lock_guard<std::mutex> lock(g_netpacket_io_mutex);
        batch.swap(g_netpacket_incoming);
    }
    if (!g_netpacket.receive) return;
    for (const auto& packet : batch) {
        if (packet.data.empty()) continue;
        g_netpacket.receive(packet.data.data(), packet.data.size(), packet.client_id);
    }
}

void RETRO_CALLCONV netpacket_poll_receive() {
    netpacket_deliver_incoming();
}

bool g_game_loaded = false;
double g_fps = 60.0;
double g_sample_rate = 48000.0;

// RetroAchievements memory (copied descriptors + rc_libretro regions).
std::vector<retro_memory_descriptor> g_mmap_descriptors;
std::vector<std::string> g_mmap_addrspaces;
retro_memory_map g_mmap{};
rc_libretro_memory_regions_t g_ra_regions{};
bool g_ra_regions_ready = false;

void clear_memory_maps() {
    g_mmap_descriptors.clear();
    g_mmap_addrspaces.clear();
    g_mmap = retro_memory_map{};
    // Keep Game Link I/O pokes across SET_MEMORY_MAPS; a full SIO reset used to
    // unplug the cable mid-session whenever the core refreshed maps.
    xora_gba_sio_drop_hook();
}

template <typename T>
T load_sym(void* handle, const char* name) {
    return reinterpret_cast<T>(dlsym(handle, name));
}

bool read_rom_file(const char* path, std::vector<uint8_t>& out, std::string& error) {
    FILE* file = std::fopen(path, "rb");
    if (!file) {
        error = std::string("Cannot open ROM: ") + path;
        return false;
    }
    if (std::fseek(file, 0, SEEK_END) != 0) {
        std::fclose(file);
        error = "Cannot seek ROM file";
        return false;
    }
    const long length = std::ftell(file);
    if (length < 0) {
        std::fclose(file);
        error = "Cannot size ROM file";
        return false;
    }
    std::rewind(file);
    out.resize(static_cast<size_t>(length));
    if (length > 0) {
        const size_t read = std::fread(out.data(), 1, out.size(), file);
        std::fclose(file);
        if (read != out.size()) {
            out.clear();
            error = "Cannot read ROM file";
            return false;
        }
    } else {
        std::fclose(file);
    }
    return true;
}

bool rom_file_exists(const char* path) {
    FILE* file = std::fopen(path, "rb");
    if (!file) return false;
    std::fclose(file);
    return true;
}

std::string to_lower_copy(std::string s) {
    for (char& c : s) {
        if (c >= 'A' && c <= 'Z') c = static_cast<char>(c - 'A' + 'a');
    }
    return s;
}

std::string path_extension(const std::string& path) {
    const auto slash = path.find_last_of("/\\");
    const auto base = slash == std::string::npos ? path : path.substr(slash + 1);
    const auto dot = base.find_last_of('.');
    if (dot == std::string::npos || dot == 0) return "";
    return to_lower_copy(base.substr(dot + 1));
}

std::string path_directory(const std::string& path) {
    const auto slash = path.find_last_of("/\\");
    if (slash == std::string::npos) return ".";
    if (slash == 0) return "/";
    return path.substr(0, slash);
}

std::string path_basename_no_ext(const std::string& path) {
    const auto slash = path.find_last_of("/\\");
    const auto base = slash == std::string::npos ? path : path.substr(slash + 1);
    const auto dot = base.find_last_of('.');
    if (dot == std::string::npos || dot == 0) return base;
    return base.substr(0, dot);
}

bool extension_in_list(const std::string& ext, const char* list) {
    if (ext.empty() || !list || !list[0]) return false;
    const std::string hay = to_lower_copy(list);
    size_t start = 0;
    while (start <= hay.size()) {
        size_t end = hay.find('|', start);
        if (end == std::string::npos) end = hay.size();
        if (end > start && hay.compare(start, end - start, ext) == 0) return true;
        if (end == hay.size()) break;
        start = end + 1;
    }
    return false;
}

bool resolve_need_fullpath(const retro_system_info& info, const std::string& ext) {
    for (const auto& o : g_content_overrides) {
        if (extension_in_list(ext, o.extensions.c_str())) return o.need_fullpath;
    }
    return info.need_fullpath;
}

bool resolve_persistent_data(const retro_system_info& info, const std::string& ext) {
    for (const auto& o : g_content_overrides) {
        if (extension_in_list(ext, o.extensions.c_str())) return o.persistent_data;
    }
    (void)info;
    return true;  // keep buffer for session unless override says otherwise
}

bool is_ignored_zip_entry(const std::string& name) {
    if (name.empty() || name.back() == '/') return true;
    const std::string lower = to_lower_copy(name);
    if (lower.rfind("__macosx/", 0) == 0) return true;
    if (lower.find(".ds_store") != std::string::npos) return true;
    return false;
}

uint32_t read_le32(const uint8_t* p) {
    return static_cast<uint32_t>(p[0]) |
        (static_cast<uint32_t>(p[1]) << 8) |
        (static_cast<uint32_t>(p[2]) << 16) |
        (static_cast<uint32_t>(p[3]) << 24);
}

uint16_t read_le16(const uint8_t* p) {
    return static_cast<uint16_t>(
        static_cast<uint16_t>(p[0]) | (static_cast<uint16_t>(p[1]) << 8));
}

/**
 * Extract the best entry from a ZIP into [out_data]. Prefers entries whose extension is listed
 * in valid_extensions. Returns the inner file name (not full path).
 */
bool extract_zip_entry(
    const std::vector<uint8_t>& zip,
    const char* valid_extensions,
    std::vector<uint8_t>& out_data,
    std::string& out_name,
    std::string& error
) {
    out_data.clear();
    out_name.clear();
    std::vector<uint8_t> fallback_data;
    std::string fallback_name;

    size_t offset = 0;
    while (offset + 30 <= zip.size()) {
        if (read_le32(zip.data() + offset) != 0x04034b50u) break;
        const uint16_t flags = read_le16(zip.data() + offset + 6);
        const uint16_t method = read_le16(zip.data() + offset + 8);
        uint32_t comp_size = read_le32(zip.data() + offset + 18);
        uint32_t uncomp_size = read_le32(zip.data() + offset + 22);
        const uint16_t name_len = read_le16(zip.data() + offset + 26);
        const uint16_t extra_len = read_le16(zip.data() + offset + 28);
        if (offset + 30 + name_len + extra_len > zip.size()) break;

        std::string name(
            reinterpret_cast<const char*>(zip.data() + offset + 30),
            reinterpret_cast<const char*>(zip.data() + offset + 30 + name_len)
        );
        size_t data_off = offset + 30 + name_len + extra_len;

        // Data descriptor: sizes may be zero in local header when bit 3 is set.
        if ((flags & 0x8) != 0 && comp_size == 0) {
            // Can't reliably stream without central directory; skip this entry strategy.
            error = "ZIP uses data descriptors; re-zip without streaming";
            return false;
        }
        if (data_off + comp_size > zip.size()) break;

        if (!is_ignored_zip_entry(name) && method <= 8) {
            std::vector<uint8_t> decoded;
            bool ok = false;
            if (method == 0) {
                decoded.assign(zip.begin() + static_cast<std::ptrdiff_t>(data_off),
                               zip.begin() + static_cast<std::ptrdiff_t>(data_off + comp_size));
                ok = true;
            } else if (method == 8) {
                decoded.resize(uncomp_size > 0 ? uncomp_size : comp_size * 4 + 64);
                z_stream strm{};
                strm.next_in = const_cast<Bytef*>(zip.data() + data_off);
                strm.avail_in = comp_size;
                strm.next_out = decoded.data();
                strm.avail_out = static_cast<uInt>(decoded.size());
                // Negative windowBits = raw DEFLATE (ZIP).
                if (inflateInit2(&strm, -MAX_WBITS) == Z_OK) {
                    int rc = inflate(&strm, Z_FINISH);
                    if (rc == Z_STREAM_END || rc == Z_OK) {
                        decoded.resize(strm.total_out);
                        ok = true;
                    }
                    inflateEnd(&strm);
                }
            }

            if (ok && !decoded.empty()) {
                const std::string ext = path_extension(name);
                if (extension_in_list(ext, valid_extensions) ||
                    (valid_extensions == nullptr || valid_extensions[0] == '\0')) {
                    out_data = std::move(decoded);
                    // Basename only.
                    const auto slash = name.find_last_of("/\\");
                    out_name = slash == std::string::npos ? name : name.substr(slash + 1);
                    return true;
                }
                if (fallback_data.empty()) {
                    fallback_data = std::move(decoded);
                    const auto slash = name.find_last_of("/\\");
                    fallback_name = slash == std::string::npos ? name : name.substr(slash + 1);
                }
            }
        }

        offset = data_off + comp_size;
    }

    if (!fallback_data.empty()) {
        out_data = std::move(fallback_data);
        out_name = std::move(fallback_name);
        return true;
    }
    error = "No usable file inside ZIP";
    return false;
}

void clear_game_info_ext() {
    g_game_info_ext = retro_game_info_ext{};
    g_ext_dir.clear();
    g_ext_name.clear();
    g_ext_ext.clear();
    g_ext_archive_path.clear();
    g_ext_archive_file.clear();
    g_ext_from_archive = false;
    g_ext_persistent = true;
}

void fill_game_info_ext(bool has_data) {
    g_ext_dir = path_directory(g_rom_path);
    g_ext_name = path_basename_no_ext(g_rom_path);
    g_ext_ext = path_extension(g_rom_path);
    g_game_info_ext.full_path = g_ext_from_archive ? nullptr : g_rom_path.c_str();
    g_game_info_ext.archive_path = g_ext_from_archive ? g_ext_archive_path.c_str() : nullptr;
    g_game_info_ext.archive_file = g_ext_from_archive ? g_ext_archive_file.c_str() : nullptr;
    g_game_info_ext.dir = g_ext_dir.c_str();
    g_game_info_ext.name = g_ext_name.c_str();
    g_game_info_ext.ext = g_ext_ext.c_str();
    g_game_info_ext.meta = nullptr;
    g_game_info_ext.data = has_data ? g_rom_buffer.data() : nullptr;
    g_game_info_ext.size = has_data ? g_rom_buffer.size() : 0;
    g_game_info_ext.file_in_archive = g_ext_from_archive;
    g_game_info_ext.persistent_data = g_ext_persistent;
}

void video_refresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (width == 0 || height == 0) return;

    // HW cores pass RETRO_HW_FRAME_BUFFER_VALID after rendering into our FBO.
    if (data == RETRO_HW_FRAME_BUFFER_VALID) {
        std::vector<uint32_t> rgba;
        if (xora_hw::read_frame(width, height, rgba)) {
            std::lock_guard<std::mutex> lock(g_frame_mutex);
            g_frame_w = static_cast<int>(width);
            g_frame_h = static_cast<int>(height);
            g_frame_rgba = std::move(rgba);
        }
        return;
    }

    if (!data) return;

    std::lock_guard<std::mutex> lock(g_frame_mutex);
    g_frame_w = static_cast<int>(width);
    g_frame_h = static_cast<int>(height);
    g_frame_rgba.resize(static_cast<size_t>(width) * height);

    if (g_pixel_fmt == PixelFmt::Rgb565) {
        const auto* src = static_cast<const uint16_t*>(data);
        const size_t src_stride = pitch / 2;
        for (unsigned y = 0; y < height; ++y) {
            const uint16_t* row = src + y * src_stride;
            uint32_t* dst = g_frame_rgba.data() + y * width;
            for (unsigned x = 0; x < width; ++x) {
                const uint16_t p = row[x];
                const uint32_t r = (p >> 11) & 0x1F;
                const uint32_t g = (p >> 5) & 0x3F;
                const uint32_t b = p & 0x1F;
                dst[x] = 0xFF000000u |
                    ((r * 255 / 31) << 16) |
                    ((g * 255 / 63) << 8) |
                    (b * 255 / 31);
            }
        }
    } else if (g_pixel_fmt == PixelFmt::Xrgb1555) {
        // Historical libretro default: 0RGB1555 (16-bit).
        const auto* src = static_cast<const uint16_t*>(data);
        const size_t src_stride = pitch / 2;
        for (unsigned y = 0; y < height; ++y) {
            const uint16_t* row = src + y * src_stride;
            uint32_t* dst = g_frame_rgba.data() + y * width;
            for (unsigned x = 0; x < width; ++x) {
                const uint16_t p = row[x];
                const uint32_t r = (p >> 10) & 0x1F;
                const uint32_t g = (p >> 5) & 0x1F;
                const uint32_t b = p & 0x1F;
                dst[x] = 0xFF000000u |
                    ((r * 255 / 31) << 16) |
                    ((g * 255 / 31) << 8) |
                    (b * 255 / 31);
            }
        }
    } else {
        // XRGB8888
        const auto* src = static_cast<const uint8_t*>(data);
        for (unsigned y = 0; y < height; ++y) {
            const auto* row = reinterpret_cast<const uint32_t*>(src + y * pitch);
            uint32_t* dst = g_frame_rgba.data() + y * width;
            for (unsigned x = 0; x < width; ++x) {
                const uint32_t p = row[x];
                dst[x] = 0xFF000000u | (p & 0x00FFFFFFu);
            }
        }
    }
}

void audio_sample(int16_t left, int16_t right) {
    std::lock_guard<std::mutex> lock(g_audio_mutex);
    g_audio.push_back(left);
    g_audio.push_back(right);
}

size_t audio_sample_batch(const int16_t* data, size_t frames) {
    if (!data || frames == 0) return 0;
    std::lock_guard<std::mutex> lock(g_audio_mutex);
    g_audio.insert(g_audio.end(), data, data + frames * 2);
    return frames;
}

void input_poll() {
    xora_gba_sio_on_poll();
}

bool is_multiplayer_adapter(const char* desc);

bool is_pad_device(unsigned id, const char* desc) {
    if (id == RETRO_DEVICE_NONE) return false;
    const unsigned base = id & RETRO_DEVICE_MASK;
    if (base == RETRO_DEVICE_MOUSE || base == RETRO_DEVICE_POINTER ||
        base == RETRO_DEVICE_KEYBOARD || base == RETRO_DEVICE_LIGHTGUN) {
        return false;
    }
    if (!desc || !desc[0]) {
        return base == RETRO_DEVICE_JOYPAD || base == RETRO_DEVICE_ANALOG;
    }
    const std::string d = to_lower_copy(desc);
    if (d.find("none") != std::string::npos) return false;
    if (d.find("zapper") != std::string::npos || d.find("scope") != std::string::npos ||
        d.find("justifier") != std::string::npos || d.find("guncon") != std::string::npos ||
        d.find("lightgun") != std::string::npos || d.find("mouse") != std::string::npos ||
        d.find("paddle") != std::string::npos || d.find("tablet") != std::string::npos ||
        d.find("keyboard") != std::string::npos) {
        return false;
    }
    // A multitap on port 2 replaces the P2 pad. Netplay writes joypad bits to that
    // port, so the adapter must never be the plugged device.
    if (is_multiplayer_adapter(desc)) return false;
    return true;
}

bool is_multiplayer_adapter(const char* desc) {
    if (!desc || !desc[0]) return false;
    const std::string d = to_lower_copy(desc);
    return d.find("multitap") != std::string::npos ||
           d.find("four score") != std::string::npos ||
           d.find("fourscore") != std::string::npos ||
           d.find("4-player") != std::string::npos ||
           d.find("4 player") != std::string::npos ||
           d.find("teamplayer") != std::string::npos ||
           d.find("team player") != std::string::npos ||
           d.find("4-way") != std::string::npos ||
           d.find("4 way") != std::string::npos;
}

int pad_device_score(unsigned port, unsigned id, const char* desc) {
    (void)port;
    const std::string d = desc ? to_lower_copy(desc) : "";
    if (is_multiplayer_adapter(desc)) return -1;
    if (d.find("gamepad") != std::string::npos) return 90;
    if (d.find("gamecube") != std::string::npos) return 88;
    if (d.find("playstation controller") != std::string::npos) return 85;
    if (d.find("standard") != std::string::npos) return 85;
    if (d.find("joypad") != std::string::npos || d.find("retropad") != std::string::npos) return 80;
    if (d.find("dualshock") != std::string::npos) return 70;
    if (d.find("analog controller") != std::string::npos) return 70;
    if (d.find("controller") != std::string::npos) return 55;
    if (d == "auto") return 20;
    const unsigned base = id & RETRO_DEVICE_MASK;
    if (base == RETRO_DEVICE_ANALOG) return 45;
    if (base == RETRO_DEVICE_JOYPAD) return 40;
    return 10;
}

void reset_port_devices() {
    g_controller_ports = 0;
    for (unsigned i = 0; i < kMaxControllerPorts; ++i) {
        g_port_device[i] = RETRO_DEVICE_JOYPAD;
    }
}

void apply_controller_info(const retro_controller_info* ports) {
    if (!ports) return;
    unsigned count = 0;
    for (unsigned port = 0; port < kMaxControllerPorts; ++port) {
        const retro_controller_info& info = ports[port];
        if (!info.types || info.num_types == 0) break;
        unsigned best_id = RETRO_DEVICE_JOYPAD;
        int best_score = -1;
        const char* best_desc = "";
        for (unsigned i = 0; i < info.num_types; ++i) {
            const retro_controller_description& type = info.types[i];
            if (!is_pad_device(type.id, type.desc)) continue;
            const int score = pad_device_score(port, type.id, type.desc);
            if (score > best_score) {
                best_score = score;
                best_id = type.id;
                best_desc = type.desc ? type.desc : "";
            }
        }
        g_port_device[port] = best_id;
        count = port + 1;
        ALOGI("Controller port %u device %u (%s)", port, best_id, best_desc);
    }
    if (count > 0) {
        const unsigned fill = g_port_device[0];
        for (unsigned port = count; port < kMaxControllerPorts; ++port) {
            g_port_device[port] = fill;
        }
        g_controller_ports = kMaxControllerPorts;
    }
}

int16_t analog_x_or_dpad(unsigned port) {
    const int16_t axis = g_axis_lx[port].load(std::memory_order_relaxed);
    if (axis != 0) return axis;
    const uint16_t buttons = g_pad_buttons[port].load(std::memory_order_relaxed);
    if (buttons & (1u << RETRO_DEVICE_ID_JOYPAD_LEFT)) return -0x7fff;
    if (buttons & (1u << RETRO_DEVICE_ID_JOYPAD_RIGHT)) return 0x7fff;
    return 0;
}

int16_t analog_y_or_dpad(unsigned port) {
    const int16_t axis = g_axis_ly[port].load(std::memory_order_relaxed);
    if (axis != 0) return axis;
    const uint16_t buttons = g_pad_buttons[port].load(std::memory_order_relaxed);
    if (buttons & (1u << RETRO_DEVICE_ID_JOYPAD_UP)) return -0x7fff;
    if (buttons & (1u << RETRO_DEVICE_ID_JOYPAD_DOWN)) return 0x7fff;
    return 0;
}

void plug_controllers() {
    if (g_plugging_controllers || !g_api.set_controller_port_device) return;
    g_plugging_controllers = true;
    const unsigned d0 = g_port_device[0];
    for (unsigned port = 0; port < kMaxControllerPorts; ++port) {
        const unsigned id = (g_controller_ports > port) ? g_port_device[port] : d0;
        g_api.set_controller_port_device(port, id);
    }
    ALOGI("Plugged P1–P4 devices %u %u %u %u (ports=%u)",
          d0,
          g_port_device[1],
          g_port_device[2],
          g_port_device[3],
          g_controller_ports);
    g_plugging_controllers = false;
}

int16_t input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= kMaxControllerPorts) return 0;
    // Cores pass SET_CONTROLLER_INFO subclasses (NES Gamepad, DualShock, GC pad).
    // The API requires masking to the generic RetroPad / analog type.
    const unsigned masked = device & RETRO_DEVICE_MASK;
    const uint16_t buttons = g_pad_buttons[port].load(std::memory_order_relaxed);
    if (masked == RETRO_DEVICE_JOYPAD) {
        if (id == RETRO_DEVICE_ID_JOYPAD_MASK) {
            return static_cast<int16_t>(buttons);
        }
        if (id > 15) return 0;
        return (buttons >> id) & 1;
    }
    if (masked == RETRO_DEVICE_ANALOG) {
        if (index == RETRO_DEVICE_INDEX_ANALOG_LEFT) {
            if (id == RETRO_DEVICE_ID_ANALOG_X) return analog_x_or_dpad(port);
            if (id == RETRO_DEVICE_ID_ANALOG_Y) return analog_y_or_dpad(port);
        }
        if (index == RETRO_DEVICE_INDEX_ANALOG_RIGHT) {
            if (id == RETRO_DEVICE_ID_ANALOG_X) return g_axis_rx[port].load(std::memory_order_relaxed);
            if (id == RETRO_DEVICE_ID_ANALOG_Y) return g_axis_ry[port].load(std::memory_order_relaxed);
        }
        if (index == RETRO_DEVICE_INDEX_ANALOG_BUTTON) {
            if (id > 15) return 0;
            return (buttons >> id) & 1 ? 0x7fff : 0;
        }
    }
    return 0;
}

void log_printf(enum retro_log_level level, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    android_LogPriority prio = ANDROID_LOG_INFO;
    if (level == RETRO_LOG_DEBUG) prio = ANDROID_LOG_DEBUG;
    else if (level == RETRO_LOG_WARN) prio = ANDROID_LOG_WARN;
    else if (level == RETRO_LOG_ERROR) prio = ANDROID_LOG_ERROR;
    __android_log_vprint(prio, "LibretroCore", fmt, args);
    va_end(args);
}

bool environment(unsigned cmd, void* data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE: {
            if (data) *static_cast<bool*>(data) = true;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            if (!data) return false;
            const auto fmt = *static_cast<const enum retro_pixel_format*>(data);
            if (fmt == RETRO_PIXEL_FORMAT_XRGB8888) {
                g_pixel_fmt = PixelFmt::Xrgb8888;
                return true;
            }
            if (fmt == RETRO_PIXEL_FORMAT_RGB565) {
                g_pixel_fmt = PixelFmt::Rgb565;
                return true;
            }
            if (fmt == RETRO_PIXEL_FORMAT_0RGB1555) {
                g_pixel_fmt = PixelFmt::Xrgb1555;
                return true;
            }
            return false;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: {
            if (!data) return false;
            *static_cast<const char**>(data) =
                g_system_dir.empty() ? nullptr : g_system_dir.c_str();
            return !g_system_dir.empty();
        }
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
            if (!data) return false;
            *static_cast<const char**>(data) =
                g_save_dir.empty() ? nullptr : g_save_dir.c_str();
            return !g_save_dir.empty();
        }
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME: {
            return true;
        }
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            if (!data) return false;
            auto* cb = static_cast<retro_log_callback*>(data);
            cb->log = log_printf;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            if (!data) return false;
            auto* var = static_cast<retro_variable*>(data);
            if (!var->key) return false;
            std::lock_guard<std::mutex> lock(g_vars_mutex);
            std::string value;
            auto o = g_var_overrides.find(var->key);
            if (o != g_var_overrides.end()) {
                value = o->second;
            } else {
                auto d = g_var_defaults.find(var->key);
                if (d == g_var_defaults.end()) {
                    var->value = nullptr;
                    return true;
                }
                value = d->second;
            }
            g_var_query_cache[var->key] = value;
            var->value = g_var_query_cache[var->key].c_str();
            return true;
        }
        case RETRO_ENVIRONMENT_SET_VARIABLES: {
            if (!data) return false;
            const auto* vars = static_cast<const retro_variable*>(data);
            std::lock_guard<std::mutex> lock(g_vars_mutex);
            for (; vars->key; ++vars) {
                // "desc;val1|val2|…" — default is the first value token.
                std::string def;
                if (vars->value) {
                    const char* semi = std::strchr(vars->value, ';');
                    const char* start = semi ? semi + 1 : vars->value;
                    while (*start == ' ') ++start;
                    const char* bar = std::strchr(start, '|');
                    def = bar ? std::string(start, bar) : std::string(start);
                }
                g_var_defaults[vars->key] = def;
            }
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            if (!data) return false;
            *static_cast<bool*>(data) = g_vars_updated.exchange(false);
            return true;
        }
        case RETRO_ENVIRONMENT_GET_USERNAME: {
            if (!data) return false;
            *static_cast<const char**>(data) =
                g_netplay_username.empty() ? nullptr : g_netplay_username.c_str();
            return !g_netplay_username.empty();
        }
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS: {
            // Advertise real bitmask support — returning true with *data=false still lets
            // some cores treat the callback as supported and then query JOYPAD_MASK (256),
            // which previously always returned 0 and made pads appear dead.
            if (data) *static_cast<bool*>(data) = true;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_INPUT_MAX_USERS: {
            if (!data) return false;
            // NES/SNES/N64/PS/GC expose up to 4 sockets; we drive all of them.
            *static_cast<unsigned*>(data) = 4u;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_INPUT_DEVICE_CAPABILITIES: {
            if (!data) return false;
            *static_cast<uint64_t*>(data) =
                (1ULL << RETRO_DEVICE_JOYPAD) | (1ULL << RETRO_DEVICE_ANALOG);
            return true;
        }
        case RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER: {
            if (!data) return false;
            return xora_hw::preferred_hw_context(static_cast<unsigned*>(data));
        }
        case RETRO_ENVIRONMENT_SET_HW_RENDER: {
            if (!data) return false;
            auto* cb = static_cast<retro_hw_render_callback*>(data);
            if (!xora_hw::accept_hw_render(cb)) {
                ALOGW("SET_HW_RENDER rejected (unsupported or EGL init failed)");
                return false;
            }
            return true;
        }
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO: {
            if (!data) return false;
            apply_controller_info(static_cast<const retro_controller_info*>(data));
            // Dolphin (and others) refresh this after load_game. Re-plug then so P2
            // gets the core's GameCube/NES/SNES/PS pad, not a generic RetroPad.
            if (g_game_loaded) plug_controllers();
            return true;
        }
        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS: {
            if (!data) return false;
            xora_host_set_memory_maps(static_cast<const retro_memory_map*>(data));
            return true;
        }
        case RETRO_ENVIRONMENT_SET_CONTENT_INFO_OVERRIDE: {
            // NULL probes support.
            if (!data) return g_supports_content_overrides;
            g_content_overrides.clear();
            const auto* items = static_cast<const retro_system_content_info_override*>(data);
            for (; items && items->extensions; ++items) {
                ContentOverride o;
                o.extensions = to_lower_copy(items->extensions);
                o.need_fullpath = items->need_fullpath;
                o.persistent_data = items->persistent_data;
                g_content_overrides.push_back(std::move(o));
            }
            ALOGI("Content info overrides: %zu", g_content_overrides.size());
            return true;
        }
        case RETRO_ENVIRONMENT_GET_GAME_INFO_EXT: {
            if (!data) return false;
            // Single-element array for retro_load_game.
            static const retro_game_info_ext* ext_ptr;
            ext_ptr = &g_game_info_ext;
            *static_cast<const retro_game_info_ext**>(data) = ext_ptr;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_NETPACKET_INTERFACE: {
            if (!data) return false;
            const auto* cb = static_cast<const retro_netpacket_callback*>(data);
            if (!cb->start || !cb->receive) return false;
            g_netpacket = *cb;
            g_netpacket_set.store(true, std::memory_order_relaxed);
            ALOGI("SET_NETPACKET_INTERFACE accepted (protocol %s)",
                  cb->protocol_version ? cb->protocol_version : "core");
            return true;
        }
        default:
            return false;
    }
}

void unload_unlocked() {
    netpacket_clear_interface();
    xora_gba_link_stop();
    xora_host_memory_destroy();
    xora_gba_sio_reset();
    clear_memory_maps();
    if (g_api.handle && g_game_loaded && g_api.unload_game) {
        g_api.unload_game();
        g_game_loaded = false;
    }
    if (g_api.handle && g_api.deinit) {
        g_api.deinit();
    }
    xora_hw::destroy();
    if (g_api.handle) {
        dlclose(g_api.handle);
        g_api = CoreApi{};
    }
    g_pixel_fmt = PixelFmt::Xrgb1555;
    reset_port_devices();
    g_rom_path.clear();
    g_rom_buffer.clear();
    g_rom_buffer.shrink_to_fit();
    g_content_overrides.clear();
    clear_game_info_ext();
    {
        std::lock_guard<std::mutex> lock(g_vars_mutex);
        g_var_defaults.clear();
        g_var_query_cache.clear();
        // Keep overrides across core reloads within the same activity; cleared via JNI.
    }
}

bool load_symbols(void* handle) {
    g_api.handle = handle;
    g_api.set_environment = load_sym<SetEnvironment>(handle, "retro_set_environment");
    g_api.set_video_refresh = load_sym<SetVideoRefresh>(handle, "retro_set_video_refresh");
    g_api.set_audio_sample = load_sym<SetAudioSample>(handle, "retro_set_audio_sample");
    g_api.set_audio_sample_batch = load_sym<SetAudioSampleBatch>(handle, "retro_set_audio_sample_batch");
    g_api.set_input_poll = load_sym<SetInputPoll>(handle, "retro_set_input_poll");
    g_api.set_input_state = load_sym<SetInputState>(handle, "retro_set_input_state");
    g_api.init = load_sym<InitFn>(handle, "retro_init");
    g_api.deinit = load_sym<DeinitFn>(handle, "retro_deinit");
    g_api.api_version = load_sym<ApiVersionFn>(handle, "retro_api_version");
    g_api.get_system_info = load_sym<GetSystemInfoFn>(handle, "retro_get_system_info");
    g_api.get_system_av_info = load_sym<GetSystemAvInfoFn>(handle, "retro_get_system_av_info");
    g_api.set_controller_port_device =
        load_sym<SetControllerPortDeviceFn>(handle, "retro_set_controller_port_device");
    g_api.reset = load_sym<ResetFn>(handle, "retro_reset");
    g_api.run = load_sym<RunFn>(handle, "retro_run");
    g_api.serialize_size = load_sym<SerializeSizeFn>(handle, "retro_serialize_size");
    g_api.serialize = load_sym<SerializeFn>(handle, "retro_serialize");
    g_api.unserialize = load_sym<UnserializeFn>(handle, "retro_unserialize");
    g_api.load_game = load_sym<LoadGameFn>(handle, "retro_load_game");
    g_api.unload_game = load_sym<UnloadGameFn>(handle, "retro_unload_game");
    g_api.get_memory_data = load_sym<GetMemoryDataFn>(handle, "retro_get_memory_data");
    g_api.get_memory_size = load_sym<GetMemorySizeFn>(handle, "retro_get_memory_size");

    return g_api.set_environment && g_api.set_video_refresh && g_api.set_audio_sample_batch &&
        g_api.set_input_poll && g_api.set_input_state && g_api.init && g_api.deinit &&
        g_api.load_game && g_api.run;
}

}  // namespace

uint16_t xora_host_pad_buttons(int port) {
    if (port < 0 || port >= static_cast<int>(kMaxControllerPorts)) return 0;
    return g_pad_buttons[port].load(std::memory_order_relaxed);
}

void xora_host_publish_frame_argb(int width, int height, const uint32_t* pixels) {
    if (width <= 0 || height <= 0 || !pixels) return;
    std::lock_guard<std::mutex> lock(g_frame_mutex);
    g_frame_w = width;
    g_frame_h = height;
    g_frame_rgba.assign(
        pixels,
        pixels + static_cast<size_t>(width) * static_cast<size_t>(height)
    );
}

void xora_host_push_stereo_s16(const int16_t* samples, size_t count) {
    if (!samples || count == 0) return;
    std::lock_guard<std::mutex> lock(g_audio_mutex);
    g_audio.insert(g_audio.end(), samples, samples + count);
}

void xora_host_set_timing(double fps, double sample_rate) {
    if (fps > 1.0) g_fps = fps;
    if (sample_rate > 1.0) g_sample_rate = sample_rate;
}

extern "C" void get_core_memory_info(uint32_t id, rc_libretro_core_memory_info_t* info) {
    if (!info) return;
    info->data = nullptr;
    info->size = 0;
    if (!g_api.get_memory_data || !g_api.get_memory_size) return;
    info->data = static_cast<uint8_t*>(g_api.get_memory_data(id));
    info->size = g_api.get_memory_size(id);
}

extern "C" void xora_host_set_memory_maps(const struct retro_memory_map* mmap) {
    clear_memory_maps();
    if (!mmap || !mmap->descriptors || mmap->num_descriptors == 0) return;
    g_mmap_descriptors.reserve(mmap->num_descriptors);
    g_mmap_addrspaces.reserve(mmap->num_descriptors);
    for (unsigned i = 0; i < mmap->num_descriptors; ++i) {
        retro_memory_descriptor desc = mmap->descriptors[i];
        if (desc.addrspace) {
            g_mmap_addrspaces.emplace_back(desc.addrspace);
            desc.addrspace = g_mmap_addrspaces.back().c_str();
        }
        g_mmap_descriptors.push_back(desc);
    }
    g_mmap.descriptors = g_mmap_descriptors.data();
    g_mmap.num_descriptors = static_cast<unsigned>(g_mmap_descriptors.size());
    ALOGI("Memory maps: %u descriptors", g_mmap.num_descriptors);
}

extern "C" int xora_host_memory_init(uint32_t console_id) {
    xora_host_memory_destroy();
    const retro_memory_map* map = g_mmap.num_descriptors > 0 ? &g_mmap : nullptr;
    const int ok = rc_libretro_memory_init(
        &g_ra_regions,
        map,
        get_core_memory_info,
        console_id
    );
    g_ra_regions_ready = ok != 0 && g_ra_regions.total_size > 0;
    ALOGI(
        "RA memory init console=%u ok=%d regions=%u bytes=%zu",
        console_id,
        ok,
        g_ra_regions.count,
        g_ra_regions.total_size
    );
    return g_ra_regions_ready ? 1 : 0;
}

extern "C" void xora_host_memory_destroy(void) {
    if (g_ra_regions_ready || g_ra_regions.count > 0) {
        rc_libretro_memory_destroy(&g_ra_regions);
    }
    g_ra_regions = rc_libretro_memory_regions_t{};
    g_ra_regions_ready = false;
}

extern "C" uint32_t xora_host_memory_read(uint32_t address, uint8_t* buffer, uint32_t num_bytes) {
    if (!buffer || num_bytes == 0) return 0;
    if (g_ra_regions_ready) {
        return rc_libretro_memory_read(&g_ra_regions, address, buffer, num_bytes);
    }
    // Fallback: treat SYSTEM_RAM as a flat map from address 0.
    if (g_api.get_memory_data && g_api.get_memory_size) {
        auto* data = static_cast<uint8_t*>(g_api.get_memory_data(RETRO_MEMORY_SYSTEM_RAM));
        const size_t size = g_api.get_memory_size(RETRO_MEMORY_SYSTEM_RAM);
        if (!data || address >= size) return 0;
        const uint32_t avail = static_cast<uint32_t>(size - address);
        const uint32_t n = num_bytes < avail ? num_bytes : avail;
        std::memcpy(buffer, data + address, n);
        return n;
    }
    return 0;
}

extern "C" int xora_host_memory_ready(void) {
    return g_ra_regions_ready ? 1 : 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeLoadCore(
    JNIEnv* env,
    jclass,
    jstring core_path,
    jstring system_dir,
    jstring save_dir
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_unlocked();
    g_last_error.clear();

    const char* core_c = env->GetStringUTFChars(core_path, nullptr);
    const char* sys_c = system_dir ? env->GetStringUTFChars(system_dir, nullptr) : nullptr;
    const char* save_c = save_dir ? env->GetStringUTFChars(save_dir, nullptr) : nullptr;
    g_system_dir = sys_c ? sys_c : "";
    g_save_dir = save_c ? save_c : "";

    void* handle = dlopen(core_c, RTLD_LOCAL | RTLD_NOW);
    if (sys_c) env->ReleaseStringUTFChars(system_dir, sys_c);
    if (save_c) env->ReleaseStringUTFChars(save_dir, save_c);

    if (!handle) {
        g_last_error = dlerror() ? dlerror() : "dlopen failed";
        ALOGE("dlopen(%s): %s", core_c, g_last_error.c_str());
        env->ReleaseStringUTFChars(core_path, core_c);
        return JNI_FALSE;
    }
    env->ReleaseStringUTFChars(core_path, core_c);

    if (!load_symbols(handle)) {
        g_last_error = "Missing required libretro symbols";
        unload_unlocked();
        return JNI_FALSE;
    }

    g_api.set_environment(environment);
    g_api.set_video_refresh(video_refresh);
    g_api.set_audio_sample(audio_sample);
    g_api.set_audio_sample_batch(audio_sample_batch);
    g_api.set_input_poll(input_poll);
    g_api.set_input_state(input_state);
    g_api.init();
    plug_controllers();

    ALOGI("Core loaded (api %u)", g_api.api_version ? g_api.api_version() : 0u);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeLoadGame(
    JNIEnv* env,
    jclass,
    jstring rom_path
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_api.handle || !g_api.load_game) {
        g_last_error = "No core loaded";
        return JNI_FALSE;
    }

    const char* path_c = env->GetStringUTFChars(rom_path, nullptr);
    g_rom_path = path_c ? path_c : "";
    env->ReleaseStringUTFChars(rom_path, path_c);
    g_rom_buffer.clear();
    clear_game_info_ext();

    if (g_rom_path.empty()) {
        g_last_error = "Empty ROM path";
        return JNI_FALSE;
    }

    retro_system_info info{};
    if (g_api.get_system_info) g_api.get_system_info(&info);

    std::string ext = path_extension(g_rom_path);
    const bool block_extract = info.block_extract;
    bool from_archive = false;

    // No-Intro / TOSEC zips: extract inner ROM unless the core wants the archive itself (FBNeo).
    if (!block_extract && ext == "zip") {
        std::string read_error;
        std::vector<uint8_t> zip_bytes;
        if (!read_rom_file(g_rom_path.c_str(), zip_bytes, read_error)) {
            g_last_error = read_error;
            ALOGE("%s", g_last_error.c_str());
            g_rom_path.clear();
            return JNI_FALSE;
        }
        std::string inner_name;
        std::vector<uint8_t> inner;
        std::string zip_error;
        if (!extract_zip_entry(zip_bytes, info.valid_extensions, inner, inner_name, zip_error)) {
            g_last_error = zip_error.empty() ? "Failed to extract ZIP ROM" : zip_error;
            ALOGE("%s (%s)", g_last_error.c_str(), g_rom_path.c_str());
            g_rom_path.clear();
            return JNI_FALSE;
        }
        g_ext_archive_path = g_rom_path;
        g_ext_archive_file = inner_name;
        g_ext_from_archive = true;
        from_archive = true;
        // Fabricate a path with the inner extension — many cores gate on path suffix.
        g_rom_path = path_directory(g_rom_path) + "/" + inner_name;
        g_rom_buffer = std::move(inner);
        ext = path_extension(inner_name);
        ALOGI(
            "Extracted ZIP entry '%s' (%zu bytes) for core %s",
            inner_name.c_str(),
            g_rom_buffer.size(),
            info.library_name ? info.library_name : "?"
        );
    }

    const bool need_fullpath = resolve_need_fullpath(info, ext);
    g_ext_persistent = resolve_persistent_data(info, ext);

    auto attempt_load = [&](bool with_data) -> bool {
        retro_game_info game{};
        game.path = g_rom_path.c_str();
        game.meta = nullptr;
        if (with_data) {
            game.data = g_rom_buffer.data();
            game.size = g_rom_buffer.size();
        } else {
            game.data = nullptr;
            game.size = 0;
        }
        fill_game_info_ext(with_data);
        ALOGI(
            "retro_load_game path=%s data=%zu need_fullpath=%d archive=%d core=%s",
            g_rom_path.c_str(),
            with_data ? g_rom_buffer.size() : 0u,
            need_fullpath ? 1 : 0,
            from_archive ? 1 : 0,
            info.library_name ? info.library_name : "?"
        );
        return g_api.load_game(&game);
    };

    // Ensure buffer when the primary strategy needs data.
    if (!need_fullpath && g_rom_buffer.empty()) {
        std::string read_error;
        if (!read_rom_file(g_rom_path.c_str(), g_rom_buffer, read_error)) {
            g_last_error = read_error;
            ALOGE("%s", g_last_error.c_str());
            g_rom_path.clear();
            return JNI_FALSE;
        }
    }
    if (need_fullpath && !from_archive && !rom_file_exists(g_rom_path.c_str())) {
        g_last_error = "ROM not readable at path: " + g_rom_path;
        ALOGE("%s", g_last_error.c_str());
        return JNI_FALSE;
    }

    // Extracted archives with need_fullpath: write a temp file next to saves so fopen works.
    std::string temp_extracted;
    if (need_fullpath && from_archive && !g_rom_buffer.empty()) {
        const std::string dir = g_save_dir.empty() ? path_directory(g_ext_archive_path) : g_save_dir;
        temp_extracted = dir + "/.xora_extracted_" + g_ext_archive_file;
        FILE* out = std::fopen(temp_extracted.c_str(), "wb");
        if (!out) {
            g_last_error = "Cannot write extracted ROM to " + temp_extracted;
            ALOGE("%s", g_last_error.c_str());
            return JNI_FALSE;
        }
        const size_t written = std::fwrite(g_rom_buffer.data(), 1, g_rom_buffer.size(), out);
        std::fclose(out);
        if (written != g_rom_buffer.size()) {
            g_last_error = "Incomplete extracted ROM write";
            return JNI_FALSE;
        }
        g_rom_path = temp_extracted;
        // Path core: don't pass buffer.
        g_rom_buffer.clear();
    }

    bool ok = attempt_load(!need_fullpath && !g_rom_buffer.empty());
    // Fallback: some Android cores mis-report need_fullpath or accept either form.
    if (!ok) {
        ALOGW("Primary load failed; trying alternate path/buffer strategy");
        if (need_fullpath) {
            if (g_rom_buffer.empty() && !from_archive) {
                std::string read_error;
                if (!read_rom_file(g_rom_path.c_str(), g_rom_buffer, read_error)) {
                    ALOGW("Fallback buffer read failed: %s", read_error.c_str());
                }
            }
            if (!g_rom_buffer.empty()) ok = attempt_load(true);
        } else {
            ok = attempt_load(false);
        }
    }

    if (!ok) {
        g_last_error = std::string("retro_load_game failed (core=") +
            (info.library_name ? info.library_name : "?") +
            ", ext=" + ext +
            ", bytes=" + std::to_string(g_rom_buffer.size()) +
            (from_archive ? ", from_zip" : "") +
            ")";
        ALOGE("%s path=%s", g_last_error.c_str(), g_rom_path.c_str());
        g_rom_path.clear();
        g_rom_buffer.clear();
        clear_game_info_ext();
        if (!temp_extracted.empty()) std::remove(temp_extracted.c_str());
        return JNI_FALSE;
    }

    g_game_loaded = true;
    plug_controllers();
    unsigned hw_w = 640;
    unsigned hw_h = 480;
    if (g_api.get_system_av_info) {
        retro_system_av_info av{};
        g_api.get_system_av_info(&av);
        if (av.timing.fps > 1.0) g_fps = av.timing.fps;
        if (av.timing.sample_rate > 1.0) g_sample_rate = av.timing.sample_rate;
        // Prefer base geometry for the initial FBO. max_* from GLideN64 can be 4K+ and
        // allocating that offscreen + glReadPixels every frame OOMs on handhelds.
        if (av.geometry.base_width > 0) hw_w = av.geometry.base_width;
        if (av.geometry.base_height > 0) hw_h = av.geometry.base_height;
        else {
            if (av.geometry.max_width > 0) hw_w = av.geometry.max_width;
            if (av.geometry.max_height > 0) hw_h = av.geometry.max_height;
        }
        constexpr unsigned kMaxHwFbo = 1920;
        if (hw_w > kMaxHwFbo) hw_w = kMaxHwFbo;
        if (hw_h > kMaxHwFbo) hw_h = kMaxHwFbo;
        ALOGI("AV: %ux%u @ %.2f fps, %.0f Hz (hw fbo %ux%u)",
              av.geometry.base_width, av.geometry.base_height, g_fps, g_sample_rate,
              hw_w, hw_h);
    }
    // HW cores need context_reset after a successful load (and a sized FBO).
    if (xora_hw::is_active()) {
        if (!xora_hw::ensure_context(hw_w, hw_h)) {
            g_last_error = "OpenGL ES context init failed for HW core";
            ALOGE("%s", g_last_error.c_str());
            if (g_api.unload_game) g_api.unload_game();
            g_game_loaded = false;
            xora_hw::destroy();
            return JNI_FALSE;
        }
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeUnload(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_unlocked();
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeRunFrame(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (xora_gba_link_active()) {
        xora_gba_link_run_frame();
        return;
    }
    if (g_api.handle && g_game_loaded && g_api.run) {
        // Keep the EGL context current for GLES cores on this emu thread.
        if (xora_hw::is_active()) {
            xora_hw::ensure_context(0, 0);
        }
        if (g_netpacket_started.load(std::memory_order_relaxed)) {
            netpacket_deliver_incoming();
            if (g_netpacket.poll) g_netpacket.poll();
        }
        g_api.run();
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeGbaLinkStart(
    JNIEnv* env,
    jclass,
    jstring rom_path,
    jint players,
    jint local_slot
) {
    if (!rom_path) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(rom_path, nullptr);
    if (!path) return JNI_FALSE;
    std::string error;
    bool ok = false;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        ok = xora_gba_link_start(path, static_cast<int>(players), static_cast<int>(local_slot) - 1, error);
        if (!ok && !error.empty()) g_last_error = error;
    }
    env->ReleaseStringUTFChars(rom_path, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeGbaLinkStop(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    xora_gba_link_stop();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeGbaLinkActive(JNIEnv*, jclass) {
    return xora_gba_link_active() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeNetpacketAvailable(JNIEnv*, jclass) {
    return g_netpacket_set.load(std::memory_order_relaxed) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeNetpacketStart(
    JNIEnv*,
    jclass,
    jint local_client_id
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_netpacket_set.load(std::memory_order_relaxed) || !g_netpacket.start) {
        g_last_error = "This core did not publish a netpacket interface";
        return JNI_FALSE;
    }
    const uint16_t client_id = static_cast<uint16_t>(local_client_id & 0xFFFF);
    if (g_netpacket_started.load(std::memory_order_relaxed)) {
        if (g_netpacket_local_id == client_id) return JNI_TRUE;
        if (g_netpacket.stop) g_netpacket.stop();
        g_netpacket_started.store(false, std::memory_order_relaxed);
    }
        g_netpacket_local_id = client_id;
        g_netpacket.start(client_id, netpacket_send, netpacket_poll_receive);
        g_netpacket_started.store(true, std::memory_order_relaxed);
        ALOGI("netpacket start client_id=%u", static_cast<unsigned>(client_id));
        return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeNetpacketStop(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    netpacket_reset_unlocked();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeNetpacketPeerConnected(
    JNIEnv*,
    jclass,
    jint client_id
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_netpacket_started.load(std::memory_order_relaxed)) return JNI_FALSE;
    if (!g_netpacket.connected) return JNI_TRUE;
    const bool ok = g_netpacket.connected(static_cast<uint16_t>(client_id & 0xFFFF));
    ALOGI("netpacket connected client_id=%d accepted=%d", static_cast<int>(client_id), ok ? 1 : 0);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeNetpacketPeerDisconnected(
    JNIEnv*,
    jclass,
    jint client_id
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_netpacket_started.load(std::memory_order_relaxed) || !g_netpacket.disconnected) return;
    g_netpacket.disconnected(static_cast<uint16_t>(client_id & 0xFFFF));
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeNetpacketIncoming(
    JNIEnv* env,
    jclass,
    jint from_client_id,
    jbyteArray data
) {
    if (!data) return;
    const jsize length = env->GetArrayLength(data);
    if (length <= 0) return;
    NetpacketIo packet;
    packet.client_id = static_cast<uint16_t>(from_client_id & 0xFFFF);
    packet.data.resize(static_cast<size_t>(length));
    env->GetByteArrayRegion(data, 0, length, reinterpret_cast<jbyte*>(packet.data.data()));
    std::lock_guard<std::mutex> lock(g_netpacket_io_mutex);
    if (g_netpacket_incoming.size() > 512) g_netpacket_incoming.pop_front();
    g_netpacket_incoming.push_back(std::move(packet));
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeNetpacketDrainOutgoing(JNIEnv* env, jclass) {
    std::deque<NetpacketIo> batch;
    {
        std::lock_guard<std::mutex> lock(g_netpacket_io_mutex);
        batch.swap(g_netpacket_outgoing);
    }
    jclass byteArrayClass = env->FindClass("[B");
    if (!byteArrayClass) return nullptr;
    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(batch.size()), byteArrayClass, nullptr);
    if (!arr) return nullptr;
    jsize index = 0;
    for (const auto& packet : batch) {
        const jsize n = static_cast<jsize>(4 + packet.data.size());
        jbyteArray item = env->NewByteArray(n);
        if (!item) continue;
        std::vector<jbyte> packed(static_cast<size_t>(n));
        packed[0] = static_cast<jbyte>((packet.client_id >> 8) & 0xFF);
        packed[1] = static_cast<jbyte>(packet.client_id & 0xFF);
        packed[2] = static_cast<jbyte>((packet.flags >> 8) & 0xFF);
        packed[3] = static_cast<jbyte>(packet.flags & 0xFF);
        if (!packet.data.empty()) {
            std::memcpy(packed.data() + 4, packet.data.data(), packet.data.size());
        }
        env->SetByteArrayRegion(item, 0, n, packed.data());
        env->SetObjectArrayElement(arr, index++, item);
        env->DeleteLocalRef(item);
    }
    return arr;
}

// Game Link: poke the mmap'd GBA I/O page only. Walking emulator RAM for gba->sio
// (string scan + guessed RCNT/SIOCNT pointers) crashed mGBA the instant Player 2
// linked. Cable detect may be weaker; the session must stay up.
namespace {

constexpr uint32_t kGbaIoBase = 0x04000000u;
constexpr size_t kGbaIoMinLen = 0x204u;
constexpr int kGbaRegSiomulti0 = 0x120 / 2;
constexpr int kGbaRegSiocnt = 0x128 / 2;
constexpr int kGbaRegSiomltSend = 0x12A / 2;
constexpr int kGbaRegRcnt = 0x134 / 2;
constexpr uint16_t kGbaSiocntMulti = 0x2000u;
constexpr uint16_t kGbaSiocntModeMask = 0x3000u;

std::atomic<bool> g_sio_link_on{false};
std::atomic<int> g_sio_local_id{0};
std::atomic<uint16_t> g_sio_multi[4]{{0xFFFF}, {0xFFFF}, {0xFFFF}, {0xFFFF}};
bool g_sio_logged_hook = false;

uint16_t* gba_io_regs() {
    for (const auto& desc : g_mmap_descriptors) {
        if (desc.start == kGbaIoBase && desc.ptr && desc.len >= kGbaIoMinLen) {
            return static_cast<uint16_t*>(desc.ptr);
        }
    }
    return nullptr;
}

void gba_sio_apply_io(uint16_t* io) {
    if (!io) return;
    const int id = g_sio_local_id.load(std::memory_order_relaxed);
    if (id >= 0 && id < 4) {
        io[kGbaRegSiomltSend] = g_sio_multi[id].load(std::memory_order_relaxed);
    }
    for (int i = 0; i < 4; ++i) {
        uint16_t word = g_sio_multi[i].load(std::memory_order_relaxed);
        if (word == 0xFFFF && g_sio_link_on.load(std::memory_order_relaxed) && i < 2) {
            word = 0;
        }
        io[kGbaRegSiomulti0 + i] = word;
    }

    uint16_t rcnt = io[kGbaRegRcnt];
    rcnt = static_cast<uint16_t>((rcnt & ~0x0004u) | 0x000Au); // SI=0, SD=1, SO=1
    io[kGbaRegRcnt] = rcnt;

    uint16_t cnt = io[kGbaRegSiocnt];
    if ((cnt & kGbaSiocntModeMask) == kGbaSiocntMulti) {
        // Keep BUSY (bit 7) and ERROR (bit 6).
        cnt = static_cast<uint16_t>(cnt & ~0x003Cu); // SI, SD, ID
        cnt = static_cast<uint16_t>(cnt | 0x0008u | ((id & 3) << 4));
        if (id != 0) {
            cnt = static_cast<uint16_t>(cnt | 0x0004u);
        } else {
            cnt = static_cast<uint16_t>(cnt & ~0x0004u);
        }
    } else {
        cnt = static_cast<uint16_t>(cnt & ~0x0004u);
    }
    io[kGbaRegSiocnt] = cnt;
}

void gba_sio_refresh(uint16_t* io) {
    if (!io || !g_sio_link_on.load(std::memory_order_relaxed)) return;
    if (!g_sio_logged_hook) {
        g_sio_logged_hook = true;
        ALOGI("GBA Game Link: writing SIOMULTI/SIOCNT/RCNT on mapped I/O only");
    }
    gba_sio_apply_io(io);
}

void xora_gba_sio_drop_hook() {
    g_sio_logged_hook = false;
}

void xora_gba_sio_reset() {
    xora_gba_sio_drop_hook();
    g_sio_link_on.store(false, std::memory_order_relaxed);
    g_sio_local_id.store(0, std::memory_order_relaxed);
    for (auto& slot : g_sio_multi) slot.store(0xFFFF, std::memory_order_relaxed);
}

void xora_gba_sio_on_poll() {
    if (!g_sio_link_on.load(std::memory_order_relaxed)) return;
    uint16_t* io = gba_io_regs();
    if (!io) return;
    const int id = g_sio_local_id.load(std::memory_order_relaxed);
    if (id >= 0 && id < 4) {
        g_sio_multi[id].store(io[kGbaRegSiomltSend], std::memory_order_relaxed);
    }
    gba_sio_refresh(io);
}

}  // namespace

extern "C" JNIEXPORT jintArray JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeGbaSioRead(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    uint16_t* io = gba_io_regs();
    if (!io) return nullptr;
    uint16_t cnt = io[kGbaRegSiocnt];
    jint packed[2] = {
        static_cast<jint>(io[kGbaRegSiomltSend]),
        static_cast<jint>(cnt),
    };
    jintArray out = env->NewIntArray(2);
    if (!out) return nullptr;
    env->SetIntArrayRegion(out, 0, 2, packed);
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeGbaSioApply(
    JNIEnv* env,
    jclass,
    jintArray multi,
    jint local_id
) {
    if (!multi) return;
    std::lock_guard<std::mutex> lock(g_mutex);
    uint16_t* io = gba_io_regs();
    if (!io) return;
    jsize n = env->GetArrayLength(multi);
    if (n < 4) return;
    jint slots[4];
    env->GetIntArrayRegion(multi, 0, 4, slots);
    const int id = local_id < 0 ? 0 : (local_id > 3 ? 3 : local_id);
    g_sio_local_id.store(id, std::memory_order_relaxed);
    for (int i = 0; i < 4; ++i) {
        g_sio_multi[i].store(static_cast<uint16_t>(slots[i] & 0xFFFF), std::memory_order_relaxed);
    }
    g_sio_link_on.store(true, std::memory_order_relaxed);
    gba_sio_refresh(io);
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeGbaSioSetEnabled(
    JNIEnv*,
    jclass,
    jboolean enabled
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_sio_link_on.store(enabled == JNI_TRUE, std::memory_order_relaxed);
    if (enabled != JNI_TRUE) return;
    uint16_t* io = gba_io_regs();
    if (io) gba_sio_refresh(io);
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeReset(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (xora_gba_link_active()) {
        xora_gba_link_reset();
        return;
    }
    if (g_api.handle && g_game_loaded && g_api.reset) g_api.reset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeSetPadState(
    JNIEnv*,
    jclass,
    jint buttons,
    jshort lx,
    jshort ly,
    jshort rx,
    jshort ry
) {
    g_pad_buttons[0].store(static_cast<uint16_t>(buttons & 0xFFFF), std::memory_order_relaxed);
    g_axis_lx[0].store(lx, std::memory_order_relaxed);
    g_axis_ly[0].store(ly, std::memory_order_relaxed);
    g_axis_rx[0].store(rx, std::memory_order_relaxed);
    g_axis_ry[0].store(ry, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeSetPadStatePort(
    JNIEnv*,
    jclass,
    jint port,
    jint buttons,
    jshort lx,
    jshort ly,
    jshort rx,
    jshort ry
) {
    if (port < 0 || port >= static_cast<int>(kMaxControllerPorts)) return;
    g_pad_buttons[port].store(static_cast<uint16_t>(buttons & 0xFFFF), std::memory_order_relaxed);
    g_axis_lx[port].store(lx, std::memory_order_relaxed);
    g_axis_ly[port].store(ly, std::memory_order_relaxed);
    g_axis_rx[port].store(rx, std::memory_order_relaxed);
    g_axis_ry[port].store(ry, std::memory_order_relaxed);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeCopyFrameRgba(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(g_frame_mutex);
    if (g_frame_w <= 0 || g_frame_h <= 0 || g_frame_rgba.empty()) return nullptr;
    const jsize len = static_cast<jsize>(g_frame_rgba.size() + 2);
    jintArray out = env->NewIntArray(len);
    if (!out) return nullptr;
    std::vector<jint> packed(static_cast<size_t>(len));
    packed[0] = g_frame_w;
    packed[1] = g_frame_h;
    for (size_t i = 0; i < g_frame_rgba.size(); ++i) {
        packed[i + 2] = static_cast<jint>(g_frame_rgba[i]);
    }
    env->SetIntArrayRegion(out, 0, len, packed.data());
    return out;
}

extern "C" JNIEXPORT jshortArray JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeDrainAudio(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(g_audio_mutex);
    if (g_audio.empty()) return nullptr;
    const jsize len = static_cast<jsize>(g_audio.size());
    jshortArray out = env->NewShortArray(len);
    if (!out) return nullptr;
    env->SetShortArrayRegion(out, 0, len, g_audio.data());
    g_audio.clear();
    return out;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeGetFps(JNIEnv*, jclass) {
    if (xora_gba_link_active()) return xora_gba_link_fps();
    return g_fps;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeGetSampleRate(JNIEnv*, jclass) {
    if (xora_gba_link_active()) return xora_gba_link_sample_rate();
    return g_sample_rate;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeSerialize(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_api.serialize_size || !g_api.serialize || !g_game_loaded) return nullptr;
    const size_t size = g_api.serialize_size();
    if (size == 0 || size > 64u * 1024u * 1024u) return nullptr;
    std::vector<uint8_t> buf(size);
    if (!g_api.serialize(buf.data(), size)) return nullptr;
    jbyteArray out = env->NewByteArray(static_cast<jsize>(size));
    if (!out) return nullptr;
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(size),
                            reinterpret_cast<const jbyte*>(buf.data()));
    return out;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeUnserialize(
    JNIEnv* env,
    jclass,
    jbyteArray data
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_api.unserialize || !g_game_loaded || !data) return JNI_FALSE;
    const jsize len = env->GetArrayLength(data);
    if (len <= 0) return JNI_FALSE;
    std::vector<uint8_t> buf(static_cast<size_t>(len));
    env->GetByteArrayRegion(data, 0, len, reinterpret_cast<jbyte*>(buf.data()));
    if (!g_api.unserialize(buf.data(), buf.size())) return JNI_FALSE;
    plug_controllers();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeLastError(JNIEnv* env, jclass) {
    return env->NewStringUTF(g_last_error.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativePlugControllers(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_game_loaded) plug_controllers();
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeClearCoreVariables(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_vars_mutex);
    g_var_overrides.clear();
    g_var_query_cache.clear();
    g_vars_updated.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeSetCoreVariable(
    JNIEnv* env,
    jclass,
    jstring key,
    jstring value
) {
    if (!key || !value) return;
    const char* k = env->GetStringUTFChars(key, nullptr);
    const char* v = env->GetStringUTFChars(value, nullptr);
    {
        std::lock_guard<std::mutex> lock(g_vars_mutex);
        g_var_overrides[k ? k : ""] = v ? v : "";
        g_vars_updated.store(true);
    }
    if (k) env->ReleaseStringUTFChars(key, k);
    if (v) env->ReleaseStringUTFChars(value, v);
}

extern "C" JNIEXPORT void JNICALL
Java_com_arcadia_shell_libretro_LibretroNative_nativeSetNetplayUsername(
    JNIEnv* env,
    jclass,
    jstring name
) {
    if (!name) {
        g_netplay_username = "Player";
        return;
    }
    const char* n = env->GetStringUTFChars(name, nullptr);
    g_netplay_username = n ? n : "Player";
    env->ReleaseStringUTFChars(name, n);
}
