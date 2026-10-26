package org.nikoresu.transcare.ui.exams

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.domain.model.ExamResult
import org.nikoresu.transcare.domain.model.formatExamNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamEditorScreen(
    initialResult: ExamResult?,
    initialMetric: ExamMetric?,
    onDismiss: () -> Unit,
    onSave: (ExamResult) -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDate = initialResult?.date ?: LocalDate.now(),
    )

    var selectedMetricName by rememberSaveable {
        mutableStateOf((initialResult?.metric ?: initialMetric ?: ExamMetric.ESTRADIOL).name)
    }
    val selectedMetric = ExamMetric.entries.firstOrNull { it.name == selectedMetricName }
        ?: ExamMetric.ESTRADIOL

    var valueText by rememberSaveable(initialResult?.id) {
        mutableStateOf(initialResult?.let { formatExamNumber(it.value) }.orEmpty())
    }
    var note by rememberSaveable(initialResult?.id) {
        mutableStateOf(initialResult?.note.orEmpty())
    }
    var metricMenuExpanded by remember { mutableStateOf(false) }

    val parsedValue = valueText.trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0.0 }
    val hasValueError = valueText.isNotBlank() && parsedValue == null

    BackHandler(onBack = onDismiss)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (initialResult == null) {
                                R.string.exams_new_title
                            } else {
                                R.string.exams_edit_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.exams_metric),
                    style = MaterialTheme.typography.titleSmall,
                )
                Box {
                    OutlinedButton(
                        onClick = { metricMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = selectedMetric.displayName(),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                        )
                        Text(text = "▾")
                    }
                    DropdownMenu(
                        expanded = metricMenuExpanded,
                        onDismissRequest = { metricMenuExpanded = false },
                        modifier = Modifier
                            .widthIn(min = 280.dp)
                            .heightIn(max = 480.dp),
                    ) {
                        ExamMetric.entries.forEach { metric ->
                            DropdownMenuItem(
                                text = { Text(text = metric.displayName()) },
                                onClick = {
                                    selectedMetricName = metric.name
                                    metricMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                val referenceLabel = selectedMetric.referenceLabelText()
                val referenceNote = selectedMetric.referenceNote()
                Text(
                    text = if (referenceNote == null) {
                        stringResource(R.string.exams_reference, referenceLabel)
                    } else {
                        stringResource(
                            R.string.exams_reference_with_note,
                            referenceLabel,
                            referenceNote,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DatePicker(state = datePickerState)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text(text = stringResource(R.string.exams_value)) },
                    suffix = { Text(text = selectedMetric.unit) },
                    singleLine = true,
                    isError = hasValueError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hasValueError) {
                    Text(
                        text = stringResource(R.string.exams_invalid_value),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(text = stringResource(R.string.exams_note)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        val date = datePickerState.selectedDateMillis?.let { millis ->
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        } ?: LocalDate.now()
                        onSave(
                            ExamResult(
                                id = initialResult?.id ?: 0L,
                                metric = selectedMetric,
                                value = parsedValue ?: return@Button,
                                dayStartMillis = ExamResult.dayStartMillisOf(date),
                                note = note.trim(),
                            ),
                        )
                    },
                    enabled = parsedValue != null,
                ) {
                    Text(text = stringResource(R.string.action_save))
                }
            }
        }
    }
}
