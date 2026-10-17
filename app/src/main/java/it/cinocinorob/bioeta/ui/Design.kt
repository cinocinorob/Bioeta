package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
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

// ======================= Design system BioEtà =======================
//
// Identità: "inchiostro" scuro come tema predefinito (ispirazione: app di benessere premium),
// superfici leggermente rialzate con bordo sottile invece delle ombre, un colore per ogni dominio
// fisiologico usato in modo coerente in tutta l'app (anelli, grafici, icone, badge).
// Contrasti: testo principale ≥ 12:1, testo secondario ≥ 6:1 sullo sfondo (WCAG AA ampiamente rispettato).

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
)

val DarkPalette = Palette(
    bg = Color(0xFF0A0E13), card = Color(0xFF121820), text = Color(0xFFF1F4F8),
    text2 = Color(0xFF9AA7B6), sep = Color(0xFF222B36), field = Color(0xFF1A222D), dark = true,
    card2 = Color(0xFF1A222D), border = Color(0x14FFFFFF), text3 = Color(0xFF6E7A88),
)
val LightPalette = Palette(
    bg = Color(0xFFF5F4F1), card = Color(0xFFFFFFFF), text = Color(0xFF10151B),
    text2 = Color(0xFF5B6572), sep = Color(0xFFE6E3DE), field = Color(0xFFF0EEEA), dark = false,
    card2 = Color(0xFFF2F0EC), border = Color(0x12000000), text3 = Color(0xFF8A929C),
)

/** Colori di dominio (identici in tema chiaro e scuro: saturazione media, leggibili su entrambi). */
object Tint {
    val brand = Color(0xFF3ED69A)    // menta BioEtà
    val heart = Color(0xFFFF6B88)    // FC, cuore
    val hrv = Color(0xFF45C8E0)      // HRV, sistema nervoso autonomo
    val sleep = Color(0xFF9D8BFF)    // sonno
    val activity = Color(0xFFFFAA4C) // passi, movimento
    val load = Color(0xFFFF7A5C)     // carico cardiaco
    val resp = Color(0xFF4FB8E8)
    val spo2 = Color(0xFF5B8DEF)
    val body = Color(0xFFC08BFF)
    val blood = Color(0xFFF2617A)
    val bio = Color(0xFF3ED69A)      // età biologica
    val vo2 = Color(0xFFFF8A5B)
    val blue = Color(0xFF6E9BFF)     // analisi, link
    val good = Color(0xFF3ED69A)
    val bad = Color(0xFFFF5D73)
    val warn = Color(0xFFFFB547)
    val bp = Color(0xFFE85D9A)
}

val LocalPalette = staticCompositionLocalOf { DarkPalette }

enum class ThemeMode(val label: String) { DARK("Scuro"), LIGHT("Chiaro"), SYSTEM("Come il sistema") }

/** Manrope (SIL OFL 1.1, file in res/font): font variabile, pesi selezionati via asse "wght". */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun manrope(w: Int) = androidx.compose.ui.text.font.Font(
    it.cinocinorob.bioeta.R.font.manrope, weight = FontWeight(w),
    variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(w)),
)

val Manrope = androidx.compose.ui.text.font.FontFamily(
    manrope(400), manrope(500), manrope(600), manrope(700), manrope(800),
)

/** Cifre a larghezza fissa: i numeri non "ballano" durante le animazioni. */
const val TABULAR = "tnum"

