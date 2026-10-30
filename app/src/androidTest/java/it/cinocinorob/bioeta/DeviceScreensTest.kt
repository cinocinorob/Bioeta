package it.cinocinorob.bioeta

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Volume
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.cinocinorob.bioeta.data.LabStore
import it.cinocinorob.bioeta.data.LabValue
import it.cinocinorob.bioeta.data.Panel
import it.cinocinorob.bioeta.data.Profile
import it.cinocinorob.bioeta.data.Sex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.sin

/**
 * Verifica end-to-end su emulatore con DATI SINTETICI DI PROVA (non dati reali dell'utente):
 * 1. concede i permessi di Connessione Salute all'app;
 * 2. inserisce 30 giorni di sonno, FC a riposo, HRV, passi e, per oggi, una pedalata (con FC) e una corsa
 *    (senza FC) nella stessa mattina — il caso segnalato come "allenamenti non importati";
 * 3. avvia l'app reale, verifica che entrambi gli allenamenti compaiano e cattura screenshot di tutte le
 *    schermate principali in /data/local/tmp/bioeta (recuperati dal workflow con adb pull).
 */
@RunWith(AndroidJUnit4::class)
class DeviceScreensTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val pkg = ctx.packageName
    private val zone = ZoneId.systemDefault()
    private val dir = "/data/local/tmp/bioeta"
    private var shot = 0

    private fun shell(cmd: String): String {
        val pfd: ParcelFileDescriptor = inst.uiAutomation.executeShellCommand(cmd)
        return FileInputStream(pfd.fileDescriptor).use { it.readBytes().decodeToString() }.also { pfd.close() }
    }

    private fun screenshot(name: String) {
        closeSystemDialogs()
        compose.waitForIdle()
        Thread.sleep(700)
        shot++
        val file = "$dir/%02d_%s.png".format(shot, name)
        shell("screencap -p $file")
        Log.i("BioEtaE2E", "screenshot $file")
    }

    private fun offset(t: Instant): ZoneOffset = zone.rules.getOffset(t)
    private val meta get() = Metadata.autoRecorded(Device(type = Device.TYPE_WATCH, manufacturer = "Test", model = "Emulatore"))

    private val readPerms = listOf(
        "READ_STEPS", "READ_RESTING_HEART_RATE", "READ_HEART_RATE_VARIABILITY", "READ_SLEEP", "READ_VO2_MAX", "READ_WEIGHT",
        "READ_BODY_FAT", "READ_LEAN_BODY_MASS", "READ_OXYGEN_SATURATION", "READ_RESPIRATORY_RATE", "READ_HEART_RATE",
        "READ_EXERCISE", "READ_ACTIVE_CALORIES_BURNED", "READ_DISTANCE", "READ_TOTAL_CALORIES_BURNED", "READ_HEALTH_DATA_HISTORY", "READ_NUTRITION", "READ_HYDRATION",
    )
    private val writePerms = listOf(
        "WRITE_STEPS", "WRITE_HEART_RATE", "WRITE_RESTING_HEART_RATE", "WRITE_HEART_RATE_VARIABILITY", "WRITE_SLEEP",
        "WRITE_EXERCISE", "WRITE_VO2_MAX", "WRITE_WEIGHT", "WRITE_RESPIRATORY_RATE", "WRITE_OXYGEN_SATURATION", "WRITE_NUTRITION", "WRITE_HYDRATION",
    )

    private fun grantAll() {
        (readPerms + writePerms).forEach { p ->
            val out = shell("pm grant $pkg android.permission.health.$p")
            if (out.isNotBlank()) Log.w("BioEtaE2E", "pm grant $p: $out")
        }
    }

    private suspend fun seed(client: HealthConnectClient) {
        val now = Instant.now()
        val today = LocalDate.now(zone)
        val records = mutableListOf<Record>()

        // 30 notti di sonno (23:10 → 06:55 circa) con fasi, FC a riposo, HRV, respirazione, SpO2
        for (k in 0 until 30) {
            val wakeDay = today.minusDays(k.toLong())
            val start = wakeDay.minusDays(1).atTime(23, 5 + (k % 4) * 5).atZone(zone).toInstant()
            val end = wakeDay.atTime(6, 50 + (k % 3) * 4).atZone(zone).toInstant()
            if (end.isAfter(now)) continue
            val stages = mutableListOf<SleepSessionRecord.Stage>()
            var t = start
            val cycle = listOf(SleepSessionRecord.STAGE_TYPE_LIGHT to 35L, SleepSessionRecord.STAGE_TYPE_DEEP to 25L,
                SleepSessionRecord.STAGE_TYPE_LIGHT to 20L, SleepSessionRecord.STAGE_TYPE_REM to 18L, SleepSessionRecord.STAGE_TYPE_AWAKE to 3L)
            var i = 0
            while (t.isBefore(end)) {
                val (type, min) = cycle[i % cycle.size]
                val e = minOf(t.plusSeconds(min * 60), end)
                stages += SleepSessionRecord.Stage(t, e, type)
                t = e; i++
            }
            records += SleepSessionRecord(startTime = start, startZoneOffset = offset(start), endTime = end, endZoneOffset = offset(end), metadata = meta, stages = stages)
            val morning = wakeDay.atTime(6, 30).atZone(zone).toInstant()
            records += RestingHeartRateRecord(time = morning, zoneOffset = offset(morning), beatsPerMinute = (55 + (k % 5) - 2 + if (k == 0) 1 else 0).toLong(), metadata = meta)
            records += HeartRateVariabilityRmssdRecord(time = morning, zoneOffset = offset(morning), heartRateVariabilityMillis = 46.0 + 6 * sin(k / 3.0), metadata = meta)
            records += RespiratoryRateRecord(time = morning, zoneOffset = offset(morning), rate = 14.2 + 0.4 * sin(k / 2.0), metadata = meta)
            records += OxygenSaturationRecord(time = morning, zoneOffset = offset(morning), percentage = Percentage(96.0 + (k % 3) * 0.5), metadata = meta)
        }

        // Passi: giorni passati (orari) e oggi fino a ora
        for (k in 0 until 30) {
            val d = today.minusDays(k.toLong())
            for (h in 7..21) {
                val s = d.atTime(h, 0).atZone(zone).toInstant(); val e = s.plusSeconds(3600)
                if (e.isAfter(now)) break
                val count = 350L + ((h * 37 + k * 53) % 500)
                records += StepsRecord(startTime = s, startZoneOffset = offset(s), endTime = e, endZoneOffset = offset(e), count = count, metadata = meta)
            }
        }

        // Allenamenti di oggi: pedalata con FC ogni 2 minuti e corsa SENZA FC, nella stessa mattina
        val dayStart0 = today.atStartOfDay(zone).toInstant()
        val base0 = if (Duration.between(dayStart0, now).toMinutes() > 150) now.minus(Duration.ofMinutes(120)) else now.minus(Duration.ofHours(26))
        val busyFrom = base0.minus(Duration.ofMinutes(5)); val busyTo = base0.plus(Duration.ofMinutes(90))

        // FC di base ogni 5 minuti nelle ultime 72 ore (scrittura in blocchi orari), esclusi gli allenamenti
        // (la corsa deve risultare senza frequenza cardiaca, come un orologio che non l'ha registrata)
        var hStart = now.minus(Duration.ofHours(72)).let { Instant.ofEpochSecond(it.epochSecond / 3600 * 3600) }
        while (hStart.isBefore(now.minus(Duration.ofMinutes(5)))) {
            val hEnd = minOf(hStart.plusSeconds(3600), now)
            val samples = generateSequence(hStart) { it.plusSeconds(300) }.takeWhile { it.isBefore(hEnd) }
                .filter { it.isBefore(busyFrom) || it.isAfter(busyTo) }
                .map { HeartRateRecord.Sample(it, (64 + (it.epochSecond / 300 % 9)).toLong()) }.toList()
            if (samples.isNotEmpty()) records += HeartRateRecord(startTime = hStart, startZoneOffset = offset(hStart), endTime = hEnd, endZoneOffset = offset(hEnd), samples = samples, metadata = meta)
            hStart = hEnd
        }

        val base = base0
        val bikeS = base; val bikeE = base.plus(Duration.ofMinutes(45))
        val runS = base.plus(Duration.ofMinutes(55)); val runE = base.plus(Duration.ofMinutes(85))
        records += ExerciseSessionRecord(startTime = bikeS, startZoneOffset = offset(bikeS), endTime = bikeE, endZoneOffset = offset(bikeE),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_BIKING, title = null, metadata = meta)
        records += ExerciseSessionRecord(startTime = runS, startZoneOffset = offset(runS), endTime = runE, endZoneOffset = offset(runE),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, title = null, metadata = meta)
        val bikeHr = generateSequence(bikeS) { it.plusSeconds(120) }.takeWhile { it.isBefore(bikeE) }
            .mapIndexed { i, t -> HeartRateRecord.Sample(t, (128 + (i % 6) * 4).toLong()) }.toList()
        records += HeartRateRecord(startTime = bikeS, startZoneOffset = offset(bikeS), endTime = bikeE, endZoneOffset = offset(bikeE), samples = bikeHr, metadata = meta)

        // Corpo e capacità aerobica
        for (k in listOf(28, 21, 14, 7, 1)) {
            val t = today.minusDays(k.toLong()).atTime(7, 30).atZone(zone).toInstant()
            records += WeightRecord(time = t, zoneOffset = offset(t), weight = Mass.kilograms(72.4 - k * 0.02), metadata = meta)
        }
        val v = today.minusDays(3).atTime(9, 0).atZone(zone).toInstant()
        records += Vo2MaxRecord(time = v, zoneOffset = offset(v), vo2MillilitersPerMinuteKilogram = 44.5, measurementMethod = Vo2MaxRecord.MEASUREMENT_METHOD_OTHER, metadata = meta)

        // Pasti di 21 giorni (come un diario alimentare): colazione con caffè, pranzo, cena (tardi un giorno su tre),
        // caffè pomeridiano un giorno su due; acqua 1,8 L
        for (k in 1..21) {
            val d = today.minusDays(k.toLong())
            fun meal(h: Int, m: Int, type: Int, kcal: Double, prot: Double, carb: Double, fat: Double, sat: Double, fib: Double, na: Double, caf: Double) {
                val s = d.atTime(h, m).atZone(zone).toInstant(); val e = s.plusSeconds(1200)
                records += NutritionRecord(startTime = s, startZoneOffset = offset(s), endTime = e, endZoneOffset = offset(e),
                    energy = Energy.kilocalories(kcal), protein = Mass.grams(prot), totalCarbohydrate = Mass.grams(carb), totalFat = Mass.grams(fat),
                    saturatedFat = Mass.grams(sat), dietaryFiber = Mass.grams(fib), sugar = Mass.grams(carb * 0.25), sodium = Mass.milligrams(na),
                    potassium = Mass.milligrams(kcal * 1.4), caffeine = if (caf > 0) Mass.milligrams(caf) else null, mealType = type, metadata = meta)
            }
            meal(7, 30, MealType.MEAL_TYPE_BREAKFAST, 450.0, 22.0, 55.0, 14.0, 5.0, 6.0, 350.0, 80.0)
            meal(13, 0, MealType.MEAL_TYPE_LUNCH, 800.0, 38.0, 90.0, 28.0, 8.0, 9.0, 900.0, 0.0)
            if (k % 2 == 0) meal(16, 30, MealType.MEAL_TYPE_SNACK, 60.0, 1.0, 8.0, 2.0, 1.0, 0.0, 20.0, 70.0)
            if (k % 3 == 0) meal(22, 15, MealType.MEAL_TYPE_DINNER, 850.0, 40.0, 85.0, 34.0, 11.0, 7.0, 1100.0, 0.0)
            else meal(20, 0, MealType.MEAL_TYPE_DINNER, 750.0, 42.0, 70.0, 26.0, 7.0, 10.0, 800.0, 0.0)
            val wS = d.atTime(10, 0).atZone(zone).toInstant()
            records += HydrationRecord(startTime = wS, startZoneOffset = offset(wS), endTime = wS.plusSeconds(60), endZoneOffset = offset(wS), volume = Volume.liters(1.8), metadata = meta)
        }

        records.chunked(500).forEach { client.insertRecords(it) }
        Log.i("BioEtaE2E", "inseriti ${records.size} record")
    }

    private fun seedLocal() {
        val store = LabStore(ctx)
        store.saveProfile(Profile(birthDate = LocalDate.of(1981, 5, 10), sex = Sex.M, name = "Robbie"))
        fun panel(d: LocalDate, crp: Double, glu: Double, rdw: Double) = Panel(
            date = d, lab = "Laboratorio di prova",
            values = mapOf(
                "albumin" to LabValue(4.6), "creatinine" to LabValue(0.95), "glucose" to LabValue(glu), "crp" to LabValue(crp),
                "lymph_pct" to LabValue(33.0), "mcv" to LabValue(89.0), "rdw" to LabValue(rdw), "alp" to LabValue(61.0), "wbc" to LabValue(5.6),
            ),
        )
        // Diario: umore di 26 degli ultimi 30 giorni (dati sintetici), per il grafico dell'andamento
        // Etichetta «Alcol» la sera prima dei mattini in cui l'HRV sintetica è più bassa (per la schermata Scoperte)
        store.saveJournal((1..29).filter { it % 7 != 3 }.map { k ->
            val d = LocalDate.now().minusDays(k.toLong())
            val nextMorningK = k - 1
            val tags = if (nextMorningK >= 0 && kotlin.math.sin(nextMorningK / 3.0) < -0.55) setOf("Alcol") else if (k % 4 == 0) setOf("Caffè dopo le 14") else emptySet()
            it.cinocinorob.bioeta.science.JournalEntry(d, mood = (3 + kotlin.math.round(1.2 * kotlin.math.sin(k / 4.0))).toInt().coerceIn(1, 5), tags = tags)
        }.sortedBy { it.date })
        store.savePanels(listOf(panel(LocalDate.now().minusMonths(9), 1.4, 94.0, 13.2), panel(LocalDate.now().minusDays(20), 0.7, 88.0, 12.8)))
    }

    private fun waitText(text: String, timeoutMs: Long = 60_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Chiude i popup di sistema (es. «Pixel Launcher isn't responding» sugli emulatori lenti) che rubano il tasto Indietro. */
    private fun closeSystemDialogs() {
        shell("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
        Thread.sleep(300)
    }

    private fun back() {
        closeSystemDialogs()
        inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        Thread.sleep(800)
    }

    private fun tab(name: String) {
        closeSystemDialogs()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("tab_$name").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_$name").performClick()
        compose.waitForIdle()
        Thread.sleep(1200)
    }

    private fun hasScrollableList() = androidx.compose.ui.test.hasScrollAction()

    @Test
    fun seedLaunchAndCapture() = runBlocking {
        shell("mkdir -p $dir")
        shell("rm -f $dir/*.png")
        grantAll()
        shell("settings put global hide_error_dialogs 1")
        val client = HealthConnectClient.getOrCreate(ctx)
        val granted = client.permissionController.getGrantedPermissions()
        Log.i("BioEtaE2E", "permessi concessi: ${granted.size}")
        assertTrue("Permesso di scrittura esercizi non concesso", HealthPermission.getWritePermission(ExerciseSessionRecord::class) in granted)
        seed(client)
        seedLocal()

        ActivityScenario.launch(MainActivity::class.java).use {
            // Primo avvio: avvertenza su scopo e privacy
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("welcome_accept").fetchSemanticsNodes().isNotEmpty() }
            screenshot("benvenuto")
            compose.onNodeWithTag("welcome_accept").performScrollTo().performClick()
            waitText("Aggiornato", 90_000)
            Thread.sleep(6000) // fase completa: storico, carico, statistiche allenamenti
            screenshot("home")
            // Radar malessere: si apre al tocco con i parametri della notte e il loro intervallo
            compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("radar_card"))
            compose.onNodeWithTag("radar_card").performClick()
            Thread.sleep(600)
            screenshot("home_radar")
            compose.onNodeWithTag("radar_card").performClick()
            Thread.sleep(400)
            // Fatica percepita: richiesta per gli allenamenti di oggi, voto 6 e poi 4
            compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("rpe_prompt"))
            screenshot("home_rpe")
            compose.onNodeWithTag("rpe_6").performClick()
            Thread.sleep(500)
            compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("rpe_prompt"))
            compose.onNodeWithTag("rpe_4").performClick()
            Thread.sleep(500)
            compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("radar_card"))
            compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("highlights"))
            screenshot("home_in_evidenza")
            compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("nutrition_card"))
            waitText("giorni completi", 30_000)
            screenshot("home_alimentazione")
            compose.onNodeWithTag("nutrition_card").performClick()
            waitText("Rispetto ai riferimenti", 10_000)
            screenshot("alimentazione")
            compose.onNodeWithTag("nutrition_list").performScrollToNode(hasTestTag("nutrition_targets"))
            screenshot("alimentazione_riferimenti")
            compose.onNodeWithTag("nutrition_list").performScrollToNode(hasTestTag("nutrition_energy"))
            screenshot("alimentazione_bilancio")
            compose.onNodeWithTag("nutrition_list").performScrollToNode(hasTestTag("nutrition_timing"))
            screenshot("alimentazione_orari")
            back()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("home_list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("longterm_card"))
            screenshot("home_lungo_termine")
            compose.onNodeWithTag("longterm_card").performClick()
            waitText("Cosa dice la ricerca", 10_000)
            screenshot("lungo_termine")
            compose.onNodeWithTag("longterm_list").performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("lungo_termine_dettaglio")
            back()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("home_list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("home_list").performScrollToNode(hasText("Parametri della notte"))
            screenshot("home_parametri_notte")
            compose.onNodeWithTag("home_list").performScrollToNode(hasText("Corpo ed età"))
            screenshot("home_corpo_eta")
            compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("discoveries_card"))
            screenshot("home_fine")
            // Diario in stile WHOOP: abitudini di ieri
            compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("journal_yesterday"))
            compose.onNodeWithTag("journal_yesterday").performClick()
            waitText("risposte", 10_000)
            compose.onAllNodesWithTag("yes")[0].performClick()
            Thread.sleep(400)
            screenshot("diario")
            compose.onNodeWithTag("journal_list").performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("diario_domande")
            compose.onNodeWithTag("journal_list").performScrollToNode(hasTestTag("journal_manage"))
            compose.onNodeWithTag("journal_manage").performClick()
            Thread.sleep(800)
            screenshot("diario_scelta_domande")
            compose.onNodeWithText("Fatto").performClick()
            Thread.sleep(500)
            compose.onNodeWithContentDescription("Indietro").performClick()
            Thread.sleep(800)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("home_list").fetchSemanticsNodes().isNotEmpty() }
            // Il tasto Indietro di sistema deve chiudere il diario
            compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("journal_card"))
            compose.onNodeWithTag("journal_card").performClick()
            waitText("risposte", 10_000)
            back()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("home_list").fetchSemanticsNodes().isNotEmpty() }

            tab("ACTIVITY")
            screenshot("attivita")
            compose.onNode(hasScrollableList()).performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("attivita_carico")
            compose.onNode(hasScrollableList()).performScrollToNode(hasTestTag("active_card"))
            waitText("Da passi", 90_000)
            waitText("Fonte della frequenza cardiaca", 90_000)
            // La scheda è cliccabile: i suoi figli sono fusi nel nodo della scheda, quindi si scorre di poco a mano
            compose.onNode(hasScrollableList()).performTouchInput { swipeUp(startY = centerY, endY = centerY - 500f, durationMillis = 400) }
            screenshot("attivita_minuti_attivi")
            compose.onNodeWithTag("hr_source_button").performClick()
            waitText("Automatica (consigliata)", 10_000)
            screenshot("attivita_fonte_fc")
            compose.onNodeWithTag("hr_source_close").performClick()
            compose.onNode(hasScrollableList()).performScrollToNode(hasTestTag("rpe_card"))
            waitText("allenamenti votati", 10_000)
            screenshot("attivita_carico_percepito")
            compose.onNode(hasScrollableList()).performTouchInput { swipeUp(durationMillis = 400) }
            compose.onNode(hasScrollableList()).performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("attivita_allenamenti")

            // Fonti e sincronizzazione: entrambi gli allenamenti devono essere stati importati
            compose.onNode(hasScrollableList()).performScrollToNode(hasText("allenamenti importati", substring = true))
            compose.onNodeWithText("allenamenti importati", substring = true).performClick()
            waitText("Allenamenti importati")
            screenshot("fonti_sincronizzazione")
            // Diagnostica: cosa funziona con i dati che arrivano
            compose.onNodeWithText("Diagnostica").performClick()
            waitText("Cosa funziona con i tuoi dati", 30_000)
            Thread.sleep(1500)
            screenshot("diagnostica")
            back()
            waitText("Allenamenti importati")
            // Verifica: entrambi gli allenamenti della mattina sono stati importati
            compose.onNode(hasScrollableList()).performScrollToNode(hasText("Corsa"))
            compose.onNode(hasScrollableList()).performScrollToNode(hasText("Bici"))
            screenshot("allenamenti_importati")
            waitText("Bici", 20_000)
            waitText("Corsa", 20_000)
            back()

            tab("HEALTH")
            screenshot("salute")
            compose.onNode(hasScrollableList()).performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("salute_recupero")
            compose.onNode(hasScrollableList()).performScrollToNode(hasTestTag("regularity_card"))
            screenshot("salute_regolarita")
            compose.onNode(hasScrollableList()).performScrollToNode(hasTestTag("mood_card"))
            waitText("Cosa va insieme al tuo umore", 10_000)
            screenshot("salute_umore")
            // Scoperte: schermata dedicata
            compose.onNode(hasScrollableList()).performScrollToNode(hasTestTag("discoveries_card"))
            screenshot("salute_scoperte_card")
            compose.onNodeWithTag("discoveries_card").performClick()
            waitText("Cosa cambia i tuoi numeri", 20_000)
            Thread.sleep(4000) // calcolo in background (bootstrap e permutazioni)
            screenshot("scoperte")
            compose.onNodeWithTag("discoveries_list").performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("scoperte_foresta")
            compose.onNodeWithTag("discoveries_list").performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("scoperte_attesa")
            back()

            tab("AGE")
            screenshot("eta_biologica")
            compose.onNode(hasScrollableList()).performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("eta_dettagli")

            tab("LABS")
            screenshot("analisi")

            tab("HOME")
            compose.onNodeWithTag("home_list").performScrollToNode(hasText("FC a riposo"))
            compose.onAllNodesWithText("FC a riposo")[0].performClick()
            Thread.sleep(1200)
            screenshot("dettaglio_fc_riposo")
            back()

            // Profilo e tema chiaro
            compose.onNodeWithTag("profile_button").performClick()
            Thread.sleep(1000)
            screenshot("profilo")
            compose.onNodeWithText("Scuro").performClick()
            back()
            screenshot("home_tema_scuro")
            compose.onNodeWithTag("home_list").performScrollToNode(hasText("Parametri della notte"))
            screenshot("home_tema_scuro_parametri")
            tab("ACTIVITY")
            screenshot("attivita_tema_scuro")
            tab("HEALTH")
            screenshot("salute_tema_scuro")
        }
        Unit
    }
}
