package it.cinocinorob.bioeta.ui

import android.graphics.Paint
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.science.BloodPressure
import it.cinocinorob.bioeta.science.BpCategory
import it.cinocinorob.bioeta.science.BpReading
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun bpColor(c: BpCategory): Color = when (c) {
    BpCategory.NON_ELEVATED -> Tint.good
    BpCategory.ELEVATED -> Tint.warn
    BpCategory.HYPERTENSION -> Color(0xFFF07A4A)
    BpCategory.HIGH, BpCategory.SEVERE -> Tint.bad
}

private val TIME_FMT = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ITALIAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodPressureScreen(vm: AppViewModel, onBack: () -> Unit, onAsk: (String) -> Unit) {
    val p = LocalPalette.current
    var adding by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<BpReading?>(null) }
    var range by remember { mutableStateOf(30) }
    val readings = vm.bpReadings
    val summary = vm.bpSummary
    val bpInsight = vm.insights.firstOrNull { it.id == "bp" }

    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { IconButton(onClick = onBack) { Icon(BioIcons.Back, "Indietro", tint = Tint.blue) } },
                actions = {
                    TextButton(onClick = { adding = true }) { Text("Aggiungi", color = Tint.bp, fontWeight = FontWeight.Bold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { LargeHeader("Pressione", "Misure a domicilio") }

            if (readings.isEmpty()) item {
                HealthCard("Registra la tua pressione", BioIcons.Heart, Tint.bp) {
                    Body("Per una valutazione affidabile (protocollo ESH): 3–7 giorni, 2 misure al mattino e 2 alla sera, seduto da 5 minuti, bracciale all'altezza del cuore.")
                    Button(onClick = { adding = true }) { Text("Aggiungi misura") }
                }
            }

            if (summary != null) item {
                val c = bpColor(summary.category)
                HealthCard("Media degli ultimi 7 giorni", BioIcons.Heart, Tint.bp) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${summary.meanSys.f(0)}/${summary.meanDia.f(0)}", style = androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 52.sp, fontFeatureSettings = TABULAR), color = p.text)
                        Text(" mmHg", fontSize = 16.sp, color = p.text2, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Pill(summary.category.label, c)
                    BpScale(summary.meanSys, summary.meanDia)
                    Caption("${summary.n} misure in ${summary.days} giorni" +
                        (summary.meanPulse?.let { " · polso medio ${it.f(0)} bpm" } ?: "") +
                        (summary.sdSys?.let { " · variabilità sistolica ±${it.f(0)}" } ?: ""))
                    if (!summary.protocolComplete) Caption("Valutazione parziale: servono almeno 3 giorni e 12 misure (primo giorno escluso, come da protocollo ESH).", Tint.warn)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        summary.morning?.let { MiniStat("Mattino", "${it.first.f(0)}/${it.second.f(0)}", Modifier.weight(1f)) }
                        summary.evening?.let { MiniStat("Sera", "${it.first.f(0)}/${it.second.f(0)}", Modifier.weight(1f)) }
                    }
                    if (summary.trendSys != null && summary.trendDia != null) {
                        Body("Tendenza a 90 giorni: ${summary.trendSys.fs(1)}/${summary.trendDia.fs(1)} mmHg al mese", weight = FontWeight.SemiBold)
                    }
                }
            }

            if (readings.size >= 2) item {
                HealthCard("Andamento", BioIcons.Pulse, Tint.bp) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(7, 30, 90, 365).forEachIndexed { i, d ->
                            SegmentedButton(
                                selected = range == d, onClick = { range = d },
                                shape = SegmentedButtonDefaults.itemShape(i, 4),
                            ) { Text(if (d == 365) "1 a" else "$d g", fontSize = 13.sp) }
                        }
                    }
                    BpRangeChart(readings.filter { !it.time.toLocalDate().isBefore(LocalDate.now().minusDays(range.toLong() - 1)) })
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendDot(Tint.bp, "Sistolica")
                        LegendDot(Tint.sleep, "Diastolica")
                        LegendDot(p.text2, "Soglie 135/85")
                    }
                }
            }

            if (bpInsight != null) item { InsightCard(bpInsight, onAsk) }

            if (readings.isNotEmpty()) {
                item { SectionLabel("Misure") }
                item {
                    InsetGroup {
                        val rev = readings.reversed().take(60)
                        rev.forEachIndexed { i, r ->
                            val c = bpColor(BloodPressure.classify(r.sys.toDouble(), r.dia.toDouble()))
                            GroupRow(
                                title = "${r.sys}/${r.dia} mmHg" + (r.pulse?.let { " · $it bpm" } ?: ""),
                                subtitle = TIME_FMT.format(r.time) + (if (r.arm.isNotBlank()) " · braccio ${r.arm}" else "") + (if (r.note.isNotBlank()) " · ${r.note}" else ""),
                                leading = { Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(c)) },
                                showDivider = i < rev.lastIndex,
                                onClick = { toDelete = r },
                            )
                        }
                    }
                }
            }
        }
    }

    if (adding) BpEntryDialog(onDismiss = { adding = false }, onSave = { vm.addBp(it); adding = false })
    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminare la misura?") },
            text = { Text("${r.sys}/${r.dia} mmHg del ${TIME_FMT.format(r.time)}") },
            confirmButton = { TextButton(onClick = { vm.deleteBp(r.id); toDelete = null }) { Text("Elimina", color = Tint.bad) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(p.field).padding(12.dp)) {
        Text(label, fontSize = 12.sp, color = p.text2, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 20.sp, color = p.text, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun LegendDot(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(c))
        Text(" $label", fontSize = 12.sp, color = LocalPalette.current.text2)
    }
}

/** Scala orizzontale a fasce ESC (domiciliare) con il marcatore della media. */
@Composable
fun BpScale(sys: Double, dia: Double) {
    val p = LocalPalette.current
    val bands = listOf(
        Triple("< 120/70", Tint.good, 0f),
        Triple("120–134", Tint.warn, 0.33f),
        Triple("≥ 135/85", Color(0xFFF07A4A), 0.62f),
        Triple("≥ 160/100", Tint.bad, 0.84f),
    )
    // posizione: categoria peggiore tra sistolica e diastolica, interpolata nella fascia
    fun pos(): Float {
        val s = when {
            sys < 120 -> (sys - 90) / 30 * 0.33
            sys < 135 -> 0.33 + (sys - 120) / 15 * 0.29
            sys < 160 -> 0.62 + (sys - 135) / 25 * 0.22
            else -> 0.84 + (sys - 160) / 40 * 0.16
        }
        val d = when {
            dia < 70 -> (dia - 50) / 20 * 0.33
            dia < 85 -> 0.33 + (dia - 70) / 15 * 0.29
            dia < 100 -> 0.62 + (dia - 85) / 15 * 0.22
            else -> 0.84 + (dia - 100) / 20 * 0.16
        }
        return maxOf(s, d).toFloat().coerceIn(0.02f, 0.98f)
    }
    val x = pos()
    val ring = p.card
    val ink = p.text
    Column {
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            val h = 10.dp.toPx(); val y = size.height / 2 - h / 2
            bands.forEachIndexed { i, (_, c, start) ->
                val end = if (i == bands.lastIndex) 1f else bands[i + 1].third
                drawRoundRect(c.copy(alpha = 0.85f), Offset(size.width * start + 2f, y), Size(size.width * (end - start) - 4f, h), CornerRadius(h / 2))
            }
            val cx = size.width * x
            drawCircle(ring, 11.dp.toPx(), Offset(cx, size.height / 2))
            drawCircle(ink, 7.dp.toPx(), Offset(cx, size.height / 2))
        }
        Row(Modifier.fillMaxWidth()) {
            bands.forEachIndexed { i, (l, _, start) ->
                val end = if (i == bands.lastIndex) 1f else bands[i + 1].third
                Text(l, fontSize = 10.sp, color = p.text2, modifier = Modifier.weight(end - start), textAlign = TextAlign.Center)
            }
        }
    }
}

