package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.ActivityEngine
import it.cinocinorob.bioeta.science.ActivityKind
import it.cinocinorob.bioeta.science.DailyValue
import it.cinocinorob.bioeta.science.DayActivity
import it.cinocinorob.bioeta.science.LoadQuality
import it.cinocinorob.bioeta.science.LongTerm
import it.cinocinorob.bioeta.science.LongTermInputs
import it.cinocinorob.bioeta.science.PillarStatus
import it.cinocinorob.bioeta.science.Session
import it.cinocinorob.bioeta.science.SleepNight
import it.cinocinorob.bioeta.science.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class LongTermTest {
    private val z = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 10, 26)

    private fun series(n: Int, v: (Int) -> Double) = (1..n).map { DailyValue(today.minusDays(it.toLong()), v(it)) }
    private fun day(k: Int, active: Int, bouts: Int = 0) = DayActivity(
        today.minusDays(k.toLong()), 900, active, 0, 0, 0, 50.0, Zone.entries.associateWith { 0 }, LoadQuality.COMPLETE,
        shortBouts = bouts, shortBoutMin = bouts,
    )
    private fun inputs(
        vo2: List<DailyValue> = emptyList(), steps: List<DailyValue> = emptyList(), rhr: List<DailyValue> = emptyList(),
        days: List<DayActivity> = emptyList(), nights: List<SleepNight> = emptyList(), vo2Manual: Double? = null,
    ) = LongTermInputs(today, 45.0, true, vo2, vo2Manual, steps, rhr, days, nights, z)

    @Test fun stepsThresholdsFromMetaAnalysis() {
        assertEquals(PillarStatus.GOOD, LongTerm.steps(inputs(steps = series(30) { 8000.0 })).status)
        assertEquals(PillarStatus.MID, LongTerm.steps(inputs(steps = series(30) { 6000.0 })).status)
        assertEquals(PillarStatus.LOW, LongTerm.steps(inputs(steps = series(30) { 4000.0 })).status)
        assertEquals(PillarStatus.NA, LongTerm.steps(inputs(steps = series(10) { 8000.0 })).status)
    }

    @Test fun activeMinutesAreAWeeklyTotal() {
        // 30 min al giorno per 28 giorni = 210 a settimana; gli stessi minuti concentrati in 2 giorni/settimana valgono uguale
        val daily = (1..28).map { day(it, 30) }
        val p = LongTerm.active(inputs(days = daily))
        assertEquals("210", p.value); assertEquals(PillarStatus.GOOD, p.status)
        val weekend = (1..28).map { day(it, if (it % 7 < 2) 105 else 0) }
        assertEquals(PillarStatus.GOOD, LongTerm.active(inputs(days = weekend)).status)
        assertEquals(PillarStatus.LOW, LongTerm.active(inputs(days = (1..28).map { day(it, 5) })).status)
    }

    @Test fun restingHeartRateBands() {
        assertEquals(PillarStatus.GOOD, LongTerm.rhr(inputs(rhr = series(30) { 55.0 })).status)
        assertEquals(PillarStatus.MID, LongTerm.rhr(inputs(rhr = series(30) { 70.0 })).status)
        assertEquals(PillarStatus.LOW, LongTerm.rhr(inputs(rhr = series(30) { 85.0 })).status)
        // andamento: 4 bpm più bassa rispetto ai 3 mesi prima
        val trend = LongTerm.rhr(inputs(rhr = series(120) { if (it <= 30) 54.0 else 58.0 }))
        assertTrue(trend.detail, trend.detail.contains("Più bassa di 4 bpm"))
    }

    @Test fun vo2AgainstAgeReference() {
        // riferimento FRIEND a 45 anni, uomo ≈ 41 mL/kg/min
        assertEquals(PillarStatus.GOOD, LongTerm.vo2(inputs(vo2 = series(30) { 44.5 })).status)
        assertEquals(PillarStatus.MID, LongTerm.vo2(inputs(vo2 = series(30) { 39.0 })).status)
        assertEquals(PillarStatus.LOW, LongTerm.vo2(inputs(vo2 = series(30) { 35.0 })).status)
        // il valore misurato nel profilo prevale e non è una stima
        val m = LongTerm.vo2(inputs(vo2 = series(30) { 35.0 }, vo2Manual = 46.0))
        assertEquals(PillarStatus.GOOD, m.status); assertEquals(false, m.estimate)
        assertNull(LongTerm.vo2(inputs()).value)
    }

    @Test fun regularSleepIsGood() {
        val nights = (0 until 28).map { k ->
            val d = today.minusDays(k.toLong())
            SleepNight(d, d.minusDays(1).atTime(LocalTime.of(23, 0)).atZone(z).toInstant(), d.atTime(7, 30).atZone(z).toInstant(), emptyList())
        }
        val p = LongTerm.sleep(inputs(nights = nights))
        assertEquals(PillarStatus.GOOD, p.status)
    }

    @Test fun shortBoutsNeverLow() {
        val few = (1..28).map { day(it, 20, bouts = 0) }
        assertEquals(PillarStatus.MID, LongTerm.shortBouts(inputs(days = few)).status)
        val many = (1..28).map { day(it, 20, bouts = 4) }
        assertEquals(PillarStatus.GOOD, LongTerm.shortBouts(inputs(days = many)).status)
    }

    @Test fun engineCountsOnlyShortVigorousBurstsOutsideWorkouts() {
        val d = LocalDate.of(2026, 10, 20)
        fun m(h: Int, mi: Int) = Math.floorDiv(d.atTime(h, mi).atZone(z).toEpochSecond(), 60L)
        val hr = HashMap<Long, Double>()
        // tre sforzi da 1 minuto (scale), uno da 2 minuti, un blocco di 6 minuti (non è «breve»)
        listOf(m(8, 0), m(12, 0), m(18, 0)).forEach { hr[it] = 150.0 }
        hr[m(19, 0)] = 150.0; hr[m(19, 1)] = 150.0
        (0 until 6).forEach { hr[m(20, it)] = 150.0 }
        // uno sforzo breve dentro un allenamento registrato: escluso
        hr[m(7, 10)] = 150.0
        val run = Session("r", "x", ActivityKind.RUNNING, null, d.atTime(7, 0).atZone(z).toInstant(), d.atTime(7, 30).atZone(z).toInstant())
        val a = ActivityEngine.day(d, z, hr, listOf(run), 55.0, 45.0, true)
        assertEquals(4, a.shortBouts)
        assertEquals(5, a.shortBoutMin)
    }
}
