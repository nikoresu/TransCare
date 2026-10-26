package org.nikoresu.transcare

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nikoresu.transcare.notifications.AppointmentReminders

class AppointmentReminderTest {

    private val start = 1_800_000_000_000L

    @Test
    fun defaults_areThreeDaysOneDayAndOneHourBefore() {
        val offsets = AppointmentReminders.defaults.map { it.offsetMillis }

        assertEquals(
            listOf(
                TimeUnit.DAYS.toMillis(3),
                TimeUnit.DAYS.toMillis(1),
                TimeUnit.HOURS.toMillis(1),
            ),
            offsets,
        )
        assertEquals(listOf(0, 1, 2), AppointmentReminders.defaults.map { it.index })
    }

    @Test
    fun futureFor_returnsAllThreeWhenAppointmentIsWellAhead() {
        val reminders = AppointmentReminders.futureFor(
            startMillis = start,
            nowMillis = start - TimeUnit.DAYS.toMillis(10),
        )

        assertEquals(3, reminders.size)
        assertEquals(
            listOf(
                start - TimeUnit.DAYS.toMillis(3),
                start - TimeUnit.DAYS.toMillis(1),
                start - TimeUnit.HOURS.toMillis(1),
            ),
            reminders.map { it.triggerAt(start) },
        )
    }

    @Test
    fun futureFor_skipsTheThreeDayReminderWhenOnlyTwoDaysRemain() {
        val reminders = AppointmentReminders.futureFor(
            startMillis = start,
            nowMillis = start - TimeUnit.DAYS.toMillis(2),
        )

        assertEquals(listOf(1, 2), reminders.map { it.index })
    }

    @Test
    fun futureFor_skipsRemindersThatWouldFireInThePast() {
        val reminders = AppointmentReminders.futureFor(
            startMillis = start,
            nowMillis = start - TimeUnit.DAYS.toMillis(2),
        )

        assertTrue(reminders.all { it.triggerAt(start) > start - TimeUnit.DAYS.toMillis(2) })
    }

    @Test
    fun futureFor_returnsOnlyTheHourReminderTheDayBefore() {
        val reminders = AppointmentReminders.futureFor(
            startMillis = start,
            nowMillis = start - TimeUnit.HOURS.toMillis(20),
        )

        assertEquals(listOf(2), reminders.map { it.index })
    }

    @Test
    fun futureFor_returnsNothingOnceTheAppointmentIsClose() {
        val reminders = AppointmentReminders.futureFor(
            startMillis = start,
            nowMillis = start - TimeUnit.MINUTES.toMillis(30),
        )

        assertTrue(reminders.isEmpty())
    }

    @Test
    fun futureFor_returnsNothingForPastAppointments() {
        val reminders = AppointmentReminders.futureFor(
            startMillis = start,
            nowMillis = start + TimeUnit.HOURS.toMillis(1),
        )

        assertTrue(reminders.isEmpty())
    }

    @Test
    fun triggerAt_excludesAReminderThatIsDueExactlyNow() {
        val reminders = AppointmentReminders.futureFor(
            startMillis = start,
            nowMillis = start - TimeUnit.DAYS.toMillis(3),
        )

        assertTrue(reminders.none { it.index == 0 })
        assertEquals(listOf(1, 2), reminders.map { it.index })
    }

    @Test
    fun requestCode_isUniquePerAppointmentAndReminder() {
        val codes = (1L..10L).flatMap { appointmentId ->
            AppointmentReminders.defaults.map { reminder ->
                AppointmentReminders.requestCode(appointmentId, reminder.index)
            }
        }

        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun requestCode_isStableForTheSamePair() {
        assertEquals(
            AppointmentReminders.requestCode(42L, 1),
            AppointmentReminders.requestCode(42L, 1),
        )
        assertTrue(
            AppointmentReminders.requestCode(42L, 0) !=
                AppointmentReminders.requestCode(42L, 1),
        )
    }
}
