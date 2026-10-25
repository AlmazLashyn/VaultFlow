package com.vaultflow.core.security

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedFileCipherTest {
    @get:Rule val temporary = TemporaryFolder()
    private val secret = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val cipher = EncryptedFileCipher(KeyProvider { secret })

    @Test fun encryptsAndRestoresBytesWithoutStoringPlaintext() {
        val input = "vault contents are private".toByteArray()
        val encrypted = File(temporary.root, "document.vlt")
        val restored = File(temporary.root, "restored.pdf")
        cipher.encrypt(ByteArrayInputStream(input), encrypted)

        assertFalse(encrypted.readBytes().contentEquals(input))
        cipher.decrypt(encrypted, restored)
        assertArrayEquals(input, restored.readBytes())
    }

    @Test fun changesToCiphertextAndAuthenticationTagAreRejected() {
        val encrypted = encryptedFile("authenticated payload".toByteArray())
        val bytes = encrypted.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        encrypted.writeBytes(bytes)
        val output = File(temporary.root, "bad-output")

        assertThrows(InvalidEncryptedFileException::class.java) { cipher.decrypt(encrypted, output) }
        assertFalse(output.exists())
    }

    @Test fun nonceIsDifferentForEachEncryption() {
        val first = encryptedFile(byteArrayOf(1)).readBytes()
        val second = encryptedFile(byteArrayOf(1)).readBytes()
        assertFalse(first.copyOfRange(5, 17).contentEquals(second.copyOfRange(5, 17)))
    }

    @Test fun emptyFilesRoundTrip() {
        val encrypted = encryptedFile(byteArrayOf())
        val restored = File(temporary.root, "empty")
        assertTrue(cipher.decrypt(encrypted, restored) == 0L)
        assertTrue(restored.readBytes().isEmpty())
    }

    @Test fun invalidNonceAndTruncationAreRejected() {
        val nonceChanged = encryptedFile("nonce test".toByteArray())
        val bytes = nonceChanged.readBytes()
        bytes[7] = (bytes[7].toInt() xor 0x40).toByte()
        nonceChanged.writeBytes(bytes)
        assertThrows(InvalidEncryptedFileException::class.java) {
            cipher.decrypt(nonceChanged, File(temporary.root, "nonce-output"))
        }

        val truncated = encryptedFile("truncated payload".toByteArray())
        truncated.writeBytes(truncated.readBytes().dropLast(4).toByteArray())
        assertThrows(InvalidEncryptedFileException::class.java) {
            cipher.decrypt(truncated, File(temporary.root, "truncated-output"))
        }
    }

    @Test fun unknownFormatVersionIsRejected() {
        val encrypted = encryptedFile(byteArrayOf(4, 5, 6))
        val bytes = encrypted.readBytes()
        bytes[4] = 99
        encrypted.writeBytes(bytes)
        assertThrows(UnsupportedEncryptedFileVersionException::class.java) {
            cipher.decrypt(encrypted, File(temporary.root, "future-format"))
        }
    }

    @Test fun oversizedInputDoesNotPublishCiphertext() {
        val limited = EncryptedFileCipher(KeyProvider { secret }, maxPlaintextBytes = 10)
        val destination = File(temporary.root, "too-large.vlt")
        assertThrows(FileTooLargeException::class.java) {
            limited.encrypt(ByteArrayInputStream(ByteArray(11)), destination)
        }
        assertFalse(destination.exists())
        assertFalse(File(temporary.root, "too-large.vlt.part").exists())
    }

    @Test fun configuredMaximumSizeRoundTripsInChunks() {
        val destination = File(temporary.root, "max.vlt")
        val restored = File(temporary.root, "max-restored")
        val length = EncryptedFileCipher.MAX_IMPORT_BYTES
        val written = cipher.encrypt(ZeroStream(length), destination)
        assertTrue(written == length)
        assertTrue(cipher.decrypt(destination, restored) == length)
        assertTrue(restored.length() == length)
    }

    private fun encryptedFile(plain: ByteArray): File = File(temporary.root, "${System.nanoTime()}.vlt").also {
        cipher.encrypt(ByteArrayInputStream(plain), it)
    }

    private class ZeroStream(private var remaining: Long) : InputStream() {
        override fun read(): Int = if (remaining <= 0) -1 else { remaining--; 0 }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return -1
            val count = minOf(length.toLong(), remaining).toInt()
            java.util.Arrays.fill(buffer, offset, offset + count, 0.toByte())
            remaining -= count
            return count
        }
    }
}
