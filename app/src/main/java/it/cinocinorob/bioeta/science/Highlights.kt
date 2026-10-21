package it.cinocinorob.bioeta.science

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * «In evidenza» (come i Riepiloghi di Apple Salute e le Tendenze di Apple Fitness): poche frasi in italiano
 * semplice su ciò che è cambiato davvero, ciascuna con un piccolo grafico.
 *
 * Due confronti per ogni parametro:
 * - Settimana: media degli ultimi 7 giorni contro le 4 settimane precedenti (giorni 8–35).
 * - Mese: media degli ultimi 30 giorni contro i 90 precedenti (giorni 31–120; l'app legge 120 giorni di storico).
 * Un cambiamento entra solo se supera la soglia di rilevanza del parametro E l'effetto standardizzato
 * (differenza / DS giornaliera del periodo di riferimento) è almeno 0,5 — «medio» secondo Cohen. Così non si
 * commentano oscillazioni normali. Ordine: effetto standardizzato decrescente.
 */
enum class HMetric(
    val label: String,
    val unit: String,
    val higherBetter: Boolean?,
    /** Soglia di rilevanza nella stessa unità. */
    val mid: Double,
    val decimals: Int,
) {
    RHR("FC a riposo", "bpm", false, 1.0, 0),
    HRV("HRV", "ms", true, 3.0, 0),
    SLEEP("Sonno", "h", true, 0.25, 1),
    STEPS("Passi", "", true, 800.0, 0),
    ACTIVE("Minuti attivi", "min", true, 5.0, 0),
    VO2("VO₂max", "mL/kg/min", true, 0.7, 1),
    WEIGHT("Peso", "kg", null, 0.7, 1),
    RESP("Respirazione notturna", "atti/min", null, 0.5, 1),
}

enum class HPeriod(val label: String) { WEEK("questa settimana"), MONTH("nell'ultimo mese") }

data class Highlight(
    val metric: HMetric,
    val period: HPeriod,
    val current: Double,
    val reference: Double,
    val effect: Double,
    /** null = nessuna direzione «migliore» (es. peso). */
    val good: Boolean?,
    val sentence: String,
    /** Medie per blocco (5 settimane o 4 mesi da 30 giorni), dal più vecchio; l'ultimo è il periodo attuale. */
    val bars: List<Double?>,
)

object Highlights {
    private fun sd(v: List<Double>): Double {
        if (v.size < 2) return 0.0
        val m = v.average(); return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1))
    }

    fun fmt(m: HMetric, v: Double): String = when (m) {
        HMetric.SLEEP -> { val h = v.toInt(); val mm = ((v - h) * 60).roundToInt(); if (mm == 60) "${h + 1} h" else "$h h ${"%02d".format(mm)}" }
        HMetric.STEPS -> String.format(java.util.Locale.ITALY, "%,d", v.roundToInt())
        else -> String.format(java.util.Locale.ITALY, "%.${m.decimals}f", v)
    }

    private fun diffText(m: HMetric, d: Double): String = when (m) {
        HMetric.SLEEP -> "${(abs(d) * 60).roundToInt()} minuti"
        HMetric.STEPS -> "${String.format(java.util.Locale.ITALY, "%,d", abs(d).roundToInt())} passi"
        else -> "${String.format(java.util.Locale.ITALY, "%.${m.decimals}f", abs(d))} ${m.unit}".trim()
    }

    private fun sentence(m: HMetric, p: HPeriod, cur: Double, d: Double): String {
        val more = d > 0
        val comp = if (p == HPeriod.WEEK) "rispetto alle 4 settimane prima" else "rispetto ai 3 mesi prima"
        return when (m) {
            HMetric.SLEEP -> "Hai dormito in media ${fmt(m, cur)} ${p.label}, ${diffText(m, d)} ${if (more) "in più" else "in meno"} $comp."
            HMetric.STEPS -> "Hai fatto in media ${fmt(m, cur)} passi al giorno ${p.label}, ${diffText(m, d)} ${if (more) "in più" else "in meno"} $comp."
            HMetric.ACTIVE -> "${p.label.replaceFirstChar { it.uppercase() }} hai fatto in media ${fmt(m, cur)} minuti attivi al giorno, ${diffText(m, d)} ${if (more) "in più" else "in meno"} $comp."
            HMetric.WEIGHT -> "Il tuo peso medio ${p.label} è ${fmt(m, cur)} kg, ${diffText(m, d)} ${if (more) "in più" else "in meno"} $comp."
            else -> "${m.label} media ${p.label}: ${fmt(m, cur)} ${m.unit}, ${diffText(m, d)} ${if (more) "più alta" else "più bassa"} $comp."
        }
    }

    /**
     * @param series valori giornalieri per parametro
     */
    fun compute(series: Map<HMetric, Map<LocalDate, Double>>, today: LocalDate, max: Int = 4): List<Highlight> {
        val out = mutableListOf<Highlight>()
        for ((m, s) in series) {
            fun win(from: Long, to: Long) = (from..to).mapNotNull { s[today.minusDays(it)] }
            // Settimana: giorni 0–6 contro 7–34 (oggi incluso solo se presente)
            val w = win(0, 6); val wRef = win(7, 34)
            if (w.size >= 4 && wRef.size >= 10) add(out, m, HPeriod.WEEK, w, wRef, (4 downTo 0).map { k -> win(7L * k, 7L * k + 6).takeIf { it.size >= 3 }?.average() })
            // Mese: giorni 0–29 contro 30–119
            val mo = win(0, 29); val moRef = win(30, 119)
            if (mo.size >= 15 && moRef.size >= 40) add(out, m, HPeriod.MONTH, mo, moRef, (3 downTo 0).map { k -> win(30L * k, 30L * k + 29).takeIf { it.size >= 10 }?.average() })
        }
        // Un solo messaggio per parametro: tiene il confronto con l'effetto maggiore
        return out.groupBy { it.metric }.map { (_, l) -> l.maxBy { abs(it.effect) } }
            .sortedByDescending { abs(it.effect) }.take(max)
    }

    private fun add(out: MutableList<Highlight>, m: HMetric, p: HPeriod, cur: List<Double>, ref: List<Double>, bars: List<Double?>) {
        val c = cur.average(); val r = ref.average(); val d = c - r
        val s = sd(ref)
        if (abs(d) < m.mid || s <= 0) return
        val eff = d / s
        if (abs(eff) < 0.5) return
        val good = m.higherBetter?.let { if (it) d > 0 else d < 0 }
        out += Highlight(m, p, c, r, eff, good, sentence(m, p, c, d), bars)
    }
}
