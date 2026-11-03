package it.cinocinorob.bioeta.science

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.floor

/*
 * Obiettivi e traguardi (gamification), versione 1.
 *
 * Evidenze:
 *  - Nelle app, la gamification rispetto alle stesse app senza giochi aumenta i passi di 489/giorno (IC 64–914; GRADE
 *    alto ma effetto «trascurabile» secondo gli autori), con piccole riduzioni di peso e grasso: Nishi 2024,
 *    36 RCT, doi:10.1016/j.eclinm.2024.102798.
 *  - STEP UP (Patel 2019, RCT, n = 602, doi:10.1001/jamainternmed.2019.3505): punti e livelli costruiti con l'economia
 *    comportamentale (punti dati in anticipo e persi quando si manca l'obiettivo: avversione alla perdita) aumentano
 *    i passi di 637–920/giorno; l'effetto cala quando il gioco finisce.
 *  - Obiettivi adattivi (percentile dei giorni recenti) battono un obiettivo fisso: +1.130 passi/giorno in un RCT
 *    piccolo (n = 20; Adams 2013, doi:10.1371/journal.pone.0082901).
 *
 * Scelte progettuali (non validate, dichiarate):
 *  - Obiettivo del giorno = 60° percentile dei passi degli ultimi 10 giorni indossati (≥ 5), arrotondato a 250,
 *    tra 3.000 e l'obiettivo per età (oltre il quale il beneficio non cresce: Paluch 2022). Così sale da solo
 *    finché lo raggiungi e scende se una settimana va male.
 *  - Ascolta il corpo (come Gentler Streak): con recupero basso l'obiettivo diventa il 40° percentile; mancarlo non
 *    costa punti. Con segni di malessere dal radar il giorno è di pausa.
 *  - Bici, nuoto e palestra fanno pochi passi: 30 minuti attivi OMS (vigorosi ×2) valgono come obiettivo raggiunto.
 *  - Punti (schema di STEP UP): 70 punti a inizio settimana, −10 per ogni giorno mancato. Con ≥ 40 a fine settimana
 *    si sale di livello, sotto si scende (Blu → Bronzo → Argento → Oro → Platino).
 *  - Serie gentile: i giorni di pausa e senza orologio non la interrompono; un giorno mancato a settimana è coperto
 *    da un jolly.
 *  - Nessun premio oltre le soglie di beneficio (niente medaglie per 20.000 passi o per allenarsi tutti i giorni).
 */

enum class DayKind { MET, LIGHT_MET, MISSED, REST, NO_DATA, PENDING }

enum class GameLevel(val label: String) { BLU("Blu"), BRONZO("Bronzo"), ARGENTO("Argento"), ORO("Oro"), PLATINO("Platino") }

data class DayGoal(val date: LocalDate, val target: Int?, val light: Boolean, val rest: Boolean, val calibrating: Boolean)

data class DayOutcome(val date: LocalDate, val goal: DayGoal, val steps: Int?, val activeMinutes: Int?, val kind: DayKind) {
    val met get() = kind == DayKind.MET || kind == DayKind.LIGHT_MET
}

data class WeekResult(
    val start: LocalDate,
    val points: Int,
    val met: Int,
    val missed: Int,
    /** La settimana è finita (domenica passata). */
    val complete: Boolean,
    /** Settimana con abbastanza giorni valutati per cambiare livello (≥ 4). */
    val rated: Boolean,
    val levelAfter: GameLevel,
    val days: List<DayOutcome>,
)

/** Settimana chiusa e salvata: non viene più ricalcolata (i numeri passati non cambiano in silenzio). */
data class FrozenWeek(val start: LocalDate, val points: Int, val met: Int, val missed: Int, val rated: Boolean, val levelAfter: GameLevel)

data class GameInput(
    val today: LocalDate,
    /** Passi per giorno (oggi compreso, anche parziale). */
    val steps: Map<LocalDate, Int>,
    /** Minuti attivi OMS per giorno (vigorosi ×2). */
    val activeMinutes: Map<LocalDate, Int>,
    /** Punteggio Recupero per giorno. */
    val readiness: Map<LocalDate, Int>,
    /** Obiettivo passi per età (tetto dell'obiettivo adattivo). */
    val ageGoal: Int,
    /** Segni di malessere stanotte (radar): oggi è giorno di pausa. */
    val restToday: Boolean = false,
    val frozen: Map<LocalDate, FrozenWeek> = emptyMap(),
)

