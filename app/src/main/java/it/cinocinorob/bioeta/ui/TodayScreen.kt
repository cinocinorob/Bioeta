package it.cinocinorob.bioeta.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.health.connect.client.records.ExerciseSessionRecord
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.science.Fitness
import it.cinocinorob.bioeta.science.LoadStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Pillar {
    val recovery = listOf(Color(0xFF5FD3A0), Color(0xFF2FB68A))
    val sleep = listOf(Color(0xFFA996FF), Color(0xFF7D6BF2))
    val load = listOf(Color(0xFFFFB27A), Color(0xFFFF7E67))
}

/**
 * Quadrante firma di BioEtà: un anello diviso in tre archi — Recupero, Sonno, Carico —
 * con la mascotte al centro. Ogni arco si riempie in proporzione al proprio valore.
 */
@Composable
fun TriDial(recovery: Int?, sleep: Int?, loadFraction: Float?, mood: Mood, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val v1 = animatedValue((recovery ?: 0) / 100f, 900)
    val v2 = animatedValue((sleep ?: 0) / 100f, 1000)
    val v3 = animatedValue((loadFraction ?: 0f).coerceIn(0f, 1f), 1100)
    Box(modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = 20.dp.toPx()
            val inset = st / 2 + 4.dp.toPx()
            val arc = Size(size.width - 2 * inset, size.height - 2 * inset)
            val tl = Offset(inset, inset)
            val gap = 7f
            val seg = 120f - gap
            val specs = listOf(
                Triple(-90f + gap / 2, Pillar.recovery, v1),
                Triple(30f + gap / 2, Pillar.sleep, v2),
                Triple(150f + gap / 2, Pillar.load, v3),
            )
            specs.forEach { (start, colors, v) ->
                drawArc(colors.first().copy(alpha = if (p.dark) 0.18f else 0.16f), start, seg, false, tl, arc, style = Stroke(st, cap = StrokeCap.Round))
                if (v > 0.005f) drawArc(
                    Brush.sweepGradient(listOf(colors[0], colors[1], colors[0])), start, seg * v, false, tl, arc,
                    style = Stroke(st, cap = StrokeCap.Round),
                )
            }
        }
        Sprout(mood, sizeDp = 120)
    }
}

@Composable
private fun PillarLegend(label: String, value: String, colors: List<Color>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(colors.first().copy(alpha = 0.13f)).clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors[1])
    }
}

