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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.data.Sex
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    modifier: Modifier,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onPickBackupFolder: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val p = vm.profile
    var nameText by remember(p.name) { mutableStateOf(p.name) }
    var birthText by remember(p.birthDate) { mutableStateOf(p.birthDate?.itDate() ?: "") }
    var vo2Text by remember(p.vo2Manual) { mutableStateOf(p.vo2Manual?.f(1) ?: "") }
    val parsedBirth = runCatching { LocalDate.parse(birthText.trim(), DateTimeFormatter.ofPattern("d/M/uuuu")) }.getOrNull()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = pal.field, unfocusedContainerColor = pal.field,
        unfocusedBorderColor = Color.Transparent, focusedBorderColor = Tint.blue,
    )

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { LargeHeader("Profilo") }
        item {
            HealthCard("Dati personali", BioIcons.Person, Tint.body) {
                OutlinedTextField(
                    value = nameText, onValueChange = { nameText = it.take(30) },
                    label = { Text("Come ti chiami? (per il saluto)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors, shape = RoundedCornerShape(10.dp),
                )
                OutlinedTextField(
                    value = birthText, onValueChange = { birthText = it },
                    label = { Text("Data di nascita (gg/mm/aaaa)") },
                    isError = birthText.isNotBlank() && parsedBirth == null,
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors, shape = RoundedCornerShape(10.dp),
                )
                Caption("Sesso biologico (riferimenti VO₂max ed eGFR)")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = p.sex == Sex.M, onClick = { vm.updateProfile(p.copy(sex = Sex.M)) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Maschio") }
                    SegmentedButton(
                        selected = p.sex == Sex.F, onClick = { vm.updateProfile(p.copy(sex = Sex.F)) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Femmina") }
                }
                OutlinedTextField(
                    value = vo2Text, onValueChange = { vo2Text = it },
                    label = { Text("VO₂max misurato (mL/kg/min, facoltativo)") },
                    supportingText = { Text("Da test cardiopolmonare. Se vuoto si usa la stima dell'orologio.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors, shape = RoundedCornerShape(10.dp),
                )
                Button(
                    onClick = {
                        vm.updateProfile(p.copy(birthDate = parsedBirth, vo2Manual = parseNum(vo2Text), name = nameText.trim()))
                        vm.message = "Profilo salvato."
                    },
                    enabled = (birthText.isBlank() || parsedBirth != null) && (vo2Text.isBlank() || parseNum(vo2Text) != null),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                ) { Text("Salva profilo", fontWeight = FontWeight.SemiBold) }
            }
        }
        item {
            HealthCard("Backup automatico", BioIcons.Shield, if (vm.backupFolder != null) Tint.good else Tint.warn) {
                if (vm.backupFolder != null) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Pill("Attivo", Tint.good)
                        Text("  cartella «${vm.backupFolder}»", fontSize = 14.sp, color = pal.text)
                    }
                    Caption(vm.lastBackup?.let {
                        "Ultimo backup: " + java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale.ITALIAN)
                            .format(it.atZone(java.time.ZoneId.systemDefault()))
                    } ?: "Nessun backup eseguito ancora.")
                    vm.backupError?.let { Caption("⚠ $it", Tint.bad) }
                    Caption("Dopo ogni modifica viene aggiornato «BioEta-backup.json» e conservata una copia al giorno (ultime 14).")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { vm.autoBackupNow(announce = true) }, modifier = Modifier.weight(1f)) { Text("Esegui ora") }
                        FilledTonalButton(onClick = onPickBackupFolder, modifier = Modifier.weight(1f)) { Text("Cambia cartella") }
                    }
                    androidx.compose.material3.TextButton(onClick = { vm.disableBackupFolder() }) { Text("Disattiva", color = Tint.bad) }
                } else {
                    Body("Scegli una cartella (es. Documenti/BioEta): l'app vi salverà automaticamente una copia dei dati dopo ogni modifica.")
                    Button(onClick = onPickBackupFolder, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Scegli cartella") }
                }
                Caption("In più, se il backup del telefono su Google è attivo (Impostazioni → Sistema → Backup), Android salva anche i dati dell'app nel tuo account. La chiave API di Claude è esclusa da tutti i backup.")
            }
        }
        item {
            HealthCard("Esporta e ripristina", BioIcons.Doc, Tint.blue) {
                Body("${vm.panels.size} referti salvati su questo telefono.")
                Caption("«Esporta» crea un file .json dove vuoi (anche su Google Drive). «Ripristina» importa un backup: i referti vengono uniti a quelli presenti.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Esporta") }
                    FilledTonalButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Ripristina") }
                }
            }
        }
        item {
            HealthCard("Assistente Claude", BioIcons.Chat, Tint.sleep) {
                if (vm.apiKey.isNotBlank()) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Pill("Chiave salvata", Tint.good)
                        Text("  …${vm.apiKey.takeLast(4)}", fontSize = 14.sp, color = pal.text2)
                    }
                    androidx.compose.material3.TextButton(onClick = { vm.saveApiKey("") }) { Text("Rimuovi chiave", color = Tint.bad) }
                } else Caption("Nessuna chiave API: configurala dalla scheda Claude.")
            }
        }
        if (vm.apiKey.isNotBlank()) item { ModelPicker(vm) }
        if (vm.hcAvailable) item {
            HealthCard("Connessione Salute", BioIcons.Heart, Tint.heart,
                onClick = { runCatching { ctx.startActivity(android.content.Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) } }) {
                Caption("Gestisci permessi e app collegate.")
            }
        }
        item {
            HealthCard("Metodo, fonti, limiti e privacy", BioIcons.Info, Tint.blue, onClick = onOpenInfo) {
                Caption("BioEtà è uno strumento di automonitoraggio, non un dispositivo medico: non fornisce diagnosi.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(onBack: () -> Unit) {
    val p = LocalPalette.current
    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { IconButton(onClick = onBack) { Icon(BioIcons.Back, "Indietro", tint = Tint.blue) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LargeHeader("Metodo e privacy")
            HealthCard("Privacy", BioIcons.Info, Tint.blue) {
                Body("I dati di Connessione Salute sono letti in sola lettura. Referti e risultati restano nella memoria privata dell'app; i PDF e le foto sono letti sul telefono (OCR locale, telemetria disattivata). La rete è usata solo dall'assistente: quando invii una domanda, età, sesso, valori degli ultimi 4 referti e medie dello smartwatch (senza nome) vanno ad Anthropic, e le ricerche su PubMed contengono solo termini scientifici. L'accesso si revoca in qualsiasi momento da Connessione Salute.")
            }
            HealthCard("1 · PhenoAge", BioIcons.Sparkle, Tint.bio) {
                Body("Levine et al., Aging 2018 (doi:10.18632/aging.101414); Liu et al., PLoS Med 2018 (doi:10.1371/journal.pmed.1002718). Regressione di Cox penalizzata su NHANES III (9.926 adulti, ~23 anni di follow-up): da 42 biomarcatori ne sono stati selezionati 9 più l'età. Il punteggio stima il rischio di morte a 10 anni (modello di Gompertz) e lo converte nell'età alla quale quel rischio sarebbe medio.")
                Body("Validazione NHANES IV (n = 6.209): r = 0,94 con l'età anagrafica; a parità di età, +1 anno di PhenoAge → HR 1,09 mortalità totale, 1,10 cardiovascolare, 1,07 oncologica, 1,20 da diabete.")
                Caption("Limiti: campione USA, calibrazione europea non garantita; variabilità biologica di PCR e globuli bianchi (un'infezione può spostare il risultato di anni); differenze di metodo tra laboratori (es. albumina colorimetrica vs elettroforesi); indice associativo, non causale.")
            }
            HealthCard("2 · Età cardiorespiratoria", BioIcons.Gauge, Tint.vo2) {
                Body("Età alla quale il 50° percentile di riferimento del VO₂max (FRIEND-I, Peterman et al., Mayo Clin Proc 2020; doi:10.1016/j.mayocp.2019.06.013) eguaglia il tuo, con interpolazione esponenziale tra 25 e 75 anni. Nella coorte HUNT ogni MET in più era associato a −15% di mortalità negli uomini e −8% nelle donne sotto i 60 anni (Nes et al., Med Sci Sports Exerc 2014; doi:10.1249/MSS.0000000000000219).")
                Caption("Limiti: il VO₂max dello smartwatch è una stima indiretta con errore individuale rilevante; l'interpolazione tra classi d'età è un'approssimazione.")
            }
            HealthCard("3 · Sensori", BioIcons.Pulse, Tint.heart) {
                Body("Media degli ultimi 7 giorni confrontata con media e DS giornaliera dei giorni 8–67 precedenti. Le soglie ±0,5 e ±1,5 DS sono convenzioni descrittive, non cliniche. Nessuna \"età\" viene derivata da questi dati: le età proposte da anelli e orologi commerciali usano algoritmi proprietari senza coefficienti pubblicati né validazione indipendente.")
            }
            HealthCard("4 · Lettura dei referti", BioIcons.Doc, Tint.blood) {
                Body("Il testo del PDF viene estratto (o riconosciuto con OCR se il PDF è una scansione o una foto). I valori sono cercati per nome dell'analita, convertiti nelle unità standard e scartati se fisiologicamente implausibili. È sempre una bozza: va confrontata con il referto prima di salvare.")
            }
            HealthCard("5 · Insight e assistente", BioIcons.Sparkle, Tint.sleep) {
                Body("Gli insight integrati usano soglie di linee guida (ESC/EAS, ADA, AHA/CDC, KDIGO, IOM) e risultati di meta-analisi e grandi RCT; ogni fonte è stata verificata su PubMed e mostra tipo di studio e certezza delle prove.")
                Body("L'assistente usa Claude (Anthropic) con accesso a PubMed: deve cercare e leggere gli studi prima di rispondere e citarli con il PMID. L'app controlla che ogni PMID esista e sia stato letto durante la ricerca; non può però garantire che ogni frase rispecchi fedelmente lo studio. Le risposte sono informative e non sostituiscono il medico.")
            }
            HealthCard("6 · Punteggio Sonno", BioIcons.Moon, Tint.sleep) {
                Body("Gli algoritmi commerciali dichiarano i componenti ma non i pesi. Oura usa durata, efficienza, irrequietezza, REM, profondo, latenza e orario; Fitbit durata, sonno «stabile», tempo per addormentarsi e interruzioni; Apple durata (50 punti), orario di coricamento (30) e interruzioni (20). BioEtà combina gli stessi domini in modo trasparente:")
                Body("• Durata 30%: 100 tra 7 e 9 h (7–8 h dai 65 anni), raccomandazioni NSF (Hirshkowitz 2015; PMID 29073412).\n• Regolarità 15%: Sleep Regularity Index sugli ultimi 7 giorni (Phillips 2017; PMID 28607474); nella UK Biobank la regolarità predice la mortalità più della durata (Windred 2024; PMID 37738616).\n• Efficienza 15% e Continuità 15%: indicatori NSF di buona qualità, efficienza ≥ 85%, veglia notturna ≤ 20 min, ≤ 1 risveglio > 5 min (Ohayon 2017; PMID 28346153).\n• Addormentamento 5%: buono ≤ 30 min (NSF).\n• Profondo e REM 10%: confrontati con la tua media, perché la stadiazione dei dispositivi da polso è poco accurata rispetto alla polisonnografia (Chinoy 2021; PMID 33378539) e il consenso NSF sull'architettura è debole.\n• FC notturna 10%: rispetto alla tua baseline.")
            }
            HealthCard("7 · Punteggio Prontezza", BioIcons.Bolt, Tint.good) {
                Body("Oura, Fitbit/Google e WHOOP usano HRV, FC a riposo e sonno; Oura aggiunge temperatura e attività, WHOOP la frequenza respiratoria. BioEtà confronta ogni segnale con la tua baseline personale degli ultimi 60 giorni:")
                Body("• HRV 30%: ln(RMSSD) di oggi e media 7 giorni rispetto alla baseline (Plews 2013; PMID 23852425; Buchheit 2014; PMID 24578692). L'allenamento guidato dall'HRV migliora il VO₂max più di quello predefinito, con effetto piccolo (meta-analisi Granero-Gallegos 2020; PMID 33143175).\n• FC a riposo 20%: aumenti rispetto alla norma personale precedono infezioni (Radin 2020; PMID 33334565; Mishra 2020; PMID 33208926).\n• Sonno della notte 20% e debito di sonno 7 giorni 10%.\n• Frequenza respiratoria 5%: aumenti notturni precedono i sintomi di COVID-19 (Miller 2020; PMID 33301493).\n• Carico di allenamento 15%: TRIMP di Banister e modello fitness-fatica (Morton 1990; PMID 2246166; FCmax di Tanaka 2001; PMID 11153730). Non usiamo il rapporto acuto:cronico, criticato metodologicamente (Impellizzeri 2020; PMID 32502973).")
                Body("Se la FC a riposo supera di 2 DS la tua norma insieme a HRV bassa o respiro accelerato, il punteggio è limitato a 45 e compare un avviso.")
                Caption("Limiti: le soglie intermedie e i pesi sono scelte progettuali motivate dalla letteratura, non stime ottenute da un modello addestrato su esiti; il punteggio composito non è stato validato clinicamente. Senza almeno metà del peso disponibile il punteggio non viene mostrato.")
            }
            HealthCard("8 · eGFR", BioIcons.Drop, Tint.resp) {
                Body("CKD-EPI 2021 senza coefficiente razziale (Inker et al., N Engl J Med 2021; doi:10.1056/NEJMoa2102953), da creatinina ed età al prelievo.")
            }
            Caption("BioEtà non è un dispositivo medico. Valori anomali vanno discussi con il medico.")
        }
    }
}
