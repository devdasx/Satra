package dev.satra.wallet.data.sync.accountchain

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
import java.math.BigDecimal
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.Locale

class AccountChainWalletSyncService(
    private val httpClient: AccountChainHttpClient = AccountChainHttpClient(),
    private val maxParallelNetworks: Int = 8,
    private val maxHistoryItemsPerNetwork: Int = 120,
) {
    suspend fun syncWallet(
        walletId: String,
        addresses: List<WalletAddressRecord>,
        networkId: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        onNetworkResult: suspend (AccountChainNetworkSyncResult) -> Unit = {},
    ): AccountChainWalletSyncResult = coroutineScope {
        val requestedNetworks = if (networkId == null) {
            addresses
                .map { it.networkId }
                .filter { it in AccountChainProviderRegistry.supportedNetworkIds }
                .toSet()
        } else {
            setOf(networkId).also { AccountChainProviderRegistry.requireConfig(networkId) }
        }
        val limiter = Semaphore(maxParallelNetworks.coerceAtLeast(1))
        val results = requestedNetworks.sorted().map { accountChainNetworkId ->
            async {
                limiter.withPermit {
                    val config = AccountChainProviderRegistry.requireConfig(accountChainNetworkId)
                    val result = syncNetwork(
                        walletId = walletId,
                        config = config,
                        addresses = addresses.addressesForNetwork(accountChainNetworkId),
                        nowMillis = nowMillis,
                        onNetworkResult = onNetworkResult,
                    )
                    runCatching { onNetworkResult(result) }
                    result
                }
            }
        }.awaitAll()

        AccountChainWalletSyncResult(
            walletId = walletId,
            networkResults = results,
        )
    }

    private suspend fun syncNetwork(
        walletId: String,
        config: AccountChainNetworkConfig,
        addresses: List<WalletAddressRecord>,
        nowMillis: Long,
        onNetworkResult: suspend (AccountChainNetworkSyncResult) -> Unit,
    ): AccountChainNetworkSyncResult {
        val assets = SupportedAssetCatalog.assets.filter { it.networkId == config.networkId }
        val address = addresses.firstOrNull()?.address
        if (address.isNullOrBlank()) {
            return AccountChainNetworkSyncResult(
                walletId = walletId,
                networkId = config.networkId,
                address = null,
                balanceCompleteness = EvmSyncCompleteness.Failed,
                historyCompleteness = EvmSyncCompleteness.Failed,
                balances = emptyList(),
                transactions = emptyList(),
                providerName = null,
                latestLedger = null,
                cursor = null,
                error = "No wallet address stored for ${config.networkId}.",
            )
        }

        val balanceResult = runCatching {
            syncBalances(config, address, assets, nowMillis)
        }.getOrElse { error ->
            AccountChainBalanceResult(
                balances = emptyList(),
                providerName = null,
                latestLedger = null,
                completeness = EvmSyncCompleteness.Failed,
                error = error.message,
            )
        }

        runCatching {
            onNetworkResult(
                AccountChainNetworkSyncResult(
                    walletId = walletId,
                    networkId = config.networkId,
                    address = address,
                    balanceCompleteness = balanceResult.completeness,
                    historyCompleteness = EvmSyncCompleteness.Partial,
                    balances = balanceResult.balances,
                    transactions = emptyList(),
                    providerName = balanceResult.providerName,
                    latestLedger = balanceResult.latestLedger,
                    cursor = null,
                    error = balanceResult.error,
                ),
            )
        }

        val historyResult = runCatching {
            syncHistory(
                walletId = walletId,
                config = config,
                address = address,
                assets = assets,
                latestLedger = balanceResult.latestLedger,
                nowMillis = nowMillis,
            )
        }.getOrElse { error ->
            AccountChainHistoryResult(
                transactions = emptyList(),
                providerName = null,
                latestLedger = balanceResult.latestLedger,
                completeness = EvmSyncCompleteness.Failed,
                cursor = null,
                error = error.message,
            )
        }

        return AccountChainNetworkSyncResult(
            walletId = walletId,
            networkId = config.networkId,
            address = address,
            balanceCompleteness = balanceResult.completeness,
            historyCompleteness = historyResult.completeness,
            balances = balanceResult.balances,
            transactions = historyResult.transactions,
            providerName = balanceResult.providerName ?: historyResult.providerName,
            latestLedger = maxOfOrNull(balanceResult.latestLedger, historyResult.latestLedger),
            cursor = historyResult.cursor,
            error = listOfNotNull(balanceResult.error, historyResult.error)
                .joinToString(" | ")
                .ifBlank { null },
        )
    }

    private suspend fun syncBalances(
        config: AccountChainNetworkConfig,
        address: String,
        assets: List<SupportedAsset>,
        nowMillis: Long,
    ): AccountChainBalanceResult =
        when (config.networkId) {
            "tron" -> syncTronBalances(httpClient, config, address, assets, nowMillis)
            "aptos" -> syncAptosBalances(httpClient, config, address, assets, nowMillis)
            "near" -> syncNearBalances(httpClient, config, address, assets, nowMillis)
            "polkadot" -> syncPolkadotBalances(httpClient, config, address, assets, nowMillis)
            "ripple" -> syncRippleBalances(httpClient, config, address, assets, nowMillis)
            "stellar" -> syncStellarBalances(httpClient, config, address, assets, nowMillis)
            "sui" -> syncSuiBalances(httpClient, config, address, assets, nowMillis)
            "ton" -> syncTonBalances(httpClient, config, address, assets, nowMillis)
            "kava" -> syncKavaBalances(httpClient, config, address, assets, nowMillis)
            else -> error("Unsupported account-chain balance sync: ${config.networkId}")
        }

    private suspend fun syncHistory(
        walletId: String,
        config: AccountChainNetworkConfig,
        address: String,
        assets: List<SupportedAsset>,
        latestLedger: Long?,
        nowMillis: Long,
    ): AccountChainHistoryResult =
        when (config.networkId) {
            "tron" -> syncTronHistory(httpClient, walletId, config, address, assets, maxHistoryItemsPerNetwork)
            "aptos" -> syncAptosHistory(httpClient, walletId, config, address, assets, nowMillis, maxHistoryItemsPerNetwork)
            "near" -> syncNearHistory(httpClient, walletId, config, address, assets, maxHistoryItemsPerNetwork)
            "polkadot" -> syncPolkadotHistory(config)
            "ripple" -> syncRippleHistory(httpClient, walletId, config, address, assets, latestLedger, nowMillis, maxHistoryItemsPerNetwork)
            "stellar" -> syncStellarHistory(httpClient, walletId, config, address, assets, maxHistoryItemsPerNetwork)
            "sui" -> syncSuiHistory(httpClient, walletId, config, address, assets, latestLedger, nowMillis, maxHistoryItemsPerNetwork)
            "ton" -> syncTonHistory(httpClient, walletId, config, address, assets, nowMillis, maxHistoryItemsPerNetwork)
            "kava" -> syncKavaHistory(httpClient, walletId, config, address, assets, nowMillis, maxHistoryItemsPerNetwork)
            else -> error("Unsupported account-chain history sync: ${config.networkId}")
        }
}

private data class AccountChainProviderValue<T>(
    val value: T,
    val provider: AccountChainProvider,
)

private suspend fun <T> AccountChainNetworkConfig.tryProviders(
    label: String,
    block: suspend (AccountChainProvider) -> T,
): AccountChainProviderValue<T> {
    val failures = mutableListOf<String>()
    providers.forEach { provider ->
        try {
            return AccountChainProviderValue(block(provider), provider)
        } catch (error: Throwable) {
            failures += "${provider.name} $label: ${error.message}"
        }
    }
    error("All ${networkId} providers failed for $label: ${failures.joinToString(" | ")}")
}

private fun accountBalance(
    asset: SupportedAsset,
    amountRaw: String,
    providerName: String,
    ledgerHeight: Long?,
    nowMillis: Long,
): AccountChainAssetBalance =
    AccountChainAssetBalance(
        assetId = asset.assetId,
        networkId = asset.networkId,
        balanceRaw = amountRaw,
        balanceDecimal = amountRaw.toAccountBigIntegerOrZero().toAccountDecimalString(asset.decimals),
        providerName = providerName,
        ledgerHeight = ledgerHeight,
        syncedAtMillis = nowMillis,
    )

private fun List<SupportedAsset>.nativeAsset(): SupportedAsset =
    first { it.assetType == "NATIVE" || it.contractAddress == null }

private fun List<SupportedAsset>.tokenAssets(): List<SupportedAsset> =
    filter { it.assetType != "NATIVE" && it.contractAddress != null }

private fun List<WalletAddressRecord>.addressesForNetwork(networkId: String): List<WalletAddressRecord> =
    filter { it.networkId == networkId }
        .sortedWith(
            compareByDescending<WalletAddressRecord> { it.isPrimary }
                .thenBy { it.addressIndex ?: Int.MAX_VALUE }
                .thenBy { it.createdAt },
        )

