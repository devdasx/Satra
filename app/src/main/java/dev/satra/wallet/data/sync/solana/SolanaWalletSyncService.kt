package dev.satra.wallet.data.sync.solana

import dev.satra.wallet.data.assets.SupportedAsset
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.db.WalletAddressRecord
import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionStatus
import dev.satra.wallet.data.sync.evm.EvmSyncCompleteness
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.util.Locale

class SolanaWalletSyncService(
    private val clientFactory: (SolanaNetworkConfig) -> SolanaRpcClient = { config ->
        SolanaJsonRpcClient(config)
    },
    private val maxParallelSignatureSources: Int = 8,
    private val maxParallelTransactionFetches: Int = 10,
    private val maxSignaturesPerAddress: Int = 150,
) {
    suspend fun syncWallet(
        walletId: String,
        addresses: List<WalletAddressRecord>,
        networkId: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        onNetworkResult: suspend (SolanaNetworkSyncResult) -> Unit = {},
    ): SolanaWalletSyncResult = coroutineScope {
        val requestedNetworks = if (networkId == null) {
            addresses
                .map { it.networkId }
                .filter { it in SolanaProviderRegistry.supportedNetworkIds }
                .toSet()
        } else {
            setOf(networkId).also { SolanaProviderRegistry.requireConfig(networkId) }
        }

        val results = requestedNetworks
            .sorted()
            .map { solanaNetworkId ->
                async {
                    val result = syncNetwork(
                        walletId = walletId,
                        networkId = solanaNetworkId,
                        addresses = addresses.addressesForNetwork(solanaNetworkId),
                        nowMillis = nowMillis,
                        onNetworkResult = onNetworkResult,
                    )
                    runCatching { onNetworkResult(result) }
                    result
                }
            }
            .awaitAll()

        SolanaWalletSyncResult(
            walletId = walletId,
            networkResults = results,
        )
    }

    private suspend fun syncNetwork(
        walletId: String,
        networkId: String,
        addresses: List<WalletAddressRecord>,
        nowMillis: Long,
        onNetworkResult: suspend (SolanaNetworkSyncResult) -> Unit,
    ): SolanaNetworkSyncResult {
        val assets = SupportedAssetCatalog.assets.filter { it.networkId == networkId }
        val walletAddresses = addresses.map { it.address }.distinct()
        val primaryAddress = walletAddresses.firstOrNull()
        if (walletAddresses.isEmpty()) {
            return SolanaNetworkSyncResult(
                walletId = walletId,
                networkId = networkId,
                address = null,
                tokenAccountCount = 0,
                balanceCompleteness = EvmSyncCompleteness.Failed,
                historyCompleteness = EvmSyncCompleteness.Failed,
                balances = emptyList(),
                transactions = emptyList(),
                providerName = null,
                latestSlot = null,
                cursorBeforeSignature = null,
                error = "No wallet address stored for $networkId.",
            )
        }

        val balanceResult = runCatching {
            syncBalances(
                walletAddresses = walletAddresses,
                assets = assets,
                nowMillis = nowMillis,
            )
        }.getOrElse { error ->
            SolanaNetworkBalanceResult(
                balances = emptyList(),
                tokenAccounts = emptyList(),
                providerName = null,
                latestSlot = null,
                completeness = EvmSyncCompleteness.Failed,
                error = error.message,
            )
        }

        runCatching {
            onNetworkResult(
                SolanaNetworkSyncResult(
                    walletId = walletId,
                    networkId = networkId,
                    address = primaryAddress,
                    tokenAccountCount = balanceResult.tokenAccounts.size,
                    balanceCompleteness = balanceResult.completeness,
                    historyCompleteness = EvmSyncCompleteness.Partial,
                    balances = balanceResult.balances,
                    transactions = emptyList(),
                    providerName = balanceResult.providerName,
                    latestSlot = balanceResult.latestSlot,
                    cursorBeforeSignature = null,
                    error = balanceResult.error,
                ),
            )
        }

        val historyResult = runCatching {
            syncHistory(
                walletId = walletId,
                walletAddresses = walletAddresses,
                assets = assets,
                tokenAccounts = balanceResult.tokenAccounts,
                latestSlot = balanceResult.latestSlot,
                nowMillis = nowMillis,
            )
        }.getOrElse { error ->
            SolanaNetworkHistoryResult(
                transactions = emptyList(),
                providerName = null,
                latestSlot = balanceResult.latestSlot,
                completeness = EvmSyncCompleteness.Failed,
                cursorBeforeSignature = null,
                error = error.message,
            )
        }

        return SolanaNetworkSyncResult(
            walletId = walletId,
            networkId = networkId,
            address = primaryAddress,
            tokenAccountCount = balanceResult.tokenAccounts.size,
            balanceCompleteness = balanceResult.completeness,
            historyCompleteness = historyResult.completeness,
            balances = balanceResult.balances,
            transactions = historyResult.transactions,
            providerName = balanceResult.providerName ?: historyResult.providerName,
            latestSlot = maxOfOrNull(balanceResult.latestSlot, historyResult.latestSlot),
            cursorBeforeSignature = historyResult.cursorBeforeSignature,
            error = listOfNotNull(balanceResult.error, historyResult.error)
                .joinToString(" | ")
                .ifBlank { null },
        )
    }

    private suspend fun syncBalances(
        walletAddresses: List<String>,
        assets: List<SupportedAsset>,
        nowMillis: Long,
    ): SolanaNetworkBalanceResult = coroutineScope {
        require(assets.isNotEmpty()) { "No assets supplied for Solana balance sync." }
        require(assets.all { it.networkId == SolanaProviderRegistry.NETWORK_ID }) {
            "All Solana sync assets must belong to the Solana network."
        }

        val config = SolanaProviderRegistry.config
        val client = clientFactory(config)
        val slotDeferred = async { client.slot() }
        val nativeBalanceResults = walletAddresses.map { address ->
            async {
                runCatching { client.nativeBalance(address) }
                    .fold(
                        onSuccess = { SolanaNativeBalanceQueryResult(address, it, null) },
                        onFailure = { error -> SolanaNativeBalanceQueryResult(address, null, error.message) },
                    )
            }
        }
        val tokenAccountResults = walletAddresses.flatMap { address ->
            listOf(
                async {
                    runCatching {
                        client.tokenAccountsByOwner(address, SolanaProviderRegistry.TOKEN_PROGRAM_ID)
                    }.fold(
                        onSuccess = { SolanaTokenAccountQueryResult(address, SolanaProviderRegistry.TOKEN_PROGRAM_ID, it, null) },
                        onFailure = { error -> SolanaTokenAccountQueryResult(address, SolanaProviderRegistry.TOKEN_PROGRAM_ID, null, error.message) },
                    )
                },
                async {
                    runCatching {
                        client.tokenAccountsByOwner(address, SolanaProviderRegistry.TOKEN_2022_PROGRAM_ID)
                    }.fold(
                        onSuccess = { SolanaTokenAccountQueryResult(address, SolanaProviderRegistry.TOKEN_2022_PROGRAM_ID, it, null) },
                        onFailure = { error -> SolanaTokenAccountQueryResult(address, SolanaProviderRegistry.TOKEN_2022_PROGRAM_ID, null, error.message) },
                    )
                },
            )
        }

        val slotResult = slotDeferred.await()
        val nativeResults = nativeBalanceResults.awaitAll()
        val nativeErrors = nativeResults.mapNotNull { result ->
            result.error?.let { "${result.address}: $it" }
        }
        val successfulNativeBalances = nativeResults.mapNotNull { it.result }
        if (successfulNativeBalances.isEmpty()) {
            error("All Solana native balance calls failed: ${nativeErrors.joinToString(" | ")}")
        }
        val nativeAmount = successfulNativeBalances.fold(BigInteger.ZERO) { total, result ->
            total + result.value
        }
        val tokenAccountQueryResults = tokenAccountResults.awaitAll()
        val tokenErrors = tokenAccountQueryResults.mapNotNull { result ->
            result.error?.let { "${result.ownerAddress}/${result.programId}: $it" }
        }
        val tokenAccounts = tokenAccountQueryResults
            .flatMap { it.result?.value.orEmpty() }
            .distinctBy { it.address }
        val tokenTotalsByMint = tokenAccounts
            .groupBy { it.mint }
            .mapValues { (_, accounts) ->
                accounts.fold(BigInteger.ZERO) { total, account -> total + account.amountRaw }
            }
        val tokenAccountsByMint = tokenAccounts.groupBy { it.mint }

        val balances = assets.map { asset ->
            if (asset.assetType == "NATIVE" || asset.contractAddress == null) {
                SolanaAssetBalance(
                    assetId = asset.assetId,
                    networkId = asset.networkId,
                    balanceRaw = nativeAmount.toString(),
                    balanceDecimal = nativeAmount.toSolanaDecimalString(asset.decimals),
                    providerName = successfulNativeBalances.first().provider.name,
                    slot = slotResult.value,
                    syncedAtMillis = nowMillis,
                )
            } else {
                val mint = checkNotNull(asset.contractAddress)
                val amount = tokenTotalsByMint[mint] ?: BigInteger.ZERO
                SolanaAssetBalance(
                    assetId = asset.assetId,
                    networkId = asset.networkId,
                    balanceRaw = amount.toString(),
                    balanceDecimal = amount.toSolanaDecimalString(asset.decimals),
                    providerName = successfulNativeBalances.first().provider.name,
                    slot = slotResult.value,
                    syncedAtMillis = nowMillis,
                    tokenAccounts = tokenAccountsByMint[mint].orEmpty(),
                )
            }
        }

        SolanaNetworkBalanceResult(
            balances = balances,
            tokenAccounts = tokenAccounts,
            providerName = successfulNativeBalances.first().provider.name,
            latestSlot = slotResult.value,
            completeness = if (nativeErrors.isEmpty() && tokenErrors.isEmpty()) {
                EvmSyncCompleteness.Complete
            } else {
                EvmSyncCompleteness.Partial
            },
            error = (nativeErrors + tokenErrors).joinToString(" | ").ifBlank { null },
        )
    }

    private suspend fun syncHistory(
        walletId: String,
        walletAddresses: List<String>,
        assets: List<SupportedAsset>,
        tokenAccounts: List<SolanaTokenAccount>,
        latestSlot: Long?,
        nowMillis: Long,
    ): SolanaNetworkHistoryResult = coroutineScope {
        val config = SolanaProviderRegistry.config
        val client = clientFactory(config)
        val signatureSourceLimiter = Semaphore(maxParallelSignatureSources.coerceAtLeast(1))
        val signatureSources = (walletAddresses + tokenAccounts.map { it.address }).distinct()
        val signatureAttempts = signatureSources.map { source ->
            async {
                signatureSourceLimiter.withPermit {
                    runCatching {
                        client.signaturesForAddress(
                            address = source,
                            limit = maxSignaturesPerAddress,
                        )
                    }.fold(
                        onSuccess = { result -> result to null },
                        onFailure = { error ->
                            SolanaRpcCallResult<List<SolanaSignatureInfo>>(
                                value = emptyList(),
                                provider = SolanaRpcProvider("failed-$source", ""),
                                slot = latestSlot,
                            ) to "${source}: ${error.message}"
                        },
                    )
                }
            }
        }.awaitAll()
        val signatureResults = signatureAttempts.map { it.first }
        val signatureErrors = signatureAttempts.mapNotNull { it.second }

        val signatures = signatureResults
            .flatMap { it.value }
            .distinctBy { it.signature }
            .sortedByDescending { it.slot }
        if (signatures.isEmpty()) {
            return@coroutineScope SolanaNetworkHistoryResult(
                transactions = emptyList(),
                providerName = signatureResults.firstOrNull()?.provider?.name,
                latestSlot = latestSlot,
                completeness = if (signatureErrors.isEmpty()) {
                    EvmSyncCompleteness.Complete
                } else {
                    EvmSyncCompleteness.Partial
                },
                cursorBeforeSignature = null,
                error = signatureErrors.joinToString(" | ").ifBlank { null },
            )
        }

        val transactionLimiter = Semaphore(maxParallelTransactionFetches.coerceAtLeast(1))
        val supportedAssetsByMint = assets
            .filter { it.contractAddress != null }
            .associateBy { checkNotNull(it.contractAddress) }
        val nativeAsset = assets.firstOrNull { it.assetType == "NATIVE" || it.contractAddress == null }
        val tokenAccountAddresses = tokenAccounts.map { it.address }.toSet()
        val walletAddressSet = walletAddresses.toSet()
        val transactions = signatures
            .map { signature ->
                async {
                    transactionLimiter.withPermit {
                        runCatching {
                            val parsed = client.parsedTransaction(signature.signature)
                            parsed.value?.toNormalizedTransactions(
                                walletId = walletId,
                                walletAddresses = walletAddressSet,
                                tokenAccountAddresses = tokenAccountAddresses,
                                nativeAsset = nativeAsset,
                                supportedAssetsByMint = supportedAssetsByMint,
                                latestSlot = latestSlot,
                                providerName = parsed.provider.name,
                                fallbackTimestampMillis = signature.blockTimeSeconds?.times(1_000L)
                                    ?: nowMillis,
                                fallbackSignature = signature.signature,
                            ).orEmpty()
                        }.getOrDefault(emptyList())
                    }
                }
            }
            .awaitAll()
            .flatten()
            .deduplicate()

        val reachedLimit = signatureResults.any { it.value.size >= maxSignaturesPerAddress }
        val partial = reachedLimit || signatureErrors.isNotEmpty()
        SolanaNetworkHistoryResult(
            transactions = transactions,
            providerName = signatureResults.firstOrNull()?.provider?.name,
            latestSlot = latestSlot,
            completeness = if (partial) EvmSyncCompleteness.Partial else EvmSyncCompleteness.Complete,
            cursorBeforeSignature = signatures.lastOrNull()?.signature.takeIf { reachedLimit },
            error = (
                signatureErrors +
                    listOfNotNull(
                        "History reached the public RPC page limit and should continue from cursor."
                            .takeIf { reachedLimit },
                    )
                ).joinToString(" | ").ifBlank { null },
        )
    }
}

