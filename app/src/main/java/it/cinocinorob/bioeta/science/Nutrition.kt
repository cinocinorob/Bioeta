package it.cinocinorob.bioeta.science

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.sqrt

/*
 * Nutrizione da Connessione Salute (pasti scritti da MyFitnessPal, Cronometer, Samsung Health, Fitbit, Yazio…).
 *
 * Regole sui dati:
 * - Una sola app per giorno (quella con più calorie registrate quel giorno): se due app scrivono gli stessi pasti
 *   (es. un diario alimentare sincronizzato anche con Fitbit) la somma li conterebbe due volte.
 * - Giorno «completo» = almeno 2 registrazioni e almeno 1.000 kcal: i giorni registrati a metà renderebbero le
 *   medie fuorvianti. Soglia progettuale, dichiarata.
 * - L'autoregistrazione sottostima in media l'apporto reale; i valori di sodio non comprendono il sale aggiunto.
 *
 * Riferimenti (verificati su PubMed / documenti ufficiali, ottobre 2026):
 * - Proteine: PRI EFSA 0,83 g/kg; con allenamento di forza i guadagni di massa magra si fermano attorno a
 *   1,6 g/kg/giorno (meta-regressione di 49 RCT, Morton 2018, doi:10.1136/bjsports-2017-097608).
 * - Fibre: rischio più basso di mortalità e malattie cardiovascolari tra 25 e 29 g/giorno, possibile beneficio oltre
 *   (Reynolds 2019, Lancet, doi:10.1016/S0140-6736(18)31809-9); EFSA AI 25 g.
 * - Grassi saturi < 10% dell'energia (OMS 2023). Sodio < 2.000 mg (OMS). Potassio ≥ 3.500 mg (OMS 2012; EFSA AI 3.500 mg).
 * - Caffeina: fino a 400 mg/giorno senza problemi di sicurezza per gli adulti (EFSA 2015).
 * - Acqua: AI EFSA 2,5 L/giorno di acqua totale negli uomini e 2,0 L nelle donne, di cui circa il 70–80% da bevande.
 * - Zuccheri: Connessione Salute fornisce gli zuccheri totali, non quelli aggiunti a cui si riferisce l'OMS: si mostra
 *   il valore senza giudizio.
 * - Dispendio stimato (come MacroFactor): apporto medio registrato − variazione delle riserve, con
 *   7.700 kcal per kg di variazione di peso (approssimazione valida su poche settimane). Coerente con le abitudini di
 *   registrazione della persona, quindi utile per pianificare; non è il dispendio vero se la registrazione è
 *   incompleta. Il dispendio dell'orologio ha errori individuali ampi (fino a circa ±20%: revisione ombrello
 *   2024, doi:10.1007/s40279-024-02077-2).
 */

data class Meal(
    val start: Instant,
    val origin: String,
    val mealType: Int,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val satFat: Double?,
    val fiber: Double?,
    val sugar: Double?,
    val sodiumMg: Double?,
    val potassiumMg: Double?,
    val caffeineMg: Double?,
)

data class Drink(val time: Instant, val origin: String, val liters: Double)

data class NutritionDay(
    val date: LocalDate,
    val origin: String,
    val records: Int,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val satFat: Double?,
    val fiber: Double?,
    val sugar: Double?,
    val sodiumMg: Double?,
    val potassiumMg: Double?,
    val caffeineMg: Double?,
    /** Ora dell'ultimo pasto (inizio dell'ultima registrazione). */
    val lastMeal: LocalTime?,
    /** Ora dell'ultima registrazione con caffeina > 0. */
    val lastCaffeine: LocalTime?,
    val waterL: Double?,
) {
    val complete: Boolean get() = records >= Nutrition.MIN_RECORDS && kcal >= Nutrition.MIN_KCAL
}

enum class NutStatus(val label: String) { GOOD("In linea"), MID("Migliorabile"), LOW("Da curare"), INFO("Solo informativo"), NA("Dati insufficienti") }

