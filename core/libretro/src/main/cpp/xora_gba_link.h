#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

/**
 * In-process mGBA Game Link: two (or more) libmgba cores on one lockstep
 * coordinator, the same arrangement desktop mGBA uses for "New multiplayer
 * window". Network play only exchanges pads — never SIO bytes.
 */
bool xora_gba_link_start(const char* rom_path, int players, int local_slot, std::string& error);
void xora_gba_link_stop();
bool xora_gba_link_active();
void xora_gba_link_run_frame();
void xora_gba_link_reset();
double xora_gba_link_fps();
double xora_gba_link_sample_rate();

/**
 * Cart bytes for lockstep: the unzipped ROM libretro already loaded, or a
 * .gba/.zip read from [path]. Never pass a folder into mCoreLoadFile.
 */
bool xora_host_load_gba_rom(const char* path, std::vector<uint8_t>& out, std::string& error);

/** Libretro joypad bits from the host, port 0 = Player 1. */
uint16_t xora_host_pad_buttons(int port);
void xora_host_publish_frame_argb(int width, int height, const uint32_t* pixels);
void xora_host_push_stereo_s16(const int16_t* samples, size_t count);
void xora_host_set_timing(double fps, double sample_rate);
