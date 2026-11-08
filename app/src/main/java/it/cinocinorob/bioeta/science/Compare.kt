package it.cinocinorob.bioeta.science

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/*
 * Confronta due metriche (idea ripresa da Sonar: correlazioni tra qualsiasi coppia di metriche). Statistica:
 * correlazione di Spearman sui giorni in comune con IC al 95% di Fisher (SE = √(1,06/(n−3)), Fieller 1957), come per
 * l'umore; ritardo di 0 o 1 giorno (es. passi di oggi → HRV di stanotte). Per leggerla in concreto: media della
 * seconda metrica nel terzo di giorni con la prima più bassa e in quello più alta.
 * È un'associazione nei dati di una persona: i giorni non sono indipendenti e una terza variabile (malattia, viaggi,
 * stagione) può spiegare entrambe.
 */

data class CompareResult(
    val a: String, val b: String, val lag: Int, val n: Int,
    val assoc: MoodAssoc?,
    /** Serie normalizzate 0–1 sulle date in comune, per il grafico sovrapposto. */
    val seriesA: List<Pair<LocalDate, Double>>,
    val seriesB: List<Pair<LocalDate, Double>>,
)

object Compare {
    const val MIN_DAYS = MoodTrend.MIN_PAIRS

    fun compare(nameA: String, a: Map<LocalDate, Double>, nameB: String, b: Map<LocalDate, Double>, lag: Int, from: LocalDate): CompareResult {
        val pairs = a.filterKeys { !it.isBefore(from) }.mapNotNull { (d, va) -> b[d.plusDays(lag.toLong())]?.let { vb -> Triple(d, va, vb) } }.sortedBy { it.first }
        val assoc = if (pairs.size >= MIN_DAYS) MoodTrend.assoc(nameA, pairs.map { it.second to it.third }) else null
        fun norm(v: List<Double>): List<Double> { val lo = v.minOrNull() ?: 0.0; val hi = v.maxOrNull() ?: 1.0; return v.map { if (hi - lo < 1e-9) 0.5 else (it - lo) / (hi - lo) } }
        val na = norm(pairs.map { it.second }); val nb = norm(pairs.map { it.third })
        return CompareResult(nameA, nameB, lag, pairs.size, assoc, pairs.mapIndexed { i, p -> p.first to na[i] }, pairs.mapIndexed { i, p -> p.first to nb[i] })
    }

    /** Frase semplice: forza, direzione, valori concreti. */
    fun sentence(r: CompareResult, fmtA: (Double) -> String, fmtB: (Double) -> String): String {
        val a = r.assoc ?: return "Servono almeno $MIN_DAYS giorni con entrambi i valori (ora ${r.n})."
        val when_ = if (r.lag == 1) " il giorno dopo" else " lo stesso giorno"
        if (!a.clear) return "Nessun legame chiaro tra ${r.a} e ${r.b}$when_ (ρ ${String.format(Locale.ITALY, "%.2f", a.rho)}, ${r.n} giorni): l'intervallo comprende lo zero."
        val s = when { abs(a.rho) < 0.3 -> "debole"; abs(a.rho) < 0.5 -> "moderato"; else -> "forte" }
        val dir = if (a.rho > 0) "salgono insieme" else "vanno in direzioni opposte"
        val lo = a.moodLowThird; val hi = a.moodHighThird; val xl = a.xLowCut; val xh = a.xHighCut
        val concrete = if (lo != null && hi != null && xl != null && xh != null)
            " Nei giorni con ${r.a} sotto ${fmtA(xl)}, ${r.b}$when_ è in media ${fmtB(lo)}; sopra ${fmtA(xh)} è ${fmtB(hi)}." else ""
        return "Legame $s: ${r.a} e ${r.b}$when_ $dir.$concrete"
    }
}

// ---------------- Controlli consigliati ----------------

enum class CheckState(val label: String) { DUE("Da fare"), OK("In regola"), INFO("Da sapere"), NOT_YET("Non ancora") }

data class Checkup(val title: String, val why: String, val when_: String, val state: CheckState, val source: String)

/**
 * Controlli e vaccini consigliati per età e sesso (idea ripresa da Sonar), con le regole italiane ed europee:
 * screening oncologici del SSN (colon-retto 50–69 anni, sangue occulto ogni 2 anni; mammografia 50–69 ogni 2 anni,
 * in alcune Regioni 45–74; cervice 25–65, Pap test ogni 3 anni o HPV test ogni 5 dai 30–35); vaccini del PNPV
 * 2023–25 (dTpa ogni 10 anni; pneumococco e herpes zoster dai 65 anni; influenza secondo la circolare annuale);
 * pressione: almeno ogni anno dai 40 anni (ESC 2024); valutazione del rischio cardiovascolare (SCORE2) da considerare
 * negli uomini oltre i 40 e nelle donne oltre i 50 (ESC 2021). Non sostituisce il medico di base.
 */
