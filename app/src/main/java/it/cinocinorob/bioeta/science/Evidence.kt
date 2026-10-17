package it.cinocinorob.bioeta.science

/**
 * Fonti verificate su PubMed (titolo, PMID, DOI controllati il 12/10/2026).
 * "certainty" riporta il GRADE quando dichiarato dagli autori; altrimenti è una valutazione
 * conservativa basata sul disegno dello studio, ed è indicata come tale.
 */
enum class StudyType(val label: String) {
    GUIDELINE("Linea guida"),
    CONSENSUS("Consensus"),
    META_RCT("Meta-analisi di RCT"),
    META_OBS("Meta-analisi osservazionale"),
    RCT("RCT"),
    SYSTEMATIC("Revisione sistematica"),
    OBS("Studio osservazionale"),
}

data class Ref(
    val short: String,
    val title: String,
    val pmid: String,
    val doi: String,
    val type: StudyType,
) {
    val url: String get() = "https://pubmed.ncbi.nlm.nih.gov/$pmid/"
}

object Refs {
    val CTT = Ref("CTT Collaboration, Lancet 2010", "Efficacy and safety of more intensive lowering of LDL cholesterol: a meta-analysis of data from 170,000 participants in 26 randomised trials", "21067804", "10.1016/S0140-6736(10)61350-5", StudyType.META_RCT)
    val ESC2019 = Ref("Mach et al., Eur Heart J 2020", "2019 ESC/EAS Guidelines for the management of dyslipidaemias", "31504418", "10.1093/eurheartj/ehz455", StudyType.GUIDELINE)
    val HOOPER = Ref("Hooper et al., Cochrane 2020", "Reduction in saturated fat intake for cardiovascular disease", "32827219", "10.1002/14651858.CD011737.pub3", StudyType.META_RCT)
    val PSYLLIUM = Ref("Jovanovski et al., Am J Clin Nutr 2018", "Effect of psyllium fiber on LDL cholesterol, non-HDL cholesterol and apolipoprotein B: a systematic review and meta-analysis of RCTs", "30239559", "10.1093/ajcn/nqy115", StudyType.META_RCT)
    val KODAMA_HDL = Ref("Kodama et al., Arch Intern Med 2007", "Effect of aerobic exercise training on serum levels of HDL cholesterol: a meta-analysis", "17533202", "10.1001/archinte.167.10.999", StudyType.META_RCT)
    val ADA2024 = Ref("ADA, Diabetes Care 2024", "2. Diagnosis and Classification of Diabetes: Standards of Care in Diabetes—2024", "38078589", "10.2337/dc24-S002", StudyType.GUIDELINE)
    val DPP = Ref("Knowler et al. (DPP), N Engl J Med 2002", "Reduction in the incidence of type 2 diabetes with lifestyle intervention or metformin", "11832527", "10.1056/NEJMoa012512", StudyType.RCT)
    val UMPIERRE = Ref("Umpierre et al., JAMA 2011", "Physical activity advice only or structured exercise training and association with HbA1c levels in type 2 diabetes", "21540423", "10.1001/jama.2011.576", StudyType.META_RCT)
    val PEARSON = Ref("Pearson et al., Circulation 2003", "Markers of inflammation and cardiovascular disease: AHA/CDC scientific statement", "12551878", "10.1161/01.CIR.0000052939.59093.45", StudyType.GUIDELINE)
    val SELVIN = Ref("Selvin et al., Arch Intern Med 2007", "The effect of weight loss on C-reactive protein: a systematic review", "17210875", "10.1001/archinte.167.1.31", StudyType.SYSTEMATIC)
    val KDIGO = Ref("KDIGO, Kidney Int 2024", "KDIGO 2024 Clinical Practice Guideline for the Evaluation and Management of CKD", "38490803", "10.1016/j.kint.2023.10.018", StudyType.GUIDELINE)
    val IOM = Ref("Ross et al., J Clin Endocrinol Metab 2011", "The 2011 report on dietary reference intakes for calcium and vitamin D from the Institute of Medicine", "21118827", "10.1210/jc.2010-2704", StudyType.GUIDELINE)
    val VITAL = Ref("Manson et al. (VITAL), N Engl J Med 2019", "Vitamin D supplements and prevention of cancer and cardiovascular disease", "30415629", "10.1056/NEJMoa1809944", StudyType.RCT)
    val HCY = Ref("Martí-Carvajal et al., Cochrane 2017", "Homocysteine-lowering interventions for preventing cardiovascular events", "28816346", "10.1002/14651858.CD006612.pub5", StudyType.META_RCT)
    val LPA = Ref("Kronenberg et al., Eur Heart J 2022", "Lipoprotein(a) in atherosclerotic cardiovascular disease and aortic stenosis: EAS consensus statement", "36036785", "10.1093/eurheartj/ehac361", StudyType.CONSENSUS)
    val ZHANG_RHR = Ref("Zhang et al., CMAJ 2016", "Resting heart rate and all-cause and cardiovascular mortality in the general population: a meta-analysis", "26598376", "10.1503/cmaj.150535", StudyType.META_OBS)
    val REIMERS = Ref("Reimers et al., J Clin Med 2018", "Effects of exercise on the resting heart rate: a systematic review and meta-analysis of interventional studies", "30513777", "10.3390/jcm7120503", StudyType.SYSTEMATIC)
    val KODAMA_CRF = Ref("Kodama et al., JAMA 2009", "Cardiorespiratory fitness as a quantitative predictor of all-cause mortality and cardiovascular events", "19454641", "10.1001/jama.2009.681", StudyType.META_OBS)
    val MILANOVIC = Ref("Milanović et al., Sports Med 2015", "Effectiveness of HIT and continuous endurance training for VO2max improvements", "26243014", "10.1007/s40279-015-0365-0", StudyType.META_RCT)
    val WATSON = Ref("Watson et al., Sleep 2015", "Recommended amount of sleep for a healthy adult: AASM/SRS joint consensus statement", "26039963", "10.5665/sleep.4716", StudyType.CONSENSUS)
    val CAPPUCCIO = Ref("Cappuccio et al., Sleep 2010", "Sleep duration and all-cause mortality: a systematic review and meta-analysis of prospective studies", "20469800", "10.1093/sleep/33.5.585", StudyType.META_OBS)
    val PALUCH = Ref("Paluch et al., Lancet Public Health 2022", "Daily steps and all-cause mortality: a meta-analysis of 15 international cohorts", "35247352", "10.1016/S2468-2667(21)00302-9", StudyType.META_OBS)
    val ESC_BP = Ref("McEvoy et al., Eur Heart J 2024", "2024 ESC Guidelines for the management of elevated blood pressure and hypertension", "39210715", "10.1093/eurheartj/ehae178", StudyType.GUIDELINE)
    val ESH_BP = Ref("Mancia et al., J Hypertens 2023", "2023 ESH Guidelines for the management of arterial hypertension", "37345492", "10.1097/HJH.0000000000003480", StudyType.GUIDELINE)
    val ETTEHAD = Ref("Ettehad et al., Lancet 2016", "Blood pressure lowering for prevention of cardiovascular disease and death: a systematic review and meta-analysis", "26724178", "10.1016/S0140-6736(15)01225-8", StudyType.META_RCT)
    val SALT = Ref("He et al., BMJ 2013", "Effect of longer term modest salt reduction on blood pressure: Cochrane systematic review and meta-analysis", "23558162", "10.1136/bmj.f1325", StudyType.META_RCT)
    val DASH = Ref("Filippou et al., Adv Nutr 2020", "DASH diet and blood pressure reduction in adults with and without hypertension: systematic review and meta-analysis of RCTs", "32330233", "10.1093/advances/nmaa041", StudyType.META_RCT)
    val EXERCISE_BP = Ref("Edwards et al., Br J Sports Med 2023", "Exercise training and resting blood pressure: a large-scale pairwise and network meta-analysis of RCTs", "37491419", "10.1136/bjsports-2022-106503", StudyType.META_RCT)
    val WEIGHT_BP = Ref("Neter et al., Hypertension 2003", "Influence of weight reduction on blood pressure: a meta-analysis of randomized controlled trials", "12975389", "10.1161/01.HYP.0000094221.86888.AE", StudyType.META_RCT)
    val ALCOHOL_BP = Ref("Roerecke et al., Lancet Public Health 2017", "The effect of a reduction in alcohol consumption on blood pressure: a systematic review and meta-analysis", "29253389", "10.1016/S2468-2667(17)30003-8", StudyType.META_RCT)
    val LEVINE = Ref("Levine et al., Aging 2018", "An epigenetic biomarker of aging for lifespan and healthspan", "29676998", "10.18632/aging.101414", StudyType.META_OBS)
}