data class GameState(
    val today: DayOutcome,
    val level: GameLevel,
    val week: WeekResult,
    val weeks: List<WeekResult>,
    val dayStreak: Int,
    val bestDayStreak: Int,
    val weekStreak: Int,
    val jollyUsedThisWeek: Boolean,
    val bestSteps: Pair<LocalDate, Int>?,
    /** Settimane chiuse da salvare. */
    val toFreeze: List<FrozenWeek>,
)

object Game {
    const val WINDOW = 10
    const val MIN_DAYS = 5
    const val MIN_WORN_STEPS = 300
    const val FLOOR = 3000
    const val LIGHT_FLOOR = 2000
    const val ACTIVE_EQ = 30
    const val START_POINTS = 70
    const val DAY_POINTS = 10
    const val LEVEL_UP = 40
    /** Una settimana si congela 2 giorni dopo la fine (i dati dell'orologio possono arrivare in ritardo). */
    const val FREEZE_AFTER = 2L

    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Quantile con interpolazione lineare (tipo 7 di Hyndman–Fan, quello di R e NumPy). */
    fun quantile(v: List<Int>, q: Double): Double {
        val s = v.sorted()
        if (s.size == 1) return s[0].toDouble()
        val h = (s.size - 1) * q
        val lo = floor(h).toInt(); val hi = ceil(h).toInt()
        return s[lo] + (h - lo) * (s[hi] - s[lo])
    }

    private fun roundUp(x: Double, step: Int = 250) = (ceil(x / step) * step).toInt()

    /** Recupero basso: sotto 50 o nel 30% più basso dei 28 giorni precedenti (con ≥ 14 valori). */
    fun lowRecovery(d: LocalDate, readiness: Map<LocalDate, Int>): Boolean {
        val r = readiness[d] ?: return false
        if (r < 50) return true
        val hist = (1..28).mapNotNull { readiness[d.minusDays(it.toLong())] }
        val pct = Personal.percentile(r, hist) ?: return false
        return pct < 30
    }

    fun goalFor(d: LocalDate, i: GameInput): DayGoal {
        val rest = d == i.today && i.restToday
        val prior = (1..WINDOW).mapNotNull { i.steps[d.minusDays(it.toLong())]?.takeIf { s -> s >= MIN_WORN_STEPS } }
        if (prior.size < MIN_DAYS) {
            return DayGoal(d, minOf(i.ageGoal, Fitness.STEP_HEALTH), light = false, rest = rest, calibrating = true)
        }
        val base = roundUp(quantile(prior, 0.60)).coerceIn(FLOOR, i.ageGoal)
        val light = lowRecovery(d, i.readiness)
        val target = if (light) roundUp(quantile(prior, 0.40)).coerceIn(LIGHT_FLOOR, base) else base
        return DayGoal(d, target, light, rest, calibrating = false)
    }

    fun outcome(d: LocalDate, i: GameInput): DayOutcome {
        val g = goalFor(d, i)
        val st = i.steps[d]
        val am = i.activeMinutes[d]
        val worn = (st ?: 0) >= MIN_WORN_STEPS || (am ?: 0) > 0
        val met = g.target != null && ((st ?: 0) >= g.target || (am ?: 0) >= ACTIVE_EQ)
        val kind = when {
            met -> if (g.light) DayKind.LIGHT_MET else DayKind.MET
            g.rest -> DayKind.REST
            d == i.today -> DayKind.PENDING
            !worn -> DayKind.NO_DATA
            // giornata leggera con recupero basso: fermarsi è la scelta giusta, non si perdono punti
            g.light -> DayKind.REST
            else -> DayKind.MISSED
        }
        return DayOutcome(d, g, st, am, kind)
    }

    private fun up(l: GameLevel) = GameLevel.entries[minOf(l.ordinal + 1, GameLevel.entries.size - 1)]
    private fun down(l: GameLevel) = GameLevel.entries[maxOf(l.ordinal - 1, 0)]

