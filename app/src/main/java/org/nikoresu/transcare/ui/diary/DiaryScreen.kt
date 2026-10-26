package org.nikoresu.transcare.ui.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.DiaryEntry
import org.nikoresu.transcare.domain.model.DiaryEntryType
import org.nikoresu.transcare.ui.formatAppDate

private enum class DiaryFilter {
    ALL,
    NOTES,
    THOUGHTS,
}

/** Diary tab: entries grouped by day, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel,
    modifier: Modifier = Modifier,
    onEditorVisibilityChanged: (Boolean) -> Unit = {},
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val isLoaded by viewModel.isLoaded.collectAsStateWithLifecycle()

    var showEditor by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    var newEntryType by remember { mutableStateOf(DiaryEntryType.NOTES) }
    var entryPendingDelete by remember { mutableStateOf<DiaryEntry?>(null) }
    var selectedFilter by rememberSaveable { mutableStateOf(DiaryFilter.ALL) }

    val startNewEntry = {
        editingEntry = null
        newEntryType = if (selectedFilter == DiaryFilter.THOUGHTS) {
            DiaryEntryType.THOUGHTS
        } else {
            DiaryEntryType.NOTES
        }
        showEditor = true
    }

    val filteredEntries = remember(entries, selectedFilter) {
        when (selectedFilter) {
            DiaryFilter.ALL -> entries
            DiaryFilter.NOTES -> entries.filter { it.type == DiaryEntryType.NOTES }
            DiaryFilter.THOUGHTS -> entries.filter { it.type == DiaryEntryType.THOUGHTS }
        }
    }

    LaunchedEffect(showEditor) { onEditorVisibilityChanged(showEditor) }
    DisposableEffect(Unit) {
        onDispose { onEditorVisibilityChanged(false) }
    }

    if (showEditor) {
        DiaryEditorScreen(
            initialEntry = editingEntry,
            initialType = newEntryType,
            onDismiss = { showEditor = false },
            onSave = { entry ->
                if (editingEntry == null) {
                    viewModel.addEntry(entry)
                } else {
                    viewModel.updateEntry(entry)
                }
                showEditor = false
            },
        )
    } else {
        Scaffold(
            modifier = modifier,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(title = { Text(text = stringResource(R.string.diary_title)) })
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = startNewEntry,
                ) {
                    Text(text = stringResource(R.string.diary_add))
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (isLoaded && entries.isNotEmpty()) {
                    DiaryFilterRow(
                        selectedFilter = selectedFilter,
                        onSelect = { selectedFilter = it },
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        !isLoaded -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }

                        entries.isEmpty() -> EmptyDiaryState(
                            onAdd = startNewEntry,
                            modifier = Modifier.fillMaxSize(),
                        )

                        filteredEntries.isEmpty() -> FilteredEmptyState(
                            onShowAll = { selectedFilter = DiaryFilter.ALL },
                            modifier = Modifier.fillMaxSize(),
                        )

                        else -> DiaryList(
                            entries = filteredEntries,
                            onEdit = { entry ->
                                editingEntry = entry
                                showEditor = true
                            },
                            onDelete = { entry -> entryPendingDelete = entry },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    val entryToDelete = entryPendingDelete
    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryPendingDelete = null },
            title = { Text(text = stringResource(R.string.diary_delete_title)) },
            text = { Text(text = stringResource(R.string.diary_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEntry(entryToDelete)
                        entryPendingDelete = null
                    },
                ) {
                    Text(text = stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { entryPendingDelete = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun DiaryFilterRow(
    selectedFilter: DiaryFilter,
    onSelect: (DiaryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedFilter == DiaryFilter.ALL,
            onClick = { onSelect(DiaryFilter.ALL) },
            label = { Text(text = stringResource(R.string.diary_filter_all)) },
        )
        FilterChip(
            selected = selectedFilter == DiaryFilter.NOTES,
            onClick = { onSelect(DiaryFilter.NOTES) },
            label = { Text(text = DiaryEntryType.NOTES.label()) },
        )
        FilterChip(
            selected = selectedFilter == DiaryFilter.THOUGHTS,
            onClick = { onSelect(DiaryFilter.THOUGHTS) },
            label = { Text(text = DiaryEntryType.THOUGHTS.label()) },
        )
    }
}

@Composable
private fun DiaryList(
    entries: List<DiaryEntry>,
    onEdit: (DiaryEntry) -> Unit,
    onDelete: (DiaryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = remember(entries) {
        entries
            .sortedWith(
                compareByDescending<DiaryEntry> { it.dayStartMillis }
                    .thenByDescending { it.id },
            )
            .groupBy { it.date }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        grouped.forEach { (date, dayEntries) ->
            item(key = "day-${date.toEpochDay()}") {
                DayHeader(date = date)
            }
            items(dayEntries, key = { it.id }) { entry ->
                DiaryEntryCard(
                    entry = entry,
                    onEdit = { onEdit(entry) },
                    onDelete = { onDelete(entry) },
                )
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    Text(
        text = formatAppDate(date),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun DiaryEntryCard(
    entry: DiaryEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = entry.type.label(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(
                text = entry.title.ifBlank { stringResource(R.string.diary_untitled) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (entry.text.isNotBlank()) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDelete) {
                    Text(text = stringResource(R.string.action_delete))
                }
            }
        }
    }
}

@Composable
private fun FilteredEmptyState(
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.diary_filter_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.diary_filter_empty_description),
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(
            onClick = onShowAll,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.diary_show_all))
        }
    }
}

@Composable
private fun EmptyDiaryState(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.diary_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.diary_empty_description),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.diary_empty_action),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.diary_empty_button))
        }
    }
}
