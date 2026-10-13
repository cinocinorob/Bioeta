package it.cinocinorob.bioeta.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Accesso a PubMed tramite le E-utilities pubbliche dell'NCBI (nessuna chiave richiesta). */
object PubMed {
    private const val BASE = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/"
    private const val TOOL = "&tool=bioeta&email=bioeta.app@example.invalid"

    data class Summary(
        val pmid: String,
        val title: String,
        val journal: String,
        val year: String,
        val pubTypes: List<String>,
        val doi: String?,
        val firstAuthor: String?,
    ) {
        /** Livello di evidenza dichiarato da PubMed (tipo di pubblicazione). */
        val evidenceLabel: String
            get() = when {
                pubTypes.any { it.contains("Practice Guideline", true) || it == "Guideline" } -> "Linea guida"
                pubTypes.any { it.contains("Meta-Analysis", true) } -> "Meta-analisi"
                pubTypes.any { it.contains("Systematic Review", true) } -> "Revisione sistematica"
                pubTypes.any { it.contains("Randomized Controlled Trial", true) } -> "RCT"
                pubTypes.any { it.contains("Consensus", true) } -> "Consensus"
                pubTypes.any { it.contains("Observational", true) } -> "Osservazionale"
                pubTypes.any { it.contains("Review", true) } -> "Revisione narrativa"
                else -> "Studio primario"
            }
        val rank: Int
            get() = when (evidenceLabel) {
                "Linea guida", "Meta-analisi", "Revisione sistematica" -> 3
                "RCT", "Consensus" -> 2
                else -> 1
            }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "BioEta/1.0 (Android)")
        try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) error("PubMed HTTP $code")
            return body
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun search(query: String, max: Int = 8): List<Summary> {
        val term = enc(query)
        val js = JSONObject(get("${BASE}esearch.fcgi?db=pubmed&retmode=json&sort=relevance&retmax=${max.coerceIn(1, 15)}&term=$term$TOOL"))
        val ids = js.getJSONObject("esearchresult").getJSONArray("idlist")
        val list = (0 until ids.length()).map { ids.getString(it) }
        return summaries(list)
    }

    fun summaries(pmids: List<String>): List<Summary> {
        if (pmids.isEmpty()) return emptyList()
        val js = JSONObject(get("${BASE}esummary.fcgi?db=pubmed&retmode=json&id=${pmids.joinToString(",")}$TOOL"))
        val res = js.optJSONObject("result") ?: return emptyList()
        return pmids.mapNotNull { id ->
            val o = res.optJSONObject(id) ?: return@mapNotNull null
            if (o.has("error")) return@mapNotNull null
            val types = o.optJSONArray("pubtype") ?: JSONArray()
            val ids = o.optJSONArray("articleids") ?: JSONArray()
            var doi: String? = null
            for (k in 0 until ids.length()) {
                val a = ids.getJSONObject(k)
                if (a.optString("idtype") == "doi") doi = a.optString("value")
            }
            val authors = o.optJSONArray("authors")
            Summary(
                pmid = id,
                title = o.optString("title"),
                journal = o.optString("source"),
                year = o.optString("pubdate").take(4),
                pubTypes = (0 until types.length()).map { types.getString(it) },
                doi = doi,
                firstAuthor = authors?.optJSONObject(0)?.optString("name"),
            )
        }
    }

    /** PMID corrispondente a un DOI, se indicizzato in PubMed. */
    fun pmidForDoi(doi: String): String? {
        val js = JSONObject(get("${BASE}esearch.fcgi?db=pubmed&retmode=json&retmax=1&term=${enc("$doi[doi]")}$TOOL"))
        val ids = js.getJSONObject("esearchresult").getJSONArray("idlist")
        return if (ids.length() > 0) ids.getString(0) else null
    }

    fun abstract(pmid: String): String {
        val txt = get("${BASE}efetch.fcgi?db=pubmed&rettype=abstract&retmode=text&id=${enc(pmid)}$TOOL")
        return txt.trim().take(6000)
    }
}
