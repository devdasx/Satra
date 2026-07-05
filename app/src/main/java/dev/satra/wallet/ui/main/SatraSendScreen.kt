package dev.satra.wallet.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satra.wallet.R
import dev.satra.wallet.data.assets.SupportedAsset
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.assets.SupportedNetwork
import dev.satra.wallet.data.db.AddressBookEntryRecord
import dev.satra.wallet.data.db.SatraWalletRepository
import dev.satra.wallet.data.db.WalletAddressRecord
import dev.satra.wallet.data.db.WalletAssetRecord
import dev.satra.wallet.data.db.WalletPrivateKeyRecord
import dev.satra.wallet.data.db.WalletRecord
import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionRecord
import dev.satra.wallet.data.db.WalletTransactionStatus
import dev.satra.wallet.data.send.SatraSendException
import dev.satra.wallet.data.send.SatraSendService
import dev.satra.wallet.ui.components.SatraButton
import dev.satra.wallet.ui.components.SatraButtonDefaults
import dev.satra.wallet.ui.components.SatraButtonVariant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    initialWalletSnapshot: SatraMainWalletSnapshot?,
    onWalletSnapshotLoaded: (SatraMainWalletSnapshot) -> Unit,
    onBack: () -> Unit,
    onAssetSelected: (String) -> Unit,
    onNetworkRequired: (String) -> Unit,
) {
    var state by remember {
        mutableStateOf(
            initialWalletSnapshot
                ?.takeIf { snapshot -> snapshot.hasSendData() }
                ?.toSendSnapshot()
                ?.toAssetSelectionState()
                ?: SendAssetScreenState.Loading,
        )
    }

    LaunchedEffect(initialWalletSnapshot) {
        initialWalletSnapshot?.takeIf { snapshot -> snapshot.hasSendData() }?.let { snapshot ->
            state = snapshot.toSendSnapshot().toAssetSelectionState()
        }
    }

    LaunchedEffect(walletRepository, initialWalletSnapshot?.walletId) {
        if (initialWalletSnapshot.hasSendData()) return@LaunchedEffect
        val snapshot = walletRepository.loadMainWalletSnapshot(
            includeTransactions = false,
        )
        onWalletSnapshotLoaded(snapshot)
        state = snapshot.toSendSnapshot().toAssetSelectionState()
    }

    when (val current = state) {
        SendAssetScreenState.Loading -> SendLoadingScreen(
            title = stringResource(R.string.send_choose_asset_title),
            onBack = onBack,
        )

        SendAssetScreenState.Empty -> SendEmptyScreen(
            title = stringResource(R.string.send_choose_asset_title),
            emptyTitle = stringResource(R.string.send_empty_title),
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
    initialWalletSnapshot: SatraMainWalletSnapshot?,
    onWalletSnapshotLoaded: (SatraMainWalletSnapshot) -> Unit,
    symbol: String,
    onBack: () -> Unit,
    onNetworkSelected: (String) -> Unit,
) {
    var state by remember(symbol) {
        mutableStateOf(
            initialWalletSnapshot
                ?.takeIf { snapshot -> snapshot.hasSendData() }
                ?.toSendSnapshot()
                ?.toNetworkSelectionState(Uri.decode(symbol))
                ?: SendNetworkScreenState.Loading,
        )
    }

    LaunchedEffect(initialWalletSnapshot, symbol) {
        initialWalletSnapshot?.takeIf { snapshot -> snapshot.hasSendData() }?.let { snapshot ->
            state = snapshot.toSendSnapshot().toNetworkSelectionState(Uri.decode(symbol))
        }
    }

    LaunchedEffect(walletRepository, symbol, initialWalletSnapshot?.walletId) {
        if (initialWalletSnapshot.hasSendData()) return@LaunchedEffect
        val snapshot = walletRepository.loadMainWalletSnapshot(
            includeTransactions = false,
        )
        onWalletSnapshotLoaded(snapshot)
        state = snapshot.toSendSnapshot().toNetworkSelectionState(Uri.decode(symbol))
    }

    when (val current = state) {
        SendNetworkScreenState.Loading -> SendLoadingScreen(
            title = stringResource(R.string.send_choose_network_title),
            onBack = onBack,
        )

        SendNetworkScreenState.Empty -> SendEmptyScreen(
            title = stringResource(R.string.send_choose_network_title),
            emptyTitle = stringResource(R.string.send_network_empty_title),
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
    initialWalletSnapshot: SatraMainWalletSnapshot?,
    onWalletSnapshotLoaded: (SatraMainWalletSnapshot) -> Unit,
    assetId: String,
    scannedAddress: String,
    onBack: () -> Unit,
    onScanClick: () -> Unit,
    onScannedAddressConsumed: () -> Unit,
    onContinue: (assetId: String, recipient: String, warnPoison: Boolean) -> Unit,
) {
    var state by remember(assetId) {
        mutableStateOf(
            initialWalletSnapshot?.toSendDetailsState(Uri.decode(assetId))
                ?: SendDetailsState.Loading,
        )
    }

    LaunchedEffect(initialWalletSnapshot, assetId) {
        initialWalletSnapshot?.toSendDetailsState(Uri.decode(assetId))?.let { cachedState ->
            state = cachedState
        }
    }

    LaunchedEffect(walletRepository, assetId, initialWalletSnapshot?.walletId) {
        state = walletRepository.loadSendDetails(Uri.decode(assetId))
        onWalletSnapshotLoaded(walletRepository.loadMainWalletSnapshot())
    }

    when (val current = state) {
        SendDetailsState.Loading -> SendLoadingScreen(
            title = stringResource(R.string.send_recipient_title),
            onBack = onBack,
        )

        SendDetailsState.Empty -> SendEmptyScreen(
            title = stringResource(R.string.send_recipient_title),
            emptyTitle = stringResource(R.string.send_asset_empty_title),
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
    initialWalletSnapshot: SatraMainWalletSnapshot?,
    onWalletSnapshotLoaded: (SatraMainWalletSnapshot) -> Unit,
    assetId: String,
    recipient: String,
    warnPoison: Boolean,
    onBack: () -> Unit,
    onReview: (assetId: String, recipient: String, amount: String, warnPoison: Boolean) -> Unit,
) {
    var state by remember(assetId) {
        mutableStateOf(
            initialWalletSnapshot?.toSendDetailsState(Uri.decode(assetId))
                ?: SendDetailsState.Loading,
        )
    }

    LaunchedEffect(initialWalletSnapshot, assetId) {
        initialWalletSnapshot?.toSendDetailsState(Uri.decode(assetId))?.let { cachedState ->
            state = cachedState
        }
    }

    LaunchedEffect(walletRepository, assetId, initialWalletSnapshot?.walletId) {
        state = walletRepository.loadSendDetails(Uri.decode(assetId))
        onWalletSnapshotLoaded(walletRepository.loadMainWalletSnapshot())
    }

    when (val current = state) {
        SendDetailsState.Loading -> SendLoadingScreen(
            title = stringResource(R.string.send_amount_title),
            onBack = onBack,
        )

        SendDetailsState.Empty -> SendEmptyScreen(
            title = stringResource(R.string.send_amount_title),
            emptyTitle = stringResource(R.string.send_asset_empty_title),
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
    initialWalletSnapshot: SatraMainWalletSnapshot?,
    onWalletSnapshotLoaded: (SatraMainWalletSnapshot) -> Unit,
    assetId: String,
    recipient: String,
    amount: String,
    warnPoison: Boolean,
    onBack: () -> Unit,
    onCheckRecipient: () -> Unit,
    onSent: (String) -> Unit,
) {
    var state by remember(assetId) {
        mutableStateOf(
            initialWalletSnapshot?.toSendDetailsState(Uri.decode(assetId))
                ?: SendDetailsState.Loading,
        )
    }

    LaunchedEffect(initialWalletSnapshot, assetId) {
        initialWalletSnapshot?.toSendDetailsState(Uri.decode(assetId))?.let { cachedState ->
            state = cachedState
        }
    }

    LaunchedEffect(walletRepository, assetId, initialWalletSnapshot?.walletId) {
        state = walletRepository.loadSendDetails(Uri.decode(assetId))
        onWalletSnapshotLoaded(walletRepository.loadMainWalletSnapshot())
    }

    when (val current = state) {
        SendDetailsState.Loading -> SendLoadingScreen(
            title = stringResource(R.string.send_review_screen_title),
            onBack = onBack,
        )

        SendDetailsState.Empty -> SendEmptyScreen(
            title = stringResource(R.string.send_review_screen_title),
            emptyTitle = stringResource(R.string.send_asset_empty_title),
            body = stringResource(R.string.send_asset_empty_body),
            onBack = onBack,
        )

        is SendDetailsState.Content -> SendReviewContent(
            walletRepository = walletRepository,
            state = current,
            recipient = Uri.decode(recipient),
            amountText = Uri.decode(amount),
            warnPoison = warnPoison,
            onBack = onBack,
            onCheckRecipient = onCheckRecipient,
            onSent = onSent,
        )
    }
}

@Composable
fun SatraSendSentScreen(
    walletRepository: SatraWalletRepository,
    initialWalletSnapshot: SatraMainWalletSnapshot?,
    onWalletSnapshotLoaded: (SatraMainWalletSnapshot) -> Unit,
    transactionId: String,
    onDone: () -> Unit,
    onSendAnother: () -> Unit,
) {
    val nativeAssetLabel = stringResource(R.string.asset_type_native)
    var state by remember(transactionId) {
        mutableStateOf<SendReceiptState>(SendReceiptState.Loading)
    }

    LaunchedEffect(walletRepository, transactionId, initialWalletSnapshot?.walletId, nativeAssetLabel) {
        while (true) {
            val receipt = walletRepository.loadSendReceipt(Uri.decode(transactionId), nativeAssetLabel)
            state = receipt
            onWalletSnapshotLoaded(walletRepository.loadMainWalletSnapshot())
            val isPending = (receipt as? SendReceiptState.Content)
                ?.transaction
                ?.status == WalletTransactionStatus.Pending.value
            if (!isPending) break
            delay(SEND_RECEIPT_POLL_INTERVAL_MS)
        }
    }

    when (val current = state) {
        SendReceiptState.Loading -> SendReceiptLoadingScreen()

        SendReceiptState.Empty -> SendReceiptEmptyScreen(onDone = onDone)

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

    SatraChooseAssetScaffold(
        title = stringResource(R.string.send_choose_asset_title),
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
                items(fundedGroups, key = { group -> "funded-${group.symbol}" }) { group ->
                    SendAssetGroupRow(
                        group = group,
                        showSecondaryAmount = true,
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
                val title = if (fundedGroups.isEmpty()) {
                    R.string.send_section_your_assets
                } else {
                    R.string.send_section_all_assets
                }
                item { ChooseAssetSectionHeader(title = stringResource(title)) }
                items(unfundedGroups, key = { group -> "all-${group.symbol}" }) { group ->
                    SendAssetGroupRow(
                        group = group,
                        showSecondaryAmount = false,
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
    val nativeAssetLabel = stringResource(R.string.asset_type_native)
    SatraChooseAssetScaffold(
        title = stringResource(R.string.send_choose_network_title),
        onBack = onBack,
    ) {
        item {
            ChooseNetworkContextLine(
                symbol = state.symbol,
                networkCount = state.assets.size,
                iconRes = assetIconRes(state.symbol),
            )
        }
        items(state.assets, key = { row -> row.asset.assetId }) { row ->
            ChooseNetworkRow(
                networkName = row.network.displayName,
                standard = row.networkStandardLabel(nativeAssetLabel),
                primaryAmount = formatCryptoAmount(row.balanceDecimal),
                secondaryAmount = if (row.fiatAmount > BigDecimal.ZERO) {
                    row.fiatFormatted
                } else {
                    stringResource(R.string.send_network_empty_value)
                },
                iconRes = networkIconRes(row.network.networkId),
                enabled = row.hasSigningKey,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNetworkSelected(row.asset.assetId)
                },
            )
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
    val showError = recipient.length >= RECIPIENT_ERROR_MIN_LENGTH && !isValid
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

    SendStepScaffold(
        title = stringResource(R.string.send_recipient_title),
        onBack = onBack,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = ChooseAssetContentMaxWidth),
                ) {
                    SendRecipientContextLine(row = state.row)
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
                    SendRecipientAddressBox(
                        value = recipient,
                        networkName = state.row.network.displayName,
                        showError = showError,
                        onValueChange = { recipient = it.trim() },
                        onPasteClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            clipboard.getText()?.text?.takeIf(String::isNotBlank)?.let { recipient = it.trim() }
                        },
                        onScanClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onScanClick()
                        },
                        onBookClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            bookVisible = true
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    when {
                        showError -> SendRecipientInlineMessage(
                            text = stringResource(R.string.send_recipient_error),
                            isError = true,
                        )
                        warnPoison -> SendRecipientInlineMessage(
                            text = stringResource(R.string.send_recipient_poison_body),
                            isError = true,
                            strong = true,
                        )
                        !isKnown && recipient.isNotBlank() && isValid -> SendRecipientInlineMessage(
                            text = stringResource(R.string.send_recipient_never_sent_body),
                        )
                    }
                    if (state.recentRecipients.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.send_recent_recipients_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        state.recentRecipients.take(3).forEach { recent ->
                            SendRecentRecipientRow(
                                title = recent.label,
                                body = recent.address.shortAddress(),
                                onClick = { recipient = recent.address },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
            SendRecipientBottomAction(
                enabled = canContinue,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onContinue(state.row.asset.assetId, recipient.trim(), warnPoison)
                },
            )
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
    val amountOverBalance = amount != null && amount > maxAmount
    val canReview = amount != null && amount > BigDecimal.ZERO && amount <= maxAmount && state.canSign
    val fiatPreview = remember(amount, state.row) {
        state.row.fiatValueForAmount(amount ?: BigDecimal.ZERO)
    }

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

    SendStepScaffold(
        title = stringResource(R.string.send_amount_title),
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = ChooseAssetContentMaxWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SendAmountDisplay(
                        amountText = amountText,
                        symbol = state.row.asset.symbol,
                        fiatPreview = formatFiat(fiatPreview.toPlainString(), state.wallet.localCurrencyCode),
                        isOverBalance = amountOverBalance,
                    )
                    SendAmountAvailableRow(
                        balance = state.row.balanceFormatted,
                        networkName = state.row.network.displayName,
                        onMaxClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            amountText = formatCryptoAmount(maxAmount)
                        },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                SendKeypad(
                    onKey = { key ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        amountText = amountText.applyAmountKey(key)
                    },
                )
                Spacer(modifier = Modifier.height(14.dp))
                SendFeePill(
                    quote = feeQuote,
                    onClick = { feeSheetVisible = true },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            SendStepBottomAction(
                text = stringResource(R.string.send_review_action),
                enabled = canReview,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onReview(state.row.asset.assetId, recipient, amountText, warnPoison)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendReviewContent(
    walletRepository: SatraWalletRepository,
    state: SendDetailsState.Content,
    recipient: String,
    amountText: String,
    warnPoison: Boolean,
    onBack: () -> Unit,
    onCheckRecipient: () -> Unit,
    onSent: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val nativeAssetLabel = stringResource(R.string.asset_type_native)
    val scope = rememberCoroutineScope()
    var submitError by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var showPoisonSheet by remember(warnPoison) { mutableStateOf(false) }
    val amount = amountText.toBigDecimalOrNullSafe() ?: BigDecimal.ZERO
    val feeQuote = estimatedFeeFor(state.row, SendFeeSpeed.Normal)
    val maxAmount = remember(state.row, feeQuote) { state.row.maxSendAmount(feeQuote) }
    val amountFormatted = "${formatCryptoAmount(amount)} ${state.row.asset.symbol}"
    val fiatPreview = remember(amount, state.row) { state.row.fiatValueForAmount(amount) }
    val unsupportedNetworkBody = stringResource(
        R.string.send_broadcast_unsupported_network_body,
        state.row.network.displayName,
    )
    val missingKeyBody = stringResource(R.string.send_broadcast_missing_key_body)
    val invalidRecipientBody = stringResource(R.string.send_broadcast_invalid_recipient_body)
    val invalidAmountBody = stringResource(R.string.send_broadcast_invalid_amount_body)
    val insufficientBalanceBody = stringResource(R.string.send_broadcast_insufficient_balance_body)
    val broadcastFailedBody = stringResource(R.string.send_broadcast_failed_body)
    val canSubmit = state.canSign && amount > BigDecimal.ZERO && amount <= maxAmount

    fun submit() {
        if (!canSubmit || sending) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        submitError = null
        sending = true
        scope.launch {
            runCatching {
                walletRepository.signAndBroadcastSend(
                    assetId = state.row.asset.assetId,
                    recipientAddress = recipient,
                    amountDecimal = amount,
                )
            }.onSuccess { transactionId ->
                sending = false
                onSent(transactionId)
            }.onFailure { error ->
                sending = false
                submitError = when (error) {
                    is SatraSendException.UnsupportedNetwork -> unsupportedNetworkBody
                    is SatraSendException.MissingSigningKey -> missingKeyBody
                    is SatraSendException.InvalidRecipient -> invalidRecipientBody
                    is SatraSendException.InvalidAmount -> invalidAmountBody
                    is SatraSendException.InsufficientBalance -> insufficientBalanceBody
                    else -> broadcastFailedBody
                }
            }
        }
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
                    text = stringResource(R.string.send_poison_review_again),
                    onClick = {
                        showPoisonSheet = false
                        onCheckRecipient()
                    },
                )
                SatraButton(
                    text = stringResource(R.string.send_poison_send_anyway),
                    onClick = {
                        showPoisonSheet = false
                        submit()
                    },
                    enabled = canSubmit && !sending,
                    modifier = Modifier.fillMaxWidth(),
                    variant = SatraButtonVariant.Secondary,
                    height = SatraButtonDefaults.CompactHeight,
                    contentColor = MaterialTheme.colorScheme.error,
                    borderColor = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    SendStepScaffold(
        title = stringResource(R.string.send_review_screen_title),
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = ChooseAssetContentMaxWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SendReviewHeaderBlock(
                        row = state.row,
                        amountFormatted = amountFormatted,
                        fiatPreview = formatFiat(fiatPreview.toPlainString(), state.wallet.localCurrencyCode),
                    )
                    SendReviewFactCard(
                        rows = listOf(
                            stringResource(R.string.send_review_to) to recipient.shortAddress(),
                            stringResource(R.string.send_review_network) to
                                "${state.row.network.displayName} · ${state.row.networkStandardLabel(nativeAssetLabel)}",
                            stringResource(R.string.send_review_fee) to "~${feeQuote.displayText}",
                            stringResource(R.string.send_review_total) to
                                stringResource(R.string.send_review_total_plus_fee, amountFormatted),
                        ),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    SendReviewWarningStrip(
                        text = stringResource(R.string.send_final_warning_body, state.row.network.displayName),
                        emphasizedText = state.row.network.displayName,
                    )
                    submitError?.let { error ->
                        Spacer(modifier = Modifier.height(14.dp))
                        SendWarningCard(
                            title = stringResource(R.string.send_prepare_failed_title),
                            body = error,
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
            SendReviewBottomAction(
                enabled = canSubmit,
                loading = sending,
                onClick = {
                    if (warnPoison) {
                        showPoisonSheet = true
                    } else {
                        submit()
                    }
                },
            )
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
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    val transactionClipboardLabel = stringResource(R.string.send_receipt_hash)
    val transactionHash = state.transaction.transactionHash?.takeIf(String::isNotBlank)
    var copiedTransaction by remember(transactionHash) { mutableStateOf(false) }
    LaunchedEffect(copiedTransaction) {
        if (copiedTransaction) {
            delay(SEND_RECEIPT_COPY_RESET_MS)
            copiedTransaction = false
        }
    }

    val status = rememberReceiptStatus(
        status = state.transaction.status,
        networkName = state.network.displayName,
    )
    val savedTime = formatSendDateTime(state.transaction.timestamp)
        .takeIf(String::isNotBlank)
        ?: stringResource(R.string.send_review_pending)
    val amountWithFiat = stringResource(
        R.string.send_receipt_amount_with_fiat,
        state.amountFormatted,
        state.fiatFormatted,
    )
    val networkWithStandard = "${state.network.displayName} · ${state.networkStandardLabel}"
    val transactionValue = if (copiedTransaction) {
        stringResource(R.string.send_receipt_copied)
    } else {
        transactionHash?.shortHash() ?: stringResource(R.string.send_review_pending)
    }

    SendReceiptScaffold {
        SendReceiptStatusBlock(status = status)
        SendReceiptFactCard {
            SendReceiptFactRow(
                label = stringResource(R.string.send_review_amount),
                value = amountWithFiat,
            )
            SendReceiptFactRow(
                label = stringResource(R.string.send_review_to),
                value = state.transaction.toAddress?.shortAddress()
                    ?: stringResource(R.string.send_review_pending),
            )
            SendReceiptNetworkFactRow(
                label = stringResource(R.string.send_review_network),
                value = networkWithStandard,
                networkId = state.network.networkId,
            )
            SendReceiptFactRow(
                label = stringResource(R.string.send_review_fee),
                value = "~${state.networkFeeFormatted}",
            )
            SendReceiptFactRow(
                label = stringResource(R.string.send_receipt_hash),
                value = transactionValue,
                showDivider = true,
                onClick = transactionHash?.let { hash ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        clipboardManager?.setPrimaryClip(
                            ClipData.newPlainText(
                                transactionClipboardLabel,
                                hash,
                            ),
                        )
                        copiedTransaction = true
                    }
                },
            )
            SendReceiptFactRow(
                label = stringResource(R.string.send_receipt_saved),
                value = savedTime,
                showDivider = false,
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SendReceiptGhostButton(
                text = stringResource(R.string.send_receipt_share),
                iconRes = R.drawable.ic_send_share,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    shareReceipt(context, state)
                },
                modifier = Modifier.weight(1f),
            )
            SendReceiptGhostButton(
                text = stringResource(R.string.send_receipt_explorer),
                iconRes = R.drawable.ic_send_external,
                enabled = state.explorerUrl != null,
                onClick = {
                    state.explorerUrl?.let { url ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        if (state.transaction.status == WalletTransactionStatus.Failed.value) {
            Spacer(modifier = Modifier.height(10.dp))
            SendReceiptGhostButton(
                text = stringResource(R.string.send_receipt_contact_support),
                iconRes = R.drawable.ic_send_mail,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    contactSupportForReceipt(
                        context = context,
                        state = state,
                        transactionId = transactionHash.orEmpty(),
                        time = savedTime,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                contentColor = status.tint,
                borderColor = status.tint,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        SatraButton(
            text = stringResource(R.string.send_receipt_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            height = 50.dp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        SatraButton(
            text = stringResource(R.string.send_receipt_send_another),
            onClick = onSendAnother,
            modifier = Modifier.fillMaxWidth(),
            variant = SatraButtonVariant.Secondary,
            height = 50.dp,
        )
        SendReceiptFooter()
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
        SatraFlowTopBar(
            title = title,
            onBack = onBack,
        )
        content()
    }
}

@Composable
private fun SendStepScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SatraFlowTopBar(
            title = title,
            onBack = onBack,
        )
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
    emptyTitle: String,
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
            SatraEmptyState(
                title = emptyTitle,
                body = body,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SendReceiptLoadingScreen() {
    SendReceiptScaffold {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SendReceiptEmptyScreen(onDone: () -> Unit) {
    SendReceiptScaffold {
        Spacer(modifier = Modifier.height(80.dp))
        SatraEmptyState(
            title = stringResource(R.string.send_receipt_empty_title),
            body = stringResource(R.string.send_receipt_empty_body),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(24.dp))
        SatraButton(
            text = stringResource(R.string.send_receipt_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            height = 50.dp,
        )
    }
}

@Composable
private fun SendReceiptScaffold(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.satra_lockup_horizontal),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 20.dp, bottom = 6.dp)
                .width(86.dp)
                .height(31.dp),
            contentScale = ContentScale.Fit,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = ChooseAssetContentMaxWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

@Composable
private fun rememberReceiptStatus(
    status: String,
    networkName: String,
): SendReceiptVisuals {
    val isDark = isSystemInDarkTheme()
    val gain = if (isDark) Color(0xFF7FC9A6) else Color(0xFF2E7D5A)
    val loss = if (isDark) Color(0xFFE08A76) else Color(0xFFB3452E)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val chipStrong = MaterialTheme.colorScheme.surfaceContainerHighest
    val lossContainer = MaterialTheme.colorScheme.errorContainer
    return when (status) {
        WalletTransactionStatus.Success.value -> SendReceiptVisuals(
            title = stringResource(R.string.send_sent_status_success_title),
            chip = stringResource(R.string.send_status_success),
            body = stringResource(R.string.send_sent_status_success_body, networkName),
            chipContainer = gain.copy(alpha = 0.14f),
            tint = gain,
            discContainer = MaterialTheme.colorScheme.inverseSurface,
            discContent = MaterialTheme.colorScheme.inverseOnSurface,
            isPending = false,
            isFailed = false,
        )
        WalletTransactionStatus.Failed.value -> SendReceiptVisuals(
            title = stringResource(R.string.send_sent_status_failed_title),
            chip = stringResource(R.string.send_status_failed),
            body = stringResource(R.string.send_sent_status_failed_body),
            chipContainer = lossContainer,
            tint = loss,
            discContainer = lossContainer,
            discContent = loss,
            isPending = false,
            isFailed = true,
        )
        WalletTransactionStatus.Canceled.value -> SendReceiptVisuals(
            title = stringResource(R.string.send_sent_status_canceled_title),
            chip = stringResource(R.string.send_status_canceled),
            body = stringResource(R.string.send_sent_status_canceled_body),
            chipContainer = chipStrong,
            tint = muted,
            discContainer = MaterialTheme.colorScheme.inverseSurface,
            discContent = MaterialTheme.colorScheme.inverseOnSurface,
            isPending = false,
            isFailed = false,
        )
        else -> SendReceiptVisuals(
            title = stringResource(R.string.send_sent_status_pending_title),
            chip = stringResource(R.string.send_status_pending),
            body = stringResource(R.string.send_sent_status_pending_body),
            chipContainer = chipStrong,
            tint = muted,
            discContainer = MaterialTheme.colorScheme.inverseSurface,
            discContent = MaterialTheme.colorScheme.inverseOnSurface,
            isPending = true,
            isFailed = false,
        )
    }
}

@Composable
private fun SendReceiptStatusBlock(status: SendReceiptVisuals) {
    Column(
        modifier = Modifier.padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(status.discContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (status.isFailed) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = status.discContent,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_brand_move),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = status.discContent,
                )
            }
        }
        Text(
            text = status.title,
            modifier = Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SendReceiptStatusChip(status = status)
        Text(
            text = status.body,
            modifier = Modifier
                .padding(top = 12.dp)
                .widthIn(max = 280.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 21.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SendReceiptStatusChip(status: SendReceiptVisuals) {
    val transition = rememberInfiniteTransition(label = "receiptStatusChip")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "receiptStatusDotAlpha",
    )
    Row(
        modifier = Modifier
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(status.chipContainer)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .alpha(if (status.isPending) pulseAlpha else 1f)
                .clip(CircleShape)
                .background(status.tint),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = status.chip,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
            color = status.tint,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SendReceiptFactCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SendReceiptFactRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    SendReceiptFactRowBase(
        label = label,
        onClick = onClick,
        showDivider = showDivider,
    ) {
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.5.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SendReceiptNetworkFactRow(
    label: String,
    value: String,
    networkId: String,
    showDivider: Boolean = true,
) {
    SendReceiptFactRowBase(
        label = label,
        showDivider = showDivider,
    ) {
        Text(
            text = value,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.5.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(5.dp))
        SatraCryptoIcon(
            iconRes = networkIconRes(networkId),
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun SendReceiptFactRowBase(
    label: String,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    valueContent: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = valueContent,
        )
    }
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SendReceiptGhostButton(
    text: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    SatraButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        variant = SatraButtonVariant.Secondary,
        height = 50.dp,
        contentColor = contentColor,
        borderColor = borderColor,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SendReceiptFooter() {
    val footer = stringResource(R.string.send_receipt_footer)
    val brand = stringResource(R.string.send_receipt_footer_brand)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val brandColor = MaterialTheme.colorScheme.onSurface
    val footerText = remember(footer, brand, textColor, brandColor) {
        val start = footer.indexOf(brand)
        if (start < 0) {
            buildAnnotatedString { append(footer) }
        } else {
            buildAnnotatedString {
                append(footer.take(start))
                withStyle(SpanStyle(color = brandColor, fontWeight = FontWeight.Bold)) {
                    append(brand)
                }
                append(footer.drop(start + brand.length))
            }
        }
    }
    Text(
        text = footerText,
        modifier = Modifier.padding(top = 14.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp, lineHeight = 18.sp),
        color = textColor,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SendHero(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = SendContentMaxWidth)
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
private fun SendAssetGroupRow(
    group: SendAssetGroup,
    showSecondaryAmount: Boolean,
    onClick: () -> Unit,
) {
    ChooseAssetRow(
        symbol = group.symbol,
        name = group.name,
        networkCount = group.assets.size,
        primaryAmount = group.totalBalanceValueFormatted,
        secondaryAmount = group.totalFiatFormatted,
        showSecondaryAmount = showSecondaryAmount,
        iconRes = group.iconRes,
        enabled = group.canSend,
        onClick = onClick,
    )
}

@Composable
private fun SendRecipientContextLine(row: SendAssetRow) {
    val nativeAssetLabel = stringResource(R.string.asset_type_native)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.send_recipient_context_sending, row.asset.symbol),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(4.dp))
        SatraCryptoIcon(
            iconRes = row.iconRes,
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.send_recipient_context_on, row.network.displayName),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(4.dp))
        SatraCryptoIcon(
            iconRes = networkIconRes(row.network.networkId),
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.send_recipient_context_standard, row.networkStandardLabel(nativeAssetLabel)),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SendRecipientAddressBox(
    value: String,
    networkName: String,
    showError: Boolean,
    onValueChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onScanClick: () -> Unit,
    onBookClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = if (showError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape,
            )
            .padding(14.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
            ),
            minLines = 2,
            maxLines = 5,
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isBlank()) {
                        Text(
                            text = stringResource(R.string.send_recipient_address_placeholder, networkName),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SendRecipientToolChip(
                text = stringResource(R.string.send_recipient_paste),
                iconRes = R.drawable.ic_send_paste,
                onClick = onPasteClick,
                modifier = Modifier.weight(1f),
            )
            SendRecipientToolChip(
                text = stringResource(R.string.send_recipient_scan),
                iconRes = R.drawable.ic_brand_scan,
                onClick = onScanClick,
                modifier = Modifier.weight(1f),
            )
            SendRecipientToolChip(
                text = stringResource(R.string.send_recipient_book),
                iconRes = R.drawable.ic_send_book,
                onClick = onBookClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SendRecipientToolChip(
    text: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SendRecipientInlineMessage(
    text: String,
    isError: Boolean = false,
    strong: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isError) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 18.sp),
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal,
    )
}

@Composable
private fun SendRecentRecipientRow(
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send_clock),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = body,
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SendAmountDisplay(
    amountText: String,
    symbol: String,
    fiatPreview: String,
    isOverBalance: Boolean,
) {
    Column(
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = amountText.takeIf(String::isNotBlank) ?: "0",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp, lineHeight = 44.sp),
                color = if (isOverBalance) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = symbol,
                modifier = Modifier.padding(bottom = 3.dp),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Text(
            text = fiatPreview,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SendAmountAvailableRow(
    balance: String,
    networkName: String,
    onMaxClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.send_amount_available_on, balance, networkName),
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.send_amount_max),
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onMaxClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SendReviewHeaderBlock(
    row: SendAssetRow,
    amountFormatted: String,
    fiatPreview: String,
) {
    Column(
        modifier = Modifier.padding(top = 18.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SatraCryptoIcon(
            iconRes = row.iconRes,
            modifier = Modifier.size(42.dp),
        )
        Text(
            text = amountFormatted,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, lineHeight = 32.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = fiatPreview,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SendReviewFactCard(rows: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                SendReviewFactRow(
                    label = row.first,
                    value = row.second,
                )
            }
        }
    }
}

@Composable
private fun SendReviewFactRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.5.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SendReviewWarningStrip(text: String) {
    SendReviewWarningStrip(
        text = text,
        emphasizedText = "",
    )
}

@Composable
private fun SendReviewWarningStrip(
    text: String,
    emphasizedText: String,
) {
    val emphasisColor = MaterialTheme.colorScheme.onSurface
    val warningText = remember(text, emphasizedText, emphasisColor) {
        val emphasisStart = emphasizedText
            .takeIf(String::isNotBlank)
            ?.let { text.indexOf(it) }
            ?: -1
        if (emphasisStart < 0) {
            buildAnnotatedString { append(text) }
        } else {
            buildAnnotatedString {
                append(text.take(emphasisStart))
                withStyle(
                    SpanStyle(
                        color = emphasisColor,
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append(emphasizedText)
                }
                append(text.drop(emphasisStart + emphasizedText.length))
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_brand_scan),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = warningText,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    SatraButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        variant = SatraButtonVariant.Secondary,
        height = SatraButtonDefaults.CompactHeight,
    )
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
    SendRecentRecipientRow(
        title = title,
        body = body,
        onClick = onClick,
    )
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
                SatraEmptyState(
                    title = stringResource(R.string.send_address_book_empty_title),
                    body = stringResource(R.string.send_address_book_empty_body),
                    modifier = Modifier.fillMaxWidth(),
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
            .clip(RoundedCornerShape(100.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_brand_settings),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = stringResource(R.string.send_fee_pill_title),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "~${quote.displayText}",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurface,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = ChooseAssetContentMaxWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == "⌫") {
                            Icon(
                                painter = painterResource(R.drawable.ic_satra_backspace),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                            )
                        }
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
    SatraButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        loading = loading,
        modifier = Modifier
            .fillMaxWidth(),
        height = 58.dp,
    )
}

@Composable
private fun SendRecipientBottomAction(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SendStepBottomAction(
        text = stringResource(R.string.send_continue_action),
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun SendStepBottomAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SatraButton(
            text = text,
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = ChooseAssetContentMaxWidth),
            height = 50.dp,
        )
    }
}

@Composable
private fun SendReviewBottomAction(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SatraButton(
            onClick = onClick,
            enabled = enabled,
            loading = loading,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = ChooseAssetContentMaxWidth),
            height = 50.dp,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_brand_move),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.send_now_action),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SendBottomActionBar(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = SendContentMaxWidth),
            content = content,
        )
    }
}

@Composable
private fun SendEmptyInline(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    SatraEmptyState(
        title = title,
        body = body,
        modifier = modifier,
    )
}

private fun SatraMainWalletSnapshot?.hasSendData(): Boolean {
    val snapshot = this ?: return false
    val wallet = snapshot.wallet ?: return false
    return snapshot.addresses.isNotEmpty() &&
        (wallet.isWatchOnly || snapshot.privateKeys.isNotEmpty())
}

private fun SatraMainWalletSnapshot.toSendSnapshot(): SendSnapshot =
    wallet?.let { walletRecord ->
        SendSnapshot.Content(
            wallet = walletRecord,
            assets = assets,
            addresses = addresses,
            privateKeys = privateKeys,
        )
    } ?: SendSnapshot.Empty

private fun SatraMainWalletSnapshot.toSendDetailsState(assetId: String): SendDetailsState =
    when (val snapshot = toSendSnapshot()) {
        SendSnapshot.Empty -> SendDetailsState.Empty
        is SendSnapshot.Content -> {
            val row = snapshot.toSendAssetRows().firstOrNull { candidate -> candidate.asset.assetId == assetId }
                ?: return SendDetailsState.Empty
            SendDetailsState.Content(
                wallet = snapshot.wallet,
                row = row,
                canSign = !snapshot.wallet.isWatchOnly && row.hasSigningKey,
                addressBookEntries = emptyList(),
                recentRecipients = emptyList(),
            )
        }
    }

private fun SatraMainWalletSnapshot.toSendReceiptState(
    transactionId: String,
    nativeAssetLabel: String,
): SendReceiptState =
    when (val walletRecord = wallet) {
        null -> SendReceiptState.Empty
        else -> {
            val transaction = transactions.firstOrNull { it.transactionId == transactionId }
                ?: return SendReceiptState.Empty
            val asset = SupportedAssetCatalog.assets.firstOrNull { it.assetId == transaction.assetId }
                ?: return SendReceiptState.Empty
            val network = SupportedAssetCatalog.networks.firstOrNull { it.networkId == transaction.networkId }
                ?: return SendReceiptState.Empty
            SendReceiptState.Content(
                wallet = walletRecord,
                transaction = transaction,
                asset = asset,
                network = network,
                amountFormatted = transaction.receiptAmountFormatted(asset),
                fiatFormatted = transaction.receiptFiatFormatted(),
                networkFeeFormatted = transaction.receiptFeeFormatted(network),
                networkStandardLabel = networkStandardLabelFor(asset, network, nativeAssetLabel),
                explorerUrl = transaction.transactionHash?.let { hash -> explorerUrlFor(network.networkId, hash) },
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

private suspend fun SatraWalletRepository.loadSendReceipt(
    transactionId: String,
    nativeAssetLabel: String,
): SendReceiptState =
    coroutineScope {
        val transaction = getWalletTransaction(transactionId)
            ?: return@coroutineScope SendReceiptState.Empty
        val wallet = getWallet(transaction.walletId) ?: getPrimaryWallet()
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
            amountFormatted = transaction.receiptAmountFormatted(asset),
            fiatFormatted = transaction.receiptFiatFormatted(),
            networkFeeFormatted = transaction.receiptFeeFormatted(network),
            networkStandardLabel = networkStandardLabelFor(asset, network, nativeAssetLabel),
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
                SendNetworkScreenState.Content(
                    symbol = symbol.uppercase(Locale.US),
                    assets = rows.sortedByNetworkValue(),
                )
            }
        }
    }

private fun SendSnapshot.Content.toSendAssetRows(): List<SendAssetRow> {
    val networksById = SupportedAssetCatalog.networks.associateBy { it.networkId }
    val walletAssetsById = assets.associateBy { it.assetId }
    val addressesByNetwork = addresses
        .filter { address -> address.addressType == "receive" || address.addressType == "watch_only" }
        .groupBy { address -> address.networkId }
    return SupportedAssetCatalog.assets.mapNotNull { asset ->
        val network = networksById[asset.networkId] ?: return@mapNotNull null
        val sourceAddress = addressesByNetwork[asset.networkId]
            .orEmpty()
            .sortedWith(
                compareByDescending<WalletAddressRecord> { it.isPrimary }
                    .thenBy { it.addressIndex ?: Int.MAX_VALUE },
            )
            .firstOrNull()
            ?.address
            ?: return@mapNotNull null
        val walletAsset = walletAssetsById[asset.assetId]
            ?: asset.toZeroBalanceWalletAsset(wallet)
        val balance = walletAsset.balanceDecimal.toBigDecimalOrZero()
        val canSignAndBroadcast = SatraSendService.canSignAndBroadcast(asset, network)
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
            hasSigningKey = canSignAndBroadcast &&
                privateKeys.any { privateKey ->
                    privateKey.networkId == walletAsset.networkId
                },
        )
    }.sortedWith(
        compareByDescending<SendAssetRow> { row -> row.fiatAmount }
            .thenByDescending { row -> row.balanceDecimal }
            .thenBy { row -> row.asset.name.lowercase(Locale.US) }
            .thenBy { row -> row.network.displayName.lowercase(Locale.US) },
    )
}

private fun SupportedAsset.toZeroBalanceWalletAsset(wallet: WalletRecord): WalletAssetRecord =
    WalletAssetRecord(
        walletAssetId = "catalog-${wallet.walletId}-$assetId",
        walletId = wallet.walletId,
        assetId = assetId,
        networkId = networkId,
        isEnabled = true,
        isVisible = true,
        balanceRaw = "0",
        balanceDecimal = "0",
        balanceFiatValue = "0",
        localCurrencyCode = wallet.localCurrencyCode,
        priceFiatValue = "0",
        priceFiatUpdatedAt = null,
        balanceUpdatedAt = null,
        createdAt = 0L,
        updatedAt = 0L,
        metadataJson = "{}",
    )

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
                totalBalanceValueFormatted = formatCryptoAmount(totalBalance),
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
            group.symbol.lowercase(Locale.US).contains(normalized)
    }
}

private fun List<SendAssetRow>.sortedByNetworkValue(): List<SendAssetRow> =
    sortedWith(
        compareByDescending<SendAssetRow> { row -> row.fiatAmount }
            .thenByDescending { row -> row.balanceDecimal }
            .thenBy { row -> row.network.displayName.lowercase(Locale.US) },
    )

private fun SendAssetRow.networkStandardLabel(nativeAssetLabel: String): String =
    networkStandardLabelFor(asset, network, nativeAssetLabel)

private fun networkStandardLabelFor(
    asset: SupportedAsset,
    network: SupportedNetwork,
    nativeAssetLabel: String,
): String =
    asset.tokenStandard
        ?: network.tokenStandard
        ?: if (asset.assetType == "NATIVE") nativeAssetLabel else network.family.uppercase(Locale.US)

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

private fun SendAssetRow.fiatValueForAmount(amount: BigDecimal): BigDecimal {
    if (amount <= BigDecimal.ZERO || balanceDecimal <= BigDecimal.ZERO || fiatAmount <= BigDecimal.ZERO) {
        return BigDecimal.ZERO
    }
    val unitFiat = fiatAmount.divide(balanceDecimal, 18, RoundingMode.HALF_UP)
    return amount.multiply(unitFiat)
}

private fun estimatedFeeFor(
    row: SendAssetRow,
    speed: SendFeeSpeed,
): SendFeeQuote =
    estimatedFeeFor(
        network = row.network,
        speed = speed,
    )

private fun estimatedFeeFor(
    network: SupportedNetwork,
    speed: SendFeeSpeed,
): SendFeeQuote {
    val base = when (network.family) {
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
        symbol = network.nativeSymbol,
        displayText = "${formatCryptoAmount(amount)} ${network.nativeSymbol}",
    )
}

private fun WalletTransactionRecord.receiptAmountFormatted(asset: SupportedAsset): String {
    val amount = amountDecimal.toBigDecimalOrZero().abs()
    return "${formatCryptoAmount(amount)} ${asset.symbol}"
}

private fun WalletTransactionRecord.receiptFiatFormatted(): String =
    formatFiat(
        value = fiatValue?.takeIf(String::isNotBlank) ?: "0",
        currencyCode = localCurrencyCode,
    )

private fun WalletTransactionRecord.receiptFeeFormatted(network: SupportedNetwork): String {
    val feeAmount = feeDecimal
        ?.toBigDecimalOrZero()
        ?.abs()
        ?.takeIf { amount -> amount > BigDecimal.ZERO }
    if (feeAmount != null) {
        val feeSymbol = feeAssetId
            ?.let { assetId -> SupportedAssetCatalog.assets.firstOrNull { asset -> asset.assetId == assetId } }
            ?.symbol
            ?: network.nativeSymbol
        return "${formatCryptoAmount(feeAmount)} $feeSymbol"
    }
    return estimatedFeeFor(network, SendFeeSpeed.Normal).displayText
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
    var fractionDigits = 0
    forEach { character ->
        when {
            character.isDigit() && !hasDecimal -> builder.append(character)
            character.isDigit() && fractionDigits < CRYPTO_DISPLAY_DECIMALS -> {
                fractionDigits += 1
                builder.append(character)
            }
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

private fun String.shortHash(): String =
    if (length <= 18) this else "${take(10)}…${takeLast(6)}"

private fun explorerUrlFor(networkId: String, hash: String): String? =
    when (networkId) {
        "ethereum" -> "https://etherscan.io/tx/$hash"
        "arbitrum" -> "https://arbiscan.io/tx/$hash"
        "base" -> "https://basescan.org/tx/$hash"
        "optimism" -> "https://optimistic.etherscan.io/tx/$hash"
        "scroll" -> "https://scrollscan.com/tx/$hash"
        "zkSync" -> "https://explorer.zksync.io/tx/$hash"
        "polygon" -> "https://polygonscan.com/tx/$hash"
        "bnbChain" -> "https://bscscan.com/tx/$hash"
        "opBNB" -> "https://opbnb.bscscan.com/tx/$hash"
        "avalanche" -> "https://snowtrace.io/tx/$hash"
        "celo" -> "https://celoscan.io/tx/$hash"
        "kavaEvm" -> "https://kavascan.com/tx/$hash"
        "kava" -> "https://www.mintscan.io/kava/txs/$hash"
        "aptos" -> "https://explorer.aptoslabs.com/txn/$hash"
        "near" -> "https://nearblocks.io/txns/$hash"
        "polkadot" -> "https://assethub-polkadot.subscan.io/extrinsic/$hash"
        "ripple" -> "https://xrpscan.com/tx/$hash"
        "bitcoin" -> "https://mempool.space/tx/$hash"
        "bitcoinCash" -> "https://blockchair.com/bitcoin-cash/transaction/$hash"
        "dogecoin" -> "https://blockchair.com/dogecoin/transaction/$hash"
        "litecoin" -> "https://blockchair.com/litecoin/transaction/$hash"
        "solana" -> "https://solscan.io/tx/$hash"
        "stellar" -> "https://stellarchain.io/transactions/$hash"
        "sui" -> "https://suivision.xyz/txblock/$hash"
        "ton" -> "https://tonviewer.com/transaction/$hash"
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

private fun contactSupportForReceipt(
    context: android.content.Context,
    state: SendReceiptState.Content,
    transactionId: String,
    time: String,
) {
    val subject = context.getString(
        R.string.send_receipt_support_subject,
        transactionId.shortHash(),
    )
    val body = context.getString(
        R.string.send_receipt_support_body,
        transactionId,
        state.network.displayName,
        state.amountFormatted,
        time,
    )
    val uri = Uri.parse("mailto:care@satra.app").buildUpon()
        .appendQueryParameter("subject", subject)
        .appendQueryParameter("body", body)
        .build()
    context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
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
        val fiatFormatted: String,
        val networkFeeFormatted: String,
        val networkStandardLabel: String,
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
    val totalBalanceValueFormatted: String,
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

private data class SendReceiptVisuals(
    val title: String,
    val chip: String,
    val body: String,
    val chipContainer: Color,
    val tint: Color,
    val discContainer: Color,
    val discContent: Color,
    val isPending: Boolean,
    val isFailed: Boolean,
)

private enum class SendFeeSpeed(val labelRes: Int) {
    Slow(R.string.send_fee_speed_slow),
    Normal(R.string.send_fee_speed_normal),
    Fast(R.string.send_fee_speed_fast),
}

private val SendContentMaxWidth = 720.dp
private const val CRYPTO_DISPLAY_DECIMALS = 8
private const val RECIPIENT_ERROR_MIN_LENGTH = 8
private const val SEND_RECEIPT_POLL_INTERVAL_MS = 6_000L
private const val SEND_RECEIPT_COPY_RESET_MS = 900L
