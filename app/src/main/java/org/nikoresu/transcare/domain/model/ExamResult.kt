package org.nikoresu.transcare.domain.model

import androidx.annotation.StringRes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.nikoresu.transcare.R

data class ExamResult(
    val id: Long = 0L,
    val metric: ExamMetric,
    val value: Double,
    val dayStartMillis: Long = 0L,
    val note: String = "",
) {
    /** Calendar day on which the sample was taken. */
    val date: LocalDate
        get() = dateOf(dayStartMillis)

    val formattedValue: String
        get() = formatExamNumber(value)

    val status: ExamStatus
        get() = ExamStatus.of(metric, value)

    companion object {
        private val SYSTEM_ZONE: ZoneId = ZoneId.systemDefault()

        fun dayStartMillisOf(date: LocalDate): Long =
            date.atStartOfDay(SYSTEM_ZONE).toInstant().toEpochMilli()

        fun dateOf(epochMillis: Long): LocalDate =
            Instant.ofEpochMilli(epochMillis).atZone(SYSTEM_ZONE).toLocalDate()
    }
}

enum class ExamStatus(@param:StringRes val labelRes: Int) {
    BELOW(R.string.exam_status_below),
    IN_RANGE(R.string.exam_status_in_range),
    ABOVE(R.string.exam_status_above),
    NO_RANGE(R.string.exam_status_no_range),
    ;

    companion object {
        fun of(metric: ExamMetric, value: Double): ExamStatus {
            val low = metric.referenceLow
            val high = metric.referenceHigh
            return when {
                low == null && high == null -> NO_RANGE
                low != null && value < low -> BELOW
                high != null && value > high -> ABOVE
                else -> IN_RANGE
            }
        }
    }
}
