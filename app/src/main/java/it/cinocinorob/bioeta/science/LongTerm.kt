package it.cinocinorob.bioeta.science

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/*
 * «Salute a lungo termine»: i pochi indicatori misurabili dall'orologio che nella letteratura sono associati in modo
 * robusto a mortalità e malattie cardiovascolari. A differenza dei punteggi giornalieri (recupero, sonno), che
 * nessuno ha validato su esiti clinici, qui ogni soglia viene da studi di popolazione. Tutte le associazioni sono
 * osservazionali: indicano cosa vale la pena curare, non dimostrano un effetto causale sulla singola persona.
 *
 * Fonti (verificate su PubMed, ottobre 2026):
 * - Forma cardiorespiratoria: per ogni MET in più mortalità totale RR 0,86 (0,83–0,88); la stima da dispositivo
 *   predice in modo simile al test diretto. Meta-analisi di 42 studi, 3,8 milioni di osservazioni,
 *   J Sport Health Sci 2024, doi:10.1016/j.jshs.2024.100986. Riferimento per età: FRIEND (Peterman 2020).
 * - Passi: flesso della curva a 5.000–7.000 passi/giorno; 7.000 associati a miglioramenti clinicamente rilevanti.
 *   Meta-analisi Lancet Public Health 2025, 57 studi, doi:10.1016/S2468-2667(25)00164-1.
 * - Minuti attivi: OMS 2020, 150–300 min/settimana moderati (vigorosi ×2), PMID 33239350. Conta il totale della
 *   settimana: concentrarli in 1–2 giorni è associato a benefici simili (studi «weekend warrior»).
 * - Sonno: la regolarità predice la mortalità più della durata (UK Biobank, Windred 2024, doi:10.1093/sleep/zsad253);
 *   SRI mediano 81, IQR 73,8–86,3. Durata raccomandata 7–9 h (NSF).
 * - FC a riposo: RR 1,09 per 10 bpm; rispetto alla fascia più bassa, 60–80 bpm RR 1,12 e > 80 bpm RR 1,45.
 *   Meta-analisi di 46 studi, CMAJ 2016, doi:10.1503/cmaj.150535.
 * - Brevi sforzi intensi (VILPA): ~3 sforzi al giorno di 1–2 minuti associati a −38–40% di mortalità
 *   (Stamatakis 2022, doi:10.1038/s41591-022-02100-x); associazione più debole escludendo chi era già malato
 *   (coorte USA 2026, doi:10.1186/s12966-026-01876-2). Qui è una stima da FC e cadenza: mai giudicata «bassa».
 */

enum class PillarStatus(val label: String) { GOOD("In zona buona"), MID("Migliorabile"), LOW("Da curare"), NA("Dati insufficienti") }

data class Pillar(
    val key: String,
    val title: String,
    /** Valore principale già formattato, null = non disponibile. */
    val value: String?,
    val unit: String,
    val status: PillarStatus,
    /** Giudizio in una frase semplice. */
    val verdict: String,
    /** Dettaglio: periodo, andamento, numeri di contorno. */
    val detail: String,
    /** Cosa lo migliora, in una riga. */
    val howTo: String,
    /** Cosa dice la ricerca, con la fonte. */
    val evidence: String,
    val doi: String,
    /** true = il valore è una stima indiretta (non una misura). */
    val estimate: Boolean = false,
)

data class LongTermInputs(
    val today: LocalDate,
    val age: Double?,
    val male: Boolean,
    /** VO₂max giornaliero dall'orologio (mL/kg/min). */
    val vo2: List<DailyValue>,
    /** VO₂max misurato inserito nel profilo: prevale sulla stima. */
    val vo2Manual: Double?,
    val steps: List<DailyValue>,
    val rhr: List<DailyValue>,
    /** Giorni di attività calcolati (minuti attivi, brevi sforzi). */
    val days: List<DayActivity>,
    /** Notti di sonno (per regolarità e durata). */
    val nights: List<SleepNight>,
    val zone: ZoneId,
)

