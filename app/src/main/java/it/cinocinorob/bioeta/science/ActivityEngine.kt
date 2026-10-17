package it.cinocinorob.bioeta.science

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * Motore di calcolo dell'attività (versione algoritmo: ACTIVITY_ALGO_VERSION).
 * Funzioni pure, senza dipendenze Android, coperte da test unitari (ActivityEngineTest).
 *
 * 1) Serie minuto per minuto della FC:
 *    - i campioni sono raggruppati per minuto (media);
 *    - i minuti senza campione sono riempiti per interpolazione lineare SOLO se il buco tra due minuti
 *      misurati è ≤ MAX_GAP_MIN (5 min). Fitbit e altre app scrivono la FC con cadenza variabile:
 *      senza questo passaggio un'ora di corsa con un campione ogni 2–3 min risultava di pochi minuti.
 *      Buchi più lunghi restano "non coperti" (nessun valore inventato).
 *
 * 2) Intensità di ogni minuto (ACSM, Garber 2011, PMID 21694556) come % della riserva cardiaca (Karvonen):
 *    moderata 40–59%, vigorosa ≥ 60%. FCmax = 208 − 0,7·età (Tanaka 2001, PMID 11153730).
 *
 * 3) Minuti attivi (OMS 2020, PMID 33239350): moderati ×1 + vigorosi ×2.
 *    - "Misurati": minuti con FC (reale o interpolata) e intensità ≥ moderata.
 *    - "Stimati": minuti DENTRO un allenamento registrato ma SENZA FC: l'intensità viene dal tipo di attività
 *      secondo il Compendio delle attività fisiche 2024 (Herrmann 2024, PMID 38242596; moderata 3–5,9 MET,
 *      vigorosa ≥ 6 MET), con scelta conservativa. I tipi con intensità non determinabile (es. "altro",
 *      yoga, stretching) NON vengono contati.
 *    - Ogni minuto è contato una sola volta (unione degli intervalli): allenamenti sovrapposti di due app
 *      non raddoppiano i minuti; un minuto con FC usa sempre la FC, mai la stima.
 *    - Ogni minuto è attribuito al giorno locale in cui cade: un'attività a cavallo della mezzanotte
 *      viene divisa tra i due giorni.
 *
 * 4) Carico cardiaco = TRIMP di Banister (Morton 1990, PMID 2246166) sommato sui minuti con FC e FCR ≥ 30%.
 *    È calcolato SOLO dalla FC: le stime da tipo di attività non entrano nel carico.
 *    Stato: NON DISPONIBILE senza minuti di FC; PARZIALE con meno di 10 h di copertura; altrimenti COMPLETO.
 *    Non è il carico muscolare né il consumo calorico.
 */

const val ACTIVITY_ALGO_VERSION = 2

enum class LoadQuality(val label: String) {
    UNAVAILABLE("Non disponibile"),
    PARTIAL("Parziale"),
    COMPLETE("Completo"),
}

enum class SessionIntensity { VIGOROUS, MODERATE, LIGHT, UNKNOWN }

/** Categoria di attività, indipendente dai codici numerici di Connessione Salute (mappati in HealthRepo). */
enum class ActivityKind(val label: String, val intensity: SessionIntensity) {
    RUNNING("Corsa", SessionIntensity.VIGOROUS),
    HIIT("Allenamento intervallato", SessionIntensity.VIGOROUS),
    TEAM_SPORT("Sport di squadra", SessionIntensity.VIGOROUS),
    COMBAT("Sport da combattimento", SessionIntensity.VIGOROUS),
    STAIRS("Scale", SessionIntensity.VIGOROUS),
    CLIMBING("Arrampicata", SessionIntensity.VIGOROUS),
    BIKING("Ciclismo", SessionIntensity.MODERATE),
    SWIMMING("Nuoto", SessionIntensity.MODERATE),
    WALKING("Camminata", SessionIntensity.MODERATE),
    HIKING("Escursionismo", SessionIntensity.MODERATE),
    ROWING("Canottaggio", SessionIntensity.MODERATE),
    ELLIPTICAL("Ellittica", SessionIntensity.MODERATE),
    STRENGTH("Forza", SessionIntensity.MODERATE),
    RACKET("Sport con racchetta", SessionIntensity.MODERATE),
    DANCE_CLASS("Danza / corso", SessionIntensity.MODERATE),
    WINTER("Sport invernali", SessionIntensity.MODERATE),
    WATER("Sport acquatici", SessionIntensity.MODERATE),
    MIND_BODY("Yoga, pilates, stretching", SessionIntensity.LIGHT),
    LIGHT_OTHER("Attività leggera", SessionIntensity.LIGHT),
    OTHER("Altro", SessionIntensity.UNKNOWN),
}

