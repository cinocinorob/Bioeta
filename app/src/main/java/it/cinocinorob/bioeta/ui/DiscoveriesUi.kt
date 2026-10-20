package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.science.AssocResult
import it.cinocinorob.bioeta.science.Associations
import it.cinocinorob.bioeta.science.Verdict

const val DISCOVERIES_INFO =
    "Come funziona. Per ogni abitudine (dal diario o rilevata dall'orologio) BioEtà confronta i giorni in cui c'è con quelli in cui non c'è, guardando la notte successiva e il mattino dopo: HRV, FC a riposo, sonno totale e profondo, efficienza, Recupero, umore.\n\n" +
        "• Ogni valore è confrontato con la TUA baseline mobile (mediana dei 28 giorni precedenti), così stagioni e periodi di forma non vengono scambiati per effetti.\n" +
        "• Per le etichette del diario contano come «senza» solo i giorni in cui hai compilato il diario.\n" +
        "• Servono almeno 5 giorni con e 5 senza, negli ultimi 120.\n" +
        "• Intervallo di confidenza al 95% con bootstrap; p con test di permutazione; correzione per confronti multipli di Benjamini‑Hochberg (FDR 10%) su tutte le coppie testate.\n" +
        "• Sotto una soglia di rilevanza (HRV 3%, FC 1 bpm, sonno 10 min, sonno profondo 5 min, efficienza 1,5 punti, Recupero 3 punti, umore 0,3) una differenza non viene considerata.\n\n" +
        "Confermata = supera la correzione e la soglia. Indizio = p < 0,10 senza correzione: da verificare con altri giorni.\n\n" +
        "Limiti: sono associazioni nei dati di una sola persona, non prove di causa. Abitudini che capitano insieme (per esempio alcol e cena tardiva) si confondono: quando succede lo segnaliamo. Per una prova vera serve un esperimento: cambiare una sola abitudine per 2–3 settimane."

private fun findingTitle(r: AssocResult): String {
    val o = r.outcome.label
    // minuscola solo per le parole comuni, non per le sigle (HRV, FC)
    val out = if (o.length > 1 && o[1].isLowerCase()) o.replaceFirstChar { it.lowercase() } else o
    return "${r.exposure.label} → $out"
}

/** Ampiezza dell'asse (in multipli della soglia di rilevanza) che contiene tutti gli intervalli, minimo ±4. */
private fun axisRange(rs: List<AssocResult>): Double =
    maxOf(4.0, rs.maxOfOrNull { maxOf(kotlin.math.abs(it.lowMid), kotlin.math.abs(it.highMid)) } ?: 4.0) * 1.1

/** Riepilogo in Home e in Salute: le scoperte più forti, oppure quanto manca. */
@Composable
fun DiscoveriesCard(vm: AppViewModel, onOpen: () -> Unit) {
    LaunchedEffect(Unit) { vm.refreshDiscoveries() }
    val d = vm.discoveries
    val p = LocalPalette.current
    HealthCard("Scoperte", BioIcons.Sparkle, Tint.good, onClick = onOpen, modifier = Modifier.testTag("discoveries_card")) {
        when {
            d == null -> { Caption("Analisi delle tue abitudini in corso…"); LoadingBar() }
            d.results.none { it.verdict != Verdict.NONE } -> {
                val next = d.pending.firstOrNull()
                Caption(
                    if (d.testedPairs > 0) "Nessuna associazione chiara finora su ${d.testedPairs} confronti. Continua a compilare il diario: più giorni, più precisione."
                    else "Segna le tue abitudini nel diario (alcol, caffè, cena tardiva, stress…): con 5 giorni «sì» e 5 «no» BioEtà ti dice come cambiano HRV, sonno e recupero." +
                        (next?.let { " Ora: ${it.exposure.label} ${it.nWith}/5." } ?: ""),
                )
            }
            else -> d.results.filter { it.verdict != Verdict.NONE }.take(3).forEach { r -> FindingRow(r, compact = true) }
        }
        if (d != null && d.results.any { it.verdict != Verdict.NONE }) Text("Tutte le scoperte", style = MaterialTheme.typography.labelLarge, color = p.brand)
    }
}

