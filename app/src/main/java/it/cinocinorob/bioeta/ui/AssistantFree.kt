package it.cinocinorob.bioeta.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.assistant.CitationCheck

private const val CLAUDE_PACKAGE = "com.anthropic.claude"

/** Selettore tra modalità gratuita (app Claude) e API a pagamento. */
@Composable
fun ModeSwitch(vm: AppViewModel) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(p.field).padding(4.dp),
    ) {
        listOf("free" to "Gratis con l'app Claude", "api" to "API (a consumo)").forEach { (id, label) ->
            val sel = vm.assistantMode == id
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (sel) p.card else Color.Transparent)
                    .clickable { vm.selectAssistantMode(id) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (sel) p.text else p.text2)
            }
        }
    }
}

/** Copia negli appunti e apre l'app Claude (o claude.ai) con il testo. */
fun openInClaude(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("BioEtà → Claude", text))
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent(send).setPackage(CLAUDE_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Toast.makeText(context, "Testo inviato a Claude (copiato anche negli appunti)", Toast.LENGTH_LONG).show()
    } catch (e: ActivityNotFoundException) {
        // App non installata: claude.ai nel browser; il testo resta negli appunti da incollare
        val url = if (text.length <= 6000) "https://claude.ai/new?q=" + Uri.encode(text) else "https://claude.ai/new"
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Toast.makeText(context, "Testo copiato: se non compare, incollalo nella chat di Claude", Toast.LENGTH_LONG).show()
    }
}

private val FREE_SUGGESTIONS = listOf(
    "Commenta il mio ultimo referto e cosa posso migliorare",
    "Come interpreto la mia pressione e cosa la abbassa secondo le prove?",
    "Cosa dicono sonno e recupero delle ultime settimane?",
    "Quali marcatori pesano di più sulla mia età biologica?",
)

@Composable
fun FreeClaudeView(vm: AppViewModel, modifier: Modifier, initialQuestion: String?, onConsumeInitial: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var question by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }

    LaunchedEffect(initialQuestion) {
        if (!initialQuestion.isNullOrBlank()) { question = initialQuestion; onConsumeInitial() }
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = p.field, unfocusedContainerColor = p.field,
        unfocusedBorderColor = Color.Transparent, focusedBorderColor = Tint.sleep,
    )

    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LargeHeader("Chiedi a Claude", "Assistente")
        ModeSwitch(vm)

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Tint.warn.copy(alpha = 0.13f)).padding(12.dp),
        ) {
            Icon(BioIcons.Info, null, tint = Tint.warn, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(DISCLAIMER_SHORT, fontSize = 13.sp, lineHeight = 17.sp, color = p.text)
        }

        HealthCard("1 · Scrivi la domanda", BioIcons.Chat, Tint.sleep) {
            Caption("BioEtà aggiunge i tuoi dati (senza nome) e le regole scientifiche: fonti PubMed con PMID/DOI, certezza delle prove, prove contrarie, rinvio al medico.")
            OutlinedTextField(
                value = question, onValueChange = { question = it },
                placeholder = { Text("Es. come posso migliorare il mio LDL?") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                shape = RoundedCornerShape(18.dp), colors = fieldColors,
            )
            FREE_SUGGESTIONS.forEach { s ->
                Text(
                    s, fontSize = 14.sp, color = Tint.sleep, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tint.sleep.copy(alpha = 0.09f))
                        .clickable { question = s }.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }

        HealthCard("2 · Invia dalla tua app Claude", BioIcons.Send, Tint.blue) {
            Caption("Usa il tuo account Claude, anche gratuito: nessun costo aggiuntivo in BioEtà. Se nell'app Claude hai la ricerca web attiva, potrà consultare PubMed direttamente.")
            Button(
                onClick = { openInClaude(ctx, vm.freePrompt(question)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Icon(BioIcons.Sparkle, null, modifier = Modifier.size(18.dp))
                Text("  Apri in Claude", fontWeight = FontWeight.ExtraBold)
            }
            Text(
                if (showPreview) "Nascondi il testo che verrà inviato" else "Mostra il testo che verrà inviato",
                fontSize = 13.sp, color = Tint.blue, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { showPreview = !showPreview },
            )
            if (showPreview) Text(
                vm.freePrompt(question), fontSize = 11.sp, lineHeight = 15.sp, color = p.text2,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.field).padding(10.dp),
            )
        }

        HealthCard("3 · Verifica le fonti della risposta", BioIcons.Shield, Tint.good) {
            Caption("Copia la risposta di Claude e incollala qui: BioEtà controlla su PubMed ogni PMID e DOI citato e mostra il tipo di studio.")
            OutlinedTextField(
                value = pasted, onValueChange = { pasted = it },
                placeholder = { Text("Incolla qui la risposta…") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 220.dp),
                shape = RoundedCornerShape(18.dp), colors = fieldColors,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let { pasted = it }
                }, modifier = Modifier.weight(1f)) { Text("Incolla") }
                Button(
                    onClick = { vm.checkPastedAnswer(pasted) }, enabled = pasted.isNotBlank() && !vm.checkingPaste,
                    modifier = Modifier.weight(1f),
                ) { Text("Verifica", fontWeight = FontWeight.Bold) }
            }
            if (vm.checkingPaste) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Tint.good)
                Caption("  Verifica su PubMed…")
            }
            vm.pastedReport?.let { CitationReportView(it, hasText = pasted.isNotBlank()) }
        }
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
fun CitationReportView(r: CitationCheck.Report, hasText: Boolean) {
    val p = LocalPalette.current
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val total = r.verified.size + r.notFound.size
        when {
            total == 0 && hasText -> Text("⚠ Nessun PMID o DOI trovato nella risposta: trattala come non verificata e chiedi a Claude di indicare le fonti.", color = Tint.warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            r.notFound.isEmpty() && r.verified.isNotEmpty() -> Pill("${r.verified.size} fonti su ${total} verificate", Tint.good)
            else -> Pill("${r.verified.size} di $total fonti verificate", Tint.warn)
        }
        r.verified.forEach { s ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.field)
                    .clickable { uri.openUri("https://pubmed.ncbi.nlm.nih.gov/${s.pmid}/") }.padding(10.dp),
            ) {
                Pill(s.evidenceLabel, when (s.rank) { 3 -> Tint.good; 2 -> Tint.blue; else -> Tint.warn })
                Text(s.title, fontSize = 13.sp, lineHeight = 17.sp, color = p.text, modifier = Modifier.padding(top = 4.dp))
                Text("${s.firstAuthor ?: ""} · ${s.journal} ${s.year} · PMID ${s.pmid}", fontSize = 12.sp, color = p.text2)
            }
        }
        if (r.notFound.isNotEmpty()) Text(
            "⚠ Non trovati su PubMed: ${r.notFound.joinToString(", ")}. Le affermazioni collegate non sono verificate (possibile citazione errata o inventata).",
            color = Tint.bad, fontSize = 13.sp,
        )
        Caption("La verifica conferma che lo studio esiste e il suo tipo, non che la frase lo riporti fedelmente: apri le fonti più importanti.")
    }
}
