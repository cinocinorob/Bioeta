package it.cinocinorob.bioeta.data

import android.content.Context
import it.cinocinorob.bioeta.health.DailyPoint
import it.cinocinorob.bioeta.health.HealthRepo
import it.cinocinorob.bioeta.health.Metric
import it.cinocinorob.bioeta.science.ExerciseLoad
import it.cinocinorob.bioeta.science.SleepNight
import it.cinocinorob.bioeta.science.Stage
import it.cinocinorob.bioeta.science.StageType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Ultima fotografia dei dati letti da Connessione Salute. Salvata sul telefono così l'app si apre già
 * con i numeri dell'ultima lettura (poi aggiornati) e il worker in background può aggiornarla.
 * Non entra nei backup: è ricostruibile da Connessione Salute.
 */
data class HealthSnapshot(
    val wearable: Map<Metric, List<DailyPoint>> = emptyMap(),
    val nights: List<SleepNight> = emptyList(),
    val exercises: List<ExerciseLoad> = emptyList(),
    val today: HealthRepo.TodayActivity? = null,
    val todayDate: LocalDate? = null,
    val lastRefresh: Instant? = null,
    val lastFull: Instant? = null,
    val lastWearable: Instant? = null,
    val changesToken: String? = null,
    val lastBackground: Instant? = null,
) {
    val hasData: Boolean get() = wearable.isNotEmpty() || nights.isNotEmpty()
}

/** Regole pure di unione e di aggiornamento (coperte da test). */
object SnapshotMerge {
    /** Sostituisce i punti dal giorno [from] in poi con quelli nuovi, per ogni metrica letta. */
    fun series(old: Map<Metric, List<DailyPoint>>, fresh: Map<Metric, List<DailyPoint>>, from: LocalDate): Map<Metric, List<DailyPoint>> {
        val out = old.toMutableMap()
        for ((m, pts) in fresh) {
            val kept = old[m].orEmpty().filter { it.date.isBefore(from) }
            out[m] = (kept + pts.filter { !it.date.isBefore(from) }).sortedBy { it.date }
        }
        return out
    }

    /** Le notti nuove sostituiscono quelle con la stessa data. */
    fun nights(old: List<SleepNight>, fresh: List<SleepNight>): List<SleepNight> {
        val dates = fresh.map { it.date }.toSet()
        return (old.filter { it.date !in dates } + fresh).sortedBy { it.date }
    }

    /**
     * Serve una lettura completa (120 giorni)? Sì se: richiesta esplicita, nessuna fotografia, ultima completa
     * più vecchia di 6 ore, token dei cambiamenti scaduto/assente, oppure Connessione Salute segnala dati
     * modificati più vecchi di 3 giorni (es. orologio sincronizzato in ritardo). Altrimenti basta la lettura
     * veloce degli ultimi giorni.
     */
    fun needFull(force: Boolean, s: HealthSnapshot, now: Instant, changes: ChangeSummary?): Boolean {
        if (force || !s.hasData) return true
        val lf = s.lastFull ?: return true
        if (Duration.between(lf, now) > Duration.ofHours(6)) return true
        if (changes == null || changes.expired) return true
        val oldest = changes.oldestChanged ?: return false
        return oldest.isBefore(now.minus(Duration.ofDays(3)))
    }
}

/** Esito della lettura dei cambiamenti di Connessione Salute dall'ultimo token. */
data class ChangeSummary(val count: Int, val oldestChanged: Instant?, val expired: Boolean, val newToken: String?)

class HealthCache(context: Context) {
    private val file = File(context.filesDir, "hc_snapshot.json")

    fun load(): HealthSnapshot = runCatching { fromJson(JSONObject(file.readText())) }.getOrDefault(HealthSnapshot())

