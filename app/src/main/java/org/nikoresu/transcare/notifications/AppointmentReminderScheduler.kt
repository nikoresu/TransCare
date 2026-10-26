package org.nikoresu.transcare.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import org.nikoresu.transcare.domain.model.Appointment

/**
 * Schedules the default appointment reminders: 3 days, 1 day and 1 hour
 * before the appointment. Each appointment always gets all future reminders.
 *
 * Alarms are registered with AlarmManager and re-synced whenever appointments
 * change, on boot and after time or timezone changes.
 */
object AppointmentReminderScheduler {

    const val ACTION_REMINDER = "org.nikoresu.transcare.action.APPOINTMENT_REMINDER"
    const val EXTRA_NOTIFICATION_ID = "notificationId"
    const val EXTRA_DOCTOR_NAME = "doctorName"
    const val EXTRA_PLACE = "place"
    const val EXTRA_START_MILLIS = "startMillis"
    const val EXTRA_REMINDER_INDEX = "reminderIndex"

    private const val TAG = "ReminderScheduler"
    private const val PREFS_NAME = "appointment_reminders"
    private const val KEY_REQUEST_CODES = "scheduled_request_codes"
    private const val REMINDER_URI_PREFIX = "transcare://appointment-reminder/"

    /**
     * Makes the scheduled alarms match [appointments]. Stale alarms (deleted
     * or edited appointments) are cancelled, and future reminders are set.
     *
     * Synchronized so the UI and the boot receiver cannot interleave.
     */
    @Synchronized
    fun sync(context: Context, appointments: List<Appointment>) {
        val appContext = context.applicationContext
        AppointmentNotifications.ensureChannel(appContext)

        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousCodes = prefs
            .getStringSet(KEY_REQUEST_CODES, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()

        val now = System.currentTimeMillis()
        val desiredCodes = mutableSetOf<Int>()

        for (appointment in appointments) {
            for (reminder in AppointmentReminders.futureFor(appointment.startMillis, now)) {
                val triggerAt = reminder.triggerAt(appointment.startMillis)
                val requestCode = AppointmentReminders.requestCode(
                    appointmentId = appointment.id,
                    reminderIndex = reminder.index,
                )
                desiredCodes += requestCode
                schedule(
                    context = appContext,
                    alarmManager = alarmManager,
                    appointment = appointment,
                    reminder = reminder,
                    triggerAt = triggerAt,
                    requestCode = requestCode,
                )
            }
        }

        (previousCodes - desiredCodes).forEach { staleCode ->
            cancel(appContext, alarmManager, staleCode)
        }

        prefs.edit {
            putStringSet(
                KEY_REQUEST_CODES,
                desiredCodes.map { it.toString() }.toSet(),
            )
        }
    }

    private fun schedule(
        context: Context,
        alarmManager: AlarmManager,
        appointment: Appointment,
        reminder: AppointmentReminder,
        triggerAt: Long,
        requestCode: Int,
    ) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            reminderIntent(context, appointment, reminder, requestCode),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (canScheduleExactAlarms(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            }
        } catch (exception: SecurityException) {
            Log.w(TAG, "Exact alarm not allowed, using an inexact alarm", exception)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        }
    }

    private fun cancel(context: Context, alarmManager: AlarmManager, requestCode: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            cancelIntent(context, requestCode),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        AppointmentNotifications.cancel(context, requestCode)
    }

    private fun reminderIntent(
        context: Context,
        appointment: Appointment,
        reminder: AppointmentReminder,
        requestCode: Int,
    ): Intent = Intent(context, AppointmentReminderReceiver::class.java)
        .setAction(ACTION_REMINDER)
        .setData("$REMINDER_URI_PREFIX$requestCode".toUri())
        .putExtra(EXTRA_NOTIFICATION_ID, requestCode)
        .putExtra(EXTRA_DOCTOR_NAME, appointment.doctorName)
        .putExtra(EXTRA_PLACE, appointment.place)
        .putExtra(EXTRA_START_MILLIS, appointment.startMillis)
        .putExtra(EXTRA_REMINDER_INDEX, reminder.index)

    private fun cancelIntent(context: Context, requestCode: Int): Intent =
        Intent(context, AppointmentReminderReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .setData("$REMINDER_URI_PREFIX$requestCode".toUri())

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
}
