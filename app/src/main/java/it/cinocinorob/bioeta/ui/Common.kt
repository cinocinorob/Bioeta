package it.cinocinorob.bioeta.ui

import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BioTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF7FE0C3))
        else -> lightColorScheme(primary = Color(0xFF0F6B5E))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun Double.f(decimals: Int): String = String.format(Locale.ITALY, "%.${decimals}f", this)
fun Double.fs(decimals: Int): String = String.format(Locale.ITALY, "%+.${decimals}f", this)

private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
fun LocalDate.itDate(): String = format(DATE_FMT)

/** Accetta "12,5" / "12.5"; null se non numerico. */
fun parseNum(s: String): Double? = s.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

data class ChartSeries(
    val points: List<Pair<Double, Double>>,
    val color: Color,
    val dots: Boolean = true,
    val dashed: Boolean = false,
    val width: Float = 2.5f,
)

/**
 * Grafico a linee minimale su Canvas. x numerico (es. giorno epoch), etichette fornite da xLabel.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    yDecimals: Int = 1,
    xLabel: (Double) -> String = { it.f(0) },
) {
    val all = series.flatMap { it.points }
    if (all.isEmpty()) return
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        val left = 44.dp.toPx()
        val bottom = 22.dp.toPx()
        val top = 8.dp.toPx()
        val right = 8.dp.toPx()
        var minX = all.minOf { it.first }
        var maxX = all.maxOf { it.first }
        var minY = all.minOf { it.second }
        var maxY = all.maxOf { it.second }
        if (maxX == minX) { minX -= 1; maxX += 1 }
        if (maxY == minY) { minY -= 1; maxY += 1 }
        val padY = (maxY - minY) * 0.08
        minY -= padY; maxY += padY
        val w = size.width - left - right
        val h = size.height - top - bottom
        fun px(x: Double) = (left + (x - minX) / (maxX - minX) * w).toFloat()
        fun py(y: Double) = (top + (1 - (y - minY) / (maxY - minY)) * h).toFloat()

        val paint = Paint().apply {
            color = labelColor
            textSize = 11.dp.toPx()
            isAntiAlias = true
        }
        for (i in 0..3) {
            val yv = minY + (maxY - minY) * i / 3.0
            val yy = py(yv)
            drawLine(gridColor, Offset(left, yy), Offset(size.width - right, yy), strokeWidth = 1f)
            paint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(yv.f(yDecimals), left - 6.dp.toPx(), yy + 4.dp.toPx(), paint)
        }
        paint.textAlign = Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(xLabel(minX), left, size.height - 4.dp.toPx(), paint)
        paint.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(xLabel(maxX), size.width - right, size.height - 4.dp.toPx(), paint)

        for (s in series) {
            val pts = s.points.sortedBy { it.first }
            if (pts.size >= 2) {
                val path = Path()
                pts.forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(px(p.first), py(p.second)) else path.lineTo(px(p.first), py(p.second))
                }
                drawPath(
                    path, s.color,
                    style = Stroke(
                        width = s.width.dp.toPx(),
                        pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 10f)) else null,
                    ),
                )
            }
            if (s.dots) pts.forEach { drawCircle(s.color, radius = 3.5.dp.toPx(), center = Offset(px(it.first), py(it.second))) }
        }
    }
}

fun epochLabel(x: Double): String = LocalDate.ofEpochDay(x.toLong()).format(DateTimeFormatter.ofPattern("dd/MM/yy"))
