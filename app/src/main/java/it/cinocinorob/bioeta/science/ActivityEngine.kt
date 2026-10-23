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
 *    - riconciliazione delle fonti (come Vora o la «priorità delle app» di Connessione Salute): se più app
 *      scrivono la FC (orologio, anello, telefono) NON si fa la media tra loro. Si usa una fonte principale
 *      (quella scelta dall'utente o, in automatico, quella con più minuti misurati nel giorno) e le altre
 *      servono solo a coprire i minuti in cui la principale non ha dati. Motivo: un anello misura di giorno
 *      soprattutto a riposo; mediato con l'orologio durante una camminata abbassava la FC e faceva perdere
 *      minuti attivi;
 *    - i campioni sono raggruppati per minuto (media) separatamente per ogni fonte;
 *    - i minuti senza campione sono riempiti per interpolazione lineare SOLO se il buco tra due minuti
 *      misurati è ≤ MAX_GAP_MIN (5 min). Fitbit e altre app scrivono la FC con cadenza variabile:
 *      senza questo passaggio un'ora di corsa con un campione ogni 2–3 min risultava di pochi minuti.
 *      Buchi più lunghi restano "non coperti" (nessun valore inventato). L'interpolazione avviene dentro
 *      una stessa fonte, mai tra un campione dell'orologio e uno dell'anello.
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

const val ACTIVITY_ALGO_VERSION = 4

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
    /** Minuti classificati attivi dalla cadenza dei passi (non dalla FC). Già inclusi nei misurati. */
    val cadenceMinutes: Int = 0,
    /** Minuti attivi equivalenti (vigorosi ×2) dovuti alla cadenza: parte dei misurati. */
    val cadenceEq: Int = 0,
    /** Minuti attivi equivalenti per ora locale (0–23), misurati + stimati: per la linea del giorno. */
    val hourly: List<Int> = emptyList(),
    /** App usata come fonte principale della FC nel giorno (nome del pacchetto), null se nessuna FC. */
    val source: String? = null,
    /** Quando è stato calcolato (secondi epoch): un giorno calcolato prima della fine non va riusato. */
    val computedAt: Long = 0,
    /** true = l'ultima lettura da Connessione Salute non è riuscita: valore precedente, non aggiornato. */
    val stale: Boolean = false,
) {
    /** Minuti attivi equivalenti dalla sola frequenza cardiaca. */
    val hrEq: Int get() = moderateMeasured + 2 * vigorousMeasured - cadenceEq

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

    /** FC per minuto riconciliata tra le fonti. */
    data class HrSeries(
        val byMinute: Map<Long, Double>,
        /** Fonte principale (pacchetto), null se non c'è FC. */
        val primary: String?,
        /** Minuti presi da ciascuna fonte nella serie finale. */
        val minutesByOrigin: Map<String, Int>,
    )

    /**
     * Riconciliazione delle fonti: serie per minuto di ciascuna app (con interpolazione interna), poi
     * fonte principale = [prefer] se presente nei dati, altrimenti quella con più minuti; le altre fonti
     * riempiono solo i minuti mancanti, in ordine di copertura.
     */
    fun reconcile(samples: List<HrSample>, prefer: String? = null, maxGapMin: Int = MAX_GAP_MIN): HrSeries {
        val per = samples.groupBy { it.origin }.mapValues { (_, l) -> minuteSeries(l, maxGapMin) }.filterValues { it.isNotEmpty() }
        if (per.isEmpty()) return HrSeries(emptyMap(), null, emptyMap())
        val order = per.entries.sortedWith(compareByDescending<Map.Entry<String, Map<Long, Double>>> { it.key == prefer }.thenByDescending { it.value.size }).map { it.key }
        val out = HashMap<Long, Double>(per.getValue(order.first()).size * 2)
        val count = LinkedHashMap<String, Int>()
        for (o in order) {
            var n = 0
            for ((k, v) in per.getValue(o)) if (!out.containsKey(k)) { out[k] = v; n++ }
            if (n > 0) count[o] = n
        }
        return HrSeries(out, order.first(), count)
    }

    /** Media per minuto (chiave = minuto epoch) con interpolazione dei buchi brevi. Una sola fonte. */
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
    /**
     * Soglie di cadenza (passi/min) per intensità moderata (3 MET) e vigorosa (6 MET) nella camminata:
     * 100 e 130 tra 21 e 60 anni (CADENCE-Adults: Tudor-Locke 2019, doi:10.1186/s12966-019-0769-6;
     * Tudor-Locke 2020, doi:10.1186/s12966-020-01045-z). Oltre i 60 anni i dati non supportano una soglia per
     * 6 MET (Tudor-Locke 2021): si usa solo quella moderata.
     */
    const val CADENCE_MODERATE = 100.0
    const val CADENCE_VIGOROUS = 130.0
    /** Si usa la cadenza solo da record di passi brevi: in un blocco lungo non si sa come sono distribuiti. */
    const val CADENCE_MAX_CHUNK_MIN = 10
    /** Oltre questa cadenza il dato è implausibile (record sovrapposti della stessa app): minuto scartato. */
    const val CADENCE_PLAUSIBLE_MAX = 250.0

    /** Cadenza per minuto (passi/min) dai record grezzi brevi; tra app diverse si prende il massimo. */
    fun cadenceByMinute(chunks: List<StepChunk>): Map<Long, Double> {
        val perOrigin = HashMap<String, HashMap<Long, Double>>()
        for (c in chunks) {
            val secs = Duration.between(c.start, c.end).seconds
            if (c.count <= 0 || secs < 30 || secs > CADENCE_MAX_CHUNK_MIN * 60L) continue
            val perSec = c.count.toDouble() / secs
            var t = c.start.epochSecond
            val end = c.end.epochSecond
            val m = perOrigin.getOrPut(c.origin) { HashMap() }
            while (t < end) {
                val minute = Math.floorDiv(t, 60L)
                val segEnd = min(end, (minute + 1) * 60L)
                m.merge(minute, perSec * (segEnd - t), Double::plus)
                t = segEnd
            }
        }
        val out = HashMap<Long, Double>()
        for (m in perOrigin.values) for ((k, v) in m) if (v <= CADENCE_PLAUSIBLE_MAX) out[k] = maxOf(out[k] ?: 0.0, v)
        return out
    }

    fun day(
        date: LocalDate,
        zone: ZoneId,
        hrByMinute: Map<Long, Double>,
        sessions: List<Session>,
        restHr: Double,
        age: Double,
        male: Boolean,
        cadence: Map<Long, Double> = emptyMap(),
        source: String? = null,
        computedAt: Long = 0,
    ): DayActivity {
        val dayStartMin = Math.floorDiv(date.atStartOfDay(zone).toEpochSecond(), 60L)
        val dayEndMin = Math.floorDiv(date.plusDays(1).atStartOfDay(zone).toEpochSecond(), 60L)
        val maxHr = Fitness.hrMax(age)
        var load = 0.0; var hrMin = 0; var modM = 0; var vigM = 0; var cadM = 0; var cadEq = 0
        val hourly = IntArray(24)
        fun hourOf(m: Long) = java.time.Instant.ofEpochSecond(m * 60).atZone(zone).hour
        fun credit(m: Long, cls: Int) { if (cls > 0) hourly[hourOf(m)] += cls }
        val zones = Zone.entries.associateWith { 0 }.toMutableMap()
        // 0 = sotto la soglia moderata, 1 = moderato, 2 = vigoroso
        fun cadClass(c: Double?) = when {
            c == null -> 0
            c >= CADENCE_VIGOROUS && age <= 60 -> 2
            c >= CADENCE_MODERATE -> 1
            else -> 0
        }
        val active = HashSet<Long>()
        for ((m, bpm) in hrByMinute) {
            if (m < dayStartMin || m >= dayEndMin) continue
            hrMin++
            val f = Fitness.hrr(bpm, restHr, maxHr)
            val z = Fitness.zoneOf(f)
            zones[z] = zones.getValue(z) + 1
            if (f >= Zone.LIGHT.lo) load += Fitness.trimpMinute(f, male)
            val hrClass = when { f >= Zone.VIGOROUS.lo -> 2; f >= Zone.MODERATE.lo -> 1; else -> 0 }
            val cc = cadClass(cadence[m])
            val cls = maxOf(hrClass, cc)
            when (cls) { 2 -> vigM++; 1 -> modM++ }
            if (cc > hrClass) { cadM++; cadEq += cc - hrClass }
            if (cls > 0) { active += m; credit(m, cls) }
        }
        // Minuti senza FC ma con cadenza da camminata svelta o corsa
        for ((m, c) in cadence) {
            if (m < dayStartMin || m >= dayEndMin || hrByMinute.containsKey(m)) continue
            when (val cc = cadClass(c)) { 2, 1 -> { if (cc == 2) vigM++ else modM++; cadM++; cadEq += cc; active += m; credit(m, cc) } }
        }
        // Minuti di allenamento senza FC né cadenza: unione degli intervalli, per intensità (la vigorosa prevale)
        val estimated = HashMap<Long, SessionIntensity>()
        for (s in dedupe(sessions)) {
            val inten = s.kind.intensity
            if (inten != SessionIntensity.MODERATE && inten != SessionIntensity.VIGOROUS) continue
            val a = max(Math.floorDiv(s.start.epochSecond, 60L), dayStartMin)
            val b = min(Math.floorDiv(s.end.epochSecond + 59, 60L), dayEndMin)
            for (m in a until b) {
                if (hrByMinute.containsKey(m) || m in active) continue
                val prev = estimated[m]
                if (prev == null || (prev == SessionIntensity.MODERATE && inten == SessionIntensity.VIGOROUS)) estimated[m] = inten
            }
        }
        for ((m, i) in estimated) credit(m, if (i == SessionIntensity.VIGOROUS) 2 else 1)
        val modE = estimated.values.count { it == SessionIntensity.MODERATE }
        val vigE = estimated.values.count { it == SessionIntensity.VIGOROUS }
        val quality = when {
            hrMin == 0 -> LoadQuality.UNAVAILABLE
            hrMin < COMPLETE_COVERAGE_MIN -> LoadQuality.PARTIAL
            else -> LoadQuality.COMPLETE
        }
        return DayActivity(date, hrMin, modM, vigM, modE, vigE, load, zones, quality, cadM, cadEq, hourly.toList(), source, computedAt)
    }

    /**
     * Un giorno in cache si può riusare solo se è stato calcolato dopo la sua fine, lasciando tempo
     * all'orologio di sincronizzare: +6 h se la FC copre la giornata, +48 h altrimenti (orologio poco
     * indossato o sincronizzato tardi). I giorni con allenamenti si ricalcolano sempre.
     */
    fun cacheReusable(c: DayActivity, zone: ZoneId, hasSessions: Boolean): Boolean {
        if (hasSessions || c.stale) return false
        val end = c.date.plusDays(1).atStartOfDay(zone).toEpochSecond()
        return (c.computedAt >= end + 6 * 3600 && c.hrMinutes >= COMPLETE_COVERAGE_MIN) || c.computedAt >= end + 48 * 3600
    }

    /** Minuti di FC (reale o interpolata) dentro una sessione: serve a dire se l'allenamento ha dati cardiaci. */
    fun hrCoverage(s: Session, hrByMinute: Map<Long, Double>): Double {
        val a = Math.floorDiv(s.start.epochSecond, 60L); val b = Math.floorDiv(s.end.epochSecond + 59, 60L)
        if (b <= a) return 0.0
        return (a until b).count { hrByMinute.containsKey(it) }.toDouble() / (b - a)
    }

    fun approxEqual(a: Double, b: Double, eps: Double = 1e-9) = abs(a - b) < eps
}

