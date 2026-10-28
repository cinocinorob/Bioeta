package it.cinocinorob.bioeta.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import java.time.LocalDate

fun metricTint(m: Metric): Color = when (m) {
    Metric.RHR -> Tint.heart
    Metric.HRV -> Tint.hrv
    Metric.SLEEP -> Tint.sleep
    Metric.STEPS -> Tint.activity
    Metric.VO2 -> Tint.vo2
    Metric.RESP -> Tint.resp
    Metric.SPO2 -> Tint.spo2
    Metric.WEIGHT -> Tint.body
    Metric.BODY_FAT -> Tint.body
    Metric.LEAN_MASS -> Tint.body
    Metric.SKIN_TEMP -> Tint.warn
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
    Metric.BODY_FAT -> BioIcons.Person
    Metric.LEAN_MASS -> BioIcons.Person
    Metric.SKIN_TEMP -> BioIcons.Pulse
}

fun fmtValue(m: Metric, v: Double): String = when (m) {
    Metric.SLEEP -> { val h = v.toInt(); val mm = ((v - h) * 60).toInt(); "${h}h ${mm.toString().padStart(2, '0')}m" }
    Metric.STEPS -> String.format(java.util.Locale.ITALY, "%,d", v.toLong())
    else -> v.f(m.decimals)
}

@Composable
fun WearableScreen(
    vm: AppViewModel,
    modifier: Modifier,
    onRequestPermissions: () -> Unit,
    onOpenJournal: () -> Unit = {},
    onOpenDiscoveries: () -> Unit = {},
    onOpenBp: () -> Unit = {},
    onOpenMetric: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val summaries = Metric.entries.mapNotNull { m ->
        vm.wearable[m]?.takeIf { it.isNotEmpty() }?.let { HealthRepo.summarize(m, it) }
    }
    val missing = Metric.entries.filter { vm.wearable[it].isNullOrEmpty() && it != Metric.SKIN_TEMP }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { LargeHeader("Salute", "Recupero, sonno e parametri vitali") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HealthShortcut("Pressione", vm.bpSummary?.let { "${it.meanSys.f(0)}/${it.meanDia.f(0)} mmHg" } ?: "Registra", Tint.bp, BioIcons.Heart, onOpenBp, Modifier.weight(1f))
                HealthShortcut("Diario", "Umore e abitudini", Tint.sleep, BioIcons.Pencil, onOpenJournal, Modifier.weight(1f))
            }
        }
        if (vm.hcAvailable && vm.hasAnyHealthPermission) item { RecoverySection(vm, onRequestPermissions) }
        if (vm.hcAvailable && vm.hasAnyHealthPermission) item { SleepRegularityCard(vm) }
        if (vm.hcAvailable && vm.hasAnyHealthPermission) item { WellnessQuestCard(vm) }
        if (vm.hcAvailable && vm.hasAnyHealthPermission) item { RadarCard(vm, startExpanded = true) }
        item { MoodTrendCard(vm, onLog = onOpenJournal) }
        if (vm.hcAvailable && vm.hasAnyHealthPermission) item { TonightCard(vm) }
        item { DiscoveriesCard(vm, onOpenDiscoveries) }
        if (vm.hcAvailable && vm.hasAnyHealthPermission) item { SectionLabel("Sensori e composizione corporea") }
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
                Body("Lettura (mai scrittura) di FC a riposo, HRV, sonno, passi, VO₂max, peso, percentuale di grasso corporeo, massa magra, SpO₂ e frequenza respiratoria. I dati restano sul telefono, salvo uso esplicito dell’assistente online.")
                Caption("Verifica che l'app Fitbit condivida i dati con Connessione Salute: Impostazioni → Connessione Salute → App → Fitbit.")
                Button(onClick = onRequestPermissions) { Text("Concedi accesso") }
            }
        }

        if (vm.hcAvailable && vm.hasAnyHealthPermission) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Caption("Ultima settimana rispetto al tuo solito (i due mesi precedenti)")
                    if (HealthRepo.HISTORY_PERMISSION !in vm.granted) {
                        Caption("Senza il permesso per i dati storici, BioEtà legge al massimo gli ultimi 30 giorni; il confronto con il tuo solito potrebbe essere incompleto.", Tint.warn)
                    }
                }
                TextButton(onClick = { vm.refreshHealth(force = true) }) { Text("Aggiorna", color = Tint.blue) }
                TextButton(onClick = onRequestPermissions) { Text("Permessi", color = Tint.blue) }
            }
        }

        items(summaries, key = { it.metric.name }) { s -> MetricCard(s) { onOpenMetric(s.metric.name) } }

        if (vm.hasAnyHealthPermission && missing.isNotEmpty()) item {
            Caption("Nessun dato per: ${missing.joinToString(", ") { it.label }}. Dipende da cosa l'orologio scrive in Connessione Salute e dai permessi concessi.")
        }

        item {
            HealthCard("Come leggere questi dati", BioIcons.Info, Tint.blue, info = "Questi indicatori non vengono convertiti in \"anni\": non esiste un modello pubblico e validato che lo faccia. Il confronto con la propria baseline è più informativo delle norme di popolazione, perché HRV e FC a riposo variano molto tra persone. Peso, grasso corporeo e massa magra sono letti da Connessione Salute se una fonte li sincronizza; le stime da bioimpedenza oscillano e vanno interpretate come tendenze, non misure esatte. Deviazioni di una settimana possono riflettere sonno, carico, stress o infezioni; contano di più le tendenze di mesi. La banda colorata nel grafico è la baseline ± 1 DS.") {
                Caption("Ultima settimana confrontata con il tuo solito. Tocca ⓘ per i dettagli.")
            }
        }
    }
}


