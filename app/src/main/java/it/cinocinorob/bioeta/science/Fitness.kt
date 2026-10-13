package it.cinocinorob.bioeta.science

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Fitness: carico cardiaco giornaliero, minuti attivi, zone, carico obiettivo.
 *
 * - Intensità come % della riserva di frequenza cardiaca (FCR, Karvonen):
 *   molto leggera < 30%, leggera 30–39%, moderata 40–59%, vigorosa 60–89%, quasi massimale ≥ 90%
 *   (ACSM position stand, Garber 2011; PMID 21694556).
 * - FCmax = 208 − 0,7·età (Tanaka 2001; PMID 11153730).
 * - Carico = TRIMP di Banister minuto per minuto (Morton 1990; PMID 2246166), sommato sui minuti
 *   con FCR ≥ 30% (come Google, il carico parte dalla zona leggera).
 * - Minuti attivi: moderati ×1, vigorosi ×2 (equivalenza OMS 2020; PMID 33239350), obiettivo 150/settimana.
 */

data class HrSample(val time: Instant, val bpm: Double)

enum class Zone(val label: String, val lo: Double) {
    VERY_LIGHT("Molto leggera", 0.0),
    LIGHT("Leggera", 0.30),
    MODERATE("Moderata", 0.40),
    VIGOROUS("Vigorosa", 0.60),
    MAXIMAL("Quasi massimale", 0.90),
}

data class DayLoad(
    val date: LocalDate,
    val load: Double,
    val moderateMin: Int,
    val vigorousMin: Int,
    val zoneMinutes: Map<Zone, Int>,
    val coveredMinutes: Int,
) {
    /** Minuti attivi equivalenti OMS (vigorosi contano doppio). */
    val activeMinutes: Int get() = moderateMin + 2 * vigorousMin
}

data class TargetLoad(val low: Double, val high: Double, val basis: String)

enum class LoadStatus(val label: String) {
    BELOW("Sotto l'obiettivo"), IN_RANGE("Nell'obiettivo"), ABOVE("Sopra l'obiettivo"),
}

object Fitness {

    fun hrMax(age: Double) = 208 - 0.7 * age

    fun hrr(bpm: Double, rest: Double, max: Double): Double =
        ((bpm - rest) / (max - rest)).coerceIn(0.0, 1.2)

    fun zoneOf(fraction: Double): Zone = Zone.entries.last { fraction >= it.lo }

    /** TRIMP di un minuto alla frazione di FCR data. */
    fun trimpMinute(fraction: Double, male: Boolean): Double {
        val r = fraction.coerceIn(0.0, 1.0)
        return if (male) r * 0.64 * exp(1.92 * r) else r * 0.86 * exp(1.67 * r)
    }

    /** Media per minuto dei campioni di FC (riduce rumore e duplicati). */
    fun perMinute(samples: List<HrSample>): Map<Long, Double> =
        samples.groupBy { it.time.epochSecond / 60 }.mapValues { (_, l) -> l.map { it.bpm }.average() }

    fun dayLoad(date: LocalDate, samples: List<HrSample>, restHr: Double, age: Double, male: Boolean): DayLoad {
        val max = hrMax(age)
        val minutes = perMinute(samples)
        var load = 0.0; var mod = 0; var vig = 0
        val zones = Zone.entries.associateWith { 0 }.toMutableMap()
        for ((_, bpm) in minutes) {
            val f = hrr(bpm, restHr, max)
            val z = zoneOf(f)
            zones[z] = zones.getValue(z) + 1
            if (f >= Zone.LIGHT.lo) load += trimpMinute(f, male)
            when {
                f >= Zone.VIGOROUS.lo -> vig++
                f >= Zone.MODERATE.lo -> mod++
            }
        }
        return DayLoad(date, load, mod, vig, zones, minutes.size)
    }

