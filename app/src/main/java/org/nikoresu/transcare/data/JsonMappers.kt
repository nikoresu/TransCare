package org.nikoresu.transcare.data

import org.json.JSONObject
import org.nikoresu.transcare.domain.model.Appointment
import org.nikoresu.transcare.domain.model.ExamResult

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