object LongTerm {
    const val MIN_DAYS = 14

    private fun f(x: Double, d: Int = 0) = String.format(java.util.Locale.ITALY, "%.${d}f", x)
    private fun thousands(x: Double) = String.format(java.util.Locale.ITALY, "%,d", x.toLong())

    private fun window(s: List<DailyValue>, today: LocalDate, from: Long, to: Long) =
        s.filter { !it.date.isAfter(today.minusDays(from)) && it.date.isAfter(today.minusDays(to + 1)) }.map { it.value }

    fun compute(i: LongTermInputs): List<Pillar> = listOf(vo2(i), steps(i), active(i), sleep(i), rhr(i), shortBouts(i))

    fun vo2(i: LongTermInputs): Pillar {
        val recent = window(i.vo2, i.today, 0, 29)
        val older = window(i.vo2, i.today, 60, 119)
        val v = i.vo2Manual ?: recent.takeIf { it.isNotEmpty() }?.average()
        val ev = "Per ogni MET (3,5 mL/kg/min) in più la mortalità totale è più bassa del 14% circa; la stima dell'orologio predice quasi quanto il test in laboratorio."
        val how = "Sale con l'allenamento aerobico intenso (intervalli, salite, corsa svelta) e con la perdita di peso in eccesso."
        if (v == null || i.age == null) return Pillar("vo2", "Forma cardiorespiratoria", null, "mL/kg/min", PillarStatus.NA,
            "Serve il VO₂max", if (i.age == null) "Inserisci la data di nascita nel profilo." else "L'orologio non ha scritto il VO₂max in Connessione Salute: inseriscilo nel profilo se hai fatto un test.",
            how, ev, "10.1016/j.jshs.2024.100986")
        val met = FitnessAge.metsVsReference(v, i.age, i.male)
        val ref = FitnessAge.reference(i.age, i.male)
        val st = when { met >= 0 -> PillarStatus.GOOD; met >= -1 -> PillarStatus.MID; else -> PillarStatus.LOW }
        val verdict = when {
            met >= 1 -> "Ben sopra la media della tua età"
            met >= 0 -> "Sopra la media della tua età"
            met >= -1 -> "Poco sotto la media della tua età"
            else -> "Sotto la media della tua età"
        }
        val trend = if (i.vo2Manual == null && recent.size >= 5 && older.size >= 5) {
            val d = recent.average() - older.average()
            if (abs(d) < 0.5) " Stabile negli ultimi mesi." else " ${if (d > 0) "In salita" else "In calo"} di ${f(abs(d), 1)} rispetto a 2–4 mesi fa."
        } else ""
        val src = if (i.vo2Manual != null) "misurato (profilo)" else "stima dell'orologio, media 30 giorni"
        return Pillar("vo2", "Forma cardiorespiratoria", f(v, 1), "mL/kg/min", st, verdict,
            "${if (met >= 0) "+" else "−"}${f(abs(met), 1)} MET rispetto alla mediana per età e sesso (${f(ref, 1)}). Valore: $src.$trend",
            how, ev, "10.1016/j.jshs.2024.100986", estimate = i.vo2Manual == null)
    }

    fun steps(i: LongTermInputs): Pillar {
        val last = window(i.steps, i.today, 1, 30)
        val ev = "La curva del beneficio si appiattisce tra 5.000 e 7.000 passi al giorno; 7.000 sono già associati a miglioramenti clinicamente rilevanti (mortalità, malattie cardiovascolari, demenza, cadute)."
        val how = "Camminate brevi dopo i pasti, scale al posto dell'ascensore, telefonate in piedi."
        if (last.size < MIN_DAYS) return Pillar("steps", "Passi", null, "al giorno", PillarStatus.NA, "Servono almeno $MIN_DAYS giorni",
            "Giorni con passi negli ultimi 30: ${last.size}.", how, ev, "10.1016/S2468-2667(25)00164-1")
        val avg = last.average()
        val over = last.count { it >= Fitness.STEP_HEALTH }
        val st = when { avg >= 7000 -> PillarStatus.GOOD; avg >= 5000 -> PillarStatus.MID; else -> PillarStatus.LOW }
        val verdict = when (st) { PillarStatus.GOOD -> "Oltre la soglia di beneficio"; PillarStatus.MID -> "Vicino alla soglia di beneficio"; else -> "Sotto la soglia di beneficio" }
        return Pillar("steps", "Passi", thousands(avg), "al giorno", st, verdict,
            "Media degli ultimi ${last.size} giorni. Giorni oltre 7.000 passi: $over su ${last.size}.", how, ev, "10.1016/S2468-2667(25)00164-1")
    }

