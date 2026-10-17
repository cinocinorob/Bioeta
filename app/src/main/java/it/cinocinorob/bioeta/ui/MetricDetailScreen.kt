package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.health.HealthRepo
import it.cinocinorob.bioeta.health.Metric
import it.cinocinorob.bioeta.science.LoadQuality
import java.time.LocalDate

/** Chiavi delle schermate di dettaglio: le metriche di Connessione Salute più due serie calcolate. */
const val DETAIL_LOAD = "LOAD"
const val DETAIL_ACTIVE = "ACTIVE"

private val RANGES = listOf(7 to "7 g", 30 to "30 g", 90 to "90 g", 365 to "1 anno")

/** Spiegazione di ogni parametro: cosa misura, perché varia, come leggerlo. Nessuna promessa clinica. */
private fun explain(key: String): String = when (key) {
    "RHR" -> "Frequenza cardiaca a riposo: battiti al minuto nei momenti di massimo riposo (di solito di notte), calcolata dall'orologio. Si abbassa con l'allenamento aerobico e aumenta con sonno scarso, alcol, stress, disidratazione e infezioni. Conta il confronto con la tua media, non con quella della popolazione."
    "HRV" -> "Variabilità della frequenza cardiaca (RMSSD notturno): quanto varia l'intervallo tra un battito e l'altro. Valori più alti del tuo solito indicano in genere un tono parasimpatico (recupero) maggiore. È molto individuale: confrontala solo con te stesso, guardando la tendenza di più giorni."
    "SLEEP" -> "Tempo di sonno effettivo (esclusi i risvegli) attribuito al giorno del risveglio. Per gli adulti sono raccomandate 7–9 ore (consensus AASM/SRS). La regolarità degli orari conta quanto la durata."
    "STEPS" -> "Passi giornalieri. Nelle meta-analisi di coorte il rischio di mortalità scende fino a circa 8–10 mila passi sotto i 60 anni e 6–8 mila sopra (Paluch 2022). La giornata in corso è parziale."
    "VO2" -> "VO₂max stimato dall'orologio (non misurato con un test cardiopolmonare): capacità aerobica massima in mL/kg/min. È il dato usato per l'età cardiorespiratoria (riferimenti FRIEND). Le stime da polso hanno un errore tipico di alcuni mL/kg/min."
    "RESP" -> "Frequenza respiratoria nel sonno. È stabile nella stessa persona: un aumento di oltre 1–2 atti/min rispetto al solito può accompagnare febbre o infezioni respiratorie."
    "SPO2" -> "Saturazione dell'ossigeno stimata dal sensore ottico. Valori singoli bassi sono spesso artefatti di movimento: conta la tendenza."
    "WEIGHT" -> "Peso registrato da una bilancia o inserito in un'app. Le oscillazioni di 0,5–2 kg da un giorno all'altro sono soprattutto acqua: guarda la media di 7 giorni."
    "BODY_FAT" -> "Percentuale di grasso corporeo, di solito da bilance a bioimpedenza: misura indiretta, sensibile a idratazione e orario. Utile come tendenza, non come valore assoluto."
    "LEAN_MASS" -> "Massa magra stimata (bioimpedenza o altre fonti). Stessi limiti del grasso corporeo: interpretala come tendenza."
    DETAIL_LOAD -> "Carico cardiaco: TRIMP di Banister calcolato minuto per minuto dalla frequenza cardiaca, sommando solo i minuti sopra il 30% della riserva cardiaca. Misura lo stress cardiovascolare della giornata, non il lavoro muscolare né le calorie. Se la frequenza cardiaca copre meno di 10 ore il valore è parziale (sottostimato); senza frequenza cardiaca non viene calcolato."
    DETAIL_ACTIVE -> "Minuti attivi secondo l'OMS: minuti moderati (40–59% della riserva cardiaca) più il doppio di quelli vigorosi (≥ 60%). Obiettivo: 150 a settimana. I minuti degli allenamenti senza frequenza cardiaca sono stimati dal tipo di attività (Compendio delle attività fisiche 2024) e mostrati separatamente."
    else -> ""
}

