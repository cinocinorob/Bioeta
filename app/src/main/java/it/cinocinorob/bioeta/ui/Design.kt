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

// ======================= Design system BioEtà — «Pastello» (Material You) =======================
//
// Linguaggio di Google (Material 3 «Expressive», app Fitbit / Google Health):
// - superfici chiare e morbide, angoli molto arrotondati (28 dp le schede, 20 dp i riquadri);
// - ogni area della salute ha un colore: icona piena su cerchio pastello e, nei riquadri del cruscotto,
//   sfondo tonale dello stesso colore (contenitore «tonal» di Material 3), così si riconosce senza leggere;
// - marchio verde‑acqua (vita, crescita), unico colore delle azioni;
// - tipografia unica: Google Sans Flex (OFL), il carattere delle app Google; i numeri usano l'asse di
//   rotondità (ROND) e la dimensione ottica grande, come i numeri di Fitbit.
// Tema predefinito: segue il sistema. Contrasti: testo principale ≥ 13:1, secondario ≥ 7:1 sullo sfondo.

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
    /** Colore di marca nel tema corrente (azioni, selezione). */
    val brand: Color = Color(0xFF00897B),
    /** Testo sopra il colore di marca. */
    val onBrand: Color = Color.White,
) {
    /** Contenitore pastello di un colore di dominio (Material 3 «tonal container»). */
    fun tone(c: Color, strength: Float = 1f): Color = lerp(card, c, (if (dark) 0.15f else 0.13f) * strength)
    /** Cerchio dell'icona: pastello più intenso. */
    fun toneStrong(c: Color): Color = lerp(card, c, if (dark) 0.34f else 0.24f)
    /** Colore di testo/icona sopra il contenitore pastello: più scuro in chiaro, più chiaro in scuro. */
    fun ink(c: Color): Color = if (dark) lerp(c, Color.White, 0.38f) else lerp(c, Color.Black, 0.30f)
}

private fun lerp(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

val LightPalette = Palette(
    bg = Color(0xFFF2F4F9), card = Color(0xFFFFFFFF), text = Color(0xFF181C22),
    text2 = Color(0xFF474D59), sep = Color(0xFFE1E5EE), field = Color(0xFFE9EDF4), dark = false,
    card2 = Color(0xFFF0F2F8), border = Color(0x14181C22), text3 = Color(0xFF6F7583),
    brand = Color(0xFF00796B), onBrand = Color.White,
)
val DarkPalette = Palette(
    bg = Color(0xFF111318), card = Color(0xFF1C1F26), text = Color(0xFFE6E8EF),
    text2 = Color(0xFFB4B9C6), sep = Color(0xFF2D313B), field = Color(0xFF282C35), dark = true,
    card2 = Color(0xFF242832), border = Color(0x1AFFFFFF), text3 = Color(0xFF8A90A0),
    brand = Color(0xFF5DD9C4), onBrand = Color(0xFF00382F),
)

/**
 * Colori di dominio: tono medio, leggibile come icona e come linea sia su bianco sia su scuro.
 * I contenitori pastello si ricavano con [Palette.tone].
 */
object Tint {
    val brand = Color(0xFF00A38C)    // marchio: verde‑acqua
    val heart = Color(0xFFE5484D)    // FC, cuore
    val hrv = Color(0xFF12A594)      // HRV, sistema nervoso autonomo
    val sleep = Color(0xFF7A5AF8)    // sonno
    val activity = Color(0xFFF2711C) // passi, movimento
    val load = Color(0xFFE8590C)     // carico cardiaco
    val resp = Color(0xFF1C8CE6)
    val spo2 = Color(0xFF4263EB)
    val body = Color(0xFF9C4FD6)
    val blood = Color(0xFFD6336C)
    val bio = Color(0xFF2B9A66)      // età biologica
    val vo2 = Color(0xFFEF6C00)
    val blue = Color(0xFF3B6FE0)     // analisi, link
    val good = Color(0xFF2B9A66)
    val bad = Color(0xFFDC3545)
    val warn = Color(0xFFC77700)
    val bp = Color(0xFFC2398A)
    val mood = Color(0xFFD99400)     // umore
}

val LocalPalette = staticCompositionLocalOf { LightPalette }

enum class ThemeMode(val label: String) { DARK("Scuro"), LIGHT("Chiaro"), SYSTEM("Sistema") }

/** Google Sans Flex (SIL OFL 1.1, file in res/font): assi peso, larghezza, dimensione ottica, rotondità. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun gsf(w: Int, opsz: Float = 18f, rond: Float = 0f) = androidx.compose.ui.text.font.Font(
    it.cinocinorob.bioeta.R.font.google_sans_flex, weight = FontWeight(w),
    variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
        androidx.compose.ui.text.font.FontVariation.weight(w),
        androidx.compose.ui.text.font.FontVariation.Setting("opsz", opsz),
        androidx.compose.ui.text.font.FontVariation.Setting("ROND", rond),
    ),
)

/** Testo, titoli, etichette. */
val AppFont = androidx.compose.ui.text.font.FontFamily(
    gsf(400), gsf(500), gsf(600), gsf(700), gsf(800),
)

/** Numeri grandi: dimensione ottica da titolo e terminazioni arrotondate. */
val DisplayFont = androidx.compose.ui.text.font.FontFamily(
    gsf(500, 72f, 100f), gsf(600, 72f, 100f), gsf(700, 72f, 100f), gsf(800, 72f, 100f),
)

/** Nome storico usato nel codice: punta al carattere dell'app. */
val Manrope = AppFont

/** Cifre a larghezza fissa: i numeri non "ballano" durante le animazioni. */
const val TABULAR = "tnum"

private fun bioTypography(): Typography {
    val base = Typography()
    fun TextStyle.m(w: FontWeight? = null, ls: Double? = null) =
        copy(fontFamily = AppFont, fontWeight = w ?: fontWeight, letterSpacing = ls?.sp ?: letterSpacing)
    return Typography(
        displayLarge = base.displayLarge.m(FontWeight.Bold, -0.5).copy(fontFamily = DisplayFont),
        displayMedium = base.displayMedium.m(FontWeight.Bold, -0.4).copy(fontFamily = DisplayFont),
        displaySmall = base.displaySmall.m(FontWeight.Bold, -0.3).copy(fontFamily = DisplayFont),
        headlineLarge = base.headlineLarge.m(FontWeight.SemiBold, -0.3), headlineMedium = base.headlineMedium.m(FontWeight.SemiBold, -0.2),
        headlineSmall = base.headlineSmall.m(FontWeight.SemiBold, -0.1),
        titleLarge = base.titleLarge.m(FontWeight.SemiBold, 0.0), titleMedium = base.titleMedium.m(FontWeight.SemiBold, 0.0), titleSmall = base.titleSmall.m(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.m(FontWeight.Normal), bodyMedium = base.bodyMedium.m(FontWeight.Normal), bodySmall = base.bodySmall.m(FontWeight.Normal),
        labelLarge = base.labelLarge.m(FontWeight.Medium), labelMedium = base.labelMedium.m(FontWeight.Medium), labelSmall = base.labelSmall.m(FontWeight.Medium),
    )
}

private fun schemeFor(p: Palette) = if (p.dark) darkColorScheme(
    primary = p.brand, onPrimary = p.onBrand, primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFF9FF2E2),
    secondary = Color(0xFFB2CCC6), onSecondary = Color(0xFF1D3531), secondaryContainer = Color(0xFF2B3A40), onSecondaryContainer = Color(0xFFCDE8E1),
    tertiary = Color(0xFFC8BFFF), tertiaryContainer = Color(0xFF3A2F7A),
    background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
    surfaceVariant = p.card2, onSurfaceVariant = p.text2,
    surfaceContainerLowest = p.bg, surfaceContainerLow = p.card, surfaceContainer = p.card, surfaceContainerHigh = p.card2, surfaceContainerHighest = Color(0xFF30343F),
    outline = Color(0xFF5A606C), outlineVariant = p.sep, error = Color(0xFFFF8A80), onError = Color(0xFF5C0A0A),
    inverseSurface = Color(0xFFE6E8EF), inverseOnSurface = Color(0xFF181C22),
) else lightColorScheme(
    primary = p.brand, onPrimary = p.onBrand, primaryContainer = Color(0xFFC5F1E8), onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635E), onSecondary = Color.White, secondaryContainer = Color(0xFFDCE8F4), onSecondaryContainer = Color(0xFF13202B),
    tertiary = Color(0xFF5B4CC4), tertiaryContainer = Color(0xFFE6E0FF),
    background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
    surfaceVariant = p.card2, onSurfaceVariant = p.text2,
    surfaceContainerLowest = Color.White, surfaceContainerLow = p.card, surfaceContainer = Color(0xFFEDF0F6), surfaceContainerHigh = p.card2, surfaceContainerHighest = Color(0xFFE2E6EE),
    outline = Color(0xFF8A90A0), outlineVariant = p.sep, error = Color(0xFFC62828), onError = Color.White,
)

