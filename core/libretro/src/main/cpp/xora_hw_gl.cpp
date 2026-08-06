/**
 * Minimal GLES HW-render backend for XOrA: PBuffer EGL context + FBO + glReadPixels.
 * Lets Android GLES cores (Mupen64Plus-Next GLES, ParaLLEl, etc.) load and present
 * through the existing software RGBA frame path.
 */
#include "xora_hw_gl.h"

#include <android/log.h>
#include <dlfcn.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <cstring>
#include <mutex>
#include <string>

#define LOG_TAG "XoraHwGl"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#ifndef EGL_OPENGL_ES3_BIT_KHR
#define EGL_OPENGL_ES3_BIT_KHR 0x00000040
#endif

#ifndef GL_DEPTH24_STENCIL8_OES
#define GL_DEPTH24_STENCIL8_OES 0x88F0
#endif
#ifndef GL_DEPTH_STENCIL_ATTACHMENT_OES
#define GL_DEPTH_STENCIL_ATTACHMENT_OES 0x821A
#endif

namespace xora_hw {
namespace {

std::mutex g_hw_mutex;
retro_hw_render_callback g_cb{};
bool g_cb_set = false;
bool g_reset_done = false;

EGLDisplay g_display = EGL_NO_DISPLAY;
EGLContext g_context = EGL_NO_CONTEXT;
EGLSurface g_surface = EGL_NO_SURFACE;
EGLConfig g_config = nullptr;
int g_gles_major = 2;

GLuint g_fbo = 0;
GLuint g_color = 0;
GLuint g_depth = 0;
unsigned g_fbo_w = 0;
unsigned g_fbo_h = 0;

bool make_current() {
    if (g_display == EGL_NO_DISPLAY || g_context == EGL_NO_CONTEXT || g_surface == EGL_NO_SURFACE) {
        return false;
    }
    if (!eglMakeCurrent(g_display, g_surface, g_surface, g_context)) {
        ALOGE("eglMakeCurrent failed: 0x%x", eglGetError());
        return false;
    }
    return true;
}

void destroy_fbo_unlocked() {
    if (g_display == EGL_NO_DISPLAY) {
        g_fbo = g_color = g_depth = 0;
        g_fbo_w = g_fbo_h = 0;
        return;
    }
    if (g_context != EGL_NO_CONTEXT && g_surface != EGL_NO_SURFACE) {
        eglMakeCurrent(g_display, g_surface, g_surface, g_context);
    }
    if (g_fbo) glDeleteFramebuffers(1, &g_fbo);
    if (g_color) glDeleteTextures(1, &g_color);
    if (g_depth) glDeleteRenderbuffers(1, &g_depth);
    g_fbo = g_color = g_depth = 0;
    g_fbo_w = g_fbo_h = 0;
}

bool create_fbo_unlocked(unsigned width, unsigned height) {
    if (width == 0) width = 640;
    if (height == 0) height = 480;
    // Cap offscreen targets — Mupen/GLideN64 can request 4K+; full glReadPixels OOMs.
    constexpr unsigned kMaxDim = 1920;
    if (width > kMaxDim) width = kMaxDim;
    if (height > kMaxDim) height = kMaxDim;
    // Grow only — avoids thrashing when cores report base vs max geometry.
    if (g_fbo && width <= g_fbo_w && height <= g_fbo_h) {
        glBindFramebuffer(GL_FRAMEBUFFER, g_fbo);
        glViewport(0, 0, static_cast<GLsizei>(width), static_cast<GLsizei>(height));
        return true;
    }

    destroy_fbo_unlocked();
    if (!make_current()) return false;

    glGenTextures(1, &g_color);
    glBindTexture(GL_TEXTURE_2D, g_color);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(
        GL_TEXTURE_2D, 0, GL_RGBA,
        static_cast<GLsizei>(width), static_cast<GLsizei>(height),
        0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr
    );

    if (g_cb.depth || g_cb.stencil) {
        glGenRenderbuffers(1, &g_depth);
        glBindRenderbuffer(GL_RENDERBUFFER, g_depth);
        const GLenum depth_fmt = (g_cb.depth && g_cb.stencil)
            ? GL_DEPTH24_STENCIL8_OES
            : (g_cb.depth ? GL_DEPTH_COMPONENT16 : GL_STENCIL_INDEX8);
        glRenderbufferStorage(
            GL_RENDERBUFFER, depth_fmt,
            static_cast<GLsizei>(width), static_cast<GLsizei>(height)
        );
    }

    glGenFramebuffers(1, &g_fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, g_fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, g_color, 0);
    if (g_depth) {
        if (g_cb.depth && g_cb.stencil) {
            glFramebufferRenderbuffer(
                GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT_OES, GL_RENDERBUFFER, g_depth
            );
        } else if (g_cb.depth) {
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, g_depth);
        } else {
            glFramebufferRenderbuffer(
                GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, g_depth
            );
        }
    }

    const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        ALOGE("FBO incomplete: 0x%x", status);
        destroy_fbo_unlocked();
        return false;
    }

