package it.cinocinorob.bioeta.science

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Punteggi BioEtà "Sonno" e "Prontezza".
 *
 * Gli algoritmi commerciali (Oura, Fitbit/Google, Apple, WHOOP) dichiarano i componenti ma non
 * pesi e formule. Questi punteggi sono trasparenti: ogni componente ha una funzione a tratti
 * ancorata a soglie pubblicate (dove esistono) o alla baseline personale, e un peso motivato
 * dalla forza delle prove e dall'accuratezza della misura da polso. La combinazione in un unico
 * punteggio è una scelta progettuale NON validata su esiti clinici: va letta come sintesi.
 */

enum class StageType { AWAKE, LIGHT, DEEP, REM, ASLEEP_UNKNOWN, OUT_OF_BED }

data class Stage(val start: Instant, val end: Instant, val type: StageType) {
    val minutes: Double get() = Duration.between(start, end).seconds / 60.0
    val asleep: Boolean get() = type != StageType.AWAKE && type != StageType.OUT_OF_BED
}

data class SleepNight(
    /** Data del risveglio. */
    val date: LocalDate,
    val start: Instant,
    val end: Instant,
    val stages: List<Stage>,
    val avgHr: Double? = null,
    val minHr: Double? = null,
) {
    val timeInBedMin: Double get() = Duration.between(start, end).seconds / 60.0
    val hasStages: Boolean get() = stages.isNotEmpty()
    val hasArchitecture: Boolean get() = stages.any { it.type == StageType.DEEP || it.type == StageType.REM }

    private val sorted by lazy { stages.sortedBy { it.start } }
    private val onset: Instant? by lazy { sorted.firstOrNull { it.asleep }?.start }
    private val finalWake: Instant? by lazy { sorted.lastOrNull { it.asleep }?.end }

    val totalSleepMin: Double
        get() = if (!hasStages) timeInBedMin else sorted.filter { it.asleep }.sumOf { it.minutes }

    val latencyMin: Double?
        get() = onset?.let { Duration.between(start, it).seconds / 60.0 }?.coerceAtLeast(0.0)

    val efficiency: Double?
        get() = if (!hasStages || timeInBedMin <= 0) null else (totalSleepMin / timeInBedMin * 100).coerceIn(0.0, 100.0)

    /** Veglia dopo l'addormentamento (fino all'ultimo episodio di sonno). */
    val wasoMin: Double?
        get() {
            val o = onset ?: return null; val f = finalWake ?: return null
            return sorted.filter { !it.asleep && it.start >= o && it.end <= f }.sumOf { it.minutes }
        }

    /** Risvegli > 5 minuti (segmenti di veglia contigui accorpati). */
    val awakenings: Int?
        get() {
            val o = onset ?: return null; val f = finalWake ?: return null
            var count = 0
            var run = 0.0
            for (s in sorted.filter { it.start >= o && it.end <= f }) {
                if (!s.asleep) run += s.minutes else { if (run > 5.0) count++; run = 0.0 }
            }
            if (run > 5.0) count++
            return count
        }

    fun stagePct(t: StageType): Double? {
        if (!hasArchitecture || totalSleepMin <= 0) return null
        return sorted.filter { it.type == t }.sumOf { it.minutes } / totalSleepMin * 100
    }

    val midpoint: Instant get() = start.plusSeconds(Duration.between(start, end).seconds / 2)
}

data class Contributor(
    val key: String,
    val label: String,
    /** 0–100, null se il dato manca. */
    val score: Int?,
    val weight: Double,
    val detail: String,
)

data class ScoreResult(
    val score: Int?,
    val contributors: List<Contributor>,
    val flags: List<String> = emptyList(),
    val calibrating: Boolean = false,
    /** Baseline costruita con meno di 14 giorni: punteggio valido ma meno preciso. */
    val provisional: Boolean = false,
    /** Giorni di storico usati per la baseline (HRV o FC a riposo, il maggiore). */
    val baselineDays: Int = 0,
) {
    val band: String
        get() {
            val s = score ?: return "—"
            return when {
                s >= 85 -> "Ottimale"
                s >= 70 -> "Buono"
                s >= 50 -> "Discreto"
                else -> "Basso"
            }
        }
}