private fun JSONObject.toNormalizedTransactions(
    walletId: String,
    walletAddresses: Set<String>,
    tokenAccountAddresses: Set<String>,
    nativeAsset: SupportedAsset?,
    supportedAssetsByMint: Map<String, SupportedAsset>,
    latestSlot: Long?,
    providerName: String,
    fallbackTimestampMillis: Long,
    fallbackSignature: String,
): List<SolanaNormalizedTransaction> {
    val meta = optJSONObject("meta") ?: return emptyList()
    val transaction = optJSONObject("transaction") ?: return emptyList()
    val message = transaction.optJSONObject("message") ?: return emptyList()
    val accountKeys = message.accountKeys()
    val signature = transaction.optJSONArray("signatures")
        ?.optString(0)
        ?.takeIf(String::isNotBlank)
        ?: fallbackSignature
    val slot = optLongOrNull("slot")
    val timestampMillis = optLongOrNull("blockTime")?.times(1_000L) ?: fallbackTimestampMillis
    val feeRaw = meta.optLong("fee", 0L).coerceAtLeast(0L)
    val feePayer = accountKeys.firstOrNull()
    val feePaidByWallet = feePayer in walletAddresses
    val status = if (meta.opt("err") == null || meta.opt("err") == JSONObject.NULL) {
        WalletTransactionStatus.Success
    } else {
        WalletTransactionStatus.Failed
    }
    val confirmations = latestSlot
        ?.let { currentSlot -> slot?.let { (currentSlot - it).coerceAtLeast(0L) } }
        ?.coerceAtMost(Int.MAX_VALUE.toLong())
        ?.toInt() ?: 0
    val instructions = message.parsedInstructions() + meta.innerParsedInstructions()

    val tokenTransactions = tokenDeltas(
        meta = meta,
        accountKeys = accountKeys,
        walletAddresses = walletAddresses,
        tokenAccountAddresses = tokenAccountAddresses,
    ).mapNotNull { delta ->
        val asset = supportedAssetsByMint[delta.mint] ?: return@mapNotNull null
        val direction = delta.amountRaw.toDirection()
        val tokenTransfer = instructions.findTokenTransfer(tokenAccountAddresses, walletAddresses, direction)
        val absAmount = delta.amountRaw.abs()
        if (absAmount == BigInteger.ZERO) return@mapNotNull null
        SolanaNormalizedTransaction(
            walletId = walletId,
            assetId = asset.assetId,
            networkId = asset.networkId,
            transactionHash = signature,
            direction = direction,
            status = status,
            amountRaw = absAmount.toString(),
            amountDecimal = absAmount.toSolanaDecimalString(asset.decimals),
            feeRaw = feeRaw.takeIf { feePaidByWallet && direction != WalletTransactionDirection.Incoming }?.toString(),
            feeDecimal = feeRaw.takeIf { feePaidByWallet && direction != WalletTransactionDirection.Incoming }
                ?.let { BigInteger.valueOf(it).toSolanaDecimalString(SolanaProviderRegistry.config.nativeDecimals) },
            feeAssetId = SolanaProviderRegistry.NATIVE_ASSET_ID.takeIf {
                feePaidByWallet && direction != WalletTransactionDirection.Incoming
            },
            fromAddress = tokenTransfer?.first,
            toAddress = tokenTransfer?.second,
            blockHeight = slot,
            blockHash = null,
            confirmations = confirmations,
            timestampMillis = timestampMillis,
            providerName = providerName,
            metadataJson = JSONObject()
                .put("syncFamily", "solana")
                .put("syncProvider", providerName)
                .put("mint", delta.mint)
                .put("signature", signature)
                .put("slot", slot)
                .put("program", "spl-token")
                .toString(),
        )
    }

    val nativeTransaction = nativeAsset?.let { asset ->
        val preBalances = meta.optJSONArray("preBalances") ?: JSONArray()
        val postBalances = meta.optJSONArray("postBalances") ?: JSONArray()
        val trackedIndexes = accountKeys
            .mapIndexedNotNull { index, account -> index.takeIf { account in walletAddresses } }
        if (trackedIndexes.isEmpty()) return@let null
        val pre = trackedIndexes.sumOf { walletIndex -> preBalances.optLong(walletIndex, 0L) }
        val post = trackedIndexes.sumOf { walletIndex -> postBalances.optLong(walletIndex, 0L) }
        val delta = post - pre
        val amountLamports = if (delta < 0 && feePaidByWallet) {
            (-delta - feeRaw).coerceAtLeast(0L)
        } else {
            kotlin.math.abs(delta)
        }
        if (amountLamports <= 0L) return@let null
        val direction = BigInteger.valueOf(delta).toDirection()
        val nativeTransfer = instructions.findNativeTransfer(walletAddresses, direction)
        SolanaNormalizedTransaction(
            walletId = walletId,
            assetId = asset.assetId,
            networkId = asset.networkId,
            transactionHash = signature,
            direction = direction,
            status = status,
            amountRaw = amountLamports.toString(),
            amountDecimal = BigInteger.valueOf(amountLamports).toSolanaDecimalString(asset.decimals),
            feeRaw = feeRaw.takeIf { feePaidByWallet }?.toString(),
            feeDecimal = feeRaw.takeIf { feePaidByWallet }
                ?.let { BigInteger.valueOf(it).toSolanaDecimalString(asset.decimals) },
            feeAssetId = asset.assetId.takeIf { feePaidByWallet },
            fromAddress = nativeTransfer?.first,
            toAddress = nativeTransfer?.second,
            blockHeight = slot,
            blockHash = null,
            confirmations = confirmations,
            timestampMillis = timestampMillis,
            providerName = providerName,
            metadataJson = JSONObject()
                .put("syncFamily", "solana")
                .put("syncProvider", providerName)
                .put("signature", signature)
                .put("slot", slot)
                .put("program", "system")
                .toString(),
        )
    }

    return (tokenTransactions + listOfNotNull(nativeTransaction))
}

