package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val ACTIVE_INFO =
    "Come si contano. Un minuto è «moderato» se la frequenza cardiaca è almeno al 40% della riserva cardiaca, «vigoroso» dal 60% (ACSM, PMID 21694556); FCmax = 208 − 0,7 × età (Tanaka 2001). Un minuto vigoroso vale 2, come nelle linee guida OMS 2020 (150 minuti a settimana, PMID 33239350). È lo stesso schema dei Minuti in zona attiva di Fitbit e Pixel Watch: 1 punto in zona brucia grassi, 2 in zona cardio o picco.\n\n" +
        "Quando la frequenza cardiaca manca contano anche i passi: almeno 100 passi al minuto = moderato, almeno 130 = vigoroso (studio CADENCE-Adults, Tudor-Locke 2019 e 2020). Gli allenamenti registrati senza frequenza cardiaca sono stimati dal tipo di attività (Compendio 2024, PMID 38242596). Ogni minuto conta una volta sola.\n\n" +
        "Più fonti. Se più app scrivono la frequenza cardiaca (orologio, anello, telefono) non si fa la media: si usa la fonte principale e le altre coprono solo i minuti scoperti. Prima la media con un anello, che di giorno misura soprattutto a riposo, abbassava la frequenza durante le camminate e faceva perdere minuti.\n\n" +
        "Perché il numero può differire da quello dell'orologio. L'orologio calcola i suoi minuti dalla frequenza cardiaca secondo per secondo e con la sua frequenza a riposo; a Connessione Salute spesso arrivano meno campioni. Differenze di qualche minuto sono attese; differenze grandi indicano dati mancanti: controlla la fonte qui sotto e la Diagnostica."

/** Scheda «Minuti attivi»: totale di oggi, da dove viene ogni minuto, andamento orario e fonte della FC. */
@Composable
fun ActiveMinutesCard(vm: AppViewModel, onOpen: () -> Unit, week: @Composable () -> Unit) {
    val p = LocalPalette.current
    val d = vm.todayDayActivity
    var pick by remember { mutableStateOf(false) }
    HealthCard("Minuti attivi", BioIcons.Bolt, Tint.good, onClick = onOpen, info = ACTIVE_INFO, modifier = Modifier.testTag("active_card")) {
        week()
        Caption("Verde: minuti moderati. Arancio: minuti vigorosi (valgono doppio).")
        if (d == null) {
            InlineNotice(
                if (vm.activityReadFailed) "Connessione Salute non ha risposto alla lettura della frequenza cardiaca. Riprova tra qualche minuto con «Sincronizza ora»."
                else "Nessun dato di oggi: servono frequenza cardiaca, passi o un allenamento registrato.",
                NoticeKind.WARN,
            )
        } else {
            if (d.stale) InlineNotice(
                "Ultima lettura non riuscita: il valore è quello delle ${Instant.ofEpochSecond(d.computedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))} e non è aggiornato.",
                NoticeKind.WARN,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                BigValue("${d.activeMinutes}", "min oggi", size = 34)
            }
            // Scomposizione: la somma dei tre riquadri è sempre il totale
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("Da FC", "${d.hrEq}", "min", Modifier.weight(1f))
                StatChip("Da passi", "${d.cadenceEq}", "min", Modifier.weight(1f))
                StatChip("Stimati", "${d.estimatedShare}", "min", Modifier.weight(1f))
            }
            if (d.hourly.size == 24) HourlyActive(d.hourly)
            if (d.hrMinutes == 0 && d.cadenceMinutes == 0 && d.estimatedShare == 0) InlineNotice(
                "Oggi nessun minuto attivo riconosciuto. Possibili cause: l'orologio non scrive la frequenza cardiaca in Connessione Salute, il permesso «Frequenza cardiaca» non è concesso, oppure i passi arrivano in blocchi lunghi (oltre 10 minuti) da cui non si ricava la cadenza. La Diagnostica mostra cosa arriva e da quale app.",
                NoticeKind.WARN,
            ) else Caption("Frequenza cardiaca disponibile per ${d.hrMinutes / 60} h ${d.hrMinutes % 60} min di oggi.")
        }
        // Fonte della FC (riconciliazione come in Vora: una fonte principale per metrica, scelta automatica o tua)
        val seen = vm.hrOriginsSeen
        if (seen.isNotEmpty() || vm.hrSourcePref != null) {
            androidx.compose.material3.HorizontalDivider(color = p.sep)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fonte della frequenza cardiaca", style = MaterialTheme.typography.labelLarge, color = p.text)
                    val src = vm.hrSourcePref ?: d?.source
                    Text(
                        (src?.let { vm.health.originLabel(it) } ?: "—") + if (vm.hrSourcePref == null) " · automatica" else " · scelta da te",
                        style = MaterialTheme.typography.bodySmall, color = p.text2,
                    )
                }
                TextButton(onClick = { pick = true }, modifier = Modifier.testTag("hr_source_button")) { Text("Cambia") }
            }
            if (seen.size > 1) Caption("Le altre app coprono solo i minuti in cui la fonte principale non ha dati: niente medie tra dispositivi.")
        }
    }
    if (pick) HrSourceDialog(vm) { pick = false }
}

