package it.cinocinorob.bioeta.science

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * «Scoperte»: come le tue abitudini si associano ai tuoi parametri della notte e del giorno dopo.
 * Stessa idea delle Personal discoveries di Oura e dell'Impatto del diario di WHOOP, con regole statistiche
 * dichiarate:
 *
 * 1. Confronto dentro la persona. Ogni esito è espresso come scarto dalla TUA baseline mobile (mediana dei
 *    28 giorni precedenti, minimo 7 valori): così una tendenza lenta (stagione, forma fisica) non viene
 *    scambiata per l'effetto di un'abitudine. HRV su scala logaritmica (effetto in %).
 * 2. Esposti contro non esposti. Per le etichette del diario contano come «no» solo i giorni in cui hai
 *    compilato il diario: un giorno non compilato è sconosciuto, non «senza alcol».
 * 3. Minimo 5 giorni esposti e 5 non esposti negli ultimi 120.
 * 4. Effetto = differenza delle medie degli scarti; intervallo al 95% con bootstrap (2.000 ricampionamenti,
 *    seme fisso: stesso risultato a ogni apertura); p con test di permutazione (5.000 permutazioni).
 * 5. Molti confronti insieme generano falsi positivi: correzione di Benjamini‑Hochberg (FDR 10%)
 *    su tutte le coppie abitudine × esito testate.
 * 6. Soglie di rilevanza minima (sotto cui una differenza non conta anche se «significativa»).
 *
 * Esito: «Confermata» (FDR ≤ 10% e effetto ≥ soglia), «Indizio» (p < 0,10 non corretto e effetto ≥ soglia),
 * altrimenti nessun effetto chiaro. Sono associazioni osservazionali in una sola persona: abitudini che
 * capitano insieme (es. alcol e cena tardiva) si confondono; per questo segnaliamo le co‑occorrenze.
 */
enum class OutcomeKind(
    val label: String,
    val unit: String,
    /** true = valori più alti sono favorevoli. */
    val higherBetter: Boolean,
    /** Differenza minima rilevante, nella scala dell'effetto (per HRV: frazione, 0,03 = 3%). */
    val mid: Double,
    val log: Boolean = false,
) {
    HRV("HRV", "%", true, 0.03, log = true),
    RHR("FC a riposo", "bpm", false, 1.0),
    SLEEP("Sonno totale", "min", true, 10.0),
    DEEP("Sonno profondo", "min", true, 5.0),
    EFFICIENCY("Efficienza del sonno", "punti %", true, 1.5),
    READINESS("Recupero", "punti", true, 3.0),
    MOOD("Umore del giorno dopo", "punti", true, 0.3),
}

/** Esposizione: un'abitudine o un evento del giorno D (gli esiti sono quelli della notte D→D+1 e del giorno D+1). */
data class Exposure(val key: String, val label: String, val days: Set<LocalDate>, val known: Set<LocalDate>, val auto: Boolean)

enum class Verdict(val label: String) { CONFIRMED("Confermata"), HINT("Indizio"), NONE("Nessun effetto chiaro") }

data class AssocResult(
    val exposure: Exposure,
    val outcome: OutcomeKind,
    /** Effetto nella scala dell'esito (HRV: % ; altri: unità). */
    val effect: Double,
    val low: Double,
    val high: Double,
    val p: Double,
    var q: Double = 1.0,
    val nWith: Int,
    val nWithout: Int,
    var verdict: Verdict = Verdict.NONE,
) {
    val favorable: Boolean get() = (effect > 0) == outcome.higherBetter
    /** Effetto in multipli della soglia di rilevanza (per il grafico a foresta). */
    val effMid: Double get() = effectOnMidScale(effect)
    val lowMid: Double get() = effectOnMidScale(low)
    val highMid: Double get() = effectOnMidScale(high)
    private fun effectOnMidScale(v: Double) = if (outcome.log) v / 100.0 / outcome.mid else v / outcome.mid
}

