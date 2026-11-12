package com.audiops.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Brand palette generated from the seed colour #5145CD. Used on Android 11 and older,
// where dynamic colour is unavailable.
private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF5145CD),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4DFFF),
    onPrimaryContainer = Color(0xFF1A0A6B),
    secondary = Color(0xFF5D5B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2DFF9),
    onSecondaryContainer = Color(0xFF1B1A2C),
    tertiary = Color(0xFF7A5170),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F3),
    onTertiaryContainer = Color(0xFF2E1129),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC8C5D0),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFC5BFFF),
    onPrimary = Color(0xFF2A1F8F),
    primaryContainer = Color(0xFF4134B3),
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFC6C3DC),
    onSecondary = Color(0xFF2E2D42),
    secondaryContainer = Color(0xFF44435A),
    onSecondaryContainer = Color(0xFFE2DFF9),
    tertiary = Color(0xFFE8B9DF),
    onTertiary = Color(0xFF462740),
    tertiaryContainer = Color(0xFF5D3D57),
    onTertiaryContainer = Color(0xFFFFD7F3),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    outline = Color(0xFF928F99),
    outlineVariant = Color(0xFF47464F),
)

// Material 3 Expressive shape scale, including the increased and extra-extra-large steps.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

/** Whether this device supports wallpaper-based dynamic colour (Android 12+). */
val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * App theme. [darkTheme] comes from the user's theme setting; [dynamicColor] uses the
 * wallpaper palette where supported; [pureBlack] darkens dark-theme surfaces to black.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudiopsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val base = when {
        dynamicColor && supportsDynamicColor ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    val colors = if (darkTheme && pureBlack) base.toPureBlack() else base
    // Expressive theme: spring-based motion scheme plus the expressive shape scale.
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = AppShapes,
        content = content,
    )
}

/** Black background with tonal surface containers so cards and sheets stay distinguishable. */
private fun ColorScheme.toPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0E0E11),
    surfaceContainer = Color(0xFF141417),
    surfaceContainerHigh = Color(0xFF1B1B1F),
    surfaceContainerHighest = Color(0xFF232327),
)
