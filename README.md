# BioEtà

App Android personale (Kotlin, Jetpack Compose, Material 3) per seguire salute, recupero, attività ed età biologica.

## Cosa fa (verificato nel codice e nei test)

- **Connessione Salute (sola lettura)**: passi, allenamenti, frequenza cardiaca, FC a riposo, HRV, sonno con fasi,
  VO₂max, peso, grasso corporeo, massa magra, SpO₂, frequenza respiratoria, calorie, distanza.
  Qualsiasi app che scrive in Connessione Salute (Fitbit/Google Health, Samsung Health, Oura, Huawei Health o WHOOP
  *se* scrivono lì) viene letta; non ci sono integrazioni cloud dirette.
- **Allenamenti**: sincronizzazione idempotente (90 giorni, 30 senza permesso storico), deduplicazione tra app,
  stato e ultima sincronizzazione visibili in *Profilo → Fonti e sincronizzazione*.
- **Minuti attivi (OMS) e carico cardiaco (TRIMP)** calcolati minuto per minuto; minuti misurati e stimati separati;
  carico marcato completo / parziale / non disponibile.
- **Recupero e sonno** rispetto alla baseline personale (provvisoria dal 3° giorno di storico).
- **Analisi del sangue**: archivio, import da PDF/foto (OCR sul telefono), andamenti, eGFR CKD-EPI 2021.
- **Età biologica**: PhenoAge (Levine 2018 / Liu 2018) da 9 biomarcatori; età cardiorespiratoria (FRIEND-I).
- Pressione arteriosa, diario, widget, backup/ripristino JSON e backup automatico in una cartella.

Metodi, formule, unità e registro delle modifiche agli algoritmi: [docs/METHODS.md](docs/METHODS.md).

## Privacy

I dati restano sul telefono. L'app usa Internet **solo** per l'assistente Claude (modalità API, con chiave
dell'utente e consenso esplicito) e per verificare le citazioni su PubMed; nella modalità gratuita il testo viene
passato all'app Claude dall'utente stesso. Nessuna telemetria. La build di release richiede solo permessi di
**lettura** in Connessione Salute; i permessi di scrittura esistono solo nella build di debug usata dai test su
emulatore.

## Build e verifica

Ogni PR e ogni push su `main` eseguono (GitHub Actions):

1. test unitari (formule scientifiche, motore attività, sonno/recupero, parser referti) e APK di release;
2. rendering Paparazzi dei componenti;
3. **test end-to-end su emulatore Android 15**: inserisce dati sintetici in Connessione Salute (sonno, FC, HRV,
   passi, una pedalata con FC e una corsa senza FC nella stessa mattina), avvia l'app reale, verifica l'import
   degli allenamenti e salva gli screenshot nel ramo `device-screenshots`.

La release (APK + SHA-256) viene pubblicata solo dai push su `main`, mai dalle PR.
L'APK è firmato con una chiave fissa (`app/bioeta.keystore`) così gli aggiornamenti si installano sopra la
versione precedente senza perdere i dati. **Limite noto**: la chiave è nel repository pubblico; cambiarla
obbligherebbe a disinstallare l'app (perdendo i dati non salvati nel backup). Va migrata a un segreto di CI solo
insieme a un piano di backup e reinstallazione.

Requisiti: Android 9+ (consigliato 14+, dove Connessione Salute è integrata nel sistema).

## Fonti principali

- Levine ME et al. Aging 2018;10:573-591. doi:10.18632/aging.101414
- Liu Z et al. PLoS Med 2018;15:e1002718. doi:10.1371/journal.pmed.1002718 (correzione doi:10.1371/journal.pmed.1002760)
- Peterman JE et al. Mayo Clin Proc 2020;95:255-264. doi:10.1016/j.mayocp.2019.06.013
- Inker LA et al. N Engl J Med 2021;385:1737-49. doi:10.1056/NEJMoa2102953
- Herrmann SD et al. J Sport Health Sci 2024;13:6-12. doi:10.1016/j.jshs.2023.10.010
- Garber CE et al. Med Sci Sports Exerc 2011 (ACSM); Tanaka H et al. JACC 2001; Morton RH et al. J Appl Physiol 1990

Font: Archivo (Omnibus-Type), SIL Open Font License 1.1 (`ARCHIVO-OFL.txt`).

Non è un dispositivo medico: le stime sono statistiche e non sostituiscono il parere del medico.
