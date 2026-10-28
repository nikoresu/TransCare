package org.nikoresu.transcare.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nikoresu.transcare.R
import org.nikoresu.transcare.security.PinHasher

/**
 * Full-screen gate shown before any personal data is composed.
 *
 * The PIN is checked off the main thread by the caller. Biometrics are an
 * optional shortcut and never replace the PIN for backup encryption.
 */
@Composable
fun LockScreen(
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    externalError: String?,
    onUnlockWithPin: suspend (String) -> Boolean,
    onUnlockWithBiometrics: () -> Unit,
    onErrorConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submitPin() {
        if (isChecking || pin.length < PinHasher.MIN_PIN_LENGTH) return
        scope.launch {
            isChecking = true
            pinError = false
            onErrorConsumed()
            val unlocked = try {
                onUnlockWithPin(pin)
            } catch (_: Exception) {
                false
            }
            if (!unlocked) {
                pinError = true
                pin = ""
            }
            isChecking = false
        }
    }

    LaunchedEffect(biometricEnabled, biometricAvailable) {
        if (biometricEnabled && biometricAvailable && !autoPrompted) {
            autoPrompted = true
            delay(350)
            onUnlockWithBiometrics()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🔒", fontSize = 32.sp)
            }

            Spacer(modifier = Modifier.size(24.dp))
            Text(
                text = stringResource(R.string.security_lock_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.security_lock_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.size(32.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { value ->
                    if (
                        value.length <= PinHasher.MAX_PIN_LENGTH &&
                        value.all { it in '0'..'9' }
                    ) {
                        pin = value
                        pinError = false
                        onErrorConsumed()
                    }
                },
                label = { Text(text = stringResource(R.string.security_lock_pin)) },
                singleLine = true,
                enabled = !isChecking,
                isError = pinError || externalError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            val errorText = when {
                pinError -> stringResource(R.string.security_lock_wrong_pin)
                externalError != null -> externalError
                else -> null
            }
            if (errorText != null) {
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.size(16.dp))
            Button(
                onClick = { submitPin() },
                enabled = pin.length >= PinHasher.MIN_PIN_LENGTH && !isChecking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.status_loading))
                } else {
                    Text(text = stringResource(R.string.security_unlock))
                }
            }

            if (biometricEnabled && biometricAvailable) {
                Spacer(modifier = Modifier.size(12.dp))
                OutlinedButton(
                    onClick = onUnlockWithBiometrics,
                    enabled = !isChecking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.security_unlock_biometric))
                }
            }

            Spacer(modifier = Modifier.size(24.dp))
            Text(
                text = stringResource(R.string.security_lock_forgot_pin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
