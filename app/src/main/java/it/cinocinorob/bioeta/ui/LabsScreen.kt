package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    if (key == Analytes.EGFR) "eGFR (CKD-EPI 2021)" else Analytes.get(key)?.name ?: key

private fun displayUnit(key: String): String =
    if (key == Analytes.EGFR) "mL/min/1,73m²" else Analytes.get(key)?.unit ?: ""

private fun displayDecimals(key: String): Int =
    if (key == Analytes.EGFR) 0 else Analytes.get(key)?.decimals ?: 1

private fun groupOf(key: String): Group = if (key == Analytes.EGFR) Group.RENE else Analytes.get(key)?.group ?: Group.ALTRO

fun groupTint(g: Group): Color = when (g) {
    Group.PHENO -> Tint.bio
    Group.EMOCROMO -> Tint.blood
    Group.METABOLISMO -> Tint.activity
    Group.LIPIDI -> Tint.heart
    Group.FEGATO -> Tint.body
    Group.RENE -> Tint.resp
    Group.ALTRO -> Tint.blue
}

// =====================================================================
// Elenco referti + andamenti
// =====================================================================

@Composable
fun LabsScreen(
    vm: AppViewModel,
    modifier: Modifier,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    onImportReport: () -> Unit,
    onAnalyte: (String) -> Unit,
) {
    val panels = vm.panels.sortedByDescending { it.date }
    val keys = Analytes.all.map { it.key } + Analytes.EGFR
    val series = keys.associateWith { BioAgeEngine.analyteSeries(it, vm.panels, vm.profile) }.filterValues { it.isNotEmpty() }
    val p = LocalPalette.current

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { LargeHeader("Analisi", "${panels.size} referti") }

        item {
            HealthCard("Aggiungi un referto", BioIcons.Drop, Tint.blood) {
                Caption("PDF del laboratorio (anche protetto da password), PDF scansionato o foto del referto. I valori vengono letti sul telefono e li verifichi prima di salvare.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onImportReport,
                        enabled = !vm.importing,
                        colors = ButtonDefaults.buttonColors(containerColor = Tint.blood),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(BioIcons.Doc, null, Modifier.size(18.dp)); Text("  Importa")
                    }
                    FilledTonalButton(onClick = onNew, modifier = Modifier.weight(1f)) {
                        Icon(BioIcons.Pencil, null, Modifier.size(18.dp)); Text("  A mano")
                    }
                }
                if (vm.importing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Tint.blood)
                    Caption("Lettura del referto in corso…")
                }
            }
        }

        if (panels.isNotEmpty()) {
            item { SectionLabel("Referti") }
            item {
                InsetGroup {
                    panels.forEachIndexed { i, panel ->
                        val att = BioAgeEngine.attempt(panel, vm.profile)
                        val pt = att.point
                        GroupRow(
                            title = panel.date.itLong(),
                            subtitle = listOfNotNull(panel.lab.takeIf { it.isNotBlank() }, "${panel.values.size} valori").joinToString(" · "),
                            leading = { IconBadge(BioIcons.Drop, Tint.blood) },
                            trailing = {
                                if (pt != null) {
                                    val d = pt.result.phenoAge - pt.age
                                    Pill("${pt.result.phenoAge.f(1)} (${d.fs(1)})", if (d <= 0) Tint.good else Tint.bad)
                                }
                            },
                            showDivider = i < panels.lastIndex,
                            onClick = { onEdit(panel.id) },
                        )
                    }
                }
            }
        }

        val groups = series.keys.groupBy { groupOf(it) }
        Group.entries.forEach { g ->
            val ks = groups[g] ?: return@forEach
            item(key = "label_${g.name}") { SectionLabel(g.label) }
            item(key = "group_${g.name}") {
                val tint = groupTint(g)
                InsetGroup {
                    ks.forEachIndexed { i, k ->
                        val s = series.getValue(k)
                        val last = s.last()
                        val prev = s.getOrNull(s.size - 2)
                        val d = displayDecimals(k)
                        GroupRow(
                            title = displayName(k),
                            subtitle = buildString {
                                append(last.first.itDate())
                                if (prev != null) append("  ·  Δ ").append((last.second.value - prev.second.value).fs(d))
                            },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (s.size >= 2) Sparkline(s.map { it.second.value }, tint)
                                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(84.dp)) {
                                        Text(
                                            (if (last.second.belowLimit) "<" else "") + last.second.value.f(d),
                                            fontWeight = FontWeight.Bold, fontSize = 17.sp, color = p.text,
                                        )
                                        Text(displayUnit(k), fontSize = 11.sp, color = p.text2, textAlign = TextAlign.End)
                                    }
                                }
                            },
                            showDivider = i < ks.lastIndex,
                            onClick = { onAnalyte(k) },
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// Inserimento / modifica / verifica import
// =====================================================================

private val INPUT_DATE = DateTimeFormatter.ofPattern("d/M/uuuu")

private fun parseDate(s: String): LocalDate? {
    val t = s.trim()
    return runCatching { LocalDate.parse(t, INPUT_DATE) }.getOrNull()
        ?: runCatching { LocalDate.parse(t) }.getOrNull()
}

private fun initialText(v: LabValue, a: Analyte?): String {
    val dec = (a?.decimals ?: 1) + 1
    var s = v.value.f(dec)
    if (s.contains(',')) s = s.trimEnd('0').trimEnd(',')
    return s
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelEditor(
    initial: Panel?,
    isNew: Boolean,
    importNotes: List<String>,
    highlighted: Set<String>,
    profile: Profile,
    onSave: (Panel) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    val p = LocalPalette.current
    var dateText by remember { mutableStateOf((initial?.date ?: LocalDate.now()).itDate()) }
    var lab by remember { mutableStateOf(initial?.lab ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val texts = remember {
        mutableStateMapOf<String, String>().apply {
            initial?.values?.forEach { (k, v) -> put(k, initialText(v, Analytes.get(k))) }
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
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = p.field, unfocusedContainerColor = p.field,
        unfocusedBorderColor = Color.Transparent, focusedBorderColor = Tint.blue,
    )

    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) (if (highlighted.isNotEmpty()) "Verifica referto" else "Nuovo referto") else "Modifica referto", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(BioIcons.Back, "Indietro", tint = Tint.blue) } },
                actions = {
                    if (!isNew && initial != null) IconButton(onClick = { confirmDelete = true }) { Icon(BioIcons.Trash, "Elimina", tint = Tint.bad) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg),
            )
        },
        bottomBar = {
            Column(Modifier.background(p.card).padding(horizontal = 16.dp, vertical = 12.dp)) {
                val pt = preview?.point
                if (pt != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PhenoAge", color = p.text2, fontSize = 14.sp)
                        Text(pt.result.phenoAge.f(1), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = p.text)
                        Pill("${(pt.result.phenoAge - pt.age).fs(1)} anni", if (pt.result.phenoAge <= pt.age) Tint.good else Tint.bad)
                    }
                } else if (preview != null) {
                    Caption("PhenoAge: mancano ${preview.missing.size} elementi")
                }
                VSpace(8)
                Button(
                    onClick = { build()?.let(onSave) },
                    enabled = date != null && invalid.isEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Salva", fontWeight = FontWeight.SemiBold) }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (importNotes.isNotEmpty()) item {
                HealthCard("Valori letti dal referto", BioIcons.Doc, Tint.blue) {
                    importNotes.forEach { Caption("• $it", p.text) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Tint.blue))
                        Caption("  = valore riconosciuto automaticamente: confrontalo con il referto.")
                    }
                }
            }
            item {
                HealthCard("Prelievo", BioIcons.Drop, Tint.blood) {
                    OutlinedTextField(
                        value = dateText, onValueChange = { dateText = it },
                        label = { Text("Data (gg/mm/aaaa)") }, isError = date == null, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), colors = fieldColors, shape = RoundedCornerShape(10.dp),
                    )
                    OutlinedTextField(lab, { lab = it }, label = { Text("Laboratorio") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), colors = fieldColors, shape = RoundedCornerShape(10.dp))
                    OutlinedTextField(note, { note = it }, label = { Text("Note (digiuno, malattie recenti, farmaci…)") },
                        modifier = Modifier.fillMaxWidth(), colors = fieldColors, shape = RoundedCornerShape(10.dp))
                }
            }
            Group.entries.forEach { g ->
                item(key = g.name) {
                    HealthCard(g.label, BioIcons.Drop, groupTint(g)) {
                        if (g == Group.PHENO) Caption("Per confronti validi nel tempo: prelievo a digiuno, al mattino, lontano da infezioni o allenamenti intensi.")
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
                                highlighted = a.key in highlighted,
                                fieldColors = fieldColors,
                            )
                        }
                    }
                }
            }
            val warns = preview?.warnings.orEmpty()
            if (warns.isNotEmpty()) item {
                HealthCard("Avvertenze", BioIcons.Info, Tint.warn) {
                    warns.forEach { Caption("⚠ $it", p.text) }
                }
            }
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare il referto?") },
            text = { Text("Il referto del ${initial.date.itDate()} verrà rimosso dall'archivio.") },
            confirmButton = { TextButton(onClick = { onDelete(initial.id) }) { Text("Elimina", color = Tint.bad) } },
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
    highlighted: Boolean,
    fieldColors: androidx.compose.material3.TextFieldColors,
) {
    val p = LocalPalette.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (highlighted) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Tint.blue))
                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
            }
            Text(a.name, Modifier.weight(1f), fontSize = 15.sp, color = p.text)
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                singleLine = true,
                isError = error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(104.dp),
                colors = fieldColors,
                shape = RoundedCornerShape(10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, color = p.text),
            )
            val unit = a.units[unitIndex.coerceIn(0, a.units.lastIndex)].label
            if (a.units.size > 1) {
                TextButton(onClick = onCycleUnit, modifier = Modifier.width(88.dp)) { Text("$unit ⇄", fontSize = 13.sp, color = Tint.blue) }
            } else {
                Text(unit, Modifier.width(88.dp).padding(start = 10.dp), fontSize = 13.sp, color = p.text2)
            }
        }
        if (a.key == Analytes.CRP) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = below, onCheckedChange = onBelow)
                Caption("sotto il limite di rilevabilità (<)")
            }
        }
        if (a.note != null) Caption(a.note)
    }
}

