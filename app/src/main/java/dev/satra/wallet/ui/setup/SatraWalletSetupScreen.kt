package dev.satra.wallet.ui.setup

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.satra.wallet.R
import dev.satra.wallet.settings.SatraSettings
import dev.satra.wallet.ui.theme.SatraButtonSecondaryBorder
import dev.satra.wallet.ui.theme.SatraTheme
import dev.satra.wallet.wallet.bip39.Bip39MnemonicValidation
import dev.satra.wallet.wallet.bip39.Bip39MnemonicValidator

enum class WalletImportMethod(val routeSegment: String, @StringRes val labelRes: Int) {
    RecoveryPhrase(
        routeSegment = "recovery-phrase",
        labelRes = R.string.wallet_setup_import_method_recovery_phrase,
    ),
    PrivateKey(
        routeSegment = "private-key",
        labelRes = R.string.wallet_setup_import_method_private_key,
    ),
    WatchOnly(
        routeSegment = "watch-only",
        labelRes = R.string.wallet_setup_import_method_watch_only,
    );

    companion object {
        fun fromRoute(routeSegment: String?): WalletImportMethod =
            entries.firstOrNull { it.routeSegment == routeSegment } ?: RecoveryPhrase
    }
}

enum class WalletImportNetwork(
    val routeSegment: String,
    val networkId: String,
    @StringRes val labelRes: Int,
    @StringRes val familyRes: Int,
    @DrawableRes val logoRes: Int,
) {
    Bitcoin("bitcoin", "bitcoin", R.string.wallet_setup_network_bitcoin, R.string.wallet_setup_network_family_utxo, R.drawable.ic_chain_bitcoin),
    BitcoinCash("bitcoin-cash", "bitcoinCash", R.string.wallet_setup_network_bitcoin_cash, R.string.wallet_setup_network_family_utxo, R.drawable.ic_chain_bitcoin_cash),
    Dogecoin("dogecoin", "dogecoin", R.string.wallet_setup_network_dogecoin, R.string.wallet_setup_network_family_utxo, R.drawable.ic_chain_dogecoin),
    Litecoin("litecoin", "litecoin", R.string.wallet_setup_network_litecoin, R.string.wallet_setup_network_family_utxo, R.drawable.ic_chain_litecoin),
    Ethereum("ethereum", "ethereum", R.string.wallet_setup_network_ethereum, R.string.wallet_setup_network_family_evm, R.drawable.ic_chain_ethereum),
    Arbitrum("arbitrum", "arbitrum", R.string.wallet_setup_network_arbitrum, R.string.wallet_setup_network_family_evm, R.drawable.ic_chain_arbitrum),
    Base("base", "base", R.string.wallet_setup_network_base, R.string.wallet_setup_network_family_evm, R.drawable.ic_chain_base),
    Optimism("optimism", "optimism", R.string.wallet_setup_network_optimism, R.string.wallet_setup_network_family_evm, R.drawable.ic_chain_optimism),
    Scroll("scroll", "scroll", R.string.wallet_setup_network_scroll, R.string.wallet_setup_network_family_evm, R.drawable.ic_chain_scroll),
    ZkSync("zksync-era", "zkSync", R.string.wallet_setup_network_zksync, R.string.wallet_setup_network_family_evm, R.drawable.ic_chain_zksync),
    Polygon("polygon", "polygon", R.string.wallet_setup_network_polygon, R.string.wallet_setup_network_family_evm, R.drawable.ic_chain_polygon),
    BnbChain("bnb-chain", "bnbChain", R.string.wallet_setup_network_bnb_chain, R.string.wallet_setup_network_family_evm, R.drawable.ic_chain_bnb_chain),
    OpBnb("opbnb", "opBNB", R.string.wallet_setup_network_opbnb, R.string.wallet_setup_network_family_evm, R.drawable.ic_chain_opbnb),
    Avalanche("avalanche", "avalanche", R.string.wallet_setup_network_avalanche, R.string.wallet_setup_network_family_evm, R.drawable.ic_chain_avalanche),
    Celo("celo", "celo", R.string.wallet_setup_network_celo, R.string.wallet_setup_network_family_evm, R.drawable.ic_chain_celo),
    KavaEvm("kava-evm", "kavaEvm", R.string.wallet_setup_network_kava_evm, R.string.wallet_setup_network_family_evm, R.drawable.ic_chain_kava_evm),
    Aptos("aptos", "aptos", R.string.wallet_setup_network_aptos, R.string.wallet_setup_network_family_non_evm, R.drawable.ic_chain_aptos),
    Near("near", "near", R.string.wallet_setup_network_near, R.string.wallet_setup_network_family_non_evm, R.drawable.ic_chain_near),
    Polkadot("polkadot", "polkadot", R.string.wallet_setup_network_polkadot, R.string.wallet_setup_network_family_non_evm, R.drawable.ic_chain_polkadot),
    XrpLedger("xrp-ledger", "ripple", R.string.wallet_setup_network_xrp_ledger, R.string.wallet_setup_network_family_non_evm, R.drawable.ic_chain_xrp_ledger),
    Solana("solana", "solana", R.string.wallet_setup_network_solana, R.string.wallet_setup_network_family_non_evm, R.drawable.ic_chain_solana),
    Stellar("stellar", "stellar", R.string.wallet_setup_network_stellar, R.string.wallet_setup_network_family_non_evm, R.drawable.ic_chain_stellar),
    Sui("sui", "sui", R.string.wallet_setup_network_sui, R.string.wallet_setup_network_family_non_evm, R.drawable.ic_chain_sui),
    Ton("ton", "ton", R.string.wallet_setup_network_ton, R.string.wallet_setup_network_family_non_evm, R.drawable.ic_chain_ton),
    Tron("tron", "tron", R.string.wallet_setup_network_tron, R.string.wallet_setup_network_family_non_evm, R.drawable.ic_chain_tron),
    Kava("kava", "kava", R.string.wallet_setup_network_kava, R.string.wallet_setup_network_family_non_evm, R.drawable.ic_chain_kava);

    companion object {
        fun fromRoute(routeSegment: String?): WalletImportNetwork? =
            entries.firstOrNull { it.routeSegment == routeSegment }
    }
}

