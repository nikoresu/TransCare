package org.nikoresu.transcare.domain.model

/** Category chosen for a diary entry. */
enum class DiaryEntryType {
    NOTES,
    THOUGHTS,
    ;

    companion object {
        /** Returns the stored type, defaulting to notes for old or unknown values. */
        fun fromStorageValue(value: String?): DiaryEntryType =
            entries.firstOrNull { it.name == value } ?: NOTES
    }
}