    fun active(i: LongTermInputs): Pillar {
        val d = i.days.filter { it.date.isBefore(i.today) && !it.date.isBefore(i.today.minusDays(28)) && !it.stale }
            .filter { it.hrMinutes > 0 || it.cadenceMinutes > 0 || it.estimatedShare > 0 }
        val ev = "L'OMS indica 150–300 minuti a settimana di attività moderata (un minuto vigoroso vale 2). Conta il totale della settimana: concentrarlo in 1–2 giorni è associato a benefici simili."
        val how = "Basta sommare: 3 uscite da 25 minuti di camminata svelta, oppure 2 corse da 20 minuti, arrivano a 150."
        if (d.size < MIN_DAYS) return Pillar("active", "Minuti attivi", null, "a settimana", PillarStatus.NA, "Servono almeno $MIN_DAYS giorni",
            "Giorni con dati negli ultimi 28: ${d.size}.", how, ev, "")
        val week = d.sumOf { it.activeMinutes }.toDouble() / d.size * 7
        val st = when { week >= 150 -> PillarStatus.GOOD; week >= 75 -> PillarStatus.MID; else -> PillarStatus.LOW }
        val verdict = when {
            week >= 300 -> "Oltre il livello raccomandato"
            week >= 150 -> "Nel livello raccomandato"
            week >= 75 -> "A metà strada dal livello raccomandato"
            else -> "Sotto il livello raccomandato"
        }
        val scaled = if (d.size < 28) " Calcolata sui ${d.size} giorni con dati e riportata a 7 giorni." else ""
        return Pillar("active", "Minuti attivi", f(week), "a settimana", st, verdict,
            "Media settimanale delle ultime 4 settimane (obiettivo OMS 150–300).$scaled", how, ev, "")
    }

    fun sleep(i: LongTermInputs): Pillar {
        val ns = i.nights.filter { it.date.isAfter(i.today.minusDays(29)) && !it.date.isAfter(i.today) }
        val sri = SleepScore.sleepRegularityIndex(i.nights, i.today, i.zone, days = 28)
        val ev = "Nella UK Biobank la regolarità del sonno predice la mortalità più della durata: chi ha orari più regolari ha un rischio più basso del 20–48% rispetto al quinto meno regolare."
        val how = "Stessa ora di sveglia ogni giorno, anche nel fine settimana (scarto entro 30–60 minuti); luce del mattino."
        if (ns.size < MIN_DAYS || sri == null) return Pillar("sleep", "Regolarità del sonno", null, "/100", PillarStatus.NA, "Servono almeno $MIN_DAYS notti",
            "Notti registrate negli ultimi 28 giorni: ${ns.size}.", how, ev, "10.1093/sleep/zsad253")
        val h = ns.map { it.totalSleepMin / 60.0 }.average()
        val st = when {
            sri < 73.8 || h < 6.0 -> PillarStatus.LOW
            sri >= 81 && h >= 7.0 && h <= 9.0 -> PillarStatus.GOOD
            else -> PillarStatus.MID
        }
        val verdict = when {
            sri >= 86.3 -> "Orari molto regolari"
            sri >= 81 -> "Orari più regolari della media"
            sri >= 73.8 -> "Regolarità nella media"
            else -> "Orari irregolari"
        }
        val dur = when { h < 6 -> "durata media bassa"; h < 7 -> "durata media sotto le 7 ore"; h <= 9 -> "durata nella fascia raccomandata"; else -> "durata media sopra le 9 ore" }
        val hh = h.toInt(); val mm = ((h - hh) * 60).toInt()
        return Pillar("sleep", "Regolarità del sonno", f(sri), "/100", st, verdict,
            "Indice di regolarità (SRI) delle ultime 4 settimane; nella popolazione la mediana è 81. Sonno medio ${hh} h ${"%02d".format(mm)}: $dur.",
            how, ev, "10.1093/sleep/zsad253")
    }

