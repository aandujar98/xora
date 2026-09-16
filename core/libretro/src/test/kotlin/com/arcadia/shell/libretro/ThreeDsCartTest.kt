package com.arcadia.shell.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ThreeDsCartTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun homebrew3dsxIsNotTreatedAsEncrypted() {
        val file = tmp.newFile("homebrew.3dsx")
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0x200)
            raf.seek(0)
            raf.write("3DSX".toByteArray(Charsets.US_ASCII))
        }
        assertEquals(ThreeDsCartCrypto.Homebrew, ThreeDsCart.inspect(file))
        assertEquals("core fail", ThreeDsCart.loadFailureMessage(file, "core fail"))
    }

    @Test
    fun decryptedCxiHasNoCryptoFlag() {
        val file = tmp.newFile("game.cxi")
        writeNcch(file, offset = 0, noCrypto = true)
        assertEquals(ThreeDsCartCrypto.Decrypted, ThreeDsCart.inspect(file))
        assertEquals("core fail", ThreeDsCart.loadFailureMessage(file, "core fail"))
    }

    @Test
    fun encryptedCxiIsRejectedWithAzaharMessage() {
        val file = tmp.newFile("dump.cxi")
        writeNcch(file, offset = 0, noCrypto = false)
        assertEquals(ThreeDsCartCrypto.Encrypted, ThreeDsCart.inspect(file))
        val message = ThreeDsCart.loadFailureMessage(file, "core fail")
        assertEquals(ThreeDsCart.LOAD_ENCRYPTED_ERROR, message)
        assertTrue(message.contains("decrypted .cci"))
        assertTrue(message.contains("cannot decrypt", ignoreCase = true))
    }

    @Test
    fun ncsdPartitionUsesNoCryptoFlag() {
        val decrypted = tmp.newFile("decrypted.cci")
        writeNcsd(decrypted, partitionUnits = 2, noCrypto = true)
        assertEquals(ThreeDsCartCrypto.Decrypted, ThreeDsCart.inspect(decrypted))

        val encrypted = tmp.newFile("encrypted.3ds")
        writeNcsd(encrypted, partitionUnits = 2, noCrypto = false)
        assertEquals(ThreeDsCartCrypto.Encrypted, ThreeDsCart.inspect(encrypted))
    }

    @Test
    fun unknownBytesStayUnknown() {
        val file = tmp.newFile("notes.bin")
        file.writeBytes(ByteArray(0x200) { 0x11 })
        assertEquals(ThreeDsCartCrypto.Unknown, ThreeDsCart.inspect(file))
        assertEquals("fallback", ThreeDsCart.loadFailureMessage(file, "fallback"))
    }

    private fun writeNcch(file: File, offset: Int, noCrypto: Boolean) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength((offset + 0x200).toLong())
            raf.seek(offset + 0x100L)
            raf.write("NCCH".toByteArray(Charsets.US_ASCII))
            raf.seek(offset + 0x18FL)
            raf.write(byteArrayOf(if (noCrypto) ThreeDsCart.NO_CRYPTO_FLAG.toByte() else 0))
        }
    }

    private fun writeNcsd(file: File, partitionUnits: Int, noCrypto: Boolean) {
        val partition = partitionUnits * ThreeDsCart.MEDIA_UNIT
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength((partition + 0x200).toLong())
            raf.seek(0x100)
            raf.write("NCSD".toByteArray(Charsets.US_ASCII))
            raf.seek(0x120)
            raf.write(
                ByteBuffer.allocate(4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(partitionUnits)
                    .array(),
            )
        }
        writeNcch(file, offset = partition, noCrypto = noCrypto)
    }
}
