package it.cinocinorob.bioeta.science

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Andamento dell'umore (scala 1–5 del diario) e associazioni con i dati dell'orologio.
 *
 * - Media mobile di 7 giorni (almeno 3 registrazioni nella finestra), per vedere la tendenza al netto
 *   delle oscillazioni quotidiane.
 * - Associazioni: correlazione per ranghi di Spearman (adatta a una scala ordinale a 5 livelli) con
 *   intervallo di confidenza al 95% via trasformazione di Fisher ed errore standard √(1,06/(n−3))
 *   (Fieller, Hartley & Pearson, Biometrika 1957). Solo con almeno 14 coppie di giorni.
 *
 * Limiti: dati di una sola persona, osservazionali e con autocorrelazione tra giorni vicini (l'intervallo
 * reale è più largo di quello calcolato). Un'associazione non indica la direzione causale: dormire meglio
 * può migliorare l'umore, ma anche l'umore può cambiare il sonno.
 */
data class MoodPoint(val date: LocalDate, val value: Double)

data class MoodAssoc(val label: String, val rho: Double, val low: Double, val high: Double, val n: Int) {
    val clear get() = low > 0 || high < 0
}

data class MoodSummary(
    val mean7: Double?,
    val prev7: Double?,
    val mean30: Double?,
    val days30: Int,
)

object MoodTrend {
    const val MIN_PAIRS = 14

    fun rolling(points: List<MoodPoint>, window: Int = 7, minN: Int = 3): List<MoodPoint> {
        if (points.isEmpty()) return emptyList()
        val byDate = points.associate { it.date to it.value }
        val first = points.minOf { it.date }; val last = points.maxOf { it.date }
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.mapNotNull { d ->
            val w = (0 until window).mapNotNull { byDate[d.minusDays(it.toLong())] }
            if (w.size >= minN) MoodPoint(d, w.average()) else null
        }.toList()
    }

    fun summary(points: List<MoodPoint>, today: LocalDate): MoodSummary {
        fun mean(a: Long, b: Long) = points.filter { !it.date.isAfter(today.minusDays(a)) && it.date.isAfter(today.minusDays(b)) }
            .map { it.value }.takeIf { it.size >= 3 }?.average()
        val in30 = points.count { !it.date.isAfter(today) && it.date.isAfter(today.minusDays(30)) }
        return MoodSummary(mean(0, 7), mean(7, 14), mean(0, 30), in30)
    }

    /** Ranghi medi (i pari merito ricevono la media dei ranghi). */
    fun ranks(v: List<Double>): List<Double> {
        val idx = v.indices.sortedBy { v[it] }
        val r = DoubleArray(v.size)
        var i = 0
        while (i < idx.size) {
            var j = i
            while (j + 1 < idx.size && v[idx[j + 1]] == v[idx[i]]) j++
            val avg = (i + j) / 2.0 + 1
            for (k in i..j) r[idx[k]] = avg
            i = j + 1
        }
        return r.toList()
    }

    fun spearman(x: List<Double>, y: List<Double>): Double? {
        if (x.size != y.size || x.size < 3) return null
        val rx = ranks(x); val ry = ranks(y)
        val mx = rx.average(); val my = ry.average()
        val num = rx.indices.sumOf { (rx[it] - mx) * (ry[it] - my) }
        val den = sqrt(rx.sumOf { (it - mx) * (it - mx) } * ry.sumOf { (it - my) * (it - my) })
        return if (den == 0.0) null else num / den
    }

    fun assoc(label: String, pairs: List<Pair<Double, Double>>): MoodAssoc? {
        if (pairs.size < MIN_PAIRS) return null
        val rho = spearman(pairs.map { it.first }, pairs.map { it.second }) ?: return null
        val z = 0.5 * ln((1 + rho.coerceIn(-0.999, 0.999)) / (1 - rho.coerceIn(-0.999, 0.999)))
        val se = sqrt(1.06 / (pairs.size - 3))
        fun back(v: Double) = (exp(2 * v) - 1) / (exp(2 * v) + 1)
        return MoodAssoc(label, rho, back(z - 1.96 * se), back(z + 1.96 * se), pairs.size)
    }

    fun describe(a: MoodAssoc): String {
        if (!a.clear) return "nessuna associazione chiara"
        val s = when { abs(a.rho) < 0.3 -> "debole"; abs(a.rho) < 0.5 -> "moderata"; else -> "forte" }
        return if (a.rho > 0) "associazione $s: più è alto, migliore l'umore" else "associazione $s: più è alto, peggiore l'umore"
    }
}
