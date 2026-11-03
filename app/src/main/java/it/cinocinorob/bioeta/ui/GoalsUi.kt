package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.health.Metric
import it.cinocinorob.bioeta.science.Badge
import it.cinocinorob.bioeta.science.Badges
import it.cinocinorob.bioeta.science.DailyAdvice
import it.cinocinorob.bioeta.science.DayKind
import it.cinocinorob.bioeta.science.DayOutcome
import it.cinocinorob.bioeta.science.Effort
import it.cinocinorob.bioeta.science.Game
import it.cinocinorob.bioeta.science.GameLevel
import it.cinocinorob.bioeta.science.GamePath
import it.cinocinorob.bioeta.science.GameState
import it.cinocinorob.bioeta.science.PathDay
import it.cinocinorob.bioeta.science.PauseReason
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun levelColor(l: GameLevel): Color = when (l) {
    GameLevel.BLU -> Tint.lvlBlue
    GameLevel.BRONZO -> Tint.lvlBronze
    GameLevel.ARGENTO -> Tint.lvlSilver
    GameLevel.ORO -> Tint.lvlGold
    GameLevel.PLATINO -> Tint.lvlPlatinum
}

private fun n(v: Int) = String.format(Locale.ITALY, "%,d", v)

private const val GAME_INFO =
    "Ogni giorno hai un obiettivo di passi su misura: il 60° percentile dei tuoi ultimi 10 giorni con l'orologio, tra 3.000 e l'obiettivo per la tua età (9.000 sotto i 60 anni, 7.000 sopra), oltre il quale il beneficio non cresce. Sale da solo finché lo raggiungi e scende se una settimana va male. 30 minuti attivi (vigorosi contano doppio) valgono come obiettivo raggiunto, così bici, nuoto e palestra contano.\n\n" +
        "Ascolta il corpo, come in Gentler Streak: con il recupero basso l'obiettivo diventa più leggero e mancarlo non costa punti; con segni di malessere stanotte il giorno è di pausa. Puoi mettere in pausa il gioco per malattia, infortunio o vacanza: quei giorni non contano.\n\n" +
        "Il percorso attività mostra ogni giorno i tuoi passi rispetto alla tua fascia: sotto la parte bassa hai fatto poco, dentro sei in linea, sopra la linea hai raggiunto l'obiettivo.\n\n" +
        "Punti e livelli: ogni lunedì ricevi 70 punti e ne perdi 10 per ogni giorno mancato. Con almeno 40 punti a fine settimana sali di livello (Blu, Bronzo, Argento, Oro, Platino), sotto scendi di uno. La serie non si interrompe nei giorni di pausa o senza orologio, e un giorno mancato a settimana è coperto da un jolly.\n\n" +
        "Il consiglio del giorno (Riposo, Leggero, Moderato, Intenso) viene dal Recupero confrontato con la tua storia e da quanto è salito il carico: è una regola di BioEtà. L'allenamento guidato dall'HRV migliora alcuni parametri ma non in modo chiaro la prestazione (meta-analisi Düking 2021).\n\n" +
        "Le prove: nelle app la gamification aumenta i passi di circa 500 al giorno rispetto alle stesse app senza giochi (meta-analisi di 36 studi randomizzati, Nishi 2024): un effetto reale ma piccolo. Lo schema dei punti persi viene dallo studio STEP UP (Patel 2019), che ha mostrato anche che l'effetto cala quando il gioco finisce. Un obiettivo adattivo ha funzionato meglio di uno fisso in un piccolo studio (Adams 2013).\n\n" +
        "Se il gioco ti mette ansia o ti spinge ad allenarti quando non stai bene, disattivalo da Profilo: i dati e le valutazioni restano uguali."

private fun kindColor(k: DayKind?, p: Palette): Color = when (k) {
    DayKind.MET, DayKind.LIGHT_MET -> Tint.good
    DayKind.MISSED -> Tint.warn
    DayKind.REST -> Tint.sleep
    DayKind.PENDING -> Tint.activity
    else -> p.field
}

private fun effortColor(e: Effort) = when (e) {
    Effort.REST -> Tint.sleep
    Effort.EASY -> Tint.hrv
    Effort.MODERATE -> Tint.activity
    Effort.HARD -> Tint.load
}

/** Colore dello stato del giorno (come la Home di FitWoody, che cambia colore con i tuoi dati). */
private fun stateColor(o: DayOutcome, paused: Boolean, e: Effort) = when {
    paused || o.goal.rest -> Tint.sleep
    o.met -> Tint.good
    else -> effortColor(e)
}

