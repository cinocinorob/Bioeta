package it.cinocinorob.bioeta.science

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/*
 * Riepiloghi ispirati a Wellness Project (Fit Score, Sedentary Patterns, Month in Review, Trophy case, obiettivi con
 * data prevista), rifatti con regole dichiarate. Il punteggio del giorno NON è un algoritmo validato: è una media
 * trasparente di punteggi che BioEtà mostra già, e ogni spicchio si vede da solo.
 */

// ---------------- Punteggio del giorno ----------------

enum class DayPart(val label: String) { RECOVERY("Recupero"), SLEEP("Sonno"), MOVE("Movimento"), TRAIN("Allenamento"), FUEL("Alimentazione"), MIND("Benessere") }

data class PartScore(val part: DayPart, val score: Int, val note: String)

data class DayScore(val score: Int?, val parts: List<PartScore>, val missing: List<DayPart>)

object DayScoreCalc {
    const val MIN_PARTS = 3

    /**
     * @param move frazione dell'obiettivo del giorno (passi o minuti attivi) già raggiunta
     * @param load carico cardiaco di oggi; [low]–[high] la fascia del tuo carico abituale
     * @param restAdvised giornata di riposo consigliata (recupero basso, malessere, pausa): poco carico è giusto
     * @param fuel frazione dei riferimenti alimentari rispettati nell'ultimo giorno completo (0–1)
     * @param mood umore ed energia di oggi dal diario (1–5)
     */
    fun compute(
        recovery: Int?, sleep: Int?, move: Double?, load: Double?, low: Double?, high: Double?, restAdvised: Boolean,
        fuel: Double?, mood: Int?, energy: Int?,
    ): DayScore {
        val parts = mutableListOf<PartScore>()
        recovery?.let { parts += PartScore(DayPart.RECOVERY, it.coerceIn(0, 100), "Punteggio Recupero") }
        sleep?.let { parts += PartScore(DayPart.SLEEP, it.coerceIn(0, 100), "Punteggio Sonno") }
        move?.let { parts += PartScore(DayPart.MOVE, (it * 100).toInt().coerceIn(0, 100), "Quanto dell'obiettivo di oggi") }
        if (load != null && low != null && high != null && high > 0) {
            val s = when {
                restAdvised && load <= high -> 100
                load in low..high -> 100
                load < low -> (100 * load / low).toInt()
                // sopra la tua fascia: non è un errore, ma il punteggio scende piano (come l'«overreaching» di Gentler Streak)
                else -> (100 - 50 * (load - high) / high).toInt().coerceAtLeast(50)
            }
            parts += PartScore(DayPart.TRAIN, s.coerceIn(0, 100), if (load > high) "Carico sopra la tua fascia" else "Carico rispetto alla tua fascia")
        }
        fuel?.let { parts += PartScore(DayPart.FUEL, (it * 100).toInt().coerceIn(0, 100), "Riferimenti rispettati nell'ultimo giorno completo") }
        val m = listOfNotNull(mood, energy)
        if (m.isNotEmpty()) parts += PartScore(DayPart.MIND, ((m.average() - 1) / 4 * 100).toInt().coerceIn(0, 100), "Umore ed energia nel diario")
        val missing = DayPart.entries.filter { p -> parts.none { it.part == p } }
        val score = if (parts.size >= MIN_PARTS) parts.map { it.score }.average().toInt() else null
        return DayScore(score, parts, missing)
    }
}

// ---------------- Momenti sedentari ----------------

data class SedentaryPattern(
    /** Giorno della settimana in cui ti muovi meno (null se non c'è un giorno ricorrente). */
    val weekday: DayOfWeek?,
    val weekdayMedian: Int?,
    val overallMedian: Int?,
    /** Inizio della fascia di 2 ore in cui ti muovi meno (null se non ricorrente). */
    val hour: Int?,
    /** Quota dei giorni in cui in quella fascia hai fatto meno di 250 passi. */
    val hourShare: Double?,
    val days: Int,
)

