package dev.satra.wallet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val SatraInk = Color(0xFF0B0B0C)
internal val SatraGraphite = Color(0xFF2C2C30)
internal val SatraSteel = Color(0xFF8E8E93)
internal val SatraMist = Color(0xFFD9D8D4)
internal val SatraBone = Color(0xFFF7F6F3)
internal val SatraWhite = Color(0xFFFFFFFF)
internal val SatraGreen = Color(0xFF2E7D5A)
internal val SatraAmber = Color(0xFFB07C2A)
internal val SatraRed = Color(0xFFB3452E)

internal val SatraLightBgApp = SatraBone
internal val SatraLightBgSubtle = Color(0xFFEFEDE8)
internal val SatraLightSurfaceCard = SatraWhite
internal val SatraLightSurfaceCardNested = SatraBone
internal val SatraLightBorder = Color(0xFFE4E2DD)
internal val SatraLightDivider = Color(0xFFECEAE5)
internal val SatraLightTextTitle = Color(0xFF131316)
internal val SatraLightTextBody = SatraGraphite
internal val SatraLightTextSubtitle = Color(0xFF55555A)
internal val SatraLightTextMuted = SatraSteel
internal val SatraLightAccent = SatraInk
internal val SatraLightAccentContrast = SatraBone
internal val SatraLightAccentSoft = Color(0x0F0B0B0C)
internal val SatraLightButtonSecondaryBorder = SatraMist
internal val SatraLightSuccess = SatraGreen
internal val SatraLightSuccessBg = Color(0xFFE7F1EC)
internal val SatraLightWarning = SatraAmber
internal val SatraLightWarningBg = Color(0xFFF7EFDF)
internal val SatraLightError = SatraRed
internal val SatraLightErrorBg = Color(0xFFF6E7E3)
internal val SatraLightScrim = Color(0x800B0B0C)

internal val SatraDarkBgApp = SatraInk
internal val SatraDarkBgSubtle = Color(0xFF0F0F11)
internal val SatraDarkSurfaceCard = Color(0xFF141416)
internal val SatraDarkSurfaceCardNested = Color(0xFF1D1D20)
internal val SatraDarkBorder = Color(0xFF26262A)
internal val SatraDarkDivider = Color(0xFF1F1F23)
internal val SatraDarkTextTitle = SatraBone
internal val SatraDarkTextBody = SatraMist
internal val SatraDarkTextSubtitle = Color(0xFFA8A8AD)
internal val SatraDarkTextMuted = SatraSteel
internal val SatraDarkAccent = SatraBone
internal val SatraDarkAccentContrast = SatraInk
internal val SatraDarkAccentSoft = Color(0x14F7F6F3)
internal val SatraDarkButtonSecondaryBorder = Color(0xFF3A3A3F)
internal val SatraDarkSuccess = Color(0xFF4E9E76)
internal val SatraDarkSuccessBg = Color(0x2E2E7D5A)
internal val SatraDarkWarning = Color(0xFFC99A4B)
internal val SatraDarkWarningBg = Color(0x29B07C2A)
internal val SatraDarkError = Color(0xFFC96A54)
internal val SatraDarkErrorBg = Color(0x29B3452E)
internal val SatraDarkScrim = Color(0x99000000)

internal val SatraButtonSecondaryBorder: Color
    @Composable
    get() = if (MaterialTheme.colorScheme.primary == SatraDarkAccent) {
        SatraDarkButtonSecondaryBorder
    } else {
        SatraLightButtonSecondaryBorder
    }
