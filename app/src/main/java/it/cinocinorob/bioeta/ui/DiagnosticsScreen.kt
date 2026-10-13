package it.cinocinorob.bioeta.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import it.cinocinorob.bioeta.AppViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Diagnostica: per ogni tipo di dato mostra permesso, numero di dati negli ultimi 7 giorni,
 * ultimo dato e app di origine. Serve a capire se il problema è il permesso, la sincronizzazione
 * dell'orologio con Connessione Salute o la lettura dell'app.
 */
@Composable
fun DiagnosticsScreen(vm: AppViewModel, onBack: () -> Unit, onRequestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.runDiagnostics() }
    val rows = vm.diagnostics
    SubScaffold("Diagnostica Connessione Salute", onBack) { m ->
        LazyColumn(
            m.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val status = when (vm.hcStatus) {
                    HealthConnectClient.SDK_AVAILABLE -> "Disponibile"
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Da aggiornare"
                    else -> "Non disponibile"
                }
                HealthCard("Stato", BioIcons.Heart, Tint.heart) {
                    Body("Connessione Salute: $status")
                    Body("Permessi concessi: ${vm.granted.size} di ${vm.health.permissions.size}")
                    val lw = vm.lastWearable
                    Body("Ultimo dato dall'orologio: " + (lw?.let { agoText(it) } ?: "nessuno"))
                    if (lw == null || Duration.between(lw, Instant.now()).toHours() >= 14) {
                        Caption("L'app dell'orologio non scrive in Connessione Salute da troppo tempo: BioEtà non può vedere il sonno di stanotte finché Fitbit/Google Health non sincronizza.")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRequestPermissions) { Text("Concedi permessi") }
                        OutlinedButton(onClick = { vm.runDiagnostics(); vm.refreshHealth(force = true) }) { Text("Riprova") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { openWatchApp(ctx) }) { Text("Apri Fitbit") }
                        FilledTonalButton(onClick = {
                            runCatching { ctx.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
                        }) { Text("Connessione Salute") }
                    }
                }
            }
            if (vm.diagnosing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (rows != null) items(rows, key = { it.label }) { r -> DiagRowCard(r) }
            item {
                HealthCard("Se mancano dati dell'orologio", BioIcons.Info, Tint.blue) {
                    Body("BioEtà legge solo ciò che un'altra app scrive in Connessione Salute. Per il Pixel Watch è l'app Google Health (o Fitbit, nelle versioni precedenti):")
                    Caption("Se l'ultimo dato è di ieri mattina, l'orologio non ha sincronizzato: apri Fitbit/Google Health, trascina in basso per sincronizzare e attendi qualche minuto. Per evitarlo: Impostazioni Android → App → Fitbit → Batteria → «Senza restrizioni».")
                    Caption("1. Apri l'app Google Health → Connessioni → App partner.\n2. «Sincronizza le tue app per la salute» → Configura.\n3. Scegli i dati da condividere (passi, sonno, frequenza cardiaca, HRV, allenamenti, calorie…) e consenti anche i dati storici.\n4. Quando compare «Sincronizzato», torna qui e tocca Riprova.")
                    Caption("Se nella tabella un dato ha il permesso ma 0 elementi, il problema è la sincronizzazione dall'orologio. Se compare un errore, mandamelo: indica un problema di lettura dell'app.")
                }
            }
        }
    }
}

@Composable
private fun DiagRowCard(r: it.cinocinorob.bioeta.health.HealthRepo.DiagRow) {
    val p = LocalPalette.current
    val zone = ZoneId.systemDefault()
    val (dot, state) = when {
        !r.granted -> Tint.bad to "Permesso non concesso"
        r.error != null -> Tint.bad to "Errore di lettura"
        (r.count7d ?: 0) == 0 -> Tint.warn to "Nessun dato negli ultimi 7 giorni"
        r.latest != null && Duration.between(r.latest, Instant.now()).toHours() >= 30 -> Tint.warn to "${r.count7d}${if (r.capped) "+" else ""} dati, ma nessuno recente"
        else -> Tint.good to "${r.count7d}${if (r.capped) "+" else ""} dati negli ultimi 7 giorni"
    }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(p.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
            Text(r.label, style = MaterialTheme.typography.titleMedium, color = p.text, modifier = Modifier.weight(1f))
        }
        Text(state, style = MaterialTheme.typography.bodyMedium, color = p.text)
        r.latest?.let {
            Text("Ultimo dato: ${DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ITALIAN).format(it.atZone(zone))} (${agoText(it)})",
                style = MaterialTheme.typography.bodySmall, color = p.text2)
        }
        if (r.origins.isNotEmpty()) Text("Scritto da: ${r.origins.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = p.text2)
        r.error?.let { Text("Errore: $it", style = MaterialTheme.typography.bodySmall, color = Tint.bad) }
        if (r.error == null && r.pastError != null) {
            Text("Errore precedente (${agoText(r.pastError.at)}), la lettura ora riesce: ${r.pastError.message}",
                style = MaterialTheme.typography.bodySmall, color = p.text2)
        }
    }
}

internal fun agoText(t: Instant): String {
    val ago = Duration.between(t, Instant.now())
    return when {
        ago.toMinutes() < 90 -> "${ago.toMinutes()} min fa"
        ago.toHours() < 48 -> "${ago.toHours()} ore fa"
        else -> "${ago.toDays()} giorni fa"
    }
}

/** Apre l'app che sincronizza l'orologio (Fitbit / Google Health); se assente, il Play Store. */
internal fun openWatchApp(ctx: android.content.Context) {
    val pkg = it.cinocinorob.bioeta.health.HealthRepo.FITBIT
    val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
    runCatching {
        ctx.startActivity(launch ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkg")))
    }
}
