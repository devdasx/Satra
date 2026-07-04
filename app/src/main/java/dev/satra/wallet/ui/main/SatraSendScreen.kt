package dev.satra.wallet.ui.main

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.satra.wallet.R
import dev.satra.wallet.data.assets.SupportedAsset
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.assets.SupportedNetwork
import dev.satra.wallet.data.db.AddressBookEntryRecord
import dev.satra.wallet.data.db.SatraPendingSendRequest
import dev.satra.wallet.data.db.SatraWalletRepository
import dev.satra.wallet.data.db.WalletAddressRecord
import dev.satra.wallet.data.db.WalletAssetRecord
import dev.satra.wallet.data.db.WalletPrivateKeyRecord
import dev.satra.wallet.data.db.WalletRecord
import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionRecord
import dev.satra.wallet.data.db.WalletTransactionStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
fun SatraSendRecipientScreen(
    walletRepository: SatraWalletRepository,
    assetId: String,
    scannedAddress: String,
    onBack: () -> Unit,
    onScanClick: () -> Unit,
    onScannedAddressConsumed: () -> Unit,
    onContinue: (assetId: String, recipient: String, warnPoison: Boolean) -> Unit,
) {
    var state by remember(assetId) { mutableStateOf<SendDetailsState>(SendDetailsState.Loading) }

    LaunchedEffect(walletRepository, assetId) {
        state = walletRepository.loadSendDetails(Uri.decode(assetId))
    }

    when (val current = state) {
        SendDetailsState.Loading -> SendLoadingScreen(
            title = stringResource(R.string.send_recipient_title),
            onBack = onBack,
        )

        SendDetailsState.Empty -> SendEmptyScreen(
            title = stringResource(R.string.send_recipient_title),
            body = stringResource(R.string.send_asset_empty_body),
            onBack = onBack,
        )

        is SendDetailsState.Content -> SendRecipientContent(
            state = current,
            scannedAddress = scannedAddress,
            onBack = onBack,
            onScanClick = onScanClick,
            onScannedAddressConsumed = onScannedAddressConsumed,
            onContinue = onContinue,
        )
    }
}

@Composable
fun SatraSendAmountScreen(
    walletRepository: SatraWalletRepository,
    assetId: String,
    recipient: String,
    warnPoison: Boolean,
    onBack: () -> Unit,
    onReview: (assetId: String, recipient: String, amount: String, warnPoison: Boolean) -> Unit,
) {
    var state by remember(assetId) { mutableStateOf<SendDetailsState>(SendDetailsState.Loading) }

    LaunchedEffect(walletRepository, assetId) {
        state = walletRepository.loadSendDetails(Uri.decode(assetId))
    }

    when (val current = state) {
        SendDetailsState.Loading -> SendLoadingScreen(
            title = stringResource(R.string.send_amount_title),
            onBack = onBack,
        )

        SendDetailsState.Empty -> SendEmptyScreen(
            title = stringResource(R.string.send_amount_title),
            body = stringResource(R.string.send_asset_empty_body),
            onBack = onBack,
        )

        is SendDetailsState.Content -> SendAmountContent(
            state = current,
            recipient = Uri.decode(recipient),
            warnPoison = warnPoison,
            onBack = onBack,
            onReview = onReview,
        )
    }
}

@Composable
fun SatraSendReviewScreen(
    walletRepository: SatraWalletRepository,
    assetId: String,
    recipient: String,
    amount: String,
    warnPoison: Boolean,
    onBack: () -> Unit,
    onSent: (String) -> Unit,
) {
    var state by remember(assetId) { mutableStateOf<SendDetailsState>(SendDetailsState.Loading) }

    LaunchedEffect(walletRepository, assetId) {
        state = walletRepository.loadSendDetails(Uri.decode(assetId))
    }

    when (val current = state) {
        SendDetailsState.Loading -> SendLoadingScreen(
            title = stringResource(R.string.send_review_screen_title),
            onBack = onBack,
        )

        SendDetailsState.Empty -> SendEmptyScreen(
            title = stringResource(R.string.send_review_screen_title),
            body = stringResource(R.string.send_asset_empty_body),
            onBack = onBack,
        )

        is SendDetailsState.Content -> SendReviewContent(
            state = current,
            recipient = Uri.decode(recipient),
            amountText = Uri.decode(amount),
            warnPoison = warnPoison,
            walletRepository = walletRepository,
            onBack = onBack,
            onSent = onSent,
        )
    }
}

