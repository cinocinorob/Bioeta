package it.cinocinorob.bioeta.data

import it.cinocinorob.bioeta.health.DailyPoint
import it.cinocinorob.bioeta.health.Metric
import it.cinocinorob.bioeta.science.DailyValue
import it.cinocinorob.bioeta.science.DayLoad
import it.cinocinorob.bioeta.science.ExerciseLoad
import it.cinocinorob.bioeta.science.Readiness
import it.cinocinorob.bioeta.science.ScoreResult
import it.cinocinorob.bioeta.science.SleepNight
import it.cinocinorob.bioeta.science.SleepScore
import java.time.LocalDate

/** Punteggi di un giorno. */
data class DayScores(val date: LocalDate, val night: SleepNight?, val sleep: ScoreResult?, val readiness: ScoreResult)

/** Dati d'ingresso dei punteggi (stessi per l'app e per l'aggiornamento in background). */
data class ScoringInputs(
    val wearable: Map<Metric, List<DailyPoint>>,
    val nights: List<SleepNight>,
    val exercises: List<ExerciseLoad>,
    val dayLoads: List<DayLoad>,
    val profile: Profile,
)

/**
 * Calcolo puro dei punteggi Sonno e Recupero per una data: nessuna dipendenza da Android,
 * usato dal ViewModel e dal worker in background (così i due non possono divergere).
 */
object Scoring {
    fun sleepFor(date: LocalDate, i: ScoringInputs): Pair<SleepNight, ScoreResult>? {
        val night = i.nights.firstOrNull { it.date == date } ?: return null
        val hist = i.nights.filter { it.date.isBefore(date) && !it.date.isBefore(date.minusDays(60)) }
        return night to SleepScore.compute(night, hist, i.profile.ageAt(date))
    }

    fun scoresFor(date: LocalDate, i: ScoringInputs): DayScores {
        val s = sleepFor(date, i)
        fun series(m: Metric) = i.wearable[m].orEmpty().filter { !it.date.isAfter(date) }.map { DailyValue(it.date, it.value) }
        val rhr = series(Metric.RHR).ifEmpty {
            // Se manca la FC a riposo, usa la FC media notturna come approssimazione
            i.nights.filter { !it.date.isAfter(date) }.mapNotNull { n -> n.avgHr?.let { DailyValue(n.date, it) } }
        }
        val r = Readiness.compute(
            Readiness.Inputs(
                date = date,
                age = i.profile.ageAt(date),
                male = i.profile.sex == Sex.M,
                hrv = series(Metric.HRV),
                rhr = rhr,
                resp = series(Metric.RESP),
                nights = i.nights.filter { !it.date.isAfter(date) },
                lastNightScore = s?.second?.score,
                exercises = i.exercises.filter { !it.date.isAfter(date) },
                cardiacLoads = i.dayLoads.filter { !it.date.isAfter(date) && it.coveredMinutes > 0 }.map { DailyValue(it.date, it.load) },
            )
        )
        return DayScores(date, s?.first, s?.second, r)
    }

    fun history(today: LocalDate, days: Int, i: ScoringInputs): List<DayScores> =
        (days - 1 downTo 0).map { scoresFor(today.minusDays(it.toLong()), i) }
}
