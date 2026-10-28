package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.DailyValue
import it.cinocinorob.bioeta.science.Drink
import it.cinocinorob.bioeta.science.Meal
import it.cinocinorob.bioeta.science.NutStatus
import it.cinocinorob.bioeta.science.Nutrition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class NutritionTest {
    private val z = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 10, 28)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(z).toInstant()
    private fun meal(d: LocalDate, h: Int, kcal: Double, origin: String = "mfp", prot: Double = 30.0, fib: Double? = 8.0, caf: Double? = null, na: Double? = 600.0) =
        Meal(at(d, h), origin, 0, kcal, prot, kcal * 0.5 / 4, kcal * 0.3 / 9, kcal * 0.08 / 9, fib, 20.0, na, 1200.0, caf)

    @Test fun oneAppPerDayAvoidsDoubleCounting() {
        val d = today.minusDays(1)
        val meals = listOf(meal(d, 8, 500.0), meal(d, 13, 800.0), meal(d, 20, 700.0),
            // la stessa giornata copiata da un'altra app
            meal(d, 8, 500.0, "fitbit"), meal(d, 13, 800.0, "fitbit"))
        val day = Nutrition.days(meals, emptyList(), z).single()
        assertEquals("mfp", day.origin)
        assertEquals(2000.0, day.kcal, 1e-9)
        assertEquals(3, day.records)
        assertEquals(LocalTime.of(20, 0), day.lastMeal)
    }

    @Test fun partialDaysAreNotComplete() {
        val d = today.minusDays(1)
        assertFalse(Nutrition.days(listOf(meal(d, 8, 1500.0)), emptyList(), z).single().complete)
        assertFalse(Nutrition.days(listOf(meal(d, 8, 400.0), meal(d, 13, 400.0)), emptyList(), z).single().complete)
        assertTrue(Nutrition.days(listOf(meal(d, 8, 500.0), meal(d, 13, 700.0)), emptyList(), z).single().complete)
    }

    @Test fun expenditureFromIntakeAndWeightTrend() {
        // 2.500 kcal al giorno e peso che scende di 0,5 kg a settimana → dispendio ≈ 2.500 + 0,5/7·7.700 = 3.050
        val days = (1..28).flatMap { k -> val d = today.minusDays(k.toLong()); listOf(meal(d, 8, 1000.0), meal(d, 13, 1500.0)) }
        val nd = Nutrition.days(days, emptyList(), z)
        val w = (1..28 step 3).map { k -> DailyValue(today.minusDays(k.toLong()), 75.0 - (28 - k) * 0.5 / 7) }
        val e = Nutrition.expenditure(nd, w, today.minusDays(28), today.minusDays(1), emptyMap())
        assertNotNull(e)
        assertEquals(3050.0, e!!.kcal, 1.0)
        assertEquals(-0.5, e.weightPerWeek, 1e-6)
        assertEquals(e.kcal, e.low, 1.0) // nessun rumore → intervallo nullo
        // poche pesate → nessuna stima
        assertNull(Nutrition.expenditure(nd, w.take(3), today.minusDays(28), today.minusDays(1), emptyMap()))
    }

    @Test fun targetsAgainstReferences() {
        val meals = (1..14).flatMap { k -> val d = today.minusDays(k.toLong()); listOf(meal(d, 8, 900.0, prot = 45.0, fib = 14.0), meal(d, 20, 1200.0, prot = 55.0, fib = 14.0, na = 2400.0)) }
        val drinks = (1..14).map { Drink(at(today.minusDays(it.toLong()), 10), "x", 2.1) }
        val s = Nutrition.summarize(meals, drinks, listOf(DailyValue(today.minusDays(2), 72.0)), emptyList(), emptyMap(), today, z, true) { it }
        val t = s.targets.associateBy { it.key }
        assertEquals(14, s.completeDays)
        assertEquals(NutStatus.GOOD, t.getValue("protein").status) // 100 g / 72 kg ≈ 1,4 g/kg
        assertEquals(NutStatus.GOOD, t.getValue("fiber").status)   // 28 g
        assertEquals(NutStatus.MID, t.getValue("sodium").status)   // 600 + 2.400 = 3.000 mg: sopra 2.000, entro 3.000
        assertEquals(NutStatus.INFO, t.getValue("sugar").status)
        assertEquals(NutStatus.GOOD, t.getValue("water").status)
    }

    @Test fun medianTimeAcrossMidnight() {
        val m = Nutrition.medianTime(listOf(LocalTime.of(23, 30), LocalTime.of(0, 30), LocalTime.of(23, 50)))
        assertEquals(LocalTime.of(23, 50), m)
    }
}
