# BioEtà — metodi di calcolo

Questo documento descrive formule, unità, input e limiti di ogni stima mostrata dall'app.
Le stime sono statistiche e informative: **non sono diagnosi** e non sostituiscono il medico.
Ogni formula è coperta da test unitari in `app/src/test` (valori attesi calcolati con
un'implementazione indipendente in Python delle equazioni pubblicate).

## Registro delle modifiche agli algoritmi

- **Ottobre 2026 — misure notturne**: HRV, respirazione e SpO₂ sono attribuite al giorno del risveglio della sessione
  di sonno che le contiene (± 1 h), non alla data di calendario dell'istante: un'app che le data a inizio sonno
  (prima di mezzanotte) le spostava sul giorno precedente rispetto a sonno e FC a riposo.

- **Ottobre 2026 — sonno v2**: pesi durata 0,30, regolarità 0,25 (era 0,15), efficienza 0,10 (0,15), continuità 0,10
  (0,15), addormentamento 0,05, profondo/REM 0,05 (0,10), FC notturna 0,15 (0,10). Più peso a ciò che l'orologio
  misura bene e che è legato agli esiti; meno a veglia e fasi, il punto debole dei dispositivi da polso.
- **Ottobre 2026 — carico 7/28 giorni**: lo stato «Picco» diventa «Molto sopra il solito», senza affermazioni sul
  rischio di infortunio (il rapporto non ha valore predittivo dimostrato: doi:10.1007/s40279-020-01378-6).
- **Ottobre 2026 — attività v5**: brevi sforzi intensi (vedi «Salute a lungo termine»).

| Data | Algoritmo | Versione | Modifica | Effetto |
|---|---|---|---|---|
| 2026-10-17 | PhenoAge | 2 | Costante di conversione corretta da 0,09165 a **0,090165** (valore della formula pubblicata dagli autori) | Le PhenoAge calcolate prima risultavano più alte di ~1,2–1,7 anni nell'intervallo 40–70 anni. Lo storico viene ricalcolato automaticamente dai valori grezzi salvati. |
| 2026-10-17 | Attività | 2 | Nuovo motore minuti attivi/carico (`ActivityEngine`): FC per minuto con interpolazione dei buchi ≤ 5 min, minuti stimati dagli allenamenti senza FC, unione degli intervalli, divisione alla mezzanotte, qualità del carico | I minuti attivi non sono più sottostimati quando l'orologio scrive la FC a intervalli di 2–5 minuti; gli allenamenti senza FC contano (in modo prudente) nei minuti attivi ma non nel carico. |
| 2026-10-17 | Recupero | 2 | Baseline disponibile dal 3° giorno di storico: deviazione standard stimata con prior (shrinkage) finché i giorni sono meno di 14; punteggio marcato "provvisorio" | Prima servivano 14 giorni di HRV o FC a riposo. |
| 2026-10-18 | Lettura dati | — | Finestre di lettura da Connessione Salute allineate alla mezzanotte locale; il primo giorno (o la prima notte) di ogni finestra, potenzialmente incompleto, viene scartato; FC a riposo di riferimento per carico/minuti attivi stabilizzata (cambia solo se la media si sposta di ≥ 2 bpm) | Prima la lettura veloce partiva da «adesso − N giorni» e sovrascriveva il giorno più vecchio con dati troncati, diversi a seconda dell'ora di apertura: allenamenti, notti e punteggi dei giorni recenti cambiavano tra un'apertura e l'altra. |
| 2026-10-18 | Parametri vitali / Radar malessere | 1 | Nuovo: intervallo personale robusto (mediana ± 2·MAD×1,4826, notti da −30 a −3, minimo 7) con DS minime per segnale; livelli Nessun segno / Segni lievi / Segni marcati | Sostituisce la vecchia scheda Parametri vitali (media ± 2 DS dei 30 giorni, ultima notte inclusa nella finestra). |
| 2026-10-18 | Carico percepito | 1 | Nuovo: session‑RPE (voto 0–10 × minuti), media di 4 settimane, monotonia e tensione (Foster 1998, 2001) | Gli allenamenti senza FC hanno finalmente un carico misurato (soggettivo). |
| 2026-10-18 | Registro punteggi | 1 | Ogni variazione ≥ 2 punti di un punteggio già visto di un giorno passato viene registrata con il motivo (dati d'ingresso confrontati) | Nessun valore cambia più «in silenzio». |

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

## 4. Attività (versione 4) — `science/ActivityEngine.kt`

- **Fonti multiple di FC** (riconciliazione, come Vora o la priorità delle app di Connessione Salute): non si fa
  la media tra app diverse. Fonte principale = quella scelta dall'utente (Attività → Minuti attivi → Fonte) o, in
  automatico, quella con più minuti di FC nel giorno; le altre coprono solo i minuti scoperti. Motivo: un anello
  misura di giorno soprattutto a riposo e, mediato con l'orologio, abbassava la FC durante una camminata.
- **FC per minuto**: per ciascuna fonte, media dei campioni nel minuto; buchi ≤ 5 minuti interpolati linearmente
  dentro la stessa fonte, buchi più lunghi lasciati vuoti. Valori fuori 25–240 bpm scartati.
- **Cadenza dei passi**: record ≤ 10 minuti, ripartiti per minuto; tra app diverse il massimo; oltre 250 passi/min
  il minuto è scartato (record sovrapposti). Soglie 100 e 130 passi/min (CADENCE-Adults, Tudor-Locke 2019/2020);
  oltre 60 anni solo la soglia moderata (Tudor-Locke 2021).
- **Intensità**: % della riserva cardiaca (Karvonen) con FCmax = 208 − 0,7·età (Tanaka 2001, PMID 11153730);
  moderata 40–59%, vigorosa ≥ 60% (ACSM, Garber 2011, PMID 21694556).
- **Minuti attivi OMS** (PMID 33239350) = moderati + 2 × vigorosi.
  - *Misurati*: minuti con FC o con cadenza; per ogni minuto conta la classe più alta tra le due. La
    scomposizione mostrata (da FC, da passi, stimati) somma sempre al totale; la parte «da passi» è solo
    l'incremento che la cadenza aggiunge rispetto alla FC.
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
- **Storico e cache**: 35 giorni. Un giorno in cache è riusato solo se calcolato dopo la sua fine (+6 h con FC
  per ≥ 10 h, +48 h altrimenti) e senza allenamenti; prima un giorno calcolato a metà giornata poteva restare in
  cache senza i minuti del pomeriggio. Letture a blocchi di 7 giorni (pagina da 5000 record, due nuovi tentativi
  con pausa) per ridurre le richieste a Connessione Salute, che oltre un limite le rifiuta per un po'. Un blocco
  non letto mantiene il valore precedente, segnato come non aggiornato, invece di diventare zero.
- **Sincronizzazione idempotente** (`data/WorkoutStore.kt`): la finestra letta (90 giorni, 30 senza permesso
  storico) sostituisce interamente la stessa finestra in archivio.

## Salute a lungo termine (versione 1) — `science/LongTerm.kt`

Indicatori con soglie dalla letteratura (associazioni osservazionali; non stime di rischio individuale):

| Indicatore | Periodo | Zona buona | Migliorabile | Da curare | Fonte |
|---|---|---|---|---|---|
| VO₂max | media 30 gg (o valore misurato) | ≥ mediana FRIEND per età/sesso | fino a −1 MET | oltre −1 MET | RR 0,86 per MET, doi:10.1016/j.jshs.2024.100986 |
| Passi | media 30 gg | ≥ 7.000 | 5.000–6.999 | < 5.000 | doi:10.1016/S2468-2667(25)00164-1 |
| Minuti attivi | media settimanale 4 settimane | ≥ 150 | 75–149 | < 75 | OMS 2020, PMID 33239350 |
| Sonno | SRI 28 gg + durata media | SRI ≥ 81 e 7–9 h | altrimenti | SRI < 73,8 o < 6 h | doi:10.1093/sleep/zsad253 |
| FC a riposo | media 30 gg | < 60 bpm | 60–80 | > 80 | doi:10.1503/cmaj.150535 |
| Brevi sforzi intensi (stima) | media 4 settimane | ≥ 3 al giorno | < 3 (mai «da curare») | — | doi:10.1038/s41591-022-02100-x |

Servono almeno 14 giorni di dati (7 per la FC a riposo). I minuti attivi sono un totale settimanale: concentrarli
in pochi giorni non penalizza. I brevi sforzi sono sequenze vigorose di 1–2 minuti fuori dagli allenamenti, da FC
(≥ 60% della riserva) o cadenza (≥ 130 passi/min): un'approssimazione del metodo accelerometrico dello studio
originale; l'associazione si attenua escludendo chi era già malato (doi:10.1186/s12966-026-01876-2).

## Alimentazione (versione 1) — `science/Nutrition.kt`

Dati letti da Connessione Salute (NutritionRecord, HydrationRecord); BioEtà non registra pasti.
- Una sola app per giorno (quella con più kcal) per non sommare due volte gli stessi pasti; idem per l'acqua.
- Giorno completo = ≥ 2 registrazioni e ≥ 1.000 kcal (scelta progettuale); medie solo sui giorni completi degli
  ultimi 28, con almeno 7 giorni.
- Riferimenti: proteine ≥ 0,83 g/kg (EFSA), 1,2–1,6 g/kg con allenamento (oltre ~1,6 nessun guadagno ulteriore di
  massa magra: doi:10.1136/bjsports-2017-097608); fibre ≥ 25 g (beneficio massimo osservato 25–29 g:
  doi:10.1016/S0140-6736(18)31809-9); grassi saturi < 10% dell'energia, sodio < 2.000 mg (OMS); potassio ≥ 3.500 mg;
  caffeina ≤ 400 mg (EFSA 2015); acqua da bevande ≈ 2,0 L uomini / 1,6 L donne (EFSA, 80% di 2,5/2,0 L). Zuccheri:
  solo informativi (le app non distinguono gli zuccheri aggiunti).
- Dispendio stimato: media kcal registrate − pendenza del peso (regressione lineare, kg/giorno) × 7.700 kcal/kg;
  intervallo ± 1,96 × errore standard della pendenza × 7.700. Servono ≥ 14 giorni completi e ≥ 4 pesate su ≥ 14 giorni.
  Confrontato con la media del dispendio totale dell'orologio (errori individuali ampi: doi:10.1007/s40279-024-02077-2).
- Scoperte: «ultimo pasto a meno di 3 h dal sonno», «caffeina dopo le 14 (dai pasti)», «fibre ≥ 25 g»; esiti della
  notte successiva. Sull'orario della cena la letteratura è ancora scarsa (nel 2026 esiste solo il protocollo di una
  revisione sistematica, doi:10.11124/JBIES-25-00257): per questo è trattato solo come confronto personale.

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


## Radar malessere e parametri vitali notturni (versione 1)

Riferimenti: Alavi A et al., *Nat Med* 2022;28:175-184, doi:10.1038/s41591-021-01593-2 (PMID 34845389);
Mishra T et al., *Nat Biomed Eng* 2020;4:1208-1220, doi:10.1038/s41551-020-00640-6 (PMID 33208926).

- Segnali: FC a riposo, FC media nel sonno, HRV (RMSSD, scala logaritmica), respirazione, SpO₂, scarto della
  temperatura cutanea (solo se l'orologio la scrive in Connessione Salute).
- Baseline per ogni segnale: notti da −30 a −3 giorni (minimo 7). Centro = mediana; scala = MAD × 1,4826 con
  minimo per segnale: FC 1,5 bpm (così +3 bpm = 2 DS, il margine di NightSignal), ln‑HRV 0,10, respirazione
  0,4 atti/min, SpO₂ 0,7 punti, temperatura 0,15 °C.
- Un segnale «preoccupa» se |z| ≥ 2 nella direzione tipica del malessere (FC, respirazione, temperatura in alto;
  HRV e SpO₂ in basso). FC a riposo e FC nel sonno contano come un solo segnale.
- Segni lievi: 2 segnali, oppure FC alta anche la notte precedente. Segni marcati: ≥ 3 segnali, oppure FC alta
  per due notti + un altro segnale.
- Contesto mostrato insieme all'avviso: carico cardiaco di ieri ≥ 1,5 × mediana dei 28 giorni; «Alcol» o
  «Stress» nel diario di ieri.
- Prestazioni: non validate da BioEtà. Nello studio di Alavi (sistema basato sulla FC notturna) avvisi nell'80%
  degli infetti, mediana 3 giorni prima dei sintomi; avvisi anche con altre infezioni, stress, alcol, viaggi.

## Carico percepito — session‑RPE (versione 1)

Riferimenti: Foster C et al., *J Strength Cond Res* 2001;15:109-115 (PMID 11708692); Foster C, *Med Sci Sports
Exerc* 1998;30:1164-1168, doi:10.1097/00005768-199807000-00023 (PMID 9662690); Haddad M et al., *Front Neurosci*
2017;11:612, doi:10.3389/fnins.2017.00612 (PMID 29163016).

- Carico = voto CR‑10 (0–10) × durata in minuti (UA).
- Settimana = somma degli ultimi 7 giorni; media di 4 settimane = somma dei 28 giorni / 4 (dopo 14 giorni di voti).
- Monotonia = media / DS (campionaria) del carico giornaliero dei 7 giorni, giorni senza allenamento = 0;
  tensione = carico settimanale × monotonia.

## Umore: andamento e associazioni (versione 1)

- Scala del diario 1–5 (Pessimo … Ottimo). Linea = media mobile di 7 giorni, solo con almeno 3 registrazioni
  nella finestra.
- Associazioni con ore di sonno della notte precedente, HRV del mattino, Recupero e minuti attivi del giorno
  prima: correlazione per ranghi di Spearman (ranghi medi per i pari merito), IC 95% con trasformazione di
  Fisher ed errore standard √(1,06/(n−3)) (Fieller, Hartley & Pearson 1957); mostrate da 14 coppie.
- «Nessuna associazione chiara» se l'IC comprende lo zero; altrimenti debole (|ρ| < 0,3), moderata (< 0,5), forte.
- Limiti: una sola persona, giorni autocorrelati (IC reale più largo), nessuna direzione causale.

## Scoperte: abitudini ed esiti (versione 1)

Ispirazione: Oura «Personal discoveries», WHOOP «Journal Impact». Regole proprie, dichiarate qui.

- Esposizioni del giorno D: etichette del diario; dall'orologio: allenamento, allenamento serale (fine ≥ 19:00),
  carico cardiaco ≥ 75° percentile personale, passi sopra la mediana personale, a letto ≥ 45 min oltre la
  mediana personale.
- Esiti della notte D→D+1 e del mattino D+1: HRV (scala log, effetto in %), FC a riposo, sonno totale, sonno
  profondo, efficienza, Recupero, umore.
- Ogni esito → scarto dalla mediana dei 28 giorni precedenti (≥ 7 valori): rimuove tendenze lente.
- Etichette del diario: «no» solo nei giorni compilati (i giorni non compilati sono sconosciuti).
- Minimo 5 giorni con e 5 senza negli ultimi 120.
- Effetto = differenza delle medie; IC 95% bootstrap percentile (2.000, seme fisso); p da 5.000 permutazioni.
- Correzione di Benjamini‑Hochberg (FDR 10%) su tutte le coppie testate.
- Soglie di rilevanza: HRV 3%, FC 1 bpm, sonno 10 min, profondo 5 min, efficienza 1,5 punti, Recupero 3 punti,
  umore 0,3. «Confermata» = q ≤ 0,10 e |effetto| ≥ soglia; «Indizio» = p < 0,10 e |effetto| ≥ soglia.
- Co‑occorrenze segnalate quando due abitudini coincidono in ≥ 60% dei giorni della prima.
- Limiti: osservazionale, una sola persona, autocorrelazione tra giorni; non stabilisce cause.

## Esperimenti personali (versione 1)

- Disegno A‑B: fase A = 21 giorni prima dell'inizio; fase B = giorni dell'esperimento in cui la risposta nel
  diario corrisponde all'impegno (fare/evitare l'abitudine). Esiti della notte e del mattino successivi.
- Minimo 5 giorni per fase; differenza delle medie, IC bootstrap 95%, p per permutazione, Benjamini‑Hochberg
  sui 7 esiti, stesse soglie di rilevanza delle Scoperte.
- Limite: cambiamenti esterni contemporanei vengono attribuiti all'esperimento; la ripetizione (A‑B‑A) lo riduce.

## Regolarità e orari del sonno

- SRI (Phillips 2017) sugli ultimi 7 giorni, riferimenti UK Biobank (Windred 2024: mediana 81, IQR 73,8–86,3).
- Orari: media e deviazione standard circolari di addormentamento e risveglio (28 giorni).
- Jet lag sociale (Wittmann 2006): differenza del punto medio del sonno tra notti con risveglio sabato/domenica
  e notti feriali (minimo 2 e 4 notti).
