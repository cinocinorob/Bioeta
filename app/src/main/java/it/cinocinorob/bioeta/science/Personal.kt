package it.cinocinorob.bioeta.science

/*
 * Lettura personale dei punteggi.
 * Un punteggio assoluto (es. sonno 97/100) satura facilmente e dice poco su "come è andata rispetto al solito".
 * Il rango percentile rispetto alla PROPRIA storia resta informativo anche quando i punteggi sono alti:
 * "meglio del 70% delle tue ultime 60 notti". Non cambia la formula del punteggio, ne aggiunge una lettura.
 */
object Personal {
    const val MIN_HISTORY = 14

    /**
     * Rango percentile (0–100) di [value] nella storia: % di valori inferiori + metà dei pari (definizione
     * standard "mid-rank"). Null con meno di [MIN_HISTORY] valori.
     */
    fun percentile(value: Int, history: List<Int>): Int? {
        if (history.size < MIN_HISTORY) return null
        val below = history.count { it < value }
        val equal = history.count { it == value }
        return Math.round((below + 0.5 * equal) / history.size * 100).toInt()
    }

    fun describe(pct: Int, what: String): String = when {
        pct >= 80 -> "tra le tue migliori $what"
        pct >= 60 -> "sopra la tua media"
        pct >= 40 -> "nella tua media"
        pct >= 20 -> "sotto la tua media"
        else -> "tra le tue peggiori $what"
    }
}

enum class Reliability(val label: String) { HIGH("Affidabilità alta"), MEDIUM("Affidabilità media"), LOW("Affidabilità bassa") }

data class ReliabilityInfo(val level: Reliability, val reason: String)

object ScoreReliability {
    /** Quota del peso totale coperta da contributori con dato disponibile. */
    fun coverage(r: ScoreResult): Double {
        val tot = r.contributors.sumOf { it.weight }
        return if (tot <= 0) 0.0 else r.contributors.filter { it.score != null }.sumOf { it.weight } / tot
    }

    fun readiness(r: ScoreResult): ReliabilityInfo? {
        if (r.score == null) return null
        val c = coverage(r)
        return when {
            r.provisional && r.baselineDays < 7 -> ReliabilityInfo(Reliability.LOW, "baseline di soli ${r.baselineDays} giorni")
            r.provisional -> ReliabilityInfo(Reliability.MEDIUM, "baseline di ${r.baselineDays} giorni (servono 14)")
            c < 0.6 -> ReliabilityInfo(Reliability.LOW, "mancano dati per ${Math.round((1 - c) * 100)}% del punteggio")
            c < 0.85 -> ReliabilityInfo(Reliability.MEDIUM, "alcuni dati mancanti")
            else -> ReliabilityInfo(Reliability.HIGH, "baseline completa e dati presenti")
        }
    }

    fun sleep(r: ScoreResult, night: SleepNight): ReliabilityInfo? {
        if (r.score == null) return null
        val c = coverage(r)
        return when {
            !night.hasStages -> ReliabilityInfo(Reliability.LOW, "senza fasi del sonno: solo durata e regolarità")
            c < 0.75 -> ReliabilityInfo(Reliability.MEDIUM, "alcuni componenti non calcolabili")
            else -> ReliabilityInfo(Reliability.HIGH, "fasi del sonno e storico disponibili")
        }
    }
}
