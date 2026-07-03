package dev.satra.wallet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = SatraLightAccent,
    onPrimary = SatraLightAccentContrast,
    primaryContainer = SatraLightAccentSoft,
    onPrimaryContainer = SatraLightTextTitle,
    secondary = SatraLightTextBody,
    onSecondary = SatraLightAccentContrast,
    secondaryContainer = SatraLightSurfaceCardNested,
    onSecondaryContainer = SatraLightTextBody,
    tertiary = SatraLightSuccess,
    onTertiary = SatraLightAccentContrast,
    tertiaryContainer = SatraLightSuccessBg,
    onTertiaryContainer = SatraLightSuccess,
    background = SatraLightBgApp,
    onBackground = SatraLightTextBody,
    surface = SatraLightBgApp,
    onSurface = SatraLightTextTitle,
    surfaceVariant = SatraLightSurfaceCard,
    onSurfaceVariant = SatraLightTextSubtitle,
    surfaceContainerLowest = SatraLightBgApp,
    surfaceContainerLow = SatraLightBgSubtle,
    surfaceContainer = SatraLightSurfaceCard,
    surfaceContainerHigh = SatraLightSurfaceCardNested,
    surfaceContainerHighest = SatraLightSurfaceCardNested,
    outline = SatraLightBorder,
    outlineVariant = SatraLightDivider,
    error = SatraLightError,
    onError = SatraLightAccentContrast,
    errorContainer = SatraLightErrorBg,
    onErrorContainer = SatraLightError,
    scrim = SatraLightScrim,
    inverseSurface = SatraLightAccent,
    inverseOnSurface = SatraLightAccentContrast,
    inversePrimary = SatraLightAccentContrast,
)

private val DarkColorScheme = darkColorScheme(
    primary = SatraDarkAccent,
    onPrimary = SatraDarkAccentContrast,
    primaryContainer = SatraDarkAccentSoft,
    onPrimaryContainer = SatraDarkTextTitle,
    secondary = SatraDarkTextBody,
    onSecondary = SatraDarkAccentContrast,
    secondaryContainer = SatraDarkSurfaceCardNested,
    onSecondaryContainer = SatraDarkTextBody,
    tertiary = SatraDarkSuccess,
    onTertiary = SatraDarkAccentContrast,
    tertiaryContainer = SatraDarkSuccessBg,
    onTertiaryContainer = SatraDarkSuccess,
    background = SatraDarkBgApp,
    onBackground = SatraDarkTextBody,
    surface = SatraDarkBgApp,
    onSurface = SatraDarkTextTitle,
    surfaceVariant = SatraDarkSurfaceCard,
    onSurfaceVariant = SatraDarkTextSubtitle,
    surfaceContainerLowest = SatraDarkBgApp,
    surfaceContainerLow = SatraDarkBgSubtle,
    surfaceContainer = SatraDarkSurfaceCard,
    surfaceContainerHigh = SatraDarkSurfaceCardNested,
    surfaceContainerHighest = SatraDarkSurfaceCardNested,
    outline = SatraDarkBorder,
    outlineVariant = SatraDarkDivider,
    error = SatraDarkError,
    onError = SatraDarkAccentContrast,
    errorContainer = SatraDarkErrorBg,
    onErrorContainer = SatraDarkError,
    scrim = SatraDarkScrim,
    inverseSurface = SatraDarkAccent,
    inverseOnSurface = SatraDarkAccentContrast,
    inversePrimary = SatraDarkAccentContrast,
)

private val SatraShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun SatraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val keepMaterialSignatureStable = dynamicColor
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SatraTypography,
        shapes = SatraShapes,
        content = content,
    )
}