/**
 * Come «Sedentary Patterns» di Wellness Project: cerca un giorno della settimana (56 giorni) e una fascia di 2 ore
 * (28 giorni, ore 8–20) in cui ti muovi meno in modo ricorrente. Nessun timer: avvisa solo quando sei in quella fascia.
 * Interrompere la sedentarietà con brevi pause attive riduce glicemia (SMD −0,54) e insulina (−0,56) dopo i pasti
 * (meta-analisi di 37 studi, Loh 2020, doi:10.1007/s40279-019-01183-w). Le soglie (75% della mediana, 250 passi/2 h,
 * 60% dei giorni) sono scelte di BioEtà.
 */
object Sedentary {
    const val WINDOW_STEPS = 250
    const val MIN_SHARE = 0.60

    fun pattern(daily: Map<LocalDate, Int>, hourly: Map<LocalDate, IntArray>, today: LocalDate): SedentaryPattern {
        val d56 = daily.filterKeys { it.isBefore(today) && !it.isBefore(today.minusDays(56)) }.filterValues { it >= 1000 }
        val overall = d56.values.takeIf { it.size >= 28 }?.let { median(it.map(Int::toDouble)) }
        var wd: DayOfWeek? = null; var wdMed: Int? = null
        if (overall != null) {
            val byDay = d56.entries.groupBy { it.key.dayOfWeek }.filterValues { it.size >= 5 }
            val cand = byDay.mapValues { (_, l) -> median(l.map { it.value.toDouble() }) to l.count { it.value < overall } / l.size.toDouble() }
                .filter { (_, v) -> v.first <= 0.75 * overall && v.second >= MIN_SHARE }
                .minByOrNull { it.value.first }
            if (cand != null) { wd = cand.key; wdMed = cand.value.first.toInt() }
        }
        val h28 = hourly.filter { (d, a) -> d.isBefore(today) && !d.isBefore(today.minusDays(28)) && a.size == 24 && a.sum() >= 1000 }
        var hr: Int? = null; var share: Double? = null
        if (h28.size >= 14) {
            val best = (8..19).map { h -> h to h28.values.count { a -> a[h] + a[h + 1] < WINDOW_STEPS } / h28.size.toDouble() }
                .filter { it.second >= MIN_SHARE }
                .maxByOrNull { it.second }
            if (best != null) { hr = best.first; share = best.second }
        }
        return SedentaryPattern(wd, wdMed, overall?.toInt(), hr, share, h28.size)
    }

    /** Avviso solo quando adesso sei nella fascia abituale e finora ti sei mosso poco. */
    fun nudgeNow(p: SedentaryPattern, hourNow: Int, todayHourly: List<Long>): Boolean {
        val h = p.hour ?: return false
        if (hourNow != h && hourNow != h + 1) return false
        val sofar = todayHourly.getOrElse(h) { 0L } + if (hourNow == h + 1) todayHourly.getOrElse(h + 1) { 0L } else 0L
        return sofar < WINDOW_STEPS
    }

    private fun median(v: List<Double>): Double { val s = v.sorted(); val n = s.size; return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2 }
}

// ---------------- Mese in rassegna e record ----------------

data class MonthLine(val label: String, val now: Double?, val before: Double?, val unit: String, val higherBetter: Boolean?, val decimals: Int = 0)

data class MonthReview(val month: YearMonth, val days: Int, val lines: List<MonthLine>)

data class PersonalRecord(val id: String, val label: String, val value: Double, val unit: String, val date: LocalDate, val decimals: Int = 0)