@Composable
private fun WellnessQuestCard(vm: AppViewModel) {
    val today = LocalDate.now()
    val from = today.minusDays(6)
    val steps = vm.wearable[Metric.STEPS].orEmpty().filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
    val sleep = vm.wearable[Metric.SLEEP].orEmpty().filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
    val moveDays = steps.count { it.value >= 7000.0 }
    val sleepNights = sleep.count { it.value in 7.0..9.0 }
    val stepsKnown = steps.map { it.date }.toSet().size
    val sleepKnown = sleep.map { it.date }.toSet().size
    HealthCard("BioEtà Quest", BioIcons.Sparkle, Tint.blue) {
        Body("Missioni settimanali: premiano la costanza, non il valore di un singolo giorno. Sono obiettivi motivazionali, non soglie cliniche.")
        SectionLabel("MOVIMENTO")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Cammina in 5 giorni", color = LocalPalette.current.text)
                Caption("$moveDays/5 giorni con almeno 7.000 passi")
            }
            Pill("$moveDays/5", if (moveDays >= 5) Tint.good else Tint.blue)
        }
        LinearProgressIndicator(
            progress = (moveDays / 5f).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
            color = Tint.blue,
        )
        SectionLabel("RECUPERO")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dai spazio al sonno", color = LocalPalette.current.text)
                Caption("$sleepNights/5 notti tra 7 e 9 ore")
            }
            Pill("$sleepNights/5", if (sleepNights >= 5) Tint.good else Tint.blue)
        }
        LinearProgressIndicator(
            progress = (sleepNights / 5f).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
            color = Tint.sleep,
        )
        if (moveDays >= 5 && sleepNights >= 5) {
            Caption("Badge della settimana: Equilibrio! Hai completato entrambe le missioni.", Tint.good)
        } else {
            Caption("Nessuna penalità per riposo, imprevisti o dati mancanti. Dati disponibili: passi $stepsKnown/7 giorni, sonno $sleepKnown/7 notti.")
        }
    }
}

@Composable
private fun MetricCard(s: MetricSummary, onClick: () -> Unit) {
    val m = s.metric
    val tint = metricTint(m)
    HealthCard(m.label, metricIcon(m), tint, trailing = s.points.last().date.itLong(), onClick = onClick) {
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
        else Caption("Confronto in costruzione: servono almeno 14 giorni di storico e 3 giorni nell'ultima settimana.")
        s.slopePer30d?.let { Caption("Tendenza su ${s.points.size} giorni: ${it.fs(m.decimals)} ${m.unit} ogni 30 giorni") }
    }
}

/** 1 = favorevole, −1 = sfavorevole, 0 = neutro o entro ±0,5 DS. */
private fun verdict(z: Double, m: Metric): Int {
    if (abs(z) < 0.5 || m.higherIsBetter == null) return 0
    return if ((z > 0) == m.higherIsBetter) 1 else -1
}

private fun interpret(z: Double, m: Metric): String {
    if (abs(z) < 0.5) return "In linea con il tuo solito."
    val dir = if (z > 0) "sopra" else "sotto"
    val v = when (verdict(z, m)) { 1 -> ", in direzione favorevole"; -1 -> ", in direzione sfavorevole"; else -> "" }
    val strength = if (abs(z) >= 1.5) "Nettamente" else "Un po'"
    return "$strength $dir il tuo solito$v."
}

private fun rolling7(points: List<DailyPoint>): List<Pair<Double, Double>> {
    val byDay = points.associateBy { it.date.toEpochDay() }
    return points.mapNotNull { p ->
        val day = p.date.toEpochDay()
        val window = (day - 6..day).mapNotNull { byDay[it]?.value }
        if (window.size >= 4) day.toDouble() to window.average() else null
    }
}

@Composable
private fun HealthShortcut(title: String, value: String, tint: Color, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    Row(
        modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(p.card)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, 34)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1)
            Text(value, style = MaterialTheme.typography.bodySmall, color = p.text2, maxLines = 1)
        }
    }
}
