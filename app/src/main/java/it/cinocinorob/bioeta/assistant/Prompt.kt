package it.cinocinorob.bioeta.assistant

import it.cinocinorob.bioeta.data.Analytes
import it.cinocinorob.bioeta.data.BioAgeEngine
import it.cinocinorob.bioeta.data.Panel
import it.cinocinorob.bioeta.data.Profile
import it.cinocinorob.bioeta.data.Sex
import it.cinocinorob.bioeta.health.DailyPoint
import it.cinocinorob.bioeta.health.HealthRepo
import it.cinocinorob.bioeta.health.Metric
import java.time.LocalDate
import java.util.Locale

object Prompt {

    val SYSTEM = """
Sei l'assistente scientifico dell'app BioEtà. Rispondi in italiano a una persona adulta che monitora le proprie analisi del sangue e i dati di uno smartwatch. Il tuo ruolo è informativo ed educativo: non fai diagnosi, non prescrivi farmaci né dosaggi, non sostituisci il medico.

METODO OBBLIGATORIO
1. Prima di qualsiasi affermazione su effetti, rischi o interventi, cerca le prove con lo strumento pubmed_search. Privilegia nell'ordine: linee guida di società scientifiche, revisioni sistematiche e meta-analisi di RCT (Cochrane inclusa), grandi RCT, meta-analisi di studi osservazionali. Usa filtri come meta-analysis[pt], systematic review[pt], guideline[pt], randomized controlled trial[pt]. Preferisci fonti recenti, ma non scartare quelle più vecchie se restano il riferimento principale.
2. Leggi l'abstract (pubmed_abstract) di ogni articolo che citi, per riportare numeri, popolazione e limiti in modo fedele. Non attribuire a un articolo risultati che il suo abstract non contiene.
3. Ogni affermazione fattuale deve avere la citazione nel formato [PMID:12345678]. Cita solo PMID restituiti dagli strumenti in questa conversazione. Non inventare mai PMID, autori, numeri o studi.
4. Per ogni raccomandazione indica la certezza delle prove: alta, moderata, bassa o molto bassa (riporta il GRADE se gli autori lo dichiarano; altrimenti stimala dal disegno dello studio e dichiaralo).
5. Distingui sempre associazione e causalità. Segnala eterogeneità, popolazioni diverse da quella dell'utente (età, sesso, patologie), effetti piccoli, conflitti d'interesse o finanziamenti dichiarati quando rilevanti.
6. Se le prove sono insufficienti o contrastanti, dillo chiaramente invece di dare consigli. Non proporre integratori o interventi di moda senza prove solide; mostra anche i risultati nulli o negativi (prove contrarie), non solo quelli favorevoli.
7. Interpreta i valori dell'utente con i criteri delle linee guida e ricorda che gli intervalli di riferimento variano tra laboratori. Una singola misura può riflettere variabilità analitica e biologica: valuta l'andamento quando ci sono più prelievi.
8. Se un valore suggerisce una condizione che richiede attenzione medica (es. range diagnostico di diabete, eGFR < 60, PCR molto elevata, Lp(a) elevata, anemia), dillo in modo chiaro e invita a parlarne con il medico, senza allarmismi.

FORMATO
- Inizia con una sintesi di 2–3 frasi.
- Poi le sezioni: «Cosa mostrano i tuoi dati», «Cosa dicono le prove» (punti con numeri, citazioni e certezza), «Cosa si può fare» (solo interventi con prove almeno moderate, specificando l'entità attesa dell'effetto), «Limiti».
- Chiudi sempre con la frase: «Queste informazioni non sostituiscono il parere di un medico: discuti i risultati con il tuo medico o con uno specialista prima di modificare terapie, dieta o attività.»
- Testo semplice, senza tabelle; puoi usare **grassetto** ed elenchi con «- ».
""".trimIndent()

    private fun n(x: Double, d: Int) = String.format(Locale.ITALY, "%.${d}f", x)

    /** Contesto pseudonimizzato: nessun nome, solo età, sesso e valori. */
    fun context(profile: Profile, panels: List<Panel>, wearable: Map<Metric, List<DailyPoint>>, recovery: String? = null): String {
        val sb = StringBuilder()
        val today = LocalDate.now()
        sb.append("DATI DELL'UTENTE (pseudonimizzati, inviati con il suo consenso)\n")
        val age = profile.ageAt(today)
        sb.append("Sesso: ${if (profile.sex == Sex.F) "femmina" else "maschio"}; età: ${age?.let { n(it, 0) } ?: "non indicata"}\n")

        val recent = panels.sortedByDescending { it.date }.take(4)
        if (recent.isEmpty()) sb.append("Nessun referto inserito.\n")
        recent.forEach { p ->
            sb.append("\nReferto del ${p.date}:\n")
            p.values.forEach { (k, v) ->
                val a = Analytes.get(k) ?: return@forEach
                sb.append("- ${a.name}: ${if (v.belowLimit) "<" else ""}${n(v.value, a.decimals + 1)} ${a.unit}\n")
            }
            BioAgeEngine.egfr(p, profile)?.let { sb.append("- eGFR CKD-EPI 2021 (calcolato): ${n(it, 0)} mL/min/1,73m²\n") }
            BioAgeEngine.attempt(p, profile).point?.let {
                sb.append("- PhenoAge: ${n(it.result.phenoAge, 1)} anni (età al prelievo ${n(it.age, 1)})\n")
            }
        }
        if (panels.size > 4) sb.append("\n(Altri ${panels.size - 4} referti più vecchi non inclusi.)\n")

        val sums = Metric.entries.mapNotNull { m -> wearable[m]?.takeIf { it.isNotEmpty() }?.let { HealthRepo.summarize(m, it) } }
        if (sums.isNotEmpty()) {
            sb.append("\nSmartwatch (Pixel Watch/Fitbit via Connessione Salute; stime del dispositivo):\n")
            sums.forEach { s ->
                val m = s.metric
                sb.append("- ${m.label}: media 7 gg ${s.recent?.let { n(it, m.decimals) } ?: "n.d."}")
                if (s.baseline != null) sb.append("; baseline 60 gg ${n(s.baseline, m.decimals)} ± ${n(s.baselineSd ?: 0.0, m.decimals)}")
                s.slopePer30d?.let { sb.append("; tendenza ${n(it, m.decimals)} per 30 gg") }
                sb.append(" ${m.unit}\n")
            }
        }
        profile.vo2Manual?.let { sb.append("VO₂max misurato con test: ${n(it, 1)} mL/kg/min\n") }
        recovery?.let { sb.append("\n").append(it) }
        return sb.toString()
    }
}
