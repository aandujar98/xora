package com.arcadia.shell.libretro

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class ThreeDsCartCrypto {
    Decrypted,
    Encrypted,
    Homebrew,
    Unknown,
}

/**
 * Azahar (standalone and libretro) will not launch encrypted carts. That is an Azahar
 * policy, not a missing XOrA key file — encrypted load was removed upstream.
 */
object ThreeDsCart {
    const val MEDIA_UNIT = 0x200
    const val NO_CRYPTO_FLAG = 0x04

    const val LOAD_ENCRYPTED_ERROR =
        "This cart is encrypted. Azahar will not boot it. Use a decrypted .cci " +
            "(a decrypted .3ds of the same CCI image also works). Encrypted 1:1 " +
            "dumps fail. Homebrew .3dsx is fine. XOrA cannot decrypt carts."

    fun inspect(path: String): ThreeDsCartCrypto = inspect(File(path))

    fun inspect(file: File): ThreeDsCartCrypto {
        if (!file.isFile || file.length() < 0x200L) return ThreeDsCartCrypto.Unknown
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(0x200)
                raf.readFully(header)
                when {
                    magicAt(header, 0) == "3DSX" -> ThreeDsCartCrypto.Homebrew
                    magicAt(header, 0x100) == "NCCH" -> cryptoFromNcch(header)
                    magicAt(header, 0x100) == "NCSD" -> inspectNcsd(raf, header, file.length())
                    else -> ThreeDsCartCrypto.Unknown
                }
            }
        }.getOrDefault(ThreeDsCartCrypto.Unknown)
    }

    fun loadFailureMessage(path: String, fallback: String): String =
        loadFailureMessage(File(path), fallback)

    fun loadFailureMessage(file: File, fallback: String): String =
        if (inspect(file) == ThreeDsCartCrypto.Encrypted) LOAD_ENCRYPTED_ERROR else fallback

    private fun inspectNcsd(
        raf: RandomAccessFile,
        header: ByteArray,
        length: Long,
    ): ThreeDsCartCrypto {
        val units = ByteBuffer.wrap(header, 0x120, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
            .toLong() and 0xFFFFFFFFL
        val candidates = buildList {
            if (units > 0L) add(units * MEDIA_UNIT)
            add(0x200L)
            add(0x4000L)
        }.distinct()
        for (offset in candidates) {
            if (offset < 0L || offset + 0x200L > length) continue
            raf.seek(offset)
            val ncch = ByteArray(0x200)
            raf.readFully(ncch)
            if (magicAt(ncch, 0x100) == "NCCH") return cryptoFromNcch(ncch)
        }
        return ThreeDsCartCrypto.Unknown
    }

    private fun cryptoFromNcch(ncch: ByteArray): ThreeDsCartCrypto {
        val flags = ncch[0x18F].toInt() and 0xFF
        return if (flags and NO_CRYPTO_FLAG != 0) {
            ThreeDsCartCrypto.Decrypted
        } else {
            ThreeDsCartCrypto.Encrypted
        }
    }

    private fun magicAt(bytes: ByteArray, offset: Int): String {
        if (offset < 0 || offset + 4 > bytes.size) return ""
        return String(bytes, offset, 4, Charsets.US_ASCII)
    }
}
