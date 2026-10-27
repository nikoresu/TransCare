package org.nikoresu.transcare.domain.model

/**
 * Hormone therapy the user is following. Only the profiles that are fully
 * supported by the app can be selected; the rest are shown as "coming soon".
 */
enum class MedicalProfile(val isAvailable: Boolean) {
    FEMINIZING_HRT(isAvailable = true),
    MASCULINIZING_HRT(isAvailable = false),
    ;

    companion object {
        /** Returns the profile stored under [value], or null when it is unknown. */
        fun fromStorage(value: String?): MedicalProfile? =
            entries.firstOrNull { it.name == value }
    }
}
