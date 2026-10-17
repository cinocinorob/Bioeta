package it.cinocinorob.bioeta.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.science.Bedtime
import it.cinocinorob.bioeta.science.ExtraRefs
import it.cinocinorob.bioeta.science.LoadTrendState
import it.cinocinorob.bioeta.science.Ref
import it.cinocinorob.bioeta.science.VitalState
import it.cinocinorob.bioeta.science.WeekStat

/** Riga di fonti cliccabili (aprono PubMed). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SourcesRow(refs: List<Ref>) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Fonti:", fontSize = 11.sp, color = p.text2)
        refs.forEach { r ->
            Text(
                r.short, fontSize = 11.sp, color = Tint.blue, fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url))) } },
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Stasera: orario consigliato + caffeina
// ---------------------------------------------------------------------------------------------

@Composable
fun TonightCard(vm: AppViewModel) {
    val g = vm.bedtimeGuide
    val p = LocalPalette.current
    HealthCard("Stasera", BioIcons.Moon, Tint.sleep) {
        if (g == null) {
            Caption("Servono almeno 5 notti registrate nelle ultime 2 settimane per suggerirti un orario.")
            return@HealthCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Caption("A letto entro")
                BigValue(Bedtime.fmt(g.bedtime), null, size = 36, color = Tint.sleep)
            }
            Column(Modifier.weight(1f)) {
                Caption("Sveglia abituale")
                BigValue(Bedtime.fmt(g.wake), null, size = 36)
            }
        }
        NightArc(g.bedtime.hour * 60 + g.bedtime.minute, g.wake.hour * 60 + g.wake.minute, g.caffeineCutoff.hour * 60 + g.caffeineCutoff.minute)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF8D6E63).copy(alpha = 0.14f)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("☕", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Ultimo caffè entro le ${Bedtime.fmt(g.caffeineCutoff)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                Text("Una tazza (~100 mg) presa meno di 8,8 h prima di dormire riduce il sonno totale.", fontSize = 12.sp, color = p.text2)
            }
        }
        Caption(g.rationale)
        SourcesRow(listOf(ExtraRefs.WINDRED, ExtraRefs.GARDINER))
    }
}

/** Arco delle 24 ore: finestra di sonno (viola) e limite caffeina (marrone). */
@Composable
private fun NightArc(bedMin: Int, wakeMin: Int, caffeineMin: Int) {
    val p = LocalPalette.current
    Canvas(Modifier.fillMaxWidth().height(34.dp)) {
        // asse da 12:00 a 12:00 del giorno dopo
        fun x(m: Int): Float { val u = ((m - 720 + 1440) % 1440); return u / 1440f * size.width }
        val h = 12.dp.toPx(); val y = size.height / 2 - h / 2
        drawRoundRect(p.field, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
        val xb = x(bedMin); val xw = x(wakeMin)
        drawRoundRect(Brush.horizontalGradient(Pillar.sleep), Offset(xb, y), Size((xw - xb).coerceAtLeast(h), h), CornerRadius(h / 2))
        val xc = x(caffeineMin)
        drawCircle(Color(0xFF8D6E63), 6.dp.toPx(), Offset(xc, size.height / 2))
        // tacche a mezzanotte
        drawLine(p.text2.copy(alpha = 0.5f), Offset(size.width / 2, y - 4f), Offset(size.width / 2, y + h + 4f), 2f)
    }
    Row(Modifier.fillMaxWidth()) {
        listOf("12", "18", "00", "06", "12").forEachIndexed { i, l ->
            Text(l, fontSize = 10.sp, color = p.text2, modifier = Modifier.weight(1f),
                textAlign = when (i) { 0 -> androidx.compose.ui.text.style.TextAlign.Start; 4 -> androidx.compose.ui.text.style.TextAlign.End; else -> androidx.compose.ui.text.style.TextAlign.Center })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Parametri vitali notturni
// ---------------------------------------------------------------------------------------------

@Composable
fun VitalsCard(vm: AppViewModel) {
    val r = vm.vitals
    val p = LocalPalette.current
    val tint = when { r.outliers >= 2 -> Tint.warn; else -> Tint.heart }
    HealthCard("Parametri vitali", BioIcons.Pulse, tint, trailing = "notte scorsa") {
        r.vitals.filter { it.state != VitalState.NO_DATA }.forEach { v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val c = when (v.state) { VitalState.IN_RANGE -> Tint.good; VitalState.NO_DATA -> p.text2; else -> Tint.warn }
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(8.dp))
                Text(v.label, fontSize = 14.sp, color = p.text, modifier = Modifier.width(104.dp), maxLines = 1)
                RangeDot(v.value!!, v.low, v.high, c, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text("${v.value.f(v.decimals)} ${v.unit}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            }
        }
        Caption(r.message)
        SourcesRow(listOf(ExtraRefs.MISHRA))
    }
}

/** Intervallo personale (banda) con il valore della notte (punto). */
@Composable
internal fun RangeDot(value: Double, low: Double?, high: Double?, color: Color, modifier: Modifier) {
    val p = LocalPalette.current
    Canvas(modifier.height(16.dp)) {
        val h = 6.dp.toPx(); val y = size.height / 2
        drawRoundRect(p.field, Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
        if (low == null || high == null) {
            drawCircle(color, 5.dp.toPx(), Offset(size.width / 2, y)); return@Canvas
        }
        val span = (high - low)
        val lo = low - span * 0.6; val hi = high + span * 0.6
        fun x(v: Double) = ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) * size.width
        drawRoundRect(Tint.good.copy(alpha = 0.30f), Offset(x(low), y - h / 2), Size(x(high) - x(low), h), CornerRadius(h / 2))
        drawCircle(p.card, 7.dp.toPx(), Offset(x(value), y))
        drawCircle(color, 5.dp.toPx(), Offset(x(value), y))
    }
}

// ---------------------------------------------------------------------------------------------
// Andamento del carico (Attività)
// ---------------------------------------------------------------------------------------------

@Composable
fun LoadTrendCard(vm: AppViewModel) {
    val t = vm.loadTrend
    val c = when (t.state) {
        LoadTrendState.DETRAINING -> Tint.blue; LoadTrendState.STEADY -> Tint.good
        LoadTrendState.BUILDING -> Tint.activity; LoadTrendState.SPIKE -> Tint.warn; LoadTrendState.NA -> Tint.sleep
    }
    HealthCard("Andamento del carico", BioIcons.Gauge, Tint.vo2,
        info = "Carico medio degli ultimi 7 giorni diviso per quello degli ultimi 28 (stessa idea del rapporto acuto:cronico di Gabbett 2016, PMID 26758673). È una descrizione di quanto stai cambiando il carico, non una previsione di infortunio: un legame causale non è dimostrato (Impellizzeri 2020, PMID 32502973). Servono almeno 14 giorni con l'orologio indossato.") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Caption("7 giorni vs 28 giorni")
                BigValue(t.ratio?.f(2) ?: "—", null, size = 34)
            }
            Pill(t.state.label, c)
        }
        RatioGauge(t.ratio)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip("Media 7 gg", t.acute.f(0), "al giorno", Modifier.weight(1f))
            StatChip("Media 28 gg", t.chronic.f(0), "al giorno", Modifier.weight(1f))
        }
        Caption(t.message)
        SourcesRow(listOf(ExtraRefs.GABBETT, ExtraRefs.IMPELLIZZERI))
    }
}

@Composable
private fun RatioGauge(ratio: Double?) {
    val p = LocalPalette.current
    Canvas(Modifier.fillMaxWidth().height(26.dp)) {
        val h = 10.dp.toPx(); val y = size.height / 2 - h / 2
        fun x(r: Double) = (r / 2.0).toFloat().coerceIn(0f, 1f) * size.width
        val segs = listOf(0.0 to 0.8 to Tint.blue, 0.8 to 1.3 to Tint.good, 1.3 to 1.5 to Tint.activity, 1.5 to 2.0 to Tint.warn)
        segs.forEach { (r, col) ->
            drawRect(col.copy(alpha = 0.35f), Offset(x(r.first), y), Size(x(r.second) - x(r.first), h))
        }
        ratio?.let {
            drawCircle(p.card, 10.dp.toPx(), Offset(x(it), size.height / 2))
            drawCircle(Tint.vo2, 7.dp.toPx(), Offset(x(it), size.height / 2))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Questa settimana
// ---------------------------------------------------------------------------------------------

@Composable
fun WeekCard(vm: AppViewModel) {
    val stats = vm.weekStats.filter { it.thisWeek != null }
    if (stats.isEmpty()) return
    val p = LocalPalette.current
    HealthCard("Questa settimana", BioIcons.Sparkle, Tint.bio, trailing = "vs settimana prima") {
        stats.forEach { s -> WeekRow(s) }
    }
}

@Composable
private fun WeekRow(s: WeekStat) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(s.label, fontSize = 14.sp, color = p.text, modifier = Modifier.weight(1f))
        Text(
            fmtStat(s.thisWeek!!, s), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.text,
        )
        Spacer(Modifier.width(10.dp))
        val d = s.lastWeek?.let { s.thisWeek - it }
        val pct = s.lastWeek?.takeIf { it > 0 }?.let { (s.thisWeek - it) / it * 100 }
        val good = when { d == null || s.higherIsBetter == null -> null; kotlin.math.abs(pct ?: 0.0) < 3 -> null; else -> (d > 0) == s.higherIsBetter }
        val col = when (good) { true -> Tint.good; false -> Tint.warn; null -> p.text2 }
        Box(Modifier.width(64.dp).clip(RoundedCornerShape(10.dp)).background(col.copy(alpha = 0.14f)).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
            Text(
                when { d == null -> "nuovo"; else -> (if (d >= 0) "▲ " else "▼ ") + fmtDelta(kotlin.math.abs(d), s) },
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = col,
            )
        }
    }
}

private fun fmtStat(v: Double, s: WeekStat): String = when (s.unit) {
    "" -> if (v >= 1000) String.format(java.util.Locale.ITALY, "%,.0f", v) else v.f(s.decimals)
    else -> "${v.f(s.decimals)} ${s.unit}"
}

private fun fmtDelta(v: Double, s: WeekStat): String =
    if (v >= 1000) String.format(java.util.Locale.ITALY, "%,.0f", v) else v.f(s.decimals)
