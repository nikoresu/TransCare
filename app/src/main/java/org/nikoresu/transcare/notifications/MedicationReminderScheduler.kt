package org.nikoresu.transcare.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.domain.model.MedicationDose
import org.nikoresu.transcare.domain.model.MedicationSchedule

/**
 * Schedules one AlarmManager alarm per medication dose slot. Each alarm is a
 * one-shot; after it fires, [MedicationReminderReceiver] resyncs and schedules
 * the next occurrence.
 *
 * As-needed medications never get automatic reminders.
 */
object MedicationReminderScheduler {

    const val ACTION_REMINDER = "org.nikoresu.transcare.action.MEDICATION_REMINDER"
    const val EXTRA_NOTIFICATION_ID = "notificationId"
    const val EXTRA_NAME = "name"
    const val EXTRA_DOSE = "dose"
    const val EXTRA_TIME_MINUTES = "timeMinutes"

    private const val TAG = "MedReminderScheduler"
    private const val PREFS_NAME = "medication_reminders"
    private const val KEY_REQUEST_CODES = "scheduled_request_codes"
    private const val REMINDER_URI_PREFIX = "transcare://medication-reminder/"
    private const val REQUEST_CODE_BASE = 1_000_000
    private const val MAX_DOSES_PER_MEDICATION = 4

    /** Makes the scheduled alarms match [medications] and [doses]. */
    @Synchronized
    fun sync(
        context: Context,
        medications: List<Medication>,
        doses: List<MedicationDose>,
    ) {
        val appContext = context.applicationContext
        MedicationNotifications.ensureChannel(appContext)

        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousCodes = prefs
            .getStringSet(KEY_REQUEST_CODES, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()

        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val desiredCodes = mutableSetOf<Int>()

        medications.forEach { medication ->
            if (!medication.hasReminder) return@forEach
            doseIndicesFor(medication).forEach { doseIndex ->
                val requestCode = requestCode(medication.id, doseIndex)

                val nextTrigger = nextTriggerMillis(medication, doseIndex, now, doses)
                if (nextTrigger == null) {
                    cancel(appContext, alarmManager, requestCode)
                    return@forEach
                }
                desiredCodes += requestCode
                schedule(
                    context = appContext,
                    alarmManager = alarmManager,
                    medication = medication,
                    doseIndex = doseIndex,
                    requestCode = requestCode,
                    triggerAt = nextTrigger,
                )

                // If today's dose is already logged, clear a notification that
                // may still be showing from an earlier alarm.
                val todayStart = today.atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val loggedToday = doses.any { dose ->
                    dose.medicationId == medication.id &&
                        dose.dayStartMillis == todayStart &&
                        dose.doseIndex == doseIndex
                }
                if (loggedToday) {
                    MedicationNotifications.cancel(appContext, requestCode)
                }
            }
        }

        (previousCodes - desiredCodes).forEach { staleCode ->
            cancel(appContext, alarmManager, staleCode)
        }

        prefs.edit {
            putStringSet(KEY_REQUEST_CODES, desiredCodes.map { it.toString() }.toSet())
        }
    }

    /**
     * Next moment a dose slot should remind, or null when reminders do not
     * apply. Pure and zone-aware so it can be unit tested.
     */
    internal fun nextTriggerMillis(
        medication: Medication,
        doseIndex: Int,
        nowMillis: Long,
        doses: List<MedicationDose>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val timeMinutes = medication.doseTimeMinutes(doseIndex) ?: return null
        if (medication.schedule == MedicationSchedule.AS_NEEDED) return null

        val startDate = Instant.ofEpochMilli(medication.startDayStartMillis)
            .atZone(zone)
            .toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        var date = if (today.isAfter(startDate)) today else startDate
        if (medication.schedule == MedicationSchedule.WEEKLY) {
            while (date.dayOfWeek != startDate.dayOfWeek) {
                date = date.plusDays(1)
            }
        }

        repeat(8) {
            val candidateMillis = date
                .atTime(timeMinutes / 60, timeMinutes % 60)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            val dayStartMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val logged = doses.any { dose ->
                dose.medicationId == medication.id &&
                    dose.dayStartMillis == dayStartMillis &&
                    dose.doseIndex == doseIndex
            }
            if (candidateMillis > nowMillis && !logged) {
                return candidateMillis
            }
            date = if (medication.schedule == MedicationSchedule.WEEKLY) {
                date.plusWeeks(1)
            } else {
                date.plusDays(1)
            }
        }
        return null
    }

    internal fun requestCode(medicationId: Long, doseIndex: Int): Int =
        (REQUEST_CODE_BASE + medicationId * MAX_DOSES_PER_MEDICATION + doseIndex).toInt()

    private fun doseIndicesFor(medication: Medication): List<Int> =
        when (medication.schedule) {
            MedicationSchedule.DAILY -> listOf(0)
            MedicationSchedule.TWICE_DAILY -> listOf(0, 1)
            MedicationSchedule.WEEKLY -> listOf(0)
            MedicationSchedule.AS_NEEDED -> emptyList()
        }

    private fun schedule(
        context: Context,
        alarmManager: AlarmManager,
        medication: Medication,
        doseIndex: Int,
        requestCode: Int,
        triggerAt: Long,
    ) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            reminderIntent(context, medication, doseIndex, requestCode),
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
        MedicationNotifications.cancel(context, requestCode)
    }

    private fun reminderIntent(
        context: Context,
        medication: Medication,
        doseIndex: Int,
        requestCode: Int,
    ): Intent = Intent(context, MedicationReminderReceiver::class.java)
        .setAction(ACTION_REMINDER)
        .setData("$REMINDER_URI_PREFIX$requestCode".toUri())
        .putExtra(EXTRA_NOTIFICATION_ID, requestCode)
        .putExtra(EXTRA_NAME, medication.name)
        .putExtra(EXTRA_DOSE, medication.dose)
        .putExtra(EXTRA_TIME_MINUTES, medication.doseTimeMinutes(doseIndex) ?: 0)

    private fun cancelIntent(context: Context, requestCode: Int): Intent =
        Intent(context, MedicationReminderReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .setData("$REMINDER_URI_PREFIX$requestCode".toUri())

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
}
