package org.nikoresu.transcare

import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nikoresu.transcare.data.BackupCrypto
import org.nikoresu.transcare.data.BackupManager
import org.nikoresu.transcare.domain.model.Appointment
import org.nikoresu.transcare.domain.model.DiaryEntry

class BackupCryptoTest {

    private val passphrase = "4821".toCharArray()
    private val iterations = 10_000

    @Test
    fun encryptsAndDecryptsTheOriginalDocument() {
        val plaintext = """{"app":"TransCare","name":"José","note":"salud"}"""

        val envelope = BackupCrypto.encrypt(
            plaintext = plaintext,
            passphrase = passphrase,
            iterations = iterations,
        )

        assertEquals(plaintext, BackupCrypto.decrypt(envelope, passphrase))
    }

    @Test
    fun encryptedEnvelopeDoesNotExposeThePlaintext() {
        val plaintext = """{"doctorName":"Dr. Rivera","place":"Clinic"}"""

        val envelope = BackupCrypto.encrypt(
            plaintext = plaintext,
            passphrase = passphrase,
            iterations = iterations,
        )

        assertFalse(envelope.contains("Dr. Rivera"))
        assertFalse(envelope.contains("Clinic"))
        assertTrue(BackupCrypto.isEncryptedBackup(envelope))
    }

    @Test
    fun wrongPassphraseIsRejected() {
        val envelope = BackupCrypto.encrypt(
            plaintext = """{"app":"TransCare"}""",
            passphrase = passphrase,
            iterations = iterations,
        )

        assertThrows(BackupCrypto.WrongPassphraseException::class.java) {
            BackupCrypto.decrypt(envelope, "0000".toCharArray())
        }
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val envelope = BackupCrypto.encrypt(
            plaintext = """{"app":"TransCare"}""",
            passphrase = passphrase,
            iterations = iterations,
        )
        val root = JSONObject(envelope)
        val ciphertext = root.getString("ciphertext")
        val replacement = if (ciphertext.first() == 'A') 'B' else 'A'
        root.put("ciphertext", replacement + ciphertext.drop(1))

        assertThrows(BackupCrypto.WrongPassphraseException::class.java) {
            BackupCrypto.decrypt(root.toString(), passphrase)
        }
    }

    @Test
    fun everyExportUsesAFreshSaltAndIv() {
        val first = JSONObject(
            BackupCrypto.encrypt(
                plaintext = """{"app":"TransCare"}""",
                passphrase = passphrase,
                iterations = iterations,
            ),
        )
        val second = JSONObject(
            BackupCrypto.encrypt(
                plaintext = """{"app":"TransCare"}""",
                passphrase = passphrase,
                iterations = iterations,
            ),
        )

        assertNotEquals(first.getString("salt"), second.getString("salt"))
        assertNotEquals(first.getString("iv"), second.getString("iv"))
        assertNotEquals(first.getString("ciphertext"), second.getString("ciphertext"))
    }

    @Test
    fun legacyEncryptedEnvelopeIsStillAccepted() {
        val plaintext = """{"app":"TransCare","note":"old backup"}"""
        val current = JSONObject(
            BackupCrypto.encrypt(
                plaintext = plaintext,
                passphrase = passphrase,
                iterations = iterations,
            ),
        )
        // The early internal build used a different format name and omitted cipher.
        current.put("format", "encrypted-backup")
        current.remove("cipher")
        val legacy = current.toString()

        assertTrue(BackupCrypto.isEncryptedBackup(legacy))
        assertEquals(plaintext, BackupCrypto.decrypt(legacy, passphrase))
    }

    @Test
    fun plainBackupIsNotDetectedAsEncrypted() {
        val plain = BackupManager.buildBackupJson(
            appointments = emptyList(),
            results = emptyList(),
            diaryEntries = emptyList(),
        )

        assertFalse(BackupCrypto.isEncryptedBackup(plain))
    }

    @Test
    fun backupManagerEncryptsAndRestoresContents() {
        val day = LocalDate.of(2026, 10, 23)
        val appointment = Appointment(
            doctorName = "Dr. Rivera",
            place = "Clinic",
            dayStartMillis = DiaryEntry.dayStartMillisOf(day),
            hour = 9,
            minute = 30,
        )

        val envelope = BackupManager.buildEncryptedBackupJson(
            appointments = listOf(appointment),
            results = emptyList(),
            diaryEntries = emptyList(),
            passphrase = passphrase,
            exportedAt = Instant.parse("2026-10-23T12:00:00Z"),
        )
        val contents = BackupManager.decryptBackup(envelope, passphrase)

        assertEquals(1, contents.appointments.size)
        assertEquals(appointment.doctorName, contents.appointments.single().doctorName)
        assertEquals(0, contents.skippedEntries)
    }

    @Test
    fun encryptedFileNameIsDistinct() {
        val day = LocalDate.of(2026, 10, 23)

        assertEquals(
            "TransCare-Data-2026-10-23.encrypted.json",
            BackupManager.fileName(day, encrypted = true),
        )
        assertEquals(
            "TransCare-Data-2026-10-23.json",
            BackupManager.fileName(day, encrypted = false),
        )
    }
}
