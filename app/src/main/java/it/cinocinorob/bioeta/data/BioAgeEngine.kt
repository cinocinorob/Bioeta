package it.cinocinorob.bioeta.data

import it.cinocinorob.bioeta.science.PhenoAge
import it.cinocinorob.bioeta.science.Stats
import java.time.LocalDate

/** Esito del calcolo PhenoAge per un singolo prelievo. */
data class PhenoPoint(
    val panel: Panel,
    val age: Double,
    val input: PhenoAge.Input,
    val result: PhenoAge.Result,
    val warnings: List<String>,
)

data class PhenoAttempt(
    val point: PhenoPoint?,
    val missing: List<String>,
    val warnings: List<String>,
)

object BioAgeEngine {

    /** Albumina in g/dL: diretta, oppure derivata da elettroforesi (% × proteine totali). */
    fun albuminGdl(panel: Panel): Pair<Double, Boolean>? {
        panel.values[Analytes.ALBUMIN]?.let { return it.value to false }
        val pct = panel.values[Analytes.ALBUMIN_PCT]?.value
        val tp = panel.values[Analytes.TOTAL_PROTEIN]?.value
        if (pct != null && tp != null) return (pct / 100.0 * tp) to true
        return null
    }

    fun attempt(panel: Panel, profile: Profile): PhenoAttempt {
        val missing = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val age = profile.ageAt(panel.date)
        if (age == null) missing += "data di nascita (Impostazioni)"

        fun v(key: String): LabValue? {
            val x = panel.values[key]
            if (x == null) missing += (Analytes.get(key)?.name ?: key)
            return x
        }

        val alb = albuminGdl(panel)
        if (alb == null) missing += "Albumina (o albumina % + proteine totali)"
        val cre = v(Analytes.CREATININE)
        val glu = v(Analytes.GLUCOSE)
        val crp = v(Analytes.CRP)
        val lym = v(Analytes.LYMPH_PCT)
        val mcv = v(Analytes.MCV)
        val rdw = v(Analytes.RDW)
        val alp = v(Analytes.ALP)
        val wbc = v(Analytes.WBC)

        if (missing.isNotEmpty() || age == null || alb == null || cre == null || glu == null ||
            crp == null || lym == null || mcv == null || rdw == null || alp == null || wbc == null
        ) return PhenoAttempt(null, missing, warnings)

        if (alb.second) warnings += "Albumina derivata dall'elettroforesi: il metodo densitometrico non coincide con quello colorimetrico usato in NHANES; possibile scostamento sistematico."
        if (crp.belowLimit) warnings += "PCR sotto il limite di rilevabilità: usato il limite (sovrastima prudenziale). Con PCR standard, non hs, la risoluzione nel range basso è scarsa."
        if (crp.value > 10.0) warnings += "PCR > 10 mg/L: probabile flogosi acuta. La PhenoAge di questo prelievo non è rappresentativa dello stato basale."
        if (crp.value <= 0.0) return PhenoAttempt(null, listOf("PCR > 0"), warnings)
        if (age < 20 || age > 85) warnings += "Età fuori dall'intervallo ben rappresentato nel campione di addestramento."

        val input = PhenoAge.Input(
            age = age,
            albuminGL = alb.first * 10.0,
            creatinineUmolL = cre.value * 88.42,
            glucoseMmolL = glu.value / 18.016,
            crpMgDl = crp.value / 10.0,
            lymphPct = lym.value,
            mcvFl = mcv.value,
            rdwPct = rdw.value,
            alpUL = alp.value,
            wbcK = wbc.value,
        )
        val res = PhenoAge.compute(input)
        return PhenoAttempt(PhenoPoint(panel, age, input, res, warnings), emptyList(), warnings)
    }

    fun series(panels: List<Panel>, profile: Profile): List<PhenoPoint> =
        panels.sortedBy { it.date }.mapNotNull { attempt(it, profile).point }

    /**
     * Ritmo di invecchiamento: pendenza di PhenoAge su età anagrafica (anni/anno).
     * Richiede ≥3 prelievi su ≥12 mesi; valori singoli molto rumorosi (variabilità biologica).
     */
    fun pace(points: List<PhenoPoint>): Double? {
        if (points.size < 3) return null
        val span = points.last().age - points.first().age
        if (span < 1.0) return null
        return Stats.olsSlope(points.map { it.age }, points.map { it.result.phenoAge })
    }

    fun egfr(panel: Panel, profile: Profile): Double? {
        val cr = panel.values[Analytes.CREATININE]?.value ?: return null
        val age = profile.ageAt(panel.date) ?: return null
        return Stats.egfrCkdEpi2021(cr, age, profile.sex == Sex.F)
    }

    /** Serie temporale di un analita (inclusi valori derivati). */
    fun analyteSeries(key: String, panels: List<Panel>, profile: Profile): List<Pair<LocalDate, LabValue>> =
        panels.sortedBy { it.date }.mapNotNull { p ->
            when (key) {
                Analytes.EGFR -> egfr(p, profile)?.let { p.date to LabValue(it) }
                Analytes.ALBUMIN -> albuminGdl(p)?.let { p.date to LabValue(it.first) }
                else -> p.values[key]?.let { p.date to it }
            }
        }

    /** Effetto marginale su PhenoAge (anni) per unità canonica, se l'analita è nel modello. */
    fun phenoSensitivity(key: String): String? {
        val k = PhenoAge.YEARS_PER_XB
        fun f(x: Double) = String.format(java.util.Locale.ITALY, "%+.2f", x)
        return when (key) {
            Analytes.ALBUMIN -> "${f(PhenoAge.B_ALB * 10.0 * k)} anni per +1 g/dL"
            Analytes.CREATININE -> "${f(PhenoAge.B_CREAT * 88.42 * 0.1 * k)} anni per +0,1 mg/dL"
            Analytes.GLUCOSE -> "${f(PhenoAge.B_GLU / 18.016 * 10.0 * k)} anni per +10 mg/dL"
            Analytes.CRP -> "${f(PhenoAge.B_LNCRP * kotlin.math.ln(2.0) * k)} anni per raddoppio della PCR"
            Analytes.LYMPH_PCT -> "${f(PhenoAge.B_LYMPH * 5.0 * k)} anni per +5 punti %"
            Analytes.MCV -> "${f(PhenoAge.B_MCV * 5.0 * k)} anni per +5 fL"
            Analytes.RDW -> "${f(PhenoAge.B_RDW * 1.0 * k)} anni per +1 punto %"
            Analytes.ALP -> "${f(PhenoAge.B_ALP * 20.0 * k)} anni per +20 U/L"
            Analytes.WBC -> "${f(PhenoAge.B_WBC * 1.0 * k)} anni per +1000/µL"
            else -> null
        }
    }
}
