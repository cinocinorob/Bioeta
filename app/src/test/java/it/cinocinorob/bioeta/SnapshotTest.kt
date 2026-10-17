package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.data.ChangeSummary
import it.cinocinorob.bioeta.data.HealthSnapshot
import it.cinocinorob.bioeta.data.SnapshotMerge
import it.cinocinorob.bioeta.health.DailyPoint
import it.cinocinorob.bioeta.health.Metric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class SnapshotTest {
    private val now = Instant.parse("2026-10-17T10:00:00Z")
    private val d = LocalDate.of(2026, 10, 17)
    private val withData = HealthSnapshot(wearable = mapOf(Metric.RHR to listOf(DailyPoint(d, 55.0))), lastFull = now.minus(Duration.ofHours(1)))

    @Test fun fullWhenNoCacheOrForcedOrOld() {
        assertTrue(SnapshotMerge.needFull(false, HealthSnapshot(), now, ChangeSummary(0, null, false, "t")))
        assertTrue(SnapshotMerge.needFull(true, withData, now, ChangeSummary(0, null, false, "t")))
        assertTrue(SnapshotMerge.needFull(false, withData.copy(lastFull = now.minus(Duration.ofHours(7))), now, ChangeSummary(0, null, false, "t")))
    }

    @Test fun quickOnlyWhenOnlyRecentChanges() {
        assertFalse(SnapshotMerge.needFull(false, withData, now, ChangeSummary(0, null, false, "t")))
        assertFalse(SnapshotMerge.needFull(false, withData, now, ChangeSummary(5, now.minus(Duration.ofDays(1)), false, "t")))
    }

    @Test fun fullWhenOldDataChangedOrTokenExpiredOrDeletion() {
        assertTrue(SnapshotMerge.needFull(false, withData, now, ChangeSummary(1, now.minus(Duration.ofDays(10)), false, "t")))
        assertTrue(SnapshotMerge.needFull(false, withData, now, ChangeSummary(0, null, true, null)))
        assertTrue(SnapshotMerge.needFull(false, withData, now, null))
        assertTrue(SnapshotMerge.needFull(false, withData, now, ChangeSummary(1, Instant.EPOCH, false, "t")))
    }

    @Test fun recentMergeReplacesOnlyRecentDays() {
        val old = mapOf(Metric.HRV to (1..10).map { DailyPoint(d.minusDays(it.toLong()), 40.0) })
        val fresh = mapOf(Metric.HRV to listOf(DailyPoint(d.minusDays(1), 50.0), DailyPoint(d, 52.0)))
        val m = SnapshotMerge.series(old, fresh, d.minusDays(3))[Metric.HRV]!!
        assertEquals(40.0, m.first { it.date == d.minusDays(5) }.value, 0.0)
        assertEquals(50.0, m.first { it.date == d.minusDays(1) }.value, 0.0)
        // il giorno 2 e 3 non riletti nella finestra recente: rimossi solo se non presenti nella lettura nuova
        assertEquals(listOf(d.minusDays(10), d.minusDays(9), d.minusDays(8), d.minusDays(7), d.minusDays(6), d.minusDays(5), d.minusDays(4), d.minusDays(1), d), m.map { it.date })
    }
}
