package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.ActivityEngine
import it.cinocinorob.bioeta.science.ActivityKind
import it.cinocinorob.bioeta.science.Fitness
import it.cinocinorob.bioeta.science.HrSample
import it.cinocinorob.bioeta.science.Session
import it.cinocinorob.bioeta.science.StepChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Revisione dei minuti attivi: fonti multiple, scomposizione, cache, dati implausibili. */
class ActiveMinutesAuditTest {
    private val z = ZoneId.of("Europe/Rome")
    private val day = LocalDate.of(2026, 10, 20)
    private val rest = 55.0
    private val age = 45.0
    private val watch = "com.google.android.apps.fitness"
    private val ring = "com.ouraring.oura"
    private fun at(h: Int, m: Int, s: Int = 0): Instant = day.atTime(h, m, s).atZone(z).toInstant()
    private fun minute(i: Instant) = Math.floorDiv(i.epochSecond, 60L)

    /** Soglia moderata in bpm (40% della riserva): ≈ 103,6 con FC a riposo 55 e 45 anni. */
    private val moderateBpm = rest + 0.40 * (Fitness.hrMax(age) - rest)

    @Test fun ringAndWatchAreNotAveraged() {
        // Camminata svelta 30 min: orologio 112 bpm ogni 30 s; anello 72 bpm ogni minuto (misura «a riposo»)
        val w = (0 until 60).map { HrSample(at(17, 0).plusSeconds(it * 30L), 112.0, watch) }
        val r = (0 until 30).map { HrSample(at(17, it), 72.0, ring) }
        val s = ActivityEngine.reconcile(w + r)
        assertEquals(watch, s.primary)
        val d = ActivityEngine.day(day, z, s.byMinute, emptyList(), rest, age, true)
        assertTrue(112.0 > moderateBpm)
        assertEquals(30, d.moderateMeasured)
        // La vecchia media dei campioni del minuto (112+112+72)/3 ≈ 99 bpm cadeva sotto la soglia: 0 minuti
        val averaged = ActivityEngine.minuteSeries(w + r)
        assertEquals(0, ActivityEngine.day(day, z, averaged, emptyList(), rest, age, true).activeMinutes)
    }

    @Test fun secondarySourceOnlyFillsGaps() {
        val w = (0 until 10).map { HrSample(at(9, it), 120.0, watch) }
        val r = (0 until 20).map { HrSample(at(9, it), 70.0, ring) }
        val s = ActivityEngine.reconcile(w + r, prefer = watch)
        assertEquals(120.0, s.byMinute[minute(at(9, 5))]!!, 1e-9)
        assertEquals(70.0, s.byMinute[minute(at(9, 15))]!!, 1e-9)
        assertEquals(mapOf(watch to 10, ring to 10), s.minutesByOrigin)
    }

    @Test fun preferredSourceWinsEvenWithFewerMinutes() {
        val w = (0 until 5).map { HrSample(at(9, it), 120.0, watch) }
        val r = (0 until 20).map { HrSample(at(9, it), 70.0, ring) }
        assertEquals(ring, ActivityEngine.reconcile(w + r).primary)
        assertEquals(watch, ActivityEngine.reconcile(w + r, prefer = watch).primary)
        // fonte preferita assente nei dati → automatica
        assertEquals(ring, ActivityEngine.reconcile(r, prefer = watch).primary)
    }

    @Test fun noInterpolationAcrossSources() {
        val s = ActivityEngine.reconcile(listOf(HrSample(at(10, 0), 130.0, watch), HrSample(at(10, 3), 70.0, ring)))
        assertEquals(2, s.byMinute.size)
        assertFalse(s.byMinute.containsKey(minute(at(10, 1))))
    }

