package it.cinocinorob.bioeta.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import it.cinocinorob.bioeta.science.LoadStatus
import it.cinocinorob.bioeta.science.LoadQuality
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.data.Sex
import it.cinocinorob.bioeta.health.HealthRepo
import it.cinocinorob.bioeta.health.Metric
import it.cinocinorob.bioeta.science.Fitness
import it.cinocinorob.bioeta.science.Zone
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

fun zoneColor(z: Zone): Color = when (z) {
    Zone.VERY_LIGHT -> Color(0xFF6E7A88)
    Zone.LIGHT -> Color(0xFF45C8E0)
    Zone.MODERATE -> Color(0xFF3ED69A)
    Zone.VIGOROUS -> Color(0xFFFFAA4C)
    Zone.MAXIMAL -> Color(0xFFFF5D73)
}

/** Tre anelli concentrici: passi, minuti attivi della settimana, carico di oggi. */
@Composable
fun ActivityRings(steps: Float, minutes: Float, load: Float) {
    val anims = listOf(
        animatedValue(steps.coerceIn(0f, 1.5f), 900), animatedValue(minutes.coerceIn(0f, 1.5f), 1050), animatedValue(load.coerceIn(0f, 1.5f), 1200),
    )
    val colors = listOf(
        listOf(Color(0xFFFFC98A), Tint.activity),
        listOf(Color(0xFF8FEBC6), Tint.good),
        listOf(Color(0xFFFFA48C), Tint.load),
    )
    Canvas(Modifier.size(170.dp)) {
        val st = 17.dp.toPx(); val gap = 4.dp.toPx()
        colors.forEachIndexed { i, c ->
            val inset = st / 2 + i * (st + gap)
            val tl = Offset(inset, inset); val sz = Size(size.width - 2 * inset, size.height - 2 * inset)
            drawArc(c[0].copy(alpha = 0.18f), 0f, 360f, false, tl, sz, style = Stroke(st))
            val v = anims[i]
            if (v > 0.003f) drawArc(Brush.sweepGradient(listOf(c[0], c[1], c[0])), -90f, 360f * v.coerceAtMost(1f), false, tl, sz, style = Stroke(st, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun RingLegend(color: Color, label: String, value: String) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Column(Modifier.padding(start = 8.dp)) {
            Text(value, style = androidx.compose.ui.text.TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, fontFeatureSettings = TABULAR), color = p.text)
            Text(label, style = MaterialTheme.typography.labelSmall, color = p.text2)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ZoneBar(zones: Map<Zone, Int>, showLegend: Boolean = true) {
    val p = LocalPalette.current
    val shown = zones.filterKeys { it != Zone.VERY_LIGHT }
    val total = shown.values.sum().coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            var x = 0f
            drawRoundRect(p.field, cornerRadius = CornerRadius(size.height / 2))
            Zone.entries.filter { it != Zone.VERY_LIGHT }.forEach { z ->
                val w = size.width * (shown[z] ?: 0) / total
                if (w > 0) drawRect(zoneColor(z), Offset(x, 0f), Size(w, size.height))
                x += w
            }
        }
        if (showLegend) androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Zone.entries.filter { it != Zone.VERY_LIGHT }.forEach { z ->
                LegendDot(zoneColor(z), "${z.short} ${shown[z] ?: 0}′")
            }
        }
    }
}

@Composable
fun ActivityScreen(
    vm: AppViewModel,
    modifier: Modifier,
    onRequestPermissions: () -> Unit,
    onOpenSync: () -> Unit = {},
    onOpenMetric: (String) -> Unit = {},
) {
    val p = LocalPalette.current
    val today = LocalDate.now()
    val age = vm.profile.ageAt(today)
    val goal = Fitness.stepGoal(age)
    val act = vm.todayActivity
    val dayAct = vm.todayDayActivity
    val target = vm.targetLoad
    val week = Fitness.weekActiveMinutes(vm.dayLoads, today)
    val quality = dayAct?.quality ?: LoadQuality.UNAVAILABLE

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { LargeHeader("Attività", "Movimento e allenamento") }
        if (vm.fitnessLoading || vm.syncing) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Tint.activity) }
        if (age == null) item {
            InlineNotice("Senza data di nascita la FC massima è stimata con 40 anni: inseriscila nel Profilo per zone e carico corretti.", NoticeKind.WARN)
        }
        if (vm.missingRecoveryPermissions) item {
            InlineNotice("Concedi frequenza cardiaca, allenamenti, calorie e distanza per completare la scheda.", NoticeKind.WARN, "Concedi", onRequestPermissions)
        }

        // ---------- Sincronizzazione allenamenti ----------
        item {
            val st = vm.syncStatus
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.card).clickable(onClick = onOpenSync).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BioIcons.Refresh, null, tint = Tint.brand, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${st.sessionsInWindow} allenamenti importati · ${st.state.label.lowercase()}", style = MaterialTheme.typography.titleSmall, color = p.text)
                    Text("Ultima sincronizzazione ${agoShort(st.lastSync)}" + (st.origins.takeIf { it.isNotEmpty() }?.let { " · " + it.joinToString(", ") } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = p.text2, maxLines = 1)
                }
                TextButton(onClick = { vm.syncNow() }, enabled = !vm.syncing && !vm.loading) { Text("Sincronizza", color = Tint.brand) }
            }
        }

        // ---------- Anelli ----------
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(p.card).border(1.dp, p.border, RoundedCornerShape(26.dp)).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActivityRings(
                    steps = (act?.steps ?: 0L) / goal.toFloat(),
                    minutes = week / 150f,
                    load = if (quality == LoadQuality.UNAVAILABLE) 0f else ((dayAct?.load ?: 0.0) / maxOf(target?.high ?: loadScale(vm), 1.0)).toFloat(),
                )
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RingLegend(Tint.activity, "passi / ${String.format(Locale.ITALY, "%,d", goal)}", act?.let { String.format(Locale.ITALY, "%,d", it.steps) } ?: "—")
                    RingLegend(Tint.good, "min attivi sett. / 150", "$week")
                    RingLegend(Tint.load, target?.let { "carico / ${it.high.f(0)}" } ?: "carico oggi", if (quality == LoadQuality.UNAVAILABLE) "—" else dayAct!!.load.f(0))
                }
            }
        }

        // ---------- Carico ----------
        item {
            HealthCard(
                "Carico cardiaco", BioIcons.Pulse, Tint.load, trailing = quality.label, onClick = { onOpenMetric(DETAIL_LOAD) },
                info = "Carico = TRIMP di Banister (Morton 1990, PMID 2246166) calcolato minuto per minuto dalla frequenza cardiaca, sommando i minuti sopra il 30% della riserva cardiaca (FCmax = 208 − 0,7 × età, Tanaka 2001). Misura lo stress cardiovascolare, non il lavoro muscolare né le calorie. Con meno di 10 ore di frequenza cardiaca nella giornata il valore è parziale (sottostimato).\n\nObiettivo: parte dalla tua media delle ultime 4 settimane ed è modulato dal recupero. Viene proposto solo dopo ${Fitness.TARGET_MIN_DAYS} giorni con l'orologio indossato: con meno dati la media di riferimento è inattendibile. È una scelta progettuale, non un algoritmo validato.",
            ) {
                when (quality) {
                    LoadQuality.UNAVAILABLE -> {
                        Text("—", style = MaterialTheme.typography.displaySmall, color = p.text3)
                        InlineNotice("Nessuna frequenza cardiaca oggi in Connessione Salute: il carico non è calcolabile.", NoticeKind.WARN)
                    }
                    else -> {
                        val da = dayAct!!
                        Row(verticalAlignment = Alignment.Bottom) {
                            BigValue(da.load.f(0), null, size = 38)
                            Spacer(Modifier.width(8.dp))
                            if (target != null) Caption("obiettivo ${target.low.f(0)}–${target.high.f(0)}")
                            Spacer(Modifier.weight(1f))
                            if (target != null) {
                                val st = Fitness.status(da.load, target)
                                Pill(st.label, when (st) { LoadStatus.IN_RANGE -> Tint.good; LoadStatus.BELOW -> Tint.blue; LoadStatus.ABOVE -> Tint.warn })
                            }
                        }
                        if (target != null) TargetGauge(da.load, target.low, target.high)
                        else Caption("Obiettivo personale disponibile dopo ${Fitness.TARGET_MIN_DAYS} giorni con l'orologio indossato (ora ${Fitness.completeDays(vm.dayLoads, today)}).")
                        if (quality == LoadQuality.PARTIAL) Caption("Parziale: frequenza cardiaca per ${da.hrMinutes} minuti. Il valore è sottostimato.", Tint.warn)
                        ZoneBar(da.zoneMinutes)
                    }
                }
                DayBars(loadBars(vm), Tint.load, "carico", band = target?.let { it.low to it.high })
            }
        }

        // ---------- Minuti attivi ----------
        item {
            HealthCard(
                "Minuti attivi", BioIcons.Bolt, Tint.good, onClick = { onOpenMetric(DETAIL_ACTIVE) },
                info = "OMS 2020 (PMID 33239350): 150–300 minuti a settimana di attività moderata; un minuto vigoroso vale 2.\n\nMisurati: minuti con frequenza cardiaca ≥ 40% della riserva cardiaca (moderati) o ≥ 60% (vigorosi), secondo ACSM (PMID 21694556). I buchi di FC fino a 5 minuti vengono interpolati.\n\nStimati: minuti di allenamenti registrati senza frequenza cardiaca; intensità attribuita dal tipo di attività secondo il Compendio delle attività fisiche 2024 (PMID 38242596), in modo prudente. Yoga, stretching e attività non classificate non sono conteggiati. Ogni minuto conta una sola volta anche se due app registrano lo stesso allenamento.",
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    BigValue("$week", "/ 150 min questa settimana", size = 32)
                }
                ProgressBar(week / 150f, listOf(Color(0xFF8FEBC6), Tint.good))
                WeekActiveBars(vm)
                if (dayAct != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("Oggi da FC", "${dayAct.moderateMeasured + 2 * dayAct.vigorousMeasured}", "min", Modifier.weight(1f))
                    StatChip("Oggi stimati", "${dayAct.estimatedShare}", "min", Modifier.weight(1f))
                }
            }
        }

        // ---------- Passi ----------
        item {
            HealthCard("Passi", BioIcons.Bolt, Tint.activity, onClick = { onOpenMetric("STEPS") },
                info = "Passi da Connessione Salute. Se l'aggregato di Connessione Salute è vuoto, BioEtà somma i record grezzi prendendo, per ogni ora, il valore più alto tra le app (mai la somma). Nelle meta-analisi di coorte (Paluch 2022) il rischio di mortalità scende fino a circa 8–10 mila passi al giorno sotto i 60 anni e 6–8 mila sopra.") {
                val pts = vm.wearable[Metric.STEPS].orEmpty().filter { it.date.isAfter(today.minusDays(7)) }
                val all = pts.map { it.date to it.value } + listOfNotNull(act?.let { today to it.steps.toDouble() })
                StepWeek(all.distinctBy { it.first }.sortedBy { it.first }, goal)
                val avg = vm.wearable[Metric.STEPS].orEmpty().filter { it.date.isAfter(today.minusDays(30)) }.map { it.value }
                if (avg.isNotEmpty()) Caption("Media 30 giorni: ${String.format(Locale.ITALY, "%,d", avg.average().toLong())} passi")
                act?.let { HourlyBars(it.hourlySteps, Tint.activity) }
            }
        }

        // ---------- Allenamenti ----------
        item { LoadTrendCard(vm) }
        item { SectionHeader("Allenamenti recenti", "Tutti", onOpenSync) }
        if (vm.displaySessions.isEmpty()) item {
            EmptyState(BioIcons.Bolt, "Nessun allenamento importato",
                "Avvia un esercizio dall'orologio: quando l'app dell'orologio lo scrive in Connessione Salute compare qui con durata, frequenza cardiaca, zone e carico.", Tint.vo2, "Sincronizza ora") { vm.syncNow() }
        }
        items(vm.displaySessions.filter { it.start.isAfter(java.time.Instant.now().minus(java.time.Duration.ofDays(14))) }.take(12), key = { it.id }) { w -> WorkoutCard(w, vm) }

    }
}

