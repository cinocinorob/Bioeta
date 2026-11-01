package it.cinocinorob.bioeta.data

import android.content.Context
import it.cinocinorob.bioeta.science.EcgRecord
import it.cinocinorob.bioeta.science.EcgResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime

/**
 * Archivio locale degli ECG: indice JSON + una copia del file originale (PDF o immagine) per ciascun tracciato,
 * nella memoria privata dell'app. Nel backup JSON i file viaggiano in Base64, così il ripristino li riporta tutti.
 */
class EcgStore(context: Context) {
    private val dir = File(context.filesDir, "ecg").apply { mkdirs() }
    private val index = File(dir, "index.json")

    fun file(r: EcgRecord): File = File(dir, r.id + if (r.kind == "pdf") ".pdf" else ".img")

    fun load(): List<EcgRecord> = runCatching { fromJson(JSONArray(index.readText())) }.getOrDefault(emptyList())

    fun save(list: List<EcgRecord>) { index.writeText(toJson(list).toString()) }

    fun add(r: EcgRecord, bytes: ByteArray, current: List<EcgRecord>): List<EcgRecord> {
        file(r).writeBytes(bytes)
        val out = (current.filter { it.id != r.id } + r).sortedByDescending { it.time }
        save(out); return out
    }

    fun delete(r: EcgRecord, current: List<EcgRecord>): List<EcgRecord> {
        file(r).delete()
        val out = current.filter { it.id != r.id }
        save(out); return out
    }

    /** Per il backup: indice con i file in Base64. */
    fun backupJson(list: List<EcgRecord>): JSONArray {
        val arr = toJson(list)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val f = file(list[i])
            if (f.exists()) o.put("data", android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
        }
        return arr
    }

    /** Ripristino: unisce per id e riscrive i file presenti nel backup. */
    fun restore(arr: JSONArray, current: List<EcgRecord>): List<EcgRecord> {
        val recs = fromJson(arr)
        recs.forEachIndexed { i, r ->
            arr.getJSONObject(i).optString("data", "").takeIf { it.isNotEmpty() }?.let {
                file(r).writeBytes(android.util.Base64.decode(it, android.util.Base64.NO_WRAP))
            }
        }
        val ids = recs.map { it.id }.toSet()
        val out = (current.filter { it.id !in ids } + recs).sortedByDescending { it.time }
        save(out); return out
    }

    companion object {
        fun toJson(list: List<EcgRecord>): JSONArray = JSONArray().apply {
            list.forEach { r ->
                put(JSONObject().put("id", r.id).put("t", r.time.toString()).put("r", r.result.name)
                    .put("hr", r.heartRate ?: -1).put("k", r.kind).put("p", r.parsed).put("n", r.note))
            }
        }

        fun fromJson(arr: JSONArray): List<EcgRecord> = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            runCatching {
                EcgRecord(
                    o.getString("id"), LocalDateTime.parse(o.getString("t")),
                    runCatching { EcgResult.valueOf(o.getString("r")) }.getOrDefault(EcgResult.UNKNOWN),
                    o.optInt("hr", -1).takeIf { it > 0 }, o.optString("k", "pdf"), o.optBoolean("p", false), o.optString("n", ""),
                )
            }.getOrNull()
        }
    }
}
