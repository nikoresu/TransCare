package org.nikoresu.transcare.domain.model

import java.util.Locale

/**
 * Basic information shown in the Profile tab. The photo itself is stored as a
 * file inside the app's internal storage; only its file name is persisted here.
 */
data class Profile(
    val name: String = "",
    val email: String = "",
    val photoFileName: String? = null,
    val age: Int? = null,
    val medicalProfile: MedicalProfile? = null,
) {
    /** One or two letters used when no profile photo has been chosen. */
    val initials: String
        get() {
            val parts = name.trim().split(WHITESPACE).filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts.first().take(1).uppercase(Locale.getDefault())
                else -> (parts.first().take(1) + parts.last().take(1))
                    .uppercase(Locale.getDefault())
            }
        }

    /** Trims user input and drops invalid or unsupported values. */
    fun normalized(): Profile = copy(
        name = name.trim(),
        email = email.trim(),
        photoFileName = photoFileName?.takeIf { isValidPhotoFileName(it) },
        age = age?.takeIf { it in MIN_AGE..MAX_AGE },
        medicalProfile = medicalProfile?.takeIf { it.isAvailable },
    )

    companion object {
        const val MIN_AGE = 13
        const val MAX_AGE = 120

        private val WHITESPACE = Regex("\\s+")

        /** Only plain file names are accepted so a profile can never point outside its folder. */
        private val PHOTO_FILE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

        fun isValidPhotoFileName(fileName: String?): Boolean =
            fileName != null && PHOTO_FILE_NAME.matches(fileName)

        /** A null age means the user did not provide one, which is allowed. */
        fun isValidAge(age: Int?): Boolean = age == null || age in MIN_AGE..MAX_AGE
    }
}
