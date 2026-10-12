# BioEtà

App Android personale (Kotlin, Jetpack Compose) che:

- legge in sola lettura da **Connessione Salute** FC a riposo, HRV, sonno, passi, VO₂max, peso, SpO₂, frequenza respiratoria;
- archivia le **analisi del sangue** (36 analiti, conversione automatica delle unità) con andamenti, tendenza OLS ed eGFR CKD-EPI 2021 calcolato;
- calcola la **PhenoAge** (Levine 2018 / Liu 2018) da 9 biomarcatori, con scomposizione della variazione tra prelievi e ritmo di invecchiamento;
- calcola l'**età cardiorespiratoria** dal VO₂max (riferimenti FRIEND-I);
- confronta i dati del wearable con la **baseline personale** (ultimi 7 giorni vs giorni 8–67).

Nessun permesso Internet: i dati restano sul telefono. Backup/ripristino via file JSON.

## Build

Ogni push su `main` esegue i test delle formule e pubblica l'APK nella sezione **Releases**.
L'APK è firmato con una chiave fissa (`app/bioeta.keystore`), così gli aggiornamenti si installano sopra la versione precedente senza perdere i dati.

Requisiti: Android 9+ (consigliato 14+, dove Connessione Salute è integrata nel sistema).

## Fonti

- Levine ME et al. Aging 2018;10:573-591. doi:10.18632/aging.101414
- Liu Z et al. PLoS Med 2018;15:e1002718. doi:10.1371/journal.pmed.1002718
- Peterman JE et al. Mayo Clin Proc 2020;95:255-264. doi:10.1016/j.mayocp.2019.06.013
- Nes BM et al. Med Sci Sports Exerc 2014;46:1159-65. doi:10.1249/MSS.0000000000000219
- Inker LA et al. N Engl J Med 2021;385:1737-49. doi:10.1056/NEJMoa2102953

Non è un dispositivo medico.
