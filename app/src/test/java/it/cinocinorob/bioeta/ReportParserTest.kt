package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.data.ReportParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportParserTest {

    private val sampleItalian = """
        LABORATORIO ANALISI CLINICHE ESEMPIO S.R.L.
        Paziente: ROSSI MARIO      Data di nascita: 01/02/1981
        Data prelievo: 03/10/2026   Data referto: 04/10/2026  ore 10:42
        EMOCROMO
        Leucociti (WBC)              6.20     10^3/µL      4.00 - 10.00
        Eritrociti (RBC)             4.85     10^6/µL      4.50 - 5.90
        Emoglobina (HGB)             15.1     g/dL         13.5 - 17.5
        Ematocrito (HCT)             44.2     %            40.0 - 52.0
        MCV                          91.1     fL           80.0 - 99.0
        MCH                          31.1     pg           27.0 - 33.0
        MCHC                         34.2     g/dL         32.0 - 36.0
        RDW-CV                       12.9     %            11.5 - 14.5
        RDW-SD                       42.1     fL           37.0 - 54.0
        Piastrine (PLT)              245      10^3/µL      150 - 400
        Neutrofili %                 55.2     %            40.0 - 75.0
        Linfociti %                  32.4     %            20.0 - 45.0
        Linfociti #                  2.01     10^3/µL      1.00 - 4.00
        CHIMICA CLINICA
        Glicemia                     92       mg/dL        70 - 100
        Creatinina                   0.94     mg/dL        0.67 - 1.17
        eGFR (CKD-EPI)               98       mL/min/1.73m²
        Colesterolo totale           195      mg/dL        < 200
        Colesterolo HDL              58       mg/dL        > 40
        Colesterolo LDL (calcolato)  118      mg/dL
        Trigliceridi                 95       mg/dL        < 150
        ALT (GPT)                    22       U/L          < 50
        AST (GOT)                    20       U/L          < 50
        Gamma-GT                     18       U/L          < 55
        Fosfatasi alcalina           64       U/L          40 - 130
        Proteina C reattiva          <0,30    mg/L         < 5,00
        Emoglobina glicata (HbA1c)   36       mmol/mol     20 - 42
        25-OH Vitamina D             31.5     ng/mL        30 - 100
        Vitamina B12                 410      pg/mL        200 - 900
        PROTIDOGRAMMA
        Proteine totali              7.1      g/dL         6.4 - 8.3
        Albumina                     59.8     %            55.8 - 66.1
        ESAME URINE
        Glucosio                     assente
        Leucociti                    2-3 per campo
    """.trimIndent()

    @Test
    fun parsesTypicalItalianReport() {
        val r = ReportParser.parse(sampleItalian)
        val v = r.values.mapValues { it.value.value }
        assertEquals(LocalDate.of(2026, 10, 3), r.date)
        assertEquals(6.20, v["wbc"]!!, 1e-9)
        assertEquals(4.85, v["rbc"]!!, 1e-9)
        assertEquals(15.1, v["hb"]!!, 1e-9)
        assertEquals(44.2, v["hct"]!!, 1e-9)
        assertEquals(91.1, v["mcv"]!!, 1e-9)
        assertEquals(12.9, v["rdw"]!!, 1e-9)
        assertEquals(245.0, v["plt"]!!, 1e-9)
        assertEquals(55.2, v["neut_pct"]!!, 1e-9)
        assertEquals(32.4, v["lymph_pct"]!!, 1e-9)
        assertEquals(92.0, v["glucose"]!!, 1e-9)
        assertEquals(0.94, v["creatinine"]!!, 1e-9)
        assertEquals(195.0, v["chol"]!!, 1e-9)
        assertEquals(58.0, v["hdl"]!!, 1e-9)
        assertEquals(118.0, v["ldl"]!!, 1e-9)
        assertEquals(95.0, v["tg"]!!, 1e-9)
        assertEquals(22.0, v["alt"]!!, 1e-9)
        assertEquals(20.0, v["ast"]!!, 1e-9)
        assertEquals(18.0, v["ggt"]!!, 1e-9)
        assertEquals(64.0, v["alp"]!!, 1e-9)
        assertEquals(0.30, v["crp"]!!, 1e-9)
        assertTrue(r.values["crp"]!!.belowLimit)
        assertEquals(36.0, v["hba1c"]!!, 1e-9)
        assertEquals(31.5, v["vitd"]!!, 1e-9)
        assertEquals(410.0, v["b12"]!!, 1e-9)
        assertEquals(7.1, v["total_protein"]!!, 1e-9)
        assertEquals(59.8, v["albumin_pct"]!!, 1e-9)
        assertNull(v["albumin"])
        assertTrue(r.lab!!.contains("LABORATORIO"))
    }

    private val sampleSiUnits = """
        Accettazione 15-03-2025
        Globuli bianchi    6200 /mm3
        Glucosio           5.1 mmol/L
        Creatinina         80 umol/L
        Albumina           44 g/L
        PCR                0.12 mg/dL
        HbA1c              5.6 %
        Colesterolo totale 5.2 mmol/L
        Trigliceridi       1.1 mmol/L
    """.trimIndent()

    @Test
    fun convertsSiUnits() {
        val r = ReportParser.parse(sampleSiUnits)
        val v = r.values.mapValues { it.value.value }
        assertEquals(LocalDate.of(2025, 3, 15), r.date)
        assertEquals(6.2, v["wbc"]!!, 1e-9)
        assertEquals(5.1 * 18.016, v["glucose"]!!, 1e-6)
        assertEquals(80 / 88.42, v["creatinine"]!!, 1e-6)
        assertEquals(4.4, v["albumin"]!!, 1e-9)
        assertEquals(1.2, v["crp"]!!, 1e-9)
        assertEquals(5.6 * 10.929 - 23.52, v["hba1c"]!!, 1e-6)
        assertEquals(5.2 * 38.67, v["chol"]!!, 1e-6)
        assertEquals(1.1 * 88.57, v["tg"]!!, 1e-6)
        assertFalse(r.values["crp"]!!.belowLimit)
    }

    @Test
    fun twoColumnLayoutAndWrappedValue() {
        val text = """
            Glicemia 88 mg/dL 70-100      Creatinina 1,02 mg/dL 0,7-1,2
            Fosfatasi alcalina
            71 U/L
        """.trimIndent()
        val v = ReportParser.parse(text).values.mapValues { it.value.value }
        assertEquals(88.0, v["glucose"]!!, 1e-9)
        assertEquals(1.02, v["creatinine"]!!, 1e-9)
        assertEquals(71.0, v["alp"]!!, 1e-9)
    }

    @Test
    fun ignoresImplausibleValues() {
        val v = ReportParser.parse("RDW 42 fL\nMCV 2025").values
        assertNull(v["rdw"])
        assertNull(v["mcv"])
    }
}