@Composable
fun DiscoveriesScreen(vm: AppViewModel, onBack: () -> Unit, onOpenJournal: () -> Unit) {
    LaunchedEffect(Unit) { vm.refreshDiscoveries() }
    val d = vm.discoveries
    SubScaffold("Scoperte", onBack) { m ->
        LazyColumn(m.fillMaxSize().testTag("discoveries_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HealthCard("Cosa cambia i tuoi numeri", BioIcons.Sparkle, Tint.good, info = DISCOVERIES_INFO) {
                    Caption("Confronto tra i giorni con e senza ciascuna abitudine, rispetto alla tua normalità. Le frecce indicano la notte e il mattino successivi.")
                    if (d == null || vm.discoveriesLoading) LoadingBar()
                }
            }
            if (d == null) return@LazyColumn
            val good = d.results.filter { it.verdict != Verdict.NONE && it.favorable }
            val bad = d.results.filter { it.verdict != Verdict.NONE && !it.favorable }
            if (good.isNotEmpty()) {
                item { SectionHeader("Cosa ti fa bene") }
                items(good, key = { "g" + it.exposure.key + it.outcome }) { r -> HealthCard { FindingRow(r, compact = false); CoNote(d.cooccurring[r.exposure.key]) } }
            }
            if (bad.isNotEmpty()) {
                item { SectionHeader("Cosa ti pesa") }
                items(bad, key = { "b" + it.exposure.key + it.outcome }) { r -> HealthCard { FindingRow(r, compact = false); CoNote(d.cooccurring[r.exposure.key]) } }
            }
            if (good.isEmpty() && bad.isEmpty()) item {
                EmptyState(BioIcons.Sparkle, "Nessuna associazione chiara, per ora",
                    if (d.testedPairs > 0) "Ho fatto ${d.testedPairs} confronti tra abitudini ed esiti: nessuno supera le soglie. Può voler dire che quelle abitudini su di te contano poco, o che servono più giorni."
                    else "Servono almeno 5 giorni con e 5 senza ciascuna abitudine. Il diario richiede pochi secondi al giorno.",
                    Tint.good, "Apri il diario", onOpenJournal)
            }

            // Grafico a foresta per abitudine
            val byExp = d.results.groupBy { it.exposure.key }
            if (byExp.isNotEmpty()) {
                item { SectionHeader("Per abitudine") }
                item { ForestLegend() }
                items(byExp.values.toList(), key = { "f" + it.first().exposure.key }) { rs ->
                    val e = rs.first().exposure
                    HealthCard(e.label, if (e.auto) BioIcons.Pulse else BioIcons.Pencil, if (e.auto) Tint.hrv else Tint.mood,
                        trailing = "${rs.maxOf { it.nWith }} sì · ${rs.maxOf { it.nWithout }} no") {
                        val range = axisRange(rs)
                        rs.sortedBy { it.outcome.ordinal }.forEach { r -> ForestRow(r, range) }
                        CoNote(d.cooccurring[e.key])
                    }
                }
            }
            if (d.pending.isNotEmpty()) {
                item { SectionHeader("In attesa di dati") }
                item {
                    HealthCard {
                        d.pending.take(12).forEach { pd ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(pd.exposure.label, style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.text, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("${minOf(pd.nWith, 5)}/5 sì · ${minOf(pd.nWithout, 5)}/5 no", style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.text2)
                            }
                            LinearProgressIndicator(
                                progress = { (minOf(pd.nWith, 5) + minOf(pd.nWithout, 5)) / 10f },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = Tint.good, trackColor = LocalPalette.current.field,
                            )
                        }
                        Caption("Le etichette del diario contano come «no» solo nei giorni in cui l'hai compilato.")
                        OutlinedButton(onClick = onOpenJournal) { Text("Apri il diario") }
                    }
                }
            }
            item { Caption("${d.testedPairs} confronti, con correzione per confronti multipli (FDR 10%). Associazioni, non cause.") }
        }
    }
}

