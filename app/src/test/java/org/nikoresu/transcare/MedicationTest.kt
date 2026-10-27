package org.nikoresu.transcare

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.domain.model.MedicationAdherence
import org.nikoresu.transcare.domain.model.MedicationDose
import org.nikoresu.transcare.domain.model.MedicationDoseStatus
import org.nikoresu.transcare.domain.model.MedicationRoute
import org.nikoresu.transcare.domain.model.MedicationSchedule

class MedicationTest {

    private val startDate = LocalDate.of(2026, 10, 26)
    private val startDayStartMillis = Medication.dayStartMillisOf(startDate)

    private fun medication(
        id: Long = 1L,
        schedule: MedicationSchedule = MedicationSchedule.DAILY,
        reminderMinutes: Int? = 9 * 60,
        supplyDoses: Int? = null,
        start: LocalDate = startDate,
    ): Medication = Medication(
        id = id,
        name = "Estradiol",
        dose = "2 mg",
        route = MedicationRoute.SUBLINGUAL,
        schedule = schedule,
        startDayStartMillis = Medication.dayStartMillisOf(start),
        reminderMinutes = reminderMinutes,
        supplyDoses = supplyDoses,
    )

    private fun dose(
        medicationId: Long = 1L,
        date: LocalDate,
        doseIndex: Int = 0,
        status: MedicationDoseStatus = MedicationDoseStatus.TAKEN,
    ): MedicationDose = MedicationDose(
        medicationId = medicationId,
        dayStartMillis = Medication.dayStartMillisOf(date),
        doseIndex = doseIndex,
        status = status,
        loggedAtMillis = Medication.dayStartMillisOf(date) + 60_000L,
    )

    @Test
    fun scheduledDoseIndices_followTheSchedule() {
        assertEquals(
            listOf(0),
            medication(schedule = MedicationSchedule.DAILY).scheduledDoseIndices(startDate),
        )
        assertEquals(
            listOf(0, 1),
            medication(schedule = MedicationSchedule.TWICE_DAILY)
                .scheduledDoseIndices(startDate),
        )
        assertEquals(
            listOf(0),
            medication(schedule = MedicationSchedule.WEEKLY).scheduledDoseIndices(startDate),
        )
        assertTrue(
            medication(schedule = MedicationSchedule.AS_NEEDED)
                .scheduledDoseIndices(startDate)
                .isEmpty(),
        )
    }

    @Test
    fun scheduledDoseIndices_weeklyOnlyOnStartWeekday() {
        val weekly = medication(schedule = MedicationSchedule.WEEKLY)
        assertEquals(DayOfWeek.MONDAY, startDate.dayOfWeek)

        assertTrue(weekly.scheduledDoseIndices(startDate.plusDays(1)).isEmpty())
        assertEquals(listOf(0), weekly.scheduledDoseIndices(startDate.plusWeeks(1)))
    }

    @Test
    fun scheduledDoseIndices_emptyBeforeStartDate() {
        val medication = medication(start = startDate.plusDays(3))

        assertTrue(medication.scheduledDoseIndices(startDate).isEmpty())
        assertEquals(listOf(0), medication.scheduledDoseIndices(startDate.plusDays(3)))
    }

    @Test
    fun doseTimeMinutes_twiceDailyIsTwelveHoursApartAndSorted() {
        val evening = medication(
            schedule = MedicationSchedule.TWICE_DAILY,
            reminderMinutes = 20 * 60,
        )

        assertEquals(8 * 60, evening.doseTimeMinutes(0))
        assertEquals(20 * 60, evening.doseTimeMinutes(1))
    }

    @Test
    fun doseTimeMinutes_twiceDailyHandlesMorningBase() {
        val morning = medication(
            schedule = MedicationSchedule.TWICE_DAILY,
            reminderMinutes = 9 * 60,
        )

        assertEquals(9 * 60, morning.doseTimeMinutes(0))
        assertEquals(21 * 60, morning.doseTimeMinutes(1))
    }

    @Test
    fun doseTimeMinutes_nullWithoutReminder() {
        assertNull(medication(reminderMinutes = null).doseTimeMinutes(0))
    }

    @Test
    fun estimatedDaysOfSupply_usesTheDailyDoseRate() {
        assertEquals(30, medication(supplyDoses = 30).estimatedDaysOfSupply)
        assertEquals(
            15,
            medication(
                schedule = MedicationSchedule.TWICE_DAILY,
                supplyDoses = 30,
            ).estimatedDaysOfSupply,
        )
        assertEquals(
            210,
            medication(
                schedule = MedicationSchedule.WEEKLY,
                supplyDoses = 30,
            ).estimatedDaysOfSupply,
        )
        assertNull(
            medication(
                schedule = MedicationSchedule.AS_NEEDED,
                supplyDoses = 30,
            ).estimatedDaysOfSupply,
        )
    }