    @Test fun breakdownAndHourlyAddUpToTotal() {
        // 20 min di corsa con FC vigorosa (06:00), 15 min di camminata solo passi (12:00), 30 min di bici senza FC (18:00)
        val hr = (0 until 20).associate { minute(at(6, it)) to 150.0 }
        val cad = ActivityEngine.cadenceByMinute((0 until 15).map { StepChunk("w", at(12, it), at(12, it + 1), 112) })
        val bike = Session("b", "x", ActivityKind.BIKING, null, at(18, 0), at(18, 30))
        val d = ActivityEngine.day(day, z, hr, listOf(bike), rest, age, true, cad)
        assertEquals(40, d.hrEq)
        assertEquals(15, d.cadenceEq)
        assertEquals(30, d.estimatedShare)
        assertEquals(d.activeMinutes, d.hrEq + d.cadenceEq + d.estimatedShare)
        assertEquals(24, d.hourly.size)
        assertEquals(d.activeMinutes, d.hourly.sum())
        assertEquals(40, d.hourly[6]); assertEquals(15, d.hourly[12]); assertEquals(30, d.hourly[18])
    }

    @Test fun cadenceUpgradeOverModerateHrCountsOnlyTheDifference() {
        // FC moderata ma cadenza da corsa (≥130): il minuto è vigoroso; 1 punto dalla FC, 1 dai passi
        val hr = (0 until 10).associate { minute(at(8, it)) to 110.0 }
        val cad = ActivityEngine.cadenceByMinute((0 until 10).map { StepChunk("w", at(8, it), at(8, it + 1), 150) })
        val d = ActivityEngine.day(day, z, hr, emptyList(), rest, age, true, cad)
        assertEquals(20, d.activeMinutes)
        assertEquals(10, d.hrEq); assertEquals(10, d.cadenceEq)
    }

    @Test fun implausibleCadenceIsDiscarded() {
        // due record sovrapposti della stessa app nello stesso minuto: 300 passi/min non sono credibili
        val c = listOf(StepChunk("w", at(9, 0), at(9, 1), 150), StepChunk("w", at(9, 0), at(9, 1), 150))
        assertTrue(ActivityEngine.cadenceByMinute(c).isEmpty())
    }

    @Test fun dayComputedBeforeItsEndIsNotReused() {
        val hr = (0 until 700).associate { minute(at(0, 0)) + it to 60.0 }
        val midday = at(13, 0).epochSecond
        val partial = ActivityEngine.day(day, z, hr, emptyList(), rest, age, true, computedAt = midday)
        assertFalse(ActivityEngine.cacheReusable(partial, z, hasSessions = false))
        val nextMorning = day.plusDays(1).atTime(8, 0).atZone(z).toEpochSecond()
        val full = partial.copy(computedAt = nextMorning)
        assertTrue(ActivityEngine.cacheReusable(full, z, hasSessions = false))
        assertFalse(ActivityEngine.cacheReusable(full, z, hasSessions = true))
        // poca FC (orologio poco indossato): si aspetta 48 h prima di considerarlo definitivo
        val sparse = full.copy(hrMinutes = 100)
        assertFalse(ActivityEngine.cacheReusable(sparse, z, hasSessions = false))
        assertTrue(ActivityEngine.cacheReusable(sparse.copy(computedAt = day.plusDays(3).atStartOfDay(z).toEpochSecond()), z, hasSessions = false))
        assertFalse(ActivityEngine.cacheReusable(full.copy(stale = true), z, hasSessions = false))
    }

    @Test fun dstDayHourlyStillHas24Slots() {
        val dst = LocalDate.of(2026, 10, 25) // fine dell'ora legale in Italia: 25 ore
        val hr = (0 until 10).associate { Math.floorDiv(dst.atTime(2, 30).atZone(z).toEpochSecond(), 60L) + it to 150.0 }
        val d = ActivityEngine.day(dst, z, hr, emptyList(), rest, age, true)
        assertEquals(24, d.hourly.size)
        assertEquals(d.activeMinutes, d.hourly.sum())
    }
}
