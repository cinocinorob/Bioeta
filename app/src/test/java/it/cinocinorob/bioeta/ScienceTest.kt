package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.FitnessAge
import it.cinocinorob.bioeta.science.PhenoAge
import it.cinocinorob.bioeta.science.Stats
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Valori attesi calcolati con un'implementazione indipendente (Python) delle stesse equazioni.
 */
class ScienceTest {

    @Test
    fun phenoAge_healthyProfile() {
        val r = PhenoAge.compute(
            PhenoAge.Input(45.0, 45.0, 80.0, 5.0, 0.1, 30.0, 89.0, 12.8, 65.0, 6.0)
        )
        assertEquals(38.0004, r.phenoAge, 0.01)
        assertEquals(0.013633, r.mortality10y, 1e-4)
    }

    @Test
    fun phenoAge_inflamedProfile() {
        val r = PhenoAge.compute(
            PhenoAge.Input(45.0, 40.0, 100.0, 6.5, 0.5, 22.0, 94.0, 14.5, 90.0, 8.5)
        )
        assertEquals(57.444, r.phenoAge, 0.01)
    }

    @Test
    fun phenoAge_olderProfile() {
        val r = PhenoAge.compute(
            PhenoAge.Input(70.0, 42.0, 88.0, 5.5, 0.2, 28.0, 91.0, 13.5, 75.0, 6.5)
        )
        assertEquals(67.527, r.phenoAge, 0.01)
    }

    @Test
    fun contributions_sumCloseToActualChange() {
        val a = PhenoAge.Input(45.0, 45.0, 80.0, 5.0, 0.1, 30.0, 89.0, 12.8, 65.0, 6.0)
        val b = a.copy(age = 46.0, glucoseMmolL = 5.4, crpMgDl = 0.2)
        val actual = PhenoAge.compute(b).phenoAge - PhenoAge.compute(a).phenoAge
        val linear = PhenoAge.contributions(a, b).sumOf { it.years }
        assertEquals(actual, linear, 0.15)
    }

    @Test
    fun fitnessAge_matchesReferenceAnchors() {
        assertEquals(25.0, FitnessAge.fitnessAgeRaw(49.5, true), 1e-9)
        assertEquals(75.0, FitnessAge.fitnessAgeRaw(30.8, true), 1e-9)
        assertEquals(25.0, FitnessAge.fitnessAgeRaw(40.6, false), 1e-9)
        assertEquals(42.315, FitnessAge.fitnessAgeRaw(42.0, true), 0.01)
    }

    @Test
    fun egfr_ckdEpi2021() {
        assertEquals(94.588, Stats.egfrCkdEpi2021(1.0, 45.0, false), 0.01)
        assertEquals(111.222, Stats.egfrCkdEpi2021(0.8, 45.0, false), 0.01)
        assertEquals(98.948, Stats.egfrCkdEpi2021(0.7, 60.0, true), 0.01)
    }

    @Test
    fun olsSlope_basic() {
        assertEquals(2.0, Stats.olsSlope(listOf(0.0, 1.0, 2.0), listOf(1.0, 3.0, 5.0))!!, 1e-12)
    }
}
