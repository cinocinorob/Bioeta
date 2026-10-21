package it.cinocinorob.bioeta.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.Instant

// =============================================================================================
// Stato del dato: misurato, stimato o non disponibile (mai uno zero al posto di un dato mancante)
// =============================================================================================

enum class DataStatus(val label: String) {
    MEASURED("Misurato"),
    ESTIMATED("Stimato"),
    PARTIAL("Parziale"),
    UNAVAILABLE("Non disponibile"),
}

@Composable
fun StatusBadge(status: DataStatus) {
    val p = LocalPalette.current
    val c = when (status) {
        DataStatus.MEASURED -> Tint.good
        DataStatus.ESTIMATED -> Tint.hrv
        DataStatus.PARTIAL -> Tint.warn
        DataStatus.UNAVAILABLE -> p.text3
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(4.dp))
        Text(status.label, style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1)
    }
}

/** Variazione con freccia; [goodWhenUp] = null se la direzione non ha un significato univoco. */
data class Trend(val text: String, val up: Boolean, val goodWhenUp: Boolean?)

@Composable
fun TrendChip(t: Trend) {
    val p = LocalPalette.current
    val c = when (t.goodWhenUp) { null -> p.text2; else -> if (t.up == t.goodWhenUp) Tint.good else Tint.warn }
    Text((if (t.up) "▲ " else "▼ ") + t.text, style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1)
}

/**
 * Tessera metrica riutilizzabile: valore + unità, stato del dato, andamento, periodo di riferimento.
 * Se [value] è null mostra "—" e lo stato Non disponibile con il motivo in [period].
 */
@Composable
fun MetricTile(
    label: String,
    value: String?,
    unit: String?,
    tint: Color,
    icon: ImageVector,
    status: DataStatus,
    period: String,
    modifier: Modifier = Modifier,
    trend: Trend? = null,
    spark: List<Double> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    val desc = "$label: ${value ?: "non disponibile"} ${unit ?: ""}. ${status.label}. $period"
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(p.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .padding(14.dp)
            .heightIn(min = 118.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value ?: "—", color = if (value == null) p.text3 else p.text, maxLines = 1,
                style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 31.sp, letterSpacing = (-0.3).sp, fontFeatureSettings = TABULAR),
            )
            if (unit != null && value != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, color = p.text2, modifier = Modifier.padding(bottom = 4.dp), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            if (spark.size >= 3) Sparkline(spark, tint, Modifier.width(54.dp).height(24.dp))
        }
        // «Misurato» è il caso normale: il distintivo compare solo quando il dato è stimato, parziale o assente
        if (status != DataStatus.MEASURED || trend != null) Row(verticalAlignment = Alignment.CenterVertically) {
            if (status != DataStatus.MEASURED) { StatusBadge(status); Spacer(Modifier.width(6.dp)) }
            if (trend != null) TrendChip(trend)
        }
        Text(period, style = MaterialTheme.typography.bodySmall, color = p.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Intestazione di sezione con azione opzionale a destra. */
@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = p.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = LocalPalette.current.brand) }
    }
}

/** Stato vuoto: icona, titolo, spiegazione e azione. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, tint: Color = Tint.brand, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = p.text)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = p.text2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null && onAction != null) {
            androidx.compose.material3.FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

/** Avviso in linea (informazione, attenzione o errore). */
enum class NoticeKind { INFO, WARN, ERROR }

@Composable
fun InlineNotice(text: String, kind: NoticeKind = NoticeKind.INFO, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val c = when (kind) { NoticeKind.INFO -> Tint.blue; NoticeKind.WARN -> Tint.warn; NoticeKind.ERROR -> Tint.bad }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BioIcons.Info, null, tint = c, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = p.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = c) }
    }
}

/** Riga segnaposto animata durante il caricamento. */
@Composable
fun LoadingBar(modifier: Modifier = Modifier, height: Int = 14) {
    val p = LocalPalette.current
    val still = androidx.compose.ui.platform.LocalInspectionMode.current
    val a by rememberInfiniteTransition(label = "sh").animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(modifier.height(height.dp).clip(RoundedCornerShape(8.dp)).background(p.card2.copy(alpha = if (still) 0.6f else a)))
}

/** "5 min fa", "3 ore fa", "2 giorni fa". */
fun agoShort(t: Instant?): String {
    if (t == null) return "mai"
    val d = Duration.between(t, Instant.now())
    return when {
        d.toMinutes() < 1 -> "ora"
        d.toMinutes() < 60 -> "${d.toMinutes()} min fa"
        d.toHours() < 48 -> "${d.toHours()} ore fa"
        else -> "${d.toDays()} giorni fa"
    }
}

// =============================================================================================
// Logo BioEtà «Anelli»: cuori concentrici come gli anelli di accrescimento (gli anni) con il nucleo pieno
// (oggi). Stessa geometria dell'icona dell'app (res/drawable/ic_fg.xml), viewport 108, area utile 21..87.
// =============================================================================================