private data class SolanaTokenDelta(
    val mint: String,
    val amountRaw: BigInteger,
)

private data class SolanaNativeBalanceQueryResult(
    val address: String,
    val result: SolanaRpcCallResult<BigInteger>?,
    val error: String?,
)

private data class SolanaTokenAccountQueryResult(
    val ownerAddress: String,
    val programId: String,
    val result: SolanaRpcCallResult<List<SolanaTokenAccount>>?,
    val error: String?,
)

private fun tokenDeltas(
    meta: JSONObject,
    accountKeys: List<String>,
    walletAddresses: Set<String>,
    tokenAccountAddresses: Set<String>,
): List<SolanaTokenDelta> {
    val pre = meta.tokenAmountsByMint("preTokenBalances", accountKeys, walletAddresses, tokenAccountAddresses)
    val post = meta.tokenAmountsByMint("postTokenBalances", accountKeys, walletAddresses, tokenAccountAddresses)
    return (pre.keys + post.keys)
        .distinct()
        .mapNotNull { mint ->
            val delta = (post[mint] ?: BigInteger.ZERO) - (pre[mint] ?: BigInteger.ZERO)
            SolanaTokenDelta(mint = mint, amountRaw = delta).takeIf { delta != BigInteger.ZERO }
        }
}

private fun JSONObject.tokenAmountsByMint(
    key: String,
    accountKeys: List<String>,
    walletAddresses: Set<String>,
    tokenAccountAddresses: Set<String>,
): Map<String, BigInteger> {
    val balances = optJSONArray(key) ?: return emptyMap()
    val totals = mutableMapOf<String, BigInteger>()
    for (index in 0 until balances.length()) {
        val item = balances.optJSONObject(index) ?: continue
        val mint = item.optString("mint").takeIf(String::isNotBlank) ?: continue
        val owner = item.optString("owner").takeIf(String::isNotBlank)
        val accountIndex = item.optInt("accountIndex", -1)
        val accountAddress = accountKeys.getOrNull(accountIndex)
        if (owner !in walletAddresses && accountAddress !in tokenAccountAddresses) continue
        val amount = item.optJSONObject("uiTokenAmount")
            ?.optString("amount")
            ?.takeIf(String::isNotBlank)
            ?.toBigIntegerOrZero() ?: BigInteger.ZERO
        totals[mint] = (totals[mint] ?: BigInteger.ZERO) + amount
    }
    return totals
}

