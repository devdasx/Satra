package dev.satra.wallet.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.CircleShape
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

@Composable
fun SatraReceiveScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
) {
    var state by remember { mutableStateOf<ReceiveScreenState>(ReceiveScreenState.Loading) }

    LaunchedEffect(walletRepository) {
        val wallet = walletRepository.getPrimaryWallet()
        if (wallet == null) {
            state = ReceiveScreenState.Empty
            return@LaunchedEffect
        }
        val (addresses, walletAssets) = coroutineScope {
            val addressesDeferred = async {
                walletRepository.ensureMnemonicReceiveAddresses(wallet.walletId)
                    .ifEmpty { walletRepository.getWalletAddresses(wallet.walletId) }
            }
            val assetsDeferred = async {
                walletRepository.getWalletAssets(wallet.walletId)
            }
            addressesDeferred.await() to assetsDeferred.await()
        }
        val rows = walletAssets.toReceiveAssetRows(addresses)
        state = if (rows.isEmpty()) ReceiveScreenState.Empty else ReceiveScreenState.Content(rows)
    }

    when (val current = state) {
        ReceiveScreenState.Loading -> ReceiveLoadingScreen(onBack = onBack)
        ReceiveScreenState.Empty -> ReceiveEmptyScreen(onBack = onBack)
        is ReceiveScreenState.Content -> ReceiveContentScreen(
            rows = current.rows,
            onBack = onBack,
        )
    }
}

@Composable
private fun ReceiveLoadingScreen(onBack: () -> Unit) {
    ReceiveScaffold(onBack = onBack) {
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
                )
            }
        }
    }
}

@Composable
private fun ReceiveEmptyScreen(onBack: () -> Unit) {
    ReceiveScaffold(onBack = onBack) {
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
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.receive_empty_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReceiveContentScreen(
    rows: List<ReceiveAssetRow>,
    onBack: () -> Unit,
) {
    var selectedAssetId by remember(rows) { mutableStateOf(rows.first().asset.assetId) }
    val selectedRow = rows.firstOrNull { it.asset.assetId == selectedAssetId } ?: rows.first()
    var selectedAddressId by remember(selectedRow.asset.assetId) {
        mutableStateOf(selectedRow.addresses.first().addressId)
    }
    val selectedAddress = selectedRow.addresses.firstOrNull { it.addressId == selectedAddressId }
        ?: selectedRow.addresses.first()
    val haptic = LocalHapticFeedback.current

    ReceiveScaffold(onBack = onBack) {
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
                        row = selectedRow,
                        address = selectedAddress,
                        allAddresses = selectedRow.addresses,
                        onAddressSelected = { address ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedAddressId = address.addressId
                        },
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    ReceiveAssetHeader(assetCount = rows.size)
                }
            }
            items(
                items = rows,
                key = { row -> row.asset.assetId },
            ) { row ->
                ReceiveAssetListRow(
                    row = row,
                    selected = row.asset.assetId == selectedRow.asset.assetId,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedAssetId = row.asset.assetId
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = ReceiveContentMaxWidth)
                        .padding(horizontal = 20.dp),
                )
            }
            item {
                Spacer(modifier = Modifier.height(22.dp))
            }
        }
    }
}

@Composable
private fun ReceiveScaffold(
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
                text = stringResource(R.string.receive_title),
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
                ChainIcon(
                    iconRes = row.iconRes,
                    modifier = Modifier.size(52.dp),
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

@Composable
private fun ReceiveAssetHeader(assetCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.receive_assets_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.receive_assets_count, assetCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ReceiveAssetListRow(
    row: ReceiveAssetRow,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChainIcon(
            iconRes = row.iconRes,
            modifier = Modifier.size(44.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.asset.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${row.asset.symbol} · ${row.network.displayName}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = row.addresses.first().label ?: row.addresses.first().derivationPath.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChainIcon(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun List<WalletAssetRecord>.toReceiveAssetRows(
    addresses: List<WalletAddressRecord>,
): List<ReceiveAssetRow> {
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
    return mapNotNull { walletAsset ->
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
        compareBy<ReceiveAssetRow> { it.network.displayName }
            .thenByDescending { it.asset.assetType == "NATIVE" }
            .thenBy { it.asset.symbol },
    )
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

private sealed interface ReceiveScreenState {
    data object Loading : ReceiveScreenState
    data object Empty : ReceiveScreenState
    data class Content(val rows: List<ReceiveAssetRow>) : ReceiveScreenState
}

private data class ReceiveAssetRow(
    val asset: SupportedAsset,
    val network: SupportedNetwork,
    val addresses: List<WalletAddressRecord>,
    @DrawableRes val iconRes: Int,
)

private val ReceiveContentMaxWidth = 720.dp
private const val QR_SIZE = 512
