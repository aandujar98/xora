package com.arcadia.shell.scraper

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Random-access byte source used by disc hashing (files or SAF descriptors). */
interface RaSeekable : Closeable {
    fun size(): Long
    fun readAt(position: Long, buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int

    fun readFully(position: Long, length: Int): ByteArray {
        val out = ByteArray(length)
        var done = 0
        while (done < length) {
            val n = readAt(position + done, out, done, length - done)
            if (n <= 0) break
            done += n
        }
        return if (done == length) out else out.copyOf(done)
    }
}

class FileRaSeekable(file: File) : RaSeekable {
    private val raf = RandomAccessFile(file, "r")

    override fun size(): Long = raf.length()

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        raf.seek(position)
        return raf.read(buffer, offset, length)
    }

    override fun close() = raf.close()
}

class ChannelRaSeekable(
    private val channel: FileChannel,
    private val sizeOverride: Long? = null,
    private val onClose: () -> Unit = {},
) : RaSeekable {
    override fun size(): Long = sizeOverride ?: channel.size()

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        val buf = ByteBuffer.wrap(buffer, offset, length)
        return channel.read(buf, position)
    }

    override fun close() {
        runCatching { channel.close() }
        onClose()
    }
}