private fun bioTypography(): Typography {
    val base = Typography()
    fun TextStyle.m(w: FontWeight? = null, ls: Double? = null) =
        copy(fontFamily = Manrope, fontWeight = w ?: fontWeight, letterSpacing = ls?.sp ?: letterSpacing)
    return Typography(
        displayLarge = base.displayLarge.m(FontWeight.ExtraBold, -1.0), displayMedium = base.displayMedium.m(FontWeight.ExtraBold, -0.8),
        displaySmall = base.displaySmall.m(FontWeight.ExtraBold, -0.6), headlineLarge = base.headlineLarge.m(FontWeight.ExtraBold, -0.5),
        headlineMedium = base.headlineMedium.m(FontWeight.ExtraBold, -0.4), headlineSmall = base.headlineSmall.m(FontWeight.Bold, -0.2),
        titleLarge = base.titleLarge.m(FontWeight.Bold, -0.2), titleMedium = base.titleMedium.m(FontWeight.Bold), titleSmall = base.titleSmall.m(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.m(FontWeight.Medium), bodyMedium = base.bodyMedium.m(FontWeight.Medium), bodySmall = base.bodySmall.m(FontWeight.Medium),
        labelLarge = base.labelLarge.m(FontWeight.SemiBold), labelMedium = base.labelMedium.m(FontWeight.SemiBold), labelSmall = base.labelSmall.m(FontWeight.SemiBold),
    )
}

private fun schemeFor(p: Palette) = if (p.dark) darkColorScheme(
    primary = Tint.brand, onPrimary = Color(0xFF00281A), primaryContainer = Color(0xFF0F3A2A), onPrimaryContainer = Color(0xFFB4F5D9),
    secondary = Tint.sleep, onSecondary = Color(0xFF1B1446), secondaryContainer = Color(0xFF2A2350), onSecondaryContainer = Color(0xFFE2DCFF),
    tertiary = Tint.hrv, tertiaryContainer = Color(0xFF0E3A44),
    background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
    surfaceVariant = p.card2, onSurfaceVariant = p.text2,
    surfaceContainerLowest = p.bg, surfaceContainerLow = p.card, surfaceContainer = p.card, surfaceContainerHigh = p.card2, surfaceContainerHighest = Color(0xFF232C38),
    outline = Color(0xFF3A4654), outlineVariant = p.sep, error = Tint.bad, onError = Color.White,
    inverseSurface = Color(0xFFE8ECF1), inverseOnSurface = Color(0xFF10151B),
) else lightColorScheme(
    primary = Color(0xFF0E8F62), onPrimary = Color.White, primaryContainer = Color(0xFFC9F3E0), onPrimaryContainer = Color(0xFF00281A),
    secondary = Color(0xFF6A55E0), onSecondary = Color.White, secondaryContainer = Color(0xFFE6E0FF), onSecondaryContainer = Color(0xFF1B1446),
    tertiary = Color(0xFF167E93), tertiaryContainer = Color(0xFFCDEFF6),
    background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
    surfaceVariant = p.card2, onSurfaceVariant = p.text2,
    surfaceContainerLowest = Color.White, surfaceContainerLow = p.card, surfaceContainer = p.card, surfaceContainerHigh = p.card2, surfaceContainerHighest = Color(0xFFE8E5E0),
    outline = Color(0xFFB9C0C8), outlineVariant = p.sep, error = Color(0xFFD0364F), onError = Color.White,
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
                large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
            ),
        ) {
            CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides TextStyle(fontFamily = Manrope)) { content() }
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(p.card)
            .border(1.dp, p.border, RoundedCornerShape(24.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    title, color = p.text, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (trailing != null) Text(trailing, color = p.text2, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                if (onClick != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(BioIcons.Chevron, null, tint = p.text3, modifier = Modifier.size(14.dp))
                }
            }
        }
        content()
    }
}

/** Valore grande + unità piccola grigia. */
@Composable
fun BigValue(value: String, unit: String? = null, size: Int = 32, color: Color? = null) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value, fontSize = size.sp, fontWeight = FontWeight.ExtraBold, color = color ?: p.text,
            style = TextStyle(fontFamily = Manrope, letterSpacing = (-0.6).sp, fontFeatureSettings = TABULAR), lineHeight = (size + 2).sp,
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.card).border(1.dp, p.border, RoundedCornerShape(24.dp)),
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
        Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.16f)),
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