    fun evaluate(i: GameInput): GameState? {
        val firstData = i.steps.filterValues { it >= MIN_WORN_STEPS }.keys.minOrNull() ?: return null
        val firstWeek = weekStart(firstData)
        val thisWeek = weekStart(i.today)
        val weeks = mutableListOf<WeekResult>()
        val freeze = mutableListOf<FrozenWeek>()
        // livello di partenza: l'ultima settimana congelata prima della finestra di dati
        var level = i.frozen.values.filter { it.start.isBefore(firstWeek) }.maxByOrNull { it.start }?.levelAfter ?: GameLevel.BLU
        var w = firstWeek
        while (!w.isAfter(thisWeek)) {
            val days = (0L..6L).map { w.plusDays(it) }.filter { !it.isAfter(i.today) && !it.isBefore(firstData) }.map { outcome(it, i) }
            val frozenW = i.frozen[w]
            val complete = w.plusDays(6).isBefore(i.today)
            if (frozenW != null) {
                level = frozenW.levelAfter
                weeks += WeekResult(w, frozenW.points, frozenW.met, frozenW.missed, true, frozenW.rated, level, days)
            } else {
                val missed = days.count { it.kind == DayKind.MISSED }
                val met = days.count { it.met }
                val points = START_POINTS - DAY_POINTS * missed
                val rated = days.count { it.kind == DayKind.MET || it.kind == DayKind.LIGHT_MET || it.kind == DayKind.MISSED } >= 4
                if (complete && rated) level = if (points >= LEVEL_UP) up(level) else down(level)
                weeks += WeekResult(w, points, met, missed, complete, rated, level, days)
                if (complete && w.plusDays(6 + FREEZE_AFTER).isBefore(i.today)) freeze += FrozenWeek(w, points, met, missed, rated, level)
            }
            w = w.plusWeeks(1)
        }
        val all = weeks.flatMap { it.days }
        val today = all.last { it.date == i.today }

        // Serie gentile, a ritroso: pause e giorni senza orologio non interrompono; un jolly per settimana
        fun streakEnding(list: List<DayOutcome>): Pair<Int, Boolean> {
            var n = 0
            val jolly = HashSet<LocalDate>()
            var jollyThisWeek = false
            for (o in list.asReversed()) {
                when (o.kind) {
                    DayKind.MET, DayKind.LIGHT_MET -> n++
                    DayKind.PENDING, DayKind.REST, DayKind.NO_DATA -> Unit
                    DayKind.MISSED -> {
                        val ws = weekStart(o.date)
                        if (ws in jolly) return n to jollyThisWeek
                        jolly += ws
                        if (ws == thisWeek) jollyThisWeek = true
                    }
                }
            }
            return n to jollyThisWeek
        }
        val (streak, jollyNow) = streakEnding(all)
        // migliore serie: rifatta in avanti con le stesse regole
        var best = 0; var cur = 0
        val jollyUsed = HashSet<LocalDate>()
        for (o in all) {
            when (o.kind) {
                DayKind.MET, DayKind.LIGHT_MET -> { cur++; best = maxOf(best, cur) }
                DayKind.MISSED -> {
                    val ws = weekStart(o.date)
                    if (ws in jollyUsed) { cur = 0; jollyUsed.clear() } else jollyUsed += ws
                }
                else -> Unit
            }
        }
        val weekStreak = weeks.filter { it.complete && it.rated }.asReversed().takeWhile { it.points >= LEVEL_UP }.size
        val bestSteps = i.steps.maxByOrNull { it.value }?.takeIf { it.value >= MIN_WORN_STEPS }?.toPair()
        return GameState(today, level, weeks.last(), weeks, streak, maxOf(best, streak), weekStreak, jollyNow, bestSteps, freeze)
    }

    /** Minuti attivi OMS per settimana (lunedì → domenica). */
    fun weekMinutes(activeMinutes: Map<LocalDate, Int>): Map<LocalDate, Int> =
        activeMinutes.entries.groupBy { weekStart(it.key) }.mapValues { e -> e.value.sumOf { it.value } }
}

// ---------------- Traguardi ----------------

data class Badge(val id: String, val title: String, val description: String, val group: String)

data class BadgeContext(
    val game: GameState?,
    val weekMinutes: Map<LocalDate, Int>,
    val steps30Mean: Double?,
    val sleepRegularity: Double?,
    val journalStreak: Int,
    val bpProtocolComplete: Boolean,
    val labPanels: Int,
    val bioAge: Boolean,
    val experimentsDone: Int,
    val ecgCount: Int,
)

