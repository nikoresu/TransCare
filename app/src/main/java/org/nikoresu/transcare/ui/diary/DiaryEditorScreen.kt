package org.nikoresu.transcare.ui.diary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.DiaryEntry
import org.nikoresu.transcare.domain.model.DiaryEntryType
import org.nikoresu.transcare.ui.formatAppDate

/**
 * Editor for a diary entry. The date, title and text can be changed; unsaved
 * edits are confirmed before the screen closes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiaryEditorScreen(
    initialEntry: DiaryEntry?,
    initialType: DiaryEntryType = DiaryEntryType.NOTES,
    onDismiss: () -> Unit,
    onSave: (DiaryEntry) -> Unit,
) {
    val initialDate = remember(initialEntry?.id) { initialEntry?.date ?: LocalDate.now() }
    val initialTitle = initialEntry?.title.orEmpty()
    val initialText = initialEntry?.text.orEmpty()
    val startingType = initialEntry?.type ?: initialType

    var dateEpochDay by rememberSaveable(initialEntry?.id) {
        mutableLongStateOf(initialDate.toEpochDay())
    }
    var title by rememberSaveable(initialEntry?.id) { mutableStateOf(initialTitle) }
    var text by rememberSaveable(initialEntry?.id) { mutableStateOf(initialText) }
    var type by rememberSaveable(initialEntry?.id) { mutableStateOf(startingType) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val date = LocalDate.ofEpochDay(dateEpochDay)
    val canSave = title.isNotBlank() || text.isNotBlank()
    val isDirty = title != initialTitle ||
        text != initialText ||
        type != startingType ||
        dateEpochDay != initialDate.toEpochDay()

    val requestDismiss = {
        if (isDirty) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    BackHandler(onBack = requestDismiss)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (initialEntry == null) {
                                R.string.diary_new_title
                            } else {
                                R.string.diary_edit_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    TextButton(onClick = requestDismiss) {
                        Text(text = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            onSave(
                                DiaryEntry(
                                    id = initialEntry?.id ?: 0L,
                                    dayStartMillis = DiaryEntry.dayStartMillisOf(date),
                                    title = title.trim(),
                                    text = text.trim(),
                                    type = type,
                                ),
                            )
                        },
                        enabled = canSave,
                    ) {
                        Text(text = stringResource(R.string.action_save))
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
                    text = stringResource(R.string.diary_date),
                    style = MaterialTheme.typography.titleSmall,
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = formatAppDate(date),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start,
                    )
                    Text(text = "▾")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.diary_type),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DiaryEntryType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(text = option.label()) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(text = stringResource(R.string.diary_entry_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(text = stringResource(R.string.diary_entry_text)) },
                placeholder = { Text(text = stringResource(R.string.diary_entry_placeholder)) },
                minLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp),
            )

            Text(
                text = stringResource(R.string.diary_storage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(text = stringResource(R.string.diary_discard_title)) },
            text = { Text(text = stringResource(R.string.diary_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onDismiss()
                    },
                ) {
                    Text(text = stringResource(R.string.diary_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(text = stringResource(R.string.diary_keep_editing))
                }
            },
        )
    }
}
