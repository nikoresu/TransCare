package org.nikoresu.transcare.ui.appointments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.Appointment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentEditorScreen(
    initialAppointment: Appointment?,
    initialDayStartMillis: Long?,
    onDismiss: () -> Unit,
    onSave: (Appointment) -> Unit,
) {
    val initialDate = initialDayStartMillis?.let { millis ->
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    } ?: LocalDate.now()
    val locale = LocalConfiguration.current.locales[0]

    var selectedEpochDay by rememberSaveable { mutableLongStateOf(initialDate.toEpochDay()) }
    var hour by rememberSaveable {
        mutableIntStateOf(initialAppointment?.hour ?: Appointment.DEFAULT_HOUR)
    }
    var minute by rememberSaveable {
        mutableIntStateOf(initialAppointment?.minute ?: Appointment.DEFAULT_MINUTE)
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)

    var doctorName by rememberSaveable { mutableStateOf(initialAppointment?.doctorName.orEmpty()) }
    var place by rememberSaveable { mutableStateOf(initialAppointment?.place.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initialAppointment?.notes.orEmpty()) }

    BackHandler(onBack = onDismiss)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (initialAppointment == null) {
                                R.string.appointments_new_title
                            } else {
                                R.string.appointments_edit_title
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateTimeSelector(
                    label = stringResource(R.string.appointments_date),
                    value = formatSelectorDate(selectedDate, locale),
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                )
                DateTimeSelector(
                    label = stringResource(R.string.appointments_time),
                    value = formatSelectorTime(hour, minute),
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = stringResource(R.string.appointments_reminders_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = doctorName,
                onValueChange = { doctorName = it },
                label = { Text(text = stringResource(R.string.appointments_doctor)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = place,
                onValueChange = { place = it },
                label = { Text(text = stringResource(R.string.appointments_place)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(text = stringResource(R.string.appointments_notes)) },
                supportingText = {
                    Text(text = stringResource(R.string.appointments_notes_hint))
                },
                minLines = 3,
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
                        onSave(
                            Appointment(
                                id = initialAppointment?.id ?: 0L,
                                doctorName = doctorName.trim(),
                                place = place.trim(),
                                notes = notes.trim(),
                                dayStartMillis = Appointment.dayStartMillisOf(selectedDate),
                                hour = hour,
                                minute = minute,
                            ),
                        )
                    },
                ) {
                    Text(text = stringResource(R.string.action_save))
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDate = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedEpochDay = Instant.ofEpochMilli(millis)
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
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true,
        )
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        hour = timePickerState.hour
                        minute = timePickerState.minute
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
            title = {
                Text(
                    text = stringResource(R.string.appointments_select_time),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

/** Compact, tappable card showing one date or time value. */
@Composable
private fun DateTimeSelector(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatSelectorDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

private fun formatSelectorTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)
