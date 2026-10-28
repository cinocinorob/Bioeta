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
    onOpenLongTerm: () -> Unit = {},
    onOpenNutrition: () -> Unit = {},
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

        // ---------------- Cruscotto: oggi ----------------
        item {
            PairRow(
                { m -> RecoveryTile(vm, mood, m, onGoHealth) },
                { m -> SleepTile(vm, m) { onOpenMetric("SLEEP") } },
            )
        }
        item { MoveTile(vm, chrono, onGoActivity, onOpenMetric) }
        item { RpePrompt(vm) }
        if (vm.hasAnyHealthPermission) item { NutritionCard(vm, onOpenNutrition, onRequestPermissions) }

        // ---------------- Salute a lungo termine: indicatori con soglie dalla letteratura ----------------
        if (vm.hasAnyHealthPermission) {
            item { SectionHeader("Salute a lungo termine", "Dettagli", onOpenLongTerm) }
            item { LongTermCard(vm.longTerm, onOpenLongTerm) }
        }

        // ---------------- Parametri della notte (stessa valutazione del radar: intervallo personale) ----------------
        item { SectionHeader("Parametri della notte", "Salute", onGoHealth) }
        val sig = vm.radar.signals.associateBy { it.spec.key }
        val vitals = listOf(
            Triple("rhr", Metric.RHR, BioIcons.Heart to Tint.heart),
            Triple("hrv", Metric.HRV, BioIcons.Pulse to Tint.hrv),
            Triple("resp", Metric.RESP, BioIcons.Wind to Tint.resp),
            Triple("spo2", Metric.SPO2, BioIcons.Drop to Tint.spo2),
            Triple("temp", Metric.SKIN_TEMP, BioIcons.Thermo to Tint.load),
            Triple("shr", null, BioIcons.Moon to Tint.sleep),
        ).filter { (k, _, _) -> sig[k]?.let { it.value != null || it.baselineNights > 0 } == true }
        vitals.chunked(2).forEach { row ->
            item {
                PairRow(
                    { m -> row[0].let { (k, metric, ic) -> VitalTile(vm, sig.getValue(k), metric, ic.first, ic.second, m) { onOpenMetric(metric?.name ?: "SLEEP") } } },
                    { m -> if (row.size > 1) row[1].let { (k, metric, ic) -> VitalTile(vm, sig.getValue(k), metric, ic.first, ic.second, m) { onOpenMetric(metric?.name ?: "SLEEP") } } else Spacer(m) },
                )
            }
        }
        if (vm.hasAnyHealthPermission && !radarAlert) item { RadarCard(vm) }

        // ---------------- In evidenza: solo ciò che è cambiato davvero ----------------
        if (vm.hasAnyHealthPermission) {
            item { SectionHeader("In evidenza") }
            item { HighlightsSection(vm.highlights, onOpenMetric) }
        }

        // ---------------- Corpo ed età ----------------
        item { SectionHeader("Corpo ed età", "Dettagli", onGoAge) }
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
                            last == null && series.isEmpty() -> "Servono 9 esami del sangue"
                            last == null -> "Dati incompleti nell'ultimo prelievo"
                            else -> "Dal prelievo del ${last.panel.date.itLong()}" + (chrono?.let { " · anagrafica ${it.f(0)}" } ?: "")
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
                            vo2 == null -> "Serve il VO₂max"
                            profile.vo2Manual != null -> "Da VO₂max ${vo2.f(1)} misurato"
                            else -> "Da VO₂max ${vo2.f(1)} dell'orologio"
                        },
                        it, onClick = { onOpenMetric("VO2") },
                    )
                },
            )
        }
        item {
            val w = vm.wearable[Metric.WEIGHT].orEmpty()
            val bp = vm.bpSummary
            PairRow(
                { m ->
                    MetricTile(
                        "Peso", w.lastOrNull()?.value?.f(1), "kg", Tint.body, BioIcons.Person,
                        if (w.isNotEmpty()) DataStatus.MEASURED else DataStatus.UNAVAILABLE,
                        w.lastOrNull()?.let { "Ultima misura ${it.date.itLong()}" } ?: "Nessuna pesata in Connessione Salute",
                        m, spark = w.takeLast(30).map { it.value }, onClick = { onOpenMetric("WEIGHT") },
                    )
                },
                { m ->
                    MetricTile(
                        "Pressione", bp?.let { "${it.meanSys.f(0)}/${it.meanDia.f(0)}" }, "mmHg", Tint.bp, BioIcons.Heart,
                        if (bp != null) DataStatus.MEASURED else DataStatus.UNAVAILABLE,
                        if (bp != null) "Media delle misure recenti" else "Tocca per registrare una misura",
                        m, onClick = onGoBp,
                    )
                },
            )
        }

        // ---------------- Benessere ----------------
        item { SectionHeader("Benessere") }
        vm.bedtimeGuide?.let { g ->
            item {
                CompactRow(BioIcons.Moon, Tint.sleep, "Stasera a letto entro ${Bedtime.fmt(g.bedtime)}",
                    "Ultimo caffè entro le ${Bedtime.fmt(g.caffeineCutoff)} · sveglia abituale ${Bedtime.fmt(g.wake)}", onGoHealth)
            }
        }
        item {
            val e = vm.journalFor(today)
            HealthCard("Umore e diario", BioIcons.Pencil, Tint.mood, onClick = onOpenJournal, modifier = Modifier.testTag("journal_card")) {
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
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.tone(Tint.mood))
                        .clickable(onClick = if (forToday) onOpenJournal else onOpenJournalYesterday)
                        .padding(horizontal = 16.dp, vertical = 14.dp).testTag("journal_yesterday"),
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
                        val mm = vm.journalReminderMin
                        Text(
                            when {
                                st > 0 -> "Serie di $st ${if (st == 1) "giorno" else "giorni"}"
                                !evening && vm.journalReminderOn -> "Promemoria alle %02d:%02d".format(mm / 60, mm % 60)
                                else -> "Si compila la sera, in 30 secondi"
                            },
                            style = MaterialTheme.typography.bodySmall, color = p.text2,
                        )
                    }
                    Box(Modifier.clip(RoundedCornerShape(50)).background(p.brand).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(if (a >= n) "Rivedi" else if (!evening && forToday && a == 0) "Apri" else "Rispondi", style = MaterialTheme.typography.labelLarge, color = p.onBrand)
                    }
                }
            }
        }
        if (vm.hasAnyHealthPermission) item { DiscoveriesCard(vm, onOpenDiscoveries) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Analisi del sangue", "${vm.panels.size} prelievi", Tint.blood, BioIcons.Drop, onGoLabs, Modifier.weight(1f))
                QuickAction("Chiedi a Claude", "Domande sui tuoi dati", Tint.sleep, BioIcons.Chat, { onAsk("") }, Modifier.weight(1f))
            }
        }

        item { Caption("Stime statistiche a scopo informativo, non diagnosi.") }
    }
}

