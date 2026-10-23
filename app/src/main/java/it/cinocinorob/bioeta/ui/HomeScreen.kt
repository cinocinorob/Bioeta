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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
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
import it.cinocinorob.bioeta.science.RadarLevel
import it.cinocinorob.bioeta.data.Sex
import it.cinocinorob.bioeta.data.SyncState
import it.cinocinorob.bioeta.health.HealthRepo
import it.cinocinorob.bioeta.health.Metric
import it.cinocinorob.bioeta.science.Fitness
import it.cinocinorob.bioeta.science.Bedtime
import it.cinocinorob.bioeta.science.Personal
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
    onOpenDiscoveries: () -> Unit = {},
    onOpenJournalYesterday: () -> Unit = {},
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
        // Il radar sostituisce l'avviso generico del Recupero quando c'è un quadro da malessere
        val radarAlert = vm.radar.level == RadarLevel.MINOR || vm.radar.level == RadarLevel.MAJOR
        if (!radarAlert) t.readiness.flags.forEach { fl -> item { InlineNotice(fl, NoticeKind.WARN) } }
        item { ChangesNotice(vm) }
        if (vm.hasAnyHealthPermission && radarAlert) item { RadarCard(vm) }

        // ---------------- Quadrante di oggi ----------------
        item { HeroToday(vm, mood, onGoHealth, onGoActivity, onOpenJournal) }
        if (vm.hasAnyHealthPermission && !radarAlert) item { RadarCard(vm) }
        item { RpePrompt(vm) }

        // ---------------- In evidenza: solo ciò che è cambiato davvero ----------------
        if (vm.hasAnyHealthPermission) {
            item { SectionHeader("In evidenza") }
            item { HighlightsSection(vm.highlights, onOpenMetric) }
        }

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
                            Trend("${abs(d).f(1)} anni", d > 0, goodWhenUp = false)
                        } else null,
                        onClick = onGoAge,
                    )
                },
                {
                    val raw = vo2?.let { FitnessAge.fitnessAgeRaw(it, male) }
                    MetricTile(
                        "Età cardio", raw?.let { r -> when { r < 20 -> "<20"; r > 80 -> ">80"; else -> r.f(0) } }, "anni", Tint.vo2, BioIcons.Gauge,
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

        // ---------------- I tuoi numeri (recupero, sonno e carico sono già nel quadrante) ----------------
        item { SectionHeader("I tuoi numeri", "Salute", onGoHealth) }
        item {
            PairRow(
                { m -> BaselineTile(vm, Metric.RHR, "FC a riposo", BioIcons.Heart, Tint.heart, m) { onOpenMetric("RHR") } },
                { m -> BaselineTile(vm, Metric.HRV, "HRV notturna", BioIcons.Pulse, Tint.hrv, m) { onOpenMetric("HRV") } },
            )
        }
        item {
            val goal = Fitness.stepGoal(chrono)
            val steps7 = vm.wearable[Metric.STEPS].orEmpty().filter { it.date.isAfter(today.minusDays(8)) }.map { it.value }
            PairRow(
                { m ->
                    val n = t.night
                    MetricTile(
                        "Sonno", n?.let { fmtHours(it.totalSleepMin / 60) }, null, Tint.sleep, BioIcons.Moon,
                        if (n != null) DataStatus.MEASURED else DataStatus.UNAVAILABLE,
                        if (n != null) "Stanotte" else "Nessuna notte registrata",
                        m, spark = vm.sleepNights.takeLast(14).map { it.totalSleepMin / 60 }, onClick = { onOpenMetric("SLEEP") },
                    )
                },
                { m ->
                    MetricTile(
                        "Passi oggi", act?.let { String.format(Locale.ITALY, "%,d", it.steps) }, null, Tint.activity, BioIcons.Bolt,
                        if (act != null) DataStatus.MEASURED else DataStatus.UNAVAILABLE,
                        if (act != null) "Obiettivo ${String.format(Locale.ITALY, "%,d", goal)}" else "Nessun dato oggi",
                        m, spark = steps7, onClick = { onOpenMetric("STEPS") },
                    )
                },
            )
        }
        item {
            PairRow(
                { m ->
                    val week = Fitness.weekActiveMinutes(vm.dayLoads, today)
                    val est = dayAct?.estimatedShare ?: 0
                    MetricTile(
                        "Minuti attivi", dayAct?.activeMinutes?.toString(), "min", Tint.good, BioIcons.Bolt,
                        when { dayAct == null -> DataStatus.UNAVAILABLE; dayAct.stale -> DataStatus.PARTIAL; est > 0 -> DataStatus.ESTIMATED; else -> DataStatus.MEASURED },
                        if (dayAct == null) "Servono FC o allenamenti" else "Settimana $week/150",
                        m, onClick = { onOpenMetric(DETAIL_ACTIVE) },
                    )
                },
                { m ->
                    val todays = vm.displaySessions.filter { it.start.atZone(ZoneId.systemDefault()).toLocalDate() == today }
                    MetricTile(
                        "Allenamenti oggi", if (vm.syncStatus.state == SyncState.NEVER && todays.isEmpty()) null else "${todays.size}", null, Tint.vo2, BioIcons.Gauge,
                        when (vm.syncStatus.state) { SyncState.OK -> DataStatus.MEASURED; SyncState.NEVER, SyncState.NO_PERMISSION -> DataStatus.UNAVAILABLE; else -> DataStatus.PARTIAL },
                        if (todays.isEmpty()) "Sincronizzati ${agoShort(vm.syncStatus.lastSync)}" else todays.joinToString(", ") { exerciseName(it.hcType, it.title) },
                        m, onClick = onGoActivity,
                    )
                },
            )
        }

        // ---------------- Sezioni del giorno, in forma compatta ----------------
        vm.bedtimeGuide?.let { g ->
            item {
                CompactRow(BioIcons.Moon, Tint.sleep, "Stasera a letto entro ${Bedtime.fmt(g.bedtime)}",
                    "Ultimo caffè entro le ${Bedtime.fmt(g.caffeineCutoff)} · sveglia abituale ${Bedtime.fmt(g.wake)}", onGoHealth)
            }
        }
        item {
            val e = vm.journalFor(today)
            HealthCard("Diario", BioIcons.Pencil, Tint.mood, onClick = onOpenJournal, modifier = Modifier.testTag("journal_card")) {
                Text("Come ti senti oggi?", style = MaterialTheme.typography.labelLarge, color = p.text2)
                ScalePicker(e.mood) { vm.saveJournal(e.copy(mood = it)) }
                MoodStrip(vm)
                // Diario serale: dalle 18 si risponde sulla giornata di oggi; al mattino si recupera ieri se mancava
                val evening = java.time.LocalTime.now().hour >= 18
                val (ya, yn) = vm.journalProgress(today.minusDays(1))
                val (ta, tn) = vm.journalProgress(today)
                val forToday = evening || ya >= yn
                val (a, n) = if (forToday) ta to tn else ya to yn
                if (n > 0) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.field)
                        .clickable(onClick = if (forToday) onOpenJournal else onOpenJournalYesterday)
                        .padding(horizontal = 14.dp, vertical = 12.dp).testTag("journal_yesterday"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        val title = when {
                            !forToday -> "Ieri sera non hai finito: $a di $n"
                            !evening && a == 0 -> "Diario di stasera: $n domande"
                            a >= n -> "Diario di oggi: completo"
                            else -> "Diario di oggi: $a di $n"
                        }
                        Text(title, style = MaterialTheme.typography.titleSmall, color = p.text)
                        val st = vm.journalStreak
                        val m = vm.journalReminderMin
                        Text(
                            when {
                                st > 0 -> "Serie di $st ${if (st == 1) "giorno" else "giorni"}"
                                !evening && vm.journalReminderOn -> "Promemoria alle %02d:%02d".format(m / 60, m % 60)
                                else -> "Si compila la sera, in 30 secondi"
                            },
                            style = MaterialTheme.typography.bodySmall, color = p.text2,
                        )
                    }
                    Text(if (a >= n) "Rivedi" else if (!evening && forToday && a == 0) "Apri" else "Rispondi", style = MaterialTheme.typography.labelLarge, color = p.brand)
                }
            }
        }
        if (vm.hasAnyHealthPermission) item { DiscoveriesCard(vm, onOpenDiscoveries) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Pressione", vm.bpSummary?.let { "${it.meanSys.f(0)}/${it.meanDia.f(0)}" } ?: "Registra", Tint.bp, BioIcons.Heart, onGoBp, Modifier.weight(1f))
                QuickAction("Analisi", "${vm.panels.size} prelievi", Tint.blue, BioIcons.Drop, onGoLabs, Modifier.weight(1f))
                QuickAction("Chiedi a Claude", "Consigli", Tint.sleep, BioIcons.Chat, { onAsk("") }, Modifier.weight(1f))
            }
        }

        item { Caption("Stime statistiche a scopo informativo, non diagnosi.") }
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
        Trend("${abs(d).f(if (metric == Metric.HRV) 0 else 1)} ${metric.unit}", d > 0, metric.higherIsBetter)
    } else null
    MetricTile(
        label, value?.let { fmtValue(metric, it) }, metric.unit, tint, icon,
        if (value != null) DataStatus.MEASURED else DataStatus.UNAVAILABLE,
        when {
            value == null -> "Nessun dato in Connessione Salute"
            s?.recent != null && s.baseline != null -> "Media della settimana, rispetto al tuo solito"
            s?.recent != null -> "Media della settimana (il confronto arriva con più giorni)"
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
        Text("Fonti", style = MaterialTheme.typography.labelLarge, color = p.brand)
    }
}

