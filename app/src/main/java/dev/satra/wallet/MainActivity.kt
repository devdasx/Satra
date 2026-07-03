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
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.satra.wallet.ui.main.SatraHomeScreen
import dev.satra.wallet.ui.onboarding.SatraOnboardingScreen
import dev.satra.wallet.ui.setup.CreateWalletBackupScreen
import dev.satra.wallet.ui.setup.CreateWalletPhraseScreen
import dev.satra.wallet.ui.setup.ImportChainScreen
import dev.satra.wallet.ui.setup.ImportMethodScreen
import dev.satra.wallet.ui.setup.ImportPrivateKeyScreen
import dev.satra.wallet.ui.setup.ImportRecoveryPhraseScreen
import dev.satra.wallet.ui.setup.ImportReviewScreen
import dev.satra.wallet.ui.setup.ImportWatchOnlyAddressScreen
import dev.satra.wallet.ui.setup.SetupBiometricsScreen
import dev.satra.wallet.ui.setup.SetupConfirmPasscodeScreen
import dev.satra.wallet.ui.setup.SetupPasscodeScreen
import dev.satra.wallet.ui.setup.SetupSuccessScreen
import dev.satra.wallet.ui.setup.WalletImportMethod
import dev.satra.wallet.ui.setup.WalletImportNetwork
import dev.satra.wallet.ui.setup.WalletSetupFlow
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
            var pendingSetupPasscode by rememberSaveable { mutableStateOf("") }
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
                                navController.navigate(SatraRoute.CREATE_WALLET_BACKUP) {
                                    launchSingleTop = true
                                }
                            },
                            onRestoreWallet = {
                                navController.navigate(SatraRoute.IMPORT_METHOD) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    composable(SatraRoute.CREATE_WALLET_PHRASE) {
                        CreateWalletPhraseScreen(
                            settings = settings,
                            onBack = {
                                navController.popBackStack()
                            },
                            onNext = {
                                navController.navigate(SatraRoute.setupPasscode(WalletSetupFlow.Create))
                            },
                        )
                    }

                    composable(SatraRoute.CREATE_WALLET_BACKUP) {
                        CreateWalletBackupScreen(
                            settings = settings,
                            onBack = {
                                navController.popBackStack()
                            },
                            onNext = {
                                navController.navigate(SatraRoute.CREATE_WALLET_PHRASE)
                            },
                        )
                    }

                    composable(SatraRoute.IMPORT_METHOD) {
                        ImportMethodScreen(
                            settings = settings,
                            onBack = {
                                navController.popBackStack()
                            },
                            onMethodContinue = { method ->
                                when (method) {
                                    WalletImportMethod.RecoveryPhrase -> {
                                        navController.navigate(SatraRoute.IMPORT_RECOVERY_PHRASE)
                                    }

                                    WalletImportMethod.PrivateKey,
                                    WalletImportMethod.WatchOnly -> {
                                        navController.navigate(SatraRoute.importChain(method))
                                    }
                                }
                            },
                        )
                    }

                    composable(SatraRoute.IMPORT_RECOVERY_PHRASE) {
                        ImportRecoveryPhraseScreen(
                            settings = settings,
                            onBack = {
                                navController.popBackStack()
                            },
                            onNext = {
                                navController.navigate(
                                    SatraRoute.importReview(
                                        method = WalletImportMethod.RecoveryPhrase,
                                        network = null,
                                    ),
                                )
                            },
                        )
                    }

                    composable(
                        route = SatraRoute.IMPORT_CHAIN,
                        arguments = listOf(
                            navArgument(SatraRoute.ARG_METHOD) {
                                type = NavType.StringType
                            },
                        ),
                    ) { backStackEntry ->
                        val method = WalletImportMethod.fromRoute(
                            backStackEntry.arguments?.getString(SatraRoute.ARG_METHOD),
                        )

                        ImportChainScreen(
                            method = method,
                            settings = settings,
                            onBack = {
                                navController.popBackStack()
                            },
                            onNetworkContinue = { network ->
                                navController.navigate(SatraRoute.importEntry(method, network))
                            },
                        )
                    }

                    composable(
                        route = SatraRoute.IMPORT_ENTRY,
                        arguments = listOf(
                            navArgument(SatraRoute.ARG_METHOD) {
                                type = NavType.StringType
                            },
                            navArgument(SatraRoute.ARG_NETWORK) {
                                type = NavType.StringType
                            },
                        ),
                    ) { backStackEntry ->
                        val method = WalletImportMethod.fromRoute(
                            backStackEntry.arguments?.getString(SatraRoute.ARG_METHOD),
                        )
                        val network = WalletImportNetwork.fromRoute(
                            backStackEntry.arguments?.getString(SatraRoute.ARG_NETWORK),
                        ) ?: WalletImportNetwork.Bitcoin

                        if (method == WalletImportMethod.PrivateKey) {
                            ImportPrivateKeyScreen(
                                network = network,
                                settings = settings,
                                onBack = {
                                    navController.popBackStack()
                                },
                                onNext = {
                                    navController.navigate(SatraRoute.importReview(method, network))
                                },
                            )
                        } else {
                            ImportWatchOnlyAddressScreen(
                                network = network,
                                settings = settings,
                                onBack = {
                                    navController.popBackStack()
                                },
                                onNext = {
                                    navController.navigate(SatraRoute.importReview(method, network))
                                },
                            )
                        }
                    }

                    composable(
                        route = SatraRoute.IMPORT_REVIEW,
                        arguments = listOf(
                            navArgument(SatraRoute.ARG_METHOD) {
                                type = NavType.StringType
                            },
                            navArgument(SatraRoute.ARG_NETWORK) {
                                type = NavType.StringType
                            },
                        ),
                    ) { backStackEntry ->
                        val method = WalletImportMethod.fromRoute(
                            backStackEntry.arguments?.getString(SatraRoute.ARG_METHOD),
                        )
                        val network = WalletImportNetwork.fromRoute(
                            backStackEntry.arguments?.getString(SatraRoute.ARG_NETWORK),
                        )

                        ImportReviewScreen(
                            method = method,
                            network = network,
                            settings = settings,
                            onBack = {
                                navController.popBackStack()
                            },
                            onNext = {
                                navController.navigate(SatraRoute.setupPasscode(WalletSetupFlow.Import))
                            },
                        )
                    }

                    composable(
                        route = SatraRoute.SETUP_PASSCODE,
                        arguments = listOf(
                            navArgument(SatraRoute.ARG_FLOW) {
                                type = NavType.StringType
                            },
                        ),
                    ) { backStackEntry ->
                        val flow = WalletSetupFlow.fromRoute(
                            backStackEntry.arguments?.getString(SatraRoute.ARG_FLOW),
                        )

                        SetupPasscodeScreen(
                            flow = flow,
                            settings = settings,
                            onBack = {
                                navController.popBackStack()
                            },
                            onPasscodeCreated = { passcode ->
                                pendingSetupPasscode = passcode
                                navController.navigate(SatraRoute.setupConfirmPasscode(flow))
                            },
                            onSkip = {
                                pendingSetupPasscode = ""
                                navController.navigate(SatraRoute.setupBiometrics(flow))
                            },
                        )
                    }

                    composable(
                        route = SatraRoute.SETUP_CONFIRM_PASSCODE,
                        arguments = listOf(
                            navArgument(SatraRoute.ARG_FLOW) {
                                type = NavType.StringType
                            },
                        ),
                    ) { backStackEntry ->
                        val flow = WalletSetupFlow.fromRoute(
                            backStackEntry.arguments?.getString(SatraRoute.ARG_FLOW),
                        )

                        SetupConfirmPasscodeScreen(
                            flow = flow,
                            expectedPasscode = pendingSetupPasscode,
                            settings = settings,
                            onBack = {
                                navController.popBackStack()
                            },
                            onConfirmed = {
                                navController.navigate(SatraRoute.setupBiometrics(flow))
                            },
                        )
                    }

                    composable(
                        route = SatraRoute.SETUP_BIOMETRICS,
                        arguments = listOf(
                            navArgument(SatraRoute.ARG_FLOW) {
                                type = NavType.StringType
                            },
                        ),
                    ) { backStackEntry ->
                        val flow = WalletSetupFlow.fromRoute(
                            backStackEntry.arguments?.getString(SatraRoute.ARG_FLOW),
                        )

                        SetupBiometricsScreen(
                            flow = flow,
                            settings = settings,
                            onBack = {
                                navController.popBackStack()
                            },
                            onContinue = {
                                navController.navigate(SatraRoute.setupSuccess(flow))
                            },
                            onSkip = {
                                navController.navigate(SatraRoute.setupSuccess(flow))
                            },
                        )
                    }

                    composable(
                        route = SatraRoute.SETUP_SUCCESS,
                        arguments = listOf(
                            navArgument(SatraRoute.ARG_FLOW) {
                                type = NavType.StringType
                            },
                        ),
                    ) { backStackEntry ->
                        val flow = WalletSetupFlow.fromRoute(
                            backStackEntry.arguments?.getString(SatraRoute.ARG_FLOW),
                        )

                        SetupSuccessScreen(
                            flow = flow,
                            settings = settings,
                            onBack = {
                                navController.popBackStack()
                            },
                            onOpenWallet = {
                                navController.navigate(SatraRoute.MAIN) {
                                    popUpTo(SatraRoute.ONBOARDING) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    composable(SatraRoute.MAIN) {
                        SatraHomeScreen()
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
    const val ARG_FLOW = "flow"
    const val ARG_METHOD = "method"
    const val ARG_NETWORK = "network"
    private const val NO_NETWORK = "none"
    const val MAIN = "main"
    const val ONBOARDING = "onboarding"
    const val CREATE_WALLET_BACKUP = "create-wallet/backup"
    const val CREATE_WALLET_PHRASE = "create-wallet/recovery-phrase"
    const val IMPORT_METHOD = "import-wallet/method"
    const val IMPORT_RECOVERY_PHRASE = "import-wallet/recovery-phrase"
    const val IMPORT_CHAIN = "import-wallet/{$ARG_METHOD}/chain"
    const val IMPORT_ENTRY = "import-wallet/{$ARG_METHOD}/entry/{$ARG_NETWORK}"
    const val IMPORT_REVIEW = "import-wallet/{$ARG_METHOD}/review/{$ARG_NETWORK}"
    const val SETUP_PASSCODE = "setup/{$ARG_FLOW}/passcode"
    const val SETUP_CONFIRM_PASSCODE = "setup/{$ARG_FLOW}/confirm-passcode"
    const val SETUP_BIOMETRICS = "setup/{$ARG_FLOW}/biometrics"
    const val SETUP_SUCCESS = "setup/{$ARG_FLOW}/success"

    fun importChain(method: WalletImportMethod): String =
        "import-wallet/${method.routeSegment}/chain"

    fun importEntry(
        method: WalletImportMethod,
        network: WalletImportNetwork,
    ): String = "import-wallet/${method.routeSegment}/entry/${network.routeSegment}"

    fun importReview(
        method: WalletImportMethod,
        network: WalletImportNetwork?,
    ): String = "import-wallet/${method.routeSegment}/review/${network?.routeSegment ?: NO_NETWORK}"

    fun setupPasscode(flow: WalletSetupFlow): String =
        "setup/${flow.routeSegment}/passcode"

    fun setupConfirmPasscode(flow: WalletSetupFlow): String =
        "setup/${flow.routeSegment}/confirm-passcode"

    fun setupBiometrics(flow: WalletSetupFlow): String =
        "setup/${flow.routeSegment}/biometrics"

    fun setupSuccess(flow: WalletSetupFlow): String =
        "setup/${flow.routeSegment}/success"
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
