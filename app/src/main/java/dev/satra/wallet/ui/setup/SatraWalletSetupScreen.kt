package dev.satra.wallet.ui.setup

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    @StringRes val labelRes: Int,
    @StringRes val familyRes: Int,
) {
    Bitcoin("bitcoin", R.string.wallet_setup_network_bitcoin, R.string.wallet_setup_network_family_utxo),
    BitcoinCash("bitcoin-cash", R.string.wallet_setup_network_bitcoin_cash, R.string.wallet_setup_network_family_utxo),
    Dogecoin("dogecoin", R.string.wallet_setup_network_dogecoin, R.string.wallet_setup_network_family_utxo),
    Litecoin("litecoin", R.string.wallet_setup_network_litecoin, R.string.wallet_setup_network_family_utxo),
    Ethereum("ethereum", R.string.wallet_setup_network_ethereum, R.string.wallet_setup_network_family_evm),
    Arbitrum("arbitrum", R.string.wallet_setup_network_arbitrum, R.string.wallet_setup_network_family_evm),
    Base("base", R.string.wallet_setup_network_base, R.string.wallet_setup_network_family_evm),
    Optimism("optimism", R.string.wallet_setup_network_optimism, R.string.wallet_setup_network_family_evm),
    Scroll("scroll", R.string.wallet_setup_network_scroll, R.string.wallet_setup_network_family_evm),
    ZkSync("zksync-era", R.string.wallet_setup_network_zksync, R.string.wallet_setup_network_family_evm),
    Polygon("polygon", R.string.wallet_setup_network_polygon, R.string.wallet_setup_network_family_evm),
    BnbChain("bnb-chain", R.string.wallet_setup_network_bnb_chain, R.string.wallet_setup_network_family_evm),
    OpBnb("opbnb", R.string.wallet_setup_network_opbnb, R.string.wallet_setup_network_family_evm),
    Avalanche("avalanche", R.string.wallet_setup_network_avalanche, R.string.wallet_setup_network_family_evm),
    Celo("celo", R.string.wallet_setup_network_celo, R.string.wallet_setup_network_family_evm),
    KavaEvm("kava-evm", R.string.wallet_setup_network_kava_evm, R.string.wallet_setup_network_family_evm),
    Aptos("aptos", R.string.wallet_setup_network_aptos, R.string.wallet_setup_network_family_non_evm),
    Near("near", R.string.wallet_setup_network_near, R.string.wallet_setup_network_family_non_evm),
    Polkadot("polkadot", R.string.wallet_setup_network_polkadot, R.string.wallet_setup_network_family_non_evm),
    XrpLedger("xrp-ledger", R.string.wallet_setup_network_xrp_ledger, R.string.wallet_setup_network_family_non_evm),
    Solana("solana", R.string.wallet_setup_network_solana, R.string.wallet_setup_network_family_non_evm),
    Stellar("stellar", R.string.wallet_setup_network_stellar, R.string.wallet_setup_network_family_non_evm),
    Sui("sui", R.string.wallet_setup_network_sui, R.string.wallet_setup_network_family_non_evm),
    Ton("ton", R.string.wallet_setup_network_ton, R.string.wallet_setup_network_family_non_evm),
    Tron("tron", R.string.wallet_setup_network_tron, R.string.wallet_setup_network_family_non_evm),
    Kava("kava", R.string.wallet_setup_network_kava, R.string.wallet_setup_network_family_non_evm);

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
    Security,
}

@Composable
fun CreateWalletIntroScreen(
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_create_intro,
        page = createWalletPages[0],
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        secondaryTextRes = R.string.wallet_setup_action_cancel,
        onBack = onBack,
        onPrimaryClick = onNext,
        onSecondaryClick = onBack,
    ) {
        TrustPillGrid(
            labels = listOf(
                R.string.wallet_setup_create_chip_on_device,
                R.string.wallet_setup_create_chip_non_custodial,
                R.string.wallet_setup_create_chip_multi_chain,
            ),
        )
    }
}

@Composable
fun CreateWalletPhraseScreen(
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_create_phrase,
        page = createWalletPages[1],
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        secondaryTextRes = R.string.wallet_setup_action_previous,
        onBack = onBack,
        onPrimaryClick = onNext,
        onSecondaryClick = onBack,
    ) {
        HiddenPhrasePanel()
    }
}

@Composable
fun CreateWalletBackupScreen(
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_create_backup,
        page = createWalletPages[2],
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        secondaryTextRes = R.string.wallet_setup_action_previous,
        onBack = onBack,
        onPrimaryClick = onNext,
        onSecondaryClick = onBack,
    ) { performHaptic ->
        BackupChecklist(performHaptic = performHaptic)
    }
}

