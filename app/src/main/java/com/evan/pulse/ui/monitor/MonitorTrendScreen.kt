package com.evan.pulse.ui.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.addressText
import com.evan.pulse.data.monitor.TrendStats
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppText
import java.util.Locale
import kotlin.math.roundToInt

/** 拨测趋势二级页：单目标延迟曲线、成功率与延迟统计 */
@Composable
fun MonitorTrendScreen(
    modifier: Modifier = Modifier,
    targetId: Long,
    viewModel: MonitorViewModel = viewModel()
) {
    val stats by viewModel.trendStats.collectAsState()
    val targetsById by viewModel.targetsById.collectAsState()

    val target = targetsById[targetId]
    val targetStats = stats

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .subPageOverScroll()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LocalTopBarInset.current)
                .padding(bottom = 12.dp)
        ) {
            target?.let {
                AppText(
                    text = it.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp
                )
                AppText(
                    text = it.addressText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary
                )
            }
        }
        if (targetStats == null || targetStats.sampleCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AppText(
                    text = stringResource(R.string.monitor_trend_empty),
                    fontSize = 13.sp,
                    color = AppTheme.colors.onSurfaceSummary
                )
            }
        } else {
            TrendStatsRow(targetStats)
            Spacer(modifier = Modifier.height(12.dp))
            AppCard(modifier = Modifier.fillMaxWidth()) {
                TrendLineChart(
                    stats = targetStats,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(8.dp)
                )
            }
        }
    }
}

/** 成功率与延迟统计卡片行 */
@Composable
private fun TrendStatsRow(stats: TrendStats) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatCell(
                label = stringResource(R.string.monitor_trend_success_rate),
                value = String.format(Locale.getDefault(), "%.1f%%", stats.successRate * 100),
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = stringResource(R.string.monitor_trend_avg),
                value = String.format(Locale.getDefault(), "%d ms", stats.avgLatencyMs.toLong()),
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = stringResource(R.string.monitor_trend_peak),
                value = String.format(Locale.getDefault(), "%d ms", stats.peakLatencyMs),
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = stringResource(R.string.monitor_trend_samples),
                value = stats.sampleCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AppText(
            text = value,
            fontSize = 16.sp
        )
        AppText(
            text = label,
            fontSize = 11.sp,
            color = AppTheme.colors.onSurfaceSummary
        )
    }
}

/**
 * 延迟曲线：平滑曲线 + 渐变面积，失败样本以红点标出。
 * 在图上横向拖动或点击可定位最近的样本，显示时间、延迟与通断状态。
 */
