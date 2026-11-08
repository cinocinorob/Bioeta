package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.health.Metric
import it.cinocinorob.bioeta.science.CheckState
import it.cinocinorob.bioeta.science.Checkups
import it.cinocinorob.bioeta.science.Compare
import it.cinocinorob.bioeta.science.LoadQuality
import it.cinocinorob.bioeta.data.Sex
import java.time.LocalDate
import java.util.Locale

/** Una metrica confrontabile: etichetta, valori per giorno, formato e colore. */
private data class CMetric(val key: String, val label: String, val values: Map<LocalDate, Double>, val fmt: (Double) -> String, val color: Color)

private fun metricsFor(vm: AppViewModel): List<CMetric> {
    fun w(m: Metric) = vm.wearable[m].orEmpty().associate { it.date to it.value }
    fun f(d: Int, u: String) = { v: Double -> String.format(Locale.ITALY, "%.${d}f", v) + if (u.isNotEmpty()) " $u" else "" }
    val hist = vm.scoreHistory60
    return listOf(
        CMetric("steps", "passi", w(Metric.STEPS), { v -> String.format(Locale.ITALY, "%,.0f", v) }, Tint.activity),
        CMetric("active", "minuti attivi", vm.dayActivities.associate { it.date to it.activeMinutes.toDouble() }, f(0, "min"), Tint.good),
        CMetric("load", "carico", vm.dayActivities.filter { it.quality != LoadQuality.UNAVAILABLE }.associate { it.date to it.load }, f(0, ""), Tint.load),
        CMetric("sleep", "sonno", vm.sleepNights.associate { it.date to it.totalSleepMin / 60.0 }, f(1, "h"), Tint.sleep),
        CMetric("hrv", "HRV", w(Metric.HRV), f(0, "ms"), Tint.hrv),
        CMetric("rhr", "FC a riposo", w(Metric.RHR), f(1, "bpm"), Tint.heart),
        CMetric("recovery", "Recupero", hist.mapNotNull { d -> d.readiness.score?.let { d.date to it.toDouble() } }.toMap(), f(0, ""), Tint.brand),
        CMetric("mood", "umore", vm.journal.filter { it.mood != null }.associate { it.date to it.mood!!.toDouble() }, f(1, "su 5"), Tint.mood),
        CMetric("weight", "peso", w(Metric.WEIGHT), f(1, "kg"), Tint.body),
    ).filter { it.values.size >= 3 }
}

private const val COMPARE_INFO =
    "Scegli due metriche e BioEtà misura quanto vanno insieme nei tuoi giorni: correlazione di Spearman con intervallo al 95%. Con «il giorno dopo» la seconda metrica è presa il giorno successivo (per esempio i passi di oggi e l'HRV di stanotte).\n\n" +
        "Sotto 0,3 il legame è debole, 0,3–0,5 moderato, oltre 0,5 forte. Se l'intervallo comprende lo zero, il legame non è chiaro. È un'associazione: una terza cosa (malattia, viaggi, stagione) può muovere entrambe. Per una prova di causa usa un esperimento di 2 settimane. Idea ripresa da Sonar."

