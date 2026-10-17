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

private val MOOD_LABELS = listOf("Pessimo", "Giù", "Normale", "Bene", "Ottimo")
private val MOOD_COLORS = listOf(Color(0xFFF2788A), Color(0xFFFFA97A), Color(0xFFFFD27A), Color(0xFF9FDDB0), Color(0xFF5FD3A0))

/** Selettore 1–5 a pallini morbidi. */
@Composable
fun ScalePicker(value: Int?, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        (1..5).forEach { v ->
            val sel = value == v
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(if (sel) MOOD_COLORS[v - 1].copy(alpha = 0.3f) else p.field)
                    .clickable { onChange(v) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(MOOD_COLORS[v - 1]).then(if (sel) Modifier.border(3.dp, p.card, CircleShape) else Modifier))
                Text(MOOD_LABELS[v - 1], fontSize = 10.sp, color = if (sel) p.text else p.text2, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Center)
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
                HealthCard("Umore", BioIcons.Sparkle, Tint.sleep) { ScalePicker(e.mood) { vm.saveJournal(e.copy(mood = it)) } }
            }
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
