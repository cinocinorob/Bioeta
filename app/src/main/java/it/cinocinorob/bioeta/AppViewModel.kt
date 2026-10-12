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
    }

    fun deletePanel(id: String) {
        panels = panels.filter { it.id != id }
        store.savePanels(panels)
    }

    fun updateProfile(p: Profile) {
        profile = p
        store.saveProfile(p)
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
            message = "Importati ${imported.size} prelievi."
        }
    }

    companion object {
        const val DAYS_WINDOW = 120L
    }
}
