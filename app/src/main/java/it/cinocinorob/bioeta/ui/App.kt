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

enum class Tab(val label: String) { AGE("Riepilogo"), LABS("Analisi"), ASSIST("Claude"), WEAR("Recupero"), SETTINGS("Profilo") }

private fun tabIcon(t: Tab): ImageVector = when (t) {
    Tab.AGE -> BioIcons.Heart
    Tab.LABS -> BioIcons.Drop
    Tab.ASSIST -> BioIcons.Chat
    Tab.WEAR -> BioIcons.Moon
    Tab.SETTINGS -> BioIcons.Person
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
    var tab by rememberSaveable { mutableStateOf(Tab.AGE) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var analyteKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showInfo by rememberSaveable { mutableStateOf(startOnPrivacy) }
    var showInsights by rememberSaveable { mutableStateOf(false) }
    var showBp by rememberSaveable { mutableStateOf(false) }
    var pendingQuestion by rememberSaveable { mutableStateOf<String?>(null) }
    val askClaude: (String) -> Unit = { q ->
        showInsights = false
        showBp = false
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

    val subOpen = showInfo || showInsights || showBp || creating || editingId != null || analyteKey != null || draft != null
    BackHandler(enabled = subOpen) {
        when {
            showInfo -> showInfo = false
            showInsights -> showInsights = false
            showBp -> showBp = false
            draft != null -> vm.importDraft = null
            creating || editingId != null -> { creating = false; editingId = null }
            analyteKey != null -> analyteKey = null
        }
    }

    when {
        showInfo -> InfoScreen(onBack = { showInfo = false })
        showInsights -> InsightsScreen(vm, onBack = { showInsights = false }, onAsk = askClaude)
        showBp -> BloodPressureScreen(vm, onBack = { showBp = false }, onAsk = askClaude)
        draft != null -> PanelEditor(
            initial = draft.panel,
            isNew = true,
            importNotes = draft.notes,
            highlighted = draft.foundKeys,
            profile = vm.profile,
            onSave = { vm.savePanel(it); vm.importDraft = null; tab = Tab.LABS; vm.message = "Referto salvato." },
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
                Tab.AGE -> AgeScreen(
                    vm, m,
                    onOpenInfo = { showInfo = true },
                    onGoLabs = { tab = Tab.LABS },
                    onGoWear = { tab = Tab.WEAR },
                    onGoProfile = { tab = Tab.SETTINGS },
                    onImportReport = onImportReport,
                    onGoInsights = { showInsights = true },
                    onGoRecovery = { tab = Tab.WEAR },
                    onGoBp = { showBp = true },
                    onAsk = askClaude,
                )
                Tab.LABS -> LabsScreen(
                    vm, m,
                    onEdit = { editingId = it },
                    onNew = { creating = true },
                    onImportReport = onImportReport,
                    onAnalyte = { analyteKey = it },
                    onGoBp = { showBp = true },
                )
                Tab.ASSIST -> AssistantScreen(vm, m, pendingQuestion, onConsumeInitial = { pendingQuestion = null })
                Tab.WEAR -> WearableScreen(vm, m, onRequestPermissions)
                Tab.SETTINGS -> SettingsScreen(vm, m, onExport, onImport, onPickBackupFolder, onOpenInfo = { showInfo = true })
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
                val c = when (t) { Tab.AGE -> Tint.heart; Tab.LABS -> Tint.blood; Tab.ASSIST -> Tint.sleep; Tab.WEAR -> Tint.sleep; Tab.SETTINGS -> Tint.blue }
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
