package it.cinocinorob.bioeta.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException

/** Navigazione principale: 5 aree raggiungibili con il pollice. Profilo dall'avatar in alto, Claude dalle azioni. */
enum class Tab(val label: String) { HOME("Home"), ACTIVITY("Attività"), HEALTH("Salute"), AGE("Età"), LABS("Analisi") }

private fun tabIcon(t: Tab) = when (t) {
    Tab.HOME -> BioIcons.Home
    Tab.ACTIVITY -> BioIcons.Bolt
    Tab.HEALTH -> BioIcons.Heart
    Tab.AGE -> BioIcons.Sparkle
    Tab.LABS -> BioIcons.Drop
}

/** Schermate secondarie, gestite come una pila: "indietro" chiude sempre la più recente. */
private const val R_INFO = "info"
private const val R_INSIGHTS = "insights"
private const val R_BP = "bp"
private const val R_JOURNAL = "journal"
private const val R_PROFILE = "profile"
private const val R_DIAG = "diag"
private const val R_EDIT = "panel:"
private const val R_ANALYTE = "analyte:"
private const val R_CLAUDE = "claude"
private const val R_SYNC = "sync"
private const val R_METRIC = "metric:"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    vm: AppViewModel,
    startOnPrivacy: Boolean,
    onRequestPermissions: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onImportReport: () -> Unit,
    onPickBackupFolder: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var healthSection by rememberSaveable { mutableStateOf(0) }
    var stackStr by rememberSaveable { mutableStateOf(if (startOnPrivacy) R_INFO else "") }
    val stack: List<String> = if (stackStr.isEmpty()) emptyList() else stackStr.split("|")
    val push: (String) -> Unit = { r -> stackStr = (stack + r).joinToString("|") }
    val pop: () -> Unit = { stackStr = stack.dropLast(1).joinToString("|") }
    var pendingQuestion by rememberSaveable { mutableStateOf<String?>(null) }
    val askClaude: (String) -> Unit = { q -> pendingQuestion = q.ifBlank { null }; stackStr = (stack.filter { it != R_CLAUDE } + R_CLAUDE).joinToString("|") }
    val snackbar = remember { SnackbarHostState() }
    val draft = vm.importDraft

    LaunchedEffect(vm.message) {
        val m = vm.message
        if (m != null) { snackbar.showSnackbar(m); vm.message = null }
    }

    val top = stack.lastOrNull()
    val subOpen = top != null || draft != null

    // Indietro predittivo (Android 14+): la schermata segue il gesto e si riduce, come nelle app Google
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = subOpen) { events ->
        try {
            events.collect { backProgress = it.progress }
            if (top != null) pop() else vm.importDraft = null
        } finally {
            backProgress = 0f
        }
    }
    // Dalle schede secondarie "indietro" torna a Oggi (convenzione Android), poi esce dall'app
    BackHandler(enabled = !subOpen && tab != Tab.HOME) { tab = Tab.HOME }

    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val p = backProgress
            if (p > 0f) {
                scaleX = 1f - 0.1f * p; scaleY = 1f - 0.1f * p
                translationX = 24.dp.toPx() * p
                shape = RoundedCornerShape((32 * p).dp); clip = true
            }
        },
    ) {
        when {
            top == R_INFO -> InfoScreen(onBack = pop)
            top == R_INSIGHTS -> InsightsScreen(vm, onBack = pop, onAsk = askClaude)
            top == R_BP -> BloodPressureScreen(vm, onBack = pop, onAsk = askClaude)
            top == R_JOURNAL -> JournalScreen(vm, onBack = pop)
            top == R_DIAG -> DiagnosticsScreen(vm, onBack = pop, onRequestPermissions = onRequestPermissions)
            top == R_SYNC -> SyncScreen(vm, onBack = pop, onRequestPermissions = onRequestPermissions, onOpenDiagnostics = { push(R_DIAG) })
            top == R_CLAUDE -> SubScaffold("Chiedi a Claude", pop) { m ->
                AssistantScreen(vm, m, pendingQuestion, onConsumeInitial = { pendingQuestion = null })
            }
            top != null && top.startsWith(R_METRIC) -> MetricDetailScreen(vm, top.removePrefix(R_METRIC), onBack = pop)
            top == R_PROFILE -> ProfileRoute(vm, onBack = pop, onExport, onImport, onPickBackupFolder,
                onOpenInfo = { push(R_INFO) }, onOpenDiagnostics = { push(R_SYNC) })
            top != null && top.startsWith(R_ANALYTE) -> AnalyteScreen(
                key = top.removePrefix(R_ANALYTE), panels = vm.panels, profile = vm.profile, onBack = pop,
            )
            top != null && top.startsWith(R_EDIT) -> {
                val id = top.removePrefix(R_EDIT).takeIf { it != "new" }
                PanelEditor(
                    initial = vm.panels.find { it.id == id }, isNew = id == null,
                    importNotes = emptyList(), highlighted = emptySet(), profile = vm.profile,
                    onSave = { vm.savePanel(it); pop() },
                    onDelete = { pid -> vm.deletePanel(pid); pop() },
                    onBack = pop,
                )
            }
            draft != null -> PanelEditor(
                initial = draft.panel, isNew = true, importNotes = draft.notes, highlighted = draft.foundKeys, profile = vm.profile,
                onSave = { vm.savePanel(it); vm.importDraft = null; tab = Tab.LABS; vm.message = "Referto salvato." },
                onDelete = { vm.importDraft = null },
                onBack = { vm.importDraft = null },
            )
            else -> MainTabs(
                vm, tab, onTab = { tab = it }, healthSection, onHealthSection = { healthSection = it }, snackbar,
                push = push, askClaude = askClaude, pendingQuestion = pendingQuestion, onConsumeQuestion = { pendingQuestion = null },
                onRequestPermissions = onRequestPermissions, onImportReport = onImportReport,
            )
        }
    }

    // Password per PDF protetti (molti laboratori usano il codice fiscale)
    val pwUri = vm.passwordFor
    if (pwUri != null) {
        var pw by remember(pwUri) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.passwordFor = null },
            title = { Text("PDF protetto") },
            text = {
                Column {
                    Text(
                        if (vm.passwordWasWrong) "Password errata. Riprova."
                        else "Il referto è protetto da password. Spesso è il codice fiscale (in maiuscolo) o una password indicata dal laboratorio.",
                    )
                    OutlinedTextField(
                        value = pw, onValueChange = { pw = it }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("Password") }, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.importReport(pwUri, pw.trim()) }, enabled = pw.isNotBlank()) { Text("Apri") } },
            dismissButton = { TextButton(onClick = { vm.passwordFor = null }) { Text("Annulla") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabs(
    vm: AppViewModel,
    tab: Tab,
    onTab: (Tab) -> Unit,
    healthSection: Int,
    onHealthSection: (Int) -> Unit,
    snackbar: SnackbarHostState,
    push: (String) -> Unit,
    askClaude: (String) -> Unit,
    pendingQuestion: String?,
    onConsumeQuestion: () -> Unit,
    onRequestPermissions: () -> Unit,
    onImportReport: () -> Unit,
) {
    val pal = LocalPalette.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val title = when (tab) {
        Tab.HOME -> {
            val h = LocalTime.now().hour
            (when (h) { in 5..12 -> "Buongiorno"; in 13..17 -> "Buon pomeriggio"; else -> "Buonasera" }) +
                (vm.profile.name.takeIf { it.isNotBlank() }?.let { ", $it" } ?: "")
        }
        Tab.ACTIVITY -> "Attività"
        Tab.HEALTH -> "Salute"
        Tab.AGE -> "Età biologica"
        Tab.LABS -> "Analisi"
    }
    val subtitle = when (tab) {
        Tab.HOME -> java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.ITALIAN)).replaceFirstChar { it.uppercase() }
        Tab.ACTIVITY -> "Movimento, allenamenti e carico"
        Tab.HEALTH -> "Recupero, sonno e parametri vitali"
        Tab.AGE -> "PhenoAge ed età cardiorespiratoria"
        Tab.LABS -> "Esami e andamenti"
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = pal.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(subtitle, maxLines = 1, style = MaterialTheme.typography.labelLarge, color = pal.text2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    Box(Modifier.padding(start = 12.dp, end = 4.dp).size(34.dp).clip(RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Color(0xFF0A0E13)), contentAlignment = Alignment.Center) {
                        BioLogo(size = 30)
                    }
                },
                actions = {
                    if (tab == Tab.LABS) IconButton(onClick = { askClaude("") }) { Icon(BioIcons.Chat, "Chiedi a Claude") }
                    IconButton(onClick = { vm.refreshHealth(force = true) }) { Icon(BioIcons.Refresh, "Aggiorna i dati") }
                    Box(
                        Modifier.padding(end = 12.dp).size(36.dp).clip(CircleShape).testTag("profile_button")
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(onClickLabel = "Profilo e impostazioni") { push(R_PROFILE) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(vm.profile.name.firstOrNull()?.uppercase() ?: "•", color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pal.bg, scrolledContainerColor = pal.bg),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = pal.card, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("tab_${t.name}"),
                        selected = tab == t, onClick = { onTab(t) },
                        icon = { Icon(tabIcon(t), null) },
                        label = { Text(t.label, maxLines = 1) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = Tint.brand, selectedTextColor = pal.text,
                            indicatorColor = Tint.brand.copy(alpha = 0.16f),
                            unselectedIconColor = pal.text2, unselectedTextColor = pal.text2,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        CompositionLocalProvider(LocalHideHeader provides true) {
            androidx.compose.animation.Crossfade(targetState = tab, label = "tab") { current ->
                when (current) {
                    Tab.HOME -> HomeScreen(
                        vm, m,
                        onGoActivity = { onTab(Tab.ACTIVITY) },
                        onGoHealth = { onTab(Tab.HEALTH) },
                        onGoAge = { onTab(Tab.AGE) },
                        onGoLabs = { onTab(Tab.LABS) },
                        onOpenMetric = { push(R_METRIC + it) },
                        onOpenSync = { push(R_SYNC) },
                        onOpenProfile = { push(R_PROFILE) },
                        onOpenJournal = { push(R_JOURNAL) },
                        onGoBp = { push(R_BP) },
                        onAsk = askClaude,
                        onRequestPermissions = onRequestPermissions,
                    )
                    Tab.ACTIVITY -> ActivityScreen(vm, m, onRequestPermissions, onOpenSync = { push(R_SYNC) }, onOpenMetric = { push(R_METRIC + it) })
                    Tab.HEALTH -> WearableScreen(
                        vm, m, onRequestPermissions,
                        onOpenJournal = { push(R_JOURNAL) }, onOpenBp = { push(R_BP) }, onOpenMetric = { push(R_METRIC + it) },
                    )
                    Tab.AGE -> AgeScreen(
                        vm, m,
                        onOpenInfo = { push(R_INFO) },
                        onGoLabs = { onTab(Tab.LABS) },
                        onGoWear = { onTab(Tab.HEALTH) },
                        onGoProfile = { push(R_PROFILE) },
                        onImportReport = onImportReport,
                        onGoInsights = { push(R_INSIGHTS) },
                        onGoRecovery = { onTab(Tab.HEALTH) },
                        onGoBp = { push(R_BP) },
                        onAsk = askClaude,
                    )
                    Tab.LABS -> LabsScreen(
                        vm, m,
                        onEdit = { push(R_EDIT + it) },
                        onNew = { push(R_EDIT + "new") },
                        onImportReport = onImportReport,
                        onAnalyte = { push(R_ANALYTE + it) },
                        onGoBp = { push(R_BP) },
                    )
                }
            }
        }
    }
}

/** Selettore segmentato Material 3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentPill(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = i == selected, onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(o, maxLines = 1) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScaffold(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable (Modifier) -> Unit) {
    val pal = LocalPalette.current
    Scaffold(
        containerColor = pal.bg,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(BioIcons.Back, "Indietro") } },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pal.bg),
            )
        },
    ) { pad -> CompositionLocalProvider(LocalHideHeader provides true) { content(Modifier.padding(pad)) } }
}

@Composable
fun ProfileRoute(
    vm: AppViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onPickBackupFolder: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    SubScaffold("Profilo e impostazioni", onBack) { m ->
        SettingsScreen(vm, m, onExport, onImport, onPickBackupFolder, onOpenInfo, onOpenDiagnostics)
    }
}
