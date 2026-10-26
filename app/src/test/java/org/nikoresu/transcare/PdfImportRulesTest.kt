package org.nikoresu.transcare

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.domain.model.ExamResult
import org.nikoresu.transcare.ui.exams.PdfImportRules

class PdfImportRulesTest {

    private val day = LocalDate.of(2026, 10, 25)

    private fun result(metric: ExamMetric, date: LocalDate = day) = ExamResult(
        metric = metric,
        value = 1.0,
        dayStartMillis = ExamResult.dayStartMillisOf(date),
    )

    @Test
    fun duplicates_onlyMatchTheSameDate() {
        val existing = listOf(
            result(ExamMetric.ESTRADIOL),
            result(ExamMetric.LH, date = day.minusDays(1)),
        )

        assertEquals(setOf("ESTRADIOL"), PdfImportRules.duplicateMetricNames(existing, day))
        assertTrue(PdfImportRules.duplicateMetricNames(emptyList(), day).isEmpty())
    }

    @Test
    fun parseValue_acceptsDotsCommasAndSpaces() {
        assertEquals(23.8, PdfImportRules.parseValue("23.8")!!, 0.0001)
        assertEquals(23.8, PdfImportRules.parseValue(" 23,8 ")!!, 0.0001)
        assertEquals(494.0, PdfImportRules.parseValue("494")!!, 0.0001)
        assertEquals(0.0, PdfImportRules.parseValue("0")!!, 0.0001)
    }

    @Test
    fun parseValue_rejectsInvalidInput() {
        assertNull(PdfImportRules.parseValue(""))
        assertNull(PdfImportRules.parseValue("   "))
        assertNull(PdfImportRules.parseValue("abc"))
        assertNull(PdfImportRules.parseValue("-1"))
        assertNull(PdfImportRules.parseValue("NaN"))
        assertNull(PdfImportRules.parseValue("Infinity"))
    }

    @Test
    fun isImportable_requiresSelectionUniqueMetricAndValidValue() {
        val duplicates = setOf("LH")

        assertTrue(
            PdfImportRules.isImportable("ESTRADIOL", "23.8", selected = true, duplicateMetricNames = duplicates),
        )
        assertFalse(
            PdfImportRules.isImportable("ESTRADIOL", "23.8", selected = false, duplicateMetricNames = duplicates),
        )
        assertFalse(
            PdfImportRules.isImportable("LH", "10.1", selected = true, duplicateMetricNames = duplicates),
        )
        assertFalse(
            PdfImportRules.isImportable("ESTRADIOL", "", selected = true, duplicateMetricNames = duplicates),
        )
        assertFalse(
            PdfImportRules.isImportable("ESTRADIOL", "-2", selected = true, duplicateMetricNames = duplicates),
        )
    }
}
