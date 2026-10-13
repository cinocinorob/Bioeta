package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.DayLoad
import it.cinocinorob.bioeta.science.Fitness
import it.cinocinorob.bioeta.science.HrSample
import it.cinocinorob.bioeta.science.Journal
import it.cinocinorob.bioeta.science.JournalEntry
import it.cinocinorob.bioeta.science.LoadStatus
import it.cinocinorob.bioeta.science.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FitnessTest {
    private val d = LocalDate.of(2026, 10, 12)

    @Test
    fun zonesFollowAcsmHeartRateReserve() {
        assertEquals(Zone.VERY_LIGHT, Fitness.zoneOf(0.2))
        assertEquals(Zone.LIGHT, Fitness.zoneOf(0.35))
        assertEquals(Zone.MODERATE, Fitness.zoneOf(0.5))
        assertEquals(Zone.VIGOROUS, Fitness.zoneOf(0.7))
        assertEquals(Zone.MAXIMAL, Fitness.zoneOf(0.95))
    }

    @Test
    fun dayLoadCountsMinutesAndTrimp() {
        // età 45 → FCmax 176,5; riposo 56 → FCR 120,5
        val start = Instant.parse("2026-10-12T07:00:00Z")
        // 30 minuti a 0,5 FCR (116,25 bpm), 10 a 0,7 (140,35), 60 a riposo; due campioni per minuto
        val bpms = List(30) { 116.25 } + List(10) { 140.35 } + List(60) { 60.0 }
        val samples = bpms.flatMapIndexed { i, b -> listOf(HrSample(start.plusSeconds(i * 60L), b), HrSample(start.plusSeconds(i * 60L + 30), b)) }
        val dl = Fitness.dayLoad(d, samples, restHr = 56.0, age = 45.0, male = true)
        assertEquals(100, dl.coveredMinutes)
        assertEquals(30, dl.moderateMin)
        assertEquals(10, dl.vigorousMin)
        assertEquals(50, dl.activeMinutes)
        val expected = 30 * Fitness.trimpMinute(0.5, true) + 10 * Fitness.trimpMinute(0.7, true)
        assertEquals(expected, dl.load, 0.05)
    }

    @Test
    fun targetScalesWithReadiness() {
        val hist = (1..28).map { DayLoad(d.minusDays(it.toLong()), 60.0, 20, 5, emptyMap(), 900) }
        val hi = Fitness.target(hist, d, 85, true)
        val lo = Fitness.target(hist, d, 30, true)
        assertEquals(54.0, hi.low, 1e-9); assertEquals(84.0, hi.high, 1e-9)
        assertEquals(0.0, lo.low, 1e-9); assertEquals(30.0, lo.high, 1e-9)
        assertEquals(LoadStatus.ABOVE, Fitness.status(40.0, lo))
    }

    @Test
    fun weekActiveMinutesFromMonday() {
        // 12/10/2026 è lunedì
        val hist = (0..6).map { DayLoad(d.minusDays(it.toLong()), 0.0, 10, 5, emptyMap(), 900) }
        assertEquals(20, Fitness.weekActiveMinutes(hist, d))
    }

    @Test
    fun tagEffectsNeedEnoughDays() {
        val entries = (1..12).map { JournalEntry(d.minusDays(it.toLong()), tags = if (it % 2 == 0) setOf("Alcol") else emptySet()) }
        val eff = Journal.tagEffects(entries, { day -> if (entries.first { it.date == day.minusDays(1) }.tags.contains("Alcol")) 60 else 80 })
        val alcol = eff.first { it.tag == "Alcol" }
        assertEquals(-20.0, alcol.diff, 1e-9)
        assertTrue(alcol.nWith >= 4 && alcol.nWithout >= 4)
    }
}
