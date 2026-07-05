package dev.satra.wallet

import android.app.Activity
import android.app.LocaleManager
import android.content.pm.ActivityInfo
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.satra.wallet.data.db.AppSettingsRecord
import dev.satra.wallet.data.db.SatraDatabaseProvider
import dev.satra.wallet.scanner.SatraScanPurpose
import dev.satra.wallet.settings.SatraSettings
import dev.satra.wallet.settings.SatraSettingsDefaults
import dev.satra.wallet.settings.SatraThemePreference
import dev.satra.wallet.ui.main.SatraMainScreen
import dev.satra.wallet.ui.onboarding.SatraOnboardingScreen
import dev.satra.wallet.ui.scanner.SatraScannerScreen
import dev.satra.wallet.ui.security.SatraAppLockScreen
import dev.satra.wallet.ui.setup.CreateWalletBackupScreen
import dev.satra.wallet.ui.setup.CreateWalletPhraseScreen
import dev.satra.wallet.ui.setup.ImportChainScreen
import dev.satra.wallet.ui.setup.ImportMethodScreen
import dev.satra.wallet.ui.setup.ImportPrivateKeyScreen
import dev.satra.wallet.ui.setup.ImportRecoveryPhraseScreen
import dev.satra.wallet.ui.setup.ImportWatchOnlyAddressScreen
import dev.satra.wallet.ui.setup.SetupConfirmPasscodeScreen
import dev.satra.wallet.ui.setup.SetupPasscodeScreen
import dev.satra.wallet.ui.setup.WalletImportMethod
import dev.satra.wallet.ui.setup.WalletImportNetwork
import dev.satra.wallet.ui.setup.WalletSetupFlow
import dev.satra.wallet.ui.theme.SatraTheme
import dev.satra.wallet.wallet.bip39.Bip39MnemonicGenerator
import dev.satra.wallet.wallet.bip39.Bip39MnemonicValidator
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : FragmentActivity() {
    private var screenCaptureCallback: Activity.ScreenCaptureCallback? = null
    private var onScreenCaptured: (() -> Unit)? = null

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerScreenshotDetection()
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            unregisterScreenshotDetection()
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        val settingsStore = getSharedPreferences(SETTINGS_PREFS_NAME, MODE_PRIVATE)
        applyAppLocale(readLanguageTag(settingsStore))

        super.onCreate(savedInstanceState)
        applyOrientationPolicy()
        enableEdgeToEdge()

        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            var themePreference by remember { mutableStateOf(readThemePreference(settingsStore)) }
            var hapticsEnabled by remember { mutableStateOf(readHapticsEnabled(settingsStore)) }
            var languageTag by remember { mutableStateOf(readLanguageTag(settingsStore)) }
            var pendingSetupPasscode by rememberSaveable { mutableStateOf("") }
            var pendingGeneratedMnemonic by rememberSaveable { mutableStateOf("") }
            var pendingGeneratedMnemonicWordCount by rememberSaveable {
                mutableStateOf(DEFAULT_MNEMONIC_WORD_COUNT)
            }
            var pendingCreatePassphrase by rememberSaveable { mutableStateOf("") }
            var pendingImportMethodSegment by rememberSaveable { mutableStateOf("") }
            var pendingImportNetworkSegment by rememberSaveable { mutableStateOf("") }
            var pendingImportRecoveryPhrase by rememberSaveable { mutableStateOf("") }
            var pendingImportPassphrase by rememberSaveable { mutableStateOf("") }
            var pendingImportPrivateKey by rememberSaveable { mutableStateOf("") }
            var pendingImportWatchAddress by rememberSaveable { mutableStateOf("") }
            var pendingWalletId by rememberSaveable { mutableStateOf("") }
            var screenshotWarningRequests by remember { mutableStateOf(0) }
            val navController = rememberNavController()
            val lifecycleOwner = LocalLifecycleOwner.current
            val coroutineScope = rememberCoroutineScope()
            val walletRepository = remember {
                SatraDatabaseProvider.walletRepository(this@MainActivity)
            }
            var appUnlocked by rememberSaveable { mutableStateOf(false) }
            var appLockError by rememberSaveable { mutableStateOf(false) }
            var appLockResetNonce by rememberSaveable { mutableStateOf(0) }
            val setupBiometricsAvailable = remember { isBiometricUnlockAvailable() }

            fun markAppUnlockedAndOpenMain() {
                appUnlocked = true
                appLockError = false
                settingsStore.edit()
                    .remove(KEY_LAST_BACKGROUND_AT)
                    .apply()
                navController.navigate(SatraRoute.main()) {
                    popUpTo(SatraRoute.APP_LOCK) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }

            fun resetLocalPreferencesAfterErase() {
                appUnlocked = false
                themePreference = SatraThemePreference.System
                hapticsEnabled = true
                languageTag = SatraSettingsDefaults.DEFAULT_LANGUAGE_TAG
                settingsStore.edit().clear().apply()
                applyAppLocale(SatraSettingsDefaults.DEFAULT_LANGUAGE_TAG)
            }

            DisposableEffect(lifecycleOwner, navController, walletRepository, appUnlocked) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            if (!isFinishing) {
                                settingsStore.edit()
                                    .putLong(KEY_LAST_BACKGROUND_AT, System.currentTimeMillis())
                                    .apply()
                            }
                        }

                        Lifecycle.Event.ON_START -> {
                            coroutineScope.launch {
                                val walletExists = walletRepository.getPrimaryWallet() != null
                                val appSettings = walletRepository.getAppSettings()
                                val currentRoute = navController.currentDestination?.route
                                if (walletExists &&
                                    appSettings.requiresAppLock() &&
                                    shouldLockOnResume(appSettings, appUnlocked, settingsStore) &&
                                    currentRoute != SatraRoute.APP_LOCK
                                ) {
                                    appUnlocked = false
                                    appLockError = false
                                    navController.navigate(SatraRoute.APP_LOCK) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }

                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            DisposableEffect(Unit) {
                onScreenCaptured = {
                    screenshotWarningRequests += 1
                }
                onDispose {
                    onScreenCaptured = null
                }
            }
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
            fun resetPendingWalletSetup() {
                pendingSetupPasscode = ""
                pendingGeneratedMnemonic = ""
                pendingGeneratedMnemonicWordCount = DEFAULT_MNEMONIC_WORD_COUNT
                pendingCreatePassphrase = ""
                pendingImportMethodSegment = ""
                pendingImportNetworkSegment = ""
                pendingImportRecoveryPhrase = ""
                pendingImportPassphrase = ""
                pendingImportPrivateKey = ""
                pendingImportWatchAddress = ""
                pendingWalletId = ""
            }

            fun persistPendingWalletSetup(
                flow: WalletSetupFlow,
                biometricsEnabled: Boolean,
            ): Boolean {
                if (pendingWalletId.isNotBlank()) {
                    return true
                }

                return try {
                    val metadataJson = setupMetadataJson(
                        passcodeEnabled = pendingSetupPasscode.isNotBlank(),
                        passcodeLength = pendingSetupPasscode.length.takeIf { it > 0 },
                        biometricsEnabled = biometricsEnabled,
                    )
                    pendingWalletId = if (flow == WalletSetupFlow.Create) {
                        val mnemonic = pendingGeneratedMnemonic.ifBlank {
                            Bip39MnemonicGenerator.generate(
                                wordCount = pendingGeneratedMnemonicWordCount,
                            ).also { generatedMnemonic ->
                                pendingGeneratedMnemonic = generatedMnemonic
                            }
                        }
                        walletRepository.createMnemonicWallet(
                            walletName = getString(R.string.wallet_default_created_name),
                            mnemonic = mnemonic,
                            passphrase = pendingCreatePassphrase,
                            isBackedUp = true,
                            metadataJson = metadataJson,
                        )
                    } else {
                        when (WalletImportMethod.fromRoute(pendingImportMethodSegment)) {
                            WalletImportMethod.RecoveryPhrase -> {
                                require(Bip39MnemonicValidator.validate(pendingImportRecoveryPhrase).isValid)
                                walletRepository.importMnemonicWallet(
                                    walletName = getString(R.string.wallet_default_imported_name),
                                    mnemonic = pendingImportRecoveryPhrase,
                                    passphrase = pendingImportPassphrase,
                                    metadataJson = metadataJson,
                                )
                            }

                            WalletImportMethod.PrivateKey -> {
                                require(pendingImportPrivateKey.isNotBlank())
                                walletRepository.importPrivateKeyWallet(
                                    walletName = getString(R.string.wallet_default_imported_name),
                                    networkId = WalletImportNetwork.fromRoute(pendingImportNetworkSegment)
                                        ?.networkId ?: WalletImportNetwork.Bitcoin.networkId,
                                    privateKey = pendingImportPrivateKey,
                                    metadataJson = metadataJson,
                                )
                            }

                            WalletImportMethod.WatchOnly -> {
                                require(pendingImportWatchAddress.isNotBlank())
                                walletRepository.importWatchOnlyWallet(
                                    walletName = getString(R.string.wallet_default_watch_only_name),
                                    networkId = WalletImportNetwork.fromRoute(pendingImportNetworkSegment)
                                        ?.networkId ?: WalletImportNetwork.Bitcoin.networkId,
                                    address = pendingImportWatchAddress,
                                    metadataJson = metadataJson,
                                )
                            }
                        }
                    }
                    walletRepository.saveSetupSecurity(
                        passcode = pendingSetupPasscode,
                        biometricsEnabled = pendingSetupPasscode.isNotBlank() && biometricsEnabled,
                    )
                    true
                } catch (_: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.wallet_setup_save_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                    false
                }
            }

            fun finishWalletSetup(flow: WalletSetupFlow) {
                appUnlocked = pendingSetupPasscode.isNotBlank()
                settingsStore.edit()
                    .remove(KEY_LAST_BACKGROUND_AT)
                    .apply()
                resetPendingWalletSetup()
                navController.navigate(SatraRoute.main(flow)) {
                    popUpTo(SatraRoute.ONBOARDING) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }

            SatraTheme(darkTheme = darkTheme) {
                NavHost(
                    navController = navController,
                    startDestination = SatraRoute.STARTUP,
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
                    composable(SatraRoute.STARTUP) {
                        LaunchedEffect(walletRepository) {
                            val walletExists = walletRepository.getPrimaryWallet() != null
                            val appSettings = walletRepository.getAppSettings()
                            val destination = if (!walletExists) {
                                SatraRoute.ONBOARDING
                            } else if (appSettings.requiresAppLock() && !appUnlocked) {
                                SatraRoute.APP_LOCK
                            } else {
                                SatraRoute.main()
                            }
                            navController.navigate(destination) {
                                popUpTo(SatraRoute.STARTUP) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    }

                    composable(SatraRoute.APP_LOCK) {
                        var loadedAppSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
                        var biometricPromptShown by rememberSaveable { mutableStateOf(false) }

                        fun handleBiometricUnlock() {
                            showBiometricUnlockPrompt(
                                onSuccess = {
                                    markAppUnlockedAndOpenMain()
                                },
                                onError = { message ->
                                    Toast.makeText(
                                        this@MainActivity,
                                        message,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        }

                        LaunchedEffect(walletRepository) {
                            loadedAppSettings = walletRepository.getAppSettings()
                        }

                        val currentAppSettings = loadedAppSettings
                        if (currentAppSettings != null) {
                            LaunchedEffect(currentAppSettings.biometricsEnabled) {
                                if (currentAppSettings.biometricsEnabled && !biometricPromptShown) {
                                    biometricPromptShown = true
                                    handleBiometricUnlock()
                                }
                            }

                            SatraAppLockScreen(
                                passcodeLength = currentAppSettings.passcodeLength ?: 6,
                                biometricsEnabled = currentAppSettings.biometricsEnabled,
                                settings = settings,
                                resetNonce = appLockResetNonce,
                                errorMessage = if (appLockError) {
                                    getString(R.string.app_lock_wrong_passcode)
                                } else {
                                    null
                                },
                                onPasscodeComplete = { passcode ->
                                    coroutineScope.launch {
                                        val unlocked = walletRepository.verifyAppPasscode(passcode)
                                        if (unlocked) {
                                            markAppUnlockedAndOpenMain()
                                        } else if (walletRepository.getPrimaryWallet() == null) {
                                            resetLocalPreferencesAfterErase()
                                            Toast.makeText(
                                                this@MainActivity,
                                                getString(R.string.app_lock_wallet_erased),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                            navController.navigate(SatraRoute.ONBOARDING) {
                                                popUpTo(SatraRoute.APP_LOCK) {
                                                    inclusive = true
                                                }
                                                launchSingleTop = true
                                            }
                                        } else {
                                            appLockError = true
                                            appLockResetNonce += 1
                                        }
                                    }
                                },
                                onBiometricClick = {
                                    handleBiometricUnlock()
                                },
                            )
                        }
                    }

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
                                resetPendingWalletSetup()
                                pendingGeneratedMnemonicWordCount = DEFAULT_MNEMONIC_WORD_COUNT
                                pendingGeneratedMnemonic = Bip39MnemonicGenerator.generate(
                                    wordCount = pendingGeneratedMnemonicWordCount,
                                )
                                navController.navigate(SatraRoute.CREATE_WALLET_BACKUP) {
                                    launchSingleTop = true
                                }
                            },
                            onRestoreWallet = {
                                resetPendingWalletSetup()
                                navController.navigate(SatraRoute.IMPORT_METHOD) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    composable(SatraRoute.CREATE_WALLET_PHRASE) {
                        CreateWalletPhraseScreen(
                            mnemonic = pendingGeneratedMnemonic,
                            mnemonicWordCount = pendingGeneratedMnemonicWordCount,
                            passphrase = pendingCreatePassphrase,
                            screenshotWarningRequests = screenshotWarningRequests,
                            settings = settings,
                            onMnemonicWordCountChange = { wordCount ->
                                if (pendingGeneratedMnemonicWordCount != wordCount) {
                                    pendingGeneratedMnemonicWordCount = wordCount
                                    pendingGeneratedMnemonic = Bip39MnemonicGenerator.generate(
                                        wordCount = wordCount,
                                    )
                                }
                            },
                            onPassphraseChange = { passphrase ->
                                pendingCreatePassphrase = passphrase
                            },
                            onBack = {
                                navController.popBackStack()
                            },
                            onGenerateNewMnemonic = {
                                pendingGeneratedMnemonic = Bip39MnemonicGenerator.generate(
                                    wordCount = pendingGeneratedMnemonicWordCount,
                                )
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

                    composable(SatraRoute.IMPORT_RECOVERY_PHRASE) { backStackEntry ->
                        val scannedRecoveryPhrase by backStackEntry.savedStateHandle
                            .getStateFlow(SatraRoute.SCAN_RESULT_VALUE, "")
                            .collectAsState()

                        ImportRecoveryPhraseScreen(
                            settings = settings,
                            scannedRecoveryPhrase = scannedRecoveryPhrase,
                            onBack = {
                                navController.popBackStack()
                            },
                            onScanClick = {
                                navController.navigate(SatraRoute.scanner(SatraScanPurpose.RecoveryPhrase))
                            },
                            onScannedRecoveryPhraseConsumed = {
                                backStackEntry.savedStateHandle[SatraRoute.SCAN_RESULT_VALUE] = ""
                            },
                            onNext = { recoveryPhrase, passphrase ->
                                pendingImportMethodSegment = WalletImportMethod.RecoveryPhrase.routeSegment
                                pendingImportNetworkSegment = ""
                                pendingImportRecoveryPhrase = recoveryPhrase
                                pendingImportPassphrase = passphrase
                                pendingImportPrivateKey = ""
                                pendingImportWatchAddress = ""
                                navController.navigate(SatraRoute.setupPasscode(WalletSetupFlow.Import))
                            },
                        )
                    }

                    composable(
                        route = SatraRoute.SCANNER,
                        arguments = listOf(
                            navArgument(SatraRoute.ARG_SCAN_PURPOSE) {
                                type = NavType.StringType
                            },
                        ),
                    ) { backStackEntry ->
                        val purpose = SatraScanPurpose.fromRoute(
                            backStackEntry.arguments?.getString(SatraRoute.ARG_SCAN_PURPOSE),
                        )

                        SatraScannerScreen(
                            purpose = purpose,
                            settings = settings,
                            onBack = {
                                navController.popBackStack()
                            },
                            onScanResult = { result ->
                                navController.previousBackStackEntry?.savedStateHandle?.apply {
                                    set(SatraRoute.SCAN_RESULT_VALUE, result.normalizedValue)
                                    set(SatraRoute.SCAN_RESULT_KIND, result.kind.name)
                                    set(SatraRoute.SCAN_RESULT_AMOUNT, result.amount.orEmpty())
                                    set(SatraRoute.SCAN_RESULT_SCHEME, result.scheme.orEmpty())
                                }
                                navController.popBackStack()
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
                                onNext = { privateKey ->
                                    pendingImportMethodSegment = method.routeSegment
                                    pendingImportNetworkSegment = network.routeSegment
                                    pendingImportRecoveryPhrase = ""
                                    pendingImportPassphrase = ""
                                    pendingImportPrivateKey = privateKey
                                    pendingImportWatchAddress = ""
                                    navController.navigate(SatraRoute.setupPasscode(WalletSetupFlow.Import))
                                },
                            )
                        } else {
                            ImportWatchOnlyAddressScreen(
                                network = network,
                                settings = settings,
                                onBack = {
                                    navController.popBackStack()
                                },
                                onNext = { address ->
                                    pendingImportMethodSegment = method.routeSegment
                                    pendingImportNetworkSegment = network.routeSegment
                                    pendingImportRecoveryPhrase = ""
                                    pendingImportPassphrase = ""
                                    pendingImportPrivateKey = ""
                                    pendingImportWatchAddress = address
                                    navController.navigate(SatraRoute.setupPasscode(WalletSetupFlow.Import))
                                },
                            )
                        }
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
                                if (persistPendingWalletSetup(flow, biometricsEnabled = false)) {
                                    finishWalletSetup(flow)
                                }
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
                            showBiometricsOption = setupBiometricsAvailable,
                            onBack = {
                                navController.popBackStack()
                            },
                            onConfirmed = { biometricsEnabled ->
                                if (persistPendingWalletSetup(flow, biometricsEnabled)) {
                                    finishWalletSetup(flow)
                                }
                            },
                            onMismatch = {
                                pendingSetupPasscode = ""
                                navController.navigate(SatraRoute.setupPasscode(flow)) {
                                    popUpTo(SatraRoute.setupPasscode(flow)) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    composable(
                        route = SatraRoute.MAIN,
                        arguments = listOf(
                            navArgument(SatraRoute.ARG_SETUP_RESULT) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { backStackEntry ->
                        val setupResultSegment = backStackEntry.arguments
                            ?.getString(SatraRoute.ARG_SETUP_RESULT)
                        val setupCompletionFlow = setupResultSegment
                            ?.takeIf(String::isNotBlank)
                            ?.let(WalletSetupFlow::fromRoute)
                        val scannedAddress by backStackEntry.savedStateHandle
                            .getStateFlow(SatraRoute.SCAN_RESULT_VALUE, "")
                            .collectAsState()
                        SatraMainScreen(
                            walletRepository = walletRepository,
                            settings = settings,
                            appVersion = BuildConfig.VERSION_NAME,
                            setupCompletionFlow = setupCompletionFlow,
                            scannedAddress = scannedAddress,
                            onScanAddressClick = {
                                navController.navigate(SatraRoute.scanner(SatraScanPurpose.Address))
                            },
                            onScannedAddressConsumed = {
                                backStackEntry.savedStateHandle.set(SatraRoute.SCAN_RESULT_VALUE, "")
                                backStackEntry.savedStateHandle.set(SatraRoute.SCAN_RESULT_KIND, "")
                                backStackEntry.savedStateHandle.set(SatraRoute.SCAN_RESULT_AMOUNT, "")
                                backStackEntry.savedStateHandle.set(SatraRoute.SCAN_RESULT_SCHEME, "")
                            },
                            onCreateWallet = {
                                resetPendingWalletSetup()
                                pendingGeneratedMnemonicWordCount = DEFAULT_MNEMONIC_WORD_COUNT
                                pendingGeneratedMnemonic = Bip39MnemonicGenerator.generate(
                                    wordCount = pendingGeneratedMnemonicWordCount,
                                )
                                navController.navigate(SatraRoute.CREATE_WALLET_BACKUP) {
                                    launchSingleTop = true
                                }
                            },
                            onImportWallet = {
                                resetPendingWalletSetup()
                                navController.navigate(SatraRoute.IMPORT_METHOD) {
                                    launchSingleTop = true
                                }
                            },
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
                            onResetComplete = {
                                themePreference = SatraThemePreference.System
                                hapticsEnabled = true
                                languageTag = SatraSettingsDefaults.DEFAULT_LANGUAGE_TAG
                                settingsStore.edit().clear().apply()
                                applyAppLocale(SatraSettingsDefaults.DEFAULT_LANGUAGE_TAG)
                                navController.navigate(SatraRoute.ONBOARDING) {
                                    popUpTo(SatraRoute.MAIN) {
                                        inclusive = true
                                    }
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

    private fun applyOrientationPolicy() {
        requestedOrientation = if (resources.getBoolean(R.bool.lock_compact_phone_to_portrait)) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun showBiometricUnlockPrompt(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    onError(getString(R.string.app_lock_biometric_failed))
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_lock_biometric_prompt_title))
            .setSubtitle(getString(R.string.app_lock_biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.app_lock_biometric_prompt_cancel))
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun isBiometricUnlockAvailable(): Boolean {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        return BiometricManager.from(this).canAuthenticate(authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun registerScreenshotDetection() {
        if (screenCaptureCallback != null) {
            return
        }
        val callback = Activity.ScreenCaptureCallback {
            onScreenCaptured?.invoke()
        }
        screenCaptureCallback = callback
        registerScreenCaptureCallback(mainExecutor, callback)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun unregisterScreenshotDetection() {
        screenCaptureCallback?.let { callback ->
            unregisterScreenCaptureCallback(callback)
        }
        screenCaptureCallback = null
    }
}

private const val SETTINGS_PREFS_NAME = "satra_settings"
private const val KEY_THEME_PREFERENCE = "theme_preference"
private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
private const val KEY_LANGUAGE_TAG = "language_tag"
private const val KEY_LAST_BACKGROUND_AT = "last_background_at"
private const val NAV_ANIMATION_MILLIS = 280
private const val DEFAULT_MNEMONIC_WORD_COUNT = 12

private fun setupMetadataJson(
    passcodeEnabled: Boolean,
    passcodeLength: Int?,
    biometricsEnabled: Boolean,
): String =
    buildString {
        append("{")
        append("\"passcodeEnabled\":")
        append(passcodeEnabled)
        append(",\"passcodeLength\":")
        append(passcodeLength ?: 0)
        append(",\"biometricsEnabled\":")
        append(biometricsEnabled)
        append("}")
    }

private object SatraRoute {
    const val ARG_FLOW = "flow"
    const val ARG_METHOD = "method"
    const val ARG_NETWORK = "network"
    const val ARG_SCAN_PURPOSE = "scanPurpose"
    const val ARG_SETUP_RESULT = "setupResult"
    const val SCAN_RESULT_VALUE = "scan_result_value"
    const val SCAN_RESULT_KIND = "scan_result_kind"
    const val SCAN_RESULT_AMOUNT = "scan_result_amount"
    const val SCAN_RESULT_SCHEME = "scan_result_scheme"
    const val STARTUP = "startup"
    const val APP_LOCK = "app-lock"
    const val MAIN_BASE = "main"
    const val MAIN = "$MAIN_BASE?$ARG_SETUP_RESULT={$ARG_SETUP_RESULT}"
    const val ONBOARDING = "onboarding"
    const val CREATE_WALLET_BACKUP = "create-wallet/backup"
    const val CREATE_WALLET_PHRASE = "create-wallet/recovery-phrase"
    const val IMPORT_METHOD = "import-wallet/method"
    const val IMPORT_RECOVERY_PHRASE = "import-wallet/recovery-phrase"
    const val IMPORT_CHAIN = "import-wallet/{$ARG_METHOD}/chain"
    const val IMPORT_ENTRY = "import-wallet/{$ARG_METHOD}/entry/{$ARG_NETWORK}"
    const val SCANNER = "scanner/{$ARG_SCAN_PURPOSE}"
    const val SETUP_PASSCODE = "setup/{$ARG_FLOW}/passcode"
    const val SETUP_CONFIRM_PASSCODE = "setup/{$ARG_FLOW}/confirm-passcode"

    fun main(flow: WalletSetupFlow? = null): String =
        flow?.let { "$MAIN_BASE?$ARG_SETUP_RESULT=${it.routeSegment}" } ?: MAIN_BASE

    fun importChain(method: WalletImportMethod): String =
        "import-wallet/${method.routeSegment}/chain"

    fun importEntry(
        method: WalletImportMethod,
        network: WalletImportNetwork,
    ): String = "import-wallet/${method.routeSegment}/entry/${network.routeSegment}"

    fun scanner(purpose: SatraScanPurpose): String =
        "scanner/${purpose.routeSegment}"

    fun setupPasscode(flow: WalletSetupFlow): String =
        "setup/${flow.routeSegment}/passcode"

    fun setupConfirmPasscode(flow: WalletSetupFlow): String =
        "setup/${flow.routeSegment}/confirm-passcode"
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

private fun AppSettingsRecord.requiresAppLock(): Boolean =
    passcodeEnabled &&
        !passcodeHash.isNullOrBlank() &&
        !passcodeSalt.isNullOrBlank()

private fun shouldLockOnResume(
    appSettings: AppSettingsRecord,
    appUnlocked: Boolean,
    settingsStore: SharedPreferences,
): Boolean {
    if (!appSettings.requiresAppLock()) return false
    if (!appUnlocked) return true
    val lastBackgroundAt = settingsStore.getLong(KEY_LAST_BACKGROUND_AT, 0L)
    if (lastBackgroundAt <= 0L) return false
    val elapsedMillis = System.currentTimeMillis() - lastBackgroundAt
    return elapsedMillis >= appSettings.autoLockTimeoutMillis
}