private suspend fun syncTronBalances(
    httpClient: AccountChainHttpClient,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    nowMillis: Long,
): AccountChainBalanceResult {
    val providerResult = config.tryProviders("account") { provider ->
        JSONObject(httpClient.get(provider, "/v1/accounts/$address"))
    }
    val account = providerResult.value.optJSONArray("data")?.optJSONObject(0)
    val nativeRaw = account?.optLongOrNull("balance")?.toString() ?: "0"
    val tokenBalances = mutableMapOf<String, String>()
    val trc20 = account?.optJSONArray("trc20") ?: JSONArray()
    for (index in 0 until trc20.length()) {
        val item = trc20.optJSONObject(index) ?: continue
        item.keys().forEach { contractAddress ->
            tokenBalances[contractAddress] = item.optBigIntegerString(contractAddress) ?: "0"
        }
    }

    val balances = buildList {
        add(accountBalance(assets.nativeAsset(), nativeRaw, providerResult.provider.name, null, nowMillis))
        assets.tokenAssets().forEach { asset ->
            val contractAddress = checkNotNull(asset.contractAddress)
            add(accountBalance(asset, tokenBalances[contractAddress] ?: "0", providerResult.provider.name, null, nowMillis))
        }
    }
    return AccountChainBalanceResult(
        balances = balances,
        providerName = providerResult.provider.name,
        latestLedger = null,
        completeness = EvmSyncCompleteness.Complete,
        error = null,
    )
}

private suspend fun syncTronHistory(
    httpClient: AccountChainHttpClient,
    walletId: String,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    limit: Int,
): AccountChainHistoryResult {
    val provider = config.providers.first()
    val nativeAsset = assets.nativeAsset()
    val tokenAssetsByContract = assets.tokenAssets().associateBy { checkNotNull(it.contractAddress) }
    val nativeDeferred = coroutineScope {
        async {
            runCatching {
                JSONObject(
                    httpClient.get(
                        provider = provider,
                        path = "/v1/accounts/$address/transactions" + queryString(
                            "limit" to limit.coerceIn(1, 200).toString(),
                            "only_confirmed" to "true",
                            "order_by" to "block_timestamp,desc",
                        ),
                    ),
                )
            }
        }
    }
    val tokenDeferred = coroutineScope {
        async {
            runCatching {
                JSONObject(
                    httpClient.get(
                        provider = provider,
                        path = "/v1/accounts/$address/transactions/trc20" + queryString(
                            "limit" to limit.coerceIn(1, 200).toString(),
                            "only_confirmed" to "true",
                            "order_by" to "block_timestamp,desc",
                        ),
                    ),
                )
            }
        }
    }
    val nativeResult = nativeDeferred.await()
    val tokenResult = tokenDeferred.await()
    val transactions = buildList {
        nativeResult.getOrNull()
            ?.optJSONArray("data")
            ?.objects()
            .orEmpty()
            .flatMap { item ->
                item.toTronNativeTransactions(walletId, address, nativeAsset, provider.name)
            }
            .let(::addAll)
        tokenResult.getOrNull()
            ?.optJSONArray("data")
            ?.objects()
            .orEmpty()
            .mapNotNull { item ->
                item.toTronTokenTransaction(walletId, address, tokenAssetsByContract, provider.name)
            }
            .let(::addAll)
    }.distinctBy { it.transactionHash to it.assetId }
        .sortedByDescending { it.timestampMillis }
        .take(limit)

    val errors = listOfNotNull(
        nativeResult.exceptionOrNull()?.message?.let { "TRX history: $it" },
        tokenResult.exceptionOrNull()?.message?.let { "TRC-20 history: $it" },
    )
    return AccountChainHistoryResult(
        transactions = transactions,
        providerName = provider.name,
        latestLedger = null,
        completeness = if (errors.isEmpty()) EvmSyncCompleteness.Complete else EvmSyncCompleteness.Partial,
        cursor = null,
        error = errors.joinToString(" | ").ifBlank { null },
    )
}

private fun JSONObject.toTronNativeTransactions(
    walletId: String,
    address: String,
    asset: SupportedAsset,
    providerName: String,
): List<AccountChainNormalizedTransaction> {
    val hash = optString("txID").takeIf(String::isNotBlank) ?: return emptyList()
    val timestampMillis = optLongOrNull("block_timestamp") ?: System.currentTimeMillis()
    val blockHeight = optLongOrNull("blockNumber")
    val feeRaw = optLongOrNull("fee")?.toString()
    val status = if (
        optJSONArray("ret")
            ?.optJSONObject(0)
            ?.optString("contractRet")
            ?.equals("SUCCESS", ignoreCase = true) != false
    ) {
        WalletTransactionStatus.Success
    } else {
        WalletTransactionStatus.Failed
    }
    val contracts = optJSONObject("raw_data")?.optJSONArray("contract") ?: JSONArray()
    return contracts.objects().mapNotNull { contract ->
        if (contract.optString("type") != "TransferContract") return@mapNotNull null
        val value = contract.optJSONObject("parameter")?.optJSONObject("value") ?: return@mapNotNull null
        val fromAddress = value.optString("owner_address").hexTronAddressToBase58()
        val toAddress = value.optString("to_address").hexTronAddressToBase58()
        val amountRaw = value.optBigIntegerString("amount") ?: "0"
        val direction = directionFor(address, fromAddress, toAddress)
        AccountChainNormalizedTransaction(
            walletId = walletId,
            assetId = asset.assetId,
            networkId = asset.networkId,
            transactionHash = hash,
            direction = direction,
            status = status,
            amountRaw = amountRaw,
            amountDecimal = amountRaw.toAccountBigIntegerOrZero().toAccountDecimalString(asset.decimals),
            feeRaw = feeRaw,
            feeDecimal = feeRaw?.toAccountBigIntegerOrZero()?.toAccountDecimalString(asset.decimals),
            feeAssetId = asset.assetId,
            fromAddress = fromAddress,
            toAddress = toAddress,
            blockHeight = blockHeight,
            blockHash = optStringOrNull("blockID"),
            confirmations = 0,
            timestampMillis = timestampMillis,
            providerName = providerName,
            metadataJson = JSONObject()
                .put("syncFamily", "account-chain")
                .put("chainFamily", "tron")
                .put("eventType", "native_transfer")
                .toString(),
        )
    }
}

private fun JSONObject.toTronTokenTransaction(
    walletId: String,
    address: String,
    tokenAssetsByContract: Map<String, SupportedAsset>,
    providerName: String,
): AccountChainNormalizedTransaction? {
    val contractAddress = optJSONObject("token_info")?.optString("address")
        ?: optString("contract_address")
    val asset = tokenAssetsByContract[contractAddress] ?: return null
    val hash = optString("transaction_id").takeIf(String::isNotBlank) ?: return null
    val fromAddress = optStringOrNull("from")
    val toAddress = optStringOrNull("to")
    val amountRaw = optBigIntegerString("value") ?: "0"
    return AccountChainNormalizedTransaction(
        walletId = walletId,
        assetId = asset.assetId,
        networkId = asset.networkId,
        transactionHash = hash,
        direction = directionFor(address, fromAddress, toAddress),
        status = WalletTransactionStatus.Success,
        amountRaw = amountRaw,
        amountDecimal = amountRaw.toAccountBigIntegerOrZero().toAccountDecimalString(asset.decimals),
        feeRaw = null,
        feeDecimal = null,
        feeAssetId = null,
        fromAddress = fromAddress,
        toAddress = toAddress,
        blockHeight = optLongOrNull("block_number"),
        blockHash = null,
        confirmations = 0,
        timestampMillis = optLongOrNull("block_timestamp") ?: System.currentTimeMillis(),
        providerName = providerName,
        metadataJson = JSONObject()
            .put("syncFamily", "account-chain")
            .put("chainFamily", "tron")
            .put("eventType", "trc20_transfer")
            .put("contractAddress", contractAddress)
            .toString(),
    )
}

private suspend fun syncAptosBalances(
    httpClient: AccountChainHttpClient,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    nowMillis: Long,
): AccountChainBalanceResult {
    val indexer = config.providers.first { it.name.contains("indexer") }
    val indexerResult = runCatching {
        val query = """
            query(${'$'}owner:String!) {
              current_fungible_asset_balances(where:{owner_address:{_eq:${'$'}owner}}, limit:200) {
                asset_type
                amount
              }
              ledger_infos(limit:1) {
                chain_id
              }
            }
        """.trimIndent()
        val response = JSONObject(
            httpClient.post(
                provider = indexer,
                path = "",
                body = JSONObject()
                    .put("query", query)
                    .put("variables", JSONObject().put("owner", address.lowercase(Locale.US))),
            ),
        )
        response.optJSONArray("errors")?.takeIf { it.length() > 0 }?.let { errors ->
            error("Aptos indexer returned errors: $errors")
        }
        response
    }

    val response = indexerResult.getOrNull()
    if (response == null) {
        return AccountChainBalanceResult(
            balances = assets.map { asset ->
                accountBalance(
                    asset = asset,
                    amountRaw = "0",
                    providerName = indexer.name,
                    ledgerHeight = null,
                    nowMillis = nowMillis,
                )
            },
            providerName = indexer.name,
            latestLedger = null,
            completeness = EvmSyncCompleteness.Partial,
            error = indexerResult.exceptionOrNull()?.message,
        )
    }

    val data = response.getJSONObject("data")
    val rows = data.optJSONArray("current_fungible_asset_balances")?.objects().orEmpty()
    val amountsByAssetType = rows.associate { item ->
        item.optString("asset_type").lowercase(Locale.US) to (item.optBigIntegerString("amount") ?: "0")
    }
    val balances = assets.map { asset ->
        val assetType = asset.aptosAssetType().lowercase(Locale.US)
        accountBalance(
            asset = asset,
            amountRaw = amountsByAssetType[assetType] ?: "0",
            providerName = indexer.name,
            ledgerHeight = null,
            nowMillis = nowMillis,
        )
    }
    return AccountChainBalanceResult(
        balances = balances,
        providerName = indexer.name,
        latestLedger = null,
        completeness = EvmSyncCompleteness.Complete,
        error = null,
    )
}