@Composable
fun SatraSendSentScreen(
    walletRepository: SatraWalletRepository,
    transactionId: String,
    onDone: () -> Unit,
    onSendAnother: () -> Unit,
) {
    var state by remember(transactionId) { mutableStateOf<SendReceiptState>(SendReceiptState.Loading) }

    LaunchedEffect(walletRepository, transactionId) {
        state = walletRepository.loadSendReceipt(Uri.decode(transactionId))
    }

    when (val current = state) {
        SendReceiptState.Loading -> SendLoadingScreen(
            title = stringResource(R.string.send_sent_title),
            onBack = onDone,
        )

        SendReceiptState.Empty -> SendEmptyScreen(
            title = stringResource(R.string.send_sent_title),
            body = stringResource(R.string.send_receipt_empty_body),
            onBack = onDone,
        )

        is SendReceiptState.Content -> SendReceiptContent(
            state = current,
            onDone = onDone,
            onSendAnother = onSendAnother,
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
    var query by rememberSaveable { mutableStateOf("") }
    val filteredGroups = remember(state.groups, query) {
        state.groups.filterByQuery(query)
    }
    val fundedGroups = filteredGroups.filter { group -> group.totalFiat > BigDecimal.ZERO || group.totalBalance > BigDecimal.ZERO }
    val unfundedGroups = filteredGroups.filterNot { group -> group in fundedGroups }

    SendScaffold(
        title = stringResource(R.string.send_choose_asset_title),
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                SendHero(
                    iconRes = R.drawable.ic_brand_move,
                    title = stringResource(R.string.send_asset_header_title),
                    body = stringResource(R.string.send_asset_header_body),
                )
                SendSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.send_search_asset_placeholder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = SendContentMaxWidth)
                        .padding(horizontal = 20.dp),
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
            if (filteredGroups.isEmpty()) {
                item {
                    SendEmptyInline(
                        text = stringResource(R.string.send_asset_empty_search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = SendContentMaxWidth)
                            .padding(horizontal = 20.dp),
                    )
                }
            } else {
                if (fundedGroups.isNotEmpty()) {
                    item {
                        SendSectionHeader(
                            title = stringResource(R.string.send_section_your_assets),
                            trailing = stringResource(R.string.send_assets_shown, fundedGroups.size),
                        )
                    }
                    items(fundedGroups, key = { group -> "funded-${group.symbol}" }) { group ->
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
                        )
                    }
                }
                if (unfundedGroups.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        SendSectionHeader(
                            title = stringResource(R.string.send_section_all_assets),
                            trailing = stringResource(R.string.send_assets_shown, unfundedGroups.size),
                        )
                    }
                    items(unfundedGroups, key = { group -> "all-${group.symbol}" }) { group ->
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
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(26.dp)) }
            }
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
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                SendHero(
                    iconRes = assetIconRes(state.symbol),
                    title = stringResource(R.string.send_network_header_title, state.symbol),
                    body = stringResource(R.string.send_network_context, state.symbol, state.assets.size),
                )
            }
            items(state.assets, key = { row -> row.asset.assetId }) { row ->
                SendNetworkRow(
                    row = row,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNetworkSelected(row.asset.assetId)
                    },
                )
            }
            item { Spacer(modifier = Modifier.height(26.dp)) }
        }
    }
}