@Composable
fun MetricDetailScreen(vm: AppViewModel, key: String, onBack: () -> Unit) {
    var range by rememberSaveable { mutableIntStateOf(1) }
    val days = RANGES[range].first
    val today = LocalDate.now()
    val from = today.minusDays(days.toLong() - 1)
    val metric = Metric.entries.firstOrNull { it.name == key }
    val title = when (key) { DETAIL_LOAD -> "Carico cardiaco"; DETAIL_ACTIVE -> "Minuti attivi"; else -> metric?.label ?: key }
    val tint = when (key) { DETAIL_LOAD -> Tint.load; DETAIL_ACTIVE -> Tint.good; else -> metric?.let { metricTint(it) } ?: Tint.blue }

    SubScaffold(title, onBack) { m ->
        LazyColumn(m.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { SegmentPill(RANGES.map { it.second }, range, onSelect = { range = it }) }

            if (metric != null) {
                val pts = vm.wearable[metric].orEmpty().filter { !it.date.isBefore(from) }
                item {
                    HealthCard(title, metricIcon(metric), tint, trailing = RANGES[range].second) {
                        if (pts.isEmpty()) {
                            EmptyState(metricIcon(metric), "Nessun dato nel periodo",
                                "Connessione Salute non contiene ${metric.label.lowercase()} per questo intervallo. Controlla permessi e sincronizzazione dell'app sorgente in Profilo → Fonti e sincronizzazione.", tint)
                        } else {
                            val vals = pts.map { it.value }
                            Row(verticalAlignment = Alignment.Bottom) {
                                BigValue(fmtValue(metric, vals.average()), if (metric == Metric.SLEEP) null else metric.unit, size = 34)
                                Spacer(Modifier.width(8.dp))
                                Caption("media del periodo")
                            }
                            HealthChart(
                                listOf(ChartLayer(pts.map { it.date.toEpochDay().toDouble() to it.value }, tint, if (pts.size > 45) LayerKind.LINE else LayerKind.AREA, emphasizeLast = true)),
                                yDecimals = if (metric == Metric.STEPS) 0 else minOf(metric.decimals, 1), height = 220, unit = if (metric == Metric.SLEEP) "ore" else metric.unit,
                                valueLabel = { v -> fmtValue(metric, v) },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatChip("Minimo", fmtValue(metric, vals.min()), if (metric == Metric.SLEEP) "" else metric.unit, Modifier.weight(1f))
                                StatChip("Massimo", fmtValue(metric, vals.max()), if (metric == Metric.SLEEP) "" else metric.unit, Modifier.weight(1f))
                                StatChip("Giorni", "${pts.size}", "con dati", Modifier.weight(1f))
                            }
                            val all = vm.wearable[metric].orEmpty()
                            if (all.size >= 3) HealthRepo.summarize(metric, all).z?.let { z ->
                                Caption("Ultimi 7 giorni rispetto alla tua baseline (giorni 8–67): ${z.fs(1)} deviazioni standard.")
                            }
                        }
                    }
                }
            } else if (key == DETAIL_LOAD || key == DETAIL_ACTIVE) {
                val acts = vm.dayActivities.filter { !it.date.isBefore(from) }
                item {
                    HealthCard(title, if (key == DETAIL_LOAD) BioIcons.Pulse else BioIcons.Bolt, tint, trailing = RANGES[range].second) {
                        if (acts.isEmpty()) {
                            EmptyState(BioIcons.Pulse, "Ancora nessun calcolo",
                                "Servono la frequenza cardiaca o gli allenamenti da Connessione Salute. Lo storico calcolato copre al massimo le ultime 4 settimane.", tint)
                        } else if (key == DETAIL_LOAD) {
                            val ok = acts.filter { it.quality != LoadQuality.UNAVAILABLE }
                            if (ok.isEmpty()) InlineNotice("Nessun giorno con frequenza cardiaca nel periodo: il carico non è calcolabile.", NoticeKind.WARN)
                            else {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    BigValue(ok.map { it.load }.average().f(0), "al giorno", size = 34)
                                }
                                DayBars(loadBars(vm, minOf(days, 28)), Tint.load, "carico", band = vm.targetLoad?.let { it.low to it.high }, height = 180)
                                val partial = acts.count { it.quality == LoadQuality.PARTIAL }
                                val missing = acts.count { it.quality == LoadQuality.UNAVAILABLE }
                                Caption("Giorni completi ${acts.count { it.quality == LoadQuality.COMPLETE }}, parziali $partial (meno di 10 h di FC), senza FC $missing (esclusi dal grafico).")
                            }
                        } else {
                            val total = acts.sumOf { it.activeMinutes }
                            val est = acts.sumOf { it.estimatedShare }
                            Row(verticalAlignment = Alignment.Bottom) { BigValue("$total", "min nel periodo", size = 34) }
                            HealthChart(
                                listOf(ChartLayer(acts.map { it.date.toEpochDay().toDouble() to it.activeMinutes.toDouble() }, tint, LayerKind.AREA, emphasizeLast = true)),
                                height = 190, unit = "min",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatChip("Da FC", "${total - est}", "min", Modifier.weight(1f))
                                StatChip("Stimati", "$est", "min", Modifier.weight(1f))
                            }
                            if (est > 0) Caption("I minuti stimati vengono da allenamenti registrati senza frequenza cardiaca: intensità attribuita dal tipo di attività, in modo prudente.")
                        }
                    }
                }
            }
            item {
                HealthCard("Come leggerlo", BioIcons.Info, Tint.blue) { Caption(explain(key)) }
            }
        }
    }
}
