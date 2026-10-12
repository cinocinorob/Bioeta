package it.cinocinorob.bioeta.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.health.DailyPoint
import it.cinocinorob.bioeta.health.HealthRepo
import it.cinocinorob.bioeta.health.Metric
import it.cinocinorob.bioeta.health.MetricSummary
import kotlin.math.abs

@Composable
fun WearableScreen(vm: AppViewModel, modifier: Modifier, onRequestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    val summaries = Metric.entries.mapNotNull { m ->
        vm.wearable[m]?.takeIf { it.isNotEmpty() }?.let { HealthRepo.summarize(m, it) }
    }
    val missing = Metric.entries.filter { vm.wearable[it].isNullOrEmpty() }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (vm.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

        when (vm.hcStatus) {
            HealthConnectClient.SDK_UNAVAILABLE -> item {
                SectionCard("Connessione Salute non disponibile") {
                    Text("Questo dispositivo non supporta Connessione Salute.")
                }
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> item {
                SectionCard("Aggiornamento richiesto") {
                    Text("Connessione Salute va installata o aggiornata.")
                    Button(onClick = {
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${HealthRepo.PROVIDER}&url=healthconnect%3A%2F%2Fonboarding"))
                                    .setPackage("com.android.vending")
                            )
                        }
                    }) { Text("Apri Play Store") }
                }
            }
        }

        if (vm.hcAvailable && !vm.hasAnyHealthPermission) item {
            SectionCard("Collega Connessione Salute") {
                Text("L'app legge (solo lettura) FC a riposo, HRV, sonno, passi, VO₂max, peso, SpO₂ e frequenza respiratoria. Nessun dato lascia il telefono.")
                Muted("Verifica che l'app Fitbit condivida i dati con Connessione Salute (impostazioni di Connessione Salute → App → Fitbit).")
                Button(onClick = onRequestPermissions) { Text("Concedi accesso") }
            }
        }

        if (vm.hcAvailable && vm.hasAnyHealthPermission) item {
            SectionCard {
                Text("Ultimi 7 giorni confrontati con la tua baseline personale (giorni 8–67 precedenti).")
                if (HealthRepo.HISTORY_PERMISSION !in vm.granted) {
                    Muted("Senza il permesso \"dati storici\" Connessione Salute fornisce solo i 30 giorni precedenti alla concessione: la baseline sarà incompleta per alcune settimane.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.refreshHealth() }) { Text("Aggiorna") }
                    OutlinedButton(onClick = onRequestPermissions) { Text("Permessi") }
                }
            }
        }

        items(summaries, key = { it.metric.name }) { s -> MetricCard(s) }

        if (vm.hasAnyHealthPermission && missing.isNotEmpty()) item {
            Muted("Nessun dato per: ${missing.joinToString(", ") { it.label }}. Dipende da cosa l'orologio scrive in Connessione Salute e dai permessi concessi.")
        }

        item {
            SectionCard("Come leggere questi dati") {
                Muted("Questi indicatori non sono convertiti in \"anni\": non esiste un modello validato e pubblico che lo faccia. Il confronto con la propria baseline (approccio intra-individuale) è più informativo del confronto con norme di popolazione, perché HRV e FC a riposo hanno ampia variabilità tra persone. Deviazioni di una settimana riflettono spesso sonno, alcol, carico di allenamento, stress o infezioni iniziali; le tendenze di mesi sono più rilevanti per lo stato di salute.")
            }
        }
    }
}

@Composable
private fun MetricCard(s: MetricSummary) {
    val m = s.metric
    val d = m.decimals
    SectionCard(m.label) {
        val r = s.recent
        val b = s.baseline
        if (r != null) {
            Text("${r.f(d)} ${m.unit}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Muted("media ultimi 7 giorni")
        } else {
            val last = s.points.last()
            Text("${last.value.f(d)} ${m.unit}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Muted("ultimo dato: ${last.date.itDate()} (meno di 3 giorni negli ultimi 7)")
        }
        if (b != null && s.baselineSd != null) {
            Text("Baseline: ${b.f(d)} ± ${s.baselineSd.f(d)} ${m.unit}")
        } else {
            Muted("Baseline non ancora disponibile (servono ≥14 giorni di dati nel periodo di riferimento).")
        }
        val z = s.z
        if (z != null) {
            Text(interpret(z, m), color = when (verdict(z, m)) {
                1 -> MaterialTheme.colorScheme.primary
                -1 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            })
        }
        s.slopePer30d?.let { Muted("Tendenza sull'intera finestra: ${it.fs(d)} ${m.unit} ogni 30 giorni") }
        if (s.points.size >= 2) {
            LineChart(
                listOf(
                    ChartSeries(s.points.map { it.date.toEpochDay().toDouble() to it.value }, MaterialTheme.colorScheme.outline, dots = false, width = 1f),
                    ChartSeries(rolling7(s.points), MaterialTheme.colorScheme.primary, dots = false),
                ),
                yDecimals = if (m == Metric.STEPS) 0 else d.coerceAtMost(1),
                xLabel = ::epochLabel,
            )
            Muted("Grigio: valori giornalieri. Colore: media mobile 7 giorni.")
        }
    }
}

/** 1 = favorevole, -1 = sfavorevole, 0 = neutro o entro ±0,5 DS. */
private fun verdict(z: Double, m: Metric): Int {
    if (abs(z) < 0.5 || m.higherIsBetter == null) return 0
    val up = z > 0
    return if (up == m.higherIsBetter) 1 else -1
}

private fun interpret(z: Double, m: Metric): String {
    if (abs(z) < 0.5) return "In linea con la tua baseline (${z.fs(1)} DS)."
    val dir = if (z > 0) "sopra" else "sotto"
    val v = when (verdict(z, m)) {
        1 -> ", in direzione favorevole"
        -1 -> ", in direzione sfavorevole"
        else -> ""
    }
    val strength = if (abs(z) >= 1.5) "Marcatamente" else "Moderatamente"
    return "$strength $dir la baseline (${z.fs(1)} DS)$v."
}

private fun rolling7(points: List<DailyPoint>): List<Pair<Double, Double>> {
    val byDay = points.associateBy { it.date.toEpochDay() }
    return points.mapNotNull { p ->
        val day = p.date.toEpochDay()
        val window = (day - 6..day).mapNotNull { byDay[it]?.value }
        if (window.size >= 4) day.toDouble() to window.average() else null
    }
}
