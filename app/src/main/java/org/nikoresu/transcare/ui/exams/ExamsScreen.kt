package org.nikoresu.transcare.ui.exams

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.text.format.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.domain.model.ExamResult
import org.nikoresu.transcare.domain.model.ExamStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    viewModel: ExamsViewModel,
    modifier: Modifier = Modifier,
    onEditorVisibilityChanged: (Boolean) -> Unit = {},
) {
    val results by viewModel.results.collectAsStateWithLifecycle()

    var selectedMetric by remember { mutableStateOf<ExamMetric?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingResult by remember { mutableStateOf<ExamResult?>(null) }
    var pendingDelete by remember { mutableStateOf<ExamResult?>(null) }

    LaunchedEffect(showEditor) { onEditorVisibilityChanged(showEditor) }
    DisposableEffect(Unit) {
        onDispose { onEditorVisibilityChanged(false) }
    }
    LaunchedEffect(results, selectedMetric) {
        if (selectedMetric != null && results.none { it.metric == selectedMetric }) {
            selectedMetric = null
        }
    }

    if (showEditor) {
        ExamEditorScreen(
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
    } else {
        ExamsContent(
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
    modifier: Modifier = Modifier,
) {
    val historyByMetric = remember(results) {
        results.groupBy { it.metric }
            .mapValues { (_, values) ->
                values.sortedWith(compareBy({ it.dayStartMillis }, { it.id }))
            }
    }
    val metricsWithResults = remember(historyByMetric) {
        ExamMetric.entries.filter { it in historyByMetric }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.exams_title)) },
                actions = {
                    if (selectedMetric != null) {
                        TextButton(onClick = { onSelectMetric(null) }) {
                            Text(text = stringResource(R.string.exams_overview))
                        }
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
                EmptyExamsState()
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
                            history = historyByMetric.getValue(overviewMetric),
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
                        history = historyByMetric[metric].orEmpty(),
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
private fun EmptyExamsState() {
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
    history: List<ExamResult>,
    onClick: () -> Unit,
) {
    val latest = history.lastOrNull() ?: return

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

            if (history.size > 1) {
                Sparkline(
                    values = history.map { it.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                )
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
    history: List<ExamResult>,
    onEdit: (ExamResult) -> Unit,
    onDelete: (ExamResult) -> Unit,
) {
    if (history.isEmpty()) {
        Text(
            text = stringResource(R.string.exams_no_results, metric.displayName()),
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }

    val latest = history.lastOrNull()

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
                if (history.size > 1) {
                    MetricChart(
                        metric = metric,
                        history = history,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatExamDate(history.first().date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatExamDate(history.last().date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (metric.referenceLow != null || metric.referenceHigh != null) {
                        Text(
                            text = stringResource(R.string.exams_reference_band),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

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
                Text(
                    text = if (history.size > 1) {
                        pluralStringResource(
                            R.plurals.exams_trend_count,
                            history.size,
                            history.size,
                        )
                    } else {
                        stringResource(R.string.exams_add_for_trend)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = stringResource(R.string.exams_history),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        history.asReversed().forEach { result ->
            ExamResultItem(
                result = result,
                onEdit = { onEdit(result) },
                onDelete = { onDelete(result) },
            )
        }
    }
}

@Composable
private fun ExamResultItem(
    result: ExamResult,
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

@Composable
private fun MetricChart(
    metric: ExamMetric,
    history: List<ExamResult>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val values = history.map { it.value }

    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val referenceLow = metric.referenceLow
        val referenceHigh = metric.referenceHigh

        var minValue = values.minOrNull() ?: return@Canvas
        var maxValue = values.maxOrNull() ?: return@Canvas
        if (referenceLow != null) minValue = minOf(minValue, referenceLow)
        if (referenceHigh != null) maxValue = maxOf(maxValue, referenceHigh)
        if (minValue == maxValue) {
            minValue -= 1.0
            maxValue += 1.0
        }

        val padding = (maxValue - minValue) * 0.08
        minValue -= padding
        maxValue += padding

        val horizontalPadding = 8.dp.toPx()
        val verticalPadding = 10.dp.toPx()
        val canvasWidth = size.width
        val canvasHeight = size.height
        val usableWidth = (canvasWidth - horizontalPadding * 2).coerceAtLeast(1f)
        val usableHeight = (canvasHeight - verticalPadding * 2).coerceAtLeast(1f)
        val span = maxValue - minValue

        fun yFor(value: Double): Float {
            val fraction = ((value - minValue) / span).toFloat()
            return verticalPadding + usableHeight * (1f - fraction)
        }

        fun xFor(index: Int): Float =
            if (values.size == 1) {
                canvasWidth / 2f
            } else {
                horizontalPadding + usableWidth * index / (values.size - 1)
            }

        val highY = referenceHigh?.let { yFor(it) }
        val lowY = referenceLow?.let { yFor(it) }
        when {
            highY != null && lowY != null -> drawRect(
                color = bandColor,
                topLeft = Offset(0f, highY),
                size = Size(canvasWidth, lowY - highY),
            )

            highY != null -> drawRect(
                color = bandColor,
                topLeft = Offset(0f, highY),
                size = Size(canvasWidth, canvasHeight - highY),
            )

            lowY != null -> drawRect(
                color = bandColor,
                topLeft = Offset(0f, 0f),
                size = Size(canvasWidth, lowY),
            )
        }

        val dash = PathEffect.dashPathEffect(
            intervals = floatArrayOf(6.dp.toPx(), 6.dp.toPx()),
        )
        if (referenceHigh != null) {
            drawLine(
                color = guideColor,
                start = Offset(0f, yFor(referenceHigh)),
                end = Offset(canvasWidth, yFor(referenceHigh)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash,
            )
        }
        if (referenceLow != null) {
            drawLine(
                color = guideColor,
                start = Offset(0f, yFor(referenceLow)),
                end = Offset(canvasWidth, yFor(referenceLow)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash,
            )
        }

        if (values.size == 1) {
            drawCircle(
                color = lineColor,
                radius = 5.dp.toPx(),
                center = Offset(xFor(0), yFor(values[0])),
            )
        } else {
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = xFor(index)
                val y = yFor(value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            values.forEachIndexed { index, value ->
                drawCircle(
                    color = lineColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(xFor(index), yFor(value)),
                )
            }
        }
    }
}

@Composable
private fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val minValue = values.minOrNull() ?: return@Canvas
        val maxValue = values.maxOrNull() ?: return@Canvas
        val padding = 4.dp.toPx()
        val canvasWidth = size.width
        val canvasHeight = size.height
        val usableWidth = (canvasWidth - padding * 2).coerceAtLeast(1f)
        val usableHeight = (canvasHeight - padding * 2).coerceAtLeast(1f)
        val span = maxValue - minValue

        fun yFor(value: Double): Float =
            if (span == 0.0) {
                canvasHeight / 2f
            } else {
                padding + usableHeight * (1f - ((value - minValue) / span).toFloat())
            }

        fun xFor(index: Int): Float =
            if (values.size == 1) {
                canvasWidth / 2f
            } else {
                padding + usableWidth * index / (values.size - 1)
            }

        if (values.size == 1) {
            drawCircle(
                color = lineColor,
                radius = 3.dp.toPx(),
                center = Offset(xFor(0), yFor(values[0])),
            )
        } else {
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = xFor(index)
                val y = yFor(value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

@Composable
private fun formatExamDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMd")
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}
