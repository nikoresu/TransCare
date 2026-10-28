package org.nikoresu.transcare.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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

/**
 * Attachment bytes read from a backup. Small files stay in memory; files from
 * a ZIP backup are staged on disk so a large restore never holds the whole
 * archive in memory.
 */
sealed interface AttachmentData {

    fun openStream(): InputStream

    fun sizeBytes(): Long

    fun readBytes(): ByteArray

    /** Removes any staged copy. In-memory data has nothing to clean up. */
    fun delete()

    class InMemory(private val bytes: ByteArray) : AttachmentData {
        override fun openStream(): InputStream = ByteArrayInputStream(bytes)
        override fun sizeBytes(): Long = bytes.size.toLong()
        override fun readBytes(): ByteArray = bytes
        override fun delete() = Unit
    }

    class Staged(val file: File) : AttachmentData {
        override fun openStream(): InputStream = FileInputStream(file)
        override fun sizeBytes(): Long = file.length()
        override fun readBytes(): ByteArray = file.readBytes()
        override fun delete() {
            file.delete()
        }
    }
}

/** Data read from a backup file, ready to replace the current local data. */
data class BackupContents(
    val appointments: List<Appointment>,
    val examResults: List<ExamResult>,
    val diaryEntries: List<DiaryEntry>,
    val medications: List<Medication>,
    val medicationDoses: List<MedicationDose>,
    val skippedEntries: Int,
    /** Plaintext attachment data keyed by attachment id. */
    val attachmentFiles: Map<String, AttachmentData> = emptyMap(),
) {
    val attachmentCount: Int
        get() = appointments.sumOf { it.attachments.size } +
            examResults.sumOf { it.attachments.size } +
            diaryEntries.sumOf { it.attachments.size }
}

/** A selected file is either a readable backup or an encrypted envelope. */
sealed interface BackupReadResult {
    data class Plain(val contents: BackupContents) : BackupReadResult

    /**
     * The envelope needs the original PIN or passphrase before it can be read.
     * [source] is a private copy of the selected file, so it can be decrypted
     * later without asking for the document permission again.
     */
    data class Encrypted(val source: File) : BackupReadResult
}

