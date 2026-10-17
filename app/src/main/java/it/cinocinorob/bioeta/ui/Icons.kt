package it.cinocinorob.bioeta.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icone vettoriali minime (24×24), per non dipendere dal pacchetto icone esteso. */
object BioIcons {
    private fun filled(name: String, d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
            .build()

    private fun stroked(name: String, d: String, w: Float = 2.2f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = w,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()

    val Home = stroked("home", "M4,11l8,-7l8,7v9h-5v-6h-6v6H4z")
    val Heart = filled("heart", "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z")
    val Moon = filled("moon", "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z")
    val Bolt = filled("bolt", "M7,2v11h3v9l7,-12h-4l4,-8z")
    val Drop = filled("drop", "M12,2c-5.33,4.55 -8,8.48 -8,11.8 0,4.98 3.8,8.2 8,8.2s8,-3.22 8,-8.2c0,-3.32 -2.67,-7.25 -8,-11.8z")
    val Sparkle = filled("sparkle", "M12,2l2.3,6.9L21,11.5l-6.7,2.6L12,21l-2.3,-6.9L3,11.5l6.7,-2.6z")
    val Person = filled("person", "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z")
    val Pulse = stroked("pulse", "M2,12h4l3,-7l4,14l3,-7h6")
    val Wind = stroked("wind", "M3,8h10a3,3 0 1,0 -3,-3M3,12h15a3,3 0 1,1 -3,3M3,16h7")
    val Gauge = stroked("gauge", "M4,17a8,8 0 1,1 16,0M12,17l4,-5")
    val Chevron = stroked("chevron", "M9,5l7,7l-7,7", 2.6f)
    val Doc = stroked("doc", "M7,3h7l5,5v13H7zM14,3v5h5M10,13h6M10,17h6")
    val Pencil = stroked("pencil", "M4,20h4L19,9l-4,-4L4,16zM13.5,6.5l4,4")
    val Plus = stroked("plus", "M12,5v14M5,12h14")
    val Info = stroked("info", "M12,3a9,9 0 1,1 0,18a9,9 0 1,1 0,-18M12,11v6M12,7.5v0.5")
    val Refresh = stroked("refresh", "M20,11a8,8 0 1,0 -2.3,5.7M20,4v7h-7")
    /** Freccia "indietro" Material. */
    val Back = filled("back", "M20,11H7.83l5.59,-5.59L12,4l-8,8l8,8l1.41,-1.41L7.83,13H20v-2z")
    val Trash = stroked("trash", "M4,7h16M9,7V4h6v3M6,7l1,13h10l1,-13")
    val Send = filled("send", "M3,20.5l18,-8.5l-18,-8.5l0,6.6l12.5,1.9l-12.5,1.9z")
    val Chat = filled("chat", "M4,4h16a2,2 0 0,1 2,2v10a2,2 0 0,1 -2,2H9l-5,4v-4H4a2,2 0 0,1 -2,-2V6a2,2 0 0,1 2,-2z")
    val Shield = filled("shield", "M12,2l8,3v6c0,5 -3.4,9.4 -8,11c-4.6,-1.6 -8,-6 -8,-11V5z")
    val Stethoscope = stroked("stetho", "M6,3v6a5,5 0 0,0 10,0V3M11,14v2a5,5 0 0,0 10,0v-2M21,14a2,2 0 1,0 0.01,0", 2.0f)
    val Gear = stroked("gear", "M12,9a3,3 0 1,1 0,6a3,3 0 1,1 0,-6M12,2v3M12,19v3M2,12h3M19,12h3M4.9,4.9l2.1,2.1M17,17l2.1,2.1M4.9,19.1l2.1,-2.1M17,7l2.1,-2.1")
}
