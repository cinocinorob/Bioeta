package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.data.SyncState
import it.cinocinorob.bioeta.health.HealthRepo
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fonti e sincronizzazione: stato, ultima sincronizzazione, allenamenti importati (verificabili uno per uno),
 * permessi e limiti reali di ciascuna fonte.
 */
@Composable
fun SyncScreen(vm: AppViewModel, onBack: () -> Unit, onRequestPermissions: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val p = LocalPalette.current
    val st = vm.syncStatus
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ITALIAN)
    val sessions = vm.displaySessions
    SubScaffold("Fonti e sincronizzazione", onBack) { m ->
        LazyColumn(m.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HealthCard("Stato", BioIcons.Refresh, Tint.brand) {
                    val c = when (st.state) {
                        SyncState.OK -> Tint.good; SyncState.PARTIAL -> Tint.warn; SyncState.ERROR -> Tint.bad
                        SyncState.NO_PERMISSION -> Tint.warn; SyncState.NEVER -> p.text3
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                        Spacer(Modifier.width(8.dp))
                        Text(st.state.label, style = MaterialTheme.typography.titleMedium, color = p.text, modifier = Modifier.weight(1f))
                        Text(agoShort(st.lastSync), style = MaterialTheme.typography.labelLarge, color = p.text2)
                    }
                    if (vm.syncing || vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Tint.brand)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatChip("Allenamenti", "${st.sessionsInWindow}", "in ${st.windowDays} gg", Modifier.weight(1f))
                        StatChip("Doppioni", "${st.duplicatesRemoved}", "esclusi", Modifier.weight(1f))
                    }
                    if (st.origins.isNotEmpty()) Caption("App che hanno scritto allenamenti: ${st.origins.joinToString(", ")}")
                    st.errors.forEach { InlineNotice(it, NoticeKind.ERROR) }
                    if (st.state == SyncState.NO_PERMISSION) InlineNotice("Concedi il permesso «Allenamenti» per importare le attività.", NoticeKind.WARN, "Concedi", onRequestPermissions)
                    if (st.windowDays in 1..30) Caption("Senza il permesso per i dati storici Connessione Salute consente di leggere solo gli ultimi 30 giorni.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.syncNow() }, enabled = !vm.syncing && !vm.loading) { Text("Sincronizza ora") }
                        OutlinedButton(onClick = onOpenDiagnostics) { Text("Diagnostica") }
                    }
                    Caption("La sincronizzazione è idempotente: rileggere gli stessi dati non crea doppioni, e gli allenamenti cancellati nell'app sorgente spariscono anche qui.")
                }
            }

            item { SectionHeader("Allenamenti importati") }
            if (sessions.isEmpty()) item {
                EmptyState(BioIcons.Bolt, "Nessun allenamento importato",
                    "Gli allenamenti compaiono qui quando l'app dell'orologio (es. Fitbit / Google Health) li scrive in Connessione Salute. Se ne manca uno: apri l'app dell'orologio, sincronizza, poi tocca «Sincronizza ora».", Tint.vo2)
            }
            items(sessions.take(60), key = { it.id }) { s ->
                val stats = vm.workoutStats[s.id]
                Row(
                    Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(p.card).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(exerciseName(s.hcType, s.title), style = MaterialTheme.typography.titleSmall, color = p.text)
                        Text("${s.start.atZone(zone).format(fmt)} · ${s.minutes.f(0)} min", style = MaterialTheme.typography.bodySmall, color = p.text2)
                        Text(vm.health.originLabel(s.origin), style = MaterialTheme.typography.bodySmall, color = p.text3)
                    }
                    when {
                        stats == null -> Text("FC in calcolo", style = MaterialTheme.typography.labelSmall, color = p.text3)
                        stats.hrCoverage >= 0.8 -> StatusBadge(DataStatus.MEASURED)
                        stats.hrCoverage > 0 -> StatusBadge(DataStatus.PARTIAL)
                        else -> StatusBadge(DataStatus.ESTIMATED)
                    }
                }
            }
            if (sessions.isNotEmpty()) item {
                Caption("Misurato = frequenza cardiaca per almeno l'80% dell'allenamento. Parziale = FC solo in parte. Stimato = nessuna FC: minuti attivi attribuiti dal tipo di attività, carico non calcolato.")
            }

            item { SectionHeader("Fonti di dati") }
            item {
                HealthCard("Connessione Salute", BioIcons.Heart, Tint.heart) {
                    Body("Fonte principale e unica attiva. Permessi concessi: ${vm.granted.count { it in vm.health.permissions }} di ${vm.health.permissions.size}.")
                    if (vm.health.stepsFromRaw) InlineNotice("L'aggregato dei passi di Connessione Salute è vuoto anche se ci sono record: l'app che li scrive probabilmente non è nell'elenco delle priorità. BioEtà somma i record direttamente (prendendo il massimo tra le app, mai la somma). Per sistemare alla fonte: Impostazioni → Connessione Salute → Dati e accesso → Attività → Priorità delle app.", NoticeKind.WARN)
                    Caption("BioEtà legge solo i tipi di dati autorizzati e non scrive nulla. Per ogni tipo, la diagnostica mostra quanti dati ci sono, quale app li ha scritti e l'ultimo aggiornamento.")
                    OutlinedButton(onClick = onRequestPermissions) { Text("Gestisci permessi") }
                }
            }
            item {
                HealthCard("Altre app e dispositivi", BioIcons.Info, Tint.blue) {
                    SourceLine("Fitbit / Google Health (Pixel Watch)", "Tramite Connessione Salute, se nell'app è attiva la condivisione dei dati. Nessuna connessione diretta al cloud Fitbit.")
                    SourceLine("Huawei Health", "Funziona solo se l'app Huawei Health scrive i dati in Connessione Salute sul tuo telefono: verificalo nella diagnostica. L'accesso diretto (Huawei Health Kit) non è integrato: richiede la registrazione come sviluppatore Huawei.")
                    SourceLine("WHOOP", "Funziona solo se l'app WHOOP scrive in Connessione Salute. L'API WHOOP non è integrata: richiede un'autorizzazione OAuth e invio dei dati via Internet, contrari all'impostazione locale di BioEtà.")
                    SourceLine("Samsung Health, Oura, altre", "Qualsiasi app che scrive in Connessione Salute viene letta automaticamente, nei limiti dei permessi concessi.")
                    Caption("Le fonti sono riconosciute dal nome del pacchetto che ha scritto il dato (${HealthRepo.KNOWN_APPS.size} app note). Una nuova fonte diretta si aggiunge implementando un lettore separato: oggi non ce ne sono.")
                }
            }
        }
    }
}

@Composable
private fun SourceLine(name: String, text: String) {
    val p = LocalPalette.current
    Column {
        Text(name, style = MaterialTheme.typography.titleSmall, color = p.text)
        Text(text, style = MaterialTheme.typography.bodySmall, color = p.text2)
    }
}
