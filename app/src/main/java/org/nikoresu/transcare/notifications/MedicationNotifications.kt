package org.nikoresu.transcare.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.nikoresu.transcare.MainActivity
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.settings.LocalePreferences

/** Channel and notification rendering for medication dose reminders. */
object MedicationNotifications {

    const val CHANNEL_ID = "medication_reminders"

    fun ensureChannel(context: Context) {
        val localized = LocalePreferences.wrap(context)
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.medication_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = localized.getString(R.string.medication_notification_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun show(
        context: Context,
        notificationId: Int,
        name: String,
        dose: String,
        timeMinutes: Int,
    ) {
        val localized = LocalePreferences.wrap(context)
        ensureChannel(localized)
        if (!canPostNotifications(context)) return

        val locale = localized.resources.configuration.locales[0]
        val formattedTime = DateTimeFormatter
            .ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale)
            .format(LocalTime.of(timeMinutes / 60, timeMinutes % 60))
        val displayName = name.ifBlank {
            localized.getString(R.string.medication_notification_fallback)
        }
        val text = if (dose.isBlank()) {
            localized.getString(R.string.medication_notification_text_no_dose, formattedTime)
        } else {
            localized.getString(R.string.medication_notification_text, dose, formattedTime)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(localized.getString(R.string.medication_notification_title, displayName))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun cancel(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
}