private suspend fun syncAptosHistory(
    httpClient: AccountChainHttpClient,
    walletId: String,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    nowMillis: Long,
    limit: Int,
): AccountChainHistoryResult {
    val fullnode = config.providers.first { it.name.contains("fullnode") }
    val result = config.tryProviders("account transactions") { provider ->
        val source = if (provider.name.contains("fullnode")) provider else fullnode
        JSONArray(
            httpClient.get(
                source,
                "/accounts/${address.lowercase(Locale.US)}/transactions" + queryString(
                    "limit" to limit.coerceIn(1, 100).toString(),
                ),
            ),
        )
    }
    val supportedAssetsByType = assets.associateBy { it.aptosAssetType().lowercase(Locale.US) }
    val nativeAsset = assets.nativeAsset()
    val transactions = result.value.objects()
        .flatMap { transaction ->
            transaction.toAptosTransactions(
                walletId = walletId,
                walletAddress = address,
                supportedAssetsByType = supportedAssetsByType,
                nativeAsset = nativeAsset,
                providerName = result.provider.name,
                fallbackTimestampMillis = 0L,
            )
        }
        .distinctBy { it.transactionHash to it.assetId to it.metadataJson }
        .sortedByDescending { it.timestampMillis }
    return AccountChainHistoryResult(
        transactions = transactions,
        providerName = result.provider.name,
        latestLedger = null,
        completeness = EvmSyncCompleteness.Complete,
        cursor = null,
        error = null,
    )
}

private fun SupportedAsset.aptosAssetType(): String =
    contractAddress ?: "0x1::aptos_coin::AptosCoin"

private fun JSONObject.toAptosTransactions(
    walletId: String,
    walletAddress: String,
    supportedAssetsByType: Map<String, SupportedAsset>,
    nativeAsset: SupportedAsset,
    providerName: String,
    fallbackTimestampMillis: Long,
): List<AccountChainNormalizedTransaction> {
    val hash = optString("hash").takeIf(String::isNotBlank) ?: return emptyList()
    val version = optLongOrNull("version")
    val success = optBoolean("success", true)
    val timestampMillis = optBigIntegerString("timestamp")
        ?.let { runCatching { BigInteger(it).divide(BigInteger("1000")).toLong() }.getOrNull() }
        ?: fallbackTimestampMillis
    val gasRaw = aptosGasFeeRaw()
    val assetTypeByStore = aptosAssetTypeByFungibleStore()
    val events = optJSONArray("events")?.objects().orEmpty()
    return events.mapNotNull { event ->
        val eventType = event.optString("type")
        val data = event.optJSONObject("data") ?: return@mapNotNull null
        val lowerType = eventType.lowercase(Locale.US)
        val gasFeeEvent = lowerType.contains("gasfeeevent") ||
            lowerType.contains("transaction_fee::feestatement")
        val amountRaw = if (gasFeeEvent) {
            gasRaw ?: data.optBigIntegerString("total_charge_gas_units")
        } else {
            data.optBigIntegerString("amount")
                ?: data.optBigIntegerString("value")
        } ?: return@mapNotNull null
        val assetType = if (gasFeeEvent) {
            nativeAsset.aptosAssetType()
        } else {
            data.optJSONObject("metadata")?.optString("inner")
                ?: data.optString("metadata")
                ?: data.optString("coin_type")
                ?: data.optStringOrNull("store")?.let { assetTypeByStore[it.lowercase(Locale.US)] }
        }
        val asset = assetType
            ?.lowercase(Locale.US)
            ?.let(supportedAssetsByType::get)
            ?: return@mapNotNull null
        val direction = when {
            gasFeeEvent -> WalletTransactionDirection.Outgoing
            lowerType.contains("deposit") -> WalletTransactionDirection.Incoming
            lowerType.contains("withdraw") -> WalletTransactionDirection.Outgoing
            else -> WalletTransactionDirection.Unknown
        }
        if (direction == WalletTransactionDirection.Unknown) return@mapNotNull null
        val eventIndex = event.optLongOrNull("sequence_number")
            ?: event.optLongOrNull("event_index")
        AccountChainNormalizedTransaction(
            walletId = walletId,
            assetId = asset.assetId,
            networkId = asset.networkId,
            transactionHash = hash,
            direction = direction,
            status = if (success) WalletTransactionStatus.Success else WalletTransactionStatus.Failed,
            amountRaw = amountRaw,
            amountDecimal = amountRaw.toAccountBigIntegerOrZero().toAccountDecimalString(asset.decimals),
            feeRaw = gasRaw.takeIf { asset.assetId == nativeAsset.assetId && direction == WalletTransactionDirection.Outgoing },
            feeDecimal = gasRaw.takeIf { asset.assetId == nativeAsset.assetId && direction == WalletTransactionDirection.Outgoing }
                ?.toAccountBigIntegerOrZero()
                ?.toAccountDecimalString(asset.decimals),
            feeAssetId = nativeAsset.assetId.takeIf { direction == WalletTransactionDirection.Outgoing },
            fromAddress = walletAddress.takeIf { direction == WalletTransactionDirection.Outgoing },
            toAddress = walletAddress.takeIf { direction == WalletTransactionDirection.Incoming },
            blockHeight = version,
            blockHash = null,
            confirmations = 0,
            timestampMillis = timestampMillis,
            providerName = providerName,
            metadataJson = JSONObject()
                .put("syncFamily", "account-chain")
                .put("chainFamily", "aptos")
                .put("eventType", eventType)
                .put("version", version)
                .put("eventIndex", eventIndex)
                .put("assetType", assetType)
                .toString(),
        )
    }
}

private fun JSONObject.aptosGasFeeRaw(): String? {
    val gasUsed = optBigIntegerString("gas_used")?.toAccountBigIntegerOrZero() ?: return null
    val gasUnitPrice = optBigIntegerString("gas_unit_price")?.toAccountBigIntegerOrZero() ?: BigInteger.ONE
    return gasUsed.multiply(gasUnitPrice).toString()
}

private fun JSONObject.aptosAssetTypeByFungibleStore(): Map<String, String> =
    optJSONArray("changes")
        ?.objects()
        .orEmpty()
        .mapNotNull { change ->
            val resource = change.optJSONObject("data") ?: return@mapNotNull null
            val type = resource.optString("type")
            if (!type.contains("fungible_asset::FungibleStore", ignoreCase = true)) return@mapNotNull null
            val storeAddress = change.optString("address").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val metadata = resource.optJSONObject("data")
                ?.optJSONObject("metadata")
                ?.optString("inner")
                ?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            storeAddress.lowercase(Locale.US) to metadata
        }
        .toMap()

private suspend fun syncNearBalances(
    httpClient: AccountChainHttpClient,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    nowMillis: Long,
): AccountChainBalanceResult = coroutineScope {
    val rpc = config.providers.first { it.name.contains("rpc") }
    val nativeDeferred = async {
        runCatching {
            nearRpcQuery(
                httpClient = httpClient,
                provider = rpc,
                requestType = "view_account",
                accountId = address,
            )
        }
    }
    val tokenDeferred = assets.tokenAssets().map { asset ->
        async {
            runCatching {
                val contract = checkNotNull(asset.contractAddress)
                val args = Base64.getEncoder().encodeToString(
                    JSONObject().put("account_id", address).toString().toByteArray(Charsets.UTF_8),
                )
                asset to nearRpcQuery(
                    httpClient = httpClient,
                    provider = rpc,
                    requestType = "call_function",
                    accountId = contract,
                    methodName = "ft_balance_of",
                    argsBase64 = args,
                )
            }
        }
    }
    val nativeResult = nativeDeferred.await()
    val tokenResults = tokenDeferred.awaitAll()
    val nativeJson = nativeResult.getOrThrow()
    val nativeAmount = nativeJson.optJSONObject("result")?.optBigIntegerString("amount") ?: "0"
    val ledger = nativeJson.optJSONObject("result")?.optLongOrNull("block_height")
    val balances = buildList {
        add(accountBalance(assets.nativeAsset(), nativeAmount, rpc.name, ledger, nowMillis))
        tokenResults.forEach { result ->
            val pair = result.getOrNull()
            if (pair != null) {
                val (asset, tokenJson) = pair
                val bytes = tokenJson.optJSONObject("result")?.optJSONArray("result") ?: JSONArray()
                val amountRaw = bytes.toUtf8String().trim('"').ifBlank { "0" }
                add(accountBalance(asset, amountRaw, rpc.name, ledger, nowMillis))
            }
        }
    }
    val errors = tokenResults.mapNotNull { it.exceptionOrNull()?.message }
    AccountChainBalanceResult(
        balances = balances,
        providerName = rpc.name,
        latestLedger = ledger,
        completeness = if (errors.isEmpty()) EvmSyncCompleteness.Complete else EvmSyncCompleteness.Partial,
        error = errors.joinToString(" | ").ifBlank { null },
    )
}

