package org.nikoresu.transcare.ui.exams

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.domain.model.ExamResult
import org.nikoresu.transcare.domain.model.formatExamNumber
import org.nikoresu.transcare.pdf.ParsedPdfExams

/** Editable row shown while reviewing a parsed PDF. */
private data class PdfRowState(
    val metricName: String,
    val valueText: String,
    val selected: Boolean,
) : Serializable

/**
 * Review screen for values found in a PDF. The user can change the sample
 * date, edit values, and deselect rows before appending them to the history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfImportScreen(
    parsed: ParsedPdfExams,
    existingResults: List<ExamResult>,
    onDismiss: () -> Unit,
    onImport: (List<ExamResult>) -> Unit,
) {
    var dateEpochDay by rememberSaveable(parsed) {
        mutableLongStateOf((parsed.date ?: LocalDate.now()).toEpochDay())
    }
    val date = LocalDate.ofEpochDay(dateEpochDay)

    var rows by rememberSaveable(parsed) {
        mutableStateOf<List<PdfRowState>>(
            ArrayList(
                parsed.results.map { result ->
                    PdfRowState(
                        metricName = result.metric.name,
                        valueText = formatExamNumber(result.value),
                        selected = true,
                    )
                },
            ),
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val duplicateMetricNames = remember(existingResults, date) {
        PdfImportRules.duplicateMetricNames(existingResults, date)
    }
    val selectableRows = rows.filter { it.metricName !in duplicateMetricNames }
    val allSelected = selectableRows.isNotEmpty() && selectableRows.all { it.selected }
    val importableRows = rows.filter { row ->
        PdfImportRules.isImportable(
            metricName = row.metricName,
            valueText = row.valueText,
            selected = row.selected,
            duplicateMetricNames = duplicateMetricNames,
        )
    }

    BackHandler(onBack = onDismiss)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.exams_pdf_review_title)) },
                navigationIcon = {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (importableRows.isEmpty()) {
                        Text(
                            text = stringResource(R.string.exams_pdf_nothing_selected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            val results = importableRows.mapNotNull { row ->
                                row.toExamResult(date)
                            }
                            if (results.isNotEmpty()) onImport(results)
                        },
                        enabled = importableRows.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.exams_pdf_import_action,
                                importableRows.size,
                                importableRows.size,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.exams_pdf_review_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.exams_pdf_found_count,
                    rows.size,
                    rows.size,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.exams_pdf_date),
                    style = MaterialTheme.typography.titleSmall,
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = formatExamDate(date),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start,
                    )
                    Text(text = "▾")
                }
                if (parsed.date == null) {
                    Text(
                        text = stringResource(R.string.exams_pdf_date_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.exams_pdf_values_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = {
                        rows = ArrayList(
                            rows.map { row ->
                                if (row.metricName in duplicateMetricNames) {
                                    row
                                } else {
                                    row.copy(selected = !allSelected)
                                }
                            },
                        )
                    },
                    enabled = selectableRows.isNotEmpty(),
                ) {
                    Text(
                        text = stringResource(
                            if (allSelected) {
                                R.string.exams_pdf_deselect_all
                            } else {
                                R.string.exams_pdf_select_all
                            },
                        ),
                    )
                }
            }

            rows.forEach { row ->
                val metric = ExamMetric.entries.firstOrNull { it.name == row.metricName }
                    ?: return@forEach
                val isDuplicate = row.metricName in duplicateMetricNames
                val isInvalid = row.valueText.isNotBlank() &&
                    PdfImportRules.parseValue(row.valueText) == null
                PdfImportRow(
                    metric = metric,
                    row = row,
                    isDuplicate = isDuplicate,
                    isInvalid = isInvalid,
                    onSelectedChange = { checked ->
                        rows = ArrayList(
                            rows.map {
                                if (it.metricName == row.metricName) {
                                    it.copy(selected = checked)
                                } else {
                                    it
                                }
                            },
                        )
                    },
                    onValueChange = { text ->
                        rows = ArrayList(
                            rows.map {
                                if (it.metricName == row.metricName) {
                                    it.copy(valueText = text)
                                } else {
                                    it
                                }
                            },
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            dateEpochDay = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay()
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun PdfImportRow(
    metric: ExamMetric,
    row: PdfRowState,
    isDuplicate: Boolean,
    isInvalid: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = row.selected && !isDuplicate,
                    onCheckedChange = onSelectedChange,
                    enabled = !isDuplicate,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metric.displayName(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = metric.unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = row.valueText,
                onValueChange = onValueChange,
                label = { Text(text = stringResource(R.string.exams_value)) },
                suffix = { Text(text = metric.unit) },
                singleLine = true,
                enabled = !isDuplicate,
                isError = isInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            when {
                isDuplicate -> Text(
                    text = stringResource(R.string.exams_pdf_duplicate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                isInvalid -> Text(
                    text = stringResource(R.string.exams_invalid_value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun PdfRowState.toExamResult(date: LocalDate): ExamResult? {
    val metric = ExamMetric.entries.firstOrNull { it.name == metricName } ?: return null
    val value = PdfImportRules.parseValue(valueText) ?: return null
    return ExamResult(
        metric = metric,
        value = value,
        dayStartMillis = ExamResult.dayStartMillisOf(date),
    )
}