object MonthReviewCalc {
    /** Medie del mese (fino a oggi) contro lo stesso numero di giorni del mese precedente. */
    fun review(
        today: LocalDate, steps: Map<LocalDate, Double>, sleepMin: Map<LocalDate, Double>, rhr: Map<LocalDate, Double>,
        hrv: Map<LocalDate, Double>, activeMin: Map<LocalDate, Int>, weight: Map<LocalDate, Double>, goalsMet: Set<LocalDate>,
        month: YearMonth = YearMonth.from(today),
    ): MonthReview {
        val start = month.atDay(1)
        val end = minOf(today, month.atEndOfMonth())
        val n = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
        val pStart = month.minusMonths(1).atDay(1)
        val pEnd = minOf(pStart.plusDays(n - 1L), month.minusMonths(1).atEndOfMonth())
        fun inR(d: LocalDate, a: LocalDate, b: LocalDate) = !d.isBefore(a) && !d.isAfter(b)
        fun avg(m: Map<LocalDate, Double>, a: LocalDate, b: LocalDate, min: Double = Double.NEGATIVE_INFINITY) =
            m.filter { inR(it.key, a, b) && it.value > min }.values.takeIf { it.size >= 3 }?.average()
        fun sumWeek(a: LocalDate, b: LocalDate) = activeMin.filterKeys { inR(it, a, b) }.takeIf { it.size >= 5 }?.let { it.values.sum() * 7.0 / it.size }
        fun lastW(a: LocalDate, b: LocalDate) = weight.filterKeys { inR(it, a, b) }.maxByOrNull { it.key }?.value
        val lines = listOf(
            MonthLine("Passi al giorno", avg(steps, start, end, 300.0), avg(steps, pStart, pEnd, 300.0), "", true),
            MonthLine("Minuti attivi a settimana", sumWeek(start, end), sumWeek(pStart, pEnd), "min", true),
            MonthLine("Sonno per notte", avg(sleepMin, start, end), avg(sleepMin, pStart, pEnd), "min", null),
            MonthLine("FC a riposo", avg(rhr, start, end), avg(rhr, pStart, pEnd), "bpm", false, 1),
            MonthLine("HRV", avg(hrv, start, end), avg(hrv, pStart, pEnd), "ms", true),
            MonthLine("Peso (ultima misura)", lastW(start, end), lastW(pStart, pEnd), "kg", null, 1),
            MonthLine("Giorni con l'obiettivo", goalsMet.count { inR(it, start, end) }.toDouble(), goalsMet.count { inR(it, pStart, pEnd) }.toDouble(), "", true),
        )
        return MonthReview(month, n, lines)
    }

    /**
     * Record personali sui dati disponibili (solo grandezze in cui «di più» o «di meno» è chiaramente meglio, e senza
     * premiare gli eccessi): miglior giorno di passi, miglior settimana di minuti attivi (lun–dom), FC a riposo media
     * di 7 giorni più bassa, VO₂max più alto, serie più lunga.
     */
    fun records(steps: Map<LocalDate, Double>, activeMin: Map<LocalDate, Int>, rhr: Map<LocalDate, Double>, vo2: Map<LocalDate, Double>, bestStreak: Int, today: LocalDate): List<PersonalRecord> {
        val out = mutableListOf<PersonalRecord>()
        steps.filterKeys { it.isBefore(today) }.maxByOrNull { it.value }?.let { out += PersonalRecord("steps_day", "Giorno con più passi", it.value, "passi", it.key) }
        Game.weekMinutes(activeMin.filterKeys { Game.weekStart(it).plusDays(6).isBefore(today) }).maxByOrNull { it.value }
            ?.let { out += PersonalRecord("week_min", "Settimana più attiva", it.value.toDouble(), "min attivi", it.key) }
        val r = rhr.toSortedMap()
        if (r.size >= 7) {
            val keys = r.keys.toList()
            val best = (6 until keys.size).map { i -> keys[i] to (i - 6..i).map { r.getValue(keys[it]) }.average() }.minByOrNull { it.second }
            best?.let { out += PersonalRecord("rhr7", "FC a riposo più bassa (media 7 giorni)", it.second, "bpm", it.first, 1) }
        }
        vo2.maxByOrNull { it.value }?.let { out += PersonalRecord("vo2", "VO₂max più alto", it.value, "mL/kg/min", it.key, 1) }
        if (bestStreak > 0) out += PersonalRecord("streak", "Serie più lunga", bestStreak.toDouble(), "giorni", today)
        return out
    }

