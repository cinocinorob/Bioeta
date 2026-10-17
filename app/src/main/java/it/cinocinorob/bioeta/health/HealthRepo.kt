package it.cinocinorob.bioeta.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import it.cinocinorob.bioeta.data.ReadWindow
import it.cinocinorob.bioeta.science.ActivityKind
import it.cinocinorob.bioeta.science.ExerciseLoad
import it.cinocinorob.bioeta.science.Session
import it.cinocinorob.bioeta.science.StepChunk
import it.cinocinorob.bioeta.science.StepsMerge
import it.cinocinorob.bioeta.science.HrSample
import it.cinocinorob.bioeta.science.SleepNight
import it.cinocinorob.bioeta.science.Stage
import it.cinocinorob.bioeta.science.StageType
import it.cinocinorob.bioeta.science.Stats
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

enum class Metric(
    val label: String,
    val unit: String,
    /** true = valori più alti favorevoli; false = sfavorevoli; null = nessuna direzione univoca. */
    val higherIsBetter: Boolean?,
    val decimals: Int,
) {
    RHR("FC a riposo", "bpm", false, 1),
    HRV("HRV (RMSSD)", "ms", true, 1),
    SLEEP("Sonno totale", "h", null, 2),
    STEPS("Passi", "/giorno", true, 0),
    VO2("VO₂max stimato", "mL/kg/min", true, 1),
    RESP("Frequenza respiratoria", "atti/min", null, 1),
    SPO2("SpO₂", "%", null, 1),
    WEIGHT("Peso", "kg", null, 1),
    BODY_FAT("Grasso corporeo", "%", null, 1),
    LEAN_MASS("Massa magra", "kg", null, 1),
}

data class DailyPoint(val date: LocalDate, val value: Double)

data class MetricSummary(
    val metric: Metric,
    val points: List<DailyPoint>,
    /** Media ultimi 7 giorni. */
    val recent: Double?,
    /** Media e DS giornaliera del periodo di riferimento (giorni 8-67 prima di oggi). */
    val baseline: Double?,
    val baselineSd: Double?,
    /** (recente − baseline) / DS giornaliera di baseline. */
    val z: Double?,
    /** Pendenza OLS per 30 giorni sull'intera finestra. */
    val slopePer30d: Double?,
)

