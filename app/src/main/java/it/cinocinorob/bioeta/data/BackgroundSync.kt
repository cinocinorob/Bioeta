package it.cinocinorob.bioeta.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.cinocinorob.bioeta.health.HealthRepo
import it.cinocinorob.bioeta.science.ActivityEngine
import it.cinocinorob.bioeta.science.Fitness
import it.cinocinorob.bioeta.widget.BioWidget
import it.cinocinorob.bioeta.widget.WidgetData
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Aggiornamento orario in background (WorkManager): lettura veloce da Connessione Salute, allenamenti,
 * fotografia salvata e widget. Funziona solo se l'utente ha concesso la lettura in background
 * (Connessione Salute recente, es. Android 15+ / Pixel); altrimenti non fa nulla.
 * Non fa letture complete (120 giorni): quelle restano all'apertura dell'app.
 */
class BackgroundSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = HealthRepo(applicationContext)
        if (repo.sdkStatus() != androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) return Result.success()
        val granted = runCatching { repo.granted() }.getOrDefault(emptySet())
        if (HealthRepo.BACKGROUND_PERMISSION !in granted) return Result.success()
        return try {
            val cache = HealthCache(applicationContext)
            val sync = HealthSync(repo)
            var snap = cache.load()
            val (_, changes) = sync.decide(snap, force = false)
            snap = sync.quick(snap)
            if (changes != null && !changes.expired && changes.newToken != null) snap = snap.copy(changesToken = changes.newToken)
            snap = snap.copy(lastBackground = Instant.now())
            cache.save(snap)

            // Allenamenti: stessa unione idempotente usata dall'app
            val store = WorkoutStore(applicationContext)
            val (sessions, status) = store.load()
            val r = repo.readSessions(90)
            if (r.permitted && r.error == null) {
                val merged = WorkoutStore.merge(sessions, r.sessions, r.from, r.to)
                store.save(merged, status.copy(lastSync = Instant.now(), state = SyncState.OK, windowDays = r.days,
                    sessionsInWindow = ActivityEngine.dedupe(merged.filter { !it.start.isBefore(r.from) }).size))
            }

            // Widget: punteggi calcolati con lo stesso codice dell'app
            val profile = LabStore(applicationContext).loadProfile()
            val days = store.loadDays()?.days.orEmpty()
            val scores = Scoring.scoresFor(LocalDate.now(), ScoringInputs(snap.wearable, snap.nights, snap.exercises, days.map { it.toDayLoad() }, profile))
            WidgetData.save(
                applicationContext, readiness = scores.readiness.score, sleep = scores.sleep?.score,
                steps = snap.today?.steps, stepGoal = Fitness.stepGoal(profile.ageAt(LocalDate.now())),
                load = days.lastOrNull { it.date == LocalDate.now() }?.load,
            )
            BioWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "bioeta-background-sync"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
