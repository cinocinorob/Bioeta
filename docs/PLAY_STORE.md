# Pubblicazione su Google Play — piano e materiali

Stato: preparazione. Nessun invio effettuato. I passaggi marcati **[tu]** richiedono il tuo account.

## 1. Bloccanti prima dell'invio

| # | Cosa | Perché | Stato |
|---|---|---|---|
| 1 | Account sviluppatore Google Play **[tu]** | Obbligatorio. Gli account personali nuovi devono prima fare un test chiuso con almeno 12 tester attivi per 14 giorni consecutivi, poi chiedere l’accesso alla produzione. | da fare |
| 2 | Chiave di caricamento **[tu]** | Play App Signing: Google firma l'app, tu firmi i caricamenti. La chiave NON va nel repository. | CI pronto: segreti `BIOETA_UPLOAD_KEYSTORE_B64`, `BIOETA_UPLOAD_STORE_PASSWORD`, `BIOETA_UPLOAD_KEY_ALIAS`, `BIOETA_UPLOAD_KEY_PASSWORD` |
| 3 | Informativa privacy pubblica | Obbligatoria per Connessione Salute e per la Sicurezza dei dati. | `docs/privacy/index.html` → GitHub Pages |
| 4 | Dichiarazione dei permessi di Connessione Salute **[tu]** | Google verifica ogni tipo di dato richiesto; vanno giustificati uno per uno (sezione 4). | testo pronto |
| 5 | Modulo «Sicurezza dei dati» **[tu]** | Obbligatorio. | risposte pronte (sezione 5) |
| 6 | Dichiarazione app di salute **[tu]** | Norma Google Play «App per la salute». | risposte pronte (sezione 6) |
| 7 | Valutazione regolatoria (UE, MDR) | Vedi sezione 7: alcune funzioni sono al confine con il software medico. | **decisione tua** |

Nota sulla firma: l'APK che installi oggi è firmato con la chiave «fixed» del repository. La versione Play sarà firmata da Google: le due **non** si aggiornano a vicenda. Prima di installare da Play: Profilo → Esporta, disinstalla, installa da Play, Profilo → Ripristina.

## 2. Scheda dello store (it-IT)

**Nome (≤ 30):** BioEtà: salute ed età biologica

**Descrizione breve (≤ 80):** Sonno, recupero, attività ed età biologica dai tuoi dati, con metodi dichiarati.

**Descrizione completa:**

BioEtà legge i dati del tuo orologio da Connessione Salute e le tue analisi del sangue, e ti dice come stai rispetto alla TUA normalità, con metodi scientifici dichiarati e verificabili.

• Recupero e Sonno: punteggi 0–100 e, soprattutto, il confronto con le tue giornate («meglio del 62% delle tue notti»).
• Parametri della notte: FC a riposo, HRV, respirazione, SpO₂ e temperatura confrontati con il tuo intervallo abituale; segnala quando più parametri escono insieme dalla norma.
• Attività: minuti attivi secondo l'OMS (da frequenza cardiaca e cadenza dei passi), carico cardiaco, carico percepito.
• Età biologica: PhenoAge (Levine 2018) dai 9 esami del sangue ed età cardiorespiratoria dal VO₂max.
• Diario serale e Scoperte: segna le tue abitudini e scopri come cambiano HRV, sonno e recupero, con statistica rigorosa (correzione per confronti multipli, intervalli di confidenza).
• Esperimenti personali: cambia un'abitudine per 2–3 settimane e misura l'effetto su di te.
• Regolarità del sonno, pressione a domicilio secondo il protocollo ESH, analisi del sangue con andamento.

Privacy: nessun account, nessun server, nessuna pubblicità. I dati restano sul telefono.

BioEtà è uno strumento di benessere e automonitoraggio, non un dispositivo medico: non fa diagnosi e non sostituisce il medico.

**Categoria:** Salute e fitness. **Pubblico:** 18+. **Contatto:** email sviluppatore **[tu]**. **Informativa:** https://cinocinorob.github.io/Bioeta/privacy/