private suspend fun nearRpcQuery(
    httpClient: AccountChainHttpClient,
    provider: AccountChainProvider,
    requestType: String,
    accountId: String,
    methodName: String? = null,
    argsBase64: String? = null,
): JSONObject =
    JSONObject(
        httpClient.post(
            provider = provider,
            path = "",
            body = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", "satra")
                .put("method", "query")
                .put(
                    "params",
                    JSONObject()
                        .put("request_type", requestType)
                        .put("finality", "final")
                        .put("account_id", accountId)
                        .apply {
                            methodName?.let { put("method_name", it) }
                            argsBase64?.let { put("args_base64", it) }
                        },
                ),
        ),
    ).also { response ->
        response.optJSONObject("error")?.let { error("NEAR RPC error: $it") }
    }

private suspend fun syncNearHistory(
    httpClient: AccountChainHttpClient,
    walletId: String,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    limit: Int,
): AccountChainHistoryResult = coroutineScope {
    val provider = config.providers.first { it.name.contains("nearblocks") }
    val nativeDeferred = async {
        runCatching {
            JSONObject(
                httpClient.get(
                    provider,
                    "/v1/account/$address/txns" + queryString(
                        "limit" to limit.coerceIn(1, 50).toString(),
                        "page" to "1",
                        "order" to "desc",
                    ),
                ),
            )
        }
    }
    val ftDeferred = async {
        runCatching {
            JSONObject(
                httpClient.get(
                    provider,
                    "/v1/account/$address/ft-txns" + queryString(
                        "limit" to limit.coerceIn(1, 50).toString(),
                        "page" to "1",
                        "order" to "desc",
                    ),
                ),
            )
        }
    }
    val nativeAsset = assets.nativeAsset()
    val tokensByContract = assets.tokenAssets().associateBy { checkNotNull(it.contractAddress) }
    val nativeResult = nativeDeferred.await()
    val ftResult = ftDeferred.await()
    val transactions = buildList {
        nativeResult.getOrNull()
            ?.optJSONArray("txns")
            ?.objects()
            .orEmpty()
            .mapNotNull { item -> item.toNearNativeTransaction(walletId, address, nativeAsset, provider.name) }
            .let(::addAll)
        ftResult.getOrNull()
            ?.optJSONArray("txns")
            ?.objects()
            .orEmpty()
            .mapNotNull { item -> item.toNearTokenTransaction(walletId, address, tokensByContract, provider.name) }
            .let(::addAll)
    }.distinctBy { it.transactionHash to it.assetId }
        .sortedByDescending { it.timestampMillis }
        .take(limit)
    val errors = listOfNotNull(
        nativeResult.exceptionOrNull()?.message,
        ftResult.exceptionOrNull()?.message,
    )
    AccountChainHistoryResult(
        transactions = transactions,
        providerName = provider.name,
        latestLedger = null,
        completeness = if (errors.isEmpty()) EvmSyncCompleteness.Complete else EvmSyncCompleteness.Partial,
        cursor = null,
        error = errors.joinToString(" | ").ifBlank { null },
    )
}

private fun JSONObject.toNearNativeTransaction(
    walletId: String,
    address: String,
    asset: SupportedAsset,
    providerName: String,
): AccountChainNormalizedTransaction? {
    val hash = optString("transaction_hash")
        .ifBlank { optString("hash") }
        .takeIf(String::isNotBlank) ?: return null
    val signer = optStringOrNull("predecessor_account_id") ?: optStringOrNull("signer_account_id")
    val receiver = optStringOrNull("receiver_account_id")
    val amountRaw = listOfNotNull(
        optBigIntegerString("deposit"),
        optJSONObject("actions_agg")?.optBigIntegerString("deposit"),
        nearActionDepositRaw(),
    ).firstOrNull { it.toAccountBigIntegerFromJsonNumberOrZero() > BigInteger.ZERO }
        ?: return null
    if (amountRaw.toAccountBigIntegerFromJsonNumberOrZero() == BigInteger.ZERO) return null
    val normalizedAmountRaw = amountRaw.toAccountBigIntegerFromJsonNumberOrZero().toString()
    val feeRaw = optBigIntegerString("transaction_fee")
        ?: optJSONObject("outcomes_agg")?.optBigIntegerString("transaction_fee")
    return AccountChainNormalizedTransaction(
        walletId = walletId,
        assetId = asset.assetId,
        networkId = asset.networkId,
        transactionHash = hash,
        direction = directionFor(address, signer, receiver),
        status = if (optString("outcomes_status").equals("SUCCESS", true)) {
            WalletTransactionStatus.Success
        } else {
            WalletTransactionStatus.Success
        },
        amountRaw = normalizedAmountRaw,
        amountDecimal = normalizedAmountRaw.toAccountBigIntegerOrZero().toAccountDecimalString(asset.decimals),
        feeRaw = feeRaw?.toAccountBigIntegerFromJsonNumberOrZero()?.toString(),
        feeDecimal = feeRaw?.toAccountBigIntegerFromJsonNumberOrZero()?.toAccountDecimalString(asset.decimals),
        feeAssetId = asset.assetId,
        fromAddress = signer,
        toAddress = receiver,
        blockHeight = optLongOrNull("block_height"),
        blockHash = null,
        confirmations = 0,
        timestampMillis = optBigIntegerString("block_timestamp")
            ?.let { runCatching { BigInteger(it).divide(BigInteger("1000000")).toLong() }.getOrNull() }
            ?: System.currentTimeMillis(),
        providerName = providerName,
        metadataJson = JSONObject()
            .put("syncFamily", "account-chain")
            .put("chainFamily", "near")
            .put("eventType", "native_transfer")
            .toString(),
    )
}

private fun JSONObject.toNearTokenTransaction(
    walletId: String,
    address: String,
    tokensByContract: Map<String, SupportedAsset>,
    providerName: String,
): AccountChainNormalizedTransaction? {
    val contract = optStringOrNull("ft_contract_id")
        ?: optStringOrNull("token_id")
        ?: optJSONObject("ft")?.optStringOrNull("contract")
        ?: return null
    val asset = tokensByContract[contract] ?: return null
    val hash = optString("transaction_hash")
        .ifBlank { optString("hash") }
        .takeIf(String::isNotBlank) ?: return null
    val fromAddress = optStringOrNull("cause")
        ?: optStringOrNull("old_owner_id")
        ?: optStringOrNull("sender")
    val toAddress = optStringOrNull("involved_account_id")
        ?: optStringOrNull("new_owner_id")
        ?: optStringOrNull("receiver")
    val amountRaw = optBigIntegerString("delta_amount")
        ?: optBigIntegerString("amount")
        ?: return null
    val normalizedAmount = amountRaw.toAccountBigIntegerFromJsonNumberOrZero().abs().toString()
    return AccountChainNormalizedTransaction(
        walletId = walletId,
        assetId = asset.assetId,
        networkId = asset.networkId,
        transactionHash = hash,
        direction = directionFor(address, fromAddress, toAddress),
        status = WalletTransactionStatus.Success,
        amountRaw = normalizedAmount,
        amountDecimal = normalizedAmount.toAccountBigIntegerOrZero().toAccountDecimalString(asset.decimals),
        feeRaw = null,
        feeDecimal = null,
        feeAssetId = null,
        fromAddress = fromAddress,
        toAddress = toAddress,
        blockHeight = optLongOrNull("block_height"),
        blockHash = null,
        confirmations = 0,
        timestampMillis = optBigIntegerString("block_timestamp")
            ?.let { runCatching { BigInteger(it).divide(BigInteger("1000000")).toLong() }.getOrNull() }
            ?: System.currentTimeMillis(),
        providerName = providerName,
        metadataJson = JSONObject()
            .put("syncFamily", "account-chain")
            .put("chainFamily", "near")
            .put("eventType", "nep141_transfer")
            .put("contractAddress", contract)
            .toString(),
    )
}

private fun JSONObject.nearActionDepositRaw(): String? {
    val total = optJSONArray("actions")
        ?.objects()
        .orEmpty()
        .fold(BigInteger.ZERO) { acc, action ->
            acc + (action.optBigIntegerString("deposit")?.toAccountBigIntegerFromJsonNumberOrZero() ?: BigInteger.ZERO)
        }
    return total.takeIf { it > BigInteger.ZERO }?.toString()
}

