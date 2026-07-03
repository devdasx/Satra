package dev.satra.wallet.ui.main

import android.content.res.Resources
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.satra.wallet.R
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.db.SatraWalletRepository
import dev.satra.wallet.data.db.WalletAssetRecord
import dev.satra.wallet.data.db.WalletRecord
import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionRecord
import dev.satra.wallet.data.db.WalletTransactionStatus
import dev.satra.wallet.data.sync.evm.EvmProviderRegistry
import dev.satra.wallet.data.sync.evm.EvmSyncCompleteness
import dev.satra.wallet.settings.SatraSettings
import dev.satra.wallet.settings.SatraThemePreference
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal
import java.text.NumberFormat
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
    onThemePreferenceChange: (SatraThemePreference) -> Unit,
    onHapticsEnabledChange: (Boolean) -> Unit,
    onLanguageTagChange: (String) -> Unit,
    onResetComplete: () -> Unit,
) {
    val tabNavController = rememberNavController()
    val tabs = remember { SatraMainTab.entries }
    val backStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: SatraMainTab.Home.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            SatraBottomNavigationBar(
                tabs = tabs,
                currentRoute = currentRoute,
                onTabSelected = { tab ->
                    tabNavController.navigate(tab.route) {
                        popUpTo(tabNavController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
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
                    onSendClick = { tabNavController.navigate(SatraMainRoute.SendAsset) },
                    onReceiveClick = { tabNavController.navigate(SatraMainRoute.Receive) },
                )
            }
            composable(SatraMainTab.Activity.route) {
                SatraActivityScreen(walletRepository = walletRepository)
            }
            composable(SatraMainTab.Markets.route) {
                SatraMainPlaceholderTab(title = stringResource(R.string.main_nav_markets))
            }
            composable(SatraMainTab.Settings.route) {
                SatraSettingsRootScreen(
                    walletRepository = walletRepository,
                    settings = settings,
                    onNavigate = tabNavController::navigate,
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
                    onBack = { tabNavController.popBackStack() },
                    onNavigate = tabNavController::navigate,
                    onHapticsEnabledChange = onHapticsEnabledChange,
                )
            }
            composable(SatraMainRoute.Currency) {
                SatraCurrencyScreen(
                    walletRepository = walletRepository,
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
                    onResetComplete = onResetComplete,
                )
            }
            composable(SatraMainRoute.Receive) {
                SatraReceiveScreen(
                    walletRepository = walletRepository,
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(SatraMainRoute.SendAsset) {
                SatraSendAssetScreen(
                    walletRepository = walletRepository,
                    onBack = { tabNavController.popBackStack() },
                    onAssetSelected = { assetId ->
                        tabNavController.navigate(SatraMainRoute.sendComposer(assetId))
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
                        tabNavController.navigate(SatraMainRoute.sendComposer(assetId))
                    },
                )
            }
            composable(SatraMainRoute.SendComposerPattern) { entry ->
                val assetId = entry.arguments?.getString(SatraMainRoute.ArgAssetId).orEmpty()
                SatraSendComposerScreen(
                    walletRepository = walletRepository,
                    assetId = assetId,
                    onBack = { tabNavController.popBackStack() },
                    onDone = {
                        tabNavController.popBackStack(
                            route = SatraMainTab.Home.route,
                            inclusive = false,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SatraBottomNavigationBar(
    tabs: List<SatraMainTab>,
    currentRoute: String,
    onTabSelected: (SatraMainTab) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
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

@Composable
private fun SatraHomeDashboard(
    walletRepository: SatraWalletRepository,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
) {
    var homeState by remember { mutableStateOf<HomeDashboardState>(HomeDashboardState.Loading) }
    var assetFilterState by remember { mutableStateOf(HomeAssetFilterState()) }
    var assetFilterSheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(walletRepository) {
        val wallet = walletRepository.getPrimaryWallet()
        if (wallet == null) {
            homeState = HomeDashboardState.Content(
                walletName = "",
                status = HomeSyncStatus.Ready,
                totalBalance = formatFiat("0", "USD"),
                currencyCode = "USD",
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
            homeState = latestWallet.toHomeDashboardState(
                walletAssets = walletAssets,
                walletTransactions = walletTransactions,
                status = status,
                chartRange = HomeChartRange.OneWeek,
                nowMillis = System.currentTimeMillis(),
            )
        }

        loadContent(HomeSyncStatus.Syncing)
        runCatching {
            walletRepository.syncWalletData(wallet.walletId)
        }
        loadContent(HomeSyncStatus.Ready)
    }

    val content = when (val state = homeState) {
        HomeDashboardState.Loading -> HomeDashboardState.Content(
            walletName = "",
            status = HomeSyncStatus.Syncing,
            totalBalance = formatFiat("0", "USD"),
            currencyCode = "USD",
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
    val visibleAssets = remember(content.assets, assetFilterState) {
        content.assets.applyHomeAssetFilter(assetFilterState)
    }
    val assetNetworks = remember(content.assets) {
        content.assets
            .distinctBy { asset -> asset.networkId }
            .sortedBy { asset -> asset.network.lowercase() }
            .map { asset -> asset.networkId to asset.network }
    }

    if (assetFilterSheetVisible) {
        HomeAssetFilterSheet(
            networks = assetNetworks,
            filterState = assetFilterState,
            onFilterStateChange = { nextState -> assetFilterState = nextState },
            onDismiss = { assetFilterSheetVisible = false },
        )
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
                SatraHomeHeader(
                    walletName = content.walletName.ifBlank {
                        stringResource(R.string.home_wallet_label)
                    },
                    status = content.status,
                )
                Spacer(modifier = Modifier.height(20.dp))
                HomeBalanceCard(
                    totalBalance = content.totalBalance,
                    currencyCode = content.currencyCode,
                    transactions = content.chartTransactions,
                    initialChartData = content.chartData,
                    onSendClick = onSendClick,
                    onReceiveClick = onReceiveClick,
                )
                Spacer(modifier = Modifier.height(22.dp))
                HomeAssetsHeader(
                    assetCount = visibleAssets.size,
                    totalAssetCount = content.assets.size,
                    isFiltered = assetFilterState.isActive,
                    onFilterClick = { assetFilterSheetVisible = true },
                )
            }
        }
        items(
            items = visibleAssets,
            key = { asset -> asset.assetId },
        ) { asset ->
            HomeAssetListRow(
                asset = asset,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = HomeContentMaxWidth)
                    .padding(horizontal = 20.dp),
            )
        }
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SatraActivityScreen(
    walletRepository: SatraWalletRepository,
) {
    val resources = LocalContext.current.resources
    var activityState by remember {
        mutableStateOf<ActivityScreenState>(ActivityScreenState.Loading)
    }

    LaunchedEffect(walletRepository) {
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
            val (latestWallet, walletTransactions) = coroutineScope {
                val walletDeferred = async { walletRepository.getPrimaryWallet() }
                val transactionsDeferred = async { walletRepository.getWalletTransactions(wallet.walletId) }
                (walletDeferred.await() ?: wallet) to transactionsDeferred.await()
            }
            val transactions = walletTransactions.toActivityRows(latestWallet.localCurrencyCode, resources)
            activityState = ActivityScreenState.Content(
                walletName = latestWallet.walletName,
                status = status,
                transactions = transactions,
                syncedNetworkCount = latestWallet.evmSyncedNetworkCount(),
                error = error,
            )
        }

        loadContent(HomeSyncStatus.Syncing)
        val syncError = runCatching {
            val result = walletRepository.syncEvmWallet(wallet.walletId)
            if (result.networkResults.any { network ->
                    network.error != null ||
                        network.balanceCompleteness != EvmSyncCompleteness.Complete ||
                        network.historyCompleteness != EvmSyncCompleteness.Complete
                }
            ) {
                "Partial EVM sync"
            } else {
                null
            }
        }.getOrElse { error ->
            error.message
        }
        loadContent(HomeSyncStatus.Ready, syncError)
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
                Spacer(modifier = Modifier.height(22.dp))
                ActivityTransactionsHeader(transactionCount = content.transactions.size)
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
        } else {
            items(
                items = content.transactions,
                key = { transaction -> transaction.transactionId },
            ) { transaction ->
                ActivityTransactionCard(
                    transaction = transaction,
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
                    EvmProviderRegistry.supportedNetworkIds.size,
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
private fun ActivityEmptyState(
    isSyncing: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_brand_history),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    if (isSyncing) {
                        R.string.activity_syncing_title
                    } else {
                        R.string.activity_empty_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    if (isSyncing) {
                        R.string.activity_syncing_body
                    } else {
                        R.string.activity_empty_body
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ActivityTransactionCard(
    transaction: ActivityTransactionRow,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
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
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(transaction.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
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
                        text = transaction.amount,
                        style = MaterialTheme.typography.titleMedium,
                        color = when (transaction.direction) {
                            WalletTransactionDirection.Incoming.value -> MaterialTheme.colorScheme.tertiary
                            WalletTransactionDirection.Outgoing.value -> MaterialTheme.colorScheme.onSurface
                            WalletTransactionDirection.Self.value -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = transaction.fiatValue,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = transaction.counterparty,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = transaction.hash,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            if (transaction.fee.isNotBlank()) {
                Text(
                    text = transaction.fee,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SatraHomeHeader(
    walletName: String,
    status: HomeSyncStatus,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(R.string.app_name),
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
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
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
private fun HomeBalanceCard(
    totalBalance: String,
    currencyCode: String,
    transactions: List<WalletTransactionRecord>,
    initialChartData: HomeBalanceChartData,
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
    var selectedPointIndex by remember(chartData.points, selectedRange) {
        mutableStateOf(chartData.points.lastIndex.coerceAtLeast(0))
    }
    val selectedPoint = chartData.points.getOrNull(selectedPointIndex)
    val contentColor = MaterialTheme.colorScheme.inverseOnSurface
    val mutedContentColor = contentColor.copy(alpha = 0.64f)
    val chartPanelColor = contentColor.copy(alpha = 0.08f)
    val chartChangeColor = if (chartData.changeValue.signum() >= 0) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.home_balance_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = mutedContentColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = currencyCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.52f),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "${formatSignedFiat(chartData.changeValue, currencyCode)} · ${formatPercent(chartData.percentChange)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = chartChangeColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = totalBalance,
                style = MaterialTheme.typography.displayLarge,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(chartPanelColor)
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_chart_selected_value),
                            style = MaterialTheme.typography.labelMedium,
                            color = mutedContentColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = selectedPoint?.value?.let { value ->
                                formatFiat(value.toPlainString(), currencyCode)
                            } ?: stringResource(R.string.home_chart_value_placeholder),
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = if (chartData.hasActivity && selectedPoint != null) {
                            formatChartPointTime(selectedPoint.timestampMillis, selectedRange)
                        } else {
                            stringResource(R.string.home_chart_value_placeholder)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = mutedContentColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                HomeBalanceChart(
                    chartData = chartData,
                    selectedPointIndex = selectedPointIndex,
                    onPointSelected = { index -> selectedPointIndex = index },
                    lineColor = contentColor,
                    gridColor = contentColor.copy(alpha = 0.16f),
                    fillColor = contentColor.copy(alpha = 0.11f),
                    selectedColor = contentColor,
                    surfaceColor = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2.45f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                HomeChartRangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = { range -> selectedRange = range },
                    selectedContainerColor = contentColor,
                    selectedContentColor = MaterialTheme.colorScheme.inverseSurface,
                    unselectedContentColor = mutedContentColor,
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomePrimaryActionButton(
                    label = stringResource(R.string.home_action_move),
                    iconRes = R.drawable.ic_brand_move,
                    onClick = onSendClick,
                    modifier = Modifier.weight(1f),
                )
                HomeSecondaryActionButton(
                    label = stringResource(R.string.home_action_receive),
                    iconRes = R.drawable.ic_brand_receive,
                    onClick = onReceiveClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HomePrimaryActionButton(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.inverseOnSurface,
            contentColor = MaterialTheme.colorScheme.inverseSurface,
        ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun HomeSecondaryActionButton(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(100.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.24f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun HomeChartRangeSelector(
    selectedRange: HomeChartRange,
    onRangeSelected: (HomeChartRange) -> Unit,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primary,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HomeChartRange.entries.forEach { range ->
            val selected = selectedRange == range
            if (selected) {
                Button(
                    onClick = { onRangeSelected(range) },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = selectedContainerColor,
                        contentColor = selectedContentColor,
                    ),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Text(
                        text = stringResource(range.labelRes),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            } else {
                TextButton(
                    onClick = { onRangeSelected(range) },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(100.dp),
                ) {
                    Text(
                        text = stringResource(range.labelRes),
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
    selectedPointIndex: Int,
    onPointSelected: (Int) -> Unit,
    lineColor: Color = MaterialTheme.colorScheme.onSurface,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    fillColor: Color = MaterialTheme.colorScheme.primaryContainer,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    surfaceColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier,
) {
    var chartPixelWidth by remember { mutableStateOf(0f) }

    Canvas(
        modifier = modifier
            .onSizeChanged { size -> chartPixelWidth = size.width.toFloat() }
            .pointerInput(chartData.points, chartPixelWidth) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onPointSelected(
                            nearestHomeChartPointIndex(
                                xPosition = offset.x,
                                chartWidth = chartPixelWidth,
                                pointCount = chartData.points.size,
                            ),
                        )
                    },
                    onDrag = { change, _ ->
                        onPointSelected(
                            nearestHomeChartPointIndex(
                                xPosition = change.position.x,
                                chartWidth = chartPixelWidth,
                                pointCount = chartData.points.size,
                            ),
                        )
                    },
                )
            },
    ) {
        val horizontalPadding = 8.dp.toPx()
        val topPadding = 8.dp.toPx()
        val bottomPadding = 12.dp.toPx()
        val chartHeight = size.height - topPadding - bottomPadding
        val chartWidth = size.width - horizontalPadding * 2
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
        val path = Path().apply {
            coordinates.forEachIndexed { index, offset ->
                if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
            }
        }
        val area = Path().apply {
            moveTo(coordinates.first().x, size.height - bottomPadding)
            coordinates.forEach { offset -> lineTo(offset.x, offset.y) }
            lineTo(coordinates.last().x, size.height - bottomPadding)
            close()
        }

        repeat(3) { index ->
            val y = topPadding + chartHeight * (index + 1) / 4f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawPath(path = area, color = fillColor.copy(alpha = 0.42f))
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        val selectedCoordinate = coordinates.getOrNull(selectedPointIndex) ?: coordinates.last()
        drawLine(
            color = selectedColor.copy(alpha = 0.42f),
            start = Offset(selectedCoordinate.x, topPadding),
            end = Offset(selectedCoordinate.x, size.height - bottomPadding),
            strokeWidth = 1.dp.toPx(),
        )
        drawCircle(
            color = selectedColor,
            radius = 5.dp.toPx(),
            center = selectedCoordinate,
        )
        drawCircle(
            color = surfaceColor,
            radius = 2.4.dp.toPx(),
            center = selectedCoordinate,
        )
    }
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(asset.iconRes),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }
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
                text = "${asset.symbol} · ${asset.network}",
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
                text = asset.fiatValue,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = asset.amount,
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
    val direction: String,
    val title: String,
    val subtitle: String,
    val amount: String,
    val fiatValue: String,
    val counterparty: String,
    val hash: String,
    val fee: String,
)

private sealed interface HomeDashboardState {
    data object Loading : HomeDashboardState

    data class Content(
        val walletName: String,
        val status: HomeSyncStatus,
        val totalBalance: String,
        val currencyCode: String,
        val assets: List<HomeAssetRow>,
        val chartTransactions: List<WalletTransactionRecord>,
        val chartData: HomeBalanceChartData,
    ) : HomeDashboardState
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
        total + asset.balanceFiatValue.toBigDecimalOrZero()
    }
    return HomeDashboardState.Content(
        walletName = walletName,
        status = status,
        totalBalance = formatFiat(totalFiat.toPlainString(), localCurrencyCode),
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

private fun List<WalletTransactionRecord>.toActivityRows(
    localCurrencyCode: String,
    resources: Resources,
): List<ActivityTransactionRow> {
    val catalogAssetsById = SupportedAssetCatalog.assets.associateBy { it.assetId }
    val networksById = SupportedAssetCatalog.networks.associateBy { it.networkId }
    return filter { transaction -> transaction.networkId in EvmProviderRegistry.supportedNetworkIds }
        .mapNotNull { transaction ->
            val asset = catalogAssetsById[transaction.assetId] ?: return@mapNotNull null
            val network = networksById[transaction.networkId] ?: return@mapNotNull null
            val status = transaction.status.activityStatusLabel(resources)
            val direction = transaction.direction.activityDirectionLabel(resources)
            ActivityTransactionRow(
                transactionId = transaction.transactionId,
                iconRes = networkIconRes(transaction.networkId),
                direction = transaction.direction,
                title = "$direction ${asset.symbol}",
                subtitle = "${network.displayName} · $status · ${formatActivityTime(transaction.timestamp, resources)}",
                amount = "${transaction.direction.activityAmountPrefix()}${formatCryptoAmount(transaction.amountDecimal)} ${asset.symbol}",
                fiatValue = transaction.fiatValue?.takeIf(String::isNotBlank)?.let {
                    formatFiat(it, localCurrencyCode)
                } ?: formatFiat("0", localCurrencyCode),
                counterparty = transaction.activityCounterparty(resources),
                hash = transaction.transactionHash?.shortHash().orEmpty(),
                fee = transaction.feeDecimal?.takeIf { it.toBigDecimalOrZero() > BigDecimal.ZERO }
                    ?.let { fee ->
                        val feeAsset = transaction.feeAssetId?.let(catalogAssetsById::get)
                        resources.getString(
                            R.string.activity_fee,
                            formatCryptoAmount(fee),
                            feeAsset?.symbol.orEmpty(),
                        ).trim()
                    }
                    .orEmpty(),
            )
        }
}

private fun List<WalletAssetRecord>.toHomeAssetRows(localCurrencyCode: String): List<HomeAssetRow> {
    val catalogAssetsById = SupportedAssetCatalog.assets.associateBy { it.assetId }
    val networksById = SupportedAssetCatalog.networks.associateBy { it.networkId }
    return mapNotNull { walletAsset ->
        val asset = catalogAssetsById[walletAsset.assetId] ?: return@mapNotNull null
        val network = networksById[walletAsset.networkId] ?: return@mapNotNull null
        val balance = walletAsset.balanceDecimal.toBigDecimalOrZero()
        val fiatValue = walletAsset.balanceFiatValue.toBigDecimalOrZero()
        HomeAssetRow(
            assetId = walletAsset.assetId,
            networkId = walletAsset.networkId,
            iconRes = networkIconRes(walletAsset.networkId),
            symbol = asset.symbol,
            name = asset.name,
            network = network.displayName,
            amount = "${formatCryptoAmount(walletAsset.balanceDecimal)} ${asset.symbol}",
            amountValue = balance,
            fiatValue = formatFiat(walletAsset.balanceFiatValue, localCurrencyCode),
            fiatValueAmount = fiatValue,
            hasBalance = balance > BigDecimal.ZERO,
            isNative = asset.assetType == "NATIVE",
        )
    }.applyHomeAssetFilter(HomeAssetFilterState())
}

private fun formatCryptoAmount(value: String): String {
    val decimal = value.toBigDecimalOrZero().stripTrailingZeros()
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

private fun formatSignedFiat(
    value: BigDecimal,
    currencyCode: String,
): String =
    when {
        value.signum() > 0 -> "+${formatFiat(value.abs().toPlainString(), currencyCode)}"
        value.signum() < 0 -> "-${formatFiat(value.abs().toPlainString(), currencyCode)}"
        else -> formatFiat("0", currencyCode)
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

private fun WalletRecord.evmSyncedNetworkCount(): Int =
    runCatching {
        org.json.JSONObject(metadataJson)
            .optJSONObject("evmSync")
            ?.optInt("syncedNetworkCount")
    }.getOrNull() ?: 0

private fun formatActivityTime(
    timestampMillis: Long,
    resources: Resources,
): String =
    if (timestampMillis <= 0L) {
        resources.getString(R.string.activity_status_pending)
    } else {
        ActivityDateFormatter.format(Instant.ofEpochMilli(timestampMillis))
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
    const val SendNetworkPattern = "main/send/network/{$ArgSymbol}"
    const val SendComposerPattern = "main/send/compose/{$ArgAssetId}"
    const val AddressBook = "main/settings/address-book"
    const val Preferences = "main/settings/preferences"
    const val Currency = "main/settings/preferences/currency"
    const val Language = "main/settings/preferences/language"
    const val Appearance = "main/settings/preferences/appearance"
    const val Security = "main/settings/security"
    const val Notifications = "main/settings/notifications"
    const val About = "main/settings/about"
    const val Legal = "main/settings/legal"
    const val DangerZone = "main/settings/danger-zone"

    fun sendNetwork(symbol: String): String =
        "main/send/network/${Uri.encode(symbol)}"

    fun sendComposer(assetId: String): String =
        "main/send/compose/${Uri.encode(assetId)}"
}

private val HomeContentMaxWidth = 720.dp
private val ActivityDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)
        .withZone(ZoneId.systemDefault())
