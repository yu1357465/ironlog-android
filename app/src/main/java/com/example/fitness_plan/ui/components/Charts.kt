package com.example.fitness_plan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import com.example.fitness_plan.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class ChartPoint(val label: String, val value: Double, val valueLabel: String)

/**
 * 重量趋势折线图：点上标数值，底部标日期。
 * 路径与文字排版放在 drawWithCache 里，只在尺寸或数据变化时计算一次，展开动画期间不重复排版。
 */
@Composable
fun TrendLineChart(points: List<ChartPoint>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val measurer = rememberTextMeasurer()
    val ringColor = LocalExtraColors.current.card
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val valueStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    val axisStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Spacer(
        modifier.fillMaxWidth().height(140.dp).drawWithCache {
            if (points.isEmpty()) return@drawWithCache onDrawBehind {}
            val top = 20.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            val left = 12.dp.toPx()
            val right = size.width - 12.dp.toPx()
            val min = points.minOf { it.value }
            val max = points.maxOf { it.value }
            val flat = max - min < 1e-6
            fun x(i: Int) = if (points.size == 1) (left + right) / 2 else left + (right - left) * i / (points.size - 1)
            fun y(v: Double) = if (flat) (top + bottom) / 2 else bottom - ((v - min) / (max - min) * (bottom - top)).toFloat()

            val dots = points.indices.map { Offset(x(it), y(points[it].value)) }
            val line = Path().apply { dots.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            val fill = Path().apply {
                moveTo(dots.first().x, bottom)
                dots.forEach { lineTo(it.x, it.y) }
                lineTo(dots.last().x, bottom)
                close()
            }
            val fillBrush = Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent), top, bottom)
            val stroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val valueTexts = points.map { measurer.measure(it.valueLabel, valueStyle) }
            // 点多时隔一个标日期，避免重叠
            val dateTexts = points.mapIndexed { i, p ->
                if (points.size <= 6 || i % 2 == points.lastIndex % 2) measurer.measure(p.label, axisStyle) else null
            }
            val ring = 4.5.dp.toPx()
            val dot = 3.dp.toPx()
            val gap = 4.dp.toPx()
            val axisGap = 3.dp.toPx()

            onDrawBehind {
                drawLine(gridColor, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
                drawPath(fill, fillBrush)
                drawPath(line, color, style = stroke)
                dots.forEachIndexed { i, o ->
                    drawCircle(ringColor, ring, o)
                    drawCircle(color, dot, o)
                    val v = valueTexts[i]
                    drawText(v, topLeft = Offset((o.x - v.size.width / 2).coerceIn(0f, size.width - v.size.width), o.y - v.size.height - gap))
                    dateTexts[i]?.let { d ->
                        drawText(d, topLeft = Offset((o.x - d.size.width / 2).coerceIn(0f, size.width - d.size.width), bottom + axisGap))
                    }
                }
            }
        }
    )
}

/** 六边形能力雷达图，数值 0–100。切换数据时从旧形状平滑过渡到新形状。 */
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

    // 每个顶点一个动画值；首次显示从 0 展开，之后在新旧数值间插值
    val animated = remember(labels.size) { List(labels.size) { Animatable(0f) } }
    val animatedColor = remember { androidx.compose.animation.Animatable(color) }
    LaunchedEffect(values) {
        values.forEachIndexed { i, v ->
            launch { animated[i].animateTo(v.coerceIn(0f, 100f) / 100f, tween(450, easing = FastOutSlowInEasing)) }
        }
    }
    LaunchedEffect(color) { animatedColor.animateTo(color, tween(300)) }

    Spacer(
        modifier.fillMaxWidth().height(280.dp).drawWithCache {
            val n = labels.size
            val center = Offset(size.width / 2, size.height / 2)
            val radius = minOf(size.width, size.height) / 2 - 36.dp.toPx()
            fun vertex(i: Int, r: Float): Offset {
                val angle = -PI / 2 + 2 * PI * i / n
                return Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat())
            }
            val webs = (1..4).map { level ->
                Path().apply {
                    for (i in 0 until n) { val p = vertex(i, radius * level / 4); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                    close()
                }
            }
            val spokes = (0 until n).map { vertex(it, radius) }
            val webStroke = Stroke(1.dp.toPx())
            val shapeStroke = Stroke(2.dp.toPx(), join = StrokeJoin.Round)
            val dotRadius = 3.dp.toPx()
            val labelLayouts = labels.mapIndexed { i, label ->
                val l = measurer.measure(label, labelStyle)
                val s = measurer.measure(values.getOrElse(i) { 0f }.toInt().toString(), scoreStyle)
                val anchor = vertex(i, radius + 20.dp.toPx())
                val h = l.size.height + s.size.height
                Triple(l to Offset(anchor.x - l.size.width / 2, anchor.y - h / 2), s, Offset(anchor.x - s.size.width / 2, anchor.y - h / 2 + l.size.height))
            }
            val shape = Path()

            onDrawBehind {
                if (n < 3) return@onDrawBehind
                webs.forEach { drawPath(it, webColor, style = webStroke) }
                spokes.forEach { drawLine(webColor, center, it, 1.dp.toPx()) }

                // 只有多边形随动画变化，每帧仅重建这一条路径
                val c = animatedColor.value
                shape.reset()
                for (i in 0 until n) {
                    val p = vertex(i, radius * animated[i].value)
                    if (i == 0) shape.moveTo(p.x, p.y) else shape.lineTo(p.x, p.y)
                }
                shape.close()
                drawPath(shape, c.copy(alpha = 0.25f))
                drawPath(shape, c, style = shapeStroke)
                for (i in 0 until n) drawCircle(c, dotRadius, vertex(i, radius * animated[i].value))

                labelLayouts.forEach { (label, score, scorePos) ->
                    drawText(label.first, topLeft = label.second)
                    drawText(score, topLeft = scorePos)
                }
            }
        }
    )
}