@Composable
fun BioTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    val p = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = schemeFor(p), typography = bioTypography(),
            shapes = androidx.compose.material3.Shapes(
                extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp),
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
            .clip(RoundedCornerShape(28.dp))
            .background(p.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(p.toneStrong(tint)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = p.ink(tint), modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    title, color = p.text, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (trailing != null) Text(trailing, color = p.text2, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                if (info != null) {
                    androidx.compose.material3.IconButton(onClick = { showInfo = true }, modifier = Modifier.size(32.dp)) {
                        Icon(BioIcons.Info, "Come è calcolato", tint = p.text3, modifier = Modifier.size(17.dp))
                    }
                }
                if (onClick != null) {
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(28.dp).clip(CircleShape).background(p.card2), contentAlignment = Alignment.Center) {
                        Icon(BioIcons.Chevron, null, tint = p.text2, modifier = Modifier.size(12.dp))
                    }
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
            value, fontSize = (size * 1.12).sp, fontWeight = FontWeight.SemiBold, color = color ?: p.text,
            style = TextStyle(fontFamily = DisplayFont, letterSpacing = (-0.2).sp, fontFeatureSettings = TABULAR), lineHeight = (size + 4).sp,
            maxLines = 1,
        )
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(unit, fontSize = (size * 0.45).coerceAtLeast(13.0).sp, color = p.text2, fontWeight = FontWeight.Medium,
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
        Modifier.clip(RoundedCornerShape(50)).background(LocalPalette.current.toneStrong(color))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text, color = LocalPalette.current.ink(color), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Gruppo di righe su card unica con separatori rientrati (lista "inset grouped"). */
@Composable
fun InsetGroup(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(p.card).padding(vertical = 6.dp),
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
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, color = p.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = p.text2, maxLines = 2)
            }
            if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }

        }
        if (showDivider) HorizontalDivider(Modifier.padding(start = 18.dp, end = 18.dp), color = p.sep, thickness = 1.dp)
    }
}

/** Icona dentro un quadratino arrotondato colorato (stile Impostazioni/Salute). */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Int = 30) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(LocalPalette.current.toneStrong(tint)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = LocalPalette.current.ink(tint), modifier = Modifier.size((size * 0.54).dp))
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
            .clip(RoundedCornerShape(32.dp))
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
