package dev.satra.wallet.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.satra.wallet.R
import dev.satra.wallet.data.assets.SupportedAsset
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.assets.SupportedNetwork
import dev.satra.wallet.data.db.DEFAULT_LOCAL_CURRENCY_CODE
import dev.satra.wallet.data.db.SatraWalletRepository
import dev.satra.wallet.data.db.WalletAddressRecord
import dev.satra.wallet.data.db.WalletAssetRecord
import dev.satra.wallet.ui.components.SatraButton
import dev.satra.wallet.ui.components.SatraButtonDefaults
import dev.satra.wallet.ui.components.SatraButtonVariant
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
fun SatraReceiveAssetScreen(
    walletRepository: SatraWalletRepository,
    initialWalletSnapshot: SatraMainWalletSnapshot?,
    onWalletSnapshotLoaded: (SatraMainWalletSnapshot) -> Unit,
    onBack: () -> Unit,
    onAssetSelected: (String) -> Unit,
    onNetworkRequired: (String) -> Unit,
) {
    var state by remember {
        mutableStateOf(
            initialWalletSnapshot?.toReceiveSnapshot()?.toAssetSelectionState()
                ?: ReceiveAssetScreenState.Loading,
        )
    }

    LaunchedEffect(initialWalletSnapshot) {
        initialWalletSnapshot?.let { snapshot ->
            state = snapshot.toReceiveSnapshot().toAssetSelectionState()
        }
    }

    LaunchedEffect(walletRepository, initialWalletSnapshot?.walletId) {
        if (initialWalletSnapshot.hasReceiveData()) return@LaunchedEffect
        val snapshot = walletRepository.loadMainWalletSnapshot(
            includePrivateKeys = false,
            includeTransactions = false,
        )
        onWalletSnapshotLoaded(snapshot)
        state = snapshot.toReceiveSnapshot().toAssetSelectionState()
    }

    when (val current = state) {
        ReceiveAssetScreenState.Loading -> ReceiveLoadingScreen(
            title = stringResource(R.string.receive_choose_asset_title),
            onBack = onBack,
        )

        ReceiveAssetScreenState.Empty -> ReceiveEmptyScreen(
            title = stringResource(R.string.receive_choose_asset_title),
            emptyTitle = stringResource(R.string.receive_empty_title),
            body = stringResource(R.string.receive_empty_body),
            onBack = onBack,
        )

        is ReceiveAssetScreenState.Content -> ReceiveAssetSelectionContent(
            state = current,
            onBack = onBack,
            onAssetSelected = onAssetSelected,
            onNetworkRequired = onNetworkRequired,
        )
    }
}

@Composable
fun SatraReceiveNetworkScreen(
    walletRepository: SatraWalletRepository,
    initialWalletSnapshot: SatraMainWalletSnapshot?,
    onWalletSnapshotLoaded: (SatraMainWalletSnapshot) -> Unit,
    symbol: String,
    onBack: () -> Unit,
    onNetworkSelected: (String) -> Unit,
) {
    var state by remember(symbol) {
        mutableStateOf(
            initialWalletSnapshot?.toReceiveSnapshot()?.toNetworkSelectionState(Uri.decode(symbol))
                ?: ReceiveNetworkScreenState.Loading,
        )
    }

    LaunchedEffect(initialWalletSnapshot, symbol) {
        initialWalletSnapshot?.let { snapshot ->
            state = snapshot.toReceiveSnapshot().toNetworkSelectionState(Uri.decode(symbol))
        }
    }

    LaunchedEffect(walletRepository, symbol, initialWalletSnapshot?.walletId) {
        if (initialWalletSnapshot.hasReceiveData()) return@LaunchedEffect
        val snapshot = walletRepository.loadMainWalletSnapshot(
            includePrivateKeys = false,
            includeTransactions = false,
        )
        onWalletSnapshotLoaded(snapshot)
        state = snapshot.toReceiveSnapshot().toNetworkSelectionState(Uri.decode(symbol))
    }

    when (val current = state) {
        ReceiveNetworkScreenState.Loading -> ReceiveLoadingScreen(
            title = stringResource(R.string.receive_choose_network_title),
            onBack = onBack,
        )

        ReceiveNetworkScreenState.Empty -> ReceiveEmptyScreen(
            title = stringResource(R.string.receive_choose_network_title),
            emptyTitle = stringResource(R.string.receive_network_empty_title),
            body = stringResource(R.string.receive_network_empty_body),
            onBack = onBack,
        )

        is ReceiveNetworkScreenState.Content -> ReceiveNetworkSelectionContent(
            state = current,
            onBack = onBack,
            onNetworkSelected = onNetworkSelected,
        )
    }
}

