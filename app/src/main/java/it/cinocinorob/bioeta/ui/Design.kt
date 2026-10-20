package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ======================= Design system BioEtà — «Anelli» =======================
//
// Identità: l'età scritta ad anelli, come negli anelli di accrescimento di un tronco; il cuore è il marchio.
// - Notte (predefinito): petrolio profondo invece del nero, così il rosa del battito e i colori dei dati
//   restano leggibili senza l'effetto «schermo spento».
// - Giorno: grigio‑acqua chiaro, superfici bianche.
// - Un solo colore di marca (Battito, rosa‑corallo); i colori di dominio servono solo a riconoscere i dati.
// - Tipografia unica: Archivo (variabile in peso e larghezza). I numeri grandi usano la larghezza
//   condensata, come una lettura di strumento; testi e titoli la larghezza normale.
// Contrasti: testo principale ≥ 13:1, secondario ≥ 6,5:1 sullo sfondo in entrambi i temi.

data class Palette(
    val bg: Color,
    val card: Color,
    val text: Color,
    val text2: Color,
    val sep: Color,
    val field: Color,
    val dark: Boolean,
    /** Superficie rialzata (chip, campi, celle interne). */
    val card2: Color = field,
    /** Bordo sottile delle card. */
    val border: Color = sep,
    /** Testo terziario (etichette assi, note). */
    val text3: Color = text2,
    /** Colore di marca nel tema corrente. */
    val brand: Color = Color(0xFFFF6F8A),
)

val DarkPalette = Palette(
    bg = Color(0xFF0C1C20), card = Color(0xFF132A2F), text = Color(0xFFEAF4F2),
    text2 = Color(0xFF9BB5B3), sep = Color(0xFF22424A), field = Color(0xFF1A363C), dark = true,
    card2 = Color(0xFF1A363C), border = Color(0x1AFFFFFF), text3 = Color(0xFF6C8987),
    brand = Color(0xFFFF6F8A),
)
val LightPalette = Palette(
    bg = Color(0xFFEDF3F2), card = Color(0xFFFFFFFF), text = Color(0xFF0C1C20),
    text2 = Color(0xFF4C6563), sep = Color(0xFFD5E2E0), field = Color(0xFFE2EBE9), dark = false,
    card2 = Color(0xFFE6EEEC), border = Color(0x140C1C20), text3 = Color(0xFF7D9492),
    brand = Color(0xFFDE3F63),
)

/** Colori di dominio (identici in tema chiaro e scuro: saturazione media, leggibili su entrambi). */
object Tint {
    val brand = Color(0xFFFF6F8A)    // Battito: marchio e cuore
    val heart = Color(0xFFFF6F8A)    // FC, cuore
    val hrv = Color(0xFF4FD1C5)      // HRV, sistema nervoso autonomo
    val sleep = Color(0xFF9AA5FF)    // sonno
    val activity = Color(0xFFF5B04E) // passi, movimento
    val load = Color(0xFFFF8A5C)     // carico cardiaco
    val resp = Color(0xFF6CC4E8)
    val spo2 = Color(0xFF7DA2FF)
    val body = Color(0xFFC29BFF)
    val blood = Color(0xFFFF6F8A)
    val bio = Color(0xFF66D9A6)      // età biologica
    val vo2 = Color(0xFFFF9B6A)
    val blue = Color(0xFF7FB2FF)     // analisi, link
    val good = Color(0xFF66D9A6)
    val bad = Color(0xFFFF5A4F)
    val warn = Color(0xFFF5B94E)
    val bp = Color(0xFFE879B0)
    val mood = Color(0xFFF7C85C)     // umore
}

val LocalPalette = staticCompositionLocalOf { DarkPalette }

enum class ThemeMode(val label: String) { DARK("Scuro"), LIGHT("Chiaro"), SYSTEM("Sistema") }

/** Archivo (SIL OFL 1.1, file in res/font): font variabile, assi peso ("wght") e larghezza ("wdth"). */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun archivo(w: Int, width: Float = 100f) = androidx.compose.ui.text.font.Font(
    it.cinocinorob.bioeta.R.font.archivo, weight = FontWeight(w),
    variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
        androidx.compose.ui.text.font.FontVariation.weight(w),
        androidx.compose.ui.text.font.FontVariation.width(width),
    ),
)

/** Testo, titoli, etichette. */
val AppFont = androidx.compose.ui.text.font.FontFamily(
    archivo(400), archivo(500), archivo(600), archivo(700), archivo(800),
)

