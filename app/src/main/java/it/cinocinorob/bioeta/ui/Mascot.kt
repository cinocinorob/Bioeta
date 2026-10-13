package it.cinocinorob.bioeta.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class Mood { HAPPY, CALM, TIRED, SLEEPY }

fun moodFor(readiness: Int?, calibrating: Boolean): Mood = when {
    readiness == null || calibrating -> Mood.SLEEPY
    readiness >= 75 -> Mood.HAPPY
    readiness >= 50 -> Mood.CALM
    else -> Mood.TIRED
}

fun moodColors(m: Mood): List<Color> = when (m) {
    Mood.HAPPY -> listOf(Color(0xFFFF7A8E), Color(0xFFE5304D))
    Mood.CALM -> listOf(Color(0xFFFFA27C), Color(0xFFF0605A))
    Mood.TIRED -> listOf(Color(0xFFF6AEBE), Color(0xFFD06A86))
    Mood.SLEEPY -> listOf(Color(0xFFCDB2FF), Color(0xFF9073F0))
}

/** Sagoma del cuore inscritta in un rettangolo w×h. */
fun heartPath(w: Float, h: Float, ox: Float = 0f, oy: Float = 0f) = Path().apply {
    moveTo(ox + w * 0.5f, oy + h * 0.94f)
    cubicTo(ox + w * 0.12f, oy + h * 0.66f, ox - w * 0.02f, oy + h * 0.36f, ox + w * 0.12f, oy + h * 0.17f)
    cubicTo(ox + w * 0.26f, oy - h * 0.02f, ox + w * 0.47f, oy + h * 0.04f, ox + w * 0.5f, oy + h * 0.22f)
    cubicTo(ox + w * 0.53f, oy + h * 0.04f, ox + w * 0.74f, oy - h * 0.02f, ox + w * 0.88f, oy + h * 0.17f)
    cubicTo(ox + w * 1.02f, oy + h * 0.36f, ox + w * 0.88f, oy + h * 0.66f, ox + w * 0.5f, oy + h * 0.94f)
    close()
}

/**
 * Avatar di BioEtà: un cuore che batte alla TUA frequenza a riposo (doppio colpo "lub-dub"),
 * con colore e alone che seguono la prontezza del giorno.
 */
@Composable
fun HeartAvatar(mood: Mood, bpm: Double?, modifier: Modifier = Modifier, sizeDp: Int = 110) {
    val period = (60_000.0 / (bpm ?: 60.0).coerceIn(40.0, 110.0)).toInt()
    val still = androidx.compose.ui.platform.LocalInspectionMode.current
    val tr = rememberInfiniteTransition(label = "beat")
    val beat by tr.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            androidx.compose.animation.core.keyframes {
                durationMillis = period
                1f at 0
                1.10f at (period * 0.10).toInt()
                0.99f at (period * 0.22).toInt()
                1.05f at (period * 0.32).toInt()
                1f at (period * 0.48).toInt()
            },
        ), label = "b",
    )
    val sc = if (still) 1.04f else beat
    val colors = moodColors(mood)
    Canvas(modifier.size(sizeDp.dp)) {
        val w = size.width; val h = size.height
        // alone
        drawCircle(
            Brush.radialGradient(listOf(colors[0].copy(alpha = 0.40f * (sc - 0.9f) * 5f), Color.Transparent), center = Offset(w / 2, h / 2), radius = w * 0.55f),
            radius = w * 0.55f,
        )
        val hw = w * 0.62f * sc; val hh = h * 0.58f * sc
        val ox = (w - hw) / 2; val oy = (h - hh) / 2 + h * 0.02f
        // ombra
        drawOval(Color.Black.copy(alpha = 0.10f), Offset(w * 0.3f, oy + hh * 0.98f), Size(w * 0.4f, h * 0.05f))
        val heart = heartPath(hw, hh, ox, oy)
        drawPath(heart, Brush.linearGradient(listOf(colors[0], colors[1]), start = Offset(ox, oy), end = Offset(ox + hw, oy + hh)))
        // riflesso lucido
        val gloss = heartPath(hw * 0.42f, hh * 0.40f, ox + hw * 0.12f, oy + hh * 0.08f)
        drawPath(gloss, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)), startY = oy, endY = oy + hh * 0.45f))
        // traccia ECG stilizzata
        val y0 = oy + hh * 0.50f
        val ecg = Path().apply {
            moveTo(ox + hw * 0.18f, y0)
            lineTo(ox + hw * 0.36f, y0)
            lineTo(ox + hw * 0.43f, y0 - hh * 0.16f)
            lineTo(ox + hw * 0.52f, y0 + hh * 0.18f)
            lineTo(ox + hw * 0.59f, y0 - hh * 0.05f)
            lineTo(ox + hw * 0.64f, y0)
            lineTo(ox + hw * 0.82f, y0)
        }
        drawPath(ecg, Color.White.copy(alpha = 0.92f), style = Stroke(w * 0.028f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** Frase gentile del giorno, coerente con la prontezza (nessuna promessa clinica). */
fun gentleMessage(mood: Mood, hasFlags: Boolean): Pair<String, String> = when {
    hasFlags -> "Ascolta il tuo corpo" to "Alcuni segnali sono fuori dalla tua norma. Oggi meglio attività leggere, idratazione e riposo; se compaiono sintomi parlane con il medico."
    mood == Mood.HAPPY -> "Sei in forma" to "I tuoi segnali di recupero sono nella norma o migliori. Buona giornata per un allenamento più impegnativo, se ti va."
    mood == Mood.CALM -> "Giornata equilibrata" to "Recupero discreto: va bene muoversi con intensità moderata. Un po' di attenzione al sonno stasera aiuterà."
    mood == Mood.TIRED -> "Con calma oggi" to "Il recupero è sotto la tua media. Una camminata o della mobilità leggera vanno benissimo; il riposo fa parte dell'allenamento."
    else -> "Ti sto conoscendo" to "Servono circa due settimane di dati dall'orologio per capire la tua normalità. Nel frattempo registra sonno e analisi."
}
