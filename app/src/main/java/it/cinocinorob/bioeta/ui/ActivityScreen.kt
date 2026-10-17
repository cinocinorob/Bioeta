package it.cinocinorob.bioeta.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
                    load = if (quality == LoadQuality.UNAVAILABLE) 0f else ((dayAct?.load ?: 0.0) / maxOf(target.high, 1.0)).toFloat(),
                )
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RingLegend(Tint.activity, "passi / ${String.format(Locale.ITALY, "%,d", goal)}", act?.let { String.format(Locale.ITALY, "%,d", it.steps) } ?: "—")
                    RingLegend(Tint.good, "min attivi sett. / 150", "$week")
                    RingLegend(Tint.load, "carico / ${target.high.f(0)}", if (quality == LoadQuality.UNAVAILABLE) "—" else dayAct!!.load.f(0))
                }
            }
        }

        // ---------- Carico ----------
        item {
            HealthCard("Carico cardiaco", BioIcons.Pulse, Tint.load, trailing = quality.label, onClick = { onOpenMetric(DETAIL_LOAD) }) {
                when (quality) {
                    LoadQuality.UNAVAILABLE -> {
                        Text("—", style = MaterialTheme.typography.displaySmall, color = p.text3)
                        InlineNotice("Nessuna frequenza cardiaca oggi in Connessione Salute: il carico non è calcolabile. Servono il permesso «Frequenza cardiaca» e la sincronizzazione dell'orologio.", NoticeKind.WARN)
                    }
                    else -> {
                        val st = Fitness.status(dayAct!!.load, target)
                        Row(verticalAlignment = Alignment.Bottom) {
                            BigValue(dayAct.load.f(0), null, size = 38)
                            Spacer(Modifier.width(8.dp))
                            Caption("obiettivo ${target.low.f(0)}–${target.high.f(0)}")
                            Spacer(Modifier.weight(1f))
                            Pill(st.label, when (st) { LoadStatus.IN_RANGE -> Tint.good; LoadStatus.BELOW -> Tint.blue; LoadStatus.ABOVE -> Tint.warn })
                        }
                        TargetGauge(dayAct.load, target.low, target.high)
                        if (quality == LoadQuality.PARTIAL) Caption("Parziale: frequenza cardiaca per ${dayAct.hrMinutes} minuti (meno di 10 ore). Il valore è sottostimato.", Tint.warn)
                        ZoneBar(dayAct.zoneMinutes)
                    }
                }
                val days = vm.dayActivities.filter { it.quality != LoadQuality.UNAVAILABLE }
                if (days.size >= 2) LoadBars(days.map { it.date to it.load }, target.low, target.high)
                Caption("TRIMP di Banister minuto per minuto dalla frequenza cardiaca (sopra il 30% della riserva cardiaca). Non include stime da tipo di attività, calorie o lavoro muscolare. Obiettivo: ${target.basis}.")
            }
        }

        // ---------- Minuti attivi ----------
        item {
            HealthCard("Minuti attivi", BioIcons.Bolt, Tint.good, onClick = { onOpenMetric(DETAIL_ACTIVE) }) {
                Row(verticalAlignment = Alignment.Bottom) {
                    BigValue("$week", "/ 150 min questa settimana", size = 32)
                }
                ProgressBar(week / 150f, listOf(Color(0xFF8FEBC6), Tint.good))
                WeekActiveBars(vm)
                if (dayAct != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("Oggi da FC", "${dayAct.moderateMeasured + 2 * dayAct.vigorousMeasured}", "min", Modifier.weight(1f))
                    StatChip("Oggi stimati", "${dayAct.estimatedShare}", "min", Modifier.weight(1f))
                }
                Caption("OMS 2020: 150–300 minuti a settimana di attività moderata (1 minuto vigoroso vale 2). Misurati: minuti con FC ≥ 40% della riserva cardiaca. Stimati: minuti di allenamenti senza FC, intensità dal tipo di attività (Compendio 2024); yoga, stretching e attività non classificate non sono conteggiati. Ogni minuto conta una sola volta anche se due app registrano lo stesso allenamento.")
            }
        }

        // ---------- Passi ----------
        item {
            HealthCard("Passi", BioIcons.Bolt, Tint.activity, onClick = { onOpenMetric("STEPS") }) {
                val pts = vm.wearable[Metric.STEPS].orEmpty().filter { it.date.isAfter(today.minusDays(7)) }
                val all = pts.map { it.date to it.value } + listOfNotNull(act?.let { today to it.steps.toDouble() })
                StepWeek(all.distinctBy { it.first }.sortedBy { it.first }, goal)
                val avg = vm.wearable[Metric.STEPS].orEmpty().filter { it.date.isAfter(today.minusDays(30)) }.map { it.value }
                if (avg.isNotEmpty()) Caption("Media 30 giorni: ${String.format(Locale.ITALY, "%,d", avg.average().toLong())} passi. Il beneficio sulla mortalità cresce fino a 8–10 mila passi sotto i 60 anni e 6–8 mila sopra (meta-analisi di 15 coorti).")
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

        item {
            HealthCard("Come sono calcolati", BioIcons.Info, Tint.blue) {
                Caption("FCmax = 208 − 0,7 × età (Tanaka 2001, PMID 11153730). Intensità come % della riserva cardiaca: moderata 40–59%, vigorosa ≥ 60% (ACSM, PMID 21694556). FC minuto per minuto con interpolazione solo dei buchi ≤ 5 minuti. Carico: TRIMP di Banister (Morton 1990, PMID 2246166). Minuti attivi: OMS 2020 (PMID 33239350); stime da tipo di attività: Compendio 2024 (PMID 38242596). L'obiettivo di carico giornaliero parte dalla tua media di 4 settimane ed è modulato dal recupero: è una scelta progettuale, non un algoritmo validato.")
            }
        }
    }
}

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

@Composable
fun LoadBars(values: List<Pair<LocalDate, Double>>, low: Double, high: Double) {
    val p = LocalPalette.current
    val mx = maxOf(values.maxOfOrNull { it.second } ?: 0.0, high, 10.0).toFloat()
    Column {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n; val bw = slot * 0.6f
            val yLow = size.height * (1 - low.toFloat() / mx); val yHigh = size.height * (1 - high.toFloat() / mx)
            drawRoundRect(Tint.good.copy(alpha = 0.16f), Offset(0f, yHigh), Size(size.width, (yLow - yHigh).coerceAtLeast(2f)), CornerRadius(6.dp.toPx()))
            values.forEachIndexed { i, (_, v) ->
                val h = (v.toFloat() / mx * size.height).coerceAtLeast(2.dp.toPx())
                val last = i == values.lastIndex
                drawRoundRect(
                    if (last) Brush.verticalGradient(Pillar.load) else Brush.verticalGradient(Pillar.load.map { it.copy(alpha = 0.45f) }),
                    Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h), CornerRadius(bw / 2),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            val f = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
            Text(values.firstOrNull()?.first?.format(f) ?: "", fontSize = 10.sp, color = p.text2, modifier = Modifier.weight(1f))
            Text("oggi", fontSize = 10.sp, color = p.text2)
        }
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
