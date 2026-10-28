package org.nikoresu.transcare.ui.backup

import android.content.res.Resources
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nikoresu.transcare.R
import org.nikoresu.transcare.data.BackupContents
import org.nikoresu.transcare.data.BackupCrypto
import org.nikoresu.transcare.data.BackupManager
import org.nikoresu.transcare.data.BackupReadResult
import org.nikoresu.transcare.domain.model.Appointment
import org.nikoresu.transcare.domain.model.DiaryEntry
import org.nikoresu.transcare.domain.model.ExamResult
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.domain.model.MedicationDose
import org.nikoresu.transcare.security.SecurityPreferences

private const val TAG = "BackupSection"

/** Some file providers report .json files as plain text or binary. */
private val IMPORT_MIME_TYPES = arrayOf(
    "application/json",
    "text/plain",
    "application/octet-stream",
)

private data class StatusMessage(
    val text: String,
    val isError: Boolean,
)

/** Backup and restore controls. */
@Composable
fun BackupSection(
    appointments: List<Appointment>,
    results: List<ExamResult>,
    entries: List<DiaryEntry>,
    medications: List<Medication>,
    medicationDoses: List<MedicationDose>,
    onImport: suspend (
        List<Appointment>,
        List<ExamResult>,
        List<DiaryEntry>,
        List<Medication>,
        List<MedicationDose>,
    ) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<StatusMessage?>(null) }
    var importMessage by remember { mutableStateOf<StatusMessage?>(null) }
    var pendingImport by remember { mutableStateOf<BackupContents?>(null) }
    var pendingEncryptedText by remember { mutableStateOf<String?>(null) }
    var importPassphrase by remember { mutableStateOf("") }
    var importPassphraseError by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportPassphrase by remember { mutableStateOf("") }
    var exportPassphraseError by remember { mutableStateOf<String?>(null) }
    var showPlainWarning by remember { mutableStateOf(false) }
    val lockEnabled = SecurityPreferences.isLockEnabled(context)

    fun showContents(contents: BackupContents) {
        if (
            contents.appointments.isEmpty() &&
            contents.examResults.isEmpty() &&
            contents.diaryEntries.isEmpty() &&
            contents.medications.isEmpty() &&
            contents.medicationDoses.isEmpty()
        ) {
            importMessage = StatusMessage(
                text = resources.getString(R.string.backup_empty_file),
                isError = true,
            )
        } else {
            pendingImport = contents
        }
    }

    fun saveEncrypted(passphrase: CharArray) {
        isSaving = true
        saveMessage = null
        scope.launch {
            try {
                val file = BackupManager.saveToDownloads(
                    context = context.applicationContext,
                    appointments = appointments,
                    results = results,
                    entries = entries,
                    medications = medications,
                    medicationDoses = medicationDoses,
                    passphrase = passphrase,
                )
                saveMessage = StatusMessage(
                    text = resources.getString(
                        R.string.backup_saved_encrypted,
                        file.displayName,
                    ),
                    isError = false,
                )
            } catch (exception: Exception) {
                Log.e(TAG, "Encrypted backup failed", exception)
                saveMessage = StatusMessage(
                    text = resources.getString(R.string.backup_save_failed),
                    isError = true,
                )
            } finally {
                passphrase.fill('\u0000')
                isSaving = false
            }
        }
    }

    fun savePlain() {
        isSaving = true
        saveMessage = null
        scope.launch {
            try {
                val file = BackupManager.saveToDownloads(
                    context = context.applicationContext,
                    appointments = appointments,
                    results = results,
                    entries = entries,
                    medications = medications,
                    medicationDoses = medicationDoses,
                )
                saveMessage = StatusMessage(
                    text = resources.getString(
                        R.string.backup_saved_plain,
                        file.displayName,
                    ),
                    isError = false,
                )
            } catch (exception: Exception) {
                Log.e(TAG, "Backup failed", exception)
                saveMessage = StatusMessage(
                    text = resources.getString(R.string.backup_save_failed),
                    isError = true,
                )
            } finally {
                isSaving = false
            }
        }
    }

    fun decryptPending() {
        val text = pendingEncryptedText ?: return
        if (importPassphrase.length < 4) {
            importPassphraseError = resources.getString(R.string.backup_passphrase_invalid)
            return
        }
        isImporting = true
        importPassphraseError = null
        scope.launch {
            val passphrase = importPassphrase.toCharArray()
            try {
                val contents = withContext(Dispatchers.Default) {
                    BackupManager.decryptBackup(text, passphrase)
                }
                pendingEncryptedText = null
                importPassphrase = ""
                showContents(contents)
            } catch (_: BackupCrypto.WrongPassphraseException) {
                importPassphraseError = resources.getString(R.string.backup_passphrase_wrong)
            } catch (exception: Exception) {
                Log.e(TAG, "Could not decrypt backup", exception)
                pendingEncryptedText = null
                importPassphrase = ""
                importMessage = StatusMessage(
                    text = resources.getString(R.string.backup_read_failed),
                    isError = true,
                )
            } finally {
                passphrase.fill('\u0000')
                isImporting = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImporting = true
        importMessage = null
        scope.launch {
            try {
                when (
                    val result = BackupManager.readFromUri(
                        context.applicationContext,
                        uri,
                    )
                ) {
                    is BackupReadResult.Plain -> showContents(result.contents)
                    is BackupReadResult.Encrypted -> {
                        pendingEncryptedText = result.text
                        importPassphrase = ""
                        importPassphraseError = null
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Could not read backup", exception)
                importMessage = StatusMessage(
                    text = resources.getString(R.string.backup_read_failed),
                    isError = true,
                )
            } finally {
                isImporting = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showTitle) {
            Text(
                text = stringResource(R.string.backup_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = stringResource(R.string.backup_description),
            style = MaterialTheme.typography.bodyLarge,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BackupRow(
                    label = stringResource(R.string.backup_appointments),
                    value = appointments.size.toString(),
                )
                BackupRow(
                    label = stringResource(R.string.backup_exam_results),
                    value = results.size.toString(),
                )
                BackupRow(
                    label = stringResource(R.string.backup_diary_entries),
                    value = entries.size.toString(),
                )
                BackupRow(
                    label = stringResource(R.string.backup_medications),
                    value = medications.size.toString(),
                )
                BackupRow(
                    label = stringResource(R.string.backup_medication_doses),
                    value = medicationDoses.size.toString(),
                )
                BackupRow(
                    label = stringResource(R.string.backup_file_name),
                    value = BackupManager.fileName(),
                )
            }
        }

        Button(
            onClick = {
                exportPassphrase = ""
                exportPassphraseError = null
                showExportDialog = true
            },
            enabled = !isSaving && !isImporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.status_saving))
            } else {
                Text(text = stringResource(R.string.backup_save_action))
            }
        }

        saveMessage?.let { message ->
            BackupMessage(text = message.text, isError = message.isError)
        }

        Text(
            text = stringResource(R.string.backup_restore_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.backup_restore_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = { importLauncher.launch(IMPORT_MIME_TYPES) },
            enabled = !isSaving && !isImporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.status_importing))
            } else {
                Text(text = stringResource(R.string.backup_import_action))
            }
        }

        importMessage?.let { message ->
            BackupMessage(text = message.text, isError = message.isError)
        }

        Text(
            text = stringResource(R.string.backup_security_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isVerifying && !isSaving) {
                    showExportDialog = false
                    exportPassphrase = ""
                    exportPassphraseError = null
                }
            },
            title = { Text(text = stringResource(R.string.backup_encrypt_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (lockEnabled) {
                            stringResource(R.string.backup_encrypt_description_pin)
                        } else {
                            stringResource(R.string.backup_encrypt_description_passphrase)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = exportPassphrase,
                        onValueChange = { value ->
                            exportPassphrase = if (lockEnabled) {
                                value.filter { it in '0'..'9' }
                                    .take(8)
                            } else {
                                value.take(64)
                            }
                            exportPassphraseError = null
                        },
                        label = {
                            Text(
                                text = if (lockEnabled) {
                                    stringResource(R.string.security_current_pin)
                                } else {
                                    stringResource(R.string.backup_passphrase_label)
                                },
                            )
                        },
                        singleLine = true,
                        enabled = !isVerifying && !isSaving,
                        isError = exportPassphraseError != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (lockEnabled) {
                                KeyboardType.NumberPassword
                            } else {
                                KeyboardType.Password
                            },
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    exportPassphraseError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val passphrase = exportPassphrase
                        if (passphrase.isEmpty()) {
                            exportPassphraseError = if (lockEnabled) {
                                resources.getString(R.string.security_pin_invalid)
                            } else {
                                resources.getString(R.string.backup_passphrase_invalid)
                            }
                            return@TextButton
                        }
                        scope.launch {
                            isVerifying = true
                            val valid = if (lockEnabled) {
                                withContext(Dispatchers.Default) {
                                    SecurityPreferences.verifyPin(context, passphrase)
                                }
                            } else {
                                passphrase.length >= 4
                            }
                            if (!valid) {
                                exportPassphraseError = if (lockEnabled) {
                                    resources.getString(R.string.security_pin_wrong)
                                } else {
                                    resources.getString(R.string.backup_passphrase_invalid)
                                }
                                isVerifying = false
                                return@launch
                            }
                            if (!showExportDialog) {
                                isVerifying = false
                                return@launch
                            }
                            showExportDialog = false
                            exportPassphrase = ""
                            exportPassphraseError = null
                            isVerifying = false
                            saveEncrypted(passphrase.toCharArray())
                        }
                    },
                    enabled = !isVerifying && !isSaving,
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(text = stringResource(R.string.backup_encrypt_action))
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            if (!isVerifying && !isSaving) {
                                showExportDialog = false
                                exportPassphrase = ""
                                exportPassphraseError = null
                                showPlainWarning = true
                            }
                        },
                        enabled = !isVerifying && !isSaving,
                    ) {
                        Text(text = stringResource(R.string.backup_save_plain_action))
                    }
                    TextButton(
                        onClick = {
                            if (!isVerifying && !isSaving) {
                                showExportDialog = false
                                exportPassphrase = ""
                                exportPassphraseError = null
                            }
                        },
                        enabled = !isVerifying && !isSaving,
                    ) {
                        Text(text = stringResource(R.string.action_cancel))
                    }
                }
            },
        )
    }

    if (showPlainWarning) {
        AlertDialog(
            onDismissRequest = { showPlainWarning = false },
            title = { Text(text = stringResource(R.string.backup_plain_warning_title)) },
            text = { Text(text = stringResource(R.string.backup_plain_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPlainWarning = false
                        savePlain()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = stringResource(R.string.backup_plain_save_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlainWarning = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val encryptedText = pendingEncryptedText
    if (encryptedText != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isImporting) {
                    pendingEncryptedText = null
                    importPassphrase = ""
                    importPassphraseError = null
                }
            },
            title = { Text(text = stringResource(R.string.backup_decrypt_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.backup_decrypt_description),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = importPassphrase,
                        onValueChange = {
                            importPassphrase = it.take(64)
                            importPassphraseError = null
                        },
                        label = { Text(text = stringResource(R.string.backup_passphrase_label)) },
                        singleLine = true,
                        enabled = !isImporting,
                        isError = importPassphraseError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    importPassphraseError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { decryptPending() },
                    enabled = !isImporting,
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(text = stringResource(R.string.backup_decrypt_action))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isImporting) {
                            pendingEncryptedText = null
                            importPassphrase = ""
                            importPassphraseError = null
                        }
                    },
                    enabled = !isImporting,
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val importData = pendingImport
    if (importData != null) {
        val importedAppointments = countsLabel(
            resources,
            importData.appointments.size,
            R.plurals.appointment_count,
        )
        val importedResults = countsLabel(
            resources,
            importData.examResults.size,
            R.plurals.exam_result_count,
        )
        val importedEntries = countsLabel(
            resources,
            importData.diaryEntries.size,
            R.plurals.diary_entry_count,
        )
        val importedMedications = countsLabel(
            resources,
            importData.medications.size,
            R.plurals.medication_count,
        )
        val importedMedicationDoses = countsLabel(
            resources,
            importData.medicationDoses.size,
            R.plurals.medication_dose_count,
        )
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(text = stringResource(R.string.backup_replace_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            R.string.backup_contains,
                            importedAppointments,
                            importedResults,
                            importedEntries,
                            importedMedications,
                            importedMedicationDoses,
                        ),
                    )
                    Text(
                        text = stringResource(
                            R.string.backup_will_replace,
                            countsLabel(
                                resources,
                                appointments.size,
                                R.plurals.appointment_count,
                            ),
                            countsLabel(
                                resources,
                                results.size,
                                R.plurals.exam_result_count,
                            ),
                            countsLabel(
                                resources,
                                entries.size,
                                R.plurals.diary_entry_count,
                            ),
                            countsLabel(
                                resources,
                                medications.size,
                                R.plurals.medication_count,
                            ),
                            countsLabel(
                                resources,
                                medicationDoses.size,
                                R.plurals.medication_dose_count,
                            ),
                        ),
                    )
                    if (importData.skippedEntries > 0) {
                        Text(
                            text = resources.getQuantityString(
                                R.plurals.invalid_entry_will_be_skipped,
                                importData.skippedEntries,
                                importData.skippedEntries,
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImport = null
                        isImporting = true
                        importMessage = null
                        scope.launch {
                            importMessage = try {
                                withContext(NonCancellable) {
                                    onImport(
                                        importData.appointments,
                                        importData.examResults,
                                        importData.diaryEntries,
                                        importData.medications,
                                        importData.medicationDoses,
                                    )
                                }
                                val skipped = if (importData.skippedEntries > 0) {
                                    resources.getQuantityString(
                                        R.plurals.invalid_entry_skipped,
                                        importData.skippedEntries,
                                        importData.skippedEntries,
                                    )
                                } else {
                                    null
                                }
                                StatusMessage(
                                    text = if (skipped == null) {
                                        resources.getString(
                                            R.string.backup_imported,
                                            importedAppointments,
                                            importedResults,
                                            importedEntries,
                                            importedMedications,
                                            importedMedicationDoses,
                                        )
                                    } else {
                                        resources.getString(
                                            R.string.backup_imported_with_skipped,
                                            importedAppointments,
                                            importedResults,
                                            importedEntries,
                                            importedMedications,
                                            importedMedicationDoses,
                                            skipped,
                                        )
                                    },
                                    isError = false,
                                )
                            } catch (exception: Exception) {
                                Log.e(TAG, "Import failed", exception)
                                StatusMessage(
                                    text = resources.getString(R.string.backup_import_failed),
                                    isError = true,
                                )
                            } finally {
                                isImporting = false
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = stringResource(R.string.action_replace))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun BackupRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BackupMessage(
    text: String,
    isError: Boolean,
) {
    Surface(
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun countsLabel(resources: Resources, count: Int, @PluralsRes pluralRes: Int): String =
    resources.getQuantityString(pluralRes, count, count)