enum class ImportSetupPage {
    Method,
    Chain,
    RecoveryPhrase,
    PrivateKey,
    WatchOnlyAddress,
    Review,
}

enum class WalletSetupFlow(val routeSegment: String) {
    Create("create"),
    Import("import");

    companion object {
        fun fromRoute(routeSegment: String?): WalletSetupFlow =
            entries.firstOrNull { it.routeSegment == routeSegment } ?: Create
    }
}

private enum class SecuritySetupPage {
    Passcode,
    ConfirmPasscode,
    Biometrics,
    Success,
}

@Composable
fun CreateWalletPhraseScreen(
    mnemonic: String,
    mnemonicWordCount: Int = DEFAULT_RECOVERY_PHRASE_WORD_COUNT,
    passphrase: String = "",
    screenshotWarningRequests: Int = 0,
    settings: SatraSettings = SatraSettings(),
    onMnemonicWordCountChange: (Int) -> Unit = {},
    onPassphraseChange: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onGenerateNewMnemonic: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    val clipboardLabel = stringResource(R.string.wallet_setup_recovery_phrase_clip_label)
    val copiedMessage = stringResource(R.string.wallet_setup_recovery_phrase_copied)
    var handledScreenshotRequests by rememberSaveable {
        mutableStateOf(screenshotWarningRequests)
    }
    var showScreenshotWarning by rememberSaveable { mutableStateOf(false) }
    var showRecoveryPhraseOptions by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(screenshotWarningRequests) {
        if (screenshotWarningRequests > handledScreenshotRequests) {
            handledScreenshotRequests = screenshotWarningRequests
            showScreenshotWarning = true
        }
    }

    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_create_phrase,
        page = createWalletPages[1],
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        onBack = onBack,
        onPrimaryClick = onNext,
        topBarAction = { performHaptic ->
            RecoveryPhraseOptionsIconButton(
                onClick = {
                    performHaptic()
                    showRecoveryPhraseOptions = true
                },
            )
        },
    ) { performHaptic ->
        HiddenPhrasePanel(
            mnemonic = mnemonic,
            onCopyClick = {
                performHaptic()
                clipboardManager?.setPrimaryClip(
                    ClipData.newPlainText(
                        clipboardLabel,
                        mnemonic,
                    ),
                )
                Toast.makeText(
                    context,
                    copiedMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )

        if (showRecoveryPhraseOptions) {
            RecoveryPhraseOptionsBottomSheet(
                selectedWordCount = mnemonicWordCount,
                passphrase = passphrase,
                onWordCountSelected = onMnemonicWordCountChange,
                onPassphraseChange = onPassphraseChange,
                onDismissRequest = {
                    showRecoveryPhraseOptions = false
                },
                performHaptic = performHaptic,
            )
        }

        if (showScreenshotWarning) {
            RecoveryPhraseScreenshotWarningSheet(
                onGenerateNewMnemonic = {
                    performHaptic()
                    onGenerateNewMnemonic()
                    showScreenshotWarning = false
                },
                onKeepCurrent = {
                    performHaptic()
                    showScreenshotWarning = false
                },
                onDismissRequest = {
                    showScreenshotWarning = false
                },
            )
        }
    }
}

@Composable
fun CreateWalletBackupScreen(
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    var privatePlaceChecked by rememberSaveable { mutableStateOf(false) }
    var noScreenshotChecked by rememberSaveable { mutableStateOf(false) }
    var restoreChecked by rememberSaveable { mutableStateOf(false) }
    val backupConfirmed = privatePlaceChecked && noScreenshotChecked && restoreChecked

    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_create_backup,
        page = createWalletPages[0],
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        secondaryTextRes = R.string.wallet_setup_action_cancel,
        primaryEnabled = backupConfirmed,
        onBack = onBack,
        onPrimaryClick = onNext,
        onSecondaryClick = onBack,
    ) { performHaptic ->
        BackupChecklist(
            privatePlaceChecked = privatePlaceChecked,
            noScreenshotChecked = noScreenshotChecked,
            restoreChecked = restoreChecked,
            onPrivatePlaceCheckedChange = { checked ->
                performHaptic()
                privatePlaceChecked = checked
            },
            onNoScreenshotCheckedChange = { checked ->
                performHaptic()
                noScreenshotChecked = checked
            },
            onRestoreCheckedChange = { checked ->
                performHaptic()
                restoreChecked = checked
            },
        )
    }
}

@Composable
fun ImportMethodScreen(
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onMethodContinue: (WalletImportMethod) -> Unit = {},
) {
    var selectedMethod by rememberSaveable { mutableStateOf(WalletImportMethod.RecoveryPhrase) }

    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_import_method,
        page = importSetupPage(
            page = ImportSetupPage.Method,
            method = selectedMethod,
        ),
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        secondaryTextRes = R.string.wallet_setup_action_cancel,
        onBack = onBack,
        onPrimaryClick = { onMethodContinue(selectedMethod) },
        onSecondaryClick = onBack,
    ) { performHaptic ->
        ImportMethodPanel(
            selectedMethod = selectedMethod,
            onMethodSelected = { method ->
                performHaptic()
                selectedMethod = method
            },
        )
    }
}

