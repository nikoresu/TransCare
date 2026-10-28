package org.nikoresu.transcare

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.domain.model.ExamResult
import org.nikoresu.transcare.domain.model.ExamStatus
import org.nikoresu.transcare.domain.model.ExamTrend
import org.nikoresu.transcare.domain.model.formatExamDelta
import org.nikoresu.transcare.domain.model.formatExamNumber

class ExamTest {

    private fun exam(
        metric: ExamMetric,
        value: Double,
        date: LocalDate,
        id: Long = 0L,
    ): ExamResult = ExamResult(
        id = id,
        metric = metric,
        value = value,
        dayStartMillis = ExamResult.dayStartMillisOf(date),
    )

    @Test
    fun status_usesReferenceRange() {
        assertEquals(ExamStatus.IN_RANGE, ExamStatus.of(ExamMetric.ESTRADIOL, 150.0))
        assertEquals(ExamStatus.BELOW, ExamStatus.of(ExamMetric.ESTRADIOL, 80.0))
        assertEquals(ExamStatus.ABOVE, ExamStatus.of(ExamMetric.ESTRADIOL, 250.0))
    }

    @Test
    fun status_handlesOneSidedRanges() {
        assertEquals(ExamStatus.IN_RANGE, ExamStatus.of(ExamMetric.TESTOSTERONE_TOTAL, 20.0))
        assertEquals(ExamStatus.ABOVE, ExamStatus.of(ExamMetric.TESTOSTERONE_TOTAL, 60.0))
    }

    @Test
    fun status_isUnknownForLabSpecificRanges() {
        assertEquals(ExamStatus.NO_RANGE, ExamStatus.of(ExamMetric.PROGESTERONE, 5.0))
    }

    @Test
    fun numberFormattingTrimsTrailingZeros() {
        assertEquals("150", formatExamNumber(150.0))
        assertEquals("0.5", formatExamNumber(0.5))
        assertEquals("3.5", formatExamNumber(3.5))
        assertEquals("0", formatExamNumber(0.0))
    }

    @Test
    fun referenceLabelsDescribeTheRange() {
        assertEquals(
            "100–200 pg/mL",
            ExamMetric.ESTRADIOL.referenceLabel(
                underLabel = "under",
                overLabel = "over",
                labSpecificLabel = "lab-specific range",
            ),
        )
        assertEquals(
            "under 50 ng/dL",
            ExamMetric.TESTOSTERONE_TOTAL.referenceLabel(
                underLabel = "under",
                overLabel = "over",
                labSpecificLabel = "lab-specific range",
            ),
        )
        assertEquals(
            "lab-specific range",
            ExamMetric.PROGESTERONE.referenceLabel(
                underLabel = "under",
                overLabel = "over",
                labSpecificLabel = "lab-specific range",
            ),
        )
    }

    @Test
    fun referenceLabelsFollowTheProvidedLanguage() {
        assertEquals(
            "menos de 50 ng/dL",
            ExamMetric.TESTOSTERONE_TOTAL.referenceLabel(
                underLabel = "menos de",
                overLabel = "más de",
                labSpecificLabel = "rango específico del laboratorio",
            ),
        )
        assertEquals(
            "rango específico del laboratorio",
            ExamMetric.PROGESTERONE.referenceLabel(
                underLabel = "menos de",
                overLabel = "más de",
                labSpecificLabel = "rango específico del laboratorio",
            ),
        )
    }

    @Test
    fun trend_sortsByDateAndComputesDeltaAndGaps() {
        val day = LocalDate.of(2026, 1, 1)
        val trend = ExamTrend.of(
            ExamMetric.ESTRADIOL,
            listOf(
                exam(ExamMetric.ESTRADIOL, 150.0, day.plusDays(180)),
                exam(ExamMetric.ESTRADIOL, 120.0, day),
                exam(ExamMetric.ESTRADIOL, 140.0, day.plusDays(90)),
            ),
        )

        assertEquals(listOf(120.0, 140.0, 150.0), trend.points.map { it.result.value })
        assertEquals(listOf(0L, 90L, 180L), trend.points.map { it.daysSinceFirst })
        assertNull(trend.points.first().daysSincePrevious)
        assertEquals(90L, trend.points[1].daysSincePrevious)
        assertEquals(90L, trend.points[2].daysSincePrevious)
        assertEquals(90.0, trend.averageGapDays!!, 0.001)
        assertEquals(90L, trend.latestGapDays)
        assertEquals(10.0, trend.delta!!, 0.001)
    }

    @Test
    fun trend_ignoresResultsFromOtherMetrics() {
        val day = LocalDate.of(2026, 1, 1)
        val trend = ExamTrend.of(
            ExamMetric.ESTRADIOL,
            listOf(
                exam(ExamMetric.ESTRADIOL, 120.0, day),
                exam(ExamMetric.TESTOSTERONE_TOTAL, 20.0, day.plusDays(30)),
                exam(ExamMetric.ESTRADIOL, 150.0, day.plusDays(90)),
            ),
        )

        assertEquals(2, trend.points.size)
        assertEquals(150.0, trend.latest?.value ?: 0.0, 0.001)
        assertEquals(90L, trend.latestGapDays)
    }

    @Test
    fun trend_singleResultHasNoDeltaOrGaps() {
        val trend = ExamTrend.of(
            ExamMetric.ESTRADIOL,
            listOf(exam(ExamMetric.ESTRADIOL, 150.0, LocalDate.of(2026, 1, 1))),
        )

        assertNull(trend.delta)
        assertNull(trend.latestGapDays)
        assertNull(trend.averageGapDays)
    }

    @Test
    fun trend_chartRangeIncludesTheReferenceBand() {
        val day = LocalDate.of(2026, 1, 1)
        val trend = ExamTrend.of(
            ExamMetric.ESTRADIOL,
            listOf(
                exam(ExamMetric.ESTRADIOL, 150.0, day),
                exam(ExamMetric.ESTRADIOL, 160.0, day.plusDays(30)),
            ),
        )

        assertTrue(trend.chartRange.start <= 100.0)
        assertTrue(trend.chartRange.endInclusive >= 200.0)
    }

    @Test
    fun trend_chartRangeAddsPaddingWhenValuesAreConstant() {
        val day = LocalDate.of(2026, 1, 1)
        val trend = ExamTrend.of(
            ExamMetric.PROGESTERONE,
            listOf(
                exam(ExamMetric.PROGESTERONE, 5.0, day),
                exam(ExamMetric.PROGESTERONE, 5.0, day.plusDays(30)),
            ),
        )

        assertTrue(trend.chartRange.start < 5.0)
        assertTrue(trend.chartRange.endInclusive > 5.0)
    }

    @Test
    fun deltaFormattingIncludesTheSign() {
        assertEquals("+12.5", formatExamDelta(12.5))
        assertEquals("−3", formatExamDelta(-3.0))
        assertEquals("0", formatExamDelta(0.0))
    }
}
