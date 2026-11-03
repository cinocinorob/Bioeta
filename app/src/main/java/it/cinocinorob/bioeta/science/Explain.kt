package it.cinocinorob.bioeta.science

import java.util.Locale
import kotlin.math.abs

/*
 * Spiegazioni delle correlazioni in linguaggio semplice, sul modello delle app che le presentano meglio
 * (Ultrahuman, Oura, WHOOP): un titolo con il numero che conta, una frase con i tuoi valori «con / senza»,
 * quanto è forte e quanto è affidabile, e cosa puoi fare. I numeri sono gli stessi delle Scoperte (stessa
 * statistica, vedi Associations.kt); cambia solo come vengono detti. Le parole restano descrittive
 * («è più bassa», non «abbassa»): sono associazioni nei dati di una persona, non cause.
 */

enum class Strength(val label: String) { STRONG("Effetto forte"), MODERATE("Effetto moderato"), MILD("Effetto lieve") }

data class FindingText(
    /** Numero principale, es. «−14%». */
    val big: String,
    /** Titolo breve, es. «HRV la notte dopo». */
    val metric: String,
    /** Frase con i tuoi valori. */
    val sentence: String,
    val strength: Strength,
    val reliability: String,
    /** Valori «con» e «senza» per il grafico (unità grezze) e loro etichette. */
    val withValue: Double?,
    val withoutValue: Double?,
    val withLabel: String,
    val withoutLabel: String,
    val action: String,
    /** Chiave della voce del diario se si può avviare un esperimento su questa abitudine. */
    val experimentKey: String?,
    /** true = per l'esperimento conviene evitare l'abitudine. */
    val experimentAvoid: Boolean,
)

object Explain {
    private fun f0(v: Double) = String.format(Locale.ITALY, "%.0f", v)
    private fun f1(v: Double) = String.format(Locale.ITALY, "%.1f", v)

    /** Valore grezzo con la sua unità, es. «48 ms», «7 h 05», «82/100». */
    fun raw(k: OutcomeKind, v: Double): String = when (k) {
        OutcomeKind.HRV -> "${f0(v)} ms"
        OutcomeKind.RHR -> "${f1(v)} bpm"
        OutcomeKind.SLEEP -> { val m = v.toInt(); "${m / 60} h ${"%02d".format(m % 60)}" }
        OutcomeKind.DEEP -> "${f0(v)} min"
        OutcomeKind.EFFICIENCY -> "${f0(v)}%"
        OutcomeKind.READINESS -> f0(v)
        OutcomeKind.MOOD -> "${f1(v)} su 5"
    }

    private fun subject(k: OutcomeKind) = when (k) {
        OutcomeKind.HRV -> "la tua HRV"
        OutcomeKind.RHR -> "la tua FC a riposo"
        OutcomeKind.SLEEP -> "il tuo sonno"
        OutcomeKind.DEEP -> "il tuo sonno profondo"
        OutcomeKind.EFFICIENCY -> "l'efficienza del tuo sonno"
        OutcomeKind.READINESS -> "il tuo Recupero"
        OutcomeKind.MOOD -> "il tuo umore"
    }

    private fun metric(k: OutcomeKind) = when (k) {
        OutcomeKind.HRV -> "HRV la notte dopo"
        OutcomeKind.RHR -> "FC a riposo il mattino dopo"
        OutcomeKind.SLEEP -> "sonno la notte dopo"
        OutcomeKind.DEEP -> "sonno profondo la notte dopo"
        OutcomeKind.EFFICIENCY -> "efficienza del sonno"
        OutcomeKind.READINESS -> "Recupero il mattino dopo"
        OutcomeKind.MOOD -> "umore il giorno dopo"
    }

    private fun when_(k: OutcomeKind) = when (k) {
        OutcomeKind.MOOD -> "il giorno dopo"
        OutcomeKind.RHR, OutcomeKind.READINESS -> "il mattino dopo"
        else -> "la notte dopo"
    }

    private fun lowerFirst(s: String) = if (s.length > 1 && s[1].isLowerCase()) s.replaceFirstChar { it.lowercase() } else s

    fun strength(r: AssocResult): Strength = when {
        abs(r.effMid) >= 3 -> Strength.STRONG
        abs(r.effMid) >= 2 -> Strength.MODERATE
        else -> Strength.MILD
    }

