package org.nikoresu.transcare.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.MedicalProfile

/**
 * Selectable medical profiles. The masculinizing option is visible but
 * disabled until that part of the app is supported.
 */
@Composable
fun MedicalProfileOptions(
    selected: MedicalProfile?,
    onSelect: (MedicalProfile?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            val feminizing = MedicalProfile.FEMINIZING_HRT
            MedicalProfileOption(
                label = stringResource(R.string.medical_profile_feminizing),
                description = stringResource(R.string.medical_profile_feminizing_description),
                selected = selected == feminizing,
                enabled = enabled && feminizing.isAvailable,
                onClick = { onSelect(feminizing) },
            )
            HorizontalDivider()
            val masculinizing = MedicalProfile.MASCULINIZING_HRT
            MedicalProfileOption(
                label = stringResource(R.string.medical_profile_masculinizing),
                description = stringResource(R.string.medical_profile_masculinizing_description),
                selected = selected == masculinizing,
                enabled = enabled && masculinizing.isAvailable,
                badge = if (masculinizing.isAvailable) {
                    null
                } else {
                    stringResource(R.string.medical_profile_coming_soon)
                },
                onClick = { onSelect(masculinizing) },
            )
            HorizontalDivider()
            MedicalProfileOption(
                label = stringResource(R.string.medical_profile_none),
                selected = selected == null,
                enabled = enabled,
                onClick = { onSelect(null) },
            )
        }
    }
}

@Composable
private fun MedicalProfileOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    badge: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (badge != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
