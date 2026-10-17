package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.data.SyncState
import it.cinocinorob.bioeta.data.SyncStatus
import it.cinocinorob.bioeta.data.WorkoutStats
import it.cinocinorob.bioeta.data.WorkoutStore
import it.cinocinorob.bioeta.science.*
import androidx.glance.appwidget.updateAll
import it.cinocinorob.bioeta.widget.BioWidget
import it.cinocinorob.bioeta.widget.WidgetData
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
import it.cinocinorob.bioeta.data.DayScores
import it.cinocinorob.bioeta.data.Scoring
import it.cinocinorob.bioeta.data.ScoringInputs
import it.cinocinorob.bioeta.data.HealthCache
import it.cinocinorob.bioeta.data.HealthSnapshot
import it.cinocinorob.bioeta.data.HealthSync
import it.cinocinorob.bioeta.data.DayInputs
import it.cinocinorob.bioeta.data.Ledger
import it.cinocinorob.bioeta.data.LedgerEntry
import it.cinocinorob.bioeta.data.LedgerStore
import it.cinocinorob.bioeta.data.ScoreChange
import it.cinocinorob.bioeta.assistant.CitationCheck
import it.cinocinorob.bioeta.assistant.Claude
import it.cinocinorob.bioeta.assistant.Prompt
import it.cinocinorob.bioeta.science.BloodPressure
import it.cinocinorob.bioeta.science.BpReading
import it.cinocinorob.bioeta.science.BpSummary
import it.cinocinorob.bioeta.science.DailyValue
import it.cinocinorob.bioeta.science.DayLoad
import it.cinocinorob.bioeta.science.Fitness
import it.cinocinorob.bioeta.science.Journal
import it.cinocinorob.bioeta.science.JournalEntry
import it.cinocinorob.bioeta.science.TagEffect
import it.cinocinorob.bioeta.science.TargetLoad
import it.cinocinorob.bioeta.science.ExerciseLoad
import it.cinocinorob.bioeta.science.Insight
import it.cinocinorob.bioeta.science.Readiness
import it.cinocinorob.bioeta.science.ScoreResult
import it.cinocinorob.bioeta.science.SleepNight
import it.cinocinorob.bioeta.science.SleepScore
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
    /** Fotografia salvata dell'ultima lettura: l'app si apre già con i dati (vedi HealthCache). */
    private val healthCache = HealthCache(app)
    private var snapshot: HealthSnapshot = healthCache.load()

    var wearable by mutableStateOf<Map<Metric, List<DailyPoint>>>(snapshot.wearable)
        private set
    var loading by mutableStateOf(false)
        private set
    /** Ultimo dato scritto dall'orologio in Connessione Salute (null = nessuno o non leggibile). */
    var lastWearable by mutableStateOf<java.time.Instant?>(snapshot.lastWearable)
        private set
    var message by mutableStateOf<String?>(null)

    val phenoSeries: List<PhenoPoint> get() = BioAgeEngine.series(panels, profile)

    val hcAvailable: Boolean get() = hcStatus == HealthConnectClient.SDK_AVAILABLE
    val hasAnyHealthPermission: Boolean get() = granted.any { it in health.permissions && it != HealthRepo.HISTORY_PERMISSION }

    private var refreshJob: kotlinx.coroutines.Job? = null

    private var lastFull = 0L

    /** Momento dell'ultima lettura riuscita da Connessione Salute (mostrato nella dashboard). */
    var lastRefresh by mutableStateOf<java.time.Instant?>(snapshot.lastRefresh)
        private set

    /**
     * Lettura in due fasi:
     * 1) veloce (≈1 s): passi e attività di oggi, ultime 3 notti, ultimo dato dell'orologio → la schermata
     *    Oggi si riempie subito;
     * 2) completa: storico 120 giorni, sonno 75 giorni, carico cardiaco 28 giorni (può richiedere decine
     *    di secondi; ripetuta al massimo ogni 10 minuti, o subito se interrotta).
     * Prima la fase veloce arrivava solo alla fine della completa: se l'app veniva chiusa a metà,
     * passi e sonno di oggi non comparivano mai.
     */
    fun refreshHealth(force: Boolean = false) {
        hcStatus = health.sdkStatus()
        if (!hcAvailable) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loading = true
            try {
                granted = health.granted()
                if (hasAnyHealthPermission) {
                    val sync = HealthSync(health)
                    // Lettura incrementale: i cambiamenti dall'ultimo token decidono se serve rileggere tutto
                    val (needFull, changes) = sync.decide(snapshot, force)
                    snapshot = sync.quick(snapshot).let { s -> if (changes != null && !changes.expired && changes.newToken != null) s.copy(changesToken = changes.newToken) else s }
                    applySnapshot(snapshot)
                    // Allenamenti e minuti attivi/carico di oggi già nella fase veloce
                    quietly { syncSessions() }
                    quietly { refreshToday() }
                    quietly { updateLedger() }
                    saveSnapshot()
                    updateWidget()
                    if (needFull) {
                        snapshot = sync.full(snapshot, granted)
                        applySnapshot(snapshot)
                        refreshFitness()
                        quietly { updateLedger() }
                        saveSnapshot()
                        updateWidget()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Lettura da Connessione Salute non riuscita: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    private fun applySnapshot(s: HealthSnapshot) {
        wearable = s.wearable
        sleepNights = s.nights
        exercises = s.exercises
        if (s.todayDate == java.time.LocalDate.now()) todayActivity = s.today
        lastWearable = s.lastWearable
        lastRefresh = s.lastRefresh
    }

    private suspend fun saveSnapshot() {
        val s = snapshot.copy(today = todayActivity ?: snapshot.today, todayDate = if (todayActivity != null) java.time.LocalDate.now() else snapshot.todayDate)
        snapshot = s
        withContext(Dispatchers.IO) { runCatching { healthCache.save(s) } }
    }

    /** Ultimo aggiornamento eseguito in background (null se mai). */
    val lastBackgroundSync: java.time.Instant? get() = healthCache.load().lastBackground

    /** Esegue un passo opzionale: gli errori non bloccano il resto, la cancellazione sì. */
    private inline fun quietly(block: () -> Unit) {
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
    }

    /** Aggiorna il widget della schermata Home con i valori correnti. */
    private suspend fun updateWidget() {
        runCatching {
            val t = today
            WidgetData.save(
                getApplication<Application>(),
                readiness = t.readiness.score, sleep = t.sleep?.score,
                steps = todayActivity?.steps, stepGoal = Fitness.stepGoal(profile.ageAt(java.time.LocalDate.now())),
                load = todayLoad?.load,
            )
            BioWidget().updateAll(getApplication<Application>())
        }
    }

    /** Interrompe le letture quando l'app va in secondo piano (Connessione Salute le rifiuterebbe). */
    fun stopReads() { refreshJob?.cancel(); loading = false }

    fun onPermissionsResult(@Suppress("UNUSED_PARAMETER") result: Set<String>) = refreshHealth(force = true)

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
        val json = LabStore.toJson(panels, profile, bpReadings, journal).toString(2)
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
                        it.write(LabStore.toJson(panels, profile, bpReadings, journal).toString(2).toByteArray())
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

    private var journalImport: List<JournalEntry> = emptyList()

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
                    journalImport = LabStore.journalFromJson(root)
                    Triple(LabStore.panelsFromJson(root), LabStore.profileFromJson(root), LabStore.bpFromJson(root))
                }.getOrNull()
            }
            if (parsed == null) {
                message = "File non valido: qui si importa il backup .json. Per i referti PDF usa Analisi → Importa referto."
                return@launch
            }
            val (imported, prof, bpImported) = parsed
            if (journalImport.isNotEmpty()) {
                val dates = journalImport.map { it.date }.toSet()
                journal = (journal.filter { it.date !in dates } + journalImport).sortedBy { it.date }
                store.saveJournal(journal)
            }
            if (bpImported.isNotEmpty()) {
                val bpIds = bpImported.map { it.id }.toSet()
                bpReadings = (bpReadings.filter { it.id !in bpIds } + bpImported).sortedBy { it.time }
                store.saveBp(bpReadings)
            }
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
                    bp = bpSummary?.takeIf { java.time.Duration.between(bpReadings.last().time, java.time.LocalDateTime.now()).toDays() <= 60 },
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

    /** Contesto pseudonimizzato inviato all'assistente (API o app Claude). */
    fun assistantContext(): String {
        val t = today
        val recovery = buildString {
            append("Punteggi BioEtà di oggi (algoritmo trasparente dell'app, non validato clinicamente):\n")
            append("- Sonno: ${t.sleep?.score ?: "n.d."}; Prontezza: ${t.readiness.score ?: "in calibrazione"}\n")
            (t.sleep?.contributors.orEmpty() + t.readiness.contributors).forEach { c ->
                append("- ${c.label}: ${c.score ?: "n.d."}/100 — ${c.detail}\n")
            }
            t.readiness.flags.forEach { append("- Avviso: $it\n") }
        }
        val bp = bpSummary
        val extra = buildString {
            append(recovery)
            if (bp != null) {
                append("\nPressione arteriosa domiciliare (media ultimi 7 giorni, primo giorno escluso): ${bp.meanSys.toInt()}/${bp.meanDia.toInt()} mmHg su ${bp.n} misure in ${bp.days} giorni")
                bp.morning?.let { append("; mattino ${it.first.toInt()}/${it.second.toInt()}") }
                bp.evening?.let { append("; sera ${it.first.toInt()}/${it.second.toInt()}") }
                bp.trendSys?.let { append("; tendenza sistolica ${String.format(java.util.Locale.ITALY, "%+.1f", it)} mmHg/mese") }
                append("; categoria ESC 2024 domiciliare: ${bp.category.label}\n")
            }
        }
        val fit = buildString {
            val tl = todayLoad
            if (dayLoads.isNotEmpty()) {
                append("\nAttività (dalla frequenza cardiaca): carico cardiaco di oggi ${tl?.load?.toInt() ?: "n.d."} (obiettivo ${targetLoad?.let { "${it.low.toInt()}–${it.high.toInt()}" } ?: "non ancora calcolabile"}); ")
                append("minuti attivi questa settimana ${Fitness.weekActiveMinutes(dayLoads, java.time.LocalDate.now())}/150 (OMS); ")
                append("carico medio 28 giorni ${dayLoads.map { it.load }.average().toInt()}\n")
            }
            todayActivity?.let { append("Passi oggi ${it.steps}\n") }
            val recentJ = journal.filter { it.date.isAfter(java.time.LocalDate.now().minusDays(7)) }
            if (recentJ.isNotEmpty()) append("Diario ultimi 7 giorni: " + recentJ.joinToString("; ") { e -> "${e.date}: umore ${e.mood ?: "-"}, energia ${e.energy ?: "-"}, ${e.tags.joinToString(", ")}" } + "\n")
            tagEffects.forEach { append("Associazione personale: ${it.tag} → prontezza del giorno dopo ${it.diff.toInt()} punti (n=${it.nWith}/${it.nWithout})\n") }
        }
        return Prompt.context(profile, panels, wearable, extra + fit)
    }

    // ---- Modalità gratuita: domanda inviata all'app Claude dell'utente ----
    /** Tema grafico (predefinito: scuro). */
    var themeMode by mutableStateOf(runCatching { it.cinocinorob.bioeta.ui.ThemeMode.valueOf(settings.getString("theme", "DARK") ?: "DARK") }.getOrDefault(it.cinocinorob.bioeta.ui.ThemeMode.DARK))
        private set

    fun selectTheme(m: it.cinocinorob.bioeta.ui.ThemeMode) {
        themeMode = m
        settings.edit().putString("theme", m.name).apply()
    }

    var assistantMode by mutableStateOf(settings.getString("assistant_mode", "free") ?: "free")
        private set
    var pastedReport by mutableStateOf<CitationCheck.Report?>(null)
        private set
    var checkingPaste by mutableStateOf(false)
        private set

    fun selectAssistantMode(m: String) {
        assistantMode = m
        settings.edit().putString("assistant_mode", m).apply()
    }

    fun freePrompt(question: String): String = Prompt.forClaudeApp(assistantContext(), question)

    fun checkPastedAnswer(text: String) {
        if (text.isBlank()) return
        checkingPaste = true
        viewModelScope.launch {
            pastedReport = withContext(Dispatchers.IO) { CitationCheck.checkPasted(text) }
            checkingPaste = false
        }
    }

    fun clearPasted() { pastedReport = null }

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
        val ctx = assistantContext()
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


    // ---------------- Sonno e Prontezza ----------------

    var sleepNights by mutableStateOf<List<SleepNight>>(snapshot.nights)
        private set
    var exercises by mutableStateOf<List<ExerciseLoad>>(snapshot.exercises)
        private set

    val missingRecoveryPermissions: Boolean
        get() = hcAvailable && hasAnyHealthPermission && health.permissions.any { it !in granted && it != HealthRepo.HISTORY_PERMISSION }

    private val scoringInputs: ScoringInputs
        get() = ScoringInputs(wearable, sleepNights, exercises, dayLoads, profile)

    fun sleepFor(date: java.time.LocalDate): Pair<SleepNight, ScoreResult>? = Scoring.sleepFor(date, scoringInputs)

    fun scoresFor(date: java.time.LocalDate): DayScores = Scoring.scoresFor(date, scoringInputs)

    // Cache: ricalcola solo quando cambiano i dati di ingresso
    private var cacheKey: List<Any?>? = null
    private var cacheHistory: List<DayScores> = emptyList()

    /** Ultimi 60 giorni di punteggi, dal più vecchio (per il confronto con la propria storia). */
    val scoreHistory60: List<DayScores>
        get() {
            val key = listOf(sleepNights, exercises, wearable, dayLoads, profile, java.time.LocalDate.now())
            if (key != cacheKey) {
                cacheHistory = Scoring.history(java.time.LocalDate.now(), 60, scoringInputs)
                cacheKey = key
            }
            return cacheHistory
        }

    /** Ultimi 14 giorni, dal più vecchio. */
    val recoveryHistory: List<DayScores> get() = scoreHistory60.takeLast(14)

    /** Rango del sonno di stanotte tra le notti dei 60 giorni precedenti (null con meno di 14 notti). */
    val sleepPercentile: Int?
        get() = today.sleep?.score?.let { s -> Personal.percentile(s, scoreHistory60.dropLast(1).mapNotNull { it.sleep?.score }) }

    val readinessPercentile: Int?
        get() = today.readiness.score?.let { s -> Personal.percentile(s, scoreHistory60.dropLast(1).mapNotNull { it.readiness.score }) }

    val today: DayScores get() = recoveryHistory.last()



    // ---------------- Funzioni aggiuntive (orario sonno, parametri vitali, andamento carico, settimana) ----------------

    val bedtimeGuide: BedtimeGuide?
        get() = Bedtime.guide(sleepNights, java.time.LocalDate.now(), profile.ageAt(java.time.LocalDate.now()))

    // ---------------- Radar malessere e parametri vitali notturni ----------------

    private var radarKey: List<Any?>? = null
    private var radarCache: RadarResult? = null

    /** Segni precoci di malessere dalla notte (vedi science/Radar.kt per metodo e limiti). */
    val radar: RadarResult
        get() {
            val today = java.time.LocalDate.now()
            val key = listOf(wearable, sleepNights, dayLoads, journal, today)
            radarCache?.let { if (key == radarKey) return it }
            fun s(m: Metric) = wearable[m].orEmpty().map { DailyValue(it.date, it.value) }
            val series = linkedMapOf(
                Signals.RHR to s(Metric.RHR),
                Signals.SLEEP_HR to sleepNights.mapNotNull { n -> n.avgHr?.let { DailyValue(n.date, it) } },
                Signals.HRV to s(Metric.HRV),
                Signals.RESP to s(Metric.RESP),
                Signals.SPO2 to s(Metric.SPO2),
                Signals.TEMP to s(Metric.SKIN_TEMP),
            ).filterValues { it.isNotEmpty() }
            val r = IllnessRadar.assess(
                series, today,
                loads = dayLoads.filter { it.coveredMinutes > 0 }.map { DailyValue(it.date, it.load) },
                eveningTags = journalFor(today.minusDays(1)).tags,
            )
            radarKey = key; radarCache = r
            return r
        }

    /** Parametri vitali della notte rispetto al proprio intervallo abituale (stessa valutazione del radar). */
    val vitals: VitalsReport
        get() {
            val r = radar
            val list = r.signals.map { e ->
                val st = when {
                    e.value == null -> VitalState.NO_DATA
                    e.z == null -> VitalState.IN_RANGE
                    e.z >= 2.0 -> VitalState.HIGH
                    e.z <= -2.0 -> VitalState.LOW
                    else -> VitalState.IN_RANGE
                }
                Vital(e.spec.label, e.spec.unit, e.value, e.low, e.high, st, e.spec.decimals)
            }
            val n = r.signals.count { it.outOfRange }
            return VitalsReport(list, n, if (r.level == RadarLevel.CALIBRATING) r.detail else r.detail)
        }

    // ---------------- Registro dei punteggi: ogni cambiamento di un giorno passato ha un motivo ----------------

    private val ledgerStore = LedgerStore(app)
    var scoreChanges by mutableStateOf(ledgerStore.load().changes)
        private set

    private fun dayInputs(d: java.time.LocalDate): DayInputs {
        fun w(m: Metric) = wearable[m].orEmpty().lastOrNull { it.date == d }?.value
        val z = zoneId()
        return DayInputs(
            sleepMin = sleepNights.firstOrNull { it.date == d }?.totalSleepMin?.let { kotlin.math.round(it).toInt() },
            hrv = w(Metric.HRV)?.let { kotlin.math.round(it).toInt() },
            rhr = w(Metric.RHR)?.let { kotlin.math.round(it).toInt() },
            resp10 = w(Metric.RESP)?.let { kotlin.math.round(it * 10).toInt() },
            workouts = displaySessions.count { it.start.atZone(z).toLocalDate() == d },
            load = dayLoads.firstOrNull { it.date == d && it.coveredMinutes > 0 }?.load?.let { kotlin.math.round(it).toInt() },
        )
    }

    private suspend fun updateLedger() {
        val today = java.time.LocalDate.now()
        val now = java.time.Instant.now()
        val fresh = scoreHistory60.filter { it.date.isAfter(today.minusDays(30)) }.map { d ->
            LedgerEntry(d.date, d.sleep?.score, d.readiness.score, dayInputs(d.date), final = d.date.isBefore(today))
        }
        val st = withContext(Dispatchers.IO) { ledgerStore.load() }
        val found = Ledger.diff(st.entries, fresh, today, now)
        val changes = (found + st.changes).filter { it.at.isAfter(now.minus(java.time.Duration.ofDays(14))) }.take(40)
        withContext(Dispatchers.IO) { ledgerStore.save(LedgerStore.State(fresh.associateBy { it.date }, changes)) }
        scoreChanges = changes
    }

    /** Cambiamenti segnalati nelle ultime 24 ore (mostrati in Home). */
    val recentChanges: List<ScoreChange>
        get() = scoreChanges.filter { it.at.isAfter(java.time.Instant.now().minus(java.time.Duration.ofHours(24))) }

    // ---------------- Fatica percepita (session-RPE) ----------------

    var rpe by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    fun setRpe(id: String, value: Int) {
        rpe = rpe + (id to value)
        val snap = rpe
        viewModelScope.launch(Dispatchers.IO) { workoutStore.saveRpe(snap) }
    }

    /** Allenamento concluso nelle ultime 36 ore (≥ 10 min) ancora da votare. */
    val pendingRpe: Session?
        get() {
            val now = java.time.Instant.now()
            return displaySessions.firstOrNull { s ->
                s.end.isBefore(now) && s.end.isAfter(now.minus(java.time.Duration.ofHours(36))) && s.minutes >= 10 && s.id !in rpe
            }
        }

    val rpeSummary: SessionRpe.Summary?
        get() {
            val z = zoneId()
            val rated = displaySessions.mapNotNull { s ->
                rpe[s.id]?.takeIf { it >= 0 }?.let { SessionRpe.Rated(s.start.atZone(z).toLocalDate(), it, s.minutes) }
            }
            return if (rated.isEmpty()) null else SessionRpe.summary(rated, java.time.LocalDate.now(z))
        }

    val loadTrend: LoadTrend get() = LoadTrendCalc.compute(dayLoads, java.time.LocalDate.now())

    val weekStats: List<WeekStat>
        get() {
            val today = java.time.LocalDate.now()
            val h = recoveryHistory
            val thisW = h.filter { it.date.isAfter(today.minusDays(7)) }
            val lastW = h.filter { !it.date.isAfter(today.minusDays(7)) }
            fun nightsIn(a: Long, b: Long) = sleepNights.filter { it.date.isAfter(today.minusDays(b)) && !it.date.isAfter(today.minusDays(a)) }
            fun stepsIn(a: Long, b: Long) = wearable[Metric.STEPS].orEmpty().filter { it.date.isAfter(today.minusDays(b)) && !it.date.isAfter(today.minusDays(a)) }.map { it.value }
            fun loadsIn(a: Long, b: Long) = dayLoads.filter { it.date.isAfter(today.minusDays(b)) && !it.date.isAfter(today.minusDays(a)) }
            return listOf(
                WeekStat("Recupero medio", WeekSummary.avg(thisW.mapNotNull { it.readiness.score?.toDouble() }), WeekSummary.avg(lastW.mapNotNull { it.readiness.score?.toDouble() }), "", 0, true),
                WeekStat("Punteggio sonno", WeekSummary.avg(thisW.mapNotNull { it.sleep?.score?.toDouble() }), WeekSummary.avg(lastW.mapNotNull { it.sleep?.score?.toDouble() }), "", 0, true),
                WeekStat("Sonno per notte", WeekSummary.avg(nightsIn(0, 7).map { it.totalSleepMin / 60 }), WeekSummary.avg(nightsIn(7, 14).map { it.totalSleepMin / 60 }), "h", 1, null),
                WeekStat("Passi al giorno", WeekSummary.avg(stepsIn(1, 8)), WeekSummary.avg(stepsIn(8, 15)), "", 0, true),
                WeekStat("Minuti attivi", loadsIn(0, 7).takeIf { it.isNotEmpty() }?.sumOf { it.activeMinutes }?.toDouble(), loadsIn(7, 14).takeIf { it.isNotEmpty() }?.sumOf { it.activeMinutes }?.toDouble(), "min", 0, true),
            )
        }

    // ---------------- Pressione arteriosa ----------------

    var bpReadings by mutableStateOf(store.loadBp())
        private set

    fun addBp(r: BpReading) {
        bpReadings = (bpReadings.filter { it.id != r.id } + r).sortedBy { it.time }
        store.saveBp(bpReadings)
        autoBackupNow()
    }

    fun deleteBp(id: String) {
        bpReadings = bpReadings.filter { it.id != id }
        store.saveBp(bpReadings)
        autoBackupNow()
    }

    val bpSummary: BpSummary? get() = BloodPressure.summarize(bpReadings)


    // ---------------- Fitness ----------------

    var todayActivity by mutableStateOf<HealthRepo.TodayActivity?>(snapshot.today.takeIf { snapshot.todayDate == java.time.LocalDate.now() })
        private set
    private val workoutStore = WorkoutStore(app)
    init { rpe = workoutStore.loadRpe() }
    private val storedWorkouts = workoutStore.load()

    /** Allenamenti importati (archivio locale idempotente, vedi WorkoutStore). */
    var sessions by mutableStateOf(storedWorkouts.first)
        private set
    var syncStatus by mutableStateOf(storedWorkouts.second)
        private set
    var syncing by mutableStateOf(false)
        private set
    /** Statistiche cardiache per allenamento (id → stats), calcolate dalla FC. */
    var workoutStats by mutableStateOf<Map<String, WorkoutStats>>(emptyMap())
        private set

    /** Allenamenti senza doppioni tra app diverse, dal più recente. */
    val displaySessions: List<Session> get() = ActivityEngine.dedupe(sessions)

    /** Ultimi 28 giorni di attività (minuti attivi misurati/stimati, carico cardiaco), dal più vecchio. */
    private val initialDays = workoutStore.loadDays()?.takeIf { it.version == ACTIVITY_ALGO_VERSION }
    var dayActivities by mutableStateOf(initialDays?.days ?: emptyList())
        private set

    /**
     * FC a riposo di riferimento per carico e minuti attivi, stabilizzata: resta quella usata per lo storico
     * finché la media dei 30 giorni non si sposta di almeno 2 bpm. Così oggi e i giorni in cache sono calcolati
     * con lo stesso riferimento e i valori passati non cambiano a ogni apertura per spostamenti di decimi di bpm.
     */
    private var activityRest: Double? = initialDays?.rest
    private fun stableRest(): Double {
        val cur = restHrRef
        val prev = activityRest
        val r = if (prev != null && kotlin.math.abs(prev - cur) < 2.0) prev else cur
        activityRest = r
        return r
    }
    val dayLoads: List<DayLoad> get() = dayActivities.map { it.toDayLoad() }
    val todayDayActivity: DayActivity? get() = dayActivities.lastOrNull { it.date == java.time.LocalDate.now() }

    var fitnessLoading by mutableStateOf(false)
        private set

    /** FC a riposo di riferimento per la riserva cardiaca: media 30 gg, altrimenti FC notturna, altrimenti 60. */
    val restHrRef: Double
        get() {
            val today = java.time.LocalDate.now()
            val rhr = wearable[Metric.RHR].orEmpty().filter { it.date.isAfter(today.minusDays(30)) }.map { it.value }
            if (rhr.size >= 5) return rhr.average()
            val night = sleepNights.mapNotNull { it.minHr }.takeLast(14)
            return if (night.size >= 5) night.average() else 60.0
        }

    /**
     * Sincronizzazione degli allenamenti (idempotente): legge 90 giorni (30 senza permesso storico)
     * e sostituisce in archivio il contenuto della stessa finestra.
     */
    suspend fun syncSessions() {
        syncing = true
        try {
            val r = health.readSessions(90)
            if (!r.permitted) {
                syncStatus = syncStatus.copy(state = SyncState.NO_PERMISSION, lastSync = java.time.Instant.now(), errors = listOf("Permesso «Allenamenti» non concesso in Connessione Salute."))
            } else if (r.error != null) {
                // In caso di errore l'archivio NON viene toccato: niente perdita di allenamenti già importati
                syncStatus = syncStatus.copy(state = SyncState.ERROR, lastSync = java.time.Instant.now(), errors = listOf(r.error))
            } else {
                sessions = WorkoutStore.merge(sessions, r.sessions, r.from, r.to)
                val inWindow = sessions.filter { !it.start.isBefore(r.from) }
                val deduped = ActivityEngine.dedupe(inWindow)
                syncStatus = SyncStatus(
                    state = SyncState.OK, lastSync = java.time.Instant.now(), windowDays = r.days,
                    sessionsInWindow = deduped.size, duplicatesRemoved = inWindow.size - deduped.size,
                    origins = inWindow.map { health.originLabel(it.origin) }.distinct(),
                )
            }
            withContext(Dispatchers.IO) { workoutStore.save(sessions, syncStatus) }
        } finally {
            syncing = false
        }
    }

    /** Sincronizzazione manuale completa (allenamenti + tutti i dati). */
    fun syncNow() = refreshHealth(force = true)

    private fun zoneId() = java.time.ZoneId.systemDefault()

    /** FC minuto per minuto di un giorno locale (con 10 min di margine per interpolare a cavallo della mezzanotte). */
    private suspend fun hrMinutesFor(d: java.time.LocalDate): Pair<Map<Long, Double>, Boolean> {
        val zone = zoneId()
        val from = d.atStartOfDay(zone).toInstant().minusSeconds(600)
        val to = d.plusDays(1).atStartOfDay(zone).toInstant().plusSeconds(600)
        val t0 = java.time.Instant.now()
        val samples = health.heartRate(from, to)
        val failed = health.errors["Frequenza cardiaca"]?.at?.isBefore(t0) == false
        return withContext(Dispatchers.Default) { ActivityEngine.minuteSeries(samples) } to failed
    }

    private fun computeDay(d: java.time.LocalDate, hr: Map<Long, Double>, rest: Double): DayActivity {
        val age = profile.ageAt(java.time.LocalDate.now()) ?: 40.0
        return ActivityEngine.day(d, zoneId(), hr, displaySessions, rest, age, profile.sex == Sex.M)
    }

    /** Solo oggi: per mostrare subito minuti attivi e carico di oggi nella fase veloce. */
    private suspend fun refreshToday() {
        val today = java.time.LocalDate.now(zoneId())
        val (hr, _) = hrMinutesFor(today)
        val rest = stableRest()
        val day = withContext(Dispatchers.Default) { computeDay(today, hr, rest) }
        dayActivities = (dayActivities.filter { it.date != today } + day).sortedBy { it.date }
    }

    private suspend fun refreshFitness() {
        todayActivity = runCatching { health.todayActivity() }.getOrNull() ?: todayActivity
        val age = profile.ageAt(java.time.LocalDate.now()) ?: 40.0
        fitnessLoading = true
        try {
            val rest = stableRest()
            val cache = withContext(Dispatchers.IO) { workoutStore.loadDays() }
            val reuse = if (cache != null && cache.version == ACTIVITY_ALGO_VERSION && kotlin.math.abs(cache.rest - rest) < 2.0 && kotlin.math.abs(cache.age - age) < 1.0)
                cache.days.associateBy { it.date } else emptyMap()
            val today = java.time.LocalDate.now(zoneId())
            val out = mutableListOf<DayActivity>()
            val failed = mutableSetOf<java.time.LocalDate>()
            val sessionDays = displaySessions.flatMap { s ->
                val a = s.start.atZone(zoneId()).toLocalDate(); val b = s.end.atZone(zoneId()).toLocalDate()
                generateSequence(a) { it.plusDays(1) }.takeWhile { !it.isAfter(b) }.toList()
            }.toSet()
            for (k in 27 downTo 0) {
                val d = today.minusDays(k.toLong())
                val c = reuse[d]
                // Cache solo per giorni conclusi e completi; i giorni con allenamenti si ricalcolano sempre
                // (un allenamento sincronizzato in ritardo deve comparire nei minuti attivi).
                if (c != null && k >= 2 && c.hrMinutes >= ActivityEngine.COMPLETE_COVERAGE_MIN && d !in sessionDays) { out += c; continue }
                val (hr, err) = hrMinutesFor(d)
                if (err) failed += d
                out += withContext(Dispatchers.Default) { computeDay(d, hr, rest) }
            }
            dayActivities = out
            withContext(Dispatchers.IO) { workoutStore.saveDays(rest, age, out.filter { it.date !in failed }) }
            refreshWorkoutStats()
        } finally {
            fitnessLoading = false
        }
    }

    /** FC media/massima, copertura, zone e carico per gli allenamenti degli ultimi 14 giorni. */
    private suspend fun refreshWorkoutStats() {
        val age = profile.ageAt(java.time.LocalDate.now()) ?: 40.0
        val male = profile.sex == Sex.M
        val rest = stableRest()
        val maxHr = Fitness.hrMax(age)
        val since = java.time.Instant.now().minus(java.time.Duration.ofDays(14))
        val out = HashMap<String, WorkoutStats>()
        for (s in displaySessions.filter { it.start.isAfter(since) }) {
            val samples = health.heartRate(s.start.minusSeconds(300), s.end.plusSeconds(300))
            val series = withContext(Dispatchers.Default) { ActivityEngine.minuteSeries(samples) }
            val a = Math.floorDiv(s.start.epochSecond, 60L); val b = Math.floorDiv(s.end.epochSecond + 59, 60L)
            val inSession = series.filterKeys { it in a until b }
            val zones = Zone.entries.associateWith { 0 }.toMutableMap()
            var load = 0.0
            inSession.values.forEach { bpm ->
                val f = Fitness.hrr(bpm, rest, maxHr)
                val z = Fitness.zoneOf(f); zones[z] = zones.getValue(z) + 1
                if (f >= Zone.LIGHT.lo) load += Fitness.trimpMinute(f, male)
            }
            out[s.id] = WorkoutStats(
                avgHr = inSession.values.takeIf { it.isNotEmpty() }?.average(),
                maxHr = samples.filter { it.time >= s.start && it.time <= s.end }.maxOfOrNull { it.bpm },
                hrCoverage = if (b > a) inSession.size.toDouble() / (b - a) else 0.0,
                load = if (inSession.isEmpty()) null else load, zoneMinutes = zones,
            )
        }
        workoutStats = out
    }

    val todayLoad: DayLoad? get() = dayLoads.lastOrNull { it.date == java.time.LocalDate.now() }

    val targetLoad: TargetLoad?
        get() = Fitness.target(dayLoads, java.time.LocalDate.now(), today.readiness.score, profile.sex == Sex.M)

    // ---------------- Diario ----------------

    var journal by mutableStateOf(store.loadJournal())
        private set

    fun journalFor(d: java.time.LocalDate): JournalEntry = journal.firstOrNull { it.date == d } ?: JournalEntry(d)

    fun saveJournal(e: JournalEntry) {
        journal = (journal.filter { it.date != e.date } + e).sortedBy { it.date }
        store.saveJournal(journal)
        autoBackupNow()
    }

    val tagEffects: List<TagEffect>
        get() = Journal.tagEffects(
            journal.filter { it.date.isAfter(java.time.LocalDate.now().minusDays(120)) },
            nextDayScore = { d -> recoveryHistoryLong.firstOrNull { it.first == d }?.second },
        )

    /** Prontezza degli ultimi 120 giorni (per il diario); calcolata su richiesta e memorizzata. */
    private var longKey: Any? = null
    private var longCache: List<Pair<java.time.LocalDate, Int?>> = emptyList()
    private val recoveryHistoryLong: List<Pair<java.time.LocalDate, Int?>>
        get() {
            val key = listOf(sleepNights, wearable, exercises, profile, journal.size)
            if (key != longKey) {
                val firstDay = journal.minOfOrNull { it.date } ?: java.time.LocalDate.now()
                val days = java.time.temporal.ChronoUnit.DAYS.between(firstDay, java.time.LocalDate.now()).coerceIn(0, 120)
                longCache = (days downTo 0).map { k ->
                    val d = java.time.LocalDate.now().minusDays(k)
                    d to scoresFor(d).readiness.score
                }
                longKey = key
            }
            return longCache
        }


    // ---------------- Diagnostica Connessione Salute ----------------
    var diagnostics by mutableStateOf<List<HealthRepo.DiagRow>?>(null)
        private set
    var diagnosing by mutableStateOf(false)
        private set

    fun runDiagnostics() {
        viewModelScope.launch {
            diagnosing = true
            diagnostics = runCatching { health.diagnose() }.getOrNull()
            diagnosing = false
        }
    }

    companion object {
        const val DAYS_WINDOW = 120L
        const val RECOVERY_WINDOW = 75L
    }
}