@Composable
private fun SendRecipientContent(
    state: SendDetailsState.Content,
    scannedAddress: String,
    onBack: () -> Unit,
    onScanClick: () -> Unit,
    onScannedAddressConsumed: () -> Unit,
    onContinue: (assetId: String, recipient: String, warnPoison: Boolean) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var recipient by rememberSaveable(state.row.asset.assetId) { mutableStateOf("") }
    var bookVisible by remember { mutableStateOf(false) }
    val knownAddresses = remember(state.addressBookEntries, state.recentRecipients) {
        (state.addressBookEntries.map { it.address } + state.recentRecipients.map { it.address }).distinct()
    }
    val isValid = isLikelyAddressForNetwork(recipient, state.row.network)
    val showError = recipient.isNotBlank() && !isValid
    val isKnown = knownAddresses.any { it.equals(recipient.trim(), ignoreCase = true) }
    val warnPoison = recipient.isNotBlank() && isPoisonLike(recipient, knownAddresses)
    val canContinue = state.canSign && isValid

    LaunchedEffect(scannedAddress) {
        if (scannedAddress.isNotBlank()) {
            recipient = scannedAddress
            onScannedAddressConsumed()
        }
    }

    if (bookVisible) {
        SendAddressBookSheet(
            entries = state.addressBookEntries,
            recentRecipients = state.recentRecipients,
            onSelect = { address ->
                recipient = address
                bookVisible = false
            },
            onDismiss = { bookVisible = false },
        )
    }

    SendScaffold(
        title = stringResource(R.string.send_recipient_title),
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SendHero(
                iconRes = R.drawable.ic_brand_receive,
                title = stringResource(R.string.send_recipient_header_title),
                body = stringResource(
                    R.string.send_recipient_header_body,
                    state.row.asset.symbol,
                    state.row.network.displayName,
                ),
            )
            SendContentColumn {
                if (!state.canSign) {
                    SendWarningCard(
                        title = stringResource(R.string.send_cannot_sign_title),
                        body = if (state.wallet.isWatchOnly) {
                            stringResource(R.string.send_watch_only_body)
                        } else {
                            stringResource(R.string.send_missing_key_body)
                        },
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
                SendInputCard {
                    OutlinedTextField(
                        value = recipient,
                        onValueChange = { recipient = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        label = { Text(stringResource(R.string.send_recipient_label)) },
                        supportingText = {
                            Text(
                                text = if (showError) {
                                    stringResource(R.string.send_recipient_error)
                                } else {
                                    stringResource(R.string.send_recipient_helper, state.row.network.displayName)
                                },
                            )
                        },
                        isError = showError,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SendToolButton(
                            text = stringResource(R.string.send_recipient_paste),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                clipboard.getText()?.text?.takeIf(String::isNotBlank)?.let { recipient = it.trim() }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SendToolButton(
                            text = stringResource(R.string.send_recipient_scan),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onScanClick()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SendToolButton(
                            text = stringResource(R.string.send_recipient_book),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                bookVisible = true
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                when {
                    warnPoison -> SendWarningCard(
                        title = stringResource(R.string.send_recipient_poison_title),
                        body = stringResource(R.string.send_recipient_poison_body),
                    )
                    isKnown && isValid -> SendNoticeCard(
                        title = stringResource(R.string.send_recipient_known_title),
                        body = recipient.shortAddress(),
                    )
                    recipient.isNotBlank() && isValid -> SendNoticeCard(
                        title = stringResource(R.string.send_recipient_never_sent_title),
                        body = stringResource(R.string.send_recipient_never_sent_body),
                    )
                }
                if (state.recentRecipients.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.send_recent_recipients_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    state.recentRecipients.take(3).forEach { recent ->
                        SendCompactAddressRow(
                            title = recent.label,
                            body = recent.address.shortAddress(),
                            onClick = { recipient = recent.address },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(26.dp))
                SendPrimaryButton(
                    text = stringResource(R.string.send_continue_action),
                    enabled = canContinue,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onContinue(state.row.asset.assetId, recipient.trim(), warnPoison)
                    },
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SendAmountContent(
    state: SendDetailsState.Content,
    recipient: String,
    warnPoison: Boolean,
    onBack: () -> Unit,
    onReview: (assetId: String, recipient: String, amount: String, warnPoison: Boolean) -> Unit,
) {
    var amountText by rememberSaveable(state.row.asset.assetId, recipient) { mutableStateOf("") }
    var feeSpeed by rememberSaveable(state.row.asset.assetId) { mutableStateOf(SendFeeSpeed.Normal) }
    var feeSheetVisible by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val amount = amountText.toBigDecimalOrNullSafe()
    val feeQuote = remember(state.row, feeSpeed) { estimatedFeeFor(state.row, feeSpeed) }
    val maxAmount = remember(state.row, feeQuote) { state.row.maxSendAmount(feeQuote) }
    val amountError = amountText.isNotBlank() &&
        (amount == null || amount <= BigDecimal.ZERO || amount > maxAmount)
    val canReview = amount != null && amount > BigDecimal.ZERO && amount <= maxAmount && state.canSign

    if (feeSheetVisible) {
        SendFeeSheet(
            selected = feeSpeed,
            quoteFor = { estimatedFeeFor(state.row, it) },
            onSelected = {
                feeSpeed = it
                feeSheetVisible = false
            },
            onDismiss = { feeSheetVisible = false },
        )
    }

    SendScaffold(
        title = stringResource(R.string.send_amount_title),
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SendHero(
                iconRes = state.row.iconRes,
                title = stringResource(R.string.send_amount_header_title),
                body = stringResource(R.string.send_amount_header_body),
            )
            SendContentColumn {
                SendInputCard {
                    Text(
                        text = amountText.takeIf(String::isNotBlank) ?: "0",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.row.asset.symbol,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.send_amount_available, state.row.balanceFormatted),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                amountText = formatCryptoAmount(maxAmount)
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.send_amount_max),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    SendFeePill(
                        quote = feeQuote,
                        onClick = { feeSheetVisible = true },
                    )
                    if (amountError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.send_amount_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                SendKeypad(
                    onKey = { key ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        amountText = amountText.applyAmountKey(key)
                    },
                )
                Spacer(modifier = Modifier.height(26.dp))
                SendPrimaryButton(
                    text = stringResource(R.string.send_review_action),
                    enabled = canReview,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onReview(state.row.asset.assetId, recipient, amountText, warnPoison)
                    },
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendReviewContent(
    state: SendDetailsState.Content,
    recipient: String,
    amountText: String,
    warnPoison: Boolean,
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
    onSent: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var showPoisonSheet by remember(warnPoison) { mutableStateOf(false) }
    val amount = amountText.toBigDecimalOrNullSafe() ?: BigDecimal.ZERO
    val feeQuote = estimatedFeeFor(state.row, SendFeeSpeed.Normal)
    val prepareFailedFallback = stringResource(R.string.send_prepare_failed_body)
    val canSubmit = !submitting && state.canSign && amount > BigDecimal.ZERO && amount <= state.row.balanceDecimal

    fun submit() {
        submitting = true
        submitError = null
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launchCatching(
            onError = { throwable ->
                submitting = false
                submitError = throwable.message ?: prepareFailedFallback
            },
            block = {
                val transactionId = walletRepository.createPendingSendTransaction(
                    SatraPendingSendRequest(
                        walletId = state.wallet.walletId,
                        assetId = state.row.asset.assetId,
                        amountDecimal = amount,
                        toAddress = recipient,
                    ),
                )
                submitting = false
                onSent(transactionId)
            },
        )
    }

    if (showPoisonSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPoisonSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            SendSheetContent {
                Text(
                    text = stringResource(R.string.send_poison_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.send_poison_sheet_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SendPrimaryButton(
                    text = stringResource(R.string.send_poison_send_anyway),
                    enabled = canSubmit,
                    onClick = {
                        showPoisonSheet = false
                        submit()
                    },
                )
                OutlinedButton(
                    onClick = { showPoisonSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(100.dp),
                ) {
                    Text(
                        text = stringResource(R.string.send_poison_review_again),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    SendScaffold(
        title = stringResource(R.string.send_review_screen_title),
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SendHero(
                iconRes = R.drawable.ic_brand_list,
                title = stringResource(R.string.send_review_header_title),
                body = stringResource(R.string.send_review_header_body),
            )
            SendContentColumn {
                SendAmountHero(
                    row = state.row,
                    amount = amount,
                )
                Spacer(modifier = Modifier.height(14.dp))
                SendInputCard {
                    SendReviewLine(
                        label = stringResource(R.string.send_review_to),
                        value = recipient.shortAddress(),
                    )
                    SendReviewLine(
                        label = stringResource(R.string.send_review_network),
                        value = state.row.network.displayName,
                    )
                    SendReviewLine(
                        label = stringResource(R.string.send_review_source),
                        value = state.row.sourceAddress.shortAddress(),
                    )
                    SendReviewLine(
                        label = stringResource(R.string.send_review_fee),
                        value = feeQuote.displayText,
                    )
                    SendReviewLine(
                        label = stringResource(R.string.send_review_total),
                        value = "${formatCryptoAmount(amount)} ${state.row.asset.symbol}",
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                SendWarningCard(
                    title = stringResource(R.string.send_final_warning_title),
                    body = stringResource(R.string.send_final_warning_body),
                )
                submitError?.let { error ->
                    Spacer(modifier = Modifier.height(14.dp))
                    SendWarningCard(
                        title = stringResource(R.string.send_prepare_failed_title),
                        body = error,
                    )
                }
                Spacer(modifier = Modifier.height(26.dp))
                SendPrimaryButton(
                    text = stringResource(R.string.send_now_action),
                    enabled = canSubmit,
                    loading = submitting,
                    onClick = {
                        if (warnPoison) {
                            showPoisonSheet = true
                        } else {
                            submit()
                        }
                    },
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SendReceiptContent(
    state: SendReceiptState.Content,
    onDone: () -> Unit,
    onSendAnother: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val statusLabel = when (state.transaction.status) {
        WalletTransactionStatus.Pending.value -> stringResource(R.string.send_status_pending)
        WalletTransactionStatus.Success.value -> stringResource(R.string.send_status_success)
        WalletTransactionStatus.Failed.value -> stringResource(R.string.send_status_failed)
        WalletTransactionStatus.Canceled.value -> stringResource(R.string.send_status_canceled)
        else -> state.transaction.status.replaceFirstChar { it.uppercase(Locale.US) }
    }
    val savedTime = formatSendDateTime(state.transaction.firstSeenAt)
        .takeIf(String::isNotBlank)
        ?: stringResource(R.string.send_review_pending)
    SendScaffold(
        title = stringResource(R.string.send_sent_title),
        onBack = onDone,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SendContentColumn {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    SatraAssetNetworkIcon(
                        assetSymbol = state.asset.symbol,
                        networkId = state.network.networkId,
                        modifier = Modifier.size(78.dp),
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.send_sent_pending_title),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.send_sent_pending_body),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))
                SendInputCard {
                    SendReviewLine(
                        label = stringResource(R.string.send_receipt_status),
                        value = statusLabel,
                    )
                    SendReviewLine(
                        label = stringResource(R.string.send_review_amount),
                        value = state.amountFormatted,
                    )
                    SendReviewLine(
                        label = stringResource(R.string.send_review_network),
                        value = state.network.displayName,
                    )
                    SendReviewLine(
                        label = stringResource(R.string.send_review_to),
                        value = state.transaction.toAddress?.shortAddress()
                            ?: stringResource(R.string.send_review_pending),
                    )
                    SendReviewLine(
                        label = stringResource(R.string.send_receipt_saved),
                        value = savedTime,
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            shareReceipt(context, state)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(100.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.send_receipt_share),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            state.explorerUrl?.let { url ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        enabled = state.explorerUrl != null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(100.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.send_receipt_explorer),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(26.dp))
                SendPrimaryButton(
                    text = stringResource(R.string.send_receipt_done),
                    onClick = onDone,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSendAnother,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(100.dp),
                ) {
                    Text(
                        text = stringResource(R.string.send_receipt_send_another),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
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
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SendHero(
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
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
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
private fun SendContentColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = SendContentMaxWidth)
            .padding(horizontal = 20.dp),
        content = content,
    )
}

@Composable
private fun SendSectionHeader(
    title: String,
    trailing: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = SendContentMaxWidth)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = trailing,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SendSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(100.dp)),
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
}

@Composable
private fun SendAssetGroupRow(
    group: SendAssetGroup,
    onClick: () -> Unit,
) {
    SendSelectableRow(
        title = group.name,
        subtitle = if (group.assets.size > 1) {
            stringResource(R.string.send_asset_network_count, group.assets.size)
        } else {
            "${group.symbol} · ${group.assets.first().network.displayName}"
        },
        trailingPrimary = group.totalFiatFormatted,
        trailingSecondary = group.totalBalanceFormatted,
        enabled = group.canSend,
        onClick = onClick,
        leadingIcon = {
            Image(
                painter = painterResource(group.iconRes),
                contentDescription = null,
                modifier = Modifier.size(50.dp),
            )
        },
    )
}

@Composable
private fun SendNetworkRow(
    row: SendAssetRow,
    onClick: () -> Unit,
) {
    SendSelectableRow(
        title = row.network.displayName,
        subtitle = row.network.tokenStandard ?: row.network.family.uppercase(Locale.US),
        trailingPrimary = row.fiatFormatted,
        trailingSecondary = row.balanceFormatted,
        enabled = row.hasSigningKey,
        onClick = onClick,
        leadingIcon = {
            SatraBadgedIcon(
                primaryIconRes = networkIconRes(row.network.networkId),
                badgeIconRes = row.iconRes,
            )
        },
    )
}

@Composable
private fun SendSelectableRow(
    title: String,
    subtitle: String,
    trailingPrimary: String,
    trailingSecondary: String,
    enabled: Boolean,
    onClick: () -> Unit,
    leadingIcon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = SendContentMaxWidth)
            .height(82.dp)
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon()
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = trailingPrimary,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = trailingSecondary,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SendInputCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content,
        )
    }
}

@Composable
private fun SendToolButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(100.dp),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SendNoticeCard(
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SendWarningCard(
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun SendCompactAddressRow(
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_receive),
            contentDescription = null,
            modifier = Modifier.size(34.dp),
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
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendAddressBookSheet(
    entries: List<AddressBookEntryRecord>,
    recentRecipients: List<SendRecentRecipient>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        SendSheetContent {
            Text(
                text = stringResource(R.string.send_address_book_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            if (entries.isEmpty() && recentRecipients.isEmpty()) {
                Text(
                    text = stringResource(R.string.send_address_book_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                entries.forEach { entry ->
                    SendCompactAddressRow(
                        title = entry.label,
                        body = entry.address.shortAddress(),
                        onClick = { onSelect(entry.address) },
                    )
                }
                if (entries.isNotEmpty() && recentRecipients.isNotEmpty()) {
                    HorizontalDivider()
                }
                recentRecipients.forEach { recent ->
                    SendCompactAddressRow(
                        title = recent.label,
                        body = recent.address.shortAddress(),
                        onClick = { onSelect(recent.address) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendFeeSheet(
    selected: SendFeeSpeed,
    quoteFor: (SendFeeSpeed) -> SendFeeQuote,
    onSelected: (SendFeeSpeed) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        SendSheetContent {
            Text(
                text = stringResource(R.string.send_fee_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            SendFeeSpeed.entries.forEach { speed ->
                val quote = quoteFor(speed)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (speed == selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .clickable { onSelected(speed) }
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(speed.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (speed == selected) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = quote.displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (speed == selected) {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SendSheetContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun SendFeePill(
    quote: SendFeeQuote,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.send_fee_pill_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = quote.displayText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SendKeypad(onKey: (String) -> Unit) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    Button(
                        onClick = { onKey(key) },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SendAmountHero(
    row: SendAssetRow,
    amount: BigDecimal,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SatraAssetNetworkIcon(
                    assetSymbol = row.asset.symbol,
                    networkId = row.network.networkId,
                    modifier = Modifier.size(58.dp),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.asset.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${row.asset.symbol} · ${row.network.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "${formatCryptoAmount(amount)} ${row.asset.symbol}",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontWeight = FontWeight.Bold,
            )
        }
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
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SendPrimaryButton(
    text: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.surface,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SendEmptyInline(
    text: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
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

private suspend fun SatraWalletRepository.loadSendDetails(assetId: String): SendDetailsState =
    coroutineScope {
        when (val snapshot = loadSendSnapshot()) {
            SendSnapshot.Empty -> SendDetailsState.Empty
            is SendSnapshot.Content -> {
                val row = snapshot.toSendAssetRows().firstOrNull { candidate -> candidate.asset.assetId == assetId }
                    ?: return@coroutineScope SendDetailsState.Empty
                val addressBookDeferred = async { getAddressBookEntries() }
                val transactionsDeferred = async { getWalletTransactions(snapshot.wallet.walletId) }
                val addressBook = addressBookDeferred.await()
                    .filter { entry -> entry.networkId == row.network.networkId }
                    .sortedWith(compareByDescending<AddressBookEntryRecord> { it.isFavorite }.thenBy { it.label.lowercase(Locale.US) })
                val recent = transactionsDeferred.await().toRecentRecipients(row.network.networkId)
                SendDetailsState.Content(
                    wallet = snapshot.wallet,
                    row = row,
                    canSign = !snapshot.wallet.isWatchOnly && row.hasSigningKey,
                    addressBookEntries = addressBook,
                    recentRecipients = recent,
                )
            }
        }
    }

private suspend fun SatraWalletRepository.loadSendReceipt(transactionId: String): SendReceiptState =
    coroutineScope {
        val wallet = getPrimaryWallet() ?: return@coroutineScope SendReceiptState.Empty
        val transaction = getWalletTransactions(wallet.walletId)
            .firstOrNull { it.transactionId == transactionId }
            ?: return@coroutineScope SendReceiptState.Empty
        val asset = SupportedAssetCatalog.assets.firstOrNull { it.assetId == transaction.assetId }
            ?: return@coroutineScope SendReceiptState.Empty
        val network = SupportedAssetCatalog.networks.firstOrNull { it.networkId == transaction.networkId }
            ?: return@coroutineScope SendReceiptState.Empty
        SendReceiptState.Content(
            wallet = wallet,
            transaction = transaction,
            asset = asset,
            network = network,
            amountFormatted = "${formatCryptoAmount(transaction.amountDecimal.toBigDecimalOrZero())} ${asset.symbol}",
            explorerUrl = transaction.transactionHash?.let { hash -> explorerUrlFor(network.networkId, hash) },
        )
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

private fun SendSnapshot.Content.toSendAssetRows(): List<SendAssetRow> {
    val catalogAssetsById = SupportedAssetCatalog.assets.associateBy { it.assetId }
    val networksById = SupportedAssetCatalog.networks.associateBy { it.networkId }
    val addressesByNetwork = addresses
        .filter { address -> address.addressType == "receive" || address.addressType == "watch_only" }
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
            iconRes = assetIconRes(asset.symbol),
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
            val totalBalance = rows.fold(BigDecimal.ZERO) { total, row -> total + row.balanceDecimal }
            SendAssetGroup(
                symbol = symbol,
                name = primary.asset.name,
                assets = rows,
                totalFiat = totalFiat,
                totalBalance = totalBalance,
                totalFiatFormatted = formatFiat(totalFiat.toPlainString(), localCurrencyCode),
                totalBalanceFormatted = "${formatCryptoAmount(totalBalance)} $symbol",
                iconRes = primary.iconRes,
                canSend = rows.any { row -> row.hasSigningKey },
            )
        }
        .sortedWith(
            compareByDescending<SendAssetGroup> { group -> group.totalFiat }
                .thenByDescending { group -> group.totalBalance }
                .thenBy { group -> group.name.lowercase(Locale.US) },
        )

private fun List<SendAssetGroup>.filterByQuery(query: String): List<SendAssetGroup> {
    val normalized = query.trim().lowercase(Locale.US)
    if (normalized.isBlank()) return this
    return filter { group ->
        group.name.lowercase(Locale.US).contains(normalized) ||
            group.symbol.lowercase(Locale.US).contains(normalized) ||
            group.assets.any { row ->
                row.network.displayName.lowercase(Locale.US).contains(normalized) ||
                    row.network.networkId.lowercase(Locale.US).contains(normalized)
            }
    }
}

private fun List<WalletTransactionRecord>.toRecentRecipients(networkId: String): List<SendRecentRecipient> =
    asSequence()
        .filter { transaction ->
            transaction.networkId == networkId &&
                transaction.direction == WalletTransactionDirection.Outgoing.value &&
                !transaction.toAddress.isNullOrBlank()
        }
        .sortedByDescending { it.timestamp }
        .distinctBy { it.toAddress.orEmpty().lowercase(Locale.US) }
        .take(6)
        .map { transaction ->
            SendRecentRecipient(
                label = transaction.memo?.takeIf(String::isNotBlank)
                    ?: transaction.toAddress.orEmpty().shortAddress(),
                address = transaction.toAddress.orEmpty(),
            )
        }
        .toList()

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

private fun isPoisonLike(address: String, knownAddresses: List<String>): Boolean {
    val normalized = address.trim().lowercase(Locale.US)
    if (normalized.length < 10) return false
    return knownAddresses.any { known ->
        val candidate = known.trim().lowercase(Locale.US)
        candidate != normalized &&
            candidate.length >= 10 &&
            candidate.take(6) == normalized.take(6) &&
            candidate.takeLast(4) == normalized.takeLast(4)
    }
}

private fun SendAssetRow.maxSendAmount(feeQuote: SendFeeQuote): BigDecimal =
    if (asset.assetType == "NATIVE") {
        (balanceDecimal - feeQuote.amount).takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ZERO
    } else {
        balanceDecimal
    }

private fun estimatedFeeFor(
    row: SendAssetRow,
    speed: SendFeeSpeed,
): SendFeeQuote {
    val base = when (row.network.family) {
        "utxo" -> BigDecimal("0.00005000")
        "evm" -> BigDecimal("0.00030000")
        "solana" -> BigDecimal("0.00000500")
        "tron" -> BigDecimal("1")
        "ripple", "stellar" -> BigDecimal("0.00001000")
        else -> BigDecimal("0.001")
    }
    val multiplier = when (speed) {
        SendFeeSpeed.Slow -> BigDecimal("0.7")
        SendFeeSpeed.Normal -> BigDecimal.ONE
        SendFeeSpeed.Fast -> BigDecimal("1.5")
    }
    val amount = base.multiply(multiplier).setScale(8, RoundingMode.UP)
    return SendFeeQuote(
        speed = speed,
        amount = amount,
        symbol = row.network.nativeSymbol,
        displayText = "${formatCryptoAmount(amount)} ${row.network.nativeSymbol}",
    )
}

private fun String.applyAmountKey(key: String): String =
    when (key) {
        "⌫" -> dropLast(1)
        "." -> if (contains(".")) this else if (isBlank()) "0." else "$this."
        else -> {
            val next = if (this == "0") key else this + key
            next.filterAmountInput()
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

private fun formatSendDateTime(timestampMillis: Long): String =
    if (timestampMillis <= 0L) {
        ""
    } else {
        DateTimeFormatter
            .ofPattern("MMM d, HH:mm", Locale.US)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timestampMillis))
    }

private fun String.shortAddress(): String =
    if (length <= 14) this else "${take(6)}…${takeLast(6)}"

private fun explorerUrlFor(networkId: String, hash: String): String? =
    when (networkId) {
        "ethereum" -> "https://etherscan.io/tx/$hash"
        "arbitrum" -> "https://arbiscan.io/tx/$hash"
        "base" -> "https://basescan.org/tx/$hash"
        "optimism" -> "https://optimistic.etherscan.io/tx/$hash"
        "polygon" -> "https://polygonscan.com/tx/$hash"
        "bnbChain" -> "https://bscscan.com/tx/$hash"
        "avalanche" -> "https://snowtrace.io/tx/$hash"
        "bitcoin" -> "https://mempool.space/tx/$hash"
        "bitcoinCash" -> "https://blockchair.com/bitcoin-cash/transaction/$hash"
        "dogecoin" -> "https://blockchair.com/dogecoin/transaction/$hash"
        "litecoin" -> "https://blockchair.com/litecoin/transaction/$hash"
        "solana" -> "https://solscan.io/tx/$hash"
        "tron" -> "https://tronscan.org/#/transaction/$hash"
        else -> null
    }

private fun shareReceipt(
    context: android.content.Context,
    state: SendReceiptState.Content,
) {
    val text = context.getString(
        R.string.send_receipt_share_text,
        state.amountFormatted,
        state.network.displayName,
        state.transaction.toAddress?.shortAddress().orEmpty(),
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(
        Intent.createChooser(
            intent,
            context.getString(R.string.send_share_chooser_title),
        ),
    )
}

private fun kotlinx.coroutines.CoroutineScope.launchCatching(
    onError: (Throwable) -> Unit,
    block: suspend () -> Unit,
) {
    launch {
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

private sealed interface SendDetailsState {
    data object Loading : SendDetailsState
    data object Empty : SendDetailsState
    data class Content(
        val wallet: WalletRecord,
        val row: SendAssetRow,
        val canSign: Boolean,
        val addressBookEntries: List<AddressBookEntryRecord>,
        val recentRecipients: List<SendRecentRecipient>,
    ) : SendDetailsState
}

private sealed interface SendReceiptState {
    data object Loading : SendReceiptState
    data object Empty : SendReceiptState
    data class Content(
        val wallet: WalletRecord,
        val transaction: WalletTransactionRecord,
        val asset: SupportedAsset,
        val network: SupportedNetwork,
        val amountFormatted: String,
        val explorerUrl: String?,
    ) : SendReceiptState
}

private data class SendAssetGroup(
    val symbol: String,
    val name: String,
    val assets: List<SendAssetRow>,
    val totalFiat: BigDecimal,
    val totalBalance: BigDecimal,
    val totalFiatFormatted: String,
    val totalBalanceFormatted: String,
    @DrawableRes val iconRes: Int,
    val canSend: Boolean,
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

private data class SendRecentRecipient(
    val label: String,
    val address: String,
)

private data class SendFeeQuote(
    val speed: SendFeeSpeed,
    val amount: BigDecimal,
    val symbol: String,
    val displayText: String,
)

private enum class SendFeeSpeed(val labelRes: Int) {
    Slow(R.string.send_fee_speed_slow),
    Normal(R.string.send_fee_speed_normal),
    Fast(R.string.send_fee_speed_fast),
}

private val SendContentMaxWidth = 720.dp
private const val CRYPTO_DISPLAY_DECIMALS = 8
