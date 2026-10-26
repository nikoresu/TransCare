package org.nikoresu.transcare.ui.exams

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.domain.model.ExamStatus
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
