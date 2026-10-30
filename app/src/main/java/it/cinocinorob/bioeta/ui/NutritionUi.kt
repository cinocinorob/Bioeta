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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.science.NutStatus
import it.cinocinorob.bioeta.science.NutTarget
import it.cinocinorob.bioeta.science.Nutrition
import it.cinocinorob.bioeta.science.NutritionDay
import it.cinocinorob.bioeta.science.NutritionSummary
import java.time.LocalDate
import java.util.Locale

private const val NUT_INFO =
    "BioEtà non registra i pasti: li legge da Connessione Salute, dove li scrivono app come MyFitnessPal, Cronometer, Samsung Health, Yazio o Google Health, ex Fitbit (se ci registri il cibo). Se due app scrivono gli stessi pasti, per ogni giorno si usa solo quella con più calorie, così niente doppi conteggi.\n\n" +
        "Le medie usano solo i giorni «completi» (almeno 2 registrazioni e 1.000 kcal): un giorno registrato a metà le renderebbe fuorvianti.\n\n" +
        "Riferimenti: proteine ≥ 0,83 g/kg (EFSA), fino a circa 1,6 g/kg se ti alleni con i pesi (Morton 2018); fibre 25–29 g o più (Reynolds 2019, Lancet); grassi saturi < 10% dell'energia e sodio < 2.000 mg (OMS); potassio ≥ 3.500 mg; caffeina ≤ 400 mg (EFSA); acqua da bevande circa 2,0 L negli uomini e 1,6 L nelle donne (EFSA).\n\n" +
        "Limiti: chi registra i pasti tende a sottostimare quanto mangia; il sale aggiunto in cucina di solito manca. Sono indicazioni per la salute generale, non un piano alimentare."

@Composable
private fun nutColor(s: NutStatus): Color = when (s) {
    NutStatus.GOOD -> Tint.good
    NutStatus.MID -> Tint.warn
    NutStatus.LOW -> Tint.bad
    NutStatus.INFO, NutStatus.NA -> LocalPalette.current.text3
}

private fun kcal(x: Double) = String.format(Locale.ITALY, "%,d", x.toLong())

/** Scheda della Home: oggi in quattro numeri + stato di proteine e fibre. */
@Composable
fun NutritionCard(vm: AppViewModel, onOpen: () -> Unit, onConnect: () -> Unit) {
    val p = LocalPalette.current
    val s = vm.nutrition
    HealthCard("Alimentazione", BioIcons.Food, Tint.food, onClick = onOpen, info = NUT_INFO, modifier = Modifier.testTag("nutrition_card")) {
        when {
            !vm.nutritionGranted -> {
                Caption("Collega l'app con cui registri i pasti (MyFitnessPal, Cronometer, Samsung Health, Yazio, Google Health…): BioEtà legge pasti e acqua da Connessione Salute e li confronta con i riferimenti scientifici.")
                androidx.compose.material3.FilledTonalButton(onClick = onConnect) { Text("Consenti lettura dei pasti") }
            }
            s == null || s.days.isEmpty() -> Caption("Nessun pasto in Connessione Salute negli ultimi 4 settimane. Nell'app dei pasti attiva la condivisione con Connessione Salute.")
            else -> {
                val today = s.days.lastOrNull { it.date == LocalDate.now() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("Oggi", today?.let { kcal(it.kcal) } ?: "—", "kcal", Modifier.weight(1f))
                    StatChip("Proteine", today?.let { "%.0f".format(it.protein) } ?: "—", "g", Modifier.weight(1f))
                    StatChip("Fibre", today?.fiber?.let { "%.0f".format(it) } ?: "—", "g", Modifier.weight(1f))
                }
                val shown = s.targets.filter { it.key == "protein" || it.key == "fiber" }.filter { it.status != NutStatus.NA }
                if (shown.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { t -> Pill("${t.label}: ${t.status.label.lowercase()}", nutColor(t.status)) }
                }
                Caption("Media su ${s.completeDays} giorni completi negli ultimi ${s.window}.")
            }
        }
    }
}