@Composable
fun SatraReceiveQrScreen(
    walletRepository: SatraWalletRepository,
    initialWalletSnapshot: SatraMainWalletSnapshot?,
    onWalletSnapshotLoaded: (SatraMainWalletSnapshot) -> Unit,
    assetId: String,
    onBack: () -> Unit,
) {
    var state by remember(assetId) {
        mutableStateOf(
            initialWalletSnapshot?.toReceiveSnapshot()?.toQrState(Uri.decode(assetId))
                ?: ReceiveQrScreenState.Loading,
        )
    }

    LaunchedEffect(initialWalletSnapshot, assetId) {
        initialWalletSnapshot?.let { snapshot ->
            state = snapshot.toReceiveSnapshot().toQrState(Uri.decode(assetId))
        }
    }

    LaunchedEffect(walletRepository, assetId, initialWalletSnapshot?.walletId) {
        if (initialWalletSnapshot.hasReceiveData()) return@LaunchedEffect
        val snapshot = walletRepository.loadMainWalletSnapshot(
            includePrivateKeys = false,
            includeTransactions = false,
        )
        onWalletSnapshotLoaded(snapshot)
        state = snapshot.toReceiveSnapshot().toQrState(Uri.decode(assetId))
    }

    when (val current = state) {
        ReceiveQrScreenState.Loading -> ReceiveLoadingScreen(
            title = stringResource(R.string.receive_qr_title),
            onBack = onBack,
        )

        ReceiveQrScreenState.Empty -> ReceiveEmptyScreen(
            title = stringResource(R.string.receive_qr_title),
            emptyTitle = stringResource(R.string.receive_asset_empty_title),
            body = stringResource(R.string.receive_asset_empty_body),
            onBack = onBack,
        )

        is ReceiveQrScreenState.Content -> ReceiveQrContent(
            state = current,
            onBack = onBack,
        )
    }
}