    @Test
    fun estimatedDaysOfSupply_nullWithoutSupply() {
        assertNull(medication(supplyDoses = null).estimatedDaysOfSupply)
    }

    @Test
    fun hasReminder_ignoresAsNeededMedications() {
        assertTrue(medication().hasReminder)
        assertTrue(!medication(schedule = MedicationSchedule.AS_NEEDED).hasReminder)
        assertTrue(!medication(reminderMinutes = null).hasReminder)
    }

    @Test
    fun adherence_countsScheduledSlotsAndTakenDoses() {
        val medications = listOf(
            medication(id = 1L, start = startDate.minusDays(2)),
            medication(id = 2L, schedule = MedicationSchedule.TWICE_DAILY, start = startDate),
            medication(id = 3L, schedule = MedicationSchedule.AS_NEEDED),
        )
        val doses = listOf(
            dose(medicationId = 1L, date = startDate.minusDays(2)),
            dose(medicationId = 1L, date = startDate.minusDays(1), status = MedicationDoseStatus.SKIPPED),
            dose(medicationId = 2L, date = startDate, doseIndex = 1),
            dose(medicationId = 3L, date = startDate),
        )

        val summary = MedicationAdherence.summary(
            medications = medications,
            doses = doses,
            startDate = startDate.minusDays(2),
            endDate = startDate,
        )

        // Med 1: 3 days x 1 dose. Med 2: 1 day x 2 doses. Med 3 is as-needed.
        assertEquals(5, summary.scheduled)
        assertEquals(2, summary.taken)
        assertEquals(40, summary.percent)
    }

    @Test
    fun adherence_clampsToMedicationStartDate() {
        val medication = medication(start = startDate.minusDays(1))

        val summary = MedicationAdherence.summary(
            medications = listOf(medication),
            doses = listOf(dose(date = startDate.minusDays(1))),
            startDate = startDate.minusDays(10),
            endDate = startDate,
        )

        assertEquals(2, summary.scheduled)
        assertEquals(1, summary.taken)
        assertEquals(50, summary.percent)
    }

    @Test
    fun adherence_isEmptyWhenNothingIsScheduled() {
        val summary = MedicationAdherence.summary(
            medications = emptyList(),
            doses = emptyList(),
            startDate = startDate.minusDays(29),
            endDate = startDate,
        )

        assertEquals(0, summary.scheduled)
        assertEquals(0, summary.taken)
        assertNull(summary.percent)
    }

    @Test
    fun adherence_percentRoundsToNearestWholeNumber() {
        val medications = listOf(
            medication(id = 1L, start = startDate.minusDays(6)),
        )
        val doses = listOf(
            dose(medicationId = 1L, date = startDate.minusDays(6)),
            dose(medicationId = 1L, date = startDate.minusDays(5)),
        )

        val summary = MedicationAdherence.summary(
            medications = medications,
            doses = doses,
            startDate = startDate.minusDays(6),
            endDate = startDate,
        )

        assertEquals(7, summary.scheduled)
        assertEquals(2, summary.taken)
        assertEquals(29, summary.percent)
    }

    @Test
    fun doseStatus_parsesKnownValuesAndRejectsUnknownOnes() {
        assertEquals(MedicationDoseStatus.TAKEN, MedicationDoseStatus.fromStorageValue("TAKEN"))
        assertEquals(MedicationDoseStatus.SKIPPED, MedicationDoseStatus.fromStorageValue("SKIPPED"))
        assertNull(MedicationDoseStatus.fromStorageValue("MAYBE"))
        assertNull(MedicationDoseStatus.fromStorageValue(null))
    }

    @Test
    fun scheduleAndRoute_fallBackForUnknownValues() {
        assertEquals(
            MedicationSchedule.TWICE_DAILY,
            MedicationSchedule.fromStorageValue("TWICE_DAILY"),
        )
        assertEquals(MedicationSchedule.DAILY, MedicationSchedule.fromStorageValue("HOURLY"))
        assertEquals(MedicationRoute.PATCH, MedicationRoute.fromStorageValue("PATCH"))
        assertEquals(MedicationRoute.OTHER, MedicationRoute.fromStorageValue("SPRAY"))
    }

    @Test
    fun dateOf_roundTripsDayStartMillis() {
        assertEquals(startDate, Medication.dateOf(startDayStartMillis))
    }
}
