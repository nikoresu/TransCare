package org.nikoresu.transcare

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nikoresu.transcare.security.BiometricAvailability
import org.nikoresu.transcare.security.SecurityPreferences
import org.nikoresu.transcare.settings.LocalePreferences
import org.nikoresu.transcare.settings.OnboardingPreferences
import org.nikoresu.transcare.ui.MainScreen
import org.nikoresu.transcare.ui.appointments.AppointmentsViewModel
import org.nikoresu.transcare.ui.diary.DiaryViewModel
import org.nikoresu.transcare.ui.exams.ExamsViewModel
import org.nikoresu.transcare.ui.medications.MedicationsViewModel
import org.nikoresu.transcare.ui.onboarding.OnboardingScreen
import org.nikoresu.transcare.ui.profile.ProfileViewModel
import org.nikoresu.transcare.ui.security.LockScreen
import org.nikoresu.transcare.ui.theme.TranscareTheme

class MainActivity : FragmentActivity() {

    private var locked by mutableStateOf(false)
    private var biometricError by mutableStateOf<String?>(null)
    private var lastStoppedAt = 0L
    private var skipLockOnNextCreate = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val lockEnabled = SecurityPreferences.isLockEnabled(this)
        locked = lockEnabled && !skipLockOnNextCreate
        skipLockOnNextCreate = false
        if (!locked) biometricError = null

        // Check before creating the view models, because their repositories
        // load the data used to detect an install that predates onboarding.
        val startOnboarding = !OnboardingPreferences.isCompleted(this)

        val appointmentsViewModel = ViewModelProvider(this)[AppointmentsViewModel::class.java]
        val examsViewModel = ViewModelProvider(this)[ExamsViewModel::class.java]
        val diaryViewModel = ViewModelProvider(this)[DiaryViewModel::class.java]
        val medicationsViewModel = ViewModelProvider(this)[MedicationsViewModel::class.java]
        val profileViewModel = ViewModelProvider(this)[ProfileViewModel::class.java]

        setContent {
            TranscareTheme {
                var showOnboarding by remember { mutableStateOf(startOnboarding) }

                if (locked) {
                    LockScreen(
                        biometricEnabled = SecurityPreferences.isBiometricEnabled(this),
                        biometricAvailable = BiometricAvailability.isAvailable(this),
                        externalError = biometricError,
                        onUnlockWithPin = { pin ->
                            val unlocked = withContext(Dispatchers.Default) {
                                SecurityPreferences.verifyPin(this@MainActivity, pin)
                            }
                            if (unlocked) {
                                locked = false
                                biometricError = null
                            }
                            unlocked
                        },
                        onUnlockWithBiometrics = { showBiometricPrompt() },
                        onErrorConsumed = { biometricError = null },
                    )
                } else if (showOnboarding) {
                    OnboardingScreen(
                        viewModel = profileViewModel,
                        onCompleted = {
                            OnboardingPreferences.markCompleted(this)
                            showOnboarding = false
                        },
                    )
                } else {
                    MainScreen(
                        appointmentsViewModel = appointmentsViewModel,
                        examsViewModel = examsViewModel,
                        diaryViewModel = diaryViewModel,
                        medicationsViewModel = medicationsViewModel,
                        profileViewModel = profileViewModel,
                        onLanguageChanged = {
                            skipLockOnNextCreate = true
                            recreate()
                        },
                        onLockEnabledChanged = { enabled ->
                            locked = enabled
                            biometricError = null
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (lastStoppedAt <= 0L) return
        val elapsed = SystemClock.elapsedRealtime() - lastStoppedAt
        lastStoppedAt = 0L
        if (elapsed >= LOCK_GRACE_MS && SecurityPreferences.isLockEnabled(this)) {
            locked = true
            biometricError = null
        }
    }

    override fun onStop() {
        super.onStop()
        if (SecurityPreferences.isLockEnabled(this)) {
            lastStoppedAt = SystemClock.elapsedRealtime()
        }
    }

    private fun showBiometricPrompt() {
        val canAuthenticate = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK,
        )
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            biometricError = getString(R.string.security_lock_biometric_unavailable)
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    locked = false
                    biometricError = null
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        return
                    }
                    biometricError = getString(R.string.security_lock_biometric_failed)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.security_lock_biometric_title))
            .setSubtitle(getString(R.string.security_lock_biometric_subtitle))
            .setNegativeButtonText(getString(R.string.security_use_pin))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }

    private companion object {
        /** Give short trips to the photo or file picker a grace period. */
        const val LOCK_GRACE_MS = 30_000L
    }
}
