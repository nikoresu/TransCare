package org.nikoresu.transcare.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashes and verifies the app PIN with PBKDF2-HMAC-SHA256.
 *
 * The PIN itself is never stored. Only a random salt and the derived hash are
 * kept, so a copy of the settings file does not reveal the PIN.
 */
object PinHasher {

    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8

    const val DEFAULT_ITERATIONS = 210_000

    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    data class PinHash(
        val hash: ByteArray,
        val salt: ByteArray,
        val iterations: Int,
    )

    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(
        pin: String,
        salt: ByteArray = newSalt(),
        iterations: Int = DEFAULT_ITERATIONS,
    ): PinHash {
        require(isValidPin(pin)) { "PIN must contain 4 to 8 digits" }
        return PinHash(
            hash = derive(pin, salt, iterations, KEY_BITS),
            salt = salt,
            iterations = iterations,
        )
    }

    fun verify(
        pin: String,
        salt: ByteArray,
        expectedHash: ByteArray,
        iterations: Int,
    ): Boolean {
        if (salt.isEmpty() || expectedHash.isEmpty() || iterations <= 0) return false
        val actual = derive(pin, salt, iterations, expectedHash.size * 8)
        return try {
            MessageDigest.isEqual(actual, expectedHash)
        } finally {
            Arrays.fill(actual, 0)
        }
    }

    private fun derive(
        pin: String,
        salt: ByteArray,
        iterations: Int,
        keyBits: Int,
    ): ByteArray {
        val password = pin.toCharArray()
        val spec = PBEKeySpec(password, salt, iterations, keyBits)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM)
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
            Arrays.fill(password, '\u0000')
        }
    }
}