private fun todayFraction(o: DayOutcome): Float = when {
    o.met -> 1f
    o.goal.target == null || o.goal.target <= 0 -> 0f
    else -> maxOf((o.steps ?: 0) / o.goal.target.toFloat(), (o.activeMinutes ?: 0) / Game.ACTIVE_EQ.toFloat())
}

private fun todayLine(o: DayOutcome): String {
    val t = o.goal.target
    return when {
        o.goal.rest && !o.met -> "Giorno di pausa: non conta per punti e serie."
        o.met && (o.steps ?: 0) < (t ?: 0) -> "Raggiunto con ${o.activeMinutes} minuti attivi."
        o.met -> "Obiettivo raggiunto."
        o.goal.light -> "Giornata leggera: il recupero è basso, l'obiettivo è ridotto."
        o.goal.calibrating -> "Obiettivo provvisorio: si adatta a te dopo 5 giorni con l'orologio."
        else -> "Mancano ${n(((t ?: 0) - (o.steps ?: 0)).coerceAtLeast(0))} passi, oppure ${(Game.ACTIVE_EQ - (o.activeMinutes ?: 0)).coerceAtLeast(0)} minuti attivi."
    }
}

/** Compagno: il cuore di BioEtà con una frase del giorno (come Woody in FitWoody e Yorhart in Gentler Streak). */
@Composable
private fun Companion(vm: AppViewModel, g: GameState, size: Int) {
    val p = LocalPalette.current
    val effort = vm.todayEffort
    val pause = vm.activePause
    val mood = when {
        pause != null || g.today.goal.rest -> Mood.SLEEPY
        g.today.met -> Mood.HAPPY
        else -> moodFor(vm.today.readiness.score, vm.today.readiness.calibrating)
    }
    val bpm = vm.wearable[Metric.RHR]?.lastOrNull()?.value
    Row(verticalAlignment = Alignment.CenterVertically) {
        HeartAvatar(mood, bpm, sizeDp = size)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)).background(p.card).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(DailyAdvice.line(g.today, effort, pause), style = MaterialTheme.typography.bodyMedium, color = p.text)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Consiglio di oggi ", style = MaterialTheme.typography.labelMedium, color = p.text2)
                Pill(if (pause != null) "In pausa" else effort.label, effortColor(if (pause != null) Effort.REST else effort))
            }
        }
    }
}

/** Percorso attività: barre dei passi, fascia personale (obiettivo leggero → obiettivo) e linea dell'obiettivo. */
@Composable
private fun ActivityPath(days: List<PathDay>, height: Int, showLabels: Boolean) {
    val p = LocalPalette.current
    val band = Tint.good.copy(alpha = if (p.dark) 0.32f else 0.26f)
    val goalLine = p.ink(Tint.good)
    val colors = days.map { kindColor(it.kind, p) }
    val maxV = (days.maxOfOrNull { maxOf(it.steps ?: 0, it.goal ?: 0) } ?: 1).coerceAtLeast(1) * 1.12f
    Column {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            val w = size.width / days.size
            fun y(v: Int) = size.height - (v / maxV) * size.height
            // fascia personale
            val top = Path(); val bottom = mutableListOf<Offset>()
            var started = false
            days.forEachIndexed { i, d ->
                val g = d.goal ?: return@forEachIndexed
                val lo = d.low ?: g
                val x = i * w + w / 2
                if (!started) { top.moveTo(x, y(g)); started = true } else top.lineTo(x, y(g))
                bottom += Offset(x, y(lo))
            }
            if (started) {
                bottom.asReversed().forEach { top.lineTo(it.x, it.y) }
                top.close()
                drawPath(top, band)
            }
            // barre
            days.forEachIndexed { i, d ->
                val v = d.steps ?: 0
                val bw = w * 0.42f
                val x = i * w + (w - bw) / 2
                val h = if (d.kind == DayKind.REST && v < 300) size.height * 0.08f else (v / maxV) * size.height
                if (h > 0f) drawRoundRect(colors[i].copy(alpha = if (d.kind == DayKind.PENDING) 0.55f else 1f), Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 2, bw / 2))
            }
            // linea dell'obiettivo
            val gl = Path(); var s = false
            days.forEachIndexed { i, d ->
                val g = d.goal ?: return@forEachIndexed
                val x = i * w + w / 2
                if (!s) { gl.moveTo(x, y(g)); s = true } else gl.lineTo(x, y(g))
            }
            if (s) drawPath(gl, goalLine, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
        }
        if (showLabels) {
            val f = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
            Row(Modifier.fillMaxWidth()) {
                Text(days.first().date.format(f), style = MaterialTheme.typography.labelSmall, color = p.text3, modifier = Modifier.weight(1f))
                Text("oggi", style = MaterialTheme.typography.labelSmall, color = p.text3)
            }
        }
    }
}

