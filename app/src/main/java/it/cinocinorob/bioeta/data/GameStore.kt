package it.cinocinorob.bioeta.data

import android.content.Context
import it.cinocinorob.bioeta.science.FrozenWeek
import it.cinocinorob.bioeta.science.GameLevel
import it.cinocinorob.bioeta.science.GamePause
import it.cinocinorob.bioeta.science.PauseReason
import it.cinocinorob.bioeta.science.PersonalRecord
import it.cinocinorob.bioeta.science.WeightGoal
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Stato persistente di obiettivi e traguardi: settimane chiuse (non più ricalcolate), data di sblocco dei traguardi,
 * serie migliore. Lo storico dei passi letto da Connessione Salute copre 120 giorni: senza questo archivio livelli e
 * traguardi cambierebbero quando i giorni più vecchi escono dalla finestra.
 */
class GameStore(context: Context) {
    private val prefs = context.getSharedPreferences("game_v1", Context.MODE_PRIVATE)

    fun loadWeeks(): Map<LocalDate, FrozenWeek> = runCatching { weeksFromJson(JSONArray(prefs.getString("weeks", "[]"))) }.getOrDefault(emptyMap())
    fun loadBadges(): Map<String, LocalDate> = runCatching { badgesFromJson(JSONObject(prefs.getString("badges", "{}")!!)) }.getOrDefault(emptyMap())
    fun bestStreak(): Int = prefs.getInt("best_streak", 0)

    /** Pause scelte dall'utente (anche quelle finite: i giorni passati in pausa restano tali). */
    fun loadPauses(): List<GamePause> = runCatching { pausesFromJson(JSONArray(prefs.getString("pauses", "[]"))) }.getOrDefault(emptyList())
    fun savePauses(l: List<GamePause>) { prefs.edit().putString("pauses", pausesToJson(l).toString()).apply() }

    fun loadRecords(): Map<String, PersonalRecord> = runCatching {
        val a = JSONArray(prefs.getString("records", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            PersonalRecord(o.getString("id"), o.getString("l"), o.getDouble("v"), o.getString("u"), LocalDate.parse(o.getString("d")), o.optInt("k", 0))
        }.associateBy { it.id }
    }.getOrDefault(emptyMap())

    fun saveRecords(m: Map<String, PersonalRecord>) {
        val a = JSONArray()
        m.values.forEach { r -> a.put(JSONObject().put("id", r.id).put("l", r.label).put("v", r.value).put("u", r.unit).put("d", r.date.toString()).put("k", r.decimals)) }
        prefs.edit().putString("records", a.toString()).apply()
    }

    fun loadWeightGoal(): WeightGoal? = runCatching {
        val o = JSONObject(prefs.getString("weight_goal", null) ?: return null)
        WeightGoal(o.getDouble("t"), o.getDouble("s"), LocalDate.parse(o.getString("d")))
    }.getOrNull()

    fun saveWeightGoal(g: WeightGoal?) {
        prefs.edit().apply {
            if (g == null) remove("weight_goal") else putString("weight_goal", JSONObject().put("t", g.target).put("s", g.startWeight).put("d", g.startDate.toString()).toString())
        }.apply()
    }

    fun save(weeks: Map<LocalDate, FrozenWeek>, badges: Map<String, LocalDate>, best: Int) {
        prefs.edit().putString("weeks", weeksToJson(weeks).toString()).putString("badges", badgesToJson(badges).toString())
            .putInt("best_streak", best).apply()
    }

    fun toJson(): JSONObject = JSONObject().put("weeks", weeksToJson(loadWeeks())).put("badges", badgesToJson(loadBadges())).put("best_streak", bestStreak())
        .put("pauses", pausesToJson(loadPauses()))

    /** Ripristino da backup: unione (le settimane e i traguardi già presenti restano). */
    fun restore(o: JSONObject) {
        val w = weeksFromJson(o.optJSONArray("weeks") ?: JSONArray()) + loadWeeks()
        val b = badgesFromJson(o.optJSONObject("badges") ?: JSONObject()).toMutableMap()
        loadBadges().forEach { (k, v) -> b[k] = minOf(v, b[k] ?: v) }
        save(w, b, maxOf(bestStreak(), o.optInt("best_streak", 0)))
        o.optJSONArray("pauses")?.let { a -> savePauses((loadPauses() + pausesFromJson(a)).distinct()) }
    }

    companion object {
        fun weeksToJson(m: Map<LocalDate, FrozenWeek>) = JSONArray().apply {
            m.values.sortedBy { it.start }.forEach { w ->
                put(JSONObject().put("s", w.start.toString()).put("p", w.points).put("m", w.met).put("x", w.missed).put("r", w.rated).put("l", w.levelAfter.name))
            }
        }

        fun weeksFromJson(a: JSONArray): Map<LocalDate, FrozenWeek> = (0 until a.length()).mapNotNull { i ->
            runCatching {
                val o = a.getJSONObject(i)
                FrozenWeek(LocalDate.parse(o.getString("s")), o.getInt("p"), o.optInt("m"), o.optInt("x"), o.optBoolean("r", true), GameLevel.valueOf(o.getString("l")))
            }.getOrNull()
        }.associateBy { it.start }

        fun pausesToJson(l: List<GamePause>) = JSONArray().apply {
            l.forEach { p -> put(JSONObject().put("r", p.reason.name).put("f", p.from.toString()).put("t", p.to?.toString() ?: "")) }
        }

        fun pausesFromJson(a: JSONArray): List<GamePause> = (0 until a.length()).mapNotNull { i ->
            runCatching {
                val o = a.getJSONObject(i)
                GamePause(PauseReason.valueOf(o.getString("r")), LocalDate.parse(o.getString("f")), o.optString("t").takeIf { it.isNotEmpty() }?.let(LocalDate::parse))
            }.getOrNull()
        }

        fun badgesToJson(m: Map<String, LocalDate>) = JSONObject().apply { m.forEach { (k, v) -> put(k, v.toString()) } }

        fun badgesFromJson(o: JSONObject): Map<String, LocalDate> =
            o.keys().asSequence().mapNotNull { k -> runCatching { k to LocalDate.parse(o.getString(k)) }.getOrNull() }.toMap()
    }
}
