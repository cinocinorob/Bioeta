package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.science.JournalTags
import it.cinocinorob.bioeta.science.answer
import it.cinocinorob.bioeta.science.JItem
import it.cinocinorob.bioeta.science.JKind
import it.cinocinorob.bioeta.science.JournalEntry
import it.cinocinorob.bioeta.science.JournalCatalog
import it.cinocinorob.bioeta.science.withAnswer
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.testTag
import java.time.LocalDate

internal val MOOD_LABELS = listOf("Pessimo", "Giù", "Normale", "Bene", "Ottimo")
internal val MOOD_COLORS = listOf(Color(0xFFFF5A4F), Color(0xFFFF9B6A), Color(0xFFF5C85C), Color(0xFF8EDDB4), Color(0xFF4FD1C5))

/** Selettore 1–5: cinque segmenti di una scala unica; quello scelto si riempie del suo colore. */
@Composable
fun ScalePicker(value: Int?, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))) {
        (1..5).forEach { v ->
            val sel = value == v
            val c = MOOD_COLORS[v - 1]
            Column(
                Modifier.weight(1f)
                    .background(if (sel) c else p.field)
                    .clickable { onChange(v) }.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(width = 18.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(if (sel) Color(0x660C1C20) else c))
                Text(MOOD_LABELS[v - 1], fontSize = 12.sp, color = if (sel) Color(0xFF0C1C20) else p.text2,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChips(selected: Set<String>, onToggle: (String) -> Unit) {
    val p = LocalPalette.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        JournalTags.ALL.forEach { t ->
            val sel = t in selected
            Text(
                t, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (sel) Color.White else p.text,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (sel) Tint.sleep else p.field)
                    .clickable { onToggle(t) }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun JournalQuickCard(vm: AppViewModel, onOpen: () -> Unit) {
    val p = LocalPalette.current
    val e = vm.journalFor(LocalDate.now())
    HealthCard("Diario di oggi", BioIcons.Pencil, Tint.sleep, onClick = onOpen) {
        Text("Come ti senti?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.text)
        ScalePicker(e.mood) { vm.saveJournal(e.copy(mood = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Acqua", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.text, modifier = Modifier.weight(1f))
            RoundStep("−") { vm.saveJournal(e.copy(water = (e.water - 1).coerceAtLeast(0))) }
            Text("${e.water} bicchieri", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = p.text, modifier = Modifier.padding(horizontal = 12.dp))
            RoundStep("+") { vm.saveJournal(e.copy(water = (e.water + 1).coerceAtMost(30))) }
        }
        if (e.tags.isNotEmpty()) Caption("Oggi: ${e.tags.joinToString(" · ")}")
        Caption("Tocca per aggiungere energia, abitudini e note: con il tempo vedrai cosa influenza il tuo recupero.")
    }
}

@Composable
private fun RoundStep(label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(34.dp).clip(CircleShape).background(p.field).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
    }
}

private fun dayLabel(d: LocalDate): String {
    val t = LocalDate.now()
    val base = d.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.ITALIAN))
    return when (d) { t -> "Oggi, $base"; t.minusDays(1) -> "Ieri, $base"; else -> base.replaceFirstChar { it.uppercase() } }
}

/**
 * Diario in stile WHOOP: per il giorno scelto, una domanda per ogni abitudine seguita con risposta esplicita
 * Sì/No (o quantità). Le risposte alimentano le Scoperte; un giorno senza risposta resta «sconosciuto».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(vm: AppViewModel, onBack: () -> Unit, onOpenDiscoveries: () -> Unit = {}, initialDay: LocalDate = LocalDate.now()) {
    val p = LocalPalette.current
    var day by remember { mutableStateOf(initialDay) }
    val e = vm.journalFor(day)
    var note by remember(day) { mutableStateOf(e.note) }
    var manage by remember { mutableStateOf(false) }
    if (manage) ManageItemsDialog(vm) { manage = false }
    val items = vm.trackedItems
    val (answered, total) = vm.journalProgress(day)
    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(title = {}, navigationIcon = { IconButton(onClick = onBack) { Icon(BioIcons.Back, "Indietro", tint = p.text) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg))
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().testTag("journal_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { LargeHeader("Diario", dayLabel(day)) }
                    RoundStep("‹") { day = day.minusDays(1) }
                    Spacer(Modifier.width(8.dp))
                    RoundStep("›") { if (day.isBefore(LocalDate.now())) day = day.plusDays(1) }
                }
            }
            // Completamento e serie
            item {
                HealthCard {
                    Row(verticalAlignment = Alignment.Bottom) {
                        BigValue("$answered", "di $total risposte", size = 28)
                        Spacer(Modifier.weight(1f))
                        val st = vm.journalStreak
                        if (st > 0) Text("Serie: $st ${if (st == 1) "giorno" else "giorni"}", style = MaterialTheme.typography.labelLarge, color = p.brand)
                    }
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { if (total == 0) 0f else answered / total.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = p.brand, trackColor = p.field, drawStopIndicator = {},
                    )
                    Caption("Rispondi su ${if (day == LocalDate.now()) "oggi" else "quella giornata"}: le risposte «No» contano quanto i «Sì», perché permettono il confronto.")
                }
            }
            item {
                HealthCard("Come stai", BioIcons.Sparkle, Tint.mood) {
                    Text("Umore", style = MaterialTheme.typography.labelLarge, color = p.text2)
                    ScalePicker(e.mood) { vm.saveJournal(e.copy(mood = it)) }
                    Text("Energia", style = MaterialTheme.typography.labelLarge, color = p.text2)
                    ScalePicker(e.energy) { vm.saveJournal(e.copy(energy = it)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Acqua", style = MaterialTheme.typography.labelLarge, color = p.text2, modifier = Modifier.weight(1f))
                        RoundStep("−") { vm.saveJournal(e.copy(water = (e.water - 1).coerceAtLeast(0))) }
                        Text("${e.water} bicchieri", style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.padding(horizontal = 12.dp))
                        RoundStep("+") { vm.saveJournal(e.copy(water = (e.water + 1).coerceAtMost(30))) }
                    }
                }
            }
            items.groupBy { it.cat }.toSortedMap(compareBy { it.ordinal }).forEach { (cat, list) ->
                item(key = "cat_" + cat.name) {
                    HealthCard(cat.label) {
                        list.forEachIndexed { i, it ->
                            if (i > 0) androidx.compose.material3.HorizontalDivider(color = p.sep)
                            QuestionRow(it, e) { upd -> vm.saveJournal(upd) }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { vm.saveJournal(items.fold(e) { acc, it -> if (acc.answer(it.key) == null) acc.withAnswer(it.key, false) else acc }) },
                        enabled = answered < total, modifier = Modifier.weight(1f),
                    ) { Text("Le altre: No", maxLines = 1) }
                    androidx.compose.material3.OutlinedButton(onClick = { manage = true }, modifier = Modifier.weight(1f).testTag("journal_manage")) { Text("Scegli le domande", maxLines = 1) }
                }
            }
            item {
                HealthCard("Note", BioIcons.Doc, Tint.body) {
                    OutlinedTextField(
                        note, { note = it }, modifier = Modifier.fillMaxWidth().height(110.dp), shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = p.field, unfocusedContainerColor = p.field,
                            unfocusedBorderColor = Color.Transparent, focusedBorderColor = p.brand),
                    )
                    Button(onClick = { vm.saveJournal(e.copy(note = note.trim())) }) { Text("Salva nota") }
                }
            }
            item { MoodTrendCard(vm) }
            item { DiscoveriesCard(vm, onOpenDiscoveries) }
        }
    }
}

/** Una domanda: Sì/No; per le quantità, contatore quando la risposta è Sì. */
@Composable
private fun QuestionRow(item: JItem, e: JournalEntry, onChange: (JournalEntry) -> Unit) {
    val p = LocalPalette.current
    val ans = e.answer(item.key)
    var showWhy by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 4.dp).testTag("q_" + item.key)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).then(if (item.why.isNotEmpty()) Modifier.clickable { showWhy = !showWhy } else Modifier)) {
                Text(item.label, style = MaterialTheme.typography.bodyLarge, color = p.text)
                if (showWhy) Text(item.why, style = MaterialTheme.typography.bodySmall, color = p.text2)
            }
            Spacer(Modifier.width(8.dp))
            YesNo(ans) { v ->
                val amount = if (v == true && item.kind == JKind.COUNT) (e.amounts[item.key] ?: 1.0) else null
                onChange(e.withAnswer(item.key, v, amount))
            }
        }
        if (ans == true && item.kind == JKind.COUNT) {
            val n = (e.amounts[item.key] ?: 1.0).toInt()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text("Quanti?", style = MaterialTheme.typography.labelLarge, color = p.text2, modifier = Modifier.weight(1f))
                RoundStep("−") { if (n > 1) onChange(e.withAnswer(item.key, true, (n - 1).toDouble())) }
                Text("$n ${item.unit ?: ""}", style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.padding(horizontal = 12.dp))
                RoundStep("+") { if (n < item.max) onChange(e.withAnswer(item.key, true, (n + 1).toDouble())) }
            }
        }
    }
}

