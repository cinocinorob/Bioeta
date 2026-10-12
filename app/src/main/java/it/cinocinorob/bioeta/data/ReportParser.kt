package it.cinocinorob.bioeta.data

import java.time.LocalDate
import java.util.Locale

/**
 * Riconoscimento dei valori in un referto di laboratorio italiano (testo estratto da PDF o OCR).
 *
 * Strategia: per ogni riga si cerca il nome dell'analita (sinonimi/abbreviazioni usati dai
 * laboratori italiani), poi il primo numero che segue il nome, poi l'unità nei caratteri
 * successivi. I valori sono convertiti in unità canoniche e scartati se fuori da un intervallo
 * fisiologicamente plausibile. Il risultato è sempre una bozza da verificare a mano.
 */
object ReportParser {

    data class Found(
        val key: String,
        val value: Double,
        val belowLimit: Boolean,
        val sourceLine: String,
        val unitRecognized: Boolean,
    )

    data class Result(
        val date: LocalDate?,
        val lab: String?,
        val values: Map<String, Found>,
        val notes: List<String>,
    )

    private class Spec(
        val key: String,
        include: String,
        exclude: String? = null,
        val units: List<Pair<Regex, (Double) -> Double?>> = emptyList(),
        /** Conversione quando l'unità non è riconoscibile: (valore canonico, nota) o null per scartare. */
        val noUnit: ((Double) -> Pair<Double, String?>?)? = null,
        /** Correzioni di scala dopo la conversione (es. 6200/µL -> 6,2 ×10³/µL). */
        val fix: ((Double) -> Double)? = null,
        val range: ClosedFloatingPointRange<Double>,
        val needPercent: Boolean = false,
    ) {
        val include = Regex(include)
        val exclude = exclude?.let { Regex(it) }
    }

    // --- unità (testo già normalizzato: minuscolo, µ -> u) ---
    private val MG_DL = Regex("""mg\s*/\s*dl""")
    private val G_DL = Regex("""(?<![mun])g\s*/\s*dl""")
    private val G_L = Regex("""(?<![mun])g\s*/\s*l(?![a-z])""")
    private val MG_L = Regex("""mg\s*/\s*l(?![a-z])""")
    private val MMOL_L = Regex("""mmol\s*/\s*l(?![a-z])""")
    private val MMOL_MOL = Regex("""mmol\s*/\s*mol""")
    private val UMOL_L = Regex("""umol\s*/\s*l(?![a-z])""")
    private val NMOL_L = Regex("""nmol\s*/\s*l(?![a-z])""")
    private val PMOL_L = Regex("""pmol\s*/\s*l(?![a-z])""")
    private val NG_DL = Regex("""ng\s*/\s*dl""")
    private val PCT = Regex("""^\s*%""")

    private fun same(re: Regex) = re to { x: Double -> x }
    private fun conv(re: Regex, k: Double) = re to { x: Double -> x * k }

