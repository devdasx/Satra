package dev.satra.wallet

import android.app.LocaleManager
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
            val navController = rememberNavController()
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
                NavHost(
                    navController = navController,
                    startDestination = SatraRoute.ONBOARDING,
                    enterTransition = {
                        slideInHorizontally(
                            animationSpec = tween(NAV_ANIMATION_MILLIS),
                            initialOffsetX = { fullWidth -> fullWidth },
                        )
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(NAV_ANIMATION_MILLIS),
                            targetOffsetX = { fullWidth -> -fullWidth / 3 },
                        )
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            animationSpec = tween(NAV_ANIMATION_MILLIS),
                            initialOffsetX = { fullWidth -> -fullWidth / 3 },
                        )
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(NAV_ANIMATION_MILLIS),
                            targetOffsetX = { fullWidth -> fullWidth },
                        )
                    },
                ) {
                    composable(SatraRoute.ONBOARDING) {
                        SatraOnboardingScreen(
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
                                navController.navigate(SatraRoute.createWalletStep(0)) {
                                    launchSingleTop = true
                                }
                            },
                            onRestoreWallet = {
                                navController.navigate(SatraRoute.importWalletStep(0)) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    composable(
                        route = SatraRoute.CREATE_WALLET_STEP,
                        arguments = listOf(
                            navArgument(SatraRoute.ARG_STEP) {
                                type = NavType.IntType
                            },
                        ),
                    ) { backStackEntry ->
                        val stepIndex = backStackEntry.arguments
                            ?.getInt(SatraRoute.ARG_STEP)
                            ?: 0

                        SatraWalletSetupScreen(
                            mode = WalletSetupMode.Create,
                            stepIndex = stepIndex,
                            settings = settings,
                            onBack = {
                                navController.popBackStack()
                            },
                            onNextStep = { nextStep ->
                                navController.navigate(SatraRoute.createWalletStep(nextStep)) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    composable(
                        route = SatraRoute.IMPORT_WALLET_STEP,
                        arguments = listOf(
                            navArgument(SatraRoute.ARG_STEP) {
                                type = NavType.IntType
                            },
                        ),
                    ) { backStackEntry ->
                        val stepIndex = backStackEntry.arguments
                            ?.getInt(SatraRoute.ARG_STEP)
                            ?: 0

                        SatraWalletSetupScreen(
                            mode = WalletSetupMode.Import,
                            stepIndex = stepIndex,
                            settings = settings,
                            onBack = {
                                navController.popBackStack()
                            },
                            onNextStep = { nextStep ->
                                navController.navigate(SatraRoute.importWalletStep(nextStep)) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
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
private const val NAV_ANIMATION_MILLIS = 280

private object SatraRoute {
    const val ARG_STEP = "step"
    const val ONBOARDING = "onboarding"
    const val CREATE_WALLET_STEP = "create-wallet/{$ARG_STEP}"
    const val IMPORT_WALLET_STEP = "import-wallet/{$ARG_STEP}"

    fun createWalletStep(step: Int): String = "create-wallet/$step"

    fun importWalletStep(step: Int): String = "import-wallet/$step"
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