    /** Unisce i record calcolati con quelli salvati: un record migliora e non si perde quando i dati escono dalla finestra. */
    fun merge(saved: Map<String, PersonalRecord>, fresh: List<PersonalRecord>): Map<String, PersonalRecord> {
        val out = saved.toMutableMap()
        for (r in fresh) {
            val old = out[r.id]
            val lowerBetter = r.id == "rhr7"
            if (old == null || (if (lowerBetter) r.value < old.value - 1e-9 else r.value > old.value + 1e-9)) out[r.id] = r
        }
        return out
    }
}

// ---------------- Obiettivo di peso con data prevista ----------------

data class WeightGoal(val target: Double, val startWeight: Double, val startDate: LocalDate)

enum class GoalState(val label: String) { ON_TRACK("In linea"), STALLED("Fermo"), WRONG_WAY("Nella direzione opposta"), REACHED("Raggiunto"), NEED_DATA("Servono più pesate") }

data class WeightProjection(
    val state: GoalState,
    val current: Double?,
    /** kg a settimana (negativo = in calo). */
    val slopeWeek: Double?,
    val projected: LocalDate?,
    /** Data prevista con il limite favorevole e sfavorevole dell'intervallo al 95% della pendenza. */
    val projectedEarly: LocalDate?,
    val projectedLate: LocalDate?,
    /** 0–1: quanto della strada è fatta. */
    val progress: Double,
)

/**
 * Come gli obiettivi di Wellness Project: data prevista al ritmo attuale, «fermo» se il trend è piatto, tappe al 25/50/75/100%.
 * Ritmo = pendenza OLS delle pesate degli ultimi 28 giorni (≥ 4 pesate su ≥ 14 giorni); «fermo» sotto 0,05 kg/settimana.
 * Il peso oscilla di giorno in giorno (acqua, glicogeno, sale): la data è un'indicazione, non una promessa.
 */
object WeightGoalCalc {
    fun project(g: WeightGoal, weights: Map<LocalDate, Double>, today: LocalDate): WeightProjection {
        val w = weights.filterKeys { !it.isAfter(today) && it.isAfter(today.minusDays(28)) }.toSortedMap()
        val current = weights.filterKeys { !it.isAfter(today) }.maxByOrNull { it.key }?.value
        val total = g.target - g.startWeight
        val progress = if (current == null || abs(total) < 1e-9) 0.0 else ((current - g.startWeight) / total).coerceIn(0.0, 1.0)
        val reached = current != null && (if (total < 0) current <= g.target else current >= g.target)
        if (reached) return WeightProjection(GoalState.REACHED, current, null, null, null, null, 1.0)
        val span = if (w.size >= 2) java.time.temporal.ChronoUnit.DAYS.between(w.firstKey(), w.lastKey()) else 0
        if (w.size < 4 || span < 14 || current == null) return WeightProjection(GoalState.NEED_DATA, current, null, null, null, null, progress)
        val x = w.keys.map { java.time.temporal.ChronoUnit.DAYS.between(w.firstKey(), it).toDouble() }
        val y = w.values.toList()
        val mx = x.average(); val my = y.average()
        val sxx = x.sumOf { (it - mx) * (it - mx) }
        val slope = x.indices.sumOf { (x[it] - mx) * (y[it] - my) } / sxx
        val resid = x.indices.map { y[it] - (my + slope * (x[it] - mx)) }
        val se = kotlin.math.sqrt(resid.sumOf { it * it } / (x.size - 2) / sxx)
        val week = slope * 7
        val need = g.target - current
        val state = when {
            abs(week) < 0.05 -> GoalState.STALLED
            (need < 0) != (slope < 0) -> GoalState.WRONG_WAY
            else -> GoalState.ON_TRACK
        }
        fun eta(s: Double): LocalDate? = if (s == 0.0 || (need < 0) != (s < 0)) null else today.plusDays(kotlin.math.ceil(need / s).toLong().coerceAtMost(3650))
        val early = eta(slope + (if (slope < 0) -1.96 else 1.96) * se)
        val late = eta(slope - (if (slope < 0) -1.96 else 1.96) * se)
        return WeightProjection(state, current, week, if (state == GoalState.ON_TRACK) eta(slope) else null,
            if (state == GoalState.ON_TRACK) early else null, if (state == GoalState.ON_TRACK) late else null, progress)
    }
}
