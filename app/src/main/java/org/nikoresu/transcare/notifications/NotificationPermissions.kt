package org.nikoresu.transcare.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri

object NotificationPermissions {

    /**
     * POST_NOTIFICATIONS only exists from Android 13. The value is inlined at
     * compile time, so referencing it on older versions is harmless; the app
     * only asks for it on Android 13 and newer.
     */
    @SuppressLint("InlinedApi")
    val POST_NOTIFICATIONS_PERMISSION: String = Manifest.permission.POST_NOTIFICATIONS

    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)
                ?.canScheduleExactAlarms() == true

    /** Opens the system screen where the user can allow exact alarms. */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            Log.w(TAG, "No exact alarm settings screen available", exception)
        }
    }

    private const val TAG = "NotificationPermissions"
}
