# BioEtà — metodi di calcolo

Questo documento descrive formule, unità, input e limiti di ogni stima mostrata dall'app.
Le stime sono statistiche e informative: **non sono diagnosi** e non sostituiscono il medico.
Ogni formula è coperta da test unitari in `app/src/test` (valori attesi calcolati con
un'implementazione indipendente in Python delle equazioni pubblicate).

## Registro delle modifiche agli algoritmi

| Data | Algoritmo | Versione | Modifica | Effetto |
|---|---|---|---|---|
| 2026-10-17 | PhenoAge | 2 | Costante di conversione corretta da 0,09165 a **0,090165** (valore della formula pubblicata dagli autori) | Le PhenoAge calcolate prima risultavano più alte di ~1,2–1,7 anni nell'intervallo 40–70 anni. Lo storico viene ricalcolato automaticamente dai valori grezzi salvati. |
| 2026-10-17 | Attività | 2 | Nuovo motore minuti attivi/carico (`ActivityEngine`): FC per minuto con interpolazione dei buchi ≤ 5 min, minuti stimati dagli allenamenti senza FC, unione degli intervalli, divisione alla mezzanotte, qualità del carico | I minuti attivi non sono più sottostimati quando l'orologio scrive la FC a intervalli di 2–5 minuti; gli allenamenti senza FC contano (in modo prudente) nei minuti attivi ma non nel carico. |
| 2026-10-17 | Recupero | 2 | Baseline disponibile dal 3° giorno di storico: deviazione standard stimata con prior (shrinkage) finché i giorni sono meno di 14; punteggio marcato "provvisorio" | Prima servivano 14 giorni di HRV o FC a riposo. |

## 1. Età biologica — PhenoAge (versione 2)

Riferimenti: Levine ME et al., *Aging* 2018;10:573-591, doi:10.18632/aging.101414 (PMID 29676998);
Liu Z et al., *PLoS Med* 2018;15:e1002718, doi:10.1371/journal.pmed.1002718, e correzione 2019
doi:10.1371/journal.pmed.1002760.

Input (unità del modello) e conversioni applicate dall'app:

| Biomarcatore | Unità del modello | Coefficiente | Conversione dall'unità italiana usuale |
|---|---|---|---|
| Albumina | g/L | −0,0336 | g/dL × 10 (o % elettroforesi × proteine totali) |
| Creatinina | µmol/L | 0,0095 | mg/dL × 88,42 |
| Glucosio | mmol/L | 0,1953 | mg/dL ÷ 18,016 |
| PCR (log naturale) | mg/dL | 0,0954 | mg/L ÷ 10 |
| Linfociti | % | −0,0120 | — |
| MCV | fL | 0,0268 | — |
| RDW | % | 0,3306 | — |
| Fosfatasi alcalina | U/L | 0,00188 | — |
| Globuli bianchi | 10³/µL | 0,0554 | — |
| Età | anni | 0,0804 | — |

```
xb = −19,907 + Σ coefficiente × biomarcatore
M  = 1 − exp(−exp(xb) · (exp(120·γ) − 1) / γ),   γ = 0,0076927     (rischio di mortalità a 10 anni)
PhenoAge = 141,50225 + ln(−0,00553 · ln(1 − M)) / 0,090165
```

Limiti: modello addestrato su NHANES (popolazione USA); PCR > 10 mg/L (flogosi acuta) rende il prelievo non
rappresentativo (avviso in app); PCR standard poco risolutiva nel range basso; se un biomarcatore manca la stima
non viene calcolata. La scomposizione della variazione tra due prelievi è esatta perché PhenoAge è lineare in xb.

## 2. Età cardiorespiratoria (FRIEND-I)

Riferimento: Peterman JE et al., *Mayo Clin Proc* 2020;95:255-264, doi:10.1016/j.mayocp.2019.06.013.
Età alla quale il 50° percentile di VO₂max per sesso eguaglia il VO₂max della persona; interpolazione
esponenziale tra 25 anni (49,5 / 40,6 mL/kg/min, U/D) e 75 anni (30,8 / 25,0). Input: VO₂max misurato (profilo)
o stimato dall'orologio (media 30 giorni). Limite: le stime da polso hanno errori di alcuni mL/kg/min; la stima è
mostrata come "Stimato".

## 3. eGFR — CKD-EPI 2021

Inker LA et al., *N Engl J Med* 2021;385:1737-49, doi:10.1056/NEJMoa2102953. Equazione senza coefficiente razziale.

## 4. Attività (versione 2) — `science/ActivityEngine.kt`

- **FC per minuto**: media dei campioni nel minuto; buchi ≤ 5 minuti interpolati linearmente, buchi più lunghi
  lasciati vuoti. Valori fuori 25–240 bpm scartati.
- **Intensità**: % della riserva cardiaca (Karvonen) con FCmax = 208 − 0,7·età (Tanaka 2001, PMID 11153730);
  moderata 40–59%, vigorosa ≥ 60% (ACSM, Garber 2011, PMID 21694556).
- **Minuti attivi OMS** (PMID 33239350) = moderati + 2 × vigorosi.
  - *Misurati*: minuti con FC.
  - *Stimati*: minuti dentro un allenamento senza FC; intensità dal tipo di attività secondo il Compendio 2024
    (Herrmann 2024, PMID 38242596) in modo prudente (vigorosa solo per corsa, HIIT, sport di squadra, scale,
    arrampicata, combattimento; moderata per bici, nuoto, camminata, forza, ecc.; yoga/stretching e "altro" esclusi).
  - Ogni minuto conta una volta (unione degli intervalli); un minuto con FC usa sempre la FC.
  - I minuti sono attribuiti al giorno locale in cui cadono (fuso del telefono, gestione dell'ora legale).
- **Carico cardiaco**: TRIMP di Banister per minuto (Morton 1990, PMID 2246166) sui soli minuti con FC ≥ 30% della
  riserva. Qualità: *non disponibile* senza FC, *parziale* con < 10 h di FC, *completo* altrimenti. Non è carico
  muscolare né consumo calorico.
- **Deduplicazione allenamenti**: stesso id → uno; app diverse con stessa categoria (o "altro") e sovrapposizione
  ≥ 50% della sessione più breve → si tiene la più lunga.
- **Sincronizzazione idempotente** (`data/WorkoutStore.kt`): la finestra letta (90 giorni, 30 senza permesso
  storico) sostituisce interamente la stessa finestra in archivio.

## 5. Recupero e sonno

Recupero: z-score di ln(RMSSD) e FC a riposo rispetto alla baseline personale (giorni D−61…D−1), media del giorno e
dei 7 giorni (Plews 2013), sonno, debito di sonno, frequenza respiratoria, carico (modello fitness–fatica).
Con 3–13 giorni di storico la DS è `sqrt((df·s² + k·s₀²)/(df + k))`, k = 5, s₀ = 0,15 per ln(RMSSD) e 2,5 bpm per la
FC a riposo (valori di progetto prudenti); il punteggio è marcato *provvisorio*.
Sonno: durata (AASM/SRS), efficienza, continuità, latenza, regolarità (SRI; Windred 2024), architettura e FC
notturna rispetto alla propria media. I pesi sono scelte progettuali dichiarate, non un algoritmo validato.

## 6. Fonti di dati

Unica fonte attiva: **Connessione Salute** (sola lettura). Fitbit/Google Health, Huawei Health, WHOOP, Samsung
Health, Oura ecc. sono visibili solo se l'app corrispondente scrive in Connessione Salute; l'app mostra per ogni
tipo di dato quale pacchetto lo ha scritto. Nessuna API cloud diretta è integrata (richiederebbe OAuth e invio
dei dati via Internet).
