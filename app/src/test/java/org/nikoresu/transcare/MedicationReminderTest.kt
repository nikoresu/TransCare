package org.nikoresu.transcare

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.domain.model.MedicationDose
import org.nikoresu.transcare.domain.model.MedicationDoseStatus
import org.nikoresu.transcare.domain.model.MedicationSchedule
import org.nikoresu.transcare.notifications.MedicationReminderScheduler

class MedicationReminderTest {

    private val zone = ZoneId.of("UTC")

    // Monday 26 October 2026.
    private val today = LocalDate.of(2026, 10, 26)

    private fun dayStart(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun medication(
        id: Long = 1L,
        schedule: MedicationSchedule = MedicationSchedule.DAILY,
        reminderMinutes: Int? = 9 * 60,
        start: LocalDate = today,
    ): Medication = Medication(
        id = id,
        name = "Estradiol",
        dose = "2 mg",
        schedule = schedule,
        startDayStartMillis = dayStart(start),
        reminderMinutes = reminderMinutes,
    )

    private fun dose(
        medicationId: Long = 1L,
        date: LocalDate,
        doseIndex: Int = 0,
    ): MedicationDose = MedicationDose(
        medicationId = medicationId,
        dayStartMillis = dayStart(date),
        doseIndex = doseIndex,
        status = MedicationDoseStatus.TAKEN,
        loggedAtMillis = at(date, 8),
    )

    private fun nextTrigger(
        medication: Medication,
        doseIndex: Int = 0,
        nowMillis: Long,
        doses: List<MedicationDose> = emptyList(),
    ): Long? = MedicationReminderScheduler.nextTriggerMillis(
        medication = medication,
        doseIndex = doseIndex,
        nowMillis = nowMillis,
        doses = doses,
        zone = zone,
    )

    @Test
    fun daily_schedulesTodayWhenTheTimeIsStillAhead() {
        val trigger = nextTrigger(
            medication = medication(),
            nowMillis = at(today, 8),
        )

        assertEquals(at(today, 9), trigger)
    }

    @Test
    fun daily_schedulesTomorrowWhenTheTimeHasPassed() {
        val trigger = nextTrigger(
            medication = medication(),
            nowMillis = at(today, 10),
        )

        assertEquals(at(today.plusDays(1), 9), trigger)
    }

    @Test
    fun daily_skipsTodayWhenTheDoseIsAlreadyLogged() {
        val trigger = nextTrigger(
            medication = medication(),
            nowMillis = at(today, 8),
            doses = listOf(dose(date = today)),
        )

        assertEquals(at(today.plusDays(1), 9), trigger)
    }

    @Test
    fun daily_doesNotRescheduleADoseThatWasSkippedToday() {
        val trigger = nextTrigger(
            medication = medication(),
            nowMillis = at(today, 8),
            doses = listOf(
                MedicationDose(
                    medicationId = 1L,
                    dayStartMillis = dayStart(today),
                    doseIndex = 0,
                    status = MedicationDoseStatus.SKIPPED,
                    loggedAtMillis = at(today, 8),
                ),
            ),
        )

        assertEquals(at(today.plusDays(1), 9), trigger)
    }

    @Test
    fun twiceDaily_schedulesBothSlotsOnTheSameDay() {
        val medication = medication(schedule = MedicationSchedule.TWICE_DAILY)

        assertEquals(at(today, 9), nextTrigger(medication, doseIndex = 0, nowMillis = at(today, 8)))
        assertEquals(at(today, 21), nextTrigger(medication, doseIndex = 1, nowMillis = at(today, 8)))
    }

    @Test
    fun twiceDaily_rollsBothSlotsToTomorrowAfterTheLastDose() {
        val medication = medication(schedule = MedicationSchedule.TWICE_DAILY)
        val now = at(today, 22)

        assertEquals(
            at(today.plusDays(1), 9),
            nextTrigger(medication, doseIndex = 0, nowMillis = now),
        )
        assertEquals(
            at(today.plusDays(1), 21),
            nextTrigger(medication, doseIndex = 1, nowMillis = now),
        )
    }

    @Test
    fun twiceDaily_keepsTheSecondSlotWhenTheFirstIsLogged() {
        val medication = medication(schedule = MedicationSchedule.TWICE_DAILY)

        val trigger = nextTrigger(
            medication = medication,
            doseIndex = 1,
            nowMillis = at(today, 8),
            doses = listOf(dose(date = today, doseIndex = 0)),
        )

        assertEquals(at(today, 21), trigger)
    }

    @Test
    fun weekly_repeatsOnTheStartWeekday() {
        val medication = medication(schedule = MedicationSchedule.WEEKLY)
        val wednesday = today.plusDays(2)

        val trigger = nextTrigger(medication = medication, nowMillis = at(wednesday, 8))

        assertEquals(at(today.plusWeeks(1), 9), trigger)
    }

    @Test
    fun weekly_keepsTodayWhenItIsTheStartWeekdayAndTheTimeIsAhead() {
        val medication = medication(schedule = MedicationSchedule.WEEKLY)

        val trigger = nextTrigger(medication = medication, nowMillis = at(today, 8))

        assertEquals(at(today, 9), trigger)
    }

    @Test
    fun medicationBeforeItsStartDate_firstReminderIsOnTheStartDate() {
        val start = today.plusDays(7)
        val medication = medication(start = start)

        val trigger = nextTrigger(medication = medication, nowMillis = at(today, 8))

        assertEquals(at(start, 9), trigger)
    }

    @Test
    fun asNeededMedication_hasNoAutomaticReminder() {
        val medication = medication(
            schedule = MedicationSchedule.AS_NEEDED,
            reminderMinutes = 9 * 60,
        )

        assertNull(nextTrigger(medication = medication, nowMillis = at(today, 8)))
    }

    @Test
    fun medicationWithoutReminder_hasNoTrigger() {
        assertNull(
            nextTrigger(
                medication = medication(reminderMinutes = null),
                nowMillis = at(today, 8),
            ),
        )
    }

    @Test
    fun requestCodes_areUniquePerMedicationAndDoseIndex() {
        val codes = buildList {
            for (medicationId in 1L..10L) {
                for (doseIndex in 0..3) {
                    add(MedicationReminderScheduler.requestCode(medicationId, doseIndex))
                }
            }
        }

        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it > 1_000_000 })
        assertEquals(
            MedicationReminderScheduler.requestCode(1L, 0) + 1,
            MedicationReminderScheduler.requestCode(1L, 1),
        )
    }
}
