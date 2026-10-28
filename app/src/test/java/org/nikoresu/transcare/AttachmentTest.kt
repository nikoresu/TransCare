package org.nikoresu.transcare

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nikoresu.transcare.data.AttachmentData
import org.nikoresu.transcare.data.BackupCrypto
import org.nikoresu.transcare.data.BackupManager
import org.nikoresu.transcare.domain.model.Appointment
import org.nikoresu.transcare.domain.model.Attachment
import org.nikoresu.transcare.domain.model.DiaryEntry
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.domain.model.ExamResult

class AttachmentTest {

    private val day = LocalDate.of(2026, 10, 28)
    private val dayStartMillis = DiaryEntry.dayStartMillisOf(day)

    private val attachment = Attachment(
        id = "11111111-1111-1111-1111-111111111111",
        fileName = "lab-report.pdf",
        mimeType = Attachment.PDF_MIME_TYPE,
        sizeBytes = 4_096,
        addedAtMillis = 1_700_000_000_000L,
    )

    @Test
    fun metadataTravelsWithEveryOwner() {
        val appointment = Appointment(
            doctorName = "Dr. Rivera",
            dayStartMillis = dayStartMillis,
            hour = 9,
            minute = 0,
            attachments = listOf(attachment),
        )
        val result = ExamResult(
            metric = ExamMetric.ESTRADIOL,
            value = 150.0,
            dayStartMillis = dayStartMillis,
            attachments = listOf(attachment),
        )
        val entry = DiaryEntry(
            dayStartMillis = dayStartMillis,
            title = "Visit",
            attachments = listOf(attachment),
        )

        val json = BackupManager.buildBackupJson(
            appointments = listOf(appointment),
            results = listOf(result),
            diaryEntries = listOf(entry),
        )
        val contents = BackupManager.parseBackupJson(json)

        assertEquals(listOf(attachment), contents.appointments.single().attachments)
        assertEquals(listOf(attachment), contents.examResults.single().attachments)
        assertEquals(listOf(attachment), contents.diaryEntries.single().attachments)
        assertEquals(3, contents.attachmentCount)
    }

    @Test
    fun zipBackupRoundTripsAttachmentBytes() {
        val bytes = "not really a pdf".toByteArray()
        val appointment = Appointment(
            doctorName = "Dr. Rivera",
            dayStartMillis = dayStartMillis,
            hour = 9,
            minute = 0,
            attachments = listOf(attachment),
        )

        val zip = BackupManager.buildBackupZip(
            appointments = listOf(appointment),
            results = emptyList(),
            diaryEntries = emptyList(),
            attachmentBytes = mapOf(attachment.id to bytes),
        )
        val contents = BackupManager.parseBackupZip(zip)

        assertEquals(listOf(attachment), contents.appointments.single().attachments)
        assertArrayEquals(bytes, contents.attachmentFiles.getValue(attachment.id).readBytes())
        assertEquals(1, contents.attachmentCount)
    }

    @Test
    fun zipBackupDropsFilesThatNoOwnerReferences() {
        val zip = BackupManager.buildBackupZip(
            appointments = emptyList(),
            results = emptyList(),
            diaryEntries = emptyList(),
            attachmentBytes = mapOf(attachment.id to byteArrayOf(1, 2, 3)),
        )

        val contents = BackupManager.parseBackupZip(zip)

        assertTrue(contents.attachmentFiles.isEmpty())
    }

    @Test
    fun encryptedZipBackupRoundTrips() {
        val passphrase = "4821".toCharArray()
        val bytes = ByteArray(256) { it.toByte() }
        val entry = DiaryEntry(
            dayStartMillis = dayStartMillis,
            title = "Visit",
            attachments = listOf(attachment),
        )

        val encrypted = BackupManager.buildEncryptedBackupZip(
            appointments = emptyList(),
            results = emptyList(),
            diaryEntries = listOf(entry),
            attachmentBytes = mapOf(attachment.id to bytes),
            passphrase = passphrase,
        )

        assertTrue(BackupCrypto.isBinaryEnvelope(encrypted))
        assertFalse(encrypted.contentEquals(bytes))
        val contents = BackupManager.decryptBackup(encrypted, passphrase)
        assertArrayEquals(bytes, contents.attachmentFiles.getValue(attachment.id).readBytes())
    }

    @Test
    fun binaryEnvelopeRejectsWrongPassphrase() {
        val encrypted = BackupCrypto.encryptBytes(
            plaintext = byteArrayOf(1, 2, 3),
            passphrase = "4821".toCharArray(),
            iterations = 10_000,
        )

        assertThrows(BackupCrypto.WrongPassphraseException::class.java) {
            BackupCrypto.decryptBytes(encrypted, "0000".toCharArray())
        }
    }