@Composable
fun CreateWalletSecurityScreen(
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onFinish: () -> Unit = {},
) {
    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_create_security,
        page = createWalletPages[3],
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_create_wallet,
        secondaryTextRes = R.string.wallet_setup_action_previous,
        onBack = onBack,
        onPrimaryClick = onFinish,
        onSecondaryClick = onBack,
    ) { performHaptic ->
        CreateReviewPanel(performHaptic = performHaptic)
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
    onBack: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    var recoveryPhrase by rememberSaveable { mutableStateOf("") }
    val phraseValidation = remember(recoveryPhrase) {
        Bip39MnemonicValidator.validate(recoveryPhrase)
    }

    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_import_recovery_phrase,
        page = importSetupPage(
            page = ImportSetupPage.RecoveryPhrase,
            method = WalletImportMethod.RecoveryPhrase,
        ),
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        secondaryTextRes = R.string.wallet_setup_action_previous,
        primaryEnabled = phraseValidation.isValid,
        onBack = onBack,
        onPrimaryClick = onNext,
        onSecondaryClick = onBack,
    ) {
        RecoveryPhraseEntry(
            recoveryPhrase = recoveryPhrase,
            onRecoveryPhraseChange = { recoveryPhrase = it },
            validation = phraseValidation,
        )
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
        secondaryTextRes = R.string.wallet_setup_action_previous,
        onBack = onBack,
        onPrimaryClick = { onNetworkContinue(selectedNetwork) },
        onSecondaryClick = onBack,
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
    onNext: () -> Unit = {},
) {
    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_import_private_key,
        page = importSetupPage(
            page = ImportSetupPage.PrivateKey,
            method = WalletImportMethod.PrivateKey,
        ),
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        secondaryTextRes = R.string.wallet_setup_action_previous,
        onBack = onBack,
        onPrimaryClick = onNext,
        onSecondaryClick = onBack,
    ) {
        PrivateKeyEntry(selectedNetwork = network)
    }
}

@Composable
fun ImportWatchOnlyAddressScreen(
    network: WalletImportNetwork,
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_import_watch_only,
        page = importSetupPage(
            page = ImportSetupPage.WatchOnlyAddress,
            method = WalletImportMethod.WatchOnly,
        ),
        settings = settings,
        primaryTextRes = R.string.wallet_setup_action_continue,
        secondaryTextRes = R.string.wallet_setup_action_previous,
        onBack = onBack,
        onPrimaryClick = onNext,
        onSecondaryClick = onBack,
    ) {
        WatchOnlyAddressEntry(selectedNetwork = network)
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
        secondaryTextRes = R.string.wallet_setup_action_previous,
        onBack = onBack,
        onPrimaryClick = onNext,
        onSecondaryClick = onBack,
    ) {
        ImportReviewPanel(
            method = method,
            network = network,
        )
    }
}

@Composable
fun ImportSecurityScreen(
    method: WalletImportMethod,
    network: WalletImportNetwork? = null,
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onFinish: () -> Unit = {},
) {
    WalletSetupRouteScreen(
        titleRes = R.string.wallet_setup_screen_import_security,
        page = importSetupPage(
            page = ImportSetupPage.Security,
            method = method,
        ),
        settings = settings,
        primaryTextRes = importPrimaryActionRes(method = method),
        secondaryTextRes = R.string.wallet_setup_action_previous,
        onBack = onBack,
        onPrimaryClick = onFinish,
        onSecondaryClick = onBack,
    ) { performHaptic ->
        ImportSecurityPanel(performHaptic = performHaptic)
    }
}

