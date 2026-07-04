package dev.satra.wallet.ui.main

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.satra.wallet.R
import dev.satra.wallet.data.assets.SupportedAsset
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.assets.SupportedNetwork
import dev.satra.wallet.data.db.SatraPendingSendRequest
import dev.satra.wallet.data.db.SatraWalletRepository
import dev.satra.wallet.data.db.WalletAddressRecord
import dev.satra.wallet.data.db.WalletAssetRecord
import dev.satra.wallet.data.db.WalletPrivateKeyRecord
import dev.satra.wallet.data.db.WalletRecord
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
fun SatraSendAssetScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
    onAssetSelected: (String) -> Unit,
    onNetworkRequired: (String) -> Unit,
) {
    var state by remember { mutableStateOf<SendAssetScreenState>(SendAssetScreenState.Loading) }

    LaunchedEffect(walletRepository) {
        state = walletRepository.loadSendSnapshot().toAssetSelectionState()
    }

    when (val current = state) {
        SendAssetScreenState.Loading -> SendLoadingScreen(
            title = stringResource(R.string.send_choose_asset_title),
            onBack = onBack,
        )

        SendAssetScreenState.Empty -> SendEmptyScreen(
            title = stringResource(R.string.send_choose_asset_title),
            body = stringResource(R.string.send_empty_body),
            onBack = onBack,
        )

        is SendAssetScreenState.Content -> SendAssetSelectionContent(
            state = current,
            onBack = onBack,
            onAssetSelected = onAssetSelected,
            onNetworkRequired = onNetworkRequired,
        )
    }
}

@Composable
fun SatraSendNetworkScreen(
    walletRepository: SatraWalletRepository,
    symbol: String,
    onBack: () -> Unit,
    onNetworkSelected: (String) -> Unit,
) {
    var state by remember(symbol) { mutableStateOf<SendNetworkScreenState>(SendNetworkScreenState.Loading) }

    LaunchedEffect(walletRepository, symbol) {
        state = walletRepository.loadSendSnapshot().toNetworkSelectionState(Uri.decode(symbol))
    }

    when (val current = state) {
        SendNetworkScreenState.Loading -> SendLoadingScreen(
            title = stringResource(R.string.send_choose_network_title),
            onBack = onBack,
        )

        SendNetworkScreenState.Empty -> SendEmptyScreen(
            title = stringResource(R.string.send_choose_network_title),
            body = stringResource(R.string.send_network_empty_body),
            onBack = onBack,
        )

        is SendNetworkScreenState.Content -> SendNetworkSelectionContent(
            state = current,
            onBack = onBack,
            onNetworkSelected = onNetworkSelected,
        )
    }
}

@Composable
fun SatraSendComposerScreen(
    walletRepository: SatraWalletRepository,
    assetId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var state by remember(assetId) { mutableStateOf<SendComposerScreenState>(SendComposerScreenState.Loading) }

    LaunchedEffect(walletRepository, assetId) {
        state = walletRepository.loadSendSnapshot().toComposerState(Uri.decode(assetId))
    }

    when (val current = state) {
        SendComposerScreenState.Loading -> SendLoadingScreen(
            title = stringResource(R.string.send_compose_title),
            onBack = onBack,
        )

        SendComposerScreenState.Empty -> SendEmptyScreen(
            title = stringResource(R.string.send_compose_title),
            body = stringResource(R.string.send_asset_empty_body),
            onBack = onBack,
        )

        is SendComposerScreenState.Content -> SendComposerContent(
            state = current,
            walletRepository = walletRepository,
            onBack = onBack,
            onDone = onDone,
        )
    }
}

@Composable
private fun SendAssetSelectionContent(
    state: SendAssetScreenState.Content,
    onBack: () -> Unit,
    onAssetSelected: (String) -> Unit,
    onNetworkRequired: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    SendScaffold(
        title = stringResource(R.string.send_choose_asset_title),
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                SendHeader(
                    iconRes = R.drawable.ic_brand_move,
                    title = stringResource(R.string.send_asset_header_title),
                    body = stringResource(R.string.send_asset_header_body),
                )
            }
            items(
                items = state.groups,
                key = { group -> group.symbol },
            ) { group ->
                SendAssetGroupRow(
                    group = group,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (group.assets.size > 1) {
                            onNetworkRequired(group.symbol)
                        } else {
                            onAssetSelected(group.assets.first().asset.assetId)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = SendContentMaxWidth)
                        .padding(horizontal = 20.dp),
                )
            }
            item { Spacer(modifier = Modifier.height(22.dp)) }
        }
    }
}