/** Interpolazione lineare a tratti: punti (x, y) con x crescente. */
fun piecewise(x: Double, vararg pts: Pair<Double, Double>): Double {
    if (x <= pts.first().first) return pts.first().second
    if (x >= pts.last().first) return pts.last().second
    for (i in 1 until pts.size) {
        val (x0, y0) = pts[i - 1]; val (x1, y1) = pts[i]
        if (x <= x1) return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
    }
    return pts.last().second
}


/** Scarto dal proprio solito in parole: entro mezza deviazione standard è «in linea». */
internal fun solito(z: Double): String = when {
    z >= 1.5 -> "molto sopra il tuo solito"
    z >= 0.5 -> "sopra il tuo solito"
    z <= -1.5 -> "molto sotto il tuo solito"
    z <= -0.5 -> "sotto il tuo solito"
    else -> "in linea con il tuo solito"
}

/** z-score rispetto alla baseline personale: valori più alti = meglio. */
fun zGood(z: Double): Double = piecewise(
    z, -2.5 to 5.0, -2.0 to 15.0, -1.0 to 45.0, -0.5 to 70.0, 0.0 to 85.0, 0.5 to 100.0,
)

private fun combine(cs: List<Contributor>): Int? {
    val avail = cs.filter { it.score != null }
    val w = avail.sumOf { it.weight }
    if (w < 0.5) return null // meno della metà del peso disponibile: punteggio non affidabile
    return (avail.sumOf { it.score!! * it.weight } / w).roundToInt().coerceIn(0, 100)
}

private fun mean(xs: List<Double>) = if (xs.isEmpty()) null else xs.average()
private fun sd(xs: List<Double>): Double? {
    if (xs.size < 2) return null
    val m = xs.average()
    return kotlin.math.sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
}
private fun f(x: Double, d: Int = 0) = String.format(java.util.Locale.ITALY, "%.${d}f", x)

// =============================================================================================
// SONNO
// =============================================================================================

object SleepScore {

    /*
     * Pesi (versione 2, ottobre 2026). Spostati verso ciò che l'orologio misura bene e che ha legami con gli esiti:
     * durata e regolarità (la regolarità predice la mortalità più della durata: Windred 2024,
     * doi:10.1093/sleep/zsad253). Ridotti i contributi che dipendono dal riconoscimento della veglia e delle fasi,
     * il punto debole dei dispositivi da polso rispetto alla polisonnografia. Restano scelte progettuali.
     */
    const val W_DURATION = 0.30
    const val W_REGULARITY = 0.25
    const val W_EFFICIENCY = 0.10
    const val W_CONTINUITY = 0.10
    const val W_LATENCY = 0.05
    const val W_ARCHITECTURE = 0.05
    const val W_HEART = 0.15

    /** Ore raccomandate: 7–9 adulti, 7–8 dai 65 anni (NSF, Hirshkowitz 2015). */
    fun targetRange(age: Double?): Pair<Double, Double> = if ((age ?: 40.0) >= 65) 7.0 to 8.0 else 7.0 to 9.0

    fun durationScore(hours: Double, age: Double?): Double {
        val (lo, hi) = targetRange(age)
        return when {
            hours < lo -> piecewise(hours, 4.0 to 0.0, lo to 100.0)
            hours <= hi -> 100.0
            else -> piecewise(hours, hi to 100.0, hi + 2.0 to 70.0, hi + 4.0 to 50.0)
        }
    }

    /** NSF: efficienza ≥ 85% = buona qualità. Ancore inferiori: scelta progettuale. */
    fun efficiencyScore(e: Double) = piecewise(e, 65.0 to 0.0, 75.0 to 50.0, 85.0 to 90.0, 92.0 to 100.0)

    /** NSF: veglia dopo l'addormentamento ≤ 20 min e ≤ 1 risveglio = buona qualità. */
    fun continuityScore(waso: Double, awakenings: Int): Double {
        val w = piecewise(waso, 20.0 to 100.0, 40.0 to 70.0, 60.0 to 40.0, 90.0 to 0.0)
        val a = piecewise(awakenings.toDouble(), 1.0 to 100.0, 2.0 to 80.0, 3.0 to 60.0, 5.0 to 20.0, 7.0 to 0.0)
        return 0.6 * w + 0.4 * a
    }

    /** NSF: addormentarsi in ≤ 30 min. Latenze < 5 min possono indicare debito di sonno. */
    fun latencyScore(l: Double) = piecewise(l, 0.0 to 80.0, 5.0 to 90.0, 10.0 to 100.0, 20.0 to 100.0, 30.0 to 90.0, 60.0 to 30.0, 90.0 to 0.0)

