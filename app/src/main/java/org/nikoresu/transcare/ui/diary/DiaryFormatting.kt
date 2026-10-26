package org.nikoresu.transcare.ui.diary

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.DiaryEntryType

@Composable
internal fun DiaryEntryType.label(): String = stringResource(
    when (this) {
        DiaryEntryType.NOTES -> R.string.diary_type_notes
        DiaryEntryType.THOUGHTS -> R.string.diary_type_thoughts
    },
)