// =============================================================================================
// Riquadri del cruscotto
// =============================================================================================

/** Riquadro grande tonale (contenitore pastello del colore di dominio). */
@Composable
private fun BigTile(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(28.dp)).background(p.tone(tint, 1.4f)).clickable(onClick = onClick).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun BigNumber(value: String, unit: String? = null, dim: Boolean = false) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 52.sp, lineHeight = 54.sp, fontFeatureSettings = TABULAR),
            color = if (dim) p.text3 else p.text, maxLines = 1)
        if (unit != null) Text(unit, style = MaterialTheme.typography.titleSmall, color = p.text2, modifier = Modifier.padding(start = 3.dp, bottom = 9.dp), maxLines = 1)
    }
}

/** Recupero: punteggio, giudizio rispetto alle tue giornate e consiglio in poche parole. */
@Composable
private fun RecoveryTile(vm: AppViewModel, mood: Mood, m: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val t = vm.today
    val score = t.readiness.score
    val pct = vm.readinessPercentile
    val (title, advice) = when {
        score == null -> "In attesa" to "Servono alcune notti con l'orologio"
        t.readiness.flags.isNotEmpty() -> gentleMessage(mood, true).first to "Leggi l'avviso in alto"
        pct == null -> (if (score >= 75) "Buono" else if (score >= 50) "Discreto" else "Basso") to "Il confronto arriva dopo 14 giorni"
        pct >= 70 -> "Meglio del solito" to "Buon giorno per spingere"
        pct >= 30 -> "Nella tua media" to "Allenati come al solito"
        else -> "Sotto il solito" to "Meglio un giorno leggero"
    }
    val tone = when { score == null -> Tint.brand; pct != null && pct < 30 -> Tint.warn; score < 50 -> Tint.warn; else -> Tint.brand }
    BigTile("Recupero", BioIcons.Bolt, tone, m.testTag("hero"), onClick) {
        BigNumber(score?.toString() ?: "—", if (score != null) "/100" else null, dim = score == null)
        Text(title, style = MaterialTheme.typography.titleMedium, color = p.text, maxLines = 1)
        Text(advice, style = MaterialTheme.typography.bodySmall, color = p.text2, maxLines = 2)
        if (pct != null) Text("Meglio del $pct% dei tuoi giorni", style = MaterialTheme.typography.labelMedium, color = p.ink(tone), maxLines = 2)
    }
}

