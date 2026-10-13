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
                    val rec = health.readRecovery(RECOVERY_WINDOW)
                    sleepNights = rec.nights
                    exercises = rec.exercises
                    refreshFitness()
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
                append("\nAttività (dalla frequenza cardiaca): carico cardiaco di oggi ${tl?.load?.toInt() ?: "n.d."} (obiettivo ${targetLoad.low.toInt()}–${targetLoad.high.toInt()}); ")
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

    var sleepNights by mutableStateOf<List<SleepNight>>(emptyList())
        private set
    var exercises by mutableStateOf<List<ExerciseLoad>>(emptyList())
        private set

    val missingRecoveryPermissions: Boolean
        get() = hcAvailable && hasAnyHealthPermission && health.permissions.any { it !in granted && it != HealthRepo.HISTORY_PERMISSION }

    data class DayScores(val date: java.time.LocalDate, val night: SleepNight?, val sleep: ScoreResult?, val readiness: ScoreResult)

    fun sleepFor(date: java.time.LocalDate): Pair<SleepNight, ScoreResult>? {
        val night = sleepNights.firstOrNull { it.date == date } ?: return null
        val hist = sleepNights.filter { it.date.isBefore(date) && !it.date.isBefore(date.minusDays(60)) }
        return night to SleepScore.compute(night, hist, profile.ageAt(date))
    }

    fun scoresFor(date: java.time.LocalDate): DayScores {
        val s = sleepFor(date)
        fun series(m: Metric) = wearable[m].orEmpty().filter { !it.date.isAfter(date) }.map { DailyValue(it.date, it.value) }
        val rhr = series(Metric.RHR).ifEmpty {
            // Se manca la FC a riposo, usa la FC media notturna come approssimazione
            sleepNights.filter { !it.date.isAfter(date) }.mapNotNull { n -> n.avgHr?.let { DailyValue(n.date, it) } }
        }
        val r = Readiness.compute(
            Readiness.Inputs(
                date = date,
                age = profile.ageAt(date),
                male = profile.sex == Sex.M,
                hrv = series(Metric.HRV),
                rhr = rhr,
                resp = series(Metric.RESP),
                nights = sleepNights.filter { !it.date.isAfter(date) },
                lastNightScore = s?.second?.score,
                exercises = exercises.filter { !it.date.isAfter(date) },
            )
        )
        return DayScores(date, s?.first, s?.second, r)
    }

    // Cache: ricalcola solo quando cambiano i dati di ingresso
    private var cacheKey: List<Any?>? = null
    private var cacheHistory: List<DayScores> = emptyList()

    /** Ultimi 14 giorni, dal più vecchio. */
    val recoveryHistory: List<DayScores>
        get() {
            val key = listOf(sleepNights, exercises, wearable, profile, java.time.LocalDate.now())
            if (key != cacheKey) {
                cacheHistory = (13 downTo 0).map { scoresFor(java.time.LocalDate.now().minusDays(it.toLong())) }
                cacheKey = key
            }
            return cacheHistory
        }

    val today: DayScores get() = recoveryHistory.last()


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

    var todayActivity by mutableStateOf<HealthRepo.TodayActivity?>(null)
        private set
    var dayLoads by mutableStateOf<List<DayLoad>>(emptyList())
        private set
    var workouts by mutableStateOf<List<HealthRepo.Workout>>(emptyList())
        private set
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

    private suspend fun refreshFitness() {
        val age = profile.ageAt(java.time.LocalDate.now()) ?: return
        val male = profile.sex == Sex.M
        fitnessLoading = true
        try {
            todayActivity = runCatching { health.todayActivity() }.getOrNull()
            val rest = restHrRef
            val (cachedRest, cached) = withContext(Dispatchers.IO) { store.loadLoads() }
            val reuse = if (kotlin.math.abs(cachedRest - rest) < 2.0) cached.associateBy { it.date } else emptyMap()
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.LocalDate.now(zone)
            val out = mutableListOf<DayLoad>()
            for (k in 27 downTo 0) {
                val d = today.minusDays(k.toLong())
                val c = reuse[d]
                if (c != null && k >= 2) { out += c; continue }
                val samples = runCatching {
                    health.heartRate(d.atStartOfDay(zone).toInstant(), d.plusDays(1).atStartOfDay(zone).toInstant())
                }.getOrDefault(emptyList())
                out += withContext(Dispatchers.Default) { Fitness.dayLoad(d, samples, rest, age, male) }
            }
            dayLoads = out
            withContext(Dispatchers.IO) { store.saveLoads(rest, out) }
            workouts = runCatching { health.workouts(14) }.getOrDefault(emptyList())
        } finally {
            fitnessLoading = false
        }
    }

    val todayLoad: DayLoad? get() = dayLoads.lastOrNull { it.date == java.time.LocalDate.now() }

    val targetLoad: TargetLoad
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
        get() = Journal.tagEffects(journal.filter { it.date.isAfter(java.time.LocalDate.now().minusDays(120)) }) { d ->
            recoveryHistoryLong.firstOrNull { it.first == d }?.second
        }

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

    companion object {
        const val DAYS_WINDOW = 120L
        const val RECOVERY_WINDOW = 75L
    }
}
