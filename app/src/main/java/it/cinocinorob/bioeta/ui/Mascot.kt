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
    Mood.HAPPY -> listOf(Color(0xFF7EE0B5), Color(0xFF3FBF8F))
    Mood.CALM -> listOf(Color(0xFFFFD58A), Color(0xFFFFA95A))
    Mood.TIRED -> listOf(Color(0xFFFFB3B8), Color(0xFFF2788A))
    Mood.SLEEPY -> listOf(Color(0xFFC9BEFF), Color(0xFF8E7CFF))
}

/**
 * "Germoglio": mascotte originale di BioEtà. Un corpo morbido con un germoglio sulla testa;
 * l'espressione segue la prontezza del giorno. Animazione di "respiro" lenta.
 */
@Composable
fun Sprout(mood: Mood, modifier: Modifier = Modifier, sizeDp: Int = 96) {
    val tr = rememberInfiniteTransition(label = "breath")
    val breath by tr.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b",
    )
    val colors = moodColors(mood)
    Canvas(modifier.size(sizeDp.dp)) {
        val w = size.width; val h = size.height
        val squash = 1f + 0.03f * breath
        val bodyW = w * 0.78f * squash
        val bodyH = h * 0.66f / squash
        val left = (w - bodyW) / 2
        val top = h - bodyH - h * 0.06f

        // ombra
        drawOval(Color.Black.copy(alpha = 0.08f), Offset(w * 0.2f, h * 0.9f), Size(w * 0.6f, h * 0.07f))

        // germoglio
        val stemX = w / 2
        val stemTop = top - h * 0.14f
        drawLine(Color(0xFF5DAE7B), Offset(stemX, top + 4f), Offset(stemX, stemTop), strokeWidth = w * 0.035f, cap = StrokeCap.Round)
        val leafL = Path().apply {
            moveTo(stemX, stemTop + h * 0.03f)
            cubicTo(stemX - w * 0.20f, stemTop - h * 0.06f, stemX - w * 0.20f, stemTop + h * 0.08f, stemX, stemTop + h * 0.05f)
        }
        val leafR = Path().apply {
            moveTo(stemX, stemTop + h * 0.02f)
            cubicTo(stemX + w * 0.16f, stemTop - h * 0.10f, stemX + w * 0.22f, stemTop + h * 0.04f, stemX, stemTop + h * 0.04f)
        }
        drawPath(leafL, Color(0xFF7FCF95))
        drawPath(leafR, Color(0xFF6BC287))

        // corpo
        drawRoundRect(
            Brush.verticalGradient(colors, startY = top, endY = top + bodyH),
            Offset(left, top), Size(bodyW, bodyH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyW * 0.48f, bodyH * 0.55f),
        )
        // riflesso
        drawOval(Color.White.copy(alpha = 0.35f), Offset(left + bodyW * 0.14f, top + bodyH * 0.10f), Size(bodyW * 0.22f, bodyH * 0.14f))

        // viso
        val eyeY = top + bodyH * 0.45f
        val eyeDx = bodyW * 0.17f
        val ink = Color(0xFF2B2440)
        val eyeR = w * 0.035f
        when (mood) {
            Mood.SLEEPY, Mood.TIRED -> {
                listOf(-1, 1).forEach { s ->
                    val cx = w / 2 + s * eyeDx
                    drawArc(ink, 0f, 180f, false, Offset(cx - eyeR * 1.3f, eyeY - eyeR), Size(eyeR * 2.6f, eyeR * 1.8f),
                        style = Stroke(w * 0.022f, cap = StrokeCap.Round))
                }
            }
            else -> listOf(-1, 1).forEach { s ->
                val cx = w / 2 + s * eyeDx
                drawOval(ink, Offset(cx - eyeR, eyeY - eyeR * 1.3f), Size(eyeR * 2, eyeR * 2.6f))
                drawCircle(Color.White, eyeR * 0.38f, Offset(cx + eyeR * 0.3f, eyeY - eyeR * 0.6f))
            }
        }
        // guance
        drawOval(Color(0xFFFF8FA3).copy(alpha = 0.45f), Offset(w / 2 - eyeDx * 1.9f, eyeY + eyeR * 1.2f), Size(eyeR * 2.6f, eyeR * 1.4f))
        drawOval(Color(0xFFFF8FA3).copy(alpha = 0.45f), Offset(w / 2 + eyeDx * 1.9f - eyeR * 2.6f, eyeY + eyeR * 1.2f), Size(eyeR * 2.6f, eyeR * 1.4f))
        // bocca
        val my = eyeY + bodyH * 0.17f
        val mw = bodyW * 0.16f
        when (mood) {
            Mood.HAPPY -> drawArc(ink, 10f, 160f, false, Offset(w / 2 - mw / 2, my - mw * 0.45f), Size(mw, mw * 0.8f),
                style = Stroke(w * 0.022f, cap = StrokeCap.Round))
            Mood.CALM -> drawLine(ink, Offset(w / 2 - mw * 0.3f, my), Offset(w / 2 + mw * 0.3f, my), strokeWidth = w * 0.022f, cap = StrokeCap.Round)
            Mood.TIRED -> drawArc(ink, 200f, 140f, false, Offset(w / 2 - mw * 0.4f, my - mw * 0.1f), Size(mw * 0.8f, mw * 0.6f),
                style = Stroke(w * 0.022f, cap = StrokeCap.Round))
            Mood.SLEEPY -> drawOval(ink, Offset(w / 2 - mw * 0.14f, my - mw * 0.12f), Size(mw * 0.28f, mw * 0.32f))
        }
        if (mood == Mood.SLEEPY) {
            // piccole "z"
            val zx = left + bodyW * 0.95f; val zy = top + bodyH * 0.05f - 6f * breath
            val zs = w * 0.07f
            val z = Path().apply { moveTo(zx, zy); lineTo(zx + zs, zy); lineTo(zx, zy + zs); lineTo(zx + zs, zy + zs) }
            drawPath(z, Color(0xFF8E7CFF), style = Stroke(w * 0.018f, cap = StrokeCap.Round))
        }
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
