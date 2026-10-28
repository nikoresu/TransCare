package org.nikoresu.transcare.ui.exams

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import kotlin.math.roundToInt
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.domain.model.ExamStatus
import org.nikoresu.transcare.domain.model.ExamTrend
import org.nikoresu.transcare.domain.model.formatExamDelta
import org.nikoresu.transcare.ui.formatAppDate

@Composable
internal fun ExamMetric.displayName(): String = stringResource(displayNameRes)

@Composable
internal fun ExamMetric.shortLabel(): String = stringResource(shortLabelRes)

@Composable
internal fun ExamMetric.referenceNote(): String? =
    referenceNoteRes?.let { stringResource(it) }

@Composable
internal fun ExamMetric.referenceLabelText(): String = referenceLabel(
    underLabel = stringResource(R.string.exam_reference_under_label),
    overLabel = stringResource(R.string.exam_reference_over_label),
    labSpecificLabel = stringResource(R.string.exam_reference_lab_specific),
)

@Composable
internal fun ExamStatus.labelText(): String = stringResource(labelRes)

@Composable
internal fun formatExamDate(date: LocalDate): String = formatAppDate(date)

/** Change from the previous test, e.g. "+12 pg/mL since Sep 12". */
@Composable
internal fun ExamTrend.deltaLabel(): String? {
    val delta = delta ?: return null
    val previous = previous ?: return null
    return if (delta == 0.0) {
        stringResource(R.string.exams_delta_no_change, formatExamDate(previous.date))
    } else {
        stringResource(
            R.string.exams_delta_since,
            "${formatExamDelta(delta)} ${metric.unit}",
            formatExamDate(previous.date),
        )
    }
}

/** Interval between one test and the one before it. */
@Composable
internal fun examGapLabel(days: Long): String =
    pluralStringResource(R.plurals.exams_gap_days, days.toInt(), days.toInt())

/** Average interval between tests, shown once there are at least two gaps. */
@Composable
internal fun ExamTrend.averageGapLabel(): String? {
    if (points.size < 3) return null
    val average = averageGapDays ?: return null
    return stringResource(R.string.exams_average_gap, average.roundToInt())
}