    @Synchronized
    fun save(s: HealthSnapshot) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(toJson(s).toString())
        tmp.renameTo(file)
    }

    companion object {
        fun toJson(s: HealthSnapshot): JSONObject {
            val o = JSONObject()
            val w = JSONObject()
            s.wearable.forEach { (m, pts) ->
                val a = JSONArray(); pts.forEach { a.put(JSONArray().put(it.date.toString()).put(it.value)) }
                w.put(m.name, a)
            }
            o.put("w", w)
            val n = JSONArray()
            s.nights.forEach { night ->
                val st = JSONArray()
                night.stages.forEach { st.put(JSONArray().put(it.start.toEpochMilli()).put(it.end.toEpochMilli()).put(it.type.name)) }
                n.put(JSONObject().put("d", night.date.toString()).put("s", night.start.toEpochMilli()).put("e", night.end.toEpochMilli())
                    .put("st", st).apply { night.avgHr?.let { put("a", it) }; night.minHr?.let { put("m", it) } })
            }
            o.put("n", n)
            val ex = JSONArray()
            s.exercises.forEach { e -> ex.put(JSONObject().put("d", e.date.toString()).put("min", e.minutes).apply { e.avgHr?.let { put("hr", it) } }) }
            o.put("x", ex)
            s.today?.let { t ->
                o.put("t", JSONObject().put("h", JSONArray(t.hourlySteps)).put("s", t.steps).put("ex", t.recordedExerciseMinutes)
                    .apply { t.activeKcal?.let { put("k", it) }; t.distanceKm?.let { put("km", it) }; t.totalKcal?.let { put("tk", it) } })
            }
            s.todayDate?.let { o.put("td", it.toString()) }
            s.lastRefresh?.let { o.put("lr", it.toString()) }
            s.lastFull?.let { o.put("lf", it.toString()) }
            s.lastWearable?.let { o.put("lw", it.toString()) }
            s.changesToken?.let { o.put("tok", it) }
            s.lastBackground?.let { o.put("bg", it.toString()) }
            return o
        }

        fun fromJson(o: JSONObject): HealthSnapshot {
            fun inst(k: String) = if (o.has(k)) Instant.parse(o.getString(k)) else null
            val w = o.optJSONObject("w")
            val wearable = buildMap {
                w?.keys()?.forEach { k ->
                    val m = runCatching { Metric.valueOf(k) }.getOrNull() ?: return@forEach
                    val a = w.getJSONArray(k)
                    put(m, (0 until a.length()).map { i -> val p = a.getJSONArray(i); DailyPoint(LocalDate.parse(p.getString(0)), p.getDouble(1)) })
                }
            }
            val n = o.optJSONArray("n") ?: JSONArray()
            val nights = (0 until n.length()).mapNotNull { i ->
                runCatching {
                    val x = n.getJSONObject(i); val st = x.getJSONArray("st")
                    SleepNight(
                        LocalDate.parse(x.getString("d")), Instant.ofEpochMilli(x.getLong("s")), Instant.ofEpochMilli(x.getLong("e")),
                        (0 until st.length()).map { j -> val a = st.getJSONArray(j); Stage(Instant.ofEpochMilli(a.getLong(0)), Instant.ofEpochMilli(a.getLong(1)), StageType.valueOf(a.getString(2))) },
                        if (x.has("a")) x.getDouble("a") else null, if (x.has("m")) x.getDouble("m") else null,
                    )
                }.getOrNull()
            }
            val xa = o.optJSONArray("x") ?: JSONArray()
            val ex = (0 until xa.length()).map { i -> val x = xa.getJSONObject(i); ExerciseLoad(LocalDate.parse(x.getString("d")), x.getDouble("min"), if (x.has("hr")) x.getDouble("hr") else null) }
            val today = o.optJSONObject("t")?.let { t ->
                val h = t.getJSONArray("h")
                HealthRepo.TodayActivity(
                    (0 until h.length()).map { h.getLong(it) }, t.getLong("s"),
                    if (t.has("k")) t.getDouble("k") else null, if (t.has("km")) t.getDouble("km") else null,
                    if (t.has("tk")) t.getDouble("tk") else null, t.optInt("ex", 0),
                )
            }
            return HealthSnapshot(
                wearable, nights, ex, today, if (o.has("td")) LocalDate.parse(o.getString("td")) else null,
                inst("lr"), inst("lf"), inst("lw"), if (o.has("tok")) o.getString("tok") else null, inst("bg"),
            )
        }
    }
}