private fun JSONObject.accountKeys(): List<String> {
    val keys = optJSONArray("accountKeys") ?: return emptyList()
    return buildList {
        for (index in 0 until keys.length()) {
            when (val item = keys.opt(index)) {
                is JSONObject -> item.optString("pubkey").takeIf(String::isNotBlank)?.let(::add)
                is String -> add(item)
            }
        }
    }
}

private fun JSONObject.parsedInstructions(): List<JSONObject> {
    val instructions = optJSONArray("instructions") ?: return emptyList()
    return buildList {
        for (index in 0 until instructions.length()) {
            instructions.optJSONObject(index)?.takeIf { it.has("parsed") }?.let(::add)
        }
    }
}

private fun JSONObject.innerParsedInstructions(): List<JSONObject> {
    val groups = optJSONArray("innerInstructions") ?: return emptyList()
    return buildList {
        for (groupIndex in 0 until groups.length()) {
            val instructions = groups.optJSONObject(groupIndex)?.optJSONArray("instructions") ?: continue
            for (instructionIndex in 0 until instructions.length()) {
                instructions.optJSONObject(instructionIndex)?.takeIf { it.has("parsed") }?.let(::add)
            }
        }
    }
}

private fun List<JSONObject>.findTokenTransfer(
    tokenAccountAddresses: Set<String>,
    walletAddresses: Set<String>,
    direction: WalletTransactionDirection,
): Pair<String?, String?>? =
    firstNotNullOfOrNull { instruction ->
        val program = instruction.optString("program").lowercase(Locale.US)
        if (program != "spl-token" && program != "spl-token-2022") return@firstNotNullOfOrNull null
        val parsed = instruction.optJSONObject("parsed") ?: return@firstNotNullOfOrNull null
        val type = parsed.optString("type").lowercase(Locale.US)
        if (type != "transfer" && type != "transferchecked") return@firstNotNullOfOrNull null
        val info = parsed.optJSONObject("info") ?: return@firstNotNullOfOrNull null
        val source = info.optString("source").takeIf(String::isNotBlank)
        val destination = info.optString("destination").takeIf(String::isNotBlank)
        val authority = info.optString("authority").takeIf(String::isNotBlank)
        val involvesWallet = source in tokenAccountAddresses ||
            destination in tokenAccountAddresses ||
            authority in walletAddresses
        if (!involvesWallet) return@firstNotNullOfOrNull null
        when (direction) {
            WalletTransactionDirection.Incoming -> source to destination
            WalletTransactionDirection.Outgoing -> source to destination
            else -> source to destination
        }
    }

