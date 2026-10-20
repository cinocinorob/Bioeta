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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = LocalPalette.current
    var day by remember { mutableStateOf(LocalDate.now()) }
    val e = vm.journalFor(day)
    var note by remember(day) { mutableStateOf(e.note) }
    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(title = {}, navigationIcon = { IconButton(onClick = onBack) { Icon(BioIcons.Back, "Indietro", tint = Tint.blue) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg))
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { LargeHeader("Diario", day.itLong()) }
                    RoundStep("‹") { day = day.minusDays(1) }
                    Spacer(Modifier.width(8.dp))
                    RoundStep("›") { if (day.isBefore(LocalDate.now())) day = day.plusDays(1) }
                }
            }
            item {
                HealthCard("Umore", BioIcons.Sparkle, Tint.mood) { ScalePicker(e.mood) { vm.saveJournal(e.copy(mood = it)) } }
            }
            item { MoodTrendCard(vm) }
            item {
                HealthCard("Energia", BioIcons.Bolt, Tint.activity) { ScalePicker(e.energy) { vm.saveJournal(e.copy(energy = it)) } }
            }
            item {
                HealthCard("Cosa è successo oggi", BioIcons.Pencil, Tint.blue) {
                    TagChips(e.tags) { t -> vm.saveJournal(e.copy(tags = if (t in e.tags) e.tags - t else e.tags + t)) }
                }
            }
            item {
                HealthCard("Note", BioIcons.Doc, Tint.body) {
                    OutlinedTextField(
                        note, { note = it }, modifier = Modifier.fillMaxWidth().height(110.dp), shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = p.field, unfocusedContainerColor = p.field,
                            unfocusedBorderColor = Color.Transparent, focusedBorderColor = Tint.sleep),
                    )
                    Button(onClick = { vm.saveJournal(e.copy(note = note.trim())) }, colors = ButtonDefaults.buttonColors(containerColor = Tint.sleep)) {
                        Text("Salva nota", fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { TagEffectsCard(vm) }
        }
    }
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