@Composable
fun ImportRecoveryPhraseScreen(
    settings: SatraSettings = SatraSettings(),
    scannedRecoveryPhrase: String = "",
    onBack: () -> Unit = {},
    onScanClick: () -> Unit = {},
    onScannedRecoveryPhraseConsumed: () -> Unit = {},
    onNext: (String, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    val clipboardEmptyMessage = stringResource(R.string.wallet_setup_clipboard_empty)
    var recoveryPhrase by rememberSaveable { mutableStateOf("") }
    var passphrase by rememberSaveable { mutableStateOf("") }
    var showRecoveryPhraseOptions by rememberSaveable { mutableStateOf(false) }
    val phraseValidation = remember(recoveryPhrase) {
        Bip39MnemonicValidator.validate(recoveryPhrase)
    }

    LaunchedEffect(scannedRecoveryPhrase) {
        if (scannedRecoveryPhrase.isNotBlank()) {
            recoveryPhrase = scannedRecoveryPhrase
            onScannedRecoveryPhraseConsumed()
        }
    }

    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_import_recovery_phrase,
        page = importSetupPage(
            page = ImportSetupPage.RecoveryPhrase,
            method = WalletImportMethod.RecoveryPhrase,
        ),
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        primaryEnabled = phraseValidation.isValid,
        onBack = onBack,
        onPrimaryClick = { onNext(recoveryPhrase.trim(), passphrase) },
        topBarAction = { performHaptic ->
            RecoveryPhraseOptionsIconButton(
                onClick = {
                    performHaptic()
                    showRecoveryPhraseOptions = true
                },
            )
        },
    ) { performHaptic ->
        RecoveryPhraseEntry(
            recoveryPhrase = recoveryPhrase,
            onRecoveryPhraseChange = { recoveryPhrase = it },
            validation = phraseValidation,
            onPasteClick = {
                performHaptic()
                val pastedText = clipboardManager?.primaryClip
                    ?.takeIf { clipData -> clipData.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()
                    ?.trim()
                    .orEmpty()

                if (pastedText.isNotBlank()) {
                    recoveryPhrase = pastedText
                } else {
                    Toast.makeText(
                        context,
                        clipboardEmptyMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onScanClick = {
                performHaptic()
                onScanClick()
            },
        )

        if (showRecoveryPhraseOptions) {
            RecoveryPhraseOptionsBottomSheet(
                selectedWordCount = null,
                passphrase = passphrase,
                onPassphraseChange = { passphrase = it },
                onDismissRequest = {
                    showRecoveryPhraseOptions = false
                },
                performHaptic = performHaptic,
            )
        }
    }
}

@Composable
fun ImportChainScreen(
    method: WalletImportMethod,
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onNetworkContinue: (WalletImportNetwork) -> Unit = {},
) {
    var selectedNetwork by rememberSaveable { mutableStateOf(WalletImportNetwork.Bitcoin) }

    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_import_chain,
        page = importSetupPage(
            page = ImportSetupPage.Chain,
            method = method,
        ),
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        onBack = onBack,
        onPrimaryClick = { onNetworkContinue(selectedNetwork) },
    ) { performHaptic ->
        NetworkSelectionPanel(
            selectedNetwork = selectedNetwork,
            onNetworkSelected = { network ->
                performHaptic()
                selectedNetwork = network
            },
        )
    }
}

@Composable
fun ImportPrivateKeyScreen(
    network: WalletImportNetwork,
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onNext: (String) -> Unit = {},
) {
    var privateKey by rememberSaveable(network.routeSegment) { mutableStateOf("") }
    val isPrivateKeyValid = privateKey.trim().isNotEmpty()

    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_import_private_key,
        page = importSetupPage(
            page = ImportSetupPage.PrivateKey,
            method = WalletImportMethod.PrivateKey,
        ),
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        primaryEnabled = isPrivateKeyValid,
        onBack = onBack,
        onPrimaryClick = { onNext(privateKey.trim()) },
    ) {
        PrivateKeyEntry(
            selectedNetwork = network,
            privateKey = privateKey,
            onPrivateKeyChange = { privateKey = it },
        )
    }
}

@Composable
fun ImportWatchOnlyAddressScreen(
    network: WalletImportNetwork,
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onNext: (String) -> Unit = {},
) {
    var address by rememberSaveable(network.routeSegment) { mutableStateOf("") }
    val isAddressValid = address.trim().isNotEmpty()

    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_import_watch_only,
        page = importSetupPage(
            page = ImportSetupPage.WatchOnlyAddress,
            method = WalletImportMethod.WatchOnly,
        ),
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        primaryEnabled = isAddressValid,
        onBack = onBack,
        onPrimaryClick = { onNext(address.trim()) },
    ) {
        WatchOnlyAddressEntry(
            selectedNetwork = network,
            address = address,
            onAddressChange = { address = it },
        )
    }
}

@Composable
fun ImportReviewScreen(
    method: WalletImportMethod,
    network: WalletImportNetwork? = null,
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_import_review,
        page = importSetupPage(
            page = ImportSetupPage.Review,
            method = method,
        ),
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        onBack = onBack,
        onPrimaryClick = onNext,
    ) {
        ImportReviewPanel(
            method = method,
            network = network,
        )
    }
}

