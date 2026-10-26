package org.nikoresu.transcare.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Formats a calendar day using the app language and the device's date order. */
@Composable
internal fun formatAppDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMd")
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}
