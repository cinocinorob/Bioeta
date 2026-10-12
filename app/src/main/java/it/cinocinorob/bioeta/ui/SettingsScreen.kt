package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.data.Sex
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    modifier: Modifier,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    val ctx = LocalContext.current
    val p = vm.profile
    var birthText by remember(p.birthDate) { mutableStateOf(p.birthDate?.itDate() ?: "") }
    var vo2Text by remember(p.vo2Manual) { mutableStateOf(p.vo2Manual?.f(1) ?: "") }
    val parsedBirth = runCatching { LocalDate.parse(birthText.trim(), DateTimeFormatter.ofPattern("d/M/uuuu")) }.getOrNull()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("Profilo") {
                OutlinedTextField(
                    value = birthText,
                    onValueChange = { birthText = it },
                    label = { Text("Data di nascita (gg/mm/aaaa)") },
                    isError = birthText.isNotBlank() && parsedBirth == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Sesso biologico (per riferimenti VO₂max ed eGFR)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = p.sex == Sex.M, onClick = { vm.updateProfile(p.copy(sex = Sex.M)) }, label = { Text("Maschio") })
                    FilterChip(selected = p.sex == Sex.F, onClick = { vm.updateProfile(p.copy(sex = Sex.F)) }, label = { Text("Femmina") })
                }
                OutlinedTextField(
                    value = vo2Text,
                    onValueChange = { vo2Text = it },
                    label = { Text("VO₂max misurato (facoltativo, mL/kg/min)") },
                    supportingText = { Text("Da test cardiopolmonare. Se vuoto si usa la stima dell'orologio.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        vm.updateProfile(p.copy(birthDate = parsedBirth, vo2Manual = parseNum(vo2Text)))
                        vm.message = "Profilo salvato."
                    },
                    enabled = (birthText.isBlank() || parsedBirth != null) && (vo2Text.isBlank() || parseNum(vo2Text) != null),
                ) { Text("Salva profilo") }
            }
        }
        item {
            SectionCard("Archivio") {
                Text("${vm.panels.size} referti salvati solo su questo telefono.")
                Muted("L'app non ha accesso a Internet e il backup automatico Android è disattivato: esporta periodicamente un file di backup (es. su Drive).")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExport) { Text("Esporta backup") }
                    OutlinedButton(onClick = onImport) { Text("Importa") }
                }
            }
        }
        if (vm.hcAvailable) item {
            SectionCard("Connessione Salute") {
                OutlinedButton(onClick = {
                    runCatching { ctx.startActivity(android.content.Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
                }) { Text("Apri Connessione Salute") }
            }
        }
        item {
            SectionCard("Informazioni") {
                OutlinedButton(onClick = onOpenInfo) { Text("Metodo, fonti, limiti e privacy") }
                Muted("BioEtà è uno strumento di automonitoraggio, non un dispositivo medico: non fornisce diagnosi.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Metodo e privacy") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            H("Privacy")
            P("I dati di Connessione Salute sono letti in sola lettura e usati esclusivamente per i calcoli mostrati nell'app. Referti e risultati restano nella memoria privata dell'app; l'app non dichiara il permesso Internet e quindi non può trasmetterli. Puoi revocare l'accesso in qualsiasi momento dalle impostazioni di Connessione Salute.")

            H("1. PhenoAge (età biologica ematica)")
            P("Modello di Levine et al. (Aging 2018; doi:10.18632/aging.101414) e Liu et al. (PLoS Med 2018; doi:10.1371/journal.pmed.1002718). Addestrato con regressione di Cox penalizzata su NHANES III (9.926 adulti, ~23 anni di follow-up): da 42 biomarcatori sono stati selezionati 9 marcatori più l'età. Il punteggio stima il rischio di morte a 10 anni (modello di Gompertz) e lo converte nell'età alla quale quel rischio sarebbe medio.")
            P("Validazione (NHANES IV, n = 6.209): correlazione con l'età anagrafica r = 0,94; a parità di età, +1 anno di PhenoAge è associato a HR 1,09 per mortalità per tutte le cause, 1,10 cardiovascolare, 1,07 oncologica, 1,20 da diabete.")
            P("Limiti: (a) campione statunitense, calibrazione su popolazioni europee non garantita; (b) i singoli marcatori hanno variabilità biologica intra-individuale non trascurabile, specie PCR e globuli bianchi: un singolo prelievo durante un'infezione può spostare il risultato di diversi anni; (c) differenze di metodo tra laboratori (es. albumina colorimetrica vs elettroforesi) introducono scostamenti sistematici; (d) è un indice di rischio associativo: migliorare un marcatore non implica di per sé una riduzione causale del rischio.")

            H("2. Età cardiorespiratoria")
            P("Età alla quale il 50° percentile del VO₂max di riferimento (FRIEND-I, Peterman et al., Mayo Clin Proc 2020; doi:10.1016/j.mayocp.2019.06.013) eguaglia il tuo VO₂max, con interpolazione esponenziale tra 25 e 75 anni. Il VO₂max è tra i predittori più robusti di mortalità: nella coorte HUNT ogni MET in più era associato a −15% di mortalità per tutte le cause negli uomini e −8% nelle donne sotto i 60 anni (Nes et al., Med Sci Sports Exerc 2014; doi:10.1249/MSS.0000000000000219).")
            P("Limiti: il VO₂max dello smartwatch è una stima indiretta (frequenza cardiaca e velocità durante la corsa) con errore individuale rilevante; l'interpolazione tra classi d'età è un'approssimazione.")

            H("3. Wearable")
            P("FC a riposo, HRV (RMSSD), sonno, passi, SpO₂ e frequenza respiratoria sono confrontati con la tua baseline personale: media e deviazione standard giornaliera dei giorni 8–67 precedenti, contro la media degli ultimi 7 giorni. La DS giornaliera sovrastima l'incertezza di una media settimanale; per contro l'autocorrelazione dei dati riduce l'informazione indipendente. Le soglie (±0,5 e ±1,5 DS) sono convenzioni descrittive, non soglie cliniche.")
            P("Non viene calcolata un'\"età\" da questi dati: le età proposte da anelli e orologi commerciali usano algoritmi proprietari privi di coefficienti pubblicati e di validazione indipendente sugli esiti.")

            H("4. eGFR")
            P("CKD-EPI 2021 senza coefficiente razziale (Inker et al., N Engl J Med 2021; doi:10.1056/NEJMoa2102953), calcolato dalla creatinina e dall'età al prelievo.")

            Text("BioEtà non è un dispositivo medico. Valori anomali vanno discussi con il medico.", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun H(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@Composable
private fun P(t: String) = Text(t, style = MaterialTheme.typography.bodyMedium)
