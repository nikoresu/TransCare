package org.nikoresu.transcare.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** A lab result paired with the time elapsed since the first and previous test. */
data class ExamTrendPoint(
    val result: ExamResult,
    /** Days between this test and the previous one, or null for the first test. */
    val daysSincePrevious: Long?,
    /** Days between this test and the first test in the trend. */
    val daysSinceFirst: Long,
)

/**
 * Chronological history for a single metric with the deltas and test intervals
 * needed by the trend chart. Pure data so it can be unit tested.
 */
data class ExamTrend(
    val metric: ExamMetric,
    val points: List<ExamTrendPoint>,
) {
    val latest: ExamResult?
        get() = points.lastOrNull()?.result

    val previous: ExamResult?
        get() = points.getOrNull(points.size - 2)?.result

    /** Change from the previous test, or null when there is only one result. */
    val delta: Double?
        get() {
            val latestValue = latest?.value ?: return null
            val previousValue = previous?.value ?: return null
            return latestValue - previousValue
        }

    /** Days between the two most recent tests. */
    val latestGapDays: Long?
        get() = points.lastOrNull()?.daysSincePrevious

    /** Mean number of days between tests, or null with fewer than two tests. */
    val averageGapDays: Double?
        get() {
            val gaps = points.drop(1).mapNotNull { it.daysSincePrevious }
            return if (gaps.isEmpty()) null else gaps.average()
        }

    val hasReferenceRange: Boolean
        get() = metric.referenceLow != null || metric.referenceHigh != null

    /**
     * Value range plotted on the chart: the results and the reference band,
     * rounded to readable steps so the axis labels stay short.
     */
    val chartRange: ClosedFloatingPointRange<Double>
        get() {
            var minValue = points.minOfOrNull { it.result.value } ?: 0.0
            var maxValue = points.maxOfOrNull { it.result.value } ?: 0.0
            metric.referenceLow?.let { minValue = minOf(minValue, it) }
            metric.referenceHigh?.let { maxValue = maxOf(maxValue, it) }

            if (minValue == maxValue) {
                val padding = max(abs(minValue) * 0.1, 1.0)
                minValue -= padding
                maxValue += padding
            }

            val span = maxValue - minValue
            val paddedMin = minValue - span * 0.08
            val paddedMax = maxValue + span * 0.08
            val step = niceStep((paddedMax - paddedMin) / 5.0)
            return (floor(paddedMin / step) * step)..(ceil(paddedMax / step) * step)
        }

    companion object {
        /** Builds a trend from results of any metric, keeping only [metric]. */
        fun of(metric: ExamMetric, history: List<ExamResult>): ExamTrend {
            val sorted = history
                .filter { it.metric == metric }
                .sortedWith(compareBy({ it.dayStartMillis }, { it.id }))

            val firstDate: LocalDate? = sorted.firstOrNull()?.date
            var previousDate: LocalDate? = null
            val points = sorted.map { result ->
                val gap = previousDate?.let { ChronoUnit.DAYS.between(it, result.date) }
                val fromFirst = firstDate?.let { ChronoUnit.DAYS.between(it, result.date) } ?: 0L
                previousDate = result.date
                ExamTrendPoint(
                    result = result,
                    daysSincePrevious = gap,
                    daysSinceFirst = fromFirst,
                )
            }
            return ExamTrend(metric = metric, points = points)
        }
    }
}

/** Formats a signed change, e.g. 12.5 -> "+12.5", -3 -> "−3", 0 -> "0". */
fun formatExamDelta(delta: Double): String = when {
    !delta.isFinite() -> "—"
    delta > 0.0 -> "+${formatExamNumber(delta)}"
    delta < 0.0 -> "−${formatExamNumber(-delta)}"
    else -> "0"
}

/** Rounds an axis step to 1, 2, 5 or 10 times a power of ten. */
private fun niceStep(rawStep: Double): Double {
    if (!rawStep.isFinite() || rawStep <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val normalized = rawStep / magnitude
    val nice = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}