/** Quadrante: recupero, sonno e carico attorno al cuore che batte alla frequenza a riposo. */
@Composable
private fun HeroToday(vm: AppViewModel, mood: Mood, onGoHealth: () -> Unit, onGoActivity: () -> Unit, onOpenJournal: () -> Unit) {
    val p = LocalPalette.current
    val t = vm.today
    val dayAct = vm.todayDayActivity
    val score = t.readiness.score
    // Un solo messaggio coerente: quando c'è il rango personale guida il testo (il punteggio assoluto tende a stare alto)
    val pct = vm.readinessPercentile
    val (title, msg) = when {
        t.readiness.flags.isNotEmpty() || pct == null -> gentleMessage(mood, t.readiness.flags.isNotEmpty())
        pct >= 70 -> "Meglio del solito" to "Sei più recuperato della maggior parte delle tue giornate: buon giorno per un allenamento impegnativo, se ti va."
        pct >= 30 -> "Nella tua media" to "Recupero in linea con le tue giornate abituali: puoi allenarti come al solito."
        else -> "Sotto il tuo solito" to "Il punteggio è ancora buono, ma più basso della maggior parte delle tue giornate: oggi meglio un'attività moderata e un buon sonno."
    }
    val ringColor = p.brand
    // Contenitore tonale (Material 3): una velatura del colore dello stato di oggi
    val tone = when { score == null -> Color.Transparent; score >= 75 -> Tint.good; score >= 50 -> Tint.warn; else -> Tint.bad }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(p.card).background(tone.copy(alpha = if (p.dark) 0.09f else 0.07f))
            // Anelli di accrescimento attorno al marchio: l'unico elemento decorativo dell'app
            .drawBehind {
                val c = Offset(size.width - 58.dp.toPx(), 62.dp.toPx())
                for (i in 1..7) drawCircle(ringColor.copy(alpha = 0.09f - i * 0.01f), radius = (40 + i * 26).dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
            }
            .clickable(onClick = onGoHealth)
            .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)
            .testTag("hero"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Recupero di oggi", style = MaterialTheme.typography.labelLarge, color = p.text2)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        score?.toString() ?: "—",
                        style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 92.sp, lineHeight = 92.sp, letterSpacing = (-1).sp, fontFeatureSettings = TABULAR),
                        color = if (score == null) p.text3 else p.text,
                    )
                    if (score != null) Text("/100", style = MaterialTheme.typography.titleMedium, color = p.text3, modifier = Modifier.padding(start = 4.dp, bottom = 14.dp))
                }
            }
            BeatingRings(vm.restHrRef, size = 84, light = !p.dark)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = p.text)
        Text(msg, style = MaterialTheme.typography.bodyMedium, color = p.text2)
        // Lettura personale: dove si colloca oggi rispetto alla tua storia (i punteggi assoluti saturano)
        val parts = listOfNotNull(
            vm.readinessPercentile?.let { "Meglio del $it% delle tue giornate" },
            vm.sleepPercentile?.let { "Sonno meglio del $it% delle tue notti" },
        )
        parts.forEach { Text(it, style = MaterialTheme.typography.labelLarge, color = p.text) }
        if (parts.isEmpty() && score != null) Text("Il confronto con le tue giornate compare dopo 14 giorni di dati.", style = MaterialTheme.typography.labelMedium, color = p.text3)
        androidx.compose.material3.HorizontalDivider(color = p.sep, thickness = 1.dp, modifier = Modifier.padding(top = 4.dp))
        val todayMood = vm.journalFor(LocalDate.now()).mood
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            HeroReadout("Sonno", t.sleep?.score?.toString() ?: "—", Tint.sleep, onGoHealth, Modifier.weight(1f))
            androidx.compose.material3.VerticalDivider(color = p.sep)
            HeroReadout("Carico", dayAct?.takeIf { it.quality != LoadQuality.UNAVAILABLE }?.load?.f(0) ?: "—", Tint.load, onGoActivity, Modifier.weight(1f))
            androidx.compose.material3.VerticalDivider(color = p.sep)
            HeroReadout("Umore", todayMood?.let { MOOD_LABELS[it - 1] } ?: "—", Tint.mood, onOpenJournal, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroReadout(label: String, value: String, c: Color, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, fontFeatureSettings = TABULAR), color = p.text, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = p.text2, maxLines = 1)
        }
    }
}

@Composable
private fun QuickAction(title: String, value: String, tint: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(p.card).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1)
        Text(title, style = MaterialTheme.typography.labelSmall, color = p.text2, maxLines = 1)
    }
}

/** Riga compatta cliccabile: icona, titolo e sottotitolo. */
@Composable
private fun CompactRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, 32)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = p.text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.text2)
        }
        Icon(BioIcons.Chevron, null, tint = p.text3, modifier = Modifier.size(14.dp))
    }
}
