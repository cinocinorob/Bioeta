package it.cinocinorob.bioeta.science

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Funzioni ispirate a ciò che offrono Samsung Health (Bedtime Guidance), Oura/Ultrahuman (orario ideale,
 * finestra caffeina), Apple Salute (app Parametri vitali) e Bevel/Garmin (andamento del carico),
 * riscritte con criteri dichiarati e fonti verificate su PubMed.
 */

object ExtraRefs {
    val GARDINER = Ref("Gardiner et al., Sleep Med Rev 2023", "The effect of caffeine on subsequent sleep: a systematic review and meta-analysis", "36870101", "10.1016/j.smrv.2023.101764", StudyType.META_RCT)
    val WINDRED = Ref("Windred et al., Sleep 2024", "Sleep regularity is a stronger predictor of mortality risk than sleep duration: a prospective cohort study", "37738616", "10.1093/sleep/zsad253", StudyType.META_OBS)
    val MISHRA = Ref("Mishra et al., Nat Biomed Eng 2020", "Pre-symptomatic detection of COVID-19 from smartwatch data", "33208926", "10.1038/s41551-020-00640-6", StudyType.SYSTEMATIC)
    val GABBETT = Ref("Gabbett, Br J Sports Med 2016", "The training-injury prevention paradox: should athletes be training smarter and harder?", "26758673", "10.1136/bjsports-2015-095788", StudyType.SYSTEMATIC)
    val IMPELLIZZERI = Ref("Impellizzeri et al., IJSPP 2020", "Acute:Chronic Workload Ratio: conceptual issues and fundamental pitfalls", "32502973", "10.1123/ijspp.2019-0864", StudyType.SYSTEMATIC)
}

// =============================================================================================
// Orario di sonno consigliato + finestra caffeina
// =============================================================================================

data class BedtimeGuide(
    val bedtime: LocalTime,
    val wake: LocalTime,
    val caffeineCutoff: LocalTime,
    val debtHours: Double,
    val habitualBed: LocalTime,
    val nights: Int,
    val rationale: String,
)

object Bedtime {
    /** Minuti dalla mezzanotte, "srotolati" attorno a mezzogiorno (così 23:30 e 00:30 sono vicini). */
    private fun unwrap(t: LocalTime): Int { val m = t.toSecondOfDay() / 60; return if (m < 12 * 60) m + 24 * 60 else m }
    private fun wrap(m: Int): LocalTime = LocalTime.ofSecondOfDay((((m % 1440) + 1440) % 1440).toLong() * 60)
    private fun median(xs: List<Int>): Int = xs.sorted().let { s -> if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }

    /**
     * Ancora: l'orario di risveglio abituale (mediana di 14 notti), perché la regolarità sonno-veglia
     * è il parametro con l'associazione più forte con la mortalità (Windred 2024).
     * Orario consigliato = risveglio abituale − bisogno di sonno − 15 min per addormentarsi,
     * anticipato di 1/3 del debito settimanale (max 45 min) per recuperarlo in modo graduale.
     * Caffeina: stop ≥ 8,8 h prima di coricarsi per una tazza da ~107 mg (Gardiner 2023).
     * Il recupero graduale del debito è una scelta progettuale, non un protocollo validato.
     */
    fun guide(nights: List<SleepNight>, today: LocalDate, age: Double?, zone: ZoneId = ZoneId.systemDefault()): BedtimeGuide? {
        val recent = nights.filter { !it.date.isAfter(today) && it.date.isAfter(today.minusDays(14)) }
        if (recent.size < 5) return null
        val wakeMed = median(recent.map { it.end.atZone(zone).toLocalTime().toSecondOfDay() / 60 })
        val bedMed = median(recent.map { unwrap(it.start.atZone(zone).toLocalTime()) })
        val need = Readiness.sleepNeed(age)
        val week = recent.filter { it.date.isAfter(today.minusDays(7)) }
        val debt = if (week.isEmpty()) 0.0 else week.sumOf { max(0.0, need - it.totalSleepMin / 60.0) } * 7.0 / week.size
        val extra = min(45.0, debt * 60 / 3).roundToInt()
        val bed = wakeMed - (need * 60).roundToInt() - 15 - extra
        val bedT = wrap(bed); val wakeT = wrap(wakeMed)
        val why = buildString {
            append("Ti svegli di solito alle ${fmt(wakeT)}: per dormire ${fmtH(need)} servono circa ${fmtH(need + 0.25)} a letto")
            if (extra > 0) append(", più $extra min per recuperare il debito di sonno")
            append(". Mantenere lo stesso orario di risveglio conta più della durata.")
        }
        return BedtimeGuide(bedT, wakeT, wrap(bed - (8.8 * 60).roundToInt()), debt, wrap(bedMed), recent.size, why)
    }

    fun fmt(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)
    private fun fmtH(h: Double): String { val m = (h * 60).roundToInt(); return "${m / 60} h" + if (m % 60 != 0) " ${m % 60} min" else "" }
}

// =============================================================================================
// Parametri vitali notturni (come l'app Parametri vitali di Apple)
// =============================================================================================

enum class VitalState { IN_RANGE, HIGH, LOW, NO_DATA }

