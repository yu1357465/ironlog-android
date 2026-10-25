package com.example.fitness_plan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class ChartPoint(val label: String, val value: Double, val valueLabel: String)

/** 重量趋势折线图：点上标数值，底部标日期。 */
@Composable
fun TrendLineChart(points: List<ChartPoint>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val ringColor = com.example.fitness_plan.ui.theme.LocalExtraColors.current.card
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val valueStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    val axisStyle = TextStyle(fontSize = 10.sp, color = labelColor)

    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        if (points.isEmpty()) return@Canvas
        val top = 20.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val left = 12.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val min = points.minOf { it.value }
        val max = points.maxOf { it.value }
        val span = (max - min).takeIf { it > 1e-6 } ?: 1.0
        fun x(i: Int) = if (points.size == 1) (left + right) / 2 else left + (right - left) * i / (points.size - 1)
        fun y(v: Double) = if (max - min < 1e-6) (top + bottom) / 2 else bottom - ((v - min) / span * (bottom - top)).toFloat()

        drawLine(gridColor, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())

        val line = Path()
        val fill = Path()
        points.forEachIndexed { i, p ->
            val o = Offset(x(i), y(p.value))
            if (i == 0) { line.moveTo(o.x, o.y); fill.moveTo(o.x, bottom); fill.lineTo(o.x, o.y) }
            else { line.lineTo(o.x, o.y); fill.lineTo(o.x, o.y) }
        }
        fill.lineTo(x(points.lastIndex), bottom)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent), top, bottom))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        points.forEachIndexed { i, p ->
            val o = Offset(x(i), y(p.value))
            drawCircle(ringColor, 4.5.dp.toPx(), o)
            drawCircle(color, 3.dp.toPx(), o)
            val v = measurer.measure(p.valueLabel, valueStyle)
            drawText(v, topLeft = Offset((o.x - v.size.width / 2).coerceIn(0f, size.width - v.size.width), o.y - v.size.height - 4.dp.toPx()))
            // 点多时只隔一个标日期，避免重叠
            if (points.size <= 6 || i % 2 == (points.lastIndex % 2)) {
                val d = measurer.measure(p.label, axisStyle)
                drawText(d, topLeft = Offset((o.x - d.size.width / 2).coerceIn(0f, size.width - d.size.width), bottom + 3.dp.toPx()))
            }
        }
    }
}

/** 六边形能力雷达图，数值 0–100。 */
@Composable
fun RadarChart(
    labels: List<String>,
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val webColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    val scoreStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(500))
    }

    Canvas(modifier.fillMaxWidth().height(280.dp)) {
        val n = labels.size
        if (n < 3) return@Canvas
        val center = Offset(size.width / 2, size.height / 2)
        val radius = minOf(size.width, size.height) / 2 - 36.dp.toPx()
        fun vertex(i: Int, r: Float): Offset {
            val angle = -PI / 2 + 2 * PI * i / n
            return Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat())
        }

        for (level in 1..4) {
            val r = radius * level / 4
            val web = Path()
            for (i in 0 until n) { val p = vertex(i, r); if (i == 0) web.moveTo(p.x, p.y) else web.lineTo(p.x, p.y) }
            web.close()
            drawPath(web, webColor, style = Stroke(1.dp.toPx()))
        }
        for (i in 0 until n) drawLine(webColor, center, vertex(i, radius), 1.dp.toPx())

        val shape = Path()
        values.forEachIndexed { i, v ->
            val p = vertex(i, radius * (v.coerceIn(0f, 100f) / 100f) * progress.value)
            if (i == 0) shape.moveTo(p.x, p.y) else shape.lineTo(p.x, p.y)
        }
        shape.close()
        drawPath(shape, color.copy(alpha = 0.25f))
        drawPath(shape, color, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        values.forEachIndexed { i, v ->
            drawCircle(color, 3.dp.toPx(), vertex(i, radius * (v.coerceIn(0f, 100f) / 100f) * progress.value))
        }

        labels.forEachIndexed { i, label ->
            val anchor = vertex(i, radius + 20.dp.toPx())
            val l = measurer.measure(label, labelStyle)
            val s = measurer.measure(values[i].toInt().toString(), scoreStyle)
            val h = l.size.height + s.size.height
            drawText(l, topLeft = Offset(anchor.x - l.size.width / 2, anchor.y - h / 2))
            drawText(s, topLeft = Offset(anchor.x - s.size.width / 2, anchor.y - h / 2 + l.size.height))
        }
    }
}
