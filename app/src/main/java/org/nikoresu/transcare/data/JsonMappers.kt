package org.nikoresu.transcare.data

import org.json.JSONArray
import org.json.JSONObject
import org.nikoresu.transcare.domain.model.Appointment
import org.nikoresu.transcare.domain.model.Attachment
import org.nikoresu.transcare.domain.model.DiaryEntry
import org.nikoresu.transcare.domain.model.ExamResult
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.domain.model.MedicationDose

/** Serializes an appointment using the same keys as the internal storage file. */
internal fun Appointment.toJsonObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("doctorName", doctorName)
    .put("place", place)
    .put("notes", notes)
    .put("dayStartMillis", dayStartMillis)
    .put("hour", hour)
    .put("minute", minute)
    .put("attachments", attachments.toJsonArray())

/** Serializes an exam result using the same keys as the internal storage file. */
internal fun ExamResult.toJsonObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("metric", metric.name)
    .put("value", value)
    .put("dayStartMillis", dayStartMillis)
    .put("note", note)
    .put("attachments", attachments.toJsonArray())

/** Serializes a diary entry using the same keys as the internal storage file. */
internal fun DiaryEntry.toJsonObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("dayStartMillis", dayStartMillis)
    .put("title", title)
    .put("text", text)
    .put("type", type.name)
    .put("attachments", attachments.toJsonArray())

/** Serializes attachment metadata. The file itself lives in the vault. */
internal fun Attachment.toJsonObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("fileName", fileName)
    .put("mimeType", mimeType)
    .put("sizeBytes", sizeBytes)
    .put("addedAtMillis", addedAtMillis)

internal fun List<Attachment>.toJsonArray(): JSONArray {
    val array = JSONArray()
    forEach { array.put(it.toJsonObject()) }
    return array
}

/** Reads attachment metadata from an owner JSON object. */
internal fun JSONObject.optAttachments(): List<Attachment> =
    optJSONArray("attachments")?.toAttachments().orEmpty()

internal fun JSONArray.toAttachments(): List<Attachment> {
    val attachments = mutableListOf<Attachment>()
    for (index in 0 until length()) {
        val json = optJSONObject(index) ?: continue
        val attachment = json.toAttachmentOrNull() ?: continue
        attachments += attachment
    }
    return attachments
}

internal fun JSONObject.toAttachmentOrNull(): Attachment? {
    val id = optString("id")
    val fileName = optString("fileName").trim()
    val mimeType = optString("mimeType").trim()
    val sizeBytes = optLong("sizeBytes", -1L)
    val addedAtMillis = optLong("addedAtMillis", 0L)
    if (
        !AttachmentVault.isValidId(id) ||
        fileName.isEmpty() ||
        !Attachment.isSupportedMimeType(mimeType) ||
        sizeBytes < 0L ||
        addedAtMillis < 0L
    ) {
        return null
    }
    return Attachment(
        id = id,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        addedAtMillis = addedAtMillis,
    )
}

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
