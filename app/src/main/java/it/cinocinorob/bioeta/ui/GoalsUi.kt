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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.science.Badges
import it.cinocinorob.bioeta.science.DayKind
import it.cinocinorob.bioeta.science.DayOutcome
import it.cinocinorob.bioeta.science.Game
import it.cinocinorob.bioeta.science.GameLevel
import it.cinocinorob.bioeta.science.GameState
import java.time.LocalDate
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
        "Ascolta il corpo: con il recupero basso l'obiettivo diventa più leggero e mancarlo non costa punti; con segni di malessere stanotte il giorno è di pausa.\n\n" +
        "Punti e livelli: ogni lunedì ricevi 70 punti e ne perdi 10 per ogni giorno mancato. Con almeno 40 punti a fine settimana sali di livello (Blu, Bronzo, Argento, Oro, Platino), sotto scendi di uno. La serie non si interrompe nei giorni di pausa o senza orologio, e un giorno mancato a settimana è coperto da un jolly.\n\n" +
        "Le prove: nelle app la gamification aumenta i passi di circa 500 al giorno rispetto alle stesse app senza giochi (meta-analisi di 36 studi randomizzati, Nishi 2024): un effetto reale ma piccolo. Lo schema dei punti persi viene dallo studio STEP UP (Patel 2019, +637–920 passi), che ha mostrato anche che l'effetto cala quando il gioco finisce. Un obiettivo adattivo ha funzionato meglio di uno fisso in un piccolo studio (Adams 2013). Le soglie dei punti e dei livelli sono scelte di BioEtà.\n\n" +
        "Se il gioco ti mette ansia o ti spinge ad allenarti quando non stai bene, disattivalo da Profilo: i dati e le valutazioni restano uguali."

private fun dayColor(o: DayOutcome?, p: Palette): Color = when (o?.kind) {
    DayKind.MET, DayKind.LIGHT_MET -> Tint.good
    DayKind.MISSED -> Tint.warn
    DayKind.REST -> Tint.sleep
    else -> p.field
}

