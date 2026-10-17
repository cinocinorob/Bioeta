package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.data.Sex
import it.cinocinorob.bioeta.data.SyncState
import it.cinocinorob.bioeta.health.HealthRepo
import it.cinocinorob.bioeta.health.Metric
import it.cinocinorob.bioeta.science.Fitness
import it.cinocinorob.bioeta.science.FitnessAge
import it.cinocinorob.bioeta.science.LoadQuality
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Cruscotto principale. Regole: nessun valore inventato; un dato mancante è "—" con lo stato
 * "Non disponibile" e il motivo; ogni tessera dice se il valore è misurato o stimato e su quale periodo.
 */
@Composable
fun HomeScreen(
    vm: AppViewModel,
    modifier: Modifier,
    onGoActivity: () -> Unit,
    onGoHealth: () -> Unit,
    onGoAge: () -> Unit,
    onGoLabs: () -> Unit,
    onOpenMetric: (String) -> Unit,
    onOpenSync: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenJournal: () -> Unit,
    onGoBp: () -> Unit,
    onAsk: (String) -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val p = LocalPalette.current
    val today = LocalDate.now()
    val t = vm.today
    val profile = vm.profile
    val male = profile.sex == Sex.M
    val chrono = profile.ageAt(today)
    val act = vm.todayActivity
    val dayAct = vm.todayDayActivity
    val target = vm.targetLoad
    val mood = moodFor(t.readiness.score, t.readiness.calibrating || !vm.hasAnyHealthPermission)

    LazyColumn(
        modifier.fillMaxSize().testTag("home_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------------- Stato dei dati ----------------
        item { DataFreshnessRow(vm, onOpenSync) }

        when {
            vm.hcStatus == HealthConnectClient.SDK_UNAVAILABLE -> item {
                EmptyState(BioIcons.Info, "Connessione Salute non disponibile", "Questo telefono non supporta Connessione Salute: restano disponibili analisi del sangue, pressione e diario.", Tint.warn)
            }
            vm.hcStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> item {
                EmptyState(BioIcons.Info, "Aggiorna Connessione Salute", "Serve una versione più recente di Connessione Salute per leggere i dati dell'orologio.", Tint.warn, "Diagnostica", onOpenSync)
            }
            vm.hcAvailable && !vm.hasAnyHealthPermission -> item {
                EmptyState(BioIcons.Heart, "Collega i tuoi dati", "BioEtà legge (mai scrive) da Connessione Salute passi, allenamenti, frequenza cardiaca, HRV, sonno e composizione corporea. I dati restano sul telefono.", Tint.brand, "Concedi accesso", onRequestPermissions)
            }
        }
        if (vm.missingRecoveryPermissions) item {
            InlineNotice("Alcuni permessi non sono concessi: certi riquadri resteranno vuoti.", NoticeKind.WARN, "Concedi", onRequestPermissions)
        }
        val lw = vm.lastWearable
        if (vm.hasAnyHealthPermission && lw != null && java.time.Duration.between(lw, java.time.Instant.now()).toHours() >= 14) item {
            InlineNotice("L'orologio non scrive in Connessione Salute da ${agoShort(lw)}: sonno e dati recenti possono mancare.", NoticeKind.WARN, "Verifica", onOpenSync)
        }
        t.readiness.flags.forEach { fl -> item { InlineNotice(fl, NoticeKind.WARN) } }

        // ---------------- Quadrante di oggi ----------------
        item { HeroToday(vm, mood, onGoHealth, onGoActivity) }

        // ---------------- Età ----------------
        item { SectionHeader("Età", "Dettagli", onGoAge) }
        item {
            val series = vm.phenoSeries
            val last = series.lastOrNull()
            val prev = series.dropLast(1).lastOrNull()
            val wearVo2 = vm.wearable[Metric.VO2]?.filter { it.date.isAfter(today.minusDays(30)) }?.map { it.value }?.takeIf { it.isNotEmpty() }?.average()
            val vo2 = profile.vo2Manual ?: wearVo2
            PairRow(
                {
                    MetricTile(
                        "Età biologica", last?.result?.phenoAge?.f(1), "anni", Tint.bio, BioIcons.Sparkle,
                        if (last != null) DataStatus.ESTIMATED else DataStatus.UNAVAILABLE,
                        when {
                            last == null && series.isEmpty() -> "PhenoAge: servono 9 esami del sangue"
                            last == null -> "Dati incompleti nell'ultimo prelievo"
                            else -> "PhenoAge · prelievo ${last.panel.date.itLong()}" + (chrono?.let { " · anagrafica ${it.f(1)}" } ?: "")
                        },
                        it,
                        trend = if (last != null && prev != null) {
                            val d = last.result.phenoAge - prev.result.phenoAge
                            Trend("${abs(d).f(1)} anni vs prelievo prec.", d > 0, goodWhenUp = false)
                        } else null,
                        onClick = onGoAge,
                    )
                },
                {
                    val raw = vo2?.let { FitnessAge.fitnessAgeRaw(it, male) }
                    MetricTile(
                        "Età cardiorespiratoria", raw?.let { r -> when { r < 20 -> "<20"; r > 80 -> ">80"; else -> r.f(0) } }, "anni", Tint.vo2, BioIcons.Gauge,
                        if (raw != null) DataStatus.ESTIMATED else DataStatus.UNAVAILABLE,
                        when {
                            vo2 == null -> "Serve il VO₂max (dall'orologio o inserito nel profilo)"
                            profile.vo2Manual != null -> "VO₂max ${vo2.f(1)} misurato (profilo) · FRIEND"
                            else -> "VO₂max ${vo2.f(1)} stimato dall'orologio, media 30 gg"
                        },
                        it, onClick = { onOpenMetric("VO2") },
                    )
                },
            )
        }

        // ---------------- Fisiologia ----------------
        item { SectionHeader("Recupero e fisiologia", "Salute", onGoHealth) }
        item {
            PairRow(
                { m -> BaselineTile(vm, Metric.RHR, "FC a riposo", BioIcons.Heart, Tint.heart, m) { onOpenMetric("RHR") } },
                { m -> BaselineTile(vm, Metric.HRV, "HRV notturna", BioIcons.Pulse, Tint.hrv, m) { onOpenMetric("HRV") } },
            )
        }
        item {
            PairRow(
                { m ->
                    val n = t.night
                    MetricTile(
                        "Sonno", n?.let { fmtHours(it.totalSleepMin / 60) }, null, Tint.sleep, BioIcons.Moon,
                        if (n != null) DataStatus.MEASURED else DataStatus.UNAVAILABLE,
                        if (n != null) "Stanotte · punteggio ${t.sleep?.score ?: "—"}/100" else "Nessuna notte registrata in Connessione Salute",
                        m, spark = vm.sleepNights.takeLast(14).map { it.totalSleepMin / 60 }, onClick = { onOpenMetric("SLEEP") },
                    )
                },
                { m ->
                    val r = t.readiness
                    MetricTile(
                        "Recupero", r.score?.toString(), "/100", Tint.brand, BioIcons.Bolt,
                        when { r.score == null -> DataStatus.UNAVAILABLE; r.provisional -> DataStatus.PARTIAL; else -> DataStatus.ESTIMATED },
                        when {
                            r.score == null -> "Servono almeno 3 giorni di HRV o FC a riposo"
                            r.provisional -> "Indice BioEtà · baseline di ${r.baselineDays} giorni (provvisoria)"
                            else -> "Indice BioEtà vs baseline personale di 60 giorni"
                        },
                        m, spark = vm.recoveryHistory.mapNotNull { it.readiness.score?.toDouble() }, onClick = onGoHealth,
                    )
                },
            )
        }

        // ---------------- Attività ----------------
        item { SectionHeader("Attività", "Dettagli", onGoActivity) }
        item {
            val goal = Fitness.stepGoal(chrono)
            val steps7 = vm.wearable[Metric.STEPS].orEmpty().filter { it.date.isAfter(today.minusDays(8)) }.map { it.value }
            PairRow(
                { m ->
                    MetricTile(
                        "Passi oggi", act?.let { String.format(Locale.ITALY, "%,d", it.steps) }, null, Tint.activity, BioIcons.Bolt,
                        if (act != null) DataStatus.MEASURED else DataStatus.UNAVAILABLE,
                        if (act != null) "Obiettivo ${String.format(Locale.ITALY, "%,d", goal)} · giornata in corso" else "Nessun dato sui passi oggi",
                        m, spark = steps7, onClick = { onOpenMetric("STEPS") },
                    )
                },
                { m ->
                    val week = Fitness.weekActiveMinutes(vm.dayLoads, today)
                    val est = dayAct?.estimatedShare ?: 0
                    MetricTile(
                        "Minuti attivi", dayAct?.activeMinutes?.toString(), "min", Tint.good, BioIcons.Bolt,
                        when { dayAct == null -> DataStatus.UNAVAILABLE; est > 0 -> DataStatus.ESTIMATED; else -> DataStatus.MEASURED },
                        if (dayAct == null) "Servono FC o allenamenti da Connessione Salute"
                        else "Oggi · settimana $week/150 (OMS)" + if (est > 0) " · $est stimati" else "",
                        m, onClick = { onOpenMetric(DETAIL_ACTIVE) },
                    )
                },
            )
        }
        item {
            val q = dayAct?.quality ?: LoadQuality.UNAVAILABLE
            PairRow(
                { m ->
                    MetricTile(
                        "Carico cardiaco", if (q == LoadQuality.UNAVAILABLE) null else dayAct!!.load.f(0), null, Tint.load, BioIcons.Pulse,
                        when (q) { LoadQuality.COMPLETE -> DataStatus.MEASURED; LoadQuality.PARTIAL -> DataStatus.PARTIAL; LoadQuality.UNAVAILABLE -> DataStatus.UNAVAILABLE },
                        when (q) {
                            LoadQuality.UNAVAILABLE -> "Nessuna frequenza cardiaca oggi"
                            LoadQuality.PARTIAL -> "FC per ${dayAct!!.hrMinutes / 60} h ${dayAct.hrMinutes % 60} min: sottostimato · obiettivo ${target.low.f(0)}–${target.high.f(0)}"
                            LoadQuality.COMPLETE -> "Obiettivo ${target.low.f(0)}–${target.high.f(0)} · TRIMP"
                        },
                        m, spark = vm.dayActivities.filter { it.quality != LoadQuality.UNAVAILABLE }.takeLast(14).map { it.load },
                        onClick = { onOpenMetric(DETAIL_LOAD) },
                    )
                },
                { m ->
                    val todays = vm.displaySessions.filter { it.start.atZone(ZoneId.systemDefault()).toLocalDate() == today }
                    MetricTile(
                        "Allenamenti oggi", if (vm.syncStatus.state == SyncState.NEVER && todays.isEmpty()) null else "${todays.size}", null, Tint.vo2, BioIcons.Gauge,
                        when (vm.syncStatus.state) { SyncState.OK -> DataStatus.MEASURED; SyncState.NEVER, SyncState.NO_PERMISSION -> DataStatus.UNAVAILABLE; else -> DataStatus.PARTIAL },
                        if (todays.isEmpty()) "Sincronizzati ${agoShort(vm.syncStatus.lastSync)}"
                        else todays.joinToString(", ") { exerciseName(it.hcType, it.title) },
                        m, onClick = onGoActivity,
                    )
                },
            )
        }

        // ---------------- Sezioni del giorno ----------------
        if (vm.hasAnyHealthPermission) {
            if (vm.vitals.outliers >= 2) item { VitalsCard(vm) }
            item { TonightCard(vm) }
        }
        item { JournalQuickCard(vm, onOpenJournal) }
        item { WeekCard(vm) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Pressione", vm.bpSummary?.let { "${it.meanSys.f(0)}/${it.meanDia.f(0)}" } ?: "Registra", Tint.bp, BioIcons.Heart, onGoBp, Modifier.weight(1f))
                QuickAction("Analisi", "${vm.panels.size} prelievi", Tint.blue, BioIcons.Drop, onGoLabs, Modifier.weight(1f))
                QuickAction("Chiedi a Claude", "Consigli", Tint.sleep, BioIcons.Chat, { onAsk("") }, Modifier.weight(1f))
            }
        }

        item {
            Caption("BioEtà fornisce stime statistiche a scopo informativo, non diagnosi. Per decisioni sulla salute rivolgiti al medico.")
        }
    }
}