    /**
     * Carico obiettivo di oggi: parte dal carico medio giornaliero delle ultime 4 settimane
     * (minimo ≈ quanto serve per 150 min/settimana di attività moderata) e lo modula con la prontezza.
     * È una scelta progettuale coerente con il modello fitness-fatica e con l'allenamento guidato
     * dall'HRV (Granero-Gallegos 2020, PMID 33143175), non un algoritmo validato.
     */
    fun target(history: List<DayLoad>, today: LocalDate, readiness: Int?, male: Boolean): TargetLoad {
        val past = history.filter { it.date.isBefore(today) && !it.date.isBefore(today.minusDays(28)) && it.coveredMinutes > 600 }
        // ~21 min/die al 50% di FCR ≈ 150 min/settimana moderati
        val floor = 21 * trimpMinute(0.5, male)
        val chronic = max(if (past.size >= 7) past.map { it.load }.average() else floor, floor)
        val (a, b, why) = when {
            readiness == null -> Triple(0.7, 1.1, "prontezza in calibrazione: mantieni il tuo carico abituale")
            readiness >= 80 -> Triple(0.9, 1.4, "prontezza alta: puoi spingere di più")
            readiness >= 60 -> Triple(0.7, 1.1, "prontezza buona: carico abituale")
            readiness >= 40 -> Triple(0.4, 0.8, "prontezza ridotta: meglio un carico leggero")
            else -> Triple(0.0, 0.5, "prontezza bassa: giornata di recupero")
        }
        return TargetLoad(chronic * a, chronic * b, why)
    }

    fun status(load: Double, t: TargetLoad): LoadStatus = when {
        load < t.low -> LoadStatus.BELOW
        load <= t.high -> LoadStatus.IN_RANGE
        else -> LoadStatus.ABOVE
    }

    /** Minuti attivi della settimana corrente (lunedì–domenica). */
    fun weekActiveMinutes(history: List<DayLoad>, today: LocalDate): Int {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return history.filter { !it.date.isBefore(monday) && !it.date.isAfter(today) }.sumOf { it.activeMinutes }
    }

    /** Obiettivo passi per età (Paluch 2022: beneficio fino a 6–8 mila sopra i 60 anni, 8–10 mila sotto). */
    fun stepGoal(age: Double?): Int = if ((age ?: 40.0) >= 60) 7000 else 9000

    fun zoneMinutes(samples: List<HrSample>, restHr: Double, age: Double): Map<Zone, Int> {
        val max = hrMax(age)
        val out = Zone.entries.associateWith { 0 }.toMutableMap()
        perMinute(samples).values.forEach { bpm -> val z = zoneOf(hrr(bpm, restHr, max)); out[z] = out.getValue(z) + 1 }
        return out
    }
}

// =============================================================================================
// Diario e fattori personali
// =============================================================================================

data class JournalEntry(
    val date: LocalDate,
    val mood: Int? = null,      // 1–5
    val energy: Int? = null,    // 1–5
    val tags: Set<String> = emptySet(),
    val water: Int = 0,         // bicchieri
    val note: String = "",
)

object JournalTags {
    val ALL = listOf(
        "Alcol", "Caffè dopo le 14", "Cena tardiva", "Stress", "Malattia",
        "Viaggio", "Allenamento intenso", "Schermi a letto", "Meditazione", "Sauna",
    )
}

data class TagEffect(
    val tag: String,
    val withMean: Double,
    val withoutMean: Double,
    val nWith: Int,
    val nWithout: Int,
) {
    val diff: Double get() = withMean - withoutMean
}

object Journal {
    /**
     * Confronto personale: prontezza del mattino successivo nei giorni con e senza un tag.
     * Mostrato solo con ≥ 4 giorni in ciascun gruppo. È un'associazione nei TUOI dati, non una causa.
     */
    fun tagEffects(entries: List<JournalEntry>, nextDayScore: (LocalDate) -> Int?, minN: Int = 4): List<TagEffect> {
        val scored = entries.mapNotNull { e -> nextDayScore(e.date.plusDays(1))?.let { e to it } }
        return JournalTags.ALL.mapNotNull { tag ->
            val with = scored.filter { tag in it.first.tags }.map { it.second.toDouble() }
            val without = scored.filter { tag !in it.first.tags }.map { it.second.toDouble() }
            if (with.size >= minN && without.size >= minN) TagEffect(tag, with.average(), without.average(), with.size, without.size) else null
        }.sortedBy { it.diff }
    }

    fun round(x: Double) = x.roundToInt()
}

/** Giorno locale di un istante nel fuso indicato. */
fun Instant.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = atZone(zone).toLocalDate()