data class NutTarget(
    val key: String,
    val label: String,
    val value: String?,
    val unit: String,
    val status: NutStatus,
    /** Riferimento in parole (es. «25–30 g»). */
    val reference: String,
    val note: String,
)

data class Expenditure(
    /** Dispendio stimato da apporto e peso (kcal/giorno) e intervallo al 95%. */
    val kcal: Double,
    val low: Double,
    val high: Double,
    val intake: Double,
    /** Variazione di peso stimata, kg a settimana. */
    val weightPerWeek: Double,
    val days: Int,
    val weighIns: Int,
    /** Media del dispendio totale scritto dall'orologio negli stessi giorni, se c'è. */
    val watchKcal: Double?,
)

data class NutritionSummary(
    val days: List<NutritionDay>,
    val completeDays: Int,
    val window: Int,
    val targets: List<NutTarget>,
    val expenditure: Expenditure?,
    /** Mediana dell'ora dell'ultimo pasto e distanza media dal sonno (ore), se calcolabili. */
    val lastMealMedian: LocalTime?,
    val mealToSleepH: Double?,
    val lastCaffeineMedian: LocalTime?,
    val sources: List<String>,
)

object Nutrition {
    const val MIN_RECORDS = 2
    const val MIN_KCAL = 1000.0
    const val WINDOW = 28
    const val KCAL_PER_KG = 7700.0

    /** Raggruppa per giorno locale con una sola app per giorno. */
    fun days(meals: List<Meal>, drinks: List<Drink>, zone: ZoneId): List<NutritionDay> {
        val water = drinks.groupBy { it.time.atZone(zone).toLocalDate() }.mapValues { (_, l) ->
            // anche per l'acqua una sola app per giorno: quella con il volume maggiore
            l.groupBy { it.origin }.values.maxOf { g -> g.sumOf { it.liters } }
        }
        val byDay = meals.groupBy { it.start.atZone(zone).toLocalDate() }
        val dates = (byDay.keys + water.keys).toSortedSet()
        return dates.map { d ->
            val list = byDay[d].orEmpty()
            val origin = list.groupBy { it.origin }.maxByOrNull { (_, g) -> g.sumOf { it.kcal ?: 0.0 } }?.key ?: ""
            val m = list.filter { it.origin == origin }.sortedBy { it.start }
            fun sum(f: (Meal) -> Double?): Double? = m.mapNotNull(f).takeIf { it.isNotEmpty() }?.sum()
            NutritionDay(
                d, origin, m.size, sum { it.kcal } ?: 0.0, sum { it.protein } ?: 0.0, sum { it.carbs } ?: 0.0, sum { it.fat } ?: 0.0,
                sum { it.satFat }, sum { it.fiber }, sum { it.sugar }, sum { it.sodiumMg }, sum { it.potassiumMg }, sum { it.caffeineMg },
                m.lastOrNull()?.start?.atZone(zone)?.toLocalTime(),
                m.lastOrNull { (it.caffeineMg ?: 0.0) > 0 }?.start?.atZone(zone)?.toLocalTime(),
                water[d],
            )
        }
    }

    private fun f(x: Double, d: Int = 0) = String.format(java.util.Locale.ITALY, "%.${d}f", x)
    private fun thousands(x: Double) = String.format(java.util.Locale.ITALY, "%,d", x.toLong())

    /** Mediana circolare semplice di orari (gestisce il passaggio di mezzanotte riportando le ore piccole a +24 h). */
    fun medianTime(ts: List<LocalTime>): LocalTime? {
        if (ts.isEmpty()) return null
        val mins = ts.map { val m = it.hour * 60 + it.minute; if (m < 4 * 60) m + 1440 else m }.sorted()
        val med = mins[mins.size / 2] % 1440
        return LocalTime.of(med / 60, med % 60)
    }

