package org.nikoresu.transcare.data

import org.json.JSONObject
import org.nikoresu.transcare.domain.model.Appointment
import org.nikoresu.transcare.domain.model.DiaryEntry
import org.nikoresu.transcare.domain.model.ExamResult
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.domain.model.MedicationDose

/** Serializes an appointment using the same keys as the internal storage file. */
internal fun Appointment.toJsonObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("doctorName", doctorName)
    .put("place", place)
    .put("dayStartMillis", dayStartMillis)
    .put("hour", hour)
    .put("minute", minute)

/** Serializes an exam result using the same keys as the internal storage file. */
internal fun ExamResult.toJsonObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("metric", metric.name)
    .put("value", value)
    .put("dayStartMillis", dayStartMillis)
    .put("note", note)

/** Serializes a diary entry using the same keys as the internal storage file. */
internal fun DiaryEntry.toJsonObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("dayStartMillis", dayStartMillis)
    .put("title", title)
    .put("text", text)
    .put("type", type.name)

/** Serializes a medication using the same keys as the internal storage file. */
internal fun Medication.toJsonObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("dose", dose)
    .put("route", route.name)
    .put("schedule", schedule.name)
    .put("startDayStartMillis", startDayStartMillis)
    .put("reminderMinutes", reminderMinutes ?: JSONObject.NULL)
    .put("supplyDoses", supplyDoses ?: JSONObject.NULL)
    .put("notes", notes)

/** Serializes a logged dose using the same keys as the internal storage file. */
internal fun MedicationDose.toJsonObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("medicationId", medicationId)
    .put("dayStartMillis", dayStartMillis)
    .put("doseIndex", doseIndex)
    .put("status", status.name)
    .put("loggedAtMillis", loggedAtMillis)
