package it.cinocinorob.bioeta.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.science.Contributor
import it.cinocinorob.bioeta.science.SleepNight
import it.cinocinorob.bioeta.science.StageType
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object RecoveryColors {
    val sleep = listOf(Color(0xFF5E5CE6), Color(0xFFBF5AF2))
    val ready = listOf(Color(0xFF30D158), Color(0xFF64D2FF))
    val deep = Color(0xFF3634A3)
    val rem = Color(0xFF64D2FF)
    val light = Color(0xFF7D7AFF)
    val awake = Color(0xFFFF9F0A)
}

fun scoreColor(s: Int?): Color = when {
    s == null -> Color(0xFF8E8E93)
    s >= 85 -> Tint.good
    s >= 70 -> Color(0xFF34C3A0)
    s >= 50 -> Tint.warn
    else -> Tint.bad
}

/** Anello a gradiente con il punteggio al centro, animato. */
@Composable
fun ScoreRing(score: Int?, label: String, colors: List<Color>, size: Int = 130, stroke: Int = 13) {
    val p = LocalPalette.current
    val anim = remember { Animatable(0f) }
    LaunchedEffect(score) { anim.animateTo((score ?: 0) / 100f, tween(1000)) }
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val st = stroke.dp.toPx()
            val inset = st / 2
            val arc = Size(this.size.width - st, this.size.height - st)
            drawArc(colors.first().copy(alpha = 0.16f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(st))
            if (score != null) drawArc(
                Brush.sweepGradient(colors + colors.first()), -90f, 360f * anim.value, false,
                Offset(inset, inset), arc, style = Stroke(st, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score?.toString() ?: "—", fontSize = (size * 0.27).sp, fontWeight = FontWeight.Bold, color = p.text)
            Text(label, fontSize = 12.sp, color = p.text2, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Riga contributore: etichetta, barra 0–100 colorata, dettaglio. */
@Composable
fun ContributorRow(c: Contributor) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.label, fontSize = 15.sp, color = p.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("peso ${(c.weight * 100).toInt()}%", fontSize = 11.sp, color = p.text2)
            Spacer(Modifier.width(8.dp))
            Text(c.score?.toString() ?: "—", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scoreColor(c.score))
        }
        val sc = scoreColor(c.score)
        val track = p.field
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawRoundRect(track, cornerRadius = CornerRadius(3.dp.toPx()))
            if (c.score != null) drawRoundRect(sc, size = Size(size.width * c.score / 100f, size.height), cornerRadius = CornerRadius(3.dp.toPx()))
        }
        Text(c.detail, fontSize = 12.sp, lineHeight = 16.sp, color = p.text2)
    }
}

/** Ipnogramma: livelli Veglia / REM / Leggero / Profondo nel tempo. */
@Composable
fun Hypnogram(night: SleepNight) {
    val p = LocalPalette.current
    val total = Duration.between(night.start, night.end).seconds.toFloat().coerceAtLeast(1f)
    fun level(t: StageType) = when (t) {
        StageType.AWAKE, StageType.OUT_OF_BED -> 0
        StageType.REM -> 1
        StageType.LIGHT, StageType.ASLEEP_UNKNOWN -> 2
        StageType.DEEP -> 3
    }
    fun color(t: StageType) = when (t) {
        StageType.AWAKE, StageType.OUT_OF_BED -> RecoveryColors.awake
        StageType.REM -> RecoveryColors.rem
        StageType.LIGHT, StageType.ASLEEP_UNKNOWN -> RecoveryColors.light
        StageType.DEEP -> RecoveryColors.deep
    }
    val labels = listOf("Veglia", "REM", "Leggero", "Profondo")
    Column {
        Row {
            Column(Modifier.width(62.dp).height(120.dp), verticalArrangement = Arrangement.SpaceAround) {
                labels.forEach { Text(it, fontSize = 11.sp, color = p.text2) }
            }
            Canvas(Modifier.weight(1f).height(120.dp)) {
                val rowH = size.height / 4
                val barH = rowH * 0.62f
                night.stages.sortedBy { it.start }.forEach { s ->
                    val x0 = Duration.between(night.start, s.start).seconds / total * size.width
                    val w = (s.minutes * 60 / total * size.width).toFloat().coerceAtLeast(1.5f)
                    val y = level(s.type) * rowH + (rowH - barH) / 2
                    drawRoundRect(color(s.type), Offset(x0, y), Size(w, barH), CornerRadius(3.dp.toPx()))
                }
            }
        }
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val z = ZoneId.systemDefault()
        Row(Modifier.fillMaxWidth().padding(start = 62.dp)) {
            Text(fmt.format(night.start.atZone(z)), fontSize = 11.sp, color = p.text2, modifier = Modifier.weight(1f))
            Text(fmt.format(night.end.atZone(z)), fontSize = 11.sp, color = p.text2)
        }
    }
}