private const val RING_W = 56f
private const val RING_H = 52f
private const val RING_CX = 54f
private const val RING_CY = 57.5f

/** Disegna i tre anelli; [beat] 0..1 ingrandisce il nucleo (battito), [ripple] 0..1 allarga leggermente gli anelli. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRings(
    mono: Color?, beat: Float = 0f, ripple: Float = 0f, colors: Triple<Color, Color, Color>,
) {
    val k = size.width / 66f
    fun heart(s: Float): Path {
        val w = RING_W * s * k; val h = RING_H * s * k
        val cx = (RING_CX - 21f) * k; val cy = (RING_CY - 21f) * k
        return heartPath(w, h, cx - w / 2f, cy - h * 0.56f)
    }
    val sw = 3.8f * k
    drawPath(heart(1f + 0.015f * ripple), mono ?: colors.first, style = Stroke(sw, join = StrokeJoin.Round))
    drawPath(heart(0.6f + 0.02f * ripple), mono ?: colors.second, style = Stroke(sw, join = StrokeJoin.Round))
    drawPath(heart(0.24f * (1f + 0.22f * beat)), mono ?: colors.third)
}

private val RING_COLORS = Triple(Color(0xFFFF6F8A), Color(0xFF4FD1C5), Color(0xFFFFF1F3))

@Composable
fun BioLogo(modifier: Modifier = Modifier, size: Int = 40, mono: Color? = null) {
    Canvas(modifier.size(size.dp)) { drawRings(mono, colors = RING_COLORS) }
}

/**
 * Il marchio vivo: il nucleo batte alla tua frequenza a riposo (doppio colpo «lub‑dub») e gli anelli
 * rispondono appena. Unico movimento continuo dell'app; fermo in anteprima e nei test.
 */
@Composable
fun BeatingRings(bpm: Double?, modifier: Modifier = Modifier, size: Int = 72, light: Boolean = false) {
    val period = (60_000.0 / (bpm ?: 60.0).coerceIn(40.0, 120.0)).toInt()
    val inspection = androidx.compose.ui.platform.LocalInspectionMode.current
    val t = if (inspection) 0f else {
        val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "battito")
        tr.animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(period, easing = androidx.compose.animation.core.LinearEasing)),
            label = "fase",
        ).value
    }
    // lub (0–12%) e dub (18–30%) del ciclo, poi riposo
    fun bump(x: Float, a: Float, b: Float) = if (x < a || x > b) 0f else kotlin.math.sin(((x - a) / (b - a)) * Math.PI.toFloat())
    val beat = bump(t, 0f, 0.12f) + 0.6f * bump(t, 0.18f, 0.30f)
    val ripple = bump(t, 0.04f, 0.5f)
    val colors = if (light) Triple(Color(0xFFDE3F63), Color(0xFF0F9E92), Color(0xFFDE3F63)) else RING_COLORS
    Canvas(modifier.size(size.dp)) { drawRings(null, beat, ripple, colors) }
}

// =============================================================================================
// Linguaggio visivo comune: numero grande condensato + traccia sottile (progressi) o striscia del rango
// personale. Gli anelli sono riservati al marchio.
// =============================================================================================

/** Traccia di avanzamento sottile; oltre il 100% la parte eccedente resta piena. */
@Composable
fun Track(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val f = animatedValue(fraction.coerceIn(0f, 1f), 700)
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(p.field, cornerRadius = r)
        if (f > 0f) drawRoundRect(color, size = Size(maxOf(size.height, size.width * f), size.height), cornerRadius = r)
    }
}

/**
 * Dove si colloca oggi rispetto alla tua storia: striscia 0–100 con tacche a 25/50/75 e un punto al rango.
 * È la lettura principale dei punteggi, perché i valori assoluti tendono a restare alti.
 */
@Composable
fun PercentileStrip(pct: Int?, color: Color, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val y = size.height / 2; val h = 4.dp.toPx()
        drawRoundRect(p.field, Offset(0f, y - h / 2), Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2))
        listOf(0.25f, 0.5f, 0.75f).forEach { t -> drawLine(p.sep, Offset(size.width * t, y - 5.dp.toPx()), Offset(size.width * t, y + 5.dp.toPx()), 1.dp.toPx()) }
        if (pct != null) {
            val x = size.width * (pct / 100f).coerceIn(0f, 1f)
            drawCircle(p.card, 7.dp.toPx(), Offset(x, y))
            drawCircle(color, 5.dp.toPx(), Offset(x, y))
        }
    }
}

/** Riga di sintesi: etichetta con punto colorato, valore grande, obiettivo e traccia. */
@Composable
fun SummaryRow(label: String, value: String, goal: String?, fraction: Float?, color: Color, onClick: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = p.text2)
                }
                Text(value, style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, fontFeatureSettings = TABULAR), color = p.text)
            }
            if (goal != null) Text(goal, style = MaterialTheme.typography.labelMedium, color = p.text3, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (fraction != null) Track(fraction, color)
    }
}
