package it.cinocinorob.bioeta.ui

import androidx.compose.foundation.background
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

// ======================= Palette (calda e gentile, pastello) =======================

data class Palette(
    val bg: Color,
    val card: Color,
    val text: Color,
    val text2: Color,
    val sep: Color,
    val field: Color,
    val dark: Boolean,
)

private val LightPalette = Palette(
    bg = Color(0xFFF7F3EC), card = Color(0xFFFFFFFF), text = Color(0xFF221F2E),
    text2 = Color(0xFF8E8899), sep = Color(0xFFECE6DC), field = Color(0xFFF3EEE6), dark = false,
)
private val DarkPalette = Palette(
    bg = Color(0xFF141318), card = Color(0xFF211F28), text = Color(0xFFF4F1FA),
    text2 = Color(0xFF9C97A8), sep = Color(0xFF34313D), field = Color(0xFF2B2833), dark = true,
)

/** Colori per categoria: tinte pastello sature quanto basta per restare leggibili. */
object Tint {
    val heart = Color(0xFFFF6B7D)
    val hrv = Color(0xFFFF7FA3)
    val sleep = Color(0xFF8E7CFF)
    val activity = Color(0xFFFF9F5A)
    val resp = Color(0xFF4FB8E8)
    val spo2 = Color(0xFF5B8DEF)
    val body = Color(0xFFB57BEE)
    val blood = Color(0xFFF2617A)
    val bio = Color(0xFF3FBF8F)
    val vo2 = Color(0xFFFF8A5B)
    val blue = Color(0xFF5B7CF0)
    val good = Color(0xFF3FBF8F)
    val bad = Color(0xFFF2617A)
    val warn = Color(0xFFF5A524)
    val bp = Color(0xFFE85D9A)
}

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Schema di riserva (quando i colori dinamici non sono disponibili): verde salvia, tonale Material 3. */
private val FallbackLight = lightColorScheme(
    primary = Color(0xFF2E6A55), onPrimary = Color.White, primaryContainer = Color(0xFFB2F0D6), onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4C6358), onSecondary = Color.White, secondaryContainer = Color(0xFFCEE9DA), onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF3E6374), tertiaryContainer = Color(0xFFC2E8FC),
    background = Color(0xFFF6FBF6), onBackground = Color(0xFF171D1A), surface = Color(0xFFF6FBF6), onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DE), onSurfaceVariant = Color(0xFF404944),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F5F0), surfaceContainer = Color(0xFFEAEFEA),
    surfaceContainerHigh = Color(0xFFE4EAE5), surfaceContainerHighest = Color(0xFFDFE4DF),
    outline = Color(0xFF707973), outlineVariant = Color(0xFFBFC9C2), error = Color(0xFFBA1A1A),
)
private val FallbackDark = darkColorScheme(
    primary = Color(0xFF96D4BA), onPrimary = Color(0xFF003828), primaryContainer = Color(0xFF12513E), onPrimaryContainer = Color(0xFFB2F0D6),
    secondary = Color(0xFFB3CCBF), secondaryContainer = Color(0xFF354B41), onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFA6CCE0), tertiaryContainer = Color(0xFF254B5B),
    background = Color(0xFF0F1512), onBackground = Color(0xFFDEE4DF), surface = Color(0xFF0F1512), onSurface = Color(0xFFDEE4DF),
    surfaceVariant = Color(0xFF404944), onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF171D1A), surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28), surfaceContainerHighest = Color(0xFF303633),
    outline = Color(0xFF89938D), outlineVariant = Color(0xFF404944), error = Color(0xFFFFB4AB),
)

val LocalDynamicColor = androidx.compose.runtime.compositionLocalOf { true }

@Composable
fun BioTheme(dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scheme = if (dynamic && android.os.Build.VERSION.SDK_INT >= 31) {
        runCatching { if (dark) androidx.compose.material3.dynamicDarkColorScheme(ctx) else androidx.compose.material3.dynamicLightColorScheme(ctx) }
            .getOrDefault(if (dark) FallbackDark else FallbackLight)
    } else if (dark) FallbackDark else FallbackLight
    // La palette dell'app deriva dallo schema Material: superfici tonali, niente ombre "iOS"
    val p = Palette(
        bg = scheme.surface,
        card = scheme.surfaceContainerLow,
        text = scheme.onSurface,
        text2 = scheme.onSurfaceVariant,
        sep = scheme.outlineVariant,
        field = scheme.surfaceContainerHighest,
        dark = dark,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
        ), content = content)
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

/** Card bianca arrotondata con intestazione colorata (icona + categoria), come in Salute. */
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
            .softShadow(p.dark)
            .clip(RoundedCornerShape(24.dp))
            .background(p.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    title, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (trailing != null) Text(trailing, color = p.text2, fontSize = 13.sp)
                if (onClick != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(BioIcons.Chevron, null, tint = p.text2, modifier = Modifier.size(14.dp))
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
            value, fontSize = size.sp, fontWeight = FontWeight.Bold, color = color ?: p.text,
            style = TextStyle(letterSpacing = (-0.5).sp), lineHeight = (size + 2).sp,
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
    Text(text, fontSize = 13.sp, lineHeight = 17.sp, color = color ?: LocalPalette.current.text2)
}

@Composable
fun Body(text: String, color: Color? = null, weight: FontWeight = FontWeight.Normal) {
    Text(text, fontSize = 15.sp, lineHeight = 20.sp, color = color ?: LocalPalette.current.text, fontWeight = weight)
}

/** Capsula colorata (es. "−7,2 anni"). */
@Composable
fun Pill(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Gruppo di righe su card unica con separatori rientrati (lista "inset grouped"). */
@Composable
fun InsetGroup(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().softShadow(p.dark).clip(RoundedCornerShape(26.dp)).background(p.card),
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
        Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size((size * 0.6).dp))
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