private suspend fun syncPolkadotBalances(
    httpClient: AccountChainHttpClient,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    nowMillis: Long,
): AccountChainBalanceResult {
    val dotProvider = config.providers.first { it.name.contains("sidecar") && !it.name.contains("asset-hub") }
    val dotResult = runCatching {
        JSONObject(httpClient.get(dotProvider, "/accounts/$address/balance-info"))
    }
    val nativeAmount = dotResult.getOrNull()
        ?.optJSONObject("data")
        ?.optBigIntegerString("free")
        ?: dotResult.getOrNull()?.optBigIntegerString("free")
        ?: "0"
    val balances = dotResult.getOrNull()?.let {
        listOf(accountBalance(assets.nativeAsset(), nativeAmount, dotProvider.name, null, nowMillis))
    }.orEmpty()
    val errors = buildList {
        dotResult.exceptionOrNull()?.message?.let { add("DOT balance: $it") }
        if (assets.tokenAssets().isNotEmpty()) {
            add("Asset Hub token balance requires a healthy public Asset Hub indexer; public Sidecar is currently unavailable.")
        }
    }
    return AccountChainBalanceResult(
        balances = balances,
        providerName = dotProvider.name,
        latestLedger = null,
        completeness = if (errors.isEmpty()) EvmSyncCompleteness.Complete else EvmSyncCompleteness.Partial,
        error = errors.joinToString(" | ").ifBlank { null },
    )
}

private fun syncPolkadotHistory(
    config: AccountChainNetworkConfig,
): AccountChainHistoryResult =
    AccountChainHistoryResult(
        transactions = emptyList(),
        providerName = config.providers.firstOrNull()?.name,
        latestLedger = null,
        completeness = EvmSyncCompleteness.Partial,
        cursor = null,
        error = "No stable no-key public Polkadot/Asset Hub transaction indexer is configured. Balance sync remains enabled; history is marked partial.",
    )

private suspend fun syncRippleBalances(
    httpClient: AccountChainHttpClient,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    nowMillis: Long,
): AccountChainBalanceResult {
    val accountInfo = config.tryProviders("account_info") { provider ->
        xrplRpc(
            httpClient = httpClient,
            provider = provider,
            method = "account_info",
            params = JSONArray().put(
                JSONObject()
                    .put("account", address)
                    .put("ledger_index", "validated"),
            ),
        )
    }
    val result = accountInfo.value.optJSONObject("result") ?: JSONObject()
    val accountData = result.optJSONObject("account_data")
    val nativeRaw = accountData?.optBigIntegerString("Balance") ?: "0"
    val ledger = result.optLongOrNull("ledger_current_index")
        ?: result.optLongOrNull("ledger_index")
    val tokenLines = runCatching {
        xrplRpc(
            httpClient = httpClient,
            provider = accountInfo.provider,
            method = "account_lines",
            params = JSONArray().put(
                JSONObject()
                    .put("account", address)
                    .put("ledger_index", "validated"),
            ),
        ).optJSONObject("result")
            ?.optJSONArray("lines")
            ?.objects()
            .orEmpty()
    }.getOrDefault(emptyList())
    val tokenLinesByContract = tokenLines.associateBy { line ->
        "${line.optString("currency")}.${line.optString("account")}"
    }
    val balances = buildList {
        add(accountBalance(assets.nativeAsset(), nativeRaw, accountInfo.provider.name, ledger, nowMillis))
        assets.tokenAssets().forEach { asset ->
            val line = tokenLinesByContract[checkNotNull(asset.contractAddress)]
            val raw = line?.optString("balance")?.toRawAccountAmount(asset.decimals) ?: "0"
            add(accountBalance(asset, raw, accountInfo.provider.name, ledger, nowMillis))
        }
    }
    return AccountChainBalanceResult(
        balances = balances,
        providerName = accountInfo.provider.name,
        latestLedger = ledger,
        completeness = EvmSyncCompleteness.Complete,
        error = null,
    )
}

private suspend fun xrplRpc(
    httpClient: AccountChainHttpClient,
    provider: AccountChainProvider,
    method: String,
    params: JSONArray,
): JSONObject =
    JSONObject(
        httpClient.post(
            provider = provider,
            path = "",
            body = JSONObject()
                .put("method", method)
                .put("params", params),
        ),
    ).also { response ->
        val result = response.optJSONObject("result")
        val status = result?.optString("status")
        val error = result?.optString("error").orEmpty()
        if (status == "error" && error != "actNotFound") {
            error("XRPL $method failed: $result")
        }
    }

private suspend fun syncRippleHistory(
    httpClient: AccountChainHttpClient,
    walletId: String,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    latestLedger: Long?,
    nowMillis: Long,
    limit: Int,
): AccountChainHistoryResult {
    val response = config.tryProviders("account_tx") { provider ->
        xrplRpc(
            httpClient = httpClient,
            provider = provider,
            method = "account_tx",
            params = JSONArray().put(
                JSONObject()
                    .put("account", address)
                    .put("ledger_index_min", -1)
                    .put("ledger_index_max", -1)
                    .put("limit", limit.coerceIn(1, 100))
                    .put("forward", false),
            ),
        )
    }
    val nativeAsset = assets.nativeAsset()
    val tokensByContract = assets.tokenAssets().associateBy { checkNotNull(it.contractAddress) }
    val transactions = response.value.optJSONObject("result")
        ?.optJSONArray("transactions")
        ?.objects()
        .orEmpty()
        .mapNotNull { item ->
            item.toRippleTransaction(
                walletId = walletId,
                address = address,
                nativeAsset = nativeAsset,
                tokensByContract = tokensByContract,
                latestLedger = latestLedger,
                providerName = response.provider.name,
                fallbackTimestampMillis = 0L,
            )
        }
    return AccountChainHistoryResult(
        transactions = transactions,
        providerName = response.provider.name,
        latestLedger = latestLedger,
        completeness = EvmSyncCompleteness.Complete,
        cursor = response.value.optJSONObject("result")?.optJSONObject("marker")?.toString(),
        error = null,
    )
}

