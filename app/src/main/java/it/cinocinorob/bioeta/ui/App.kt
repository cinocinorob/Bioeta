package it.cinocinorob.bioeta.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cinocinorob.bioeta.AppViewModel

enum class Tab(val label: String) { TODAY("Oggi"), ACTIVITY("Attività"), WEAR("Recupero"), HEALTH("Salute"), ASSIST("Claude") }

private fun tabIcon(t: Tab): ImageVector = when (t) {
    Tab.TODAY -> BioIcons.Sparkle
    Tab.ACTIVITY -> BioIcons.Bolt
    Tab.WEAR -> BioIcons.Moon
    Tab.HEALTH -> BioIcons.Drop
    Tab.ASSIST -> BioIcons.Chat
}

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
    val pal = LocalPalette.current
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var analyteKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showInfo by rememberSaveable { mutableStateOf(startOnPrivacy) }
    var showInsights by rememberSaveable { mutableStateOf(false) }
    var showBp by rememberSaveable { mutableStateOf(false) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var showJournal by rememberSaveable { mutableStateOf(false) }
    var healthSection by rememberSaveable { mutableStateOf(0) }
    var pendingQuestion by rememberSaveable { mutableStateOf<String?>(null) }
    val askClaude: (String) -> Unit = { q ->
        showInsights = false
        showBp = false
        showProfile = false
        showJournal = false
        pendingQuestion = q.ifBlank { null }
        tab = Tab.ASSIST
    }
    val snackbar = remember { SnackbarHostState() }
    val draft = vm.importDraft

    LaunchedEffect(vm.message) {
        val m = vm.message
        if (m != null) {
            snackbar.showSnackbar(m)
            vm.message = null
        }
    }

    val subOpen = showInfo || showInsights || showBp || showProfile || showJournal || creating || editingId != null || analyteKey != null || draft != null
    BackHandler(enabled = subOpen) {
        when {
            showInfo -> showInfo = false
            showInsights -> showInsights = false
            showBp -> showBp = false
            showJournal -> showJournal = false
            showProfile -> showProfile = false
            draft != null -> vm.importDraft = null
            creating || editingId != null -> { creating = false; editingId = null }
            analyteKey != null -> analyteKey = null
        }
    }

    when {
        showInfo -> InfoScreen(onBack = { showInfo = false })
        showInsights -> InsightsScreen(vm, onBack = { showInsights = false }, onAsk = askClaude)
        showBp -> BloodPressureScreen(vm, onBack = { showBp = false }, onAsk = askClaude)
        showJournal -> JournalScreen(vm, onBack = { showJournal = false })
        showProfile -> ProfileRoute(vm, onBack = { showProfile = false }, onExport, onImport, onPickBackupFolder, onOpenInfo = { showInfo = true })
        draft != null -> PanelEditor(
            initial = draft.panel,
            isNew = true,
            importNotes = draft.notes,
            highlighted = draft.foundKeys,
            profile = vm.profile,
            onSave = { vm.savePanel(it); vm.importDraft = null; tab = Tab.HEALTH; healthSection = 1; vm.message = "Referto salvato." },
            onDelete = { vm.importDraft = null },
            onBack = { vm.importDraft = null },
        )
        creating || editingId != null -> PanelEditor(
            initial = vm.panels.find { it.id == editingId },
            isNew = editingId == null,
            importNotes = emptyList(),
            highlighted = emptySet(),
            profile = vm.profile,
            onSave = { vm.savePanel(it); creating = false; editingId = null },
            onDelete = { id -> vm.deletePanel(id); creating = false; editingId = null },
            onBack = { creating = false; editingId = null },
        )
        analyteKey != null -> AnalyteScreen(
            key = analyteKey!!,
            panels = vm.panels,
            profile = vm.profile,
            onBack = { analyteKey = null },
        )
        else -> Scaffold(
            containerColor = pal.bg,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { FloatingTabBar(tab, onSelect = { tab = it }) },
        ) { pad ->
            val m = Modifier.padding(pad)
            when (tab) {
                Tab.TODAY -> TodayScreen(
                    vm, m,
                    onGoRecovery = { tab = Tab.WEAR },
                    onGoActivity = { tab = Tab.ACTIVITY },
                    onGoHealth = { tab = Tab.HEALTH; healthSection = 0 },
                    onGoBp = { showBp = true },
                    onOpenProfile = { showProfile = true },
                    onOpenJournal = { showJournal = true },
                    onAsk = askClaude,
                    onRequestPermissions = onRequestPermissions,
                )
                Tab.ACTIVITY -> ActivityScreen(vm, m, onRequestPermissions)
                Tab.WEAR -> WearableScreen(vm, m, onRequestPermissions, onOpenJournal = { showJournal = true })
                Tab.HEALTH -> Column(m) {
                    SegmentPill(listOf("Panoramica", "Analisi del sangue"), healthSection, onSelect = { healthSection = it },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp))
                    if (healthSection == 0) AgeScreen(
                        vm, Modifier,
                        onOpenInfo = { showInfo = true },
                        onGoLabs = { healthSection = 1 },
                        onGoWear = { tab = Tab.WEAR },
                        onGoProfile = { showProfile = true },
                        onImportReport = onImportReport,
                        onGoInsights = { showInsights = true },
                        onGoRecovery = { tab = Tab.WEAR },
                        onGoBp = { showBp = true },
                        onAsk = askClaude,
                    ) else LabsScreen(
                        vm, Modifier,
                        onEdit = { editingId = it },
                        onNew = { creating = true },
                        onImportReport = onImportReport,
                        onAnalyte = { analyteKey = it },
                        onGoBp = { showBp = true },
                    )
                }
                Tab.ASSIST -> AssistantScreen(vm, m, pendingQuestion, onConsumeInitial = { pendingQuestion = null })
            }
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
                        label = { Text("Password") },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.importReport(pwUri, pw.trim()) }, enabled = pw.isNotBlank()) { Text("Apri") } },
            dismissButton = { TextButton(onClick = { vm.passwordFor = null }) { Text("Annulla") } },
        )
    }
}


