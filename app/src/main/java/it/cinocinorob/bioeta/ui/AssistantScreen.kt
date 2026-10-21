package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.assistant.Claude

const val DISCLAIMER_SHORT = "Informazioni a scopo educativo: non sono diagnosi né prescrizioni. Rivolgiti sempre al tuo medico o a uno specialista."

@Composable
fun AssistantScreen(vm: AppViewModel, modifier: Modifier, initialQuestion: String?, onConsumeInitial: () -> Unit) {
    when {
        !vm.disclaimerAccepted -> DisclaimerGate(vm, modifier)
        vm.assistantMode == "free" -> FreeClaudeView(vm, modifier, initialQuestion, onConsumeInitial)
        vm.apiKey.isBlank() -> ApiKeySetup(vm, modifier)
        else -> ChatView(vm, modifier, initialQuestion, onConsumeInitial)
    }
}

// --------------------------------------------------------------------------------------------
// 1) Disclaimer obbligatorio
// --------------------------------------------------------------------------------------------

@Composable
private fun DisclaimerGate(vm: AppViewModel, modifier: Modifier) {
    var c1 by remember { mutableStateOf(false) }
    var c2 by remember { mutableStateOf(false) }
    var c3 by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LargeHeader("Assistente", "Claude + PubMed")
        HealthCard("Prima di iniziare", BioIcons.Sparkle, Tint.blue) {
            Body("L'assistente commenta analisi e andamenti cercando le prove su PubMed. Ogni affermazione deve citare uno studio, e l'app verifica che le citazioni esistano davvero.")
        }
        HealthCard("Avvertenza medica", BioIcons.Info, Tint.bad) {
            Body("Le risposte hanno scopo esclusivamente informativo ed educativo. Non costituiscono diagnosi, prescrizione o parere medico e non sostituiscono la visita. Per qualsiasi decisione su esami, terapie, dieta, integratori o attività fisica rivolgiti sempre al tuo medico o a uno specialista.")
            Body("In caso di sintomi acuti o urgenze chiama il 112 o rivolgiti al pronto soccorso.", weight = FontWeight.SemiBold)
        }
        HealthCard("Limiti dell'intelligenza artificiale", BioIcons.Info, Tint.warn) {
            Caption("Anche con le fonti, un modello linguistico può sbagliare: interpretare male uno studio, generalizzare oltre la popolazione studiata o tralasciare prove contrarie. L'app controlla che ogni PMID citato esista su PubMed e se è stato effettivamente letto durante la ricerca, ma non può verificare automaticamente che ogni frase rispecchi lo studio. Le fonti sono cliccabili: controllale.")
        }
        HealthCard("Privacy", BioIcons.Person, Tint.blue) {
            Caption("Modalità gratuita: l'app prepara un testo con età, sesso, valori degli ultimi 4 referti, pressione e medie dello smartwatch (senza nome) e sei tu a inviarlo dalla tua app Claude, con il tuo account. Modalità API: lo stesso testo viene inviato ad Anthropic con la tua chiave. Le ricerche su PubMed per verificare le fonti contengono solo identificativi degli articoli.")
        }
        InsetGroup {
            CheckRow(c1, { c1 = it }, "Ho capito che le risposte non sostituiscono il parere del medico.")
            CheckRow(c2, { c2 = it }, "Ho capito che l'IA può commettere errori e che devo verificare le fonti.")
            CheckRow(c3, { c3 = it }, "Acconsento a condividere i miei dati pseudonimizzati con Claude (Anthropic) quando faccio una domanda.", last = true)
        }
        Button(
            onClick = { vm.acceptDisclaimer() }, enabled = c1 && c2 && c3,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        ) { Text("Accetto e continuo", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, text: String, last: Boolean = false) {
    GroupRow(
        title = text,
        leading = { Checkbox(checked = checked, onCheckedChange = onChange) },
        showDivider = !last,
        onClick = null,
    )
}

// --------------------------------------------------------------------------------------------
// 2) Configurazione chiave API
// --------------------------------------------------------------------------------------------

@Composable
private fun ApiKeySetup(vm: AppViewModel, modifier: Modifier) {
    val p = LocalPalette.current
    var key by remember { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LargeHeader("Collega Claude", "Assistente")
        ModeSwitch(vm)
        HealthCard("Chiave API Anthropic", BioIcons.Sparkle, Tint.sleep) {
            Body("L'app usa la tua chiave personale dell'API di Claude: paghi solo quello che usi, direttamente ad Anthropic.")
            Caption("1. Dal browser apri console.anthropic.com e accedi.\n2. Aggiungi credito (Billing) e imposta un limite di spesa mensile.\n3. API Keys → Create Key, poi copiala e incollala qui.")
            Caption("Costo indicativo: qualche centesimo di dollaro per domanda (ogni risposta mostra il costo stimato).")
            OutlinedTextField(
                value = key, onValueChange = { key = it }, singleLine = true,
                label = { Text("sk-ant-…") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = p.field, unfocusedContainerColor = p.field,
                    unfocusedBorderColor = Color.Transparent, focusedBorderColor = Tint.blue,
                ),
            )
            Caption("La chiave resta solo su questo telefono ed è esclusa dai backup.")
            Button(
                onClick = { vm.saveApiKey(key) }, enabled = key.trim().startsWith("sk-"),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            ) { Text("Salva chiave", fontWeight = FontWeight.SemiBold) }
        }
        ModelPicker(vm)
    }
}

@Composable
fun ModelPicker(vm: AppViewModel) {
    HealthCard("Modello", BioIcons.Gauge, Tint.sleep) {
        Claude.MODELS.forEach { (id, label) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.selectModel(id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = vm.model == id, onClick = { vm.selectModel(id) })
                Text(label, fontSize = 14.sp, color = LocalPalette.current.text)
            }
        }
    }
}

// --------------------------------------------------------------------------------------------
// 3) Chat
// --------------------------------------------------------------------------------------------

private val SUGGESTIONS = listOf(
    "Commenta il mio ultimo referto",
    "Come sta andando la mia età biologica e quali marcatori pesano di più?",
    "Quali interventi hanno le prove migliori per migliorare i miei valori?",
    "Come interpreto i dati dello smartwatch degli ultimi mesi?",
)

@Composable
private fun ChatView(vm: AppViewModel, modifier: Modifier, initialQuestion: String?, onConsumeInitial: () -> Unit) {
    val p = LocalPalette.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(initialQuestion) {
        if (!initialQuestion.isNullOrBlank()) {
            input = initialQuestion
            onConsumeInitial()
        }
    }
    LaunchedEffect(vm.chat.size, vm.thinking) {
        val total = vm.chat.size + 2
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { LargeHeader("Assistente", "Claude + PubMed") }
                    if (vm.chat.isNotEmpty()) TextButton(onClick = { vm.clearChat() }) { Text("Nuova", color = Tint.blue) }
                }
            }
            item { ModeSwitch(vm) }
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tint.warn.copy(alpha = 0.12f)).padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(BioIcons.Info, null, tint = Tint.warn, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(DISCLAIMER_SHORT, fontSize = 13.sp, lineHeight = 17.sp, color = p.text)
                }
            }
            if (vm.chat.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Caption("Prova a chiedere:")
                        SUGGESTIONS.forEach { s ->
                            Text(
                                s, fontSize = 15.sp, color = Tint.blue,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.card)
                                    .clickable { vm.ask(s) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
            itemsIndexed(vm.chat) { _, m ->
                if (m.fromUser) UserBubble(m.text) else AssistantBubble(m)
            }
            if (vm.thinking) item {
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(p.card).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Tint.sleep)
                    Spacer(Modifier.size(10.dp))
                    Text(vm.thinkingStep ?: "…", fontSize = 13.sp, color = p.text2)
                }
            }
        }
        // Barra di input
        Row(
            Modifier.fillMaxWidth().background(p.card).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Chiedi sulle tue analisi…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = p.field, unfocusedContainerColor = p.field,
                    unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.size(8.dp))
            val enabled = input.isNotBlank() && !vm.thinking
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (enabled) Tint.blue else p.sep)
                    .clickable(enabled = enabled) { vm.ask(input); input = "" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(BioIcons.Send, "Invia", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text, color = LocalPalette.current.text, fontSize = 15.sp, lineHeight = 20.sp,
            modifier = Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                .background(LocalPalette.current.card2)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AssistantBubble(m: AppViewModel.ChatMsg) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
            .background(if (m.isError) Tint.bad.copy(alpha = 0.12f) else p.card)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Tint.blue.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(BioIcons.Sparkle, null, tint = Tint.blue, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(if (m.isError) "Errore" else "Claude", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (m.isError) Tint.bad else p.text)
        }
        Text(renderMarkdown(m.text), fontSize = 15.sp, lineHeight = 21.sp, color = p.text)

        val r = m.citations
        if (r != null) {
            if (r.verified.isNotEmpty() || r.notFound.isNotEmpty()) {
                Spacer(Modifier.size(2.dp))
                Text("FONTI VERIFICATE SU PUBMED", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = p.text2)
            }
            val uri = androidx.compose.ui.platform.LocalUriHandler.current
            r.verified.forEach { s ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.field)
                        .clickable { uri.openUri("https://pubmed.ncbi.nlm.nih.gov/${s.pmid}/") }
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val c = when (s.rank) { 3 -> Tint.good; 2 -> Tint.blue; else -> Tint.warn }
                        Pill(s.evidenceLabel, c)
                        Spacer(Modifier.size(6.dp))
                        if (s.pmid in r.notConsulted) Pill("non letto dal modello", Tint.warn)
                    }
                    Text(s.title, fontSize = 13.sp, lineHeight = 17.sp, color = p.text, modifier = Modifier.padding(top = 4.dp))
                    Text("${s.firstAuthor ?: ""} · ${s.journal} ${s.year} · PMID ${s.pmid}", fontSize = 12.sp, color = p.text2)
                }
            }
            if (r.notFound.isNotEmpty()) {
                Text("⚠ PMID citati ma inesistenti su PubMed: ${r.notFound.joinToString(", ")}. Le affermazioni collegate non sono verificate.", fontSize = 13.sp, color = Tint.bad)
            }
            if (r.verified.isEmpty() && r.notFound.isEmpty() && !m.isError) {
                Text("⚠ La risposta non contiene citazioni PubMed: trattala come non verificata.", fontSize = 13.sp, color = Tint.warn)
            }
        }
        m.cost?.let { Text(it, fontSize = 11.sp, color = p.text2) }
    }
}