    private val SPECS: List<Spec> = listOf(
        Spec("albumin", """albumin(a|emia)""", """urin|micro|globulin|rapporto|creatin""",
            listOf(same(G_DL), conv(G_L, 0.1)),
            noUnit = { x -> when { x in 1.5..6.5 -> x to null; x in 15.0..65.0 -> x / 10.0 to "Albumina: unità non indicata, interpretata come g/L"; else -> null } },
            range = 1.5..6.5),
        Spec(Analytes.CREATININE, """creatinin[ae]|creatininemia""", """clearance|urin|egfr|filtrat|stima|rapporto|ckd|albumin""",
            listOf(same(MG_DL), conv(UMOL_L, 1 / 88.42)),
            noUnit = { x -> if (x > 20) x / 88.42 to "Creatinina interpretata come µmol/L" else x to null },
            range = 0.2..15.0),
        Spec(Analytes.GLUCOSE, """glucosio|glicemia|(?<![a-z])glucose""", """urin|curva|carico|post|minuti|'|a1c|glicat""",
            listOf(same(MG_DL), conv(MMOL_L, 18.016)),
            noUnit = { x -> if (x < 30) x * 18.016 to "Glucosio interpretato come mmol/L" else x to null },
            range = 30.0..600.0),
        Spec(Analytes.CRP, """proteina\s*c[\s-]*reattiva|(?<![a-z])p\.?\s?c\.?\s?r(?![a-z])|(?<![a-z])(hs\s*-?\s*)?crp(?![a-z])""",
            """anti|igg|igm|covid|sars|tampone|virus""",
            listOf(same(MG_L), conv(MG_DL, 10.0)),
            noUnit = { x -> x to "PCR: unità non indicata, assunta mg/L — verifica (in mg/dL il valore va ×10)" },
            range = 0.0..300.0),
        Spec(Analytes.LYMPH_PCT, """linfociti|(?<![a-z])lymp?h?(?![a-z])|(?<![a-z])lym(?![a-z])""",
            """atipic|reattiv|#|assolut|totali""", listOf(same(PCT)), range = 1.0..90.0, needPercent = true),
        Spec(Analytes.MCV, """(?<![a-z])mcv(?![a-z])|volume\s*(globulare|corpuscolare|cellulare)\s*medio|(?<![a-z])v\.?c\.?m(?![a-z])""",
            range = 50.0..130.0),
        Spec(Analytes.RDW, """(?<![a-z])rdw""", """sd(?![a-z])""", range = 8.0..30.0),
        Spec(Analytes.ALP, """fosfatasi\s*alcalin|(?<![a-z])alp(?![a-z])|(?<![a-z])f\.a\.l(?![a-z])""", """isoenz|ossea""",
            range = 10.0..1500.0),
        Spec(Analytes.WBC, """leucociti|globuli\s*bianchi|(?<![a-z])wbc(?![a-z])|(?<![a-z])g\.b\.|(?<![a-z])gb(?![a-z])""",
            """urin|sediment|campo|esteras|hpf""",
            fix = { x -> if (x > 100) x / 1000.0 else x }, range = 0.5..100.0),
        Spec("hb", """emoglobina|(?<![a-z])hgb(?![a-z])|(?<![a-z])hb(?![a-z])""",
            """glic|a1c|corpuscolar|media|concentraz|mch|urin|elettrofor|fetal|a2""",
            listOf(same(G_DL), conv(G_L, 0.1)),
            noUnit = { x -> if (x > 30) x / 10.0 to null else x to null }, range = 4.0..25.0),
        Spec("rbc", """eritrociti|globuli\s*rossi|(?<![a-z])rbc(?![a-z])|emazie|(?<![a-z])g\.r\.""",
            """urin|sediment|campo|nucleat|hpf|distribuz""",
            fix = { x -> if (x > 1000) x / 1_000_000.0 else x }, range = 1.0..9.0),
        Spec("hct", """ematocrito|(?<![a-z])hct(?![a-z])|(?<![a-z])ht(?![a-z])""", range = 10.0..70.0),
        Spec("plt", """piastrine|(?<![a-z])plt(?![a-z])|trombociti""", """volume|mpv|pdw|pct|piastrinocrit|aggreg|distribuz""",
            fix = { x -> if (x > 2000) x / 1000.0 else x }, range = 5.0..2000.0),
        Spec("neut_pct", """neutrofili|(?<![a-z])neut?(?![a-z])""", """#|assolut""", listOf(same(PCT)),
            range = 1.0..99.0, needPercent = true),
        Spec("hba1c", """emoglobina\s*glic|hb\s*a\s*1\s*c|(?<![a-z])a1c(?![a-z])""", null,
            listOf(same(MMOL_MOL), Regex("""%""") to { x: Double -> x * 10.929 - 23.52 }),
            noUnit = { x -> if (x < 20) x * 10.929 - 23.52 to "HbA1c interpretata come %" else x to null },
            range = 15.0..200.0),
        Spec("insulin", """insulin""", """resisten|homa|anticorp|igf""",
            listOf(conv(PMOL_L, 1.0 / 6.0)), range = 0.5..300.0),
        Spec("uric", """acido\s*urico|uricemia""", """urin""",
            listOf(same(MG_DL), conv(UMOL_L, 1 / 59.48)),
            noUnit = { x -> if (x > 20) x / 59.48 to null else x to null }, range = 0.5..20.0),
        Spec("chol", """colesterol(o|emia)""", """hdl|ldl|non|rapporto|indice|esterif|libero|rischio""",
            listOf(same(MG_DL), conv(MMOL_L, 38.67)),
            noUnit = { x -> if (x < 20) x * 38.67 to "Colesterolo interpretato come mmol/L" else x to null },
            range = 50.0..600.0),
        Spec("hdl", """(?<![a-z])hdl""", """non[\s-]*hdl|ldl|rapporto|indice|rischio|tot""",
            listOf(same(MG_DL), conv(MMOL_L, 38.67)),
            noUnit = { x -> if (x < 5) x * 38.67 to null else x to null }, range = 10.0..200.0),
        Spec("ldl", """(?<![a-z])ldl""", """non|vldl|rapporto|indice|rischio|target|obiettivo|hdl""",
            listOf(same(MG_DL), conv(MMOL_L, 38.67)),
            noUnit = { x -> if (x < 15) x * 38.67 to null else x to null }, range = 10.0..500.0),
        Spec("tg", """trigliceridi|trigliceridemia""", null,
            listOf(same(MG_DL), conv(MMOL_L, 88.57)),
            noUnit = { x -> if (x < 10) x * 88.57 to "Trigliceridi interpretati come mmol/L" else x to null },
            range = 10.0..3000.0),
        Spec("apob", """apo\s*(lipoproteina)?\s*-?\s*b(?![a-z\d])|apolipoproteina\s*b""", null,
            listOf(same(MG_DL), conv(G_L, 100.0)),
            noUnit = { x -> if (x < 3) x * 100 to null else x to null }, range = 10.0..300.0),
        Spec("lpa", """lipoproteina\s*\(\s*a\s*\)|lp\s*\(\s*a\s*\)|(?<![a-z])lpa(?![a-z])""", null,
            listOf(same(MG_DL), NMOL_L to { _: Double -> null }), range = 0.0..500.0),
        Spec("alt", """(?<![a-z])alt(?![a-z])|(?<![a-z])s?gpt(?![a-z])|alanina\s*amino|(?<![a-z])alat(?![a-z])""",
            """rapporto|de\s*ritis|ast\s*/\s*alt|alt\s*/\s*ast""", range = 1.0..3000.0),
        Spec("ast", """(?<![a-z])ast(?![a-z])|(?<![a-z])s?got(?![a-z])|aspartato|(?<![a-z])asat(?![a-z])""",
            """rapporto|de\s*ritis|ast\s*/\s*alt|alt\s*/\s*ast""", range = 1.0..3000.0),
        Spec("ggt", """gamma\s*-?\s*g\.?\s*t|(?<![a-z])ggt|(?<![a-z])g\s*-\s*gt|gamma\s*-?\s*glutamil""", null,
            range = 1.0..3000.0),
        Spec("bili", """bilirubin(a|emia)\s*tot""", null,
            listOf(same(MG_DL), conv(UMOL_L, 1 / 17.1)), range = 0.05..30.0),
        Spec(Analytes.TOTAL_PROTEIN, """proteine\s*(sieriche\s*)?totali|protidemia""", """urin""",
            listOf(same(G_DL), conv(G_L, 0.1)),
            noUnit = { x -> if (x > 20) x / 10.0 to null else x to null }, range = 3.0..12.0),
        Spec("urea", """azotemia|(?<![a-z])urea(?![a-z])""", """urin|bun|azoto|clearance""",
            listOf(same(MG_DL), conv(MMOL_L, 6.006)), range = 3.0..300.0),
        Spec("cystc", """cistatina""", range = 0.2..10.0),
        Spec("ferritin", """ferritin""", range = 1.0..5000.0),
        Spec("vitd", """vitamina\s*d|25\s*-?\s*\(?\s*oh\s*\)?\s*-?\s*(vit|d)|colecalciferol|calcidiol""",
            """1\s*[,.]?\s*25|diidross""", listOf(conv(NMOL_L, 0.4006)), range = 2.0..200.0),
        Spec("b12", """vitamina\s*b\s*12|cobalamina""", null, listOf(conv(PMOL_L, 1.355)), range = 50.0..3000.0),
        Spec("homocys", """omocisteina""", range = 1.0..200.0),
        Spec("tsh", """(?<![a-z])tsh(?![a-z])|tireotropin""", """recettor|trab|anticorp""", range = 0.001..100.0),
        Spec("testo", """testosterone""", """libero|biodispon|free|calcolat""",
            listOf(conv(NMOL_L, 0.2884), conv(NG_DL, 0.01)),
            noUnit = { x -> if (x > 30) x * 0.01 to null else x to null }, range = 0.05..20.0),
    )

