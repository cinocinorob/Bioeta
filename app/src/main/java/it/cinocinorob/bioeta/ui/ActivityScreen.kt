package it.cinocinorob.bioeta.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    Zone.VERY_LIGHT -> Color(0xFFD9D4CC)
    Zone.LIGHT -> Color(0xFF9FD8C0)
    Zone.MODERATE -> Color(0xFFFFCB6B)
    Zone.VIGOROUS -> Color(0xFFFF9466)
    Zone.MAXIMAL -> Color(0xFFF2617A)
}

/** Tre anelli concentrici: passi, minuti attivi della settimana, carico di oggi. */
@Composable
fun ActivityRings(steps: Float, minutes: Float, load: Float) {
    val anims = listOf(
        animatedValue(steps.coerceIn(0f, 1.5f), 900), animatedValue(minutes.coerceIn(0f, 1.5f), 1050), animatedValue(load.coerceIn(0f, 1.5f), 1200),
    )
    val colors = listOf(
        listOf(Color(0xFFFFC27A), Color(0xFFFF9F5A)),
        listOf(Color(0xFF6ED8B0), Color(0xFF2FB68A)),
        listOf(Color(0xFFFFA07A), Color(0xFFFF6F61)),
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
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
            Text(label, fontSize = 11.sp, color = p.text2, fontWeight = FontWeight.Bold)
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
fun ActivityScreen(vm: AppViewModel, modifier: Modifier, onRequestPermissions: () -> Unit) {
    val p = LocalPalette.current
    val today = LocalDate.now()
    val age = vm.profile.ageAt(today)
    val goal = Fitness.stepGoal(age)
    val act = vm.todayActivity
    val load = vm.todayLoad
    val target = vm.targetLoad
    val week = Fitness.weekActiveMinutes(vm.dayLoads, today)

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { LargeHeader("Attività", "Movimento e allenamento") }
        if (vm.fitnessLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Tint.activity) }
        if (age == null) item {
            HealthCard("Serve la data di nascita", BioIcons.Person, Tint.body) { Caption("Il carico cardiaco usa la frequenza massima stimata dall'età: inseriscila nel Profilo.") }
        }
        if (vm.missingRecoveryPermissions) item {
            HealthCard("Concedi i nuovi permessi", BioIcons.Bolt, Tint.activity, onClick = onRequestPermissions) {
                Caption("Frequenza cardiaca, allenamenti, calorie e distanza.")
            }
        }

        // ---------- Anelli ----------
        item {
            Row(
                Modifier.fillMaxWidth().softShadow(p.dark).clip(RoundedCornerShape(30.dp)).background(p.card).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActivityRings(
                    steps = (act?.steps ?: 0L) / goal.toFloat(),
                    minutes = week / 150f,
                    load = ((load?.load ?: 0.0) / maxOf(target.high, 1.0)).toFloat(),
                )
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RingLegend(Color(0xFFFF9F5A), "passi / ${String.format(Locale.ITALY, "%,d", goal)}", String.format(Locale.ITALY, "%,d", act?.steps ?: 0L))
                    RingLegend(Color(0xFF2FB68A), "min attivi sett. / 150", "$week")
                    RingLegend(Color(0xFFFF6F61), "carico / ${target.high.f(0)}", load?.load?.f(0) ?: "—")
                }
            }
        }

        // ---------- Carico ----------
        item {
            HealthCard("Carico cardiaco · 4 settimane", BioIcons.Pulse, Pillar.load[1]) {
                val days = vm.dayLoads
                if (days.isEmpty()) Caption("In attesa dei dati di frequenza cardiaca.") else {
                    LoadBars(days.map { it.date to it.load }, target.low, target.high)
                    val chronic = days.filter { it.date.isBefore(today) && it.coveredMinutes > 600 }.map { it.load }
                    if (chronic.size >= 7) Caption("Carico medio giornaliero: ${chronic.average().f(0)} · obiettivo di oggi ${target.low.f(0)}–${target.high.f(0)} (${target.basis})")
                    load?.let { ZoneBar(it.zoneMinutes) }
                }
                Caption("Carico = TRIMP di Banister calcolato minuto per minuto dalla frequenza cardiaca, sopra il 30% della riserva cardiaca. Le zone seguono le intensità ACSM.")
            }
        }

        // ---------- Minuti attivi ----------
        item {
            HealthCard("Minuti attivi", BioIcons.Bolt, Tint.good) {
                Row(verticalAlignment = Alignment.Bottom) {
                    BigValue("$week", "/ 150 min questa settimana", size = 32)
                }
                ProgressBar(week / 150f, listOf(Color(0xFF8FE3C2), Color(0xFF2FB68A)))
                WeekActiveBars(vm)
                Caption("OMS 2020: 150–300 minuti a settimana di attività moderata o 75–150 vigorosa (1 minuto vigoroso conta come 2), più esercizi di forza almeno 2 giorni a settimana.")
            }
        }

        // ---------- Passi ----------
        item {
            HealthCard("Passi", BioIcons.Bolt, Tint.activity) {
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
        item { SectionLabel("Allenamenti recenti") }
        if (vm.workouts.isEmpty()) item {
            Caption("Nessun allenamento negli ultimi 14 giorni. Avvia un esercizio dall'orologio per vedere qui durata, frequenza cardiaca, zone e carico.")
        }
        items(vm.workouts.take(12), key = { it.id }) { w -> WorkoutCard(w, vm) }

        item {
            HealthCard("Come sono calcolati", BioIcons.Info, Tint.blue) {
                Caption("FCmax = 208 − 0,7 × età (Tanaka 2001, PMID 11153730). Intensità come % della riserva cardiaca: moderata 40–59%, vigorosa 60–89% (ACSM, PMID 21694556). Carico: TRIMP di Banister (Morton 1990, PMID 2246166). Minuti attivi e obiettivo settimanale: OMS 2020 (PMID 33239350). L'obiettivo di carico giornaliero parte dalla tua media di 4 settimane ed è modulato dalla prontezza: è una scelta progettuale, non un algoritmo validato.")
            }
        }
    }
}

@Composable
private fun WorkoutCard(w: HealthRepo.Workout, vm: AppViewModel) {
    val p = LocalPalette.current
    val age = vm.profile.ageAt(LocalDate.now()) ?: 45.0
    val zone = ZoneId.systemDefault()
    val rest = vm.restHrRef
    val avg = w.samples.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()
    val mx = w.samples.maxOfOrNull { it.bpm }
    val male = vm.profile.sex == Sex.M
    val trimp = avg?.let { a -> w.minutes * Fitness.trimpMinute(Fitness.hrr(a, rest, Fitness.hrMax(age)), male) }
    Column(
        Modifier.fillMaxWidth().softShadow(p.dark).clip(RoundedCornerShape(26.dp)).background(p.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(Pillar.load)), contentAlignment = Alignment.Center) {
                Icon(BioIcons.Bolt, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(exerciseName(w.type, w.title), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                Text(w.start.atZone(zone).format(DateTimeFormatter.ofPattern("EEEE d MMM, HH:mm", Locale.ITALIAN)), fontSize = 12.sp, color = p.text2)
            }
            trimp?.let { Pill("carico ${it.f(0)}", Pillar.load[1]) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("Durata", w.minutes.f(0), "min", Modifier.weight(1f))
            StatChip("FC media", avg?.f(0) ?: "—", "bpm", Modifier.weight(1f))
            StatChip("FC max", mx?.f(0) ?: "—", "bpm", Modifier.weight(1f))
        }
        if (w.samples.isNotEmpty()) ZoneBar(Fitness.zoneMinutes(w.samples, rest, age))
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
