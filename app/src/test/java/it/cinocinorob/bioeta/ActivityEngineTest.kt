package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.data.WorkoutStore
import it.cinocinorob.bioeta.science.ActivityEngine
import it.cinocinorob.bioeta.science.ActivityKind
import it.cinocinorob.bioeta.science.Fitness
import it.cinocinorob.bioeta.science.HrSample
import it.cinocinorob.bioeta.science.LoadQuality
import it.cinocinorob.bioeta.science.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ActivityEngineTest {
    private val rome = ZoneId.of("Europe/Rome")
    private val day = LocalDate.of(2026, 10, 14)
    private val age = 45.0
    private val rest = 55.0
    private val maxHr = Fitness.hrMax(age) // 176.5

    private fun at(h: Int, m: Int, d: LocalDate = day): Instant = LocalDateTime.of(d, java.time.LocalTime.of(h, m)).atZone(rome).toInstant()
    private fun bpmAt(fraction: Double) = rest + fraction * (maxHr - rest)

    /** Campioni ogni [stepMin] minuti tra start (incluso) ed end (escluso). */
    private fun samples(start: Instant, end: Instant, bpm: Double, stepMin: Long = 1): List<HrSample> =
        generateSequence(start) { it.plusSeconds(stepMin * 60) }.takeWhile { it.isBefore(end) }.map { HrSample(it, bpm) }.toList()

    private fun session(id: String, kind: ActivityKind, s: Instant, e: Instant, origin: String = "com.fitbit.FitbitMobile") =
        Session(id, origin, kind, null, s, e)

    // ------------------------------------------------------------------ serie FC

    @Test fun interpolatesShortGapsOnly() {
        val t0 = at(8, 0)
        val s = listOf(HrSample(t0, 100.0), HrSample(t0.plusSeconds(4 * 60), 120.0), HrSample(t0.plusSeconds(14 * 60), 120.0))
        val m = ActivityEngine.minuteSeries(s)
        val k0 = t0.epochSecond / 60
        // buco di 4 minuti → riempito (valori intermedi interpolati)
        assertEquals(105.0, m[k0 + 1]!!, 1e-9)
        assertEquals(115.0, m[k0 + 3]!!, 1e-9)
        // buco di 10 minuti → NON riempito
        assertFalse(m.containsKey(k0 + 8))
        assertEquals(6, m.size) // minuti 0..4 + minuto 14
    }

    @Test fun ignoresImplausibleBpm() {
        val t0 = at(8, 0)
        val m = ActivityEngine.minuteSeries(listOf(HrSample(t0, 0.0), HrSample(t0.plusSeconds(60), 300.0)))
        assertTrue(m.isEmpty())
    }

    // ------------------------------------------------------------------ minuti attivi e carico

    @Test fun bikeWithHrAndRunWithoutHrSameMorning() {
        // Bici 07:00–08:00 con FC al 70% della riserva, campione ogni 2 minuti (cadenza tipica di alcune app)
        val bikeS = at(7, 0); val bikeE = at(8, 0)
        val hr = ActivityEngine.minuteSeries(samples(bikeS, bikeE, bpmAt(0.70), stepMin = 2))
        // Corsa 08:15–08:45 senza FC
        val runS = at(8, 15); val runE = at(8, 45)
        val sessions = listOf(session("bike", ActivityKind.BIKING, bikeS, bikeE), session("run", ActivityKind.RUNNING, runS, runE))
        val d = ActivityEngine.day(day, rome, hr, sessions, rest, age, true)
        // 59 minuti coperti (ultimo campione alle 07:58, interpolazione fino a 07:58) + bordo
        assertTrue(d.vigorousMeasured in 58..60)
        assertEquals(0, d.moderateMeasured)
        // corsa: 30 minuti stimati vigorosi (tipo di attività), nessun minuto della bici stimato perché ha FC
        assertEquals(30, d.vigorousEstimated)
        // al massimo l'ultimo minuto della bici (senza campione) è stimato come moderato
        assertTrue(d.moderateEstimated <= 1)
        assertEquals(d.moderateMeasured + d.moderateEstimated + 2 * (d.vigorousMeasured + d.vigorousEstimated), d.activeMinutes)
        // il carico usa solo la FC: positivo ma parziale (meno di 10 h di FC)
        assertTrue(d.load > 0)
        assertEquals(LoadQuality.PARTIAL, d.quality)
    }

    @Test fun duplicateWorkoutFromTwoAppsCountedOnce() {
        val s = at(18, 0); val e = at(18, 40)
        val sessions = listOf(
            session("a", ActivityKind.RUNNING, s, e, origin = "com.fitbit.FitbitMobile"),
            session("b", ActivityKind.RUNNING, s.plusSeconds(60), e.plusSeconds(60), origin = "com.strava"),
        )
        assertEquals(1, ActivityEngine.dedupe(sessions).size)
        val d = ActivityEngine.day(day, rome, emptyMap(), sessions, rest, age, true)
        assertEquals(40, d.vigorousEstimated) // una sola sessione, non 80 minuti
    }

    @Test fun differentActivitiesSameTimeFromSameAppAreKept() {
        val sessions = listOf(
            session("bike", ActivityKind.BIKING, at(7, 0), at(8, 0)),
            session("run", ActivityKind.RUNNING, at(8, 15), at(8, 45)),
        )
        assertEquals(2, ActivityEngine.dedupe(sessions).size)
    }

    @Test fun sameIdIsIdempotent() {
        val s = session("x", ActivityKind.WALKING, at(10, 0), at(10, 30))
        assertEquals(1, ActivityEngine.dedupe(listOf(s, s, s)).size)
    }

    @Test fun sessionAcrossMidnightIsSplit() {
        val s = session("night", ActivityKind.WALKING, at(23, 30), at(0, 30, day.plusDays(1)))
        val d1 = ActivityEngine.day(day, rome, emptyMap(), listOf(s), rest, age, true)
        val d2 = ActivityEngine.day(day.plusDays(1), rome, emptyMap(), listOf(s), rest, age, true)
        assertEquals(30, d1.moderateEstimated)
        assertEquals(30, d2.moderateEstimated)
    }

    @Test fun lightOrUnknownActivitiesAreNotCounted() {
        val sessions = listOf(
            session("yoga", ActivityKind.MIND_BODY, at(9, 0), at(10, 0)),
            session("other", ActivityKind.OTHER, at(11, 0), at(12, 0)),
        )
        val d = ActivityEngine.day(day, rome, emptyMap(), sessions, rest, age, true)
        assertEquals(0, d.activeMinutes)
    }

    @Test fun noHeartRateMeansLoadUnavailable() {
        val d = ActivityEngine.day(day, rome, emptyMap(), emptyList(), rest, age, true)
        assertEquals(LoadQuality.UNAVAILABLE, d.quality)
        assertEquals(0.0, d.load, 0.0)
        assertEquals(0, d.hrMinutes)
    }

    @Test fun fullDayCoverageIsComplete() {
        val hr = ActivityEngine.minuteSeries(samples(at(0, 0), at(0, 0, day.plusDays(1)), bpmAt(0.10)))
        val d = ActivityEngine.day(day, rome, hr, emptyList(), rest, age, true)
        assertEquals(LoadQuality.COMPLETE, d.quality)
        assertEquals(0.0, d.load, 0.0) // sotto il 30% della riserva: nessun carico
        assertEquals(0, d.activeMinutes)
    }

    @Test fun dstDayHas25Hours() {
        // In Italia il 25/10/2026 dura 25 ore (ritorno all'ora solare)
        val dst = LocalDate.of(2026, 10, 25)
        val start = dst.atStartOfDay(rome).toInstant(); val end = dst.plusDays(1).atStartOfDay(rome).toInstant()
        val hr = ActivityEngine.minuteSeries(samples(start, end, bpmAt(0.10)))
        val d = ActivityEngine.day(dst, rome, hr, emptyList(), rest, age, true)
        assertEquals(25 * 60, d.hrMinutes)
    }

    @Test fun hrMinutesHaveHigherPriorityThanEstimate() {
        // Corsa con FC moderata (45%): deve risultare moderata misurata, non vigorosa stimata
        val s = at(17, 0); val e = at(17, 30)
        val hr = ActivityEngine.minuteSeries(samples(s, e, bpmAt(0.45)))
        val d = ActivityEngine.day(day, rome, hr, listOf(session("r", ActivityKind.RUNNING, s, e)), rest, age, true)
        assertEquals(30, d.moderateMeasured)
        assertEquals(0, d.vigorousEstimated)
    }

    // ------------------------------------------------------------------ sincronizzazione idempotente

    @Test fun mergeIsIdempotentAndReflectsDeletions() {
        val from = at(0, 0, day.minusDays(30)); val to = at(23, 0)
        val old = session("old", ActivityKind.WALKING, at(9, 0, day.minusDays(40)), at(9, 30, day.minusDays(40)))
        val a = session("a", ActivityKind.BIKING, at(7, 0), at(8, 0))
        val b = session("b", ActivityKind.RUNNING, at(8, 15), at(8, 45))
        val gone = session("gone", ActivityKind.RUNNING, at(8, 0, day.minusDays(2)), at(8, 30, day.minusDays(2)))
        val stored = listOf(old, gone)
        val first = WorkoutStore.merge(stored, listOf(a, b), from, to)
        val second = WorkoutStore.merge(first, listOf(a, b), from, to)
        assertEquals(first, second)
        assertEquals(setOf("old", "a", "b"), second.map { it.id }.toSet()) // "gone" cancellata alla fonte, "old" fuori finestra
    }

    // ------------------------------------------------------------------ passi dai record grezzi

    @Test fun rawStepsTakeMaxAcrossAppsNotSum() {
        val chunks = listOf(
            it.cinocinorob.bioeta.science.StepChunk("com.fitbit.FitbitMobile", at(9, 0), at(10, 0), 500),
            it.cinocinorob.bioeta.science.StepChunk("phone", at(9, 0), at(10, 0), 450),
        )
        val h = it.cinocinorob.bioeta.science.StepsMerge.hourly(chunks, rome)
        assertEquals(500L, h[at(9, 0)])
    }

    @Test fun rawStepsSplitAcrossHoursAndDays() {
        val chunks = listOf(it.cinocinorob.bioeta.science.StepChunk("a", at(23, 30), at(0, 30, day.plusDays(1)), 600))
        val h = it.cinocinorob.bioeta.science.StepsMerge.hourly(chunks, rome)
        assertEquals(300L, h[at(23, 0)])
        assertEquals(300L, h[at(0, 0, day.plusDays(1))])
        val d = it.cinocinorob.bioeta.science.StepsMerge.daily(chunks, rome)
        assertEquals(300L, d[day]); assertEquals(300L, d[day.plusDays(1)])
    }
}
