package org.nikoresu.transcare.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the app's local JSON files with a non-exportable AES-256-GCM key
 * held by the Android Keystore.
 *
 * Files written by older versions are plain JSON. [readText] still accepts
 * them and [migrateIfNeeded] replaces them with encrypted data on startup, so
 * existing installs keep all their information.
 *
 * The key does not require user authentication, because reminder receivers and
 * background work must be able to read the data while the device is locked.
 * Access to the UI is protected separately by the app lock.
 */
object LocalDataCipher {

    private const val TAG = "LocalDataCipher"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "transcare_local_data_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val MAGIC = "TCENC1"
    private const val VERSION = 1
    private const val TAG_BITS = 128
    private const val MIN_IV_BYTES = 12
    private const val MAX_IV_BYTES = 16
    private const val BUFFER_BYTES = 64 * 1024

    private val magicBytes = MAGIC.toByteArray(Charsets.US_ASCII)
    private val keyLock = Any()

    @Volatile
    private var cachedKey: SecretKey? = null

    /** Reads an encrypted or legacy plain file. Returns null when it is absent. */
    fun readText(file: File): String? =
        readBytes(file)?.let { String(it, Charsets.UTF_8) }

    /** Reads an encrypted or legacy plain file as bytes. */
    fun readBytes(file: File): ByteArray? {
        if (!file.isFile) return null
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return ByteArray(0)
        return if (isEncrypted(bytes)) decrypt(bytes) else bytes
    }

