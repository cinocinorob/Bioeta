package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.health.Metric
import it.cinocinorob.bioeta.science.DayPart
import it.cinocinorob.bioeta.science.DayScore
import it.cinocinorob.bioeta.science.DayScoreCalc
import it.cinocinorob.bioeta.science.GoalState
import it.cinocinorob.bioeta.science.MonthLine
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

internal fun partColor(p: DayPart): Color = when (p) {
    DayPart.RECOVERY -> Tint.brand
    DayPart.SLEEP -> Tint.sleep
    DayPart.MOVE -> Tint.activity
    DayPart.TRAIN -> Tint.load
    DayPart.FUEL -> Tint.food
    DayPart.MIND -> Tint.mood
}

private const val DAY_INFO =
    "Il punteggio del giorno riassume in un numero sei parti che BioEtà misura già: Recupero, Sonno, Movimento (quanto dell'obiettivo di oggi), Allenamento (carico rispetto alla tua fascia), Alimentazione (riferimenti rispettati nell'ultimo giorno completo: proteine, fibre, grassi saturi, sodio) e Benessere (umore ed energia del diario).\n\n" +
        "È la media semplice delle parti disponibili: nessuna pesa più delle altre, e una parte senza dati non viene contata (non vale zero). Servono almeno 3 parti. Nei giorni di riposo consigliato, poco carico vale pieno.\n\n" +
        "Come il Fit Score di Wellness Project, è un riepilogo di progetto, non un indice validato: non predice la salute. Per decidere guarda le singole parti."

/** Anello in sei spicchi (uno per parte, riempito secondo il suo punteggio) con il totale al centro. */
@Composable
private fun PartsRing(d: DayScore, sizeDp: Int) {
    val p = LocalPalette.current
    val anim = animatedValue(1f, 1000)
    val all = DayPart.entries
    val byPart = d.parts.associateBy { it.part }
    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(sizeDp.dp)) {
            val st = sizeDp.dp.toPx() * 0.11f
            val inset = st / 2
            val arc = Size(size.width - st, size.height - st)
            val gap = 6f
            val seg = 360f / all.size
            all.forEachIndexed { i, part ->
                val start = -90f + i * seg + gap / 2
                val sweep = seg - gap
                val c = partColor(part)
                drawArc(c.copy(alpha = if (p.dark) 0.22f else 0.16f), start, sweep, false, Offset(inset, inset), arc, style = Stroke(st, cap = StrokeCap.Round))
                byPart[part]?.let { ps -> drawArc(c, start, sweep * ps.score / 100f * anim, false, Offset(inset, inset), arc, style = Stroke(st, cap = StrokeCap.Round)) }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(d.score?.toString() ?: "—", style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = (sizeDp * 0.30).sp, fontFeatureSettings = TABULAR), color = p.text)
            Text("oggi", style = MaterialTheme.typography.labelMedium, color = p.text2)
        }
    }
}

/** Riquadro in cima alla Home: il tuo giorno in un colpo d'occhio, con le sei parti visibili. */
@Composable
fun DayScoreCard(vm: AppViewModel) {
    val d = vm.dayScore
    val p = LocalPalette.current
    var info by rememberSaveable { mutableStateOf(false) }
    if (info) InfoDialog("Il tuo giorno", DAY_INFO) { info = false }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(p.tone(Tint.brand, 1.6f), p.tone(Tint.sleep, 1.2f), p.tone(Tint.activity, 1.2f))))
            .clickable { info = true }.padding(18.dp).testTag("day_score"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
    // Briefing: una frase che dice cosa conta oggi (sostituisce più schede)
    Text(vm.briefing, style = MaterialTheme.typography.titleMedium, color = p.text, modifier = Modifier.testTag("briefing"))
    Row(verticalAlignment = Alignment.CenterVertically) {
        PartsRing(d, 118)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                when (val s = d.score) {
                    null -> "Servono più dati"
                    in 80..100 -> "Giornata piena"
                    in 60..79 -> "Buona giornata"
                    in 40..59 -> "Giornata a metà"
                    else -> "Giornata leggera"
                },
                style = MaterialTheme.typography.titleMedium, color = p.text,
            )
            d.parts.forEach { ps ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(partColor(ps.part)))
                    Spacer(Modifier.width(6.dp))
                    Text(ps.part.label, style = MaterialTheme.typography.labelMedium, color = p.text2, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${ps.score}", style = MaterialTheme.typography.labelLarge, color = p.text)
                }
            }
            if (d.score == null) Caption("Il punteggio compare con almeno ${DayScoreCalc.MIN_PARTS} parti.")
        }
    }
    }
}