/** Blocco di passi grezzo (da StepsRecord). */
data class StepChunk(val origin: String, val start: Instant, val end: Instant, val count: Long)

/**
 * Somma dei passi dai record grezzi, usata quando l'aggregazione di Connessione Salute restituisce 0
 * pur in presenza di record (succede se l'app sorgente non è nell'elenco "priorità delle app" di
 * Connessione Salute: l'aggregato la esclude). Per evitare doppi conteggi tra app diverse, per ogni
 * intervallo (ora o giorno) si prende il MASSIMO tra le app, non la somma. I record a cavallo di due
 * intervalli vengono ripartiti in proporzione alla durata.
 */
object StepsMerge {
    fun byBucket(chunks: List<StepChunk>, bucketOf: (Instant) -> Instant, bucketLen: (Instant) -> Duration): Map<Instant, Long> {
        val perOrigin = HashMap<String, HashMap<Instant, Double>>()
        for (c in chunks) {
            if (c.count <= 0 || !c.end.isAfter(c.start)) {
                if (c.count > 0) perOrigin.getOrPut(c.origin) { HashMap() }.merge(bucketOf(c.start), c.count.toDouble(), Double::plus)
                continue
            }
            val total = Duration.between(c.start, c.end).toMillis().toDouble()
            var t = c.start
            while (t.isBefore(c.end)) {
                val b = bucketOf(t)
                val bEnd = b.plus(bucketLen(b))
                val segEnd = minOf(bEnd, c.end)
                val share = Duration.between(t, segEnd).toMillis() / total * c.count
                perOrigin.getOrPut(c.origin) { HashMap() }.merge(b, share, Double::plus)
                t = segEnd
            }
        }
        val out = HashMap<Instant, Long>()
        for (m in perOrigin.values) for ((b, v) in m) out[b] = maxOf(out[b] ?: 0L, Math.round(v))
        return out
    }

    fun hourly(chunks: List<StepChunk>, zone: ZoneId): Map<Instant, Long> =
        byBucket(chunks, { it.atZone(zone).withMinute(0).withSecond(0).withNano(0).toInstant() }, { Duration.ofHours(1) })

    fun daily(chunks: List<StepChunk>, zone: ZoneId): Map<LocalDate, Long> =
        byBucket(chunks, { it.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant() },
            { b -> Duration.between(b, b.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()) })
            .mapKeys { it.key.atZone(zone).toLocalDate() }
}