    /**
     * Regularity Index (SRI, Phillips 2017; Windred 2024): probabilità di trovarsi nello stesso stato
     * (sonno/veglia) alla stessa ora in giorni consecutivi, scalata da −100 a 100.
     * Usa solo coppie di giorni entrambi registrati.
     */
    fun sleepRegularityIndex(nights: List<SleepNight>, endDate: LocalDate, zone: ZoneId, days: Int = 7): Double? {
        val byDate = nights.associateBy { it.date }
        var same = 0L
        var total = 0L
        for (k in 0 until days) {
            val d1 = endDate.minusDays((k + 1).toLong())
            val d2 = endDate.minusDays(k.toLong())
            if (byDate[d1] == null || byDate[d2] == null) continue
            // finestra 24 h da mezzogiorno del giorno precedente al risveglio
            val w1 = d1.minusDays(1).atTime(12, 0).atZone(zone).toInstant()
            val w2 = d2.minusDays(1).atTime(12, 0).atZone(zone).toInstant()
            val s1 = asleepMask(nights, w1)
            val s2 = asleepMask(nights, w2)
            for (m in 0 until 1440) { total++; if (s1[m] == s2[m]) same++ }
        }
        if (total < 3 * 1440) return null
        return 200.0 * same / total - 100.0
    }

    private fun asleepMask(nights: List<SleepNight>, from: Instant): BooleanArray {
        val mask = BooleanArray(1440)
        val to = from.plusSeconds(86400)
        for (n in nights) {
            if (n.end <= from || n.start >= to) continue
            val segs = if (n.hasStages) n.stages.filter { it.asleep }.map { it.start to it.end } else listOf(n.start to n.end)
            for ((a, b) in segs) {
                val i0 = max(0L, Duration.between(from, a).toMinutes()).toInt()
                val i1 = min(1440L, Duration.between(from, b).toMinutes()).toInt()
                for (i in i0 until i1) mask[i] = true
            }
        }
        return mask
    }

    /** UK Biobank: SRI mediano 81 (IQR 73,8–86,3). Ancore della mappa: scelta progettuale. */
    fun regularityScore(sri: Double) = piecewise(sri, 50.0 to 0.0, 65.0 to 35.0, 74.0 to 60.0, 81.0 to 80.0, 87.0 to 100.0)

    /**
     * @param history notti precedenti (fino a 60), usate per baseline personali e regolarità.
     */
    fun compute(night: SleepNight, history: List<SleepNight>, age: Double?, zone: ZoneId = ZoneId.systemDefault()): ScoreResult {
        val cs = mutableListOf<Contributor>()
        val hours = night.totalSleepMin / 60.0
        val (lo, hi) = targetRange(age)

        cs += Contributor("duration", "Durata", durationScore(hours, age).roundToInt(), W_DURATION,
            "${f(hours, 1)} h di sonno (obiettivo ${f(lo, 0)}–${f(hi, 0)} h)")

        val all = (history + night).distinctBy { it.date }
        val sri = sleepRegularityIndex(all, night.date, zone)
        cs += Contributor("regularity", "Regolarità", sri?.let { regularityScore(it).roundToInt() }, W_REGULARITY,
            sri?.let { "Indice di regolarità ${f(it, 0)}/100 negli ultimi 7 giorni" } ?: "Servono almeno 3 notti consecutive registrate")

        cs += Contributor("efficiency", "Efficienza", night.efficiency?.let { efficiencyScore(it).roundToInt() }, W_EFFICIENCY,
            night.efficiency?.let { "${f(it, 0)}% del tempo a letto passato dormendo (buono ≥ 85%)" } ?: "Fasi del sonno non disponibili")

        val w = night.wasoMin; val a = night.awakenings
        cs += Contributor("continuity", "Continuità", if (w != null && a != null) continuityScore(w, a).roundToInt() else null, W_CONTINUITY,
            if (w != null && a != null) "${f(w, 0)} min svegli dopo l'addormentamento, $a risvegli > 5 min (buono ≤ 20 min, ≤ 1)" else "Fasi del sonno non disponibili")

        cs += Contributor("latency", "Addormentamento", night.latencyMin?.let { latencyScore(it).roundToInt() }, W_LATENCY,
            night.latencyMin?.let { "${f(it, 0)} min per addormentarti (buono ≤ 30)" } ?: "Non disponibile")

        // Architettura: confronto con la PROPRIA baseline (gli errori di stadiazione del dispositivo sono in parte sistematici)
        val deep = night.stagePct(StageType.DEEP); val rem = night.stagePct(StageType.REM)
        val archHist = history.filter { it.hasArchitecture }
        val archScore = if (deep != null && rem != null && archHist.size >= 7) {
            val dz = zOf(deep, archHist.mapNotNull { it.stagePct(StageType.DEEP) })
            val rz = zOf(rem, archHist.mapNotNull { it.stagePct(StageType.REM) })
            if (dz != null && rz != null) ((zGood(dz) + zGood(rz)) / 2).roundToInt() else null
        } else null
        cs += Contributor("architecture", "Profondo e REM", archScore, W_ARCHITECTURE,
            if (deep != null && rem != null) "Profondo ${f(deep, 0)}%, REM ${f(rem, 0)}% del sonno" +
                (if (archScore == null) " · baseline in costruzione (servono 7 notti)" else " · confronto con la tua media")
            else "Fasi del sonno non disponibili")

        val hrHist = history.mapNotNull { it.avgHr }
        val hrScore = night.avgHr?.let { hr -> zOf(hr, hrHist)?.let { zGood(-it).roundToInt() } }
        cs += Contributor("heart", "FC notturna", hrScore, W_HEART,
            night.avgHr?.let { hr ->
                "FC media nel sonno ${f(hr, 0)} bpm" + (mean(hrHist)?.let { " (tua media ${f(it, 0)})" } ?: "")
            } ?: "Frequenza cardiaca notturna non disponibile")

        val flags = mutableListOf<String>()
        if (!night.hasStages) flags += "Il dispositivo non ha fornito le fasi del sonno: il punteggio usa solo durata e regolarità."
        if ((night.latencyMin ?: 99.0) < 5 && hours < lo) flags += "Addormentamento molto rapido con sonno breve: possibile debito di sonno."
        return ScoreResult(combine(cs), cs, flags)
    }