// =====================================================================
// Dettaglio analita
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyteScreen(key: String, panels: List<Panel>, profile: Profile, onBack: () -> Unit) {
    val p = LocalPalette.current
    val s = BioAgeEngine.analyteSeries(key, panels, profile)
    val d = displayDecimals(key)
    val unit = displayUnit(key)
    val tint = groupTint(groupOf(key))
    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { IconButton(onClick = onBack) { Icon(BioIcons.Back, "Indietro", tint = Tint.blue) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { LargeHeader(displayName(key), groupOf(key).label) }
            if (s.isNotEmpty()) item {
                val last = s.last()
                HealthCard(trailing = null) {
                    Caption("ULTIMO VALORE · ${last.first.itLong()}")
                    BigValue((if (last.second.belowLimit) "<" else "") + last.second.value.f(d), unit, size = 40)
                    if (s.size >= 2) {
                        HealthChart(
                            listOf(ChartLayer(s.map { it.first.toEpochDay().toDouble() to it.second.value }, tint, LayerKind.AREA, emphasizeLast = true)),
                            yDecimals = d,
                        )
                    }
                }
            }
            item {
                val vals = s.map { it.second.value }
                HealthCard("Sintesi", BioIcons.Info, tint) {
                    if (vals.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat("Misure", vals.size.toString())
                            Stat("Min", vals.min().f(d))
                            Stat("Media", vals.average().f(d))
                            Stat("Max", vals.max().f(d))
                        }
                    }
                    val spanDays = if (s.size >= 2) s.last().first.toEpochDay() - s.first().first.toEpochDay() else 0
                    val slope = if (s.size >= 3 && spanDays >= 90) {
                        Stats.olsSlope(s.map { it.first.toEpochDay() / 365.2425 }, vals)
                    } else null
                    if (slope != null) Body("Tendenza: ${slope.fs(d + 1)} $unit/anno", weight = FontWeight.SemiBold)
                    else Caption("La tendenza lineare richiede ≥3 misure su ≥3 mesi.")
                    BioAgeEngine.phenoSensitivity(key)?.let {
                        Body("Effetto sulla PhenoAge, a parità del resto: $it")
                    }
                    Caption("Tra due prelievi oscillazioni del 5–15% sono spesso attese per variabilità analitica e biologica (molto di più per PCR e trigliceridi).")
                }
            }
            if (s.isNotEmpty()) item {
                InsetGroup {
                    val rev = s.reversed()
                    rev.forEachIndexed { i, (date, v) ->
                        GroupRow(
                            title = date.itLong(),
                            trailing = {
                                Text((if (v.belowLimit) "<" else "") + v.value.f(d) + " " + unit, fontWeight = FontWeight.SemiBold, color = p.text)
                            },
                            showDivider = i < rev.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = p.text2)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.text)
    }
}
