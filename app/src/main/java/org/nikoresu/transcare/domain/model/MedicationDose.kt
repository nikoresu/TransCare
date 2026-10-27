package org.nikoresu.transcare.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Outcome recorded for a scheduled or as-needed dose. */
enum class MedicationDoseStatus {
    TAKEN,
    SKIPPED,
    ;

    companion object {
        /** Returns null for unknown stored values so corrupt logs are skipped. */
        fun fromStorageValue(value: String?): MedicationDoseStatus? =
            entries.firstOrNull { it.name == value }
    }
}

/**
 * One logged dose. The pair ([medicationId], [dayStartMillis], [doseIndex])
 * is unique; logging the same slot again replaces the previous status.
 */
data class MedicationDose(
    val id: Long = 0L,
    val medicationId: Long = 0L,
    val dayStartMillis: Long = 0L,
    val doseIndex: Int = 0,
    val status: MedicationDoseStatus = MedicationDoseStatus.TAKEN,
    val loggedAtMillis: Long = 0L,
) {
    val date: LocalDate
        get() = Instant.ofEpochMilli(dayStartMillis).atZone(SYSTEM_ZONE).toLocalDate()

    companion object {
        private val SYSTEM_ZONE: ZoneId = ZoneId.systemDefault()

        fun dayStartMillisOf(date: LocalDate): Long =
            date.atStartOfDay(SYSTEM_ZONE).toInstant().toEpochMilli()

        fun dateOf(epochMillis: Long): LocalDate =
            Instant.ofEpochMilli(epochMillis).atZone(SYSTEM_ZONE).toLocalDate()
    }
}
