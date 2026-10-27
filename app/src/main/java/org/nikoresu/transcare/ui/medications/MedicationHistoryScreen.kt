package org.nikoresu.transcare.ui.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.AdherenceSummary
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.domain.model.MedicationAdherence
import org.nikoresu.transcare.domain.model.MedicationDose
import org.nikoresu.transcare.domain.model.MedicationDoseStatus
import org.nikoresu.transcare.ui.formatAppDate

/** Dose history grouped by day, with a 30-day adherence summary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationHistoryScreen(
    medications: List<Medication>,
    doses: List<MedicationDose>,
    onBack: () -> Unit,
    onRemoveDose: (MedicationDose) -> Unit,
) {
    val today = LocalDate.now()
    val adherence = remember(medications, doses, today) {
        MedicationAdherence.summary(
            medications = medications,
            doses = doses,
            startDate = today.minusDays(29),
            endDate = today,
        )
    }
    val medicationsById = remember(medications) {
        medications.associateBy { it.id }
    }
    val loggedDoses = remember(doses, medicationsById) {
        doses
            .filter { it.medicationId in medicationsById }
            .sortedWith(compareByDescending<MedicationDose> { it.loggedAtMillis }
                .thenByDescending { it.dayStartMillis })
    }
    val groupedDoses = remember(loggedDoses) {
        loggedDoses.groupBy { it.dayStartMillis }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.medications_history_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AdherenceSummaryCard(adherence = adherence)
            }

            if (groupedDoses.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.medications_history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                groupedDoses.forEach { (dayStartMillis, dayDoses) ->
                    item(key = "day-$dayStartMillis") {
                        Text(
                            text = formatAppDate(MedicationDose.dateOf(dayStartMillis)),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(
                        items = dayDoses,
                        key = { dose -> "dose-${dose.id}-${dose.medicationId}-${dose.doseIndex}" },
                    ) { dose ->
                        val medication = medicationsById[dose.medicationId]
                        if (medication != null) {
                            DoseHistoryRow(
                                medication = medication,
                                dose = dose,
                                onRemove = { onRemoveDose(dose) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdherenceSummaryCard(adherence: AdherenceSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.medications_adherence_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (adherence.scheduled == 0) {
                    stringResource(R.string.medications_adherence_empty)
                } else {
                    stringResource(
                        R.string.medications_adherence_value,
                        adherence.taken,
                        adherence.scheduled,
                        adherence.percent ?: 0,
                    )
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun DoseHistoryRow(
    medication: Medication,
    dose: MedicationDose,
    onRemove: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = listOfNotNull(
                        medication.dose.takeIf { it.isNotBlank() },
                        if (dose.status == MedicationDoseStatus.TAKEN) {
                            stringResource(
                                R.string.medications_history_taken_at,
                                formatLoggedTime(dose.loggedAtMillis),
                            )
                        } else {
                            stringResource(
                                R.string.medications_history_skipped_at,
                                formatLoggedTime(dose.loggedAtMillis),
                            )
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = if (dose.status == MedicationDoseStatus.TAKEN) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = dose.status.label(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            TextButton(onClick = onRemove) {
                Text(text = stringResource(R.string.medications_history_remove))
            }
        }
    }
}
