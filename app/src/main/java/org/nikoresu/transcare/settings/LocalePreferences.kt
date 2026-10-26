package org.nikoresu.transcare.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/** Languages the app can be displayed in. */
enum class AppLanguage(
    val storageValue: String?,
    val localeTag: String?,
) {
    SYSTEM(storageValue = null, localeTag = null),
    ENGLISH(storageValue = "en", localeTag = "en"),
    SPANISH_LATIN_AMERICA(storageValue = "es-419", localeTag = "es-419"),
    ;

    companion object {
        fun fromStorage(value: String?): AppLanguage =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

/**
 * Stores the in-app language choice and applies it to a [Context].
 *
 * The app does not use AppCompat, so the selected locale is applied by
 * wrapping the base context in [wrap] and recreating the activity when the
 * choice changes.
 */
object LocalePreferences {

    private const val PREFS_NAME = "settings"
    private const val KEY_LANGUAGE = "language"

    fun current(context: Context): AppLanguage =
        AppLanguage.fromStorage(prefs(context).getString(KEY_LANGUAGE, null))

    fun set(context: Context, language: AppLanguage) {
        prefs(context).edit {
            if (language.storageValue == null) {
                remove(KEY_LANGUAGE)
            } else {
                putString(KEY_LANGUAGE, language.storageValue)
            }
        }
    }

    /**
     * Returns [base] configured with the selected app language. With
     * [AppLanguage.SYSTEM] the context is returned unchanged.
     */
    @SuppressLint("AppBundleLocaleChanges")
    fun wrap(base: Context): Context {
        val tag = current(base).localeTag ?: return base
        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return base.createConfigurationContext(configuration)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