    private fun zOf(x: Double, base: List<Double>): Double? {
        if (base.size < 7) return null
        val m = base.average(); val s = sd(base) ?: return null
        return if (s <= 1e-9) 0.0 else (x - m) / s
    }
}

// =============================================================================================
// PRONTEZZA
// =============================================================================================

data class DailyValue(val date: LocalDate, val value: Double)

data class ExerciseLoad(val date: LocalDate, val minutes: Double, val avgHr: Double?)

object Readiness {

    const val W_HRV = 0.30
    const val W_RHR = 0.20
    const val W_SLEEP = 0.20
    const val W_DEBT = 0.10
    const val W_RESP = 0.05
    const val W_LOAD = 0.15

    /** Bisogno di sonno di riferimento (centro dell'intervallo NSF). */
    fun sleepNeed(age: Double?) = if ((age ?: 40.0) >= 65) 7.5 else 8.0

    /**
     * TRIMP di Banister (modello fitness-fatica, Morton, Fitz-Clarke & Banister 1990):
     * minuti × ΔFC × 0,64·e^(1,92·ΔFC) (uomini) o 0,86·e^(1,67·ΔFC) (donne),
     * con ΔFC = (FCmedia − FCriposo)/(FCmax − FCriposo) e FCmax = 208 − 0,7·età (Tanaka 2001).
     */
    fun trimp(minutes: Double, avgHr: Double, restHr: Double, age: Double, male: Boolean): Double {
        val hrMax = 208 - 0.7 * age
        val r = ((avgHr - restHr) / (hrMax - restHr)).coerceIn(0.0, 1.0)
        return if (male) minutes * r * 0.64 * exp(1.92 * r) else minutes * r * 0.86 * exp(1.67 * r)
    }

    /** Fitness (τ = 42 gg) e fatica (τ = 7 gg) come medie mobili esponenziali del carico giornaliero. */
    fun fitnessFatigue(daily: Map<LocalDate, Double>, upTo: LocalDate, days: Int = 120): Pair<Double, Double> {
        var fit = 0.0; var fat = 0.0
        val kFit = 1 - exp(-1.0 / 42); val kFat = 1 - exp(-1.0 / 7)
        for (i in days downTo 1) {
            val l = daily[upTo.minusDays(i.toLong())] ?: 0.0
            fit += kFit * (l - fit); fat += kFat * (l - fat)
        }
        return fit to fat
    }