    /**
     * Numero candidato: non preceduto da lettere/cifre/"^" "/" ":" (esclude b12, 10^3, date, orari)
     * e non seguito da "-OH", cifre, "^", "/" o ":" (esclude 25-OH, 10^3, date).
     */
    private val NUM = Regex("""(?<![\p{L}\d^/.,:])([<>≤≥]\s*)?(\d+(?:[.,]\d+)?)(?!\s*-?\s*oh)(?![\d^/:])""")

    private val DATE = Regex("""(?<!\d)(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})(?!\d)""")
    private val DATE_KEYS = Regex("""prelievo|prelevato|accettazione|accettato|raccolta|campionamento|data\s*esame""")
    private val BIRTH = Regex("""nascita|nato|nata(?![a-z])|n\.\s*il""")
    private val LAB = Regex("""laborator|analisi\s*cliniche|diagnostic|poliambulator|centro\s*medico|synlab|lifebrain|bianalisi|unilabs""")

    fun normalize(s: String): String = s.lowercase(Locale.ROOT)
        .replace('µ', 'u').replace('μ', 'u')
        .replace('³', '3').replace('⁹', '9').replace('⁶', '6')
        .replace(' ', ' ').replace("γ", "gamma ")
        .replace('–', '-').replace('—', '-')

