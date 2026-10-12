package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.cinocinorob.bioeta.AppViewModel
import it.cinocinorob.bioeta.data.BioAgeEngine
import it.cinocinorob.bioeta.data.Sex
import it.cinocinorob.bioeta.health.Metric
import it.cinocinorob.bioeta.science.FitnessAge
import it.cinocinorob.bioeta.science.PhenoAge
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun AgeScreen(
    vm: AppViewModel,
    modifier: Modifier,
    onOpenInfo: () -> Unit,
    onGoLabs: () -> Unit,
    onGoProfile: () -> Unit,
) {
    val profile = vm.profile
    val series = vm.phenoSeries
    val today = LocalDate.now()
    val chronoNow = profile.ageAt(today)

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (profile.birthDate == null) item {
            SectionCard("Imposta la data di nascita") {
                Text("Serve per calcolare l'età anagrafica a ogni prelievo e il confronto con l'età biologica.")
                Button(onClick = onGoProfile) { Text("Vai al profilo") }
            }
        }

        // ---------- PhenoAge ----------
        item {
            val last = series.lastOrNull()
            SectionCard("Età biologica ematica (PhenoAge)") {
                if (last == null) {
                    val latestPanel = vm.panels.maxByOrNull { it.date }
                    if (latestPanel == null) {
                        Text("Nessun referto inserito. Servono 9 valori: albumina, creatinina, glucosio, PCR, linfociti %, MCV, RDW, fosfatasi alcalina, globuli bianchi.")
                    } else {
                        val att = BioAgeEngine.attempt(latestPanel, profile)
                        Text("Ultimo referto (${latestPanel.date.itDate()}): mancano")
                        Muted(att.missing.joinToString(", "))
                    }
                    Button(onClick = onGoLabs) { Text("Inserisci analisi") }
                } else {
                    val delta = last.result.phenoAge - last.age
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(last.result.phenoAge.f(1), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        Column {
                            Text("anni", style = MaterialTheme.typography.titleMedium)
                            Muted("prelievo del ${last.panel.date.itDate()}")
                        }
                    }
                    Text("Età anagrafica al prelievo: ${last.age.f(1)} anni")
                    Text(
                        "Differenza: ${delta.fs(1)} anni " +
                            if (delta < 0) "(profilo ematico \"più giovane\")" else "(profilo ematico \"più vecchio\")",
                        fontWeight = FontWeight.SemiBold,
                        color = if (delta < 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Muted(
                        "Rischio di mortalità a 10 anni stimato dal modello: ${(last.result.mortality10y * 100).f(1)}% " +
                            "(calibrato sulla popolazione USA NHANES; valore relativo, non una previsione individuale)."
                    )
                    Muted("Nella validazione NHANES IV, +1 anno di PhenoAge a parità di età anagrafica è associato a HR 1,09 per mortalità per tutte le cause.")
                    last.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        // ---------- Storico ----------
        if (series.size >= 2) item {
            SectionCard("Andamento") {
                LineChart(
                    series = listOf(
                        ChartSeries(series.map { it.panel.date.toEpochDay().toDouble() to it.age }, MaterialTheme.colorScheme.outline, dots = false, dashed = true, width = 1.5f),
                        ChartSeries(series.map { it.panel.date.toEpochDay().toDouble() to it.result.phenoAge }, MaterialTheme.colorScheme.primary),
                    ),
                    yDecimals = 0,
                    xLabel = ::epochLabel,
                )
                Muted("Linea continua: PhenoAge. Tratteggio: età anagrafica.")
                val pace = BioAgeEngine.pace(series)
                if (pace != null) {
                    Text("Ritmo stimato: ${pace.f(2)} anni biologici per anno di calendario", fontWeight = FontWeight.SemiBold)
                    Muted("Pendenza OLS di PhenoAge sull'età (≥3 prelievi in ≥12 mesi). <1 = invecchiamento più lento della media del campione di riferimento. Con pochi punti l'incertezza è ampia.")
                } else {
                    Muted("Il ritmo di invecchiamento richiede almeno 3 prelievi distribuiti su 12 mesi o più.")
                }
            }
        }

        // ---------- Scomposizione ----------
        if (series.size >= 2) item {
            val a = series[series.size - 2]
            val b = series.last()
            val contribs = PhenoAge.contributions(a.input, b.input).sortedByDescending { abs(it.years) }
            val actual = b.result.phenoAge - a.result.phenoAge
            val linear = contribs.sumOf { it.years }
            SectionCard("Cosa è cambiato tra ${a.panel.date.itDate()} e ${b.panel.date.itDate()}") {
                Text("Variazione PhenoAge: ${actual.fs(1)} anni", fontWeight = FontWeight.SemiBold)
                contribs.forEach { c ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c.label)
                        Text(
                            c.years.fs(2),
                            color = when {
                                abs(c.years) < 0.05 -> MaterialTheme.colorScheme.onSurfaceVariant
                                c.years > 0 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
                Muted("Residuo non lineare: ${(actual - linear).fs(2)} anni. Scomposizione lineare (ΔPhenoAge ≈ Σ βᵢΔxᵢ / 0,0917). Variazioni piccole possono rientrare nella variabilità analitica e biologica intra-individuale, in particolare per PCR e globuli bianchi.")
            }
        }

        // ---------- Fitness age ----------
        item {
            val male = profile.sex == Sex.M
            val wearVo2 = vm.wearable[Metric.VO2]
                ?.filter { it.date.isAfter(today.minusDays(30)) }
                ?.map { it.value }
                ?.takeIf { it.isNotEmpty() }
                ?.average()
            val vo2 = profile.vo2Manual ?: wearVo2
            val source = when {
                profile.vo2Manual != null -> "inserito manualmente"
                wearVo2 != null -> "media 30 giorni da Connessione Salute (stima dell'orologio)"
                else -> null
            }
            SectionCard("Età cardiorespiratoria (VO₂max)") {
                if (vo2 == null) {
                    Text("Nessun VO₂max disponibile. Puoi collegare Connessione Salute (scheda Wearable) o inserire un valore misurato nel profilo.")
                } else {
                    val raw = FitnessAge.fitnessAgeRaw(vo2, male)
                    val fa = FitnessAge.fitnessAge(vo2, male)
                    val label = when {
                        raw < 20 -> "< 20"
                        raw > 80 -> "> 80"
                        else -> fa.f(0)
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(label, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text("anni", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("VO₂max ${vo2.f(1)} mL/kg/min ($source)")
                    if (chronoNow != null) {
                        val ref = FitnessAge.reference(chronoNow, male)
                        val mets = FitnessAge.metsVsReference(vo2, chronoNow, male)
                        Text("Mediana di riferimento alla tua età: ${ref.f(1)} mL/kg/min → ${mets.fs(1)} MET")
                    }
                    Muted("Riferimento: 50° percentile FRIEND-I (test massimali), interpolazione esponenziale tra 25 e 75 anni. Le stime da smartwatch hanno errori tipici di diversi mL/kg/min rispetto al test cardiopolmonare: interpretare la tendenza più del valore assoluto.")
                }
            }
        }

        item {
            SectionCard("Perché non un unico \"punteggio\" come Oura/Ultrahuman") {
                Text("Gli algoritmi commerciali che fondono sensori e biomarcatori in un'unica età non hanno coefficienti pubblicati né validazione peer-reviewed sugli esiti. Qui ogni indicatore resta separato e tracciabile alla sua fonte.")
                OutlinedButton(onClick = onOpenInfo) { Text("Metodo, fonti e limiti") }
            }
        }
    }
}
