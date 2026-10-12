package it.cinocinorob.bioeta.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.science.Area
import it.cinocinorob.bioeta.science.Insight
import it.cinocinorob.bioeta.science.Level

fun levelColor(l: Level): Color = when (l) {
    Level.ALERT -> Tint.bad
    Level.ATTENTION -> Tint.warn
    Level.INFO -> Tint.blue
    Level.POSITIVE -> Tint.good
}

fun levelLabel(l: Level): String = when (l) {
    Level.ALERT -> "Da discutere col medico"
    Level.ATTENTION -> "Da tenere d'occhio"
    Level.INFO -> "Informazione"
    Level.POSITIVE -> "Nella norma"
}

fun areaTint(a: Area): Color = when (a) {
    Area.LIPIDS -> Tint.heart
    Area.GLUCOSE -> Tint.activity
    Area.INFLAMMATION -> Tint.blood
    Area.KIDNEY -> Tint.resp
    Area.VITAMINS -> Tint.body
    Area.FITNESS -> Tint.vo2
    Area.HEART -> Tint.heart
    Area.SLEEP -> Tint.sleep
    Area.ACTIVITY -> Tint.activity
    Area.AGING -> Tint.bio
}

private fun certaintyColor(c: String): Color = when (c) {
    "alta" -> Tint.good
    "moderata" -> Tint.blue
    "bassa", "molto bassa" -> Tint.warn
    else -> Tint.body
}

/** Card espandibile di un insight con evidenze, certezza e fonti cliccabili. */
@Composable
fun InsightCard(insight: Insight, onAsk: ((String) -> Unit)?) {
    val p = LocalPalette.current
    val uri = LocalUriHandler.current
    var open by rememberSaveable(insight.id) { mutableStateOf(false) }
    val lc = levelColor(insight.level)
    Row(
        Modifier.fillMaxWidth().softShadow(p.dark).clip(RoundedCornerShape(18.dp)).background(p.card)
            .drawBehind { drawRect(lc, size = androidx.compose.ui.geometry.Size(5.dp.toPx(), size.height)) }
            .clickable { open = !open }.animateContentSize(),
    ) {
        Column(Modifier.padding(start = 19.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(insight.area.label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = areaTint(insight.area))
                Spacer(Modifier.weight(1f))
                Pill(levelLabel(insight.level), lc)
            }
            Text(insight.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.text)
            Text(insight.finding, fontSize = 14.sp, lineHeight = 19.sp, color = p.text)
            if (!open) {
                Text("Mostra le prove (${insight.evidence.size}) ›", fontSize = 13.sp, color = Tint.blue, fontWeight = FontWeight.SemiBold)
            } else {
                insight.evidence.forEach { e ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.field).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(e.text, fontSize = 14.sp, lineHeight = 19.sp, color = p.text)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(e.ref.type.label, Tint.sleep)
                            Spacer(Modifier.width(6.dp))
                            Pill("certezza ${e.certainty}${if (e.gradeByAuthors) " (GRADE)" else ""}", certaintyColor(e.certainty))
                        }
                        Text(
                            "${e.ref.short} · PMID ${e.ref.pmid} ↗",
                            fontSize = 12.sp, color = Tint.blue,
                            modifier = Modifier.clickable { uri.openUri(e.ref.url) },
                        )
                    }
                }
                Caption("Certezza: GRADE quando dichiarato dagli autori, altrimenti stima prudente dal disegno dello studio. Soglie e interventi vanno discussi con il medico.")
                if (onAsk != null) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Tint.sleep.copy(alpha = 0.12f))
                            .clickable { onAsk(insight.ask) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(BioIcons.Sparkle, null, tint = Tint.sleep, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Approfondisci con Claude", fontSize = 14.sp, color = Tint.sleep, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Riquadro riassuntivo: conteggio per livello. */
@Composable
fun InsightSummaryRow(insights: List<Insight>) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Level.ALERT, Level.ATTENTION, Level.INFO, Level.POSITIVE).forEach { l ->
            val c = insights.count { it.level == l }
            Column(
                Modifier.weight(1f).softShadow(p.dark).clip(RoundedCornerShape(16.dp)).background(p.card).padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(c.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (c > 0) levelColor(l) else p.text2)
                Box(Modifier.height(3.dp).width(22.dp).clip(RoundedCornerShape(2.dp)).background(levelColor(l).copy(alpha = if (c > 0) 1f else 0.3f)))
                Text(
                    when (l) { Level.ALERT -> "Medico"; Level.ATTENTION -> "Attenzione"; Level.INFO -> "Info"; Level.POSITIVE -> "Ok" },
                    fontSize = 11.sp, color = p.text2, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(vm: it.cinocinorob.bioeta.AppViewModel, onBack: () -> Unit, onAsk: (String) -> Unit) {
    val p = LocalPalette.current
    val ins = vm.insights
    androidx.compose.material3.Scaffold(
        containerColor = p.bg,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) { Icon(BioIcons.Back, "Indietro", tint = Tint.blue) }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = p.bg),
            )
        },
    ) { pad ->
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { LargeHeader("Insight", "Basati su linee guida e meta-analisi") }
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tint.warn.copy(alpha = 0.12f)).padding(12.dp),
                ) {
                    Icon(BioIcons.Info, null, tint = Tint.warn, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(DISCLAIMER_SHORT, fontSize = 13.sp, lineHeight = 17.sp, color = p.text)
                }
            }
            if (ins.isEmpty()) item {
                Caption("Nessun insight disponibile: inserisci un referto o collega lo smartwatch.")
            } else item { InsightSummaryRow(ins) }
            ins.forEach { i -> item(key = i.id) { InsightCard(i, onAsk) } }
            item {
                Caption("Ogni fonte è stata verificata su PubMed (titolo, PMID e DOI) e i numeri riportati provengono dagli abstract. Gli insight usano i valori degli ultimi 18 mesi e le medie dello smartwatch degli ultimi 7 giorni.")
            }
        }
    }
}