    fun parse(text: String): Result {
        val raw = text.lines()
        val lines = raw.map(::normalize)
        val found = linkedMapOf<String, Found>()
        val notes = mutableListOf<String>()

        fun matchesAny(l: String) = SPECS.any { it.include.containsMatchIn(l) }

        for ((i, line) in lines.withIndex()) {
            for (spec in SPECS) {
                val m = spec.include.find(line) ?: continue
                if (spec.exclude?.containsMatchIn(line) == true) continue

                var after = line.substring(m.range.last + 1)
                var cand = NUM.find(after)?.takeIf { it.range.first <= 60 }
                if (cand == null && i + 1 < lines.size) {
                    val next = lines[i + 1]
                    if (!matchesAny(next)) {
                        val c2 = NUM.find(next)
                        if (c2 != null && next.substring(0, c2.range.first).isBlank()) {
                            after = next; cand = c2
                        }
                    }
                }
                if (cand == null) continue

                val below = cand.groupValues[1].trim().let { it == "<" || it == "≤" }
                val v = cand.groupValues[2].replace(',', '.').toDoubleOrNull() ?: continue
                val unitText = after.substring(cand.range.last + 1).take(18)
                val isPercent = PCT.containsMatchIn(unitText) || line.contains('%')

                var key = spec.key
                var range = spec.range
                if (key == "albumin" && isPercent) { key = Analytes.ALBUMIN_PCT; range = 30.0..80.0 }
                if (spec.needPercent && !isPercent) continue
                if (key in found) continue

                var canonical: Double? = null
                var recognized = false
                var note: String? = null
                if (key == Analytes.ALBUMIN_PCT) {
                    canonical = v; recognized = true
                } else {
                    for ((re, f) in spec.units) {
                        if (re.containsMatchIn(unitText)) {
                            canonical = f(v); recognized = true
                            if (canonical == null) note = "${Analytes.get(key)?.name ?: key}: unità non convertibile, valore ignorato"
                            break
                        }
                    }
                    if (!recognized) {
                        val nu = spec.noUnit
                        if (nu != null) {
                            val r = nu(v)
                            canonical = r?.first
                            note = r?.second
                        } else canonical = v
                    }
                }
                if (note != null) notes += note
                var c = canonical ?: continue
                spec.fix?.let { c = it(c) }
                if (c !in range) {
                    notes += "${Analytes.get(key)?.name ?: key}: valore ${cand.value.trim()} fuori dall'intervallo plausibile, ignorato"
                    continue
                }
                found[key] = Found(key, c, below, raw[i].trim(), recognized)
            }
        }

        return Result(findDate(lines), findLab(raw, lines), found, notes.distinct())
    }

    private fun toDate(m: MatchResult): LocalDate? {
        val d = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        var y = m.groupValues[3].toInt()
        if (y < 100) y += 2000
        return runCatching { LocalDate.of(y, mo, d) }.getOrNull()
            ?.takeIf { it.year in 1990..2100 && !it.isAfter(LocalDate.now().plusDays(1)) }
    }

    fun findDate(lines: List<String>): LocalDate? {
        // 1) data che segue una parola chiave di prelievo/accettazione
        for ((i, l) in lines.withIndex()) {
            val k = DATE_KEYS.find(l) ?: continue
            if (BIRTH.containsMatchIn(l.substring(k.range.first))) continue
            val sameLine = DATE.findAll(l).firstOrNull { it.range.first > k.range.first }?.let(::toDate)
            if (sameLine != null) return sameLine
            if (i + 1 < lines.size && !BIRTH.containsMatchIn(lines[i + 1])) {
                DATE.find(lines[i + 1])?.let(::toDate)?.let { return it }
            }
        }
        // 2) data più frequente fuori dalle righe anagrafiche
        val all = lines.filterNot { BIRTH.containsMatchIn(it) }
            .flatMap { l -> DATE.findAll(l).mapNotNull(::toDate).toList() }
        return all.groupingBy { it }.eachCount().maxWithOrNull(compareBy({ it.value }, { it.key }))?.key
    }

    private fun findLab(raw: List<String>, lines: List<String>): String? {
        val idx = lines.indexOfFirst { LAB.containsMatchIn(it) }
        if (idx < 0) return null
        return raw[idx].trim().replace(Regex("""\s{2,}"""), " ").take(60)
    }
}
