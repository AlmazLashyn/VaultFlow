package com.vaultflow.core.security

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Vault file format v1: ASCII "VFLT", one version byte, a 12-byte GCM nonce,
 * then AES-GCM ciphertext and its 16-byte authentication tag. The complete header
 * is supplied as AAD. Plaintext is capped at 50 MiB and written only to a private
 * temporary file; decryption publishes the output only after doFinal authenticates it.
 */
class EncryptedFileCipher(
    private val keyProvider: KeyProvider,
    private val maxPlaintextBytes: Long = MAX_IMPORT_BYTES,
) {
    fun encrypt(input: InputStream, destination: File, onProgress: (Long) -> Unit = {}): Long {
        val part = File(destination.parentFile, "${destination.name}.part")
        part.parentFile?.mkdirs()
        var count = 0L
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreateKey())
            val nonce = cipher.iv
            require(nonce.size == NONCE_BYTES) { "Unexpected GCM nonce length" }
            val header = MAGIC + byteArrayOf(FORMAT_VERSION.toByte()) + nonce
            cipher.updateAAD(header)

            FileOutputStream(part).use { output ->
                output.write(header)
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    count += read
                    if (count > maxPlaintextBytes) throw FileTooLargeException()
                    cipher.update(buffer, 0, read)?.let(output::write)
                    onProgress(count)
                }
                cipher.doFinal().let(output::write)
                output.fd.sync()
            }
            publish(part, destination)
            return count
        } catch (error: Throwable) {
            part.delete()
            if (error is FileTooLargeException) throw error
            if (error is GeneralSecurityException) throw CryptoOperationException(error)
            throw error
        }
    }

    fun decrypt(source: File, destination: File): Long {
        val part = File(destination.parentFile, "${destination.name}.part")
        part.parentFile?.mkdirs()
        var count = 0L
        try {
            FileInputStream(source).use { input ->
                val header = ByteArray(HEADER_BYTES)
                readFully(input, header)
                if (!header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                    throw InvalidEncryptedFileException()
                }
                val version = header[MAGIC.size].toInt() and 0xff
                if (version != FORMAT_VERSION) throw UnsupportedEncryptedFileVersionException()
                val nonce = header.copyOfRange(MAGIC.size + 1, HEADER_BYTES)
                if (nonce.size != NONCE_BYTES) throw InvalidEncryptedFileException()

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, keyProvider.getOrCreateKey(), GCMParameterSpec(TAG_BITS, nonce))
                cipher.updateAAD(header)
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        val plain = cipher.update(buffer, 0, read)
                        if (plain != null) {
                            count += plain.size
                            if (count > maxPlaintextBytes) throw FileTooLargeException()
                            output.write(plain)
                        }
                    }
                    val final = cipher.doFinal()
                    count += final.size
                    if (count > maxPlaintextBytes) throw FileTooLargeException()
                    output.write(final)
                    output.fd.sync()
                }
            }
            publish(part, destination)
            return count
        } catch (error: Throwable) {
            part.delete()
            if (error is GeneralSecurityException) throw InvalidEncryptedFileException(error)
            throw error
        }
    }

    private fun publish(part: File, destination: File) {
        if (destination.exists() && !destination.delete()) throw java.io.IOException("Cannot replace output file")
        if (!part.renameTo(destination)) throw java.io.IOException("Cannot finalize output file")
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw InvalidEncryptedFileException()
            offset += count
        }
    }

    companion object {
        const val MAX_IMPORT_BYTES = 50L * 1024L * 1024L
        private const val BUFFER_BYTES = 64 * 1024
        private const val TAG_BITS = 128
        private const val NONCE_BYTES = 12
        private const val FORMAT_VERSION = 1
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val MAGIC = byteArrayOf('V'.code.toByte(), 'F'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
        private val HEADER_BYTES = MAGIC.size + 1 + NONCE_BYTES
    }
}

class FileTooLargeException : Exception()
class InvalidEncryptedFileException(cause: Throwable? = null) : Exception(cause)
class UnsupportedEncryptedFileVersionException : Exception()
class CryptoOperationException(cause: Throwable) : Exception(cause)