@Composable
private fun TrendLineChart(stats: TrendStats, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val primary = AppTheme.colors.primary
    val error = AppTheme.colors.error
    val outline = AppTheme.colors.outline
    val summary = AppTheme.colors.onSurfaceSummary
    val tooltipBackground = AppTheme.colors.surfaceContainerHigh
    val tooltipForeground = AppTheme.colors.onSurface
    val okLabel = stringResource(R.string.monitor_state_ok)
    val downLabel = stringResource(R.string.monitor_state_down)
    val timeFormat = remember { java.text.SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val axisFormat = remember { java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val points = stats.points
    val peakMs = stats.peakLatencyMs.coerceAtLeast(1L)
    var selected by remember(points) { mutableStateOf<Int?>(null) }

    Canvas(
        modifier = modifier
            .pointerInput(points.size) {
                fun indexAt(x: Float): Int {
                    if (points.size <= 1) return 0
                    val left = 34.dp.toPx()
                    val right = size.width - 8.dp.toPx()
                    val step = (right - left) / (points.size - 1)
                    if (step <= 0f) return 0
                    return ((x - left) / step).roundToInt().coerceIn(0, points.size - 1)
                }
                detectDragGestures(
                    onDragStart = { offset -> selected = indexAt(offset.x) },
                    onDrag = { change, _ ->
                        change.consume()
                        selected = indexAt(change.position.x)
                    }
                )
            }
            .pointerInput(points.size) {
                detectTapGestures { offset ->
                    val left = 34.dp.toPx()
                    val right = size.width - 8.dp.toPx()
                    val step = if (points.size <= 1) 0f else (right - left) / (points.size - 1)
                    selected = if (step <= 0f) 0
                    else ((offset.x - left) / step).roundToInt().coerceIn(0, points.size - 1)
                }
            }
    ) {
        val stroke = 2.dp.toPx()
        val dotRadius = 3.dp.toPx()
        val leftPad = 34.dp.toPx()
        val rightPad = 8.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val plotWidth = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)
        val baselineY = topPad + plotHeight
        val yRange = peakMs.toFloat() * 1.15f

        fun xAt(index: Int): Float =
            if (points.size <= 1) leftPad + plotWidth / 2f
            else leftPad + plotWidth * index / (points.size - 1)

        fun yAt(durationMs: Long): Float =
            baselineY - (durationMs.toFloat() / yRange).coerceIn(0f, 1f) * plotHeight

        // 横向网格与纵轴刻度
        val gridDash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        for (i in 0..3) {
            val y = topPad + plotHeight * i / 3f
            drawLine(
                color = outline.copy(alpha = 0.16f),
                start = Offset(leftPad, y),
                end = Offset(size.width - rightPad, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = gridDash
            )
        }
        drawText(
            textMeasurer = textMeasurer,
            text = "${peakMs}ms",
            topLeft = Offset(0f, topPad - 4.dp.toPx()),
            style = TextStyle(color = summary, fontSize = 9.sp)
        )
        drawText(
            textMeasurer = textMeasurer,
            text = "0",
            topLeft = Offset(0f, baselineY - 12.dp.toPx()),
            style = TextStyle(color = summary, fontSize = 9.sp)
        )

        val coords = points.mapIndexed { index, point ->
            Offset(xAt(index), yAt(point.durationMs))
        }

        if (coords.size == 1) {
            drawCircle(color = primary, radius = dotRadius, center = coords.first())
        } else if (coords.size > 1) {
            val linePath = smoothPath(coords)
            val areaPath = smoothPath(coords).apply {
                lineTo(coords.last().x, baselineY)
                lineTo(coords.first().x, baselineY)
                close()
            }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(primary.copy(alpha = 0.26f), primary.copy(alpha = 0f)),
                    startY = topPad,
                    endY = baselineY
                )
            )
            drawPath(
                path = linePath,
                color = primary,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // 样本点：失败用红色；成功点在样本较少时也标出，避免曲线过密
            points.forEachIndexed { index, point ->
                if (!point.ok || points.size <= 40) {
                    drawCircle(
                        color = if (point.ok) primary else error,
                        radius = dotRadius,
                        center = coords[index]
                    )
                }
            }
        }

        // 底部时间标签：首与末
        val firstLabel = axisFormat.format(java.util.Date(points.first().checkedAt))
        val lastLabel = axisFormat.format(java.util.Date(points.last().checkedAt))
        drawText(
            textMeasurer = textMeasurer,
            text = firstLabel,
            topLeft = Offset(leftPad, baselineY + 5.dp.toPx()),
            style = TextStyle(color = summary, fontSize = 9.sp)
        )
        val lastLayout = textMeasurer.measure(
            text = AnnotatedString(lastLabel),
            style = TextStyle(color = summary, fontSize = 9.sp)
        )
        drawText(
            textLayoutResult = lastLayout,
            topLeft = Offset(size.width - rightPad - lastLayout.size.width, baselineY + 5.dp.toPx())
        )

        // 拖动定位：竖线 + 高亮点 + 数据气泡
        selected?.let { index ->
            val point = points.getOrNull(index) ?: return@let
            val x = xAt(index)
            val y = yAt(point.durationMs)
            drawLine(
                color = primary.copy(alpha = 0.55f),
                start = Offset(x, topPad),
                end = Offset(x, baselineY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
            )
            drawCircle(color = primary.copy(alpha = 0.18f), radius = 7.dp.toPx(), center = Offset(x, y))
            drawCircle(color = primary, radius = 4.dp.toPx(), center = Offset(x, y))
            drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(x, y))

            val status = if (point.ok) okLabel else downLabel
            val label = "${timeFormat.format(java.util.Date(point.checkedAt))}  " +
                "${point.durationMs}ms  $status"
            val layout = textMeasurer.measure(
                text = AnnotatedString(label),
                style = TextStyle(color = tooltipForeground, fontSize = 10.sp)
            )
            val boxWidth = layout.size.width + 16.dp.toPx()
            val boxHeight = layout.size.height + 10.dp.toPx()
            val boxX = (x - boxWidth / 2f).coerceIn(0f, (size.width - boxWidth).coerceAtLeast(0f))
            val boxY = (y - boxHeight - 10.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(
                color = tooltipBackground,
                topLeft = Offset(boxX, boxY),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(boxX + 8.dp.toPx(), boxY + 5.dp.toPx())
            )
        }
    }
}

/** Catmull-Rom 转三次贝塞尔，得到经过所有样本点的平滑曲线 */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]
        val cp1 = Offset(
            p1.x + (p2.x - p0.x) / 6f,
            p1.y + (p2.y - p0.y) / 6f
        )
        val cp2 = Offset(
            p2.x - (p3.x - p1.x) / 6f,
            p2.y - (p3.y - p1.y) / 6f
        )
        path.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
    }
    return path
}