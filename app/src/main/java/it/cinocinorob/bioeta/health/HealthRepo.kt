package it.cinocinorob.bioeta.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
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
        HISTORY_PERMISSION,
    )

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context, PROVIDER)

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun granted(): Set<String> = client.permissionController.getGrantedPermissions()

    suspend fun readAll(days: Long): Map<Metric, List<DailyPoint>> {
        val granted = granted()
        val zone = ZoneId.systemDefault()
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(days))
        val out = mutableMapOf<Metric, List<DailyPoint>>()

        fun has(k: KClass<out Record>) = HealthPermission.getReadPermission(k) in granted

        if (has(RestingHeartRateRecord::class)) runCatching {
            out[Metric.RHR] = dailyMean(readRecords(RestingHeartRateRecord::class, start, end)
                .map { it.time to it.beatsPerMinute.toDouble() }, zone)
        }
        if (has(HeartRateVariabilityRmssdRecord::class)) runCatching {
            out[Metric.HRV] = dailyMean(readRecords(HeartRateVariabilityRmssdRecord::class, start, end)
                .map { it.time to it.heartRateVariabilityMillis }, zone)
        }
        if (has(Vo2MaxRecord::class)) runCatching {
            out[Metric.VO2] = dailyMean(readRecords(Vo2MaxRecord::class, start, end)
                .map { it.time to it.vo2MillilitersPerMinuteKilogram }, zone)
        }
        if (has(WeightRecord::class)) runCatching {
            out[Metric.WEIGHT] = dailyMean(readRecords(WeightRecord::class, start, end)
                .map { it.time to it.weight.inKilograms }, zone)
        }
        if (has(OxygenSaturationRecord::class)) runCatching {
            out[Metric.SPO2] = dailyMean(readRecords(OxygenSaturationRecord::class, start, end)
                .map { it.time to it.percentage.value }, zone)
        }
        if (has(RespiratoryRateRecord::class)) runCatching {
            out[Metric.RESP] = dailyMean(readRecords(RespiratoryRateRecord::class, start, end)
                .map { it.time to it.rate }, zone)
        }
        if (has(SleepSessionRecord::class)) runCatching {
            val sessions = readRecords(SleepSessionRecord::class, start, end)
            val byDay = sessions.groupBy { it.endTime.atZone(zone).toLocalDate() }
            out[Metric.SLEEP] = byDay.map { (d, list) ->
                DailyPoint(d, list.sumOf { asleepHours(it) })
            }.sortedBy { it.date }
        }
        if (has(StepsRecord::class)) runCatching {
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
