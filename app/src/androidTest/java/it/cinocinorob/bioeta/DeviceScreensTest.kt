package it.cinocinorob.bioeta

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
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
        "READ_EXERCISE", "READ_ACTIVE_CALORIES_BURNED", "READ_DISTANCE", "READ_TOTAL_CALORIES_BURNED", "READ_HEALTH_DATA_HISTORY",
    )
    private val writePerms = listOf(
        "WRITE_STEPS", "WRITE_HEART_RATE", "WRITE_RESTING_HEART_RATE", "WRITE_HEART_RATE_VARIABILITY", "WRITE_SLEEP",
        "WRITE_EXERCISE", "WRITE_VO2_MAX", "WRITE_WEIGHT", "WRITE_RESPIRATORY_RATE", "WRITE_OXYGEN_SATURATION",
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

        // FC di base ogni 5 minuti nelle ultime 72 ore (scrittura in blocchi orari)
        var hStart = now.minus(Duration.ofHours(72)).let { Instant.ofEpochSecond(it.epochSecond / 3600 * 3600) }
        while (hStart.isBefore(now.minus(Duration.ofMinutes(5)))) {
            val hEnd = minOf(hStart.plusSeconds(3600), now)
            val samples = generateSequence(hStart) { it.plusSeconds(300) }.takeWhile { it.isBefore(hEnd) }
                .map { HeartRateRecord.Sample(it, (64 + (it.epochSecond / 300 % 9)).toLong()) }.toList()
            if (samples.isNotEmpty()) records += HeartRateRecord(startTime = hStart, startZoneOffset = offset(hStart), endTime = hEnd, endZoneOffset = offset(hEnd), samples = samples, metadata = meta)
            hStart = hEnd
        }

        // Allenamenti di oggi: pedalata con FC ogni 2 minuti e corsa SENZA FC, nella stessa mattina
        val dayStart = today.atStartOfDay(zone).toInstant()
        val base = if (Duration.between(dayStart, now).toMinutes() > 150) now.minus(Duration.ofMinutes(120)) else now.minus(Duration.ofHours(26))
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
        store.savePanels(listOf(panel(LocalDate.now().minusMonths(9), 1.4, 94.0, 13.2), panel(LocalDate.now().minusDays(20), 0.7, 88.0, 12.8)))
    }

    private fun waitText(text: String, timeoutMs: Long = 60_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tab(name: String) {
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
        val client = HealthConnectClient.getOrCreate(ctx)
        val granted = client.permissionController.getGrantedPermissions()
        Log.i("BioEtaE2E", "permessi concessi: ${granted.size}")
        assertTrue("Permesso di scrittura esercizi non concesso", HealthPermission.getWritePermission(ExerciseSessionRecord::class) in granted)
        seed(client)
        seedLocal()

        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Aggiornato", 90_000)
            Thread.sleep(6000) // fase completa: storico, carico, statistiche allenamenti
            screenshot("home")
            compose.onNodeWithTag("home_list").performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("home_eta_fisiologia")
            compose.onNodeWithTag("home_list").performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("home_attivita")
            compose.onNodeWithTag("home_list").performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("home_fine")

            tab("ACTIVITY")
            screenshot("attivita")
            compose.onNode(hasScrollableList()).performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("attivita_carico")
            compose.onNode(hasScrollableList()).performTouchInput { swipeUp(durationMillis = 400) }
            compose.onNode(hasScrollableList()).performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("attivita_allenamenti")

            // Fonti e sincronizzazione: entrambi gli allenamenti devono essere stati importati
            compose.onNodeWithText("allenamenti importati", substring = true).performClick()
            waitText("Allenamenti importati")
            screenshot("fonti_sincronizzazione")
            compose.onNode(hasScrollableList()).performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("allenamenti_importati")
            waitText("Bici", 20_000)
            waitText("Corsa", 20_000)
            inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(800)

            tab("HEALTH")
            screenshot("salute")
            compose.onNode(hasScrollableList()).performTouchInput { swipeUp(durationMillis = 400) }
            screenshot("salute_recupero")

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
            inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(800)

            // Profilo e tema chiaro
            compose.onNode(androidx.compose.ui.test.hasClickLabel("Profilo e impostazioni")).performClick()
            Thread.sleep(1000)
            screenshot("profilo")
            compose.onNodeWithText("Chiaro").performClick()
            inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(1200)
            screenshot("home_tema_chiaro")
            tab("ACTIVITY")
            screenshot("attivita_tema_chiaro")
        }
        Unit
    }
}