@Composable
fun CompareScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = LocalPalette.current
    val ms = remember(vm.wearable, vm.dayActivities, vm.journal) { metricsFor(vm) }
    var ka by rememberSaveable { mutableStateOf("steps") }
    var kb by rememberSaveable { mutableStateOf("hrv") }
    var lag by rememberSaveable { mutableStateOf(1) }
    var days by rememberSaveable { mutableStateOf(90) }
    SubScaffold("Confronta", onBack) { m ->
        LazyColumn(m.fillMaxSize().testTag("compare_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ms.size < 2) {
                item { EmptyState(BioIcons.Sparkle, "Servono più dati", "Il confronto compare con almeno due metriche registrate per qualche giorno.", Tint.brand) }
                return@LazyColumn
            }
            val a = ms.firstOrNull { it.key == ka } ?: ms[0]
            val b = ms.firstOrNull { it.key == kb && it.key != a.key } ?: ms.first { it.key != a.key }
            item {
                HealthCard("Cosa confrontare", BioIcons.Sparkle, Tint.brand, info = COMPARE_INFO) {
                    Caption("Prima metrica")
                    ChipRow(ms.map { it.key to it.label }, a.key) { ka = it }
                    Caption("Seconda metrica")
                    ChipRow(ms.filter { it.key != a.key }.map { it.key to it.label }, b.key) { kb = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(lag == 0, { lag = 0 }, label = { Text("Stesso giorno") })
                        FilterChip(lag == 1, { lag = 1 }, label = { Text("Il giorno dopo") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30, 90, 180).forEach { d -> FilterChip(days == d, { days = d }, label = { Text("$d giorni") }) }
                    }
                }
            }
            val r = Compare.compare(a.label, a.values, b.label, b.values, lag, LocalDate.now().minusDays(days.toLong()))
            item {
                HealthCard("${a.label.replaceFirstChar { it.uppercase() }} e ${b.label}", BioIcons.Pulse, a.color, modifier = Modifier.testTag("compare_result")) {
                    r.assoc?.let { s ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            BigValue(String.format(Locale.ITALY, "%+.2f", s.rho), "ρ", size = 34)
                            Spacer(Modifier.width(10.dp))
                            Caption("IC 95% ${String.format(Locale.ITALY, "%.2f", s.low)} … ${String.format(Locale.ITALY, "%.2f", s.high)} · ${r.n} giorni")
                        }
                    }
                    Text(Compare.sentence(r, a.fmt, b.fmt), style = MaterialTheme.typography.bodyMedium, color = p.text)
                    if (r.seriesA.size >= 3) {
                        DualLines(r.seriesA.map { it.second }, r.seriesB.map { it.second }, a.color, b.color)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Legend(a.label, a.color); Legend(b.label + if (lag == 1) " (giorno dopo)" else "", b.color)
                        }
                        Caption("Linee riscalate tra il minimo e il massimo del periodo, per vedere se salgono e scendono insieme.")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(items: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (k, l) -> FilterChip(k == selected, { onSelect(k) }, label = { Text(l) }) }
    }
}

@Composable
private fun Legend(label: String, c: Color) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = p.text2)
    }
}

@Composable
private fun DualLines(a: List<Double>, b: List<Double>, ca: Color, cb: Color) {
    val p = LocalPalette.current
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        fun line(v: List<Double>, c: Color) {
            if (v.size < 2) return
            val path = Path()
            v.forEachIndexed { i, y ->
                val x = i * size.width / (v.size - 1)
                val yy = size.height - (y.toFloat() * (size.height - 8f) + 4f)
                if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            drawPath(path, c, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
        drawLine(p.sep, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
        line(a, ca); line(b, cb)
    }
}

// ---------------- Controlli consigliati ----------------

private const val CHECK_INFO =
    "Controlli e vaccini indicati per la tua età e il tuo sesso dalle regole italiane ed europee: screening oncologici del Servizio sanitario (colon-retto, mammella, cervice), Piano nazionale di prevenzione vaccinale 2023–25, linee guida ESC per pressione e rischio cardiovascolare, ADA per la glicemia. «In regola» usa le date delle misure e degli esami che hai in BioEtà. Non conosce la tua storia clinica: per i controlli legati a malattie o familiarità decide il medico di base. Idea ripresa da Sonar."

@Composable
fun CheckupsCard(vm: AppViewModel) {
    val p = LocalPalette.current
    val today = LocalDate.now()
    val lastLip = vm.panels.filter { it.values.containsKey("chol") || it.values.containsKey("ldl") }.maxOfOrNull { it.date }
    val lastGlu = vm.panels.filter { it.values.containsKey("glucose") || it.values.containsKey("hba1c") }.maxOfOrNull { it.date }
    val list = Checkups.list(vm.profile.ageAt(today), vm.profile.sex == Sex.M, vm.bpAll.lastOrNull()?.time?.toLocalDate(), lastLip, lastGlu, today)
    HealthCard("Controlli consigliati", BioIcons.Shield, Tint.blue, info = CHECK_INFO, modifier = Modifier.testTag("checkups")) {
        if (list.isEmpty()) { Caption("Inserisci la data di nascita nel profilo."); return@HealthCard }
        list.filter { it.state != CheckState.NOT_YET }.forEach { c ->
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(c.title, style = MaterialTheme.typography.titleSmall, color = p.text)
                    Text(c.when_, style = MaterialTheme.typography.bodySmall, color = p.text2)
                }
                Pill(c.state.label, when (c.state) { CheckState.OK -> Tint.good; CheckState.DUE -> Tint.warn; else -> Tint.blue })
            }
        }
        val later = list.filter { it.state == CheckState.NOT_YET }
        if (later.isNotEmpty()) Caption("Più avanti: " + later.joinToString(" · ") { "${it.title.lowercase()} (${it.when_.lowercase()})" } + ".")
    }
}
