package it.cinocinorob.bioeta.science

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Esperimenti personali (n‑di‑1): cambi una sola abitudine per un periodo e confronti con il periodo prima.
 * Rispetto alle Scoperte (osservazionali) qui la scelta è tua e decisa in anticipo, quindi si riduce il
 * problema delle abitudini che «capitano» insieme ad altre cose.
 *
 * Disegno: fase A (21 giorni prima dell'inizio) contro fase B (giorni di esperimento in cui hai rispettato
 * l'impegno, segnato nel diario). Esiti della notte e del mattino successivi, come nelle Scoperte.
 * Statistica: differenza delle medie, IC bootstrap 95%, p per permutazione, correzione di Benjamini‑Hochberg
 * sui 7 esiti. Limite principale del disegno A‑B: un cambiamento esterno nello stesso periodo (stagione,
 * lavoro, malattia) viene attribuito all'esperimento; ripetere l'esperimento (A‑B‑A) lo rende più solido.
 */
data class Experiment(
    val itemKey: String,
    /** true = fare l'abitudine ogni giorno; false = evitarla. */
    val doIt: Boolean,
    val start: LocalDate,
    val days: Int = 14,
) {
    val end: LocalDate get() = start.plusDays(days.toLong() - 1)
    fun dayIndex(today: LocalDate): Int = (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceIn(0, days)
    fun finished(today: LocalDate) = today.isAfter(end)
}

data class ExperimentResult(
    val exp: Experiment,
    val adherent: Int,
    val answered: Int,
    val results: List<AssocResult>,
)

object Experiments {
    const val BASELINE_DAYS = 21L
    const val MIN_DAYS = 5

    fun evaluate(
        e: Experiment,
        journal: List<JournalEntry>,
        outcomes: Map<OutcomeKind, Map<LocalDate, Double>>,
        today: LocalDate,
    ): ExperimentResult {
        val inPeriod = journal.filter { !it.date.isBefore(e.start) && !it.date.isAfter(minOf(e.end, today)) }
        val answered = inPeriod.filter { it.answer(e.itemKey) != null }
        val adherentDays = answered.filter { it.answer(e.itemKey) == e.doIt }.map { it.date }.toSet()
        val baseDays = (1..BASELINE_DAYS).map { e.start.minusDays(it) }.toSet()
        val label = JournalCatalog.resolve(e.itemKey).label
        val exposure = Exposure("exp:${e.itemKey}", (if (e.doIt) "" else "Senza ") + label, adherentDays, adherentDays + baseDays, auto = false)
        val res = mutableListOf<AssocResult>()
        for ((k, s) in outcomes) {
            fun v(d: LocalDate) = s[d.plusDays(1)]?.let { if (k.log) (if (it > 0) ln(it) else null) else it }
            val b = adherentDays.mapNotNull { v(it) }
            val a = baseDays.mapNotNull { v(it) }
            if (b.size < MIN_DAYS || a.size < MIN_DAYS) continue
            val (diff, ci, p) = Associations.compare(b, a, (e.itemKey + k.name + e.start).hashCode().toLong())
            fun scale(x: Double) = if (k.log) (exp(x) - 1) * 100 else x
            res += AssocResult(exposure, k, scale(diff), scale(ci.first), scale(ci.second), p, nWith = b.size, nWithout = a.size)
        }
        val q = Associations.benjaminiHochberg(res.map { it.p })
        res.forEachIndexed { i, r ->
            r.q = q[i]
            val relevant = abs(r.effMid) >= 1.0
            r.verdict = when { relevant && r.q <= Associations.FDR -> Verdict.CONFIRMED; relevant && r.p < 0.10 -> Verdict.HINT; else -> Verdict.NONE }
        }
        return ExperimentResult(e, adherentDays.size, answered.size, res.sortedBy { it.outcome.ordinal })
    }
}

/** Orari di sonno: mediana e variabilità di addormentamento e risveglio, «jet lag sociale». */
data class SleepTiming(
    val bedMedianMin: Double,
    val bedSdMin: Double,
    val wakeMedianMin: Double,
    val wakeSdMin: Double,
    /** Differenza del punto medio del sonno tra fine settimana e giorni feriali (Wittmann 2006), minuti. */
    val socialJetLagMin: Double?,
    val nights: Int,
)

object SleepTimingCalc {
    /** Minuti dalla mezzanotte, su scala circolare (23:30 e 00:30 sono vicini). */
    private fun circMeanSd(mins: List<Double>): Pair<Double, Double> {
        val ang = mins.map { it / 1440.0 * 2 * Math.PI }
        val c = ang.sumOf { cos(it) } / ang.size; val s = ang.sumOf { sin(it) } / ang.size
        val mean = ((atan2(s, c) / (2 * Math.PI) * 1440.0) + 1440.0) % 1440.0
        val r = sqrt(c * c + s * s).coerceIn(1e-9, 1.0)
        val sd = sqrt(-2 * ln(r)) / (2 * Math.PI) * 1440.0
        return mean to sd
    }

    fun compute(nights: List<SleepNight>, today: LocalDate, zone: ZoneId, days: Long = 28): SleepTiming? {
        val ns = nights.filter { it.date.isAfter(today.minusDays(days)) && !it.date.isAfter(today) }
        if (ns.size < 7) return null
        fun m(i: Instant) = i.atZone(zone).toLocalTime().let { it.hour * 60.0 + it.minute }
        val (bm, bs) = circMeanSd(ns.map { m(it.start) })
        val (wm, ws) = circMeanSd(ns.map { m(it.end) })
        fun mid(n: SleepNight) = m(n.start.plusSeconds(java.time.Duration.between(n.start, n.end).seconds / 2))
        // Notti libere = risveglio di sabato e domenica
        val free = ns.filter { it.date.dayOfWeek.value >= 6 }.map { mid(it) }
        val work = ns.filter { it.date.dayOfWeek.value <= 5 }.map { mid(it) }
        val sjl = if (free.size >= 2 && work.size >= 4) {
            val d = circMeanSd(free).first - circMeanSd(work).first
            ((d + 720) % 1440 + 1440) % 1440 - 720
        } else null
        return SleepTiming(bm, bs, wm, ws, sjl, ns.size)
    }

    fun hhmm(min: Double): String { val t = ((min.toInt() % 1440) + 1440) % 1440; return "%02d:%02d".format(t / 60, t % 60) }
}
