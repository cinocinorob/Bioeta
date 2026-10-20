package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.Associations
import it.cinocinorob.bioeta.science.Exposure
import it.cinocinorob.bioeta.science.OutcomeKind
import it.cinocinorob.bioeta.science.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.sin

class AssociationsTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val days = (0L..89L).map { today.minusDays(it) }.toSet()

    /** Rumore deterministico in [-1, 1]. */
    private fun noise(i: Long) = sin(i * 12.9898 + 78.233) * 0.5 + sin(i * 4.1414) * 0.5

    @Test fun strongEffectIsConfirmedWithRightSize() {
        val alcohol = days.filter { it.dayOfMonth % 6 == 0 }.toSet()
        // HRV del mattino dopo −20% nei giorni dopo alcol, altrimenti 50 ms ± rumore
        val hrv = days.associateWith { d ->
            val base = 50.0 * (1 + 0.04 * noise(d.toEpochDay()))
            if (d.minusDays(1) in alcohol) base * 0.8 else base
        }
        val e = Exposure("tag:Alcol", "Alcol", alcohol, days, auto = false)
        val r = Associations.discover(listOf(e), mapOf(OutcomeKind.HRV to hrv), today).results.single()
        assertEquals(Verdict.CONFIRMED, r.verdict)
        assertEquals(-20.0, r.effect, 3.0)
        assertTrue(r.low < r.effect && r.effect < r.high)
        assertTrue(!r.favorable)
    }

    @Test fun noEffectIsNotReported() {
        val tag = days.filter { it.dayOfMonth % 5 == 1 }.toSet()
        val rhr = days.associateWith { d -> 55 + 2 * noise(d.toEpochDay()) }
        val e = Exposure("tag:X", "X", tag, days, auto = false)
        val r = Associations.discover(listOf(e), mapOf(OutcomeKind.RHR to rhr), today).results.single()
        assertEquals(Verdict.NONE, r.verdict)
    }

    @Test fun slowTrendIsRemovedByBaseline() {
        // FC che sale lentamente di 6 bpm in 90 giorni; l'etichetta compare solo nell'ultimo mese
        val late = days.filter { it.isAfter(today.minusDays(30)) && it.dayOfMonth % 3 == 0 }.toSet()
        val rhr = days.associateWith { d -> 52 + 6.0 * (90 - java.time.temporal.ChronoUnit.DAYS.between(d, today)) / 90.0 + 0.6 * noise(d.toEpochDay()) }
        val e = Exposure("tag:Y", "Y", late, days, auto = false)
        val r = Associations.discover(listOf(e), mapOf(OutcomeKind.RHR to rhr), today).results.single()
        assertTrue("effetto spurio da tendenza: ${r.effect}", kotlin.math.abs(r.effect) < 1.0)
        assertEquals(Verdict.NONE, r.verdict)
    }

    @Test fun unknownDaysAreNotCountedAsNo() {
        val known = days.filter { it.dayOfMonth <= 10 }.toSet()
        val tag = known.filter { it.dayOfMonth % 2 == 0 }.toSet()
        val hrv = days.associateWith { 50.0 }
        val e = Exposure("tag:Z", "Z", tag, known, auto = false)
        val r = Associations.discover(listOf(e), mapOf(OutcomeKind.HRV to hrv), today).results.single()
        // giorni noti nei 90: ~30, metà con l'etichetta → i «no» non superano i giorni compilati
        assertTrue(r.nWith + r.nWithout <= known.size)
    }

    @Test fun pendingWhenTooFewDays() {
        val tag = setOf(today.minusDays(3), today.minusDays(10))
        val d = Associations.discover(listOf(Exposure("tag:W", "W", tag, days, false)), mapOf(OutcomeKind.HRV to days.associateWith { 50.0 }), today)
        assertTrue(d.results.isEmpty())
        assertEquals(2, d.pending.single().nWith)
    }

    @Test fun benjaminiHochbergMatchesReference() {
        // Esempio classico: p = 0,01 0,02 0,03 0,04 0,05 → q tutti 0,05
        val q = Associations.benjaminiHochberg(listOf(0.01, 0.02, 0.03, 0.04, 0.05))
        q.forEach { assertEquals(0.05, it, 1e-12) }
        val q2 = Associations.benjaminiHochberg(listOf(0.001, 0.5))
        assertEquals(0.002, q2[0], 1e-12); assertEquals(0.5, q2[1], 1e-12)
    }

    @Test fun resultsAreReproducible() {
        val tag = days.filter { it.dayOfMonth % 4 == 0 }.toSet()
        val s = days.associateWith { d -> 420 + 30 * noise(d.toEpochDay()) }
        val e = Exposure("tag:R", "R", tag, days, false)
        val a = Associations.discover(listOf(e), mapOf(OutcomeKind.SLEEP to s), today).results.single()
        val b = Associations.discover(listOf(e), mapOf(OutcomeKind.SLEEP to s), today).results.single()
        assertEquals(a.p, b.p, 0.0); assertEquals(a.low, b.low, 0.0)
    }
}