/** Due tessere affiancate della stessa altezza. */
@Composable
private fun PairRow(a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        a(Modifier.weight(1f).fillMaxHeight())
        b(Modifier.weight(1f).fillMaxHeight())
    }
}

fun fmtHours(h: Double): String { val m = (h * 60).toInt(); return "${m / 60}h ${(m % 60).toString().padStart(2, '0')}" }

/** Tessera con confronto tra ultimi 7 giorni e baseline personale (giorni 8–67). */
@Composable
private fun BaselineTile(vm: AppViewModel, metric: Metric, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, m: Modifier, onClick: () -> Unit) {
    val pts = vm.wearable[metric].orEmpty()
    val s = if (pts.isNotEmpty()) HealthRepo.summarize(metric, pts) else null
    val last = pts.lastOrNull()
    val value = s?.recent ?: last?.value
    val trend = if (s?.recent != null && s.baseline != null) {
        val d = s.recent - s.baseline
        Trend("${abs(d).f(if (metric == Metric.HRV) 0 else 1)} vs baseline", d > 0, metric.higherIsBetter)
    } else null
    MetricTile(
        label, value?.let { fmtValue(metric, it) }, metric.unit, tint, icon,
        if (value != null) DataStatus.MEASURED else DataStatus.UNAVAILABLE,
        when {
            value == null -> "Nessun dato in Connessione Salute"
            s?.recent != null -> "Media 7 giorni · ultimo ${last!!.date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN))}"
            else -> "Ultimo valore del ${last!!.date.itLong()}"
        },
        m, trend = trend, spark = pts.takeLast(14).map { it.value }, onClick = onClick,
    )
}

