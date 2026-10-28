package org.nikoresu.transcare.ui.exams

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.domain.model.ExamResult
import org.nikoresu.transcare.domain.model.ExamStatus
import org.nikoresu.transcare.domain.model.ExamTrend
import org.nikoresu.transcare.pdf.ParsedPdfExams
import org.nikoresu.transcare.pdf.PdfExamExtractor
import org.nikoresu.transcare.pdf.PdfImportException
import org.nikoresu.transcare.ui.attachments.AttachmentCountLabel

/** Some document providers report PDFs with a generic MIME type. */
private val PDF_MIME_TYPES = arrayOf("application/pdf", "application/octet-stream")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    viewModel: ExamsViewModel,
    modifier: Modifier = Modifier,
    onEditorVisibilityChanged: (Boolean) -> Unit = {},
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedMetric by remember { mutableStateOf<ExamMetric?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingResult by remember { mutableStateOf<ExamResult?>(null) }
    var pendingDelete by remember { mutableStateOf<ExamResult?>(null) }
    var pdfPreview by rememberSaveable { mutableStateOf<ParsedPdfExams?>(null) }
    var isReadingPdf by remember { mutableStateOf(false) }
    var pdfError by remember { mutableStateOf<PdfImportException?>(null) }

    LaunchedEffect(showEditor, pdfPreview) {
        onEditorVisibilityChanged(showEditor || pdfPreview != null)
    }
    DisposableEffect(Unit) {
        onDispose { onEditorVisibilityChanged(false) }
    }
    LaunchedEffect(results, selectedMetric) {
        if (selectedMetric != null && results.none { it.metric == selectedMetric }) {
            selectedMetric = null
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isReadingPdf = true
        pdfError = null
        scope.launch {
            try {
                pdfPreview = PdfExamExtractor.extract(context.applicationContext, uri)
            } catch (exception: PdfImportException) {
                pdfError = exception
            } catch (exception: Exception) {
                pdfError = PdfImportException.Unreadable(exception)
            } finally {
                isReadingPdf = false
            }
        }
    }

    val preview = pdfPreview
    when {
        showEditor -> ExamEditorScreen(
            initialResult = editingResult,
            initialMetric = editingResult?.metric ?: selectedMetric,
            onDismiss = { showEditor = false },
            onSave = { result ->
                if (editingResult == null) {
                    viewModel.addResult(result)
                } else {
                    viewModel.updateResult(result)
                }
                if (selectedMetric != null) {
                    selectedMetric = result.metric
                }
                showEditor = false
            },
        )

        preview != null -> PdfImportScreen(
            parsed = preview,
            existingResults = results,
            onDismiss = { pdfPreview = null },
            onImport = { imported ->
                pdfPreview = null
                scope.launch {
                    withContext(NonCancellable) { viewModel.addResults(imported) }
                    snackbarHostState.showSnackbar(
                        resources.getQuantityString(
                            R.plurals.exams_pdf_imported_count,
                            imported.size,
                            imported.size,
                        ),
                    )
                }
            },
        )

        else -> ExamsContent(
            results = results,
            selectedMetric = selectedMetric,
            onSelectMetric = { selectedMetric = it },
            onAdd = {
                editingResult = null
                showEditor = true
            },
            onEdit = { result ->
                editingResult = result
                showEditor = true
            },
            onDelete = { pendingDelete = it },
            onImportPdf = { pdfLauncher.launch(PDF_MIME_TYPES) },
            snackbarHostState = snackbarHostState,
            modifier = modifier,
        )
    }

    val resultToDelete = pendingDelete
    if (resultToDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text = stringResource(R.string.exams_delete_title)) },
            text = { Text(text = stringResource(R.string.exams_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteResult(resultToDelete)
                        pendingDelete = null
                    },
                ) {
                    Text(text = stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val importError = pdfError
    if (importError != null) {
        AlertDialog(
            onDismissRequest = { pdfError = null },
            title = { Text(text = stringResource(R.string.exams_pdf_error_title)) },
            text = { Text(text = stringResource(importError.messageRes)) },
            confirmButton = {
                TextButton(onClick = { pdfError = null }) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
        )
    }

    if (isReadingPdf) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(text = stringResource(R.string.exams_pdf_reading))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamsContent(
    results: List<ExamResult>,
    selectedMetric: ExamMetric?,
    onSelectMetric: (ExamMetric?) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ExamResult) -> Unit,
    onDelete: (ExamResult) -> Unit,
    onImportPdf: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val trendsByMetric = remember(results) {
        results.groupBy { it.metric }
            .mapValues { (metric, values) -> ExamTrend.of(metric, values) }
    }
    val metricsWithResults = remember(trendsByMetric) {
        ExamMetric.entries.filter { it in trendsByMetric }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.exams_title)) },
                actions = {
                    if (selectedMetric != null) {
                        TextButton(onClick = { onSelectMetric(null) }) {
                            Text(text = stringResource(R.string.exams_overview))
                        }
                    }
                    TextButton(onClick = onImportPdf) {
                        Text(text = stringResource(R.string.exams_pdf_short))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) {
                Text(text = stringResource(R.string.exams_add))
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (results.isEmpty()) {
                EmptyExamsState(onImportPdf = onImportPdf)
            } else {
                MetricFilterRow(
                    metrics = metricsWithResults,
                    selectedMetric = selectedMetric,
                    onSelect = onSelectMetric,
                )

                val metric = selectedMetric
                if (metric == null) {
                    Text(
                        text = stringResource(R.string.exams_overview),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    metricsWithResults.forEach { overviewMetric ->
                        MetricOverviewCard(
                            metric = overviewMetric,
                            trend = trendsByMetric.getValue(overviewMetric),
                            onClick = { onSelectMetric(overviewMetric) },
                        )
                    }
                    Text(
                        text = stringResource(R.string.exams_reference_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    MetricDetail(
                        metric = metric,
                        trend = trendsByMetric.getValue(metric),
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun EmptyExamsState(onImportPdf: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.exams_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.exams_empty_description),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.exams_empty_action),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.exams_pdf_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onImportPdf,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.exams_import_pdf))
        }
    }
}

@Composable
private fun MetricFilterRow(
    metrics: List<ExamMetric>,
    selectedMetric: ExamMetric?,
    onSelect: (ExamMetric?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedMetric == null,
            onClick = { onSelect(null) },
            label = { Text(text = stringResource(R.string.exams_all)) },
        )
        metrics.forEach { metric ->
            FilterChip(
                selected = selectedMetric == metric,
                onClick = { onSelect(metric) },
                label = { Text(text = metric.shortLabel()) },
            )
        }
    }
}

@Composable
private fun MetricOverviewCard(
    metric: ExamMetric,
    trend: ExamTrend,
    onClick: () -> Unit,
) {
    val latest = trend.latest ?: return
    val deltaLabel = trend.deltaLabel()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metric.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.exams_latest,
                            formatExamDate(latest.date),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(status = latest.status)
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = latest.formattedValue,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = metric.unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            deltaLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (trend.points.size > 1) {
                MiniTrendChart(trend = trend)
            }

            Text(
                text = stringResource(R.string.exams_reference, metric.referenceLabelText()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricDetail(
    metric: ExamMetric,
    trend: ExamTrend,
    onEdit: (ExamResult) -> Unit,
    onDelete: (ExamResult) -> Unit,
) {
    if (trend.points.isEmpty()) {
        Text(
            text = stringResource(R.string.exams_no_results, metric.displayName()),
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }

    val latest = trend.latest
    val deltaLabel = trend.deltaLabel()
    val averageGapLabel = trend.averageGapLabel()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metric.displayName(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.exams_reference, metric.referenceLabelText()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val referenceNote = metric.referenceNote()
                referenceNote?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            latest?.let { StatusPill(status = it.status) }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                latest?.let { result ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = result.formattedValue,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = metric.unit,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }

                deltaLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (trend.points.size > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    MetricTrendChart(trend = trend)
                }

                Text(
                    text = if (trend.points.size > 1) {
                        pluralStringResource(
                            R.plurals.exams_trend_count,
                            trend.points.size,
                            trend.points.size,
                        )
                    } else {
                        stringResource(R.string.exams_add_for_trend)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                averageGapLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.exams_history),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        trend.points.asReversed().forEach { point ->
            ExamResultItem(
                result = point.result,
                daysSincePrevious = point.daysSincePrevious,
                onEdit = { onEdit(point.result) },
                onDelete = { onDelete(point.result) },
            )
        }
    }
}

@Composable
private fun ExamResultItem(
    result: ExamResult,
    daysSincePrevious: Long?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.formattedValue,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = result.metric.unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                StatusPill(status = result.status)
            }
            Text(
                text = formatExamDate(result.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            daysSincePrevious?.let { days ->
                Text(
                    text = examGapLabel(days),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AttachmentCountLabel(count = result.attachments.size)
            if (result.note.isNotBlank()) {
                Text(
                    text = result.note,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEdit) {
                    Text(text = stringResource(R.string.action_edit))
                }
                TextButton(onClick = onDelete) {
                    Text(text = stringResource(R.string.action_delete))
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: ExamStatus) {
    val color = statusColor(status)
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = CircleShape,
    ) {
        Text(
            text = status.labelText(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun statusColor(status: ExamStatus) = when (status) {
    ExamStatus.BELOW -> MaterialTheme.colorScheme.tertiary
    ExamStatus.IN_RANGE -> MaterialTheme.colorScheme.primary
    ExamStatus.ABOVE -> MaterialTheme.colorScheme.error
    ExamStatus.NO_RANGE -> MaterialTheme.colorScheme.onSurfaceVariant
}