/** Barre degli ultimi 14 giorni per un punteggio. */
@Composable
fun ScoreBars(values: List<Pair<java.time.LocalDate, Int?>>, colors: List<Color>) {
    val p = LocalPalette.current
    Column {
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.62f
            values.forEachIndexed { i, (_, v) ->
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(colors.first().copy(alpha = 0.12f), Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2))
                if (v != null) {
                    val h = size.height * v / 100f
                    drawRoundRect(Brush.verticalGradient(colors.reversed(), startY = size.height - h, endY = size.height),
                        Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 2))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            val f = DateTimeFormatter.ofPattern("EEEEE", Locale.ITALIAN)
            values.forEach { (d, _) ->
                Text(f.format(d).uppercase(), fontSize = 10.sp, color = p.text2, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

/** Sezione completa Sonno + Prontezza, in cima alla scheda Recupero. */
@Composable
fun RecoverySection(vm: AppViewModel, onRequestPermissions: () -> Unit) {
    val p = LocalPalette.current
    val today = vm.today
    val history = vm.recoveryHistory
    var showSleepDetail by rememberSaveable { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (vm.missingRecoveryPermissions) {
            HealthCard("Nuovi permessi per Sonno e Prontezza", BioIcons.Moon, Tint.sleep, onClick = onRequestPermissions) {
                Caption("Per i punteggi servono anche frequenza cardiaca e allenamenti. Tocca per concederli.")
            }
        }
        // Anelli
        Row(
            Modifier.fillMaxWidth().softShadow(p.dark).clip(RoundedCornerShape(24.dp)).background(p.card).padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreRing(today.sleep?.score, "SONNO", RecoveryColors.sleep)
                Text(today.sleep?.band ?: "Nessuna notte", fontSize = 13.sp, color = p.text2, modifier = Modifier.padding(top = 6.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreRing(today.readiness.score, "PRONTEZZA", RecoveryColors.ready)
                Text(if (today.readiness.calibrating) "In calibrazione" else today.readiness.band, fontSize = 13.sp, color = p.text2, modifier = Modifier.padding(top = 6.dp))
            }
        }
        (today.readiness.flags + (today.sleep?.flags ?: emptyList())).forEach { fl ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tint.warn.copy(alpha = 0.12f)).padding(12.dp)) {
                androidx.compose.material3.Icon(BioIcons.Info, null, tint = Tint.warn, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(fl, fontSize = 13.sp, lineHeight = 17.sp, color = p.text)
            }
        }

        // Prontezza
        HealthCard("Prontezza", BioIcons.Bolt, Color(0xFF30C85E)) {
            if (today.readiness.calibrating) Caption("Servono circa 14 giorni di HRV o FC a riposo per costruire la tua baseline personale. Nel frattempo i singoli contributori sono già visibili.")
            today.readiness.contributors.forEach { ContributorRow(it) }
            Text("Ultimi 14 giorni", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text2, modifier = Modifier.padding(top = 6.dp))
            ScoreBars(history.map { it.date to it.readiness.score }, RecoveryColors.ready)
        }

        // Sonno
        HealthCard("Sonno", BioIcons.Moon, Tint.sleep, trailing = today.night?.date?.itLong()) {
            val n = today.night
            if (n == null) {
                Caption("Nessuna notte registrata oggi. Indossa l'orologio a letto e verifica che Fitbit condivida il sonno con Connessione Salute.")
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    val h = n.totalSleepMin / 60
                    BigValue("${h.toInt()}h ${((h - h.toInt()) * 60).toInt().toString().padStart(2, '0')}m", null, size = 28)
                    Spacer(Modifier.width(10.dp))
                    Caption("dormite su ${String.format(Locale.ITALY, "%.1f", n.timeInBedMin / 60)} h a letto")
                }
                if (n.hasStages) Hypnogram(n)
                if (n.hasArchitecture) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple("Profondo", StageType.DEEP, RecoveryColors.deep),
                        Triple("REM", StageType.REM, RecoveryColors.rem),
                        Triple("Leggero", StageType.LIGHT, RecoveryColors.light),
                    ).forEach { (l, t, c) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(c))
                            Text(" $l ${n.stagePct(t)?.let { String.format(Locale.ITALY, "%.0f%%", it) } ?: "—"}", fontSize = 12.sp, color = p.text2)
                        }
                    }
                }
                Text(
                    if (showSleepDetail) "Nascondi contributori" else "Mostra contributori",
                    color = Tint.blue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { showSleepDetail = !showSleepDetail },
                )
                if (showSleepDetail) today.sleep?.contributors?.forEach { ContributorRow(it) }
            }
            Text("Ultimi 14 giorni", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text2, modifier = Modifier.padding(top = 6.dp))
            ScoreBars(history.map { it.date to it.sleep?.score }, RecoveryColors.sleep)
        }
    }
}

/** Versione compatta per il Riepilogo. */
@Composable
fun RecoveryMini(vm: AppViewModel, onOpen: () -> Unit) {
    val p = LocalPalette.current
    val t = vm.today
    Row(
        Modifier.fillMaxWidth().softShadow(p.dark).clip(RoundedCornerShape(20.dp)).background(p.card).clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreRing(t.sleep?.score, "SONNO", RecoveryColors.sleep, size = 84, stroke = 9)
        Spacer(Modifier.width(10.dp))
        ScoreRing(t.readiness.score, "PRONTEZZA", RecoveryColors.ready, size = 84, stroke = 9)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Oggi", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = p.text)
            val worst = t.readiness.contributors.filter { it.score != null }.minByOrNull { it.score!! }
            Caption(
                when {
                    t.readiness.flags.isNotEmpty() -> "Segnali di stress fisiologico: guarda i dettagli"
                    t.readiness.calibrating -> "Prontezza in calibrazione"
                    worst != null && worst.score!! < 60 -> "Da curare: ${worst.label.lowercase()}"
                    else -> "Tocca per i dettagli"
                }
            )
        }
        androidx.compose.material3.Icon(BioIcons.Chevron, null, tint = p.text2, modifier = Modifier.size(14.dp))
    }
}
