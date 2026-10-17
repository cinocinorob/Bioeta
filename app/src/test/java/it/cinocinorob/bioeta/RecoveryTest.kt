package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.DailyValue
import it.cinocinorob.bioeta.science.Readiness
import it.cinocinorob.bioeta.science.SleepNight
import it.cinocinorob.bioeta.science.SleepScore
import it.cinocinorob.bioeta.science.Stage
import it.cinocinorob.bioeta.science.StageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class RecoveryTest {
    private val zone: ZoneId = ZoneOffset.UTC

    /** Notte 23:00–07:00: 15' per addormentarsi, un risveglio di 10', fasi regolari. */
    private fun night(date: LocalDate, hr: Double = 55.0, shiftMin: Long = 0): SleepNight {
        val start = date.minusDays(1).atTime(23, 0).atZone(zone).toInstant().plusSeconds(shiftMin * 60)
        fun at(min: Long) = start.plusSeconds(min * 60)
        val st = listOf(
            Stage(at(0), at(15), StageType.AWAKE),
            Stage(at(15), at(105), StageType.LIGHT),
            Stage(at(105), at(165), StageType.DEEP),
            Stage(at(165), at(225), StageType.REM),
            Stage(at(225), at(235), StageType.AWAKE),
            Stage(at(235), at(345), StageType.LIGHT),
            Stage(at(345), at(390), StageType.DEEP),
            Stage(at(390), at(480), StageType.REM),
        )
        return SleepNight(date, start, at(480), st, avgHr = hr)
    }

    @Test
    fun nightMetrics() {
        val n = night(LocalDate.of(2026, 10, 10))
        assertEquals(480.0, n.timeInBedMin, 1e-9)
        assertEquals(455.0, n.totalSleepMin, 1e-9)
        assertEquals(15.0, n.latencyMin!!, 1e-9)
        assertEquals(10.0, n.wasoMin!!, 1e-9)
        assertEquals(1, n.awakenings)
        assertEquals(455.0 / 480 * 100, n.efficiency!!, 1e-9)
        assertEquals(105.0 / 455 * 100, n.stagePct(StageType.DEEP)!!, 1e-9)
    }

    @Test
    fun perfectRegularNightScores97() {
        val d = LocalDate.of(2026, 10, 10)
        val hist = (1..14).map { night(d.minusDays(it.toLong())) }
        val r = SleepScore.compute(night(d), hist, age = 45.0, zone = zone)
        // 100·(0,30+0,15+0,15+0,15+0,05) + 85·(0,10+0,10) = 97 (baseline senza variabilità → z = 0)
        assertEquals(97, r.score)
        assertEquals(100, r.contributors.first { it.key == "regularity" }.score)
    }

    @Test
    fun shortNightLowersDurationOnly() {
        assertEquals(100.0, SleepScore.durationScore(7.5, 45.0), 1e-9)
        assertEquals(50.0, SleepScore.durationScore(5.5, 45.0), 1e-9)
        assertEquals(0.0, SleepScore.durationScore(3.5, 45.0), 1e-9)
        assertEquals(85.0, SleepScore.durationScore(10.0, 45.0), 1e-9)
    }

    @Test
    fun irregularScheduleLowersSri() {
        val d = LocalDate.of(2026, 10, 10)
        val nights = (0..7).map { night(d.minusDays(it.toLong()), shiftMin = if (it % 2 == 0) 0 else 240) }
        val sri = SleepScore.sleepRegularityIndex(nights, d, zone)!!
        assertTrue("SRI $sri", sri < 40)
    }

    @Test
    fun trimpMatchesBanisterFormula() {
        assertEquals(130.74, Readiness.trimp(60.0, 150.0, 60.0, 45.0, true), 0.01)
    }

    @Test
    fun readinessFlagsIllnessPattern() {
        val d = LocalDate.of(2026, 10, 10)
        val hrv = (1..40).map { DailyValue(d.minusDays(it.toLong()), if (it % 2 == 0) 48.0 else 52.0) } + DailyValue(d, 30.0)
        val rhr = (1..40).map { DailyValue(d.minusDays(it.toLong()), if (it % 2 == 0) 54.0 else 56.0) } + DailyValue(d, 64.0)
        val r = Readiness.compute(
            Readiness.Inputs(d, 45.0, true, hrv, rhr, emptyList(), emptyList(), 80, emptyList())
        )
        assertNotNull(r.score)
        assertTrue(r.score!! <= 45)
        assertTrue(r.flags.isNotEmpty())
    }

    @Test
    fun readinessNeedsBaseline() {
        val d = LocalDate.of(2026, 10, 10)
        val r = Readiness.compute(Readiness.Inputs(d, 45.0, true, listOf(DailyValue(d, 50.0)), emptyList(), emptyList(), emptyList(), 80, emptyList()))
        assertNull(r.score)
        assertTrue(r.calibrating)
    }

    @Test
    fun readinessProvisionalWithShortHistory() {
        // 5 giorni di storico: punteggio disponibile ma marcato come provvisorio (baseline < 14 giorni)
        val d = LocalDate.of(2026, 10, 10)
        val hrv = (1..5).map { DailyValue(d.minusDays(it.toLong()), 45.0 + it) } + DailyValue(d, 47.0)
        val rhr = (1..5).map { DailyValue(d.minusDays(it.toLong()), 56.0 + (it % 2)) } + DailyValue(d, 56.0)
        val r = Readiness.compute(Readiness.Inputs(d, 45.0, true, hrv, rhr, emptyList(), emptyList(), 80, emptyList()))
        assertNotNull(r.score)
        assertTrue(r.provisional)
        assertEquals(5, r.baselineDays)
    }
}