object Checkups {
    fun list(age: Double?, male: Boolean, lastBp: LocalDate?, lastLipids: LocalDate?, lastGlucose: LocalDate?, today: LocalDate): List<Checkup> {
        if (age == null) return emptyList()
        val out = mutableListOf<Checkup>()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)
        fun since(d: LocalDate?, months: Long) = d != null && !d.isBefore(today.minusMonths(months))
        // Pressione
        if (age >= 18) out += Checkup(
            "Pressione arteriosa",
            "L'ipertensione non dà sintomi ed è il primo fattore di rischio cardiovascolare modificabile.",
            if (age >= 40) "Almeno una volta l'anno" else "Almeno ogni 3 anni",
            if (since(lastBp, if (age >= 40) 12 else 36)) CheckState.OK else CheckState.DUE,
            "ESC 2024",
        ).let { c -> if (lastBp != null) c.copy(when_ = c.when_ + " · ultima misura ${lastBp.format(fmt)}") else c }
        // Rischio cardiovascolare
        val cvAge = if (male) 40.0 else 50.0
        out += if (age >= cvAge) Checkup(
            "Rischio cardiovascolare (SCORE2)",
            "Il medico stima il rischio a 10 anni da età, pressione, colesterolo e fumo.",
            "Colesterolo totale e HDL negli ultimi 5 anni" + (lastLipids?.let { " · ultimi lipidi ${it.format(fmt)}" } ?: ""),
            if (since(lastLipids, 60)) CheckState.OK else CheckState.DUE, "ESC 2021",
        ) else Checkup("Rischio cardiovascolare (SCORE2)", "Valutazione sistematica consigliata dai ${cvAge.toInt()} anni.", "Dai ${cvAge.toInt()} anni", CheckState.NOT_YET, "ESC 2021")
        // Glicemia
        if (age >= 35) out += Checkup(
            "Glicemia o emoglobina glicata",
            "Il prediabete non dà sintomi; si scopre con un esame del sangue.",
            "Ogni 3 anni se normale" + (lastGlucose?.let { " · ultimo ${it.format(fmt)}" } ?: ""),
            if (since(lastGlucose, 36)) CheckState.OK else CheckState.DUE, "ADA 2025",
        )
        // Screening oncologici SSN
        out += if (age >= 50 && age < 70) Checkup("Screening del colon-retto", "Sangue occulto nelle feci: riduce la mortalità per tumore del colon.",
            "Ogni 2 anni dai 50 ai 69 (invito della ASL)", CheckState.INFO, "Ministero della Salute")
        else if (age < 50) Checkup("Screening del colon-retto", "Sangue occulto nelle feci ogni 2 anni.", "Dai 50 anni (invito della ASL)", CheckState.NOT_YET, "Ministero della Salute")
        else Checkup("Screening del colon-retto", "Dopo i 69 anni si decide con il medico.", "Parlane con il medico", CheckState.INFO, "Ministero della Salute")
        if (!male) {
            out += Checkup("Mammografia", "Screening del tumore al seno.", if (age >= 45 && age < 75) "Ogni 2 anni dai 50 ai 69 (in alcune Regioni 45–74)" else "Dai 50 anni (in alcune Regioni dai 45)",
                if (age >= 50 && age < 70) CheckState.INFO else CheckState.NOT_YET, "Ministero della Salute")
            if (age >= 25 && age <= 65) out += Checkup("Pap test o HPV test", "Screening del tumore della cervice.", "Pap test ogni 3 anni (25–29), HPV test ogni 5 anni dai 30–35 ai 65", CheckState.INFO, "Ministero della Salute")
        }
        // Vaccini
        out += Checkup("Richiamo difterite-tetano-pertosse", "Il tetano si prende anche da una piccola ferita.", "Ogni 10 anni", CheckState.INFO, "PNPV 2023–25")
        out += Checkup("Influenza", "Ogni autunno; gratuita per le categorie e le età indicate nella circolare annuale del Ministero.", "Ogni anno, da ottobre", CheckState.INFO, "Circolare annuale")
        out += if (age >= 65) Checkup("Pneumococco e herpes zoster", "Offerti gratuitamente a 65 anni.", "Una volta, a 65 anni", CheckState.INFO, "PNPV 2023–25")
        else Checkup("Pneumococco e herpes zoster", "Offerti a 65 anni (prima solo con fattori di rischio).", "A 65 anni", CheckState.NOT_YET, "PNPV 2023–25")
        return out
    }
}
