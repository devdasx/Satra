package dev.satra.wallet.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
fun SatraMainScreen(
    walletRepository: SatraWalletRepository,
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
                SatraHomeDashboard(walletRepository = walletRepository)
            }
            composable(SatraMainTab.Activity.route) {
                SatraMainPlaceholderTab(title = stringResource(R.string.main_nav_activity))
            }
            composable(SatraMainTab.Markets.route) {
                SatraMainPlaceholderTab(title = stringResource(R.string.main_nav_markets))
            }
            composable(SatraMainTab.Settings.route) {
                SatraMainPlaceholderTab(title = stringResource(R.string.main_nav_settings))
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
) {
    var homeState by remember { mutableStateOf<HomeDashboardState>(HomeDashboardState.Loading) }

    LaunchedEffect(walletRepository) {
        val wallet = walletRepository.getPrimaryWallet()
        if (wallet == null) {
            homeState = HomeDashboardState.Content(
                walletName = "",
                status = HomeSyncStatus.Ready,
                totalBalance = formatFiat("0", "USD"),
                currencyCode = "USD",
                assets = emptyList(),
            )
            return@LaunchedEffect
        }

        suspend fun loadContent(status: HomeSyncStatus) {
            val walletAssets = walletRepository.getWalletAssets(wallet.walletId)
            homeState = wallet.toHomeDashboardState(
                walletAssets = walletAssets,
                status = status,
            )
        }

        loadContent(HomeSyncStatus.Syncing)
        runCatching {
            walletRepository.syncEvmWallet(wallet.walletId)
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
        )
        is HomeDashboardState.Content -> state
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
                )
                Spacer(modifier = Modifier.height(14.dp))
                HomeChartCard(totalBalance = content.totalBalance)
                Spacer(modifier = Modifier.height(22.dp))
                HomeAssetsHeader(assetCount = content.assets.size)
            }
        }
        items(
            items = content.assets,
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
) {
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
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = currencyCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.58f),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(R.string.home_balance_change_placeholder),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = totalBalance,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomePrimaryActionButton(
                    label = stringResource(R.string.home_action_move),
                    iconRes = R.drawable.ic_brand_move,
                    modifier = Modifier.weight(1f),
                )
                HomeSecondaryActionButton(
                    label = stringResource(R.string.home_action_receive),
                    iconRes = R.drawable.ic_brand_receive,
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
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = {},
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
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = {},
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
private fun HomeChartCard(
    totalBalance: String,
) {
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.home_chart_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.home_chart_range),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = totalBalance,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            HomeBalanceChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.35f),
            )
        }
    }
}

@Composable
private fun HomeBalanceChart(
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val fillColor = MaterialTheme.colorScheme.primaryContainer
    val points = remember {
        listOf(0.36f, 0.40f, 0.38f, 0.44f, 0.43f, 0.52f, 0.49f, 0.58f, 0.55f, 0.64f, 0.68f)
    }

    Canvas(modifier = modifier) {
        val horizontalPadding = 8.dp.toPx()
        val topPadding = 8.dp.toPx()
        val bottomPadding = 12.dp.toPx()
        val chartHeight = size.height - topPadding - bottomPadding
        val chartWidth = size.width - horizontalPadding * 2
        val coordinates = points.mapIndexed { index, value ->
            val x = horizontalPadding + chartWidth * (index.toFloat() / (points.lastIndex.toFloat()))
            val y = topPadding + chartHeight * (1f - value)
            Offset(x, y)
        }
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
        drawCircle(
            color = lineColor,
            radius = 4.dp.toPx(),
            center = coordinates.last(),
        )
    }
}

@Composable
private fun HomeAssetsHeader(
    assetCount: Int,
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
        Text(
            text = stringResource(R.string.home_assets_count, assetCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
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

private data class HomeAssetRow(
    val assetId: String,
    @DrawableRes val iconRes: Int,
    val symbol: String,
    val name: String,
    val network: String,
    val amount: String,
    val fiatValue: String,
    val hasBalance: Boolean,
    val isNative: Boolean,
)

private sealed interface HomeDashboardState {
    data object Loading : HomeDashboardState

    data class Content(
        val walletName: String,
        val status: HomeSyncStatus,
        val totalBalance: String,
        val currencyCode: String,
        val assets: List<HomeAssetRow>,
    ) : HomeDashboardState
}

private enum class HomeSyncStatus(@StringRes val labelRes: Int) {
    Ready(R.string.home_wallet_status_ready),
    Syncing(R.string.home_wallet_status_syncing),
}

private fun WalletRecord.toHomeDashboardState(
    walletAssets: List<WalletAssetRecord>,
    status: HomeSyncStatus,
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
    )
}

private fun List<WalletAssetRecord>.toHomeAssetRows(localCurrencyCode: String): List<HomeAssetRow> {
    val catalogAssetsById = SupportedAssetCatalog.assets.associateBy { it.assetId }
    val networksById = SupportedAssetCatalog.networks.associateBy { it.networkId }
    return mapNotNull { walletAsset ->
        val asset = catalogAssetsById[walletAsset.assetId] ?: return@mapNotNull null
        val network = networksById[walletAsset.networkId] ?: return@mapNotNull null
        val balance = walletAsset.balanceDecimal.toBigDecimalOrZero()
        HomeAssetRow(
            assetId = walletAsset.assetId,
            iconRes = networkIconRes(walletAsset.networkId),
            symbol = asset.symbol,
            name = asset.name,
            network = network.displayName,
            amount = "${formatCryptoAmount(walletAsset.balanceDecimal)} ${asset.symbol}",
            fiatValue = formatFiat(walletAsset.balanceFiatValue, localCurrencyCode),
            hasBalance = balance > BigDecimal.ZERO,
            isNative = asset.assetType == "NATIVE",
        )
    }.sortedWith(
        compareByDescending<HomeAssetRow> { it.hasBalance }
            .thenByDescending { it.isNative }
            .thenBy { it.network }
            .thenBy { it.symbol },
    )
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

private fun String.toBigDecimalOrZero(): BigDecimal =
    runCatching { BigDecimal(this) }.getOrDefault(BigDecimal.ZERO)

@DrawableRes
private fun networkIconRes(networkId: String): Int =
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

private val HomeContentMaxWidth = 720.dp
