package it.cinocinorob.bioeta.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

/**
 * Registro dei punteggi già mostrati, per spiegare ogni cambiamento.
 *
 * Un giorno passato non dovrebbe cambiare. Se cambia (dati sincronizzati in ritardo dall'orologio,
 * allenamento aggiunto, notte modificata), il registro lo rileva e ne indica il motivo confrontando i dati
 * d'ingresso di quel giorno. Oggi il Recupero si aggiorna durante la giornata con il carico: non è registrato
 * come cambiamento.
 */
data class DayInputs(
    val sleepMin: Int?,
    val hrv: Int?,
    val rhr: Int?,
    val resp10: Int?,
    val workouts: Int,
    val load: Int?,
)

/** [final] = il giorno era già concluso quando il punteggio è stato registrato. */
data class LedgerEntry(val date: LocalDate, val sleep: Int?, val readiness: Int?, val inputs: DayInputs, val final: Boolean = true)

data class ScoreChange(val date: LocalDate, val what: String, val from: Int?, val to: Int?, val reason: String, val at: Instant)

object Ledger {
    /** Cambia quando cambiano gli algoritmi: il registro viene ricostruito senza segnalare differenze. */
    const val VERSION = 1
    /** Differenze di 1 punto sono arrotondamenti: non si segnalano. */
    private const val MIN_DELTA = 2

    fun diff(old: Map<LocalDate, LedgerEntry>, fresh: List<LedgerEntry>, today: LocalDate, now: Instant): List<ScoreChange> {
        val out = mutableListOf<ScoreChange>()
        for (n in fresh) {
            val o = old[n.date] ?: continue
            val why by lazy { reason(o.inputs, n.inputs) }
            // Sonno: la notte di oggi è già conclusa, quindi vale anche per oggi
            if (changed(o.sleep, n.sleep)) out += ScoreChange(n.date, "Sonno", o.sleep, n.sleep, why, now)
            // Recupero: solo per giorni già conclusi quando li avevi visti (durante la giornata cambia col carico)
            if (o.final && n.date.isBefore(today) && changed(o.readiness, n.readiness)) out += ScoreChange(n.date, "Recupero", o.readiness, n.readiness, why, now)
        }
        return out
    }

    private fun changed(a: Int?, b: Int?): Boolean = a != null && (b == null || abs(a - b) >= MIN_DELTA)

    fun reason(a: DayInputs, b: DayInputs): String {
        val parts = mutableListOf<String>()
        fun hm(m: Int?) = m?.let { "${it / 60} h ${"%02d".format(it % 60)}" } ?: "—"
        if (a.sleepMin != b.sleepMin) parts += when {
            a.sleepMin == null -> "è arrivata la notte (${hm(b.sleepMin)})"
            b.sleepMin == null -> "la notte non è più presente in Connessione Salute"
            else -> "sonno ${hm(a.sleepMin)} → ${hm(b.sleepMin)}"
        }
        if (a.hrv != b.hrv) parts += if (a.hrv == null) "è arrivata l'HRV (${b.hrv} ms)" else "HRV ${a.hrv ?: "—"} → ${b.hrv ?: "—"} ms"
        if (a.rhr != b.rhr) parts += if (a.rhr == null) "è arrivata la FC a riposo (${b.rhr} bpm)" else "FC a riposo ${a.rhr ?: "—"} → ${b.rhr ?: "—"} bpm"
        if (a.resp10 != b.resp10) parts += "respirazione ${a.resp10?.let { "%.1f".format(it / 10.0) } ?: "—"} → ${b.resp10?.let { "%.1f".format(it / 10.0) } ?: "—"}"
        if (a.workouts != b.workouts) parts += if (b.workouts > a.workouts) "allenamento aggiunto (${a.workouts} → ${b.workouts})" else "allenamento rimosso (${a.workouts} → ${b.workouts})"
        // Carico assente e carico zero sono equivalenti; differenze sotto 5 sono arrotondamenti
        if (abs((a.load ?: 0) - (b.load ?: 0)) >= 5) parts += "carico cardiaco ${a.load ?: 0} → ${b.load ?: 0}"
        return if (parts.isEmpty()) "sono cambiati i dati dei giorni precedenti, usati come riferimento" else parts.joinToString("; ")
    }
}

class LedgerStore(context: Context) {
    private val file = File(context.filesDir, "score_ledger.json")

    data class State(val entries: Map<LocalDate, LedgerEntry>, val changes: List<ScoreChange>)

    fun load(): State = runCatching {
        val o = JSONObject(file.readText())
        if (o.optInt("v") != Ledger.VERSION) return State(emptyMap(), emptyList())
        val e = o.getJSONArray("e")
        val entries = (0 until e.length()).map { i ->
            val j = e.getJSONObject(i)
            val inp = j.getJSONObject("i")
            fun JSONObject.int(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
            LedgerEntry(
                LocalDate.parse(j.getString("d")), j.int("s"), j.int("r"),
                DayInputs(inp.int("sl"), inp.int("hrv"), inp.int("rhr"), inp.int("rs"), inp.optInt("w", 0), inp.int("l")),
                j.optBoolean("f", true),
            )
        }.associateBy { it.date }
        val c = o.optJSONArray("c") ?: JSONArray()
        val changes = (0 until c.length()).map { i ->
            val j = c.getJSONObject(i)
            ScoreChange(
                LocalDate.parse(j.getString("d")), j.getString("w"),
                if (j.isNull("f")) null else j.getInt("f"), if (j.isNull("t")) null else j.getInt("t"),
                j.getString("r"), Instant.parse(j.getString("a")),
            )
        }
        State(entries, changes)
    }.getOrDefault(State(emptyMap(), emptyList()))

    fun save(s: State) {
        val e = JSONArray()
        s.entries.values.sortedBy { it.date }.forEach { x ->
            e.put(JSONObject().put("d", x.date.toString()).put("s", x.sleep ?: JSONObject.NULL).put("r", x.readiness ?: JSONObject.NULL).put("f", x.final)
                .put("i", JSONObject().put("sl", x.inputs.sleepMin ?: JSONObject.NULL).put("hrv", x.inputs.hrv ?: JSONObject.NULL)
                    .put("rhr", x.inputs.rhr ?: JSONObject.NULL).put("rs", x.inputs.resp10 ?: JSONObject.NULL)
                    .put("w", x.inputs.workouts).put("l", x.inputs.load ?: JSONObject.NULL)))
        }
        val c = JSONArray()
        s.changes.forEach { x ->
            c.put(JSONObject().put("d", x.date.toString()).put("w", x.what).put("f", x.from ?: JSONObject.NULL)
                .put("t", x.to ?: JSONObject.NULL).put("r", x.reason).put("a", x.at.toString()))
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONObject().put("v", Ledger.VERSION).put("e", e).put("c", c).toString())
        tmp.renameTo(file)
    }
}