/** Avviso solo quando sei nella tua fascia ferma abituale (momenti sedentari). */
@Composable
fun SedentaryNudge(vm: AppViewModel) {
    if (!vm.sedentaryNudge) return
    val h = vm.sedentary.hour ?: return
    InlineNotice("Di solito tra le $h e le ${h + 2} ti muovi poco. Bastano 5 minuti a piedi adesso: le pause attive abbassano la glicemia dopo i pasti.", NoticeKind.INFO)
}

private const val SED_INFO =
    "BioEtà cerca, nei tuoi dati, un giorno della settimana (ultimi 56 giorni) e una fascia di 2 ore (ultimi 28 giorni, tra le 8 e le 22) in cui ti muovi meno in modo ricorrente: un giorno con la mediana dei passi sotto il 75% della tua, e una fascia con meno di 250 passi in almeno 6 giorni su 10. Non è un timer: l'avviso in Home compare solo quando sei in quella fascia e finora ti sei mosso poco.\n\n" +
        "Perché conta: interrompere la sedentarietà con brevi pause attive riduce glicemia e insulina dopo i pasti (meta-analisi di 37 studi, Loh 2020), con un effetto maggiore in chi ha un BMI più alto. Le soglie sono scelte di BioEtà. Idea ripresa da Wellness Project."

@Composable
fun SedentaryCard(vm: AppViewModel) {
    val s = vm.sedentary
    HealthCard("Momenti sedentari", BioIcons.Person, Tint.activity, info = SED_INFO, modifier = Modifier.testTag("sedentary_card")) {
        val day = s.weekday?.getDisplayName(JTextStyle.FULL, Locale.ITALIAN)
        when {
            s.days < 14 && s.overallMedian == null -> Caption("Servono almeno 2–4 settimane di passi per riconoscere un'abitudine.")
            day == null && s.hour == null -> Caption("Nessun momento fermo ricorrente: il tuo movimento è distribuito bene nella settimana e nella giornata.")
            else -> {
                if (day != null) Text("Il $day ti muovi meno: mediana di ${String.format(Locale.ITALY, "%,d", s.weekdayMedian ?: 0)} passi contro ${String.format(Locale.ITALY, "%,d", s.overallMedian ?: 0)} degli altri giorni.",
                    style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.text)
                s.hour?.let { h ->
                    Text("Tra le $h e le ${h + 2} resti quasi fermo in ${((s.hourShare ?: 0.0) * 100).toInt()}% dei giorni.", style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.text)
                }
                Caption("Un piccolo cambiamento proprio lì è il più facile da ripetere: una camminata dopo pranzo, una telefonata in piedi.")
            }
        }
    }
}

// ---------------- Mese in rassegna ----------------

private fun fmtLine(l: MonthLine, v: Double): String = when {
    l.unit == "min" && l.label.startsWith("Sonno") -> { val m = v.toInt(); "${m / 60} h ${"%02d".format(m % 60)}" }
    l.decimals > 0 -> String.format(Locale.ITALY, "%.${l.decimals}f", v) + if (l.unit.isNotEmpty()) " ${l.unit}" else ""
    else -> String.format(Locale.ITALY, "%,.0f", v) + if (l.unit.isNotEmpty()) " ${l.unit}" else ""
}

