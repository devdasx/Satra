package dev.satra.wallet

import android.app.LocaleManager
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.satra.wallet.settings.SatraSettings
import dev.satra.wallet.settings.SatraSettingsDefaults
import dev.satra.wallet.settings.SatraThemePreference
import dev.satra.wallet.ui.onboarding.SatraOnboardingScreen
import dev.satra.wallet.ui.setup.SatraWalletSetupScreen
import dev.satra.wallet.ui.setup.WalletSetupMode
import dev.satra.wallet.ui.theme.SatraTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        val settingsStore = getSharedPreferences(SETTINGS_PREFS_NAME, MODE_PRIVATE)
        applyAppLocale(readLanguageTag(settingsStore))

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            var themePreference by remember { mutableStateOf(readThemePreference(settingsStore)) }
            var hapticsEnabled by remember { mutableStateOf(readHapticsEnabled(settingsStore)) }
            var languageTag by remember { mutableStateOf(readLanguageTag(settingsStore)) }
            var activeScreen by rememberSaveable { mutableStateOf(SatraRootScreen.Onboarding.name) }
            val settings = SatraSettings(
                themePreference = themePreference,
                hapticsEnabled = hapticsEnabled,
                languageTag = languageTag,
            )
            val darkTheme = when (themePreference) {
                SatraThemePreference.System -> systemDarkTheme
                SatraThemePreference.Light -> false
                SatraThemePreference.Dark -> true
            }

            SatraTheme(darkTheme = darkTheme) {
                when (SatraRootScreen.valueOf(activeScreen)) {
                    SatraRootScreen.Onboarding -> SatraOnboardingScreen(
                        settings = settings,
                        appVersion = BuildConfig.VERSION_NAME,
                        onThemePreferenceChange = { preference ->
                            themePreference = preference
                            settingsStore.edit()
                                .putString(KEY_THEME_PREFERENCE, preference.name)
                                .apply()
                        },
                        onHapticsEnabledChange = { enabled ->
                            hapticsEnabled = enabled
                            settingsStore.edit()
                                .putBoolean(KEY_HAPTICS_ENABLED, enabled)
                                .apply()
                        },
                        onLanguageTagChange = { tag ->
                            languageTag = tag
                            settingsStore.edit()
                                .putString(KEY_LANGUAGE_TAG, tag)
                                .apply()
                            applyAppLocale(tag)
                        },
                        onCreateWallet = {
                            activeScreen = SatraRootScreen.CreateWallet.name
                        },
                        onRestoreWallet = {
                            activeScreen = SatraRootScreen.ImportWallet.name
                        },
                    )

                    SatraRootScreen.CreateWallet -> SatraWalletSetupScreen(
                        mode = WalletSetupMode.Create,
                        settings = settings,
                        onExit = {
                            activeScreen = SatraRootScreen.Onboarding.name
                        },
                    )

                    SatraRootScreen.ImportWallet -> SatraWalletSetupScreen(
                        mode = WalletSetupMode.Import,
                        settings = settings,
                        onExit = {
                            activeScreen = SatraRootScreen.Onboarding.name
                        },
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyAppLocale(languageTag: String) {
        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(languageTag)
        } else {
            val config = resources.configuration
            config.setLocales(LocaleList(locale))
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }
}

private const val SETTINGS_PREFS_NAME = "satra_settings"
private const val KEY_THEME_PREFERENCE = "theme_preference"
private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
private const val KEY_LANGUAGE_TAG = "language_tag"

private enum class SatraRootScreen {
    Onboarding,
    CreateWallet,
    ImportWallet,
}

private fun readThemePreference(settingsStore: SharedPreferences): SatraThemePreference {
    val storedValue = settingsStore.getString(
        KEY_THEME_PREFERENCE,
        SatraThemePreference.System.name,
    )

    return SatraThemePreference.entries.firstOrNull { it.name == storedValue }
        ?: SatraThemePreference.System
}

private fun readHapticsEnabled(settingsStore: SharedPreferences): Boolean =
    settingsStore.getBoolean(KEY_HAPTICS_ENABLED, true)

private fun readLanguageTag(settingsStore: SharedPreferences): String =
    settingsStore.getString(
        KEY_LANGUAGE_TAG,
        SatraSettingsDefaults.DEFAULT_LANGUAGE_TAG,
    ) ?: SatraSettingsDefaults.DEFAULT_LANGUAGE_TAG
