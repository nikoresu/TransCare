package org.nikoresu.transcare.settings

import android.content.Context
import androidx.core.content.edit
import java.io.File

/**
 * Remembers whether the first-run onboarding has been completed.
 *
 * Installs created before onboarding existed already contain user data but no
 * flag. In that case the flag is written silently so those users go straight to
 * the main screen instead of seeing the welcome flow again.
 */
object OnboardingPreferences {

    private const val PREFS_NAME = "settings"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

    /** Data files written by the app before onboarding was introduced. */
    private val DATA_FILE_NAMES = listOf(
        "profile.json",
        "appointments.json",
        "exam_results.json",
        "diary_entries.json",
        "medications.json",
        "medication_doses.json",
    )

    fun isCompleted(context: Context): Boolean {
        if (prefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)) return true
        if (!hasExistingData(context)) return false
        markCompleted(context)
        return true
    }

    fun markCompleted(context: Context) {
        prefs(context).edit { putBoolean(KEY_ONBOARDING_COMPLETED, true) }
    }

    private fun hasExistingData(context: Context): Boolean =
        DATA_FILE_NAMES.any { name ->
            val file = File(context.filesDir, name)
            file.isFile && file.length() > 0L
        }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