    /**
     * Opens [file] for streaming reads, decrypting on the fly when it uses the
     * current or legacy encrypted frame. Plain files from older versions are
     * returned unchanged. The caller owns the returned stream and must close
     * it; the GCM tag is verified when the end of the stream is reached.
     */
    fun openInputStream(file: File): InputStream? {
        if (!file.isFile) return null
        val input = FileInputStream(file)
        try {
            val prefix = ByteArray(magicBytes.size + 1)
            var read = 0
            while (read < prefix.size) {
                val count = input.read(prefix, read, prefix.size - read)
                if (count < 0) break
                read += count
            }
            if (read <= magicBytes.size || !matchesMagic(prefix)) {
                input.channel.position(0)
                return input
            }

            val versionOrIvLength = prefix[magicBytes.size].toInt() and 0xFF
            val legacy = versionOrIvLength != VERSION
            val ivSize: Int
            val ivStart: Int
            if (legacy) {
                ivSize = versionOrIvLength
                ivStart = magicBytes.size + 1
            } else {
                val ivLengthByte = input.read()
                if (ivLengthByte < 0) throw IOException("Truncated local data file")
                ivSize = ivLengthByte
                ivStart = magicBytes.size + 2
            }
            if (ivSize !in MIN_IV_BYTES..MAX_IV_BYTES) {
                throw IOException("Invalid local data IV")
            }

            input.channel.position(ivStart.toLong())
            val iv = ByteArray(ivSize)
            var ivRead = 0
            while (ivRead < ivSize) {
                val count = input.read(iv, ivRead, ivSize - ivRead)
                if (count < 0) throw IOException("Truncated local data file")
                ivRead += count
            }
            input.channel.position((ivStart + ivSize).toLong())

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(TAG_BITS, iv),
            )
            return CipherInputStream(input, cipher)
        } catch (exception: Exception) {
            input.close()
            throw exception
        }
    }

    private fun matchesMagic(bytes: ByteArray): Boolean {
        if (bytes.size < magicBytes.size) return false
        for (index in magicBytes.indices) {
            if (bytes[index] != magicBytes[index]) return false
        }
        return true
    }

    /** Writes [text] encrypted, replacing the file atomically. */
    fun writeText(file: File, text: String) {
        writeBytes(file, text.toByteArray(Charsets.UTF_8))
    }

    /** Writes [plaintext] encrypted, replacing the file atomically. */
    fun writeBytes(file: File, plaintext: ByteArray) {
        writeRaw(file, encrypt(plaintext))
    }

    /** Encrypts a plain file, or upgrades an old frame, in place. */
    fun migrateIfNeeded(file: File) {
        if (!file.isFile || file.length() == 0L) return
        try {
            val bytes = file.readBytes()
            if (isEncrypted(bytes)) {
                // Files written before the version byte was added still read
                // fine, but rewriting them keeps the on-disk format current.
                if (isLegacyFrame(bytes)) writeBytes(file, decrypt(bytes))
                return
            }
            writeBytes(file, bytes)
        } catch (exception: Exception) {
            Log.e(TAG, "Could not encrypt ${file.name}", exception)
        }
    }

    private fun writeRaw(file: File, encrypted: ByteArray) {
        writeAtomically(file) { output -> output.write(encrypted) }
    }

    /**
     * Encrypts the bytes read from [input] straight into [file], so a large
     * attachment can be restored from a backup without loading it fully into
     * memory. The file is replaced atomically once the whole stream is written.
     */
    fun writeBytes(file: File, input: InputStream) {
        writeAtomically(file) { output ->
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            require(iv.size in MIN_IV_BYTES..MAX_IV_BYTES) { "Unexpected GCM IV size" }

            val header = ByteArray(magicBytes.size + 2 + iv.size)
            System.arraycopy(magicBytes, 0, header, 0, magicBytes.size)
            header[magicBytes.size] = VERSION.toByte()
            header[magicBytes.size + 1] = iv.size.toByte()
            System.arraycopy(iv, 0, header, magicBytes.size + 2, iv.size)
            output.write(header)

            // Closing the cipher appends the GCM tag but must not close the
            // file, because the caller still flushes and syncs it.
            CipherOutputStream(NonClosingOutputStream(output), cipher).use { stream ->
                input.copyTo(stream, BUFFER_BYTES)
            }
        }
    }

    private fun writeAtomically(file: File, write: (FileOutputStream) -> Unit) {
        val parent = file.absoluteFile.parentFile
            ?: throw IOException("Could not resolve the data folder")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Could not create the data folder")
        }

        val temporary = File(parent, "${file.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                write(output)
                output.flush()
                output.fd.sync()
            }
            if (!temporary.renameTo(file)) {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (exception: Exception) {
            temporary.delete()
            throw exception
        }
    }

    /** Leaves the wrapped file stream open for the final flush and sync. */
    private class NonClosingOutputStream(
        private val delegate: OutputStream,
    ) : OutputStream() {
        override fun write(b: Int) = delegate.write(b)

        override fun write(b: ByteArray, off: Int, len: Int) =
            delegate.write(b, off, len)

        override fun flush() = delegate.flush()

        override fun close() = flush()
    }

    internal fun isEncrypted(bytes: ByteArray): Boolean {
        // The magic alone is not a frame: at least the version or IV length
        // byte must follow it.
        if (bytes.size <= magicBytes.size) return false
        for (index in magicBytes.indices) {
            if (bytes[index] != magicBytes[index]) return false
        }
        return true
    }

    /**
     * True for the old frame that stored the IV length where the version byte
     * now lives. A GCM IV is 12 to 16 bytes, so it can never be mistaken for
     * [VERSION].
     */
    internal fun isLegacyFrame(bytes: ByteArray): Boolean {
        if (!isEncrypted(bytes) || bytes.size <= magicBytes.size) return false
        return (bytes[magicBytes.size].toInt() and 0xFF) != VERSION
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        require(iv.size in MIN_IV_BYTES..MAX_IV_BYTES) { "Unexpected GCM IV size" }
        val ciphertext = cipher.doFinal(plaintext)

        val result = ByteArray(magicBytes.size + 2 + iv.size + ciphertext.size)
        System.arraycopy(magicBytes, 0, result, 0, magicBytes.size)
        result[magicBytes.size] = VERSION.toByte()
        result[magicBytes.size + 1] = iv.size.toByte()
        System.arraycopy(iv, 0, result, magicBytes.size + 2, iv.size)
        System.arraycopy(
            ciphertext,
            0,
            result,
            magicBytes.size + 2 + iv.size,
            ciphertext.size,
        )
        return result
    }

    private fun decrypt(data: ByteArray): ByteArray {
        if (!isEncrypted(data)) throw IOException("Not an encrypted local data file")
        val ivSize: Int
        val ivStart: Int
        if (isLegacyFrame(data)) {
            // The first internal build wrote magic + IV length + IV.
            ivSize = data[magicBytes.size].toInt() and 0xFF
            ivStart = magicBytes.size + 1
        } else {
            if (data.size < magicBytes.size + 3) {
                throw IOException("Not an encrypted local data file")
            }
            val version = data[magicBytes.size].toInt() and 0xFF
            if (version != VERSION) throw IOException("Unsupported local data version")
            ivSize = data[magicBytes.size + 1].toInt() and 0xFF
            ivStart = magicBytes.size + 2
        }
        if (ivSize !in MIN_IV_BYTES..MAX_IV_BYTES) throw IOException("Invalid local data IV")
        val ciphertextStart = ivStart + ivSize
        if (data.size <= ciphertextStart) throw IOException("Missing local data ciphertext")

        val iv = data.copyOfRange(ivStart, ciphertextStart)
        val ciphertext = data.copyOfRange(ciphertextStart, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(TAG_BITS, iv),
        )
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }
        synchronized(keyLock) {
            cachedKey?.let { return it }
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val existing = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
                ?.secretKey
            val key = existing ?: generateKey()
            cachedKey = key
            return key
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
