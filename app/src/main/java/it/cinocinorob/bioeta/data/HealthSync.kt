package it.cinocinorob.bioeta.data

import it.cinocinorob.bioeta.health.HealthRepo
import it.cinocinorob.bioeta.health.Metric
import java.time.Instant
import java.time.LocalDate

/**
 * Letture da Connessione Salute condivise tra app e worker in background.
 *
 * - [quick]: oggi (passi, calorie), ultime 3 notti, ultimi 4 giorni delle serie giornaliere. Pochi secondi.
 * - [full]: 120 giorni di serie, 75 di sonno. Solo quando [decide] lo ritiene necessario.
 * In entrambe, se la lettura di un tipo di dato fallisce, i dati validi già presenti non vengono cancellati.
 */
class HealthSync(private val repo: HealthRepo) {

    companion object {
        const val DAYS_WINDOW = 120L
        const val RECOVERY_WINDOW = 75L
        private const val RECENT_DAYS = 4L
    }

    suspend fun decide(s: HealthSnapshot, force: Boolean): Pair<Boolean, ChangeSummary?> {
        val ch = repo.changesSince(s.changesToken)
        return SnapshotMerge.needFull(force, s, Instant.now(), ch) to ch
    }

    suspend fun quick(s: HealthSnapshot): HealthSnapshot {
        val today = LocalDate.now()
        val from = today.minusDays(RECENT_DAYS - 1)
        val ta = runCatching { repo.todayActivity() }.getOrNull() ?: s.today.takeIf { s.todayDate == today }
        val rec = repo.readRecovery(3)
        val nights = if (rec.nights.isNotEmpty()) SnapshotMerge.nights(s.nights, rec.nights) else s.nights
        val exercises = if (repo.errors.containsKey("Allenamenti")) s.exercises
            else s.exercises.filter { it.date.isBefore(from) } + rec.exercises.filter { !it.date.isBefore(from) }
        val recent = repo.readAll(RECENT_DAYS)
        val okRecent = recent.filterKeys { m -> !repo.errors.containsKey(repo.readErrorKeyFor(m)) }
        return s.copy(
            wearable = SnapshotMerge.series(s.wearable, okRecent, from),
            nights = nights,
            exercises = exercises.sortedBy { it.date },
            today = ta, todayDate = if (ta != null) today else s.todayDate,
            lastWearable = runCatching { repo.lastWearableData() }.getOrNull() ?: s.lastWearable,
            lastRefresh = Instant.now(),
        )
    }

    suspend fun full(s: HealthSnapshot, granted: Set<String>): HealthSnapshot {
        // Token preso PRIMA della lettura: i dati arrivati durante la lettura verranno visti la prossima volta
        val token = repo.changesToken()
        val fresh = repo.readAll(DAYS_WINDOW)
        // Metriche autorizzate la cui lettura è fallita: si conservano i dati precedenti
        val keep = Metric.entries.filter { m ->
            repo.permissionFor(m) in granted && m !in fresh.keys && repo.errors.containsKey(repo.readErrorKeyFor(m))
        }.toSet()
        val wearable = fresh + s.wearable.filterKeys { it in keep && repo.permissionFor(it) in granted }
        val rec = repo.readRecovery(RECOVERY_WINDOW)
        val nights = if (repo.permissionFor(Metric.SLEEP) in granted && repo.errors.containsKey("Sonno (fasi)")) s.nights else rec.nights
        val exercises = if (repo.errors.containsKey("Allenamenti")) s.exercises else rec.exercises
        return s.copy(
            wearable = wearable, nights = nights, exercises = exercises,
            lastFull = Instant.now(), lastRefresh = Instant.now(),
            changesToken = token ?: s.changesToken,
        )
    }
}
