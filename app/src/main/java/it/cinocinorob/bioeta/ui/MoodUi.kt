package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.science.MoodPoint
import it.cinocinorob.bioeta.science.MoodTrend
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val MOOD_INFO =
    "Ogni punto è l'umore che hai registrato nel diario (da Pessimo a Ottimo). La linea è la media degli ultimi 7 giorni, calcolata quando ci sono almeno 3 registrazioni: mostra la tendenza al netto degli alti e bassi quotidiani.\n\n" +
        "«Cosa va insieme al tuo umore» confronta i giorni con correlazione per ranghi (Spearman), adatta a una scala a 5 livelli, con intervallo di confidenza al 95%. Compare dopo 14 giorni con umore e dati dell'orologio. Se l'intervallo comprende lo zero non c'è un'associazione chiara.\n\n" +
        "Limiti: sono i dati di una sola persona e i giorni vicini si somigliano, quindi l'incertezza reale è maggiore di quella mostrata. Un'associazione non dice cosa causa cosa: il sonno può cambiare l'umore e l'umore può cambiare il sonno."

private fun moodWord(v: Double): String = MOOD_LABELS[(v.roundToInt() - 1).coerceIn(0, 4)]

/** Grafico dell'umore con media mobile e associazioni con i dati dell'orologio. */
@Composable
fun MoodTrendCard(vm: AppViewModel, onLog: (() -> Unit)? = null) {
    val p = LocalPalette.current
    var range by rememberSaveable { mutableIntStateOf(0) }
    val days = listOf(30, 90)[range]
    val today = LocalDate.now()
    val all = vm.moodPoints
    val pts = all.filter { it.date.isAfter(today.minusDays(days.toLong())) }
    HealthCard("Umore", BioIcons.Pencil, Tint.mood, info = MOOD_INFO, modifier = Modifier.testTag("mood_card")) {
        if (all.size < 3) {
            Caption("Registra come ti senti ogni giorno (in Home o nel Diario): dopo 3 giorni compare l'andamento, dopo 14 il confronto con sonno, HRV e attività.")
            if (onLog != null) androidx.compose.material3.OutlinedButton(onClick = onLog) { androidx.compose.material3.Text("Registra l'umore di oggi") }
            if (all.isNotEmpty()) MoodChart(pts, days)
            return@HealthCard
        }
        SegmentPill(listOf("30 giorni", "90 giorni"), range, onSelect = { range = it })
        val sm = MoodTrend.summary(all, today)
        Row(verticalAlignment = Alignment.Bottom) {
            val m = sm.mean7 ?: sm.mean30
            BigValue(m?.f(1) ?: "—", null, size = 34)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.padding(bottom = 4.dp)) {
                Text(m?.let { "media ${if (sm.mean7 != null) "7 giorni" else "30 giorni"}: ${moodWord(it).lowercase()}" } ?: "", style = MaterialTheme.typography.labelLarge, color = p.text)
                if (sm.mean7 != null && sm.prev7 != null) {
                    val d = sm.mean7 - sm.prev7
                    Text(
                        when { d >= 0.3 -> "in salita rispetto alla settimana prima (${d.fs(1)})"; d <= -0.3 -> "in calo rispetto alla settimana prima (${d.fs(1)})"; else -> "stabile rispetto alla settimana prima" },
                        style = MaterialTheme.typography.labelMedium, color = p.text2,
                    )
                }
            }
        }
        MoodChart(pts, days)
        Caption("${sm.days30} giorni registrati negli ultimi 30.")

        androidx.compose.material3.HorizontalDivider(color = p.sep)
        Text("Cosa va insieme al tuo umore", style = MaterialTheme.typography.titleSmall, color = p.text)
        val assoc = vm.moodAssociations
        if (assoc.all { it.second == null }) {
            Caption("Compare dopo ${MoodTrend.MIN_PAIRS} giorni con umore e dati dell'orologio (ora ${vm.moodPairedDays}).")
        } else assoc.forEach { (label, a) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = p.text)
                    Text(a?.let { MoodTrend.describe(it) } ?: "dati insufficienti", style = MaterialTheme.typography.bodySmall, color = p.text2)
                }
                if (a != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ρ ${a.rho.fs(2)}", style = MaterialTheme.typography.labelLarge, color = if (a.clear) p.text else p.text3)
                        Text("IC ${a.low.f(2)}…${a.high.f(2)} · n ${a.n}", style = MaterialTheme.typography.labelSmall, color = p.text3)
                    }
                }
            }
        }
        Caption("Associazioni nei tuoi dati, non cause.")
    }
}