/** Scala del carico quando non c'è ancora un obiettivo: massimo delle ultime 4 settimane. */
private fun loadScale(vm: AppViewModel): Double = vm.dayActivities.maxOfOrNull { it.load } ?: 100.0

@Composable
private fun WorkoutCard(w: it.cinocinorob.bioeta.science.Session, vm: AppViewModel) {
    val p = LocalPalette.current
    val zone = ZoneId.systemDefault()
    val st = vm.workoutStats[w.id]
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(p.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(Pillar.load)), contentAlignment = Alignment.Center) {
                Icon(BioIcons.Bolt, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(exerciseName(w.hcType, w.title), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                Text(w.start.atZone(zone).format(DateTimeFormatter.ofPattern("EEEE d MMM, HH:mm", Locale.ITALIAN)) + " · " + vm.health.originLabel(w.origin), fontSize = 12.sp, color = p.text2)
            }
            st?.load?.let { Pill("carico ${it.f(0)}", Pillar.load[1]) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("Durata", w.minutes.f(0), "min", Modifier.weight(1f))
            StatChip("FC media", st?.avgHr?.f(0) ?: "—", "bpm", Modifier.weight(1f))
            StatChip("FC max", st?.maxHr?.f(0) ?: "—", "bpm", Modifier.weight(1f))
        }
        if (st != null && st.hrCoverage > 0) ZoneBar(st.zoneMinutes)
        else Caption("Nessuna frequenza cardiaca per questo allenamento: minuti attivi stimati dal tipo di attività, carico non calcolabile.")
    }
}