@Composable
fun TodayScreen(
    vm: AppViewModel,
    modifier: Modifier,
    onGoRecovery: () -> Unit,
    onGoActivity: () -> Unit,
    onGoHealth: () -> Unit,
    onGoBp: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenJournal: () -> Unit,
    onAsk: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenDiagnostics: () -> Unit = {},
) {
    val p = LocalPalette.current
    val t = vm.today
    val profile = vm.profile
    val target = vm.targetLoad
    val load = vm.todayLoad
    val act = vm.todayActivity
    val mood = moodFor(t.readiness.score, t.readiness.calibrating || !vm.hasAnyHealthPermission)
    val (title, msg) = gentleMessage(mood, t.readiness.flags.isNotEmpty())
    val loadFraction = load?.let { (it.load / maxOf(target.high, 1.0)).toFloat() }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)).replaceFirstChar { it.uppercase() },
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = p.text2)
        }

        if (!vm.hasAnyHealthPermission && vm.hcAvailable) item {
            HealthCard("Collega l'orologio", BioIcons.Heart, Tint.heart, onClick = onRequestPermissions) {
                Caption("Concedi l'accesso a Connessione Salute per vedere passi, carico cardiaco, sonno e recupero.")
            }
        } else if (vm.missingRecoveryPermissions) item {
            HealthCard("Nuovi dati disponibili", BioIcons.Bolt, Tint.activity, onClick = onRequestPermissions) {
                Caption("Concedi frequenza cardiaca, allenamenti, calorie e distanza per completare la scheda giornaliera.")
            }
        }

        val lw = vm.lastWearable
        val stale = vm.hasAnyHealthPermission && !vm.loading && lw != null &&
            java.time.Duration.between(lw, java.time.Instant.now()).toHours() >= 14
        val ctx = androidx.compose.ui.platform.LocalContext.current
        if (stale) item {
            HealthCard("L'orologio non sincronizza da ${agoText(lw!!)}", BioIcons.Info, Tint.warn, onClick = { openWatchApp(ctx) }) {
                Caption("Il sonno di stanotte e i dati recenti non sono ancora in Connessione Salute: li scrive l'app Fitbit/Google Health quando sincronizza. Tocca per aprirla e trascina in basso per sincronizzare, poi torna qui. Se succede spesso: Impostazioni → App → Fitbit → Batteria → «Senza restrizioni».")
            }
        }
        if (!stale && vm.hasAnyHealthPermission && !vm.loading && !vm.fitnessLoading && (act == null || act.steps == 0L) && t.night == null) item {
            HealthCard("Non vedo dati dall'orologio", BioIcons.Info, Tint.warn, onClick = onOpenDiagnostics) {
                Caption("Né passi di oggi né sonno di stanotte risultano in Connessione Salute. Tocca per la diagnostica: mostra, per ogni tipo di dato, se c'è il permesso, quanti dati ci sono e quale app li scrive.")
            }
        }

        // ---------- Quadrante ----------
        item {
            Column(
                Modifier.fillMaxWidth().softShadow(p.dark).clip(RoundedCornerShape(32.dp)).background(p.card).padding(vertical = 18.dp, horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TriDial(t.readiness.score, t.sleep?.score, loadFraction, mood)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillarLegend("Recupero", t.readiness.score?.toString() ?: "—", Pillar.recovery, onGoRecovery, Modifier.weight(1f))
                    PillarLegend("Sonno", t.sleep?.score?.toString() ?: "—", Pillar.sleep, onGoRecovery, Modifier.weight(1f))
                    PillarLegend("Carico", load?.load?.f(0) ?: "—", Pillar.load, onGoActivity, Modifier.weight(1f))
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                    Text(msg, fontSize = 14.sp, lineHeight = 19.sp, color = p.text2)
                }
            }
        }
        (t.readiness.flags).forEach { fl ->
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Tint.warn.copy(alpha = 0.14f)).padding(14.dp)) {
                    Icon(BioIcons.Info, null, tint = Tint.warn, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(fl, fontSize = 13.sp, lineHeight = 17.sp, color = p.text)
                }
            }
        }

        // ---------- Carico cardiaco ----------
        item {
            HealthCard("Carico cardiaco", BioIcons.Pulse, Pillar.load[1], onClick = onGoActivity) {
                if (load == null) {
                    Caption(if (vm.fitnessLoading) "Calcolo dalla frequenza cardiaca…" else "Nessun dato di frequenza cardiaca per oggi.")
                } else {
                    val st = Fitness.status(load.load, target)
                    Row(verticalAlignment = Alignment.Bottom) {
                        BigValue(load.load.f(0), null, size = 38)
                        Spacer(Modifier.width(8.dp))
                        Caption("obiettivo ${target.low.f(0)}–${target.high.f(0)}")
                        Spacer(Modifier.weight(1f))
                        Pill(st.label, when (st) { LoadStatus.IN_RANGE -> Tint.good; LoadStatus.BELOW -> Tint.blue; LoadStatus.ABOVE -> Tint.warn })
                    }
                    TargetGauge(load.load, target.low, target.high)
                    Caption("Obiettivo di oggi: ${target.basis}.")
                }
            }
        }

        // ---------- La tua giornata ----------
        item {
            HealthCard("La tua giornata", BioIcons.Bolt, Tint.activity, onClick = onGoActivity) {
                val goal = Fitness.stepGoal(profile.ageAt(LocalDate.now()))
                val steps = act?.steps ?: 0L
                Row(verticalAlignment = Alignment.Bottom) {
                    BigValue(String.format(Locale.ITALY, "%,d", steps), "passi", size = 34)
                    Spacer(Modifier.weight(1f))
                    Caption("obiettivo ${String.format(Locale.ITALY, "%,d", goal)}")
                }
                ProgressBar(steps / goal.toFloat(), listOf(Color(0xFFFFC27A), Color(0xFFFF9F5A)))
                act?.let { HourlyBars(it.hourlySteps, Tint.activity) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip("Minuti attivi", "${load?.activeMinutes ?: 0}", "oggi", Modifier.weight(1f))
                    StatChip("Settimana", "${Fitness.weekActiveMinutes(vm.dayLoads, LocalDate.now())}", "/150 min", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip("Calorie attive", act?.activeKcal?.f(0) ?: "—", "kcal", Modifier.weight(1f))
                    StatChip("Distanza", act?.distanceKm?.f(1) ?: "—", "km", Modifier.weight(1f))
                }
            }
        }

        // ---------- Diario ----------
        item { JournalQuickCard(vm, onOpenJournal) }

        // ---------- Cronologia della giornata ----------
        item { TimelineCard(vm) }

        // ---------- Scorciatoie ----------
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShortcutTile("Pressione", vm.bpSummary?.let { "${it.meanSys.f(0)}/${it.meanDia.f(0)}" } ?: "Registra", Tint.bp, BioIcons.Heart, onGoBp, Modifier.weight(1f))
                ShortcutTile("Età biologica", vm.phenoSeries.lastOrNull()?.result?.phenoAge?.f(1) ?: "—", Tint.bio, BioIcons.Sparkle, onGoHealth, Modifier.weight(1f))
                ShortcutTile("Chiedi a Claude", "Consigli", Tint.sleep, BioIcons.Chat, { onAsk("") }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, colors: List<Color>, height: Int = 12) {
    val p = LocalPalette.current
    val av = animatedValue(fraction.coerceIn(0f, 1f), 900)
    Canvas(Modifier.fillMaxWidth().height(height.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(p.field, cornerRadius = r)
        if (av > 0f) drawRoundRect(Brush.horizontalGradient(colors), size = Size(maxOf(size.height, size.width * av), size.height), cornerRadius = r)
    }
}

/** Indicatore con banda dell'obiettivo (stile Google "target load") e marcatore del carico attuale. */
@Composable
fun TargetGauge(value: Double, low: Double, high: Double) {
    val p = LocalPalette.current
    val maxV = maxOf(high * 1.5, value * 1.1, 10.0)
    Canvas(Modifier.fillMaxWidth().height(30.dp)) {
        val h = 12.dp.toPx(); val y = size.height / 2 - h / 2
        val r = CornerRadius(h / 2)
        drawRoundRect(p.field, Offset(0f, y), Size(size.width, h), r)
        val x0 = (low / maxV * size.width).toFloat(); val x1 = (high / maxV * size.width).toFloat()
        drawRoundRect(Tint.good.copy(alpha = 0.35f), Offset(x0, y), Size((x1 - x0).coerceAtLeast(h), h), r)
        val xv = (value / maxV * size.width).toFloat().coerceIn(h / 2, size.width - h / 2)
        drawRoundRect(Brush.horizontalGradient(Pillar.load), Offset(0f, y), Size(xv, h), r)
        drawCircle(p.card, 11.dp.toPx(), Offset(xv, size.height / 2))
        drawCircle(Pillar.load[1], 7.dp.toPx(), Offset(xv, size.height / 2))
    }
}

@Composable
fun HourlyBars(hourly: List<Long>, color: Color) {
    val p = LocalPalette.current
    val now = LocalTime.now().hour
    val mx = (hourly.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    Column {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val slot = size.width / 24
            val bw = slot * 0.62f
            hourly.forEachIndexed { h, v ->
                val bh = (v / mx * size.height).coerceAtLeast(if (v > 0) 3.dp.toPx() else 2.dp.toPx())
                val c = when { h > now -> p.field; v >= 250 -> color; else -> color.copy(alpha = 0.35f) }
                drawRoundRect(c, Offset(h * slot + (slot - bw) / 2, size.height - bh), Size(bw, bh), CornerRadius(bw / 2))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("0", "6", "12", "18", "24").forEachIndexed { i, l ->
                Text(l, fontSize = 10.sp, color = p.text2, modifier = Modifier.weight(1f), textAlign = if (i == 4) TextAlign.End else TextAlign.Start)
            }
        }
        Caption("Ore con almeno 250 passi: ${hourly.take(now + 1).count { it >= 250 }} di ${now + 1}")
    }
}

@Composable
fun StatChip(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(p.field).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, fontSize = 12.sp, color = p.text2, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
            Text(" $unit", fontSize = 12.sp, color = p.text2, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun ShortcutTile(title: String, value: String, tint: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.softShadow(p.dark).clip(RoundedCornerShape(24.dp)).background(p.card).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = p.text, maxLines = 1)
        Text(title, fontSize = 11.sp, color = p.text2, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Eventi di oggi in ordine cronologico: risveglio, allenamenti, pressione, diario. */
@Composable
fun TimelineCard(vm: AppViewModel) {
    val p = LocalPalette.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    data class Ev(val time: LocalTime, val title: String, val detail: String, val color: Color)
    val evs = buildList {
        vm.today.night?.let { n ->
            add(Ev(n.end.atZone(zone).toLocalTime(), "Risveglio", "${(n.totalSleepMin / 60).f(1)} h di sonno", Pillar.sleep[1]))
        }
        vm.workouts.filter { it.start.atZone(zone).toLocalDate() == today }.forEach { w ->
            add(Ev(w.start.atZone(zone).toLocalTime(), exerciseName(w.type, w.title), "${w.minutes.f(0)} min" +
                (w.samples.takeIf { it.isNotEmpty() }?.let { s -> " · FC media ${s.map { it.bpm }.average().f(0)}" } ?: ""), Pillar.load[1]))
        }
        vm.bpReadings.filter { it.time.toLocalDate() == today }.forEach { r ->
            add(Ev(r.time.toLocalTime(), "Pressione", "${r.sys}/${r.dia} mmHg", Tint.bp))
        }
    }.sortedBy { it.time }
    HealthCard("Cronologia di oggi", BioIcons.Refresh, Tint.blue) {
        if (evs.isEmpty()) Caption("Ancora nessun evento registrato oggi.")
        evs.forEachIndexed { i, e ->
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(18.dp)) {
                    Box(Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(e.color))
                    if (i < evs.lastIndex) Box(Modifier.width(2.dp).height(30.dp).background(p.sep))
                }
                Spacer(Modifier.width(10.dp))
                Text(e.time.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 13.sp, color = p.text2, fontWeight = FontWeight.Bold, modifier = Modifier.width(46.dp))
                Column {
                    Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                    Text(e.detail, fontSize = 13.sp, color = p.text2)
                }
            }
        }
    }
}

fun exerciseName(type: Int, title: String?): String {
    if (!title.isNullOrBlank()) return title
    return when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Corsa"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Corsa su tapis roulant"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Camminata"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Escursionismo"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Bici"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Cyclette"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Nuoto in piscina"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Nuoto in acque libere"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Forza"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Pesi"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Calcio"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Ellittica"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Vogatore"
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Stretching"
        ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "Basket"
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "Tennis"
        ExerciseSessionRecord.EXERCISE_TYPE_SQUASH -> "Squash"
        else -> "Allenamento"
    }
}
