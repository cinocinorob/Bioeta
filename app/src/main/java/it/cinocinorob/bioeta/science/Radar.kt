package it.cinocinorob.bioeta.science

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Parametri vitali notturni confrontati con il proprio intervallo abituale e «radar malessere».
 *
 * Basi:
 * - Mishra T et al., Nat Biomed Eng 2020;4:1208-1220 (doi:10.1038/s41551-020-00640-6): la FC a riposo da
 *   smartwatch sale prima dei sintomi nella maggior parte delle infezioni da SARS-CoV-2 analizzate.
 * - Alavi A et al., Nat Med 2022;28:175-184 (doi:10.1038/s41591-021-01593-2), algoritmo NightSignal:
 *   baseline = mediana delle notti precedenti; notte «anomala» se la FC notturna supera la baseline di
 *   ≥ 3–4 bpm; allerta con deviazioni in notti consecutive. Studio prospettico su 3.318 persone: avvisi
 *   in 67 degli 84 infetti (80%), segnali pre‑sintomatici con mediana 3 giorni prima dei sintomi; gli
 *   avvisi NON sono specifici: altre infezioni respiratorie, stress, alcol e viaggi li fanno scattare
 *   (in media 1,15 giorni di avviso a persona contro 3,42 nei casi COVID).
 * - Oura «Symptom Radar»: combinazione di temperatura cutanea, respirazione, FC a riposo, HRV; almeno 7 notti
 *   negli ultimi 14 giorni. Soglie e prestazioni non pubblicate.
 *
 * Qui: mediana e deviazione robusta (MAD) delle notti da −30 a −3 giorni, così una malattia che cresce da
 * due notti non sposta la propria baseline. Le soglie minime di deviazione standard derivano dalla
 * variabilità notte‑notte tipica (es. FC: ±3 bpm = 2 DS, come il margine di NightSignal).
 * Nessuna validazione propria: è un sistema di attenzione, non una diagnosi.
 */
enum class Concern { UP, DOWN }

data class SignalSpec(
    val key: String,
    val label: String,
    val unit: String,
    val decimals: Int,
    /** Direzione che, nelle infezioni, si discosta dalla norma. */
    val concern: Concern,
    /** Deviazione standard minima (nella scala di calcolo) per non avere intervalli assurdamente stretti. */
    val minSd: Double,
    /** true = calcolo su scala logaritmica (HRV, distribuita in modo asimmetrico). */
    val log: Boolean = false,
)

object Signals {
    val RHR = SignalSpec("rhr", "FC a riposo", "bpm", 0, Concern.UP, 1.5)
    val SLEEP_HR = SignalSpec("shr", "FC nel sonno", "bpm", 0, Concern.UP, 1.5)
    val HRV = SignalSpec("hrv", "HRV", "ms", 0, Concern.DOWN, 0.10, log = true)
    val RESP = SignalSpec("resp", "Respirazione", "atti/min", 1, Concern.UP, 0.4)
    val SPO2 = SignalSpec("spo2", "SpO₂", "%", 1, Concern.DOWN, 0.7)
    val TEMP = SignalSpec("temp", "Temperatura cutanea", "°C", 2, Concern.UP, 0.15)
}

data class SignalEval(
    val spec: SignalSpec,
    val date: LocalDate?,
    val value: Double?,
    /** Mediana e limiti dell'intervallo abituale (± 2 DS robuste), nella scala originale. */
    val median: Double?,
    val low: Double?,
    val high: Double?,
    /** Scarto in DS robuste (positivo = verso l'alto). */
    val z: Double?,
    val baselineNights: Int,
) {
    val hasBaseline get() = median != null
    val outOfRange get() = z != null && abs(z) >= 2.0
    /** Fuori intervallo nella direzione tipica di un malessere. */
    val concerning get() = z != null && when (spec.concern) { Concern.UP -> z >= 2.0; Concern.DOWN -> z <= -2.0 }
}

enum class RadarLevel(val label: String) {
    CALIBRATING("In calibrazione"), NONE("Nessun segno"), MINOR("Segni lievi"), MAJOR("Segni marcati")
}

data class RadarResult(
    val level: RadarLevel,
    val night: LocalDate?,
    val signals: List<SignalEval>,
    val headline: String,
    val detail: String,
    /** Contesto che può spiegare il quadro senza malattia (es. allenamento intenso ieri). */
    val context: String?,
)

object NightVitals {
    const val MIN_BASELINE = 7
    const val BASE_FROM = 30L
    const val BASE_TO = 3L

    fun median(v: List<Double>): Double {
        val s = v.sorted(); val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    /** Valutazione di un segnale per la notte [date] (valore di quella data). */
    fun evaluate(spec: SignalSpec, series: List<DailyValue>, date: LocalDate): SignalEval {
        val v = series.lastOrNull { it.date == date }?.value
        val base = series.filter { !it.date.isBefore(date.minusDays(BASE_FROM)) && !it.date.isAfter(date.minusDays(BASE_TO)) }
            .map { it.value }.filter { !spec.log || it > 0 }
        if (base.size < MIN_BASELINE) return SignalEval(spec, if (v != null) date else null, v, null, null, null, null, base.size)
        fun t(x: Double) = if (spec.log) ln(x) else x
        fun inv(x: Double) = if (spec.log) exp(x) else x
        val tb = base.map { t(it) }
        val m = median(tb)
        val mad = median(tb.map { abs(it - m) }) * 1.4826
        val sd = max(mad, spec.minSd)
        val z = if (v != null && (!spec.log || v > 0)) (t(v) - m) / sd else null
        return SignalEval(spec, if (v != null) date else null, v, inv(m), inv(m - 2 * sd), inv(m + 2 * sd), z, base.size)
    }
}

object IllnessRadar {

