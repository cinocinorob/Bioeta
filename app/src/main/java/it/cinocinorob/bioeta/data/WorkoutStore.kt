package it.cinocinorob.bioeta.data

import android.content.Context
import it.cinocinorob.bioeta.science.ACTIVITY_ALGO_VERSION
import it.cinocinorob.bioeta.science.ActivityKind
import it.cinocinorob.bioeta.science.DayActivity
import it.cinocinorob.bioeta.science.LoadQuality
import it.cinocinorob.bioeta.science.Session
import it.cinocinorob.bioeta.science.Zone
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Statistiche cardiache di un allenamento, calcolate dalla FC minuto per minuto. */
data class WorkoutStats(
    val avgHr: Double?,
    val maxHr: Double?,
    val hrCoverage: Double,
    val load: Double?,
    val zoneMinutes: Map<Zone, Int>,
)

enum class SyncState(val label: String) { NEVER("Mai sincronizzato"), OK("Sincronizzato"), PARTIAL("Sincronizzato con errori"), ERROR("Errore"), NO_PERMISSION("Permesso mancante") }

data class SyncStatus(
    val state: SyncState = SyncState.NEVER,
    val lastSync: Instant? = null,
    val windowDays: Int = 0,
    val sessionsInWindow: Int = 0,
    val duplicatesRemoved: Int = 0,
    val origins: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)

/**
 * Archivio locale degli allenamenti importati da Connessione Salute.
 *
 * Sincronizzazione idempotente: per la finestra letta [from, to) il contenuto locale viene SOSTITUITO
 * con quello di Connessione Salute (stessi id → nessun duplicato; sessioni cancellate alla fonte →
 * rimosse). Le sessioni più vecchie della finestra restano invariate. Ripetere la sincronizzazione dà
 * sempre lo stesso risultato.
 */
class WorkoutStore(context: Context) {
    private val file = File(context.filesDir, "workouts.json")
    private val actFile = File(context.cacheDir, "activity_days.json")

    fun load(): Pair<List<Session>, SyncStatus> = runCatching {
        val o = JSONObject(file.readText())
        val arr = o.getJSONArray("sessions")
        val list = (0 until arr.length()).mapNotNull { i -> runCatching { sessionFrom(arr.getJSONObject(i)) }.getOrNull() }
        list to statusFrom(o.optJSONObject("status"))
    }.getOrDefault(emptyList<Session>() to SyncStatus())

    private val rpeFile = File(context.filesDir, "rpe.json")

    /** Voti di fatica percepita per allenamento (id → 0..10; −1 = l'utente ha scelto di non votare). */
    fun loadRpe(): Map<String, Int> = runCatching {
        val o = JSONObject(rpeFile.readText())
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    fun saveRpe(m: Map<String, Int>) {
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k, v) }
        val tmp = File(rpeFile.parentFile, rpeFile.name + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(rpeFile)
    }

    fun save(sessions: List<Session>, status: SyncStatus) {
        val arr = JSONArray(); sessions.forEach { arr.put(sessionTo(it)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONObject().put("sessions", arr).put("status", statusTo(status)).toString())
        tmp.renameTo(file)
    }

    companion object {
        /** Unione idempotente: sostituisce la finestra [from, to) con [fresh]. */
        fun merge(stored: List<Session>, fresh: List<Session>, from: Instant, to: Instant): List<Session> {
            val outside = stored.filter { it.start.isBefore(from) || !it.start.isBefore(to) }
            val freshIds = fresh.map { it.id }.toSet()
            return (outside.filter { it.id !in freshIds } + fresh.distinctBy { it.id }).sortedByDescending { it.start }
        }

        fun sessionTo(s: Session) = JSONObject()
            .put("id", s.id).put("o", s.origin).put("k", s.kind.name).put("t", s.hcType)
            .put("s", s.start.toString()).put("e", s.end.toString())
            .apply { s.title?.let { put("title", it) } }

        fun sessionFrom(o: JSONObject) = Session(
            id = o.getString("id"), origin = o.optString("o", ""),
            kind = runCatching { ActivityKind.valueOf(o.getString("k")) }.getOrDefault(ActivityKind.OTHER),
            title = if (o.has("title")) o.getString("title") else null,
            start = Instant.parse(o.getString("s")), end = Instant.parse(o.getString("e")),
            hcType = o.optInt("t", 0),
        )

        private fun statusTo(s: SyncStatus) = JSONObject()
            .put("state", s.state.name).put("w", s.windowDays).put("n", s.sessionsInWindow).put("dup", s.duplicatesRemoved)
            .put("orig", JSONArray(s.origins)).put("err", JSONArray(s.errors))
            .apply { s.lastSync?.let { put("last", it.toString()) } }

        private fun statusFrom(o: JSONObject?): SyncStatus {
            if (o == null) return SyncStatus()
            fun arr(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            return SyncStatus(
                state = runCatching { SyncState.valueOf(o.getString("state")) }.getOrDefault(SyncState.NEVER),
                lastSync = if (o.has("last")) Instant.parse(o.getString("last")) else null,
                windowDays = o.optInt("w"), sessionsInWindow = o.optInt("n"), duplicatesRemoved = o.optInt("dup"),
                origins = arr("orig"), errors = arr("err"),
            )
        }
    }

    // ---------------- Cache dei giorni di attività (ricalcolabile, non nei backup) ----------------

    data class DayCache(val version: Int, val rest: Double, val age: Double, val days: List<DayActivity>)

    fun loadDays(): DayCache? = runCatching {
        val o = JSONObject(actFile.readText())
        val arr = o.getJSONArray("days")
        DayCache(
            o.getInt("v"), o.getDouble("rest"), o.getDouble("age"),
            (0 until arr.length()).map { i ->
                val d = arr.getJSONObject(i); val z = d.optJSONObject("z")
                DayActivity(
                    LocalDate.parse(d.getString("d")), d.getInt("hr"), d.getInt("mm"), d.getInt("vm"), d.getInt("me"), d.getInt("ve"),
                    d.getDouble("l"), Zone.entries.associateWith { z?.optInt(it.name, 0) ?: 0 },
                    runCatching { LoadQuality.valueOf(d.getString("q")) }.getOrDefault(LoadQuality.UNAVAILABLE),
                    d.optInt("cad", 0),
                )
            },
        )
    }.getOrNull()

    fun saveDays(rest: Double, age: Double, days: List<DayActivity>) {
        val arr = JSONArray()
        days.forEach { a ->
            val z = JSONObject(); a.zoneMinutes.forEach { (k, v) -> z.put(k.name, v) }
            arr.put(JSONObject().put("d", a.date.toString()).put("hr", a.hrMinutes).put("mm", a.moderateMeasured).put("vm", a.vigorousMeasured)
                .put("me", a.moderateEstimated).put("ve", a.vigorousEstimated).put("l", a.load).put("q", a.quality.name).put("z", z).put("cad", a.cadenceMinutes))
        }
        actFile.writeText(JSONObject().put("v", ACTIVITY_ALGO_VERSION).put("rest", rest).put("age", age).put("days", arr).toString())
    }
}