/** Minuti attivi equivalenti per ora del giorno; tocco per leggere il valore. */
@Composable
fun HourlyActive(hourly: List<Int>) {
    val p = LocalPalette.current
    val now = LocalTime.now().hour
    var sel by remember(hourly) { mutableStateOf<Int?>(null) }
    val mx = maxOf(hourly.maxOrNull() ?: 0, 10).toFloat()
    Column(Modifier.testTag("active_hourly")) {
        val s = sel
        val peak = hourly.indices.maxByOrNull { hourly[it] }?.takeIf { hourly[it] > 0 }
        Text(
            when {
                s != null -> "Ore ${s}–${s + 1}: ${hourly[s]} min attivi"
                peak != null -> "Durante la giornata · ora più attiva ${peak}–${peak + 1} (${hourly[peak]} min)"
                else -> "Durante la giornata"
            },
            style = MaterialTheme.typography.labelMedium, color = p.text2,
        )
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier.fillMaxWidth().height(56.dp).pointerInput(hourly) {
                detectTapGestures { o -> val i = (o.x / size.width * 24).toInt().coerceIn(0, 23); sel = if (sel == i) null else i }
            },
        ) {
            val slot = size.width / 24
            val bw = slot * 0.62f
            hourly.forEachIndexed { h, v ->
                val bh = if (v > 0) (v / mx * size.height).coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()
                val c = when { h > now -> p.field; v == 0 -> p.field; sel == h -> Tint.good; else -> Tint.good.copy(alpha = 0.75f) }
                drawRoundRect(c, Offset(h * slot + (slot - bw) / 2, size.height - bh), Size(bw, bh), CornerRadius(bw / 2))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("0", "6", "12", "18", "24").forEachIndexed { i, l ->
                Text(l, fontSize = 10.sp, color = p.text3, modifier = Modifier.weight(1f), textAlign = if (i == 4) TextAlign.End else TextAlign.Start)
            }
        }
    }
}

@Composable
private fun HrSourceDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val seen = vm.hrOriginsSeen.entries.sortedByDescending { it.value }
    val options: List<Pair<String?, String>> = listOf<Pair<String?, String>>(null to "Automatica (consigliata)") +
        seen.map { (pkg, min) -> pkg to "${vm.health.originLabel(pkg)} · ${min / 60} h di dati" } +
        (vm.hrSourcePref?.takeIf { pref -> seen.none { it.key == pref } }?.let { listOf(it to vm.health.originLabel(it)) } ?: emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fonte della frequenza cardiaca") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Automatica: per ogni giorno si usa l'app con più minuti di frequenza cardiaca. Scegli un'app se sai che è la più affidabile (di solito l'orologio). Le altre restano solo come riserva per i minuti scoperti.",
                    style = MaterialTheme.typography.bodySmall, color = p.text2,
                )
                options.forEach { (pkg, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.setHrSource(pkg); onDismiss() }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = vm.hrSourcePref == pkg, onClick = { vm.setHrSource(pkg); onDismiss() })
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = p.text)
                    }
                }
                Text("Dopo il cambio i minuti attivi delle ultime 5 settimane vengono ricalcolati.", style = MaterialTheme.typography.labelSmall, color = p.text3)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("hr_source_close")) { Text("Chiudi") } },
    )
}
