package it.cinocinorob.bioeta.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID
import it.cinocinorob.bioeta.science.BpReading
import it.cinocinorob.bioeta.science.DayLoad
import it.cinocinorob.bioeta.science.JournalEntry
import it.cinocinorob.bioeta.science.Zone

/** Valore in unità canonica; belowLimit = referto "<x" (x = limite di rilevabilità). */
data class LabValue(val value: Double, val belowLimit: Boolean = false)

data class Panel(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val lab: String = "",
    val note: String = "",
    val values: Map<String, LabValue> = emptyMap(),
)

enum class Sex { M, F }

data class Profile(
    val birthDate: LocalDate? = null,
    val sex: Sex = Sex.M,
    /** VO2max misurato (es. test cardiopolmonare); se presente ha priorità sulla stima dell'orologio. */
    val vo2Manual: Double? = null,
    /** Nome per il saluto (facoltativo, resta sul telefono e non viene inviato a Claude). */
    val name: String = "",
) {
    fun ageAt(date: LocalDate): Double? {
        val b = birthDate ?: return null
        return (date.toEpochDay() - b.toEpochDay()) / 365.2425
    }
}

/** Archivio locale (file JSON nella memoria privata dell'app). */
class LabStore(context: Context) {
    private val file = File(context.filesDir, "panels.json")
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    fun loadPanels(): List<Panel> =
        if (!file.exists()) emptyList()
        else runCatching { panelsFromJson(JSONObject(file.readText())) }.getOrDefault(emptyList())

