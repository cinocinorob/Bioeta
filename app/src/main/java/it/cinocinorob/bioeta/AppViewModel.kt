package it.cinocinorob.bioeta

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.cinocinorob.bioeta.data.BioAgeEngine
import it.cinocinorob.bioeta.data.Analytes
import it.cinocinorob.bioeta.data.AutoBackup
import it.cinocinorob.bioeta.data.Sex
import it.cinocinorob.bioeta.assistant.CitationCheck
import it.cinocinorob.bioeta.assistant.Claude
import it.cinocinorob.bioeta.assistant.Prompt
import it.cinocinorob.bioeta.science.Insight
import it.cinocinorob.bioeta.science.InsightEngine
import it.cinocinorob.bioeta.science.InsightInput
import it.cinocinorob.bioeta.data.LabStore
import it.cinocinorob.bioeta.data.LabValue
import it.cinocinorob.bioeta.data.ReportParser
import it.cinocinorob.bioeta.data.ReportText
import it.cinocinorob.bioeta.data.Panel
import it.cinocinorob.bioeta.data.PhenoPoint
import it.cinocinorob.bioeta.data.Profile
import it.cinocinorob.bioeta.health.DailyPoint
import it.cinocinorob.bioeta.health.HealthRepo
import it.cinocinorob.bioeta.health.Metric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LabStore(app)
    val health = HealthRepo(app)

    var panels by mutableStateOf(store.loadPanels())
        private set
    var profile by mutableStateOf(store.loadProfile())
        private set
    var hcStatus by mutableIntStateOf(health.sdkStatus())
        private set
    var granted by mutableStateOf<Set<String>>(emptySet())
        private set
    var wearable by mutableStateOf<Map<Metric, List<DailyPoint>>>(emptyMap())
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    val phenoSeries: List<PhenoPoint> get() = BioAgeEngine.series(panels, profile)

    val hcAvailable: Boolean get() = hcStatus == HealthConnectClient.SDK_AVAILABLE
    val hasAnyHealthPermission: Boolean get() = granted.any { it in health.permissions && it != HealthRepo.HISTORY_PERMISSION }

    fun refreshHealth() {
        hcStatus = health.sdkStatus()
        if (!hcAvailable) return
        viewModelScope.launch {
            loading = true
            try {
                granted = health.granted()
                if (hasAnyHealthPermission) {
                    wearable = health.readAll(DAYS_WINDOW)
                }
            } catch (e: Exception) {
                message = "Lettura da Connessione Salute non riuscita: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun onPermissionsResult(@Suppress("UNUSED_PARAMETER") result: Set<String>) = refreshHealth()

    fun savePanel(p: Panel) {
        panels = (panels.filter { it.id != p.id } + p).sortedBy { it.date }
        store.savePanels(panels)
        autoBackupNow()
    }

    fun deletePanel(id: String) {
        panels = panels.filter { it.id != id }
        store.savePanels(panels)
        autoBackupNow()
    }

    fun updateProfile(p: Profile) {
        profile = p
        store.saveProfile(p)
        autoBackupNow()
    }

    // ---------------- Backup automatico ----------------

    val backup = AutoBackup(app)
    var backupFolder by mutableStateOf(backup.folderName())
        private set
    var lastBackup by mutableStateOf(backup.lastBackup)
        private set
    var backupError by mutableStateOf(backup.lastError)
        private set

    fun setBackupFolder(uri: Uri) {
        backup.setFolder(uri)
        backupFolder = backup.folderName()
        autoBackupNow(announce = true)
    }

    fun disableBackupFolder() {
        backup.clearFolder()
        backupFolder = null
        backupError = null
    }

    fun autoBackupNow(announce: Boolean = false) {
        if (backup.folderUri == null) return
        val json = LabStore.toJson(panels, profile).toString(2)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { backup.write(json) }
            lastBackup = backup.lastBackup
            backupError = backup.lastError
            if (announce) message = if (ok) "Backup salvato in \"${backupFolder ?: "cartella"}\"." else "Backup non riuscito: ${backupError}"
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(LabStore.toJson(panels, profile).toString(2).toByteArray())
                    }
                }.isSuccess
            }
            message = if (ok) "Backup esportato (${panels.size} prelievi)." else "Esportazione non riuscita."
        }
    }

    // ---------------- Import referti (PDF / foto) ----------------

    data class ImportDraft(val panel: Panel, val notes: List<String>, val foundKeys: Set<String>)

    var importDraft by mutableStateOf<ImportDraft?>(null)
    var passwordFor by mutableStateOf<Uri?>(null)
    var passwordWasWrong by mutableStateOf(false)
        private set
    var importing by mutableStateOf(false)
        private set

    fun importReport(uri: Uri, password: String? = null) {
        viewModelScope.launch {
            importing = true
            try {
                val ex = withContext(Dispatchers.IO) {
                    ReportText.extract(getApplication<Application>(), uri, password)
                }
                val r = ReportParser.parse(ex.text)
                passwordFor = null
                if (r.values.isEmpty()) {
                    message = if (ex.text.isBlank()) "Nessun testo leggibile nel file."
                    else "Nessun valore riconosciuto. Puoi inserirli a mano con \"Inserisci\"."
                    return@launch
                }
                val notes = buildList {
                    add("${r.values.size} valori riconosciuti${if (ex.usedOcr) " tramite OCR (verifica con più attenzione)" else ""}.")
                    if (r.date == null) add("Data del prelievo non trovata: impostata a oggi, correggila.")
                    val unknownUnit = r.values.values.filter { !it.unitRecognized }
                        .mapNotNull { Analytes.get(it.key)?.name }
                    if (unknownUnit.isNotEmpty()) add("Unità non indicata nel referto per: ${unknownUnit.joinToString(", ")} (assunta quella standard).")
                    addAll(r.notes)
                }
                importDraft = ImportDraft(
                    panel = Panel(
                        date = r.date ?: java.time.LocalDate.now(),
                        lab = r.lab ?: "",
                        note = "Importato da referto",
                        values = r.values.mapValues { LabValue(it.value.value, it.value.belowLimit) },
                    ),
                    notes = notes,
                    foundKeys = r.values.keys,
                )
            } catch (e: ReportText.PasswordRequired) {
                passwordWasWrong = e.wrongPassword
                passwordFor = uri
            } catch (e: Exception) {
                message = "Impossibile leggere il referto: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                importing = false
            }
        }
    }

    fun importFrom(uri: Uri) {
        // Se l'utente sceglie un referto (PDF/immagine) invece di un backup, lo indirizza all'import referti
        val type = getApplication<Application>().contentResolver.getType(uri) ?: ""
        if (type == "application/pdf" || type.startsWith("image/")) {
            importReport(uri)
            return
        }
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .bufferedReader().use { it.readText() }
                    val root = JSONObject(text)
                    LabStore.panelsFromJson(root) to LabStore.profileFromJson(root)
                }.getOrNull()
            }
            if (parsed == null) {
                message = "File non valido: qui si importa il backup .json. Per i referti PDF usa Analisi → Importa referto."
                return@launch
            }
            val (imported, prof) = parsed
            // Unione per id: i prelievi importati sostituiscono quelli con lo stesso id
            val ids = imported.map { it.id }.toSet()
            panels = (panels.filter { it.id !in ids } + imported).sortedBy { it.date }
            store.savePanels(panels)
            if (prof != null && profile.birthDate == null) updateProfile(prof)
            autoBackupNow()
            message = "Ripristinati ${imported.size} prelievi."
        }
    }


    // ---------------- Insight basati su evidenze ----------------

    val insights: List<Insight>
        get() {
            val today = java.time.LocalDate.now()
            val cutoff = today.minusMonths(18)
            val recentPanels = panels.filter { !it.date.isBefore(cutoff) }.sortedByDescending { it.date }
            fun latest(k: String): LabValue? = recentPanels.firstNotNullOfOrNull { it.values[k] }
            val egfr = recentPanels.firstNotNullOfOrNull { BioAgeEngine.egfr(it, profile) }
            fun rec(m: Metric): Double? = wearable[m]?.takeIf { it.isNotEmpty() }?.let { HealthRepo.summarize(m, it).recent }
            val vo2 = profile.vo2Manual ?: wearable[Metric.VO2]?.filter { it.date.isAfter(today.minusDays(30)) }
                ?.map { it.value }?.takeIf { it.isNotEmpty() }?.average()
            val last = phenoSeries.lastOrNull()?.takeIf { !it.panel.date.isBefore(cutoff) }
            return InsightEngine.build(
                InsightInput(
                    age = profile.ageAt(today),
                    male = profile.sex == Sex.M,
                    ldl = latest("ldl")?.value,
                    hdl = latest("hdl")?.value,
                    tg = latest("tg")?.value,
                    glucose = latest(Analytes.GLUCOSE)?.value,
                    hba1c = latest("hba1c")?.value,
                    crp = latest(Analytes.CRP)?.value,
                    crpBelowLimit = latest(Analytes.CRP)?.belowLimit == true,
                    egfr = egfr,
                    vitD = latest("vitd")?.value,
                    homocys = latest("homocys")?.value,
                    lpa = latest("lpa")?.value,
                    rhr7d = rec(Metric.RHR),
                    sleep7d = rec(Metric.SLEEP),
                    steps7d = rec(Metric.STEPS),
                    vo2 = vo2,
                    phenoDelta = last?.let { it.result.phenoAge - it.age },
                )
            )
        }

    // ---------------- Assistente (Claude + PubMed) ----------------

    data class ChatMsg(
        val fromUser: Boolean,
        val text: String,
        val citations: CitationCheck.Report? = null,
        val cost: String? = null,
        val isError: Boolean = false,
    )

    private val secrets = app.getSharedPreferences("secrets", android.content.Context.MODE_PRIVATE)
    private val settings = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    var apiKey by mutableStateOf(secrets.getString("api_key", "") ?: "")
        private set
    var model by mutableStateOf(settings.getString("model", Claude.MODELS.first().first) ?: Claude.MODELS.first().first)
        private set
    var disclaimerAccepted by mutableStateOf(settings.getBoolean("disclaimer_v1", false))
        private set
    var chat by mutableStateOf<List<ChatMsg>>(emptyList())
        private set
    var thinking by mutableStateOf(false)
        private set
    var thinkingStep by mutableStateOf<String?>(null)
        private set

    fun saveApiKey(k: String) {
        apiKey = k.trim()
        secrets.edit().putString("api_key", apiKey).apply()
    }

    fun selectModel(m: String) {
        model = m
        settings.edit().putString("model", m).apply()
    }

    fun acceptDisclaimer() {
        disclaimerAccepted = true
        settings.edit().putBoolean("disclaimer_v1", true).apply()
    }

    fun clearChat() { chat = emptyList() }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || thinking || apiKey.isBlank()) return
        val ctx = Prompt.context(profile, panels, wearable)
        // Solo coppie domanda/risposta complete, per mantenere l'alternanza user/assistant
        val prior = chat.zipWithNext().filter { (u, a) -> u.fromUser && !a.fromUser && !a.isError }
            .flatMap { listOf(it.first, it.second) }
        chat = chat + ChatMsg(true, q)
        thinking = true
        thinkingStep = "Analisi della domanda…"
        viewModelScope.launch {
            try {
                val history = buildList {
                    // Il contesto con i dati viene anteposto alla prima domanda della conversazione
                    prior.forEachIndexed { i, m ->
                        val t = if (i == 0 && m.fromUser) ctx + "\nDOMANDA: " + m.text else m.text
                        add((if (m.fromUser) "user" else "assistant") to t)
                    }
                    add("user" to (if (prior.isEmpty()) ctx + "\nDOMANDA: " + q else q))
                }
                val answer = withContext(Dispatchers.IO) {
                    Claude(apiKey, model).ask(Prompt.SYSTEM, history) { step ->
                        thinkingStep = when (step) {
                            is Claude.Step.Searching -> "Ricerca su PubMed: ${step.query}"
                            is Claude.Step.Reading -> "Lettura abstract PMID ${step.pmid}"
                        }
                    }
                }
                thinkingStep = "Verifica delle citazioni su PubMed…"
                val report = withContext(Dispatchers.IO) { CitationCheck.check(answer.text, answer.consulted) }
                val (pin, pout) = if (model.contains("opus")) 4.0 to 20.0 else 2.0 to 10.0
                val usd = answer.inputTokens / 1e6 * pin + answer.outputTokens / 1e6 * pout
                chat = chat + ChatMsg(
                    false, answer.text, report,
                    cost = String.format(java.util.Locale.ITALY, "%,d token · ≈ %.3f $", answer.inputTokens + answer.outputTokens, usd),
                )
            } catch (e: Exception) {
                chat = chat + ChatMsg(false, e.message ?: "Errore di rete", isError = true)
            } finally {
                thinking = false
                thinkingStep = null
            }
        }
    }

    companion object {
        const val DAYS_WINDOW = 120L
    }
}