class HealthRepo(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(Vo2MaxRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HISTORY_PERMISSION,
    )

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context, PROVIDER)

    /** Ultimo errore per tipo di dato (mostrato nella diagnostica invece di essere ignorato). */
    data class ReadError(val message: String, val at: Instant)
    val errors = java.util.concurrent.ConcurrentHashMap<String, ReadError>()

    inline fun <T> track(name: String, block: () -> T): T? = try {
        block().also { errors.remove(name) }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        errors[name] = ReadError((e.javaClass.simpleName + ": " + (e.message ?: "")).take(300), Instant.now())
        null
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun granted(): Set<String> = client.permissionController.getGrantedPermissions()

    /** Permesso associato a ogni serie mostrata nella dashboard. */
    fun permissionFor(metric: Metric): String = when (metric) {
        Metric.RHR -> HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        Metric.HRV -> HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)
        Metric.SLEEP -> HealthPermission.getReadPermission(SleepSessionRecord::class)
        Metric.STEPS -> HealthPermission.getReadPermission(StepsRecord::class)
        Metric.VO2 -> HealthPermission.getReadPermission(Vo2MaxRecord::class)
        Metric.RESP -> HealthPermission.getReadPermission(RespiratoryRateRecord::class)
        Metric.SPO2 -> HealthPermission.getReadPermission(OxygenSaturationRecord::class)
        Metric.WEIGHT -> HealthPermission.getReadPermission(WeightRecord::class)
        Metric.BODY_FAT -> HealthPermission.getReadPermission(BodyFatRecord::class)
        Metric.LEAN_MASS -> HealthPermission.getReadPermission(LeanBodyMassRecord::class)
    }

    /** Chiave diagnostica usata da readAll per distinguere un errore da una serie vuota. */
    fun readErrorKeyFor(metric: Metric): String = when (metric) {
        Metric.RHR -> "FC a riposo"
        Metric.HRV -> "HRV"
        Metric.SLEEP -> "Sonno (riepilogo)"
        Metric.STEPS -> "Passi giornalieri"
        Metric.VO2 -> "VO2max"
        Metric.RESP -> "Respirazione"
        Metric.SPO2 -> "SpO2"
        Metric.WEIGHT -> "Peso"
        Metric.BODY_FAT -> "Grasso corporeo"
        Metric.LEAN_MASS -> "Massa magra"
    }

    // ------------------------------------------------------------------
    // Cambiamenti (lettura incrementale) e lettura in background
    // ------------------------------------------------------------------

    /** Tipi seguiti per capire se sono cambiati dati vecchi (la FC è esclusa: troppi record, gestita a parte). */
    private val trackedTypes: List<KClass<out Record>> = listOf(
        SleepSessionRecord::class, HeartRateVariabilityRmssdRecord::class, RestingHeartRateRecord::class,
        StepsRecord::class, ExerciseSessionRecord::class, WeightRecord::class, RespiratoryRateRecord::class,
    )

    suspend fun changesToken(): String? = runCatching {
        val g = granted()
        val types = trackedTypes.filter { HealthPermission.getReadPermission(it) in g }.toSet()
        if (types.isEmpty()) null else client.getChangesToken(androidx.health.connect.client.request.ChangesTokenRequest(recordTypes = types))
    }.getOrNull()

    /** Riassume i cambiamenti dall'ultimo token: quanti e il dato più vecchio toccato. */
    suspend fun changesSince(token: String?): it.cinocinorob.bioeta.data.ChangeSummary? {
        if (token == null) return null
        return try {
            var t: String = token
            var count = 0
            var oldest: Instant? = null
            var unknownTime = false
            var pages = 0
            do {
                val r = client.getChanges(t)
                if (r.changesTokenExpired) return it.cinocinorob.bioeta.data.ChangeSummary(count, oldest, true, null)
                for (c in r.changes) {
                    count++
                    when (c) {
                        is androidx.health.connect.client.changes.UpsertionChange -> {
                            val rec = c.record
                            val time = when (rec) {
                                is SleepSessionRecord -> rec.startTime
                                is StepsRecord -> rec.startTime
                                is ExerciseSessionRecord -> rec.startTime
                                is HeartRateVariabilityRmssdRecord -> rec.time
                                is RestingHeartRateRecord -> rec.time
                                is WeightRecord -> rec.time
                                is RespiratoryRateRecord -> rec.time
                                else -> null
                            }
                            if (time == null) unknownTime = true else if (oldest == null || time.isBefore(oldest)) oldest = time
                        }
                        // Una cancellazione non dice di quale giorno era il dato: si rilegge tutto per sicurezza
                        else -> unknownTime = true
                    }
                }
                t = r.nextChangesToken
                pages++
            } while (r.hasMore && pages < 50)
            it.cinocinorob.bioeta.data.ChangeSummary(count, if (unknownTime) Instant.EPOCH else oldest, false, t)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** La lettura in background è supportata da questo telefono (Connessione Salute recente)? */
    fun backgroundReadAvailable(): Boolean = runCatching {
        client.features.getFeatureStatus(androidx.health.connect.client.HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            androidx.health.connect.client.HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    /** Permessi da chiedere: quelli di lettura più, se supportato, la lettura in background. */
    fun permissionsToRequest(): Set<String> = if (backgroundReadAvailable()) permissions + BACKGROUND_PERMISSION else permissions

    /** true se i passi mostrati vengono dai record grezzi perché l'aggregato di Connessione Salute era vuoto. */
    @Volatile var stepsFromRaw = false

    private suspend fun stepChunks(start: Instant, end: Instant): List<StepChunk> =
        readRecords(StepsRecord::class, start, end).map { StepChunk(it.metadata.dataOrigin.packageName, it.startTime, it.endTime, it.count) }

    suspend fun readAll(days: Long): Map<Metric, List<DailyPoint>> {
        val granted = granted()
        val zone = ZoneId.systemDefault()
        val end = Instant.now()
        val start = queryStart(days, granted, end)
        val out = mutableMapOf<Metric, List<DailyPoint>>()

        fun has(k: KClass<out Record>) = HealthPermission.getReadPermission(k) in granted

        if (has(RestingHeartRateRecord::class)) track("FC a riposo") {
            out[Metric.RHR] = dailyMean(readRecords(RestingHeartRateRecord::class, start, end)
                .map { it.time to it.beatsPerMinute.toDouble() }, zone)
        }
        if (has(HeartRateVariabilityRmssdRecord::class)) track("HRV") {
            out[Metric.HRV] = dailyMean(readRecords(HeartRateVariabilityRmssdRecord::class, start, end)
                .map { it.time to it.heartRateVariabilityMillis }, zone)
        }
        if (has(Vo2MaxRecord::class)) track("VO2max") {
            out[Metric.VO2] = dailyMean(readRecords(Vo2MaxRecord::class, start, end)
                .map { it.time to it.vo2MillilitersPerMinuteKilogram }, zone)
        }
        if (has(WeightRecord::class)) track("Peso") {
            out[Metric.WEIGHT] = dailyMean(readRecords(WeightRecord::class, start, end)
                .map { it.time to it.weight.inKilograms }, zone)
        }
        if (has(BodyFatRecord::class)) track("Grasso corporeo") {
            out[Metric.BODY_FAT] = dailyMean(readRecords(BodyFatRecord::class, start, end)
                .map { it.time to it.percentage.value }, zone)
        }
        if (has(LeanBodyMassRecord::class)) track("Massa magra") {
            out[Metric.LEAN_MASS] = dailyMean(readRecords(LeanBodyMassRecord::class, start, end)
                .map { it.time to it.mass.inKilograms }, zone)
        }
        if (has(OxygenSaturationRecord::class)) track("SpO2") {
            out[Metric.SPO2] = dailyMean(readRecords(OxygenSaturationRecord::class, start, end)
                .map { it.time to it.percentage.value }, zone)
        }
        if (has(RespiratoryRateRecord::class)) track("Respirazione") {
            out[Metric.RESP] = dailyMean(readRecords(RespiratoryRateRecord::class, start, end)
                .map { it.time to it.rate }, zone)
        }
        if (has(SleepSessionRecord::class)) track("Sonno (riepilogo)") {
            val sessions = readRecords(SleepSessionRecord::class, start, end)
            val byDay = sessions.groupBy { it.endTime.atZone(zone).toLocalDate() }
            out[Metric.SLEEP] = byDay.map { (d, list) ->
                val origin = list.maxBy { Duration.between(it.startTime, it.endTime) }.metadata.dataOrigin.packageName
                DailyPoint(d, list.filter { it.metadata.dataOrigin.packageName == origin }.sumOf { asleepHours(it) })
            }.sortedBy { it.date }
        }
        if (has(StepsRecord::class)) track("Passi giornalieri") {
            val today = LocalDate.now(zone)
            val groups = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        LocalDateTime.ofInstant(start, zone).toLocalDate().atStartOfDay(),
                        today.atStartOfDay(), // giornata in corso esclusa (parziale)
                    ),
                    timeRangeSlicer = Period.ofDays(1),
                )
            )
            val agg = groups.mapNotNull { g ->
                val c = g.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
                if (c <= 0L) null else g.startTime.toLocalDate() to c
            }.toMap()
            // Ripiego sui record grezzi per i giorni che l'aggregato restituisce vuoti (vedi StepsMerge)
            val todayStart = today.atStartOfDay(zone).toInstant()
            val raw = StepsMerge.daily(stepChunks(start, todayStart), zone)
            stepsFromRaw = raw.keys.any { it !in agg }
            out[Metric.STEPS] = (raw.keys + agg.keys).toSortedSet().mapNotNull { d ->
                val v = agg[d] ?: raw[d] ?: return@mapNotNull null
                if (v <= 0L) null else DailyPoint(d, v.toDouble())
            }
        }
        // Il primo giorno della finestra può essere incompleto: lo si scarta, così una lettura breve non
        // sovrascrive mai un valore completo con uno parziale.
        val firstPoint = ReadWindow.firstCompleteDay(start, zone, overnight = false)
        val firstNight = ReadWindow.firstCompleteDay(start, zone, overnight = true)
        return out.mapValues { (m, pts) ->
            val first = if (m == Metric.SLEEP) firstNight else firstPoint
            pts.filter { !it.date.isBefore(first) }
        }
    }

    // ------------------------------------------------------------------
    // Sonno dettagliato e allenamenti (per i punteggi Sonno e Prontezza)
    // ------------------------------------------------------------------

    data class RecoveryData(
        val nights: List<SleepNight>,
        val exercises: List<ExerciseLoad>,
    )

    suspend fun readRecovery(days: Long): RecoveryData {
        val granted = granted()
        val zone = ZoneId.systemDefault()
        val end = Instant.now()
        val start = queryStart(days, granted, end)
        val hasHr = HealthPermission.getReadPermission(HeartRateRecord::class) in granted

        val nights = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) track("Sonno (fasi)") {
            val sessions = readRecords(SleepSessionRecord::class, start, end)
            // Notte principale = gruppo di sessioni contigue (pause < 2 h) attorno alla più lunga tra quelle
            // che terminano in quel giorno: alcune app spezzano la notte in più sessioni.
            sessions.groupBy { it.endTime.atZone(zone).toLocalDate() }
                .mapNotNull { (date, list) ->
                    val sorted = list.sortedBy { it.startTime }
                    val longest = sorted.maxByOrNull { Duration.between(it.startTime, it.endTime) } ?: return@mapNotNull null
                    val cluster = mutableListOf(longest)
                    var i = sorted.indexOf(longest) - 1
                    while (i >= 0 && Duration.between(sorted[i].endTime, cluster.first().startTime).toMinutes() < 120) { cluster.add(0, sorted[i]); i-- }
                    i = sorted.indexOf(longest) + 1
                    while (i < sorted.size && Duration.between(cluster.last().endTime, sorted[i].startTime).toMinutes() < 120) { cluster.add(sorted[i]); i++ }
                    // Se due app scrivono la stessa notte (es. orologio e modalità Riposo del telefono),
                    // tengo la fonte della sessione principale per non contare due volte il sonno.
                    val origin = longest.metadata.dataOrigin.packageName
                    fun overlaps(a: SleepSessionRecord, b: SleepSessionRecord) = a.startTime < b.endTime && b.startTime < a.endTime
                    cluster.retainAll { ss -> ss.metadata.dataOrigin.packageName == origin || cluster.none { o -> o.metadata.dataOrigin.packageName == origin && overlaps(o, ss) } }
                    val mStart = cluster.first().startTime
                    val mEnd = cluster.maxOf { it.endTime }
                    if (Duration.between(mStart, mEnd).toMinutes() < 180) return@mapNotNull null
                    val anyStages = cluster.any { it.stages.isNotEmpty() }
                    val stages = if (!anyStages) emptyList() else cluster.flatMap { ss ->
                        if (ss.stages.isEmpty()) listOf(Stage(ss.startTime, ss.endTime, StageType.ASLEEP_UNKNOWN))
                        else ss.stages.map { Stage(it.startTime, it.endTime, mapStage(it.stage)) }
                    }.sortedBy { it.start }
                    var avg: Double? = null
                    var mn: Double? = null
                    if (hasHr) runCatching {
                        val agg = client.aggregate(
                            AggregateRequest(
                                metrics = setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MIN),
                                timeRangeFilter = TimeRangeFilter.between(mStart, mEnd),
                            )
                        )
                        avg = agg[HeartRateRecord.BPM_AVG]?.toDouble()
                        mn = agg[HeartRateRecord.BPM_MIN]?.toDouble()
                    }
                    SleepNight(date, mStart, mEnd, stages, avg, mn)
                }.sortedBy { it.date }
        } ?: emptyList() else emptyList()

        val exercises = if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) track("Allenamenti") {
            readRecords(ExerciseSessionRecord::class, start, end).map { ex ->
                val minutes = Duration.between(ex.startTime, ex.endTime).seconds / 60.0
                var avg: Double? = null
                if (hasHr) runCatching {
                    val agg = client.aggregate(
                        AggregateRequest(setOf(HeartRateRecord.BPM_AVG), TimeRangeFilter.between(ex.startTime, ex.endTime))
                    )
                    avg = agg[HeartRateRecord.BPM_AVG]?.toDouble()
                }
                ExerciseLoad(ex.startTime.atZone(zone).toLocalDate(), minutes, avg)
            }
        } ?: emptyList() else emptyList()

        val firstNight = ReadWindow.firstCompleteDay(start, zone, overnight = true)
        val firstDay = ReadWindow.firstCompleteDay(start, zone, overnight = false)
        return RecoveryData(nights.filter { !it.date.isBefore(firstNight) }, exercises.filter { !it.date.isBefore(firstDay) })
    }

    // ------------------------------------------------------------------
    // Fitness: FC grezza, attività di oggi, allenamenti
    // ------------------------------------------------------------------

    suspend fun heartRate(start: Instant, end: Instant): List<HrSample> {
        if (HealthPermission.getReadPermission(HeartRateRecord::class) !in granted()) return emptyList()
        return track("Frequenza cardiaca") {
            // Connessione Salute a volte rifiuta la lettura se l'app passa in secondo piano
            // durante una lettura lunga: un secondo tentativo, dopo una breve pausa, di solito riesce.
            // Il helper readRecords() scorre tutte le pagine prima di restituire i campioni.
            val recs = try {
                readRecords(HeartRateRecord::class, start, end)
            } catch (e: SecurityException) {
                kotlinx.coroutines.delay(700)
                readRecords(HeartRateRecord::class, start, end)
            }
            recs
                .flatMap { r -> r.samples.map { HrSample(it.time, it.beatsPerMinute.toDouble()) } }
                .filter { it.time >= start && it.time < end }
        } ?: emptyList()
    }

    data class TodayActivity(
        val hourlySteps: List<Long>,
        val steps: Long,
        val activeKcal: Double?,
        val distanceKm: Double?,
        val totalKcal: Double? = null,
        val recordedExerciseMinutes: Int = 0,
    )

    suspend fun todayActivity(): TodayActivity {
        val g = granted()
        val zone = ZoneId.systemDefault()
        // Filtri in Instant: con LocalDateTime Connessione Salute usa il fuso salvato in ogni record,
        // e i record senza fuso (es. contapassi del telefono) possono restare fuori.
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val now = Instant.now()
        val hourly = MutableList(24) { 0L }
        var total: Long? = null
        if (HealthPermission.getReadPermission(StepsRecord::class) in g) {
            track("Passi di oggi") {
                client.aggregateGroupByDuration(
                    AggregateGroupByDurationRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(dayStart, now),
                        timeRangeSlicer = Duration.ofHours(1),
                    )
                ).forEach { b ->
                    val h = b.startTime.atZone(zone).hour.coerceIn(0, 23)
                    hourly[h] = b.result[StepsRecord.COUNT_TOTAL] ?: 0L
                }
                total = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(dayStart, now)))[StepsRecord.COUNT_TOTAL]
                if ((total ?: 0L) == 0L && hourly.sum() == 0L) {
                    val raw = StepsMerge.hourly(stepChunks(dayStart, now), zone)
                    if (raw.isNotEmpty()) {
                        raw.forEach { (b, v) -> val h = b.atZone(zone).hour; if (b >= dayStart) hourly[h] = hourly[h] + v }
                        total = hourly.sum()
                        stepsFromRaw = true
                    }
                }
            }
        }
        var kcal: Double? = null
        var km: Double? = null
        var tot: Double? = null
        track("Calorie e distanza") {
            val metrics = buildSet {
                if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in g) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in g) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
                if (HealthPermission.getReadPermission(DistanceRecord::class) in g) add(DistanceRecord.DISTANCE_TOTAL)
            }
            if (metrics.isNotEmpty()) {
                val r = client.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(dayStart, now)))
                kcal = r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                tot = r[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                km = r[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
            }
        }
        var exerciseMinutes = 0
        if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in g) {
            track("Allenamenti di oggi") {
                val sessions = readRecords(ExerciseSessionRecord::class, dayStart, now)
                    .mapNotNull { ex ->
                        val s = maxOf(ex.startTime, dayStart)
                        val e = minOf(ex.endTime, now)
                        if (e.isAfter(s)) s to e else null
                    }.sortedBy { it.first }
                var intervalStart: Instant? = null
                var intervalEnd: Instant? = null
                var seconds = 0L
                for ((s, e) in sessions) {
                    val currentEnd = intervalEnd
                    if (intervalStart == null) { intervalStart = s; intervalEnd = e }
                    else if (!s.isAfter(currentEnd)) { if (e.isAfter(currentEnd)) intervalEnd = e }
                    else {
                        seconds += Duration.between(intervalStart, currentEnd).seconds
                        intervalStart = s; intervalEnd = e
                    }
                }
                if (intervalStart != null && intervalEnd != null) seconds += Duration.between(intervalStart, intervalEnd).seconds
                exerciseMinutes = (seconds / 60L).toInt()
            }
        }
        return TodayActivity(hourly, maxOf(total ?: 0L, hourly.sum()), kcal, km, tot, exerciseMinutes)
    }

    // ------------------------------------------------------------------
    // Diagnostica: cosa c'è davvero in Connessione Salute
    // ------------------------------------------------------------------

    data class DiagRow(
        val label: String,
        val permission: String,
        val granted: Boolean,
        val count7d: Int?,
        val latest: Instant?,
        val origins: List<String>,
        val error: String?,
        val pastError: ReadError? = null,
        val capped: Boolean = false,
    )

    suspend fun diagnose(): List<DiagRow> {
        val g = runCatching { granted() }.getOrDefault(emptySet())
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(7))
        suspend fun <T : Record> row(label: String, k: KClass<T>, errKeys: List<String>, time: (T) -> Instant): DiagRow {
            val perm = HealthPermission.getReadPermission(k)
            val past = errKeys.mapNotNull { errors[it] }.maxByOrNull { it.at }
            if (perm !in g) return DiagRow(label, perm, false, null, null, emptyList(), null, past)
            return try {
                // Conteggio (fino a 20 pagine) e ultimo dato letto a parte, in ordine decrescente:
                // con molti campioni (es. FC) la prima pagina non contiene i dati più recenti.
                var n = 0; var token: String? = null; var pages = 0
                val origins = linkedSetOf<String>()
                do {
                    val resp = client.readRecords(ReadRecordsRequest(k, TimeRangeFilter.between(start, end), pageSize = 1000, pageToken = token))
                    n += resp.records.size
                    resp.records.forEach { origins += it.metadata.dataOrigin.packageName }
                    token = resp.pageToken; pages++
                } while (token != null && pages < 20)
                val last = client.readRecords(ReadRecordsRequest(k, TimeRangeFilter.between(start, end), ascendingOrder = false, pageSize = 1))
                    .records.firstOrNull()?.let(time)
                DiagRow(label, perm, true, n, last, origins.map(::appLabel), null, past, capped = token != null)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagRow(label, perm, true, null, null, emptyList(), e.javaClass.simpleName + ": " + (e.message ?: ""), past)
            }
        }
        return listOf(
            row("Passi", StepsRecord::class, listOf("Passi giornalieri", "Passi di oggi")) { it.endTime },
            row("Sonno", SleepSessionRecord::class, listOf("Sonno (riepilogo)", "Sonno (fasi)")) { it.endTime },
            row("Frequenza cardiaca", HeartRateRecord::class, listOf("Frequenza cardiaca")) { it.endTime },
            row("FC a riposo", RestingHeartRateRecord::class, listOf("FC a riposo")) { it.time },
            row("HRV", HeartRateVariabilityRmssdRecord::class, listOf("HRV")) { it.time },
            row("Allenamenti", ExerciseSessionRecord::class, listOf("Allenamenti")) { it.endTime },
            row("Calorie totali", TotalCaloriesBurnedRecord::class, listOf("Calorie e distanza")) { it.endTime },
            row("Calorie attive", ActiveCaloriesBurnedRecord::class, listOf("Calorie e distanza")) { it.endTime },
            row("Distanza", DistanceRecord::class, listOf("Calorie e distanza")) { it.endTime },
            row("Respirazione", RespiratoryRateRecord::class, listOf("Respirazione")) { it.time },
            row("VO2max", Vo2MaxRecord::class, listOf("VO2max")) { it.time },
            row("SpO2", OxygenSaturationRecord::class, listOf("SpO2")) { it.time },
            row("Peso", WeightRecord::class, listOf("Peso")) { it.time },
            row("Grasso corporeo", BodyFatRecord::class, listOf("Grasso corporeo")) { it.time },
            row("Massa magra", LeanBodyMassRecord::class, listOf("Massa magra")) { it.time },
        )
    }

    /**
     * Istante dell'ultimo dato scritto dall'orologio (sonno, HRV, FC a riposo, FC), esclusi i passi
     * contati dal telefono. Serve a capire se l'app dell'orologio sta sincronizzando.
     */
    suspend fun lastWearableData(): Instant? {
        val g = runCatching { granted() }.getOrDefault(emptySet())
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(30))
        suspend fun <T : Record> last(k: KClass<T>, time: (T) -> Instant): Instant? {
            if (HealthPermission.getReadPermission(k) !in g) return null
            return runCatching {
                client.readRecords(ReadRecordsRequest(k, TimeRangeFilter.between(start, end), ascendingOrder = false, pageSize = 1))
                    .records.firstOrNull()?.let(time)
            }.getOrNull()
        }
        return listOfNotNull(
            last(SleepSessionRecord::class) { it.endTime },
            last(HeartRateVariabilityRmssdRecord::class) { it.time },
            last(RestingHeartRateRecord::class) { it.time },
            last(HeartRateRecord::class) { it.endTime },
        ).maxOrNull()
    }

    /**
     * Health Connect limita la lettura dei dati di altre app ai 30 giorni precedenti
     * se non è concesso READ_HEALTH_DATA_HISTORY. Evita query più lunghe in quel caso,
     * perché una query fuori finestra può fallire e far perdere anche i dati recenti.
     */
    /** Inizio lettura allineato alla mezzanotte locale (vedi [ReadWindow]). */
    private fun queryStart(days: Long, granted: Set<String>, end: Instant): Instant =
        ReadWindow.start(days, end, ZoneId.systemDefault(), HISTORY_PERMISSION in granted)

    private fun appLabel(pkg: String): String = KNOWN_APPS[pkg]
        ?: if (pkg.startsWith("com.android.healthconnect") || pkg == PROVIDER) "Telefono (contapassi di Connessione Salute)"
        else runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    data class Workout(
        val id: String,
        val type: Int,
        val title: String?,
        val start: Instant,
        val end: Instant,
        val samples: List<HrSample>,
    ) {
        val minutes: Double get() = Duration.between(start, end).seconds / 60.0
    }

    suspend fun workouts(days: Long): List<Workout> {
        val g = granted()
        if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) !in g) return emptyList()
        val end = Instant.now()
        val start = queryStart(days, g, end)
        return readRecords(ExerciseSessionRecord::class, start, end).sortedByDescending { it.startTime }.map { ex ->
            val hr = runCatching { heartRate(ex.startTime, ex.endTime) }.getOrDefault(emptyList())
            Workout(ex.metadata.id, ex.exerciseType, ex.title, ex.startTime, ex.endTime, hr)
        }
    }

    /** Esito della lettura delle sessioni di esercizio per la sincronizzazione. */
    data class SessionRead(val sessions: List<Session>, val from: Instant, val to: Instant, val days: Int, val error: String?, val permitted: Boolean)

    /**
     * Legge TUTTE le sessioni di esercizio nella finestra (tutte le pagine), senza FC: è una lettura
     * leggera, eseguita nella fase veloce dell'aggiornamento così gli allenamenti compaiono subito.
     * Finestra: [days] giorni, ridotta a 30 senza il permesso dello storico (limite di Connessione Salute).
     */
    suspend fun readSessions(days: Long): SessionRead {
        val g = granted()
        val end = Instant.now()
        val start = queryStart(days, g, end)
        val nDays = Duration.between(start, end).toDays().toInt()
        if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) !in g) return SessionRead(emptyList(), start, end, nDays, null, false)
        return try {
            val list = readRecords(ExerciseSessionRecord::class, start, end).map { ex ->
                Session(
                    id = ex.metadata.id, origin = ex.metadata.dataOrigin.packageName, kind = kindOf(ex.exerciseType),
                    title = ex.title, start = ex.startTime, end = ex.endTime, hcType = ex.exerciseType,
                )
            }
            errors.remove("Allenamenti (sincronizzazione)")
            SessionRead(list, start, end, nDays, null, true)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = (e.javaClass.simpleName + ": " + (e.message ?: "")).take(300)
            errors["Allenamenti (sincronizzazione)"] = ReadError(msg, Instant.now())
            SessionRead(emptyList(), start, end, nDays, msg, true)
        }
    }

    fun originLabel(pkg: String): String = appLabel(pkg)

    private fun mapStage(s: Int): StageType = when (s) {
        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> StageType.AWAKE
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> StageType.OUT_OF_BED
        SleepSessionRecord.STAGE_TYPE_LIGHT -> StageType.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> StageType.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> StageType.REM
        else -> StageType.ASLEEP_UNKNOWN
    }

    private fun asleepHours(s: SleepSessionRecord): Double {
        val notAsleep = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        )
        val stages = s.stages
        val seconds = if (stages.isEmpty()) {
            Duration.between(s.startTime, s.endTime).seconds
        } else {
            stages.filter { it.stage !in notAsleep }
                .sumOf { Duration.between(it.startTime, it.endTime).seconds }
        }
        return seconds / 3600.0
    }

    private suspend fun <T : Record> readRecords(type: KClass<T>, start: Instant, end: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val resp = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = token,
                )
            )
            out += resp.records
            token = resp.pageToken
        } while (token != null)
        return out
    }

    private fun dailyMean(samples: List<Pair<Instant, Double>>, zone: ZoneId): List<DailyPoint> =
        samples.groupBy { it.first.atZone(zone).toLocalDate() }
            .map { (d, l) -> DailyPoint(d, l.map { it.second }.average()) }
            .sortedBy { it.date }

    companion object {

        /** Mappa i codici di Connessione Salute nelle categorie usate dal motore di calcolo. */
        fun kindOf(t: Int): ActivityKind = with(ExerciseSessionRecord) {
            when (t) {
                EXERCISE_TYPE_RUNNING, EXERCISE_TYPE_RUNNING_TREADMILL -> ActivityKind.RUNNING
                EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, EXERCISE_TYPE_BOOT_CAMP -> ActivityKind.HIIT
                EXERCISE_TYPE_SOCCER, EXERCISE_TYPE_BASKETBALL, EXERCISE_TYPE_RUGBY, EXERCISE_TYPE_FOOTBALL_AMERICAN,
                EXERCISE_TYPE_FOOTBALL_AUSTRALIAN, EXERCISE_TYPE_HANDBALL, EXERCISE_TYPE_ICE_HOCKEY, EXERCISE_TYPE_ROLLER_HOCKEY,
                EXERCISE_TYPE_WATER_POLO -> ActivityKind.TEAM_SPORT
                EXERCISE_TYPE_BOXING, EXERCISE_TYPE_MARTIAL_ARTS -> ActivityKind.COMBAT
                EXERCISE_TYPE_STAIR_CLIMBING, EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> ActivityKind.STAIRS
                EXERCISE_TYPE_ROCK_CLIMBING -> ActivityKind.CLIMBING
                EXERCISE_TYPE_BIKING, EXERCISE_TYPE_BIKING_STATIONARY -> ActivityKind.BIKING
                EXERCISE_TYPE_SWIMMING_POOL, EXERCISE_TYPE_SWIMMING_OPEN_WATER -> ActivityKind.SWIMMING
                EXERCISE_TYPE_WALKING, EXERCISE_TYPE_WHEELCHAIR -> ActivityKind.WALKING
                EXERCISE_TYPE_HIKING, EXERCISE_TYPE_SNOWSHOEING -> ActivityKind.HIKING
                EXERCISE_TYPE_ROWING, EXERCISE_TYPE_ROWING_MACHINE, EXERCISE_TYPE_PADDLING -> ActivityKind.ROWING
                EXERCISE_TYPE_ELLIPTICAL -> ActivityKind.ELLIPTICAL
                EXERCISE_TYPE_STRENGTH_TRAINING, EXERCISE_TYPE_WEIGHTLIFTING, EXERCISE_TYPE_CALISTHENICS -> ActivityKind.STRENGTH
                EXERCISE_TYPE_TENNIS, EXERCISE_TYPE_BADMINTON, EXERCISE_TYPE_SQUASH, EXERCISE_TYPE_RACQUETBALL,
                EXERCISE_TYPE_TABLE_TENNIS, EXERCISE_TYPE_VOLLEYBALL -> ActivityKind.RACKET
                EXERCISE_TYPE_DANCING, EXERCISE_TYPE_EXERCISE_CLASS, EXERCISE_TYPE_GYMNASTICS -> ActivityKind.DANCE_CLASS
                EXERCISE_TYPE_SKIING, EXERCISE_TYPE_SNOWBOARDING, EXERCISE_TYPE_SKATING, EXERCISE_TYPE_ICE_SKATING -> ActivityKind.WINTER
                EXERCISE_TYPE_SURFING -> ActivityKind.WATER
                EXERCISE_TYPE_YOGA, EXERCISE_TYPE_PILATES, EXERCISE_TYPE_STRETCHING, EXERCISE_TYPE_GUIDED_BREATHING -> ActivityKind.MIND_BODY
                EXERCISE_TYPE_GOLF, EXERCISE_TYPE_SAILING -> ActivityKind.LIGHT_OTHER
                else -> ActivityKind.OTHER
            }
        }

        val KNOWN_APPS = mapOf(
            "com.fitbit.FitbitMobile" to "Fitbit / Google Health",
            "com.google.android.apps.fitness" to "Google Fit",
            "com.google.android.apps.wearables.maestro.companion" to "Pixel Watch",
            "com.sec.android.app.shealth" to "Samsung Health",
            "com.ouraring.oura" to "Oura",
        )
        const val FITBIT = "com.fitbit.FitbitMobile"
        const val PROVIDER = "com.google.android.apps.healthdata"
        const val HISTORY_PERMISSION = "android.permission.health.READ_HEALTH_DATA_HISTORY"
        const val BACKGROUND_PERMISSION = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

        fun summarize(metric: Metric, points: List<DailyPoint>, today: LocalDate = LocalDate.now()): MetricSummary {
            val recentFrom = today.minusDays(7)
            val baseFrom = today.minusDays(67)
            val recent = points.filter { it.date.isAfter(recentFrom) }.map { it.value }
            val base = points.filter { !it.date.isAfter(recentFrom) && it.date.isAfter(baseFrom) }.map { it.value }
            val r = if (recent.size >= 3) Stats.mean(recent) else null
            val b = if (base.size >= 14) Stats.mean(base) else null
            val sd = if (base.size >= 14) Stats.sd(base) else null
            val z = if (r != null && b != null && sd != null && sd > 0) (r - b) / sd else null
            val slope = if (points.size >= 14) {
                val x0 = points.first().date.toEpochDay()
                Stats.olsSlope(points.map { (it.date.toEpochDay() - x0).toDouble() }, points.map { it.value })
                    ?.let { it * 30.0 }
            } else null
            return MetricSummary(metric, points, r, b, sd, z, slope)
        }
    }
}
