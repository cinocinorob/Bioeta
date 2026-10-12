package it.cinocinorob.bioeta.science

import kotlin.math.exp
import kotlin.math.ln

/**
 * Phenotypic Age (PhenoAge).
 *
 * Fonti:
 *  - Levine ME et al., "An epigenetic biomarker of aging for lifespan and healthspan",
 *    Aging 2018;10(4):573-591, doi:10.18632/aging.101414 (Tabella 1: pesi dei 9 biomarcatori).
 *  - Liu Z et al., PLoS Med 2018;15(12):e1002718, doi:10.1371/journal.pmed.1002718
 *    (derivazione/validazione della Phenotypic Age su NHANES III -> NHANES IV).
 *
 * Modello: Gompertz a rischi proporzionali addestrato su NHANES III (n = 9.926),
 * rischio di morte a 10 anni (120 mesi) convertito in "anni".
 * Unità del modello: albumina g/L, creatinina µmol/L, glucosio mmol/L, PCR mg/dL (ln),
 * linfociti %, MCV fL, RDW %, fosfatasi alcalina U/L, globuli bianchi 10^3/µL.
 */
object PhenoAge {

    data class Input(
        val age: Double,
        val albuminGL: Double,
        val creatinineUmolL: Double,
        val glucoseMmolL: Double,
        val crpMgDl: Double,
        val lymphPct: Double,
        val mcvFl: Double,
        val rdwPct: Double,
        val alpUL: Double,
        val wbcK: Double,
    )

    data class Result(
        val phenoAge: Double,
        /** Rischio di mortalità a 10 anni stimato dal modello (popolazione USA NHANES). */
        val mortality10y: Double,
        val xb: Double,
    )

    const val INTERCEPT = -19.907
    const val B_ALB = -0.0336
    const val B_CREAT = 0.0095
    const val B_GLU = 0.1953
    const val B_LNCRP = 0.0954
    const val B_LYMPH = -0.0120
    const val B_MCV = 0.0268
    const val B_RDW = 0.3306
    const val B_ALP = 0.00188
    const val B_WBC = 0.0554
    const val B_AGE = 0.0804

    private const val GAMMA = 0.0076927
    private const val B_CONV = 0.09165

    /** Anni di PhenoAge per unità di predittore lineare (approssimazione valida per M piccolo). */
    const val YEARS_PER_XB = 1.0 / B_CONV

    fun xb(i: Input): Double {
        require(i.crpMgDl > 0.0) { "La PCR deve essere > 0 per il logaritmo" }
        return INTERCEPT +
            B_ALB * i.albuminGL +
            B_CREAT * i.creatinineUmolL +
            B_GLU * i.glucoseMmolL +
            B_LNCRP * ln(i.crpMgDl) +
            B_LYMPH * i.lymphPct +
            B_MCV * i.mcvFl +
            B_RDW * i.rdwPct +
            B_ALP * i.alpUL +
            B_WBC * i.wbcK +
            B_AGE * i.age
    }

    fun compute(i: Input): Result {
        val x = xb(i)
        var m = 1.0 - exp(-exp(x) * (exp(120.0 * GAMMA) - 1.0) / GAMMA)
        m = m.coerceIn(1e-12, 1.0 - 1e-12)
        val pa = 141.50225 + ln(-0.00553 * ln(1.0 - m)) / B_CONV
        return Result(pa, m, x)
    }

    data class Contribution(val label: String, val years: Double)

    /**
     * Scompone la variazione di PhenoAge tra due prelievi in contributi per biomarcatore,
     * usando l'approssimazione lineare dPhenoAge ≈ Δxb / 0.09165.
     * Il residuo (non linearità) è restituito separatamente.
     */
    fun contributions(a: Input, b: Input): List<Contribution> {
        val k = YEARS_PER_XB
        return listOf(
            Contribution("Albumina", B_ALB * (b.albuminGL - a.albuminGL) * k),
            Contribution("Creatinina", B_CREAT * (b.creatinineUmolL - a.creatinineUmolL) * k),
            Contribution("Glucosio", B_GLU * (b.glucoseMmolL - a.glucoseMmolL) * k),
            Contribution("PCR", B_LNCRP * (ln(b.crpMgDl) - ln(a.crpMgDl)) * k),
            Contribution("Linfociti %", B_LYMPH * (b.lymphPct - a.lymphPct) * k),
            Contribution("MCV", B_MCV * (b.mcvFl - a.mcvFl) * k),
            Contribution("RDW", B_RDW * (b.rdwPct - a.rdwPct) * k),
            Contribution("Fosfatasi alcalina", B_ALP * (b.alpUL - a.alpUL) * k),
            Contribution("Globuli bianchi", B_WBC * (b.wbcK - a.wbcK) * k),
            Contribution("Età anagrafica", B_AGE * (b.age - a.age) * k),
        )
    }
}
