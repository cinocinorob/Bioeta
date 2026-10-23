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
import androidx.compose.ui.graphics.drawscope.withTransform
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
        Modifier.clip(RoundedCornerShape(50)).background(p.card.copy(alpha = 0.75f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(5.dp))
        Text(status.label, style = MaterialTheme.typography.labelSmall, color = p.text2, maxLines = 1)
    }
}

/** Variazione con freccia; [goodWhenUp] = null se la direzione non ha un significato univoco. */
data class Trend(val text: String, val up: Boolean, val goodWhenUp: Boolean?)

@Composable
fun TrendChip(t: Trend) {
    val p = LocalPalette.current
    val c = when (t.goodWhenUp) { null -> p.text2; else -> p.ink(if (t.up == t.goodWhenUp) Tint.good else Tint.warn) }
    Text((if (t.up) "↑ " else "↓ ") + t.text, style = MaterialTheme.typography.labelLarge, color = c, maxLines = 1)
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
            .clip(RoundedCornerShape(24.dp))
            .background(if (value == null) p.card else p.tone(tint))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .padding(16.dp)
            .heightIn(min = 124.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (value == null) p.card2 else tint), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (value == null) p.text3 else Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value ?: "—", color = if (value == null) p.text3 else p.text, maxLines = 1,
                style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, letterSpacing = (-0.2).sp, fontFeatureSettings = TABULAR),
            )
            if (unit != null && value != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, color = p.text2, modifier = Modifier.padding(bottom = 4.dp), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            if (spark.size >= 3) Sparkline(spark, p.ink(tint), Modifier.width(54.dp).height(24.dp))
        }
        // «Misurato» è il caso normale: il distintivo compare solo quando il dato è stimato, parziale o assente
        if (status != DataStatus.MEASURED || trend != null) Row(verticalAlignment = Alignment.CenterVertically) {
            if (status != DataStatus.MEASURED) { StatusBadge(status); Spacer(Modifier.width(6.dp)) }
            if (trend != null) TrendChip(trend)
        }
        Text(period, style = MaterialTheme.typography.bodySmall, color = p.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Intestazione di sezione con azione opzionale a destra. */
@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = p.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = LocalPalette.current.brand) }
    }
}

/** Stato vuoto: icona, titolo, spiegazione e azione. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, tint: Color = Tint.brand, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(p.tone(tint)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.tone(c, 1.2f)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BioIcons.Info, null, tint = p.ink(c), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = p.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = p.ink(c)) }
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
// Logo BioEtà: cuore pieno da cui nasce un germoglio. Stessa geometria dell'icona (res/drawable/ic_fg.xml),
// viewport 108, forma contenuta tra ~35 e ~73.
// =============================================================================================

private const val LOGO_PATH = "M58.88,43.89 L60.06,43.81 L61.46,43.55 L62.79,43.13 L64.04,42.57 L64.84,42.12 L65.96,41.34 L67.00,40.44 L67.94,39.42 L68.79,38.30 L69.54,37.08 L70.17,35.79 L70.70,34.41 L71.11,32.97 L71.40,31.47 L71.56,29.92 L71.59,28.86 L70.02,28.98 L68.49,29.23 L67.02,29.61 L65.16,30.29 L63.85,30.93 L62.25,31.95 L61.15,32.83 L60.16,33.80 L59.29,34.87 L58.78,35.62 L57.92,37.24 L57.45,38.53 L57.13,39.90 L56.97,41.32 L56.96,42.42 L56.44,42.66 L56.03,43.10 L53.84,47.55 L53.56,48.53 L52.35,47.59 L51.14,46.89 L49.69,46.29 L48.34,45.93 L46.78,45.74 L45.38,45.76 L43.83,45.99 L42.49,46.38 L41.06,47.01 L39.86,47.75 L38.65,48.74 L37.70,49.77 L36.80,51.05 L36.15,52.29 L35.68,53.56 L35.34,55.08 L35.23,56.16 L35.23,57.29 L35.34,58.37 L35.55,59.42 L35.87,60.46 L36.38,61.65 L36.91,62.59 L37.53,63.47 L38.45,64.51 L52.44,78.50 L53.05,78.94 L53.67,79.12 L54.21,79.14 L54.63,79.05 L55.13,78.83 L55.47,78.58 L69.90,64.13 L70.90,62.86 L71.70,61.45 L72.29,59.94 L72.65,58.37 L72.77,57.02 L72.74,55.89 L72.57,54.61 L72.29,53.51 L71.84,52.29 L71.22,51.09 L70.59,50.15 L69.76,49.16 L68.56,48.07 L67.22,47.17 L65.76,46.47 L64.21,46.00 L62.61,45.76 L60.99,45.75 L59.39,45.99 L57.80,46.47 L58.78,44.45ZM61.59,40.29 L61.38,40.39 L61.07,40.42 L60.78,40.34 L60.54,40.15 L60.41,39.95 L60.35,39.65 L60.38,39.42 L60.52,39.14 L67.89,31.55 L68.16,31.40 L68.39,31.36 L68.62,31.39 L68.89,31.53 L69.09,31.77 L69.19,32.06 L69.16,32.37 L69.02,32.65Z"

private val logoPath: Path by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString(LOGO_PATH).toPath().apply { fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd } }

/** Disegna la forma del logo centrata, scalata di [scale] attorno al centro. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLogoShape(color: Color, scale: Float = 1f, crop: Float = 46f) {
    // crop = lato del riquadro (unità del viewport) attorno al centro 54,54 che riempie il Canvas
    val k = size.width / crop
    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(k * scale, k * scale, Offset.Zero)
        translate(-54f, -54f)
    }) { drawPath(logoPath, color) }
}

/** Logo in versione icona: forma bianca su quadrato arrotondato verde‑acqua (o forma sola se [mono]). */
@Composable
fun BioLogo(modifier: Modifier = Modifier, size: Int = 40, mono: Color? = null) {
    Canvas(modifier.size(size.dp)) {
        if (mono != null) { drawLogoShape(mono, crop = 48f); return@Canvas }
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF12A08E), Color(0xFF00796B))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(this.size.width * 0.3f))
        drawLogoShape(Color.White, crop = 72f)
    }
}

/**
 * Il marchio vivo: il cuore batte alla tua frequenza a riposo (doppio colpo «lub‑dub»). Unico movimento
 * continuo dell'app; fermo in anteprima e nei test.
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
    fun bump(x: Float, a: Float, b: Float) = if (x < a || x > b) 0f else kotlin.math.sin(((x - a) / (b - a)) * Math.PI.toFloat())
    val beat = bump(t, 0f, 0.12f) + 0.6f * bump(t, 0.18f, 0.30f)
    val c = LocalPalette.current.brand
    Canvas(modifier.size(size.dp)) {
        drawCircle(c.copy(alpha = 0.14f + 0.06f * beat))
        drawLogoShape(c, scale = 1f + 0.05f * beat, crop = 62f)
    }
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
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
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
