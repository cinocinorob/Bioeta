package it.cinocinorob.bioeta.data

import android.content.Context
import it.cinocinorob.bioeta.science.FrozenWeek
import it.cinocinorob.bioeta.science.GameLevel
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

    fun save(weeks: Map<LocalDate, FrozenWeek>, badges: Map<String, LocalDate>, best: Int) {
        prefs.edit().putString("weeks", weeksToJson(weeks).toString()).putString("badges", badgesToJson(badges).toString())
            .putInt("best_streak", best).apply()
    }

    fun toJson(): JSONObject = JSONObject().put("weeks", weeksToJson(loadWeeks())).put("badges", badgesToJson(loadBadges())).put("best_streak", bestStreak())

    /** Ripristino da backup: unione (le settimane e i traguardi già presenti restano). */
    fun restore(o: JSONObject) {
        val w = weeksFromJson(o.optJSONArray("weeks") ?: JSONArray()) + loadWeeks()
        val b = badgesFromJson(o.optJSONObject("badges") ?: JSONObject()).toMutableMap()
        loadBadges().forEach { (k, v) -> b[k] = minOf(v, b[k] ?: v) }
        save(w, b, maxOf(bestStreak(), o.optInt("best_streak", 0)))
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

        fun badgesToJson(m: Map<String, LocalDate>) = JSONObject().apply { m.forEach { (k, v) -> put(k, v.toString()) } }

        fun badgesFromJson(o: JSONObject): Map<String, LocalDate> =
            o.keys().asSequence().mapNotNull { k -> runCatching { k to LocalDate.parse(o.getString(k)) }.getOrNull() }.toMap()
    }
}
