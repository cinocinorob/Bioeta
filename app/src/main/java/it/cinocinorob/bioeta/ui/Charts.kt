package it.cinocinorob.bioeta.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max

enum class LayerKind { LINE, AREA, DOTS, DASHED }

data class ChartLayer(
    val points: List<Pair<Double, Double>>,
    val color: Color,
    val kind: LayerKind = LayerKind.AREA,
    val emphasizeLast: Boolean = false,
)

/**
 * Grafico in stile Salute: griglia tratteggiata leggera, etichette Y a destra,
 * riempimento sfumato sotto la linea, banda di riferimento opzionale.
 */
@Composable
fun HealthChart(
    layers: List<ChartLayer>,
    modifier: Modifier = Modifier,
    height: Int = 170,
    yDecimals: Int = 0,
    band: Pair<Double, Double>? = null,
    bandColor: Color = Tint.blue,
    xLabel: (Double) -> String = ::epochLabel,
) {
    val all = layers.flatMap { it.points }
    if (all.isEmpty()) return
    val p = LocalPalette.current
    val labelColor = p.text2.toArgb()
    val grid = p.sep
    val cardColor = p.card
    val anim = remember { Animatable(0f) }
    LaunchedEffect(layers.sumOf { it.points.size }) { anim.snapTo(0f); anim.animateTo(1f, tween(700)) }
    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        val left = 4.dp.toPx()
        val right = 40.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = 22.dp.toPx()
        var minX = all.minOf { it.first }
        var maxX = all.maxOf { it.first }
        var minY = all.minOf { it.second }
        var maxY = all.maxOf { it.second }
        if (band != null) { minY = minOf(minY, band.first); maxY = maxOf(maxY, band.second) }
        if (maxX == minX) { minX -= 1; maxX += 1 }
        if (maxY == minY) { minY -= 1; maxY += 1 }
        val pad = (maxY - minY) * 0.12
        minY -= pad; maxY += pad
        val w = size.width - left - right
        val h = size.height - top - bottom
        fun px(x: Double) = (left + (x - minX) / (maxX - minX) * w).toFloat()
        fun py(y: Double) = (top + (1 - (y - minY) / (maxY - minY)) * h).toFloat()

        val paint = Paint().apply { color = labelColor; textSize = 11.dp.toPx(); isAntiAlias = true }
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        for (i in 0..3) {
            val yv = minY + (maxY - minY) * (i + 0.5) / 4.0
            val yy = py(yv)
            drawLine(grid, Offset(left, yy), Offset(left + w, yy), strokeWidth = 1f, pathEffect = dash)
            paint.textAlign = Paint.Align.LEFT
            drawContext.canvas.nativeCanvas.drawText(yv.f(yDecimals), left + w + 6.dp.toPx(), yy + 4.dp.toPx(), paint)
        }
        drawLine(grid, Offset(left, top + h), Offset(left + w, top + h), strokeWidth = 1f)
        paint.textAlign = Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(xLabel(minX), left, size.height - 4.dp.toPx(), paint)
        paint.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(xLabel(maxX), left + w, size.height - 4.dp.toPx(), paint)

        if (band != null) {
            val y1 = py(band.second); val y2 = py(band.first)
            drawRoundRect(bandColor.copy(alpha = 0.10f), Offset(left, y1), Size(w, y2 - y1), CornerRadius(4.dp.toPx()))
        }

        clipRect(right = left + w * anim.value + 8.dp.toPx()) {
        for (layer in layers) {
            val pts = layer.points.sortedBy { it.first }
            if (pts.isEmpty()) continue
            when (layer.kind) {
                LayerKind.DOTS -> pts.forEach {
                    drawCircle(layer.color.copy(alpha = 0.35f), 2.5.dp.toPx(), Offset(px(it.first), py(it.second)))
                }
                else -> {
                    if (pts.size >= 2) {
                        val line = Path()
                        // Curva morbida (spline con tangenti orizzontali tra i punti: nessuna oscillazione oltre i dati)
                        pts.forEachIndexed { i, pt ->
                            val x = px(pt.first); val y = py(pt.second)
                            if (i == 0) line.moveTo(x, y) else {
                                val px0 = px(pts[i - 1].first); val py0 = py(pts[i - 1].second)
                                val mx = (px0 + x) / 2
                                line.cubicTo(mx, py0, mx, y, x, y)
                            }
                        }
                        if (layer.kind == LayerKind.AREA) {
                            val fill = Path().apply {
                                addPath(line)
                                lineTo(px(pts.last().first), top + h)
                                lineTo(px(pts.first().first), top + h)
                                close()
                            }
                            drawPath(fill, Brush.verticalGradient(
                                listOf(layer.color.copy(alpha = 0.30f), layer.color.copy(alpha = 0.0f)),
                                startY = top, endY = top + h,
                            ))
                        }
                        drawPath(
                            line, layer.color,
                            style = Stroke(
                                width = (if (layer.kind == LayerKind.DASHED) 1.5f else 2.5f).dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = if (layer.kind == LayerKind.DASHED) dash else null,
                            ),
                        )
                    }
                    if (layer.kind == LayerKind.AREA && pts.size <= 24) {
                        pts.forEach { drawCircle(layer.color, 3.dp.toPx(), Offset(px(it.first), py(it.second))) }
                    }
                }
            }
            if (layer.emphasizeLast && anim.value > 0.98f) {
                val lp = pts.last()
                val c = Offset(px(lp.first), py(lp.second))
                drawCircle(layer.color.copy(alpha = 0.25f), 10.dp.toPx(), c)
                drawCircle(layer.color, 6.dp.toPx(), c)
                drawCircle(cardColor, 3.dp.toPx(), c)
            }
        }
        }
    }
}

