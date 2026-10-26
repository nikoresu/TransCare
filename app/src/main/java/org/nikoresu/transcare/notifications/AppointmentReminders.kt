package org.nikoresu.transcare.notifications

import androidx.annotation.StringRes
import java.util.concurrent.TimeUnit
import org.nikoresu.transcare.R

/**
 * One of the reminders that every appointment gets. Reminders are always
 * enabled; there is no per-appointment or per-reminder setting.
 */
internal data class AppointmentReminder(
    val index: Int,
    val offsetMillis: Long,
    @param:StringRes val labelRes: Int,
) {
    /** The moment this reminder should be delivered. */
    fun triggerAt(startMillis: Long): Long = startMillis - offsetMillis
}

/** The fixed reminder schedule shared by scheduling and its unit tests. */
internal object AppointmentReminders {

    /**
     * Defaults are intentionally hardcoded: 3 days, 1 day and 1 hour before
     * the appointment.
     */
    val defaults: List<AppointmentReminder> = listOf(
        AppointmentReminder(
            index = 0,
            offsetMillis = TimeUnit.DAYS.toMillis(3),
            labelRes = R.string.reminder_three_days,
        ),
        AppointmentReminder(
            index = 1,
            offsetMillis = TimeUnit.DAYS.toMillis(1),
            labelRes = R.string.reminder_one_day,
        ),
        AppointmentReminder(
            index = 2,
            offsetMillis = TimeUnit.HOURS.toMillis(1),
            labelRes = R.string.reminder_one_hour,
        ),
    )

    /**
     * Reminders that are still in the future at [nowMillis]. Reminders that
     * would fire in the past are skipped instead of delivered immediately.
     */
    fun futureFor(startMillis: Long, nowMillis: Long): List<AppointmentReminder> =
        defaults.filter { reminder -> reminder.triggerAt(startMillis) > nowMillis }

    /**
     * Stable PendingIntent request code for an appointment/reminder pair.
     * Unique per pair, which keeps alarms and notifications distinct.
     */
    fun requestCode(appointmentId: Long, reminderIndex: Int): Int =
        (appointmentId * defaults.size + reminderIndex).toInt()
}