@Composable
private fun CoNote(others: List<String>?) {
    if (others.isNullOrEmpty()) return
    Caption("Spesso nella stessa giornata di: ${others.joinToString(", ")}. Gli effetti possono sovrapporsi.")
}

/** Una scoperta: titolo, effetto con intervallo, distintivo e numeri. */
@Composable
private fun FindingRow(r: AssocResult, compact: Boolean) {
    val p = LocalPalette.current
    val c = if (r.favorable) Tint.good else Tint.bad
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(findingTitle(r), style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 2)
            Text("${r.verdict.label} · ${r.nWith} giorni con, ${r.nWithout} senza", style = MaterialTheme.typography.bodySmall, color = p.text2)
            if (!compact) Text("Intervallo 95%: ${Associations.ciText(r)} ${r.outcome.unit}", style = MaterialTheme.typography.bodySmall, color = p.text3)
        }
        Text(Associations.effectText(r), style = androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontFeatureSettings = TABULAR), color = c)
    }
    if (!compact) ForestBar(r, Modifier.fillMaxWidth().height(22.dp), axisRange(listOf(r)))
}

@Composable
private fun ForestLegend() {
    val p = LocalPalette.current
    Row(Modifier.padding(horizontal = 4.dp)) {
        Text("← peggio", style = MaterialTheme.typography.labelSmall, color = Tint.bad, modifier = Modifier.weight(1f))
        Text("fascia grigia = sotto la soglia di rilevanza", style = MaterialTheme.typography.labelSmall, color = p.text3, textAlign = TextAlign.Center, modifier = Modifier.weight(2f))
        Text("meglio →", style = MaterialTheme.typography.labelSmall, color = Tint.good, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

/** Riga del grafico a foresta: esito, punto e intervallo orientati in modo che destra = meglio. */
@Composable
private fun ForestRow(r: AssocResult, range: Double) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(r.outcome.label, style = MaterialTheme.typography.bodySmall, color = p.text, modifier = Modifier.width(112.dp), maxLines = 2)
        ForestBar(r, Modifier.weight(1f).height(20.dp), range)
        Text(Associations.effectText(r), style = MaterialTheme.typography.labelMedium,
            color = when { r.verdict == Verdict.NONE -> p.text3; r.favorable -> Tint.good; else -> Tint.bad },
            modifier = Modifier.width(78.dp), textAlign = TextAlign.End, maxLines = 1)
    }
}

@Composable
private fun ForestBar(r: AssocResult, modifier: Modifier, range: Double = 4.0) {
    val p = LocalPalette.current
    val sign = if (r.outcome.higherBetter) 1.0 else -1.0
    val e = r.effMid * sign
    val lo = minOf(r.lowMid * sign, r.highMid * sign); val hi = maxOf(r.lowMid * sign, r.highMid * sign)
    val c = when { r.verdict == Verdict.NONE -> p.text3; e > 0 -> Tint.good; else -> Tint.bad }
    val band = p.field; val axis = p.sep
    Canvas(modifier) {
        fun x(v: Double) = ((v.coerceIn(-range, range) + range) / (2 * range)).toFloat() * size.width
        val y = size.height / 2
        drawRect(band, Offset(x(-1.0), 0f), Size(x(1.0) - x(-1.0), size.height))
        drawLine(axis, Offset(x(0.0), 0f), Offset(x(0.0), size.height), 1.dp.toPx())
        drawLine(c, Offset(x(lo), y), Offset(x(hi), y), 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(c, 5.dp.toPx(), Offset(x(e), y))
    }
}
