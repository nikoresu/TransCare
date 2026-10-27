package org.nikoresu.transcare.ui.medications

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.domain.model.MedicationRoute
import org.nikoresu.transcare.domain.model.MedicationSchedule
import org.nikoresu.transcare.ui.formatAppDate

private const val DEFAULT_REMINDER_MINUTES = 9 * 60

/**
 * Editor for a medication. Reminder time, supply and notes are optional;
 * unsaved edits are confirmed before the screen closes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedicationEditorScreen(
    initialMedication: Medication?,
    onDismiss: () -> Unit,
    onSave: (Medication) -> Unit,
) {
    val initialStartDate = remember(initialMedication?.id) {
        initialMedication?.startDate ?: LocalDate.now()
    }
    val initialName = initialMedication?.name.orEmpty()
    val initialDose = initialMedication?.dose.orEmpty()
    val initialRoute = initialMedication?.route ?: MedicationRoute.ORAL
    val initialSchedule = initialMedication?.schedule ?: MedicationSchedule.DAILY
    val initialReminderMinutes = initialMedication?.reminderMinutes ?: DEFAULT_REMINDER_MINUTES
    val initialRemind = initialMedication?.reminderMinutes != null || initialMedication == null
    val initialSupply = initialMedication?.supplyDoses?.toString().orEmpty()
    val initialNotes = initialMedication?.notes.orEmpty()

    var name by rememberSaveable(initialMedication?.id) { mutableStateOf(initialName) }
    var dose by rememberSaveable(initialMedication?.id) { mutableStateOf(initialDose) }
    var routeName by rememberSaveable(initialMedication?.id) { mutableStateOf(initialRoute.name) }
    var scheduleName by rememberSaveable(initialMedication?.id) {
        mutableStateOf(initialSchedule.name)
    }
    var startEpochDay by rememberSaveable(initialMedication?.id) {
        mutableLongStateOf(initialStartDate.toEpochDay())
    }
    var remind by rememberSaveable(initialMedication?.id) { mutableStateOf(initialRemind) }
    var reminderMinutes by rememberSaveable(initialMedication?.id) {
        mutableIntStateOf(initialReminderMinutes)
    }
    var supplyText by rememberSaveable(initialMedication?.id) { mutableStateOf(initialSupply) }
    var notes by rememberSaveable(initialMedication?.id) { mutableStateOf(initialNotes) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val route = MedicationRoute.entries.firstOrNull { it.name == routeName } ?: initialRoute
    val schedule = MedicationSchedule.entries.firstOrNull { it.name == scheduleName }
        ?: initialSchedule
    val startDate = LocalDate.ofEpochDay(startEpochDay)
    val parsedSupply = supplyText.trim().toIntOrNull()?.takeIf { it >= 0 }
    val hasSupplyError = supplyText.isNotBlank() && parsedSupply == null
    val reminderApplies = schedule != MedicationSchedule.AS_NEEDED
    val canSave = name.isNotBlank() && !hasSupplyError
    val isDirty = name != initialName ||
        dose != initialDose ||
        route != initialRoute ||
        schedule != initialSchedule ||
        startEpochDay != initialStartDate.toEpochDay() ||
        remind != initialRemind ||
        reminderMinutes != initialReminderMinutes ||
        supplyText != initialSupply ||
        notes != initialNotes

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
                            if (initialMedication == null) {
                                R.string.medication_new_title
                            } else {
                                R.string.medication_edit_title
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
                                Medication(
                                    id = initialMedication?.id ?: 0L,
                                    name = name.trim(),
                                    dose = dose.trim(),
                                    route = route,
                                    schedule = schedule,
                                    startDayStartMillis = Medication.dayStartMillisOf(startDate),
                                    reminderMinutes = if (remind && reminderApplies) {
                                        reminderMinutes
                                    } else {
                                        null
                                    },
                                    supplyDoses = parsedSupply,
                                    notes = notes.trim(),
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(R.string.medication_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = dose,
                onValueChange = { dose = it },
                label = { Text(text = stringResource(R.string.medication_dose)) },
                placeholder = { Text(text = stringResource(R.string.medication_dose_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.medication_route),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MedicationRoute.entries.forEach { option ->
                        FilterChip(
                            selected = route == option,
                            onClick = { routeName = option.name },
                            label = { Text(text = option.label()) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.medication_schedule),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MedicationSchedule.entries.forEach { option ->
                        FilterChip(
                            selected = schedule == option,
                            onClick = { scheduleName = option.name },
                            label = { Text(text = option.label()) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.medication_start_date),
                    style = MaterialTheme.typography.titleSmall,
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = formatAppDate(startDate),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start,
                    )
                    Text(text = "▾")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.medication_reminder),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Switch(
                        checked = remind && reminderApplies,
                        onCheckedChange = { remind = it },
                        enabled = reminderApplies,
                    )
                }
                if (!reminderApplies) {
                    Text(
                        text = stringResource(R.string.medication_reminder_as_needed_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (remind) {
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.medication_reminder_time),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                        )
                        Text(text = formatMedicationTime(reminderMinutes))
                    }
                    when (schedule) {
                        MedicationSchedule.TWICE_DAILY -> Text(
                            text = stringResource(R.string.medication_reminder_twice_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        MedicationSchedule.WEEKLY -> Text(
                            text = stringResource(R.string.medication_reminder_weekly_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        else -> Unit
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = supplyText,
                    onValueChange = { supplyText = it },
                    label = { Text(text = stringResource(R.string.medication_supply)) },
                    singleLine = true,
                    isError = hasSupplyError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (hasSupplyError) {
                        stringResource(R.string.medication_supply_invalid)
                    } else {
                        stringResource(R.string.medication_supply_helper)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasSupplyError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(text = stringResource(R.string.medication_notes)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.medication_storage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            startEpochDay = Instant.ofEpochMilli(millis)
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

    if (showTimePicker) {
        val context = LocalContext.current
        val timePickerState = rememberTimePickerState(
            initialHour = reminderMinutes / 60,
            initialMinute = reminderMinutes % 60,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(text = stringResource(R.string.medication_reminder_time)) },
            text = { TimeInput(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        reminderMinutes = timePickerState.hour * 60 + timePickerState.minute
                        showTimePicker = false
                    },
                ) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(text = stringResource(R.string.medication_discard_title)) },
            text = { Text(text = stringResource(R.string.medication_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onDismiss()
                    },
                ) {
                    Text(text = stringResource(R.string.medication_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(text = stringResource(R.string.medication_keep_editing))
                }
            },
        )
    }
}
