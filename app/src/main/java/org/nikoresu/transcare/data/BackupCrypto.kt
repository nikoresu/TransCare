package org.nikoresu.transcare.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import java.util.Arrays
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Encrypts backup documents with a passphrase.
 *
 * The passphrase is the user's app PIN (or a standalone backup passphrase when
 * no app lock is configured). A random salt is used for every export, so two
 * backups made with the same PIN produce different files.
 *
 * The resulting file is a small JSON envelope. The original plain backup is
 * kept inside it as AES-256-GCM ciphertext, which also authenticates the data.
 *
 * An early internal build wrote the same envelope with the format name
 * "encrypted-backup" and no explicit cipher field. Those files are still
 * accepted so an upgrade does not strand an existing backup.
 */
internal object BackupCrypto {

    const val FORMAT = "transcare-encrypted-backup"
    const val VERSION = 1

    private const val LEGACY_FORMAT = "encrypted-backup"
    private const val BINARY_MAGIC = "TCBAK1"
    private const val BINARY_VERSION = 1
    private const val APP_NAME = "TransCare"
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val SALT_BYTES = 16
    /** Magic, version, iterations, salt length, IV length and a little slack. */
    private const val HEADER_RESERVE_BYTES = 64
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 1_000_000
    /** Higher than the app-lock hash because a stolen file can be attacked offline. */
    private const val DEFAULT_ITERATIONS = 600_000

    class WrongPassphraseException : Exception("The backup passphrase is incorrect")

    private val binaryMagicBytes = BINARY_MAGIC.toByteArray(Charsets.US_ASCII)

    /** True when [bytes] start with the binary envelope used for ZIP backups. */
    fun isBinaryEnvelope(bytes: ByteArray): Boolean {
        if (bytes.size <= binaryMagicBytes.size + 1) return false
        return binaryMagicBytes.indices.all { bytes[it] == binaryMagicBytes[it] }
    }

    fun isEncryptedBackup(text: String): Boolean = try {
        val root = JSONTokener(text).nextValue()
        root is JSONObject && isSupportedFormat(root.optString("format"))
    } catch (_: Exception) {
        false
    }

    private fun isSupportedFormat(format: String): Boolean =
        format == FORMAT || format == LEGACY_FORMAT

