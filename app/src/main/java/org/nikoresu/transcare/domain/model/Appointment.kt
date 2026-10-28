package org.nikoresu.transcare.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

data class Appointment(
    val id: Long = 0L,
    val doctorName: String = "",
    val place: String = "",
    /** Questions to ask or things to bring to the appointment. */
    val notes: String = "",
    val dayStartMillis: Long = 0L,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = DEFAULT_MINUTE,
    /** Photos or PDFs linked to this appointment. */
    val attachments: List<Attachment> = emptyList(),
) {
    /** Epoch millis of the appointment start date and time. */
    val startMillis: Long
        get() = LocalDateTime.of(
            date,
            LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)),
        ).atZone(Appointment.SYSTEM_ZONE).toInstant().toEpochMilli()

    /** Calendar day of the appointment. */
    val date: LocalDate
        get() = Instant.ofEpochMilli(dayStartMillis).atZone(Appointment.SYSTEM_ZONE).toLocalDate()

    /** Human readable time, e.g. "14:30". */
    val formattedTime: String
        get() = "%02d:%02d".format(hour, minute)

    companion object {
        const val DEFAULT_HOUR = 9
        const val DEFAULT_MINUTE = 0

        val SYSTEM_ZONE: ZoneId = ZoneId.systemDefault()

        fun startOfDay(epochMillis: Long): Long =
            Instant.ofEpochMilli(epochMillis).atZone(SYSTEM_ZONE).toLocalDate()
                .atStartOfDay(SYSTEM_ZONE).toInstant().toEpochMilli()

        fun dayStartMillisOf(date: LocalDate): Long = date.atStartOfDay(SYSTEM_ZONE).toInstant().toEpochMilli()

        /** [startMillis, endMillis) range covering the given month. */
        fun monthRange(yearMonth: YearMonth): Pair<Long, Long> {
            val startOfMonth = LocalDate.of(yearMonth.year, yearMonth.monthValue, 1)
            val startMillis = startOfMonth.atStartOfDay(SYSTEM_ZONE).toInstant().toEpochMilli()
            val endMillis = startOfMonth.plusMonths(1).atStartOfDay(SYSTEM_ZONE).toInstant().toEpochMilli()
            return startMillis to endMillis
        }
    }
}