/** Markdown minimo: **grassetto**, titoli "#", elenchi "- ", link ai PMID. */
@Composable
private fun renderMarkdown(src: String): AnnotatedString {
    val linkColor = Tint.blue
    val pmidRe = Regex("""\[?PMID[:\s]*([0-9]{5,9})]?""", RegexOption.IGNORE_CASE)
    return buildAnnotatedString {
        src.lines().forEachIndexed { li, raw ->
            if (li > 0) append("\n")
            var line = raw
            var heading = false
            if (line.trimStart().startsWith("#")) { line = line.trimStart().trimStart('#').trim(); heading = true }
            if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) line = "• " + line.trimStart().drop(2)
            val start = length
            var i = 0
            var bold = false
            while (i < line.length) {
                val m = pmidRe.find(line, i)
                val boldIdx = line.indexOf("**", i)
                val next = listOfNotNull(m?.range?.first, boldIdx.takeIf { it >= 0 }).minOrNull()
                if (next == null) { appendStyled(line.substring(i), bold); break }
                if (next > i) appendStyled(line.substring(i, next), bold)
                if (boldIdx == next) { bold = !bold; i = next + 2 }
                else if (m != null) {
                    val id = m.groupValues[1]
                    withLink(LinkAnnotation.Url("https://pubmed.ncbi.nlm.nih.gov/$id/", TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)))) {
                        append("[PMID $id]")
                    }
                    i = m.range.last + 1
                }
            }
            if (heading) addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp), start, length)
        }
    }
}

private fun AnnotatedString.Builder.appendStyled(s: String, bold: Boolean) {
    if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s) } else append(s)
}
