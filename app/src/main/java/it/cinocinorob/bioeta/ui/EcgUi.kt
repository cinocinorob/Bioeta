package it.cinocinorob.bioeta.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.science.EcgRecord
import it.cinocinorob.bioeta.science.EcgResult
import it.cinocinorob.bioeta.science.EcgStats
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ECG_INFO =
    "L'ECG si registra con l'app ECG di Google Health / Fitbit, che è un dispositivo medico marcato CE. Connessione Salute non contiene gli ECG: per averli qui, nell'app apri il risultato, tocca «Esporta PDF» e condividilo con BioEtà (oppure importa il PDF da qui).\n\n" +
        "BioEtà non interpreta il tracciato. Riconoscere la fibrillazione atriale da un ECG a una derivazione è il compito di un algoritmo validato come dispositivo medico; rifarlo qui con un metodo non certificato aggiungerebbe solo un secondo giudizio meno affidabile. L'app conserva il PDF, legge il risultato e la frequenza già calcolati dall'app ECG e li mette in ordine nel tempo, pronti da mostrare al medico.\n\n" +
        "Per la diagnosi di fibrillazione atriale le linee guida europee (ESC 2024) richiedono che un medico riveda il tracciato. Un ECG da polso non rileva infarto, altre aritmie o problemi strutturali del cuore."

internal fun ecgShort(r: EcgRecord): String = when (r.result) {
    EcgResult.SINUS -> "Sinusale"
    EcgResult.AFIB -> "Segni di FA"
    EcgResult.UNKNOWN -> "Da indicare"
    else -> "Non conclusivo"
} + " · " + r.time.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ITALIAN))

private fun resultColor(r: EcgResult) = when (r) {
    EcgResult.SINUS -> Tint.good
    EcgResult.AFIB -> Tint.bad
    EcgResult.UNKNOWN -> Tint.blue
    else -> Tint.warn
}

@Composable
fun EcgScreen(vm: AppViewModel, onBack: () -> Unit, onImport: () -> Unit) {
    val list = vm.ecgRecords
    SubScaffold("ECG", onBack, actions = {
        IconButton(onClick = onImport, modifier = Modifier.testTag("ecg_import")) { Icon(BioIcons.Plus, "Importa un ECG") }
    }) { m ->
        LazyColumn(
            m.fillMaxSize().testTag("ecg_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (list.isEmpty()) {
                item {
                    EmptyState(BioIcons.Pulse, "Nessun ECG in archivio",
                        "Nell'app Google Health apri Scopri → Valutazioni e report → Valutazione del ritmo cardiaco, scegli una registrazione, tocca «Esporta PDF» e condividi il file con BioEtà. Oppure importa qui il PDF.",
                        Tint.heart, "Importa PDF", onImport)
                }
                item { HealthCard("Come funziona", BioIcons.Info, Tint.blue) { Caption(ECG_INFO) } }
                return@LazyColumn
            }
            item { EcgSummaryCard(list) }
            items(list, key = { it.id }) { r -> EcgCard(vm, r) }
            item { Caption("Gli ECG sono salvati solo sul telefono e inclusi nel backup.") }
        }
    }
}

@Composable
private fun EcgSummaryCard(list: List<EcgRecord>) {
    val s = EcgStats.summary(list, LocalDate.now())
    HealthCard("Ultimi 12 mesi", BioIcons.Pulse, Tint.heart, info = ECG_INFO, modifier = Modifier.testTag("ecg_summary")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("Registrazioni", "${s.last12m}", "", Modifier.weight(1f))
            StatChip("Segni di FA", "${s.afib12m}", "", Modifier.weight(1f))
            StatChip("FC media", s.meanHr?.f(0) ?: "—", "bpm", Modifier.weight(1f))
        }
        if (s.afib12m > 0) InlineNotice(
            "In ${s.afib12m} registrazioni l'app ECG ha rilevato segni di fibrillazione atriale. Mostra i PDF al medico: è lui che conferma o esclude la diagnosi. Con dolore al petto, svenimento, fiato corto o battito molto rapido chiama il 112.",
            NoticeKind.ERROR,
        )
        else if (s.inconclusive12m > 0) Caption("${s.inconclusive12m} registrazioni non conclusive: di solito per movimento, braccio non appoggiato o frequenza fuori dall'intervallo valutabile. Ripeti da seduto, con il braccio fermo sul tavolo.")
        else Caption("Nessun segno di fibrillazione atriale nelle registrazioni dell'ultimo anno. Un ECG da polso non esclude altri problemi del cuore.")
    }
}

@Composable
private fun EcgCard(vm: AppViewModel, r: EcgRecord) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var open by rememberSaveable(r.id) { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val c = resultColor(r.result)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.card).clickable { open = !open }
            .padding(16.dp).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(EcgStats.fmt(r.time), style = MaterialTheme.typography.titleSmall, color = p.text)
                Text(
                    (r.heartRate?.let { "$it bpm · " } ?: "") + if (r.parsed) "risultato dal PDF" else "risultato indicato da te",
                    style = MaterialTheme.typography.bodySmall, color = p.text2,
                )
            }
            Pill(r.result.label, c)
        }
        if (open) {
            EcgPreview(vm.ecgFile(r), r.kind)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { openFile(ctx, vm.ecgFile(r), r.kind, share = false) }) { Text("Apri") }
                FilledTonalButton(onClick = { openFile(ctx, vm.ecgFile(r), r.kind, share = true) }) { Text("Condividi") }
                OutlinedButton(onClick = { edit = true }) { Text("Correggi") }
            }
            TextButton(onClick = { confirmDelete = true }) { Text("Elimina", color = p.ink(Tint.bad)) }
        }
    }
    if (edit) AlertDialog(
        onDismissRequest = { edit = false },
        title = { Text("Risultato dell'app ECG") },
        text = {
            Column {
                Caption("Scegli il risultato che l'app ECG ha mostrato per questa registrazione.")
                EcgResult.entries.filter { it != EcgResult.UNKNOWN }.forEach { res ->
                    Text(res.label, style = MaterialTheme.typography.bodyLarge, color = p.text,
                        modifier = Modifier.fillMaxWidth().clickable { vm.setEcgResult(r, res); edit = false }.padding(vertical = 10.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { edit = false }) { Text("Chiudi") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Eliminare questo ECG?") },
        text = { Text("Il PDF viene cancellato dall'archivio di BioEtà. L'originale resta nell'app ECG.") },
        confirmButton = { TextButton(onClick = { vm.deleteEcg(r); confirmDelete = false }) { Text("Elimina") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
    )
}

/** Anteprima della prima pagina del PDF (o dell'immagine). */
@Composable
private fun EcgPreview(file: File, kind: String) {
    val p = LocalPalette.current
    var img by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file) {
        img = withContext(Dispatchers.IO) {
            runCatching {
                if (kind == "pdf") ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { r ->
                        r.openPage(0).use { page ->
                            val w = 1400; val h = (w.toFloat() * page.height / page.width).toInt()
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp.asImageBitmap()
                        }
                    }
                } else android.graphics.BitmapFactory.decodeFile(file.path)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val b = img
    if (b == null) Caption("Anteprima non disponibile: usa «Apri».")
    else Image(b, "Tracciato ECG", Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.field), contentScale = ContentScale.FillWidth)
}

private fun openFile(ctx: android.content.Context, f: File, kind: String, share: Boolean) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val type = if (kind == "pdf") "application/pdf" else "image/*"
    val i = if (share) Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri)
    else Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { ctx.startActivity(Intent.createChooser(i, if (share) "Condividi l'ECG" else "Apri con")) }
}