    @Test
    fun zipImportStagesAttachmentsOnDisk() {
        val bytes = ByteArray(512 * 1024) { index -> (index % 251).toByte() }
        val entry = DiaryEntry(
            dayStartMillis = dayStartMillis,
            title = "Visit",
            attachments = listOf(attachment),
        )
        val zip = BackupManager.buildBackupZip(
            appointments = emptyList(),
            results = emptyList(),
            diaryEntries = listOf(entry),
            attachmentBytes = mapOf(attachment.id to bytes),
        )
        val staging = Files.createTempDirectory("transcare-import").toFile()
        try {
            val contents = zip.inputStream().use { input ->
                BackupManager.parseBackupZip(input, staging)
            }

            val staged = contents.attachmentFiles.getValue(attachment.id)
            assertTrue(staged is AttachmentData.Staged)
            assertEquals(staging, (staged as AttachmentData.Staged).file.parentFile)
            assertArrayEquals(bytes, staged.readBytes())
        } finally {
            staging.deleteRecursively()
        }
    }

    @Test
    fun zipImportDeletesStagedFilesWithoutMetadata() {
        val staging = Files.createTempDirectory("transcare-import").toFile()
        try {
            val zip = BackupManager.buildBackupZip(
                appointments = emptyList(),
                results = emptyList(),
                diaryEntries = emptyList(),
                attachmentBytes = mapOf(attachment.id to byteArrayOf(1, 2, 3)),
            )

            val contents = zip.inputStream().use { input ->
                BackupManager.parseBackupZip(input, staging)
            }

            assertTrue(contents.attachmentFiles.isEmpty())
            assertTrue(staging.listFiles().isNullOrEmpty())
        } finally {
            staging.deleteRecursively()
        }
    }

    @Test
    fun encryptedFileImportStagesAndRemovesTheSource() {
        val passphrase = "4821".toCharArray()
        val bytes = ByteArray(256 * 1024) { index -> (index % 97).toByte() }
        val entry = DiaryEntry(
            dayStartMillis = dayStartMillis,
            title = "Visit",
            attachments = listOf(attachment),
        )
        val encrypted = BackupManager.buildEncryptedBackupZip(
            appointments = emptyList(),
            results = emptyList(),
            diaryEntries = listOf(entry),
            attachmentBytes = mapOf(attachment.id to bytes),
            passphrase = passphrase,
        )
        val directory = Files.createTempDirectory("transcare-import").toFile()
        val source = File(directory, "backup.tcbak").apply { writeBytes(encrypted) }
        try {
            val contents = BackupManager.decryptBackup(
                source = source,
                passphrase = passphrase,
                stagingDir = File(directory, "attachments"),
            )

            assertFalse(source.exists())
            val staged = contents.attachmentFiles.getValue(attachment.id)
            assertTrue(staged is AttachmentData.Staged)
            assertArrayEquals(bytes, staged.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun encryptedFileImportRejectsWrongPassphrase() {
        val entry = DiaryEntry(
            dayStartMillis = dayStartMillis,
            title = "Visit",
            attachments = listOf(attachment),
        )
        val encrypted = BackupManager.buildEncryptedBackupZip(
            appointments = emptyList(),
            results = emptyList(),
            diaryEntries = listOf(entry),
            attachmentBytes = mapOf(attachment.id to byteArrayOf(1, 2, 3)),
            passphrase = "4821".toCharArray(),
        )
        val directory = Files.createTempDirectory("transcare-import").toFile()
        val source = File(directory, "backup.tcbak").apply { writeBytes(encrypted) }
        try {
            assertThrows(BackupCrypto.WrongPassphraseException::class.java) {
                BackupManager.decryptBackup(
                    source = source,
                    passphrase = "0000".toCharArray(),
                    stagingDir = File(directory, "attachments"),
                )
            }
            // The source is kept so the user can try another passphrase.
            assertTrue(source.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidAttachmentMetadataIsSkipped() {
        val attachments = JSONArray()
            .put(
                JSONObject()
                    .put("id", "../not-a-valid-id")
                    .put("fileName", "photo.jpg")
                    .put("mimeType", "image/jpeg")
                    .put("sizeBytes", 10)
                    .put("addedAtMillis", 1L),
            )
            .put(
                JSONObject()
                    .put("id", attachment.id)
                    .put("fileName", "notes.txt")
                    .put("mimeType", "text/plain")
                    .put("sizeBytes", 10)
                    .put("addedAtMillis", 1L),
            )
        val root = JSONObject().put(
            "appointments",
            JSONArray().put(
                JSONObject()
                    .put("doctorName", "Dr. Rivera")
                    .put("dayStartMillis", dayStartMillis)
                    .put("hour", 9)
                    .put("minute", 0)
                    .put("attachments", attachments),
            ),
        )

        val contents = BackupManager.parseBackupJson(root.toString())

        assertTrue(contents.appointments.single().attachments.isEmpty())
    }

    @Test
    fun diaryEntryWithOnlyAnAttachmentIsNotBlank() {
        assertFalse(DiaryEntry(attachments = listOf(attachment)).isBlank)
    }
}