    data class Inputs(
        val date: LocalDate,
        val age: Double?,
        val male: Boolean,
        val hrv: List<DailyValue>,
        val rhr: List<DailyValue>,
        val resp: List<DailyValue>,
        val nights: List<SleepNight>,
        val lastNightScore: Int?,
        val exercises: List<ExerciseLoad>,
        /** Carico cardiaco giornaliero già stimato dai campioni FC, se disponibile. */
        val cardiacLoads: List<DailyValue> = emptyList(),
    )

    private data class Z(val today: Double, val z: Double, val base: Double, val n: Int)

    /**
     * Variabilità giorno-per-giorno "tipica" usata come prior quando lo storico personale è breve.
     * Valori di progetto, conservativi: ln(RMSSD) ≈ 0,15 (CV ~15%); FC a riposo ≈ 2,5 bpm
     * (la FC a riposo individuale è molto stabile nel breve periodo: Quer 2020, PMID 32023264).
     */
    private const val PRIOR_SD_LNRMSSD = 0.15
    private const val PRIOR_SD_RHR = 2.5
    private const val PRIOR_WEIGHT = 5.0

    /**
     * z rispetto alla baseline dei giorni D-61…D-1. Combina oggi e media 7 gg (Plews 2013).
     * Con ≥ 14 giorni la DS è solo personale. Con 3–13 giorni la DS è una media ponderata tra quella
     * personale e un prior (stima bayesiana "shrinkage"): così il punteggio è disponibile dal 3° giorno
     * di storico invece che dal 14°, ed è marcato come provvisorio.
     */
    private fun zToday(series: List<DailyValue>, date: LocalDate, log: Boolean, priorSd: Double): Z? {
        fun t(v: Double) = if (log) ln(max(v, 1e-6)) else v
        val today = series.filter { !it.date.isAfter(date) && !it.date.isBefore(date.minusDays(1)) }.maxByOrNull { it.date } ?: return null
        val base = series.filter { it.date.isBefore(today.date) && !it.date.isBefore(today.date.minusDays(61)) }.map { t(it.value) }
        if (base.size < 3) return null
        val m = base.average()
        val sPers = sd(base) ?: priorSd
        val s = if (base.size >= 14) sPers else {
            val df = (base.size - 1).toDouble()
            sqrt((df * sPers * sPers + PRIOR_WEIGHT * priorSd * priorSd) / (df + PRIOR_WEIGHT))
        }
        if (s <= 1e-9) return Z(today.value, 0.0, if (log) exp(m) else m, base.size)
        val last7 = series.filter { !it.date.isAfter(today.date) && it.date.isAfter(today.date.minusDays(7)) }.map { t(it.value) }
        val zDay = (t(today.value) - m) / s
        val z7 = if (last7.size >= 3) (last7.average() - m) / s else zDay
        return Z(today.value, 0.5 * zDay + 0.5 * z7, if (log) exp(m) else m, base.size)
    }

