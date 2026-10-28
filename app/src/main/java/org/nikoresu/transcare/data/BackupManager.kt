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
import org.nikoresu.transcare.domain.model.DiaryEntry
import org.nikoresu.transcare.domain.model.DiaryEntryType
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.domain.model.ExamResult
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.domain.model.MedicationDose

/** Data read from a backup file, ready to replace the current local data. */
data class BackupContents(
    val appointments: List<Appointment>,
    val examResults: List<ExamResult>,
    val diaryEntries: List<DiaryEntry>,
    val medications: List<Medication>,
    val medicationDoses: List<MedicationDose>,
    val skippedEntries: Int,
)

/** A selected file is either a readable plain backup or an encrypted envelope. */
sealed interface BackupReadResult {
    data class Plain(val contents: BackupContents) : BackupReadResult

    /** The envelope needs the original PIN or passphrase before it can be read. */
    data class Encrypted(val text: String) : BackupReadResult
}

/**
 * Exports all local data into a single JSON file in the device's Downloads
 * folder using MediaStore, so no storage permission is required.
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private const val MIME_TYPE = "application/json"

    /** Reject timestamps that cannot be sensible calendar days (up to 2100). */
    private const val MAX_DAY_START_MILLIS = 4_102_444_800_000L
    private const val MAX_BACKUP_CHARS = 10 * 1024 * 1024

    /** e.g. "TransCare-Data-2026-10-23.json" or the encrypted variant. */
    fun fileName(
        date: LocalDate = LocalDate.now(),
        encrypted: Boolean = false,
    ): String = if (encrypted) {
        "TransCare-Data-$date.encrypted.json"
    } else {
        "TransCare-Data-$date.json"
    }

    /** Builds the combined backup document. */
    fun buildBackupJson(
        appointments: List<Appointment>,
        results: List<ExamResult>,
        diaryEntries: List<DiaryEntry>,
        medications: List<Medication> = emptyList(),
        medicationDoses: List<MedicationDose> = emptyList(),
        exportedAt: Instant = Instant.now(),
    ): String {
        val appointmentArray = JSONArray()
        appointments.forEach { appointmentArray.put(it.toJsonObject()) }

        val resultArray = JSONArray()
        results.forEach { resultArray.put(it.toJsonObject()) }

        val diaryArray = JSONArray()
        diaryEntries.forEach { diaryArray.put(it.toJsonObject()) }

        val medicationArray = JSONArray()
        medications.forEach { medicationArray.put(it.toJsonObject()) }

        val medicationDoseArray = JSONArray()
        medicationDoses.forEach { medicationDoseArray.put(it.toJsonObject()) }

        return JSONObject()
            .put("app", "TransCare")
            .put("schemaVersion", 4)
            .put("exportedAt", exportedAt.toString())
            .put("appointments", appointmentArray)
            .put("examResults", resultArray)
            .put("diaryEntries", diaryArray)
            .put("medications", medicationArray)
            .put("medicationDoses", medicationDoseArray)
            .toString(2)
    }

    /** Builds an encrypted backup envelope around the normal backup document. */
    fun buildEncryptedBackupJson(
        appointments: List<Appointment>,
        results: List<ExamResult>,
        diaryEntries: List<DiaryEntry>,
        medications: List<Medication> = emptyList(),
        medicationDoses: List<MedicationDose> = emptyList(),
        passphrase: CharArray,
        exportedAt: Instant = Instant.now(),
    ): String {
        val plaintext = buildBackupJson(
            appointments = appointments,
            results = results,
            diaryEntries = diaryEntries,
            medications = medications,
            medicationDoses = medicationDoses,
            exportedAt = exportedAt,
        )
        return BackupCrypto.encrypt(
            plaintext = plaintext,
            passphrase = passphrase,
            exportedAt = exportedAt,
        )
    }

    /** Decrypts an envelope and parses the backup inside it. */
    fun decryptBackup(text: String, passphrase: CharArray): BackupContents =
        parseBackupJson(BackupCrypto.decrypt(text, passphrase))

    /** Reads a backup chosen through the system file picker. */
    suspend fun readFromUri(context: Context, uri: Uri): BackupReadResult =
        withContext(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readTextLimited()
            } ?: throw IOException("Could not open the selected file")
            if (BackupCrypto.isEncryptedBackup(text)) {
                BackupReadResult.Encrypted(text)
            } else {
                BackupReadResult.Plain(parseBackupJson(text))
            }
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
                val diaryArray = root.optJSONArray("diaryEntries")
                    ?: root.optJSONArray("diary_entries")
                val medicationArray = root.optJSONArray("medications")
                val medicationDoseArray = root.optJSONArray("medicationDoses")
                    ?: root.optJSONArray("medication_doses")
                if (
                    appointmentArray == null &&
                    resultArray == null &&
                    diaryArray == null &&
                    medicationArray == null &&
                    medicationDoseArray == null
                ) {
                    throw IllegalArgumentException("Not a TransCare backup")
                }
                val (appointments, skippedAppointments) = parseAppointments(appointmentArray)
                val (results, skippedResults) = parseExamResults(resultArray)
                val (diaryEntries, skippedDiaryEntries) = parseDiaryEntries(diaryArray)
                val (medications, skippedMedications) = parseMedications(medicationArray)
                val medicationIds = medications.map { it.id }.toSet()
                val (medicationDoses, skippedMedicationDoses) =
                    parseMedicationDoses(medicationDoseArray, medicationIds)
                BackupContents(
                    appointments = appointments,
                    examResults = results,
                    diaryEntries = diaryEntries,
                    medications = medications,
                    medicationDoses = medicationDoses,
                    skippedEntries = skippedAppointments +
                        skippedResults +
                        skippedDiaryEntries +
                        skippedMedications +
                        skippedMedicationDoses,
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
                diaryEntries = emptyList(),
                medications = emptyList(),
                medicationDoses = emptyList(),
                skippedEntries = skipped,
            )
        } else {
            val (appointments, skipped) = parseAppointments(array)
            BackupContents(
                appointments = appointments,
                examResults = emptyList(),
                diaryEntries = emptyList(),
                medications = emptyList(),
                medicationDoses = emptyList(),
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
                notes = json.optString("notes").trim(),
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

    private fun parseDiaryEntries(array: JSONArray?): Pair<List<DiaryEntry>, Int> {
        if (array == null) return emptyList<DiaryEntry>() to 0
        val entries = mutableListOf<DiaryEntry>()
        var skipped = 0
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index)
            if (json == null) {
                skipped++
                continue
            }
            val dayStartMillis = json.optLong("dayStartMillis", -1L)
            val title = json.optString("title").trim()
            val text = json.optString("text").trim()
            if (!isValidDay(dayStartMillis) || (title.isEmpty() && text.isEmpty())) {
                skipped++
                continue
            }
            entries += DiaryEntry(
                dayStartMillis = dayStartMillis,
                title = title,
                text = text,
                type = DiaryEntryType.fromStorageValue(json.optString("type")),
            )
        }
        return entries to skipped
    }

    private fun parseMedications(array: JSONArray?): Pair<List<Medication>, Int> {
        if (array == null) return emptyList<Medication>() to 0
        val medications = mutableListOf<Medication>()
        var skipped = 0
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index)
            if (json == null) {
                skipped++
                continue
            }
            val medication = MedicationRepository.parseMedication(json)
            if (medication == null) {
                skipped++
                continue
            }
            medications += medication.copy(
                id = medication.id.takeIf { it > 0L } ?: (index + 1L),
            )
        }
        return medications to skipped
    }

    private fun parseMedicationDoses(
        array: JSONArray?,
        medicationIds: Set<Long>,
    ): Pair<List<MedicationDose>, Int> {
        if (array == null) return emptyList<MedicationDose>() to 0
        val doses = mutableListOf<MedicationDose>()
        var skipped = 0
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index)
            if (json == null) {
                skipped++
                continue
            }
            val dose = MedicationRepository.parseDose(json)
            if (dose == null || dose.medicationId !in medicationIds) {
                skipped++
                continue
            }
            doses += dose
        }
        return doses
            .distinctBy { Triple(it.medicationId, it.dayStartMillis, it.doseIndex) } to skipped
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
        entries: List<DiaryEntry>,
        medications: List<Medication>,
        medicationDoses: List<MedicationDose>,
        passphrase: CharArray? = null,
    ): BackupFile = withContext(Dispatchers.IO + NonCancellable) {
        val json = if (passphrase != null) {
            buildEncryptedBackupJson(
                appointments = appointments,
                results = results,
                diaryEntries = entries,
                medications = medications,
                medicationDoses = medicationDoses,
                passphrase = passphrase,
            )
        } else {
            buildBackupJson(
                appointments = appointments,
                results = results,
                diaryEntries = entries,
                medications = medications,
                medicationDoses = medicationDoses,
            )
        }
        val resolver = context.contentResolver
        val requestedName = fileName(encrypted = passphrase != null)

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