@Composable
private fun WalletSetupRouteScreen(
    @StringRes titleRes: Int,
    page: SetupPageContent,
    settings: SatraSettings,
    @StringRes primaryTextRes: Int,
    @StringRes secondaryTextRes: Int,
    primaryEnabled: Boolean = true,
    onBack: () -> Unit,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
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
                    onSecondaryClick = {
                        performHaptic()
                        onSecondaryClick()
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
    val iconContainerSize = if (compactHeight) 50.dp else 58.dp
    val iconSize = if (compactHeight) 28.dp else 32.dp

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
private fun TrustPillGrid(
    labels: List<Int>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { labelRes ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HiddenPhrasePanel() {
    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { rowIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(3) { columnIndex ->
                        val wordNumber = rowIndex * 3 + columnIndex + 1
                        RecoveryWordChip(
                            number = wordNumber,
                            text = stringResource(R.string.wallet_setup_recovery_word_placeholder),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupChecklist(performHaptic: () -> Unit) {
    var privatePlaceChecked by rememberSaveable { mutableStateOf(false) }
    var noScreenshotChecked by rememberSaveable { mutableStateOf(false) }
    var restoreChecked by rememberSaveable { mutableStateOf(false) }

    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SetupCheckboxRow(
                labelRes = R.string.wallet_setup_backup_private_place,
                checked = privatePlaceChecked,
                onCheckedChange = {
                    performHaptic()
                    privatePlaceChecked = it
                },
            )
            SetupCheckboxRow(
                labelRes = R.string.wallet_setup_backup_no_screenshots,
                checked = noScreenshotChecked,
                onCheckedChange = {
                    performHaptic()
                    noScreenshotChecked = it
                },
            )
            SetupCheckboxRow(
                labelRes = R.string.wallet_setup_backup_restore_check,
                checked = restoreChecked,
                onCheckedChange = {
                    performHaptic()
                    restoreChecked = it
                },
            )
        }
    }
}

@Composable
private fun CreateReviewPanel(performHaptic: () -> Unit) {
    var deviceLockEnabled by rememberSaveable { mutableStateOf(true) }
    var hideBalancesEnabled by rememberSaveable { mutableStateOf(false) }

    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SetupSwitchRow(
                titleRes = R.string.wallet_setup_security_device_lock,
                bodyRes = R.string.wallet_setup_security_device_lock_body,
                checked = deviceLockEnabled,
                onCheckedChange = {
                    performHaptic()
                    deviceLockEnabled = it
                },
            )
            HorizontalDivider()
            SetupSwitchRow(
                titleRes = R.string.wallet_setup_security_hide_balances,
                bodyRes = R.string.wallet_setup_security_hide_balances_body,
                checked = hideBalancesEnabled,
                onCheckedChange = {
                    performHaptic()
                    hideBalancesEnabled = it
                },
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
        }
    }
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
private fun PrivateKeyEntry(selectedNetwork: WalletImportNetwork) {
    var privateKey by rememberSaveable(selectedNetwork.routeSegment) { mutableStateOf("") }

    SecretEntryPanel(
        value = privateKey,
        onValueChange = { privateKey = it },
        labelRes = R.string.wallet_setup_private_key_label,
        placeholderRes = R.string.wallet_setup_private_key_placeholder,
        noteRes = R.string.wallet_setup_private_key_note,
        selectedNetwork = selectedNetwork,
    )
}

@Composable
private fun WatchOnlyAddressEntry(selectedNetwork: WalletImportNetwork) {
    var address by rememberSaveable(selectedNetwork.routeSegment) { mutableStateOf("") }

    SecretEntryPanel(
        value = address,
        onValueChange = { address = it },
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
private fun ImportSecurityPanel(performHaptic: () -> Unit) {
    var scanAccountsEnabled by rememberSaveable { mutableStateOf(true) }
    var labelImportedWallet by rememberSaveable { mutableStateOf(true) }

    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SetupSwitchRow(
                titleRes = R.string.wallet_setup_import_scan_accounts,
                bodyRes = R.string.wallet_setup_import_scan_accounts_body,
                checked = scanAccountsEnabled,
                onCheckedChange = {
                    performHaptic()
                    scanAccountsEnabled = it
                },
            )
            HorizontalDivider()
            SetupSwitchRow(
                titleRes = R.string.wallet_setup_import_label_wallet,
                bodyRes = R.string.wallet_setup_import_label_wallet_body,
                checked = labelImportedWallet,
                onCheckedChange = {
                    performHaptic()
                    labelImportedWallet = it
                },
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
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
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
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
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
    @StringRes secondaryTextRes: Int,
    primaryEnabled: Boolean,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
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
        titleRes = R.string.wallet_setup_create_step_intro_title,
        bodyRes = R.string.wallet_setup_create_step_intro_body,
        iconRes = R.drawable.ic_brand_wallet,
    ),
    SetupPageContent(
        titleRes = R.string.wallet_setup_create_step_phrase_title,
        bodyRes = R.string.wallet_setup_create_step_phrase_body,
        iconRes = R.drawable.ic_brand_security,
    ),
    SetupPageContent(
        titleRes = R.string.wallet_setup_create_step_backup_title,
        bodyRes = R.string.wallet_setup_create_step_backup_body,
        iconRes = R.drawable.ic_brand_list,
    ),
    SetupPageContent(
        titleRes = R.string.wallet_setup_create_step_security_title,
        bodyRes = R.string.wallet_setup_create_step_security_body,
        iconRes = R.drawable.ic_brand_settings,
    ),
)

private fun importPrimaryActionRes(
    method: WalletImportMethod,
): Int = when (method) {
    WalletImportMethod.PrivateKey -> R.string.wallet_setup_action_import_private_key
    WalletImportMethod.WatchOnly -> R.string.wallet_setup_action_add_watch_only
    WalletImportMethod.RecoveryPhrase -> R.string.wallet_setup_action_import_wallet
}

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

    ImportSetupPage.Security -> SetupPageContent(
        titleRes = R.string.wallet_setup_import_step_security_title,
        bodyRes = R.string.wallet_setup_import_step_security_body,
        iconRes = R.drawable.ic_brand_settings,
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
        CreateWalletIntroScreen()
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
        CreateWalletIntroScreen()
    }
}