/** Schermata completa della nutrizione. */
@Composable
fun NutritionScreen(vm: AppViewModel, onBack: () -> Unit, onOpenDiscoveries: () -> Unit, onConnect: () -> Unit) {
    val p = LocalPalette.current
    val s = vm.nutrition
    SubScaffold("Alimentazione", onBack) { m ->
        LazyColumn(
            m.fillMaxSize().testTag("nutrition_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!vm.nutritionGranted || s == null || s.days.isEmpty()) {
                item { NutritionCard(vm, onOpen = {}, onConnect = onConnect) }
                return@LazyColumn
            }
            item { TodayCard(s) }
            item { TargetsCard(s.targets, s.completeDays) }
            item { EnergyCard(s) }
            item { TimingCard(vm, s, onOpenDiscoveries) }
            item {
                HealthCard("Dati", BioIcons.Info, Tint.blue) {
                    Caption("Giorni completi: ${s.completeDays} su ${s.window}. Fonti: ${s.sources.joinToString(", ").ifEmpty { "—" }}.")
                    Caption("Chi registra i pasti tende a sottostimare quanto mangia, e il sale aggiunto in cucina di solito manca: considera questi numeri un limite inferiore.")
                }
            }
        }
    }
}

@Composable
private fun TodayCard(s: NutritionSummary) {
    val p = LocalPalette.current
    val today = s.days.lastOrNull { it.date == LocalDate.now() }
    HealthCard("Oggi", BioIcons.Food, Tint.food, info = NUT_INFO) {
        if (today == null || today.records == 0) { Caption("Nessun pasto registrato oggi."); return@HealthCard }
        Row(verticalAlignment = Alignment.Bottom) {
            BigValue(kcal(today.kcal), "kcal", size = 34)
            Spacer(Modifier.weight(1f))
            Caption("${today.records} registrazioni" + (today.lastMeal?.let { " · ultima alle ${Nutrition.fmtTime(it)}" } ?: ""))
        }
        MacroBar(today)
        val bars = s.days.filter { it.date.isAfter(LocalDate.now().minusDays(14)) }
        val byDate = bars.associateBy { it.date }
        DayBars((13 downTo 0).map { k -> val d = LocalDate.now().minusDays(k.toLong()); byDate[d].let { DayBar(d, it?.kcal?.takeIf { v -> v > 0 }, partial = it != null && !it.complete) } },
            Tint.food, "kcal", height = 110)
        Caption("Barre chiare = giorni registrati solo in parte (esclusi dalle medie).")
    }
}

/** Ripartizione dell'energia tra proteine, carboidrati e grassi (4/4/9 kcal per grammo). */
@Composable
private fun MacroBar(d: NutritionDay) {
    val p = LocalPalette.current
    val pe = d.protein * 4; val ce = d.carbs * 4; val fe = d.fat * 9
    val tot = (pe + ce + fe).takeIf { it > 0 } ?: return
    val parts = listOf(Triple("Proteine", pe, Tint.heart), Triple("Carboidrati", ce, Tint.activity), Triple("Grassi", fe, Tint.sleep))
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        var x = 0f
        val gap = 3.dp.toPx()
        parts.forEach { (_, e, c) ->
            val w = (e / tot).toFloat() * (size.width - gap * 2)
            if (w > 0) drawRoundRect(c, Offset(x, 0f), Size(w, size.height), CornerRadius(size.height / 2))
            x += w + gap
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        parts.forEach { (l, e, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(5.dp))
                val g = when (l) { "Grassi" -> d.fat; "Proteine" -> d.protein; else -> d.carbs }
                Text("$l ${"%.0f".format(g)} g · ${"%.0f".format(e / tot * 100)}%", style = MaterialTheme.typography.labelMedium, color = p.text2)
            }
        }
    }
}

@Composable
private fun TargetsCard(list: List<NutTarget>, complete: Int) {
    val p = LocalPalette.current
    HealthCard("Rispetto ai riferimenti", BioIcons.Shield, Tint.food, trailing = "media di $complete giorni", modifier = Modifier.testTag("nutrition_targets")) {
        list.forEachIndexed { i, t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.label, style = MaterialTheme.typography.titleSmall, color = p.text)
                    Text("Riferimento: ${t.reference}", style = MaterialTheme.typography.bodySmall, color = p.text2)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(t.value ?: "—", style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, fontFeatureSettings = TABULAR),
                            color = if (t.value == null) p.text3 else p.text)
                        if (t.value != null) Text(" ${t.unit}", style = MaterialTheme.typography.labelSmall, color = p.text2, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(nutColor(t.status)))
                        Spacer(Modifier.width(5.dp))
                        Text(t.status.label, style = MaterialTheme.typography.labelSmall, color = p.text2)
                    }
                }
            }
            Text(t.note, style = MaterialTheme.typography.bodySmall, color = p.text3)
            if (i < list.lastIndex) HorizontalDivider(color = p.sep)
        }
    }
}

