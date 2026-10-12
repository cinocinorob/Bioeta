package it.cinocinorob.bioeta.science

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object Stats {
    fun mean(xs: List<Double>): Double? = if (xs.isEmpty()) null else xs.sum() / xs.size

    /** Deviazione standard campionaria (n-1). */
    fun sd(xs: List<Double>): Double? {
        if (xs.size < 2) return null
        val m = xs.sum() / xs.size
        return sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
    }

    fun median(xs: List<Double>): Double? {
        if (xs.isEmpty()) return null
        val s = xs.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    /** Pendenza dei minimi quadrati ordinari (y su x). */
    fun olsSlope(xs: List<Double>, ys: List<Double>): Double? {
        if (xs.size != ys.size || xs.size < 3) return null
        val mx = xs.average()
        val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        if (sxx == 0.0) return null
        val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        return sxy / sxx
    }

    /**
     * eGFR CKD-EPI 2021 (senza coefficiente razziale).
     * Inker LA et al., N Engl J Med 2021;385:1737-49, doi:10.1056/NEJMoa2102953.
     */
    fun egfrCkdEpi2021(creatinineMgDl: Double, age: Double, female: Boolean): Double {
        val k = if (female) 0.7 else 0.9
        val a = if (female) -0.241 else -0.302
        val r = creatinineMgDl / k
        return 142.0 * min(r, 1.0).pow(a) * max(r, 1.0).pow(-1.200) *
            0.9938.pow(age) * (if (female) 1.012 else 1.0)
    }
}