@Composable
private fun SendNetworkSelectionContent(
    state: SendNetworkScreenState.Content,
    onBack: () -> Unit,
    onNetworkSelected: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    SendScaffold(
        title = stringResource(R.string.send_choose_network_title),
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                SendHeader(
                    iconRes = R.drawable.ic_brand_assets,
                    title = stringResource(R.string.send_network_header_title, state.symbol),
                    body = stringResource(R.string.send_network_header_body),
                )
            }
            items(
                items = state.assets,
                key = { row -> row.asset.assetId },
            ) { row ->
                SendNetworkRow(
                    row = row,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNetworkSelected(row.asset.assetId)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = SendContentMaxWidth)
                        .padding(horizontal = 20.dp),
                )
            }
            item { Spacer(modifier = Modifier.height(22.dp)) }
        }
    }
}

@Composable
private fun SendComposerContent(
    state: SendComposerScreenState.Content,
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var recipient by remember(state.row.asset.assetId) { mutableStateOf("") }
    var amountText by remember(state.row.asset.assetId) { mutableStateOf("") }
    var memo by remember(state.row.asset.assetId) { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var preparedTransactionId by remember { mutableStateOf<String?>(null) }
    var submitError by remember { mutableStateOf<String?>(null) }
    val prepareFailedFallback = stringResource(R.string.send_prepare_failed_body)
    val amount = amountText.toBigDecimalOrNullSafe()
    val recipientError = recipient.isNotBlank() && !isLikelyAddressForNetwork(recipient, state.row.network)
    val amountError = amountText.isNotBlank() &&
        (amount == null || amount <= BigDecimal.ZERO || amount > state.row.balanceDecimal)
    val canPrepare = !submitting &&
        preparedTransactionId == null &&
        state.canSign &&
        recipient.isNotBlank() &&
        amount != null &&
        amount > BigDecimal.ZERO &&
        amount <= state.row.balanceDecimal &&
        !recipientError

    SendScaffold(
        title = stringResource(R.string.send_compose_title),
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
                        .widthIn(max = SendContentMaxWidth)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    SendComposerHero(row = state.row)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (!state.canSign) {
                        SendWarningCard(
                            title = stringResource(R.string.send_cannot_sign_title),
                            body = if (state.wallet.isWatchOnly) {
                                stringResource(R.string.send_watch_only_body)
                            } else {
                                stringResource(R.string.send_missing_key_body)
                            },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    SendFieldCard {
                        OutlinedTextField(
                            value = recipient,
                            onValueChange = {
                                recipient = it.trim()
                                submitError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            minLines = 2,
                            label = { Text(stringResource(R.string.send_recipient_label)) },
                            supportingText = {
                                Text(
                                    text = if (recipientError) {
                                        stringResource(R.string.send_recipient_error)
                                    } else {
                                        stringResource(R.string.send_recipient_helper, state.row.network.displayName)
                                    },
                                )
                            },
                            isError = recipientError,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = {
                                amountText = it.filterAmountInput()
                                submitError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.send_amount_label, state.row.asset.symbol)) },
                            supportingText = {
                                Text(
                                    text = if (amountError) {
                                        stringResource(R.string.send_amount_error)
                                    } else {
                                        stringResource(
                                            R.string.send_available_balance,
                                            state.row.balanceFormatted,
                                        )
                                    },
                                )
                            },
                            isError = amountError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = memo,
                            onValueChange = {
                                memo = it
                                submitError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.send_memo_label)) },
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    SendReviewCard(
                        row = state.row,
                        recipient = recipient,
                        amount = amount,
                    )
                    preparedTransactionId?.let { transactionId ->
                        Spacer(modifier = Modifier.height(14.dp))
                        SendSuccessCard(transactionId = transactionId)
                    }
                    submitError?.let { error ->
                        Spacer(modifier = Modifier.height(14.dp))
                        SendWarningCard(
                            title = stringResource(R.string.send_prepare_failed_title),
                            body = error,
                        )
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                    Button(
                        onClick = {
                            val decimalAmount = amount ?: return@Button
                            submitting = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            submitError = null
                            coroutineScope.launchCatching(
                                onError = { throwable ->
                                    submitting = false
                                    submitError = throwable.message ?: prepareFailedFallback
                                },
                                block = {
                                    val transactionId = walletRepository.createPendingSendTransaction(
                                        SatraPendingSendRequest(
                                            walletId = state.wallet.walletId,
                                            assetId = state.row.asset.assetId,
                                            amountDecimal = decimalAmount,
                                            toAddress = recipient,
                                            memo = memo,
                                        ),
                                    )
                                    submitting = false
                                    preparedTransactionId = transactionId
                                    Toast.makeText(context, R.string.send_prepared_toast, Toast.LENGTH_SHORT).show()
                                },
                            )
                        },
                        enabled = canPrepare,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.surface,
                            )
                        } else {
                            Text(
                                text = if (preparedTransactionId == null) {
                                    stringResource(R.string.send_prepare_action)
                                } else {
                                    stringResource(R.string.send_done_action)
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (preparedTransactionId != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onDone,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.send_return_home_action),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SendScaffold(
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
private fun SendLoadingScreen(
    title: String,
    onBack: () -> Unit,
) {
    SendScaffold(title = title, onBack = onBack) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SendEmptyScreen(
    title: String,
    body: String,
    onBack: () -> Unit,
) {
    SendScaffold(title = title, onBack = onBack) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SendHeader(
    @DrawableRes iconRes: Int,
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = SendContentMaxWidth)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        SendIcon(iconRes = iconRes, modifier = Modifier.size(42.dp))
        Spacer(modifier = Modifier.height(22.dp))
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
private fun SendAssetGroupRow(
    group: SendAssetGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SendSelectableRow(
        iconRes = group.iconRes,
        title = group.name,
        subtitle = if (group.assets.size > 1) {
            stringResource(R.string.send_asset_network_count, group.assets.size)
        } else {
            group.assets.first().network.displayName
        },
        trailingPrimary = group.totalFiatFormatted,
        trailingSecondary = group.assets.joinToString(limit = 2) { it.balanceFormatted },
        enabled = true,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun SendNetworkRow(
    row: SendAssetRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SendSelectableRow(
        iconRes = row.iconRes,
        title = row.network.displayName,
        subtitle = row.network.family.uppercase(Locale.US),
        trailingPrimary = row.fiatFormatted,
        trailingSecondary = row.balanceFormatted,
        enabled = true,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = {
            SatraAssetNetworkIcon(
                assetSymbol = row.asset.symbol,
                networkId = row.network.networkId,
            )
        },
    )
}

@Composable
private fun SendSelectableRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    trailingPrimary: String,
    trailingSecondary: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
        } else {
            SendIcon(iconRes = iconRes, modifier = Modifier.size(44.dp))
        }
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
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = trailingPrimary,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = trailingSecondary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SendComposerHero(row: SendAssetRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SendIcon(
                    iconRes = row.iconRes,
                    modifier = Modifier.size(52.dp),
                    invertSurface = true,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.send_composer_header_title, row.asset.symbol),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = row.network.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.68f),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.send_available_balance, row.balanceFormatted),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = row.sourceAddress.shortAddress(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun SendFieldCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SendReviewCard(
    row: SendAssetRow,
    recipient: String,
    amount: BigDecimal?,
) {
    SendFieldCard {
        Text(
            text = stringResource(R.string.send_review_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SendReviewLine(
            label = stringResource(R.string.send_review_asset),
            value = "${row.asset.symbol} · ${row.network.displayName}",
        )
        SendReviewLine(
            label = stringResource(R.string.send_review_amount),
            value = amount?.let { "${formatCryptoAmount(it)} ${row.asset.symbol}" }
                ?: stringResource(R.string.send_review_pending),
        )
        SendReviewLine(
            label = stringResource(R.string.send_review_to),
            value = recipient.takeIf(String::isNotBlank)?.shortAddress()
                ?: stringResource(R.string.send_review_pending),
        )
        SendReviewLine(
            label = stringResource(R.string.send_review_fee),
            value = stringResource(R.string.send_fee_pending),
        )
    }
}

@Composable
private fun SendReviewLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SendWarningCard(
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun SendSuccessCard(transactionId: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.send_prepared_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.send_prepared_body, transactionId.take(8)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SendIcon(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    invertSurface: Boolean = false,
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = modifier,
    )
}

private suspend fun SatraWalletRepository.loadSendSnapshot(): SendSnapshot =
    coroutineScope {
        val wallet = getPrimaryWallet()
        if (wallet == null) {
            SendSnapshot.Empty
        } else {
            val addressesDeferred = async {
                ensureMnemonicReceiveAddresses(wallet.walletId)
                    .ifEmpty { getWalletAddresses(wallet.walletId) }
            }
            val assetsDeferred = async { getWalletAssets(wallet.walletId) }
            val privateKeysDeferred = async { getWalletPrivateKeys(wallet.walletId) }
            SendSnapshot.Content(
                wallet = wallet,
                assets = assetsDeferred.await(),
                addresses = addressesDeferred.await(),
                privateKeys = privateKeysDeferred.await(),
            )
        }
    }

private fun SendSnapshot.toAssetSelectionState(): SendAssetScreenState =
    when (this) {
        SendSnapshot.Empty -> SendAssetScreenState.Empty
        is SendSnapshot.Content -> {
            val rows = toSendAssetRows()
            if (rows.isEmpty()) {
                SendAssetScreenState.Empty
            } else {
                SendAssetScreenState.Content(rows.groupForAssetSelection(wallet.localCurrencyCode))
            }
        }
    }

private fun SendSnapshot.toNetworkSelectionState(symbol: String): SendNetworkScreenState =
    when (this) {
        SendSnapshot.Empty -> SendNetworkScreenState.Empty
        is SendSnapshot.Content -> {
            val rows = toSendAssetRows()
                .filter { row -> row.asset.symbol.equals(symbol, ignoreCase = true) }
            if (rows.isEmpty()) {
                SendNetworkScreenState.Empty
            } else {
                SendNetworkScreenState.Content(symbol = symbol.uppercase(Locale.US), assets = rows)
            }
        }
    }

private fun SendSnapshot.toComposerState(assetId: String): SendComposerScreenState =
    when (this) {
        SendSnapshot.Empty -> SendComposerScreenState.Empty
        is SendSnapshot.Content -> {
            val row = toSendAssetRows().firstOrNull { candidate -> candidate.asset.assetId == assetId }
            if (row == null) {
                SendComposerScreenState.Empty
            } else {
                SendComposerScreenState.Content(
                    wallet = wallet,
                    row = row,
                    canSign = !wallet.isWatchOnly && privateKeys.any { privateKey ->
                        privateKey.networkId == row.network.networkId && !privateKey.isEncrypted
                    },
                )
            }
        }
    }

private fun SendSnapshot.Content.toSendAssetRows(): List<SendAssetRow> {
    val catalogAssetsById = SupportedAssetCatalog.assets.associateBy { it.assetId }
    val networksById = SupportedAssetCatalog.networks.associateBy { it.networkId }
    val addressesByNetwork = addresses
        .filter { address ->
            address.addressType == "receive" || address.addressType == "watch_only"
        }
        .groupBy { address -> address.networkId }
    return assets.mapNotNull { walletAsset ->
        val asset = catalogAssetsById[walletAsset.assetId] ?: return@mapNotNull null
        val network = networksById[walletAsset.networkId] ?: return@mapNotNull null
        val sourceAddress = addressesByNetwork[walletAsset.networkId]
            .orEmpty()
            .sortedWith(
                compareByDescending<WalletAddressRecord> { it.isPrimary }
                    .thenBy { it.addressIndex ?: Int.MAX_VALUE },
            )
            .firstOrNull()
            ?.address
            ?: return@mapNotNull null
        val balance = walletAsset.balanceDecimal.toBigDecimalOrZero()
        SendAssetRow(
            asset = asset,
            network = network,
            walletAsset = walletAsset,
            sourceAddress = sourceAddress,
            balanceDecimal = balance,
            balanceFormatted = "${formatCryptoAmount(balance)} ${asset.symbol}",
            fiatAmount = walletAsset.balanceFiatValue.toBigDecimalOrZero(),
            fiatFormatted = formatFiat(walletAsset.balanceFiatValue, wallet.localCurrencyCode),
            iconRes = assetIconRes(asset.symbol, walletAsset.networkId),
            hasSigningKey = privateKeys.any { privateKey ->
                privateKey.networkId == walletAsset.networkId && !privateKey.isEncrypted
            },
        )
    }.sortedWith(
        compareByDescending<SendAssetRow> { row -> row.fiatAmount }
            .thenByDescending { row -> row.balanceDecimal }
            .thenBy { row -> row.asset.name.lowercase(Locale.US) }
            .thenBy { row -> row.network.displayName.lowercase(Locale.US) },
    )
}

private fun List<SendAssetRow>.groupForAssetSelection(localCurrencyCode: String): List<SendAssetGroup> =
    groupBy { row -> row.asset.symbol.uppercase(Locale.US) }
        .map { (symbol, rows) ->
            val primary = rows.maxWith(
                compareBy<SendAssetRow> { row -> row.fiatAmount }
                    .thenBy { row -> row.balanceDecimal },
            )
            val totalFiat = rows.fold(BigDecimal.ZERO) { total, row -> total + row.fiatAmount }
            SendAssetGroup(
                symbol = symbol,
                name = primary.asset.name,
                assets = rows,
                totalFiatFormatted = formatFiat(totalFiat.toPlainString(), localCurrencyCode),
                iconRes = primary.iconRes,
            )
        }
        .sortedWith(
            compareByDescending<SendAssetGroup> { group ->
                group.assets.fold(BigDecimal.ZERO) { total, row -> total + row.fiatAmount }
            }.thenBy { group -> group.name.lowercase(Locale.US) },
        )

private fun isLikelyAddressForNetwork(
    address: String,
    network: SupportedNetwork,
): Boolean {
    val value = address.trim()
    if (value.length < 4) return false
    return when (network.family) {
        "evm" -> value.matches(Regex("^0x[0-9a-fA-F]{40}$"))
        "utxo" -> value.length in 26..90 && value.none(Char::isWhitespace)
        "solana", "sui", "aptos", "near", "polkadot", "stellar", "ton", "tron", "ripple" ->
            value.length in 16..120 && value.none(Char::isWhitespace)
        else -> value.length in 16..120 && value.none(Char::isWhitespace)
    }
}

private fun String.filterAmountInput(): String {
    val builder = StringBuilder()
    var hasDecimal = false
    forEach { character ->
        when {
            character.isDigit() -> builder.append(character)
            character == '.' && !hasDecimal -> {
                hasDecimal = true
                builder.append(character)
            }
        }
    }
    return builder.toString()
}

private fun String.toBigDecimalOrNullSafe(): BigDecimal? =
    trim().takeIf(String::isNotBlank)?.let { value ->
        runCatching { BigDecimal(value) }.getOrNull()
    }

private fun String.toBigDecimalOrZero(): BigDecimal =
    runCatching { BigDecimal(this) }.getOrDefault(BigDecimal.ZERO)

private fun formatCryptoAmount(value: BigDecimal): String {
    val decimal = value
        .setScale(CRYPTO_DISPLAY_DECIMALS, RoundingMode.DOWN)
        .stripTrailingZeros()
    return if (decimal.compareTo(BigDecimal.ZERO) == 0) "0" else decimal.toPlainString()
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

private fun String.shortAddress(): String =
    if (length <= 14) this else "${take(6)}…${takeLast(6)}"

private fun kotlinx.coroutines.CoroutineScope.launchCatching(
    onError: (Throwable) -> Unit,
    block: suspend () -> Unit,
) {
    this.launch {
        runCatching { block() }.onFailure(onError)
    }
}

private sealed interface SendSnapshot {
    data object Empty : SendSnapshot
    data class Content(
        val wallet: WalletRecord,
        val assets: List<WalletAssetRecord>,
        val addresses: List<WalletAddressRecord>,
        val privateKeys: List<WalletPrivateKeyRecord>,
    ) : SendSnapshot
}

private sealed interface SendAssetScreenState {
    data object Loading : SendAssetScreenState
    data object Empty : SendAssetScreenState
    data class Content(val groups: List<SendAssetGroup>) : SendAssetScreenState
}

private sealed interface SendNetworkScreenState {
    data object Loading : SendNetworkScreenState
    data object Empty : SendNetworkScreenState
    data class Content(
        val symbol: String,
        val assets: List<SendAssetRow>,
    ) : SendNetworkScreenState
}

private sealed interface SendComposerScreenState {
    data object Loading : SendComposerScreenState
    data object Empty : SendComposerScreenState
    data class Content(
        val wallet: WalletRecord,
        val row: SendAssetRow,
        val canSign: Boolean,
    ) : SendComposerScreenState
}

private data class SendAssetGroup(
    val symbol: String,
    val name: String,
    val assets: List<SendAssetRow>,
    val totalFiatFormatted: String,
    @DrawableRes val iconRes: Int,
)

private data class SendAssetRow(
    val asset: SupportedAsset,
    val network: SupportedNetwork,
    val walletAsset: WalletAssetRecord,
    val sourceAddress: String,
    val balanceDecimal: BigDecimal,
    val balanceFormatted: String,
    val fiatAmount: BigDecimal,
    val fiatFormatted: String,
    @DrawableRes val iconRes: Int,
    val hasSigningKey: Boolean,
)

private val SendContentMaxWidth = 720.dp
private const val CRYPTO_DISPLAY_DECIMALS = 8
