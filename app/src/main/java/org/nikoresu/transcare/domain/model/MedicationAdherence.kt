package org.nikoresu.transcare.domain.model

import java.time.LocalDate
import kotlin.math.roundToInt

/** How many scheduled doses were taken in a period. */
data class AdherenceSummary(
    val taken: Int = 0,
    val scheduled: Int = 0,
) {
    /** Percentage taken, or null when nothing was scheduled. */
    val percent: Int?
        get() = if (scheduled == 0) null else (taken * 100.0 / scheduled).roundToInt()
}

/** Pure calculations for the medication adherence summary. */
object MedicationAdherence {

    /**
     * Counts scheduled dose slots between [startDate] and [endDate] (inclusive)
     * and how many of them have a TAKEN log. As-needed medications are ignored.
     */
    fun summary(
        medications: List<Medication>,
        doses: List<MedicationDose>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): AdherenceSummary {
        if (endDate.isBefore(startDate)) return AdherenceSummary()

        val takenKeys = doses
            .asSequence()
            .filter { it.status == MedicationDoseStatus.TAKEN }
            .map { DoseKey(it.medicationId, it.dayStartMillis, it.doseIndex) }
            .toSet()

        var scheduled = 0
        var taken = 0
        medications.forEach { medication ->
            if (medication.schedule == MedicationSchedule.AS_NEEDED) return@forEach
            var date = maxOf(startDate, medication.startDate)
            while (!date.isAfter(endDate)) {
                val dayStartMillis = Medication.dayStartMillisOf(date)
                medication.scheduledDoseIndices(date).forEach { doseIndex ->
                    scheduled++
                    if (DoseKey(medication.id, dayStartMillis, doseIndex) in takenKeys) {
                        taken++
                    }
                }
                date = date.plusDays(1)
            }
        }
        return AdherenceSummary(taken = taken, scheduled = scheduled)
    }

    private data class DoseKey(
        val medicationId: Long,
        val dayStartMillis: Long,
        val doseIndex: Int,
    )
}
