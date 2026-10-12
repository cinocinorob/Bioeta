package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.data.Analyte
import it.cinocinorob.bioeta.data.Analytes
import it.cinocinorob.bioeta.data.BioAgeEngine
import it.cinocinorob.bioeta.data.Group
import it.cinocinorob.bioeta.data.LabValue
import it.cinocinorob.bioeta.data.Panel
import it.cinocinorob.bioeta.data.Profile
import it.cinocinorob.bioeta.science.Stats
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun displayName(key: String): String =
    if (key == Analytes.EGFR) "eGFR (CKD-EPI 2021, calcolato)" else Analytes.get(key)?.name ?: key

private fun displayUnit(key: String): String =
    if (key == Analytes.EGFR) "mL/min/1,73m²" else Analytes.get(key)?.unit ?: ""

private fun displayDecimals(key: String): Int =
    if (key == Analytes.EGFR) 0 else Analytes.get(key)?.decimals ?: 1

// =====================================================================
// Elenco referti + andamenti
// =====================================================================

@Composable
fun LabsScreen(vm: AppViewModel, modifier: Modifier, onEdit: (String) -> Unit, onAnalyte: (String) -> Unit) {
    val panels = vm.panels.sortedByDescending { it.date }
    val keys = Analytes.all.map { it.key } + Analytes.EGFR
    val trendKeys = keys.filter { BioAgeEngine.analyteSeries(it, vm.panels, vm.profile).isNotEmpty() }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (panels.isEmpty()) item {
            SectionCard("Nessun referto") {
                Text("Premi + per inserire le analisi del sangue. Per la PhenoAge servono i 9 marcatori del primo gruppo; tutti gli altri valori sono facoltativi e vengono archiviati per gli andamenti.")
            }
        }
        if (panels.isNotEmpty()) item { Text("Referti", style = MaterialTheme.typography.titleMedium) }
        items(panels, key = { it.id }) { p ->
            val att = BioAgeEngine.attempt(p, vm.profile)
            SectionCard(modifier = Modifier.clickable { onEdit(p.id) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(p.date.itDate(), fontWeight = FontWeight.SemiBold)
                    Text("${p.values.size} valori")
                }
                if (p.lab.isNotBlank()) Muted(p.lab)
                val pt = att.point
                if (pt != null) Text("PhenoAge ${pt.result.phenoAge.f(1)} (Δ ${(pt.result.phenoAge - pt.age).fs(1)})")
                else Muted("PhenoAge non calcolabile: mancano ${att.missing.size} elementi")
            }
        }
        if (trendKeys.isNotEmpty()) item {
            Spacer(Modifier.height(8.dp))
            Text("Andamenti per analita", style = MaterialTheme.typography.titleMedium)
        }
        items(trendKeys, key = { "t_$it" }) { k ->
            val s = BioAgeEngine.analyteSeries(k, vm.panels, vm.profile)
            val last = s.last()
            val prev = s.getOrNull(s.size - 2)
            val d = displayDecimals(k)
            Row(
                Modifier.fillMaxWidth().clickable { onAnalyte(k) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(displayName(k))
                    Muted("${s.size} misure · ultima ${last.first.itDate()}")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text((if (last.second.belowLimit) "<" else "") + last.second.value.f(d) + " " + displayUnit(k), fontWeight = FontWeight.SemiBold)
                    if (prev != null) Muted("Δ ${(last.second.value - prev.second.value).fs(d)}")
                }
            }
            HorizontalDivider()
        }
    }
}

// =====================================================================
// Inserimento / modifica referto
// =====================================================================

private val INPUT_DATE = DateTimeFormatter.ofPattern("d/M/uuuu")

