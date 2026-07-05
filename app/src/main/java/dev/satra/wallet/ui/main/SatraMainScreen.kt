package dev.satra.wallet.ui.main

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.satra.wallet.data.assets.SupportedAsset
import dev.satra.wallet.R
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.db.AssetMarketDataRecord
import dev.satra.wallet.data.db.DEFAULT_LOCAL_CURRENCY_CODE
import dev.satra.wallet.data.db.SatraWalletRepository
import dev.satra.wallet.data.db.WalletAssetRecord
import dev.satra.wallet.data.db.WalletRecord
import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionRecord
import dev.satra.wallet.data.db.WalletTransactionStatus
import dev.satra.wallet.data.sync.evm.EvmSyncCompleteness
import dev.satra.wallet.settings.SatraSettings
import dev.satra.wallet.settings.SatraThemePreference
import dev.satra.wallet.ui.setup.WalletSetupFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

@Composable
fun SatraMainScreen(
    walletRepository: SatraWalletRepository,
    settings: SatraSettings,
    appVersion: String,
    setupCompletionFlow: WalletSetupFlow? = null,
    scannedAddress: String = "",
    onScanAddressClick: () -> Unit = {},
    onScannedAddressConsumed: () -> Unit = {},
    onCreateWallet: () -> Unit = {},
    onImportWallet: () -> Unit = {},
    onThemePreferenceChange: (SatraThemePreference) -> Unit,
    onHapticsEnabledChange: (Boolean) -> Unit,
    onLanguageTagChange: (String) -> Unit,
    onResetComplete: () -> Unit,
) {
    val context = LocalContext.current
    val tabNavController = rememberNavController()
    val tabs = remember { SatraMainTab.entries }
    val backStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: SatraMainTab.Home.route
    val currentTab = remember(currentRoute) { selectedMainTabForRoute(currentRoute) }
    var activeSetupCompletionFlow by rememberSaveable(setupCompletionFlow?.routeSegment) {
        mutableStateOf(setupCompletionFlow)
    }
    var balancesHidden by remember {
        mutableStateOf(
            context.getSharedPreferences(BALANCE_CARD_PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(BALANCE_CARD_HIDDEN_KEY, false),
        )
    }
    var activeCurrencyCode by remember { mutableStateOf(DEFAULT_LOCAL_CURRENCY_CODE) }
    var walletManagementRefreshKey by remember { mutableStateOf(0) }
    LaunchedEffect(walletRepository) {
        activeCurrencyCode = walletRepository.getAppSettings().localCurrencyCode
    }
    val onBalancesHiddenChange: (Boolean) -> Unit = { hidden ->
        balancesHidden = hidden
        context.getSharedPreferences(BALANCE_CARD_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(BALANCE_CARD_HIDDEN_KEY, hidden)
            .apply()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            SatraBottomNavigationBar(
                tabs = tabs,
                selectedTab = currentTab,
                onTabSelected = { tab ->
                    tabNavController.navigateMainTab(
                        tab = tab,
                        currentTab = currentTab,
                        currentRoute = currentRoute,
                    )
                },
            )
        },
    ) { paddingValues ->
        NavHost(
            navController = tabNavController,
            startDestination = SatraMainTab.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            composable(SatraMainTab.Home.route) {
                SatraHomeDashboard(
                    walletRepository = walletRepository,
                    localCurrencyCode = activeCurrencyCode,
                    balancesHidden = balancesHidden,
                    hapticsEnabled = settings.hapticsEnabled,
                    onBalancesHiddenChange = onBalancesHiddenChange,
                    onSendClick = { tabNavController.navigate(SatraMainRoute.SendAsset) },
                    onReceiveClick = { tabNavController.navigate(SatraMainRoute.Receive) },
                    onCreateWallet = onCreateWallet,
                    onImportWallet = onImportWallet,
                    onAssetClick = { symbol ->
                        tabNavController.navigate(SatraMainRoute.tokenDetail(symbol))
                    },
                )
            }
            composable(SatraMainTab.Activity.route) {
                SatraActivityScreen(
                    walletRepository = walletRepository,
                    localCurrencyCode = activeCurrencyCode,
                    balancesHidden = balancesHidden,
                    onTransactionClick = { transactionId ->
                        tabNavController.navigate(SatraMainRoute.transactionDetail(transactionId))
                    },
                )
            }
            composable(SatraMainTab.Markets.route) {
                SatraMarketsScreen(
                    walletRepository = walletRepository,
                    localCurrencyCode = activeCurrencyCode,
                    onAssetClick = { symbol ->
                        tabNavController.navigate(SatraMainRoute.marketDetail(symbol))
                    },
                )
            }
            composable(SatraMainTab.Settings.route) {
                SatraSettingsRootScreen(
                    walletRepository = walletRepository,
                    localCurrencyCode = activeCurrencyCode,
                    onNavigate = tabNavController::navigate,
                )
            }
            composable(SatraMainRoute.WalletManagement) {
                SatraWalletManagementScreen(
                    walletRepository = walletRepository,
                    refreshKey = walletManagementRefreshKey,
                    onBack = { tabNavController.popBackStack() },
                    onRemoveWallet = { walletId ->
                        tabNavController.navigate(SatraMainRoute.walletRemoveWarning(walletId))
                    },
                )
            }
            composable(SatraMainRoute.WalletRemoveWarningPattern) { entry ->
                val walletId = entry.arguments?.getString(SatraMainRoute.ArgWalletId).orEmpty()
                SatraWalletRemoveWarningScreen(
                    walletRepository = walletRepository,
                    walletId = walletId,
                    onBack = { tabNavController.popBackStack() },
                    onContinue = { selectedWalletId ->
                        tabNavController.navigate(SatraMainRoute.walletRemovePasscode(selectedWalletId))
                    },
                )
            }
            composable(SatraMainRoute.WalletRemovePasscodePattern) { entry ->
                val walletId = entry.arguments?.getString(SatraMainRoute.ArgWalletId).orEmpty()
                SatraWalletRemovePasscodeScreen(
                    walletRepository = walletRepository,
                    walletId = walletId,
                    onBack = { tabNavController.popBackStack() },
                    onWalletRemoved = {
                        walletManagementRefreshKey += 1
                        tabNavController.popBackStack(SatraMainRoute.WalletManagement, inclusive = false)
                    },
                    onWalletsEmpty = onResetComplete,
                )
            }
            composable(SatraMainRoute.AddressBook) {
                SatraAddressBookScreen(
                    walletRepository = walletRepository,
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(SatraMainRoute.Preferences) {
                SatraPreferencesScreen(
                    walletRepository = walletRepository,
                    settings = settings,
                    localCurrencyCode = activeCurrencyCode,
                    onBack = { tabNavController.popBackStack() },
                    onNavigate = tabNavController::navigate,
                    onHapticsEnabledChange = onHapticsEnabledChange,
                )
            }
            composable(SatraMainRoute.Currency) {
                SatraCurrencyScreen(
                    walletRepository = walletRepository,
                    selectedCurrencyCode = activeCurrencyCode,
                    onCurrencyChanged = { currencyCode ->
                        activeCurrencyCode = currencyCode
                    },
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(SatraMainRoute.Language) {
                SatraLanguageScreen(
                    walletRepository = walletRepository,
                    settings = settings,
                    onBack = { tabNavController.popBackStack() },
                    onLanguageTagChange = onLanguageTagChange,
                )
            }
            composable(SatraMainRoute.Appearance) {
                SatraAppearanceScreen(
                    walletRepository = walletRepository,
                    settings = settings,
                    onBack = { tabNavController.popBackStack() },
                    onThemePreferenceChange = onThemePreferenceChange,
                )
            }
            composable(SatraMainRoute.Security) {
                SatraSecurityScreen(
                    walletRepository = walletRepository,
                    onBack = { tabNavController.popBackStack() },
                    onTurnOffPasscode = { tabNavController.navigate(SatraMainRoute.SecurityTurnOffPasscode) },
                )
            }
            composable(SatraMainRoute.SecurityTurnOffPasscode) {
                SatraSecurityTurnOffPasscodeScreen(
                    walletRepository = walletRepository,
                    onBack = { tabNavController.popBackStack() },
                    onPasscodeDisabled = {
                        tabNavController.popBackStack(SatraMainRoute.Security, inclusive = false)
                    },
                )
            }
            composable(SatraMainRoute.Notifications) {
                SatraNotificationsScreen(
                    walletRepository = walletRepository,
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(SatraMainRoute.About) {
                SatraAboutScreen(
                    appVersion = appVersion,
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(SatraMainRoute.Legal) {
                SatraLegalScreen(onBack = { tabNavController.popBackStack() })
            }
            composable(SatraMainRoute.DangerZone) {
                SatraDangerZoneScreen(
                    walletRepository = walletRepository,
                    onBack = { tabNavController.popBackStack() },
                    onContinueReset = { tabNavController.navigate(SatraMainRoute.DangerZonePasscode) },
                )
            }
            composable(SatraMainRoute.DangerZonePasscode) {
                SatraDangerZonePasscodeScreen(
                    walletRepository = walletRepository,
                    onBack = { tabNavController.popBackStack() },
                    onResetComplete = onResetComplete,
                )
            }
            composable(SatraMainRoute.Receive) {
                SatraReceiveAssetScreen(
                    walletRepository = walletRepository,
                    onBack = { tabNavController.popBackStack() },
                    onAssetSelected = { assetId ->
                        tabNavController.navigate(SatraMainRoute.receiveQr(assetId))
                    },
                    onNetworkRequired = { symbol ->
                        tabNavController.navigate(SatraMainRoute.receiveNetwork(symbol))
                    },
                )
            }
            composable(SatraMainRoute.ReceiveNetworkPattern) { entry ->
                val symbol = entry.arguments?.getString(SatraMainRoute.ArgSymbol).orEmpty()
                SatraReceiveNetworkScreen(
                    walletRepository = walletRepository,
                    symbol = symbol,
                    onBack = { tabNavController.popBackStack() },
                    onNetworkSelected = { assetId ->
                        tabNavController.navigate(SatraMainRoute.receiveQr(assetId))
                    },
                )
            }
            composable(SatraMainRoute.ReceiveQrPattern) { entry ->
                val assetId = entry.arguments?.getString(SatraMainRoute.ArgAssetId).orEmpty()
                SatraReceiveQrScreen(
                    walletRepository = walletRepository,
                    assetId = assetId,
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(SatraMainRoute.TokenDetailPattern) { entry ->
                val symbol = entry.arguments?.getString(SatraMainRoute.ArgSymbol).orEmpty()
                SatraTokenDetailScreen(
                    walletRepository = walletRepository,
                    symbol = symbol,
                    localCurrencyCode = activeCurrencyCode,
                    balancesHidden = balancesHidden,
                    onBalancesHiddenChange = onBalancesHiddenChange,
                    onBack = { tabNavController.popBackStack() },
                    onSendAsset = { assetId ->
                        tabNavController.navigate(SatraMainRoute.sendComposer(assetId))
                    },
                    onSendNetworkRequired = { assetSymbol ->
                        tabNavController.navigate(SatraMainRoute.sendNetwork(assetSymbol))
                    },
                    onReceiveAsset = { assetId ->
                        tabNavController.navigate(SatraMainRoute.receiveQr(assetId))
                    },
                    onReceiveNetworkRequired = { assetSymbol ->
                        tabNavController.navigate(SatraMainRoute.receiveNetwork(assetSymbol))
                    },
                    onTransactionClick = { transactionId ->
                        tabNavController.navigate(SatraMainRoute.transactionDetail(transactionId))
                    },
                )
            }
            composable(SatraMainRoute.TransactionDetailPattern) { entry ->
                val transactionId = entry.arguments?.getString(SatraMainRoute.ArgTransactionId).orEmpty()
                SatraTransactionDetailScreen(
                    walletRepository = walletRepository,
                    transactionId = transactionId,
                    localCurrencyCode = activeCurrencyCode,
                    balancesHidden = balancesHidden,
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(SatraMainRoute.MarketDetailPattern) { entry ->
                val symbol = entry.arguments?.getString(SatraMainRoute.ArgSymbol).orEmpty()
                SatraMarketDetailScreen(
                    walletRepository = walletRepository,
                    symbol = symbol,
                    localCurrencyCode = activeCurrencyCode,
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(SatraMainRoute.SendAsset) {
                SatraSendAssetScreen(
                    walletRepository = walletRepository,
                    onBack = { tabNavController.popBackStack() },
                    onAssetSelected = { assetId ->
                        tabNavController.navigate(SatraMainRoute.sendRecipient(assetId))
                    },
                    onNetworkRequired = { symbol ->
                        tabNavController.navigate(SatraMainRoute.sendNetwork(symbol))
                    },
                )
            }
            composable(SatraMainRoute.SendNetworkPattern) { entry ->
                val symbol = entry.arguments?.getString(SatraMainRoute.ArgSymbol).orEmpty()
                SatraSendNetworkScreen(
                    walletRepository = walletRepository,
                    symbol = symbol,
                    onBack = { tabNavController.popBackStack() },
                    onNetworkSelected = { assetId ->
                        tabNavController.navigate(SatraMainRoute.sendRecipient(assetId))
                    },
                )
            }
            composable(SatraMainRoute.SendRecipientPattern) { entry ->
                val assetId = entry.arguments?.getString(SatraMainRoute.ArgAssetId).orEmpty()
                SatraSendRecipientScreen(
                    walletRepository = walletRepository,
                    assetId = assetId,
                    scannedAddress = scannedAddress,
                    onBack = { tabNavController.popBackStack() },
                    onScanClick = onScanAddressClick,
                    onScannedAddressConsumed = onScannedAddressConsumed,
                    onContinue = { selectedAssetId, recipient, warnPoison ->
                        tabNavController.navigate(
                            SatraMainRoute.sendAmount(
                                assetId = selectedAssetId,
                                recipient = recipient,
                                warnPoison = warnPoison,
                            ),
                        )
                    },
                )
            }
            composable(SatraMainRoute.SendAmountPattern) { entry ->
                val assetId = entry.arguments?.getString(SatraMainRoute.ArgAssetId).orEmpty()
                val recipient = entry.arguments?.getString(SatraMainRoute.ArgRecipient).orEmpty()
                val warnPoison = entry.arguments?.getString(SatraMainRoute.ArgWarnPoison).toBoolean()
                SatraSendAmountScreen(
                    walletRepository = walletRepository,
                    assetId = assetId,
                    recipient = recipient,
                    warnPoison = warnPoison,
                    onBack = { tabNavController.popBackStack() },
                    onReview = { selectedAssetId, selectedRecipient, amount, selectedWarnPoison ->
                        tabNavController.navigate(
                            SatraMainRoute.sendReview(
                                assetId = selectedAssetId,
                                recipient = selectedRecipient,
                                amount = amount,
                                warnPoison = selectedWarnPoison,
                            ),
                        )
                    },
                )
            }
            composable(SatraMainRoute.SendReviewPattern) { entry ->
                val assetId = entry.arguments?.getString(SatraMainRoute.ArgAssetId).orEmpty()
                val recipient = entry.arguments?.getString(SatraMainRoute.ArgRecipient).orEmpty()
                val amount = entry.arguments?.getString(SatraMainRoute.ArgAmount).orEmpty()
                val warnPoison = entry.arguments?.getString(SatraMainRoute.ArgWarnPoison).toBoolean()
                SatraSendReviewScreen(
                    walletRepository = walletRepository,
                    assetId = assetId,
                    recipient = recipient,
                    amount = amount,
                    warnPoison = warnPoison,
                    onBack = { tabNavController.popBackStack() },
                    onSent = { transactionId ->
                        tabNavController.navigate(SatraMainRoute.sendSent(transactionId)) {
                            popUpTo(SatraMainRoute.SendAsset) {
                                inclusive = false
                            }
                        }
                    },
                )
            }
            composable(SatraMainRoute.SendSentPattern) { entry ->
                val transactionId = entry.arguments?.getString(SatraMainRoute.ArgTransactionId).orEmpty()
                SatraSendSentScreen(
                    walletRepository = walletRepository,
                    transactionId = transactionId,
                    onDone = {
                        tabNavController.navigate(SatraMainTab.Home.route) {
                            popUpTo(SatraMainTab.Home.route) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    },
                    onSendAnother = {
                        tabNavController.navigate(SatraMainRoute.SendAsset) {
                            popUpTo(SatraMainRoute.SendAsset) {
                                inclusive = true
                            }
                        }
                    },
                )
            }
            composable(SatraMainRoute.SendComposerPattern) { entry ->
                val assetId = entry.arguments?.getString(SatraMainRoute.ArgAssetId).orEmpty()
                SatraSendRecipientScreen(
                    walletRepository = walletRepository,
                    assetId = assetId,
                    scannedAddress = scannedAddress,
                    onBack = { tabNavController.popBackStack() },
                    onScanClick = onScanAddressClick,
                    onScannedAddressConsumed = onScannedAddressConsumed,
                    onContinue = { selectedAssetId, recipient, warnPoison ->
                        tabNavController.navigate(
                            SatraMainRoute.sendAmount(
                                assetId = selectedAssetId,
                                recipient = recipient,
                                warnPoison = warnPoison,
                            ),
                        )
                    },
                )
            }
        }
    }

    activeSetupCompletionFlow?.let { flow ->
        SetupCompletionBottomSheet(
            flow = flow,
            onDismiss = {
                activeSetupCompletionFlow = null
            },
        )
    }
}

@Composable
private fun SatraBottomNavigationBar(
    tabs: List<SatraMainTab>,
    selectedTab: SatraMainTab,
    onTabSelected: (SatraMainTab) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(23.dp),
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun selectedMainTabForRoute(route: String): SatraMainTab =
    when {
        route == SatraMainTab.Activity.route ||
            route == SatraMainRoute.TransactionDetailPattern ||
            route.startsWith("${SatraMainTab.Activity.route}/") -> SatraMainTab.Activity
        route == SatraMainTab.Markets.route ||
            route == SatraMainRoute.MarketDetailPattern ||
            route.startsWith("${SatraMainTab.Markets.route}/") -> SatraMainTab.Markets
        route == SatraMainTab.Settings.route ||
            route.startsWith("${SatraMainTab.Settings.route}/") -> SatraMainTab.Settings
        else -> SatraMainTab.Home
    }

private fun NavHostController.navigateMainTab(
    tab: SatraMainTab,
    currentTab: SatraMainTab,
    currentRoute: String,
) {
    if (tab == currentTab) {
        if (currentRoute != tab.route) {
            val poppedToRoot = popBackStack(tab.route, inclusive = false)
            if (!poppedToRoot) {
                navigate(tab.route) {
                    launchSingleTop = true
                }
            }
        }
        return
    }

    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupCompletionBottomSheet(
    flow: WalletSetupFlow,
    onDismiss: () -> Unit,
) {
    val titleRes = when (flow) {
        WalletSetupFlow.Create -> R.string.wallet_setup_complete_created_title
        WalletSetupFlow.Import -> R.string.wallet_setup_complete_imported_title
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.wallet_setup_complete_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Text(
                    text = stringResource(R.string.wallet_setup_complete_action),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatraHomeDashboard(
    walletRepository: SatraWalletRepository,
    localCurrencyCode: String,
    balancesHidden: Boolean,
    hapticsEnabled: Boolean,
    onBalancesHiddenChange: (Boolean) -> Unit,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onCreateWallet: () -> Unit,
    onImportWallet: () -> Unit,
    onAssetClick: (String) -> Unit,
) {
    var homeState by remember { mutableStateOf<HomeDashboardState>(HomeDashboardState.Loading) }
    var assetFilterState by remember { mutableStateOf(HomeAssetFilterState()) }
    var assetSearchQuery by remember { mutableStateOf("") }
    var assetFilterSheetVisible by remember { mutableStateOf(false) }
    var walletSwitcherSheetVisible by remember { mutableStateOf(false) }
    var walletSwitcherRows by remember { mutableStateOf<List<WalletSwitcherRow>>(emptyList()) }
    var refreshRequest by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val showScrolledWalletBar by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 220
        }
    }

    LaunchedEffect(walletRepository, localCurrencyCode, refreshRequest) {
        try {
            val wallet = walletRepository.getPrimaryWallet()
            if (wallet == null) {
                homeState = HomeDashboardState.Content(
                    walletId = "",
                    walletName = "",
                    status = HomeSyncStatus.Ready,
                    totalBalance = formatFiat("0", localCurrencyCode),
                    totalBalanceAmount = BigDecimal.ZERO,
                    currencyCode = localCurrencyCode,
                    assets = emptyList(),
                    chartTransactions = emptyList(),
                    chartData = buildHomeBalanceChartData(
                        transactions = emptyList(),
                        range = HomeChartRange.OneWeek,
                        nowMillis = System.currentTimeMillis(),
                    ),
                )
                return@LaunchedEffect
            }

            suspend fun loadContent(status: HomeSyncStatus) {
                val (latestWallet, walletAssets, walletTransactions) = coroutineScope {
                    val walletDeferred = async { walletRepository.getPrimaryWallet() }
                    val assetsDeferred = async { walletRepository.getWalletAssets(wallet.walletId) }
                    val transactionsDeferred = async { walletRepository.getWalletTransactions(wallet.walletId) }
                    Triple(
                        walletDeferred.await() ?: wallet,
                        assetsDeferred.await(),
                        transactionsDeferred.await(),
                    )
                }
                homeState = latestWallet.copy(localCurrencyCode = localCurrencyCode).toHomeDashboardState(
                    walletAssets = walletAssets,
                    walletTransactions = walletTransactions,
                    status = status,
                    chartRange = HomeChartRange.OneWeek,
                    nowMillis = System.currentTimeMillis(),
                )
            }

            loadContent(HomeSyncStatus.Syncing)
            runCatching {
                walletRepository.syncWalletData(
                    walletId = wallet.walletId,
                    onProgress = {
                        withContext(Dispatchers.Main) {
                            loadContent(HomeSyncStatus.Syncing)
                        }
                    },
                )
            }
            loadContent(HomeSyncStatus.Ready)
        } finally {
            isRefreshing = false
        }
    }

    val content = when (val state = homeState) {
        HomeDashboardState.Loading -> HomeDashboardState.Content(
            walletId = "",
            walletName = "",
            status = HomeSyncStatus.Syncing,
            totalBalance = formatFiat("0", localCurrencyCode),
            totalBalanceAmount = BigDecimal.ZERO,
            currencyCode = localCurrencyCode,
            assets = emptyList(),
            chartTransactions = emptyList(),
            chartData = buildHomeBalanceChartData(
                transactions = emptyList(),
                range = HomeChartRange.OneWeek,
                nowMillis = System.currentTimeMillis(),
            ),
        )
        is HomeDashboardState.Content -> state
    }
    val searchedAssets = remember(content.assets, assetSearchQuery) {
        content.assets.applyHomeAssetSearch(assetSearchQuery)
    }
    val visibleAssets = remember(searchedAssets, assetFilterState) {
        searchedAssets.applyHomeAssetFilter(assetFilterState)
    }
    val assetNetworks = remember(content.assets) {
        content.assets
            .flatMap { asset -> asset.networks }
            .distinctBy { (networkId, _) -> networkId }
            .sortedBy { (_, networkName) -> networkName.lowercase() }
    }

    LaunchedEffect(walletSwitcherSheetVisible, content.walletId, refreshRequest) {
        if (walletSwitcherSheetVisible) {
            walletSwitcherRows = walletRepository.loadWalletSwitcherRows()
        }
    }

    if (assetFilterSheetVisible) {
        HomeAssetFilterSheet(
            networks = assetNetworks,
            filterState = assetFilterState,
            onFilterStateChange = { nextState -> assetFilterState = nextState },
            onDismiss = { assetFilterSheetVisible = false },
        )
    }

    if (walletSwitcherSheetVisible) {
        WalletSwitcherSheet(
            wallets = walletSwitcherRows,
            activeWalletId = content.walletId,
            balancesHidden = balancesHidden,
            onDismiss = { walletSwitcherSheetVisible = false },
            onWalletSelected = { walletId ->
                if (walletId == content.walletId) {
                    walletSwitcherSheetVisible = false
                } else {
                    if (hapticsEnabled) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    coroutineScope.launch {
                        walletRepository.setActiveWallet(walletId)
                        walletSwitcherSheetVisible = false
                        homeState = HomeDashboardState.Loading
                        refreshRequest += 1
                    }
                }
            },
            onCreateWallet = {
                walletSwitcherSheetVisible = false
                onCreateWallet()
            },
            onImportWallet = {
                walletSwitcherSheetVisible = false
                onImportWallet()
            },
        )
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing) {
                isRefreshing = true
                refreshRequest += 1
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = HomeContentMaxWidth)
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        HomeBalanceCard(
                            walletName = content.walletName.ifBlank {
                                stringResource(R.string.home_wallet_label)
                            },
                            status = content.status,
                            totalBalance = content.totalBalance,
                            currencyCode = content.currencyCode,
                            transactions = content.chartTransactions,
                            initialChartData = content.chartData,
                            isEmpty = content.totalBalanceAmount <= BigDecimal.ZERO,
                            balancesHidden = balancesHidden,
                            onWalletClick = { walletSwitcherSheetVisible = true },
                            onBalancesHiddenChange = onBalancesHiddenChange,
                            onSendClick = onSendClick,
                            onReceiveClick = onReceiveClick,
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        HomeAssetsHeader(
                            assetCount = visibleAssets.size,
                            totalAssetCount = content.assets.size,
                            isFiltered = assetFilterState.isActive || assetSearchQuery.isNotBlank(),
                            onFilterClick = { assetFilterSheetVisible = true },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HomeAssetSearchField(
                            query = assetSearchQuery,
                            onQueryChange = { query -> assetSearchQuery = query },
                        )
                    }
                }
                if (visibleAssets.isEmpty()) {
                    item {
                        SatraEmptyState(
                            title = stringResource(
                                if (assetFilterState.onlyWithBalance) {
                                    R.string.home_assets_empty_balances_title
                                } else {
                                    R.string.home_assets_empty_title
                                },
                            ),
                            body = stringResource(
                                if (assetFilterState.onlyWithBalance) {
                                    R.string.home_assets_empty_balances_body
                                } else {
                                    R.string.home_assets_empty_body
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = HomeContentMaxWidth)
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                        )
                    }
                } else {
                    items(
                        items = visibleAssets,
                        key = { asset -> asset.assetId },
                    ) { asset ->
                        HomeAssetListRow(
                            asset = asset,
                            balancesHidden = balancesHidden,
                            onClick = { onAssetClick(asset.symbol) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = HomeContentMaxWidth)
                                .padding(horizontal = 20.dp),
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            if (showScrolledWalletBar && content.walletId.isNotBlank()) {
                HomeScrolledWalletBar(
                    walletName = content.walletName.ifBlank {
                        stringResource(R.string.home_wallet_label)
                    },
                    totalBalance = content.totalBalance,
                    balancesHidden = balancesHidden,
                    onWalletClick = { walletSwitcherSheetVisible = true },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatraActivityScreen(
    walletRepository: SatraWalletRepository,
    localCurrencyCode: String,
    balancesHidden: Boolean,
    onTransactionClick: (String) -> Unit,
) {
    val resources = LocalContext.current.resources
    var activityState by remember {
        mutableStateOf<ActivityScreenState>(ActivityScreenState.Loading)
    }
    var refreshRequest by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var activitySearchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(walletRepository, localCurrencyCode, refreshRequest) {
        try {
            val wallet = walletRepository.getPrimaryWallet()
            if (wallet == null) {
                activityState = ActivityScreenState.Content(
                    walletName = "",
                    status = HomeSyncStatus.Ready,
                    transactions = emptyList(),
                    syncedNetworkCount = 0,
                    error = null,
                )
                return@LaunchedEffect
            }

            suspend fun loadContent(status: HomeSyncStatus, error: String? = null) {
                val (latestWallet, walletAssets, walletTransactions) = coroutineScope {
                    val walletDeferred = async { walletRepository.getPrimaryWallet() }
                    val assetsDeferred = async { walletRepository.getWalletAssets(wallet.walletId) }
                    val transactionsDeferred = async { walletRepository.getWalletTransactions(wallet.walletId) }
                    Triple(
                        walletDeferred.await() ?: wallet,
                        assetsDeferred.await(),
                        transactionsDeferred.await(),
                    )
                }
                val transactions = walletTransactions.toActivityRows(
                    localCurrencyCode = localCurrencyCode,
                    walletAssets = walletAssets,
                    resources = resources,
                )
                activityState = ActivityScreenState.Content(
                    walletName = latestWallet.walletName,
                    status = status,
                    transactions = transactions,
                    syncedNetworkCount = latestWallet.syncedNetworkCount(),
                    error = error,
                )
            }

            loadContent(HomeSyncStatus.Syncing)
            val syncError = runCatching {
                val result = walletRepository.syncWalletHistoryData(
                    walletId = wallet.walletId,
                    onProgress = {
                        withContext(Dispatchers.Main) {
                            loadContent(HomeSyncStatus.Syncing)
                        }
                    },
                )
                val evmPartial = result.evmSyncResult.networkResults.any { network ->
                    network.error != null ||
                        network.balanceCompleteness != EvmSyncCompleteness.Complete ||
                        network.historyCompleteness != EvmSyncCompleteness.Complete
                }
                val utxoPartial = result.utxoSyncResult.networkResults.any { network ->
                    network.error != null ||
                        network.balanceCompleteness != EvmSyncCompleteness.Complete ||
                        network.historyCompleteness != EvmSyncCompleteness.Complete
                }
                val solanaPartial = result.solanaSyncResult.networkResults.any { network ->
                    network.error != null ||
                        network.balanceCompleteness != EvmSyncCompleteness.Complete ||
                        network.historyCompleteness != EvmSyncCompleteness.Complete
                }
                val accountChainPartial = result.accountChainSyncResult.networkResults.any { network ->
                    network.error != null ||
                        network.balanceCompleteness != EvmSyncCompleteness.Complete ||
                        network.historyCompleteness != EvmSyncCompleteness.Complete
                }
                if (evmPartial || utxoPartial || solanaPartial || accountChainPartial) {
                    resources.getString(R.string.activity_partial_sync_error)
                } else {
                    null
                }
            }.getOrElse { error ->
                error.message
            }
            loadContent(HomeSyncStatus.Ready, syncError)
        } finally {
            isRefreshing = false
        }
    }

    val content = when (val state = activityState) {
        ActivityScreenState.Loading -> ActivityScreenState.Content(
            walletName = "",
            status = HomeSyncStatus.Syncing,
            transactions = emptyList(),
            syncedNetworkCount = 0,
            error = null,
        )

        is ActivityScreenState.Content -> state
    }
    val visibleTransactions = remember(content.transactions, activitySearchQuery) {
        content.transactions.applyActivitySearch(activitySearchQuery)
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing) {
                isRefreshing = true
                refreshRequest += 1
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = HomeContentMaxWidth)
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    SatraActivityHeader(
                        walletName = content.walletName.ifBlank {
                            stringResource(R.string.home_wallet_label)
                        },
                        status = content.status,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    ActivitySummaryCard(
                        transactionCount = content.transactions.size,
                        syncedNetworkCount = content.syncedNetworkCount,
                        error = content.error,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    ActivitySearchField(
                        query = activitySearchQuery,
                        onQueryChange = { query -> activitySearchQuery = query },
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    ActivityTransactionsHeader(transactionCount = visibleTransactions.size)
                }
            }
            if (content.transactions.isEmpty()) {
                item {
                    ActivityEmptyState(
                        isSyncing = content.status == HomeSyncStatus.Syncing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = HomeContentMaxWidth)
                            .padding(horizontal = 20.dp),
                    )
                }
            } else if (visibleTransactions.isEmpty()) {
                item {
                    SatraEmptyState(
                        title = stringResource(R.string.activity_search_empty_title),
                        body = stringResource(R.string.activity_search_empty_body),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = HomeContentMaxWidth)
                            .padding(horizontal = 20.dp),
                    )
                }
            } else {
                items(
                    items = visibleTransactions,
                    key = { transaction -> transaction.transactionId },
                ) { transaction ->
                    ActivityTransactionCard(
                        transaction = transaction,
                        balancesHidden = balancesHidden,
                        onClick = { onTransactionClick(transaction.transactionId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = HomeContentMaxWidth)
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }
            item {
                Spacer(
                    modifier = Modifier
                        .height(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SatraTransactionDetailScreen(
    walletRepository: SatraWalletRepository,
    transactionId: String,
    localCurrencyCode: String,
    balancesHidden: Boolean,
    onBack: () -> Unit,
) {
    val resources = LocalContext.current.resources
    var state by remember(transactionId) {
        mutableStateOf<TransactionDetailState>(TransactionDetailState.Loading)
    }

    LaunchedEffect(walletRepository, transactionId, localCurrencyCode) {
        val wallet = walletRepository.getPrimaryWallet()
        if (wallet == null || transactionId.isBlank()) {
            state = TransactionDetailState.NotFound
            return@LaunchedEffect
        }
        val (walletAssets, transaction) = coroutineScope {
            val assetsDeferred = async { walletRepository.getWalletAssets(wallet.walletId) }
            val transactionDeferred = async {
                walletRepository.getWalletTransactions(wallet.walletId)
                    .firstOrNull { record -> record.transactionId == transactionId }
            }
            assetsDeferred.await() to transactionDeferred.await()
        }
        state = transaction
            ?.toTransactionDetailContent(
                localCurrencyCode = localCurrencyCode,
                walletAssets = walletAssets,
                resources = resources,
            )
            ?: TransactionDetailState.NotFound
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = HomeContentMaxWidth)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                TransactionDetailHeader(onBack = onBack)
                Spacer(modifier = Modifier.height(22.dp))
                when (val current = state) {
                    TransactionDetailState.Loading -> TransactionDetailLoadingCard()
                    TransactionDetailState.NotFound -> TransactionDetailNotFoundCard()
                    is TransactionDetailState.Content -> {
                        TransactionDetailSummaryCard(
                            content = current,
                            balancesHidden = balancesHidden,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        TransactionDetailRowsCard(
                            rows = current.details,
                            balancesHidden = balancesHidden,
                        )
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TransactionDetailHeader(
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.activity_detail_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TransactionDetailLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.activity_detail_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransactionDetailNotFoundCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.activity_detail_not_found_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.activity_detail_not_found_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransactionDetailSummaryCard(
    content: TransactionDetailState.Content,
    balancesHidden: Boolean,
) {
    val hiddenValue = stringResource(R.string.home_balance_hidden_value)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SatraBadgedIcon(
                primaryIconRes = content.iconRes,
                badgeIconRes = content.networkIconRes,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = content.subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (balancesHidden) hiddenValue else content.amount,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (balancesHidden) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        when (content.direction) {
                            WalletTransactionDirection.Incoming.value -> MaterialTheme.colorScheme.tertiary
                            WalletTransactionDirection.Outgoing.value -> MaterialTheme.colorScheme.onSurface
                            WalletTransactionDirection.Self.value -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = if (balancesHidden) hiddenValue else content.fiatValue,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TransactionDetailRowsCard(
    rows: List<TransactionDetailRow>,
    balancesHidden: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            rows.forEachIndexed { index, row ->
                TransactionDetailRowItem(
                    row = row,
                    balancesHidden = balancesHidden,
                )
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailRowItem(
    row: TransactionDetailRow,
    balancesHidden: Boolean,
) {
    val hiddenValue = stringResource(R.string.home_balance_hidden_value)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (balancesHidden && row.sensitive) hiddenValue else row.value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatraMarketsScreen(
    walletRepository: SatraWalletRepository,
    localCurrencyCode: String,
    onAssetClick: (String) -> Unit,
) {
    var state by remember {
        mutableStateOf<MarketsScreenState>(MarketsScreenState.Loading)
    }
    var marketSearchQuery by remember { mutableStateOf("") }
    var refreshRequest by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(walletRepository, localCurrencyCode, refreshRequest) {
        try {
            suspend fun loadMarketData() {
                val marketData = walletRepository.getAllAssetMarketData()
                state = MarketsScreenState.Content(
                    currencyCode = localCurrencyCode,
                    rows = SupportedAssetCatalog.assets.toMarketRows(
                        marketData = marketData,
                        localCurrencyCode = localCurrencyCode,
                    ),
                )
            }

            loadMarketData()
            walletRepository.syncMarketData(
                localCurrencyCode = localCurrencyCode,
                onProgress = {
                    withContext(Dispatchers.Main) {
                        loadMarketData()
                    }
                },
            )
            loadMarketData()
        } finally {
            isRefreshing = false
        }
    }

    val content = when (val current = state) {
        MarketsScreenState.Loading -> MarketsScreenState.Content(
            currencyCode = localCurrencyCode,
            rows = emptyList(),
        )
        is MarketsScreenState.Content -> current
    }
    val visibleRows = remember(content.rows, marketSearchQuery) {
        content.rows.applyMarketSearch(marketSearchQuery)
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing) {
                isRefreshing = true
                refreshRequest += 1
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = HomeContentMaxWidth)
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    MarketsHeader(currencyCode = content.currencyCode)
                    Spacer(modifier = Modifier.height(18.dp))
                    MarketsSearchField(
                        query = marketSearchQuery,
                        onQueryChange = { query -> marketSearchQuery = query },
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    MarketsListHeader(assetCount = visibleRows.size)
                }
            }
            if (visibleRows.isEmpty()) {
                item {
                    SatraEmptyState(
                        title = stringResource(R.string.markets_empty_title),
                        body = stringResource(R.string.markets_empty_body),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = HomeContentMaxWidth)
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                    )
                }
            } else {
                items(
                    items = visibleRows,
                    key = { row -> row.symbol },
                ) { row ->
                    MarketsAssetRow(
                        row = row,
                        onClick = { onAssetClick(row.symbol) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = HomeContentMaxWidth)
                            .padding(horizontal = 20.dp),
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun MarketsHeader(
    currencyCode: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.markets_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.markets_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currencyCode,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MarketsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        ),
        placeholder = {
            Text(
                text = stringResource(R.string.markets_search_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = if (query.isNotBlank()) {
            {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.markets_search_clear),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(100.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun MarketsListHeader(
    assetCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.markets_supported_assets_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.markets_assets_count, assetCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MarketsAssetRow(
    row: MarketAssetRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(row.iconRes),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${row.symbol} · ${
                    if (row.networkCount > 1) {
                        stringResource(R.string.home_assets_network_count, row.networkCount)
                    } else {
                        row.networkName
                    }
                }",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = row.price,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = row.change24hPercent ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = row.change24hPercent?.let { change ->
                    if (change.startsWith("-")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                } ?: Color.Transparent,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatraMarketDetailScreen(
    walletRepository: SatraWalletRepository,
    symbol: String,
    localCurrencyCode: String,
    onBack: () -> Unit,
) {
    val resources = LocalContext.current.resources
    var state by remember(symbol) {
        mutableStateOf<MarketDetailState>(MarketDetailState.Loading)
    }
    val normalizedSymbol = remember(symbol) { Uri.decode(symbol).uppercase(Locale.US) }
    var refreshRequest by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(walletRepository, normalizedSymbol, localCurrencyCode, refreshRequest) {
        try {
            walletRepository.getAssetMarketData(normalizedSymbol)?.let { cached ->
                state = cached.toMarketDetailState(
                    localCurrencyCode = localCurrencyCode,
                    resources = resources,
                )
            }
            val refreshed = walletRepository.syncAssetMarketDetail(
                symbol = normalizedSymbol,
                localCurrencyCode = localCurrencyCode,
            )
            state = (refreshed ?: walletRepository.getAssetMarketData(normalizedSymbol))
                ?.toMarketDetailState(
                    localCurrencyCode = localCurrencyCode,
                    resources = resources,
                )
                ?: MarketDetailState.NotFound(normalizedSymbol)
        } finally {
            isRefreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing) {
                isRefreshing = true
                refreshRequest += 1
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = HomeContentMaxWidth)
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    MarketDetailHeader(onBack = onBack)
                    Spacer(modifier = Modifier.height(18.dp))
                    when (val current = state) {
                        MarketDetailState.Loading -> MarketDetailLoadingCard()
                        is MarketDetailState.NotFound -> MarketDetailNotFoundCard(symbol = current.symbol)
                        is MarketDetailState.Content -> {
                            MarketDetailHeroCard(content = current)
                            Spacer(modifier = Modifier.height(14.dp))
                            MarketDetailChartCard(content = current)
                            Spacer(modifier = Modifier.height(14.dp))
                            MarketDetailStatsCard(content = current)
                            Spacer(modifier = Modifier.height(14.dp))
                            MarketDetailDescriptionCard(content = current)
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun MarketDetailHeader(
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back_content_description),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.market_detail_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MarketDetailLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.market_detail_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MarketDetailNotFoundCard(
    symbol: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.market_detail_not_found_title, symbol),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.market_detail_not_found_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MarketDetailHeroCard(
    content: MarketDetailState.Content,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(content.iconRes),
                contentDescription = null,
                modifier = Modifier.size(58.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = content.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = content.symbol,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = content.change24hPercent,
                style = MaterialTheme.typography.labelLarge,
                color = if (content.change24hAmount.signum() < 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.market_detail_price_label, content.currencyCode),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = content.price,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun MarketDetailChartCard(
    content: MarketDetailState.Content,
) {
    var selectedPointIndex by remember(content.chartData.points) {
        mutableStateOf((content.chartData.points.size - 1).coerceAtLeast(0))
    }
    val selectedPoint = content.chartData.points.getOrNull(selectedPointIndex)
        ?: content.chartData.points.lastOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.market_detail_chart_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = selectedPoint?.value?.toPlainString()?.let { formatFiat(it, content.currencyCode) }
                            ?: content.price,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = selectedPoint?.let { formatChartPointTime(it.timestampMillis, HomeChartRange.OneWeek) }
                        ?: stringResource(R.string.home_chart_range_1w),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HomeBalanceChart(
                chartData = content.chartData,
                hidden = false,
                empty = !content.chartData.hasDrawablePoints,
                selectedPointIndex = selectedPointIndex,
                onSelectedPointChange = { selectedPointIndex = it },
                lineColor = MaterialTheme.colorScheme.onSurface,
                fillColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                baselineColor = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.35f),
            )
        }
    }
}

@Composable
private fun MarketDetailStatsCard(
    content: MarketDetailState.Content,
) {
    val rows = listOf(
        R.string.market_detail_market_cap to content.marketCap,
        R.string.market_detail_volume_24h to content.volume24h,
        R.string.market_detail_high_24h to content.high24h,
        R.string.market_detail_low_24h to content.low24h,
        R.string.market_detail_provider to content.provider,
        R.string.market_detail_updated to content.updatedAt,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(row.first),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = row.second,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketDetailDescriptionCard(
    content: MarketDetailState.Content,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.market_detail_about_asset, content.symbol),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = content.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content.homepageUrl?.let { url ->
                Text(
                    text = url,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatraTokenDetailScreen(
    walletRepository: SatraWalletRepository,
    symbol: String,
    localCurrencyCode: String,
    balancesHidden: Boolean,
    onBalancesHiddenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSendAsset: (String) -> Unit,
    onSendNetworkRequired: (String) -> Unit,
    onReceiveAsset: (String) -> Unit,
    onReceiveNetworkRequired: (String) -> Unit,
    onTransactionClick: (String) -> Unit,
) {
    val resources = LocalContext.current.resources
    var state by remember(symbol) {
        mutableStateOf<TokenDetailState>(TokenDetailState.Loading)
    }
    val normalizedSymbol = remember(symbol) { Uri.decode(symbol).uppercase(Locale.US) }
    var refreshRequest by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(walletRepository, normalizedSymbol, localCurrencyCode, refreshRequest) {
        try {
            val wallet = walletRepository.getPrimaryWallet()
            if (wallet == null) {
                state = TokenDetailState.Content(
                    symbol = normalizedSymbol,
                    name = normalizedSymbol,
                    iconRes = assetIconRes(normalizedSymbol),
                    currencyCode = localCurrencyCode,
                    totalBalance = formatFiat("0", localCurrencyCode),
                    networkBalances = emptyList(),
                    transactions = emptyList(),
                    chartTransactions = emptyList(),
                    chartData = buildHomeBalanceChartData(
                        transactions = emptyList(),
                        range = HomeChartRange.OneWeek,
                        nowMillis = System.currentTimeMillis(),
                    ),
                    sendAssetId = null,
                    sendRequiresNetwork = false,
                )
                return@LaunchedEffect
            }

            suspend fun loadContent() {
                val (walletAssets, walletTransactions) = coroutineScope {
                    val assetsDeferred = async { walletRepository.getWalletAssets(wallet.walletId) }
                    val transactionsDeferred = async { walletRepository.getWalletTransactions(wallet.walletId) }
                    assetsDeferred.await() to transactionsDeferred.await()
                }
                state = wallet.copy(localCurrencyCode = localCurrencyCode).toTokenDetailState(
                    symbol = normalizedSymbol,
                    walletAssets = walletAssets,
                    walletTransactions = walletTransactions,
                    resources = resources,
                    nowMillis = System.currentTimeMillis(),
                )
            }

            loadContent()
            if (refreshRequest > 0) {
                runCatching {
                    walletRepository.syncAssetNetworks(
                        walletId = wallet.walletId,
                        symbol = normalizedSymbol,
                        onProgress = {
                            withContext(Dispatchers.Main) {
                                loadContent()
                            }
                        },
                    )
                }
                loadContent()
            }
        } finally {
            isRefreshing = false
        }
    }

    val content = when (val current = state) {
        TokenDetailState.Loading -> TokenDetailState.Content(
            symbol = normalizedSymbol,
            name = normalizedSymbol,
            iconRes = assetIconRes(normalizedSymbol),
            currencyCode = localCurrencyCode,
            totalBalance = formatFiat("0", localCurrencyCode),
            networkBalances = emptyList(),
            transactions = emptyList(),
            chartTransactions = emptyList(),
            chartData = buildHomeBalanceChartData(
                transactions = emptyList(),
                range = HomeChartRange.OneWeek,
                nowMillis = System.currentTimeMillis(),
            ),
            sendAssetId = null,
            sendRequiresNetwork = false,
        )
        is TokenDetailState.Content -> current
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing) {
                isRefreshing = true
                refreshRequest += 1
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = HomeContentMaxWidth)
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    TokenDetailHeader(
                        title = content.name,
                        symbol = content.symbol,
                        iconRes = content.iconRes,
                        onBack = onBack,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    HomeBalanceCard(
                        totalBalance = content.totalBalance,
                        currencyCode = content.currencyCode,
                        transactions = content.chartTransactions,
                        initialChartData = content.chartData,
                        isEmpty = content.networkBalances.none { row -> row.hasBalance },
                        balancesHidden = balancesHidden,
                        onBalancesHiddenChange = onBalancesHiddenChange,
                        onSendClick = {
                            if (content.sendRequiresNetwork) {
                                onSendNetworkRequired(content.symbol)
                            } else {
                                content.sendAssetId?.let(onSendAsset)
                            }
                        },
                        onReceiveClick = {
                            val receiveRows = content.networkBalances
                            if (receiveRows.size > 1) {
                                onReceiveNetworkRequired(content.symbol)
                            } else {
                                receiveRows.singleOrNull()?.assetId?.let(onReceiveAsset)
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    TokenDetailSectionHeader(
                        title = stringResource(R.string.asset_detail_balances_title),
                        value = stringResource(R.string.home_assets_network_count, content.networkBalances.size),
                    )
                }
            }
            if (content.networkBalances.isEmpty()) {
                item {
                    SatraEmptyState(
                        title = stringResource(R.string.asset_detail_networks_empty_title),
                        body = stringResource(R.string.asset_detail_networks_empty_body),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = HomeContentMaxWidth)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(
                    items = content.networkBalances,
                    key = { row -> row.assetId },
                ) { row ->
                    TokenNetworkBalanceListRow(
                        row = row,
                        balancesHidden = balancesHidden,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = HomeContentMaxWidth)
                            .padding(horizontal = 20.dp),
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = HomeContentMaxWidth)
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(modifier = Modifier.height(22.dp))
                    TokenDetailSectionHeader(
                        title = stringResource(R.string.asset_detail_activity_title),
                        value = stringResource(R.string.activity_transactions_count, content.transactions.size),
                    )
                }
            }
            if (content.transactions.isEmpty()) {
                item {
                    TokenDetailEmptyActivity(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = HomeContentMaxWidth)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(
                    items = content.transactions,
                    key = { transaction -> transaction.transactionId },
                ) { transaction ->
                    ActivityTransactionCard(
                        transaction = transaction,
                        balancesHidden = balancesHidden,
                        onClick = { onTransactionClick(transaction.transactionId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = HomeContentMaxWidth)
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun TokenDetailHeader(
    title: String,
    symbol: String,
    @DrawableRes iconRes: Int,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = symbol,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TokenDetailSectionHeader(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TokenNetworkBalanceListRow(
    row: TokenNetworkBalanceRow,
    balancesHidden: Boolean,
    modifier: Modifier = Modifier,
) {
    val hiddenValue = stringResource(R.string.home_balance_hidden_value)
    Row(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SatraBadgedIcon(
            primaryIconRes = row.networkIconRes,
            badgeIconRes = row.assetIconRes,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = row.network,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (balancesHidden) hiddenValue else row.fiatValue,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = if (balancesHidden) hiddenValue else row.amount,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TokenDetailEmptyActivity(
    modifier: Modifier = Modifier,
) {
    SatraEmptyState(
        title = stringResource(R.string.asset_detail_activity_empty_title),
        body = stringResource(R.string.asset_detail_activity_empty_body),
        modifier = modifier,
    )
}

@Composable
private fun SatraActivityHeader(
    walletName: String,
    status: HomeSyncStatus,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.activity_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = walletName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.activity_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status == HomeSyncStatus.Syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(status.labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ActivitySummaryCard(
    transactionCount: Int,
    syncedNetworkCount: Int,
    error: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ActivitySummaryRow(
                label = stringResource(R.string.activity_summary_networks),
                value = stringResource(
                    R.string.activity_summary_networks_value,
                    syncedNetworkCount,
                    SupportedAssetCatalog.networks.size,
                ),
            )
            ActivitySummaryRow(
                label = stringResource(R.string.activity_summary_transactions),
                value = stringResource(R.string.activity_summary_transactions_value, transactionCount),
            )
            if (!error.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.activity_summary_partial),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivitySummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun ActivityTransactionsHeader(transactionCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.activity_transactions_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.activity_transactions_count, transactionCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ActivitySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        ),
        placeholder = {
            Text(
                text = stringResource(R.string.activity_search_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = if (query.isNotBlank()) {
            {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.activity_search_clear),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(100.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun ActivityEmptyState(
    isSyncing: Boolean,
    modifier: Modifier = Modifier,
) {
    SatraEmptyState(
        title = stringResource(
            if (isSyncing) {
                R.string.activity_syncing_title
            } else {
                R.string.activity_empty_title
            },
        ),
        body = stringResource(
            if (isSyncing) {
                R.string.activity_syncing_body
            } else {
                R.string.activity_empty_body
            },
        ),
        modifier = modifier,
        showProgress = isSyncing,
    )
}

@Composable
private fun ActivityTransactionCard(
    transaction: ActivityTransactionRow,
    balancesHidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hiddenValue = stringResource(R.string.home_balance_hidden_value)
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SatraBadgedIcon(
                    primaryIconRes = transaction.iconRes,
                    badgeIconRes = transaction.networkIconRes,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = transaction.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = transaction.subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (balancesHidden) hiddenValue else transaction.amount,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (balancesHidden) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            when (transaction.direction) {
                                WalletTransactionDirection.Incoming.value -> MaterialTheme.colorScheme.tertiary
                                WalletTransactionDirection.Outgoing.value -> MaterialTheme.colorScheme.onSurface
                                WalletTransactionDirection.Self.value -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = if (balancesHidden) hiddenValue else transaction.fiatValue,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeBalanceCard(
    walletName: String? = null,
    status: HomeSyncStatus? = null,
    totalBalance: String,
    currencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
    transactions: List<WalletTransactionRecord>,
    initialChartData: HomeBalanceChartData,
    isEmpty: Boolean,
    balancesHidden: Boolean,
    onWalletClick: () -> Unit = {},
    onBalancesHiddenChange: (Boolean) -> Unit,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
) {
    var selectedRange by remember { mutableStateOf(initialChartData.range) }
    val chartData = remember(transactions, selectedRange) {
        buildHomeBalanceChartData(
            transactions = transactions,
            range = selectedRange,
            nowMillis = System.currentTimeMillis(),
        )
    }
    var selectedPointIndex by remember(chartData.points, balancesHidden) {
        mutableStateOf((chartData.points.size - 1).coerceAtLeast(0))
    }
    val chartCanDraw = chartData.hasDrawablePoints && chartData.hasActivity
    val cardColor = MaterialTheme.colorScheme.inverseSurface
    val contentColor = MaterialTheme.colorScheme.inverseOnSurface
    val mutedContentColor = contentColor.copy(alpha = 0.64f)
    val cardIsDark = cardColor.luminance() < 0.5f
    val gainContentColor = if (cardIsDark) Color(0xFF7FC9A6) else Color(0xFF2E7D5A)
    val lossContentColor = if (cardIsDark) Color(0xFFE08A76) else Color(0xFFB3452E)
    val deltaIsPositive = chartData.changeValue.signum() >= 0
    val deltaContainerColor = when {
        balancesHidden -> Color.Gray.copy(alpha = 0.18f)
        deltaIsPositive -> gainContentColor.copy(alpha = if (cardIsDark) 0.16f else 0.12f)
        else -> lossContentColor.copy(alpha = if (cardIsDark) 0.16f else 0.12f)
    }
    val deltaContentColor = when {
        balancesHidden -> mutedContentColor
        deltaIsPositive -> gainContentColor
        else -> lossContentColor
    }
    val buttonBackground = contentColor
    val buttonContent = cardColor
    val displayBalance = if (balancesHidden) {
        stringResource(R.string.home_balance_hidden_value)
    } else {
        totalBalance
    }
    val displayDelta = if (balancesHidden) {
        stringResource(R.string.home_balance_hidden_delta)
    } else {
        formatPercent(chartData.percentChange)
    }
    val rangeInteractionsEnabled = !balancesHidden && chartData.hasActivity

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 20.dp),
        ) {
            if (!walletName.isNullOrBlank() || status != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeWalletPill(
                        walletName = walletName.orEmpty(),
                        onClick = onWalletClick,
                        modifier = Modifier.weight(1f, fill = false),
                        contentColor = contentColor,
                        containerColor = contentColor.copy(alpha = 0.1f),
                    )
                    status?.let { syncStatus ->
                        HomeBalanceStatusPill(
                            status = syncStatus,
                            contentColor = contentColor,
                            mutedContentColor = mutedContentColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_balance_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = mutedContentColor,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(
                    onClick = {
                        onBalancesHiddenChange(!balancesHidden)
                    },
                    modifier = Modifier.size(44.dp),
                ) {
                    HomeBalanceEyeIcon(
                        hidden = balancesHidden,
                        color = mutedContentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayBalance,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = displayDelta,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(deltaContainerColor)
                        .alpha(if (isEmpty) 0f else 1f)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = deltaContentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            HomeBalanceChart(
                chartData = chartData,
                hidden = balancesHidden,
                empty = !chartCanDraw,
                selectedPointIndex = selectedPointIndex,
                onSelectedPointChange = { selectedPointIndex = it },
                lineColor = contentColor,
                fillColor = contentColor.copy(alpha = 0.09f),
                baselineColor = contentColor.copy(alpha = 0.25f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            HomeChartRangeSelector(
                selectedRange = selectedRange,
                onRangeSelected = { range -> selectedRange = range },
                enabled = rangeInteractionsEnabled,
                selectedContainerColor = buttonBackground,
                selectedContentColor = buttonContent,
                unselectedContentColor = mutedContentColor,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomeBalanceActionButton(
                    label = stringResource(R.string.home_action_move),
                    iconRes = R.drawable.ic_brand_move,
                    onClick = onSendClick,
                    solid = !isEmpty,
                    enabled = true,
                    solidContainerColor = buttonBackground,
                    solidContentColor = buttonContent,
                    outlinedContentColor = contentColor,
                    modifier = Modifier.weight(1f),
                )
                HomeBalanceActionButton(
                    label = stringResource(R.string.home_action_receive),
                    iconRes = R.drawable.ic_brand_receive,
                    onClick = onReceiveClick,
                    solid = isEmpty,
                    enabled = true,
                    solidContainerColor = buttonBackground,
                    solidContentColor = buttonContent,
                    outlinedContentColor = contentColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HomeWalletPill(
    walletName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = walletName,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(R.string.home_wallet_menu_content_description),
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun HomeScrolledWalletBar(
    walletName: String,
    totalBalance: String,
    balancesHidden: Boolean,
    onWalletClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(surfaceColor),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = HomeContentMaxWidth)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeWalletPill(
                walletName = walletName,
                onClick = onWalletClick,
                modifier = Modifier.weight(1f, fill = false),
                contentColor = MaterialTheme.colorScheme.onSurface,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (balancesHidden) {
                    stringResource(R.string.home_balance_hidden_value)
                } else {
                    totalBalance
                },
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletSwitcherSheet(
    wallets: List<WalletSwitcherRow>,
    activeWalletId: String,
    balancesHidden: Boolean,
    onDismiss: () -> Unit,
    onWalletSelected: (String) -> Unit,
    onCreateWallet: () -> Unit,
    onImportWallet: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.home_wallet_switcher_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_wallet_switcher_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(18.dp))
            if (wallets.isEmpty()) {
                SatraEmptyState(
                    title = stringResource(R.string.home_wallet_switcher_empty_title),
                    body = stringResource(R.string.home_wallet_switcher_empty_body),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    wallets.forEach { wallet ->
                        WalletSwitcherRowItem(
                            wallet = wallet,
                            isActive = wallet.walletId == activeWalletId,
                            balancesHidden = balancesHidden,
                            onClick = { onWalletSelected(wallet.walletId) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onImportWallet,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.home_wallet_switcher_import))
                }
                Button(
                    onClick = onCreateWallet,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.home_wallet_switcher_create))
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun WalletSwitcherRowItem(
    wallet: WalletSwitcherRow,
    isActive: Boolean,
    balancesHidden: Boolean,
    onClick: () -> Unit,
) {
    val displayWalletName = wallet.walletName.ifBlank {
        stringResource(R.string.home_wallet_label)
    }
    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayWalletName,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_wallet_switcher_active),
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (balancesHidden) {
                    stringResource(R.string.home_balance_hidden_value)
                } else {
                    wallet.totalBalance
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isActive) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun HomeBalanceStatusPill(
    status: HomeSyncStatus,
    contentColor: Color,
    mutedContentColor: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(contentColor.copy(alpha = 0.1f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(contentColor),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(status.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = mutedContentColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun HomeBalanceActionButton(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    solid: Boolean,
    enabled: Boolean,
    solidContainerColor: Color,
    solidContentColor: Color,
    outlinedContentColor: Color,
    modifier: Modifier = Modifier,
) {
    if (solid) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(46.dp),
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = solidContainerColor,
                contentColor = solidContentColor,
                disabledContainerColor = solidContainerColor.copy(alpha = 0.16f),
                disabledContentColor = outlinedContentColor.copy(alpha = 0.45f),
            ),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            HomeBalanceActionButtonContent(
                label = label,
                iconRes = iconRes,
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(46.dp),
            shape = RoundedCornerShape(100.dp),
            border = BorderStroke(
                width = 1.dp,
                color = outlinedContentColor.copy(alpha = if (enabled) 0.28f else 0.14f),
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = outlinedContentColor,
                disabledContentColor = outlinedContentColor.copy(alpha = 0.36f),
            ),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            HomeBalanceActionButtonContent(
                label = label,
                iconRes = iconRes,
            )
        }
    }
}

@Composable
private fun HomeBalanceActionButtonContent(
    label: String,
    @DrawableRes iconRes: Int,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(17.dp),
    )
    Spacer(modifier = Modifier.width(7.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable
private fun HomeChartRangeSelector(
    selectedRange: HomeChartRange,
    onRangeSelected: (HomeChartRange) -> Unit,
    enabled: Boolean = true,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primary,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.42f),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HomeChartRange.entries.forEach { range ->
            val selected = selectedRange == range
            if (selected) {
                Button(
                    onClick = { onRangeSelected(range) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = selectedContainerColor,
                        contentColor = selectedContentColor,
                        disabledContainerColor = selectedContainerColor,
                        disabledContentColor = selectedContentColor,
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(
                        text = stringResource(range.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            } else {
                TextButton(
                    onClick = { onRangeSelected(range) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                    shape = RoundedCornerShape(100.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(
                        text = stringResource(range.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = unselectedContentColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeBalanceChart(
    chartData: HomeBalanceChartData,
    hidden: Boolean,
    empty: Boolean,
    selectedPointIndex: Int,
    onSelectedPointChange: (Int) -> Unit,
    lineColor: Color = MaterialTheme.colorScheme.onSurface,
    fillColor: Color = lineColor.copy(alpha = 0.09f),
    baselineColor: Color = lineColor.copy(alpha = 0.25f),
    modifier: Modifier = Modifier,
) {
    val interactionEnabled = !hidden && !empty && chartData.points.isNotEmpty()
    fun selectedIndex(): Int =
        selectedPointIndex.coerceIn(0, (chartData.points.size - 1).coerceAtLeast(0))

    fun chartPointIndexForX(xPosition: Float, width: Float, horizontalPadding: Float): Int {
        val chartWidth = width - horizontalPadding * 2f
        return nearestHomeChartPointIndex(
            xPosition = xPosition - horizontalPadding,
            chartWidth = chartWidth,
            pointCount = chartData.points.size,
        )
    }

    Canvas(
        modifier = modifier
            .pointerInput(interactionEnabled, chartData.points) {
                if (!interactionEnabled) return@pointerInput
                detectTapGestures { offset ->
                    val horizontalPadding = 5.dp.toPx()
                    onSelectedPointChange(
                        chartPointIndexForX(
                            xPosition = offset.x,
                            width = size.width.toFloat(),
                            horizontalPadding = horizontalPadding,
                        ),
                    )
                }
            }
            .pointerInput(interactionEnabled, chartData.points) {
                if (!interactionEnabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val horizontalPadding = 5.dp.toPx()
                        onSelectedPointChange(
                            chartPointIndexForX(
                                xPosition = offset.x,
                                width = size.width.toFloat(),
                                horizontalPadding = horizontalPadding,
                            ),
                        )
                    },
                    onDrag = { change, _ ->
                        val horizontalPadding = 5.dp.toPx()
                        onSelectedPointChange(
                            chartPointIndexForX(
                                xPosition = change.position.x,
                                width = size.width.toFloat(),
                                horizontalPadding = horizontalPadding,
                            ),
                        )
                    },
                )
            },
    ) {
        val horizontalPadding = 5.dp.toPx()
        val topPadding = 5.dp.toPx()
        val bottomPadding = 7.dp.toPx()
        val chartHeight = size.height - topPadding - bottomPadding
        val chartWidth = size.width - horizontalPadding * 2
        if (chartHeight <= 0f || chartWidth <= 0f) return@Canvas

        if (hidden) {
            val centerY = topPadding + chartHeight / 2f
            drawLine(
                color = baselineColor,
                start = Offset(horizontalPadding, centerY),
                end = Offset(size.width - horizontalPadding, centerY),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val minValue = chartData.points.minOfOrNull { point -> point.value } ?: BigDecimal.ZERO
        val maxValue = chartData.points.maxOfOrNull { point -> point.value } ?: BigDecimal.ZERO
        val valueRange = (maxValue - minValue).takeIf { it.compareTo(BigDecimal.ZERO) != 0 }
            ?: BigDecimal.ONE
        val coordinates = chartData.points.mapIndexed { index, point ->
            val progress = if (chartData.points.size == 1) {
                1f
            } else {
                index.toFloat() / chartData.points.lastIndex.toFloat()
            }
            val normalizedValue = (point.value - minValue)
                .divide(valueRange, 6, java.math.RoundingMode.HALF_UP)
                .toFloat()
                .coerceIn(0f, 1f)
            val x = horizontalPadding + chartWidth * progress
            val y = topPadding + chartHeight * (1f - normalizedValue)
            Offset(x, y)
        }
        if (coordinates.isEmpty()) return@Canvas
        val baselineY = if (empty || !chartData.hasActivity) {
            topPadding + chartHeight / 2f
        } else {
            coordinates.first().y
        }
        drawDottedBaseline(
            color = baselineColor,
            startX = horizontalPadding,
            endX = size.width - horizontalPadding,
            y = baselineY,
        )
        if (empty) return@Canvas

        val path = smoothHomeChartPath(coordinates)
        val area = Path().apply {
            moveTo(coordinates.first().x, size.height - bottomPadding)
            addPath(path)
            lineTo(coordinates.last().x, size.height - bottomPadding)
            close()
        }

        drawPath(path = area, color = fillColor)
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
        val nowCoordinate = coordinates[selectedIndex()]
        drawCircle(
            color = lineColor,
            radius = 4.5.dp.toPx(),
            center = nowCoordinate,
        )
        drawCircle(
            color = fillColor.copy(alpha = 0.95f),
            radius = 2.2.dp.toPx(),
            center = nowCoordinate,
        )
    }
}

private fun smoothHomeChartPath(coordinates: List<Offset>): Path {
    return Path().apply {
        coordinates.forEachIndexed { index, offset ->
            if (index == 0) {
                moveTo(offset.x, offset.y)
            } else {
                val previous = coordinates[index - 1]
                val midX = (previous.x + offset.x) / 2f
                cubicTo(
                    midX,
                    previous.y,
                    midX,
                    offset.y,
                    offset.x,
                    offset.y,
                )
            }
        }
    }
}

private fun DrawScope.drawDottedBaseline(
    color: Color,
    startX: Float,
    endX: Float,
    y: Float,
) {
    val radius = 1.4.dp.toPx()
    val spacing = 8.dp.toPx()
    var x = startX
    while (x <= endX) {
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(x, y),
        )
        x += spacing
    }
}

@Composable
private fun HomeBalanceEyeIcon(
    hidden: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2f, height / 2f)
        val strokeWidth = 1.8.dp.toPx()
        val eyePath = Path().apply {
            moveTo(width * 0.08f, height * 0.5f)
            cubicTo(width * 0.25f, height * 0.18f, width * 0.75f, height * 0.18f, width * 0.92f, height * 0.5f)
            cubicTo(width * 0.75f, height * 0.82f, width * 0.25f, height * 0.82f, width * 0.08f, height * 0.5f)
        }
        drawPath(
            path = eyePath,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawCircle(
            color = color,
            radius = minOf(width, height) * 0.16f,
            center = center,
        )
        if (hidden) {
            drawLine(
                color = color,
                start = Offset(width * 0.1f, height * 0.9f),
                end = Offset(width * 0.9f, height * 0.1f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun HomeAssetSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        ),
        placeholder = {
            Text(
                text = stringResource(R.string.home_assets_search_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = if (query.isNotBlank()) {
            {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.home_assets_search_clear),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(100.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun HomeAssetsHeader(
    assetCount: Int,
    totalAssetCount: Int,
    isFiltered: Boolean,
    onFilterClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_assets_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isFiltered) {
                    stringResource(R.string.home_assets_filtered_count, assetCount, totalAssetCount)
                } else {
                    stringResource(R.string.home_assets_count, assetCount)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(
                onClick = onFilterClick,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                ),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_brand_settings),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.home_assets_filter_action),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeAssetFilterSheet(
    networks: List<Pair<String, String>>,
    filterState: HomeAssetFilterState,
    onFilterStateChange: (HomeAssetFilterState) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = HomeContentMaxWidth)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_assets_filter_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.home_assets_filter_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { onFilterStateChange(HomeAssetFilterState()) },
                    enabled = filterState.isActive,
                ) {
                    Text(
                        text = stringResource(R.string.home_assets_filter_reset),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            HomeAssetFilterSectionTitle(R.string.home_assets_filter_show)
            HomeAssetBalanceToggleRow(
                checked = filterState.onlyWithBalance,
                onCheckedChange = { checked ->
                    onFilterStateChange(filterState.copy(onlyWithBalance = checked))
                },
            )
            HomeAssetFilterDivider()
            HomeAssetFilterSectionTitle(R.string.home_assets_filter_sort_by)
            HomeAssetSortOption.entries.forEach { option ->
                HomeAssetSelectableFilterRow(
                    title = stringResource(option.labelRes),
                    selected = filterState.sortOption == option,
                    onClick = {
                        onFilterStateChange(filterState.copy(sortOption = option))
                    },
                )
            }
            HomeAssetFilterDivider()
            HomeAssetFilterSectionTitle(R.string.home_assets_filter_chain)
            HomeAssetSelectableFilterRow(
                title = stringResource(R.string.home_assets_filter_all_chains),
                selected = filterState.networkId == null,
                onClick = {
                    onFilterStateChange(filterState.copy(networkId = null))
                },
            )
            networks.forEach { (networkId, networkName) ->
                HomeAssetSelectableFilterRow(
                    title = networkName,
                    selected = filterState.networkId == networkId,
                    onClick = {
                        onFilterStateChange(filterState.copy(networkId = networkId))
                    },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.HomeAssetFilterDivider() {
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(14.dp))
}

@Composable
private fun HomeAssetFilterSectionTitle(
    @StringRes titleRes: Int,
) {
    Text(
        text = stringResource(titleRes),
        modifier = Modifier.padding(bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun HomeAssetBalanceToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_assets_filter_only_balance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.home_assets_filter_only_balance_body),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun HomeAssetSelectableFilterRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
    }
}

@Composable
private fun HomeAssetListRow(
    asset: HomeAssetRow,
    balancesHidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hiddenValue = stringResource(R.string.home_balance_hidden_value)
    Row(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(asset.iconRes),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = asset.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${asset.symbol} · ${
                    if (asset.networkCount > 1) {
                        stringResource(R.string.home_assets_network_count, asset.networkCount)
                    } else {
                        asset.network
                    }
                }",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (balancesHidden) hiddenValue else asset.fiatValue,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = if (balancesHidden) hiddenValue else asset.amount,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SatraMainPlaceholderTab(
    title: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

internal data class HomeAssetRow(
    val assetId: String,
    val networkId: String,
    val assetIds: List<String>,
    val networkIds: Set<String>,
    val networks: List<Pair<String, String>>,
    val networkCount: Int,
    @DrawableRes val iconRes: Int,
    val symbol: String,
    val name: String,
    val network: String,
    val amount: String,
    val amountValue: BigDecimal,
    val fiatValue: String,
    val fiatValueAmount: BigDecimal,
    val hasBalance: Boolean,
    val isNative: Boolean,
)

private data class ActivityTransactionRow(
    val transactionId: String,
    @DrawableRes val iconRes: Int,
    @DrawableRes val networkIconRes: Int,
    val direction: String,
    val title: String,
    val subtitle: String,
    val amount: String,
    val fiatValue: String,
    val counterparty: String,
    val hash: String,
    val fee: String,
    val searchText: String,
)

private data class TransactionDetailRow(
    val label: String,
    val value: String,
    val sensitive: Boolean = false,
)

private data class MarketAssetRow(
    val symbol: String,
    val name: String,
    val networkName: String,
    val networkCount: Int,
    @DrawableRes val iconRes: Int,
    val price: String,
    val change24hPercent: String?,
    val priceAmount: BigDecimal,
)

private sealed interface MarketDetailState {
    data object Loading : MarketDetailState

    data class NotFound(
        val symbol: String,
    ) : MarketDetailState

    data class Content(
        val symbol: String,
        val name: String,
        @DrawableRes val iconRes: Int,
        val currencyCode: String,
        val price: String,
        val marketCap: String,
        val volume24h: String,
        val high24h: String,
        val low24h: String,
        val change24hPercent: String,
        val change24hAmount: BigDecimal,
        val description: String,
        val homepageUrl: String?,
        val provider: String,
        val updatedAt: String,
        val chartData: HomeBalanceChartData,
    ) : MarketDetailState
}

private data class RawHomeAssetRow(
    val assetId: String,
    val networkId: String,
    val networkName: String,
    val symbol: String,
    val name: String,
    val balance: BigDecimal,
    val fiatValue: BigDecimal,
    val isNative: Boolean,
)

private data class TokenNetworkBalanceRow(
    val assetId: String,
    val networkId: String,
    val network: String,
    val networkSymbol: String,
    @DrawableRes val networkIconRes: Int,
    @DrawableRes val assetIconRes: Int,
    val amount: String,
    val fiatValue: String,
    val fiatValueAmount: BigDecimal,
    val hasBalance: Boolean,
)

private data class WalletSwitcherRow(
    val walletId: String,
    val walletName: String,
    val totalBalance: String,
    val totalBalanceAmount: BigDecimal,
    val isActive: Boolean,
    val createdAt: Long,
)

private sealed interface HomeDashboardState {
    data object Loading : HomeDashboardState

    data class Content(
        val walletId: String,
        val walletName: String,
        val status: HomeSyncStatus,
        val totalBalance: String,
        val totalBalanceAmount: BigDecimal,
        val currencyCode: String,
        val assets: List<HomeAssetRow>,
        val chartTransactions: List<WalletTransactionRecord>,
        val chartData: HomeBalanceChartData,
    ) : HomeDashboardState
}

private sealed interface TokenDetailState {
    data object Loading : TokenDetailState

    data class Content(
        val symbol: String,
        val name: String,
        @DrawableRes val iconRes: Int,
        val currencyCode: String,
        val totalBalance: String,
        val networkBalances: List<TokenNetworkBalanceRow>,
        val transactions: List<ActivityTransactionRow>,
        val chartTransactions: List<WalletTransactionRecord>,
        val chartData: HomeBalanceChartData,
        val sendAssetId: String?,
        val sendRequiresNetwork: Boolean,
    ) : TokenDetailState
}

private sealed interface ActivityScreenState {
    data object Loading : ActivityScreenState

    data class Content(
        val walletName: String,
        val status: HomeSyncStatus,
        val transactions: List<ActivityTransactionRow>,
        val syncedNetworkCount: Int,
        val error: String?,
    ) : ActivityScreenState
}

private sealed interface TransactionDetailState {
    data object Loading : TransactionDetailState
    data object NotFound : TransactionDetailState

    data class Content(
        val transactionId: String,
        @DrawableRes val iconRes: Int,
        @DrawableRes val networkIconRes: Int,
        val direction: String,
        val title: String,
        val subtitle: String,
        val amount: String,
        val fiatValue: String,
        val details: List<TransactionDetailRow>,
    ) : TransactionDetailState
}

private sealed interface MarketsScreenState {
    data object Loading : MarketsScreenState

    data class Content(
        val currencyCode: String,
        val rows: List<MarketAssetRow>,
    ) : MarketsScreenState
}

private enum class HomeSyncStatus(@StringRes val labelRes: Int) {
    Ready(R.string.home_wallet_status_ready),
    Syncing(R.string.home_wallet_status_syncing),
}

private fun WalletRecord.toHomeDashboardState(
    walletAssets: List<WalletAssetRecord>,
    walletTransactions: List<WalletTransactionRecord>,
    status: HomeSyncStatus,
    chartRange: HomeChartRange,
    nowMillis: Long,
): HomeDashboardState.Content {
    val totalFiat = walletAssets.fold(BigDecimal.ZERO) { total, asset ->
        total + asset.balanceFiatAmountFor(localCurrencyCode)
    }
    return HomeDashboardState.Content(
        walletId = walletId,
        walletName = walletName,
        status = status,
        totalBalance = formatFiat(totalFiat.toPlainString(), localCurrencyCode),
        totalBalanceAmount = totalFiat,
        currencyCode = localCurrencyCode,
        assets = walletAssets.toHomeAssetRows(localCurrencyCode),
        chartTransactions = walletTransactions,
        chartData = buildHomeBalanceChartData(
            transactions = walletTransactions,
            range = chartRange,
            nowMillis = nowMillis,
        ),
    )
}

private suspend fun SatraWalletRepository.loadWalletSwitcherRows(): List<WalletSwitcherRow> =
    coroutineScope {
        getWallets()
            .map { wallet ->
                async {
                    val totalFiat = getWalletAssets(wallet.walletId).fold(BigDecimal.ZERO) { total, asset ->
                        total + asset.balanceFiatAmountFor(wallet.localCurrencyCode)
                    }
                    WalletSwitcherRow(
                        walletId = wallet.walletId,
                        walletName = wallet.walletName,
                        totalBalance = formatFiat(totalFiat.toPlainString(), wallet.localCurrencyCode),
                        totalBalanceAmount = totalFiat,
                        isActive = wallet.isActive,
                        createdAt = wallet.createdAt,
                    )
                }
            }
            .map { deferred -> deferred.await() }
            .sortedWith(
                compareByDescending<WalletSwitcherRow> { it.isActive }
                    .thenByDescending { it.createdAt },
            )
    }

private fun WalletRecord.toTokenDetailState(
    symbol: String,
    walletAssets: List<WalletAssetRecord>,
    walletTransactions: List<WalletTransactionRecord>,
    resources: Resources,
    nowMillis: Long,
): TokenDetailState.Content {
    val catalogAssetsById = SupportedAssetCatalog.assets.associateBy { it.assetId }
    val matchingAssets = walletAssets.filter { walletAsset ->
        catalogAssetsById[walletAsset.assetId]?.symbol.equals(symbol, ignoreCase = true)
    }
    val matchingAssetIds = matchingAssets.map { asset -> asset.assetId }.toSet()
    val matchingTransactions = walletTransactions.filter { transaction ->
        transaction.assetId in matchingAssetIds
    }
    val totalFiat = matchingAssets.fold(BigDecimal.ZERO) { total, asset ->
        total + asset.balanceFiatAmountFor(localCurrencyCode)
    }
    val primaryAsset = matchingAssets
        .maxByOrNull { asset -> asset.balanceFiatAmountFor(localCurrencyCode) }
        ?.let { asset -> catalogAssetsById[asset.assetId] }
        ?: SupportedAssetCatalog.assets.firstOrNull { asset -> asset.symbol.equals(symbol, ignoreCase = true) }
    val sendCandidates = matchingAssets
        .filter { asset -> asset.balanceDecimal.toBigDecimalOrZero() > BigDecimal.ZERO }
        .ifEmpty { matchingAssets }
    val sendRequiresNetwork = sendCandidates.size > 1
    val sendAssetId = sendCandidates.singleOrNull()?.assetId
    return TokenDetailState.Content(
        symbol = symbol,
        name = primaryAsset?.name ?: symbol,
        iconRes = assetIconRes(symbol),
        currencyCode = localCurrencyCode,
        totalBalance = formatFiat(totalFiat.toPlainString(), localCurrencyCode),
        networkBalances = matchingAssets.toTokenNetworkBalanceRows(localCurrencyCode),
        transactions = matchingTransactions.toActivityRows(
            localCurrencyCode = localCurrencyCode,
            walletAssets = walletAssets,
            resources = resources,
        ),
        chartTransactions = matchingTransactions,
        chartData = buildHomeBalanceChartData(
            transactions = matchingTransactions,
            range = HomeChartRange.OneWeek,
            nowMillis = nowMillis,
        ),
        sendAssetId = sendAssetId,
        sendRequiresNetwork = sendRequiresNetwork,
    )
}

private fun List<SupportedAsset>.toMarketRows(
    marketData: List<AssetMarketDataRecord>,
    localCurrencyCode: String,
): List<MarketAssetRow> {
    val marketDataBySymbol = marketData.associateBy { record -> record.symbol.uppercase(Locale.US) }
    val networksById = SupportedAssetCatalog.networks.associateBy { network -> network.networkId }
    return groupBy { asset -> asset.symbol.uppercase(Locale.US) }
        .map { (symbol, assets) ->
            val market = marketDataBySymbol[symbol]
            val primaryAsset = assets.firstOrNull { asset -> asset.assetType == "NATIVE" } ?: assets.first()
            val primaryNetwork = networksById[primaryAsset.networkId]
            val cachedMarketPrice = market?.priceLocal
                ?.toBigDecimalOrZero()
                ?.takeIf { price -> price > BigDecimal.ZERO }
            val price = cachedMarketPrice ?: BigDecimal.ZERO
            val networkCount = assets.map { asset -> asset.networkId }.distinct().size
            MarketAssetRow(
                symbol = symbol,
                name = primaryAsset.name,
                networkName = primaryNetwork?.displayName.orEmpty(),
                networkCount = networkCount,
                iconRes = assetIconRes(symbol),
                price = formatFiat(price.toPlainString(), localCurrencyCode),
                change24hPercent = market?.priceChange24hPercent
                    ?.toBigDecimalOrZero()
                    ?.let(::formatPercent),
                priceAmount = price,
            )
        }
        .sortedWith(
            compareByDescending<MarketAssetRow> { row -> row.priceAmount }
                .thenBy { row -> row.name.lowercase(Locale.US) },
        )
}

private fun List<MarketAssetRow>.applyMarketSearch(query: String): List<MarketAssetRow> {
    val normalizedQuery = query.trim().lowercase(Locale.US)
    if (normalizedQuery.isBlank()) return this

    return filter { row ->
        row.name.lowercase(Locale.US).contains(normalizedQuery) ||
            row.symbol.lowercase(Locale.US).contains(normalizedQuery) ||
            row.networkName.lowercase(Locale.US).contains(normalizedQuery)
    }
}

private fun List<ActivityTransactionRow>.applyActivitySearch(query: String): List<ActivityTransactionRow> {
    val normalizedQuery = query.trim().lowercase(Locale.US)
    if (normalizedQuery.isBlank()) return this

    return filter { row ->
        row.title.lowercase(Locale.US).contains(normalizedQuery) ||
            row.subtitle.lowercase(Locale.US).contains(normalizedQuery) ||
            row.amount.lowercase(Locale.US).contains(normalizedQuery) ||
            row.fiatValue.lowercase(Locale.US).contains(normalizedQuery) ||
            row.counterparty.lowercase(Locale.US).contains(normalizedQuery) ||
            row.hash.lowercase(Locale.US).contains(normalizedQuery) ||
            row.fee.lowercase(Locale.US).contains(normalizedQuery) ||
            row.searchText.lowercase(Locale.US).contains(normalizedQuery)
    }
}

private fun AssetMarketDataRecord.toMarketDetailState(
    localCurrencyCode: String,
    resources: Resources,
): MarketDetailState.Content {
    val displayCurrency = localCurrencyCode.ifBlank { DEFAULT_LOCAL_CURRENCY_CODE }
    val unavailable = resources.getString(R.string.market_detail_unavailable)
    val change = priceChange24hPercent?.toBigDecimalOrZero() ?: BigDecimal.ZERO
    val chartValueMultiplier = priceLocal.toBigDecimalOrZero()
        .divide(
            priceUsd.toBigDecimalOrZero().takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE,
            18,
            RoundingMode.HALF_UP,
        )
    return MarketDetailState.Content(
        symbol = symbol,
        name = name,
        iconRes = assetIconRes(symbol),
        currencyCode = displayCurrency,
        price = formatFiat(priceLocal, displayCurrency),
        marketCap = marketCapLocal?.let { formatFiat(it, displayCurrency) } ?: unavailable,
        volume24h = volume24hLocal?.let { formatFiat(it, displayCurrency) } ?: unavailable,
        high24h = high24hUsd?.let { value ->
            val local = value.toBigDecimalOrZero()
                .multiply(priceLocal.toBigDecimalOrZero())
                .divide(priceUsd.toBigDecimalOrZero().takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE, 12, RoundingMode.HALF_UP)
            formatFiat(local.toPlainString(), displayCurrency)
        } ?: unavailable,
        low24h = low24hUsd?.let { value ->
            val local = value.toBigDecimalOrZero()
                .multiply(priceLocal.toBigDecimalOrZero())
                .divide(priceUsd.toBigDecimalOrZero().takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE, 12, RoundingMode.HALF_UP)
            formatFiat(local.toPlainString(), displayCurrency)
        } ?: unavailable,
        change24hPercent = formatPercent(change),
        change24hAmount = change,
        description = description
            ?.takeIf(String::isNotBlank)
            ?: resources.getString(R.string.market_detail_description_unavailable),
        homepageUrl = homepageUrl,
        provider = provider,
        updatedAt = formatActivityTime(updatedAt, resources),
        chartData = buildMarketPriceChartData(
            chart7dJson = chart7dJson,
            fallbackPrice = priceLocal.toBigDecimalOrZero(),
            nowMillis = System.currentTimeMillis(),
            valueMultiplier = chartValueMultiplier,
        ),
    )
}

private fun List<WalletAssetRecord>.toTokenNetworkBalanceRows(localCurrencyCode: String): List<TokenNetworkBalanceRow> {
    val catalogAssetsById = SupportedAssetCatalog.assets.associateBy { it.assetId }
    val networksById = SupportedAssetCatalog.networks.associateBy { it.networkId }
    return mapNotNull { walletAsset ->
        val asset = catalogAssetsById[walletAsset.assetId] ?: return@mapNotNull null
        val network = networksById[walletAsset.networkId] ?: return@mapNotNull null
        val balance = walletAsset.balanceDecimal.toBigDecimalOrZero()
        val fiatValue = walletAsset.balanceFiatAmountFor(localCurrencyCode)
        TokenNetworkBalanceRow(
            assetId = walletAsset.assetId,
            networkId = walletAsset.networkId,
            network = network.displayName,
            networkSymbol = network.nativeSymbol,
            networkIconRes = networkIconRes(network.networkId),
            assetIconRes = assetIconRes(asset.symbol),
            amount = "${formatCryptoAmount(walletAsset.balanceDecimal)} ${asset.symbol}",
            fiatValue = formatFiat(fiatValue.toPlainString(), localCurrencyCode),
            fiatValueAmount = fiatValue,
            hasBalance = balance > BigDecimal.ZERO,
        )
    }.sortedWith(
        compareByDescending<TokenNetworkBalanceRow> { row -> row.fiatValueAmount }
            .thenByDescending { row -> row.hasBalance }
            .thenBy { row -> row.network.lowercase(Locale.US) },
    )
}

private fun List<WalletTransactionRecord>.toActivityRows(
    localCurrencyCode: String,
    walletAssets: List<WalletAssetRecord>,
    resources: Resources,
): List<ActivityTransactionRow> {
    val catalogAssetsById = SupportedAssetCatalog.assets.associateBy { it.assetId }
    val networksById = SupportedAssetCatalog.networks.associateBy { it.networkId }
    val supportedNetworkIds = networksById.keys
    val localPricesByAssetId = walletAssets.associate { asset ->
        asset.assetId to asset.priceFiatAmountFor(localCurrencyCode)
    }
    return filter { transaction -> transaction.networkId in supportedNetworkIds }
        .mapNotNull { transaction ->
            val asset = catalogAssetsById[transaction.assetId] ?: return@mapNotNull null
            val network = networksById[transaction.networkId] ?: return@mapNotNull null
            val direction = transaction.direction.activityDirectionLabel(resources)
            val time = formatTransactionListTime(
                timestampMillis = transaction.timestamp,
                status = transaction.status,
                resources = resources,
            )
            val amount = "${transaction.direction.activityAmountPrefix()}${formatCryptoAmount(transaction.amountDecimal)} ${asset.symbol}"
            val fiatValue = transaction.displayFiatValue(
                localCurrencyCode = localCurrencyCode,
                localPrice = localPricesByAssetId[transaction.assetId],
            )
            val counterparty = transaction.activityCounterparty(resources)
            val hash = transaction.transactionHash?.shortHash().orEmpty()
            val fee = transaction.feeDecimal?.takeIf { it.toBigDecimalOrZero() > BigDecimal.ZERO }
                ?.let { fee ->
                    val feeAsset = transaction.feeAssetId?.let(catalogAssetsById::get)
                    resources.getString(
                        R.string.activity_fee,
                        formatCryptoAmount(fee),
                        feeAsset?.symbol.orEmpty(),
                    ).trim()
                }
                .orEmpty()
            ActivityTransactionRow(
                transactionId = transaction.transactionId,
                iconRes = assetIconRes(asset.symbol),
                networkIconRes = networkIconRes(network.networkId),
                direction = transaction.direction,
                title = "$direction ${asset.symbol}",
                subtitle = resources.getString(
                    R.string.activity_transaction_subtitle_on_network,
                    direction,
                    network.nativeSymbol,
                    time,
                ),
                amount = amount,
                fiatValue = fiatValue,
                counterparty = counterparty,
                hash = hash,
                fee = fee,
                searchText = listOf(
                    asset.name,
                    asset.symbol,
                    asset.assetId,
                    network.displayName,
                    network.nativeSymbol,
                    network.networkId,
                    direction,
                    time,
                    amount,
                    fiatValue,
                    counterparty,
                    fee,
                    transaction.transactionId,
                    transaction.transactionHash.orEmpty(),
                    transaction.fromAddress.orEmpty(),
                    transaction.toAddress.orEmpty(),
                    transaction.status,
                    transaction.amountDecimal,
                    transaction.fiatValue.orEmpty(),
                    transaction.feeDecimal.orEmpty(),
                ).joinToString(" "),
            )
        }
}

private fun WalletTransactionRecord.toTransactionDetailContent(
    localCurrencyCode: String,
    walletAssets: List<WalletAssetRecord>,
    resources: Resources,
): TransactionDetailState.Content? {
    val catalogAssetsById = SupportedAssetCatalog.assets.associateBy { it.assetId }
    val networksById = SupportedAssetCatalog.networks.associateBy { it.networkId }
    val asset = catalogAssetsById[assetId] ?: return null
    val network = networksById[networkId]
    val directionLabel = direction.activityDirectionLabel(resources)
    val networkSymbol = network?.nativeSymbol ?: networkId
    val transactionTime = formatTransactionDetailTime(timestamp, resources)
    val displayAmount = "${direction.activityAmountPrefix()}${formatCryptoAmount(amountDecimal)} ${asset.symbol}"
    val displayFiat = displayFiatValue(
        localCurrencyCode = localCurrencyCode,
        localPrice = walletAssets
            .firstOrNull { walletAsset -> walletAsset.assetId == assetId }
            ?.priceFiatAmountFor(localCurrencyCode),
    )
    val feeAsset = feeAssetId?.let(catalogAssetsById::get)
    val displayFee = feeDecimal?.takeIf(String::isNotBlank)?.let { fee ->
        "${formatCryptoAmount(fee)} ${feeAsset?.symbol ?: asset.symbol}"
    } ?: resources.getString(R.string.activity_detail_unavailable)
    val notAvailable = resources.getString(R.string.activity_detail_unavailable)
    val detailRows = buildList {
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_transaction_id), transactionId))
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_asset), "${asset.name} (${asset.symbol})"))
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_network), network?.displayName ?: networkId))
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_status), status.activityStatusLabel(resources)))
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_direction), directionLabel))
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_amount), displayAmount, sensitive = true))
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_value), displayFiat, sensitive = true))
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_fee), displayFee, sensitive = true))
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_from), fromAddress.orEmpty().ifBlank { notAvailable }))
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_to), toAddress.orEmpty().ifBlank { notAvailable }))
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_hash), transactionHash.orEmpty().ifBlank { notAvailable }))
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_block_height), blockHeight?.toString() ?: notAvailable))
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_block_hash), blockHash.orEmpty().ifBlank { notAvailable }))
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_confirmations), confirmations.toString()))
        nonce?.takeIf(String::isNotBlank)?.let { nonceValue ->
            add(TransactionDetailRow(resources.getString(R.string.activity_detail_nonce), nonceValue))
        }
        memo?.takeIf(String::isNotBlank)?.let { memoValue ->
            add(TransactionDetailRow(resources.getString(R.string.activity_detail_memo), memoValue))
        }
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_time), transactionTime))
        add(TransactionDetailRow(resources.getString(R.string.activity_detail_updated), formatActivityTime(updatedAt, resources)))
    }
    return TransactionDetailState.Content(
        transactionId = transactionId,
        iconRes = assetIconRes(asset.symbol),
        networkIconRes = networkIconRes(networkId),
        direction = direction,
        title = "$directionLabel ${asset.symbol}",
        subtitle = resources.getString(
            R.string.activity_transaction_subtitle_on_network,
            directionLabel,
            networkSymbol,
            transactionTime,
        ),
        amount = displayAmount,
        fiatValue = displayFiat,
        details = detailRows,
    )
}

private fun List<WalletAssetRecord>.toHomeAssetRows(localCurrencyCode: String): List<HomeAssetRow> {
    val catalogAssetsById = SupportedAssetCatalog.assets.associateBy { it.assetId }
    val networksById = SupportedAssetCatalog.networks.associateBy { it.networkId }
    val rawRows = mapNotNull { walletAsset ->
        val asset = catalogAssetsById[walletAsset.assetId] ?: return@mapNotNull null
        val network = networksById[walletAsset.networkId] ?: return@mapNotNull null
        val balance = walletAsset.balanceDecimal.toBigDecimalOrZero()
        val fiatValue = walletAsset.balanceFiatAmountFor(localCurrencyCode)
        RawHomeAssetRow(
            assetId = asset.assetId,
            networkId = walletAsset.networkId,
            networkName = network.displayName,
            symbol = asset.symbol.uppercase(Locale.US),
            name = asset.name,
            balance = balance,
            fiatValue = fiatValue,
            isNative = asset.assetType == "NATIVE",
        )
    }
    return rawRows
        .groupBy { row -> row.symbol }
        .map { (symbol, rows) ->
            val primary = rows.maxWith(
                compareBy<RawHomeAssetRow> { row -> row.fiatValue }
                    .thenBy { row -> row.balance },
            )
            val totalBalance = rows.fold(BigDecimal.ZERO) { total, row -> total + row.balance }
            val totalFiat = rows.fold(BigDecimal.ZERO) { total, row -> total + row.fiatValue }
            val networks = rows
                .map { row -> row.networkId to row.networkName }
                .distinctBy { (networkId, _) -> networkId }
                .sortedBy { (_, networkName) -> networkName.lowercase(Locale.US) }
            HomeAssetRow(
                assetId = symbol,
                networkId = primary.networkId,
                assetIds = rows.map { row -> row.assetId }.distinct(),
                networkIds = networks.map { (networkId, _) -> networkId }.toSet(),
                networks = networks,
                networkCount = networks.size,
                iconRes = assetIconRes(symbol),
                symbol = symbol,
                name = primary.name,
                network = primary.networkName,
                amount = "${formatCryptoAmount(totalBalance.toPlainString())} $symbol",
                amountValue = totalBalance,
                fiatValue = formatFiat(totalFiat.toPlainString(), localCurrencyCode),
                fiatValueAmount = totalFiat,
                hasBalance = totalBalance > BigDecimal.ZERO,
                isNative = rows.all { row -> row.isNative },
            )
        }
        .applyHomeAssetFilter(HomeAssetFilterState())
}

private fun List<HomeAssetRow>.applyHomeAssetSearch(query: String): List<HomeAssetRow> {
    val normalizedQuery = query.trim().lowercase(Locale.US)
    if (normalizedQuery.isBlank()) return this
    return filter { row ->
        row.name.lowercase(Locale.US).contains(normalizedQuery) ||
            row.symbol.lowercase(Locale.US).contains(normalizedQuery) ||
            row.network.lowercase(Locale.US).contains(normalizedQuery) ||
            row.assetId.lowercase(Locale.US).contains(normalizedQuery) ||
            row.assetIds.any { assetId -> assetId.lowercase(Locale.US).contains(normalizedQuery) } ||
            row.networkIds.any { networkId -> networkId.lowercase(Locale.US).contains(normalizedQuery) } ||
            row.networks.any { (_, networkName) ->
                networkName.lowercase(Locale.US).contains(normalizedQuery)
            }
    }
}

private fun formatCryptoAmount(value: String): String {
    val decimal = value.toBigDecimalOrZero()
        .setScale(CRYPTO_DISPLAY_DECIMALS, RoundingMode.DOWN)
        .stripTrailingZeros()
    return if (decimal.compareTo(BigDecimal.ZERO) == 0) {
        "0"
    } else {
        decimal.toPlainString()
    }
}

private fun formatFiat(
    value: String,
    currencyCode: String,
): String {
    val amount = value.toBigDecimalOrZero()
    val formatter = NumberFormat.getCurrencyInstance(Locale.US).apply {
        runCatching {
            currency = Currency.getInstance(currencyCode)
        }
    }
    return formatter.format(amount)
}

private fun buildMarketPriceChartData(
    chart7dJson: String,
    fallbackPrice: BigDecimal,
    nowMillis: Long,
    valueMultiplier: BigDecimal = BigDecimal.ONE,
): HomeBalanceChartData {
    val prices = runCatching {
        val json = org.json.JSONArray(chart7dJson)
        buildList {
            for (index in 0 until json.length()) {
                json.opt(index)
                    ?.takeUnless { it == JSONObject.NULL }
                    ?.toString()
                    ?.toBigDecimalOrNull()
                    ?.multiply(valueMultiplier)
                    ?.let(::add)
            }
        }
    }.getOrDefault(emptyList())
        .ifEmpty { listOf(fallbackPrice, fallbackPrice) }
    val rangeStart = nowMillis - (HomeChartRange.OneWeek.durationMillis ?: 0L)
    val stepMillis = if (prices.size <= 1) {
        0L
    } else {
        (nowMillis - rangeStart) / (prices.size - 1)
    }
    val points = prices.mapIndexed { index, price ->
        HomeBalanceChartPoint(
            timestampMillis = if (prices.size <= 1) nowMillis else rangeStart + stepMillis * index,
            value = price,
        )
    }
    val startValue = points.firstOrNull()?.value ?: fallbackPrice
    val currentValue = points.lastOrNull()?.value ?: fallbackPrice
    val changeValue = currentValue - startValue
    val percent = if (startValue.compareTo(BigDecimal.ZERO) == 0) {
        BigDecimal.ZERO
    } else {
        changeValue
            .divide(startValue.abs(), 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal("100"))
            .stripTrailingZeros()
    }
    return HomeBalanceChartData(
        range = HomeChartRange.OneWeek,
        points = points,
        startValue = startValue,
        currentValue = currentValue,
        changeValue = changeValue,
        percentChange = percent,
        transactionCount = points.size,
    )
}

private fun formatPercent(value: BigDecimal): String {
    val normalized = value.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros()
    val prefix = if (normalized.signum() > 0) "+" else ""
    return "$prefix${normalized.toPlainString()}%"
}

private fun formatChartPointTime(
    timestampMillis: Long,
    range: HomeChartRange,
): String {
    val zone = ZoneId.systemDefault()
    val formatter = when (range) {
        HomeChartRange.OneDay -> DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        HomeChartRange.OneWeek,
        HomeChartRange.OneMonth -> DateTimeFormatter.ofPattern("MMM d", Locale.US)
        HomeChartRange.OneYear,
        HomeChartRange.All -> DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    }
    return Instant.ofEpochMilli(timestampMillis).atZone(zone).format(formatter)
}

private val HomeChartRange.labelRes: Int
    @StringRes get() = when (this) {
        HomeChartRange.OneDay -> R.string.home_chart_range_1d
        HomeChartRange.OneWeek -> R.string.home_chart_range_1w
        HomeChartRange.OneMonth -> R.string.home_chart_range_1m
        HomeChartRange.OneYear -> R.string.home_chart_range_1y
        HomeChartRange.All -> R.string.home_chart_range_all
    }

private val HomeAssetSortOption.labelRes: Int
    @StringRes get() = when (this) {
        HomeAssetSortOption.Value -> R.string.home_assets_sort_value
        HomeAssetSortOption.Amount -> R.string.home_assets_sort_amount
        HomeAssetSortOption.Name -> R.string.home_assets_sort_name
    }

private fun String.activityDirectionLabel(resources: Resources): String =
    when (this) {
        WalletTransactionDirection.Incoming.value -> resources.getString(R.string.activity_direction_received)
        WalletTransactionDirection.Outgoing.value -> resources.getString(R.string.activity_direction_sent)
        WalletTransactionDirection.Self.value -> resources.getString(R.string.activity_direction_self)
        else -> resources.getString(R.string.activity_direction_transaction)
    }

private fun String.activityAmountPrefix(): String =
    when (this) {
        WalletTransactionDirection.Incoming.value -> "+"
        WalletTransactionDirection.Outgoing.value -> "-"
        else -> ""
    }

private fun String.activityStatusLabel(resources: Resources): String =
    when (this) {
        WalletTransactionStatus.Success.value -> resources.getString(R.string.activity_status_success)
        WalletTransactionStatus.Pending.value -> resources.getString(R.string.activity_status_pending)
        WalletTransactionStatus.Canceled.value -> resources.getString(R.string.activity_status_canceled)
        WalletTransactionStatus.Failed.value -> resources.getString(R.string.activity_status_failed)
        else -> replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase(Locale.US) else character.toString()
        }
    }

private fun WalletTransactionRecord.activityCounterparty(resources: Resources): String =
    when (direction) {
        WalletTransactionDirection.Incoming.value -> toAddress?.shortAddress()?.let {
            resources.getString(R.string.activity_counterparty_to, it)
        }
        WalletTransactionDirection.Outgoing.value -> toAddress?.shortAddress()?.let {
            resources.getString(R.string.activity_counterparty_to, it)
        }
        WalletTransactionDirection.Self.value -> resources.getString(R.string.activity_direction_self)
        else -> {
            val from = fromAddress?.shortAddress()
            val to = toAddress?.shortAddress()
            if (from != null && to != null) {
                resources.getString(R.string.activity_counterparty_route, from, to)
            } else {
                listOfNotNull(from, to).firstOrNull()
            }
        }
    } ?: resources.getString(R.string.activity_counterparty_unavailable)

private fun WalletRecord.syncedNetworkCount(): Int =
    runCatching {
        val root = JSONObject(metadataJson)
        (root.optJSONObject("evmSync")?.optInt("syncedNetworkCount") ?: 0) +
            (root.optJSONObject("utxoSync")?.optInt("syncedNetworkCount") ?: 0) +
            (root.optJSONObject("solanaSync")?.optInt("syncedNetworkCount") ?: 0) +
            (root.optJSONObject("accountChainSync")?.optInt("syncedNetworkCount") ?: 0)
    }.getOrNull() ?: 0

private fun formatActivityTime(
    timestampMillis: Long,
    resources: Resources,
): String {
    if (timestampMillis <= 0L) return resources.getString(R.string.activity_status_pending)

    val timestamp = Instant.ofEpochMilli(timestampMillis)
    val now = Instant.now()
    val elapsed = Duration.between(timestamp, now)
    if (!elapsed.isNegative) {
        if (elapsed.toMinutes() < 1) {
            return resources.getString(R.string.activity_time_moment_ago)
        }
        if (elapsed.toHours() < 1) {
            return resources.getString(R.string.activity_time_minutes_ago, elapsed.toMinutes())
        }
        if (elapsed.toHours() < 6) {
            return resources.getString(R.string.activity_time_hours_ago, elapsed.toHours())
        }
    }

    val zone = ZoneId.systemDefault()
    val date = timestamp.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val time = ActivityTimeFormatter.format(timestamp)
    return when (date) {
        today -> resources.getString(R.string.activity_time_today, time)
        today.minusDays(1) -> resources.getString(R.string.activity_time_yesterday, time)
        else -> ActivityDateFormatter.format(timestamp)
    }
}

private fun formatTransactionDetailTime(
    timestampMillis: Long,
    resources: Resources,
): String =
    if (timestampMillis > 0L) {
        formatActivityTime(timestampMillis, resources)
    } else {
        resources.getString(R.string.activity_detail_unavailable)
    }

private fun formatTransactionListTime(
    timestampMillis: Long,
    status: String,
    resources: Resources,
): String =
    when {
        timestampMillis > 0L -> formatActivityTime(timestampMillis, resources)
        status == WalletTransactionStatus.Pending.value -> resources.getString(R.string.activity_status_pending)
        else -> resources.getString(R.string.activity_detail_unavailable)
    }

private fun WalletAssetRecord.balanceFiatAmountFor(localCurrencyCode: String): BigDecimal =
    if (this.localCurrencyCode.equals(localCurrencyCode, ignoreCase = true)) {
        balanceFiatValue.toBigDecimalOrZero()
    } else {
        BigDecimal.ZERO
    }

private fun WalletAssetRecord.priceFiatAmountFor(localCurrencyCode: String): BigDecimal? =
    priceFiatValue
        .toBigDecimalOrZero()
        .takeIf { price ->
            price > BigDecimal.ZERO &&
                this.localCurrencyCode.equals(localCurrencyCode, ignoreCase = true)
        }

private fun WalletTransactionRecord.displayFiatValue(
    localCurrencyCode: String,
    localPrice: BigDecimal?,
): String {
    val transactionFiat = fiatValue
        ?.takeIf(String::isNotBlank)
        ?.takeIf { this.localCurrencyCode.equals(localCurrencyCode, ignoreCase = true) }
        ?.toBigDecimalOrZero()
        ?.takeIf { it > BigDecimal.ZERO }
        ?: localPrice
            ?.takeIf { it > BigDecimal.ZERO }
            ?.multiply(amountDecimal.toBigDecimalOrZero().abs())
    return formatFiat(transactionFiat?.toPlainString() ?: "0", localCurrencyCode)
}

private fun String.shortHash(): String =
    if (length <= 14) {
        this
    } else {
        "${take(8)}...${takeLast(6)}"
    }

private fun String.shortAddress(): String =
    if (length <= 14) {
        this
    } else {
        "${take(6)}...${takeLast(4)}"
    }

private fun String.toBigDecimalOrZero(): BigDecimal =
    runCatching { BigDecimal(this) }.getOrDefault(BigDecimal.ZERO)

@DrawableRes
internal fun networkIconRes(networkId: String): Int =
    when (networkId) {
        "bitcoin" -> R.drawable.ic_chain_bitcoin
        "bitcoinCash" -> R.drawable.ic_chain_bitcoin_cash
        "dogecoin" -> R.drawable.ic_chain_dogecoin
        "litecoin" -> R.drawable.ic_chain_litecoin
        "ethereum" -> R.drawable.ic_chain_ethereum
        "arbitrum" -> R.drawable.ic_chain_arbitrum
        "base" -> R.drawable.ic_chain_base
        "optimism" -> R.drawable.ic_chain_optimism
        "scroll" -> R.drawable.ic_chain_scroll
        "zkSync" -> R.drawable.ic_chain_zksync
        "polygon" -> R.drawable.ic_chain_polygon
        "bnbChain" -> R.drawable.ic_chain_bnb_chain
        "opBNB" -> R.drawable.ic_chain_opbnb
        "avalanche" -> R.drawable.ic_chain_avalanche
        "celo" -> R.drawable.ic_chain_celo
        "kavaEvm" -> R.drawable.ic_chain_kava_evm
        "aptos" -> R.drawable.ic_chain_aptos
        "near" -> R.drawable.ic_chain_near
        "polkadot" -> R.drawable.ic_chain_polkadot
        "ripple" -> R.drawable.ic_chain_xrp_ledger
        "solana" -> R.drawable.ic_chain_solana
        "stellar" -> R.drawable.ic_chain_stellar
        "sui" -> R.drawable.ic_chain_sui
        "ton" -> R.drawable.ic_chain_ton
        "tron" -> R.drawable.ic_chain_tron
        "kava" -> R.drawable.ic_chain_kava
        else -> R.drawable.ic_brand_assets
    }

@DrawableRes
internal fun assetIconRes(symbol: String): Int =
    when (symbol.uppercase(Locale.US)) {
        "APT" -> R.drawable.ic_asset_apt
        "AUSD" -> R.drawable.ic_asset_ausd
        "AVAX" -> R.drawable.ic_asset_avax
        "BCH" -> R.drawable.ic_asset_bch
        "BNB" -> R.drawable.ic_asset_bnb
        "BTC" -> R.drawable.ic_asset_btc
        "CELO" -> R.drawable.ic_asset_celo
        "DAI" -> R.drawable.ic_asset_dai
        "DOGE" -> R.drawable.ic_asset_doge
        "DOT" -> R.drawable.ic_asset_dot
        "DUSD" -> R.drawable.ic_asset_dusd
        "ETH" -> R.drawable.ic_asset_eth
        "EURC" -> R.drawable.ic_asset_eurc
        "FDUSD" -> R.drawable.ic_asset_fdusd
        "FRAX" -> R.drawable.ic_asset_frax
        "GUSD" -> R.drawable.ic_asset_gusd
        "KAVA" -> R.drawable.ic_asset_kava
        "LTC" -> R.drawable.ic_asset_ltc
        "NEAR" -> R.drawable.ic_asset_near
        "POL" -> R.drawable.ic_asset_pol
        "PYUSD" -> R.drawable.ic_asset_pyusd
        "RLUSD" -> R.drawable.ic_asset_rlusd
        "SOL" -> R.drawable.ic_asset_sol
        "STETH" -> R.drawable.ic_asset_steth
        "SUI" -> R.drawable.ic_asset_sui
        "TON" -> R.drawable.ic_asset_ton
        "TRX" -> R.drawable.ic_asset_trx
        "TUSD" -> R.drawable.ic_asset_tusd
        "USD0" -> R.drawable.ic_asset_usd0
        "USD1" -> R.drawable.ic_asset_usd1
        "USDAI" -> R.drawable.ic_asset_usdai
        "USDC" -> R.drawable.ic_asset_usdc
        "USDD" -> R.drawable.ic_asset_usdd
        "USDE" -> R.drawable.ic_asset_usde
        "USDF" -> R.drawable.ic_asset_usdf
        "USDG" -> R.drawable.ic_asset_usdg
        "USDP" -> R.drawable.ic_asset_usdp
        "USDS" -> R.drawable.ic_asset_usds
        "USDT" -> R.drawable.ic_asset_usdt
        "WBTC" -> R.drawable.ic_asset_wbtc
        "WETH" -> R.drawable.ic_asset_weth
        "XLM" -> R.drawable.ic_asset_xlm
        "XRP" -> R.drawable.ic_asset_xrp
        else -> R.drawable.ic_brand_assets
    }

private enum class SatraMainTab(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    Home(
        route = "main/home",
        labelRes = R.string.main_nav_home,
        iconRes = R.drawable.ic_brand_wallet,
    ),
    Activity(
        route = "main/activity",
        labelRes = R.string.main_nav_activity,
        iconRes = R.drawable.ic_brand_history,
    ),
    Markets(
        route = "main/markets",
        labelRes = R.string.main_nav_markets,
        iconRes = R.drawable.ic_brand_assets,
    ),
    Settings(
        route = "main/settings",
        labelRes = R.string.main_nav_settings,
        iconRes = R.drawable.ic_brand_settings,
    ),
}

internal object SatraMainRoute {
    const val Receive = "main/receive"
    const val SendAsset = "main/send"
    const val ArgSymbol = "symbol"
    const val ArgAssetId = "assetId"
    const val ArgTransactionId = "transactionId"
    const val ArgRecipient = "recipient"
    const val ArgAmount = "amount"
    const val ArgWarnPoison = "warnPoison"
    const val ArgWalletId = "walletId"
    const val TokenDetailPattern = "main/token/{$ArgSymbol}"
    const val ReceiveNetworkPattern = "main/receive/network/{$ArgSymbol}"
    const val ReceiveQrPattern = "main/receive/qr/{$ArgAssetId}"
    const val SendNetworkPattern = "main/send/network/{$ArgSymbol}"
    const val SendComposerPattern = "main/send/compose/{$ArgAssetId}"
    const val SendRecipientPattern = "main/send/recipient/{$ArgAssetId}"
    const val SendAmountPattern = "main/send/amount/{$ArgAssetId}/{$ArgRecipient}/{$ArgWarnPoison}"
    const val SendReviewPattern = "main/send/review/{$ArgAssetId}/{$ArgRecipient}/{$ArgAmount}/{$ArgWarnPoison}"
    const val SendSentPattern = "main/send/sent/{$ArgTransactionId}"
    const val TransactionDetailPattern = "main/activity/transaction/{$ArgTransactionId}"
    const val MarketDetailPattern = "main/markets/asset/{$ArgSymbol}"
    const val WalletManagement = "main/settings/wallet-management"
    const val WalletRemoveWarningPattern = "main/settings/wallet-management/remove/{$ArgWalletId}/warning"
    const val WalletRemovePasscodePattern = "main/settings/wallet-management/remove/{$ArgWalletId}/passcode"
    const val AddressBook = "main/settings/address-book"
    const val Preferences = "main/settings/preferences"
    const val Currency = "main/settings/preferences/currency"
    const val Language = "main/settings/preferences/language"
    const val Appearance = "main/settings/preferences/appearance"
    const val Security = "main/settings/security"
    const val SecurityTurnOffPasscode = "main/settings/security/turn-off-passcode"
    const val Notifications = "main/settings/notifications"
    const val About = "main/settings/about"
    const val Legal = "main/settings/legal"
    const val DangerZone = "main/settings/danger-zone"
    const val DangerZonePasscode = "main/settings/danger-zone/passcode"

    fun tokenDetail(symbol: String): String =
        "main/token/${Uri.encode(symbol)}"

    fun receiveNetwork(symbol: String): String =
        "main/receive/network/${Uri.encode(symbol)}"

    fun receiveQr(assetId: String): String =
        "main/receive/qr/${Uri.encode(assetId)}"

    fun sendNetwork(symbol: String): String =
        "main/send/network/${Uri.encode(symbol)}"

    fun sendComposer(assetId: String): String =
        sendRecipient(assetId)

    fun sendRecipient(assetId: String): String =
        "main/send/recipient/${Uri.encode(assetId)}"

    fun sendAmount(
        assetId: String,
        recipient: String,
        warnPoison: Boolean,
    ): String =
        "main/send/amount/${Uri.encode(assetId)}/${Uri.encode(recipient)}/$warnPoison"

    fun sendReview(
        assetId: String,
        recipient: String,
        amount: String,
        warnPoison: Boolean,
    ): String =
        "main/send/review/${Uri.encode(assetId)}/${Uri.encode(recipient)}/${Uri.encode(amount)}/$warnPoison"

    fun sendSent(transactionId: String): String =
        "main/send/sent/${Uri.encode(transactionId)}"

    fun transactionDetail(transactionId: String): String =
        "main/activity/transaction/${Uri.encode(transactionId)}"

    fun marketDetail(symbol: String): String =
        "main/markets/asset/${Uri.encode(symbol)}"

    fun walletRemoveWarning(walletId: String): String =
        "main/settings/wallet-management/remove/${Uri.encode(walletId)}/warning"

    fun walletRemovePasscode(walletId: String): String =
        "main/settings/wallet-management/remove/${Uri.encode(walletId)}/passcode"
}

private val HomeContentMaxWidth = 720.dp
private const val BALANCE_CARD_PREFS_NAME = "satra_balance_card"
private const val BALANCE_CARD_HIDDEN_KEY = "satra.balanceHidden"
private const val CRYPTO_DISPLAY_DECIMALS = 8
private val ActivityDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)
        .withZone(ZoneId.systemDefault())
private val ActivityTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        .withZone(ZoneId.systemDefault())
