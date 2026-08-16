package com.arcadia.shell.libretro.netplay

/**
 * Joiner-side handshake buffer. Nakama can deliver host packets out of order, so GO
 * (and later STATE) must be kept even if ASSIGN has not arrived yet. Dropping an early
 * GO used to hang the joiner until timeout, which then left the match and reset the lobby.
 */
internal class JoinHandshakeProgress {
    var slot: Int = 0
        private set
    var host: XoraNetplayProtocol.Hello? = null
        private set
    var statePayload: ByteArray? = null
        private set
    var goPayload: ByteArray? = null
        private set

    fun onHostHello(hello: XoraNetplayProtocol.Hello) {
        host = hello
    }

    fun onAssign(slot: Int) {
        this.slot = slot
    }

    fun onState(payload: ByteArray) {
        // A STATE before our ASSIGN belongs to another joiner's barrier.
        if (slot != 0) statePayload = payload
    }

    fun onGo(payload: ByteArray) {
        goPayload = payload
    }

    fun handheldReady(): Boolean = slot != 0 && goPayload != null

    fun sharedStateReady(): Boolean = statePayload != null
}