    /**
     * @param series segnali notturni disponibili (serie giornaliere, data = giorno del risveglio)
     * @param today data odierna; si valuta la notte di oggi o, se manca, quella di ieri
     * @param loads carico cardiaco giornaliero (per il contesto «allenamento intenso»)
     */
    fun assess(
        series: Map<SignalSpec, List<DailyValue>>,
        today: LocalDate,
        loads: List<DailyValue> = emptyList(),
        /** Etichette del diario della sera prima (es. «Alcol», «Stress»). */
        eveningTags: Set<String> = emptySet(),
    ): RadarResult {
        val night = listOf(today, today.minusDays(1)).firstOrNull { d -> series.values.any { s -> s.any { it.date == d } } }
        if (night == null) return RadarResult(RadarLevel.CALIBRATING, null, emptyList(), "Nessun dato della notte",
            "Servono i dati notturni dell'orologio (FC, HRV, respirazione…). Indossalo anche di notte.", null)

        val evals = series.map { (spec, s) -> NightVitals.evaluate(spec, s, night) }.filter { it.value != null }
        val ready = evals.filter { it.hasBaseline }
        if (ready.size < 2) return RadarResult(RadarLevel.CALIBRATING, night, evals, "In calibrazione",
            "Servono almeno ${NightVitals.MIN_BASELINE} notti con dati (escluse le ultime due) per almeno due parametri. " +
                "Per ora: ${evals.maxOfOrNull { it.baselineNights } ?: 0} notti.", null)

        val flagged = ready.filter { it.concerning }
        // FC (a riposo o, se manca, nel sonno) alta anche la notte prima: criterio delle notti consecutive di NightSignal
        val hrSpec = listOf(Signals.RHR, Signals.SLEEP_HR).firstOrNull { sp -> ready.any { it.spec == sp } }
        val hrPersist = hrSpec != null && flagged.any { it.spec == hrSpec } &&
            NightVitals.evaluate(hrSpec, series[hrSpec].orEmpty(), night.minusDays(1)).concerning
        // La FC a riposo e la FC nel sonno misurano quasi la stessa cosa: contano come un solo segnale
        val distinct = flagged.map { if (it.spec == Signals.SLEEP_HR) Signals.RHR.key else it.spec.key }.distinct().size

        val level = when {
            distinct >= 3 || (hrPersist && distinct >= 2) -> RadarLevel.MAJOR
            distinct == 2 || hrPersist -> RadarLevel.MINOR
            else -> RadarLevel.NONE
        }

        val names = flagged.joinToString(", ") { describe(it) }
        val (headline, detail) = when (level) {
            RadarLevel.NONE -> "Nessun segno" to
                if (flagged.size == 1) "Solo ${describe(flagged[0])}: un parametro isolato fuori intervallo è spesso variabilità di misura."
                else "I parametri della notte sono nel tuo intervallo abituale."
            RadarLevel.MINOR -> "Segni lievi" to
                "Fuori dal tuo intervallo: $names${if (hrPersist) " (FC alta anche la notte precedente)" else ""}. Può essere l'inizio di un'infezione, " +
                "ma anche alcol, stress, poco sonno o un allenamento duro. Stasera dai priorità al sonno e ricontrolla domattina."
            RadarLevel.MAJOR -> "Segni marcati" to
                "Fuori dal tuo intervallo: $names${if (hrPersist) " (FC alta anche la notte precedente)" else ""}. Negli studi con smartwatch questo " +
                "schema ha preceduto di alcuni giorni i sintomi di infezioni respiratorie (mediana 3), ma compare anche con alcol, stress e viaggi. " +
                "Considera una giornata leggera; se compaiono febbre o sintomi, chiedi al medico."
            RadarLevel.CALIBRATING -> "" to ""
        }

        // Contesto: carico cardiaco di ieri molto sopra il tuo abituale
        val y = loads.firstOrNull { it.date == night.minusDays(1) }?.value
        val past = loads.filter { it.date.isBefore(night.minusDays(1)) && !it.date.isBefore(night.minusDays(29)) && it.value > 0 }.map { it.value }
        val notes = mutableListOf<String>()
        if (level != RadarLevel.NONE) {
            if (y != null && past.size >= 7 && y >= 1.5 * NightVitals.median(past))
                notes += "Ieri il carico cardiaco è stato alto (${y.toInt()}, il tuo abituale è ${NightVitals.median(past).toInt()}): un allenamento intenso può da solo alzare la FC e abbassare l'HRV nella notte successiva."
            if ("Alcol" in eveningTags) notes += "Nel diario di ieri hai segnato alcol: anche dosi moderate alzano la FC notturna e abbassano l'HRV."
            if ("Stress" in eveningTags) notes += "Nel diario di ieri hai segnato stress, che può dare lo stesso quadro."
        }
        val context = notes.takeIf { it.isNotEmpty() }?.joinToString(" ")

        return RadarResult(level, night, evals, headline, detail, context)
    }

    fun describe(e: SignalEval): String {
        val up = (e.z ?: 0.0) > 0
        return when (e.spec.key) {
            "rhr", "shr" -> "${e.spec.label} ${if (up) "alta" else "bassa"}"
            "hrv" -> "HRV ${if (up) "alta" else "bassa"}"
            "resp" -> "respirazione ${if (up) "più frequente" else "più lenta"}"
            "spo2" -> "SpO₂ ${if (up) "alta" else "bassa"}"
            "temp" -> "temperatura cutanea ${if (up) "più alta" else "più bassa"}"
            else -> e.spec.label
        }
    }
}
