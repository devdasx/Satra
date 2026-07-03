package dev.satra.wallet.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = SatraLightPrimary,
    onPrimary = SatraLightOnPrimary,
    primaryContainer = SatraLightPrimaryContainer,
    onPrimaryContainer = SatraLightOnPrimaryContainer,
    secondary = SatraLightSecondary,
    onSecondary = SatraLightOnSecondary,
    secondaryContainer = SatraLightSecondaryContainer,
    onSecondaryContainer = SatraLightOnSecondaryContainer,
    tertiary = SatraLightTertiary,
    onTertiary = SatraLightOnTertiary,
    surface = SatraLightSurface,
    onSurface = SatraLightOnSurface,
    onSurfaceVariant = SatraLightOnSurfaceVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = SatraDarkPrimary,
    onPrimary = SatraDarkOnPrimary,
    primaryContainer = SatraDarkPrimaryContainer,
    onPrimaryContainer = SatraDarkOnPrimaryContainer,
    secondary = SatraDarkSecondary,
    onSecondary = SatraDarkOnSecondary,
    secondaryContainer = SatraDarkSecondaryContainer,
    onSecondaryContainer = SatraDarkOnSecondaryContainer,
    tertiary = SatraDarkTertiary,
    onTertiary = SatraDarkOnTertiary,
    surface = SatraDarkSurface,
    onSurface = SatraDarkOnSurface,
    onSurfaceVariant = SatraDarkOnSurfaceVariant,
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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SatraTypography,
        shapes = SatraShapes,
        content = content,
    )
}

