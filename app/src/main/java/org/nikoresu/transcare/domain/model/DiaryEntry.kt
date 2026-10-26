package org.nikoresu.transcare.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A free-form diary entry written by the user. Entries are stored locally and
 * linked to a calendar day, but several entries can share the same day.
 */
data class DiaryEntry(
    val id: Long = 0L,
    val dayStartMillis: Long = 0L,
    val title: String = "",
    val text: String = "",
    val type: DiaryEntryType = DiaryEntryType.NOTES,
) {
    /** Calendar day the entry belongs to. */
    val date: LocalDate
        get() = dateOf(dayStartMillis)

    /** True when there is nothing to show or save. */
    val isBlank: Boolean
        get() = title.isBlank() && text.isBlank()

    companion object {
        private val SYSTEM_ZONE: ZoneId = ZoneId.systemDefault()

        fun dayStartMillisOf(date: LocalDate): Long =
            date.atStartOfDay(SYSTEM_ZONE).toInstant().toEpochMilli()

        fun dateOf(epochMillis: Long): LocalDate =
            Instant.ofEpochMilli(epochMillis).atZone(SYSTEM_ZONE).toLocalDate()
    }
}