    g_fbo_w = width;
    g_fbo_h = height;
    glViewport(0, 0, static_cast<GLsizei>(width), static_cast<GLsizei>(height));
    ALOGI("HW FBO %ux%u (depth=%d stencil=%d gles%d)",
          width, height, g_cb.depth ? 1 : 0, g_cb.stencil ? 1 : 0, g_gles_major);
    return true;
}

uintptr_t RETRO_CALLCONV get_current_framebuffer() {
    return static_cast<uintptr_t>(g_fbo);
}

retro_proc_address_t RETRO_CALLCONV get_proc_address(const char* sym) {
    if (!sym || !*sym) return nullptr;
    void* addr = reinterpret_cast<void*>(eglGetProcAddress(sym));
    if (!addr) {
        // Core GLES entry points are often missing from eglGetProcAddress on Android.
        static void* gles = nullptr;
        if (!gles) {
            gles = dlopen("libGLESv3.so", RTLD_NOW | RTLD_LOCAL);
            if (!gles) gles = dlopen("libGLESv2.so", RTLD_NOW | RTLD_LOCAL);
        }
        if (gles) addr = dlsym(gles, sym);
        if (!addr) addr = dlsym(RTLD_DEFAULT, sym);
    }
    return reinterpret_cast<retro_proc_address_t>(addr);
}

void destroy_egl_unlocked();

bool create_egl_unlocked(int gles_major) {
    if (g_display != EGL_NO_DISPLAY) return true;

    g_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (g_display == EGL_NO_DISPLAY) {
        ALOGE("eglGetDisplay failed");
        return false;
    }
    if (!eglInitialize(g_display, nullptr, nullptr)) {
        ALOGE("eglInitialize failed: 0x%x", eglGetError());
        g_display = EGL_NO_DISPLAY;
        return false;
    }

    const EGLint renderable = (gles_major >= 3)
        ? (EGL_OPENGL_ES3_BIT_KHR | EGL_OPENGL_ES2_BIT)
        : EGL_OPENGL_ES2_BIT;

    const EGLint depth_size = g_cb.depth ? 24 : 0;
    const EGLint stencil_size = g_cb.stencil ? 8 : 0;
    EGLint attribs[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, renderable,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, depth_size,
        EGL_STENCIL_SIZE, stencil_size,
        EGL_NONE,
    };

    EGLint num = 0;
    if (!eglChooseConfig(g_display, attribs, &g_config, 1, &num) || num < 1) {
        // Retry without ES3 bit if needed.
        if (gles_major >= 3) {
            attribs[3] = EGL_OPENGL_ES2_BIT;
            if (!eglChooseConfig(g_display, attribs, &g_config, 1, &num) || num < 1) {
                ALOGE("eglChooseConfig failed: 0x%x", eglGetError());
                destroy_egl_unlocked();
                return false;
            }
            gles_major = 2;
        } else {
            ALOGE("eglChooseConfig failed: 0x%x", eglGetError());
            destroy_egl_unlocked();
            return false;
        }
    }

    const EGLint ctx_attribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, gles_major,
        EGL_NONE,
    };
    g_context = eglCreateContext(g_display, g_config, EGL_NO_CONTEXT, ctx_attribs);
    if (g_context == EGL_NO_CONTEXT && gles_major >= 3) {
        const EGLint ctx2[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
        g_context = eglCreateContext(g_display, g_config, EGL_NO_CONTEXT, ctx2);
        gles_major = 2;
    }
    if (g_context == EGL_NO_CONTEXT) {
        ALOGE("eglCreateContext failed: 0x%x", eglGetError());
        destroy_egl_unlocked();
        return false;
    }

    const EGLint pbuf[] = {
        EGL_WIDTH, 16,
        EGL_HEIGHT, 16,
        EGL_NONE,
    };
    g_surface = eglCreatePbufferSurface(g_display, g_config, pbuf);
    if (g_surface == EGL_NO_SURFACE) {
        ALOGE("eglCreatePbufferSurface failed: 0x%x", eglGetError());
        destroy_egl_unlocked();
        return false;
    }

    g_gles_major = gles_major;
    if (!make_current()) {
        destroy_egl_unlocked();
        return false;
    }
    ALOGI("EGL context ready (GLES %d)", g_gles_major);
    return true;
}

void destroy_egl_unlocked() {
    destroy_fbo_unlocked();
    if (g_display != EGL_NO_DISPLAY) {
        eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (g_surface != EGL_NO_SURFACE) eglDestroySurface(g_display, g_surface);
        if (g_context != EGL_NO_CONTEXT) eglDestroyContext(g_display, g_context);
        eglTerminate(g_display);
    }
    g_display = EGL_NO_DISPLAY;
    g_context = EGL_NO_CONTEXT;
    g_surface = EGL_NO_SURFACE;
    g_config = nullptr;
    g_cb = {};
    g_cb_set = false;
    g_reset_done = false;
    g_gles_major = 2;
}