@Composable
private fun SleepTile(vm: AppViewModel, m: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val n = vm.today.night
    val sc = vm.today.sleep?.score
    BigTile("Sonno", BioIcons.Moon, Tint.sleep, m, onClick) {
        if (n == null) {
            BigNumber("—", dim = true)
            Text("Nessuna notte", style = MaterialTheme.typography.titleMedium, color = p.text)
            Text("L'orologio non ha registrato il sonno", style = MaterialTheme.typography.bodySmall, color = p.text2, maxLines = 2)
        } else {
            val mins = n.totalSleepMin.toInt()
            Row(verticalAlignment = Alignment.Bottom) {
                BigNumber("${mins / 60}", "h")
                Spacer(Modifier.width(4.dp))
                BigNumber("%02d".format(mins % 60), "min")
            }
            Text(sc?.let { "Punteggio $it" } ?: "Stanotte", style = MaterialTheme.typography.titleMedium, color = p.text, maxLines = 1)
            val z = java.time.ZoneId.systemDefault()
            val fmt = DateTimeFormatter.ofPattern("HH:mm")
            Text("${n.start.atZone(z).format(fmt)} – ${n.end.atZone(z).format(fmt)}", style = MaterialTheme.typography.bodySmall, color = p.text2, maxLines = 1)
            vm.sleepPercentile?.let { Text("Meglio del $it% delle tue notti", style = MaterialTheme.typography.labelMedium, color = p.ink(Tint.sleep), maxLines = 2) }
        }
    }
}

/** Movimento di oggi: minuti attivi (settimana OMS), passi e carico, ciascuno con la sua barra. */
@Composable
private fun MoveTile(vm: AppViewModel, chrono: Double?, onGoActivity: () -> Unit, onOpenMetric: (String) -> Unit) {
    val p = LocalPalette.current
    val today = LocalDate.now()
    val act = vm.todayActivity
    val dayAct = vm.todayDayActivity
    val goal = Fitness.stepGoal(chrono)
    val week = Fitness.weekActiveMinutes(vm.dayLoads, today)
    val target = vm.targetLoad
    val todays = vm.displaySessions.filter { it.start.atZone(ZoneId.systemDefault()).toLocalDate() == today }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(p.tone(Tint.activity, 1.4f)).clickable(onClick = onGoActivity)
            .padding(18.dp).testTag("move_tile"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Tint.activity), contentAlignment = Alignment.Center) {
                Icon(BioIcons.Bolt, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Movimento di oggi", style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.weight(1f))
            if (dayAct?.stale == true) Text("non aggiornato", style = MaterialTheme.typography.labelMedium, color = p.ink(Tint.warn))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MoveStat("Minuti attivi", dayAct?.activeMinutes?.toString() ?: "—", "settimana $week/150", week / 150f, Tint.good, Modifier.weight(1f)) { onOpenMetric(DETAIL_ACTIVE) }
            MoveStat("Passi", act?.let { String.format(Locale.ITALY, "%,d", it.steps) } ?: "—", "obiettivo ${String.format(Locale.ITALY, "%,d", goal)}",
                act?.let { it.steps / goal.toFloat() }, Tint.activity, Modifier.weight(1f)) { onOpenMetric("STEPS") }
            val load = dayAct?.takeIf { it.quality != LoadQuality.UNAVAILABLE }?.load
            MoveStat("Carico", load?.f(0) ?: "—", target?.let { "obiettivo ${it.low.f(0)}–${it.high.f(0)}" } ?: "in calibrazione",
                if (load != null && target != null) (load / target.high).toFloat() else null, Tint.load, Modifier.weight(1f)) { onGoActivity() }
        }
        Text(
            if (todays.isEmpty()) "Allenamenti oggi: nessuno" else "Allenamenti oggi: " + todays.joinToString(", ") { exerciseName(it.hcType, it.title) },
            style = MaterialTheme.typography.bodySmall, color = p.text2, maxLines = 1,
        )
    }
}