/**
 * Exports all local data into a single JSON file in the device's Downloads
 * folder using MediaStore, so no storage permission is required.
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private const val ZIP_MIME_TYPE = "application/zip"
    private const val BINARY_MIME_TYPE = "application/octet-stream"
    private const val MANIFEST_ENTRY = "backup.json"
    private const val ATTACHMENTS_PREFIX = "attachments/"

    /** Reject timestamps that cannot be sensible calendar days (up to 2100). */
    private const val MAX_DAY_START_MILLIS = 4_102_444_800_000L
    private const val MAX_MANIFEST_BYTES = 10L * 1024 * 1024
    private const val MAX_JSON_BACKUP_BYTES = 10L * 1024 * 1024
    /** Only used by the in-memory parser; streamed imports are disk-bound. */
    private const val MAX_IN_MEMORY_BACKUP_BYTES = 100L * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 500
    private const val BUFFER_BYTES = 64 * 1024
    private const val IMPORT_DIR = "backup_import"
    private const val STAGED_DIR = "attachments"
    private const val SOURCE_FILE = "source.bin"
    /** Enough for the ZIP magic and the binary envelope header. */
    private const val HEADER_BYTES = 8

    /** e.g. "TransCare-Data-2026-10-23.zip" or the encrypted variant. */
    fun fileName(
        date: LocalDate = LocalDate.now(),
        encrypted: Boolean = false,
    ): String = if (encrypted) {
        "TransCare-Data-$date.encrypted.tcbak"
    } else {
        "TransCare-Data-$date.zip"
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

    /**
     * Builds a ZIP backup: a JSON manifest plus the plaintext bytes of every
     * attachment. The vault itself is encrypted with a device key that cannot
     * leave the phone, so backups carry the original files instead.
     */
    fun buildBackupZip(
        appointments: List<Appointment>,
        results: List<ExamResult>,
        diaryEntries: List<DiaryEntry>,
        medications: List<Medication> = emptyList(),
        medicationDoses: List<MedicationDose> = emptyList(),
        attachmentBytes: Map<String, ByteArray> = emptyMap(),
        exportedAt: Instant = Instant.now(),
    ): ByteArray = ByteArrayOutputStream().use { output ->
        writeBackupZip(
            sink = output,
            appointments = appointments,
            results = results,
            diaryEntries = diaryEntries,
            medications = medications,
            medicationDoses = medicationDoses,
            exportedAt = exportedAt,
            passphrase = null,
            openAttachment = { id -> attachmentBytes[id]?.inputStream() },
        )
        output.toByteArray()
    }

    /** Builds a ZIP backup and wraps it in the binary encrypted envelope. */
    fun buildEncryptedBackupZip(
        appointments: List<Appointment>,
        results: List<ExamResult>,
        diaryEntries: List<DiaryEntry>,
        medications: List<Medication> = emptyList(),
        medicationDoses: List<MedicationDose> = emptyList(),
        attachmentBytes: Map<String, ByteArray> = emptyMap(),
        passphrase: CharArray,
        exportedAt: Instant = Instant.now(),
    ): ByteArray = ByteArrayOutputStream().use { output ->
        writeBackupZip(
            sink = output,
            appointments = appointments,
            results = results,
            diaryEntries = diaryEntries,
            medications = medications,
            medicationDoses = medicationDoses,
            exportedAt = exportedAt,
            passphrase = passphrase,
            openAttachment = { id -> attachmentBytes[id]?.inputStream() },
        )
        output.toByteArray()
    }

    /**
     * Streams a ZIP backup into [sink]: the manifest first, then each
     * attachment copied straight from the vault. Only one buffer is held in
     * memory, so the export size is limited by free disk space rather than by
     * the heap. The chain (including the encrypted envelope) is closed here.
     */
    private fun writeBackupZip(
        sink: OutputStream,
        appointments: List<Appointment>,
        results: List<ExamResult>,
        diaryEntries: List<DiaryEntry>,
        medications: List<Medication>,
        medicationDoses: List<MedicationDose>,
        exportedAt: Instant,
        passphrase: CharArray?,
        openAttachment: (String) -> InputStream?,
    ) {
        val manifest = buildBackupJson(
            appointments = appointments,
            results = results,
            diaryEntries = diaryEntries,
            medications = medications,
            medicationDoses = medicationDoses,
            exportedAt = exportedAt,
        )
        val encryptedSink = if (passphrase != null) {
            BackupCrypto.encryptingStream(sink, passphrase)
        } else {
            sink
        }
        ZipOutputStream(BufferedOutputStream(encryptedSink, BUFFER_BYTES)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val written = HashSet<String>()
            attachmentIds(appointments, results, diaryEntries).forEach { id ->
                if (!written.add(id) || !AttachmentVault.isValidId(id)) return@forEach
                val input = openAttachment(id) ?: return@forEach
                input.use { stream ->
                    zip.putNextEntry(ZipEntry("$ATTACHMENTS_PREFIX$id"))
                    stream.copyTo(zip, BUFFER_BYTES)
                    zip.closeEntry()
                }
            }
        }
    }

    private fun attachmentIds(
        appointments: List<Appointment>,
        results: List<ExamResult>,
        diaryEntries: List<DiaryEntry>,
    ): List<String> = buildList {
        appointments.forEach { appointment ->
            appointment.attachments.forEach { add(it.id) }
        }
        results.forEach { result ->
            result.attachments.forEach { add(it.id) }
        }
        diaryEntries.forEach { entry ->
            entry.attachments.forEach { add(it.id) }
        }
    }

    /**
     * Decrypts a JSON or binary envelope that is already in memory and parses
     * the backup inside it. Kept for small, self-contained backups (and tests);
     * [readFromUri] and [decryptBackup] stage large files instead.
     */
    fun decryptBackup(payload: ByteArray, passphrase: CharArray): BackupContents {
        if (BackupCrypto.isBinaryEnvelope(payload)) {
            return decryptingBackup(
                source = ByteArrayInputStream(payload),
                passphrase = passphrase,
                stagingDir = null,
            )
        }
        val plaintext = BackupCrypto.decrypt(
            String(payload, Charsets.UTF_8),
            passphrase,
        ).toByteArray(Charsets.UTF_8)
        return parsePlainBackup(plaintext, stagingDir = null)
    }

    /**
     * Decrypts a staged envelope file and parses the backup inside it. The
     * decrypted bytes are consumed as a stream and attachments are written
     * straight to [stagingDir], so memory use stays constant. The source file
     * is deleted once it has been read successfully.
     */
    fun decryptBackup(
        source: File,
        passphrase: CharArray,
        stagingDir: File? = null,
    ): BackupContents {
        val header = source.inputStream().use { it.readHeader() }
        val contents = if (BackupCrypto.isBinaryEnvelope(header)) {
            source.inputStream().use { input ->
                decryptingBackup(input, passphrase, stagingDir)
            }
        } else {
            val text = source.inputStream().use {
                String(it.readAtMost(MAX_JSON_BACKUP_BYTES), Charsets.UTF_8)
            }
            parseBackupJson(BackupCrypto.decrypt(text, passphrase))
        }
        source.delete()
        return contents
    }

    /**
     * Parses a decrypted binary envelope from [source], verifying the GCM tag
     * before the result is returned. Verification happens at the end of the
     * stream, so the remaining bytes are drained even when parsing succeeded.
     */
    private fun decryptingBackup(
        source: InputStream,
        passphrase: CharArray,
        stagingDir: File?,
    ): BackupContents {
        val decrypted = BufferedInputStream(
            BackupCrypto.decryptingStream(source, passphrase),
            BUFFER_BYTES,
        )
        try {
            val header = decrypted.peekHeader()
            val contents = try {
                if (isZip(header)) {
                    // The parser closes its stream; keep the cipher open so the
                    // remaining bytes can be drained to verify the GCM tag.
                    parseBackupZip(NonClosingInputStream(decrypted), stagingDir)
                } else {
                    val text = decrypted.readAtMost(MAX_JSON_BACKUP_BYTES)
                    parseBackupJson(String(text, Charsets.UTF_8))
                }
            } catch (exception: Exception) {
                // Garbage from a wrong passphrase usually fails to parse before
                // the tag is reached; draining reveals the real reason.
                val tagFailure = runCatching { decrypted.drain() }.exceptionOrNull()
                if (tagFailure is BackupCrypto.WrongPassphraseException) throw tagFailure
                throw exception
            }
            decrypted.drain()
            return contents
        } finally {
            decrypted.close()
        }
    }

    /** Lets a parser close its stream without closing the caller's stream. */
    private class NonClosingInputStream(
        private val delegate: InputStream,
    ) : InputStream() {
        override fun read(): Int = delegate.read()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, length)

        override fun skip(count: Long): Long = delegate.skip(count)

        override fun available(): Int = delegate.available()

        override fun close() = Unit
    }

    /**
     * Reads the manifest and attachment entries from a ZIP backup.
     *
     * When [stagingDir] is set, each attachment is copied to a plaintext file
     * there and the total size is limited only by free disk space. Without it
     * the bytes are kept in memory and capped, which suits small backups.
     *
     * [input] is closed when parsing finishes.
     */
    fun parseBackupZip(input: InputStream, stagingDir: File? = null): BackupContents {
        var manifest: String? = null
        val files = LinkedHashMap<String, AttachmentData>()
        var totalBytes = 0L
        var entryCount = 0

        val zip = ZipInputStream(input)
        try {
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw IOException("The backup contains too many files")
                }
                if (!entry.isDirectory) {
                    val name = entry.name
                    if (name == MANIFEST_ENTRY) {
                        val data = zip.readEntryLimited(MAX_MANIFEST_BYTES)
                        totalBytes += data.size
                        manifest = String(data, Charsets.UTF_8)
                    } else if (name.startsWith(ATTACHMENTS_PREFIX)) {
                        val id = name.removePrefix(ATTACHMENTS_PREFIX)
                        if (AttachmentVault.isValidId(id) && id !in files) {
                            if (stagingDir != null) {
                                files[id] = zip.stageEntry(stagingDir, id)
                            } else {
                                val data = zip.readEntryLimited(
                                    AttachmentVault.MAX_ATTACHMENT_BYTES,
                                )
                                totalBytes += data.size
                                files[id] = AttachmentData.InMemory(data)
                            }
                        }
                    }
                }
                if (stagingDir == null && totalBytes > MAX_IN_MEMORY_BACKUP_BYTES) {
                    throw IOException("The backup is too large")
                }
                zip.closeEntry()
            }
        } finally {
            // Releases the inflater without closing a caller-owned stream.
            runCatching { zip.close() }
        }

        val manifestText = manifest
            ?: throw IllegalArgumentException("Not a TransCare backup")
        val contents = parseBackupJson(manifestText)
        val referencedIds = contents.referencedAttachmentIds()
        files.forEach { (id, data) ->
            if (id !in referencedIds) data.delete()
        }
        return contents.copy(
            attachmentFiles = files.filterKeys { it in referencedIds },
        )
    }

    /** Reads a ZIP backup that is already in memory. */
    fun parseBackupZip(bytes: ByteArray): BackupContents =
        ByteArrayInputStream(bytes).use { parseBackupZip(it) }

    /**
     * Reads a backup chosen through the system file picker.
     *
     * The selected document is copied to a private cache file with a fixed
     * buffer, so neither the archive nor its attachments have to fit in
     * memory. ZIP attachments are staged next to it and streamed into the
     * vault when the user confirms the import.
     */
    suspend fun readFromUri(context: Context, uri: Uri): BackupReadResult =
        withContext(Dispatchers.IO) {
            discardStagedAttachments(context)
            val directory = importDirectory(context)
            if (!directory.mkdirs() && !directory.isDirectory) {
                throw IOException("Could not prepare the import folder")
            }
            val source = File(directory, SOURCE_FILE)
            val header = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(source).use { output ->
                    input.copyAndPeek(output, HEADER_BYTES)
                }
            } ?: throw IOException("Could not open the selected file")

            when {
                isZip(header) -> {
                    val contents = source.inputStream().use { input ->
                        parseBackupZip(input, stagingDirectory(context))
                    }
                    source.delete()
                    BackupReadResult.Plain(contents)
                }

                BackupCrypto.isBinaryEnvelope(header) -> BackupReadResult.Encrypted(source)

                else -> {
                    val text = source.inputStream().use { input ->
                        String(input.readAtMost(MAX_JSON_BACKUP_BYTES), Charsets.UTF_8)
                    }
                    if (BackupCrypto.isEncryptedBackup(text)) {
                        BackupReadResult.Encrypted(source)
                    } else {
                        source.delete()
                        BackupReadResult.Plain(parseBackupJson(text))
                    }
                }
            }
        }

    /** Where plaintext attachments are staged while an import is pending. */
    fun stagingDirectory(context: Context): File =
        File(importDirectory(context), STAGED_DIR)

    /**
     * Removes the private copy of a selected backup and any staged
     * attachments. Safe to call at any time; a pending import simply has to be
     * picked again.
     */
    fun discardStagedAttachments(context: Context) {
        importDirectory(context).deleteRecursively()
    }

    private fun importDirectory(context: Context): File =
        File(context.cacheDir, IMPORT_DIR)

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
                attachments = json.optAttachments(),
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
                attachments = json.optAttachments(),
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
            val attachments = json.optAttachments()
            if (
                !isValidDay(dayStartMillis) ||
                (title.isEmpty() && text.isEmpty() && attachments.isEmpty())
            ) {
                skipped++
                continue
            }
            entries += DiaryEntry(
                dayStartMillis = dayStartMillis,
                title = title,
                text = text,
                type = DiaryEntryType.fromStorageValue(json.optString("type")),
                attachments = attachments,
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

    private fun InputStream.readAtMost(maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw IOException("The selected file is too large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    /** Copies the whole stream to [output] and returns its first [peekBytes]. */
    private fun InputStream.copyAndPeek(output: OutputStream, peekBytes: Int): ByteArray {
        val peek = ByteArray(peekBytes)
        var peeked = 0
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (peeked < peekBytes) {
                val copy = minOf(count, peekBytes - peeked)
                System.arraycopy(buffer, 0, peek, peeked, copy)
                peeked += copy
            }
            output.write(buffer, 0, count)
        }
        return if (peeked == peekBytes) peek else peek.copyOf(peeked)
    }

    private fun InputStream.readHeader(): ByteArray {
        val header = ByteArray(HEADER_BYTES)
        var read = 0
        while (read < HEADER_BYTES) {
            val count = read(header, read, HEADER_BYTES - read)
            if (count < 0) break
            read += count
        }
        return if (read == HEADER_BYTES) header else header.copyOf(read)
    }

    private fun BufferedInputStream.peekHeader(): ByteArray {
        mark(HEADER_BYTES)
        val header = readHeader()
        reset()
        return header
    }

    private fun InputStream.drain() {
        val buffer = ByteArray(BUFFER_BYTES)
        while (read(buffer) >= 0) {
            // Reading to the end also verifies the GCM tag.
        }
    }

    private fun ZipInputStream.readEntryLimited(maxBytes: Long): ByteArray =
        readAtMost(maxBytes)

    /** Copies the current ZIP entry to a plaintext file in [directory]. */
    private fun ZipInputStream.stageEntry(
        directory: File,
        id: String,
    ): AttachmentData.Staged {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the attachment staging folder")
        }
        val file = File(directory, id)
        try {
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val count = read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > AttachmentVault.MAX_ATTACHMENT_BYTES) {
                        throw IOException("An attachment is too large")
                    }
                    output.write(buffer, 0, count)
                }
                if (total == 0L) throw IOException("An attachment is empty")
            }
        } catch (exception: Exception) {
            file.delete()
            throw exception
        }
        return AttachmentData.Staged(file)
    }

    private fun parsePlainBackup(bytes: ByteArray, stagingDir: File?): BackupContents =
        if (isZip(bytes)) {
            ByteArrayInputStream(bytes).use { parseBackupZip(it, stagingDir) }
        } else {
            parseBackupJson(String(bytes, Charsets.UTF_8))
        }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte()

    private fun BackupContents.referencedAttachmentIds(): Set<String> =
        buildSet {
            appointments.forEach { addAll(it.attachments.map { attachment -> attachment.id }) }
            examResults.forEach { addAll(it.attachments.map { attachment -> attachment.id }) }
            diaryEntries.forEach { addAll(it.attachments.map { attachment -> attachment.id }) }
        }

    /**
     * Writes the backup to Downloads and returns the created file. If a file
     * with the same name already exists, MediaStore may choose a unique name.
     *
     * The archive is streamed straight into the destination, so only a small
     * buffer is held in memory regardless of how many attachments there are.
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
        val resolver = context.contentResolver
        val requestedName = fileName(encrypted = passphrase != null)
        val mimeType = if (passphrase != null) BINARY_MIME_TYPE else ZIP_MIME_TYPE

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, requestedName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create a file in Downloads")

        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                writeBackupZip(
                    sink = NonClosingOutputStream(output),
                    appointments = appointments,
                    results = results,
                    diaryEntries = entries,
                    medications = medications,
                    medicationDoses = medicationDoses,
                    exportedAt = Instant.now(),
                    passphrase = passphrase,
                    openAttachment = { id ->
                        AttachmentVault.openReadStream(context, id)
                    },
                )
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

    /**
     * Flushes but does not close the wrapped stream, so the ZIP writer can
     * finish the archive while MediaStore still owns the file handle.
     */
    private class NonClosingOutputStream(
        private val delegate: OutputStream,
    ) : OutputStream() {
        override fun write(b: Int) = delegate.write(b)

        override fun write(b: ByteArray, off: Int, len: Int) =
            delegate.write(b, off, len)

        override fun flush() = delegate.flush()

        override fun close() = flush()
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
