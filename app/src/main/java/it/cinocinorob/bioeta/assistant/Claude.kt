package it.cinocinorob.bioeta.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client minimale per la Messages API di Anthropic con un ciclo di tool use:
 * il modello può interrogare PubMed (ricerca + abstract) prima di rispondere.
 */
class Claude(private val apiKey: String, private val model: String) {

    sealed class Step {
        data class Searching(val query: String) : Step()
        data class Reading(val pmid: String) : Step()
    }

    data class Answer(
        val text: String,
        /** PMID restituiti dagli strumenti durante la sessione (letti dal modello). */
        val consulted: Map<String, PubMed.Summary>,
        val inputTokens: Int,
        val outputTokens: Int,
    )

    class ApiError(message: String) : Exception(message)

    private val tools = JSONArray().apply {
        put(JSONObject().apply {
            put("name", "pubmed_search")
            put("description", "Cerca articoli su PubMed. Restituisce PMID, titolo, rivista, anno, DOI e tipo di pubblicazione (es. Meta-Analysis, Randomized Controlled Trial, Practice Guideline). Usa query in inglese con termini MeSH e filtri come \"meta-analysis[pt]\", \"systematic review[pt]\", \"randomized controlled trial[pt]\", \"guideline[pt]\".")
            put("input_schema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().put("type", "string").put("description", "Query PubMed in inglese"))
                    put("max_results", JSONObject().put("type", "integer").put("description", "Da 1 a 15, predefinito 8"))
                })
                put("required", JSONArray().put("query"))
            })
        })
        put(JSONObject().apply {
            put("name", "pubmed_abstract")
            put("description", "Restituisce l'abstract completo di un articolo PubMed dato il PMID. Usalo per leggere risultati numerici, popolazione, GRADE e limiti prima di citare un articolo.")
            put("input_schema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().put("pmid", JSONObject().put("type", "string")))
                put("required", JSONArray().put("pmid"))
            })
        })
    }

    fun ask(
        system: String,
        history: List<Pair<String, String>>,
        onStep: (Step) -> Unit,
    ): Answer {
        val messages = JSONArray()
        history.forEach { (role, text) -> messages.put(JSONObject().put("role", role).put("content", text)) }
        val consulted = linkedMapOf<String, PubMed.Summary>()
        var inTok = 0
        var outTok = 0
        val finalText = StringBuilder()

        repeat(MAX_TURNS) { turn ->
            val body = JSONObject().apply {
                put("model", model)
                put("max_tokens", 4096)
                put("system", system)
                put("messages", messages)
                put("tools", tools)
                if (turn == MAX_TURNS - 1) put("tool_choice", JSONObject().put("type", "none"))
            }
            val resp = post(body)
            resp.optJSONObject("usage")?.let {
                inTok += it.optInt("input_tokens"); outTok += it.optInt("output_tokens")
            }
            val content = resp.getJSONArray("content")
            messages.put(JSONObject().put("role", "assistant").put("content", content))
            val stop = resp.optString("stop_reason")

            val results = JSONArray()
            finalText.setLength(0)
            for (k in 0 until content.length()) {
                val b = content.getJSONObject(k)
                when (b.optString("type")) {
                    "text" -> finalText.append(b.optString("text"))
                    "tool_use" -> {
                        val id = b.getString("id")
                        val input = b.optJSONObject("input") ?: JSONObject()
                        val out = runCatching {
                            when (b.getString("name")) {
                                "pubmed_search" -> {
                                    val q = input.optString("query")
                                    onStep(Step.Searching(q))
                                    val list = PubMed.search(q, input.optInt("max_results", 8))
                                    list.forEach { consulted[it.pmid] = it }
                                    if (list.isEmpty()) "Nessun risultato." else list.joinToString("\n") {
                                        "PMID ${it.pmid} | ${it.year} | ${it.journal} | ${it.pubTypes.joinToString("; ")} | ${it.title}" + (it.doi?.let { d -> " | doi:$d" } ?: "")
                                    }
                                }
                                "pubmed_abstract" -> {
                                    val p = input.optString("pmid").filter { it.isDigit() }
                                    onStep(Step.Reading(p))
                                    if (p !in consulted) PubMed.summaries(listOf(p)).forEach { consulted[it.pmid] = it }
                                    PubMed.abstract(p)
                                }
                                else -> "Strumento sconosciuto"
                            }
                        }
                        results.put(JSONObject().apply {
                            put("type", "tool_result")
                            put("tool_use_id", id)
                            put("content", out.getOrElse { "Errore: ${it.message}" })
                            if (out.isFailure) put("is_error", true)
                        })
                    }
                }
            }
            if (stop != "tool_use" || results.length() == 0) {
                return Answer(finalText.toString().trim(), consulted, inTok, outTok)
            }
            messages.put(JSONObject().put("role", "user").put("content", results))
        }
        return Answer(finalText.toString().trim(), consulted, inTok, outTok)
    }

    private fun post(body: JSONObject): JSONObject {
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 20000
        c.readTimeout = 120000
        c.setRequestProperty("content-type", "application/json")
        c.setRequestProperty("x-api-key", apiKey)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        try {
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(txt).getJSONObject("error").getString("message") }.getOrDefault(txt.take(300))
                throw ApiError(
                    when (code) {
                        401 -> "Chiave API non valida."
                        402, 403 -> "Accesso negato o credito esaurito: $msg"
                        429 -> "Troppe richieste o limite di spesa raggiunto: $msg"
                        529, 503 -> "Servizio momentaneamente sovraccarico, riprova tra poco."
                        else -> "Errore API ($code): $msg"
                    }
                )
            }
            return JSONObject(txt)
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val MAX_TURNS = 8
        val MODELS = listOf(
            "claude-sonnet-5-5" to "Claude Sonnet 5.5 (consigliato, ~2 $/M token in ingresso)",
            "claude-opus-5-5" to "Claude Opus 5.5 (più accurato, ~4 $/M token in ingresso)",
        )
    }
}

/** Estrae i PMID citati nel testo e li verifica su PubMed. */
object CitationCheck {
    private val PMID = Regex("""PMID[:\s]*([0-9]{5,9})""", RegexOption.IGNORE_CASE)

    data class Report(
        val verified: List<PubMed.Summary>,
        val notFound: List<String>,
        /** PMID citati ma mai restituiti dagli strumenti durante la ricerca. */
        val notConsulted: List<String>,
    )

    fun cited(text: String): List<String> = PMID.findAll(text).map { it.groupValues[1] }.distinct().toList()

    fun check(text: String, consulted: Map<String, PubMed.Summary>): Report {
        val ids = cited(text)
        val known = ids.mapNotNull { consulted[it] }.toMutableList()
        val unknown = ids.filter { it !in consulted }
        val fetched = if (unknown.isNotEmpty()) runCatching { PubMed.summaries(unknown) }.getOrDefault(emptyList()) else emptyList()
        known += fetched
        val foundIds = known.map { it.pmid }.toSet()
        return Report(
            verified = known.sortedByDescending { it.rank },
            notFound = ids.filter { it !in foundIds },
            notConsulted = unknown.filter { it in foundIds },
        )
    }
}
