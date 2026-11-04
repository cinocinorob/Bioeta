package it.cinocinorob.bioeta.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import it.cinocinorob.bioeta.AppViewModel
import kotlinx.coroutines.launch

/**
 * Scheda «Corpo»: unisce Età e Analisi (un solo argomento: corpo, esami, età biologica) e raccoglie i controlli medici
 * (pressione, ECG), la salute a lungo termine, l'alimentazione e il report per il medico.
 */
@Composable
fun BodyScreen(
    vm: AppViewModel,
    modifier: Modifier,
    onOpenBp: () -> Unit,
    onOpenEcg: () -> Unit,
    onOpenLongTerm: () -> Unit,
    onOpenNutrition: () -> Unit,
    age: @Composable (Modifier, @Composable () -> Unit) -> Unit,
    labs: @Composable (Modifier, @Composable () -> Unit) -> Unit,
) {
    var seg by rememberSaveable { mutableStateOf(0) }
    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SegmentPill(listOf("Panoramica", "Analisi del sangue"), seg, onSelect = { seg = it }, modifier = Modifier.testTag("body_segment"))
            if (seg == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction("Pressione", vm.bpSummary?.let { "${it.meanSys.f(0)}/${it.meanDia.f(0)} mmHg" } ?: "Registra", Tint.bp, BioIcons.Heart, onOpenBp, Modifier.weight(1f).testTag("qa_bp"))
                    QuickAction("ECG", vm.ecgRecords.firstOrNull()?.let { ecgShort(it) } ?: "Importa", Tint.heart, BioIcons.Pulse, onOpenEcg, Modifier.weight(1f).testTag("qa_ecg"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction("Lungo termine", "Indicatori di salute", Tint.good, BioIcons.Gauge, onOpenLongTerm, Modifier.weight(1f).testTag("qa_longterm"))
                    QuickAction("Alimentazione", "Dalle app collegate", Tint.food, BioIcons.Food, onOpenNutrition, Modifier.weight(1f).testTag("qa_nutrition"))
                }
                DoctorReportButton(vm)
            }
        }
    }
    if (seg == 0) age(modifier, header) else labs(modifier, header)
}

/** Crea il PDF per il medico e apre la condivisione (email, WhatsApp, stampa…). */
@Composable
private fun DoctorReportButton(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by rememberSaveable { mutableStateOf(false) }
    HealthCard("Report per il medico", BioIcons.Doc, Tint.blue, modifier = Modifier.testTag("doctor_report")) {
        Caption("Un PDF con pressione (protocollo ESH), ECG, ultimi esami e andamenti di 3 mesi. Lo crei sul telefono e lo condividi tu.")
        FilledTonalButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    val f = vm.buildDoctorReport()
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(i, "Condividi il report"))
                } catch (e: Exception) {
                    vm.message = "Report non creato: ${e.message ?: e.javaClass.simpleName}"
                } finally { busy = false }
            }
        }) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Crea e condividi")
        }
    }
}
