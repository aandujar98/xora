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
    /** Port 0 = player 1, port 1 = player 2 (netplay). */
    external fun nativeSetPadStatePort(
        port: Int,
        buttons: Int,
        lx: Short,
        ly: Short,
        rx: Short,
        ry: Short,
    )
    /** Libretro pointer / stylus. [x]/[y] are −32767…32767 over the core framebuffer. */
    external fun nativeSetPointerState(x: Short, y: Short, pressed: Boolean)
    /** Packed `[width, height, pixels…]` or null when no frame yet. */
    external fun nativeCopyFrameRgba(): IntArray?
    external fun nativeDrainAudio(): ShortArray?
    external fun nativeGetFps(): Double
    external fun nativeGetSampleRate(): Double
    external fun nativeSerialize(): ByteArray?
    external fun nativeUnserialize(data: ByteArray): Boolean
    external fun nativeLastError(): String?
    /**
     * Plug P1–P4 using each core's SET_CONTROLLER_INFO device ids
     * (NES/SNES gamepad, N64 pad, DualShock, GameCube controller — never a multitap).
     */
    external fun nativePlugControllers()
    /** Packed `[send, siocnt]` from GBA SIOMLT_SEND / live SIOCNT, or null if I/O is not mapped. */
    external fun nativeGbaSioRead(): IntArray?
    /**
     * Publish SIOMULTI0–3 and this device's parent/child id on the mapped GBA I/O page.
     * Call every handheld-link frame. Never writes into guessed mGBA struct pointers.
     */
    external fun nativeGbaSioApply(multi: IntArray, localId: Int)
    /** Keep the GBA Game Link I/O poke armed while a handheld session is waiting or live. */
    external fun nativeGbaSioSetEnabled(enabled: Boolean)
    /** True when GBA I/O is reachable (mmap or gpSP io_registers). */
    external fun nativeGbaSioMapped(): Boolean

    /**
     * Start in-process mGBA lockstep (two cores + a real SIO cable). [localSlot] is 1-based.
     * Both devices must call this with the same ROM after the netplay handshake.
     */
    external fun nativeGbaLinkStart(romPath: String, players: Int, localSlot: Int): Boolean
    external fun nativeGbaLinkStop()
    external fun nativeGbaLinkActive(): Boolean

    /**
     * True after the loaded core called SET_NETPACKET_INTERFACE (gpSP Game Link).
     * Packets are bridged over the XOrA netplay session — joiners do not open a second socket.
     */
    external fun nativeNetpacketAvailable(): Boolean
    /** [localClientId] is 0 for the host and slot−1 for joiners. */
    external fun nativeNetpacketStart(localClientId: Int): Boolean
    external fun nativeNetpacketStop()
    external fun nativeNetpacketPeerConnected(clientId: Int): Boolean
    external fun nativeNetpacketPeerDisconnected(clientId: Int)
    external fun nativeNetpacketIncoming(fromClientId: Int, data: ByteArray)
    /**
     * Packed outgoing packets: each item is `u16be dest`, `u16be flags`, then payload.
     */
    external fun nativeNetpacketDrainOutgoing(): Array<ByteArray>?

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
    /** Queued Connect `r=patch` JSON (achievement set). */
    external fun nativeRaQueuePatchResponse(patchJson: String)
    /** Queued Connect `r=startsession` JSON (unlock state + session). */
    external fun nativeRaQueueStartSessionResponse(startSessionJson: String)
    /** Bind [md5Hex] → [gameId] in rcheevos so load_game skips the gameid request. */
    external fun nativeRaAddGameHash(md5Hex: String, gameId: Int)
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
    /**
     * Live achievement rows for the loaded game.
     * Each row: `[id, title, description, points, badgeUrl, unlocked ("0"/"1"), hardcore ("0"/"1"), progress]`.
     */
    external fun nativeRaListAchievements(): Array<Array<String>>?
}

/** One RetroAchievements row from [LibretroNative.nativeRaListAchievements]. */
data class RaLiveAchievement(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val badgeUrl: String,
    val unlocked: Boolean,
    val hardcore: Boolean,
    val progress: String,
) {
    companion object {
        fun fromNativeRow(row: Array<String>): RaLiveAchievement? {
            if (row.size < 8) return null
            val id = row[0].toIntOrNull() ?: return null
            return RaLiveAchievement(
                id = id,
                title = row[1],
                description = row[2],
                points = row[3].toIntOrNull() ?: 0,
                badgeUrl = row[4],
                unlocked = row[5] == "1",
                hardcore = row[6] == "1",
                progress = row[7],
            )
        }
    }
}
