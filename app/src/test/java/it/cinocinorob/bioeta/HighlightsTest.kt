package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.HMetric
import it.cinocinorob.bioeta.science.HPeriod
import it.cinocinorob.bioeta.science.Highlights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.sin

class HighlightsTest {
    private val today = LocalDate.of(2026, 10, 21)
    private fun noise(i: Long) = sin(i * 12.9898 + 78.233) * 0.5 + sin(i * 4.1414) * 0.5

    @Test fun clearWeeklyDropInRestingHrIsReportedAsGood() {
        val rhr = (0L..60L).associate { k -> today.minusDays(k) to (if (k <= 6) 52.0 else 56.0) + 1.0 * noise(k) }
        val h = Highlights.compute(mapOf(HMetric.RHR to rhr), today).single()
        assertEquals(HPeriod.WEEK, h.period)
        assertEquals(true, h.good)
        assertTrue(h.sentence, h.sentence.contains("più bassa"))
        assertEquals(5, h.bars.size)
    }

    @Test fun normalNoiseProducesNoHighlight() {
        val steps = (0L..119L).associate { k -> today.minusDays(k) to 9000.0 + 1500 * noise(k) }
        assertTrue(Highlights.compute(mapOf(HMetric.STEPS to steps), today).isEmpty())
    }

    @Test fun sleepSentenceUsesMinutes() {
        val sleep = (0L..60L).associate { k -> today.minusDays(k) to (if (k <= 6) 7.6 else 6.8) + 0.2 * noise(k) }
        val h = Highlights.compute(mapOf(HMetric.SLEEP to sleep), today).single()
        assertTrue(h.sentence, h.sentence.contains("minuti in più"))
    }

    @Test fun smallButConsistentChangeBelowRelevanceIsIgnored() {
        // FC −0,5 bpm: sotto la soglia di rilevanza di 1 bpm anche se costante
        val rhr = (0L..60L).associate { k -> today.minusDays(k) to (if (k <= 6) 55.5 else 56.0) }
        assertTrue(Highlights.compute(mapOf(HMetric.RHR to rhr), today).isEmpty())
    }
}
