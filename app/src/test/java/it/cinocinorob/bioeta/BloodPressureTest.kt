package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.BloodPressure
import it.cinocinorob.bioeta.science.BpCategory
import it.cinocinorob.bioeta.science.BpReading
import it.cinocinorob.bioeta.science.InsightEngine
import it.cinocinorob.bioeta.science.InsightInput
import it.cinocinorob.bioeta.science.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BloodPressureTest {
    @Test
    fun homeThresholdsEsc2024() {
        assertEquals(BpCategory.NON_ELEVATED, BloodPressure.classify(118.0, 68.0))
        assertEquals(BpCategory.ELEVATED, BloodPressure.classify(125.0, 68.0))
        assertEquals(BpCategory.ELEVATED, BloodPressure.classify(118.0, 72.0))
        assertEquals(BpCategory.HYPERTENSION, BloodPressure.classify(136.0, 80.0))
        assertEquals(BpCategory.HYPERTENSION, BloodPressure.classify(130.0, 86.0))
        assertEquals(BpCategory.HIGH, BloodPressure.classify(165.0, 90.0))
        assertEquals(BpCategory.SEVERE, BloodPressure.classify(150.0, 112.0))
    }

    @Test
    fun protocolDropsFirstDayAndAveragesMorningEvening() {
        val start = LocalDateTime.of(2026, 10, 1, 7, 0)
        val rs = (0 until 7).flatMap { d ->
            val day = start.plusDays(d.toLong())
            // primo giorno più alto (effetto di adattamento): va escluso
            val bump = if (d == 0) 20 else 0
            listOf(
                BpReading(time = day, sys = 130 + bump, dia = 82),
                BpReading(time = day.plusMinutes(1), sys = 128 + bump, dia = 80),
                BpReading(time = day.withHour(21), sys = 124 + bump, dia = 78),
                BpReading(time = day.withHour(21).plusMinutes(1), sys = 122 + bump, dia = 76),
            )
        }
        val s = BloodPressure.summarize(rs)!!
        assertEquals(24, s.n)
        assertEquals(6, s.days)
        assertEquals(126.0, s.meanSys, 1e-9)
        assertEquals(79.0, s.meanDia, 1e-9)
        assertEquals(129.0, s.morning!!.first, 1e-9)
        assertEquals(123.0, s.evening!!.first, 1e-9)
        assertTrue(s.protocolComplete)
        assertEquals(BpCategory.ELEVATED, s.category)
        assertNull(s.trendSys) // meno di 21 giorni
    }

    @Test
    fun plausibilityChecks() {
        assertNotNull(BloodPressure.plausible(80, 90, null))
        assertNotNull(BloodPressure.plausible(300, 90, null))
        assertNull(BloodPressure.plausible(120, 80, 60))
    }

    @Test
    fun insightOnlyAddsInterventionsWhenElevated() {
        val start = LocalDateTime.of(2026, 10, 1, 7, 0)
        val normal = BloodPressure.summarize((0 until 5).map { BpReading(time = start.plusDays(it.toLong()), sys = 112, dia = 66) })!!
        val ins = InsightEngine.build(InsightInput(45.0, true, bp = normal)).first { it.id == "bp" }
        assertEquals(Level.POSITIVE, ins.level)
        assertEquals(2, ins.evidence.size)
        val high = BloodPressure.summarize((0 until 5).map { BpReading(time = start.plusDays(it.toLong()), sys = 142, dia = 88) })!!
        val ins2 = InsightEngine.build(InsightInput(45.0, true, bp = high)).first { it.id == "bp" }
        assertEquals(Level.ALERT, ins2.level)
        assertFalse(ins2.evidence.size <= 2)
    }
}
