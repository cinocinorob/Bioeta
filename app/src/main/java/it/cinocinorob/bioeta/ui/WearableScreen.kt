package it.cinocinorob.bioeta.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.health.DailyPoint
import it.cinocinorob.bioeta.health.HealthRepo
import it.cinocinorob.bioeta.health.Metric
import it.cinocinorob.bioeta.health.MetricSummary
import kotlin.math.abs

fun metricTint(m: Metric): Color = when (m) {
    Metric.RHR -> Tint.heart
    Metric.HRV -> Tint.hrv
    Metric.SLEEP -> Tint.sleep
    Metric.STEPS -> Tint.activity
    Metric.VO2 -> Tint.vo2
    Metric.RESP -> Tint.resp
    Metric.SPO2 -> Tint.spo2
    Metric.WEIGHT -> Tint.body
}

fun metricIcon(m: Metric): ImageVector = when (m) {
    Metric.RHR -> BioIcons.Heart
    Metric.HRV -> BioIcons.Pulse
    Metric.SLEEP -> BioIcons.Moon
    Metric.STEPS -> BioIcons.Bolt
    Metric.VO2 -> BioIcons.Gauge
    Metric.RESP -> BioIcons.Wind
    Metric.SPO2 -> BioIcons.Drop
    Metric.WEIGHT -> BioIcons.Person
}

private fun fmtValue(m: Metric, v: Double): String = when (m) {
    Metric.SLEEP -> { val h = v.toInt(); val mm = ((v - h) * 60).toInt(); "${h}h ${mm.toString().padStart(2, '0')}m" }
    Metric.STEPS -> String.format(java.util.Locale.ITALY, "%,d", v.toLong())
    else -> v.f(m.decimals)
}

@Composable
fun WearableScreen(vm: AppViewModel, modifier: Modifier, onRequestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    val summaries = Metric.entries.mapNotNull { m ->
        vm.wearable[m]?.takeIf { it.isNotEmpty() }?.let { HealthRepo.summarize(m, it) }
    }
    val missing = Metric.entries.filter { vm.wearable[it].isNullOrEmpty() }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { LargeHeader("Recupero", "Sonno e prontezza") }
        if (vm.hcAvailable && vm.hasAnyHealthPermission) item { RecoverySection(vm, onRequestPermissions) }
        if (vm.hcAvailable && vm.hasAnyHealthPermission) item { SectionLabel("Sensori") }
        if (vm.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Tint.heart) }

        when (vm.hcStatus) {
            HealthConnectClient.SDK_UNAVAILABLE -> item {
                HealthCard("Non disponibile", BioIcons.Info, Tint.warn) { Body("Questo dispositivo non supporta Connessione Salute.") }
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> item {
                HealthCard("Aggiornamento richiesto", BioIcons.Info, Tint.warn) {
                    Body("Connessione Salute va installata o aggiornata.")
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
            HealthCard("Collega Connessione Salute", BioIcons.Heart, Tint.heart) {
                Body("Lettura (mai scrittura) di FC a riposo, HRV, sonno, passi, VO₂max, peso, SpO₂ e frequenza respiratoria. Nessun dato lascia il telefono.")
                Caption("Verifica che l'app Fitbit condivida i dati con Connessione Salute: Impostazioni → Connessione Salute → App → Fitbit.")
                Button(onClick = onRequestPermissions) { Text("Concedi accesso") }
            }
        }

        if (vm.hcAvailable && vm.hasAnyHealthPermission) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Caption("Ultimi 7 giorni rispetto alla tua baseline (giorni 8–67 precedenti)")
                    if (HealthRepo.HISTORY_PERMISSION !in vm.granted) {
                        Caption("Senza il permesso \"dati storici\" la baseline si completa solo col tempo.", Tint.warn)
                    }
                }
                TextButton(onClick = { vm.refreshHealth() }) { Text("Aggiorna", color = Tint.blue) }
                TextButton(onClick = onRequestPermissions) { Text("Permessi", color = Tint.blue) }
            }
        }

        items(summaries, key = { it.metric.name }) { s -> MetricCard(s) }

        if (vm.hasAnyHealthPermission && missing.isNotEmpty()) item {
            Caption("Nessun dato per: ${missing.joinToString(", ") { it.label }}. Dipende da cosa l'orologio scrive in Connessione Salute e dai permessi concessi.")
        }

        item {
            HealthCard("Come leggere questi dati", BioIcons.Info, Tint.blue) {
                Caption("Questi indicatori non vengono convertiti in \"anni\": non esiste un modello pubblico e validato che lo faccia. Il confronto con la propria baseline è più informativo delle norme di popolazione, perché HRV e FC a riposo variano molto tra persone. Deviazioni di una settimana riflettono spesso sonno, alcol, carico di allenamento, stress o infezioni iniziali; contano di più le tendenze di mesi. La banda colorata nel grafico è la baseline ± 1 DS.")
            }
        }
    }
}

@Composable
private fun MetricCard(s: MetricSummary) {
    val m = s.metric
    val tint = metricTint(m)
    HealthCard(m.label, metricIcon(m), tint, trailing = s.points.last().date.itLong()) {
        val r = s.recent
        val b = s.baseline
        val sd = s.baselineSd
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (r != null) {
                    BigValue(fmtValue(m, r), if (m == Metric.SLEEP) null else m.unit, size = 30)
                    Caption("media ultimi 7 giorni")
                } else {
                    BigValue(fmtValue(m, s.points.last().value), if (m == Metric.SLEEP) null else m.unit, size = 30)
                    Caption("ultimo dato")
                }
            }
            val z = s.z
            if (z != null && r != null && b != null) {
                val diff = r - b
                val v = verdict(z, m)
                Pill(
                    "${if (m == Metric.SLEEP) (diff * 60).fs(0) + " min" else diff.fs(m.decimals) + " " + m.unit}",
                    when (v) { 1 -> Tint.good; -1 -> Tint.bad; else -> LocalPalette.current.text2 },
                )
            }
        }
        if (s.points.size >= 2) {
            HealthChart(
                listOf(
                    ChartLayer(s.points.map { it.date.toEpochDay().toDouble() to it.value }, tint, LayerKind.DOTS),
                    ChartLayer(rolling7(s.points), tint, LayerKind.AREA, emphasizeLast = true),
                ),
                yDecimals = if (m == Metric.STEPS) 0 else minOf(m.decimals, 1),
                band = if (b != null && sd != null) (b - sd) to (b + sd) else null,
                bandColor = tint,
            )
        }
        val z = s.z
        if (z != null) Body(interpret(z, m))
        else Caption("Baseline in costruzione: servono ≥14 giorni nel periodo di riferimento e ≥3 negli ultimi 7.")
        s.slopePer30d?.let { Caption("Tendenza su ${s.points.size} giorni: ${it.fs(m.decimals)} ${m.unit} ogni 30 giorni") }
    }
}

/** 1 = favorevole, −1 = sfavorevole, 0 = neutro o entro ±0,5 DS. */
private fun verdict(z: Double, m: Metric): Int {
    if (abs(z) < 0.5 || m.higherIsBetter == null) return 0
    return if ((z > 0) == m.higherIsBetter) 1 else -1
}

private fun interpret(z: Double, m: Metric): String {
    if (abs(z) < 0.5) return "In linea con la tua baseline (${z.fs(1)} DS)."
    val dir = if (z > 0) "sopra" else "sotto"
    val v = when (verdict(z, m)) { 1 -> ", in direzione favorevole"; -1 -> ", in direzione sfavorevole"; else -> "" }
    val strength = if (abs(z) >= 1.5) "Nettamente" else "Moderatamente"
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
