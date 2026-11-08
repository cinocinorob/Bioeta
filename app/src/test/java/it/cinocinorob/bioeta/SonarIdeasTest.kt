package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.CheckState
import it.cinocinorob.bioeta.science.Checkups
import it.cinocinorob.bioeta.science.Compare
import it.cinocinorob.bioeta.science.LongTerm
import it.cinocinorob.bioeta.science.LongTermInputs
import it.cinocinorob.bioeta.science.PillarStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SonarIdeasTest {
    private val today = LocalDate.of(2026, 11, 8)

    private fun inputs(labs: Map<String, Pair<LocalDate, Double>> = emptyMap(), pheno: Pair<LocalDate, Double>? = null) =
        LongTermInputs(today, 45.0, true, emptyList(), null, emptyList(), emptyList(), emptyList(), emptyList(), ZoneId.of("Europe/Rome"), labs, pheno)

    @Test fun metabolicUsesWorstAvailableAndIgnoresOldLabs() {
        val d = today.minusMonths(3)
        val good = LongTerm.metabolic(inputs(mapOf("glucose" to (d to 88.0), "hba1c" to (d to 34.0), "ldl" to (d to 105.0), "tg" to (d to 90.0), "hdl" to (d to 55.0))))
        assertEquals(PillarStatus.GOOD, good.status)
        assertEquals("5/5", good.value)
        val mid = LongTerm.metabolic(inputs(mapOf("glucose" to (d to 108.0), "ldl" to (d to 105.0))))
        assertEquals(PillarStatus.MID, mid.status)
        assertTrue(mid.detail.contains("glicemia 108 mg/dL"))
        assertTrue(mid.detail.contains("Non valutati"))
        val high = LongTerm.metabolic(inputs(mapOf("ldl" to (d to 195.0))))
        assertEquals(PillarStatus.LOW, high.status)
        // esami di 3 anni fa: non valutata
        assertEquals(PillarStatus.NA, LongTerm.metabolic(inputs(mapOf("ldl" to (today.minusYears(3) to 100.0)))).status)
    }

    @Test fun bioAgeBands() {
        val d = today.minusMonths(1)
        assertEquals(PillarStatus.GOOD, LongTerm.bioAge(inputs(pheno = d to -3.2)).status)
        assertEquals(PillarStatus.MID, LongTerm.bioAge(inputs(pheno = d to 1.0)).status)
        assertEquals(PillarStatus.LOW, LongTerm.bioAge(inputs(pheno = d to 2.5)).status)
        assertEquals(PillarStatus.NA, LongTerm.bioAge(inputs()).status)
        assertEquals(8, LongTerm.compute(inputs()).size)
    }

    @Test fun compareFindsLaggedAssociation() {
        // passi di oggi → HRV di domani, relazione monotona perfetta
        val a = (0 until 30).associate { today.minusDays(30L - it) to (5000.0 + 100 * it) }
        val b = (0 until 30).associate { today.minusDays(29L - it) to (40.0 + it) }
        val r = Compare.compare("passi", a, "HRV", b, 1, today.minusDays(90))
        assertEquals(30, r.n)
        assertNotNull(r.assoc)
        assertEquals(1.0, r.assoc!!.rho, 1e-9)
        assertTrue(Compare.sentence(r, { "%.0f".format(it) }, { "%.0f".format(it) }).startsWith("Legame forte"))
        // troppi pochi giorni
        val few = Compare.compare("passi", a.entries.take(5).associate { it.toPair() }, "HRV", b, 1, today.minusDays(90))
        assertNull(few.assoc)
    }

    @Test fun checkupsForA45YearOldMan() {
        val l = Checkups.list(45.0, true, lastBp = today.minusMonths(2), lastLipids = null, lastGlucose = today.minusMonths(6), today = today)
        val bp = l.first { it.title.startsWith("Pressione") }
        assertEquals(CheckState.OK, bp.state)
        assertEquals(CheckState.DUE, l.first { it.title.startsWith("Rischio") }.state)   // uomo > 40 senza lipidi
        assertEquals(CheckState.OK, l.first { it.title.startsWith("Glicemia") }.state)
        assertEquals(CheckState.NOT_YET, l.first { it.title.contains("colon") }.state)  // dai 50
        assertTrue(l.none { it.title.startsWith("Mammografia") })
        // donna di 52: mammografia e colon attivi
        val f = Checkups.list(52.0, false, null, null, null, today)
        assertEquals(CheckState.INFO, f.first { it.title.startsWith("Mammografia") }.state)
        assertEquals(CheckState.INFO, f.first { it.title.contains("colon") }.state)
        assertEquals(CheckState.DUE, f.first { it.title.startsWith("Pressione") }.state)
    }
}
