package it.cinocinorob.bioeta.science

import java.time.LocalDate
import kotlin.math.sqrt

/**
 * Carico percepito con il metodo session‑RPE: voto di fatica dell'intero allenamento (scala CR‑10 di Borg
 * modificata) dato circa 30 minuti dopo la fine, moltiplicato per la durata in minuti.
 *
 * - Foster C et al., J Strength Cond Res 2001;15:109-115: relazione coerente con il carico calcolato dalla FC,
 *   sia in bici (continuo e a intervalli) sia nel basket. Rassegna di 36 studi di validità e affidabilità:
 *   Haddad M et al., Front Neurosci 2017;11:612.
 * - Foster C, Med Sci Sports Exerc 1998;30:1164-1168: monotonia = media/DS del carico giornaliero della
 *   settimana; tensione (strain) = carico settimanale × monotonia. In 25 atleti gran parte delle malattie
 *   banali compariva oltre soglie individuali, soprattutto di tensione: dati osservazionali, soglie personali.
 *
 * Vantaggio rispetto al carico cardiaco: funziona anche senza frequenza cardiaca e coglie lo sforzo
 * muscolare (forza, salite in bici) che la FC sottostima. Svantaggio: è soggettivo.
 */
object SessionRpe {
    /** Etichette della scala CR‑10 (Foster 2001); i valori senza etichetta sono intermedi. */
    fun label(v: Int): String = when (v) {
        0 -> "Riposo"; 1 -> "Molto, molto facile"; 2 -> "Facile"; 3 -> "Moderato"; 4 -> "Abbastanza duro"
        5 -> "Duro"; 6 -> "Duro"; 7 -> "Molto duro"; 8 -> "Molto duro"; 9 -> "Quasi massimale"; 10 -> "Massimale"
        else -> ""
    }

    fun load(rpe: Int, minutes: Double): Double = rpe.coerceIn(0, 10) * minutes.coerceAtLeast(0.0)

    data class Rated(val date: LocalDate, val rpe: Int, val minutes: Double)

    data class Summary(
        /** Carico percepito degli ultimi 7 giorni (UA = unità arbitrarie). */
        val week: Double,
        /** Media settimanale dei 28 giorni (4 settimane, inclusa l'ultima). Null con meno di 14 giorni di storico voti. */
        val chronicWeek: Double?,
        val ratio: Double?,
        /** Media/DS del carico giornaliero degli ultimi 7 giorni (giorni senza allenamento = 0). */
        val monotony: Double?,
        val strain: Double?,
        val sessions7: Int,
    )

    fun summary(rated: List<Rated>, today: LocalDate): Summary {
        val daily = rated.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { load(it.rpe, it.minutes) } }
        val last7 = (0L..6L).map { daily[today.minusDays(it)] ?: 0.0 }
        val week = last7.sum()
        val last28 = (0L..27L).sumOf { daily[today.minusDays(it)] ?: 0.0 }
        val first = rated.minOfOrNull { it.date }
        val history = first != null && !first.isAfter(today.minusDays(13))
        val chronic = if (history) last28 / 4.0 else null
        val mean = last7.average()
        val sd = sqrt(last7.sumOf { (it - mean) * (it - mean) } / (last7.size - 1))
        val monotony = if (week > 0 && sd > 1e-9) mean / sd else null
        return Summary(
            week = week,
            chronicWeek = chronic,
            ratio = if (chronic != null && chronic > 0) week / chronic else null,
            monotony = monotony,
            strain = monotony?.let { week * it },
            sessions7 = rated.count { !it.date.isBefore(today.minusDays(6)) && !it.date.isAfter(today) },
        )
    }
}
