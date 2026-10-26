package org.nikoresu.transcare.ui.exams

import java.time.LocalDate
import org.nikoresu.transcare.domain.model.ExamResult

/**
 * Pure rules behind the PDF review screen. Kept outside the composable so the
 * duplicate detection and value validation can be unit-tested.
 */
internal object PdfImportRules {

    /** Metrics already recorded on [date]; those rows are not imported again. */
    fun duplicateMetricNames(existing: List<ExamResult>, date: LocalDate): Set<String> =
        existing.filter { it.date == date }.map { it.metric.name }.toSet()

    /** Parses a value typed by the user, accepting a decimal comma. */
    fun parseValue(text: String): Double? =
        text.trim()
            .replace(',', '.')
            .toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 }

    fun isImportable(
        metricName: String,
        valueText: String,
        selected: Boolean,
        duplicateMetricNames: Set<String>,
    ): Boolean = selected &&
        metricName !in duplicateMetricNames &&
        parseValue(valueText) != null
}
