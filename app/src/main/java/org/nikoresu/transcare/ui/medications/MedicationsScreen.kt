package org.nikoresu.transcare.ui.medications

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.AdherenceSummary
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.domain.model.MedicationAdherence
import org.nikoresu.transcare.domain.model.MedicationDose
import org.nikoresu.transcare.domain.model.MedicationDoseStatus
import org.nikoresu.transcare.domain.model.MedicationSchedule
import org.nikoresu.transcare.notifications.MedicationNotifications
import org.nikoresu.transcare.notifications.NotificationPermissions
import org.nikoresu.transcare.ui.formatAppDate

private data class TodayDoseItem(
    val medication: Medication,
    val doseIndex: Int,
    val timeMinutes: Int?,
    val log: MedicationDose?,
)

/** Medication tab: today's doses, the medication list and adherence. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationsScreen(
    viewModel: MedicationsViewModel,
    modifier: Modifier = Modifier,
    onEditorVisibilityChanged: (Boolean) -> Unit = {},
) {
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val doses by viewModel.doses.collectAsStateWithLifecycle()
    val isLoaded by viewModel.isLoaded.collectAsStateWithLifecycle()

    var showEditor by remember { mutableStateOf(false) }
    var editingMedication by remember { mutableStateOf<Medication?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var medicationPendingDelete by remember { mutableStateOf<Medication?>(null) }

    val context = LocalContext.current
    var notificationsGranted by remember {
        mutableStateOf(MedicationNotifications.canPostNotifications(context))
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
        notificationsGranted = MedicationNotifications.canPostNotifications(context)
        exactAlarmsAllowed = NotificationPermissions.canScheduleExactAlarms(context)
    }

    LaunchedEffect(showEditor, showHistory) {
        onEditorVisibilityChanged(showEditor || showHistory)
    }
    DisposableEffect(Unit) {
        onDispose { onEditorVisibilityChanged(false) }
    }

    val today = LocalDate.now()
    val todayStartMillis = Medication.dayStartMillisOf(today)
    val todayDoses = remember(medications, doses, todayStartMillis) {
        buildTodayDoses(medications, doses, today, todayStartMillis)
    }
    val adherence = remember(medications, doses, today) {
        MedicationAdherence.summary(
            medications = medications,
            doses = doses,
            startDate = today.minusDays(29),
            endDate = today,
        )
    }
    val hasReminders = medications.any { it.hasReminder }

    when {
        showEditor -> MedicationEditorScreen(
            initialMedication = editingMedication,
            onDismiss = { showEditor = false },
            onSave = { medication ->
                if (editingMedication == null) {
                    viewModel.addMedication(medication)
                } else {
                    viewModel.updateMedication(medication)
                }
                showEditor = false
                if (medication.reminderMinutes != null && !notificationsGranted) {
                    notificationPermissionLauncher.launch(
                        NotificationPermissions.POST_NOTIFICATIONS_PERMISSION,
                    )
                }
            },
        )

        showHistory -> MedicationHistoryScreen(
            medications = medications,
            doses = doses,
            onBack = { showHistory = false },
            onRemoveDose = { dose ->
                viewModel.removeDose(
                    medicationId = dose.medicationId,
                    dayStartMillis = dose.dayStartMillis,
                    doseIndex = dose.doseIndex,
                )
            },
        )

        else -> Scaffold(
            modifier = modifier,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(R.string.medications_title)) },
                    actions = {
                        if (doses.isNotEmpty()) {
                            TextButton(onClick = { showHistory = true }) {
                                Text(text = stringResource(R.string.medications_history))
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingMedication = null
                        showEditor = true
                    },
                ) {
                    Text(text = stringResource(R.string.medications_add))
                }
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (hasReminders && !notificationsGranted) {
                    item {
                        ReminderStatusCard(
                            title = stringResource(R.string.medications_reminders_off_title),
                            message = stringResource(R.string.medications_reminders_off_message),
                            actionLabel = stringResource(R.string.appointments_allow),
                            onAction = {
                                notificationPermissionLauncher.launch(
                                    NotificationPermissions.POST_NOTIFICATIONS_PERMISSION,
                                )
                            },
                        )
                    }
                } else if (hasReminders && !exactAlarmsAllowed) {
                    item {
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

                item {
                    Text(
                        text = stringResource(R.string.medications_today),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item {
                    Text(
                        text = formatAppDate(today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when {
                    !isLoaded -> item {
                        Text(
                            text = stringResource(R.string.status_loading),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    todayDoses.isEmpty() -> item {
                        Text(
                            text = stringResource(R.string.medications_today_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> items(
                        items = todayDoses,
                        key = { item -> "${item.medication.id}-${item.doseIndex}" },
                    ) { item ->
                        TodayDoseCard(
                            item = item,
                            onLog = { status ->
                                viewModel.logDose(
                                    medicationId = item.medication.id,
                                    dayStartMillis = todayStartMillis,
                                    doseIndex = item.doseIndex,
                                    status = status,
                                )
                            },
                            onUndo = {
                                viewModel.removeDose(
                                    medicationId = item.medication.id,
                                    dayStartMillis = todayStartMillis,
                                    doseIndex = item.doseIndex,
                                )
                            },
                        )
                    }
                }

                if (adherence.scheduled > 0) {
                    item {
                        AdherenceCard(adherence = adherence)
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.medications_your),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (medications.isEmpty()) {
                    item {
                        EmptyMedicationsState(
                            onAdd = {
                                editingMedication = null
                                showEditor = true
                            },
                        )
                    }
                } else {
                    items(
                        items = medications.sortedBy { it.name.lowercase() },
                        key = { it.id },
                    ) { medication ->
                        MedicationCard(
                            medication = medication,
                            onEdit = {
                                editingMedication = medication
                                showEditor = true
                            },
                            onDelete = { medicationPendingDelete = medication },
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(72.dp))
                }
            }
        }
    }

    val medicationToDelete = medicationPendingDelete
    if (medicationToDelete != null) {
        AlertDialog(
            onDismissRequest = { medicationPendingDelete = null },
            title = { Text(text = stringResource(R.string.medication_delete_title)) },
            text = { Text(text = stringResource(R.string.medication_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMedication(medicationToDelete)
                        medicationPendingDelete = null
                    },
                ) {
                    Text(text = stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { medicationPendingDelete = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun buildTodayDoses(
    medications: List<Medication>,
    doses: List<MedicationDose>,
    today: LocalDate,
    todayStartMillis: Long,
): List<TodayDoseItem> = medications
    .filter { it.isActiveOn(today) }
    .flatMap { medication ->
        val indices = if (medication.schedule == MedicationSchedule.AS_NEEDED) {
            listOf(0)
        } else {
            medication.scheduledDoseIndices(today)
        }
        indices.map { doseIndex ->
            TodayDoseItem(
                medication = medication,
                doseIndex = doseIndex,
                timeMinutes = if (medication.schedule == MedicationSchedule.AS_NEEDED) {
                    null
                } else {
                    medication.doseTimeMinutes(doseIndex)
                },
                log = doses.firstOrNull { dose ->
                    dose.medicationId == medication.id &&
                        dose.dayStartMillis == todayStartMillis &&
                        dose.doseIndex == doseIndex
                },
            )
        }
    }
    .sortedWith(
        compareBy<TodayDoseItem> { it.timeMinutes ?: Int.MAX_VALUE }
            .thenBy { it.medication.name.lowercase() },
    )

@Composable
private fun TodayDoseCard(
    item: TodayDoseItem,
    onLog: (MedicationDoseStatus) -> Unit,
    onUndo: () -> Unit,
) {
    val medication = item.medication
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = medication.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = listOfNotNull(
                            medication.dose.takeIf { it.isNotBlank() },
                            medication.route.label(),
                            item.timeMinutes?.let { formatMedicationTime(it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.log?.let { log ->
                    StatusBadge(status = log.status)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.log == null) {
                    if (medication.schedule == MedicationSchedule.AS_NEEDED) {
                        OutlinedButton(onClick = { onLog(MedicationDoseStatus.TAKEN) }) {
                            Text(text = stringResource(R.string.medications_log_dose))
                        }
                    } else {
                        OutlinedButton(onClick = { onLog(MedicationDoseStatus.TAKEN) }) {
                            Text(text = stringResource(R.string.medication_status_taken))
                        }
                        OutlinedButton(onClick = { onLog(MedicationDoseStatus.SKIPPED) }) {
                            Text(text = stringResource(R.string.medication_skip))
                        }
                    }
                } else {
                    OutlinedButton(onClick = onUndo) {
                        Text(text = stringResource(R.string.medications_undo))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: MedicationDoseStatus) {
    val container = when (status) {
        MedicationDoseStatus.TAKEN -> MaterialTheme.colorScheme.primaryContainer
        MedicationDoseStatus.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (status) {
        MedicationDoseStatus.TAKEN -> MaterialTheme.colorScheme.onPrimaryContainer
        MedicationDoseStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MedicationCard(
    medication: Medication,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = medication.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (medication.dose.isNotBlank()) {
                Text(
                    text = medication.dose,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                text = buildString {
                    append(medication.route.label())
                    append(" · ")
                    append(medication.schedule.label())
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            reminderSummary(medication)?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            supplySummary(medication)?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRunningLow(medication)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (medication.notes.isNotBlank()) {
                Text(
                    text = medication.notes,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
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
private fun reminderSummary(medication: Medication): String? {
    if (!medication.hasReminder) return null
    val first = medication.doseTimeMinutes(0) ?: return null
    return if (medication.schedule == MedicationSchedule.TWICE_DAILY) {
        val second = medication.doseTimeMinutes(1) ?: first
        stringResource(
            R.string.medication_reminder_summary_twice,
            formatMedicationTime(first),
            formatMedicationTime(second),
        )
    } else {
        stringResource(R.string.medication_reminder_summary, formatMedicationTime(first))
    }
}

@Composable
private fun supplySummary(medication: Medication): String? {
    val supply = medication.supplyDoses ?: return null
    val days = medication.estimatedDaysOfSupply
    return if (days == null) {
        pluralStringResource(R.plurals.medication_supply_left, supply, supply)
    } else {
        pluralStringResource(R.plurals.medication_supply_summary, supply, supply, days)
    }
}

private fun isRunningLow(medication: Medication): Boolean {
    val days = medication.estimatedDaysOfSupply ?: return false
    return days <= LOW_SUPPLY_DAYS
}

@Composable
private fun AdherenceCard(adherence: AdherenceSummary) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.medications_adherence_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    R.string.medications_adherence_value,
                    adherence.taken,
                    adherence.scheduled,
                    adherence.percent ?: 0,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            LinearProgressIndicator(
                progress = { (adherence.percent ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyMedicationsState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.medications_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.medications_empty_description),
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.medications_empty_button))
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

private const val LOW_SUPPLY_DAYS = 7
