package it.cinocinorob.bioeta.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import it.cinocinorob.bioeta.AppViewModel

enum class Tab(val label: String) { AGE("Età"), LABS("Analisi"), WEAR("Wearable"), SETTINGS("Profilo") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    vm: AppViewModel,
    startOnPrivacy: Boolean,
    onRequestPermissions: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.AGE) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var analyteKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showInfo by rememberSaveable { mutableStateOf(startOnPrivacy) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        val m = vm.message
        if (m != null) {
            snackbar.showSnackbar(m)
            vm.message = null
        }
    }

    val subOpen = showInfo || creating || editingId != null || analyteKey != null
    BackHandler(enabled = subOpen) {
        when {
            showInfo -> showInfo = false
            creating || editingId != null -> { creating = false; editingId = null }
            analyteKey != null -> analyteKey = null
        }
    }

    when {
        showInfo -> InfoScreen(onBack = { showInfo = false })
        creating || editingId != null -> PanelEditor(
            initial = vm.panels.find { it.id == editingId },
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
            topBar = { TopAppBar(title = { Text(if (tab == Tab.AGE) "BioEtà" else tab.label) }) },
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (tab == Tab.LABS) FloatingActionButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Nuovo referto")
                }
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            label = { Text(t.label) },
                            icon = {
                                Icon(
                                    when (t) {
                                        Tab.AGE -> Icons.Filled.Star
                                        Tab.LABS -> Icons.AutoMirrored.Filled.List
                                        Tab.WEAR -> Icons.Filled.Favorite
                                        Tab.SETTINGS -> Icons.Filled.Settings
                                    },
                                    contentDescription = t.label,
                                )
                            },
                        )
                    }
                }
            },
        ) { pad ->
            val m = Modifier.padding(pad)
            when (tab) {
                Tab.AGE -> AgeScreen(vm, m, onOpenInfo = { showInfo = true }, onGoLabs = { tab = Tab.LABS }, onGoProfile = { tab = Tab.SETTINGS })
                Tab.LABS -> LabsScreen(vm, m, onEdit = { editingId = it }, onAnalyte = { analyteKey = it })
                Tab.WEAR -> WearableScreen(vm, m, onRequestPermissions)
                Tab.SETTINGS -> SettingsScreen(vm, m, onExport, onImport, onOpenInfo = { showInfo = true })
            }
        }
    }
}
