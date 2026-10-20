package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.data.DayInputs
import it.cinocinorob.bioeta.data.Ledger
import it.cinocinorob.bioeta.data.LedgerEntry
import it.cinocinorob.bioeta.science.DailyValue
import it.cinocinorob.bioeta.science.IllnessRadar
import it.cinocinorob.bioeta.science.NightVitals
import it.cinocinorob.bioeta.science.RadarLevel
import it.cinocinorob.bioeta.science.SessionRpe
import it.cinocinorob.bioeta.science.Signals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

class RadarTest {
    private val today = LocalDate.of(2026, 10, 18)

    /** 40 notti con piccola oscillazione deterministica; le ultime [n] sostituite da [last]. */
    private fun series(base: Double, wiggle: Double, vararg last: Double): List<DailyValue> {
        val out = (40 downTo 0).map { k -> DailyValue(today.minusDays(k.toLong()), base + wiggle * ((k % 5) - 2) / 2.0) }.toMutableList()
        last.reversed().forEachIndexed { i, v -> out[out.size - 1 - i] = DailyValue(today.minusDays(i.toLong()), v) }
        return out
    }

    @Test fun healthyNightGivesNoSigns() {
        val r = IllnessRadar.assess(mapOf(
            Signals.RHR to series(55.0, 1.0), Signals.HRV to series(45.0, 3.0), Signals.RESP to series(14.5, 0.3),
        ), today)
        assertEquals(RadarLevel.NONE, r.level)
        assertEquals(today, r.night)
    }

    @Test fun twoSignalsGiveMinorSigns() {
        val r = IllnessRadar.assess(mapOf(
            Signals.RHR to series(55.0, 1.0, 60.0), Signals.HRV to series(45.0, 3.0, 30.0), Signals.RESP to series(14.5, 0.3),
        ), today)
        assertEquals(RadarLevel.MINOR, r.level)
    }

    @Test fun persistentHeartRatePlusAnotherSignalIsMajor() {
        // FC +5 bpm per due notti (criterio NightSignal) + HRV bassa
        val r = IllnessRadar.assess(mapOf(
            Signals.RHR to series(55.0, 1.0, 60.0, 60.0), Signals.HRV to series(45.0, 3.0, 30.0), Signals.RESP to series(14.5, 0.3),
        ), today)
        assertEquals(RadarLevel.MAJOR, r.level)
    }

    @Test fun singleSignalIsNotAnAlert() {
        val r = IllnessRadar.assess(mapOf(Signals.RHR to series(55.0, 1.0, 60.0), Signals.HRV to series(45.0, 3.0)), today)
        assertEquals(RadarLevel.NONE, r.level)
    }

    @Test fun restingAndSleepingHeartRateCountOnce() {
        val r = IllnessRadar.assess(mapOf(
            Signals.RHR to series(55.0, 1.0, 60.0), Signals.SLEEP_HR to series(58.0, 1.0, 63.0), Signals.HRV to series(45.0, 3.0),
        ), today)
        assertEquals(RadarLevel.NONE, r.level)
    }

    @Test fun recentNightsDoNotContaminateBaseline() {
        // Le ultime due notti alte non entrano nella baseline (−30…−3)
        val e = NightVitals.evaluate(Signals.RHR, series(55.0, 1.0, 61.0, 61.0), today)
        assertEquals(55.0, e.median!!, 0.6)
        assertTrue(e.concerning)
    }

    @Test fun minimumSdFollowsNightSignalMargin() {
        // Serie perfettamente costante: senza DS minima ogni decimo sarebbe «anomalo»
        val flat = (40 downTo 0).map { DailyValue(today.minusDays(it.toLong()), 55.0) }
        val e = NightVitals.evaluate(Signals.RHR, flat.dropLast(1) + DailyValue(today, 57.0), today)
        assertTrue(!e.concerning)
        val e2 = NightVitals.evaluate(Signals.RHR, flat.dropLast(1) + DailyValue(today, 58.0), today)
        assertTrue(e2.concerning) // +3 bpm = 2 DS con DS minima 1,5
    }