/** Settimana in sette pallini: pieno = obiettivo, arancio = mancato, viola = pausa, contorno = oggi in corso. */
@Composable
private fun WeekDots(g: GameState, big: Boolean = false) {
    val p = LocalPalette.current
    val byDate = g.week.days.associateBy { it.date }
    val letters = listOf("L", "M", "M", "G", "V", "S", "D")
    Row(horizontalArrangement = Arrangement.spacedBy(if (big) 8.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
        (0L..6L).forEach { k ->
            val d = g.week.start.plusDays(k)
            val o = byDate[d]
            val sz = if (big) 34.dp else 22.dp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(sz).clip(CircleShape).background(dayColor(o, p))
                        .then(if (o?.kind == DayKind.PENDING) Modifier.border(2.dp, p.brand, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (big) Text(letters[k.toInt()], style = MaterialTheme.typography.labelMedium,
                        color = if (o?.met == true || o?.kind == DayKind.MISSED || o?.kind == DayKind.REST) Color.White else p.text2)
                }
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

private fun todayLine(o: DayOutcome): String {
    val t = o.goal.target
    return when {
        o.goal.rest && !o.met -> "Giorno di pausa: stanotte ci sono segni di malessere, oggi riposa."
        o.met && (o.steps ?: 0) < (t ?: 0) -> "Raggiunto con ${o.activeMinutes} minuti attivi."
        o.met -> "Obiettivo raggiunto."
        o.goal.light -> "Giornata leggera: il recupero è basso, l'obiettivo è ridotto."
        o.goal.calibrating -> "Obiettivo provvisorio: si adatta a te dopo 5 giorni con l'orologio."
        else -> "Mancano ${n(((t ?: 0) - (o.steps ?: 0)).coerceAtLeast(0))} passi (o ${Game.ACTIVE_EQ} minuti attivi)."
    }
}

/** Riquadro in Home: obiettivo di oggi, settimana, livello e serie. */
@Composable
fun GameCard(vm: AppViewModel, onOpen: () -> Unit) {
    val g = vm.game ?: return
    val p = LocalPalette.current
    LaunchedEffect(g) { vm.syncGame() }
    val o = g.today
    val t = o.goal.target
    val lc = levelColor(g.level)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(p.card).clickable(onClick = onOpen)
            .padding(18.dp).testTag("game_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(if (t != null && t > 0) (o.steps ?: 0) / t.toFloat() else if (o.met) 1f else 0f, if (o.met) Tint.good else Tint.activity, 64, 8) {
                Icon(if (o.goal.rest && !o.met) BioIcons.Moon else BioIcons.Trophy, null, tint = if (o.met) p.ink(Tint.good) else p.text2, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Obiettivo di oggi", style = MaterialTheme.typography.labelLarge, color = p.text2)
                Text(
                    if (t == null) "Pausa" else "${n(o.steps ?: 0)} / ${n(t)} passi",
                    style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, fontFeatureSettings = TABULAR),
                    color = p.text, maxLines = 1,
                )
                Text(todayLine(o), style = MaterialTheme.typography.bodySmall, color = p.text2, maxLines = 2)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeekDots(g)
            Spacer(Modifier.weight(1f))
            Pill(g.level.label, lc)
        }
        Text(
            "${g.week.points} punti questa settimana" + (if (g.dayStreak > 0) " · serie di ${g.dayStreak} ${if (g.dayStreak == 1) "giorno" else "giorni"}" else ""),
            style = MaterialTheme.typography.labelMedium, color = p.text2, maxLines = 1,
        )
        if (vm.newBadges.isNotEmpty()) {
            val titles = vm.newBadges.mapNotNull { id -> Badges.all.firstOrNull { it.id == id }?.title }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.tone(Tint.lvlGold, 1.6f)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BioIcons.Trophy, null, tint = p.ink(Tint.lvlGold), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text((if (titles.size == 1) "Nuovo traguardo: " else "Nuovi traguardi: ") + titles.joinToString(", "),
                    style = MaterialTheme.typography.labelLarge, color = p.ink(Tint.lvlGold), maxLines = 2)
            }
        }
    }
}

@Composable
fun GoalsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = LocalPalette.current
    LaunchedEffect(Unit) { vm.syncGame(); vm.seenNewBadges() }
    val g = vm.game
    val dfmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)
    SubScaffold("Traguardi", onBack) { m ->
        LazyColumn(
            m.fillMaxSize().testTag("goals_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (g == null) {
                item { EmptyState(BioIcons.Trophy, "Ancora nessun dato", "Gli obiettivi partono quando Connessione Salute ha almeno un giorno di passi dall'orologio.", Tint.activity) }
            } else {
                item {
                    val lc = levelColor(g.level)
                    HealthCard("Livello ${g.level.label}", BioIcons.Trophy, lc, info = GAME_INFO, modifier = Modifier.testTag("level_card")) {
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
                                        g.week.points < Game.LEVEL_UP -> "Questa settimana non sali: si riparte lunedì con 70 punti."
                                        next == null -> "Sei al livello più alto: con almeno ${Game.LEVEL_UP} punti domenica lo mantieni."
                                        else -> "Con almeno ${Game.LEVEL_UP} punti domenica passi a ${next.label}."
                                    },
                                    style = MaterialTheme.typography.bodyMedium, color = p.text,
                                )
                                if (g.week.points >= Game.LEVEL_UP) Caption(
                                    if (left == 0) "Nessun altro giorno mancato è concesso." else "Puoi mancare ancora $left ${if (left == 1) "giorno" else "giorni"}.",
                                )
                            }
                        }
                        WeekDots(g, big = true)
                        Caption("Verde: obiettivo · arancio: mancato · viola: pausa · grigio: senza orologio o da fare.")
                    }
                }
                item {
                    val o = g.today
                    HealthCard("Oggi", BioIcons.Bolt, Tint.activity) {
                        Text(if (o.goal.target == null) "Giorno di pausa" else "${n(o.steps ?: 0)} di ${n(o.goal.target)} passi",
                            style = MaterialTheme.typography.titleLarge, color = p.text)
                        Track(o.goal.target?.let { (o.steps ?: 0) / it.toFloat() } ?: 0f, if (o.met) Tint.good else Tint.activity)
                        Caption(todayLine(o))
                        o.activeMinutes?.let { Caption("Minuti attivi oggi: $it di ${Game.ACTIVE_EQ} (alternativa ai passi).") }
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
                        (if (g.jollyUsedThisWeek) "Jolly di questa settimana già usato: il prossimo giorno mancato interrompe la serie." else "Jolly disponibile: un giorno mancato questa settimana non interrompe la serie.") +
                            (g.bestSteps?.let { (d, s) -> " Record: ${n(s)} passi il ${d.format(dfmt)}." } ?: ""),
                    )
                }
            }
            val dates = vm.badgeDates
            item { SectionHeader("Traguardi ${dates.size} di ${Badges.all.size}") }
            Badges.all.groupBy { it.group }.forEach { (group, list) ->
                item {
                    HealthCard(group) {
                        list.forEach { b ->
                            val d = dates[b.id]
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("badge_${b.id}")) {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(if (d != null) p.toneStrong(Tint.lvlGold) else p.field),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(BioIcons.Trophy, null, tint = if (d != null) p.ink(Tint.lvlGold) else p.text3, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(b.title, style = MaterialTheme.typography.titleSmall, color = if (d != null) p.text else p.text2)
                                    Text(b.description, style = MaterialTheme.typography.bodySmall, color = p.text2)
                                }
                                if (d != null) Text(d.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)),
                                    style = MaterialTheme.typography.labelMedium, color = p.text2, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
            item { Caption("I traguardi premiano le soglie con benefici documentati, non gli eccessi. Si disattiva tutto da Profilo.") }
        }
    }
}
