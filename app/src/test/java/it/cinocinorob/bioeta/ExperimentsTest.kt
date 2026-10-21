package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.Experiment
import it.cinocinorob.bioeta.science.Experiments
import it.cinocinorob.bioeta.science.JournalEntry
import it.cinocinorob.bioeta.science.OutcomeKind
import it.cinocinorob.bioeta.science.SleepNight
import it.cinocinorob.bioeta.science.SleepTimingCalc
import it.cinocinorob.bioeta.science.Verdict
import it.cinocinorob.bioeta.science.withAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sin

class ExperimentsTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val today = start.plusDays(20)
    private fun noise(i: Long) = sin(i * 12.9898 + 78.233) * 0.5 + sin(i * 4.1414) * 0.5

    @Test fun avoidingAlcoholRaisesHrvIsDetected() {
        val e = Experiment("Alcol", doIt = false, start = start, days = 14)
        val journal = (0L..13L).map { JournalEntry(start.plusDays(it)).withAnswer("Alcol", false) }
        // HRV +15% durante l'esperimento rispetto alle 3 settimane prima
        val hrv = (-30L..21L).associate { k ->
            val d = start.plusDays(k)
            d to 50.0 * (1 + 0.04 * noise(k)) * (if (k in 1..14) 1.15 else 1.0)
        }
        val r = Experiments.evaluate(e, journal, mapOf(OutcomeKind.HRV to hrv), today)
        assertEquals(14, r.adherent)
        val h = r.results.single()
        assertEquals(Verdict.CONFIRMED, h.verdict)
        assertEquals(15.0, h.effect, 3.0)
        assertTrue(h.favorable)
    }

    @Test fun nonAdherentDaysAreExcluded() {
        val e = Experiment("Alcol", doIt = false, start = start)
        val journal = (0L..13L).map { k -> JournalEntry(start.plusDays(k)).withAnswer("Alcol", k % 2 == 0L) }
        val r = Experiments.evaluate(e, journal, emptyMap(), today)
        assertEquals(7, r.adherent)
        assertEquals(14, r.answered)
    }

    @Test fun sleepTimingIsCircularAroundMidnight() {
        val z = ZoneId.of("Europe/Rome")
        // A letto tra 23:30 e 00:30: la media deve stare vicino a mezzanotte, non a mezzogiorno
        val nights = (1L..14L).map { k ->
            val d = today.minusDays(k)
            val bed = if (k % 2 == 0L) d.minusDays(1).atTime(23, 30) else d.atTime(0, 30)
            SleepNight(d, bed.atZone(z).toInstant(), d.atTime(7, 0).atZone(z).toInstant(), emptyList())
        }
        val t = SleepTimingCalc.compute(nights, today, z)
        assertNotNull(t)
        val m = t!!.bedMedianMin
        assertTrue("media $m", m < 15 || m > 1425)
        assertEquals(30.0, t.bedSdMin, 6.0)
    }
}