/**
 * Anello in stile "Attività": l'arco rappresenta l'età biologica rispetto a quella anagrafica
 * (pieno a metà = uguali; verso il verde = più giovane).
 */
@Composable
fun AgeRing(bioAge: Double, chronoAge: Double, modifier: Modifier = Modifier, span: Double = 15.0, ringColor: Color = Color.White) {
    val target = (0.5 + (chronoAge - bioAge) / span / 2).coerceIn(0.03, 1.0).toFloat()
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target, tween(1100)) }
    Canvas(modifier.size(132.dp)) {
        val stroke = 14.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(ringColor.copy(alpha = 0.22f), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(
            Brush.sweepGradient(listOf(ringColor.copy(alpha = 0.75f), ringColor, ringColor.copy(alpha = 0.75f))),
            -90f, 360f * anim.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
        )
        // tacca a metà = età anagrafica
        drawLine(ringColor.copy(alpha = 0.9f), Offset(size.width / 2, size.height - stroke * 1.6f), Offset(size.width / 2, size.height - 2f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Mini grafico per le righe di elenco. */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    Canvas(modifier.size(width = 56.dp, height = 24.dp)) {
        val mn = values.min(); val mx = values.max()
        val span = if (mx == mn) 1.0 else mx - mn
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * size.width / (values.size - 1)
            val y = (size.height - 3.dp.toPx()) * (1 - (v - mn) / span).toFloat() + 1.5.dp.toPx()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        val lx = size.width
        val ly = (size.height - 3.dp.toPx()) * (1 - (values.last() - mn) / span).toFloat() + 1.5.dp.toPx()
        drawCircle(color, 2.5.dp.toPx(), Offset(lx, ly))
    }
}

/**
 * Barra orizzontale "più giovane ↔ più vecchio" centrata sull'età anagrafica,
 * con indicatore della PhenoAge.
 */
@Composable
fun AgeRangeBar(bioAge: Double, chronoAge: Double, span: Double = 15.0) {
    val p = LocalPalette.current
    val cardColor = p.card
    val textColor = p.text
    Column {
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            val barH = 10.dp.toPx()
            val y = size.height / 2
            val r = 9.dp.toPx()
            val x0 = r; val x1 = size.width - r
            drawRoundRect(
                Brush.horizontalGradient(listOf(Tint.good, Color(0xFF9BD34B), Color(0xFFFFCC00), Tint.warn, Tint.bad), startX = x0, endX = x1),
                Offset(x0, y - barH / 2), Size(x1 - x0, barH), CornerRadius(barH / 2),
            )
            val mid = (x0 + x1) / 2
            drawLine(textColor.copy(alpha = 0.55f), Offset(mid, y - barH), Offset(mid, y + barH), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            val frac = ((bioAge - chronoAge) / span).coerceIn(-1.0, 1.0)
            val mx = (mid + frac * (x1 - x0) / 2).toFloat()
            drawCircle(Color.Black.copy(alpha = 0.18f), r + 1.dp.toPx(), Offset(mx, y + 1.dp.toPx()))
            drawCircle(cardColor, r, Offset(mx, y))
            drawCircle(textColor, r - 3.5.dp.toPx(), Offset(mx, y))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Caption("−${span.f(0)} anni")
            Caption("età anagrafica")
            Caption("+${span.f(0)} anni")
        }
    }
}

/** Barre divergenti per i contributi (anni) dei singoli biomarcatori. */
@Composable
fun ContributionBars(items: List<Pair<String, Double>>) {
    val p = LocalPalette.current
    val maxAbs = max(items.maxOfOrNull { abs(it.second) } ?: 0.0, 0.25)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 13.sp, color = p.text, modifier = Modifier.width(118.dp), maxLines = 1)
                val color = when {
                    abs(v) < 0.05 -> p.text2
                    v > 0 -> Tint.bad
                    else -> Tint.good
                }
                val sep = p.sep
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    val mid = size.width / 2
                    drawLine(sep, Offset(mid, 0f), Offset(mid, size.height), strokeWidth = 1.dp.toPx())
                    val len = (abs(v) / maxAbs * (size.width / 2 - 2.dp.toPx())).toFloat().coerceAtLeast(2.dp.toPx())
                    val x = if (v >= 0) mid else mid - len
                    drawRoundRect(color, Offset(x, 2.dp.toPx()), Size(len, size.height - 4.dp.toPx()), CornerRadius(3.dp.toPx()))
                }
                Text(v.fs(2), fontSize = 13.sp, color = color, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(52.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
}