    fun rhr(i: LongTermInputs): Pillar {
        val recent = window(i.rhr, i.today, 0, 29)
        val older = window(i.rhr, i.today, 30, 119)
        val ev = "Ogni 10 bpm in più di FC a riposo corrispondono a +9% di mortalità; oltre 80 bpm il rischio è più alto del 45% rispetto alla fascia più bassa."
        val how = "Scende con l'allenamento aerobico regolare, il sonno sufficiente e meno alcol; sale con malattia, stress e caldo."
        if (recent.size < 7) return Pillar("rhr", "FC a riposo", null, "bpm", PillarStatus.NA, "Servono almeno 7 giorni",
            "Giorni con FC a riposo negli ultimi 30: ${recent.size}.", how, ev, "10.1503/cmaj.150535")
        val v = recent.average()
        val st = when { v < 60 -> PillarStatus.GOOD; v <= 80 -> PillarStatus.MID; else -> PillarStatus.LOW }
        val verdict = when { v < 60 -> "Nella fascia più favorevole"; v <= 80 -> "Nella fascia più comune"; else -> "Più alta della fascia comune" }
        val trend = if (older.size >= 14) {
            val d = v - older.average()
            if (abs(d) < 1.5) "Stabile rispetto ai 3 mesi prima." else "${if (d > 0) "Più alta" else "Più bassa"} di ${f(abs(d), 0)} bpm rispetto ai 3 mesi prima."
        } else "L'andamento compare con più storico."
        return Pillar("rhr", "FC a riposo", f(v), "bpm", st, verdict, "Media degli ultimi 30 giorni. $trend", how, ev, "10.1503/cmaj.150535")
    }

    fun shortBouts(i: LongTermInputs): Pillar {
        val d = i.days.filter { it.date.isBefore(i.today) && !it.date.isBefore(i.today.minusDays(28)) && !it.stale }
            .filter { it.hrMinutes >= 600 || it.cadenceMinutes > 0 }
        val ev = "Circa 3 brevi sforzi intensi al giorno (1–2 minuti: scale, camminata molto svelta, portare pesi) sono stati associati a una mortalità più bassa del 38–40% in chi non si allena. In uno studio del 2026 l'associazione si riduce escludendo chi era già malato."
        val how = "Fai le scale a passo svelto, accelera per un minuto durante le camminate, porta la spesa a piedi."
        if (d.size < MIN_DAYS) return Pillar("bouts", "Brevi sforzi intensi", null, "al giorno", PillarStatus.NA, "Servono almeno $MIN_DAYS giorni",
            "Giorni con frequenza cardiaca o passi minuto per minuto negli ultimi 28: ${d.size}.", how, ev, "10.1038/s41591-022-02100-x", estimate = true)
        val perDay = d.sumOf { it.shortBouts }.toDouble() / d.size
        val minPerDay = d.sumOf { it.shortBoutMin }.toDouble() / d.size
        // Mai «da curare»: è un'abitudine in più, utile soprattutto a chi non si allena
        val st = if (perDay >= 3) PillarStatus.GOOD else PillarStatus.MID
        val verdict = if (perDay >= 3) "Abitudine presente" else "Spazio per qualche sforzo in più"
        return Pillar("bouts", "Brevi sforzi intensi", f(perDay, 1), "al giorno", st, verdict,
            "Media delle ultime 4 settimane fuori dagli allenamenti: ${f(minPerDay, 1)} min al giorno. Stima da frequenza cardiaca e cadenza.",
            how, ev, "10.1038/s41591-022-02100-x", estimate = true)
    }
}