@Composable
fun SetupPasscodeScreen(
    flow: WalletSetupFlow,
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onPasscodeCreated: (String) -> Unit = {},
    onSkip: () -> Unit = {},
) {
    var passcode by rememberSaveable { mutableStateOf("") }
    var passcodeLength by rememberSaveable { mutableStateOf(DEFAULT_PASSCODE_LENGTH) }
    var showPasscodeOptions by rememberSaveable { mutableStateOf(false) }

    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_create_passcode,
        page = securitySetupPage(
            flow = flow,
            page = SecuritySetupPage.Passcode,
        ),
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_set_passcode,
        primaryEnabled = passcode.length == passcodeLength,
        onBack = onBack,
        onPrimaryClick = { onPasscodeCreated(passcode) },
    ) { performHaptic ->
        PasscodeEntryPanel(
            passcode = passcode,
            passcodeLength = passcodeLength,
            onPasscodeChange = { value ->
                passcode = value.filter(Char::isDigit).take(passcodeLength)
            },
            onOptionsClick = {
                performHaptic()
                showPasscodeOptions = true
            },
        )

        if (showPasscodeOptions) {
            PasscodeOptionsBottomSheet(
                selectedPasscodeLength = passcodeLength,
                onPasscodeLengthSelected = { selectedLength ->
                    passcodeLength = selectedLength
                    passcode = passcode.take(selectedLength)
                    showPasscodeOptions = false
                },
                onSkip = {
                    showPasscodeOptions = false
                    onSkip()
                },
                onDismissRequest = {
                    showPasscodeOptions = false
                },
                performHaptic = performHaptic,
            )
        }
    }
}

@Composable
fun SetupConfirmPasscodeScreen(
    flow: WalletSetupFlow,
    expectedPasscode: String,
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onConfirmed: () -> Unit = {},
) {
    var confirmation by rememberSaveable(expectedPasscode) { mutableStateOf("") }
    val passcodeLength = expectedPasscode.length.takeIf { it in supportedPasscodeLengths }
        ?: DEFAULT_PASSCODE_LENGTH
    val isComplete = confirmation.length == passcodeLength
    val matches = expectedPasscode.isNotBlank() && confirmation == expectedPasscode

    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_confirm_passcode,
        page = securitySetupPage(
            flow = flow,
            page = SecuritySetupPage.ConfirmPasscode,
        ),
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_confirm_passcode,
        primaryEnabled = matches,
        onBack = onBack,
        onPrimaryClick = onConfirmed,
    ) {
        PasscodeEntryPanel(
            passcode = confirmation,
            passcodeLength = passcodeLength,
            onPasscodeChange = { value ->
                confirmation = value.filter(Char::isDigit).take(passcodeLength)
            },
            noteRes = if (isComplete && !matches) {
                R.string.wallet_setup_passcode_mismatch
            } else {
                R.string.wallet_setup_confirm_passcode_note
            },
            isError = isComplete && !matches,
        )
    }
}

@Composable
fun SetupBiometricsScreen(
    flow: WalletSetupFlow,
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onContinue: (Boolean) -> Unit = {},
    onSkip: () -> Unit = {},
) {
    var biometricsEnabled by rememberSaveable { mutableStateOf(true) }

    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_biometrics,
        page = securitySetupPage(
            flow = flow,
            page = SecuritySetupPage.Biometrics,
        ),
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        secondaryTextRes = R.string.wallet_setup_action_skip_for_now,
        onBack = onBack,
        onPrimaryClick = { onContinue(biometricsEnabled) },
        onSecondaryClick = onSkip,
    ) { performHaptic ->
        BiometricChoicePanel(
            biometricsEnabled = biometricsEnabled,
            onBiometricsEnabledChange = { enabled ->
                performHaptic()
                biometricsEnabled = enabled
            },
        )
    }
}

@Composable
fun SetupSuccessScreen(
    flow: WalletSetupFlow,
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onOpenWallet: () -> Unit = {},
) {
    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_success,
        page = securitySetupPage(
            flow = flow,
            page = SecuritySetupPage.Success,
        ),
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_open_wallet,
        onBack = onBack,
        onPrimaryClick = onOpenWallet,
    ) {
        SuccessSummaryPanel(flow = flow)
    }
}

