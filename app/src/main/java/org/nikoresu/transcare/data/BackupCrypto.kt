package org.nikoresu.transcare.data

import java.security.SecureRandom
import java.time.Instant
import java.util.Arrays
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
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
    private const val APP_NAME = "TransCare"
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 1_000_000
    /** Higher than the app-lock hash because a stolen file can be attacked offline. */
    private const val DEFAULT_ITERATIONS = 600_000

    class WrongPassphraseException : Exception("The backup passphrase is incorrect")

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
