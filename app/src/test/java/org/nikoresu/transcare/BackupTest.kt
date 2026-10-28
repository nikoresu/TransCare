package org.nikoresu.transcare

import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nikoresu.transcare.data.BackupManager
import org.nikoresu.transcare.domain.model.Appointment
import org.nikoresu.transcare.domain.model.DiaryEntry
import org.nikoresu.transcare.domain.model.DiaryEntryType
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.domain.model.ExamResult
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.domain.model.MedicationDose
import org.nikoresu.transcare.domain.model.MedicationDoseStatus
import org.nikoresu.transcare.domain.model.MedicationRoute
import org.nikoresu.transcare.domain.model.MedicationSchedule

class BackupTest {

    private val day = LocalDate.of(2026, 10, 23)
    private val dayStartMillis = DiaryEntry.dayStartMillisOf(day)

    @Test
    fun fileNameUsesIsoDate() {
        assertEquals(
            "TransCare-Data-2026-10-23.json",
            BackupManager.fileName(LocalDate.of(2026, 10, 23)),
        )
    }

    @Test
    fun buildBackupJson_includesDiaryEntries() {
        val json = BackupManager.buildBackupJson(
            appointments = emptyList(),
            results = emptyList(),
            diaryEntries = listOf(
                DiaryEntry(dayStartMillis = dayStartMillis, title = "Morning", text = "Slept well."),
                DiaryEntry(
                    dayStartMillis = dayStartMillis,
                    text = "Only text.",
                    type = DiaryEntryType.THOUGHTS,
                ),
            ),
            exportedAt = Instant.parse("2026-10-23T12:00:00Z"),
        )

        val root = JSONObject(json)
        assertEquals(4, root.getInt("schemaVersion"))
        val entries = root.getJSONArray("diaryEntries")
        assertEquals(2, entries.length())
        assertEquals("NOTES", entries.getJSONObject(0).getString("type"))
        assertEquals("THOUGHTS", entries.getJSONObject(1).getString("type"))
    }

    @Test
    fun parseBackupJson_restoresDiaryEntries() {
        val entry = DiaryEntry(
            dayStartMillis = dayStartMillis,
            title = "Morning",
            text = "Slept well.",
            type = DiaryEntryType.THOUGHTS,
        )
        val appointment = Appointment(
            doctorName = "Dr. Rivera",
            place = "Clinic",
            notes = "Ask about the new dosage.",
            dayStartMillis = dayStartMillis,
            hour = 9,
            minute = 30,
        )
        val result = ExamResult(
            metric = ExamMetric.ESTRADIOL,
            value = 150.0,
            dayStartMillis = dayStartMillis,
            note = "Fasting",
        )

        val json = BackupManager.buildBackupJson(
            appointments = listOf(appointment),
            results = listOf(result),
            diaryEntries = listOf(entry),
        )

        val contents = BackupManager.parseBackupJson(json)

        assertEquals(1, contents.appointments.size)
        assertEquals(1, contents.examResults.size)
        assertEquals(1, contents.diaryEntries.size)
        assertEquals(0, contents.skippedEntries)
        assertEquals(appointment, contents.appointments.single())
        val restored = contents.diaryEntries.single()
        assertEquals(entry.dayStartMillis, restored.dayStartMillis)
        assertEquals(entry.title, restored.title)
        assertEquals(entry.text, restored.text)
        assertEquals(DiaryEntryType.THOUGHTS, restored.type)
    }

    @Test
    fun parseBackupJson_restoresAppointmentNotes() {
        val appointment = Appointment(
            doctorName = "Dr. Rivera",
            place = "Clinic",
            notes = "Ask about estrogen dosage.\nBring the last lab report.",
            dayStartMillis = dayStartMillis,
            hour = 9,
            minute = 30,
        )
        val json = BackupManager.buildBackupJson(
            appointments = listOf(appointment),
            results = emptyList(),
            diaryEntries = emptyList(),
        )

        val contents = BackupManager.parseBackupJson(json)

        assertEquals(appointment.notes, contents.appointments.single().notes)
    }

