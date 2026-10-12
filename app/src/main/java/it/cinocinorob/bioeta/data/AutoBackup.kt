package it.cinocinorob.bioeta.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.time.Instant
import java.time.LocalDate

/**
 * Backup automatico in una cartella scelta dall'utente (Storage Access Framework).
 * Dopo ogni modifica scrive:
 *  - BioEta-backup.json (sempre l'ultima versione)
 *  - BioEta-backup-AAAA-MM-GG.json (una copia al giorno, ultime 14 conservate)
 */
class AutoBackup(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val folderUri: Uri? get() = prefs.getString(KEY_TREE, null)?.let(Uri::parse)
    val lastBackup: Instant? get() = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
    val lastError: String? get() = prefs.getString(KEY_ERR, null)

    fun folderName(): String? = folderUri?.let { runCatching { DocumentFile.fromTreeUri(context, it)?.name }.getOrNull() }

    fun setFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.edit().putString(KEY_TREE, uri.toString()).remove(KEY_ERR).apply()
    }

    fun clearFolder() {
        folderUri?.let { u ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs.edit().remove(KEY_TREE).remove(KEY_ERR).apply()
    }

    /** Scrive il backup; restituisce true se riuscito. Da chiamare fuori dal thread principale. */
    fun write(json: String): Boolean {
        val tree = folderUri ?: return false
        return try {
            val dir = DocumentFile.fromTreeUri(context, tree) ?: error("cartella non accessibile")
            if (!dir.canWrite()) error("permesso di scrittura revocato: scegli di nuovo la cartella")
            writeFile(dir, LATEST, json)
            val daily = "$PREFIX${LocalDate.now()}.json"
            writeFile(dir, daily, json)
            // Conserva solo le ultime 14 copie giornaliere
            dir.listFiles()
                .filter { it.name?.startsWith(PREFIX) == true && it.name != LATEST }
                .sortedByDescending { it.name }
                .drop(KEEP_DAILY)
                .forEach { it.delete() }
            prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).remove(KEY_ERR).apply()
            true
        } catch (e: Exception) {
            prefs.edit().putString(KEY_ERR, e.message ?: e.javaClass.simpleName).apply()
            false
        }
    }

    private fun writeFile(dir: DocumentFile, name: String, json: String) {
        val f = dir.findFile(name) ?: dir.createFile("application/json", name.removeSuffix(".json"))
            ?: error("impossibile creare $name")
        context.contentResolver.openOutputStream(f.uri, "wt")!!.use { it.write(json.toByteArray()) }
    }

    companion object {
        private const val KEY_TREE = "backup_tree"
        private const val KEY_LAST = "backup_last"
        private const val KEY_ERR = "backup_err"
        private const val PREFIX = "BioEta-backup-"
        const val LATEST = "BioEta-backup.json"
        private const val KEEP_DAILY = 14
    }
}