private fun JSONObject.toRippleTransaction(
    walletId: String,
    address: String,
    nativeAsset: SupportedAsset,
    tokensByContract: Map<String, SupportedAsset>,
    latestLedger: Long?,
    providerName: String,
    fallbackTimestampMillis: Long,
): AccountChainNormalizedTransaction? {
    val tx = optJSONObject("tx_json") ?: optJSONObject("tx") ?: this
    if (tx.optString("TransactionType") != "Payment") return null
    val meta = optJSONObject("meta") ?: optJSONObject("metaData")
    val hash = tx.optString("hash").ifBlank { optString("hash") }.takeIf(String::isNotBlank) ?: return null
    val fromAddress = tx.optStringOrNull("Account")
    val toAddress = tx.optStringOrNull("Destination")
    val delivered = meta?.opt("delivered_amount") ?: tx.opt("Amount") ?: return null
    val (asset, amountRaw) = when (delivered) {
        is String -> nativeAsset to delivered
        is JSONObject -> {
            val contract = "${delivered.optString("currency")}.${delivered.optString("issuer")}"
            val asset = tokensByContract[contract] ?: return null
            asset to delivered.optString("value").toRawAccountAmount(asset.decimals)
        }
        else -> return null
    }
    val ledgerIndex = tx.optLongOrNull("ledger_index") ?: optLongOrNull("ledger_index")
    val confirmations = latestLedger?.let { ledger ->
        ledgerIndex?.let { (ledger - it).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
    } ?: 0
    return AccountChainNormalizedTransaction(
        walletId = walletId,
        assetId = asset.assetId,
        networkId = asset.networkId,
        transactionHash = hash,
        direction = directionFor(address, fromAddress, toAddress),
        status = if (meta?.optString("TransactionResult") == "tesSUCCESS" || meta == null) {
            WalletTransactionStatus.Success
        } else {
            WalletTransactionStatus.Failed
        },
        amountRaw = amountRaw,
        amountDecimal = amountRaw.toAccountBigIntegerOrZero().toAccountDecimalString(asset.decimals),
        feeRaw = tx.optBigIntegerString("Fee"),
        feeDecimal = tx.optBigIntegerString("Fee")?.toAccountBigIntegerOrZero()?.toAccountDecimalString(nativeAsset.decimals),
        feeAssetId = nativeAsset.assetId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        blockHeight = ledgerIndex,
        blockHash = null,
        confirmations = confirmations,
        timestampMillis = tx.optLongOrNull("date")
            ?.let { (it + XRPL_EPOCH_OFFSET_SECONDS) * 1_000L }
            ?: fallbackTimestampMillis,
        providerName = providerName,
        metadataJson = JSONObject()
            .put("syncFamily", "account-chain")
            .put("chainFamily", "ripple")
            .toString(),
    )
}

private suspend fun syncStellarBalances(
    httpClient: AccountChainHttpClient,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    nowMillis: Long,
): AccountChainBalanceResult {
    val providerResult = config.tryProviders("account") { provider ->
        JSONObject(httpClient.get(provider, "/accounts/$address"))
    }
    val nativeBalance = providerResult.value.optJSONArray("balances")
        ?.objects()
        .orEmpty()
        .firstOrNull { it.optString("asset_type") == "native" }
        ?.optString("balance")
        ?.toRawAccountAmount(assets.nativeAsset().decimals)
        ?: "0"
    return AccountChainBalanceResult(
        balances = listOf(accountBalance(assets.nativeAsset(), nativeBalance, providerResult.provider.name, null, nowMillis)),
        providerName = providerResult.provider.name,
        latestLedger = null,
        completeness = EvmSyncCompleteness.Complete,
        error = null,
    )
}

private suspend fun syncStellarHistory(
    httpClient: AccountChainHttpClient,
    walletId: String,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    limit: Int,
): AccountChainHistoryResult {
    val providerResult = config.tryProviders("payments") { provider ->
        JSONObject(
            httpClient.get(
                provider,
                "/accounts/$address/payments" + queryString(
                    "limit" to limit.coerceIn(1, 200).toString(),
                    "order" to "desc",
                    "include_failed" to "true",
                ),
            ),
        )
    }
    val asset = assets.nativeAsset()
    val transactions = providerResult.value.optJSONObject("_embedded")
        ?.optJSONArray("records")
        ?.objects()
        .orEmpty()
        .mapNotNull { item -> item.toStellarTransaction(walletId, address, asset, providerResult.provider.name) }
    return AccountChainHistoryResult(
        transactions = transactions,
        providerName = providerResult.provider.name,
        latestLedger = null,
        completeness = EvmSyncCompleteness.Complete,
        cursor = null,
        error = null,
    )
}

private fun JSONObject.toStellarTransaction(
    walletId: String,
    address: String,
    asset: SupportedAsset,
    providerName: String,
): AccountChainNormalizedTransaction? {
    if (optString("type") != "payment" || optString("asset_type") != "native") return null
    val fromAddress = optStringOrNull("from")
    val toAddress = optStringOrNull("to")
    val amountRaw = optString("amount").toRawAccountAmount(asset.decimals)
    return AccountChainNormalizedTransaction(
        walletId = walletId,
        assetId = asset.assetId,
        networkId = asset.networkId,
        transactionHash = optString("transaction_hash").takeIf(String::isNotBlank) ?: optString("id"),
        direction = directionFor(address, fromAddress, toAddress),
        status = WalletTransactionStatus.Success,
        amountRaw = amountRaw,
        amountDecimal = amountRaw.toAccountBigIntegerOrZero().toAccountDecimalString(asset.decimals),
        feeRaw = null,
        feeDecimal = null,
        feeAssetId = null,
        fromAddress = fromAddress,
        toAddress = toAddress,
        blockHeight = null,
        blockHash = null,
        confirmations = 0,
        timestampMillis = optString("created_at")
            .takeIf(String::isNotBlank)
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: System.currentTimeMillis(),
        providerName = providerName,
        metadataJson = JSONObject()
            .put("syncFamily", "account-chain")
            .put("chainFamily", "stellar")
            .toString(),
    )
}

private suspend fun syncSuiBalances(
    httpClient: AccountChainHttpClient,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    nowMillis: Long,
): AccountChainBalanceResult {
    val response = config.tryProviders("suix_getBalance") { provider ->
        suiRpc(
            httpClient = httpClient,
            provider = provider,
            method = "suix_getBalance",
            params = JSONArray()
                .put(address)
                .put(SUI_COIN_TYPE),
        )
    }
    val result = response.value.optJSONObject("result") ?: JSONObject()
    val raw = result.optBigIntegerString("totalBalance") ?: "0"
    return AccountChainBalanceResult(
        balances = listOf(accountBalance(assets.nativeAsset(), raw, response.provider.name, null, nowMillis)),
        providerName = response.provider.name,
        latestLedger = null,
        completeness = EvmSyncCompleteness.Complete,
        error = null,
    )
}

private suspend fun suiRpc(
    httpClient: AccountChainHttpClient,
    provider: AccountChainProvider,
    method: String,
    params: JSONArray,
): JSONObject =
    JSONObject(
        httpClient.post(
            provider = provider,
            path = "",
            body = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", method)
                .put("params", params),
        ),
    ).also { response ->
        response.optJSONObject("error")?.let { error("Sui $method failed: $it") }
    }

private suspend fun syncSuiHistory(
    httpClient: AccountChainHttpClient,
    walletId: String,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    latestLedger: Long?,
    nowMillis: Long,
    limit: Int,
): AccountChainHistoryResult = coroutineScope {
    val provider = config.providers.first()
    val fromDeferred = async {
        runCatching {
            suiTransactionBlocks(httpClient, provider, address, "FromAddress", limit)
        }
    }
    val toDeferred = async {
        runCatching {
            suiTransactionBlocks(httpClient, provider, address, "ToAddress", limit)
        }
    }
    val fromResult = fromDeferred.await()
    val toResult = toDeferred.await()
    val asset = assets.nativeAsset()
    val transactions = (fromResult.getOrDefault(emptyList()) + toResult.getOrDefault(emptyList()))
        .distinctBy { it.optString("digest") }
        .mapNotNull { item ->
            item.toSuiTransaction(
                walletId = walletId,
                address = address,
                asset = asset,
                latestLedger = latestLedger,
                providerName = provider.name,
                fallbackTimestampMillis = 0L,
            )
        }
        .sortedByDescending { it.timestampMillis }
        .take(limit)
    val errors = listOfNotNull(
        fromResult.exceptionOrNull()?.message,
        toResult.exceptionOrNull()?.message,
    )
    AccountChainHistoryResult(
        transactions = transactions,
        providerName = provider.name,
        latestLedger = latestLedger,
        completeness = if (errors.isEmpty()) EvmSyncCompleteness.Complete else EvmSyncCompleteness.Partial,
        cursor = null,
        error = errors.joinToString(" | ").ifBlank { null },
    )
}

private suspend fun suiTransactionBlocks(
    httpClient: AccountChainHttpClient,
    provider: AccountChainProvider,
    address: String,
    filterName: String,
    limit: Int,
): List<JSONObject> {
    val response = suiRpc(
        httpClient = httpClient,
        provider = provider,
        method = "suix_queryTransactionBlocks",
        params = JSONArray()
            .put(
                JSONObject()
                    .put("filter", JSONObject().put(filterName, address))
                    .put(
                        "options",
                        JSONObject()
                            .put("showEffects", true)
                            .put("showBalanceChanges", true),
                    ),
            )
            .put(JSONObject.NULL)
            .put(limit.coerceIn(1, 50))
            .put(true),
    )
    return response.optJSONObject("result")?.optJSONArray("data")?.objects().orEmpty()
}

private fun JSONObject.toSuiTransaction(
    walletId: String,
    address: String,
    asset: SupportedAsset,
    latestLedger: Long?,
    providerName: String,
    fallbackTimestampMillis: Long,
): AccountChainNormalizedTransaction? {
    val digest = optString("digest").takeIf(String::isNotBlank) ?: return null
    val balanceChange = optJSONArray("balanceChanges")
        ?.objects()
        .orEmpty()
        .firstOrNull { change ->
            change.optString("coinType") == SUI_COIN_TYPE &&
                change.optJSONObject("owner")?.optString("AddressOwner") == address
        } ?: return null
    val amount = balanceChange.optBigIntegerString("amount")?.toAccountBigIntegerOrZero() ?: return null
    if (amount == BigInteger.ZERO) return null
    val checkpoint = optString("checkpoint").toLongOrNull()
    val confirmations = latestLedger?.let { ledger ->
        checkpoint?.let { (ledger - it).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
    } ?: 0
    val status = optJSONObject("effects")
        ?.optJSONObject("status")
        ?.optString("status")
        ?.let { if (it == "success") WalletTransactionStatus.Success else WalletTransactionStatus.Failed }
        ?: WalletTransactionStatus.Success
    return AccountChainNormalizedTransaction(
        walletId = walletId,
        assetId = asset.assetId,
        networkId = asset.networkId,
        transactionHash = digest,
        direction = amount.toDirection(),
        status = status,
        amountRaw = amount.abs().toString(),
        amountDecimal = amount.abs().toAccountDecimalString(asset.decimals),
        feeRaw = optJSONObject("effects")?.optJSONObject("gasUsed")?.suiGasFeeRaw(),
        feeDecimal = optJSONObject("effects")?.optJSONObject("gasUsed")?.suiGasFeeRaw()
            ?.toAccountBigIntegerOrZero()
            ?.toAccountDecimalString(asset.decimals),
        feeAssetId = asset.assetId,
        fromAddress = null,
        toAddress = null,
        blockHeight = checkpoint,
        blockHash = null,
        confirmations = confirmations,
        timestampMillis = optLongOrNull("timestampMs") ?: fallbackTimestampMillis,
        providerName = providerName,
        metadataJson = JSONObject()
            .put("syncFamily", "account-chain")
            .put("chainFamily", "sui")
            .toString(),
    )
}

private suspend fun syncTonBalances(
    httpClient: AccountChainHttpClient,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    nowMillis: Long,
): AccountChainBalanceResult = coroutineScope {
    val tonApi = config.providers.first { it.name.contains("tonapi") }
    val nativeDeferred = async {
        runCatching { JSONObject(httpClient.get(tonApi, "/v2/accounts/$address")) }
    }
    val tokenDeferred = assets.tokenAssets().map { asset ->
        async {
            runCatching {
                val master = checkNotNull(asset.contractAddress)
                asset to JSONObject(httpClient.get(tonApi, "/v2/accounts/$address/jettons/$master"))
            }
        }
    }
    val nativeResult = nativeDeferred.await()
    val tokenResults = tokenDeferred.awaitAll()
    val nativeJson = nativeResult.getOrThrow()
    val nativeRaw = nativeJson.optBigIntegerString("balance") ?: "0"
    val balances = buildList {
        add(accountBalance(assets.nativeAsset(), nativeRaw, tonApi.name, null, nowMillis))
        tokenResults.forEach { result ->
            val pair = result.getOrNull()
            if (pair != null) {
                val (asset, json) = pair
                val raw = json.optBigIntegerString("balance") ?: "0"
                add(accountBalance(asset, raw, tonApi.name, null, nowMillis))
            }
        }
    }
    val errors = tokenResults.mapNotNull { it.exceptionOrNull()?.message }
    AccountChainBalanceResult(
        balances = balances,
        providerName = tonApi.name,
        latestLedger = null,
        completeness = if (errors.isEmpty()) EvmSyncCompleteness.Complete else EvmSyncCompleteness.Partial,
        error = errors.joinToString(" | ").ifBlank { null },
    )
}

private suspend fun syncTonHistory(
    httpClient: AccountChainHttpClient,
    walletId: String,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    nowMillis: Long,
    limit: Int,
): AccountChainHistoryResult = coroutineScope {
    val provider = config.providers.first { it.name.contains("tonapi") }
    val transactionsDeferred = async {
        runCatching {
            JSONObject(
                httpClient.get(
                    provider,
                    "/v2/blockchain/accounts/$address/transactions" + queryString(
                        "limit" to limit.coerceIn(1, 100).toString(),
                    ),
                ),
            )
        }
    }
    val eventsDeferred = async {
        runCatching {
            JSONObject(
                httpClient.get(
                    provider,
                    "/v2/accounts/$address/events" + queryString(
                        "limit" to limit.coerceIn(1, 100).toString(),
                    ),
                ),
            )
        }
    }
    val nativeAsset = assets.nativeAsset()
    val tokenAssetsByMaster = assets.tokenAssets().associateBy { checkNotNull(it.contractAddress) }
    val transactionsResult = transactionsDeferred.await()
    val eventsResult = eventsDeferred.await()
    val transactions = buildList {
        transactionsResult.getOrNull()
            ?.optJSONArray("transactions")
            ?.objects()
            .orEmpty()
            .mapNotNull { item -> item.toTonNativeTransaction(walletId, address, nativeAsset, provider.name, nowMillis) }
            .let(::addAll)
        eventsResult.getOrNull()
            ?.optJSONArray("events")
            ?.objects()
            .orEmpty()
            .flatMap { item -> item.toTonJettonTransactions(walletId, address, tokenAssetsByMaster, provider.name, nowMillis) }
            .let(::addAll)
    }.distinctBy { it.transactionHash to it.assetId }
        .sortedByDescending { it.timestampMillis }
        .take(limit)
    val errors = listOfNotNull(
        transactionsResult.exceptionOrNull()?.message,
        eventsResult.exceptionOrNull()?.message,
    )
    AccountChainHistoryResult(
        transactions = transactions,
        providerName = provider.name,
        latestLedger = null,
        completeness = if (errors.isEmpty()) EvmSyncCompleteness.Complete else EvmSyncCompleteness.Partial,
        cursor = null,
        error = errors.joinToString(" | ").ifBlank { null },
    )
}

private fun JSONObject.toTonNativeTransaction(
    walletId: String,
    address: String,
    asset: SupportedAsset,
    providerName: String,
    fallbackTimestampMillis: Long,
): AccountChainNormalizedTransaction? {
    val hash = optString("hash").takeIf(String::isNotBlank) ?: return null
    val inMsg = optJSONObject("in_msg")
    val outMsgs = optJSONArray("out_msgs")?.objects().orEmpty()
    val inboundValue = inMsg?.optBigIntegerString("value")?.toAccountBigIntegerOrZero() ?: BigInteger.ZERO
    val outgoing = outMsgs.firstOrNull { (it.optBigIntegerString("value") ?: "0").toAccountBigIntegerOrZero() > BigInteger.ZERO }
    val outgoingValue = outgoing?.optBigIntegerString("value")?.toAccountBigIntegerOrZero() ?: BigInteger.ZERO
    val amount = when {
        inboundValue > BigInteger.ZERO -> inboundValue
        outgoingValue > BigInteger.ZERO -> outgoingValue
        else -> return null
    }
    val fromAddress = inMsg?.optJSONObject("source")?.optStringOrNull("address")
        ?: outgoing?.optJSONObject("source")?.optStringOrNull("address")
    val toAddress = inMsg?.optJSONObject("destination")?.optStringOrNull("address")
        ?: outgoing?.optJSONObject("destination")?.optStringOrNull("address")
    return AccountChainNormalizedTransaction(
        walletId = walletId,
        assetId = asset.assetId,
        networkId = asset.networkId,
        transactionHash = hash,
        direction = if (inboundValue > BigInteger.ZERO) WalletTransactionDirection.Incoming else WalletTransactionDirection.Outgoing,
        status = if (optBoolean("success", true)) WalletTransactionStatus.Success else WalletTransactionStatus.Failed,
        amountRaw = amount.toString(),
        amountDecimal = amount.toAccountDecimalString(asset.decimals),
        feeRaw = optBigIntegerString("total_fees"),
        feeDecimal = optBigIntegerString("total_fees")?.toAccountBigIntegerOrZero()?.toAccountDecimalString(asset.decimals),
        feeAssetId = asset.assetId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        blockHeight = optLongOrNull("lt"),
        blockHash = null,
        confirmations = 0,
        timestampMillis = optLongOrNull("utime")?.times(1_000L) ?: fallbackTimestampMillis,
        providerName = providerName,
        metadataJson = JSONObject()
            .put("syncFamily", "account-chain")
            .put("chainFamily", "ton")
            .put("eventType", "native_transfer")
            .toString(),
    )
}

private fun JSONObject.toTonJettonTransactions(
    walletId: String,
    address: String,
    tokenAssetsByMaster: Map<String, SupportedAsset>,
    providerName: String,
    fallbackTimestampMillis: Long,
): List<AccountChainNormalizedTransaction> {
    val eventId = optString("event_id").takeIf(String::isNotBlank) ?: optString("id")
    val timestampMillis = optLongOrNull("timestamp")?.times(1_000L) ?: fallbackTimestampMillis
    val actions = optJSONArray("actions")?.objects().orEmpty()
    return actions.mapNotNull { action ->
        if (action.optString("type") != "JettonTransfer") return@mapNotNull null
        val transfer = action.optJSONObject("JettonTransfer") ?: return@mapNotNull null
        val jetton = transfer.optJSONObject("jetton")
        val master = jetton?.optStringOrNull("address") ?: transfer.optStringOrNull("jetton_master")
        val asset = master?.let { tokenAssetsByMaster[it] } ?: return@mapNotNull null
        val fromAddress = transfer.optJSONObject("sender")?.optStringOrNull("address")
        val toAddress = transfer.optJSONObject("recipient")?.optStringOrNull("address")
        val amountRaw = transfer.optBigIntegerString("amount") ?: return@mapNotNull null
        AccountChainNormalizedTransaction(
            walletId = walletId,
            assetId = asset.assetId,
            networkId = asset.networkId,
            transactionHash = "$eventId:${asset.assetId}",
            direction = directionFor(address, fromAddress, toAddress),
            status = if (action.optString("status") == "failed") WalletTransactionStatus.Failed else WalletTransactionStatus.Success,
            amountRaw = amountRaw,
            amountDecimal = amountRaw.toAccountBigIntegerOrZero().toAccountDecimalString(asset.decimals),
            feeRaw = null,
            feeDecimal = null,
            feeAssetId = null,
            fromAddress = fromAddress,
            toAddress = toAddress,
            blockHeight = null,
            blockHash = null,
            confirmations = 0,
            timestampMillis = timestampMillis,
            providerName = providerName,
            metadataJson = JSONObject()
                .put("syncFamily", "account-chain")
                .put("chainFamily", "ton")
                .put("eventType", "jetton_transfer")
                .put("master", master)
                .toString(),
        )
    }
}

private suspend fun syncKavaBalances(
    httpClient: AccountChainHttpClient,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    nowMillis: Long,
): AccountChainBalanceResult {
    val response = config.tryProviders("bank balances") { provider ->
        JSONObject(httpClient.get(provider, "/cosmos/bank/v1beta1/balances/$address"))
    }
    val amountsByDenom = response.value.optJSONArray("balances")
        ?.objects()
        .orEmpty()
        .associate { coin -> coin.optString("denom") to (coin.optBigIntegerString("amount") ?: "0") }
    val balances = assets.map { asset ->
        val denom = if (asset.assetType == "NATIVE") KAVA_DENOM else checkNotNull(asset.contractAddress)
        accountBalance(asset, amountsByDenom[denom] ?: "0", response.provider.name, null, nowMillis)
    }
    return AccountChainBalanceResult(
        balances = balances,
        providerName = response.provider.name,
        latestLedger = null,
        completeness = EvmSyncCompleteness.Complete,
        error = null,
    )
}

private suspend fun syncKavaHistory(
    httpClient: AccountChainHttpClient,
    walletId: String,
    config: AccountChainNetworkConfig,
    address: String,
    assets: List<SupportedAsset>,
    nowMillis: Long,
    limit: Int,
): AccountChainHistoryResult = coroutineScope {
    val provider = config.providers.first()
    val receivedDeferred = async {
        runCatching { kavaTransferTxs(httpClient, provider, "transfer.recipient='$address'", limit) }
    }
    val sentDeferred = async {
        runCatching { kavaTransferTxs(httpClient, provider, "message.sender='$address'", limit) }
    }
    val received = receivedDeferred.await()
    val sent = sentDeferred.await()
    val assetsByDenom = assets.associateBy { if (it.assetType == "NATIVE") KAVA_DENOM else checkNotNull(it.contractAddress) }
    val transactions = (received.getOrDefault(emptyList()) + sent.getOrDefault(emptyList()))
        .distinctBy { it.optString("txhash") }
        .flatMap { tx ->
            tx.toKavaTransactions(
                walletId = walletId,
                address = address,
                assetsByDenom = assetsByDenom,
                providerName = provider.name,
                fallbackTimestampMillis = 0L,
            )
        }
        .sortedByDescending { it.timestampMillis }
        .take(limit)
    val errors = listOfNotNull(
        received.exceptionOrNull()?.message,
        sent.exceptionOrNull()?.message,
    )
    AccountChainHistoryResult(
        transactions = transactions,
        providerName = provider.name,
        latestLedger = null,
        completeness = if (errors.isEmpty()) EvmSyncCompleteness.Complete else EvmSyncCompleteness.Partial,
        cursor = null,
        error = errors.joinToString(" | ").ifBlank { null },
    )
}

private suspend fun kavaTransferTxs(
    httpClient: AccountChainHttpClient,
    provider: AccountChainProvider,
    event: String,
    limit: Int,
): List<JSONObject> =
    JSONObject(
        httpClient.get(
            provider,
            "/cosmos/tx/v1beta1/txs" + queryString(
                "events" to event,
                "order_by" to "ORDER_BY_DESC",
                "pagination.limit" to limit.coerceIn(1, 50).toString(),
            ),
        ),
    ).optJSONArray("tx_responses")?.objects().orEmpty()

private fun JSONObject.toKavaTransactions(
    walletId: String,
    address: String,
    assetsByDenom: Map<String, SupportedAsset>,
    providerName: String,
    fallbackTimestampMillis: Long,
): List<AccountChainNormalizedTransaction> {
    val hash = optString("txhash").takeIf(String::isNotBlank) ?: return emptyList()
    val timestampMillis = optString("timestamp")
        .takeIf(String::isNotBlank)
        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: fallbackTimestampMillis
    val height = optLongOrNull("height")
    val success = optInt("code", 0) == 0
    val transfers = optJSONArray("logs")
        ?.objects()
        .orEmpty()
        .flatMap { log -> log.optJSONArray("events")?.objects().orEmpty() }
        .filter { event -> event.optString("type") == "transfer" }
        .flatMap { event -> event.kavaTransferEvents() }
    return transfers.mapNotNull { transfer ->
        val asset = assetsByDenom[transfer.denom] ?: return@mapNotNull null
        AccountChainNormalizedTransaction(
            walletId = walletId,
            assetId = asset.assetId,
            networkId = asset.networkId,
            transactionHash = "${hash}:${asset.assetId}:${transfer.from}:${transfer.to}:${transfer.amountRaw}",
            direction = directionFor(address, transfer.from, transfer.to),
            status = if (success) WalletTransactionStatus.Success else WalletTransactionStatus.Failed,
            amountRaw = transfer.amountRaw,
            amountDecimal = transfer.amountRaw.toAccountBigIntegerOrZero().toAccountDecimalString(asset.decimals),
            feeRaw = null,
            feeDecimal = null,
            feeAssetId = null,
            fromAddress = transfer.from,
            toAddress = transfer.to,
            blockHeight = height,
            blockHash = null,
            confirmations = 0,
            timestampMillis = timestampMillis,
            providerName = providerName,
            metadataJson = JSONObject()
                .put("syncFamily", "account-chain")
                .put("chainFamily", "kava")
                .put("denom", transfer.denom)
                .put("txhash", hash)
                .toString(),
        )
    }
}

private data class KavaTransfer(
    val from: String?,
    val to: String?,
    val denom: String,
    val amountRaw: String,
)

private fun JSONObject.kavaTransferEvents(): List<KavaTransfer> {
    val attributes = optJSONArray("attributes")?.objects().orEmpty()
    val senders = attributes.values("sender") + attributes.values("spender")
    val recipients = attributes.values("recipient")
    val amounts = attributes.values("amount")
    return amounts.mapIndexedNotNull { index, amount ->
        val (raw, denom) = amount.splitCosmosCoin() ?: return@mapIndexedNotNull null
        KavaTransfer(
            from = senders.getOrNull(index) ?: senders.firstOrNull(),
            to = recipients.getOrNull(index) ?: recipients.firstOrNull(),
            denom = denom,
            amountRaw = raw,
        )
    }
}

private fun List<JSONObject>.values(key: String): List<String> =
    filter { it.optString("key") == key }
        .mapNotNull { it.optStringOrNull("value") }

private fun String.splitCosmosCoin(): Pair<String, String>? {
    val trimmed = trim()
    val raw = trimmed.takeWhile { it.isDigit() }
    val denom = trimmed.drop(raw.length)
    return if (raw.isNotBlank() && denom.isNotBlank()) raw to denom else null
}

private fun directionFor(
    walletAddress: String,
    fromAddress: String?,
    toAddress: String?,
): WalletTransactionDirection {
    val wallet = walletAddress.normalizeAccountAddress()
    val from = fromAddress.normalizeAccountAddress()
    val to = toAddress.normalizeAccountAddress()
    return when {
        from == wallet && to == wallet -> WalletTransactionDirection.Self
        from == wallet -> WalletTransactionDirection.Outgoing
        to == wallet -> WalletTransactionDirection.Incoming
        else -> WalletTransactionDirection.Unknown
    }
}

private fun String?.normalizeAccountAddress(): String =
    orEmpty().trim().lowercase(Locale.US)

private fun BigInteger.toDirection(): WalletTransactionDirection =
    when {
        this > BigInteger.ZERO -> WalletTransactionDirection.Incoming
        this < BigInteger.ZERO -> WalletTransactionDirection.Outgoing
        else -> WalletTransactionDirection.Self
    }

private fun String.toRawAccountAmount(decimals: Int): String =
    runCatching {
        BigDecimal(this)
            .movePointRight(decimals)
            .setScale(0, java.math.RoundingMode.DOWN)
            .toPlainString()
    }.getOrDefault("0")

private fun String.toAccountBigIntegerFromJsonNumberOrZero(): BigInteger =
    runCatching { BigInteger(this) }
        .recoverCatching { BigDecimal(this).toBigInteger() }
        .getOrDefault(BigInteger.ZERO)

private fun JSONArray.toUtf8String(): String =
    buildString {
        for (index in 0 until length()) {
            append(optInt(index).toChar())
        }
    }

private fun JSONObject.suiGasFeeRaw(): String {
    val computation = optBigIntegerString("computationCost")?.toAccountBigIntegerOrZero() ?: BigInteger.ZERO
    val storage = optBigIntegerString("storageCost")?.toAccountBigIntegerOrZero() ?: BigInteger.ZERO
    val rebate = optBigIntegerString("storageRebate")?.toAccountBigIntegerOrZero() ?: BigInteger.ZERO
    return (computation + storage - rebate).coerceAtLeast(BigInteger.ZERO).toString()
}

private fun String.hexTronAddressToBase58(): String? {
    val clean = removePrefix("0x").removePrefix("0X")
    if (clean.length != 42 || !clean.all(Char::isHexDigitForAccountChain)) return null
    val bytes = clean.hexToBytes()
    val checksum = sha256(sha256(bytes)).copyOfRange(0, 4)
    return base58Encode(bytes + checksum)
}

private fun String.hexToBytes(): ByteArray {
    val clean = removePrefix("0x").removePrefix("0X")
    require(clean.length % 2 == 0) { "Hex string must have an even length." }
    return ByteArray(clean.length / 2) { index ->
        clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private fun sha256(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(bytes)

private fun base58Encode(bytes: ByteArray): String {
    var value = BigInteger(1, bytes)
    val output = StringBuilder()
    while (value > BigInteger.ZERO) {
        val divRem = value.divideAndRemainder(BigInteger.valueOf(58))
        value = divRem[0]
        output.append(BASE58_ALPHABET[divRem[1].toInt()])
    }
    bytes.takeWhile { it == 0.toByte() }.forEach { _ -> output.append(BASE58_ALPHABET[0]) }
    return output.reverse().toString()
}

private fun maxOfOrNull(first: Long?, second: Long?): Long? =
    listOfNotNull(first, second).maxOrNull()

private fun Char.isHexDigitForAccountChain(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private const val XRPL_EPOCH_OFFSET_SECONDS = 946684800L
private const val SUI_COIN_TYPE = "0x2::sui::SUI"
private const val KAVA_DENOM = "ukava"
private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