/** Punti giornalieri colorati per livello + media mobile di 7 giorni, asse Y con le parole della scala. */
@Composable
fun MoodChart(points: List<MoodPoint>, days: Int) {
    val p = LocalPalette.current
    val today = LocalDate.now()
    val from = today.minusDays(days.toLong() - 1)
    val roll = MoodTrend.rolling(points).filter { !it.date.isBefore(from) }
    val label = p.text3.toArgb()
    val lineColor = p.text
    val grid = p.sep
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
    Canvas(Modifier.fillMaxWidth().height(170.dp).testTag("mood_chart")) {
        val left = 58.dp.toPx(); val right = 6.dp.toPx(); val top = 8.dp.toPx(); val bottom = 20.dp.toPx()
        val w = size.width - left - right; val h = size.height - top - bottom
        fun x(d: LocalDate) = left + (java.time.temporal.ChronoUnit.DAYS.between(from, d).toFloat() / (days - 1).coerceAtLeast(1)) * w
        fun y(v: Double) = top + (1f - ((v - 1.0) / 4.0).toFloat()) * h
        val paint = android.graphics.Paint().apply { color = label; textSize = 11.dp.toPx(); isAntiAlias = true }
        for (v in 1..5) {
            drawLine(grid, Offset(left, y(v.toDouble())), Offset(left + w, y(v.toDouble())), 1.dp.toPx(),
                pathEffect = if (v == 3) null else PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
            drawContext.canvas.nativeCanvas.drawText(MOOD_LABELS[v - 1], 0f, y(v.toDouble()) + 4.dp.toPx(), paint)
        }
        // etichette date: inizio e fine
        drawContext.canvas.nativeCanvas.drawText(from.format(fmt), left, size.height - 4.dp.toPx(), paint)
        val endLabel = today.format(fmt)
        drawContext.canvas.nativeCanvas.drawText(endLabel, left + w - paint.measureText(endLabel), size.height - 4.dp.toPx(), paint)
        // media mobile
        if (roll.size >= 2) {
            val path = Path()
            roll.forEachIndexed { i, m -> if (i == 0) path.moveTo(x(m.date), y(m.value)) else path.lineTo(x(m.date), y(m.value)) }
            drawPath(path, lineColor.copy(alpha = 0.85f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // punti
        val r = if (days > 45) 3.dp.toPx() else 4.5.dp.toPx()
        points.filter { !it.date.isBefore(from) }.forEach { m ->
            val c = MOOD_COLORS[(m.value.roundToInt() - 1).coerceIn(0, 4)]
            drawCircle(c, r, Offset(x(m.date), y(m.value)))
        }
    }
}

/** Striscia compatta degli ultimi 14 giorni: un quadratino per giorno, colorato per umore (vuoto se non registrato). */
@Composable
fun MoodStrip(vm: AppViewModel, days: Int = 14) {
    val p = LocalPalette.current
    val today = LocalDate.now()
    val byDate = vm.moodPoints.associate { it.date to it.value }
    val logged = (0 until days).count { byDate[today.minusDays(it.toLong())] != null }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth().testTag("mood_strip")) {
            (days - 1 downTo 0).forEach { k ->
                val v = byDate[today.minusDays(k.toLong())]
                Box(
                    Modifier.weight(1f).aspectRatio(0.62f).clip(RoundedCornerShape(4.dp))
                        .background(v?.let { MOOD_COLORS[(it.roundToInt() - 1).coerceIn(0, 4)] } ?: p.field),
                )
            }
        }
        Row {
            Text("2 settimane fa", style = MaterialTheme.typography.labelSmall, color = p.text3, modifier = Modifier.weight(1f))
            Text(if (logged > 0) "$logged giorni su $days" else "nessun giorno registrato", style = MaterialTheme.typography.labelSmall, color = p.text3)
            Spacer(Modifier.weight(1f))
            Text("oggi", style = MaterialTheme.typography.labelSmall, color = p.text3)
        }
    }
}
