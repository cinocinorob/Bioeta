package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import it.cinocinorob.bioeta.science.HMetric
import it.cinocinorob.bioeta.science.HPeriod
import it.cinocinorob.bioeta.science.Highlight
import it.cinocinorob.bioeta.science.Highlights

private fun hTint(m: HMetric): Color = when (m) {
    HMetric.RHR -> Tint.heart; HMetric.HRV -> Tint.hrv; HMetric.SLEEP -> Tint.sleep; HMetric.STEPS -> Tint.activity
    HMetric.ACTIVE -> Tint.good; HMetric.VO2 -> Tint.vo2; HMetric.WEIGHT -> Tint.body; HMetric.RESP -> Tint.resp
}

fun hDetailKey(m: HMetric): String = when (m) {
    HMetric.RHR -> "RHR"; HMetric.HRV -> "HRV"; HMetric.SLEEP -> "SLEEP"; HMetric.STEPS -> "STEPS"
    HMetric.ACTIVE -> DETAIL_ACTIVE; HMetric.VO2 -> "VO2"; HMetric.WEIGHT -> "WEIGHT"; HMetric.RESP -> "RESP"
}

/** Sezione «In evidenza» della Home: frasi semplici su cosa è cambiato, ognuna con il suo grafico. */
@Composable
fun HighlightsSection(list: List<Highlight>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("highlights")) {
        if (list.isEmpty()) {
            HealthCard {
                Text("Settimana nella norma", style = MaterialTheme.typography.titleMedium, color = LocalPalette.current.text)
                Caption("Nessun parametro si discosta in modo rilevante dal tuo solito. Qui compaiono solo i cambiamenti che superano le normali oscillazioni.")
            }
        }
        list.forEach { h -> HighlightCard(h) { onOpen(hDetailKey(h.metric)) } }
    }
}

@Composable
private fun HighlightCard(h: Highlight, onClick: () -> Unit) {
    val p = LocalPalette.current
    val c = hTint(h.metric)
    HealthCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(8.dp))
            Text(h.metric.label, style = MaterialTheme.typography.labelLarge, color = p.text2, modifier = Modifier.weight(1f))
            val tone = when (h.good) { true -> Tint.good; false -> Tint.warn; null -> p.text3 }
            Text(when (h.good) { true -> "in meglio"; false -> "da tenere d'occhio"; null -> "cambiato" }, style = MaterialTheme.typography.labelMedium, color = tone)
        }
        Text(h.sentence, style = MaterialTheme.typography.titleMedium, color = p.text)
        HighlightBars(h, c)
        Text(if (h.period == HPeriod.WEEK) "Medie settimanali · ultime 5 settimane" else "Medie di 30 giorni · ultimi 4 mesi",
            style = MaterialTheme.typography.labelSmall, color = p.text3)
    }
}

/** Barre per periodo in stile Riepiloghi: i periodi passati in grigio, quello attuale nel colore del dato, linea tratteggiata = riferimento. */
@Composable
private fun HighlightBars(h: Highlight, color: Color) {
    val p = LocalPalette.current
    val vals = h.bars
    val present = vals.filterNotNull()
    if (present.isEmpty()) return
    val lo = minOf(present.min(), h.reference); val hi = maxOf(present.max(), h.reference)
    val span = (hi - lo).takeIf { it > 0 } ?: 1.0
    val base = lo - span * 0.6 // le barre partono da sotto il minimo, così le differenze si vedono senza esagerarle
    val label = p.text3
    Column {
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            val n = vals.size
            val slot = size.width / n
            val bw = slot * 0.56f
            fun y(v: Double) = (size.height * (1 - (v - base) / (hi - base))).toFloat()
            vals.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val x = i * slot + (slot - bw) / 2
                val top = y(v)
                drawRoundRect(if (i == n - 1) color else p.field, Offset(x, top), Size(bw, size.height - top), CornerRadius(8.dp.toPx()))
            }
            val ry = y(h.reference)
            drawLine(label, Offset(0f, ry), Offset(size.width, ry), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
        }
        Row(Modifier.fillMaxWidth()) {
            vals.forEachIndexed { i, v ->
                Text(v?.let { Highlights.fmt(h.metric, it) } ?: "—", style = MaterialTheme.typography.labelSmall,
                    color = if (i == vals.size - 1) p.text else p.text3, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            }
        }
    }
}