    fun finding(r: AssocResult): FindingText {
        val k = r.outcome
        val up = r.effect > 0
        val e = r.exposure
        val habit = lowerFirst(e.label)
        val big = when (k) {
            OutcomeKind.HRV -> (if (up) "+" else "−") + f0(abs(r.effect)) + "%"
            OutcomeKind.SLEEP, OutcomeKind.DEEP -> (if (up) "+" else "−") + f0(abs(r.effect)) + " min"
            OutcomeKind.RHR -> (if (up) "+" else "−") + f1(abs(r.effect)) + " bpm"
            OutcomeKind.MOOD -> (if (up) "+" else "−") + f1(abs(r.effect))
            else -> (if (up) "+" else "−") + f1(abs(r.effect)) + " punti"
        }
        val dir = when (k) {
            OutcomeKind.SLEEP, OutcomeKind.DEEP -> if (up) "più lungo" else "più corto"
            else -> if (up) "più alto" else "più basso"
        }.let { if (k == OutcomeKind.HRV || k == OutcomeKind.RHR || k == OutcomeKind.EFFICIENCY) it.replace("alto", "alta").replace("basso", "bassa") else it }
        val amount = when (k) {
            OutcomeKind.HRV -> "del ${f0(abs(r.effect))}%"
            OutcomeKind.SLEEP, OutcomeKind.DEEP -> "di ${f0(abs(r.effect))} minuti"
            OutcomeKind.RHR -> "di ${f1(abs(r.effect))} bpm"
            OutcomeKind.MOOD -> "di ${f1(abs(r.effect))} punti"
            else -> "di ${f1(abs(r.effect))} punti"
        }
        val values = if (r.meanWith != null && r.meanWithout != null)
            " In media ${raw(k, r.meanWith)} contro ${raw(k, r.meanWithout)} negli altri giorni." else ""
        val sentence = "Dopo i giorni con «$habit», ${subject(k)} ${when_(k)} è $dir $amount rispetto al tuo solito.$values"
        val reliability = when (r.verdict) {
            Verdict.CONFIRMED -> "Affidabile · ${r.nWith} giorni con, ${r.nWithout} senza"
            Verdict.HINT -> "Da confermare · ${r.nWith} giorni con, ${r.nWithout} senza"
            Verdict.NONE -> "Non chiaro · ${r.nWith} giorni con, ${r.nWithout} senza"
        }
        val tagKey = e.key.removePrefix("tag:").takeIf { e.key.startsWith("tag:") }
        val action = when {
            r.verdict == Verdict.NONE -> "Servono più giorni per capire se conta."
            r.favorable && e.auto -> "Ti fa bene: tienilo nella tua settimana."
            r.favorable -> "Ti fa bene: per esserne sicuro prova a farlo ogni giorno per 2 settimane."
            e.auto -> "Se puoi, riducilo e guarda cosa cambia."
            else -> "Per sapere se è davvero la causa, evitalo per 2 settimane e confronta."
        }
        return FindingText(
            big, metric(k), sentence, strength(r), reliability, r.meanWith, r.meanWithout,
            "Con", "Senza", action,
            experimentKey = if (r.verdict != Verdict.NONE) tagKey else null, experimentAvoid = !r.favorable,
        )
    }

    /** Frase concreta per le associazioni con l'umore: confronto tra il terzo di giorni più basso e più alto. */
    fun mood(label: String, a: MoodAssoc, fmtX: (Double) -> String): String {
        if (!a.clear) return "Nessun legame chiaro con l'umore per ora (${a.n} giorni)."
        val lo = a.moodLowThird; val hi = a.moodHighThird; val xl = a.xLowCut; val xh = a.xHighCut
        val s = when { abs(a.rho) < 0.3 -> "debole"; abs(a.rho) < 0.5 -> "moderato"; else -> "forte" }
        if (lo == null || hi == null || xl == null || xh == null) return "Legame $s con l'umore."
        return "Nei giorni con $label oltre ${fmtX(xh)} il tuo umore è in media ${f1(hi)} su 5, sotto ${fmtX(xl)} è ${f1(lo)}. Legame $s, su ${a.n} giorni."
    }
}
