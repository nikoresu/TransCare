package org.nikoresu.transcare.ui.medications

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.MedicationDoseStatus
import org.nikoresu.transcare.domain.model.MedicationRoute
import org.nikoresu.transcare.domain.model.MedicationSchedule

@Composable
internal fun MedicationRoute.label(): String = stringResource(
    when (this) {
        MedicationRoute.ORAL -> R.string.medication_route_oral
        MedicationRoute.SUBLINGUAL -> R.string.medication_route_sublingual
        MedicationRoute.PATCH -> R.string.medication_route_patch
        MedicationRoute.GEL -> R.string.medication_route_gel
        MedicationRoute.INJECTION -> R.string.medication_route_injection
        MedicationRoute.OTHER -> R.string.medication_route_other
    },
)

@Composable
internal fun MedicationSchedule.label(): String = stringResource(
    when (this) {
        MedicationSchedule.DAILY -> R.string.medication_schedule_daily
        MedicationSchedule.TWICE_DAILY -> R.string.medication_schedule_twice_daily
        MedicationSchedule.WEEKLY -> R.string.medication_schedule_weekly
        MedicationSchedule.AS_NEEDED -> R.string.medication_schedule_as_needed
    },
)

@Composable
internal fun MedicationDoseStatus.label(): String = stringResource(
    when (this) {
        MedicationDoseStatus.TAKEN -> R.string.medication_status_taken
        MedicationDoseStatus.SKIPPED -> R.string.medication_status_skipped
    },
)

/** Formats a time-of-day stored as minutes after midnight. */
@Composable
internal fun formatMedicationTime(minutes: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .format(LocalTime.of(minutes / 60, minutes % 60))
}

/** Formats the moment a dose was logged. */
@Composable
internal fun formatLoggedTime(epochMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
}