**Grafica:** `docs/store/icon-512.png` (512×512), `docs/store/feature-1024x500.png`, screenshot in `docs/store/screenshots/` (1440×2880, rapporto 2:1 come richiesto).

## 3. Build

- `gradle bundlePlay` → `app/build/outputs/bundle/play/app-play.aab` (variante «play», firmata con la chiave di caricamento se presente).
- Il CI produce l'AAB come artefatto `BioEta-play-aab` a ogni build (non nelle release pubbliche).
- targetSdk 36: è il minimo richiesto da Google Play per app nuove e aggiornamenti dal 31 agosto 2026. versionCode cresce a ogni versione.

## 4. Giustificazione dei permessi di Connessione Salute

| Permesso | Funzione che lo usa |
|---|---|
| Passi | Passi giornalieri, obiettivo, cadenza per i minuti attivi |
| Distanza, calorie attive e totali | Riepilogo dell'attività di oggi |
| Frequenza cardiaca | Minuti attivi, carico cardiaco (TRIMP), zone degli allenamenti, FC nel sonno |
| FC a riposo | Recupero, parametri della notte, riserva cardiaca |
| HRV (RMSSD) | Recupero, parametri della notte, Scoperte |
| Sonno | Punteggio sonno, regolarità, Scoperte |
| Allenamenti | Elenco allenamenti, minuti attivi, carico percepito |
| VO₂max | Età cardiorespiratoria |
| Frequenza respiratoria, SpO₂, temperatura cutanea | Parametri della notte |
| Peso, grasso corporeo, massa magra | Andamento della composizione corporea |
| Nutrizione e idratazione (READ_NUTRITION, READ_HYDRATION) | Sezione Alimentazione: medie dei nutrienti rispetto ai riferimenti, dispendio stimato da apporto e peso, orari dei pasti nelle Scoperte. Facoltativi |
| Storico (READ_HEALTH_DATA_HISTORY) | Baseline personali dai dati precedenti all'installazione |
| Background (READ_HEALTH_DATA_IN_BACKGROUND) | Aggiornamento orario di widget e dati, se l'utente lo attiva |

Nessuna scrittura in Connessione Salute.

## 5. Sicurezza dei dati (risposte)

- Raccolta: dati sanitari e di fitness, elaborati **sul dispositivo**; non trasmessi ai server dello sviluppatore (non esistono).
- Condivisione con terze parti: **sì, facoltativa**: solo se l'utente usa l'assistente, domanda e riepilogo dei valori vanno ad Anthropic (finalità: funzione dell'app richiesta dall'utente). Dichiarare «Informazioni sanitarie» e «Fitness» come condivise, facoltative.
- Crittografia in transito: sì (HTTPS) per le chiamate dell'assistente.
- Cancellazione: Profilo → Elimina tutti i dati; disinstallazione.
- Nessuna pubblicità, nessun ID pubblicitario, nessuna analisi.

## 6. App per la salute (dichiarazione)

Funzioni: monitoraggio attività e sonno, benessere, gestione di dati di laboratorio inseriti dall'utente. Non è un dispositivo medico; avvertenza al primo avvio e nell'app. Nessuna funzione per minori.

## 7. Rischio regolatorio (UE, Regolamento 2017/745 «MDR») — da valutare

Il software che fornisce informazioni per decisioni diagnostiche, o che prevede malattie, può essere un dispositivo medico (regola 11) e richiedere marcatura CE. Le funzioni di benessere generali no. Funzioni di BioEtà al confine:

1. **Radar malessere** («segni lievi/marcati» di un'infezione possibile): è la più esposta.
2. **Insight sulle analisi** con livelli («Medico / Attenzione»).
3. **Classificazione della pressione** («ipertensione») dalle misure a domicilio.
4. **Età biologica** con rischio di mortalità associato.

Opzioni: (a) versione Play con formulazioni di puro benessere (es. «Variazioni notturne» invece di «Radar malessere», niente categorie diagnostiche), (b) parere di un consulente regolatorio prima della pubblicazione pubblica, (c) test chiuso solo con persone che conosci. Non sono un avvocato: questa sezione elenca i punti da verificare, non è un parere legale.
