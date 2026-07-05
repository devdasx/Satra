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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
            initialWalletSnapshot?.toSendSnapshot()?.toAssetSelectionState()
                ?: SendAssetScreenState.Loading,
        )
    }

    LaunchedEffect(initialWalletSnapshot) {
        initialWalletSnapshot?.let { snapshot ->
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
            initialWalletSnapshot?.toSendSnapshot()?.toNetworkSelectionState(Uri.decode(symbol))
                ?: SendNetworkScreenState.Loading,
        )
    }

    LaunchedEffect(initialWalletSnapshot, symbol) {
        initialWalletSnapshot?.let { snapshot ->
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
    var state by remember(transactionId) {
        mutableStateOf<SendReceiptState>(SendReceiptState.Loading)
    }

    LaunchedEffect(walletRepository, transactionId, initialWalletSnapshot?.walletId) {
        state = walletRepository.loadSendReceipt(Uri.decode(transactionId))
        onWalletSnapshotLoaded(walletRepository.loadMainWalletSnapshot())
    }

    when (val current = state) {
        SendReceiptState.Loading -> SendLoadingScreen(
            title = stringResource(R.string.send_sent_title),
            onBack = onDone,
        )

        SendReceiptState.Empty -> SendEmptyScreen(
            title = stringResource(R.string.send_sent_title),
            emptyTitle = stringResource(R.string.send_receipt_empty_title),
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
                item { ChooseAssetSectionHeader(title = stringResource(R.string.send_section_all_assets)) }
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
                standard = row.networkStandardLabel(),
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
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SendHero(
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
                }
            }
            SendBottomActionBar {
                SendPrimaryButton(
                    text = stringResource(R.string.send_continue_action),
                    enabled = canContinue,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onContinue(state.row.asset.assetId, recipient.trim(), warnPoison)
                    },
                )
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
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SendHero(
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
                    SatraButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            amountText = formatCryptoAmount(maxAmount)
                        },
                        variant = SatraButtonVariant.Text,
                        height = 40.dp,
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
                }
            }
            SendBottomActionBar {
                SendPrimaryButton(
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
    onSent: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var submitError by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var showPoisonSheet by remember(warnPoison) { mutableStateOf(false) }
    val amount = amountText.toBigDecimalOrNullSafe() ?: BigDecimal.ZERO
    val feeQuote = estimatedFeeFor(state.row, SendFeeSpeed.Normal)
    val unsupportedNetworkBody = stringResource(
        R.string.send_broadcast_unsupported_network_body,
        state.row.network.displayName,
    )
    val missingKeyBody = stringResource(R.string.send_broadcast_missing_key_body)
    val invalidRecipientBody = stringResource(R.string.send_broadcast_invalid_recipient_body)
    val invalidAmountBody = stringResource(R.string.send_broadcast_invalid_amount_body)
    val insufficientBalanceBody = stringResource(R.string.send_broadcast_insufficient_balance_body)
    val broadcastFailedBody = stringResource(R.string.send_broadcast_failed_body)
    val canSubmit = state.canSign && amount > BigDecimal.ZERO && amount <= state.row.balanceDecimal

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
                    text = stringResource(R.string.send_poison_send_anyway),
                    enabled = canSubmit,
                    loading = sending,
                    onClick = {
                        showPoisonSheet = false
                        submit()
                    },
                )
                SatraButton(
                    text = stringResource(R.string.send_poison_review_again),
                    onClick = { showPoisonSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    variant = SatraButtonVariant.Secondary,
                    height = SatraButtonDefaults.CompactHeight,
                )
            }
        }
    }

    SendScaffold(
        title = stringResource(R.string.send_review_screen_title),
        onBack = onBack,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SendHero(
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
                }
            }
            SendBottomActionBar {
                SendPrimaryButton(
                    text = stringResource(R.string.send_now_action),
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
}

@Composable
private fun SendReceiptContent(
    state: SendReceiptState.Content,
    onDone: () -> Unit,
    onSendAnother: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val transactionHash = state.transaction.transactionHash?.takeIf(String::isNotBlank)
    val hasBlockchainHash = transactionHash != null
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
                    text = stringResource(
                        if (hasBlockchainHash) {
                            R.string.send_sent_broadcast_title
                        } else {
                            R.string.send_sent_not_broadcast_title
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        if (hasBlockchainHash) {
                            R.string.send_sent_broadcast_body
                        } else {
                            R.string.send_sent_not_broadcast_body
                        },
                    ),
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
                    transactionHash?.let { hash ->
                        SendReviewLine(
                            label = stringResource(R.string.send_receipt_hash),
                            value = hash.shortHash(),
                        )
                    }
                    SendReviewLine(
                        label = stringResource(R.string.send_receipt_saved),
                        value = savedTime,
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SatraButton(
                        text = stringResource(R.string.send_receipt_share),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            shareReceipt(context, state)
                        },
                        modifier = Modifier.weight(1f),
                        variant = SatraButtonVariant.Secondary,
                        height = SatraButtonDefaults.CompactHeight,
                    )
                    SatraButton(
                        text = stringResource(R.string.send_receipt_explorer),
                        onClick = {
                            state.explorerUrl?.let { url ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        enabled = state.explorerUrl != null,
                        modifier = Modifier.weight(1f),
                        variant = SatraButtonVariant.Secondary,
                        height = SatraButtonDefaults.CompactHeight,
                    )
                }
                Spacer(modifier = Modifier.height(26.dp))
                SendPrimaryButton(
                    text = stringResource(R.string.send_receipt_done),
                    onClick = onDone,
                )
                Spacer(modifier = Modifier.height(10.dp))
                SatraButton(
                    text = stringResource(R.string.send_receipt_send_another),
                    onClick = onSendAnother,
                    modifier = Modifier
                        .fillMaxWidth(),
                    variant = SatraButtonVariant.Secondary,
                    height = SatraButtonDefaults.CompactHeight,
                )
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
                    SatraButton(
                        onClick = { onKey(key) },
                        modifier = Modifier
                            .weight(1f),
                        variant = SatraButtonVariant.Neutral,
                        height = 54.dp,
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
    return snapshot.assets.isNotEmpty() &&
        snapshot.addresses.isNotEmpty() &&
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

private fun SatraMainWalletSnapshot.toSendReceiptState(transactionId: String): SendReceiptState =
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
                amountFormatted = "${formatCryptoAmount(transaction.amountDecimal.toBigDecimalOrZero())} ${asset.symbol}",
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

private suspend fun SatraWalletRepository.loadSendReceipt(transactionId: String): SendReceiptState =
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
                SendNetworkScreenState.Content(
                    symbol = symbol.uppercase(Locale.US),
                    assets = rows.sortedByNetworkValue(),
                )
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

private fun SendAssetRow.networkStandardLabel(): String =
    asset.tokenStandard
        ?: network.tokenStandard
        ?: if (asset.assetType == "NATIVE") "Native" else network.family.uppercase(Locale.US)

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

private fun String.shortHash(): String =
    if (length <= 18) this else "${take(10)}…${takeLast(6)}"

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

private enum class SendFeeSpeed(val labelRes: Int) {
    Slow(R.string.send_fee_speed_slow),
    Normal(R.string.send_fee_speed_normal),
    Fast(R.string.send_fee_speed_fast),
}

private val SendContentMaxWidth = 720.dp
private const val CRYPTO_DISPLAY_DECIMALS = 8
