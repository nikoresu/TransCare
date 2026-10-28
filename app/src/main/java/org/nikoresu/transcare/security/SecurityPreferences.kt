package org.nikoresu.transcare.security

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import java.util.Base64

/**
 * Stores the app-lock configuration.
 *
 * Only a salted PBKDF2 hash of the PIN is persisted. The PIN is also the
 * passphrase used to encrypt portable backups, so it is never written to disk
 * in readable form.
 */
object SecurityPreferences {

    private const val TAG = "SecurityPreferences"
    private const val PREFS_NAME = "security"
    private const val KEY_LOCK_ENABLED = "lock_enabled"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PIN_ITERATIONS = "pin_iterations"

    fun isLockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCK_ENABLED, false) && hasPin(context)

    fun isBiometricEnabled(context: Context): Boolean =
        isLockEnabled(context) && prefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun hasPin(context: Context): Boolean {
        val prefs = prefs(context)
        return prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_PIN_SALT)
    }

    /** Stores a new PIN. Returns false when the PIN is not 4 to 8 digits. */
    fun setPin(context: Context, pin: String): Boolean {
        if (!PinHasher.isValidPin(pin)) return false
        return try {
            val hashed = PinHasher.hash(pin)
            prefs(context).edit {
                putString(KEY_PIN_HASH, encode(hashed.hash))
                putString(KEY_PIN_SALT, encode(hashed.salt))
                putInt(KEY_PIN_ITERATIONS, hashed.iterations)
            }
            true
        } catch (exception: Exception) {
            Log.e(TAG, "Could not store the app PIN", exception)
            false
        }
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        if (!PinHasher.isValidPin(pin)) return false
        val prefs = prefs(context)
        val hash = decode(prefs.getString(KEY_PIN_HASH, null)) ?: return false
        val salt = decode(prefs.getString(KEY_PIN_SALT, null)) ?: return false
        val iterations = prefs.getInt(KEY_PIN_ITERATIONS, PinHasher.DEFAULT_ITERATIONS)
        return try {
            PinHasher.verify(pin, salt, hash, iterations)
        } catch (exception: Exception) {
            Log.e(TAG, "Could not verify the app PIN", exception)
            false
        }
    }

    fun setLockEnabled(context: Context, enabled: Boolean) {
        if (enabled && !hasPin(context)) return
        prefs(context).edit { putBoolean(KEY_LOCK_ENABLED, enabled) }
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        if (enabled && !isLockEnabled(context)) return
        prefs(context).edit { putBoolean(KEY_BIOMETRIC_ENABLED, enabled) }
    }

    /** Removes the PIN and all lock settings. */
    fun disableLock(context: Context) {
        prefs(context).edit {
            remove(KEY_LOCK_ENABLED)
            remove(KEY_BIOMETRIC_ENABLED)
            remove(KEY_PIN_HASH)
            remove(KEY_PIN_SALT)
            remove(KEY_PIN_ITERATIONS)
        }
    }

    private fun encode(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String?): ByteArray? {
        if (value.isNullOrBlank()) return null
        return try {
            Base64.getDecoder().decode(value)
        } catch (exception: IllegalArgumentException) {
            Log.e(TAG, "Could not decode a stored PIN value", exception)
            null
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
