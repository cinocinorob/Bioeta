package it.cinocinorob.bioeta.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.data.ScoreChange
import it.cinocinorob.bioeta.science.ExtraRefs
import it.cinocinorob.bioeta.science.RadarLevel
import it.cinocinorob.bioeta.science.Session
import it.cinocinorob.bioeta.science.SessionRpe
import it.cinocinorob.bioeta.science.SignalEval
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val RADAR_INFO =
    "Ogni mattina BioEtà confronta i parametri della notte (FC a riposo e nel sonno, HRV, respirazione, SpO₂ e, se il tuo orologio la scrive in Connessione Salute, temperatura cutanea) con il tuo intervallo abituale: mediana e deviazione robusta delle notti da 30 a 3 giorni prima, così una malattia che cresce da due notti non sposta il riferimento.\n\n" +
        "• Segni lievi: due parametri fuori intervallo nella direzione tipica di un malessere, oppure FC alta per due notti di fila.\n" +
        "• Segni marcati: tre o più parametri, oppure FC alta per due notti insieme a un altro parametro.\n\n" +
        "Su cosa si basa: in uno studio prospettico su 3.318 persone con smartwatch (Alavi 2022) un sistema simile, basato sulla FC notturna, ha dato un avviso in 67 degli 84 infetti da SARS‑CoV‑2 (80%), con segnali che precedevano i sintomi di 3 giorni (mediana). Gli stessi avvisi scattavano anche con altre infezioni respiratorie, stress, alcol e viaggi. Oura usa un approccio analogo (Symptom Radar) senza pubblicarne soglie e prestazioni.\n\n" +
        "Limiti: le soglie di BioEtà non sono state validate su persone malate; è un sistema di attenzione, non una diagnosi. Un parametro isolato fuori intervallo è spesso rumore di misura."

/** Radar malessere + parametri vitali della notte (Home e Salute). */
@Composable
fun RadarCard(vm: AppViewModel, startExpanded: Boolean = false) {
    val r = vm.radar
    val p = LocalPalette.current
    var open by rememberSaveable { mutableStateOf(startExpanded) }
    val alert = r.level == RadarLevel.MINOR || r.level == RadarLevel.MAJOR
    val tint = when (r.level) { RadarLevel.MAJOR -> Tint.bad; RadarLevel.MINOR -> Tint.warn; RadarLevel.NONE -> Tint.good; RadarLevel.CALIBRATING -> p.text3 }
    val night = r.night?.let { if (it == LocalDate.now()) "stanotte" else "notte del ${it.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN))}" }
    HealthCard(
        "Radar malessere", BioIcons.Pulse, if (alert) tint else Tint.heart, trailing = night,
        onClick = { open = !open }, info = RADAR_INFO,
        modifier = Modifier.testTag("radar_card").animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(10.dp))
            Text(r.level.label, style = MaterialTheme.typography.titleMedium, color = p.text, modifier = Modifier.weight(1f))
            val n = r.signals.count { it.hasBaseline }
            if (n > 0) Text(if (open) "Nascondi" else "$n parametri", fontSize = 13.sp, color = p.brand)
        }
        if (alert || r.level == RadarLevel.CALIBRATING || open) Caption(r.detail)
        r.context?.let { InlineNotice(it, NoticeKind.INFO) }
        if (open || alert) {
            r.signals.filter { it.value != null }.forEach { e -> VitalRow(e, highlight = e.concerning) }
            if (r.signals.any { it.hasBaseline }) Caption("Banda = il tuo intervallo abituale (± 2 deviazioni robuste, notti da −30 a −3 giorni). Punto = la notte valutata.")
        }
        if (open) SourcesRow(listOf(ExtraRefs.ALAVI, ExtraRefs.MISHRA))
    }
}