@Composable
private fun WalletSetupRouteScreen(
    @StringRes titleRes: Int,
    page: SetupPageContent,
    settings: SatraSettings,
    @StringRes primaryTextRes: Int,
    @StringRes secondaryTextRes: Int? = null,
    primaryEnabled: Boolean = true,
    onBack: () -> Unit,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: (() -> Unit)? = null,
    topBarAction: @Composable (performHaptic: () -> Unit) -> Unit = {},
    content: @Composable (performHaptic: () -> Unit) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val performHaptic = remember(settings.hapticsEnabled, hapticFeedback) {
        { performSetupHaptic(hapticFeedback, settings.hapticsEnabled) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val windowSize = remember(maxWidth) { SetupWindowSize.from(maxWidth) }
        val compactHeight = maxHeight < 780.dp
        val scrollFallback = maxHeight < 640.dp
        val contentMaxWidth = when (windowSize) {
            SetupWindowSize.Compact -> 520.dp
            SetupWindowSize.Medium -> 640.dp
            SetupWindowSize.Expanded -> 1120.dp
        }

        SetupBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(
                    horizontal = when (windowSize) {
                        SetupWindowSize.Compact -> 24.dp
                        SetupWindowSize.Medium -> 48.dp
                        SetupWindowSize.Expanded -> 72.dp
                    },
                    vertical = if (compactHeight) 16.dp else 24.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxSize()
                    .then(if (scrollFallback) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            ) {
                SetupTopBar(
                    titleRes = titleRes,
                    onBack = {
                        performHaptic()
                        onBack()
                    },
                    action = {
                        topBarAction(performHaptic)
                    },
                )

                Spacer(modifier = Modifier.height(if (compactHeight) 20.dp else 28.dp))

                SetupContentFrame(
                    page = page,
                    windowSize = windowSize,
                    compactHeight = compactHeight,
                    modifier = if (scrollFallback) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    },
                ) {
                    content(performHaptic)
                }

                Spacer(modifier = Modifier.height(if (compactHeight) 14.dp else 22.dp))

                SetupActions(
                    primaryTextRes = primaryTextRes,
                    secondaryTextRes = secondaryTextRes,
                    primaryEnabled = primaryEnabled,
                    onPrimaryClick = {
                        performHaptic()
                        onPrimaryClick()
                    },
                    onSecondaryClick = onSecondaryClick?.let { secondaryClick ->
                        {
                            performHaptic()
                            secondaryClick()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SetupTopBar(
    @StringRes titleRes: Int,
    onBack: () -> Unit,
    action: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.wallet_setup_back_content_description),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )

        action()
    }
}

@Composable
private fun RecoveryPhraseOptionsIconButton(
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.wallet_setup_recovery_phrase_options),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SetupContentFrame(
    page: SetupPageContent,
    windowSize: SetupWindowSize,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when (windowSize) {
        SetupWindowSize.Expanded -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(56.dp),
        ) {
            SetupPageBody(
                page = page,
                compactHeight = compactHeight,
                modifier = Modifier.weight(1f),
                content = content,
            )
        }

        SetupWindowSize.Compact,
        SetupWindowSize.Medium -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SetupPageBody(
                page = page,
                compactHeight = compactHeight,
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }
    }
}

@Composable
private fun SetupPageIcon(
    page: SetupPageContent,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val iconContainerSize = if (compactHeight) 58.dp else 66.dp
    val iconSize = if (compactHeight) 34.dp else 38.dp

    Box(
        modifier = modifier
            .size(iconContainerSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(page.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun SetupPageBody(
    page: SetupPageContent,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        SetupPageIcon(
            page = page,
            compactHeight = compactHeight,
        )

        Spacer(modifier = Modifier.height(if (compactHeight) 12.dp else 14.dp))

        Text(
            text = stringResource(page.titleRes),
            style = if (compactHeight) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.headlineLarge
            },
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(20.dp))
        content()
    }
}

@Composable
private fun HiddenPhrasePanel(
    mnemonic: String,
    onCopyClick: () -> Unit,
) {
    val words = remember(mnemonic) {
        mnemonic.split(Regex("\\s+")).filter(String::isNotBlank)
    }

    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            words.chunked(3).forEachIndexed { rowIndex, rowWords ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowWords.forEachIndexed { columnIndex, word ->
                        val wordNumber = rowIndex * 3 + columnIndex + 1
                        RecoveryWordChip(
                            number = wordNumber,
                            text = word,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - rowWords.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            OutlinedButton(
                onClick = onCopyClick,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, SatraButtonSecondaryBorder),
            ) {
                Text(
                    text = stringResource(R.string.wallet_setup_recovery_phrase_copy),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryPhraseOptionsBottomSheet(
    selectedWordCount: Int?,
    passphrase: String,
    onWordCountSelected: (Int) -> Unit = {},
    onPassphraseChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    performHaptic: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var passphraseEnabled by rememberSaveable {
        mutableStateOf(passphrase.isNotEmpty())
    }

    LaunchedEffect(passphrase) {
        if (passphrase.isNotEmpty()) {
            passphraseEnabled = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.wallet_setup_recovery_phrase_options_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            selectedWordCount?.let { wordCount ->
                PasscodeOptionRow(
                    titleRes = R.string.wallet_setup_recovery_phrase_use_12,
                    bodyRes = R.string.wallet_setup_recovery_phrase_use_12_body,
                    selected = wordCount == DEFAULT_RECOVERY_PHRASE_WORD_COUNT,
                    onClick = {
                        performHaptic()
                        onWordCountSelected(DEFAULT_RECOVERY_PHRASE_WORD_COUNT)
                    },
                )
                PasscodeOptionRow(
                    titleRes = R.string.wallet_setup_recovery_phrase_use_24,
                    bodyRes = R.string.wallet_setup_recovery_phrase_use_24_body,
                    selected = wordCount == STRONG_RECOVERY_PHRASE_WORD_COUNT,
                    onClick = {
                        performHaptic()
                        onWordCountSelected(STRONG_RECOVERY_PHRASE_WORD_COUNT)
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            SetupSwitchRow(
                titleRes = R.string.wallet_setup_passphrase_toggle,
                bodyRes = R.string.wallet_setup_passphrase_toggle_body,
                checked = passphraseEnabled,
                onCheckedChange = { enabled ->
                    performHaptic()
                    passphraseEnabled = enabled
                    if (!enabled) {
                        onPassphraseChange("")
                    }
                },
            )
            if (passphraseEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                OptionalPassphraseField(
                    passphrase = passphrase,
                    onPassphraseChange = onPassphraseChange,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    performHaptic()
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.wallet_setup_action_done),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryPhraseScreenshotWarningSheet(
    onGenerateNewMnemonic: () -> Unit,
    onKeepCurrent: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.wallet_setup_screenshot_warning_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.wallet_setup_screenshot_warning_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onGenerateNewMnemonic,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.wallet_setup_screenshot_generate_new),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            OutlinedButton(
                onClick = onKeepCurrent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                border = BorderStroke(1.dp, SatraButtonSecondaryBorder),
            ) {
                Text(
                    text = stringResource(R.string.wallet_setup_screenshot_keep_current),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun BackupChecklist(
    privatePlaceChecked: Boolean,
    noScreenshotChecked: Boolean,
    restoreChecked: Boolean,
    onPrivatePlaceCheckedChange: (Boolean) -> Unit,
    onNoScreenshotCheckedChange: (Boolean) -> Unit,
    onRestoreCheckedChange: (Boolean) -> Unit,
) {
    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SetupCheckboxRow(
                labelRes = R.string.wallet_setup_backup_private_place,
                checked = privatePlaceChecked,
                onCheckedChange = onPrivatePlaceCheckedChange,
            )
            SetupCheckboxRow(
                labelRes = R.string.wallet_setup_backup_no_screenshots,
                checked = noScreenshotChecked,
                onCheckedChange = onNoScreenshotCheckedChange,
            )
            SetupCheckboxRow(
                labelRes = R.string.wallet_setup_backup_restore_check,
                checked = restoreChecked,
                onCheckedChange = onRestoreCheckedChange,
            )
        }
    }
}

@Composable
private fun PasscodeEntryPanel(
    passcode: String,
    passcodeLength: Int,
    onPasscodeChange: (String) -> Unit,
    @StringRes noteRes: Int? = null,
    isError: Boolean = false,
    onOptionsClick: (() -> Unit)? = null,
) {
    FramedTool {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PasscodePinInput(
                passcode = passcode,
                passcodeLength = passcodeLength,
                onPasscodeChange = onPasscodeChange,
            )

            onOptionsClick?.let {
                TextButton(onClick = it) {
                    Text(
                        text = stringResource(R.string.wallet_setup_passcode_options),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            noteRes?.let { messageRes ->
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PasscodePinInput(
    passcode: String,
    passcodeLength: Int,
    onPasscodeChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(passcodeLength) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                focusRequester.requestFocus()
                keyboardController?.show()
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = passcode,
            onValueChange = onPasscodeChange,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Transparent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(Color.Transparent),
            singleLine = true,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(passcodeLength) { index ->
                PasscodeDot(filled = index < passcode.length)
            }
        }
    }
}

@Composable
private fun PasscodeDot(filled: Boolean) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(
                if (filled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasscodeOptionsBottomSheet(
    selectedPasscodeLength: Int,
    onPasscodeLengthSelected: (Int) -> Unit,
    onSkip: () -> Unit,
    onDismissRequest: () -> Unit,
    performHaptic: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.wallet_setup_passcode_options_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            PasscodeOptionRow(
                titleRes = R.string.wallet_setup_passcode_use_six,
                bodyRes = R.string.wallet_setup_passcode_use_six_body,
                selected = selectedPasscodeLength == DEFAULT_PASSCODE_LENGTH,
                onClick = {
                    performHaptic()
                    onPasscodeLengthSelected(DEFAULT_PASSCODE_LENGTH)
                },
            )
            PasscodeOptionRow(
                titleRes = R.string.wallet_setup_passcode_use_four,
                bodyRes = R.string.wallet_setup_passcode_use_four_body,
                selected = selectedPasscodeLength == SHORT_PASSCODE_LENGTH,
                onClick = {
                    performHaptic()
                    onPasscodeLengthSelected(SHORT_PASSCODE_LENGTH)
                },
            )
            PasscodeOptionRow(
                titleRes = R.string.wallet_setup_action_skip_for_now,
                bodyRes = R.string.wallet_setup_passcode_skip_body,
                selected = null,
                onClick = {
                    performHaptic()
                    onSkip()
                },
            )
        }
    }
}

@Composable
private fun PasscodeOptionRow(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    selected: Boolean?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        selected?.let {
            RadioButton(
                selected = it,
                onClick = null,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BiometricChoicePanel(
    biometricsEnabled: Boolean,
    onBiometricsEnabledChange: (Boolean) -> Unit,
) {
    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SetupSwitchRow(
                titleRes = R.string.wallet_setup_biometrics_toggle,
                bodyRes = R.string.wallet_setup_biometrics_toggle_body,
                checked = biometricsEnabled,
                onCheckedChange = onBiometricsEnabledChange,
            )
            Text(
                text = stringResource(R.string.wallet_setup_biometrics_note),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuccessSummaryPanel(flow: WalletSetupFlow) {
    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReviewTextRow(
                titleRes = R.string.wallet_setup_success_wallet,
                value = stringResource(
                    if (flow == WalletSetupFlow.Create) {
                        R.string.wallet_setup_success_wallet_created
                    } else {
                        R.string.wallet_setup_success_wallet_imported
                    },
                ),
            )
            ReviewTextRow(
                titleRes = R.string.wallet_setup_success_security,
                value = stringResource(R.string.wallet_setup_success_security_optional),
            )
            ReviewTextRow(
                titleRes = R.string.wallet_setup_success_access,
                value = stringResource(R.string.wallet_setup_success_access_ready),
            )
        }
    }
}

@Composable
private fun ImportMethodPanel(
    selectedMethod: WalletImportMethod,
    onMethodSelected: (WalletImportMethod) -> Unit,
) {
    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ImportMethodRow(
                titleRes = R.string.wallet_setup_import_method_recovery_phrase,
                bodyRes = R.string.wallet_setup_import_method_recovery_phrase_body,
                selected = selectedMethod == WalletImportMethod.RecoveryPhrase,
                onClick = {
                    onMethodSelected(WalletImportMethod.RecoveryPhrase)
                },
            )
            ImportMethodRow(
                titleRes = R.string.wallet_setup_import_method_private_key,
                bodyRes = R.string.wallet_setup_import_method_private_key_body,
                selected = selectedMethod == WalletImportMethod.PrivateKey,
                onClick = {
                    onMethodSelected(WalletImportMethod.PrivateKey)
                },
            )
            ImportMethodRow(
                titleRes = R.string.wallet_setup_import_method_watch_only,
                bodyRes = R.string.wallet_setup_import_method_watch_only_body,
                selected = selectedMethod == WalletImportMethod.WatchOnly,
                onClick = {
                    onMethodSelected(WalletImportMethod.WatchOnly)
                },
            )
        }
    }
}

@Composable
private fun RecoveryPhraseEntry(
    recoveryPhrase: String,
    onRecoveryPhraseChange: (String) -> Unit,
    validation: Bip39MnemonicValidation,
    onPasteClick: () -> Unit,
    onScanClick: () -> Unit,
) {
    val showError = recoveryPhrase.isNotBlank() && !validation.isValid
    val messageColor = when {
        validation.isValid -> MaterialTheme.colorScheme.primary
        showError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = recoveryPhrase,
                onValueChange = onRecoveryPhraseChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 148.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                isError = showError,
                placeholder = {
                    Text(text = stringResource(R.string.wallet_setup_recovery_phrase_placeholder))
                },
                supportingText = {
                    Text(
                        text = recoveryPhraseValidationMessage(validation),
                        color = messageColor,
                    )
                },
                minLines = 4,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onPasteClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    border = BorderStroke(1.dp, SatraButtonSecondaryBorder),
                ) {
                    Text(
                        text = stringResource(R.string.wallet_setup_action_paste),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Button(
                    onClick = onScanClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.wallet_setup_action_scan),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionalPassphraseField(
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = passphrase,
        onValueChange = onPassphraseChange,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        label = {
            Text(text = stringResource(R.string.wallet_setup_passphrase_label))
        },
        placeholder = {
            Text(text = stringResource(R.string.wallet_setup_passphrase_placeholder))
        },
        supportingText = {
            Text(text = stringResource(R.string.wallet_setup_passphrase_note))
        },
    )
}

@Composable
private fun recoveryPhraseValidationMessage(validation: Bip39MnemonicValidation): String =
    when (validation) {
        Bip39MnemonicValidation.Empty -> {
            stringResource(R.string.wallet_setup_recovery_phrase_help)
        }

        is Bip39MnemonicValidation.Valid -> {
            stringResource(R.string.wallet_setup_recovery_phrase_valid, validation.wordCount)
        }

        is Bip39MnemonicValidation.InvalidWordCount -> {
            stringResource(R.string.wallet_setup_recovery_phrase_invalid_length)
        }

        is Bip39MnemonicValidation.UnknownWord -> {
            stringResource(R.string.wallet_setup_recovery_phrase_unknown_word, validation.word)
        }

        Bip39MnemonicValidation.InvalidChecksum -> {
            stringResource(R.string.wallet_setup_recovery_phrase_invalid_checksum)
        }
    }

@Composable
private fun NetworkSelectionPanel(
    selectedNetwork: WalletImportNetwork,
    onNetworkSelected: (WalletImportNetwork) -> Unit,
) {
    FramedTool {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WalletImportNetwork.entries.forEach { network ->
                NetworkRow(
                    network = network,
                    selected = selectedNetwork == network,
                    onClick = { onNetworkSelected(network) },
                )
            }
        }
    }
}

@Composable
private fun PrivateKeyEntry(
    selectedNetwork: WalletImportNetwork,
    privateKey: String,
    onPrivateKeyChange: (String) -> Unit,
) {
    SecretEntryPanel(
        value = privateKey,
        onValueChange = onPrivateKeyChange,
        labelRes = R.string.wallet_setup_private_key_label,
        placeholderRes = R.string.wallet_setup_private_key_placeholder,
        noteRes = R.string.wallet_setup_private_key_note,
        selectedNetwork = selectedNetwork,
    )
}

@Composable
private fun WatchOnlyAddressEntry(
    selectedNetwork: WalletImportNetwork,
    address: String,
    onAddressChange: (String) -> Unit,
) {
    SecretEntryPanel(
        value = address,
        onValueChange = onAddressChange,
        labelRes = R.string.wallet_setup_watch_address_label,
        placeholderRes = R.string.wallet_setup_watch_address_placeholder,
        noteRes = R.string.wallet_setup_watch_address_note,
        selectedNetwork = selectedNetwork,
    )
}

@Composable
private fun SecretEntryPanel(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
    @StringRes placeholderRes: Int,
    @StringRes noteRes: Int,
    selectedNetwork: WalletImportNetwork,
) {
    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectedNetworkPill(selectedNetwork = selectedNetwork)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = {
                    Text(text = stringResource(placeholderRes))
                },
                label = {
                    Text(text = stringResource(labelRes))
                },
                minLines = 4,
            )
            Text(
                text = stringResource(noteRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportReviewPanel(
    method: WalletImportMethod = WalletImportMethod.RecoveryPhrase,
    network: WalletImportNetwork? = null,
) {
    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReviewTextRow(
                titleRes = R.string.wallet_setup_review_source,
                value = stringResource(importReviewSourceRes(method)),
            )
            ReviewTextRow(
                titleRes = R.string.wallet_setup_review_networks,
                value = network?.let { stringResource(it.labelRes) }
                    ?: stringResource(R.string.wallet_setup_review_networks_supported),
            )
            ReviewTextRow(
                titleRes = R.string.wallet_setup_review_privacy,
                value = stringResource(R.string.wallet_setup_review_privacy_local),
            )
        }
    }
}

@Composable
private fun FramedTool(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun RecoveryWordChip(
    number: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SetupCheckboxRow(
    @StringRes labelRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SetupSwitchRow(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ImportMethodRow(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NetworkRow(
    network: WalletImportNetwork,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(network.logoRes),
            contentDescription = null,
            modifier = Modifier
                .size(34.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(network.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(network.familyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

@Composable
private fun SelectedNetworkPill(selectedNetwork: WalletImportNetwork) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(selectedNetwork.logoRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.wallet_setup_selected_network),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(selectedNetwork.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ReviewTextRow(
    @StringRes titleRes: Int,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SetupActions(
    @StringRes primaryTextRes: Int,
    @StringRes secondaryTextRes: Int?,
    primaryEnabled: Boolean,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: (() -> Unit)?,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = onPrimaryClick,
            enabled = primaryEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = stringResource(primaryTextRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        if (secondaryTextRes != null && onSecondaryClick != null) {
            OutlinedButton(
                onClick = onSecondaryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                border = BorderStroke(1.dp, SatraButtonSecondaryBorder),
            ) {
                Text(
                    text = stringResource(secondaryTextRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SetupBackground(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme

    Canvas(modifier = modifier.fillMaxSize()) {
        val gridSpacing = 48.dp.toPx()
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = colorScheme.outlineVariant.copy(alpha = 0.12f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
            y += gridSpacing
        }
    }
}

private data class SetupPageContent(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @DrawableRes val iconRes: Int,
)

private val createWalletPages = listOf(
    SetupPageContent(
        titleRes = R.string.wallet_setup_create_step_backup_title,
        bodyRes = R.string.wallet_setup_create_step_backup_body,
        iconRes = R.drawable.ic_brand_list,
    ),
    SetupPageContent(
        titleRes = R.string.wallet_setup_create_step_phrase_title,
        bodyRes = R.string.wallet_setup_create_step_phrase_body,
        iconRes = R.drawable.ic_brand_security,
    ),
)

private fun importSetupPage(
    page: ImportSetupPage,
    method: WalletImportMethod,
): SetupPageContent = when (page) {
    ImportSetupPage.Method -> SetupPageContent(
        titleRes = R.string.wallet_setup_import_step_method_title,
        bodyRes = R.string.wallet_setup_import_step_method_body,
        iconRes = R.drawable.ic_brand_receive,
    )

    ImportSetupPage.Chain -> SetupPageContent(
        titleRes = R.string.wallet_setup_import_step_chain_title,
        bodyRes = R.string.wallet_setup_import_step_chain_body,
        iconRes = R.drawable.ic_brand_assets,
    )

    ImportSetupPage.RecoveryPhrase -> SetupPageContent(
        titleRes = R.string.wallet_setup_import_step_phrase_title,
        bodyRes = R.string.wallet_setup_import_step_phrase_body,
        iconRes = R.drawable.ic_brand_wallet,
    )

    ImportSetupPage.PrivateKey -> SetupPageContent(
        titleRes = R.string.wallet_setup_import_step_private_key_title,
        bodyRes = R.string.wallet_setup_import_step_private_key_body,
        iconRes = R.drawable.ic_brand_security,
    )

    ImportSetupPage.WatchOnlyAddress -> SetupPageContent(
        titleRes = R.string.wallet_setup_import_step_watch_address_title,
        bodyRes = R.string.wallet_setup_import_step_watch_address_body,
        iconRes = R.drawable.ic_brand_scan,
    )

    ImportSetupPage.Review -> SetupPageContent(
        titleRes = if (method == WalletImportMethod.RecoveryPhrase) {
            R.string.wallet_setup_import_step_review_title
        } else {
            R.string.wallet_setup_import_step_branch_review_title
        },
        bodyRes = R.string.wallet_setup_import_step_review_body,
        iconRes = R.drawable.ic_brand_list,
    )
}

private fun securitySetupPage(
    flow: WalletSetupFlow,
    page: SecuritySetupPage,
): SetupPageContent = when (page) {
    SecuritySetupPage.Passcode -> SetupPageContent(
        titleRes = if (flow == WalletSetupFlow.Create) {
            R.string.wallet_setup_passcode_create_title
        } else {
            R.string.wallet_setup_passcode_import_title
        },
        bodyRes = R.string.wallet_setup_passcode_body,
        iconRes = R.drawable.ic_brand_security,
    )

    SecuritySetupPage.ConfirmPasscode -> SetupPageContent(
        titleRes = R.string.wallet_setup_confirm_passcode_title,
        bodyRes = R.string.wallet_setup_confirm_passcode_body,
        iconRes = R.drawable.ic_brand_security,
    )

    SecuritySetupPage.Biometrics -> SetupPageContent(
        titleRes = R.string.wallet_setup_biometrics_title,
        bodyRes = R.string.wallet_setup_biometrics_body,
        iconRes = R.drawable.ic_brand_scan,
    )

    SecuritySetupPage.Success -> SetupPageContent(
        titleRes = if (flow == WalletSetupFlow.Create) {
            R.string.wallet_setup_success_create_title
        } else {
            R.string.wallet_setup_success_import_title
        },
        bodyRes = R.string.wallet_setup_success_body,
        iconRes = R.drawable.ic_brand_wallet,
    )
}

@StringRes
private fun importReviewSourceRes(method: WalletImportMethod): Int = when (method) {
    WalletImportMethod.RecoveryPhrase -> R.string.wallet_setup_review_source_recovery_phrase
    WalletImportMethod.PrivateKey -> R.string.wallet_setup_review_source_private_key
    WalletImportMethod.WatchOnly -> R.string.wallet_setup_review_source_watch_only
}

private enum class SetupWindowSize {
    Compact,
    Medium,
    Expanded;

    companion object {
        fun from(width: Dp): SetupWindowSize = when {
            width >= 840.dp -> Expanded
            width >= 600.dp -> Medium
            else -> Compact
        }
    }
}

private const val DEFAULT_PASSCODE_LENGTH = 6
private const val SHORT_PASSCODE_LENGTH = 4
private const val DEFAULT_RECOVERY_PHRASE_WORD_COUNT = 12
private const val STRONG_RECOVERY_PHRASE_WORD_COUNT = 24
private val supportedPasscodeLengths = setOf(
    SHORT_PASSCODE_LENGTH,
    DEFAULT_PASSCODE_LENGTH,
)

private fun performSetupHaptic(
    hapticFeedback: HapticFeedback,
    enabled: Boolean,
) {
    if (enabled) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun CreateWalletSetupPreview() {
    SatraTheme {
        CreateWalletBackupScreen()
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ImportWalletSetupPreview() {
    SatraTheme {
        ImportMethodScreen()
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PrivateKeyChainSetupPreview() {
    SatraTheme {
        ImportChainScreen(method = WalletImportMethod.PrivateKey)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun WatchOnlyAddressSetupPreview() {
    SatraTheme {
        ImportWatchOnlyAddressScreen(network = WalletImportNetwork.Ethereum)
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun ExpandedCreateWalletSetupPreview() {
    SatraTheme {
        CreateWalletBackupScreen()
    }
}
