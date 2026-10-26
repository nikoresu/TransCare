package org.nikoresu.transcare.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.nikoresu.transcare.domain.model.Appointment
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.domain.model.ExamResult

/** Data read from a backup file, ready to replace the current local data. */
data class BackupContents(
    val appointments: List<Appointment>,
    val examResults: List<ExamResult>,
    val skippedEntries: Int,
)

/**
 * Exports all local data into a single JSON file in the device's Downloads
 * folder using MediaStore, so no storage permission is required.
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private const val MIME_TYPE = "application/json"

    /** Reject timestamps that cannot be sensible calendar days (up to 2100). */
    private const val MAX_DAY_START_MILLIS = 4_102_444_800_000L
    private const val MAX_BACKUP_CHARS = 5 * 1024 * 1024

    /** e.g. "TransCare-Data-2026-10-23.json". */
    fun fileName(date: LocalDate = LocalDate.now()): String =
        "TransCare-Data-$date.json"

    /** Builds the combined backup document. */
    fun buildBackupJson(
        appointments: List<Appointment>,
        results: List<ExamResult>,
        exportedAt: Instant = Instant.now(),
    ): String {
        val appointmentArray = JSONArray()
        appointments.forEach { appointmentArray.put(it.toJsonObject()) }

        val resultArray = JSONArray()
        results.forEach { resultArray.put(it.toJsonObject()) }

        return JSONObject()
            .put("app", "TransCare")
            .put("schemaVersion", 1)
            .put("exportedAt", exportedAt.toString())
            .put("appointments", appointmentArray)
            .put("examResults", resultArray)
            .toString(2)
    }

    /** Reads and validates a backup chosen through the system file picker. */
    suspend fun readFromUri(context: Context, uri: Uri): BackupContents =
        withContext(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readTextLimited()
            } ?: throw IOException("Could not open the selected file")
            parseBackupJson(text)
        }

    /**
     * Parses a combined TransCare backup. Also accepts the older standalone
     * appointments or exam-results arrays for convenience.
     */
    fun parseBackupJson(text: String): BackupContents {
        return when (val root = JSONTokener(text).nextValue()) {
            is JSONObject -> {
                val appointmentArray = root.optJSONArray("appointments")
                val resultArray = root.optJSONArray("examResults")
                    ?: root.optJSONArray("exam_results")
                if (appointmentArray == null && resultArray == null) {
                    throw IllegalArgumentException("Not a TransCare backup")
                }
                val (appointments, skippedAppointments) = parseAppointments(appointmentArray)
                val (results, skippedResults) = parseExamResults(resultArray)
                BackupContents(
                    appointments = appointments,
                    examResults = results,
                    skippedEntries = skippedAppointments + skippedResults,
                )
            }

            is JSONArray -> parseLegacyArray(root)
            else -> throw IllegalArgumentException("Not a TransCare backup")
        }
    }

    private fun parseLegacyArray(array: JSONArray): BackupContents {
        val first = array.optJSONObject(0)
            ?: throw IllegalArgumentException("The backup contains no data")
        return if (first.has("metric")) {
            val (results, skipped) = parseExamResults(array)
            BackupContents(
                appointments = emptyList(),
                examResults = results,
                skippedEntries = skipped,
            )
        } else {
            val (appointments, skipped) = parseAppointments(array)
            BackupContents(
                appointments = appointments,
                examResults = emptyList(),
                skippedEntries = skipped,
            )
        }
    }

    private fun parseAppointments(array: JSONArray?): Pair<List<Appointment>, Int> {
        if (array == null) return emptyList<Appointment>() to 0
        val appointments = mutableListOf<Appointment>()
        var skipped = 0
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index)
            if (json == null) {
                skipped++
                continue
            }
            val dayStartMillis = json.optLong("dayStartMillis", -1L)
            val hour = json.optInt("hour", -1)
            val minute = json.optInt("minute", -1)
            if (!isValidDay(dayStartMillis) || hour !in 0..23 || minute !in 0..59) {
                skipped++
                continue
            }
            appointments += Appointment(
                doctorName = json.optString("doctorName").trim(),
                place = json.optString("place").trim(),
                dayStartMillis = dayStartMillis,
                hour = hour,
                minute = minute,
            )
        }
        return appointments to skipped
    }

    private fun parseExamResults(array: JSONArray?): Pair<List<ExamResult>, Int> {
        if (array == null) return emptyList<ExamResult>() to 0
        val results = mutableListOf<ExamResult>()
        var skipped = 0
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index)
            if (json == null) {
                skipped++
                continue
            }
            val metricName = json.optString("metric")
            val metric = ExamMetric.entries.firstOrNull { it.name == metricName }
            val value = json.optDouble("value", Double.NaN)
            val dayStartMillis = json.optLong("dayStartMillis", -1L)
            if (
                metric == null ||
                !value.isFinite() ||
                value < 0.0 ||
                !isValidDay(dayStartMillis)
            ) {
                skipped++
                continue
            }
            results += ExamResult(
                metric = metric,
                value = value,
                dayStartMillis = dayStartMillis,
                note = json.optString("note"),
            )
        }
        return results to skipped
    }

    private fun isValidDay(epochMillis: Long): Boolean =
        epochMillis in 1L..MAX_DAY_START_MILLIS

    private fun InputStream.readTextLimited(): String {
        val reader = bufferedReader(Charsets.UTF_8)
        val text = StringBuilder()
        val buffer = CharArray(8 * 1024)
        var total = 0
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_BACKUP_CHARS) {
                throw IOException("The selected file is too large")
            }
            text.append(buffer, 0, count)
        }
        return text.toString()
    }

    /**
     * Writes the backup to Downloads and returns the created file. If a file
     * with the same name already exists, MediaStore may choose a unique name.
     */
    suspend fun saveToDownloads(
        context: Context,
        appointments: List<Appointment>,
        results: List<ExamResult>,
    ): BackupFile = withContext(Dispatchers.IO + NonCancellable) {
        val json = buildBackupJson(appointments, results)
        val resolver = context.contentResolver
        val requestedName = fileName()

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, requestedName)
            put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create a file in Downloads")

        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("Could not open the backup file for writing")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )

            BackupFile(
                displayName = resolver.displayNameOf(uri) ?: requestedName,
                uri = uri,
            )
        } catch (exception: Exception) {
            resolver.delete(uri, null, null)
            Log.e(TAG, "Could not save backup", exception)
            throw exception
        }
    }

    private fun android.content.ContentResolver.displayNameOf(uri: Uri): String? =
        query(
            uri,
            arrayOf(MediaStore.Downloads.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    data class BackupFile(
        val displayName: String,
        val uri: Uri,
    )
}