@Composable
private fun WeekDots(g: GameState, big: Boolean = false) {
    val p = LocalPalette.current
    val byDate = g.week.days.associateBy { it.date }
    val letters = listOf("L", "M", "M", "G", "V", "S", "D")
    Row(horizontalArrangement = Arrangement.spacedBy(if (big) 8.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
        (0L..6L).forEach { k ->
            val d = g.week.start.plusDays(k)
            val o = byDate[d]
            val sz = if (big) 34.dp else 20.dp
            val c = if (o?.kind == DayKind.PENDING) p.field else kindColor(o?.kind, p)
            Box(
                Modifier.size(sz).clip(CircleShape).background(c)
                    .then(if (o?.kind == DayKind.PENDING) Modifier.border(2.dp, Tint.activity, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (big) Text(letters[k.toInt()], style = MaterialTheme.typography.labelMedium,
                    color = if (c == p.field) p.text2 else Color.White)
            }
        }
    }
}

@Composable
private fun ProgressRing(fraction: Float, color: Color, size: Int, stroke: Int, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val f = animatedValue(fraction.coerceIn(0f, 1f), 900)
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val st = stroke.dp.toPx(); val inset = st / 2
            val arc = Size(this.size.width - st, this.size.height - st)
            drawArc(p.field, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(st))
            if (f > 0f) drawArc(color, -90f, 360f * f, false, Offset(inset, inset), arc, style = Stroke(st, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Riquadro in Home: compagno con il consiglio, obiettivo di oggi, percorso di 14 giorni, settimana e livello. */
@Composable
fun GameCard(vm: AppViewModel, onOpen: () -> Unit) {
    val g = vm.game ?: return
    val p = LocalPalette.current
    LaunchedEffect(g) { vm.syncGame() }
    val o = g.today
    val sc = stateColor(o, vm.activePause != null, vm.todayEffort)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(p.tone(sc, 2.2f), p.tone(sc, 0.7f))))
            .clickable(onClick = onOpen).padding(18.dp).testTag("game_card"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Companion(vm, g, 72)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(todayFraction(o), if (o.met) Tint.good else Tint.activity, 58, 7) {
                Icon(if (o.goal.rest && !o.met) BioIcons.Moon else BioIcons.Trophy, null, tint = if (o.met) p.ink(Tint.good) else p.text2, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (o.goal.target == null || o.goal.rest && !o.met) "Giorno di pausa" else "${n(o.steps ?: 0)} / ${n(o.goal.target)} passi",
                    style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, fontFeatureSettings = TABULAR),
                    color = p.text, maxLines = 1,
                )
                Text(todayLine(o), style = MaterialTheme.typography.bodySmall, color = p.text2, maxLines = 2)
            }
        }
        vm.gameInput?.let { inp -> ActivityPath(GamePath.path(inp, 14), 54, showLabels = false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeekDots(g)
            Spacer(Modifier.weight(1f))
            Pill(g.level.label, levelColor(g.level))
        }
        Text(
            "${g.week.points} punti questa settimana" + (if (g.dayStreak > 0) " · serie di ${g.dayStreak} ${if (g.dayStreak == 1) "giorno" else "giorni"}" else ""),
            style = MaterialTheme.typography.labelMedium, color = p.text2, maxLines = 1,
        )
        if (vm.newBadges.isNotEmpty()) {
            val titles = vm.newBadges.mapNotNull { id -> Badges.all.firstOrNull { it.id == id }?.title }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BioIcons.Trophy, null, tint = p.ink(Tint.lvlGold), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (titles.size == 1) "Nuovo traguardo: ${titles[0]}" else "${titles.size} nuovi traguardi: ${titles[0]} e altri",
                    style = MaterialTheme.typography.labelLarge, color = p.ink(Tint.lvlGold), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun GoalsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = LocalPalette.current
    LaunchedEffect(Unit) { vm.syncGame(); vm.seenNewBadges() }
    val g = vm.game
    val dfmt = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
    var pausing by rememberSaveable { mutableStateOf(false) }
    var badgeOpen by remember { mutableStateOf<Badge?>(null) }
    if (pausing) PauseDialog(vm) { pausing = false }
    badgeOpen?.let { b ->
        val d = vm.badgeDates[b.id]
        AlertDialog(
            onDismissRequest = { badgeOpen = null },
            title = { Text(b.title) },
            text = { Text(b.description + (d?.let { "\n\nSbloccato il ${it.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN))}." } ?: "\n\nAncora da sbloccare.")) },
            confirmButton = { TextButton(onClick = { badgeOpen = null }) { Text("Chiudi") } },
        )
    }
    SubScaffold("Traguardi", onBack) { m ->
        LazyColumn(
            m.fillMaxSize().testTag("goals_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (g == null) {
                item { EmptyState(BioIcons.Trophy, "Ancora nessun dato", "Gli obiettivi partono quando Connessione Salute ha almeno un giorno di passi dall'orologio.", Tint.activity) }
            } else {
                val o = g.today
                val sc = stateColor(o, vm.activePause != null, vm.todayEffort)
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(p.tone(sc, 2.2f), p.tone(sc, 0.7f))))
                            .padding(18.dp).testTag("companion_card"),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Companion(vm, g, 96)
                        val pause = vm.activePause
                        if (pause != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${pause.reason.label}" + (pause.to?.let { " · fino al ${it.format(dfmt)}" } ?: " · finché non riprendi"),
                                    style = MaterialTheme.typography.labelLarge, color = p.text, modifier = Modifier.weight(1f))
                                FilledTonalButton(onClick = { vm.endPause() }) { Text("Riprendi") }
                            }
                        } else OutlinedButton(onClick = { pausing = true }, modifier = Modifier.testTag("pause_button")) { Text("Metti in pausa") }
                    }
                }
                vm.gameInput?.let { inp ->
                    val path = GamePath.path(inp, 30)
                    item {
                        HealthCard("Percorso attività", BioIcons.Bolt, Tint.good, info = GAME_INFO, modifier = Modifier.testTag("path_card")) {
                            ActivityPath(path, 120, showLabels = true)
                            val met = path.count { it.kind == DayKind.MET || it.kind == DayKind.LIGHT_MET }
                            val rest = path.count { it.kind == DayKind.REST }
                            val miss = path.count { it.kind == DayKind.MISSED }
                            Caption("Ultimi 30 giorni: $met con l'obiettivo, $rest di pausa, $miss sotto la tua fascia. La fascia verde è il tuo ritmo; la linea tratteggiata è l'obiettivo.")
                        }
                    }
                }
                item {
                    val lc = levelColor(g.level)
                    HealthCard("Livello ${g.level.label}", BioIcons.Trophy, lc, modifier = Modifier.testTag("level_card")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressRing(g.week.points / Game.START_POINTS.toFloat(), if (g.week.points >= Game.LEVEL_UP) Tint.good else Tint.warn, 92, 10) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${g.week.points}", style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, fontFeatureSettings = TABULAR), color = p.text)
                                    Text("punti", style = MaterialTheme.typography.labelSmall, color = p.text2)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val next = GameLevel.entries.getOrNull(g.level.ordinal + 1)
                                val left = (g.week.points - Game.LEVEL_UP) / Game.DAY_POINTS
                                Text(
                                    when {
                                        g.week.points < Game.LEVEL_UP -> "Questa settimana resti dove sei o scendi di uno: lunedì si riparte con 70 punti."
                                        next == null -> "Sei al livello più alto: con almeno ${Game.LEVEL_UP} punti domenica lo mantieni."
                                        else -> "Con almeno ${Game.LEVEL_UP} punti domenica passi a ${next.label}."
                                    },
                                    style = MaterialTheme.typography.bodyMedium, color = p.text,
                                )
                                if (g.week.points >= Game.LEVEL_UP) Caption(if (left == 0) "Nessun altro giorno mancato è concesso." else "Puoi mancare ancora $left ${if (left == 1) "giorno" else "giorni"}.")
                            }
                        }
                        WeekDots(g, big = true)
                        LevelLadder(g.level)
                    }
                }
                item {
                    HealthCard("Oggi", BioIcons.Bolt, Tint.activity) {
                        Text(if (o.goal.target == null || o.goal.rest && !o.met) "Giorno di pausa" else "${n(o.steps ?: 0)} di ${n(o.goal.target)} passi",
                            style = MaterialTheme.typography.titleLarge, color = p.text)
                        Track(todayFraction(o), if (o.met) Tint.good else Tint.activity)
                        Caption(todayLine(o))
                    }
                }
                item {
                    val r = GamePath.recap(g, vm.dayActivities.associate { d -> d.date to d.activeMinutes },
                        vm.gameInput?.steps ?: emptyMap(), java.time.LocalDate.now())
                    HealthCard("La tua settimana", BioIcons.Sparkle, Tint.brand) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatChip("Obiettivo", "${r.activeDays}", "giorni", Modifier.weight(1f))
                            StatChip("Pausa", "${r.restDays}", "giorni", Modifier.weight(1f))
                            StatChip("Minuti attivi", "${r.minutes}", "", Modifier.weight(1f))
                        }
                        Caption(
                            when {
                                r.prevMinutes == null -> "Il confronto con la settimana scorsa arriva quando c'è una settimana intera di dati."
                                r.minutes >= r.prevMinutes -> "Finora ${r.minutes - r.prevMinutes} minuti attivi in più rispetto agli stessi giorni della settimana scorsa."
                                else -> "Finora ${r.prevMinutes - r.minutes} minuti attivi in meno degli stessi giorni della settimana scorsa: va bene così se il corpo chiedeva riposo."
                            } + (r.stepsAvg?.let { " Passi medi: ${n(it)}" + (r.prevStepsAvg?.let { pv -> " (prima ${n(pv)})" } ?: "") + "." } ?: ""),
                        )
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatChip("Serie", "${g.dayStreak}", "giorni", Modifier.weight(1f))
                        StatChip("Migliore", "${maxOf(g.bestDayStreak, vm.bestStreakSaved)}", "giorni", Modifier.weight(1f))
                        StatChip("Settimane ok", "${g.weekStreak}", "di fila", Modifier.weight(1f))
                    }
                }
                item {
                    Caption(
                        (if (g.jollyUsedThisWeek) "Jolly di questa settimana già usato." else "Jolly disponibile: un giorno mancato questa settimana non interrompe la serie.") +
                            (g.bestSteps?.let { (d, s) -> " Record: ${n(s)} passi il ${d.format(dfmt)}." } ?: ""),
                    )
                }
            }
            val dates = vm.badgeDates
            item { SectionHeader("Traguardi ${dates.size} di ${Badges.all.size}") }
            Badges.all.groupBy { it.group }.forEach { (group, list) ->
                item {
                    HealthCard(group) {
                        list.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { b -> Medal(b, dates[b.id] != null, Modifier.weight(1f)) { badgeOpen = b } }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            item { Caption("I traguardi premiano le soglie con benefici documentati, non gli eccessi. Si disattiva tutto da Profilo.") }
        }
    }
}

