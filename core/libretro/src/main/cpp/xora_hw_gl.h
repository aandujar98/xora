#pragma once

#include "libretro.h"

#include <cstdint>
#include <vector>

/**
 * Offscreen EGL + GLES FBO for Libretro SET_HW_RENDER.
 * Frames are read back into RGBA8888 for the Compose software path.
 */
namespace xora_hw {

bool accept_hw_render(retro_hw_render_callback* cb);
bool preferred_hw_context(unsigned* out_type);

/** Create / resize FBO to at least width×height and call context_reset once ready. */
bool ensure_context(unsigned width, unsigned height);

bool is_active();
void destroy();

/** Read the HW FBO into dst (ARGB8888 packed as 0xAARRGGBB). */
bool read_frame(unsigned width, unsigned height, std::vector<uint32_t>& dst);

}  // namespace xora_hw
