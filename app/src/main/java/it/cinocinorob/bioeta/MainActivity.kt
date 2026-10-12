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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val action = intent?.action
        val openPrivacy = action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
            action == "android.intent.action.VIEW_PERMISSION_USAGE"
        setContent {
            BioTheme {
                App(
                    vm = vm,
                    startOnPrivacy = openPrivacy,
                    onRequestPermissions = { permissionLauncher.launch(vm.health.permissions) },
                    onExport = { exportLauncher.launch("bioeta-backup-${LocalDate.now()}.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshHealth()
    }
}