@Composable
private fun EnergyCard(s: NutritionSummary) {
    val p = LocalPalette.current
    val e = s.expenditure
    HealthCard("Bilancio energetico", BioIcons.Bolt, Tint.activity, info =
        "Dispendio stimato come fa MacroFactor: calorie medie registrate meno la variazione delle riserve del corpo, ricavata dall'andamento del peso (7.700 kcal per kg, approssimazione valida su poche settimane). L'intervallo riflette quanto il peso oscilla tra una pesata e l'altra.\n\n" +
            "È coerente con il tuo modo di registrare i pasti: se registri sempre allo stesso modo, è il numero giusto per pianificare. Il dispendio dell'orologio è indicato per confronto: nelle revisioni gli errori individuali arrivano a circa il 20%.",
        modifier = Modifier.testTag("nutrition_energy"),
    ) {
        if (e == null) {
            Caption("Servono almeno 14 giorni di pasti completi e 4 pesate distribuite su 2 settimane nelle ultime 4. Pesati al mattino, a digiuno, 2–3 volte a settimana.")
            return@HealthCard
        }
        Text("Dispendio stimato", style = MaterialTheme.typography.labelLarge, color = p.text2)
        BigValue(kcal(e.kcal), "kcal/giorno", size = 34)
        if (e.high - e.low >= 50) Caption("Intervallo probabile ${kcal(e.low)}–${kcal(e.high)} kcal.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("Mangiate", kcal(e.intake), "kcal", Modifier.weight(1f))
            StatChip("Peso/sett.", (if (e.weightPerWeek >= 0) "+" else "−") + "%.1f".format(kotlin.math.abs(e.weightPerWeek)).replace('.', ','), "kg", Modifier.weight(1f))
            StatChip("Orologio", e.watchKcal?.let { kcal(it) } ?: "—", "kcal", Modifier.weight(1f))
        }
        val bal = e.intake - e.kcal
        Caption(when {
            kotlin.math.abs(e.weightPerWeek) < 0.1 -> "Peso stabile: stai mangiando quanto consumi."
            bal < 0 -> "In media ${kcal(-bal)} kcal al giorno sotto il dispendio: il peso scende di circa ${"%.1f".format(-e.weightPerWeek).replace('.', ',')} kg a settimana."
            else -> "In media ${kcal(bal)} kcal al giorno sopra il dispendio: il peso sale di circa ${"%.1f".format(e.weightPerWeek).replace('.', ',')} kg a settimana."
        } + " Basato su ${e.days} giorni e ${e.weighIns} pesate.")
    }
}

@Composable
private fun TimingCard(vm: AppViewModel, s: NutritionSummary, onOpenDiscoveries: () -> Unit) {
    val p = LocalPalette.current
    HealthCard("Orari", BioIcons.Moon, Tint.sleep, modifier = Modifier.testTag("nutrition_timing")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("Ultimo pasto", s.lastMealMedian?.let { Nutrition.fmtTime(it) } ?: "—", "", Modifier.weight(1f))
            StatChip("Al sonno", s.mealToSleepH?.let { "%.1f".format(it).replace('.', ',') } ?: "—", "h", Modifier.weight(1f))
            StatChip("Caffeina", s.lastCaffeineMedian?.let { Nutrition.fmtTime(it) } ?: "—", "", Modifier.weight(1f))
        }
        Caption("Orari tipici (mediana) dell'ultimo pasto e dell'ultima caffeina; «al sonno» = ore tra ultimo pasto e addormentamento.")
        val cut = vm.bedtimeGuide?.caffeineCutoff
        val lc = s.lastCaffeineMedian
        if (cut != null && lc != null) Caption(
            if (lc.isAfter(cut)) "Di solito prendi l'ultima caffeina dopo le ${it.cinocinorob.bioeta.science.Bedtime.fmt(cut)}: meno di 8,8 ore prima di dormire riduce il sonno totale (meta-analisi Gardiner 2023)."
            else "L'ultima caffeina arriva di solito prima delle ${it.cinocinorob.bioeta.science.Bedtime.fmt(cut)}, il limite per non accorciare il sonno.",
        )
        Caption("Sull'orario della cena la ricerca è ancora poco solida: per questo le Scoperte confrontano i tuoi giorni con pasto vicino al sonno e quelli senza, guardando sonno e HRV della notte.")
        androidx.compose.material3.TextButton(onClick = onOpenDiscoveries) { Text("Apri le Scoperte") }
    }
}