    /**
     * Dispendio da bilancio: regressione lineare del peso nel periodo (kg/giorno) → variazione delle riserve.
     * Servono ≥ 14 giorni completi e ≥ 4 pesate distribuite su ≥ 14 giorni.
     */
    fun expenditure(days: List<NutritionDay>, weight: List<DailyValue>, from: LocalDate, to: LocalDate, watch: Map<LocalDate, Double>): Expenditure? {
        val complete = days.filter { it.complete && !it.date.isBefore(from) && !it.date.isAfter(to) }
        val w = weight.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }
        if (complete.size < 14 || w.size < 4) return null
        val span = java.time.temporal.ChronoUnit.DAYS.between(w.first().date, w.last().date)
        if (span < 14) return null
        val x = w.map { java.time.temporal.ChronoUnit.DAYS.between(from, it.date).toDouble() }
        val y = w.map { it.value }
        val mx = x.average(); val my = y.average()
        val sxx = x.sumOf { (it - mx) * (it - mx) }
        val slope = x.indices.sumOf { (x[it] - mx) * (y[it] - my) } / sxx
        val resid = x.indices.map { y[it] - (my + slope * (x[it] - mx)) }
        val se = if (w.size > 2) sqrt(resid.sumOf { it * it } / (w.size - 2) / sxx) else 0.0
        val intake = complete.map { it.kcal }.average()
        val tdee = intake - slope * KCAL_PER_KG
        val half = 1.96 * se * KCAL_PER_KG
        val watchAvg = complete.mapNotNull { watch[it.date] }.takeIf { it.size >= 7 }?.average()
        return Expenditure(tdee, tdee - half, tdee + half, intake, slope * 7, complete.size, w.size, watchAvg)
    }

    fun summarize(
        meals: List<Meal>, drinks: List<Drink>, weight: List<DailyValue>, nights: List<SleepNight>,
        watchKcal: Map<LocalDate, Double>, today: LocalDate, zone: ZoneId, male: Boolean, sources: (String) -> String,
    ): NutritionSummary {
        val from = today.minusDays(WINDOW.toLong())
        val all = days(meals, drinks, zone).filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
        val past = all.filter { it.date.isBefore(today) }
        val comp = past.filter { it.complete }
        val kg = weight.lastOrNull { !it.date.isAfter(today) }?.value
        val t = mutableListOf<NutTarget>()
        fun avg(f: (NutritionDay) -> Double?): Double? = comp.mapNotNull(f).takeIf { it.size >= 7 }?.average()
        val enough = comp.size >= 7

        // Proteine per kg
        val prot = avg { it.protein }
        t += if (prot == null) NutTarget("protein", "Proteine", null, "g/kg", NutStatus.NA, "0,8–1,6 g/kg", "Servono 7 giorni completi.")
        else if (kg == null) NutTarget("protein", "Proteine", f(prot), "g", NutStatus.INFO, "0,8–1,6 g/kg", "Registra il peso per il confronto per kg.")
        else {
            val gkg = prot / kg
            NutTarget("protein", "Proteine", f(gkg, 1), "g/kg", when { gkg >= 1.2 -> NutStatus.GOOD; gkg >= 0.83 -> NutStatus.MID; else -> NutStatus.LOW },
                "≥ 0,83 g/kg; 1,2–1,6 se ti alleni", "${f(prot)} g al giorno con ${f(kg, 1)} kg. Oltre 1,6 g/kg non si vedono guadagni ulteriori di massa magra.")
        }
        val fib = avg { it.fiber }
        t += NutTarget("fiber", "Fibre", fib?.let { f(it) }, "g", when { fib == null -> NutStatus.NA; fib >= 25 -> NutStatus.GOOD; fib >= 15 -> NutStatus.MID; else -> NutStatus.LOW },
            "25–30 g o più", "Benefici più ampi tra 25 e 29 g al giorno: legumi, cereali integrali, frutta, verdura, frutta secca.")
        val satPct = if (enough) comp.filter { it.satFat != null && it.kcal > 0 }.takeIf { it.size >= 7 }?.map { it.satFat!! * 9 / it.kcal * 100 }?.average() else null
        t += NutTarget("satfat", "Grassi saturi", satPct?.let { f(it) }, "% energia", when { satPct == null -> NutStatus.NA; satPct <= 10 -> NutStatus.GOOD; satPct <= 13 -> NutStatus.MID; else -> NutStatus.LOW },
            "< 10% dell'energia", "Sostituirli con grassi insaturi (olio d'oliva, frutta secca, pesce) riduce il colesterolo LDL.")
        val na = avg { it.sodiumMg }
        t += NutTarget("sodium", "Sodio", na?.let { thousands(it) }, "mg", when { na == null -> NutStatus.NA; na <= 2000 -> NutStatus.GOOD; na <= 3000 -> NutStatus.MID; else -> NutStatus.LOW },
            "< 2.000 mg (5 g di sale)", "Il sale aggiunto a tavola e in cottura di solito non è registrato: il valore reale è più alto.")
        val k = avg { it.potassiumMg }
        t += NutTarget("potassium", "Potassio", k?.let { thousands(it) }, "mg", when { k == null -> NutStatus.NA; k >= 3500 -> NutStatus.GOOD; k >= 2500 -> NutStatus.MID; else -> NutStatus.LOW },
            "≥ 3.500 mg", "Frutta, verdura e legumi: insieme alla riduzione del sodio abbassa la pressione.")
        val caf = avg { it.caffeineMg }
        t += NutTarget("caffeine", "Caffeina", caf?.let { f(it) }, "mg", when { caf == null -> NutStatus.NA; caf <= 400 -> NutStatus.GOOD; else -> NutStatus.MID },
            "≤ 400 mg al giorno", "Un espresso contiene circa 60–100 mg. Per il sonno conta soprattutto l'orario dell'ultima tazza.")
        val waterDays = past.mapNotNull { it.waterL }
        val water = waterDays.takeIf { it.size >= 7 }?.average()
        val wRef = if (male) 2.0 else 1.6
        t += NutTarget("water", "Acqua da bevande", water?.let { f(it, 1) }, "L", when { water == null -> NutStatus.NA; water >= wRef -> NutStatus.GOOD; water >= wRef * 0.75 -> NutStatus.MID; else -> NutStatus.LOW },
            "≈ ${f(wRef, 1)} L (EFSA)", "Il resto dell'acqua arriva dal cibo. Il fabbisogno sale con caldo e sport.")
        val sug = avg { it.sugar }
        t += NutTarget("sugar", "Zuccheri totali", sug?.let { f(it) }, "g", if (sug == null) NutStatus.NA else NutStatus.INFO,
            "nessun limite per i totali", "Il limite OMS (< 10% dell'energia) riguarda gli zuccheri aggiunti, che le app non distinguono da quelli di frutta e latte.")

        // Orari: ultimo pasto e distanza dall'inizio del sonno della notte successiva
        val nightByDate = nights.associateBy { it.date }
        val gaps = comp.mapNotNull { d ->
            val n = nightByDate[d.date.plusDays(1)] ?: return@mapNotNull null
            val lm = d.lastMeal ?: return@mapNotNull null
            val meal = d.date.atTime(lm).atZone(zone).toInstant()
            val h = java.time.Duration.between(meal, n.start).toMinutes() / 60.0
            h.takeIf { it in 0.0..12.0 }
        }
        return NutritionSummary(
            all, comp.size, WINDOW, t,
            expenditure(past, weight, from, today.minusDays(1), watchKcal),
            medianTime(comp.mapNotNull { it.lastMeal }), gaps.takeIf { it.size >= 7 }?.average(),
            medianTime(past.mapNotNull { it.lastCaffeine }),
            all.map { it.origin }.filter { it.isNotEmpty() }.distinct().map(sources),
        )
    }

    /** Distanza in ore tra l'ultimo pasto del giorno [d] e l'inizio del sonno della notte successiva. */
    fun mealToSleep(d: NutritionDay, next: SleepNight?, zone: ZoneId): Double? {
        val lm = d.lastMeal ?: return null
        val n = next ?: return null
        val h = java.time.Duration.between(d.date.atTime(lm).atZone(zone).toInstant(), n.start).toMinutes() / 60.0
        return h.takeIf { it in 0.0..12.0 }
    }

    fun fmtTime(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)
}