    @Test fun calibratingWithShortHistory() {
        val short = (5 downTo 0).map { DailyValue(today.minusDays(it.toLong()), 55.0) }
        val r = IllnessRadar.assess(mapOf(Signals.RHR to short, Signals.HRV to short), today)
        assertEquals(RadarLevel.CALIBRATING, r.level)
    }

    @Test fun usesYesterdayWhenTonightIsMissing() {
        val s = series(55.0, 1.0).dropLast(1)
        val r = IllnessRadar.assess(mapOf(Signals.RHR to s, Signals.HRV to series(45.0, 3.0).dropLast(1)), today)
        assertEquals(today.minusDays(1), r.night)
    }

    @Test fun hardTrainingYesterdayIsGivenAsContext() {
        val loads = (30 downTo 1).map { DailyValue(today.minusDays(it.toLong()), if (it == 1) 200.0 else 80.0) }
        val r = IllnessRadar.assess(mapOf(
            Signals.RHR to series(55.0, 1.0, 60.0), Signals.HRV to series(45.0, 3.0, 30.0),
        ), today, loads, setOf("Alcol"))
        assertNotNull(r.context)
        assertTrue(r.context!!.contains("carico") && r.context!!.contains("alcol"))
    }

    @Test fun hrvIsEvaluatedOnLogScale() {
        val e = NightVitals.evaluate(Signals.HRV, series(45.0, 3.0), today)
        // Intervallo asimmetrico attorno alla mediana, come atteso su scala logaritmica
        assertTrue((e.high!! - e.median!!) > (e.median!! - e.low!!))
    }
}

class RpeTest {
    private val today = LocalDate.of(2026, 10, 18)

    @Test fun loadIsRpeTimesMinutes() = assertEquals(300.0, SessionRpe.load(5, 60.0), 1e-9)

    @Test fun weeklySummaryAndMonotony() {
        val rated = (0L..27L).filter { it % 2 == 0L }.map { SessionRpe.Rated(today.minusDays(it), 5, 60.0) }
        val s = SessionRpe.summary(rated, today)
        // 4 allenamenti negli ultimi 7 giorni (0,2,4,6) × 300
        assertEquals(1200.0, s.week, 1e-9)
        assertEquals(14 * 300.0 / 4, s.chronicWeek!!, 1e-9)
        assertEquals(1200.0 / 1050.0, s.ratio!!, 1e-9)
        // giorni: 300,0,300,0,300,0,300 → media 171,4; DS 160,4 → monotonia ≈ 1,07
        assertEquals(1.07, s.monotony!!, 0.01)
    }

    @Test fun noChronicWithShortHistory() {
        val s = SessionRpe.summary(listOf(SessionRpe.Rated(today, 6, 45.0)), today)
        assertNull(s.chronicWeek)
        assertNull(s.monotony?.takeIf { false })
    }
}

class LedgerTest {
    private val today = LocalDate.of(2026, 10, 18)
    private val inp = DayInputs(420, 45, 55, 145, 1, 100)

    @Test fun pastDayChangeIsReportedWithReason() {
        val d = today.minusDays(1)
        val old = mapOf(d to LedgerEntry(d, 80, 70, inp, final = true))
        val fresh = listOf(LedgerEntry(d, 80, 64, inp.copy(workouts = 2, load = 160)))
        val ch = Ledger.diff(old, fresh, today, Instant.EPOCH)
        assertEquals(1, ch.size)
        assertEquals("Recupero", ch[0].what)
        assertTrue(ch[0].reason.contains("allenamento aggiunto"))
    }

    @Test fun todayReadinessIsNotReported() {
        val old = mapOf(today to LedgerEntry(today, 80, 70, inp, final = false))
        val ch = Ledger.diff(old, listOf(LedgerEntry(today, 80, 60, inp.copy(load = 200), final = false)), today, Instant.EPOCH)
        assertTrue(ch.isEmpty())
    }

