package org.nikoresu.transcare.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.nikoresu.transcare.data.AppointmentRepository
import org.nikoresu.transcare.data.MedicationRepository

/**
 * Restores appointment reminder alarms after a reboot, app update, or clock
 * or timezone change, since AlarmManager alarms do not survive those events.
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action !in RESCHEDULE_ACTIONS) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appointments = AppointmentRepository.readAppointments(appContext)
                AppointmentReminderScheduler.sync(appContext, appointments)
                MedicationReminderScheduler.sync(
                    context = appContext,
                    medications = MedicationRepository.readMedications(appContext),
                    doses = MedicationRepository.readDoses(appContext),
                )
            } catch (exception: Exception) {
                Log.e(TAG, "Could not reschedule reminders", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ReminderReschedule"

        /** System broadcasts after which AlarmManager alarms need restoring. */
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
