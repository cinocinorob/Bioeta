package it.cinocinorob.bioeta.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
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
import it.cinocinorob.bioeta.science.ExerciseLoad
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
    }

    suspend fun readAll(days: Long): Map<Metric, List<DailyPoint>> {
        val granted = granted()
        val zone = ZoneId.systemDefault()
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(days))
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
            out[Metric.STEPS] = groups.mapNotNull { g ->
                val c = g.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
                if (c <= 0L) null else DailyPoint(g.startTime.toLocalDate(), c.toDouble())
            }
        }
        return out
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
        val start = end.minus(Duration.ofDays(days))
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

        return RecoveryData(nights, exercises)
    }

    // ------------------------------------------------------------------
    // Fitness: FC grezza, attività di oggi, allenamenti
    // ------------------------------------------------------------------

    suspend fun heartRate(start: Instant, end: Instant): List<HrSample> {
        if (HealthPermission.getReadPermission(HeartRateRecord::class) !in granted()) return emptyList()
        return track("Frequenza cardiaca") {
            // Connessione Salute a volte rifiuta la lettura se l'app passa in secondo piano
            // durante una lettura lunga: un secondo tentativo, dopo una breve pausa, di solito riesce.
            // Legge tutte le pagine: un singolo readRecords() può restituire solo la prima
            // pagina e troncare il carico giornaliero nei giorni con molti record cardiaci.
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
        return TodayActivity(hourly, maxOf(total ?: 0L, hourly.sum()), kcal, km, tot)
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
        val start = end.minus(Duration.ofDays(days))
        return readRecords(ExerciseSessionRecord::class, start, end).sortedByDescending { it.startTime }.map { ex ->
            val hr = runCatching { heartRate(ex.startTime, ex.endTime) }.getOrDefault(emptyList())
            Workout(ex.metadata.id, ex.exerciseType, ex.title, ex.startTime, ex.endTime, hr)
        }
    }

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
