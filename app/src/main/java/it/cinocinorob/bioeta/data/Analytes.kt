package it.cinocinorob.bioeta.data

/** Unità di inserimento: valore_canonico = valore * factor + offset. */
data class UnitOpt(val label: String, val factor: Double = 1.0, val offset: Double = 0.0) {
    fun toCanonical(v: Double): Double = v * factor + offset
    fun fromCanonical(c: Double): Double = (c - offset) / factor
}

enum class Group(val label: String) {
    PHENO("Biomarcatori PhenoAge"),
    EMOCROMO("Emocromo"),
    METABOLISMO("Metabolismo glucidico"),
    LIPIDI("Lipidi"),
    FEGATO("Fegato e proteine"),
    RENE("Rene"),
    ALTRO("Altri"),
}

data class Analyte(
    val key: String,
    val name: String,
    val group: Group,
    /** La prima unità è quella canonica (fattore 1). */
    val units: List<UnitOpt>,
    val decimals: Int = 1,
    val note: String? = null,
) {
    val unit: String get() = units.first().label
}

object Analytes {
    // Chiavi usate dal calcolo PhenoAge
    const val ALBUMIN = "albumin"
    const val ALBUMIN_PCT = "albumin_pct"
    const val TOTAL_PROTEIN = "total_protein"
    const val CREATININE = "creatinine"
    const val GLUCOSE = "glucose"
    const val CRP = "crp"
    const val LYMPH_PCT = "lymph_pct"
    const val MCV = "mcv"
    const val RDW = "rdw"
    const val ALP = "alp"
    const val WBC = "wbc"

