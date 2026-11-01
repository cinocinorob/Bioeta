package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.EcgParser
import it.cinocinorob.bioeta.science.EcgRecord
import it.cinocinorob.bioeta.science.EcgResult
import it.cinocinorob.bioeta.science.EcgStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EcgTest {
    private val en = """
        Fitbit ECG App
        Name: Robbie   Date of birth: 10/05/1981
        Recorded: Oct 30, 2026 10:21 AM
        Result: Normal Sinus Rhythm
        Average heart rate: 64 bpm
        Lead I  25 mm/s  10 mm/mV
    """.trimIndent()

    private val ita = """
        App ECG – Valutazione del ritmo cardiaco
        Data di nascita 10/05/1981
        Registrato il 28 ott 2026, 21:05
        Risultato: Fibrillazione atriale
        Frequenza cardiaca media 96 bpm
        Derivazione I
    """.trimIndent()

    @Test fun recognisesEcgReports() {
        assertTrue(EcgParser.looksLikeEcg(en))
        assertTrue(EcgParser.looksLikeEcg(ita))
        // un referto di analisi del sangue non è un ECG
        assertFalse(EcgParser.looksLikeEcg("Laboratorio analisi\nGlucosio 88 mg/dL\nCreatinina 0,95 mg/dL\nAlbumina 4,6 g/dL"))
    }

    @Test fun parsesEnglishFitbitReport() {
        val p = EcgParser.parse(en)
        assertEquals(EcgResult.SINUS, p.result)
        assertEquals(64, p.heartRate)
        // la data di nascita (1981) viene scartata: conta quella della registrazione
        assertEquals(LocalDateTime.of(2026, 10, 30, 10, 21), p.time)
    }

    @Test fun parsesItalianReport() {
        val p = EcgParser.parse(ita)
        assertEquals(EcgResult.AFIB, p.result)
        assertEquals(96, p.heartRate)
        assertEquals(LocalDateTime.of(2026, 10, 28, 21, 5), p.time)
    }

    @Test fun negationIsNotAfib() {
        assertEquals(EcgResult.SINUS, EcgParser.parse("ECG result: No signs of atrial fibrillation. 70 bpm").result)
        assertEquals(EcgResult.SINUS, EcgParser.parse("ECG: nessun segno di fibrillazione atriale").result)
    }

    @Test fun otherOutcomes() {
        assertEquals(EcgResult.INCONCLUSIVE, EcgParser.parse("ECG – Inconclusive result. Lead I").result)
        assertEquals(EcgResult.HIGH_HR, EcgParser.parse("ECG: High heart rate. Heart rate over 120 bpm").result)
        assertEquals(EcgResult.UNKNOWN, EcgParser.parse("ECG senza risultato leggibile").result)
    }

    @Test fun summaryCountsLastYear() {
        val now = LocalDate.of(2026, 11, 1)
        fun r(days: Long, res: EcgResult, hr: Int?) = EcgRecord("$days", now.minusDays(days).atTime(9, 0), res, hr, "pdf", true)
        val s = EcgStats.summary(listOf(r(1, EcgResult.SINUS, 60), r(30, EcgResult.AFIB, 100), r(40, EcgResult.INCONCLUSIVE, null), r(500, EcgResult.AFIB, 90)), now)
        assertEquals(4, s.total); assertEquals(3, s.last12m); assertEquals(1, s.afib12m); assertEquals(1, s.inconclusive12m)
        assertEquals(80.0, s.meanHr!!, 1e-9)
    }
}
