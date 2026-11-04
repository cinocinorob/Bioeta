package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.DayPart
import it.cinocinorob.bioeta.science.DayScoreCalc
import it.cinocinorob.bioeta.science.GoalState
import it.cinocinorob.bioeta.science.MonthReviewCalc
import it.cinocinorob.bioeta.science.PersonalRecord
import it.cinocinorob.bioeta.science.Sedentary
import it.cinocinorob.bioeta.science.WeightGoal
import it.cinocinorob.bioeta.science.WeightGoalCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class DailyOverviewTest {
    private val today = LocalDate.of(2026, 11, 4)

    @Test fun dayScoreIsPlainMeanOfAvailableParts() {
        val d = DayScoreCalc.compute(80, 70, 0.5, 60.0, 40.0, 80.0, false, null, 4, 4)
        // Recupero 80, Sonno 70, Movimento 50, Allenamento 100, Benessere 75 → 75
        assertEquals(75, d.score)
        assertEquals(listOf(DayPart.FUEL), d.missing)
        // carico sopra la fascia: scende piano, mai sotto 50
        val over = DayScoreCalc.compute(null, null, null, 160.0, 40.0, 80.0, false, null, null, null)
        assertEquals(50, over.parts.single().score)
        assertNull(over.score) // meno di 3 parti
        // riposo consigliato: poco carico vale pieno
        assertEquals(100, DayScoreCalc.compute(null, null, null, 5.0, 40.0, 80.0, true, null, null, null).parts.single().score)
    }

    @Test fun sedentaryWeekdayAndHour() {
        val daily = HashMap<LocalDate, Int>()
        val hourly = HashMap<LocalDate, IntArray>()
        for (k in 1L..56L) {
            val d = today.minusDays(k)
            daily[d] = if (d.dayOfWeek == DayOfWeek.SUNDAY) 3000 else 9000
            if (k <= 28) hourly[d] = IntArray(24) { h -> if (h in 15..16) 40 else if (h in 8..21) 700 else 0 }
        }
        val p = Sedentary.pattern(daily, hourly, today)
        assertEquals(DayOfWeek.SUNDAY, p.weekday)
        assertEquals(15, p.hour)
        assertEquals(1.0, p.hourShare!!, 1e-9)
        // avviso solo nella fascia e solo se finora ti sei mosso poco
        val now = List(24) { 0L }
        assertTrue(Sedentary.nudgeNow(p, 15, now))
        assertFalse(Sedentary.nudgeNow(p, 18, now))
        assertFalse(Sedentary.nudgeNow(p, 16, List(24) { h -> if (h == 15) 600L else 0L }))
    }

    @Test fun noPatternWhenMovementIsEven() {
        val daily = (1L..56L).associate { today.minusDays(it) to 8000 }
        val hourly = (1L..28L).associate { today.minusDays(it) to IntArray(24) { h -> if (h in 8..21) 600 else 0 } }
        val p = Sedentary.pattern(daily, hourly, today)
        assertNull(p.weekday); assertNull(p.hour)
    }

    @Test fun recordsOnlyImprove() {
        val saved = mapOf("steps_day" to PersonalRecord("steps_day", "x", 15000.0, "passi", today.minusDays(200)),
            "rhr7" to PersonalRecord("rhr7", "x", 52.0, "bpm", today.minusDays(200), 1))
        val fresh = listOf(PersonalRecord("steps_day", "x", 12000.0, "passi", today), PersonalRecord("rhr7", "x", 51.0, "bpm", today, 1))
        val m = MonthReviewCalc.merge(saved, fresh)
        assertEquals(15000.0, m.getValue("steps_day").value, 1e-9)   // il vecchio record resta
        assertEquals(51.0, m.getValue("rhr7").value, 1e-9)           // FC più bassa = record migliore
    }

    @Test fun weightProjection() {
        // −0,5 kg a settimana per 4 settimane, da 76 kg; obiettivo 72
        val w = (0..27 step 3).associate { k -> today.minusDays(27L - k) to 76.0 - 0.5 / 7 * k }
        val g = WeightGoal(72.0, 76.0, today.minusDays(27))
        val pr = WeightGoalCalc.project(g, w, today)
        assertEquals(GoalState.ON_TRACK, pr.state)
        assertEquals(-0.5, pr.slopeWeek!!, 1e-6)
        // mancano ~2,07 kg a 0,0714 kg/giorno → circa 29 giorni
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, pr.projected!!)
        assertTrue(days in 28..31)
        // peso fermo
        val flat = (0..27 step 3).associate { k -> today.minusDays(27L - k) to 75.0 }
        assertEquals(GoalState.STALLED, WeightGoalCalc.project(g, flat, today).state)
        // nella direzione opposta
        val up = (0..27 step 3).associate { k -> today.minusDays(27L - k) to 74.0 + 0.05 * k }
        assertEquals(GoalState.WRONG_WAY, WeightGoalCalc.project(g, up, today).state)
        // poche pesate
        assertEquals(GoalState.NEED_DATA, WeightGoalCalc.project(g, mapOf(today to 75.0), today).state)
        // raggiunto
        assertEquals(GoalState.REACHED, WeightGoalCalc.project(g, mapOf(today to 71.8), today).state)
    }

    @Test fun monthReviewComparesSameDays() {
        val steps = HashMap<LocalDate, Double>()
        for (d in 1..4) steps[LocalDate.of(2026, 11, d)] = 9000.0
        for (d in 1..31) steps[LocalDate.of(2026, 10, d)] = if (d <= 4) 6000.0 else 20000.0
        val r = MonthReviewCalc.review(today, steps, emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptySet(), YearMonth.of(2026, 11))
        assertEquals(4, r.days)
        val s = r.lines.first()
        assertEquals(9000.0, s.now!!, 1e-9)
        assertEquals(6000.0, s.before!!, 1e-9) // solo 1–4 ottobre, non il mese intero
        assertNotNull(r.lines.firstOrNull { it.label.startsWith("Giorni") })
    }
}