enum class Level { POSITIVE, INFO, ATTENTION, ALERT }

enum class Area(val label: String) { BP("Pressione"), LIPIDS("Lipidi"), GLUCOSE("Glicemia"), INFLAMMATION("Infiammazione"), KIDNEY("Rene"), VITAMINS("Vitamine"), FITNESS("Forma fisica"), HEART("Cuore"), SLEEP("Sonno"), ACTIVITY("Attività"), AGING("Età biologica") }

data class EvidencePoint(
    val text: String,
    /** Certezza: "alta", "moderata", "bassa", "molto bassa". */
    val certainty: String,
    /** true se la certezza è il GRADE dichiarato dagli autori. */
    val gradeByAuthors: Boolean,
    val ref: Ref,
)

data class Insight(
    val id: String,
    val area: Area,
    val level: Level,
    val title: String,
    val finding: String,
    val evidence: List<EvidencePoint>,
    /** Domanda suggerita da approfondire con l'assistente. */
    val ask: String,
)

/** Dati in ingresso, già in unità canoniche dell'app. */
data class InsightInput(
    val age: Double?,
    val male: Boolean,
    val ldl: Double? = null,       // mg/dL
    val hdl: Double? = null,       // mg/dL
    val tg: Double? = null,        // mg/dL
    val glucose: Double? = null,   // mg/dL
    val hba1c: Double? = null,     // mmol/mol
    val crp: Double? = null,       // mg/L
    val crpBelowLimit: Boolean = false,
    val egfr: Double? = null,      // mL/min/1,73m²
    val vitD: Double? = null,      // ng/mL
    val homocys: Double? = null,   // µmol/L
    val lpa: Double? = null,       // mg/dL
    val rhr7d: Double? = null,     // bpm
    val sleep7d: Double? = null,   // h
    val steps7d: Double? = null,   // passi/giorno
    val vo2: Double? = null,       // mL/kg/min
    val phenoDelta: Double? = null,
    val bp: BpSummary? = null,
)

