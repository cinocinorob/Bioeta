package it.cinocinorob.bioeta.science

import kotlin.math.ln
import kotlin.math.pow

/**
 * Età "cardiorespiratoria" (fitness age) dal VO2max.
 *
 * Definizione: età alla quale il 50° percentile di riferimento del VO2max, per lo stesso sesso,
 * coincide con il VO2max della persona.
 *
 * Riferimento: Peterman JE et al., Mayo Clin Proc 2020;95(2):255-264,
 * doi:10.1016/j.mayocp.2019.06.013 (FRIEND-I, test massimali al treadmill):
 * 50° percentile 20-29 anni = 49,5 (uomini) / 40,6 (donne) mL/kg/min;
 * 70-79 anni = 30,8 / 25,0 mL/kg/min; declino medio ~9% per decade.
 *
 * Interpolazione: esponenziale tra i centri di classe 25 e 75 anni (ipotesi di declino
 * percentuale costante per decade, coerente con il ~9%/decade riportato dagli autori).
 * Fuori da 20-80 anni il valore è un'estrapolazione e viene troncato.
 *
 * Contesto prognostico: Nes BM et al., Med Sci Sports Exerc 2014;46(6):1159-65,
 * doi:10.1249/MSS.0000000000000219 — per ogni MET (3,5 mL/kg/min) in più,
 * mortalità per tutte le cause −15% negli uomini e −8% nelle donne (<60 anni).
 */
object FitnessAge {
    private const val M25 = 49.5
    private const val M75 = 30.8
    private const val F25 = 40.6
    private const val F75 = 25.0

    private fun params(male: Boolean): Pair<Double, Double> {
        val r25 = if (male) M25 else F25
        val r75 = if (male) M75 else F75
        val ratioPerDecade = (r75 / r25).pow(1.0 / 5.0)
        return r25 to ratioPerDecade
    }

    /** 50° percentile di riferimento del VO2max a una data età. */
    fun reference(age: Double, male: Boolean): Double {
        val (r25, ratio) = params(male)
        return r25 * ratio.pow((age - 25.0) / 10.0)
    }

    /** Età alla quale il riferimento eguaglia il VO2max dato (non troncata). */
    fun fitnessAgeRaw(vo2: Double, male: Boolean): Double {
        val (r25, ratio) = params(male)
        return 25.0 + 10.0 * ln(vo2 / r25) / ln(ratio)
    }

    fun fitnessAge(vo2: Double, male: Boolean): Double = fitnessAgeRaw(vo2, male).coerceIn(20.0, 80.0)

    /** Differenza dal riferimento per età, in MET (1 MET = 3,5 mL/kg/min). */
    fun metsVsReference(vo2: Double, age: Double, male: Boolean): Double =
        (vo2 - reference(age, male)) / 3.5
}
