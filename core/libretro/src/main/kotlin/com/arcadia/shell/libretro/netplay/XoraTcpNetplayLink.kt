package com.arcadia.shell.libretro.netplay

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

internal class XoraTcpNetplayLink(
    private val socket: Socket,
) : XoraNetplayLink {
    private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
    private val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
    private val writeLock = Any()

    override fun send(type: Int, payload: ByteArray) {
        synchronized(writeLock) {
            XoraNetplayProtocol.writeMessage(output, type, payload)
        }
    }

    override fun receive(timeoutMs: Int): Pair<Int, ByteArray> {
        val previous = socket.soTimeout
        socket.soTimeout = timeoutMs.coerceAtLeast(0)
        try {
            return XoraNetplayProtocol.readMessage(input)
        } finally {
            runCatching { socket.soTimeout = previous }
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }
}
