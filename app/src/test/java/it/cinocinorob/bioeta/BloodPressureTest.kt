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

    /** 7 giorni di protocollo completo: 2 misure al mattino e 2 alla sera. */
    private fun week(sys: Int, dia: Int, start: LocalDateTime = LocalDateTime.of(2026, 10, 1, 7, 0)) = (0 until 7).flatMap { d ->
        val day = start.plusDays(d.toLong())
        listOf(day, day.plusMinutes(1), day.withHour(21), day.withHour(21).plusMinutes(1)).map { BpReading(time = it, sys = sys, dia = dia) }
    }

    @Test
    fun insightOnlyAddsInterventionsWhenElevated() {
        val normal = BloodPressure.summarize(week(112, 66))!!
        val ins = InsightEngine.build(InsightInput(45.0, true, bp = normal)).first { it.id == "bp" }
        assertEquals(Level.POSITIVE, ins.level)
        assertEquals(2, ins.evidence.size)
        val high = BloodPressure.summarize(week(142, 88))!!
        val ins2 = InsightEngine.build(InsightInput(45.0, true, bp = high)).first { it.id == "bp" }
        assertEquals(Level.ALERT, ins2.level)
        assertFalse(ins2.evidence.size <= 2)
    }

    @Test
    fun fewReadingsAreProvisionalAndNeverAlarm() {
        val start = LocalDateTime.of(2026, 10, 1, 7, 0)
        // una sola misura alta: categoria indicativa, livello «attenzione», non «allarme»
        val one = BloodPressure.summarize(listOf(BpReading(time = start, sys = 150, dia = 92)))!!
        assertTrue(one.provisional)
        assertEquals(BpCategory.HYPERTENSION, one.category)
        assertEquals(Level.ATTENTION, InsightEngine.build(InsightInput(45.0, true, bp = one)).first { it.id == "bp" }.level)
        // valori molto elevati restano un allarme anche con una sola misura
        val severe = BloodPressure.summarize(listOf(BpReading(time = start, sys = 185, dia = 112)))!!
        assertEquals(Level.ALERT, InsightEngine.build(InsightInput(45.0, true, bp = severe)).first { it.id == "bp" }.level)
        // poche misure normali: non si promette «tutto bene»
        val few = BloodPressure.summarize((0 until 5).map { BpReading(time = start.plusDays(it.toLong()), sys = 112, dia = 66) })!!
        assertTrue(few.provisional)
        assertEquals(Level.ATTENTION, InsightEngine.build(InsightInput(45.0, true, bp = few)).first { it.id == "bp" }.level)
    }

    @Test
    fun windowDatesAreReported() {
        val s = BloodPressure.summarize(week(120, 75))!!
        // primo giorno escluso: la media va dal 2 al 7 ottobre
        assertEquals(java.time.LocalDate.of(2026, 10, 2), s.from)
        assertEquals(java.time.LocalDate.of(2026, 10, 7), s.to)
        assertFalse(s.provisional)
    }

    @Test
    fun interArmDifference() {
        val start = LocalDateTime.of(2026, 10, 1, 7, 0)
        val rs = (0 until 4).flatMap { d ->
            val t = start.plusDays(d.toLong())
            listOf(BpReading(time = t, sys = 138, dia = 82, arm = "destro"), BpReading(time = t.plusMinutes(2), sys = 125, dia = 80, arm = "sinistro"))
        }
        val s = BloodPressure.summarize(rs)!!
        assertEquals(13.0, s.interArm!!, 1e-9)
        assertEquals("destro", s.higherArm)
        // meno di 3 coppie: nessuna stima
        assertNull(BloodPressure.summarize(rs.take(4))!!.interArm)
        // misure sullo stesso braccio non formano coppie
        assertNull(BloodPressure.summarize(rs.map { it.copy(arm = "sinistro") })!!.interArm)
    }
}
