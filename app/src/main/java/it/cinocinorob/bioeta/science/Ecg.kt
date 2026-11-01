package it.cinocinorob.bioeta.science

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Archivio ECG. I tracciati arrivano dai PDF che l'app ECG di Google Health / Fitbit (dispositivo medico marcato CE)
 * permette di esportare: Connessione Salute non contiene ECG e l'API Fitbit che li esponeva è stata dismessa.
 *
 * Scelta di metodo: BioEtà NON interpreta il tracciato. Riconoscere la fibrillazione atriale da un ECG a una
 * derivazione è la funzione di un dispositivo medico validato; rifarla qui con un algoritmo non certificato
 * aggiungerebbe solo un secondo giudizio meno affidabile. L'app conserva il PDF, legge il risultato e la frequenza
 * già calcolati dall'app ECG, e li mette in ordine nel tempo. Per la diagnosi di fibrillazione atriale le linee
 * guida ESC 2024 richiedono che un medico riveda il tracciato.
 */

enum class EcgResult(val label: String) {
    SINUS("Ritmo sinusale"),
    AFIB("Segni di fibrillazione atriale"),
    INCONCLUSIVE("Non conclusivo"),
    HIGH_HR("Frequenza alta, non valutabile"),
    LOW_HR("Frequenza bassa, non valutabile"),
    UNKNOWN("Da indicare"),
}

data class EcgRecord(
    val id: String,
    val time: LocalDateTime,
    val result: EcgResult,
    val heartRate: Int?,
    /** "pdf" oppure "image". */
    val kind: String,
    /** Risultato letto dal file (true) o indicato a mano (false). */
    val parsed: Boolean,
    val note: String = "",
)

data class EcgParsed(val time: LocalDateTime?, val result: EcgResult, val heartRate: Int?)

object EcgParser {
    private val ecgWords = listOf("ecg", "ekg", "elettrocardiogramma", "electrocardiogram", "heart rhythm", "ritmo cardiaco")
    private val resultWords = listOf("sinus", "sinusale", "atrial fibrillation", "fibrillazione atriale", "afib", "inconclusive", "non conclusiv", "inconcludent", "lead i", "derivazione i")

    /** Il testo estratto da un PDF sembra un referto ECG (e non un referto di analisi del sangue)? */
    fun looksLikeEcg(text: String): Boolean {
        val t = text.lowercase()
        return ecgWords.any { it in t } && resultWords.any { it in t }
    }

    fun parse(text: String): EcgParsed {
        val t = text.lowercase()
        val result = when {
            // l'ordine conta: «no signs of atrial fibrillation» è un ritmo sinusale
            Regex("(no|nessun|assenza di)\\s+(signs?\\s+of\\s+|segn[oi]\\s+di\\s+)?(atrial fibrillation|afib|fibrillazione atriale)").containsMatchIn(t) -> EcgResult.SINUS
            "atrial fibrillation" in t || "fibrillazione atriale" in t || Regex("\\bafib\\b").containsMatchIn(t) -> EcgResult.AFIB
            "sinus rhythm" in t || "ritmo sinusale" in t -> EcgResult.SINUS
            Regex("(high|elevat\\w*|alta)\\s+(heart rate|frequenza)").containsMatchIn(t) || Regex("(heart rate|frequenza cardiaca)\\s+(over|oltre|superiore)").containsMatchIn(t) -> EcgResult.HIGH_HR
            Regex("(low|bassa)\\s+(heart rate|frequenza)").containsMatchIn(t) || Regex("(heart rate|frequenza cardiaca)\\s+(under|below|sotto|inferiore)").containsMatchIn(t) -> EcgResult.LOW_HR
            "inconclusive" in t || "non conclusiv" in t || "inconcludent" in t -> EcgResult.INCONCLUSIVE
            else -> EcgResult.UNKNOWN
        }
        val hr = Regex("(\\d{2,3})\\s*(bpm|battiti)").findAll(t).map { it.groupValues[1].toInt() }.firstOrNull { it in 30..220 }
        return EcgParsed(findDateTime(text), result, hr)
    }

    private val months = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        "gen" to 1, "mag" to 5, "giu" to 6, "lug" to 7, "ago" to 8, "set" to 9, "ott" to 10, "dic" to 12,
    )

    private fun time(after: String): LocalTime? {
        val m = Regex("^[^0-9]{0,12}(\\d{1,2})[:.](\\d{2})\\s*(am|pm|a\\.m\\.|p\\.m\\.)?", RegexOption.IGNORE_CASE).find(after) ?: return null
        var h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        val ap = m.groupValues[3].lowercase()
        if (ap.startsWith("p") && h < 12) h += 12
        if (ap.startsWith("a") && h == 12) h = 0
        return if (h in 0..23 && mi in 0..59) LocalTime.of(h, mi) else null
    }

    /** Prima data plausibile nel testo (formati italiani e inglesi), con l'ora se segue subito. */
    fun findDateTime(text: String): LocalDateTime? {
        val candidates = mutableListOf<Pair<Int, LocalDateTime>>()
        // 30/10/2026 o 30-10-2026
        Regex("\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})\\b").findAll(text).forEach { m ->
            runCatching {
                val d = LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
                candidates += m.range.first to d.atTime(time(text.substring(m.range.last + 1).take(30)) ?: LocalTime.NOON)
            }
        }
        // 30 ott 2026 / 30 October 2026
        Regex("\\b(\\d{1,2})\\s+([A-Za-zàé]{3,})\\.?\\s+(\\d{4})", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            val mo = months[m.groupValues[2].lowercase().take(3)] ?: return@forEach
            runCatching {
                val d = LocalDate.of(m.groupValues[3].toInt(), mo, m.groupValues[1].toInt())
                candidates += m.range.first to d.atTime(time(text.substring(m.range.last + 1).take(30)) ?: LocalTime.NOON)
            }
        }
        // Oct 30, 2026
        Regex("\\b([A-Za-z]{3,})\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            val mo = months[m.groupValues[1].lowercase().take(3)] ?: return@forEach
            runCatching {
                val d = LocalDate.of(m.groupValues[3].toInt(), mo, m.groupValues[2].toInt())
                candidates += m.range.first to d.atTime(time(text.substring(m.range.last + 1).take(30)) ?: LocalTime.NOON)
            }
        }
        // La data di nascita compare spesso prima di quella della registrazione: si scartano le date lontane
        val plausible = candidates.filter { it.second.toLocalDate().isAfter(LocalDate.of(2018, 1, 1)) && !it.second.toLocalDate().isAfter(LocalDate.now().plusDays(1)) }
        return plausible.minByOrNull { it.first }?.second
    }
}

data class EcgSummary(val total: Int, val last12m: Int, val afib12m: Int, val inconclusive12m: Int, val meanHr: Double?)

object EcgStats {
    fun summary(list: List<EcgRecord>, today: LocalDate): EcgSummary {
        val y = list.filter { it.time.toLocalDate().isAfter(today.minusYears(1)) }
        return EcgSummary(
            list.size, y.size, y.count { it.result == EcgResult.AFIB },
            y.count { it.result == EcgResult.INCONCLUSIVE || it.result == EcgResult.HIGH_HR || it.result == EcgResult.LOW_HR },
            y.mapNotNull { it.heartRate }.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    fun fmt(t: LocalDateTime): String = t.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ITALIAN))
}
