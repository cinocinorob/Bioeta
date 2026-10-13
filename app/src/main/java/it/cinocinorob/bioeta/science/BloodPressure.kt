package it.cinocinorob.bioeta.science

import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.sqrt

data class BpReading(
    val id: String = UUID.randomUUID().toString(),
    val time: LocalDateTime,
    val sys: Int,
    val dia: Int,
    val pulse: Int? = null,
    val arm: String = "",
    val note: String = "",
) {
    val morning: Boolean get() = time.hour in 4..11
}

enum class BpCategory(val label: String, val short: String) {
    NON_ELEVATED("Non elevata", "Non elevata"),
    ELEVATED("Elevata", "Elevata"),
    HYPERTENSION("Nel range dell'ipertensione", "Ipertensione"),
    HIGH("Marcatamente elevata", "Alta"),
    SEVERE("Molto elevata", "Molto alta"),
}

data class BpSummary(
    val n: Int,
    val days: Int,
    val meanSys: Double,
    val meanDia: Double,
    val meanPulse: Double?,
    val sdSys: Double?,
    val morning: Pair<Double, Double>?,
    val evening: Pair<Double, Double>?,
    val category: BpCategory,
    /** true se rispetta il protocollo minimo (≥ 3 giorni e ≥ 12 misure, primo giorno escluso). */
    val protocolComplete: Boolean,
    /** mmHg/mese su 90 giorni (≥ 10 misure in ≥ 21 giorni). */
    val trendSys: Double?,
    val trendDia: Double?,
)

/**
 * Pressione misurata a domicilio.
 * Soglie domiciliari ESC 2024 (PMID 39210715): non elevata < 120/70, elevata 120–134/70–84,
 * ipertensione ≥ 135/85 (equivalente domiciliare di 140/90 in ambulatorio).
 * Protocollo: almeno 3 giorni (meglio 7) con 2 misure mattino e sera, escludendo il primo giorno
 * (ESH 2023, PMID 37345492).
 */
object BloodPressure {

    fun classify(sys: Double, dia: Double): BpCategory = when {
        sys >= 180 || dia >= 110 -> BpCategory.SEVERE
        sys >= 160 || dia >= 100 -> BpCategory.HIGH
        sys >= 135 || dia >= 85 -> BpCategory.HYPERTENSION
        sys >= 120 || dia >= 70 -> BpCategory.ELEVATED
        else -> BpCategory.NON_ELEVATED
    }

    /** Lettura singola molto alta: va ripetuta; con sintomi è un'urgenza. */
    fun isUrgent(r: BpReading) = r.sys >= 180 || r.dia >= 110

    fun summarize(all: List<BpReading>, windowDays: Long = 7): BpSummary? {
        if (all.isEmpty()) return null
        val last = all.maxOf { it.time }
        val from = last.toLocalDate().minusDays(windowDays - 1)
        var win = all.filter { !it.time.toLocalDate().isBefore(from) }
        val dates = win.map { it.time.toLocalDate() }.distinct().sorted()
        // ESH: si scarta il primo giorno (effetto di adattamento), se restano almeno 3 giorni
        if (dates.size >= 4) win = win.filter { it.time.toLocalDate() != dates.first() }
        if (win.isEmpty()) return null
        val ms = win.map { it.sys.toDouble() }
        val md = win.map { it.dia.toDouble() }
        val mSys = ms.average(); val mDia = md.average()
        val mo = win.filter { it.morning }
        val ev = win.filter { !it.morning }
        val days = win.map { it.time.toLocalDate() }.distinct().size

        val t90 = all.filter { !it.time.isBefore(last.minusDays(90)) }.sortedBy { it.time }
        val span = if (t90.size >= 2) java.time.Duration.between(t90.first().time, t90.last().time).toDays() else 0
        val (ts, td) = if (t90.size >= 10 && span >= 21) {
            val x = t90.map { java.time.Duration.between(t90.first().time, it.time).toMinutes() / (60.0 * 24 * 30.44) }
            Stats.olsSlope(x, t90.map { it.sys.toDouble() }) to Stats.olsSlope(x, t90.map { it.dia.toDouble() })
        } else null to null

        return BpSummary(
            n = win.size,
            days = days,
            meanSys = mSys,
            meanDia = mDia,
            meanPulse = win.mapNotNull { it.pulse?.toDouble() }.takeIf { it.isNotEmpty() }?.average(),
            sdSys = if (ms.size >= 3) sqrt(ms.sumOf { (it - mSys) * (it - mSys) } / (ms.size - 1)) else null,
            morning = if (mo.isNotEmpty()) mo.map { it.sys.toDouble() }.average() to mo.map { it.dia.toDouble() }.average() else null,
            evening = if (ev.isNotEmpty()) ev.map { it.sys.toDouble() }.average() to ev.map { it.dia.toDouble() }.average() else null,
            category = classify(mSys, mDia),
            protocolComplete = days >= 3 && win.size >= 12,
            trendSys = ts,
            trendDia = td,
        )
    }

    fun plausible(sys: Int, dia: Int, pulse: Int?): String? = when {
        sys !in 60..280 -> "Sistolica fuori dall'intervallo plausibile (60–280)"
        dia !in 30..180 -> "Diastolica fuori dall'intervallo plausibile (30–180)"
        dia >= sys -> "La diastolica deve essere inferiore alla sistolica"
        pulse != null && pulse !in 25..250 -> "Frequenza fuori dall'intervallo plausibile"
        else -> null
    }
}