    fun encrypt(
        plaintext: String,
        passphrase: CharArray,
        exportedAt: Instant = Instant.now(),
        iterations: Int = DEFAULT_ITERATIONS,
    ): String {
        require(passphrase.isNotEmpty()) { "A passphrase is required" }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Invalid iteration count" }

        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt, iterations)
        return try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            JSONObject()
                .put("app", APP_NAME)
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("kdf", KDF)
                .put("iterations", iterations)
                .put("cipher", CIPHER)
                .put("salt", encode(salt))
                .put("iv", encode(iv))
                .put("ciphertext", encode(ciphertext))
                .put("exportedAt", exportedAt.toString())
                .toString(2)
        } finally {
            Arrays.fill(key, 0)
        }
    }

    fun decrypt(envelopeText: String, passphrase: CharArray): String {
        require(passphrase.isNotEmpty()) { "A passphrase is required" }
        val root = JSONObject(envelopeText)
        require(isSupportedFormat(root.optString("format"))) {
            "Not an encrypted TransCare backup"
        }
        require(root.optInt("version", -1) == VERSION) { "Unsupported backup version" }
        require(root.optString("kdf") == KDF) { "Unsupported key derivation function" }
        require(root.optString("cipher", CIPHER) == CIPHER) { "Unsupported encryption algorithm" }

        val iterations = root.optInt("iterations", -1)
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Invalid iteration count" }

        val salt = decode(root.getString("salt"))
        val iv = decode(root.getString("iv"))
        val ciphertext = decode(root.getString("ciphertext"))
        require(salt.size >= 8) { "Invalid salt" }
        require(iv.size in 12..16) { "Invalid IV" }
        require(ciphertext.isNotEmpty()) { "Missing ciphertext" }

        val key = deriveKey(passphrase, salt, iterations)
        return try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            throw WrongPassphraseException()
        } finally {
            Arrays.fill(key, 0)
        }
    }

    /**
     * Encrypts arbitrary bytes (a ZIP backup) into a compact binary envelope.
     * The binary form avoids the base64 overhead of the JSON envelope, which
     * matters once photos and PDFs are included.
     */
    fun encryptBytes(
        plaintext: ByteArray,
        passphrase: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
    ): ByteArray = ByteArrayOutputStream(plaintext.size + HEADER_RESERVE_BYTES).use { output ->
        encryptingStream(output, passphrase, iterations).use { stream ->
            stream.write(plaintext)
        }
        output.toByteArray()
    }

    /**
     * Writes the binary envelope header to [sink] and returns a stream that
     * encrypts everything written to it. Closing the returned stream appends
     * the GCM tag, so the backup is authenticated as a whole while only a
     * small buffer is kept in memory.
     */
    fun encryptingStream(
        sink: OutputStream,
        passphrase: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
    ): OutputStream {
        require(passphrase.isNotEmpty()) { "A passphrase is required" }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Invalid iteration count" }

        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt, iterations)
        try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            val iv = cipher.iv
            require(iv.size in 12..16) { "Unexpected GCM IV size" }

            val header = ByteBuffer.allocate(
                binaryMagicBytes.size + 1 + 4 + 1 + salt.size + 1 + iv.size,
            )
                .put(binaryMagicBytes)
                .put(BINARY_VERSION.toByte())
                .putInt(iterations)
                .put(salt.size.toByte())
                .put(salt)
                .put(iv.size.toByte())
                .put(iv)
                .array()
            sink.write(header)
            return CipherOutputStream(sink, cipher)
        } finally {
            Arrays.fill(key, 0)
        }
    }

    /** Decrypts a binary envelope produced by [encryptBytes]. */
    fun decryptBytes(envelope: ByteArray, passphrase: CharArray): ByteArray =
        ByteArrayInputStream(envelope).use { input ->
            decryptingStream(input, passphrase).use { it.readBytes() }
        }

    /**
     * Reads the binary envelope header from [input] and returns a stream that
     * decrypts the rest on the fly. Only the header is buffered, so a backup
     * of any size can be read with a small, constant amount of memory.
     *
     * The GCM tag is checked when the end of the returned stream is reached.
     * A wrong passphrase therefore surfaces as [WrongPassphraseException] from
     * the final read (or close), not from this call.
     */
    fun decryptingStream(input: InputStream, passphrase: CharArray): InputStream {
        require(passphrase.isNotEmpty()) { "A passphrase is required" }

        val magic = ByteArray(binaryMagicBytes.size)
        input.readFully(magic)
        require(magic.contentEquals(binaryMagicBytes)) { "Not an encrypted TransCare backup" }
        val version = input.read()
        require(version == BINARY_VERSION) { "Unsupported backup version" }

        val iterations = input.readInt()
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Invalid iteration count" }

        val saltLength = input.readByteValue()
        require(saltLength in 8..64) { "Invalid salt" }
        val salt = ByteArray(saltLength)
        input.readFully(salt)

        val ivLength = input.readByteValue()
        require(ivLength in 12..16) { "Invalid IV" }
        val iv = ByteArray(ivLength)
        input.readFully(iv)

        val key = deriveKey(passphrase, salt, iterations)
        return try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            AuthenticatedInputStream(CipherInputStream(input, cipher))
        } finally {
            Arrays.fill(key, 0)
        }
    }

    /**
     * Turns the tag failure that [CipherInputStream] reports at the end of the
     * stream into the same exception the in-memory path throws, so the UI can
     * tell "wrong passphrase" apart from a corrupt file.
     */
    private class AuthenticatedInputStream(
        input: InputStream,
    ) : FilterInputStream(input) {

        override fun read(): Int = translate { super.read() }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            translate { super.read(buffer, offset, length) }

        override fun close() = translate { super.close() }

        private inline fun <T> translate(block: () -> T): T = try {
            block()
        } catch (exception: IOException) {
            if (exception.cause is AEADBadTagException) {
                throw WrongPassphraseException()
            }
            throw exception
        }
    }

    private fun InputStream.readFully(target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val count = read(target, offset, target.size - offset)
            if (count < 0) throw IOException("Truncated backup")
            offset += count
        }
    }

    private fun InputStream.readByteValue(): Int {
        val value = read()
        if (value < 0) throw IOException("Truncated backup")
        return value
    }

    private fun InputStream.readInt(): Int {
        val bytes = ByteArray(4)
        readFully(bytes)
        return ByteBuffer.wrap(bytes).int
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(KDF)
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String): ByteArray =
        Base64.getDecoder().decode(value)
}
