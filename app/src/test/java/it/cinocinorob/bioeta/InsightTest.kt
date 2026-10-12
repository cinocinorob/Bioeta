package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.assistant.CitationCheck
import it.cinocinorob.bioeta.science.InsightEngine
import it.cinocinorob.bioeta.science.InsightInput
import it.cinocinorob.bioeta.science.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightTest {
    @Test
    fun thresholdsFollowGuidelines() {
        val ins = InsightEngine.build(
            InsightInput(age = 45.0, male = true, ldl = 200.0, glucose = 110.0, hba1c = 36.0, crp = 0.5, egfr = 55.0, vo2 = 42.0)
        ).associateBy { it.id }
        assertEquals(Level.ALERT, ins["ldl"]!!.level)
        assertEquals(Level.ATTENTION, ins["glyc"]!!.level)   // glicemia 100–125 = prediabete
        assertEquals(Level.POSITIVE, ins["crp"]!!.level)
        assertEquals(Level.ALERT, ins["egfr"]!!.level)
        assertEquals(Level.POSITIVE, ins["vo2"]!!.level)     // 12 MET
        // Ordinamento: prima gli alert
        val ordered = InsightEngine.build(InsightInput(age = 45.0, male = true, ldl = 200.0, crp = 0.5))
        assertEquals("ldl", ordered.first().id)
    }

    @Test
    fun everyInsightHasVerifiedSource() {
        val all = InsightEngine.build(
            InsightInput(45.0, true, 130.0, 35.0, 200.0, 130.0, 50.0, 5.0, false, 50.0, 15.0, 20.0, 80.0, 85.0, 6.0, 5000.0, 25.0, 3.0)
        )
        assertTrue(all.size >= 12)
        all.forEach { i ->
            assertTrue(i.evidence.isNotEmpty())
            i.evidence.forEach { e -> assertTrue(e.ref.pmid.all { it.isDigit() } && e.ref.doi.startsWith("10.")) }
        }
    }

    @Test
    fun extractsCitedPmids() {
        val t = "Effetto moderato [PMID:21067804], confermato (PMID 32827219) e ripetuto [PMID:21067804]."
        assertEquals(listOf("21067804", "32827219"), CitationCheck.cited(t))
    }
}