@Composable
private fun ReceiveAssetSelectionContent(
    state: ReceiveAssetScreenState.Content,
    onBack: () -> Unit,
    onAssetSelected: (String) -> Unit,
    onNetworkRequired: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var query by rememberSaveable { mutableStateOf("") }
    val filteredGroups = remember(state.groups, query) {
        state.groups.filterByQuery(query)
    }
    val fundedGroups = filteredGroups.filter { group -> group.totalFiat > BigDecimal.ZERO || group.totalBalance > BigDecimal.ZERO }
    val unfundedGroups = filteredGroups.filterNot { group -> group in fundedGroups }

    SatraChooseAssetScaffold(
        title = stringResource(R.string.receive_choose_asset_title),
        onBack = onBack,
    ) {
        item {
            ChooseAssetSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.send_search_asset_placeholder),
            )
        }
        if (filteredGroups.isEmpty()) {
            item { ChooseAssetEmptySearchNote() }
        } else {
            if (fundedGroups.isNotEmpty()) {
                item { ChooseAssetSectionHeader(title = stringResource(R.string.send_section_your_assets)) }
                items(
                    items = fundedGroups,
                    key = { group -> "funded-${group.symbol}" },
                ) { group ->
                    ReceiveAssetGroupRow(
                        group = group,
                        showSecondaryAmount = true,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (group.rows.size > 1) {
                                onNetworkRequired(group.symbol)
                            } else {
                                onAssetSelected(group.rows.first().asset.assetId)
                            }
                        },
                    )
                }
            }
            if (unfundedGroups.isNotEmpty()) {
                item { ChooseAssetSectionHeader(title = stringResource(R.string.send_section_all_assets)) }
                items(
                    items = unfundedGroups,
                    key = { group -> "all-${group.symbol}" },
                ) { group ->
                    ReceiveAssetGroupRow(
                        group = group,
                        showSecondaryAmount = false,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (group.rows.size > 1) {
                                onNetworkRequired(group.symbol)
                            } else {
                                onAssetSelected(group.rows.first().asset.assetId)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiveNetworkSelectionContent(
    state: ReceiveNetworkScreenState.Content,
    onBack: () -> Unit,
    onNetworkSelected: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    SatraChooseAssetScaffold(
        title = stringResource(R.string.receive_choose_network_title),
        onBack = onBack,
    ) {
        item {
            ChooseNetworkContextLine(
                symbol = state.symbol,
                networkCount = state.rows.size,
                iconRes = assetIconRes(state.symbol),
            )
        }
        items(
            items = state.rows,
            key = { row -> row.asset.assetId },
        ) { row ->
            ChooseNetworkRow(
                networkName = row.network.displayName,
                standard = row.networkStandardLabel(),
                primaryAmount = formatReceiveCryptoAmount(row.balanceAmount),
                secondaryAmount = if (row.fiatAmount > BigDecimal.ZERO) {
                    row.fiatFormatted
                } else {
                    stringResource(R.string.send_network_empty_value)
                },
                iconRes = networkIconRes(row.network.networkId),
                enabled = true,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNetworkSelected(row.asset.assetId)
                },
            )
        }
    }
}

@Composable
private fun ReceiveQrContent(
    state: ReceiveQrScreenState.Content,
    onBack: () -> Unit,
) {
    var selectedAddressId by remember(state.row.asset.assetId) {
        mutableStateOf(state.row.addresses.first().addressId)
    }
    val selectedAddress = state.row.addresses.firstOrNull { it.addressId == selectedAddressId }
        ?: state.row.addresses.first()
    val haptic = LocalHapticFeedback.current

    ReceiveScaffold(
        title = stringResource(R.string.receive_qr_title),
        onBack = onBack,
    ) {
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
                        .widthIn(max = ReceiveContentMaxWidth)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    ReceiveAddressCard(
                        row = state.row,
                        address = selectedAddress,
                        allAddresses = state.row.addresses,
                        onAddressSelected = { address ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedAddressId = address.addressId
                        },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(22.dp)) }
        }
    }
}

@Composable
private fun ReceiveLoadingScreen(
    title: String,
    onBack: () -> Unit,
) {
    ReceiveScaffold(title = title, onBack = onBack) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.receive_loading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ReceiveEmptyScreen(
    title: String,
    emptyTitle: String,
    body: String,
    onBack: () -> Unit,
) {
    ReceiveScaffold(title = title, onBack = onBack) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            SatraEmptyState(
                title = emptyTitle,
                body = body,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReceiveScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .padding(horizontal = 20.dp),
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
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content()
    }
}

@Composable
private fun ReceiveHeader(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = ReceiveContentMaxWidth)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun ReceiveAssetGroupRow(
    group: ReceiveAssetGroup,
    showSecondaryAmount: Boolean,
    onClick: () -> Unit,
) {
    ChooseAssetRow(
        symbol = group.symbol,
        name = group.name,
        networkCount = group.rows.size,
        primaryAmount = group.totalBalanceValueFormatted,
        secondaryAmount = group.totalFiatFormatted,
        showSecondaryAmount = showSecondaryAmount,
        iconRes = group.iconRes,
        onClick = onClick,
        enabled = true,
    )
}

@Composable
private fun ReceiveAddressCard(
    row: ReceiveAssetRow,
    address: WalletAddressRecord,
    allAddresses: List<WalletAddressRecord>,
    onAddressSelected: (WalletAddressRecord) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    val haptic = LocalHapticFeedback.current
    val shareSubject = stringResource(R.string.receive_share_subject, row.asset.symbol)
    val shareChooserTitle = stringResource(R.string.receive_share_address_title)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SatraCryptoIcon(
                    iconRes = row.iconRes,
                    modifier = Modifier.size(58.dp),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.receive_header_title, row.asset.symbol),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ReceiveNetworkPill(network = row.network)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(
                    R.string.receive_header_body,
                    row.asset.symbol,
                    row.network.displayName,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                ReceiveQrCode(
                    value = address.address,
                    modifier = Modifier.size(210.dp),
                )
            }
            if (allAddresses.size > 1) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.receive_address_variant),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allAddresses, key = { it.addressId }) { candidate ->
                        AddressVariantButton(
                            address = candidate,
                            selected = candidate.addressId == address.addressId,
                            onClick = { onAddressSelected(candidate) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.receive_address_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = address.address,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SatraButton(
                    text = stringResource(R.string.receive_copy_address),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        clipboardManager.setPrimaryClip(
                            ClipData.newPlainText(row.asset.symbol, address.address),
                        )
                        Toast.makeText(context, R.string.receive_copied, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f),
                    height = SatraButtonDefaults.CompactHeight,
                )
                SatraButton(
                    text = stringResource(R.string.receive_share_address),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                            putExtra(Intent.EXTRA_TEXT, address.address)
                        }
                        context.startActivity(
                            Intent.createChooser(
                                sendIntent,
                                shareChooserTitle,
                            ),
                        )
                    },
                    modifier = Modifier
                        .weight(1f),
                    variant = SatraButtonVariant.Secondary,
                    height = SatraButtonDefaults.CompactHeight,
                )
            }
        }
    }
}