/** Numeri grandi: larghezza condensata, come la lettura di uno strumento. */
val DisplayFont = androidx.compose.ui.text.font.FontFamily(
    archivo(500, 74f), archivo(600, 74f), archivo(700, 74f), archivo(800, 72f),
)

/** Nome storico usato nel codice: ora punta ad Archivo. */
val Manrope = AppFont

/** Cifre a larghezza fissa: i numeri non "ballano" durante le animazioni. */
const val TABULAR = "tnum"

private fun bioTypography(): Typography {
    val base = Typography()
    fun TextStyle.m(w: FontWeight? = null, ls: Double? = null) =
        copy(fontFamily = AppFont, fontWeight = w ?: fontWeight, letterSpacing = ls?.sp ?: letterSpacing)
    return Typography(
        displayLarge = base.displayLarge.m(FontWeight.ExtraBold, -1.0).copy(fontFamily = DisplayFont),
        displayMedium = base.displayMedium.m(FontWeight.ExtraBold, -0.8).copy(fontFamily = DisplayFont),
        displaySmall = base.displaySmall.m(FontWeight.ExtraBold, -0.6).copy(fontFamily = DisplayFont),
        headlineLarge = base.headlineLarge.m(FontWeight.Bold, -0.6), headlineMedium = base.headlineMedium.m(FontWeight.Bold, -0.5),
        headlineSmall = base.headlineSmall.m(FontWeight.Bold, -0.3),
        titleLarge = base.titleLarge.m(FontWeight.Bold, -0.3), titleMedium = base.titleMedium.m(FontWeight.SemiBold, -0.1), titleSmall = base.titleSmall.m(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.m(FontWeight.Normal), bodyMedium = base.bodyMedium.m(FontWeight.Normal), bodySmall = base.bodySmall.m(FontWeight.Normal),
        labelLarge = base.labelLarge.m(FontWeight.SemiBold), labelMedium = base.labelMedium.m(FontWeight.Medium), labelSmall = base.labelSmall.m(FontWeight.Medium),
    )
}

private fun schemeFor(p: Palette) = if (p.dark) darkColorScheme(
    primary = p.brand, onPrimary = Color(0xFF3A0714), primaryContainer = Color(0xFF4A1A26), onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = Tint.hrv, onSecondary = Color(0xFF00302B), secondaryContainer = Color(0xFF184844), onSecondaryContainer = Color(0xFFC6F4EE),
    tertiary = Tint.sleep, tertiaryContainer = Color(0xFF2B3160),
    background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
    surfaceVariant = p.card2, onSurfaceVariant = p.text2,
    surfaceContainerLowest = p.bg, surfaceContainerLow = p.card, surfaceContainer = p.card, surfaceContainerHigh = p.card2, surfaceContainerHighest = Color(0xFF224149),
    outline = Color(0xFF3C5E64), outlineVariant = p.sep, error = Tint.bad, onError = Color.White,
    inverseSurface = Color(0xFFE6F0EE), inverseOnSurface = Color(0xFF0C1C20),
) else lightColorScheme(
    primary = p.brand, onPrimary = Color.White, primaryContainer = Color(0xFFFFD9E0), onPrimaryContainer = Color(0xFF3A0714),
    secondary = Color(0xFF0F7F75), onSecondary = Color.White, secondaryContainer = Color(0xFFC6F0EA), onSecondaryContainer = Color(0xFF00302B),
    tertiary = Color(0xFF4B55C7), tertiaryContainer = Color(0xFFDFE2FF),
    background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
    surfaceVariant = p.card2, onSurfaceVariant = p.text2,
    surfaceContainerLowest = Color.White, surfaceContainerLow = p.card, surfaceContainer = p.card, surfaceContainerHigh = p.card2, surfaceContainerHighest = Color(0xFFD9E4E2),
    outline = Color(0xFFA9BCBA), outlineVariant = p.sep, error = Color(0xFFD1382F), onError = Color.White,
)

@Composable
fun BioTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    val p = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = schemeFor(p), typography = bioTypography(),
            shapes = androidx.compose.material3.Shapes(
                extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
            ),
        ) {
            CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides TextStyle(fontFamily = AppFont)) { content() }
        }
    }
}

// ======================= Formattazione =======================

fun Double.f(decimals: Int): String = String.format(Locale.ITALY, "%.${decimals}f", this)
fun Double.fs(decimals: Int): String = String.format(Locale.ITALY, "%+.${decimals}f", this).replace('-', '−')

private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
fun LocalDate.itDate(): String = format(DATE_FMT)
fun LocalDate.itLong(): String = format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN))

fun parseNum(s: String): Double? = s.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

