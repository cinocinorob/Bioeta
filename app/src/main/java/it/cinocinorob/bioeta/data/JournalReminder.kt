package it.cinocinorob.bioeta.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.cinocinorob.bioeta.MainActivity
import it.cinocinorob.bioeta.science.JournalCatalog
import it.cinocinorob.bioeta.science.answer
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Promemoria serale del diario: una notifica all'ora scelta, solo se le domande di oggi non sono complete.
 * Nessun dato sanitario nel testo della notifica (è visibile sulla schermata di blocco).
 */
class JournalReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(JournalReminder.KEY_ON, false)) return Result.success()
        val tracked = prefs.getString("journal_items", null)?.split("\u001F")?.filter { it.isNotBlank() }
            ?: JournalCatalog.DEFAULT_KEYS.toList()
        val e = LabStore(ctx).loadJournal().firstOrNull { it.date == LocalDate.now() }
        val answered = tracked.count { k -> e?.answer(k) != null }
        if (answered < tracked.size) JournalReminder.notify(ctx, tracked.size - answered)
        JournalReminder.schedule(ctx) // prossima sera
        return Result.success()
    }
}

object JournalReminder {
    const val KEY_ON = "journal_reminder_on"
    const val KEY_TIME = "journal_reminder_min"
    const val DEFAULT_MIN = 21 * 60 + 30
    private const val WORK = "journal_reminder"
    private const val CHANNEL = "diario"

    fun minutes(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt(KEY_TIME, DEFAULT_MIN)

    /** Programma il prossimo promemoria (oggi se l'ora non è passata, altrimenti domani). */
    fun schedule(ctx: Context) {
        val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val wm = WorkManager.getInstance(ctx)
        if (!prefs.getBoolean(KEY_ON, false)) { wm.cancelUniqueWork(WORK); return }
        val m = minutes(ctx)
        val now = LocalDateTime.now()
        var next = LocalDate.now().atTime(LocalTime.of(m / 60, m % 60))
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()
        wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<JournalReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
    }

    fun notify(ctx: Context, missing: Int) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Diario serale", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Promemoria per rispondere alle domande del diario"
            })
        }
        val open = PendingIntent.getActivity(ctx, 7,
            Intent(ctx, MainActivity::class.java).putExtra("open", "journal").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(it.cinocinorob.bioeta.R.drawable.ic_mono)
            .setContentTitle("Com'è andata oggi?")
            .setContentText(if (missing == 1) "Manca 1 risposta nel diario: 30 secondi." else "Mancano $missing risposte nel diario: 30 secondi.")
            .setContentIntent(open).setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(71, n) }
    }
}