object Badges {
    val all = listOf(
        Badge("first_goal", "Primo obiettivo", "Raggiungi l'obiettivo del giorno per la prima volta.", "Movimento"),
        Badge("full_week", "Settimana piena", "Obiettivo raggiunto in tutti i giorni valutati di una settimana (almeno 5).", "Movimento"),
        Badge("streak_14", "Due settimane di fila", "Serie gentile di 14 giorni.", "Movimento"),
        Badge("streak_30", "Un mese di fila", "Serie gentile di 30 giorni.", "Movimento"),
        Badge("listen_body", "Ascolta il corpo", "Con il recupero basso hai fatto una giornata leggera invece di spingere.", "Recupero"),
        Badge("who_150", "150 minuti", "Una settimana con 150 minuti attivi: la raccomandazione OMS.", "Movimento"),
        Badge("who_300", "300 minuti", "Una settimana con 300 minuti attivi: il livello OMS dei benefici aggiuntivi.", "Movimento"),
        Badge("steps_7k", "Media 7.000", "Media di 7.000 passi negli ultimi 30 giorni, la soglia associata ai maggiori benefici.", "Movimento"),
        Badge("lvl_argento", "Livello Argento", "Due settimane riuscite a partire da Blu.", "Livelli"),
        Badge("lvl_oro", "Livello Oro", "Tre settimane riuscite a partire da Blu.", "Livelli"),
        Badge("lvl_platino", "Livello Platino", "Il livello più alto: mantenerlo conta più che raggiungerlo.", "Livelli"),
        Badge("weeks_4", "Un mese di settimane", "4 settimane riuscite di fila.", "Livelli"),
        Badge("weeks_12", "Una stagione", "12 settimane riuscite di fila.", "Livelli"),
        Badge("sleep_regular", "Sonno regolare", "Indice di regolarità del sonno ≥ 81, la mediana della UK Biobank.", "Sonno"),
        Badge("journal_7", "Diario di una settimana", "Diario compilato 7 sere di fila.", "Diario"),
        Badge("journal_30", "Diario di un mese", "Diario compilato 30 sere di fila.", "Diario"),
        Badge("bp_protocol", "Pressione misurata bene", "Protocollo ESH completo: 3 giorni e 12 misure, mattino e sera.", "Controlli"),
        Badge("first_lab", "Primo referto", "Un'analisi del sangue in archivio.", "Controlli"),
        Badge("bio_age", "Età biologica", "Prima età biologica calcolata da un prelievo completo.", "Controlli"),
        Badge("ecg_first", "Primo ECG", "Un ECG in archivio.", "Controlli"),
        Badge("experiment", "Scienziato di te stesso", "Un esperimento personale concluso.", "Diario"),
    )

    /** Traguardi soddisfatti adesso (la data del primo sblocco la conserva l'app). */
    fun satisfied(c: BadgeContext): Set<String> = buildSet {
        val g = c.game
        if (g != null) {
            val days = g.weeks.flatMap { it.days }
            if (days.any { it.met }) add("first_goal")
            if (g.weeks.any { wk -> val v = wk.days.filter { it.kind != DayKind.NO_DATA && it.kind != DayKind.PENDING }; v.size >= 5 && v.all { it.met || it.kind == DayKind.REST } && v.count { it.met } >= 5 }) add("full_week")
            if (g.bestDayStreak >= 14) add("streak_14")
            if (g.bestDayStreak >= 30) add("streak_30")
            if (days.any { it.goal.light && (it.kind == DayKind.LIGHT_MET || it.kind == DayKind.REST) }) add("listen_body")
            val maxLevel = g.weeks.maxOfOrNull { it.levelAfter.ordinal } ?: 0
            if (maxLevel >= GameLevel.ARGENTO.ordinal) add("lvl_argento")
            if (maxLevel >= GameLevel.ORO.ordinal) add("lvl_oro")
            if (maxLevel >= GameLevel.PLATINO.ordinal) add("lvl_platino")
            if (g.weekStreak >= 4) add("weeks_4")
            if (g.weekStreak >= 12) add("weeks_12")
        }
        if (c.weekMinutes.values.any { it >= 150 }) add("who_150")
        if (c.weekMinutes.values.any { it >= 300 }) add("who_300")
        if ((c.steps30Mean ?: 0.0) >= Fitness.STEP_HEALTH) add("steps_7k")
        if ((c.sleepRegularity ?: 0.0) >= 81.0) add("sleep_regular")
        if (c.journalStreak >= 7) add("journal_7")
        if (c.journalStreak >= 30) add("journal_30")
        if (c.bpProtocolComplete) add("bp_protocol")
        if (c.labPanels > 0) add("first_lab")
        if (c.bioAge) add("bio_age")
        if (c.ecgCount > 0) add("ecg_first")
        if (c.experimentsDone > 0) add("experiment")
    }
}