data class Vital(
    val label: String,
    val unit: String,
    val value: Double?,
    val low: Double?,
    val high: Double?,
    val state: VitalState,
    val decimals: Int,
)

data class VitalsReport(val vitals: List<Vital>, val outliers: Int, val message: String)

object Vitals {
    /**
     * Intervallo personale = media ± 2 DS dei 30 giorni precedenti (minimo 10 valori).
     * Due o più parametri fuori intervallo nella stessa notte sono il segnale che le app usano per
     * avvisare di malattia incipiente, alcol o stress: l'aumento di FC a riposo precede i sintomi
     * nelle infezioni (Mishra 2020). È un'indicazione, non una diagnosi.
     */
    fun report(series: Map<String, Triple<List<DailyValue>, String, Int>>, today: LocalDate): VitalsReport {
        val out = series.map { (label, t) ->
            val (pts, unit, dec) = t
            val v = pts.lastOrNull { it.date == today || it.date == today.minusDays(1) }
            val base = pts.filter { it.date.isBefore(v?.date ?: today) && it.date.isAfter(today.minusDays(31)) }.map { it.value }
            if (v == null) return@map Vital(label, unit, null, null, null, VitalState.NO_DATA, dec)
            if (base.size < 10) return@map Vital(label, unit, v.value, null, null, VitalState.IN_RANGE, dec)
            val m = base.average(); val sd = sqrt(base.sumOf { (it - m) * (it - m) } / (base.size - 1))
            val band = max(2 * sd, abs(m) * 0.02) // evita intervalli assurdamente stretti
            val lo = m - band; val hi = m + band
            val st = when { v.value > hi -> VitalState.HIGH; v.value < lo -> VitalState.LOW; else -> VitalState.IN_RANGE }
            Vital(label, unit, v.value, lo, hi, st, dec)
        }
        val n = out.count { it.state == VitalState.HIGH || it.state == VitalState.LOW }
        val msg = when {
            out.all { it.state == VitalState.NO_DATA } -> "Nessun dato notturno recente."
            n == 0 -> "Tutti i parametri sono nel tuo intervallo abituale."
            n == 1 -> "Un parametro è fuori dal tuo intervallo: da solo è spesso rumore di misura."
            else -> "$n parametri fuori dal tuo intervallo: succede con un'infezione in arrivo, alcol, stress o allenamento molto intenso. Se ti senti male, considera di riposare; se persiste, parlane con il medico."
        }
        return VitalsReport(out, n, msg)
    }
}

// =============================================================================================
// Andamento del carico (7 vs 28 giorni) — solo descrittivo
// =============================================================================================

enum class LoadTrendState(val label: String) { DETRAINING("In calo"), STEADY("Stabile"), BUILDING("In crescita"), SPIKE("Picco"), NA("In calibrazione") }

data class LoadTrend(val acute: Double, val chronic: Double, val ratio: Double?, val state: LoadTrendState, val message: String)

object LoadTrendCalc {
    /**
     * Carico medio degli ultimi 7 giorni rispetto agli ultimi 28 (stessa idea del rapporto acuto:cronico
     * di Gabbett 2016). Lo mostriamo SOLO come descrizione di quanto stai cambiando il carico:
     * non esiste prova causale che modificare il rapporto riduca gli infortuni (Impellizzeri 2020).
     */
    fun compute(loads: List<DayLoad>, today: LocalDate): LoadTrend {
        val last28 = loads.filter { it.date.isAfter(today.minusDays(28)) && !it.date.isAfter(today) }
        val last7 = last28.filter { it.date.isAfter(today.minusDays(7)) }
        val acute = if (last7.isEmpty()) 0.0 else last7.sumOf { it.load } / 7.0
        val chronic = if (last28.isEmpty()) 0.0 else last28.sumOf { it.load } / 28.0
        if (last28.count { it.coveredMinutes > 600 } < 14 || chronic < 1.0) {
            return LoadTrend(acute, chronic, null, LoadTrendState.NA, "Servono almeno 14 giorni con l'orologio indossato.")
        }
        val r = acute / chronic
        val (st, msg) = when {
            r < 0.8 -> LoadTrendState.DETRAINING to "Questa settimana ti muovi meno del solito: va bene per recuperare, ma se dura a lungo la forma cala."
            r <= 1.3 -> LoadTrendState.STEADY to "Carico in linea con le ultime 4 settimane."
            r <= 1.5 -> LoadTrendState.BUILDING to "Stai aumentando il carico: ok se il recupero resta buono."
            else -> LoadTrendState.SPIKE to "Aumento brusco rispetto al tuo abituale: gli aumenti rapidi sono quelli che più spesso precedono sovraccarichi. Valuta un giorno leggero."
        }
        return LoadTrend(acute, chronic, r, st, msg)
    }
}

// =============================================================================================
// Riepilogo settimanale
// =============================================================================================

data class WeekStat(val label: String, val thisWeek: Double?, val lastWeek: Double?, val unit: String, val decimals: Int, val higherIsBetter: Boolean?)

object WeekSummary {
    fun avg(xs: List<Double>): Double? = if (xs.isEmpty()) null else xs.average()

    fun durationH(n: SleepNight) = Duration.between(n.start, n.end).toMinutes() / 60.0
}