    val all: List<Analyte> = listOf(
        // --- PhenoAge ---
        Analyte(ALBUMIN, "Albumina (sierica)", Group.PHENO,
            listOf(UnitOpt("g/dL"), UnitOpt("g/L", 0.1)), 2,
            "Se il referto riporta solo l'albumina % dell'elettroforesi, inseriscila sotto \"Fegato e proteine\" insieme alle proteine totali: verrà derivata."),
        Analyte(CREATININE, "Creatinina", Group.PHENO,
            listOf(UnitOpt("mg/dL"), UnitOpt("µmol/L", 1.0 / 88.42)), 2),
        Analyte(GLUCOSE, "Glucosio (a digiuno)", Group.PHENO,
            listOf(UnitOpt("mg/dL"), UnitOpt("mmol/L", 18.016)), 0),
        Analyte(CRP, "PCR (meglio hs-PCR)", Group.PHENO,
            listOf(UnitOpt("mg/L"), UnitOpt("mg/dL", 10.0)), 2,
            "Se il referto indica \"<0,3\", inserisci 0,3 e spunta \"sotto il limite\"."),
        Analyte(LYMPH_PCT, "Linfociti %", Group.PHENO, listOf(UnitOpt("%")), 1),
        Analyte(MCV, "MCV", Group.PHENO, listOf(UnitOpt("fL")), 1),
        Analyte(RDW, "RDW-CV", Group.PHENO, listOf(UnitOpt("%")), 1,
            "Usa l'RDW-CV (%), non l'RDW-SD (fL)."),
        Analyte(ALP, "Fosfatasi alcalina", Group.PHENO, listOf(UnitOpt("U/L")), 0),
        Analyte(WBC, "Globuli bianchi", Group.PHENO,
            listOf(UnitOpt("10³/µL"), UnitOpt("10⁹/L", 1.0)), 2),

        // --- Emocromo ---
        Analyte("hb", "Emoglobina", Group.EMOCROMO, listOf(UnitOpt("g/dL"), UnitOpt("g/L", 0.1)), 1),
        Analyte("rbc", "Globuli rossi", Group.EMOCROMO, listOf(UnitOpt("10⁶/µL")), 2),
        Analyte("hct", "Ematocrito", Group.EMOCROMO, listOf(UnitOpt("%")), 1),
        Analyte("plt", "Piastrine", Group.EMOCROMO, listOf(UnitOpt("10³/µL")), 0),
        Analyte("neut_pct", "Neutrofili %", Group.EMOCROMO, listOf(UnitOpt("%")), 1),

        // --- Metabolismo ---
        Analyte("hba1c", "Emoglobina glicata (HbA1c)", Group.METABOLISMO,
            listOf(UnitOpt("mmol/mol"), UnitOpt("%", 10.929, -23.52)), 0),
        Analyte("insulin", "Insulina (a digiuno)", Group.METABOLISMO,
            listOf(UnitOpt("µU/mL"), UnitOpt("pmol/L", 1.0 / 6.0)), 1),
        Analyte("uric", "Acido urico", Group.METABOLISMO,
            listOf(UnitOpt("mg/dL"), UnitOpt("µmol/L", 1.0 / 59.48)), 1),

        // --- Lipidi ---
        Analyte("chol", "Colesterolo totale", Group.LIPIDI,
            listOf(UnitOpt("mg/dL"), UnitOpt("mmol/L", 38.67)), 0),
        Analyte("hdl", "Colesterolo HDL", Group.LIPIDI,
            listOf(UnitOpt("mg/dL"), UnitOpt("mmol/L", 38.67)), 0),
        Analyte("ldl", "Colesterolo LDL", Group.LIPIDI,
            listOf(UnitOpt("mg/dL"), UnitOpt("mmol/L", 38.67)), 0),
        Analyte("tg", "Trigliceridi", Group.LIPIDI,
            listOf(UnitOpt("mg/dL"), UnitOpt("mmol/L", 88.57)), 0),
        Analyte("apob", "Apolipoproteina B", Group.LIPIDI,
            listOf(UnitOpt("mg/dL"), UnitOpt("g/L", 100.0)), 0),
        Analyte("lpa", "Lipoproteina(a)", Group.LIPIDI, listOf(UnitOpt("mg/dL")), 0),

        // --- Fegato e proteine ---
        Analyte("alt", "ALT (GPT)", Group.FEGATO, listOf(UnitOpt("U/L")), 0),
        Analyte("ast", "AST (GOT)", Group.FEGATO, listOf(UnitOpt("U/L")), 0),
        Analyte("ggt", "Gamma-GT", Group.FEGATO, listOf(UnitOpt("U/L")), 0),
        Analyte("bili", "Bilirubina totale", Group.FEGATO, listOf(UnitOpt("mg/dL")), 2),
        Analyte(TOTAL_PROTEIN, "Proteine totali", Group.FEGATO,
            listOf(UnitOpt("g/dL"), UnitOpt("g/L", 0.1)), 1),
        Analyte(ALBUMIN_PCT, "Albumina % (elettroforesi)", Group.FEGATO, listOf(UnitOpt("%")), 1),

        // --- Rene ---
        Analyte("urea", "Azotemia (urea)", Group.RENE, listOf(UnitOpt("mg/dL")), 0),
        Analyte("cystc", "Cistatina C", Group.RENE, listOf(UnitOpt("mg/L")), 2),

        // --- Altri ---
        Analyte("ferritin", "Ferritina", Group.ALTRO, listOf(UnitOpt("ng/mL")), 0),
        Analyte("vitd", "Vitamina D (25-OH)", Group.ALTRO,
            listOf(UnitOpt("ng/mL"), UnitOpt("nmol/L", 0.4006)), 1),
        Analyte("b12", "Vitamina B12", Group.ALTRO,
            listOf(UnitOpt("pg/mL"), UnitOpt("pmol/L", 1.355)), 0),
        Analyte("homocys", "Omocisteina", Group.ALTRO, listOf(UnitOpt("µmol/L")), 1),
        Analyte("tsh", "TSH", Group.ALTRO, listOf(UnitOpt("mUI/L")), 2),
        Analyte("testo", "Testosterone totale", Group.ALTRO,
            listOf(UnitOpt("ng/mL"), UnitOpt("nmol/L", 0.2884)), 2),
    )

    private val byKey = all.associateBy { it.key }
    fun get(key: String): Analyte? = byKey[key]

    /** Chiave virtuale per eGFR calcolato (non inseribile). */
    const val EGFR = "egfr_calc"
}