    fun compute(i: Inputs): ScoreResult {
        val cs = mutableListOf<Contributor>()
        val flags = mutableListOf<String>()

        val hz = zToday(i.hrv, i.date, log = true, priorSd = PRIOR_SD_LNRMSSD)
        cs += Contributor("hrv", "Variabilità cardiaca", hz?.let { zGood(it.z).roundToInt() }, W_HRV,
            hz?.let { "HRV ${f(it.today, 0)} ms, ${solito(it.z)} (di solito ${f(it.base, 0)} ms)" } ?: "Servono almeno 3 giorni di HRV")

        val rz = zToday(i.rhr, i.date, log = false, priorSd = PRIOR_SD_RHR)
        cs += Contributor("rhr", "FC a riposo", rz?.let { zGood(-it.z).roundToInt() }, W_RHR,
            rz?.let { "${f(it.today, 0)} bpm, ${solito(it.z)} (di solito ${f(it.base, 0)} bpm)" } ?: "Servono almeno 3 giorni di FC a riposo")

        cs += Contributor("sleep", "Sonno della notte", i.lastNightScore, W_SLEEP,
            i.lastNightScore?.let { "Punteggio sonno $it" } ?: "Nessuna notte registrata")

        val need = sleepNeed(i.age)
        val week = (1..7).mapNotNull { k -> i.nights.firstOrNull { it.date == i.date.minusDays((k - 1).toLong()) } }
        val debt = if (week.size >= 4) week.sumOf { max(0.0, need - it.totalSleepMin / 60.0) } * 7.0 / week.size else null
        cs += Contributor("debt", "Debito di sonno", debt?.let { piecewise(it, 0.0 to 100.0, 2.0 to 85.0, 5.0 to 50.0, 10.0 to 0.0).roundToInt() }, W_DEBT,
            debt?.let { "${f(it, 1)} h sotto le ${f(need, 1)} h a notte negli ultimi 7 giorni" } ?: "Servono almeno 4 notti nell'ultima settimana")

        val respToday = i.resp.filter { !it.date.isAfter(i.date) && !it.date.isBefore(i.date.minusDays(1)) }.maxByOrNull { it.date }
        val respBase = respToday?.let { t -> i.resp.filter { it.date.isBefore(t.date) && !it.date.isBefore(t.date.minusDays(61)) }.map { it.value } }
        val respDelta = if (respToday != null && respBase != null && respBase.size >= 5) respToday.value - respBase.average() else null
        cs += Contributor("resp", "Frequenza respiratoria", respDelta?.let { piecewise(it, 0.5 to 100.0, 1.0 to 80.0, 2.0 to 40.0, 3.0 to 0.0).roundToInt() }, W_RESP,
            respDelta?.let { "${f(respToday!!.value, 1)} atti/min (${if (it >= 0) "+" else ""}${f(it, 1)} rispetto alla tua media)" } ?: "Baseline in costruzione")

        // Carico di allenamento: preferisci il carico cardiaco calcolato dai campioni FC
        // sull'intera giornata; usa le sessioni di allenamento solo come fallback.
        val restHr = rz?.base ?: i.rhr.map { it.value }.takeIf { it.isNotEmpty() }?.average()
        val loadScore: Int?
        val loadDetail: String
        val observedCardiacLoads = i.cardiacLoads.filter { !it.date.isAfter(i.date) && it.value >= 0.0 }
        val daily: Map<LocalDate, Double> = if (observedCardiacLoads.size >= 3) {
            observedCardiacLoads.associate { it.date to it.value }
        } else if (i.age != null && restHr != null) {
            i.exercises.filter { it.avgHr != null }.groupBy { it.date }
                .mapValues { (_, l) -> l.sumOf { trimp(it.minutes, it.avgHr!!, restHr, i.age, i.male) } }
        } else emptyMap()
        if (daily.size >= 3) {
            // La serie disponibile è limitata allo storico effettivamente letto: niente giorni
            // non osservati inventati come zero prima dell'inizio dei dati.
            val first = daily.keys.minOrNull()!!
            val span = (java.time.temporal.ChronoUnit.DAYS.between(first, i.date) + 1).toInt().coerceIn(1, 120)
            val (fit, fat) = fitnessFatigue(daily, i.date.plusDays(1), days = span)
            if (fit > 1e-6) {
                val form = (fit - fat) / fit
                loadScore = piecewise(form, -1.0 to 15.0, -0.6 to 35.0, -0.3 to 65.0, -0.1 to 90.0, 0.0 to 100.0).roundToInt()
                loadDetail = "Fatica recente ${f(fat, 0)} vs carico di fondo ${f(fit, 0)} (carico cardiaco/giorno)"
            } else { loadScore = null; loadDetail = "Carico cardiaco insufficiente per stimare forma e fatica" }
        } else { loadScore = null; loadDetail = "Dati cardiaci insufficienti per stimare il carico" }
        cs += Contributor("load", "Carico cardiaco", loadScore, W_LOAD, loadDetail)

        // Segnali di allerta (combinazioni osservate prima di infezioni nei dati da wearable)
        val rhrHigh = (rz?.z ?: 0.0) >= 2.0
        val hrvLow = (hz?.z ?: 0.0) <= -1.5
        val respHigh = (respDelta ?: 0.0) >= 1.0
        var score = combine(cs)
        if (rhrHigh && (hrvLow || respHigh)) {
            flags += "FC a riposo nettamente sopra la tua norma insieme a HRV bassa o respiro più frequente: è un pattern osservato all'inizio di infezioni o con stress fisiologico elevato. Considera una giornata leggera; se compaiono sintomi rivolgiti al medico."
            score = score?.let { min(it, 45) }
        }
        val calibrating = hz == null && rz == null
        if (calibrating) score = null
        val n = max(hz?.n ?: 0, rz?.n ?: 0)
        return ScoreResult(score, cs, flags, calibrating, provisional = !calibrating && n < 14, baselineDays = n)
    }
}
