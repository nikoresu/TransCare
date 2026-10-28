package org.nikoresu.transcare.ui.exams

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.ExamResult
import org.nikoresu.transcare.domain.model.ExamStatus
import org.nikoresu.transcare.domain.model.ExamTrend
import org.nikoresu.transcare.domain.model.ExamTrendPoint
import org.nikoresu.transcare.domain.model.formatExamNumber

private val ChartHeight = 160.dp
private val MiniChartHeight = 40.dp
private val AxisLabelWidth = 56.dp

/**
 * Line chart for one metric over time. The horizontal axis follows the real
 * dates, so the distance between points shows the gap between tests. The shaded
 * area is the reference range, and the latest point is drawn larger.
 */
@Composable
internal fun MetricTrendChart(
    trend: ExamTrend,
    modifier: Modifier = Modifier,
) {
    val points = trend.points
    if (points.isEmpty()) return

    val range = trend.chartRange
    val minValue = range.start
    val maxValue = range.endInclusive
    val midValue = (minValue + maxValue) / 2.0

    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val belowColor = MaterialTheme.colorScheme.tertiary
    val aboveColor = MaterialTheme.colorScheme.error
    val noRangeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pointColors = points.map { point ->
        point.result.statusColor(belowColor, aboveColor, noRangeColor, lineColor)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(AxisLabelWidth),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                AxisLabel(text = formatExamNumber(maxValue), color = labelColor)
                AxisLabel(text = formatExamNumber(midValue), color = labelColor)
                AxisLabel(text = formatExamNumber(minValue), color = labelColor)
            }

            Spacer(modifier = Modifier.width(6.dp))

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                drawTrend(
                    points = points,
                    minValue = minValue,
                    maxValue = maxValue,
                    referenceLow = trend.metric.referenceLow,
                    referenceHigh = trend.metric.referenceHigh,
                    lineColor = lineColor,
                    bandColor = bandColor,
                    guideColor = guideColor,
                    surfaceColor = surfaceColor,
                    pointColors = pointColors,
                    strokeWidth = 2.dp.toPx(),
                    dotRadius = 3.5.dp.toPx(),
                    horizontalPadding = 6.dp.toPx(),
                    drawGuides = true,
                    highlightLatest = true,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AxisLabelWidth + 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AxisLabel(text = formatExamDate(points.first().result.date), color = labelColor)
            if (points.size > 1) {
                AxisLabel(text = formatExamDate(points.last().result.date), color = labelColor)
            }
        }

        if (trend.hasReferenceRange) {
            Text(
                text = stringResource(R.string.exams_reference_band),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier.padding(start = AxisLabelWidth + 6.dp),
            )
        }
    }
}

/** Compact trend line for the overview cards, with the same date spacing. */
@Composable
internal fun MiniTrendChart(
    trend: ExamTrend,
    modifier: Modifier = Modifier,
) {
    val points = trend.points
    if (points.size < 2) return

    val range = trend.chartRange
    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val belowColor = MaterialTheme.colorScheme.tertiary
    val aboveColor = MaterialTheme.colorScheme.error
    val noRangeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pointColors = points.map { point ->
        point.result.statusColor(belowColor, aboveColor, noRangeColor, lineColor)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniChartHeight),
    ) {
        drawTrend(
            points = points,
            minValue = range.start,
            maxValue = range.endInclusive,
            referenceLow = trend.metric.referenceLow,
            referenceHigh = trend.metric.referenceHigh,
            lineColor = lineColor,
            bandColor = bandColor,
            guideColor = Color.Transparent,
            surfaceColor = surfaceColor,
            pointColors = pointColors,
            strokeWidth = 1.5.dp.toPx(),
            dotRadius = 2.dp.toPx(),
            horizontalPadding = 2.dp.toPx(),
            drawGuides = false,
            highlightLatest = false,
        )
    }
}

@Composable
private fun AxisLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
    )
}

private fun ExamResult.statusColor(
    belowColor: Color,
    aboveColor: Color,
    noRangeColor: Color,
    inRangeColor: Color,
): Color = when (status) {
    ExamStatus.BELOW -> belowColor
    ExamStatus.IN_RANGE -> inRangeColor
    ExamStatus.ABOVE -> aboveColor
    ExamStatus.NO_RANGE -> noRangeColor
}

/**
 * Draws the reference band, grid lines and the line itself. The x position is
 * based on [ExamTrendPoint.daysSinceFirst], so long gaps between tests appear
 * as wider spaces on the chart.
 */
private fun DrawScope.drawTrend(
    points: List<ExamTrendPoint>,
    minValue: Double,
    maxValue: Double,
    referenceLow: Double?,
    referenceHigh: Double?,
    lineColor: Color,
    bandColor: Color,
    guideColor: Color,
    surfaceColor: Color,
    pointColors: List<Color>,
    strokeWidth: Float,
    dotRadius: Float,
    horizontalPadding: Float,
    drawGuides: Boolean,
    highlightLatest: Boolean,
) {
    if (points.isEmpty()) return
    val span = maxValue - minValue
    if (span <= 0.0) return

    val usableWidth = (size.width - horizontalPadding * 2).coerceAtLeast(1f)
    val spanDays = points.last().daysSinceFirst.toDouble()

    fun yFor(value: Double): Float {
        val fraction = ((value - minValue) / span).toFloat().coerceIn(0f, 1f)
        return size.height * (1f - fraction)
    }

    fun xFor(index: Int): Float = when {
        points.size == 1 -> size.width / 2f
        spanDays <= 0.0 -> horizontalPadding + usableWidth * index / (points.size - 1)
        else -> horizontalPadding + usableWidth * (points[index].daysSinceFirst / spanDays).toFloat()
    }

    if (drawGuides) {
        listOf(0f, size.height / 2f, size.height).forEach { y ->
            drawLine(
                color = guideColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }

    val highY = referenceHigh?.let { yFor(it) }
    val lowY = referenceLow?.let { yFor(it) }
    when {
        highY != null && lowY != null -> drawRect(
            color = bandColor,
            topLeft = Offset(0f, highY),
            size = Size(size.width, (lowY - highY).coerceAtLeast(1f)),
        )

        highY != null -> drawRect(
            color = bandColor,
            topLeft = Offset(0f, highY),
            size = Size(size.width, (size.height - highY).coerceAtLeast(1f)),
        )

        lowY != null -> drawRect(
            color = bandColor,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, lowY.coerceAtLeast(1f)),
        )
    }

    if (drawGuides && guideColor != Color.Transparent) {
        val dash = PathEffect.dashPathEffect(
            intervals = floatArrayOf(6.dp.toPx(), 6.dp.toPx()),
        )
        highY?.let { y ->
            drawLine(
                color = guideColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash,
            )
        }
        lowY?.let { y ->
            drawLine(
                color = guideColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash,
            )
        }
    }

    if (points.size > 1) {
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = xFor(index)
            val y = yFor(point.result.value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }

    points.forEachIndexed { index, point ->
        val center = Offset(xFor(index), yFor(point.result.value))
        val isLatest = index == points.lastIndex
        val radius = if (highlightLatest && isLatest) dotRadius * 1.4f else dotRadius
        if (highlightLatest && isLatest) {
            drawCircle(
                color = surfaceColor,
                radius = radius + 2.5.dp.toPx(),
                center = center,
            )
        }
        drawCircle(
            color = pointColors.getOrElse(index) { lineColor },
            radius = radius,
            center = center,
        )
    }
}
