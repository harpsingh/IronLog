package com.ironlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ironlog.domain.stats.ChartPoint
import java.util.Locale

private fun axisLabel(v: Float): String =
    if (v >= 10_000) String.format(Locale.US, "%.1fk", v / 1000) else String.format(Locale.US, "%.0f", v)

/** Line chart drawn with Compose Canvas. */
@Composable
fun LineChart(points: List<ChartPoint>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (points.size < 2) {
        Text(
            if (points.isEmpty()) "No data yet" else "Log one more session to see a trend",
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(vertical = 24.dp),
        )
        return
    }
    val grid = MaterialTheme.colorScheme.outline
    val maxV = points.maxOf { it.value }.coerceAtLeast(1f)
    val minV = points.minOf { it.value }.let { if (it == maxV) 0f else it * 0.9f }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val w = size.width
            val h = size.height
            for (i in 0..3) {
                val y = h * i / 3
                drawLine(grid.copy(alpha = 0.5f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            fun x(i: Int) = w * i / (points.size - 1)
            fun y(v: Float) = h - (v - minV) / (maxV - minV) * (h - 12f) - 6f
            val line = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.value)) else lineTo(x(i), y(p.value)) }
            }
            val fill = Path().apply {
                addPath(line); lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
            points.forEachIndexed { i, p -> drawCircle(color, 7f, Offset(x(i), y(p.value))) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("peak ${axisLabel(maxV)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(points.last().label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Bar chart drawn with Compose Canvas; every bar is labelled underneath. */
@Composable
fun BarChart(points: List<ChartPoint>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (points.isEmpty()) return
    val maxV = points.maxOf { it.value }.coerceAtLeast(1f)
    val muted = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / points.size
            val barW = slot * 0.6f
            points.forEachIndexed { i, p ->
                val bh = (p.value / maxV) * size.height
                drawRoundRect(muted, Offset(i * slot + (slot - barW) / 2, 0f), Size(barW, size.height), CornerRadius(8f))
                if (bh > 0) drawRoundRect(color, Offset(i * slot + (slot - barW) / 2, size.height - bh), Size(barW, bh), CornerRadius(8f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            points.forEach { p ->
                Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(axisLabel(p.value), style = MaterialTheme.typography.labelMedium)
                    Text(p.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