@Composable
fun MonthScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = LocalPalette.current
    var offset by rememberSaveable { mutableStateOf(0L) }
    val month = java.time.YearMonth.now().minusMonths(offset)
    val r = vm.monthReview(month)
    val mname = month.month.getDisplayName(JTextStyle.FULL_STANDALONE, Locale.ITALIAN).replaceFirstChar { it.uppercase() }
    SubScaffold("Il tuo mese", onBack) { m ->
        LazyColumn(m.fillMaxSize().testTag("month_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { offset += 1 }, enabled = offset < 3) { Text("‹ Prima") }
                    Text("$mname ${month.year}", style = MaterialTheme.typography.titleLarge, color = p.text, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(onClick = { offset -= 1 }, enabled = offset > 0) { Text("Dopo ›") }
                }
            }
            item {
                HealthCard("Rispetto al mese prima", BioIcons.Sparkle, Tint.brand, modifier = Modifier.testTag("month_card")) {
                    Caption(if (offset == 0L) "Primi ${r.days} giorni del mese contro gli stessi giorni del mese precedente." else "Mese intero contro il precedente.")
                    r.lines.forEach { l -> MonthRow(l) }
                    Caption("Freccia verde = nella direzione favorevole. Sonno e peso non hanno una direzione «giusta» valida per tutti.")
                }
            }
            item { SectionHeader("Record personali") }
            item {
                HealthCard {
                    val recs = vm.records.values.sortedBy { it.id }
                    if (recs.isEmpty()) Caption("I record compaiono con qualche settimana di dati.")
                    recs.forEach { rec ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("record_${rec.id}")) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(p.toneStrong(Tint.lvlGold)), contentAlignment = Alignment.Center) {
                                Icon(BioIcons.Trophy, null, tint = p.ink(Tint.lvlGold), modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(rec.label, style = MaterialTheme.typography.titleSmall, color = p.text)
                                Text(rec.date.format(DateTimeFormatter.ofPattern(if (rec.id == "week_min") "'settimana del' d MMM yyyy" else "d MMM yyyy", Locale.ITALIAN)),
                                    style = MaterialTheme.typography.bodySmall, color = p.text2)
                            }
                            Text(String.format(Locale.ITALY, if (rec.decimals > 0) "%.${rec.decimals}f" else "%,.0f", rec.value) + " " + rec.unit,
                                style = MaterialTheme.typography.labelLarge, color = p.text)
                        }
                    }
                    Caption("I record restano salvati anche quando i giorni escono dalla finestra di 120 giorni di Connessione Salute.")
                }
            }
        }
    }
}

@Composable
private fun MonthRow(l: MonthLine) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(l.label, style = MaterialTheme.typography.bodyMedium, color = p.text, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(l.now?.let { fmtLine(l, it) } ?: "—", style = MaterialTheme.typography.labelLarge, color = p.text)
            val before = l.before
            if (l.now != null && before != null) {
                val d = l.now - before
                val up = d > 0
                val good = l.higherBetter?.let { hb -> if (abs(d) < 1e-9) null else up == hb }
                Text((if (abs(d) < 1e-9) "= " else if (up) "↑ " else "↓ ") + "prima ${fmtLine(l, before)}", style = MaterialTheme.typography.labelSmall,
                    color = when (good) { true -> p.ink(Tint.good); false -> p.ink(Tint.warn); null -> p.text3 })
            }
        }
    }
}

private fun abs(v: Double) = kotlin.math.abs(v)

// ---------------- Obiettivo di peso ----------------

private const val WEIGHT_INFO =
    "La data prevista viene dal ritmo delle pesate degli ultimi 28 giorni (regressione lineare, servono almeno 4 pesate in 14 giorni), con un intervallo dato dall'incertezza del ritmo. Sotto 0,05 kg a settimana l'obiettivo è «fermo». Il peso oscilla di 1–2 kg da un giorno all'altro per acqua, glicogeno e sale: pesati alla stessa ora, la mattina. Per perdere peso in modo sostenibile le linee guida indicano circa 0,5–1 kg a settimana. Idea ripresa dagli obiettivi di Wellness Project."

