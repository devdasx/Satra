package dev.satra.wallet.ui.main

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import dev.satra.wallet.data.db.SatraWalletRepository
import dev.satra.wallet.data.db.WalletAddressRecord
import dev.satra.wallet.data.db.WalletAssetRecord
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Locale

@Composable
fun SatraReceiveAssetScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
    onAssetSelected: (String) -> Unit,
    onNetworkRequired: (String) -> Unit,
) {
    var state by remember { mutableStateOf<ReceiveAssetScreenState>(ReceiveAssetScreenState.Loading) }

    LaunchedEffect(walletRepository) {
        state = walletRepository.loadReceiveSnapshot().toAssetSelectionState()
    }

    when (val current = state) {
        ReceiveAssetScreenState.Loading -> ReceiveLoadingScreen(
            title = stringResource(R.string.receive_choose_asset_title),
            onBack = onBack,
        )

        ReceiveAssetScreenState.Empty -> ReceiveEmptyScreen(
            title = stringResource(R.string.receive_choose_asset_title),
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
    symbol: String,
    onBack: () -> Unit,
    onNetworkSelected: (String) -> Unit,
) {
    var state by remember(symbol) { mutableStateOf<ReceiveNetworkScreenState>(ReceiveNetworkScreenState.Loading) }

    LaunchedEffect(walletRepository, symbol) {
        state = walletRepository.loadReceiveSnapshot().toNetworkSelectionState(Uri.decode(symbol))
    }

    when (val current = state) {
        ReceiveNetworkScreenState.Loading -> ReceiveLoadingScreen(
            title = stringResource(R.string.receive_choose_network_title),
            onBack = onBack,
        )

        ReceiveNetworkScreenState.Empty -> ReceiveEmptyScreen(
            title = stringResource(R.string.receive_choose_network_title),
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
    assetId: String,
    onBack: () -> Unit,
) {
    var state by remember(assetId) { mutableStateOf<ReceiveQrScreenState>(ReceiveQrScreenState.Loading) }

    LaunchedEffect(walletRepository, assetId) {
        state = walletRepository.loadReceiveSnapshot().toQrState(Uri.decode(assetId))
    }

    when (val current = state) {
        ReceiveQrScreenState.Loading -> ReceiveLoadingScreen(
            title = stringResource(R.string.receive_qr_title),
            onBack = onBack,
        )

        ReceiveQrScreenState.Empty -> ReceiveEmptyScreen(
            title = stringResource(R.string.receive_qr_title),
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
    ReceiveScaffold(
        title = stringResource(R.string.receive_choose_asset_title),
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                ReceiveHeader(
                    iconRes = R.drawable.ic_brand_receive,
                    title = stringResource(R.string.receive_asset_header_title),
                    body = stringResource(R.string.receive_asset_header_body),
                )
            }
            items(
                items = state.groups,
                key = { group -> group.symbol },
            ) { group ->
                ReceiveAssetGroupRow(
                    group = group,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (group.rows.size > 1) {
                            onNetworkRequired(group.symbol)
                        } else {
                            onAssetSelected(group.rows.first().asset.assetId)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = ReceiveContentMaxWidth)
                        .padding(horizontal = 20.dp),
                )
            }
            item { Spacer(modifier = Modifier.height(22.dp)) }
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
    ReceiveScaffold(
        title = stringResource(R.string.receive_choose_network_title),
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                ReceiveHeader(
                    iconRes = R.drawable.ic_brand_assets,
                    title = stringResource(R.string.receive_network_header_title, state.symbol),
                    body = stringResource(R.string.receive_network_header_body),
                )
            }
            items(
                items = state.rows,
                key = { row -> row.asset.assetId },
            ) { row ->
                ReceiveNetworkRow(
                    row = row,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNetworkSelected(row.asset.assetId)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = ReceiveContentMaxWidth)
                        .padding(horizontal = 20.dp),
                )
            }
            item { Spacer(modifier = Modifier.height(22.dp)) }
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.receive_empty_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
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
    @DrawableRes iconRes: Int,
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = ReceiveContentMaxWidth)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ReceiveSelectableRow(
        iconRes = group.iconRes,
        title = group.name,
        subtitle = if (group.rows.size > 1) {
            stringResource(R.string.receive_asset_network_count, group.rows.size)
        } else {
            group.rows.first().network.displayName
        },
        trailing = group.symbol,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun ReceiveNetworkRow(
    row: ReceiveAssetRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ReceiveSelectableRow(
        iconRes = row.iconRes,
        title = row.network.displayName,
        subtitle = row.network.family.uppercase(Locale.US),
        trailing = row.asset.symbol,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun ReceiveSelectableRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    trailing: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(42.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = trailing,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
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
                Image(
                    painter = painterResource(row.iconRes),
                    contentDescription = null,
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
                    Text(
                        text = row.network.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
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
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = address.address,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 3,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (!address.derivationPath.isNullOrBlank()) {
                Text(
                    text = "${stringResource(R.string.receive_derivation_path)}: ${address.derivationPath}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    clipboardManager.setPrimaryClip(
                        ClipData.newPlainText(row.asset.symbol, address.address),
                    )
                    Toast.makeText(context, R.string.receive_copied, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Text(
                    text = stringResource(R.string.receive_copy_address),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
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
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Text(text = label, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(100.dp),
        ) {
            Text(text = label, fontWeight = FontWeight.Bold, maxLines = 1)
        }
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

private suspend fun SatraWalletRepository.loadReceiveSnapshot(): ReceiveSnapshot =
    coroutineScope {
        val wallet = getPrimaryWallet()
        if (wallet == null) {
            ReceiveSnapshot.Empty
        } else {
            val addressesDeferred = async {
                ensureMnemonicReceiveAddresses(wallet.walletId)
                    .ifEmpty { getWalletAddresses(wallet.walletId) }
            }
            val assetsDeferred = async { getWalletAssets(wallet.walletId) }
            ReceiveSnapshot.Content(
                assets = assetsDeferred.await(),
                addresses = addressesDeferred.await(),
            )
        }
    }

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
                    rows = rows,
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
            iconRes = assetIconRes(asset.symbol, walletAsset.networkId),
        )
    }.sortedWith(
        compareBy<ReceiveAssetRow> { it.asset.name.lowercase(Locale.US) }
            .thenBy { it.network.displayName.lowercase(Locale.US) },
    )
}

private fun List<ReceiveAssetRow>.groupForAssetSelection(): List<ReceiveAssetGroup> =
    groupBy { row -> row.asset.symbol.uppercase(Locale.US) }
        .map { (symbol, rows) ->
            val primary = rows.first()
            ReceiveAssetGroup(
                symbol = symbol,
                name = primary.asset.name,
                rows = rows,
                iconRes = primary.iconRes,
            )
        }
        .sortedBy { group -> group.name.lowercase(Locale.US) }

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
    @DrawableRes val iconRes: Int,
)

private data class ReceiveAssetRow(
    val asset: SupportedAsset,
    val network: SupportedNetwork,
    val addresses: List<WalletAddressRecord>,
    @DrawableRes val iconRes: Int,
)

private val ReceiveContentMaxWidth = 720.dp
private const val QR_SIZE = 512