int gles_major_for(enum retro_hw_context_type type) {
    switch (type) {
        case RETRO_HW_CONTEXT_OPENGLES3:
        case RETRO_HW_CONTEXT_OPENGLES_VERSION:
            return 3;
        case RETRO_HW_CONTEXT_OPENGLES2:
        case RETRO_HW_CONTEXT_OPENGL:
            return 2;
        default:
            return 0;
    }
}

}  // namespace

bool preferred_hw_context(unsigned* out_type) {
    if (!out_type) return false;
    *out_type = RETRO_HW_CONTEXT_OPENGLES3;
    return true;
}

bool accept_hw_render(retro_hw_render_callback* cb) {
    if (!cb) return false;
    const int major = gles_major_for(cb->context_type);
    if (major == 0) {
        ALOGW("Unsupported HW context type %d", static_cast<int>(cb->context_type));
        return false;
    }

    std::lock_guard<std::mutex> lock(g_hw_mutex);
    destroy_egl_unlocked();
    g_cb = *cb;
    g_cb.get_current_framebuffer = get_current_framebuffer;
    g_cb.get_proc_address = get_proc_address;
    *cb = g_cb;
    g_cb_set = true;
    g_reset_done = false;

    if (!create_egl_unlocked(major)) {
        g_cb_set = false;
        return false;
    }
    // Minimal FBO so get_current_framebuffer is valid during retro_load_game.
    if (!create_fbo_unlocked(640, 480)) {
        destroy_egl_unlocked();
        return false;
    }
    // Defer context_reset until after retro_load_game (ensure_context).
    // Mupen sets first_context_reset only after SET_HW_RENDER returns; resetting
    // here skips emu_step_initialize and crashes on the first run.
    ALOGI("SET_HW_RENDER accepted (type=%d depth=%d stencil=%d) — reset deferred",
          static_cast<int>(g_cb.context_type), g_cb.depth ? 1 : 0, g_cb.stencil ? 1 : 0);
    return true;
}

bool ensure_context(unsigned width, unsigned height) {
    retro_hw_context_reset_t reset_fn = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_hw_mutex);
        if (!g_cb_set) return false;
        if (!create_egl_unlocked(gles_major_for(g_cb.context_type))) return false;
        if (width == 0 || height == 0) {
            if (!g_fbo && !create_fbo_unlocked(640, 480)) return false;
            if (!make_current()) return false;
        } else if (!create_fbo_unlocked(width, height)) {
            return false;
        }

        if (!g_reset_done && g_cb.context_reset) {
            reset_fn = g_cb.context_reset;
            g_reset_done = true;
        }
    }
    // Call outside the lock — cores may re-enter the environment / GL helpers.
    if (reset_fn) {
        ALOGI("Calling HW context_reset");
        reset_fn();
    }
    return true;
}

bool is_active() {
    std::lock_guard<std::mutex> lock(g_hw_mutex);
    return g_cb_set && g_fbo != 0;
}

void destroy() {
    retro_hw_context_reset_t destroy_fn = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_hw_mutex);
        if (g_cb_set && g_reset_done && g_cb.context_destroy) {
            destroy_fn = g_cb.context_destroy;
            g_cb.context_destroy = nullptr;
            make_current();
        }
    }
    if (destroy_fn) {
        destroy_fn();
    }
    std::lock_guard<std::mutex> lock(g_hw_mutex);
    destroy_egl_unlocked();
}

bool read_frame(unsigned width, unsigned height, std::vector<uint32_t>& dst) {
    std::lock_guard<std::mutex> lock(g_hw_mutex);
    if (!g_cb_set || !g_fbo || width == 0 || height == 0) return false;
    if (width > g_fbo_w || height > g_fbo_h) {
        if (!create_fbo_unlocked(width, height)) return false;
    }
    if (!make_current()) return false;

    glBindFramebuffer(GL_FRAMEBUFFER, g_fbo);
    std::vector<uint8_t> rgba(static_cast<size_t>(width) * height * 4);
    glReadPixels(
        0, 0,
        static_cast<GLsizei>(width), static_cast<GLsizei>(height),
        GL_RGBA, GL_UNSIGNED_BYTE, rgba.data()
    );

    dst.resize(static_cast<size_t>(width) * height);
    // glReadPixels row 0 is the GL bottom. Flip when the core uses bottom-left origin
    // so the Compose bitmap is top-left first.
    const bool flip = g_cb.bottom_left_origin;
    for (unsigned y = 0; y < height; ++y) {
        const unsigned src_y = flip ? (height - 1 - y) : y;
        const uint8_t* row = rgba.data() + static_cast<size_t>(src_y) * width * 4;
        uint32_t* out = dst.data() + static_cast<size_t>(y) * width;
        for (unsigned x = 0; x < width; ++x) {
            const uint8_t r = row[x * 4 + 0];
            const uint8_t g = row[x * 4 + 1];
            const uint8_t b = row[x * 4 + 2];
            const uint8_t a = row[x * 4 + 3];
            out[x] = (static_cast<uint32_t>(a) << 24) |
                (static_cast<uint32_t>(r) << 16) |
                (static_cast<uint32_t>(g) << 8) |
                static_cast<uint32_t>(b);
        }
    }
    return true;
}

}  // namespace xora_hw
