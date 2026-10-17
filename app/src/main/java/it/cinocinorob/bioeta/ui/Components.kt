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
            .clip(RoundedCornerShape(22.dp))
            .background(p.card)
            .border(1.dp, p.border, RoundedCornerShape(22.dp))
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
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR),
            )
            if (unit != null && value != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, color = p.text2, modifier = Modifier.padding(bottom = 4.dp), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            if (spark.size >= 3) Sparkline(spark, tint, Modifier.width(54.dp).height(24.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(status)
            Spacer(Modifier.width(6.dp))
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
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = Tint.brand) }
    }
}

/** Stato vuoto: icona, titolo, spiegazione e azione. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, tint: Color = Tint.brand, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.card).border(1.dp, p.border, RoundedCornerShape(24.dp)).padding(22.dp),
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
// Logo BioEtà: un anello (il tempo, l'età) aperto in alto con un battito che lo attraversa.
// Stessa geometria dell'icona dell'app (res/drawable/ic_fg.xml), viewport 108.
// =============================================================================================

@Composable
fun BioLogo(modifier: Modifier = Modifier, size: Int = 40, mono: Color? = null) {
    Canvas(modifier.size(size.dp)) {
        val k = this.size.width / 66f // area utile 21..87 dell'icona adattiva
        fun x(v: Float) = (v - 21f) * k
        val ring = Brush.sweepGradient(listOf(Color(0xFF9D8BFF), Color(0xFF3ED69A), Color(0xFF45C8E0), Color(0xFF9D8BFF)), center = Offset(x(54f), x(54f)))
        drawArc(
            brush = mono?.let { Brush.linearGradient(listOf(it, it)) } ?: ring,
            startAngle = -50f, sweepAngle = 310f, useCenter = false,
            topLeft = Offset(x(28f), x(28f)), size = Size(52f * k, 52f * k),
            style = Stroke(7f * k, cap = StrokeCap.Round),
        )
        drawCircle(mono ?: Color(0xFF3ED69A), 3.6f * k, Offset(x(60.7f), x(28.9f)))
        val pulse = Path().apply {
            moveTo(x(34f), x(56f)); lineTo(x(44f), x(56f)); lineTo(x(48.5f), x(46f)); lineTo(x(54.5f), x(66f))
            lineTo(x(59.5f), x(52f)); lineTo(x(62.5f), x(56f)); lineTo(x(74f), x(56f))
        }
        drawPath(pulse, mono ?: Color.White, style = Stroke(4.6f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