/** Interruttore a due posizioni; toccare di nuovo la scelta la annulla. */
@Composable
private fun YesNo(value: Boolean?, onChange: (Boolean?) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(p.field)) {
        listOf(false to "No", true to "Sì").forEach { (v, l) ->
            val sel = value == v
            Box(
                Modifier.width(52.dp).height(36.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) p.text else Color.Transparent)
                    .clickable { onChange(if (sel) null else v) }.testTag(if (v) "yes" else "no"),
                contentAlignment = Alignment.Center,
            ) { Text(l, style = MaterialTheme.typography.labelLarge, color = if (sel) p.bg else p.text2) }
        }
    }
}

/** Scelta delle domande da seguire, per categoria, più voci personalizzate. */
@Composable
private fun ManageItemsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var custom by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Domande del diario") },
        text = {
            LazyColumn(Modifier.height(480.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item { Caption("Scegli cosa vuoi monitorare. Meglio poche domande a cui rispondi ogni giorno che molte lasciate vuote.") }
                JournalCatalog.ITEMS.groupBy { it.cat }.forEach { (cat, list) ->
                    item { Text(cat.label, style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.padding(top = 10.dp)) }
                    list.forEach { ji ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { vm.setTracked(ji.key, ji.key !in vm.journalTracked) }) {
                                androidx.compose.material3.Checkbox(ji.key in vm.journalTracked, { vm.setTracked(ji.key, it) })
                                Text(ji.label, style = MaterialTheme.typography.bodyMedium, color = p.text)
                            }
                        }
                    }
                }
                val customs = vm.journalTracked.filter { it.startsWith("custom:") }
                item { Text("Le tue voci", style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.padding(top = 10.dp)) }
                customs.forEach { k ->
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(k.removePrefix("custom:"), style = MaterialTheme.typography.bodyMedium, color = p.text, modifier = Modifier.weight(1f))
                            androidx.compose.material3.TextButton(onClick = { vm.setTracked(k, false) }) { Text("Togli") }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(custom, { custom = it.take(40) }, placeholder = { Text("Nuova voce") }, singleLine = true, modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { vm.addCustomItem(custom); custom = "" }, enabled = custom.isNotBlank()) { Text("Aggiungi") }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Fatto") } },
    )
}

/** "Cosa influenza il tuo recupero": confronto personale con/senza ciascun tag. */
@Composable
fun TagEffectsCard(vm: AppViewModel) {
    val p = LocalPalette.current
    val eff = vm.tagEffects
    HealthCard("Cosa influenza il tuo recupero", BioIcons.Sparkle, Tint.good) {
        if (eff.isEmpty()) {
            Caption("Compila il diario per qualche settimana: quando un'abitudine compare in almeno 4 giorni (e manca in almeno 4), qui vedrai come cambia in media il tuo recupero del mattino dopo.")
        } else {
            eff.forEach { t ->
                val c = if (t.diff < -2) Tint.bad else if (t.diff > 2) Tint.good else p.text2
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(t.tag, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                        Text("${t.nWith} giorni con · ${t.nWithout} senza", fontSize = 12.sp, color = p.text2)
                    }
                    Text("${if (t.diff >= 0) "+" else "−"}${kotlin.math.abs(t.diff).f(0)} punti", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c)
                }
            }
            Caption("Differenza media del recupero del mattino successivo. È un'associazione nei tuoi dati, con pochi giorni: può dipendere dal caso o da altri fattori insieme (es. alcol e cena tardiva nella stessa sera).")
        }
    }
}
