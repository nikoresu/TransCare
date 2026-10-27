package org.nikoresu.transcare.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.floor

/** How a medication is taken. */
enum class MedicationRoute {
    ORAL,
    SUBLINGUAL,
    PATCH,
    GEL,
    INJECTION,
    OTHER,
    ;

    companion object {
        fun fromStorageValue(value: String?): MedicationRoute =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}

/** How often a medication is taken. */
enum class MedicationSchedule {
    DAILY,
    TWICE_DAILY,
    WEEKLY,
    AS_NEEDED,
    ;

    companion object {
        fun fromStorageValue(value: String?): MedicationSchedule =
            entries.firstOrNull { it.name == value } ?: DAILY
    }
}

/**
 * A medication the user takes, together with its schedule and an optional
 * reminder. Dose history is stored separately in [MedicationDose].
 */
data class Medication(
    val id: Long = 0L,
    val name: String = "",
    val dose: String = "",
    val route: MedicationRoute = MedicationRoute.ORAL,
    val schedule: MedicationSchedule = MedicationSchedule.DAILY,
    val startDayStartMillis: Long = 0L,
    val reminderMinutes: Int? = null,
    val supplyDoses: Int? = null,
    val notes: String = "",
) {
    /** First day this medication is taken. */
    val startDate: LocalDate
        get() = dateOf(startDayStartMillis)

    /** True when the medication has started by [date]. */
    fun isActiveOn(date: LocalDate): Boolean = !date.isBefore(startDate)

    /** Number of scheduled doses on [date]. As-needed medications return 0. */
    fun scheduledDoseIndices(date: LocalDate): List<Int> {
        if (!isActiveOn(date)) return emptyList()
        return when (schedule) {
            MedicationSchedule.DAILY -> listOf(0)
            MedicationSchedule.TWICE_DAILY -> listOf(0, 1)
            MedicationSchedule.WEEKLY -> {
                if (date.dayOfWeek == startDate.dayOfWeek) listOf(0) else emptyList()
            }

            MedicationSchedule.AS_NEEDED -> emptyList()
        }
    }

    /**
     * Reminder time for a dose slot, in minutes after midnight. Twice-daily
     * medications get two times 12 hours apart, sorted from earliest to latest.
     */
    fun doseTimeMinutes(index: Int): Int? {
        val base = reminderMinutes ?: return null
        if (schedule != MedicationSchedule.TWICE_DAILY) return base
        val second = (base + MINUTES_PER_DAY / 2) % MINUTES_PER_DAY
        return listOf(base, second).sorted().getOrNull(index)
    }

    /** Doses used per day on average, or null for as-needed medications. */
    val dailyDoseRate: Double?
        get() = when (schedule) {
            MedicationSchedule.DAILY -> 1.0
            MedicationSchedule.TWICE_DAILY -> 2.0
            MedicationSchedule.WEEKLY -> 1.0 / 7.0
            MedicationSchedule.AS_NEEDED -> null
        }

    /** Estimated full days of supply left, based on [supplyDoses]. */
    val estimatedDaysOfSupply: Int?
        get() {
            val supply = supplyDoses ?: return null
            val rate = dailyDoseRate ?: return null
            return floor(supply / rate).toInt()
        }

    /** True when the medication has a reminder configured. */
    val hasReminder: Boolean
        get() = reminderMinutes != null && schedule != MedicationSchedule.AS_NEEDED

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        private val SYSTEM_ZONE: ZoneId = ZoneId.systemDefault()

        fun dayStartMillisOf(date: LocalDate): Long =
            date.atStartOfDay(SYSTEM_ZONE).toInstant().toEpochMilli()

        fun dateOf(epochMillis: Long): LocalDate =
            Instant.ofEpochMilli(epochMillis).atZone(SYSTEM_ZONE).toLocalDate()

        fun timeOf(minutes: Int): LocalTime =
            LocalTime.of(minutes / 60, minutes % 60)
    }
}