private fun List<JSONObject>.findNativeTransfer(
    walletAddresses: Set<String>,
    direction: WalletTransactionDirection,
): Pair<String?, String?>? =
    firstNotNullOfOrNull { instruction ->
        if (instruction.optString("program").lowercase(Locale.US) != "system") {
            return@firstNotNullOfOrNull null
        }
        val parsed = instruction.optJSONObject("parsed") ?: return@firstNotNullOfOrNull null
        if (parsed.optString("type").lowercase(Locale.US) != "transfer") {
            return@firstNotNullOfOrNull null
        }
        val info = parsed.optJSONObject("info") ?: return@firstNotNullOfOrNull null
        val source = info.optString("source").takeIf(String::isNotBlank)
        val destination = info.optString("destination").takeIf(String::isNotBlank)
        val involvesWallet = source in walletAddresses || destination in walletAddresses
        if (!involvesWallet) return@firstNotNullOfOrNull null
        when (direction) {
            WalletTransactionDirection.Incoming -> source to destination
            WalletTransactionDirection.Outgoing -> source to destination
            else -> source to destination
        }
    }

private fun BigInteger.toDirection(): WalletTransactionDirection =
    when {
        this > BigInteger.ZERO -> WalletTransactionDirection.Incoming
        this < BigInteger.ZERO -> WalletTransactionDirection.Outgoing
        else -> WalletTransactionDirection.Self
    }

private fun List<SolanaNormalizedTransaction>.deduplicate(): List<SolanaNormalizedTransaction> =
    distinctBy { "${it.transactionHash}:${it.assetId}" }
        .sortedByDescending { it.timestampMillis }

private fun List<WalletAddressRecord>.addressesForNetwork(networkId: String): List<WalletAddressRecord> =
    filter { it.networkId == networkId }
        .sortedWith(
            compareByDescending<WalletAddressRecord> { it.isPrimary }
                .thenBy { it.addressIndex ?: Int.MAX_VALUE }
                .thenBy { it.createdAt },
        )

private fun maxOfOrNull(
    first: Long?,
    second: Long?,
): Long? =
    listOfNotNull(first, second).maxOrNull()