data class Pending(val exposure: Exposure, val nWith: Int, val nWithout: Int)

data class Discoveries(
    val results: List<AssocResult>,
    val pending: List<Pending>,
    /** Coppie di abitudini che capitano insieme in ≥ 60% dei giorni della più rara. */
    val cooccurring: Map<String, List<String>>,
    val testedPairs: Int,
)

object Associations {
    const val MIN_N = 5
    const val WINDOW_DAYS = 120L
    const val FDR = 0.10
    private const val BOOT = 2000
    private const val PERM = 5000

    /** Scarti dalla baseline mobile (mediana dei 28 giorni precedenti, ≥ 7 valori). */
    fun residuals(series: Map<LocalDate, Double>, log: Boolean): Map<LocalDate, Double> {
        val t = if (log) series.filterValues { it > 0 }.mapValues { ln(it.value) } else series
        val dates = t.keys.sorted()
        val out = HashMap<LocalDate, Double>()
        for (d in dates) {
            val base = dates.filter { it.isBefore(d) && !it.isBefore(d.minusDays(28)) }.map { t.getValue(it) }
            if (base.size >= 7) out[d] = t.getValue(d) - median(base)
        }
        return out
    }

    fun median(v: List<Double>): Double {
        val s = v.sorted(); val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    /** Generatore pseudo‑casuale deterministico (xorshift) per bootstrap e permutazioni riproducibili. */
    private class Rng(seed: Long) {
        private var x = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed
        fun next(): Long { x = x xor (x shl 13); x = x xor (x ushr 7); x = x xor (x shl 17); return x }
        fun int(n: Int): Int = ((next() ushr 1) % n).toInt()
    }

    /** Differenza delle medie, IC bootstrap al 95% (percentili) e p bilaterale da permutazioni. */
    fun compare(a: List<Double>, b: List<Double>, seed: Long = 42L): Triple<Double, Pair<Double, Double>, Double> {
        val diff = a.average() - b.average()
        val rng = Rng(seed)
        val boots = DoubleArray(BOOT) {
            var sa = 0.0; repeat(a.size) { sa += a[rng.int(a.size)] }
            var sb = 0.0; repeat(b.size) { sb += b[rng.int(b.size)] }
            sa / a.size - sb / b.size
        }
        boots.sort()
        val lo = boots[(0.025 * BOOT).toInt()]; val hi = boots[(0.975 * BOOT).toInt() - 1]
        val pooled = (a + b).toDoubleArray()
        val na = a.size
        var extreme = 0
        repeat(PERM) {
            // permutazione parziale di Fisher‑Yates per i primi na elementi
            for (i in 0 until na) { val j = i + rng.int(pooled.size - i); val tmp = pooled[i]; pooled[i] = pooled[j]; pooled[j] = tmp }
            var s1 = 0.0; for (i in 0 until na) s1 += pooled[i]
            var s2 = 0.0; for (i in na until pooled.size) s2 += pooled[i]
            if (abs(s1 / na - s2 / (pooled.size - na)) >= abs(diff) - 1e-12) extreme++
        }
        val p = (extreme + 1.0) / (PERM + 1.0)
        return Triple(diff, lo to hi, p)
    }

    /** Benjamini‑Hochberg: q‑value per ciascun p. */
    fun benjaminiHochberg(p: List<Double>): List<Double> {
        val m = p.size
        if (m == 0) return emptyList()
        val order = p.indices.sortedBy { p[it] }
        val q = DoubleArray(m)
        var prev = 1.0
        for (r in m - 1 downTo 0) {
            val i = order[r]
            prev = min(prev, p[i] * m / (r + 1))
            q[i] = prev
        }
        return q.toList()
    }

    /**
     * @param exposures abitudini del giorno D
     * @param outcomes esiti per data: HRV/FC/Recupero del mattino D+1, notte con data D+1, umore D+1
     */
    fun discover(exposures: List<Exposure>, outcomes: Map<OutcomeKind, Map<LocalDate, Double>>, today: LocalDate): Discoveries {
        val from = today.minusDays(WINDOW_DAYS)
        val res = outcomes.mapValues { (k, s) -> residuals(s, k.log) }
        val results = mutableListOf<AssocResult>()
        val pending = mutableListOf<Pending>()
        for (e in exposures) {
            var bestWith = 0; var bestWithout = 0
            var testedAny = false
            for ((k, r) in res) {
                val a = mutableListOf<Double>(); val b = mutableListOf<Double>()
                for (d in e.known) {
                    if (d.isBefore(from) || d.isAfter(today)) continue
                    val v = r[d.plusDays(1)] ?: continue
                    if (d in e.days) a += v else b += v
                }
                bestWith = max(bestWith, a.size); bestWithout = max(bestWithout, b.size)
                if (a.size < MIN_N || b.size < MIN_N) continue
                testedAny = true
                val seed = (e.key + k.name).hashCode().toLong()
                val (diff, ci, p) = compare(a, b, seed)
                fun scale(v: Double) = if (k.log) (exp(v) - 1) * 100 else v
                results += AssocResult(e, k, scale(diff), scale(ci.first), scale(ci.second), p, nWith = a.size, nWithout = b.size)
            }
            if (!testedAny) pending += Pending(e, bestWith, bestWithout)
        }
        val q = benjaminiHochberg(results.map { it.p })
        results.forEachIndexed { i, r ->
            r.q = q[i]
            val relevant = abs(r.effMid) >= 1.0
            r.verdict = when {
                relevant && r.q <= FDR -> Verdict.CONFIRMED
                relevant && r.p < 0.10 -> Verdict.HINT
                else -> Verdict.NONE
            }
        }
        // Co‑occorrenze: abitudini che capitano insieme nella maggior parte dei giorni della più rara
        val co = HashMap<String, MutableList<String>>()
        for (x in exposures) for (y in exposures) {
            if (x.key == y.key) continue
            val inter = x.days.intersect(y.days).size
            val smaller = min(x.days.size, y.days.size)
            if (smaller >= MIN_N && inter >= 0.6 * x.days.size) co.getOrPut(x.key) { mutableListOf() } += y.label
        }
        return Discoveries(
            results.sortedWith(compareBy<AssocResult> { it.verdict.ordinal }.thenByDescending { abs(it.effMid) }),
            pending.sortedByDescending { it.nWith }, co, results.size,
        )
    }

    /** Testo dell'effetto, es. «HRV −14%», «FC a riposo +2,1 bpm». */
    fun effectText(r: AssocResult): String {
        val sign = if (r.effect >= 0) "+" else "−"
        val v = abs(r.effect)
        val num = when (r.outcome) {
            OutcomeKind.HRV -> String.format(java.util.Locale.ITALY, "%.0f", v)
            OutcomeKind.SLEEP, OutcomeKind.DEEP -> String.format(java.util.Locale.ITALY, "%.0f", v)
            OutcomeKind.MOOD -> String.format(java.util.Locale.ITALY, "%.1f", v)
            else -> String.format(java.util.Locale.ITALY, "%.1f", v)
        }
        return "$sign$num ${r.outcome.unit}"
    }

    fun ciText(r: AssocResult): String {
        val d = if (r.outcome == OutcomeKind.HRV || r.outcome == OutcomeKind.SLEEP || r.outcome == OutcomeKind.DEEP) 0 else 1
        fun f(x: Double) = String.format(java.util.Locale.ITALY, "%+.${d}f", x).replace('-', '−')
        return "${f(r.low)} … ${f(r.high)}"
    }

    @Suppress("unused")
    private fun sd(v: List<Double>): Double { val m = v.average(); return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1)) }
}
