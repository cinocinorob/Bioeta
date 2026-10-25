package it.cinocinorob.bioeta.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.science.Pillar
import it.cinocinorob.bioeta.science.PillarStatus

private fun pillarIcon(key: String): Pair<ImageVector, Color> = when (key) {
    "vo2" -> BioIcons.Gauge to Tint.vo2
    "steps" -> BioIcons.Bolt to Tint.activity
    "active" -> BioIcons.Bolt to Tint.good
    "sleep" -> BioIcons.Moon to Tint.sleep
    "rhr" -> BioIcons.Heart to Tint.heart
    else -> BioIcons.Pulse to Tint.load
}

@Composable
private fun statusColor(s: PillarStatus): Color = when (s) {
    PillarStatus.GOOD -> Tint.good
    PillarStatus.MID -> Tint.warn
    PillarStatus.LOW -> Tint.bad
    PillarStatus.NA -> LocalPalette.current.text3
}

private const val LONG_INFO =
    "Questi sono gli indicatori misurabili dall'orologio che negli studi di popolazione sono legati in modo solido alla salute a lungo termine (mortalità, malattie cardiovascolari). Le soglie vengono dalle meta-analisi citate in ogni scheda.\n\n" +
        "Sono associazioni osservazionali: dicono cosa vale la pena curare, non quanto rischi tu personalmente, e non sono una diagnosi. A differenza dei punteggi giornalieri, cambiano lentamente: guardali una volta al mese."

/** Scheda della Home: un riepilogo per riga, stato colorato. */
@Composable
fun LongTermCard(list: List<Pillar>, onOpen: () -> Unit) {
    val p = LocalPalette.current
    val ok = list.count { it.status == PillarStatus.GOOD }
    val known = list.count { it.status != PillarStatus.NA }
    HealthCard(
        if (known > 0) "$ok su $known in zona buona" else "In costruzione", BioIcons.Shield, Tint.brand,
        onClick = onOpen, info = LONG_INFO, modifier = Modifier.testTag("longterm_card"),
    ) {
        list.forEachIndexed { i, pl ->
            val (ic, tint) = pillarIcon(pl.key)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(p.toneStrong(tint)), contentAlignment = Alignment.Center) {
                    Icon(ic, null, tint = p.ink(tint), modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(pl.title, style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1)
                    Text(pl.verdict, style = MaterialTheme.typography.bodySmall, color = p.text2, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(pl.value ?: "—", style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, fontFeatureSettings = TABULAR),
                            color = if (pl.value == null) p.text3 else p.text, maxLines = 1)
                        if (pl.value != null && pl.unit.length <= 4) Text(" ${pl.unit}", style = MaterialTheme.typography.labelSmall, color = p.text2, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor(pl.status)))
                        Spacer(Modifier.width(5.dp))
                        Text(pl.status.label, style = MaterialTheme.typography.labelSmall, color = p.text2, maxLines = 1)
                    }
                }
            }
            if (i < list.lastIndex) HorizontalDivider(color = p.sep)
        }
    }
}

/** Dettaglio: per ogni indicatore valore, giudizio, periodo, cosa lo migliora e cosa dice la ricerca. */
@Composable
fun LongTermScreen(vm: AppViewModel, onBack: () -> Unit) {
    val list = vm.longTerm
    SubScaffold("Salute a lungo termine", onBack) { m ->
        LazyColumn(
            m.fillMaxSize().testTag("longterm_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InlineNotice("Indicatori con soglie dalle meta-analisi più recenti. Sono associazioni negli studi di popolazione, non la tua probabilità personale e non una diagnosi.")
            }
            items(list, key = { it.key }) { PillarCard(it) }
            item { Caption("Fonti verificate su PubMed, ottobre 2026. Tocca un DOI per aprire lo studio.") }
        }
    }
}

@Composable
private fun PillarCard(pl: Pillar) {
    val p = LocalPalette.current
    val (ic, tint) = pillarIcon(pl.key)
    val uri = LocalUriHandler.current
    HealthCard(pl.title, ic, tint, modifier = Modifier.testTag("pillar_${pl.key}")) {
        Row(verticalAlignment = Alignment.Bottom) {
            BigValue(pl.value ?: "—", if (pl.value != null) pl.unit else null, size = 34, color = if (pl.value == null) p.text3 else null)
            Spacer(Modifier.weight(1f))
            Pill(pl.status.label, statusColor(pl.status))
        }
        Text(pl.verdict, style = MaterialTheme.typography.titleMedium, color = p.text)
        Caption(pl.detail)
        if (pl.estimate) Text("Stima", style = MaterialTheme.typography.labelMedium, color = p.ink(Tint.hrv))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.tone(tint)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Cosa lo migliora", style = MaterialTheme.typography.labelLarge, color = p.text)
            Text(pl.howTo, style = MaterialTheme.typography.bodyMedium, color = p.text2)
        }
        Text("Cosa dice la ricerca", style = MaterialTheme.typography.labelLarge, color = p.text)
        Text(pl.evidence, style = MaterialTheme.typography.bodyMedium, color = p.text2)
        if (pl.doi.isNotEmpty()) Text(
            "doi:${pl.doi}", style = MaterialTheme.typography.labelMedium, color = p.ink(p.brand),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { runCatching { uri.openUri("https://doi.org/${pl.doi}") } }.padding(vertical = 4.dp),
        ) else Text("OMS 2020, PMID 33239350", style = MaterialTheme.typography.labelMedium, color = p.text3)
    }
}