/** Allenamento letto da Connessione Salute, già normalizzato. */
data class Session(
    val id: String,
    val origin: String,
    val kind: ActivityKind,
    val title: String?,
    val start: Instant,
    val end: Instant,
    val hcType: Int = 0,
) {
    val minutes: Double get() = Duration.between(start, end).seconds / 60.0
}

data class DayActivity(
    val date: LocalDate,
    val hrMinutes: Int,
    val moderateMeasured: Int,
    val vigorousMeasured: Int,
    val moderateEstimated: Int,
    val vigorousEstimated: Int,
    val load: Double,
    val zoneMinutes: Map<Zone, Int>,
    val quality: LoadQuality,
) {
    /** Minuti attivi equivalenti OMS (vigorosi ×2), misurati + stimati. */
    val activeMinutes: Int get() = moderateMeasured + moderateEstimated + 2 * (vigorousMeasured + vigorousEstimated)
    val estimatedShare: Int get() = moderateEstimated + 2 * vigorousEstimated

    fun toDayLoad() = DayLoad(date, load, moderateMeasured + moderateEstimated, vigorousMeasured + vigorousEstimated, zoneMinutes, hrMinutes)
}

object ActivityEngine {
    const val MAX_GAP_MIN = 5
    const val COMPLETE_COVERAGE_MIN = 600

    /**
     * Deduplicazione idempotente:
     *  - stesso id → un solo record;
     *  - due app diverse con lo stesso allenamento (stessa famiglia di attività, o una delle due "altro",
     *    e sovrapposizione ≥ 50% della sessione più breve) → si tiene la sessione più lunga.
     * Sessioni diverse nella stessa mattina (es. bici e poi corsa) restano entrambe.
     */
    fun dedupe(sessions: List<Session>): List<Session> {
        val unique = sessions.distinctBy { it.id }.sortedByDescending { it.minutes }
        val kept = mutableListOf<Session>()
        for (s in unique) {
            val dup = kept.any { k ->
                k.origin != s.origin && sameKind(k, s) && overlapFraction(k, s) >= 0.5
            }
            if (!dup) kept += s
        }
        return kept.sortedByDescending { it.start }
    }

    private fun sameKind(a: Session, b: Session) =
        a.kind == b.kind || a.kind == ActivityKind.OTHER || b.kind == ActivityKind.OTHER

    fun overlapFraction(a: Session, b: Session): Double {
        val s = maxOf(a.start, b.start); val e = minOf(a.end, b.end)
        if (!e.isAfter(s)) return 0.0
        val shorter = min(a.minutes, b.minutes)
        return if (shorter <= 0) 0.0 else Duration.between(s, e).seconds / 60.0 / shorter
    }

