package org.nikoresu.transcare.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.nikoresu.transcare.R
import org.nikoresu.transcare.security.BiometricAvailability
import org.nikoresu.transcare.security.PinHasher
import org.nikoresu.transcare.security.SecurityPreferences

private data class StatusMessage(
    val text: String,
    val isError: Boolean,
)

private sealed interface SecurityDialog {
    data object SetupPin : SecurityDialog
    data object ChangePin : SecurityDialog
    data object DisableLock : SecurityDialog
    data object EnableBiometrics : SecurityDialog
}

/**
 * App-lock controls. The PIN is the same secret used to encrypt backups, so
 * changing or removing it is protected by the current PIN.
 */
@Composable
fun SecuritySection(
    onLockEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var lockEnabled by remember { mutableStateOf(SecurityPreferences.isLockEnabled(context)) }
    var biometricEnabled by remember {
        mutableStateOf(SecurityPreferences.isBiometricEnabled(context))
    }
    val biometricAvailable = remember { BiometricAvailability.isAvailable(context) }
    var dialog by remember { mutableStateOf<SecurityDialog?>(null) }
    var message by remember { mutableStateOf<StatusMessage?>(null) }

    Text(
        text = stringResource(R.string.security_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.security_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.security_app_lock),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.security_app_lock_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Switch(
                    checked = lockEnabled,
                    onCheckedChange = { checked ->
                        dialog = if (checked) {
                            SecurityDialog.SetupPin
                        } else {
                            SecurityDialog.DisableLock
                        }
                    },
                )
            }

            if (lockEnabled) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.security_biometric),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = if (biometricAvailable) {
                                stringResource(R.string.security_biometric_description)
                            } else {
                                stringResource(R.string.security_biometric_unavailable)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Switch(
                        checked = biometricEnabled && biometricAvailable,
                        enabled = biometricAvailable,
                        onCheckedChange = { checked ->
                            if (checked) {
                                dialog = SecurityDialog.EnableBiometrics
                            } else {
                                SecurityPreferences.setBiometricEnabled(context, false)
                                biometricEnabled = false
                                message = StatusMessage(
                                    text = resources.getString(
                                        R.string.security_biometric_disabled,
                                    ),
                                    isError = false,
                                )
                            }
                        },
                    )
                }

                HorizontalDivider()
                TextButton(
                    onClick = { dialog = SecurityDialog.ChangePin },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(text = stringResource(R.string.security_change_pin))
                }
            }
        }
    }

    Text(
        text = stringResource(R.string.security_pin_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    message?.let { current ->
        SecurityMessage(text = current.text, isError = current.isError)
    }

    when (dialog) {
        SecurityDialog.SetupPin -> PinSetupDialog(
            onDismiss = { dialog = null },
            onSave = { pin ->
                val saved = SecurityPreferences.setPin(context, pin)
                if (saved) {
                    SecurityPreferences.setLockEnabled(context, true)
                    lockEnabled = true
                    biometricEnabled = SecurityPreferences.isBiometricEnabled(context)
                    dialog = null
                    message = StatusMessage(
                        text = resources.getString(R.string.security_app_lock_enabled),
                        isError = false,
                    )
                    onLockEnabledChanged(true)
                }
                saved
            },
        )

        SecurityDialog.ChangePin -> PinChangeDialog(
            onDismiss = { dialog = null },
            verifyCurrent = { SecurityPreferences.verifyPin(context, it) },
            onChange = { SecurityPreferences.setPin(context, it) },
            onSuccess = {
                dialog = null
                message = StatusMessage(
                    text = resources.getString(R.string.security_pin_changed),
                    isError = false,
                )
            },
        )

        SecurityDialog.DisableLock -> PinVerifyDialog(
            title = stringResource(R.string.security_pin_disable_title),
            description = stringResource(R.string.security_pin_disable_description),
            confirmLabel = stringResource(R.string.security_disable),
            onDismiss = { dialog = null },
            verify = { SecurityPreferences.verifyPin(context, it) },
            onVerified = {
                SecurityPreferences.disableLock(context)
                lockEnabled = false
                biometricEnabled = false
                dialog = null
                message = StatusMessage(
                    text = resources.getString(R.string.security_app_lock_disabled),
                    isError = false,
                )
                onLockEnabledChanged(false)
            },
        )

        SecurityDialog.EnableBiometrics -> PinVerifyDialog(
            title = stringResource(R.string.security_pin_enable_biometric_title),
            description = stringResource(R.string.security_pin_enable_biometric_description),
            confirmLabel = stringResource(R.string.security_enable),
            onDismiss = { dialog = null },
            verify = { SecurityPreferences.verifyPin(context, it) },
            onVerified = {
                SecurityPreferences.setBiometricEnabled(context, true)
                biometricEnabled = true
                dialog = null
                message = StatusMessage(
                    text = resources.getString(R.string.security_biometric_enabled),
                    isError = false,
                )
            },
        )

        null -> Unit
    }
}

@Composable
private fun PinSetupDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Boolean,
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.security_pin_setup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.security_pin_setup_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PinField(
                    value = pin,
                    onValueChange = { pin = it; error = null },
                    label = stringResource(R.string.security_new_pin),
                )
                PinField(
                    value = confirmation,
                    onValueChange = { confirmation = it; error = null },
                    label = stringResource(R.string.security_confirm_pin),
                )
                error?.let { messageRes ->
                    Text(
                        text = stringResource(messageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        !PinHasher.isValidPin(pin) -> R.string.security_pin_invalid
                        pin != confirmation -> R.string.security_pin_mismatch
                        !onSave(pin) -> R.string.security_pin_setup_failed
                        else -> null
                    }
                },
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun PinChangeDialog(
    onDismiss: () -> Unit,
    verifyCurrent: (String) -> Boolean,
    onChange: (String) -> Boolean,
    onSuccess: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.security_pin_change_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.security_pin_change_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PinField(
                    value = current,
                    onValueChange = { current = it; error = null },
                    label = stringResource(R.string.security_current_pin),
                )
                PinField(
                    value = newPin,
                    onValueChange = { newPin = it; error = null },
                    label = stringResource(R.string.security_new_pin),
                )
                PinField(
                    value = confirmation,
                    onValueChange = { confirmation = it; error = null },
                    label = stringResource(R.string.security_confirm_pin),
                )
                error?.let { messageRes ->
                    Text(
                        text = stringResource(messageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        !verifyCurrent(current) -> R.string.security_pin_wrong
                        !PinHasher.isValidPin(newPin) -> R.string.security_pin_invalid
                        newPin != confirmation -> R.string.security_pin_mismatch
                        !onChange(newPin) -> R.string.security_pin_setup_failed
                        else -> null
                    }
                    if (error == null) onSuccess()
                },
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun PinVerifyDialog(
    title: String,
    description: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    verify: (String) -> Boolean,
    onVerified: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
                PinField(
                    value = pin,
                    onValueChange = { pin = it; error = null },
                    label = stringResource(R.string.security_current_pin),
                )
                error?.let { messageRes ->
                    Text(
                        text = stringResource(messageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = if (verify(pin)) null else R.string.security_pin_wrong
                    if (error == null) onVerified()
                },
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            if (
                candidate.length <= PinHasher.MAX_PIN_LENGTH &&
                candidate.all { it in '0'..'9' }
            ) {
                onValueChange(candidate)
            }
        },
        label = { Text(text = label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecurityMessage(
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
