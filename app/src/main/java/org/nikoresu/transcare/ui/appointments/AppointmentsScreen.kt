package org.nikoresu.transcare.ui.appointments

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.text.format.DateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.Appointment
import org.nikoresu.transcare.notifications.AppointmentNotifications
import org.nikoresu.transcare.notifications.NotificationPermissions

private val WeekdayOrder = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsScreen(
    viewModel: AppointmentsViewModel,
    modifier: Modifier = Modifier,
    onEditorVisibilityChanged: (Boolean) -> Unit = {},
) {
    val appointments by viewModel.appointments.collectAsStateWithLifecycle()

    var yearMonth by remember { mutableStateOf(YearMonth.now()) }
    var showEditor by remember { mutableStateOf(false) }
    var editingAppointment by remember { mutableStateOf<Appointment?>(null) }
    var appointmentPendingDelete by remember { mutableStateOf<Appointment?>(null) }

    val context = LocalContext.current
    var notificationsGranted by remember {
        mutableStateOf(AppointmentNotifications.canPostNotifications(context))
    }
    var exactAlarmsAllowed by remember {
        mutableStateOf(NotificationPermissions.canScheduleExactAlarms(context))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsGranted = AppointmentNotifications.canPostNotifications(context)
        exactAlarmsAllowed = NotificationPermissions.canScheduleExactAlarms(context)
    }

    LaunchedEffect(showEditor) { onEditorVisibilityChanged(showEditor) }
    DisposableEffect(Unit) {
        onDispose { onEditorVisibilityChanged(false) }
    }

    val (monthStartMillis, monthEndMillis) = Appointment.monthRange(yearMonth)
    val monthAppointments = appointments
        .filter { it.dayStartMillis in monthStartMillis until monthEndMillis }
        .sortedBy { it.startMillis }

    if (showEditor) {
        AppointmentEditorScreen(
            initialAppointment = editingAppointment,
            initialDayStartMillis = editingAppointment?.dayStartMillis,
            onDismiss = { showEditor = false },
            onSave = { appointment ->
                if (editingAppointment == null) {
                    viewModel.addAppointment(appointment)
                } else {
                    viewModel.updateAppointment(appointment)
                }
                showEditor = false
                if (!notificationsGranted) {
                    notificationPermissionLauncher.launch(
                        NotificationPermissions.POST_NOTIFICATIONS_PERMISSION,
                    )
                }
            },
        )
    } else {
        Scaffold(
            modifier = modifier,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text(text = formatMonthTitle(yearMonth)) },
                    actions = {
                        TextButton(onClick = { yearMonth = YearMonth.now() }) {
                            Text(text = stringResource(R.string.appointments_today))
                        }
                        TextButton(onClick = { yearMonth = YearMonth.from(yearMonth).minusMonths(1) }) {
                            Text(text = "‹")
                        }
                        TextButton(onClick = { yearMonth = YearMonth.from(yearMonth).plusMonths(1) }) {
                            Text(text = "›")
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingAppointment = null
                        showEditor = true
                    },
                ) {
                    Text(text = stringResource(R.string.appointments_add))
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (appointments.isNotEmpty()) {
                    if (!notificationsGranted) {
                        ReminderStatusCard(
                            title = stringResource(R.string.appointments_reminders_off_title),
                            message = stringResource(R.string.appointments_reminders_off_message),
                            actionLabel = stringResource(R.string.appointments_allow),
                            onAction = {
                                notificationPermissionLauncher.launch(
                                    NotificationPermissions.POST_NOTIFICATIONS_PERMISSION,
                                )
                            },
                        )
                    } else if (!exactAlarmsAllowed) {
                        ReminderStatusCard(
                            title = stringResource(R.string.appointments_reminders_late_title),
                            message = stringResource(R.string.appointments_reminders_late_message),
                            actionLabel = stringResource(R.string.appointments_settings),
                            onAction = {
                                NotificationPermissions.openExactAlarmSettings(context)
                            },
                        )
                    }
                }

                MonthCalendar(
                    yearMonth = yearMonth,
                    hasAppointmentOnDay = { day -> appointments.any { it.date == day } },
                )

                Text(
                    text = stringResource(R.string.appointments_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                if (monthAppointments.isEmpty()) {
                    Text(
                        text = stringResource(R.string.appointments_empty_month),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                } else {
                    monthAppointments.forEach { appointment ->
                        AppointmentItem(
                            appointment = appointment,
                            onEdit = {
                                editingAppointment = appointment
                                showEditor = true
                            },
                            onDelete = { appointmentPendingDelete = appointment },
                        )
                    }
                }

                Spacer(Modifier.height(72.dp))
            }
        }
    }

    val appointmentToDelete = appointmentPendingDelete
    if (appointmentToDelete != null) {
        AlertDialog(
            onDismissRequest = { appointmentPendingDelete = null },
            title = { Text(text = stringResource(R.string.appointments_delete_title)) },
            text = { Text(text = stringResource(R.string.appointments_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAppointment(appointmentToDelete)
                        appointmentPendingDelete = null
                    },
                ) {
                    Text(text = stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { appointmentPendingDelete = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun formatMonthTitle(yearMonth: YearMonth): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, "MMMMy")
    return DateTimeFormatter.ofPattern(pattern, locale)
        .format(LocalDate.of(yearMonth.year, yearMonth.monthValue, 1))
}

@Composable
private fun MonthCalendar(
    yearMonth: YearMonth,
    hasAppointmentOnDay: (LocalDate) -> Boolean,
) {
    val firstDay = LocalDate.of(yearMonth.year, yearMonth.monthValue, 1)
    val leadingBlankDays = firstDay.dayOfWeek.value % 7L
    val startDate = firstDay.minusDays(leadingBlankDays)

    val cells = remember(yearMonth) {
        val result = mutableListOf<LocalDate>()
        var current = startDate
        repeat(42) {
            result.add(current)
            current = current.plusDays(1)
        }
        result
    }

    val locale = LocalConfiguration.current.locales[0]
    val weekdayLabels = remember(locale) {
        WeekdayOrder.map { it.getDisplayName(TextStyle.SHORT, locale) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val inMonth = day.year == yearMonth.year && day.monthValue == yearMonth.monthValue
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = day.dayOfMonth.toString(),
                            style = if (inMonth) {
                                MaterialTheme.typography.bodyMedium
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                            },
                            fontWeight = if (day == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (hasAppointmentOnDay(day)) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 7.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppointmentItem(
    appointment: Appointment,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = appointment.formattedTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = appointment.doctorName,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDate(appointment.date),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = appointment.place.ifBlank {
                        stringResource(R.string.appointments_no_place)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                if (appointment.notes.isNotBlank()) {
                    Text(
                        text = appointment.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column {
                TextButton(onClick = onEdit) {
                    Text(text = stringResource(R.string.action_edit))
                }
                TextButton(onClick = onDelete) {
                    Text(text = stringResource(R.string.action_delete))
                }
            }
        }
    }
}

@Composable
private fun ReminderStatusCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = onAction,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
private fun formatDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEEMMMd")
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}
