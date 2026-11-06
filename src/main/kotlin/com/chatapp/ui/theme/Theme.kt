package com.chatapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat

val LocalPawPalColors = staticCompositionLocalOf { LightPawPalColors }

val PawPalShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

/**
 * PawPal theme. The doodle palette is provided both as Material3 colors and as
 * a [PawPalColors] composition local so components can reach the paper/ink/
 * pastel tokens directly.
 */
@Composable
fun PawPalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkPawPalColors else LightPawPalColors
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        val controller = WindowInsetsControllerCompat(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
    MaterialTheme(
        colorScheme = colors.toColorScheme(),
        typography = PawPalTypography,
        shapes = PawPalShapes,
        content = {
            CompositionLocalProviderCompat(colors, content)
        }
    )
}

@Composable
private fun CompositionLocalProviderCompat(colors: PawPalColors, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalPawPalColors provides colors) {
        content()
    }
}

private fun PawPalColors.toColorScheme() = if (isDark) {
    darkColorScheme(
        primary = coral,
        onPrimary = Color(0xFF2A1A1A),
        primaryContainer = coralSoft,
        onPrimaryContainer = ink,
        secondary = mint,
        onSecondary = Color(0xFF14251F),
        secondaryContainer = mintSoft,
        onSecondaryContainer = ink,
        tertiary = sky,
        onTertiary = Color(0xFF10222C),
        tertiaryContainer = skySoft,
        onTertiaryContainer = ink,
        background = paper,
        onBackground = ink,
        surface = card,
        onSurface = ink,
        surfaceVariant = cardAlt,
        onSurfaceVariant = inkSoft,
        outline = line,
        outlineVariant = lineSoft,
        error = danger,
        onError = Color(0xFF2A1A1A)
    )
} else {
    lightColorScheme(
        primary = coral,
        onPrimary = Color(0xFF2A1A1A),
        primaryContainer = coralSoft,
        onPrimaryContainer = ink,
        secondary = mint,
        onSecondary = Color(0xFF14251F),
        secondaryContainer = mintSoft,
        onSecondaryContainer = ink,
        tertiary = sky,
        onTertiary = Color(0xFF10222C),
        tertiaryContainer = skySoft,
        onTertiaryContainer = ink,
        background = paper,
        onBackground = ink,
        surface = card,
        onSurface = ink,
        surfaceVariant = cardAlt,
        onSurfaceVariant = inkSoft,
        outline = line,
        outlineVariant = lineSoft,
        error = danger,
        onError = Color.White
    )
}
