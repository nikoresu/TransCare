package org.nikoresu.transcare

import org.junit.Assert.assertEquals
import org.junit.Test
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.domain.model.ExamStatus
import org.nikoresu.transcare.domain.model.formatExamNumber

class ExamTest {

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
}