    @Test
    fun parseBackupJson_defaultsMissingAppointmentNotesToEmpty() {
        val root = JSONObject().put(
            "appointments",
            JSONArray().put(
                JSONObject()
                    .put("doctorName", "Dr. Rivera")
                    .put("place", "Clinic")
                    .put("dayStartMillis", dayStartMillis)
                    .put("hour", 9)
                    .put("minute", 30),
            ),
        )

        val contents = BackupManager.parseBackupJson(root.toString())

        assertEquals("", contents.appointments.single().notes)
    }

    @Test
    fun parseBackupJson_acceptsBackupWithoutDiaryEntries() {
        val root = JSONObject()
            .put("app", "TransCare")
            .put("schemaVersion", 1)
            .put("appointments", JSONArray())
            .put("examResults", JSONArray())

        val contents = BackupManager.parseBackupJson(root.toString())

        assertTrue(contents.diaryEntries.isEmpty())
        assertEquals(0, contents.skippedEntries)
    }

    @Test
    fun parseBackupJson_acceptsDiaryEntriesSnakeCaseKey() {
        val root = JSONObject()
            .put("diary_entries", JSONArray().put(validEntryJson()))

        val contents = BackupManager.parseBackupJson(root.toString())

        assertEquals(1, contents.diaryEntries.size)
    }

    @Test
    fun parseBackupJson_defaultsMissingDiaryTypeToNotes() {
        val root = JSONObject()
            .put("diaryEntries", JSONArray().put(validEntryJson()))

        val contents = BackupManager.parseBackupJson(root.toString())

        assertEquals(DiaryEntryType.NOTES, contents.diaryEntries.single().type)
    }

    @Test
    fun parseBackupJson_defaultsUnknownDiaryTypeToNotes() {
        val root = JSONObject()
            .put("diaryEntries", JSONArray().put(validEntryJson().put("type", "DREAMS")))

        val contents = BackupManager.parseBackupJson(root.toString())

        assertEquals(DiaryEntryType.NOTES, contents.diaryEntries.single().type)
    }

    @Test
    fun parseBackupJson_skipsInvalidDiaryEntries() {
        val entries = JSONArray()
            .put(validEntryJson())
            .put(JSONObject().put("dayStartMillis", dayStartMillis).put("title", " ").put("text", ""))
            .put(JSONObject().put("dayStartMillis", -1L).put("title", "Bad day").put("text", "Text"))
            .put("not an object")

        val root = JSONObject().put("diaryEntries", entries)

        val contents = BackupManager.parseBackupJson(root.toString())

        assertEquals(1, contents.diaryEntries.size)
        assertEquals(3, contents.skippedEntries)
        assertEquals("Morning", contents.diaryEntries.single().title)
    }

    @Test
    fun buildBackupJson_includesMedicationsAndDoses() {
        val json = BackupManager.buildBackupJson(
            appointments = emptyList(),
            results = emptyList(),
            diaryEntries = emptyList(),
            medications = listOf(medication()),
            medicationDoses = listOf(dose()),
            exportedAt = Instant.parse("2026-10-23T12:00:00Z"),
        )

        val root = JSONObject(json)
        assertEquals(4, root.getInt("schemaVersion"))
        assertEquals(1, root.getJSONArray("medications").length())
        assertEquals(1, root.getJSONArray("medicationDoses").length())
        val medication = root.getJSONArray("medications").getJSONObject(0)
        assertEquals("Estradiol", medication.getString("name"))
        assertEquals("SUBLINGUAL", medication.getString("route"))
        assertEquals(9 * 60, medication.getInt("reminderMinutes"))
        val dose = root.getJSONArray("medicationDoses").getJSONObject(0)
        assertEquals("TAKEN", dose.getString("status"))
    }