@Composable
private fun DataFreshnessRow(vm: AppViewModel, onOpenSync: () -> Unit) {
    val p = LocalPalette.current
    val st = vm.syncStatus
    val dot = when {
        vm.loading || vm.syncing -> Tint.blue
        !vm.hasAnyHealthPermission -> p.text3
        st.state == SyncState.ERROR || st.state == SyncState.PARTIAL -> Tint.warn
        else -> Tint.good
    }
    val text = when {
        vm.loading || vm.syncing -> "Aggiornamento da Connessione Salute…"
        !vm.hasAnyHealthPermission -> "Connessione Salute non collegata"
        else -> "Aggiornato ${agoShort(vm.lastRefresh)}" + (st.origins.takeIf { it.isNotEmpty() }?.let { " · " + it.joinToString(", ") } ?: "")
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).clickable(onClick = onOpenSync).padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = p.text2, maxLines = 1, modifier = Modifier.weight(1f))
        Text("Fonti", style = MaterialTheme.typography.labelLarge, color = Tint.brand)
    }
}

/** Quadrante: recupero, sonno e carico attorno al cuore che batte alla frequenza a riposo. */
@Composable
private fun HeroToday(vm: AppViewModel, mood: Mood, onGoHealth: () -> Unit, onGoActivity: () -> Unit) {
    val p = LocalPalette.current
    val t = vm.today
    val dayAct = vm.todayDayActivity
    val target = vm.targetLoad
    val loadFraction = dayAct?.takeIf { it.quality != LoadQuality.UNAVAILABLE }?.let { (it.load / maxOf(target.high, 1.0)).toFloat() }
    val (title, msg) = gentleMessage(mood, t.readiness.flags.isNotEmpty())
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(p.card)
            .background(Brush.verticalGradient(listOf(moodColors(mood)[0].copy(alpha = if (p.dark) 0.16f else 0.14f), Color.Transparent)))
            .border(1.dp, p.border, RoundedCornerShape(28.dp))
            .padding(vertical = 18.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TriDial(t.readiness.score, t.sleep?.score, loadFraction, mood, bpm = vm.restHrRef)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroLegend("Recupero", t.readiness.score?.toString() ?: "—", Pillar.recovery[1], onGoHealth, Modifier.weight(1f))
            HeroLegend("Sonno", t.sleep?.score?.toString() ?: "—", Pillar.sleep[1], onGoHealth, Modifier.weight(1f))
            HeroLegend("Carico", loadFraction?.let { dayAct!!.load.f(0) } ?: "—", Pillar.load[1], onGoActivity, Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = p.text)
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = p.text2)
        }
    }
}

@Composable
private fun HeroLegend(label: String, value: String, c: Color, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(c.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, fontFeatureSettings = TABULAR), color = p.text)
        Text(label, style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1)
    }
}

@Composable
private fun QuickAction(title: String, value: String, tint: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(p.card).border(1.dp, p.border, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(value, style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1)
        Text(title, style = MaterialTheme.typography.labelSmall, color = p.text2, maxLines = 1)
    }
}