@Composable
private fun MoveStat(label: String, value: String, sub: String, fraction: Float?, c: Color, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = p.text2, maxLines = 1)
        Text(value, style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, fontFeatureSettings = TABULAR), color = p.text, maxLines = 1)
        Track(fraction ?: 0f, c)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = p.text2, maxLines = 1)
    }
}

/** Parametro della notte: valore, posizione nel tuo intervallo abituale e giudizio in parole. */
@Composable
private fun VitalTile(
    vm: AppViewModel, e: it.cinocinorob.bioeta.science.SignalEval, metric: Metric?,
    icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, m: Modifier, onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val v = e.value
    val z = e.z
    val (verdict, vc) = when {
        v == null -> "Nessun dato stanotte" to p.text3
        z == null -> "In calibrazione (${e.baselineNights}/7 notti)" to p.text3
        e.concerning -> (if (z > 0) "Più alta del solito" else "Più bassa del solito") to p.ink(Tint.warn)
        kotlin.math.abs(z) >= 2 -> (if (z > 0) "Sopra il tuo intervallo" else "Sotto il tuo intervallo") to p.ink(Tint.blue)
        else -> "Nella norma" to p.ink(Tint.good)
    }
    Column(
        m.clip(RoundedCornerShape(24.dp)).background(p.card).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(p.toneStrong(tint)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = p.ink(tint), modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(e.spec.label, style = MaterialTheme.typography.labelLarge, color = p.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            val sign = if (e.spec.key == "temp" && (v ?: 0.0) >= 0) "+" else ""
            Text(v?.let { sign + it.f(e.spec.decimals) } ?: "—", style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, fontFeatureSettings = TABULAR),
                color = if (v == null) p.text3 else p.text, maxLines = 1)
            if (v != null) Text(" ${e.spec.unit}", style = MaterialTheme.typography.labelMedium, color = p.text2, modifier = Modifier.padding(bottom = 5.dp), maxLines = 1)
        }
        if (v != null && e.low != null && e.high != null) RangeDot(v, e.low, e.high, tint, Modifier.fillMaxWidth())
        Text(verdict, style = MaterialTheme.typography.labelLarge, color = vc, maxLines = 1)
        e.median?.let { Text("di solito ${it.f(e.spec.decimals)} ${e.spec.unit}", style = MaterialTheme.typography.bodySmall, color = p.text2, maxLines = 1) }
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(p.card).clickable(onClick = onOpenSync).padding(vertical = 10.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = p.text2, maxLines = 1, modifier = Modifier.weight(1f))
        Text("Fonti", style = MaterialTheme.typography.labelLarge, color = p.ink(p.brand))
    }
}

@Composable
private fun QuickAction(title: String, value: String, tint: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(p.tone(tint)).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodySmall, color = p.text2, maxLines = 1)
    }
}

/** Riga compatta cliccabile: icona, titolo e sottotitolo. */
@Composable
private fun CompactRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.card)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
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