@Composable
fun WeightGoalCard(vm: AppViewModel) {
    val p = LocalPalette.current
    var editing by rememberSaveable { mutableStateOf(false) }
    val g = vm.weightGoal
    val cur = vm.wearable[Metric.WEIGHT]?.lastOrNull()?.value
    if (editing) WeightGoalDialog(vm, cur) { editing = false }
    HealthCard("Obiettivo di peso", BioIcons.Person, Tint.body, info = WEIGHT_INFO, modifier = Modifier.testTag("weight_goal")) {
        if (g == null) {
            Caption(if (cur == null) "Serve almeno una pesata in Connessione Salute (bilancia o app collegata)." else "Peso attuale ${String.format(Locale.ITALY, "%.1f", cur)} kg. Imposta un obiettivo per vedere quando lo raggiungi al ritmo attuale.")
            if (cur != null) OutlinedButton(onClick = { editing = true }) { Text("Imposta un obiettivo") }
            return@HealthCard
        }
        val pr = vm.weightProjection ?: return@HealthCard
        val f = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(pr.current?.let { String.format(Locale.ITALY, "%.1f", it) } ?: "—", style = TextStyle(fontFamily = DisplayFont, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR), color = p.text)
            Text(" → ${String.format(Locale.ITALY, "%.1f", g.target)} kg", style = MaterialTheme.typography.titleMedium, color = p.text2, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
            Pill(pr.state.label, when (pr.state) { GoalState.ON_TRACK, GoalState.REACHED -> Tint.good; GoalState.STALLED, GoalState.NEED_DATA -> Tint.blue; GoalState.WRONG_WAY -> Tint.warn })
        }
        Milestones(pr.progress)
        Caption(
            when (pr.state) {
                GoalState.REACHED -> "Obiettivo raggiunto: complimenti. Ora conta mantenerlo."
                GoalState.NEED_DATA -> "Servono almeno 4 pesate in 14 giorni per stimare il ritmo."
                GoalState.STALLED -> "Peso stabile nelle ultime 4 settimane (${String.format(Locale.ITALY, "%+.2f", pr.slopeWeek ?: 0.0)} kg a settimana)."
                GoalState.WRONG_WAY -> "Nelle ultime 4 settimane il peso va nella direzione opposta (${String.format(Locale.ITALY, "%+.2f", pr.slopeWeek ?: 0.0)} kg a settimana)."
                GoalState.ON_TRACK -> "Al ritmo attuale (${String.format(Locale.ITALY, "%+.2f", pr.slopeWeek ?: 0.0)} kg a settimana) arrivi intorno al ${pr.projected?.format(f)}" +
                    (if (pr.projectedEarly != null && pr.projectedLate != null) ", probabilmente tra il ${pr.projectedEarly.format(f)} e il ${pr.projectedLate.format(f)}." else ".")
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { editing = true }) { Text("Cambia") }
            TextButton(onClick = { vm.setWeightGoal(null) }) { Text("Rimuovi") }
        }
    }
}

/** Barra con le tappe al 25, 50, 75 e 100%. */
@Composable
private fun Milestones(progress: Double) {
    val p = LocalPalette.current
    Column {
        Track(progress.toFloat(), Tint.body)
        Row(Modifier.fillMaxWidth()) {
            listOf(0, 25, 50, 75, 100).forEach { m ->
                Text(if (m == 0) "inizio" else "$m%", style = MaterialTheme.typography.labelSmall,
                    color = if (progress * 100 >= m && m > 0) p.ink(Tint.body) else p.text3, modifier = Modifier.weight(1f),
                    textAlign = when (m) { 0 -> androidx.compose.ui.text.style.TextAlign.Start; 100 -> androidx.compose.ui.text.style.TextAlign.End; else -> androidx.compose.ui.text.style.TextAlign.Center })
            }
        }
    }
}

@Composable
private fun WeightGoalDialog(vm: AppViewModel, current: Double?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(vm.weightGoal?.target?.let { String.format(Locale.ITALY, "%.1f", it) } ?: "") }
    val v = text.replace(',', '.').toDoubleOrNull()
    val ok = v != null && v in 35.0..250.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Obiettivo di peso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                current?.let { Caption("Peso attuale ${String.format(Locale.ITALY, "%.1f", it)} kg: è il punto di partenza.") }
                OutlinedTextField(text, { text = it }, label = { Text("Peso obiettivo (kg)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("weight_input"))
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { vm.setWeightGoal(v); onDismiss() }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