    @Test
    fun parseBackupJson_restoresMedicationsAndDoses() {
        val originalMedication = medication(id = 7L)
        val originalDose = dose(medicationId = 7L)
        val json = BackupManager.buildBackupJson(
            appointments = emptyList(),
            results = emptyList(),
            diaryEntries = emptyList(),
            medications = listOf(originalMedication),
            medicationDoses = listOf(originalDose),
        )

        val contents = BackupManager.parseBackupJson(json)

        assertEquals(1, contents.medications.size)
        assertEquals(1, contents.medicationDoses.size)
        assertEquals(0, contents.skippedEntries)
        assertEquals(originalMedication, contents.medications.single())
        assertEquals(originalDose, contents.medicationDoses.single())
    }

    @Test
    fun parseBackupJson_acceptsBackupWithoutMedications() {
        val root = JSONObject()
            .put("app", "TransCare")
            .put("schemaVersion", 3)
            .put("appointments", JSONArray())

        val contents = BackupManager.parseBackupJson(root.toString())

        assertTrue(contents.medications.isEmpty())
        assertTrue(contents.medicationDoses.isEmpty())
        assertEquals(0, contents.skippedEntries)
    }

    @Test
    fun parseBackupJson_acceptsMedicationDosesSnakeCaseKey() {
        val root = JSONObject()
            .put("medications", JSONArray().put(validMedicationJson().put("id", 1L)))
            .put("medication_doses", JSONArray().put(validDoseJson().put("medicationId", 1L)))

        val contents = BackupManager.parseBackupJson(root.toString())

        assertEquals(1, contents.medications.size)
        assertEquals(1, contents.medicationDoses.size)
    }

    @Test
    fun parseBackupJson_skipsInvalidMedicationsAndOrphanDoses() {
        val medications = JSONArray()
            .put(validMedicationJson().put("id", 1L))
            .put(validMedicationJson().put("name", " "))
            .put(JSONObject().put("name", "No start date"))
            .put("not an object")
        val doses = JSONArray()
            .put(validDoseJson().put("medicationId", 1L))
            .put(validDoseJson().put("medicationId", 99L))
            .put(validDoseJson().put("medicationId", 1L).put("status", "MAYBE"))
            .put(validDoseJson().put("medicationId", 1L).put("doseIndex", -1))

        val root = JSONObject()
            .put("medications", medications)
            .put("medicationDoses", doses)

        val contents = BackupManager.parseBackupJson(root.toString())

        assertEquals(1, contents.medications.size)
        assertEquals(1, contents.medicationDoses.size)
        assertEquals(6, contents.skippedEntries)
    }

    private fun medication(id: Long = 1L): Medication = Medication(
        id = id,
        name = "Estradiol",
        dose = "2 mg",
        route = MedicationRoute.SUBLINGUAL,
        schedule = MedicationSchedule.DAILY,
        startDayStartMillis = dayStartMillis,
        reminderMinutes = 9 * 60,
        supplyDoses = 30,
        notes = "After breakfast",
    )

    private fun dose(medicationId: Long = 1L): MedicationDose = MedicationDose(
        medicationId = medicationId,
        dayStartMillis = dayStartMillis,
        doseIndex = 0,
        status = MedicationDoseStatus.TAKEN,
        loggedAtMillis = dayStartMillis + 60_000L,
    )

    private fun validMedicationJson(): JSONObject = JSONObject()
        .put("name", "Estradiol")
        .put("dose", "2 mg")
        .put("route", "SUBLINGUAL")
        .put("schedule", "DAILY")
        .put("startDayStartMillis", dayStartMillis)
        .put("reminderMinutes", 9 * 60)
        .put("supplyDoses", 30)
        .put("notes", "After breakfast")

    private fun validDoseJson(): JSONObject = JSONObject()
        .put("dayStartMillis", dayStartMillis)
        .put("doseIndex", 0)
        .put("status", "TAKEN")
        .put("loggedAtMillis", dayStartMillis + 60_000L)

    private fun validEntryJson(): JSONObject = JSONObject()
        .put("dayStartMillis", dayStartMillis)
        .put("title", "Morning")
        .put("text", "Slept well.")
}
