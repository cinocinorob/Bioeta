package it.cinocinorob.bioeta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.health.connect.client.PermissionController
import it.cinocinorob.bioeta.ui.App
import it.cinocinorob.bioeta.ui.BioTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            vm.onPermissionsResult(granted)
        }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) vm.exportTo(uri)
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importFrom(uri)
        }

    private val reportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importReport(uri)
        }

    private val backupFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) vm.setBackupFolder(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        it.cinocinorob.bioeta.data.BackgroundSyncWorker.schedule(applicationContext)
        val action = intent?.action
        val openPrivacy = action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
            action == "android.intent.action.VIEW_PERMISSION_USAGE"
        setContent {
            val dark = when (vm.themeMode) {
                it.cinocinorob.bioeta.ui.ThemeMode.DARK -> true
                it.cinocinorob.bioeta.ui.ThemeMode.LIGHT -> false
                it.cinocinorob.bioeta.ui.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            androidx.compose.runtime.SideEffect {
                // Icone della barra di stato leggibili sul tema dell'app (non su quello di sistema)
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            BioTheme(mode = vm.themeMode) {
                App(
                    vm = vm,
                    startOnPrivacy = openPrivacy,
                    onRequestPermissions = { permissionLauncher.launch(vm.health.permissionsToRequest()) },
                    onExport = { exportLauncher.launch("bioeta-backup-${LocalDate.now()}.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onImportReport = { reportLauncher.launch(arrayOf("application/pdf", "image/*")) },
                    onPickBackupFolder = { backupFolderLauncher.launch(null) },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.stopReads()
    }

    override fun onResume() {
        super.onResume()
        vm.refreshHealth()
    }
}
