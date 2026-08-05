package com.arcadia.shell.libretro

/**
 * JNI bridge to the XOrA Libretro host (`libxora_libretro.so`), including RetroAchievements.
 */
object LibretroNative {
    init {
        System.loadLibrary("xora_libretro")
    }

    external fun nativeLoadCore(corePath: String, systemDir: String?, saveDir: String?): Boolean
    external fun nativeLoadGame(romPath: String): Boolean
    external fun nativeUnload()
    external fun nativeRunFrame()
    external fun nativeReset()
    external fun nativeSetPadState(buttons: Int, lx: Short, ly: Short, rx: Short, ry: Short)
    /** Packed `[width, height, pixels…]` or null when no frame yet. */
    external fun nativeCopyFrameRgba(): IntArray?
    external fun nativeDrainAudio(): ShortArray?
    external fun nativeGetFps(): Double
    external fun nativeGetSampleRate(): Double
    external fun nativeSerialize(): ByteArray?
    external fun nativeUnserialize(data: ByteArray): Boolean
    external fun nativeLastError(): String?

    /** Clear frontend core-option overrides (call before applying a fresh set). */
    external fun nativeClearCoreVariables()
    /** Override a Libretro core option; cores read via GET_VARIABLE. */
    external fun nativeSetCoreVariable(key: String, value: String)
    /** Exposed to cores via RETRO_ENVIRONMENT_GET_USERNAME (netplay / online). */
    external fun nativeSetNetplayUsername(name: String)

    // --- RetroAchievements (rcheevos) ---
    external fun nativeRaAttach(bridge: LibretroRaBridge)
    external fun nativeRaDetach()
    /**
     * Queue a successful Connect `login2` JSON body to satisfy the next rcheevos login
     * server_call (avoids a second Cloudflare-facing HTTP request).
     */
    external fun nativeRaQueueLoginResponse(loginJson: String)
    /** Queued Connect `r=gameid` JSON so rcheevos can skip a Cloudflare-blocked lookup. */
    external fun nativeRaQueueGameIdResponse(gameIdJson: String)
    external fun nativeRaLogin(username: String, token: String)
    /** Softcore (false) allows save states; hardcore (true) matches RA leaderboards. */
    external fun nativeRaSetHardcore(enabled: Boolean)
    external fun nativeRaInitMemory(consoleId: Int): Boolean
    external fun nativeRaLoadGame(md5Hex: String)
    external fun nativeRaUnloadGame()
    external fun nativeRaDoFrame()
    external fun nativeRaIdle()
    external fun nativeRaReset()
    external fun nativeRaSummary(): String?
}