/** Barre giornaliere min–max di sistolica e diastolica, con le soglie domiciliari 135/85. */
@Composable
fun BpRangeChart(readings: List<BpReading>) {
    val p = LocalPalette.current
    if (readings.isEmpty()) { Caption("Nessuna misura nel periodo."); return }
    val byDay = readings.groupBy { it.time.toLocalDate() }.toSortedMap()
    val first = byDay.firstKey(); val last = byDay.lastKey()
    val nDays = (last.toEpochDay() - first.toEpochDay() + 1).toInt().coerceAtLeast(1)
    val lo = minOf(60, readings.minOf { it.dia } - 5).toFloat()
    val hi = maxOf(150, readings.maxOf { it.sys } + 5).toFloat()
    val label = p.text2.toArgb()
    val grid = p.sep
    val diaColor = Tint.sleep
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val right = 34.dp.toPx(); val bottom = 18.dp.toPx()
        val w = size.width - right; val h = size.height - bottom
        fun y(v: Float) = h * (1 - (v - lo) / (hi - lo))
        val paint = Paint().apply { color = label; textSize = 10.dp.toPx(); isAntiAlias = true }
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        listOf(135f, 85f).forEach { t ->
            drawLine(p.text2.copy(alpha = 0.6f), Offset(0f, y(t)), Offset(w, y(t)), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
            drawContext.canvas.nativeCanvas.drawText(t.toInt().toString(), w + 6.dp.toPx(), y(t) + 4.dp.toPx(), paint)
        }
        listOf(lo, hi).forEach { t ->
            drawLine(grid, Offset(0f, y(t)), Offset(w, y(t)), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(t.toInt().toString(), w + 6.dp.toPx(), y(t) + 4.dp.toPx(), paint)
        }
        val slot = w / nDays
        val bw = (slot * 0.5f).coerceIn(3.dp.toPx(), 12.dp.toPx())
        byDay.forEach { (d, list) ->
            val cx = (d.toEpochDay() - first.toEpochDay()) * slot + slot / 2
            fun bar(minV: Int, maxV: Int, c: Color) {
                val top = y(maxV.toFloat()); val bot = y(minV.toFloat())
                drawRoundRect(c, Offset(cx - bw / 2, top), Size(bw, (bot - top).coerceAtLeast(bw)), CornerRadius(bw / 2))
            }
            bar(list.minOf { it.sys }, list.maxOf { it.sys }, Tint.bp)
            bar(list.minOf { it.dia }, list.maxOf { it.dia }, diaColor)
        }
        val f = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
        paint.textAlign = Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(f.format(first), 0f, size.height - 2.dp.toPx(), paint)
        paint.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(f.format(last), w, size.height - 2.dp.toPx(), paint)
    }
}

@Composable
private fun BpEntryDialog(onDismiss: () -> Unit, onSave: (BpReading) -> Unit) {
    val p = LocalPalette.current
    var sys by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("") }
    var pulse by remember { mutableStateOf("") }
    var arm by remember { mutableStateOf("sinistro") }
    var note by remember { mutableStateOf("") }
    var whenText by remember { mutableStateOf(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))) }
    val time = runCatching { LocalDateTime.parse(whenText.trim(), DateTimeFormatter.ofPattern("d/M/uuuu H:mm")) }.getOrNull()
    val s = sys.toIntOrNull(); val d = dia.toIntOrNull(); val pu = pulse.toIntOrNull()
    val err = if (s != null && d != null) BloodPressure.plausible(s, d, pu) else null
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = p.field, unfocusedContainerColor = p.field,
        unfocusedBorderColor = Color.Transparent, focusedBorderColor = Tint.bp,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Nuova misura", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(sys, { sys = it.filter(Char::isDigit).take(3) }, label = { Text("Sistolica") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                        modifier = Modifier.weight(1f), colors = colors, shape = RoundedCornerShape(16.dp))
                    OutlinedTextField(dia, { dia = it.filter(Char::isDigit).take(3) }, label = { Text("Diastolica") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                        modifier = Modifier.weight(1f), colors = colors, shape = RoundedCornerShape(16.dp))
                }
                OutlinedTextField(pulse, { pulse = it.filter(Char::isDigit).take(3) }, label = { Text("Battito (facoltativo)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = colors, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(whenText, { whenText = it }, label = { Text("Data e ora (gg/mm/aaaa hh:mm)") }, singleLine = true,
                    isError = time == null, modifier = Modifier.fillMaxWidth(), colors = colors, shape = RoundedCornerShape(16.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("sinistro", "destro").forEachIndexed { i, a ->
                        SegmentedButton(selected = arm == a, onClick = { arm = a }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text("Braccio $a", fontSize = 13.sp) }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note (caffè, farmaci, stress…)") },
                    modifier = Modifier.fillMaxWidth(), colors = colors, shape = RoundedCornerShape(16.dp))
                err?.let { Text(it, color = Tint.bad, fontSize = 13.sp) }
                if (s != null && d != null && err == null && (s >= 180 || d >= 110)) {
                    Text("Valore molto alto: riposa 5 minuti e ripeti la misura. Con dolore al petto, mancanza di fiato, disturbi della vista o della parola o forte mal di testa chiama il 112.",
                        color = Tint.bad, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Caption("Seduto da 5 minuti, schiena appoggiata, piedi a terra, braccio sul tavolo all'altezza del cuore. Fai 2 misure a 1 minuto di distanza.")
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(BpReading(time = time!!, sys = s!!, dia = d!!, pulse = pu, arm = arm, note = note.trim())) },
                enabled = s != null && d != null && err == null && time != null,
                colors = ButtonDefaults.buttonColors(),
            ) { Text("Salva", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

/** Riquadro compatto per il Riepilogo. */
@Composable
fun BpMini(vm: AppViewModel, onOpen: () -> Unit) {
    val p = LocalPalette.current
    val s = vm.bpSummary
    val last = vm.bpReadings.lastOrNull()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card).clickable(onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Tint.bp.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(BioIcons.Heart, null, tint = Tint.bp, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Pressione", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = p.text)
            if (s != null && last != null) {
                Text("${s.meanSys.f(0)}/${s.meanDia.f(0)} mmHg · media 7 gg", fontSize = 14.sp, color = p.text)
                Caption("Ultima: ${last.sys}/${last.dia} il ${TIME_FMT.format(last.time)}")
            } else Caption("Tocca per registrare la prima misura")
        }
        if (s != null) Pill(s.category.short, bpColor(s.category))
        Spacer(Modifier.width(6.dp))
        Icon(BioIcons.Chevron, null, tint = p.text2, modifier = Modifier.size(14.dp))
    }
}