@Composable
private fun VitalRow(e: SignalEval, highlight: Boolean) {
    val p = LocalPalette.current
    val c = when { e.z == null -> p.text2; highlight -> Tint.warn; e.outOfRange -> Tint.blue; else -> Tint.good }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(e.spec.label, fontSize = 13.sp, color = p.text, modifier = Modifier.width(118.dp), maxLines = 1)
        RangeDot(e.value!!, e.low, e.high, c, Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        val sign = if (e.spec.key == "temp" && e.value >= 0) "+" else ""
        Text("$sign${e.value.f(e.spec.decimals)} ${e.spec.unit}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text, modifier = Modifier.width(92.dp), textAlign = TextAlign.End)
    }
    if (!e.hasBaseline) Text("Intervallo in costruzione (${e.baselineNights}/7 notti)", fontSize = 11.sp, color = p.text3)
}

// ---------------------------------------------------------------------------------------------
// Cambiamenti dei punteggi
// ---------------------------------------------------------------------------------------------

fun ScoreChange.text(): String {
    val day = when (date) {
        LocalDate.now() -> "di oggi"
        LocalDate.now().minusDays(1) -> "di ieri"
        else -> "del ${date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN))}"
    }
    return "$what $day ${from ?: "—"} → ${to ?: "—"}: $reason."
}

/** Avviso in Home quando un punteggio già visto è cambiato, con il motivo. */
@Composable
fun ChangesNotice(vm: AppViewModel) {
    val recent = vm.recentChanges
    if (recent.isEmpty()) return
    var show by rememberSaveable { mutableStateOf(false) }
    if (show) ChangesDialog(vm.scoreChanges) { show = false }
    InlineNotice(
        "Punteggio aggiornato. ${recent.first().text()}" + if (recent.size > 1) " (+${recent.size - 1})" else "",
        NoticeKind.INFO, "Storico", { show = true },
    )
}

@Composable
fun ChangesDialog(changes: List<ScoreChange>, onDismiss: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ITALIAN)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cosa è cambiato") },
        text = {
            Column(Modifier.androidxVerticalScroll(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "I giorni passati cambiano solo se arrivano dati nuovi da Connessione Salute (per esempio l'orologio sincronizza in ritardo). Ogni cambiamento è registrato qui con il motivo. Il Recupero di oggi invece si aggiorna durante la giornata con il carico.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (changes.isEmpty()) Text("Nessun cambiamento negli ultimi 14 giorni.", style = MaterialTheme.typography.bodyMedium)
                changes.forEach { c ->
                    Column {
                        Text(c.text(), style = MaterialTheme.typography.bodyMedium)
                        Text("rilevato ${c.at.atZone(ZoneId.systemDefault()).format(fmt)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Fatica percepita (session-RPE)
// ---------------------------------------------------------------------------------------------

private const val RPE_INFO =
    "Metodo session‑RPE (Foster 2001): circa 30 minuti dopo la fine dai un voto alla fatica dell'intero allenamento da 0 a 10. Carico = voto × minuti.\n\n" +
        "Perché: funziona anche senza frequenza cardiaca e coglie lo sforzo muscolare (forza, salite) che la FC sottostima. Una rassegna di 36 studi ne conferma validità e affidabilità in molti sport (Haddad 2017).\n\n" +
        "Monotonia = media/deviazione standard del carico dei 7 giorni (giorni senza allenamento = 0). Monotonia e tensione (carico × monotonia) alte, in 25 atleti, accompagnavano gran parte delle malattie banali (Foster 1998): dati osservazionali, utili come tendenza personale."

private fun sessionWhen(s: Session): String {
    val z = ZoneId.systemDefault()
    val d = s.start.atZone(z).toLocalDate()
    val day = when (d) { LocalDate.now() -> "oggi"; LocalDate.now().minusDays(1) -> "ieri"; else -> d.format(DateTimeFormatter.ofPattern("EEE d", Locale.ITALIAN)) }
    return "$day ${s.start.atZone(z).format(DateTimeFormatter.ofPattern("HH:mm"))} · ${s.minutes.f(0)} min"
}

/** In Home: chiede il voto per l'ultimo allenamento non votato. */
@Composable
fun RpePrompt(vm: AppViewModel) {
    val s = vm.pendingRpe ?: return
    HealthCard("Com'è stato l'allenamento?", BioIcons.Bolt, Tint.vo2, trailing = "fatica 0–10", info = RPE_INFO, modifier = Modifier.testTag("rpe_prompt")) {
        Body("${exerciseName(s.hcType, s.title)} · ${sessionWhen(s)}")
        RpeScale(null) { vm.setRpe(s.id, it) }
        TextButton(onClick = { vm.setRpe(s.id, -1) }) { Text("Non votare questo") }
    }
}

@Composable
fun RpeScale(selected: Int?, onPick: (Int) -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1..5, 6..10).forEach { range ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                range.forEach { v ->
                    val sel = selected == v
                    val c = rpeColor(v)
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) c else c.copy(alpha = 0.14f))
                            .border(1.dp, if (sel) c else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { onPick(v) }.testTag("rpe_$v"),
                        contentAlignment = Alignment.Center,
                    ) { Text("$v", fontWeight = FontWeight.Bold, color = if (sel) Color.Black else p.text) }
                }
            }
        }
        Row {
            Text("1 molto facile", fontSize = 11.sp, color = p.text3, modifier = Modifier.weight(1f))
            Text("5 duro", fontSize = 11.sp, color = p.text3, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("10 massimale", fontSize = 11.sp, color = p.text3, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

private fun rpeColor(v: Int): Color = when {
    v <= 3 -> Tint.good
    v <= 6 -> Tint.warn
    else -> Tint.bad
}

/** Dialogo per votare o correggere il voto di un allenamento (da Fonti e sincronizzazione). */
@Composable
fun RpeDialog(s: Session, current: Int?, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fatica percepita") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${exerciseName(s.hcType, s.title)} · ${sessionWhen(s)}", style = MaterialTheme.typography.bodyMedium)
                RpeScale(current?.takeIf { it >= 0 }) { onPick(it); onDismiss() }
                current?.takeIf { it >= 0 }?.let { Text("Attuale: $it (${SessionRpe.label(it)}) · carico ${SessionRpe.load(it, s.minutes).f(0)}", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

/** In Attività: carico percepito della settimana. */
@Composable
fun RpeLoadCard(vm: AppViewModel) {
    val sm = vm.rpeSummary
    HealthCard("Carico percepito", BioIcons.Bolt, Tint.vo2, trailing = "7 giorni", info = RPE_INFO, modifier = Modifier.testTag("rpe_card")) {
        if (sm == null) {
            Caption("Dai un voto di fatica (0–10) agli allenamenti: compare la richiesta in Home dopo ogni allenamento, oppure toccane uno in Profilo → Fonti e sincronizzazione. Funziona anche per gli allenamenti senza frequenza cardiaca.")
            return@HealthCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            BigValue(sm.week.f(0), "UA", size = 34)
            Spacer(Modifier.width(8.dp))
            Caption("${sm.sessions7} allenamenti votati")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("Media 4 sett.", sm.chronicWeek?.f(0) ?: "—", if (sm.chronicWeek != null) "UA/sett." else "dopo 14 gg", Modifier.weight(1f))
            StatChip("Rapporto", sm.ratio?.f(2) ?: "—", "7 gg / media", Modifier.weight(1f))
            StatChip("Monotonia", sm.monotony?.f(1) ?: "—", "indice", Modifier.weight(1f))
        }
        val msg = when {
            sm.ratio == null -> "Il confronto con le 4 settimane compare dopo 14 giorni di voti."
            sm.ratio > 1.5 -> "Settimana molto più pesante del tuo solito: aumenti bruschi sono quelli da tenere d'occhio, soprattutto se il recupero cala."
            sm.ratio < 0.8 -> "Settimana più leggera del tuo solito."
            else -> "Settimana in linea con le ultime 4."
        }
        Caption(msg)
        if ((sm.monotony ?: 0.0) > 2.0) Caption("Monotonia alta: carichi simili tutti i giorni, senza giorni facili. Alternare giorni duri e leggeri riduce la tensione complessiva.")
        SourcesRow(listOf(ExtraRefs.FOSTER2001, ExtraRefs.HADDAD, ExtraRefs.FOSTER1998))
    }
}
