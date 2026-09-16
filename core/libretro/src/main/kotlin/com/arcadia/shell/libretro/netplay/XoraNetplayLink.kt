package com.arcadia.shell.libretro.netplay

/**
 * Bidirectional netplay transport. LAN TCP and XOrA Network (Nakama match data) both implement
 * this so the handshake and pad exchange stay identical.
 */
interface XoraNetplayLink {
    fun send(type: Int, payload: ByteArray)

    /**
     * Blocks until the next assembled message. [timeoutMs] of `0` waits indefinitely.
     * Throws [java.net.SocketTimeoutException] on timeout and [java.io.IOException] when closed.
     */
    fun receive(timeoutMs: Int = 0): Pair<Int, ByteArray>

    fun close()
}
