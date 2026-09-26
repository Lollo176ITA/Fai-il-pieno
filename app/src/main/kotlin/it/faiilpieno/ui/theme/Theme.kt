package it.faiilpieno.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Schema di ripiego per Android < 12 (senza colori dinamici): verde benzinaio.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6B4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6F2C8),
    onPrimaryContainer = Color(0xFF002113),
    secondary = Color(0xFF4E6356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D8),
    onSecondaryContainer = Color(0xFF0B1F15),
    tertiary = Color(0xFF3C6472),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC0E9FA),
    onTertiaryContainer = Color(0xFF001F28),
    background = Color(0xFFF8F9F5),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFF8F9F5),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD5AD),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005236),
    onPrimaryContainer = Color(0xFFA6F2C8),
    secondary = Color(0xFFB4CCBC),
    onSecondary = Color(0xFF203529),
    secondaryContainer = Color(0xFF364B3F),
    onSecondaryContainer = Color(0xFFD0E8D8),
    tertiary = Color(0xFFA4CDDD),
    onTertiary = Color(0xFF053542),
    tertiaryContainer = Color(0xFF234C59),
    onTertiaryContainer = Color(0xFFC0E9FA),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    outline = Color(0xFF8A938C),
)

/**
 * Colori semantici per "sotto/sopra la media". Sono fissi (non dinamici) per garantire
 * contrasto AA in entrambi i temi, e sono sempre accompagnati da icona e testo.
 */
@Immutable
data class PriceColors(
    val cheaper: Color,
    val onCheaper: Color,
    val cheaperContainer: Color,
    val onCheaperContainer: Color,
    val pricier: Color,
    val pricierContainer: Color,
    val onPricierContainer: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
)

private val LightPriceColors = PriceColors(
    cheaper = Color(0xFF1E7A34),
    onCheaper = Color(0xFFFFFFFF),
    cheaperContainer = Color(0xFFCDEFD3),
    onCheaperContainer = Color(0xFF0B3D17),
    pricier = Color(0xFFB3261E),
    pricierContainer = Color(0xFFFFDAD6),
    onPricierContainer = Color(0xFF5C0F0B),
    neutralContainer = Color(0xFFE6E8E3),
    onNeutralContainer = Color(0xFF2E312E),
)

private val DarkPriceColors = PriceColors(
    cheaper = Color(0xFF7FD68E),
    onCheaper = Color(0xFF00390F),
    cheaperContainer = Color(0xFF14512A),
    onCheaperContainer = Color(0xFFC6F3CE),
    pricier = Color(0xFFFFB4AB),
    pricierContainer = Color(0xFF7A1B16),
    onPricierContainer = Color(0xFFFFDAD6),
    neutralContainer = Color(0xFF363A36),
    onNeutralContainer = Color(0xFFE1E3DF),
)

val LocalPriceColors = staticCompositionLocalOf { LightPriceColors }

@Composable
fun FaiIlPienoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalPriceColors provides if (darkTheme) DarkPriceColors else LightPriceColors) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = AppTypography,
            content = content,
        )
    }
}
