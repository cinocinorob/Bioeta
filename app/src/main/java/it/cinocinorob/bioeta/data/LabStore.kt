package it.cinocinorob.bioeta.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

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
    )

    fun saveProfile(p: Profile) {
        prefs.edit()
            .putString("birth", p.birthDate?.toString())
            .putString("sex", p.sex.name)
            .putString("vo2", p.vo2Manual?.toString())
            .apply()
    }

    companion object {
        fun toJson(panels: List<Panel>, profile: Profile?): JSONObject {
            val root = JSONObject()
            root.put("format", "bioeta")
            root.put("version", 1)
            if (profile != null) {
                val p = JSONObject()
                p.put("birthDate", profile.birthDate?.toString() ?: JSONObject.NULL)
                p.put("sex", profile.sex.name)
                profile.vo2Manual?.let { p.put("vo2Manual", it) }
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
            )
        }
    }
}