    /** Media per minuto (chiave = minuto epoch) con interpolazione dei buchi brevi. */
    fun minuteSeries(samples: List<HrSample>, maxGapMin: Int = MAX_GAP_MIN): Map<Long, Double> {
        val raw = samples.filter { it.bpm in 25.0..240.0 }
            .groupBy { Math.floorDiv(it.time.epochSecond, 60L) }
            .mapValues { (_, l) -> l.map { it.bpm }.average() }
            .toSortedMap()
        if (raw.isEmpty()) return emptyMap()
        val out = HashMap<Long, Double>(raw.size * 2)
        var prevK: Long? = null; var prevV = 0.0
        for ((k, v) in raw) {
            val pk = prevK
            if (pk != null) {
                val gap = k - pk
                if (gap in 2..maxGapMin.toLong()) {
                    for (m in 1 until gap) out[pk + m] = prevV + (v - prevV) * m / gap
                }
            }
            out[k] = v
            prevK = k; prevV = v
        }
        return out
    }

    /**
     * Calcolo di un giorno locale. [hrByMinute] può contenere minuti di altri giorni: vengono ignorati.
     */
    fun day(
        date: LocalDate,
        zone: ZoneId,
        hrByMinute: Map<Long, Double>,
        sessions: List<Session>,
        restHr: Double,
        age: Double,
        male: Boolean,
    ): DayActivity {
        val dayStartMin = Math.floorDiv(date.atStartOfDay(zone).toEpochSecond(), 60L)
        val dayEndMin = Math.floorDiv(date.plusDays(1).atStartOfDay(zone).toEpochSecond(), 60L)
        val maxHr = Fitness.hrMax(age)
        var load = 0.0; var hrMin = 0; var modM = 0; var vigM = 0
        val zones = Zone.entries.associateWith { 0 }.toMutableMap()
        for ((m, bpm) in hrByMinute) {
            if (m < dayStartMin || m >= dayEndMin) continue
            hrMin++
            val f = Fitness.hrr(bpm, restHr, maxHr)
            val z = Fitness.zoneOf(f)
            zones[z] = zones.getValue(z) + 1
            if (f >= Zone.LIGHT.lo) load += Fitness.trimpMinute(f, male)
            when {
                f >= Zone.VIGOROUS.lo -> vigM++
                f >= Zone.MODERATE.lo -> modM++
            }
        }
        // Minuti di allenamento senza FC: unione degli intervalli, per intensità (la vigorosa prevale)
        val estimated = HashMap<Long, SessionIntensity>()
        for (s in dedupe(sessions)) {
            val inten = s.kind.intensity
            if (inten != SessionIntensity.MODERATE && inten != SessionIntensity.VIGOROUS) continue
            val a = max(Math.floorDiv(s.start.epochSecond, 60L), dayStartMin)
            val b = min(Math.floorDiv(s.end.epochSecond + 59, 60L), dayEndMin)
            for (m in a until b) {
                if (hrByMinute.containsKey(m)) continue
                val prev = estimated[m]
                if (prev == null || (prev == SessionIntensity.MODERATE && inten == SessionIntensity.VIGOROUS)) estimated[m] = inten
            }
        }
        val modE = estimated.values.count { it == SessionIntensity.MODERATE }
        val vigE = estimated.values.count { it == SessionIntensity.VIGOROUS }
        val quality = when {
            hrMin == 0 -> LoadQuality.UNAVAILABLE
            hrMin < COMPLETE_COVERAGE_MIN -> LoadQuality.PARTIAL
            else -> LoadQuality.COMPLETE
        }
        return DayActivity(date, hrMin, modM, vigM, modE, vigE, load, zones, quality)
    }

    /** Minuti di FC (reale o interpolata) dentro una sessione: serve a dire se l'allenamento ha dati cardiaci. */
    fun hrCoverage(s: Session, hrByMinute: Map<Long, Double>): Double {
        val a = Math.floorDiv(s.start.epochSecond, 60L); val b = Math.floorDiv(s.end.epochSecond + 59, 60L)
        if (b <= a) return 0.0
        return (a until b).count { hrByMinute.containsKey(it) }.toDouble() / (b - a)
    }

    fun approxEqual(a: Double, b: Double, eps: Double = 1e-9) = abs(a - b) < eps
}
