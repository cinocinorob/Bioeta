package it.cinocinorob.bioeta.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import it.cinocinorob.bioeta.MainActivity

/** Valori mostrati dal widget, salvati dall'app a ogni lettura di Connessione Salute. */
object WidgetData {
    private const val P = "widget"
    data class Snap(val readiness: Int?, val sleep: Int?, val steps: Long?, val stepGoal: Int, val load: Double?, val at: Long)

    fun save(ctx: Context, readiness: Int?, sleep: Int?, steps: Long?, stepGoal: Int, load: Double?) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putInt("r", readiness ?: -1).putInt("s", sleep ?: -1).putLong("st", steps ?: -1)
            .putInt("g", stepGoal).putFloat("l", (load ?: -1.0).toFloat()).putLong("at", System.currentTimeMillis())
            .apply()
    }

    fun load(ctx: Context): Snap {
        val p = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        fun i(k: String) = p.getInt(k, -1).takeIf { it >= 0 }
        return Snap(i("r"), i("s"), p.getLong("st", -1).takeIf { it >= 0 }, p.getInt("g", 9000),
            p.getFloat("l", -1f).takeIf { it >= 0 }?.toDouble(), p.getLong("at", 0))
    }
}

class BioWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetData.load(context)
        provideContent { GlanceTheme { Content(snap) } }
    }

    @Composable
    private fun Content(s: WidgetData.Snap) {
        val c = GlanceTheme.colors
        Column(
            GlanceModifier.fillMaxSize().background(c.widgetBackground).cornerRadius(24.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("BioEtà · oggi", style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Tile("Recupero", s.readiness?.toString() ?: "—", Color(0xFF2FB98A), GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                Tile("Sonno", s.sleep?.toString() ?: "—", Color(0xFF8E7CFF), GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                Tile("Carico", s.load?.let { "%.0f".format(it) } ?: "—", Color(0xFFFF8A5B), GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(10.dp))
            val steps = s.steps ?: 0
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("%,d passi".format(steps).replace(',', '.'), style = TextStyle(color = c.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                Text("obiettivo ${"%,d".format(s.stepGoal).replace(',', '.')}", style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp))
            }
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = (steps.toFloat() / s.stepGoal).coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
                color = ColorProvider(Color(0xFFFFA14A)),
                backgroundColor = c.surfaceVariant,
            )
        }
    }

    @Composable
    private fun Tile(label: String, value: String, tint: Color, modifier: GlanceModifier) {
        Column(
            modifier.background(ColorProvider(tint.copy(alpha = 0.16f))).cornerRadius(16.dp).padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            Text(label, style = TextStyle(color = ColorProvider(tint), fontSize = 11.sp, fontWeight = FontWeight.Medium))
        }
    }
}

class BioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BioWidget()
}