/** Barra di navigazione flottante a pillola, traslucida, con l'elemento attivo evidenziato. */
@Composable
fun FloatingTabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val pal = LocalPalette.current
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(34.dp), clip = false, ambientColor = Color(0x33806A4A), spotColor = Color(0x33806A4A))
                .clip(RoundedCornerShape(34.dp))
                .background(pal.card.copy(alpha = if (pal.dark) 0.94f else 0.96f))
                .border(1.dp, pal.sep.copy(alpha = 0.7f), RoundedCornerShape(34.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Tab.entries.forEach { t ->
                val sel = t == selected
                val c = when (t) { Tab.TODAY -> Pillar.recovery[1]; Tab.ACTIVITY -> Pillar.load[1]; Tab.WEAR -> Pillar.sleep[1]; Tab.HEALTH -> Tint.blood; Tab.ASSIST -> Tint.blue }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(28.dp))
                        .background(if (sel) c.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onSelect(t) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tabIcon(t), t.label, tint = if (sel) c else pal.text2, modifier = Modifier.size(22.dp))
                    Text(t.label, fontSize = 10.sp, color = if (sel) c else pal.text2, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}


@Composable
fun SegmentPill(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(pal.field).padding(4.dp),
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            androidx.compose.foundation.layout.Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (sel) pal.card else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, fontSize = 13.sp, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (sel) pal.text else pal.text2) }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileRoute(
    vm: AppViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onPickBackupFolder: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    val pal = LocalPalette.current
    Scaffold(
        containerColor = pal.bg,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {},
                navigationIcon = { androidx.compose.material3.IconButton(onClick = onBack) { Icon(BioIcons.Back, "Indietro", tint = Tint.blue) } },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = pal.bg),
            )
        },
    ) { pad -> SettingsScreen(vm, Modifier.padding(pad), onExport, onImport, onPickBackupFolder, onOpenInfo) }
}