object InsightEngine {

    private fun n(x: Double, d: Int = 0) = String.format(java.util.Locale.ITALY, "%.${d}f", x)

    fun build(i: InsightInput): List<Insight> {
        val out = mutableListOf<Insight>()

        // ---------------- Pressione arteriosa (misure domiciliari) ----------------
        i.bp?.let { b ->
            val lvl = when (b.category) {
                BpCategory.SEVERE, BpCategory.HIGH -> Level.ALERT
                BpCategory.HYPERTENSION -> Level.ALERT
                BpCategory.ELEVATED -> Level.ATTENTION
                BpCategory.NON_ELEVATED -> Level.POSITIVE
            }
            val txt = "Media domiciliare ${n(b.meanSys)}/${n(b.meanDia)} mmHg su ${b.n} misure in ${b.days} giorni: ${b.category.label.lowercase()} secondo le soglie domiciliari ESC 2024 (< 120/70 non elevata, 120–134/70–84 elevata, ≥ 135/85 ipertensione). " +
                (if (!b.protocolComplete) "Per una valutazione affidabile servono almeno 3 giorni con 2 misure mattino e sera. " else "") +
                when (b.category) {
                    BpCategory.SEVERE -> "Valori molto elevati: ricontrolla a breve e contatta il medico; con dolore toracico, mancanza di fiato, disturbi neurologici o cefalea intensa chiama il 112."
                    BpCategory.HIGH, BpCategory.HYPERTENSION -> "Valori da discutere con il medico, che valuterà conferma diagnostica e rischio cardiovascolare."
                    else -> ""
                }
            val ev = buildList {
                add(EvidencePoint("A domicilio, 135/85 mmHg equivale alla soglia ambulatoriale di 140/90; la categoria «elevata» indica un rischio intermedio da gestire anzitutto con lo stile di vita.", "linea guida", false, Refs.ESC_BP))
                add(EvidencePoint("Protocollo di automisurazione: seduto da 5 minuti, schiena appoggiata, bracciale all'altezza del cuore, 2 misure a 1 minuto di distanza al mattino e alla sera per 3–7 giorni, escludendo il primo giorno.", "linea guida", false, Refs.ESH_BP))
                if (b.category != BpCategory.NON_ELEVATED) {
                    add(EvidencePoint("Ogni 10 mmHg in meno di sistolica riducono del 20% gli eventi cardiovascolari maggiori (RR 0,80) e del 13% la mortalità totale nei trial di trattamento.", "alta", false, Refs.ETTEHAD))
                    add(EvidencePoint("Ridurre il sale di circa 4,4 g/die abbassa la pressione di 4,2/2,1 mmHg (5,4/2,8 negli ipertesi).", "moderata", false, Refs.SALT))
                    add(EvidencePoint("L'esercizio fisico riduce la pressione a riposo: aerobico −4,5/−2,5, contro resistenza −4,6/−3,0, combinato −6,0/−2,5, isometrico (es. wall squat, handgrip) −8,2/−4,0 mmHg.", "moderata", false, Refs.EXERCISE_BP))
                    add(EvidencePoint("La dieta DASH riduce la pressione di 3,2/2,5 mmHg rispetto a una dieta di controllo (GRADE moderato).", "moderata", true, Refs.DASH))
                    add(EvidencePoint("Ogni kg perso riduce la pressione di circa 1,05/0,92 mmHg.", "moderata", false, Refs.WEIGHT_BP))
                    add(EvidencePoint("Ridurre l'alcol abbassa la pressione solo in chi beve più di 2 drink al giorno (fino a −5,5/−4,0 mmHg in chi ne beve ≥ 6).", "moderata", false, Refs.ALCOHOL_BP))
                }
            }
            out += Insight(
                "bp", Area.BP, lvl, "Pressione arteriosa", txt, ev,
                "La mia pressione domiciliare media è ${n(b.meanSys)}/${n(b.meanDia)} mmHg. Come la interpreto e quali interventi hanno le prove migliori?",
            )
        }

        // ---------------- LDL ----------------
        i.ldl?.let { ldl ->
            val mmol = ldl / 38.67
            val level = when { ldl >= 190 -> Level.ALERT; ldl >= 116 -> Level.ATTENTION; else -> Level.POSITIVE }
            out += Insight(
                "ldl", Area.LIPIDS, level, "Colesterolo LDL",
                "LDL ${n(ldl)} mg/dL (${n(mmol, 1)} mmol/L). Le linee guida ESC/EAS fissano obiettivi in base al rischio cardiovascolare globale: < 116 mg/dL a rischio basso, < 100 a rischio moderato, valori più bassi a rischio alto. " +
                    if (ldl >= 190) "Un valore ≥ 190 mg/dL indica di per sé un rischio elevato e richiede valutazione medica (es. ipercolesterolemia familiare)." else "",
                listOf(
                    EvidencePoint("Nei trial con statine ogni riduzione di 1 mmol/L (≈ 39 mg/dL) di LDL riduce del 22% gli eventi vascolari maggiori (RR 0,78) e del 10% la mortalità totale (RR 0,90), anche partendo da valori bassi.", "alta", false, Refs.CTT),
                    EvidencePoint("Ridurre l'apporto di grassi saturi riduce gli eventi cardiovascolari combinati del 17% (RR 0,83) in trial di almeno 2 anni; l'effetto è maggiore quanto più scende il colesterolo.", "moderata", true, Refs.HOOPER),
                    EvidencePoint("Circa 10 g/die di psillio riducono l'LDL di 0,33 mmol/L (≈ 13 mg/dL) e l'apoB di 0,05 g/L.", "moderata", true, Refs.PSYLLIUM),
                    EvidencePoint("Gli obiettivi di LDL dipendono dalla stima del rischio (SCORE2), da calcolare con il medico.", "linea guida", false, Refs.ESC2019),
                ),
                "Il mio LDL è ${n(ldl)} mg/dL. Quali interventi non farmacologici hanno le prove migliori e di quanto lo riducono?",
            )
        }

        // ---------------- Trigliceridi ----------------
        i.tg?.let { tg ->
            if (tg >= 150) out += Insight(
                "tg", Area.LIPIDS, if (tg >= 500) Level.ALERT else Level.ATTENTION, "Trigliceridi",
                "Trigliceridi ${n(tg)} mg/dL: sopra 150 mg/dL (1,7 mmol/L), soglia oltre la quale le linee guida ESC/EAS li considerano un marcatore di rischio aumentato. Il valore risente molto del digiuno, dell'alcol e del pasto precedente: va ricontrollato.",
                listOf(
                    EvidencePoint("Le linee guida indicano come primo approccio calo ponderale, riduzione di alcol e zuccheri semplici e attività fisica; la terapia farmacologica è riservata a casi selezionati.", "linea guida", false, Refs.ESC2019),
                ),
                "I miei trigliceridi sono ${n(tg)} mg/dL. Quali interventi riducono i trigliceridi secondo meta-analisi di RCT?",
            )
        }

        // ---------------- HDL ----------------
        i.hdl?.let { hdl ->
            val low = if (i.male) hdl < 40 else hdl < 50
            if (low) out += Insight(
                "hdl", Area.LIPIDS, Level.INFO, "Colesterolo HDL basso",
                "HDL ${n(hdl)} mg/dL, sotto ${if (i.male) 40 else 50} mg/dL. Un HDL basso è un marcatore di rischio da interpretare nel quadro complessivo (LDL, trigliceridi, peso, attività fisica).",
                listOf(
                    EvidencePoint("L'allenamento aerobico aumenta l'HDL in media di 2,5 mg/dL; serve un volume minimo di circa 120 min/settimana e conta soprattutto la durata delle sedute.", "moderata", false, Refs.KODAMA_HDL),
                ),
                "Il mio HDL è ${n(hdl)} mg/dL. Ha senso cercare di aumentarlo e come?",
            )
        }

        // ---------------- Glicemia / HbA1c ----------------
        val gLevel = when {
            (i.hba1c ?: 0.0) >= 48 || (i.glucose ?: 0.0) >= 126 -> Level.ALERT
            (i.hba1c ?: 0.0) >= 39 || (i.glucose ?: 0.0) >= 100 -> Level.ATTENTION
            i.hba1c != null || i.glucose != null -> Level.POSITIVE
            else -> null
        }
        if (gLevel != null) {
            val parts = listOfNotNull(
                i.glucose?.let { "glicemia a digiuno ${n(it)} mg/dL" },
                i.hba1c?.let { "HbA1c ${n(it)} mmol/mol (${n((it + 23.52) / 10.929, 1)}%)" },
            ).joinToString(", ")
            val msg = when (gLevel) {
                Level.ALERT -> "Valori nell'intervallo diagnostico di diabete secondo ADA (glicemia ≥ 126 mg/dL o HbA1c ≥ 48 mmol/mol). La diagnosi richiede conferma con un secondo esame: parlane con il medico a breve."
                Level.ATTENTION -> "Valori nell'intervallo di prediabete secondo ADA (glicemia 100–125 mg/dL o HbA1c 39–47 mmol/mol)."
                else -> "Valori nell'intervallo di normalità secondo i criteri ADA."
            }
            out += Insight(
                "glyc", Area.GLUCOSE, gLevel, "Metabolismo glucidico", "${parts.replaceFirstChar { it.uppercase() }}. $msg",
                buildList {
                    add(EvidencePoint("Criteri diagnostici: glicemia a digiuno 100–125 mg/dL o HbA1c 5,7–6,4% = prediabete; ≥ 126 mg/dL o ≥ 6,5% = diabete (con conferma).", "linea guida", false, Refs.ADA2024))
                    if (gLevel != Level.POSITIVE) {
                        add(EvidencePoint("In persone con alterata glicemia, un programma di stile di vita (−7% di peso, ≥ 150 min/settimana di attività) ha ridotto l'incidenza di diabete del 58% in 2,8 anni, più della metformina (−31%).", "alta", false, Refs.DPP))
                        add(EvidencePoint("Nel diabete tipo 2 l'esercizio strutturato riduce l'HbA1c dello 0,67% in media; con più di 150 min/settimana la riduzione è dello 0,89%.", "moderata", false, Refs.UMPIERRE))
                    }
                },
                "Commenta i miei valori glicemici ($parts) e l'evidenza sugli interventi di stile di vita.",
            )
        }

        // ---------------- PCR ----------------
        i.crp?.let { crp ->
            val (lvl, txt) = when {
                crp > 10 -> Level.ATTENTION to "PCR ${n(crp, 1)} mg/L: oltre 10 mg/L indica di solito un processo infiammatorio acuto (es. infezione). Non va usata per stimare il rischio cardiovascolare: ripeti l'esame a distanza dall'evento."
                crp > 3 -> Level.ATTENTION to "PCR ${n(crp, 1)} mg/L: fascia > 3 mg/L, associata a rischio cardiovascolare più alto (AHA/CDC). Va confermata con un secondo dosaggio a ≥ 2 settimane."
                crp >= 1 -> Level.INFO to "PCR ${n(crp, 1)} mg/L: fascia 1–3 mg/L (rischio medio secondo AHA/CDC)."
                else -> Level.POSITIVE to "PCR ${if (i.crpBelowLimit) "<" else ""}${n(crp, 1)} mg/L: fascia < 1 mg/L (rischio basso secondo AHA/CDC)."
            }
            out += Insight(
                "crp", Area.INFLAMMATION, lvl, "Infiammazione (PCR)", txt,
                buildList {
                    add(EvidencePoint("Soglie per la hs-PCR: < 1, 1–3 e > 3 mg/L = rischio basso, medio e alto; usare la media di due misure; > 10 mg/L va escluso per la stima del rischio.", "linea guida", false, Refs.PEARSON))
                    if (crp >= 1) add(EvidencePoint("Nelle revisioni di interventi di calo ponderale, ogni kg perso si associa a una riduzione media della PCR di 0,13 mg/L (confronti prima-dopo, effetto minore nei soli interventi di stile di vita).", "bassa", false, Refs.SELVIN))
                },
                "La mia PCR è ${n(crp, 1)} mg/L. Come interpretarla e quali interventi la riducono secondo le prove?",
            )
        }

        // ---------------- eGFR ----------------
        i.egfr?.let { e ->
            if (e < 60) out += Insight(
                "egfr", Area.KIDNEY, Level.ALERT, "Funzione renale",
                "eGFR stimato ${n(e)} mL/min/1,73m² (< 60). Se confermato per oltre 3 mesi corrisponde a malattia renale cronica secondo KDIGO; serve una valutazione medica (anche dell'albuminuria).",
                listOf(EvidencePoint("La malattia renale cronica è definita da eGFR < 60 o marcatori di danno renale persistenti per > 3 mesi.", "linea guida", false, Refs.KDIGO)),
                "Il mio eGFR calcolato è ${n(e)}. Cosa significa e quali esami andrebbero discussi con il medico?",
            )
        }

        // ---------------- Vitamina D ----------------
        i.vitD?.let { d ->
            out += Insight(
                "vitd", Area.VITAMINS, if (d < 12) Level.ATTENTION else if (d < 20) Level.INFO else Level.POSITIVE, "Vitamina D",
                "25-OH vitamina D ${n(d, 1)} ng/mL. Secondo l'Institute of Medicine un livello ≥ 20 ng/mL (50 nmol/L) copre il fabbisogno del 97,5% della popolazione per la salute ossea.",
                listOf(
                    EvidencePoint("Il valore di 20 ng/mL deriva dalle evidenze sull'osso; per esiti extra-scheletrici le prove erano incoerenti, e per alcuni esiti l'associazione è a U (rischio anche a valori alti).", "linea guida", false, Refs.IOM),
                    EvidencePoint("In 25.871 adulti, 2000 UI/die per 5,3 anni non hanno ridotto tumori (HR 0,96) né eventi cardiovascolari maggiori (HR 0,97) rispetto al placebo.", "alta", false, Refs.VITAL),
                ),
                "La mia vitamina D è ${n(d, 1)} ng/mL. Quando l'integrazione ha benefici dimostrati e quando no?",
            )
        }

        // ---------------- Omocisteina ----------------
        i.homocys?.let { h ->
            if (h > 15) out += Insight(
                "hcy", Area.VITAMINS, Level.INFO, "Omocisteina",
                "Omocisteina ${n(h, 1)} µmol/L (> 15). Abbassarla con vitamine del gruppo B è possibile, ma l'effetto sugli eventi clinici è limitato.",
                listOf(EvidencePoint("In 15 RCT (71.422 persone) le vitamine B6/B9/B12 non hanno ridotto infarto (RR 1,02) né mortalità totale (RR 1,01); piccola riduzione dell'ictus (RR 0,90).", "alta", true, Refs.HCY)),
                "La mia omocisteina è ${n(h, 1)} µmol/L. Quali sono le cause da indagare e cosa dicono i trial sull'integrazione?",
            )
        }

        // ---------------- Lp(a) ----------------
        i.lpa?.let { l ->
            if (l > 50) out += Insight(
                "lpa", Area.LIPIDS, Level.ATTENTION, "Lipoproteina(a)",
                "Lp(a) ${n(l)} mg/dL (> 50). È determinata soprattutto geneticamente, è un fattore di rischio causale e non si modifica in modo rilevante con lo stile di vita.",
                listOf(EvidencePoint("In assenza di terapie specifiche approvate, con Lp(a) elevata si raccomanda una gestione precoce e più intensa degli altri fattori di rischio; utile lo screening dei familiari.", "consensus", false, Refs.LPA)),
                "La mia Lp(a) è ${n(l)} mg/dL. Cosa implica per il rischio e per gli obiettivi degli altri fattori?",
            )
        }

        // ---------------- FC a riposo ----------------
        i.rhr7d?.let { r ->
            if (r >= 75) out += Insight(
                "rhr", Area.HEART, if (r > 80) Level.ATTENTION else Level.INFO, "Frequenza cardiaca a riposo",
                "FC a riposo media ${n(r)} bpm negli ultimi 7 giorni (misurata dall'orologio, che può differire dalla misura clinica).",
                listOf(
                    EvidencePoint("Ogni +10 bpm di FC a riposo si associa a +9% di mortalità totale (RR 1,09); il dato è osservazionale, con eterogeneità e possibile bias di pubblicazione.", "bassa", false, Refs.ZHANG_RHR),
                    EvidencePoint("L'allenamento di resistenza aerobica e lo yoga riducono la FC a riposo in entrambi i sessi; l'effetto è maggiore se la FC iniziale è più alta.", "moderata", false, Refs.REIMERS),
                ),
                "La mia FC a riposo è ${n(r)} bpm. Cosa la influenza e come ridurla?",
            )
        }

        // ---------------- VO2max ----------------
        i.vo2?.let { v ->
            val met = v / 3.5
            out += Insight(
                "vo2", Area.FITNESS, if (met < 7.9) Level.ATTENTION else Level.POSITIVE, "Capacità aerobica",
                "VO₂max ${n(v, 1)} mL/kg/min ≈ ${n(met, 1)} MET. " + if (met < 7.9) "Sotto 7,9 MET, fascia associata a rischio più alto." else "Sopra 7,9 MET, soglia oltre la quale il rischio è nettamente inferiore.",
                listOf(
                    EvidencePoint("Ogni MET in più di capacità aerobica si associa a −13% di mortalità totale (RR 0,87) e −15% di eventi cardiovascolari; capacità < 7,9 MET: RR 1,70 rispetto a ≥ 10,9 MET.", "moderata", false, Refs.KODAMA_CRF),
                    EvidencePoint("In adulti sani 18–45 anni l'allenamento di resistenza aumenta il VO₂max di 4,9 mL/kg/min e l'interval training ad alta intensità di 5,5 mL/kg/min rispetto a nessun esercizio.", "moderata", false, Refs.MILANOVIC),
                ),
                "Il mio VO₂max è ${n(v, 1)} mL/kg/min. Come strutturare l'allenamento per aumentarlo secondo le prove?",
            )
        }

        // ---------------- Sonno ----------------
        i.sleep7d?.let { s ->
            if (s < 7 || s > 9) out += Insight(
                "sleep", Area.SLEEP, Level.INFO, "Durata del sonno",
                "Sonno medio ${n(s, 1)} h negli ultimi 7 giorni (stima dell'orologio, tende a differire dalla polisonnografia).",
                listOf(
                    EvidencePoint("Per un adulto sono raccomandate almeno 7 ore per notte.", "consensus", false, Refs.WATSON),
                    EvidencePoint("Il sonno breve si associa a +12% di mortalità (RR 1,12) e quello lungo a +30% (RR 1,30); associazione osservazionale, il sonno lungo può riflettere malattie sottostanti.", "bassa", false, Refs.CAPPUCCIO),
                ),
                "Dormo in media ${n(s, 1)} ore. Quali interventi migliorano durata e qualità del sonno secondo le prove?",
            )
        }

        // ---------------- Passi ----------------
        i.steps7d?.let { st ->
            val target = if ((i.age ?: 50.0) >= 60) 6000 else 8000
            if (st < target) out += Insight(
                "steps", Area.ACTIVITY, Level.INFO, "Passi giornalieri",
                "Media ${n(st)} passi/giorno negli ultimi 7 giorni.",
                listOf(EvidencePoint("Il rischio di mortalità scende progressivamente con i passi fino a 6.000–8.000/die sopra i 60 anni e 8.000–10.000/die sotto i 60 (dati osservazionali, 15 coorti).", "moderata", false, Refs.PALUCH)),
                "Faccio in media ${n(st)} passi al giorno. Che obiettivo ha senso per la mia età?",
            )
        }

        // ---------------- PhenoAge ----------------
        i.phenoDelta?.let { d ->
            out += Insight(
                "pheno", Area.AGING, if (d > 2) Level.ATTENTION else if (d < -2) Level.POSITIVE else Level.INFO, "Come usare la PhenoAge",
                "Differenza tra PhenoAge ed età anagrafica: ${String.format(java.util.Locale.ITALY, "%+.1f", d)} anni. È un indice di rischio sintetico: nessuno studio ha dimostrato che intervenire per \"abbassare la PhenoAge\" in sé migliori gli esiti.",
                listOf(EvidencePoint("La PhenoAge predice mortalità e morbilità a parità di età; i singoli biomarcatori che la compongono vanno interpretati clinicamente uno per uno.", "moderata", false, Refs.LEVINE)),
                "Quali biomarcatori stanno pesando di più sulla mia PhenoAge e quali sono modificabili secondo le prove?",
            )
        }

        return out.sortedBy { when (it.level) { Level.ALERT -> 0; Level.ATTENTION -> 1; Level.INFO -> 2; Level.POSITIVE -> 3 } }
    }
}
