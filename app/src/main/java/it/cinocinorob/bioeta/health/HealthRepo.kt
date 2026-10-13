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
    val errors = java.util.concurrent.ConcurrentHashMap<String, String>()

    inline fun <T> track(name: String, block: () -> T): T? = try {
        block().also { errors.remove(name) }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        errors[name] = (e.javaClass.simpleName + ": " + (e.message ?: "")).take(300)
        null
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun granted(): Set<String> = client.permissionController.getGrantedPermissions()

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
                DailyPoint(d, list.sumOf { asleepHours(it) })
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
            // Notte principale = sessione più lunga che termina in quel giorno (i pisolini sono esclusi)
            sessions.groupBy { it.endTime.atZone(zone).toLocalDate() }
                .mapNotNull { (date, list) ->
                    val main = list.maxByOrNull { Duration.between(it.startTime, it.endTime) } ?: return@mapNotNull null
                    if (Duration.between(main.startTime, main.endTime).toMinutes() < 180) return@mapNotNull null
                    val stages = main.stages.map { Stage(it.startTime, it.endTime, mapStage(it.stage)) }
                    var avg: Double? = null
                    var mn: Double? = null
                    if (hasHr) runCatching {
                        val agg = client.aggregate(
                            AggregateRequest(
                                metrics = setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MIN),
                                timeRangeFilter = TimeRangeFilter.between(main.startTime, main.endTime),
                            )
                        )
                        avg = agg[HeartRateRecord.BPM_AVG]?.toDouble()
                        mn = agg[HeartRateRecord.BPM_MIN]?.toDouble()
                    }
                    SleepNight(date, main.startTime, main.endTime, stages, avg, mn)
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
            readRecords(HeartRateRecord::class, start, end)
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
        val dayStart = LocalDate.now(zone).atStartOfDay()
        val now = LocalDateTime.now(zone)
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
                    val h = b.startTime.atZone(zone).hour
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
    )

    suspend fun diagnose(): List<DiagRow> {
        val g = runCatching { granted() }.getOrDefault(emptySet())
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(7))
        suspend fun <T : Record> row(label: String, k: KClass<T>, time: (T) -> Instant): DiagRow {
            val perm = HealthPermission.getReadPermission(k)
            if (perm !in g) return DiagRow(label, perm, false, null, null, emptyList(), errors[label])
            return try {
                val resp = client.readRecords(ReadRecordsRequest(k, TimeRangeFilter.between(start, end), pageSize = 1000))
                val recs = resp.records
                DiagRow(
                    label, perm, true, recs.size, recs.maxOfOrNull(time),
                    recs.map { it.metadata.dataOrigin.packageName }.distinct().map(::appLabel), errors[label],
                )
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagRow(label, perm, true, null, null, emptyList(), e.javaClass.simpleName + ": " + (e.message ?: ""))
            }
        }
        return listOf(
            row("Passi", StepsRecord::class) { it.endTime },
            row("Sonno", SleepSessionRecord::class) { it.endTime },
            row("Frequenza cardiaca", HeartRateRecord::class) { it.endTime },
            row("FC a riposo", RestingHeartRateRecord::class) { it.time },
            row("HRV", HeartRateVariabilityRmssdRecord::class) { it.time },
            row("Allenamenti", ExerciseSessionRecord::class) { it.endTime },
            row("Calorie totali", TotalCaloriesBurnedRecord::class) { it.endTime },
            row("Calorie attive", ActiveCaloriesBurnedRecord::class) { it.endTime },
            row("Distanza", DistanceRecord::class) { it.endTime },
            row("Respirazione", RespiratoryRateRecord::class) { it.time },
            row("VO2max", Vo2MaxRecord::class) { it.time },
            row("SpO2", OxygenSaturationRecord::class) { it.time },
            row("Peso", WeightRecord::class) { it.time },
        )
    }

    private fun appLabel(pkg: String): String = runCatching {
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
