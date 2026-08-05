#pragma once

#include "libretro.h"

#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

/** Copy descriptors from a core's SET_MEMORY_MAPS callback (pointers must stay valid). */
void xora_host_set_memory_maps(const struct retro_memory_map* mmap);

/** Rebuild RA address space after a game is loaded. */
int xora_host_memory_init(uint32_t console_id);

void xora_host_memory_destroy(void);

/** Peek emulator RAM for rcheevos (returns bytes read). */
uint32_t xora_host_memory_read(uint32_t address, uint8_t* buffer, uint32_t num_bytes);

/** True when at least one memory region is mapped. */
int xora_host_memory_ready(void);

#ifdef __cplusplus
}
#endif