fun epochLabel(x: Double): String =
    LocalDate.ofEpochDay(x.toLong()).format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN))

// ======================= Componenti =======================

/** Intestazione grande in stile iOS, con data in maiuscoletto sopra. */
@Composable
fun LargeHeader(title: String, overline: String? = null) {
    if (LocalHideHeader.current) return
    val p = LocalPalette.current
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = p.text)
        if (overline != null) Text(overline, style = MaterialTheme.typography.bodyMedium, color = p.text2)
    }
}

/** Nelle schede principali il titolo è nella LargeTopAppBar: l'intestazione nel contenuto si nasconde. */
val LocalHideHeader = androidx.compose.runtime.compositionLocalOf { false }

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
        color = LocalPalette.current.text,
    )
}

/** Card del design system: superficie rialzata, bordo sottile, intestazione con icona nel colore di dominio. */
@Composable
fun HealthCard(
    title: String? = null,
    icon: ImageVector? = null,
    tint: Color = Tint.blue,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Spiegazione del metodo: mostrata su richiesta (icona ⓘ) invece che come testo fisso nella scheda. */
    info: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    var showInfo by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (showInfo && info != null) InfoDialog(title ?: "Informazioni", info) { showInfo = false }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title, color = p.text, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (trailing != null) Text(trailing, color = p.text2, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                if (info != null) {
                    androidx.compose.material3.IconButton(onClick = { showInfo = true }, modifier = Modifier.size(32.dp)) {
                        Icon(BioIcons.Info, "Come è calcolato", tint = p.text3, modifier = Modifier.size(17.dp))
                    }
                }
                if (onClick != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(BioIcons.Chevron, null, tint = p.text3, modifier = Modifier.size(14.dp))
                }
            }
        }
        content()
    }
}

/** Finestra con la spiegazione di un parametro o di un metodo di calcolo. */
@Composable
fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.androidxVerticalScroll()) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

@Composable
internal fun Modifier.androidxVerticalScroll(): Modifier =
    this.verticalScroll(androidx.compose.foundation.rememberScrollState())

/** Valore grande + unità piccola grigia. */
@Composable
fun BigValue(value: String, unit: String? = null, size: Int = 32, color: Color? = null) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value, fontSize = (size * 1.12).sp, fontWeight = FontWeight.Bold, color = color ?: p.text,
            style = TextStyle(fontFamily = DisplayFont, letterSpacing = (-0.4).sp, fontFeatureSettings = TABULAR), lineHeight = (size + 4).sp,
            maxLines = 1,
        )
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(unit, fontSize = (size * 0.5).coerceAtLeast(13.0).sp, color = p.text2, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = (size * 0.12).dp))
        }
    }
}

@Composable
fun Caption(text: String, color: Color? = null) {
    Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = color ?: LocalPalette.current.text2)
}

@Composable
fun Body(text: String, color: Color? = null, weight: FontWeight = FontWeight.Normal) {
    Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp), color = color ?: LocalPalette.current.text, fontWeight = weight)
}

/** Capsula colorata (es. "−7,2 anni"). */
@Composable
fun Pill(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Gruppo di righe su card unica con separatori rientrati (lista "inset grouped"). */
@Composable
fun InsetGroup(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card),
        content = content,
    )
}

@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    Column {
        Row(
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, color = p.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = p.text2, maxLines = 2)
            }
            if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }

        }
        if (showDivider) HorizontalDivider(Modifier.padding(start = 16.dp), color = p.sep, thickness = 0.5.dp)
    }
}

/** Icona dentro un quadratino arrotondato colorato (stile Impostazioni/Salute). */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Int = 30) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.56).dp))
    }
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))


/** Ombra morbida e diffusa (solo in modalità chiara). */
fun Modifier.softShadow(dark: Boolean): Modifier =
    this

/** Card con sfondo a gradiente per i contenuti principali. */
@Composable
fun GradientHero(colors: List<Color>, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(30.dp), clip = false, ambientColor = colors.first().copy(alpha = 0.35f), spotColor = colors.first().copy(alpha = 0.35f))
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(colors))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * Valore animato verso [target]. In anteprima/rendering statico (LocalInspectionMode) restituisce
 * subito il valore finale, così grafici e anelli sono verificabili senza animazione.
 */
@Composable
fun animatedValue(target: Float, durationMs: Int = 900, restartKey: Any? = null): Float {
    if (androidx.compose.ui.platform.LocalInspectionMode.current) return target
    val a = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(target, restartKey) {
        if (restartKey != null) a.snapTo(0f)
        a.animateTo(target, androidx.compose.animation.core.tween(durationMs))
    }
    return a.value
}
