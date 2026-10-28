package org.nikoresu.transcare.security

import android.content.Context
import androidx.biometric.BiometricManager

/** Reports whether the device can currently perform a biometric unlock. */
object BiometricAvailability {

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK,
        ) == BiometricManager.BIOMETRIC_SUCCESS
}
