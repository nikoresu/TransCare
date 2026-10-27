package org.nikoresu.transcare.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.nikoresu.transcare.data.MedicationRepository

/** Shows a dose reminder and schedules the next occurrence. */
class MedicationReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != MedicationReminderScheduler.ACTION_REMINDER) return

        val notificationId = intent.getIntExtra(
            MedicationReminderScheduler.EXTRA_NOTIFICATION_ID,
            -1,
        )
        if (notificationId < 0) return

        MedicationNotifications.show(
            context = context,
            notificationId = notificationId,
            name = intent.getStringExtra(MedicationReminderScheduler.EXTRA_NAME).orEmpty(),
            dose = intent.getStringExtra(MedicationReminderScheduler.EXTRA_DOSE).orEmpty(),
            timeMinutes = intent.getIntExtra(
                MedicationReminderScheduler.EXTRA_TIME_MINUTES,
                0,
            ),
        )

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MedicationReminderScheduler.sync(
                    context = appContext,
                    medications = MedicationRepository.readMedications(appContext),
                    doses = MedicationRepository.readDoses(appContext),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