@Composable
private fun ReceiveNetworkPill(network: SupportedNetwork) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SatraCryptoIcon(
            iconRes = networkIconRes(network.networkId),
            modifier = Modifier.size(18.dp),
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = network.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddressVariantButton(
    address: WalletAddressRecord,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = address.label ?: address.derivationPath ?: address.address
    if (selected) {
        SatraButton(
            text = label,
            onClick = onClick,
            height = 40.dp,
        )
    } else {
        SatraButton(
            text = label,
            onClick = onClick,
            variant = SatraButtonVariant.Secondary,
            height = 40.dp,
        )
    }
}

@Composable
private fun ReceiveQrCode(
    value: String,
    modifier: Modifier = Modifier,
) {
    val qrBitmap = remember(value) { createQrBitmap(value) }
    Image(
        bitmap = qrBitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.receive_qr_description),
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

private fun SatraMainWalletSnapshot?.hasReceiveData(): Boolean =
    this?.wallet != null &&
        assets.isNotEmpty() &&
        addresses.any { address -> address.addressType == "receive" || address.addressType == "watch_only" }

private fun SatraMainWalletSnapshot.toReceiveSnapshot(): ReceiveSnapshot =
    wallet?.let { walletRecord ->
        ReceiveSnapshot.Content(
            assets = assets,
            addresses = addresses,
            localCurrencyCode = walletRecord.localCurrencyCode,
        )
    } ?: ReceiveSnapshot.Empty

private fun ReceiveSnapshot.toAssetSelectionState(): ReceiveAssetScreenState =
    when (this) {
        ReceiveSnapshot.Empty -> ReceiveAssetScreenState.Empty
        is ReceiveSnapshot.Content -> {
            val rows = toReceiveAssetRows()
            if (rows.isEmpty()) {
                ReceiveAssetScreenState.Empty
            } else {
                ReceiveAssetScreenState.Content(rows.groupForAssetSelection())
            }
        }
    }

private fun ReceiveSnapshot.toNetworkSelectionState(symbol: String): ReceiveNetworkScreenState =
    when (this) {
        ReceiveSnapshot.Empty -> ReceiveNetworkScreenState.Empty
        is ReceiveSnapshot.Content -> {
            val rows = toReceiveAssetRows()
                .filter { row -> row.asset.symbol.equals(symbol, ignoreCase = true) }
            if (rows.isEmpty()) {
                ReceiveNetworkScreenState.Empty
            } else {
                ReceiveNetworkScreenState.Content(
                    symbol = symbol.uppercase(Locale.US),
                    rows = rows.sortedByNetworkValue(),
                )
            }
        }
    }

private fun ReceiveSnapshot.toQrState(assetId: String): ReceiveQrScreenState =
    when (this) {
        ReceiveSnapshot.Empty -> ReceiveQrScreenState.Empty
        is ReceiveSnapshot.Content -> {
            val row = toReceiveAssetRows().firstOrNull { candidate -> candidate.asset.assetId == assetId }
            if (row == null) {
                ReceiveQrScreenState.Empty
            } else {
                ReceiveQrScreenState.Content(row = row)
            }
        }
    }

private fun ReceiveSnapshot.Content.toReceiveAssetRows(): List<ReceiveAssetRow> {
    val catalogAssetsById = SupportedAssetCatalog.assets.associateBy { it.assetId }
    val networksById = SupportedAssetCatalog.networks.associateBy { it.networkId }
    val addressesByNetwork = addresses
        .filter { it.addressType == "receive" || it.addressType == "watch_only" }
        .groupBy { it.networkId }
        .mapValues { entry ->
            entry.value.sortedWith(
                compareByDescending<WalletAddressRecord> { it.isPrimary }
                    .thenBy { it.addressIndex ?: Int.MAX_VALUE }
                    .thenBy { it.label.orEmpty() },
            )
        }
    return assets.mapNotNull { walletAsset ->
        val asset = catalogAssetsById[walletAsset.assetId] ?: return@mapNotNull null
        val network = networksById[walletAsset.networkId] ?: return@mapNotNull null
        val networkAddresses = addressesByNetwork[walletAsset.networkId].orEmpty()
        if (networkAddresses.isEmpty()) return@mapNotNull null
        ReceiveAssetRow(
            asset = asset,
            network = network,
            addresses = networkAddresses,
            balanceAmount = walletAsset.balanceDecimal.toBigDecimalOrZero(),
            fiatAmount = walletAsset.balanceFiatValue.toBigDecimalOrZero(),
            balanceFormatted = "${formatReceiveCryptoAmount(walletAsset.balanceDecimal.toBigDecimalOrZero())} ${asset.symbol}",
            fiatFormatted = formatReceiveFiat(walletAsset.balanceFiatValue, localCurrencyCode),
            localCurrencyCode = localCurrencyCode,
            iconRes = assetIconRes(asset.symbol),
        )
    }.sortedWith(
        compareByDescending<ReceiveAssetRow> { it.fiatAmount }
            .thenByDescending { it.balanceAmount }
            .thenBy { it.asset.name.lowercase(Locale.US) }
            .thenBy { it.network.displayName.lowercase(Locale.US) },
    )
}

private fun List<ReceiveAssetRow>.groupForAssetSelection(): List<ReceiveAssetGroup> =
    groupBy { row -> row.asset.symbol.uppercase(Locale.US) }
        .map { (symbol, rows) ->
            val primary = rows.maxWith(
                compareBy<ReceiveAssetRow> { row -> row.fiatAmount }
                    .thenBy { row -> row.balanceAmount },
            )
            val totalFiat = rows.fold(BigDecimal.ZERO) { total, row -> total + row.fiatAmount }
            val totalBalance = rows.fold(BigDecimal.ZERO) { total, row -> total + row.balanceAmount }
            val localCurrencyCode = rows.firstOrNull()?.localCurrencyCode ?: DEFAULT_LOCAL_CURRENCY_CODE
            ReceiveAssetGroup(
                symbol = symbol,
                name = primary.asset.name,
                rows = rows,
                totalFiat = totalFiat,
                totalBalance = totalBalance,
                totalFiatFormatted = formatReceiveFiat(totalFiat.toPlainString(), localCurrencyCode),
                totalBalanceValueFormatted = formatReceiveCryptoAmount(totalBalance),
                iconRes = primary.iconRes,
            )
        }
        .sortedWith(
            compareByDescending<ReceiveAssetGroup> { group -> group.totalFiat }
                .thenByDescending { group -> group.totalBalance }
                .thenBy { group -> group.name.lowercase(Locale.US) },
        )

private fun List<ReceiveAssetGroup>.filterByQuery(query: String): List<ReceiveAssetGroup> {
    val normalized = query.trim().lowercase(Locale.US)
    if (normalized.isBlank()) return this
    return filter { group ->
        group.name.lowercase(Locale.US).contains(normalized) ||
            group.symbol.lowercase(Locale.US).contains(normalized)
    }
}

private fun List<ReceiveAssetRow>.sortedByNetworkValue(): List<ReceiveAssetRow> =
    sortedWith(
        compareByDescending<ReceiveAssetRow> { row -> row.fiatAmount }
            .thenByDescending { row -> row.balanceAmount }
            .thenBy { row -> row.network.displayName.lowercase(Locale.US) },
    )

private fun ReceiveAssetRow.networkStandardLabel(): String =
    asset.tokenStandard
        ?: network.tokenStandard
        ?: if (asset.assetType == "NATIVE") "Native" else network.family.uppercase(Locale.US)

private fun String.toBigDecimalOrZero(): BigDecimal =
    runCatching { BigDecimal(this) }.getOrDefault(BigDecimal.ZERO)

private fun formatReceiveCryptoAmount(value: BigDecimal): String {
    val decimal = value
        .setScale(CRYPTO_DISPLAY_DECIMALS, RoundingMode.DOWN)
        .stripTrailingZeros()
    return if (decimal.compareTo(BigDecimal.ZERO) == 0) "0" else decimal.toPlainString()
}

private fun formatReceiveFiat(
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

private fun createQrBitmap(value: String): Bitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints)
    val bitmap = Bitmap.createBitmap(QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888)
    for (x in 0 until QR_SIZE) {
        for (y in 0 until QR_SIZE) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}

private sealed interface ReceiveSnapshot {
    data object Empty : ReceiveSnapshot
    data class Content(
        val assets: List<WalletAssetRecord>,
        val addresses: List<WalletAddressRecord>,
        val localCurrencyCode: String,
    ) : ReceiveSnapshot
}

private sealed interface ReceiveAssetScreenState {
    data object Loading : ReceiveAssetScreenState
    data object Empty : ReceiveAssetScreenState
    data class Content(val groups: List<ReceiveAssetGroup>) : ReceiveAssetScreenState
}

private sealed interface ReceiveNetworkScreenState {
    data object Loading : ReceiveNetworkScreenState
    data object Empty : ReceiveNetworkScreenState
    data class Content(
        val symbol: String,
        val rows: List<ReceiveAssetRow>,
    ) : ReceiveNetworkScreenState
}

private sealed interface ReceiveQrScreenState {
    data object Loading : ReceiveQrScreenState
    data object Empty : ReceiveQrScreenState
    data class Content(val row: ReceiveAssetRow) : ReceiveQrScreenState
}

private data class ReceiveAssetGroup(
    val symbol: String,
    val name: String,
    val rows: List<ReceiveAssetRow>,
    val totalFiat: BigDecimal,
    val totalBalance: BigDecimal,
    val totalFiatFormatted: String,
    val totalBalanceValueFormatted: String,
    @DrawableRes val iconRes: Int,
)

private data class ReceiveAssetRow(
    val asset: SupportedAsset,
    val network: SupportedNetwork,
    val addresses: List<WalletAddressRecord>,
    val balanceAmount: BigDecimal,
    val fiatAmount: BigDecimal,
    val balanceFormatted: String,
    val fiatFormatted: String,
    val localCurrencyCode: String,
    @DrawableRes val iconRes: Int,
)

private val ReceiveContentMaxWidth = 720.dp
private const val CRYPTO_DISPLAY_DECIMALS = 8
private const val QR_SIZE = 512
