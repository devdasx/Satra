package dev.satra.wallet.settings

data class SatraSettings(
    val themePreference: SatraThemePreference = SatraThemePreference.System,
    val hapticsEnabled: Boolean = true,
    val languageTag: String = SatraSettingsDefaults.DEFAULT_LANGUAGE_TAG,
)

enum class SatraThemePreference {
    System,
    Light,
    Dark,
}

object SatraSettingsDefaults {
    const val DEFAULT_LANGUAGE_TAG = "en"
}