    @Test fun readinessSeenWhileDayWasOpenIsNotReportedNextDay() {
        val d = today.minusDays(1)
        val old = mapOf(d to LedgerEntry(d, 80, 70, inp, final = false))
        val ch = Ledger.diff(old, listOf(LedgerEntry(d, 80, 62, inp.copy(load = 180))), today, Instant.EPOCH)
        assertTrue(ch.isEmpty())
    }

    @Test fun oneOrZeroPointDifferencesAreIgnored() {
        val d = today.minusDays(2)
        val old = mapOf(d to LedgerEntry(d, 80, 70, inp))
        assertTrue(Ledger.diff(old, listOf(LedgerEntry(d, 81, 69, inp)), today, Instant.EPOCH).isEmpty())
    }

    @Test fun lateNightIsExplained() {
        val d = today.minusDays(1)
        val old = mapOf(d to LedgerEntry(d, 60, 70, inp.copy(sleepMin = 250)))
        val ch = Ledger.diff(old, listOf(LedgerEntry(d, 78, 70, inp.copy(sleepMin = 430))), today, Instant.EPOCH)
        assertEquals("Sonno", ch.single().what)
        assertTrue(ch.single().reason.contains("4 h 10 → 7 h 10"))
        assertTrue(abs(ch.single().to!! - 78) == 0)
    }
}

class MoodTrendTest {
    private val d0 = LocalDate.of(2026, 10, 1)

    @Test fun spearmanPerfectAndTies() {
        assertEquals(1.0, it.cinocinorob.bioeta.science.MoodTrend.spearman(listOf(1.0, 2.0, 3.0, 4.0), listOf(10.0, 20.0, 30.0, 40.0))!!, 1e-9)
        assertEquals(-1.0, it.cinocinorob.bioeta.science.MoodTrend.spearman(listOf(1.0, 2.0, 3.0), listOf(3.0, 2.0, 1.0))!!, 1e-9)
        // ranghi medi per i pari merito
        assertEquals(listOf(1.5, 1.5, 3.0), it.cinocinorob.bioeta.science.MoodTrend.ranks(listOf(2.0, 2.0, 5.0)))
    }

    @Test fun assocNeedsFourteenPairsAndCiContainsRho() {
        val T = it.cinocinorob.bioeta.science.MoodTrend
        assertNull(T.assoc("x", (1..13).map { it.toDouble() to it.toDouble() }))
        val pairs = (1..30).map { i -> i.toDouble() to (if (i % 3 == 0) i - 4.0 else i.toDouble()) }
        val a = T.assoc("x", pairs)!!
        assertTrue(a.low < a.rho && a.rho < a.high)
        assertTrue(a.clear)
        assertEquals(30, a.n)
    }

    @Test fun noAssociationWhenIndependent() {
        val T = it.cinocinorob.bioeta.science.MoodTrend
        // sequenza alternata senza relazione monotona
        val pairs = (1..20).map { i -> i.toDouble() to (if (i % 2 == 0) 5.0 else 1.0) }
        val a = T.assoc("x", pairs)!!
        assertTrue(!a.clear)
        assertEquals("nessuna associazione chiara", T.describe(a))
    }

    @Test fun rollingNeedsThreeEntries() {
        val T = it.cinocinorob.bioeta.science.MoodTrend
        val pts = listOf(
            it.cinocinorob.bioeta.science.MoodPoint(d0, 2.0),
            it.cinocinorob.bioeta.science.MoodPoint(d0.plusDays(1), 4.0),
            it.cinocinorob.bioeta.science.MoodPoint(d0.plusDays(3), 3.0),
        )
        val r = T.rolling(pts)
        assertEquals(1, r.size)
        assertEquals(d0.plusDays(3), r[0].date)
        assertEquals(3.0, r[0].value, 1e-9)
    }

    @Test fun ledgerTreatsMissingAndZeroLoadAsEqual() {
        val a = DayInputs(420, 45, 55, 145, 0, null)
        assertEquals("sono cambiati i dati dei giorni precedenti, usati come riferimento", Ledger.reason(a, a.copy(load = 0)))
    }
}