    fun savePanels(panels: List<Panel>) {
        val tmp = File(file.parentFile, "panels.json.tmp")
        tmp.writeText(toJson(panels, null).toString())
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun loadProfile(): Profile = Profile(
        birthDate = prefs.getString("birth", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        sex = if (prefs.getString("sex", "M") == "F") Sex.F else Sex.M,
        vo2Manual = prefs.getString("vo2", null)?.toDoubleOrNull(),
        name = prefs.getString("name", "") ?: "",
    )

    fun saveProfile(p: Profile) {
        prefs.edit()
            .putString("birth", p.birthDate?.toString())
            .putString("sex", p.sex.name)
            .putString("vo2", p.vo2Manual?.toString())
            .putString("name", p.name)
            .apply()
    }

    // ---------------- Pressione arteriosa ----------------
    private val bpFile = File(context.filesDir, "bp.json")

    fun loadBp(): List<BpReading> =
        if (!bpFile.exists()) emptyList()
        else runCatching { bpFromJson(JSONObject(bpFile.readText())) }.getOrDefault(emptyList())

    fun saveBp(list: List<BpReading>) {
        val root = JSONObject().put("bp", bpToJson(list))
        val tmp = File(bpFile.parentFile, "bp.json.tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(bpFile)) { bpFile.writeText(tmp.readText()); tmp.delete() }
    }

    // ---------------- Diario ----------------
    private val journalFile = File(context.filesDir, "journal.json")

    fun loadJournal(): List<JournalEntry> =
        if (!journalFile.exists()) emptyList()
        else runCatching { journalFromJson(JSONObject(journalFile.readText())) }.getOrDefault(emptyList())

    fun saveJournal(list: List<JournalEntry>) {
        journalFile.writeText(JSONObject().put("journal", journalToJson(list)).toString())
    }

    // ---------------- Cache del carico cardiaco (ricalcolabile, non nei backup) ----------------
    private val loadFile = File(context.cacheDir, "loads.json")

    fun loadLoads(): Pair<Double, List<DayLoad>> = runCatching {
        val o = JSONObject(loadFile.readText())
        val rest = o.getDouble("rest")
        val arr = o.getJSONArray("days")
        rest to (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            val z = d.optJSONObject("z")
            DayLoad(
                LocalDate.parse(d.getString("d")), d.getDouble("l"), d.getInt("m"), d.getInt("v"),
                Zone.entries.associateWith { z?.optInt(it.name, 0) ?: 0 }, d.getInt("c"),
            )
        }
    }.getOrDefault(0.0 to emptyList())

    fun saveLoads(rest: Double, list: List<DayLoad>) {
        val arr = JSONArray()
        list.forEach { l ->
            val z = JSONObject(); l.zoneMinutes.forEach { (k, v) -> z.put(k.name, v) }
            arr.put(JSONObject().put("d", l.date.toString()).put("l", l.load).put("m", l.moderateMin).put("v", l.vigorousMin).put("c", l.coveredMinutes).put("z", z))
        }
        loadFile.writeText(JSONObject().put("rest", rest).put("days", arr).toString())
    }

    companion object {
        fun bpToJson(list: List<BpReading>): JSONArray {
            val arr = JSONArray()
            list.forEach { r ->
                arr.put(JSONObject().apply {
                    put("id", r.id); put("t", r.time.toString()); put("s", r.sys); put("d", r.dia)
                    r.pulse?.let { put("p", it) }
                    if (r.arm.isNotBlank()) put("arm", r.arm)
                    if (r.note.isNotBlank()) put("note", r.note)
                })
            }
            return arr
        }

        fun bpFromJson(root: JSONObject): List<BpReading> {
            val arr = root.optJSONArray("bp") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                runCatching {
                    BpReading(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        time = java.time.LocalDateTime.parse(o.getString("t")),
                        sys = o.getInt("s"), dia = o.getInt("d"),
                        pulse = if (o.has("p")) o.getInt("p") else null,
                        arm = o.optString("arm", ""), note = o.optString("note", ""),
                    )
                }.getOrNull()
            }.sortedBy { it.time }
        }

        fun journalToJson(list: List<JournalEntry>): JSONArray {
            val arr = JSONArray()
            list.forEach { e ->
                arr.put(JSONObject().apply {
                    put("d", e.date.toString())
                    e.mood?.let { put("mood", it) }; e.energy?.let { put("energy", it) }
                    put("tags", JSONArray(e.tags.toList())); put("water", e.water)
                    if (e.note.isNotBlank()) put("note", e.note)
                })
            }
            return arr
        }

        fun journalFromJson(root: JSONObject): List<JournalEntry> {
            val arr = root.optJSONArray("journal") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                runCatching {
                    val t = o.optJSONArray("tags")
                    JournalEntry(
                        date = LocalDate.parse(o.getString("d")),
                        mood = if (o.has("mood")) o.getInt("mood") else null,
                        energy = if (o.has("energy")) o.getInt("energy") else null,
                        tags = if (t == null) emptySet() else (0 until t.length()).map { t.getString(it) }.toSet(),
                        water = o.optInt("water", 0), note = o.optString("note", ""),
                    )
                }.getOrNull()
            }.sortedBy { it.date }
        }

        fun toJson(panels: List<Panel>, profile: Profile?, bp: List<BpReading> = emptyList(), journal: List<JournalEntry> = emptyList()): JSONObject {
            val root = JSONObject()
            if (journal.isNotEmpty()) root.put("journal", journalToJson(journal))
            if (bp.isNotEmpty()) root.put("bp", bpToJson(bp))
            root.put("format", "bioeta")
            root.put("version", 1)
            if (profile != null) {
                val p = JSONObject()
                p.put("birthDate", profile.birthDate?.toString() ?: JSONObject.NULL)
                p.put("sex", profile.sex.name)
                profile.vo2Manual?.let { p.put("vo2Manual", it) }
                if (profile.name.isNotBlank()) p.put("name", profile.name)
                root.put("profile", p)
            }
            val arr = JSONArray()
            for (panel in panels) {
                val o = JSONObject()
                o.put("id", panel.id)
                o.put("date", panel.date.toString())
                o.put("lab", panel.lab)
                o.put("note", panel.note)
                val vals = JSONObject()
                for ((k, v) in panel.values) {
                    val vo = JSONObject()
                    vo.put("v", v.value)
                    if (v.belowLimit) vo.put("lt", true)
                    vals.put(k, vo)
                }
                o.put("values", vals)
                arr.put(o)
            }
            root.put("panels", arr)
            return root
        }

        fun panelsFromJson(root: JSONObject): List<Panel> {
            val arr = root.optJSONArray("panels") ?: return emptyList()
            val out = mutableListOf<Panel>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val vals = mutableMapOf<String, LabValue>()
                val vo = o.optJSONObject("values")
                if (vo != null) {
                    val keys = vo.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val x = vo.getJSONObject(k)
                        vals[k] = LabValue(x.getDouble("v"), x.optBoolean("lt", false))
                    }
                }
                out += Panel(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    date = LocalDate.parse(o.getString("date")),
                    lab = o.optString("lab", ""),
                    note = o.optString("note", ""),
                    values = vals,
                )
            }
            return out.sortedBy { it.date }
        }

        fun profileFromJson(root: JSONObject): Profile? {
            val p = root.optJSONObject("profile") ?: return null
            val b = p.optString("birthDate", "")
            return Profile(
                birthDate = if (b.isBlank() || b == "null") null else runCatching { LocalDate.parse(b) }.getOrNull(),
                sex = if (p.optString("sex", "M") == "F") Sex.F else Sex.M,
                vo2Manual = if (p.has("vo2Manual")) p.optDouble("vo2Manual") else null,
                name = p.optString("name", ""),
            )
        }
    }
}
