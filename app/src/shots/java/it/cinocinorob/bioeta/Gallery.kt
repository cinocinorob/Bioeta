package it.cinocinorob.bioeta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import it.cinocinorob.bioeta.science.InsightEngine
import it.cinocinorob.bioeta.science.InsightInput
import it.cinocinorob.bioeta.science.Zone
import it.cinocinorob.bioeta.ui.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class Gallery {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3400),
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 100.0,
    )

    @Composable
    private fun Today() {
        val p = LocalPalette.current
        Column(Modifier.fillMaxSize().background(p.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("MARTEDÌ 13 OTTOBRE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.text2)
            Text("Buongiorno, Robbie", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(p.card).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TriDial(78, 84, 0.55f, Mood.HAPPY)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Triple("Recupero", "78", Pillar.recovery), Triple("Sonno", "84", Pillar.sleep), Triple("Carico", "46", Pillar.load)).forEach { (l, v, c) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(c.first().copy(alpha = 0.13f)).padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                            Text(l, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c[1])
                        }
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    Text("Sei in forma", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                    Text("I tuoi segnali di recupero sono nella norma o migliori.", fontSize = 14.sp, color = p.text2)
                }
            }
            HealthCard("Carico cardiaco", BioIcons.Pulse, Pillar.load[1]) {
                Row(verticalAlignment = Alignment.Bottom) {
                    BigValue("46", null, size = 38); Spacer(Modifier.width(8.dp)); Caption("obiettivo 42–66")
                    Spacer(Modifier.weight(1f)); Pill("Nell'obiettivo", Tint.good)
                }
                TargetGauge(46.0, 42.0, 66.0)
            }
            HealthCard("La tua giornata", BioIcons.Bolt, Tint.activity) {
                BigValue("6.420", "passi", size = 34)
                ProgressBar(0.71f, listOf(androidx.compose.ui.graphics.Color(0xFFFFC27A), androidx.compose.ui.graphics.Color(0xFFFF9F5A)))
                HourlyBars(listOf(0L,0,0,0,0,0,120,800,1200,300,150,90,600,400,200,1300,700,560,0,0,0,0,0,0), Tint.activity)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip("Minuti attivi", "34", "oggi", Modifier.weight(1f)); StatChip("Settimana", "96", "/150 min", Modifier.weight(1f))
                }
            }
            JournalPreview()
        }
    }

    @Composable
    private fun JournalPreview() {
        HealthCard("Diario di oggi", BioIcons.Pencil, Tint.sleep) {
            ScalePicker(4) {}
            TagChips(setOf("Caffè dopo le 14", "Allenamento intenso")) {}
        }
    }

    @Composable
    private fun Activity() {
        val p = LocalPalette.current
        Column(Modifier.fillMaxSize().background(p.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LargeHeader("Attività", "Movimento e allenamento")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(p.card).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                ActivityRings(0.71f, 0.64f, 0.7f)
            }
            HealthCard("Carico cardiaco · 4 settimane", BioIcons.Pulse, Pillar.load[1]) {
                val d = LocalDate.of(2026, 10, 13)
                LoadBars((27 downTo 0).map { d.minusDays(it.toLong()) to (20 + (it * 37 % 60)).toDouble() }, 42.0, 66.0)
                ZoneBar(mapOf(Zone.LIGHT to 40, Zone.MODERATE to 25, Zone.VIGOROUS to 9, Zone.MAXIMAL to 1))
            }
            HealthCard("Pressione", BioIcons.Heart, Tint.bp) {
                BigValue("128/79", "mmHg", size = 40); BpScale(128.0, 79.0)
            }
            InsightCard(InsightEngine.build(InsightInput(45.0, true, ldl = 132.0)).first(), null)
        }
    }


    @Composable
    private fun Recovery() {
        val p = LocalPalette.current
        val start = java.time.Instant.parse("2026-10-12T21:30:00Z")
        fun at(m: Long) = start.plusSeconds(m * 60)
        val st = it.cinocinorob.bioeta.science.StageType.entries
        val stages = listOf(0L to 12L to 0, 12L to 90L to 1, 90L to 150L to 2, 150L to 200L to 3, 200L to 210L to 0, 210L to 320L to 1, 320L to 360L to 2, 360L to 450L to 3)
            .map { (r, t) -> it.cinocinorob.bioeta.science.Stage(at(r.first), at(r.second), listOf(st[0], st[1], st[2], st[3])[t]) }
        val night = it.cinocinorob.bioeta.science.SleepNight(LocalDate.of(2026, 10, 13), start, at(450), stages, 54.0, 49.0)
        Column(Modifier.fillMaxSize().background(p.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LargeHeader("Recupero", "Sonno e prontezza")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.card).padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ScoreRing(84, "SONNO", RecoveryColors.sleep); ScoreRing(78, "PRONTEZZA", RecoveryColors.ready)
            }
            HealthCard("Sonno", BioIcons.Moon, Tint.sleep) { Hypnogram(night) }
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Mood.entries.forEach { Sprout(it, sizeDp = 70) }
            }
            ActivityRings(0.8f, 0.6f, 0.45f)
        }
    }

    @Test fun recoveryLight() = paparazzi.snapshot { BioTheme { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) { Recovery() } } }

    @Test fun todayLight() = paparazzi.snapshot { BioTheme { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) { Today() } } }
    @Test fun activityLight() = paparazzi.snapshot { BioTheme { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) { Activity() } } }
    @Test fun todayDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(screenHeight = 3400, nightMode = NightMode.NIGHT))
        paparazzi.snapshot { BioTheme { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) { Today() } } }
    }
}