private fun parseDate(s: String): LocalDate? {
    val t = s.trim()
    return runCatching { LocalDate.parse(t, INPUT_DATE) }.getOrNull()
        ?: runCatching { LocalDate.parse(t) }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelEditor(
    initial: Panel?,
    profile: Profile,
    onSave: (Panel) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    var dateText by remember { mutableStateOf((initial?.date ?: LocalDate.now()).itDate()) }
    var lab by remember { mutableStateOf(initial?.lab ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val texts = remember {
        mutableStateMapOf<String, String>().apply {
            initial?.values?.forEach { (k, v) ->
                val a = Analytes.get(k)
                put(k, v.value.f(a?.decimals?.plus(1) ?: 2).trimEnd('0').trimEnd(',').ifEmpty { "0" })
            }
        }
    }
    val below = remember { mutableStateMapOf<String, Boolean>().apply { initial?.values?.forEach { (k, v) -> if (v.belowLimit) put(k, true) } } }
    val unitIdx = remember { mutableStateMapOf<String, Int>() }
    var confirmDelete by remember { mutableStateOf(false) }

    val date = parseDate(dateText)
    val invalid = texts.filter { (_, t) -> t.isNotBlank() && parseNum(t) == null }.keys

    fun build(): Panel? {
        val d = date ?: return null
        val vals = mutableMapOf<String, LabValue>()
        for (a in Analytes.all) {
            val n = parseNum(texts[a.key] ?: "") ?: continue
            val u = a.units[(unitIdx[a.key] ?: 0).coerceIn(0, a.units.lastIndex)]
            vals[a.key] = LabValue(u.toCanonical(n), below[a.key] == true)
        }
        return Panel(id = initial?.id ?: java.util.UUID.randomUUID().toString(), date = d, lab = lab.trim(), note = note.trim(), values = vals)
    }

    val preview = build()?.let { BioAgeEngine.attempt(it, profile) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Nuovo referto" else "Modifica referto") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    if (initial != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Elimina") }
                },
            )
        },
        bottomBar = {
            Column(Modifier.padding(16.dp)) {
                val p = preview?.point
                if (p != null) {
                    Text("PhenoAge: ${p.result.phenoAge.f(1)} anni (Δ ${(p.result.phenoAge - p.age).fs(1)})", fontWeight = FontWeight.SemiBold)
                } else if (preview != null) {
                    Muted("PhenoAge: mancano ${preview.missing.size} elementi")
                }
                Button(
                    onClick = { build()?.let(onSave) },
                    enabled = date != null && invalid.isEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Salva") }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard("Dati del prelievo") {
                    OutlinedTextField(
                        value = dateText, onValueChange = { dateText = it },
                        label = { Text("Data (gg/mm/aaaa)") },
                        isError = date == null, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(lab, { lab = it }, label = { Text("Laboratorio (facoltativo)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(note, { note = it }, label = { Text("Note (digiuno, malattie recenti, farmaci…)") }, modifier = Modifier.fillMaxWidth())
                }
            }
            Group.entries.forEach { g ->
                item(key = g.name) {
                    SectionCard(g.label) {
                        if (g == Group.PHENO) Muted("Per un confronto valido nel tempo: prelievo a digiuno, al mattino, lontano da infezioni o allenamenti intensi.")
                        Analytes.all.filter { it.group == g }.forEach { a ->
                            AnalyteInput(
                                a = a,
                                text = texts[a.key] ?: "",
                                onText = { texts[a.key] = it },
                                unitIndex = unitIdx[a.key] ?: 0,
                                onCycleUnit = { unitIdx[a.key] = ((unitIdx[a.key] ?: 0) + 1) % a.units.size },
                                below = below[a.key] == true,
                                onBelow = { below[a.key] = it },
                                error = a.key in invalid,
                            )
                        }
                    }
                }
            }
            val warns = preview?.warnings.orEmpty()
            if (warns.isNotEmpty()) item {
                SectionCard("Avvertenze") {
                    warns.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare il referto?") },
            text = { Text("Il referto del ${initial.date.itDate()} verrà rimosso dall'archivio.") },
            confirmButton = { TextButton(onClick = { onDelete(initial.id) }) { Text("Elimina") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun AnalyteInput(
    a: Analyte,
    text: String,
    onText: (String) -> Unit,
    unitIndex: Int,
    onCycleUnit: () -> Unit,
    below: Boolean,
    onBelow: (Boolean) -> Unit,
    error: Boolean,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                singleLine = true,
                isError = error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(110.dp),
            )
            val unit = a.units[unitIndex.coerceIn(0, a.units.lastIndex)].label
            if (a.units.size > 1) {
                TextButton(onClick = onCycleUnit, modifier = Modifier.width(92.dp)) { Text("$unit ⇄") }
            } else {
                Text(unit, Modifier.width(92.dp).padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (a.key == Analytes.CRP) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = below, onCheckedChange = onBelow)
                Text("sotto il limite di rilevabilità (<)", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (a.note != null) Muted(a.note)
    }
}

// =====================================================================
// Dettaglio analita
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyteScreen(key: String, panels: List<Panel>, profile: Profile, onBack: () -> Unit) {
    val s = BioAgeEngine.analyteSeries(key, panels, profile)
    val d = displayDecimals(key)
    val unit = displayUnit(key)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayName(key)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s.size >= 2) item {
                SectionCard {
                    LineChart(
                        listOf(ChartSeries(s.map { it.first.toEpochDay().toDouble() to it.second.value }, MaterialTheme.colorScheme.primary)),
                        yDecimals = d,
                        xLabel = ::epochLabel,
                    )
                }
            }
            item {
                val vals = s.map { it.second.value }
                SectionCard("Sintesi ($unit)") {
                    if (vals.isNotEmpty()) {
                        Text("Misure: ${vals.size} · min ${vals.min().f(d)} · max ${vals.max().f(d)} · media ${vals.average().f(d)}")
                    }
                    val spanDays = if (s.size >= 2) s.last().first.toEpochDay() - s.first().first.toEpochDay() else 0
                    val slope = if (s.size >= 3 && spanDays >= 90) {
                        Stats.olsSlope(s.map { it.first.toEpochDay() / 365.2425 }, vals)
                    } else null
                    if (slope != null) Text("Tendenza (OLS): ${slope.fs(d + 1)} $unit/anno")
                    else Muted("La tendenza lineare richiede ≥3 misure su ≥3 mesi.")
                    BioAgeEngine.phenoSensitivity(key)?.let {
                        Text("Effetto sulla PhenoAge a parità del resto: $it")
                    }
                    Muted("Una differenza tra due prelievi va interpretata alla luce della variabilità analitica e biologica intra-individuale: per molti analiti oscillazioni del 5-15% sono attese anche senza cambiamenti reali (molto di più per PCR e trigliceridi).")
                }
            }
            items(s.reversed()) { (date, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(date.itDate())
                    Text((if (v.belowLimit) "<" else "") + v.value.f(d) + " " + unit, fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider()
            }
        }
    }
}
