package org.nikoresu.transcare.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Displays an appointment reminder when its AlarmManager alarm fires. */
class AppointmentReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AppointmentReminderScheduler.ACTION_REMINDER) return

        val notificationId = intent.getIntExtra(
            AppointmentReminderScheduler.EXTRA_NOTIFICATION_ID,
            -1,
        )
        if (notificationId < 0) return

        AppointmentNotifications.show(
            context = context,
            notificationId = notificationId,
            doctorName = intent.getStringExtra(
                AppointmentReminderScheduler.EXTRA_DOCTOR_NAME,
            ).orEmpty(),
            place = intent.getStringExtra(
                AppointmentReminderScheduler.EXTRA_PLACE,
            ).orEmpty(),
            startMillis = intent.getLongExtra(
                AppointmentReminderScheduler.EXTRA_START_MILLIS,
                0L,
            ),
            reminderIndex = intent.getIntExtra(
                AppointmentReminderScheduler.EXTRA_REMINDER_INDEX,
                2,
            ),
        )
    }
}
