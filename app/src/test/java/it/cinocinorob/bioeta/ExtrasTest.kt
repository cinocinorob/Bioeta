package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.Bedtime
import it.cinocinorob.bioeta.science.DailyValue
import it.cinocinorob.bioeta.science.DayLoad
import it.cinocinorob.bioeta.science.LoadTrendCalc
import it.cinocinorob.bioeta.science.LoadTrendState
import it.cinocinorob.bioeta.science.SleepNight
import it.cinocinorob.bioeta.science.VitalState
import it.cinocinorob.bioeta.science.Vitals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class ExtrasTest {
    private val z = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 13)

    private fun night(d: LocalDate, bed: LocalTime, wake: LocalTime) =
        SleepNight(d, d.minusDays(1).atTime(bed).toInstant(z), d.atTime(wake).toInstant(z), emptyList())

    @Test fun bedtimeAnchorsOnWake() {
        val nights = (0 until 10).map { night(today.minusDays(it.toLong()), LocalTime.of(23, 0), LocalTime.of(7, 0)) }
        val g = Bedtime.guide(nights, today, 45.0, z)
        assertNotNull(g)
        assertEquals(LocalTime.of(7, 0), g!!.wake)
        // 8 h di sonno = nessun debito → 07:00 − 8 h − 15 min = 22:45
        assertEquals(LocalTime.of(22, 45), g.bedtime)
        // caffeina 8,8 h prima → 13:57
        assertEquals(LocalTime.of(13, 57), g.caffeineCutoff)
    }

    @Test fun bedtimeEarlierWithDebt() {
        val nights = (0 until 10).map { night(today.minusDays(it.toLong()), LocalTime.of(1, 0), LocalTime.of(7, 0)) }
        val g = Bedtime.guide(nights, today, 45.0, z)!!
        // debito 14 h/settimana → anticipo max 45 min → 22:00
        assertEquals(LocalTime.of(22, 0), g.bedtime)
    }

    @Test fun bedtimeNeedsFiveNights() {
        assertNull(Bedtime.guide(listOf(night(today, LocalTime.of(23, 0), LocalTime.of(7, 0))), today, 45.0, z))
    }

    @Test fun loadTrendSpike() {
        val loads = (0 until 28).map { k ->
            val d = today.minusDays(k.toLong())
            DayLoad(d, if (k < 7) 120.0 else 40.0, 0, 0, emptyMap(), 900)
        }
        val t = LoadTrendCalc.compute(loads, today)
        assertEquals(LoadTrendState.SPIKE, t.state)
    }

    @Test fun vitalsFlagsOutlier() {
        val base = (1..30).map { DailyValue(today.minusDays(it.toLong()), 55.0 + (it % 3)) }
        val r = Vitals.report(mapOf("FC" to Triple(base + DailyValue(today, 70.0), "bpm", 0)), today)
        assertEquals(VitalState.HIGH, r.vitals.first().state)
    }
}
