package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.data.BioAgeEngine
import it.cinocinorob.bioeta.data.Sex
import it.cinocinorob.bioeta.health.HealthRepo
import it.cinocinorob.bioeta.health.Metric
import it.cinocinorob.bioeta.science.FitnessAge
import it.cinocinorob.bioeta.science.PhenoAge
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun AgeScreen(
    vm: AppViewModel,
    modifier: Modifier,
    onOpenInfo: () -> Unit,
    onGoLabs: () -> Unit,
    onGoWear: () -> Unit,
    onGoProfile: () -> Unit,
    onImportReport: () -> Unit,
    onGoInsights: () -> Unit,
    onAsk: (String) -> Unit,
) {
    val profile = vm.profile
    val series = vm.phenoSeries
    val today = LocalDate.now()
    val chronoNow = profile.ageAt(today)
    val p = LocalPalette.current

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LargeHeader("Riepilogo", today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)))
        }

        if (profile.birthDate == null) item {
            HealthCard("Completa il profilo", BioIcons.Person, Tint.body, onClick = onGoProfile) {
                Body("Inserisci data di nascita e sesso: servono per confrontare l'età biologica con quella anagrafica.")
            }
        }

        // ---------- Età biologica (hero) ----------
        item {
            val last = series.lastOrNull()
            if (last == null) {
                GradientHero(listOf(Color(0xFF30C85E), Color(0xFF0FA3A3))) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(BioIcons.Sparkle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("  Età biologica", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    val latestPanel = vm.panels.maxByOrNull { it.date }
                    if (latestPanel == null) {
                        Text("Importa un referto per calcolare la tua PhenoAge", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp)
                        Text("Servono 9 valori: albumina, creatinina, glucosio, PCR, linfociti %, MCV, RDW, fosfatasi alcalina, globuli bianchi.", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    } else {
                        val att = BioAgeEngine.attempt(latestPanel, profile)
                        Text("Nell'ultimo referto mancano:", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(att.missing.joinToString(", "), color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    }
                    Button(
                        onClick = onImportReport,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F7F5F)),
                    ) { Text("Importa referto", fontWeight = FontWeight.SemiBold) }
                }
            } else {
                val delta = last.result.phenoAge - last.age
                val colors = if (delta <= 0) listOf(Color(0xFF34C759), Color(0xFF0FA3A3)) else listOf(Color(0xFFFF9F0A), Color(0xFFFF375F))
                GradientHero(colors, Modifier.clickable { onGoLabs() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(BioIcons.Sparkle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("  Età biologica", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(last.panel.date.itLong(), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            AgeRing(last.result.phenoAge, last.age)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(last.result.phenoAge.f(1), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                                Text("anni", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${delta.fs(1)} anni", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(if (delta <= 0) "rispetto all'età anagrafica: profilo ematico più giovane" else "rispetto all'età anagrafica: profilo ematico più vecchio",
                                color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, lineHeight = 17.sp)
                            Text("Età anagrafica ${last.age.f(1)}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                        }
                    }
                    Text("PhenoAge (Levine 2018): +1 anno a parità di età ≈ +9% rischio di mortalità nella validazione NHANES IV.",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp)
                }
                last.warnings.forEach { Caption("⚠ $it", Tint.warn) }
            }
        }

        // ---------- Insight ----------
        val ins = vm.insights
        if (ins.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    SectionLabel("Insight basati sulle prove", Modifier.weight(1f))
                    TextButton(onClick = onGoInsights) { Text("Tutti (${ins.size})", color = Tint.blue) }
                }
            }
            item { InsightSummaryRow(ins) }
            ins.take(3).forEach { insight ->
                item(key = "ins_${insight.id}") { InsightCard(insight, onAsk) }
            }
        }

        // ---------- Assistente ----------
        item {
            Row(
                Modifier.fillMaxWidth().softShadow(p.dark).clip(RoundedCornerShape(18.dp)).background(p.card)
                    .clickable { onAsk("") }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF5E5CE6), Color(0xFF0A84FF)))),
                    contentAlignment = Alignment.Center,
                ) { Icon(BioIcons.Chat, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Chiedi a Claude", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = p.text)
                    Caption("Commenti sulle tue analisi con fonti PubMed verificate")
                }
                Icon(BioIcons.Chevron, null, tint = p.text2, modifier = Modifier.size(14.dp))
            }
        }

        // ---------- Backup ----------
        if (vm.backupFolder == null) item {
            HealthCard("Backup automatico non attivo", BioIcons.Shield, Tint.warn, onClick = onGoProfile) {
                Caption("Scegli una cartella nel Profilo: l'app salverà una copia dei dati dopo ogni modifica.")
            }
        }

        // ---------- Andamento PhenoAge ----------
        if (series.size >= 2) item {
            val pace = BioAgeEngine.pace(series)
            HealthCard("Andamento età biologica", BioIcons.Sparkle, Tint.bio, trailing = "${series.size} prelievi") {
                HealthChart(
                    listOf(
                        ChartLayer(series.map { it.panel.date.toEpochDay().toDouble() to it.age }, p.text2, LayerKind.DASHED),
                        ChartLayer(series.map { it.panel.date.toEpochDay().toDouble() to it.result.phenoAge }, Tint.bio, LayerKind.AREA, emphasizeLast = true),
                    ),
                    yDecimals = 0,
                )
                Caption("Verde: PhenoAge · tratteggio: età anagrafica")
                if (pace != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigValue(pace.f(2), "anni/anno", size = 24)
                        Pill(if (pace < 1) "più lento della media" else "più veloce della media", if (pace < 1) Tint.good else Tint.bad)
                    }
                    Caption("Ritmo di invecchiamento: pendenza della PhenoAge rispetto all'età (≥3 prelievi in ≥12 mesi). Con pochi punti l'incertezza è ampia.")
                }
            }
        }

        // ---------- Scomposizione ----------
        if (series.size >= 2) item {
            val a = series[series.size - 2]
            val b = series.last()
            val contribs = PhenoAge.contributions(a.input, b.input).sortedByDescending { abs(it.years) }
            val actual = b.result.phenoAge - a.result.phenoAge
            val linear = contribs.sumOf { it.years }
            HealthCard("Cosa è cambiato", BioIcons.Drop, Tint.blood, trailing = "${a.panel.date.itDate()} → ${b.panel.date.itDate()}") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigValue(actual.fs(1), "anni", size = 24, color = if (actual <= 0) Tint.good else Tint.bad)
                }
                ContributionBars(contribs.map { it.label to it.years })
                Caption("Verde = ringiovanisce, rosso = invecchia. Residuo non lineare ${(actual - linear).fs(2)} anni. Piccole variazioni di PCR e globuli bianchi rientrano spesso nella variabilità biologica.")
            }
        }

        // ---------- Tile wearable ----------
        item { SectionLabel("Dati recenti") }
        item {
            val male = profile.sex == Sex.M
            val wearVo2 = vm.wearable[Metric.VO2]?.filter { it.date.isAfter(today.minusDays(30)) }
                ?.map { it.value }?.takeIf { it.isNotEmpty() }?.average()
            val vo2 = profile.vo2Manual ?: wearVo2
            val tiles = listOf(
                Tile("Età cardio", BioIcons.Gauge, Tint.vo2,
                    vo2?.let {
                        val raw = FitnessAge.fitnessAgeRaw(it, male)
                        when { raw < 20 -> "<20"; raw > 80 -> ">80"; else -> raw.f(0) }
                    }, "anni",
                    vm.wearable[Metric.VO2]?.takeLast(30)?.map { it.value }),
                tileFor(vm, Metric.RHR, "FC a riposo", BioIcons.Heart, Tint.heart),
                tileFor(vm, Metric.HRV, "HRV", BioIcons.Pulse, Tint.hrv),
                tileFor(vm, Metric.SLEEP, "Sonno", BioIcons.Moon, Tint.sleep),
                tileFor(vm, Metric.STEPS, "Passi", BioIcons.Bolt, Tint.activity),
                tileFor(vm, Metric.RESP, "Respirazione", BioIcons.Wind, Tint.resp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { t ->
                            HealthCard(t.title, t.icon, t.tint, modifier = Modifier.weight(1f), onClick = onGoWear) {
                                BigValue(t.value ?: "—", if (t.value != null) t.unit else null, size = 26)
                                val sp = t.spark
                                if (sp != null && sp.size >= 2) Sparkline(sp, t.tint, Modifier.fillMaxWidth())
                                else Caption(if (vm.hasAnyHealthPermission) "nessun dato" else "non collegato")
                            }
                        }
                    }
                }
            }
        }

        // ---------- Età cardiorespiratoria (dettaglio) ----------
        item {
            val male = profile.sex == Sex.M
            val wearVo2 = vm.wearable[Metric.VO2]?.filter { it.date.isAfter(today.minusDays(30)) }
                ?.map { it.value }?.takeIf { it.isNotEmpty() }?.average()
            val vo2 = profile.vo2Manual ?: wearVo2
            if (vo2 != null && chronoNow != null) {
                val ref = FitnessAge.reference(chronoNow, male)
                val mets = FitnessAge.metsVsReference(vo2, chronoNow, male)
                HealthCard("Età cardiorespiratoria", BioIcons.Gauge, Tint.vo2) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BigValue(vo2.f(1), "mL/kg/min", size = 28)
                            Caption(if (profile.vo2Manual != null) "VO₂max misurato" else "VO₂max stimato dall'orologio (media 30 gg)")
                        }
                        Pill("${mets.fs(1)} MET", if (mets >= 0) Tint.good else Tint.bad)
                    }
                    Caption("Mediana di riferimento alla tua età: ${ref.f(1)} mL/kg/min (FRIEND-I). Ogni MET in più è associato a circa −15% di mortalità negli uomini sotto i 60 anni (studio HUNT). Le stime da smartwatch hanno errori di alcuni mL/kg/min: conta più la tendenza.")
                }
            }
        }

        item {
            HealthCard("Metodo e limiti", BioIcons.Info, Tint.blue, onClick = onOpenInfo) {
                Caption("Ogni indicatore resta separato e tracciabile alla sua fonte: i punteggi unici di Oura/Ultrahuman non hanno coefficienti pubblicati né validazione sugli esiti.")
            }
        }
    }
}

private data class Tile(
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val value: String?,
    val unit: String,
    val spark: List<Double>?,
)

private fun tileFor(vm: AppViewModel, m: Metric, title: String, icon: ImageVector, tint: Color): Tile {
    val pts = vm.wearable[m].orEmpty()
    val s = if (pts.isNotEmpty()) HealthRepo.summarize(m, pts) else null
    val v = s?.recent ?: pts.lastOrNull()?.value
    val value = v?.let {
        if (m == Metric.SLEEP) {
            val hh = it.toInt(); val mm = ((it - hh) * 60).toInt()
            "${hh}h ${mm.toString().padStart(2, '0')}"
        } else it.f(if (m == Metric.RESP) 1 else 0)
    }
    val unit = when (m) { Metric.SLEEP -> ""; Metric.STEPS -> "/die"; else -> m.unit }
    return Tile(title, icon, tint, value, unit, pts.takeLast(30).map { it.value })
}