/** Barra di un giorno: valore (null = dato assente) e se è parziale (es. poca FC). */
data class DayBar(val date: LocalDate, val value: Double?, val partial: Boolean = false)

/**
 * Barre giornaliere su una griglia FISSA di giorni (es. 28): i giorni senza dato restano visibili come
 * puntini, così un solo giorno con dati non diventa una barra gigante. Asse Y con valori, banda
 * dell'obiettivo opzionale, tocco per leggere il valore del giorno.
 */
@Composable
fun DayBars(
    bars: List<DayBar>,
    color: Color,
    unit: String,
    band: Pair<Double, Double>? = null,
    height: Int = 130,
    decimals: Int = 0,
) {
    val p = LocalPalette.current
    if (bars.isEmpty()) return
    val mxRaw = maxOf(bars.mapNotNull { it.value }.maxOrNull() ?: 0.0, band?.second ?: 0.0, 1.0)
    // Scala "pulita" per le etichette dell'asse
    val step = niceStep(mxRaw / 3)
    val mx = (kotlin.math.ceil(mxRaw / step) * step).toFloat()
    var sel by androidx.compose.runtime.remember(bars) { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val f = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
    Column {
        val s = sel
        Text(
            if (s != null) bars[s].let { b -> "${b.date.format(f)} · " + (b.value?.let { "${it.f(decimals)} $unit" + if (b.partial) " (parziale)" else "" } ?: "nessun dato") }
            else "Tocca una barra per il valore",
            style = MaterialTheme.typography.labelMedium, color = if (s != null) p.text else p.text3,
        )
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier.fillMaxWidth().height(height.dp).pointerInput(bars) {
                detectTapGestures { o ->
                    val axis = 34.dp.toPx(); val w = size.width - axis
                    val i = ((o.x / w) * bars.size).toInt().coerceIn(0, bars.lastIndex)
                    sel = if (sel == i) null else i
                }
            },
        ) {
            val axis = 34.dp.toPx()
            val w = size.width - axis
            val h = size.height
            val slot = w / bars.size
            val bw = minOf(slot * 0.62f, 14.dp.toPx())
            val labelPaint = android.graphics.Paint().apply { this.color = p.text3.toArgb(); textSize = 10.dp.toPx(); isAntiAlias = true }
            // griglia e asse Y
            var v = 0.0
            while (v <= mx + 1e-9) {
                val y = h - (v / mx * h).toFloat()
                drawLine(p.sep, Offset(0f, y), Offset(w, y), strokeWidth = 1f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                drawContext.canvas.nativeCanvas.drawText(v.f(0), w + 6.dp.toPx(), (y + 4.dp.toPx()).coerceIn(10.dp.toPx(), h), labelPaint)
                v += step
            }
            band?.let { (lo, hi) ->
                val y1 = h - (hi / mx * h).toFloat(); val y2 = h - (lo / mx * h).toFloat()
                drawRect(Tint.good.copy(alpha = 0.14f), Offset(0f, y1), Size(w, (y2 - y1).coerceAtLeast(2f)))
            }
            bars.forEachIndexed { i, b ->
                val cx = i * slot + slot / 2
                val value = b.value
                if (value == null) {
                    drawCircle(p.text3.copy(alpha = 0.5f), 1.6.dp.toPx(), Offset(cx, h - 2.dp.toPx()))
                } else {
                    val bh = (value / mx * h).toFloat().coerceAtLeast(3.dp.toPx())
                    val a = when { sel == i -> 1f; b.partial -> 0.38f; i == bars.lastIndex -> 1f; else -> 0.7f }
                    drawRoundRect(color.copy(alpha = a), Offset(cx - bw / 2, h - bh), Size(bw, bh), CornerRadius(minOf(bw / 2, bh / 2)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = 34.dp)) {
            Text(bars.first().date.format(f), fontSize = 10.sp, color = p.text3, modifier = Modifier.weight(1f))
            Text(bars[bars.size / 2].date.format(f), fontSize = 10.sp, color = p.text3, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("oggi", fontSize = 10.sp, color = p.text3, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

/** Passo "rotondo" per le etichette degli assi (1, 2, 5 × 10^n). */
fun niceStep(raw: Double): Double {
    if (raw <= 0) return 1.0
    val exp = kotlin.math.floor(kotlin.math.log10(raw))
    val base = Math.pow(10.0, exp)
    val m = raw / base
    return base * when { m <= 1 -> 1.0; m <= 2 -> 2.0; m <= 5 -> 5.0; else -> 10.0 }
}

/** Ultimi [days] giorni di carico cardiaco come barre (giorni senza FC = assenti, con poca FC = parziali). */
fun loadBars(vm: AppViewModel, days: Int = 28): List<DayBar> {
    val today = LocalDate.now()
    val by = vm.dayActivities.associateBy { it.date }
    return (days - 1 downTo 0).map { k ->
        val d = today.minusDays(k.toLong())
        val a = by[d]
        DayBar(d, a?.takeIf { it.quality != LoadQuality.UNAVAILABLE }?.load, a?.quality == LoadQuality.PARTIAL)
    }
}

@Composable
private fun WeekActiveBars(vm: AppViewModel) {
    val p = LocalPalette.current
    val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val byDate = vm.dayLoads.associateBy { it.date }
    val mx = maxOf(days.maxOfOrNull { byDate[it]?.activeMinutes ?: 0 } ?: 0, 30).toFloat()
    Column {
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val slot = size.width / 7; val bw = slot * 0.5f
            days.forEachIndexed { i, d ->
                val dl = byDate[d]
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(p.field, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2))
                if (dl != null) {
                    val hm = dl.moderateMin / mx * size.height
                    val hv = 2 * dl.vigorousMin / mx * size.height
                    if (hm + hv > 0) {
                        drawRoundRect(zoneColor(Zone.MODERATE), Offset(x, size.height - hm - hv), Size(bw, hm + hv), CornerRadius(bw / 2))
                        if (hv > 0) drawRoundRect(zoneColor(Zone.VIGOROUS), Offset(x, size.height - hv), Size(bw, hv), CornerRadius(bw / 2))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                Text(d.format(DateTimeFormatter.ofPattern("EEEEE", Locale.ITALIAN)).uppercase(), fontSize = 10.sp, color = p.text2,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun StepWeek(values: List<Pair<LocalDate, Double>>, goal: Int) {
    val p = LocalPalette.current
    if (values.isEmpty()) { Caption("Nessun dato sui passi."); return }
    val mx = maxOf(values.maxOf { it.second }, goal.toDouble()).toFloat()
    Column {
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            val n = values.size; val slot = size.width / n; val bw = slot * 0.55f
            val yGoal = size.height * (1 - goal / mx)
            drawLine(Tint.activity.copy(alpha = 0.6f), Offset(0f, yGoal), Offset(size.width, yGoal), strokeWidth = 1.5.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            values.forEachIndexed { i, (_, v) ->
                val h = (v.toFloat() / mx * size.height).coerceAtLeast(3.dp.toPx())
                val c = if (v >= goal) Brush.verticalGradient(listOf(Color(0xFFFFC27A), Color(0xFFFF9F5A))) else Brush.verticalGradient(listOf(Color(0xFFFFC27A).copy(alpha = 0.5f), Color(0xFFFF9F5A).copy(alpha = 0.5f)))
                drawRoundRect(c, Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h), CornerRadius(bw / 2))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            values.forEach { (d, _) ->
                Text(d.format(DateTimeFormatter.ofPattern("EEEEE", Locale.ITALIAN)).uppercase(), fontSize = 10.sp, color = p.text2,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
    }
}