/** Medaglia: cerchio dorato se sbloccata, grigio se no; tocco per i dettagli. */
@Composable
private fun Medal(b: Badge, unlocked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp).testTag("badge_${b.id}"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(56.dp).clip(CircleShape)
                .background(if (unlocked) Brush.linearGradient(listOf(p.toneStrong(Tint.lvlGold), p.tone(Tint.lvlGold, 1.2f))) else Brush.linearGradient(listOf(p.field, p.field))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(BioIcons.Trophy, null, tint = if (unlocked) p.ink(Tint.lvlGold) else p.text3, modifier = Modifier.size(26.dp))
        }
        Text(b.title, style = MaterialTheme.typography.labelMedium, color = if (unlocked) p.text else p.text3, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Scala dei livelli: dove sei tra Blu e Platino. */
@Composable
private fun LevelLadder(current: GameLevel) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        GameLevel.entries.forEach { l ->
            val on = l.ordinal <= current.ordinal
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (on) levelColor(l) else p.field))
                Text(l.label, style = MaterialTheme.typography.labelSmall, color = if (l == current) p.text else p.text3, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PauseDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf(PauseReason.ILLNESS) }
    var days by remember { mutableStateOf<Int?>(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Metti in pausa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Caption("Durante la pausa i giorni non contano: niente punti persi e la serie resta com'è.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PauseReason.entries.take(2).forEach { r -> FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r.label) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PauseReason.entries.drop(2).forEach { r -> FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r.label) }) }
                }
                Caption("Per quanto")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "Oggi", 3 to "3 giorni", 7 to "1 settimana").forEach { (d, l) -> FilterChip(selected = days == d, onClick = { days = d }, label = { Text(l) }) }
                }
                FilterChip(selected = days == null, onClick = { days = null }, label = { Text("Finché non riprendo (max 30 giorni)") })
            }
        },
        confirmButton = { TextButton(onClick = { vm.startPause(reason, days); onDismiss() }, modifier = Modifier.testTag("pause_confirm")) { Text("Metti in pausa") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
