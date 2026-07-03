package dev.satra.wallet.data.sync.evm

import dev.satra.wallet.data.assets.SupportedAsset
import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

interface EvmHttpGetTransport {
    suspend fun get(
        url: String,
        timeoutMillis: Int,
    ): String
}

class HttpUrlConnectionEvmHttpGetTransport : EvmHttpGetTransport {
    override suspend fun get(
        url: String,
        timeoutMillis: Int,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Satra-Android/0.1")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: error("HTTP $code with empty response")
            }
            val response = BufferedReader(stream.reader(Charsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) {
                error("HTTP $code: ${response.take(MAX_ERROR_LENGTH)}")
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 240
    }
}

class EvmHistorySyncService(
    private val rpcClientFactory: (EvmNetworkConfig) -> EvmJsonRpcClient = { config ->
        EvmJsonRpcClient(config)
    },
    private val httpGetTransport: EvmHttpGetTransport = HttpUrlConnectionEvmHttpGetTransport(),
    private val timeoutMillis: Int = 8_000,
    private val maxLogScanBlockRange: Long = 25_000,
) {
    suspend fun syncHistory(
        walletId: String,
        address: String,
        assets: List<SupportedAsset>,
        latestKnownBlock: Long?,
        nowMillis: Long = System.currentTimeMillis(),
    ): EvmNetworkHistoryResult {
        require(assets.isNotEmpty()) { "No assets supplied for EVM history sync." }
        val networkId = assets.first().networkId
        val config = EvmProviderRegistry.requireConfig(networkId)
        val supportedAssetsByContract = assets
            .filter { it.contractAddress != null }
            .associateBy { EvmAbi.normalizeAddress(checkNotNull(it.contractAddress)) }
        val nativeAsset = assets.firstOrNull { it.assetType == "NATIVE" || it.contractAddress == null }

        val indexedResult = fetchIndexedHistory(
            walletId = walletId,
            address = address,
            config = config,
            nativeAsset = nativeAsset,
            supportedAssetsByContract = supportedAssetsByContract,
            latestKnownBlock = latestKnownBlock,
            nowMillis = nowMillis,
        )
        if (indexedResult.completeness == EvmSyncCompleteness.Complete) {
            return indexedResult
        }

        val logFallback = scanTokenTransferLogs(
            walletId = walletId,
            address = address,
            config = config,
            assetsByContract = supportedAssetsByContract,
            latestKnownBlock = latestKnownBlock,
            nowMillis = nowMillis,
        )

        return EvmNetworkHistoryResult(
            transactions = (indexedResult.transactions + logFallback.transactions).deduplicate(),
            providerName = logFallback.providerName ?: indexedResult.providerName,
            latestBlockNumber = logFallback.latestBlockNumber ?: indexedResult.latestBlockNumber,
            completeness = when {
                indexedResult.completeness == EvmSyncCompleteness.Complete -> EvmSyncCompleteness.Complete
                logFallback.transactions.isNotEmpty() -> EvmSyncCompleteness.Partial
                else -> EvmSyncCompleteness.Partial
            },
            cursorFromBlock = logFallback.cursorFromBlock,
            cursorToBlock = logFallback.cursorToBlock,
            error = listOfNotNull(indexedResult.error, logFallback.error)
                .joinToString(" | ")
                .ifBlank { null },
        )
    }

    private suspend fun fetchIndexedHistory(
        walletId: String,
        address: String,
        config: EvmNetworkConfig,
        nativeAsset: SupportedAsset?,
        supportedAssetsByContract: Map<String, SupportedAsset>,
        latestKnownBlock: Long?,
        nowMillis: Long,
    ): EvmNetworkHistoryResult {
        val failures = mutableListOf<String>()
        config.explorerApis.forEach { api ->
            try {
                val transactions = when (api.style) {
                    EvmExplorerApiStyle.BlockscoutV2 -> fetchBlockscoutV2History(
                        walletId = walletId,
                        address = address,
                        config = config,
                        api = api,
                        nativeAsset = nativeAsset,
                        supportedAssetsByContract = supportedAssetsByContract,
                        latestKnownBlock = latestKnownBlock,
                    )

                    EvmExplorerApiStyle.EtherscanCompatible -> fetchEtherscanCompatibleHistory(
                        walletId = walletId,
                        address = address,
                        config = config,
                        api = api,
                        nativeAsset = nativeAsset,
                        latestKnownBlock = latestKnownBlock,
                    )
                }
                return EvmNetworkHistoryResult(
                    transactions = transactions.deduplicate(),
                    providerName = api.name,
                    latestBlockNumber = latestKnownBlock,
                    completeness = EvmSyncCompleteness.Complete,
                    cursorFromBlock = null,
                    cursorToBlock = latestKnownBlock,
                    error = null,
                )
            } catch (error: Throwable) {
                failures += "${api.name}: ${error.message}"
            }
        }

        return EvmNetworkHistoryResult(
            transactions = emptyList(),
            providerName = null,
            latestBlockNumber = latestKnownBlock,
            completeness = EvmSyncCompleteness.Partial,
            cursorFromBlock = null,
            cursorToBlock = latestKnownBlock,
            error = failures.joinToString(" | ").ifBlank { "No public indexer configured." },
        )
    }

    private suspend fun fetchBlockscoutV2History(
        walletId: String,
        address: String,
        config: EvmNetworkConfig,
        api: EvmExplorerApi,
        nativeAsset: SupportedAsset?,
        supportedAssetsByContract: Map<String, SupportedAsset>,
        latestKnownBlock: Long?,
    ): List<EvmNormalizedTransaction> {
        val nativeTransactions = if (nativeAsset == null) {
            emptyList()
        } else {
            val response = httpGetTransport.get(
                url = "${api.baseUrl.trimEnd('/')}/api/v2/addresses/${address.urlEncoded()}/transactions",
                timeoutMillis = timeoutMillis,
            )
            val items = JSONObject(response).optJSONArray("items") ?: JSONArray()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    add(
                        item.toBlockscoutNativeTransaction(
                            walletId = walletId,
                            networkId = config.networkId,
                            asset = nativeAsset,
                            walletAddress = address,
                            providerName = api.name,
                            latestKnownBlock = latestKnownBlock,
                        ),
                    )
                }
            }
        }

        val tokenResponse = httpGetTransport.get(
            url = "${api.baseUrl.trimEnd('/')}/api/v2/addresses/${address.urlEncoded()}/token-transfers?type=ERC-20",
            timeoutMillis = timeoutMillis,
        )
        val tokenItems = JSONObject(tokenResponse).optJSONArray("items") ?: JSONArray()
        val tokenTransactions = buildList {
            for (index in 0 until tokenItems.length()) {
                val item = tokenItems.optJSONObject(index) ?: continue
                val token = item.optJSONObject("token") ?: continue
                val contract = token.optString("address").takeIf(String::isNotBlank) ?: continue
                val asset = supportedAssetsByContract[EvmAbi.normalizeAddress(contract)] ?: continue
                add(
                    item.toBlockscoutTokenTransfer(
                        walletId = walletId,
                        networkId = config.networkId,
                        asset = asset,
                        walletAddress = address,
                        providerName = api.name,
                        latestKnownBlock = latestKnownBlock,
                    ),
                )
            }
        }
        return nativeTransactions + tokenTransactions
    }

    private suspend fun fetchEtherscanCompatibleHistory(
        walletId: String,
        address: String,
        config: EvmNetworkConfig,
        api: EvmExplorerApi,
        nativeAsset: SupportedAsset?,
        latestKnownBlock: Long?,
    ): List<EvmNormalizedTransaction> {
        if (nativeAsset == null) return emptyList()
        val url = "${api.baseUrl.trimEnd('/')}/api?module=account&action=txlist&address=${address.urlEncoded()}&sort=asc"
        val response = JSONObject(httpGetTransport.get(url, timeoutMillis))
        val result = response.optJSONArray("result") ?: JSONArray()
        return buildList {
            for (index in 0 until result.length()) {
                val item = result.optJSONObject(index) ?: continue
                add(
                    item.toEtherscanNativeTransaction(
                        walletId = walletId,
                        networkId = config.networkId,
                        asset = nativeAsset,
                        walletAddress = address,
                        providerName = api.name,
                        latestKnownBlock = latestKnownBlock,
                    ),
                )
            }
        }
    }

    private suspend fun scanTokenTransferLogs(
        walletId: String,
        address: String,
        config: EvmNetworkConfig,
        assetsByContract: Map<String, SupportedAsset>,
        latestKnownBlock: Long?,
        nowMillis: Long,
    ): EvmNetworkHistoryResult {
        if (assetsByContract.isEmpty()) {
            return EvmNetworkHistoryResult(
                transactions = emptyList(),
                providerName = null,
                latestBlockNumber = latestKnownBlock,
                completeness = EvmSyncCompleteness.Complete,
                cursorFromBlock = null,
                cursorToBlock = latestKnownBlock,
                error = null,
            )
        }

        return try {
            val client = rpcClientFactory(config)
            val latestBlock = latestKnownBlock ?: client.blockNumber().value
            val fromBlock = maxOf(0L, latestBlock - maxLogScanBlockRange + 1L)
            val transactions = mutableListOf<EvmNormalizedTransaction>()
            assetsByContract.forEach { (contract, asset) ->
                val incoming = client.getLogs(
                    transferFilter(
                        contract = contract,
                        indexedAddressTopicPosition = 2,
                        walletAddress = address,
                        fromBlock = fromBlock,
                        toBlock = latestBlock,
                    ),
                )
                val outgoing = client.getLogs(
                    transferFilter(
                        contract = contract,
                        indexedAddressTopicPosition = 1,
                        walletAddress = address,
                        fromBlock = fromBlock,
                        toBlock = latestBlock,
                    ),
                )
                transactions += incoming.value.toTransferTransactions(
                    walletId = walletId,
                    networkId = config.networkId,
                    asset = asset,
                    walletAddress = address,
                    direction = WalletTransactionDirection.Incoming,
                    providerName = incoming.provider.name,
                    latestKnownBlock = latestBlock,
                    nowMillis = nowMillis,
                )
                transactions += outgoing.value.toTransferTransactions(
                    walletId = walletId,
                    networkId = config.networkId,
                    asset = asset,
                    walletAddress = address,
                    direction = WalletTransactionDirection.Outgoing,
                    providerName = outgoing.provider.name,
                    latestKnownBlock = latestBlock,
                    nowMillis = nowMillis,
                )
            }
            EvmNetworkHistoryResult(
                transactions = transactions.deduplicate(),
                providerName = null,
                latestBlockNumber = latestBlock,
                completeness = EvmSyncCompleteness.Partial,
                cursorFromBlock = fromBlock,
                cursorToBlock = latestBlock,
                error = "RPC log scan covered latest $maxLogScanBlockRange blocks only; history is partial until earlier ranges are scanned.",
            )
        } catch (error: Throwable) {
            EvmNetworkHistoryResult(
                transactions = emptyList(),
                providerName = null,
                latestBlockNumber = latestKnownBlock,
                completeness = EvmSyncCompleteness.Partial,
                cursorFromBlock = null,
                cursorToBlock = latestKnownBlock,
                error = "RPC log fallback failed: ${error.message}",
            )
        }
    }

    private fun transferFilter(
        contract: String,
        indexedAddressTopicPosition: Int,
        walletAddress: String,
        fromBlock: Long,
        toBlock: Long,
    ): JSONObject {
        val topics = JSONArray()
            .put(EvmAbi.ERC20_TRANSFER_TOPIC)
            .put(JSONObject.NULL)
            .put(JSONObject.NULL)
        topics.put(indexedAddressTopicPosition, EvmAbi.addressToTopic(walletAddress))
        return JSONObject()
            .put("address", "0x$contract")
            .put("fromBlock", "0x${fromBlock.toString(16)}")
            .put("toBlock", "0x${toBlock.toString(16)}")
            .put("topics", topics)
    }
}

data class EvmNetworkHistoryResult(
    val transactions: List<EvmNormalizedTransaction>,
    val providerName: String?,
    val latestBlockNumber: Long?,
    val completeness: EvmSyncCompleteness,
    val cursorFromBlock: Long?,
    val cursorToBlock: Long?,
    val error: String?,
)

private fun JSONObject.toBlockscoutNativeTransaction(
    walletId: String,
    networkId: String,
    asset: SupportedAsset,
    walletAddress: String,
    providerName: String,
    latestKnownBlock: Long?,
): EvmNormalizedTransaction {
    val hash = optString("hash")
    val from = optString("from").addressHash()
    val to = optString("to").addressHash()
    val valueRaw = optString("value").ifBlank { "0" }
    val gasUsed = optString("gas_used").toBigIntegerOrNullSafe()
    val gasPrice = optString("gas_price").toBigIntegerOrNullSafe()
    val feeRaw = if (gasUsed != null && gasPrice != null) (gasUsed * gasPrice).toString() else null
    val blockHeight = optLongOrNull("block_number")
    return EvmNormalizedTransaction(
        walletId = walletId,
        assetId = asset.assetId,
        networkId = networkId,
        transactionHash = hash,
        direction = directionFor(walletAddress, from, to),
        status = if (optString("status") == "error") {
            WalletTransactionStatus.Failed
        } else {
            WalletTransactionStatus.Success
        },
        amountRaw = valueRaw,
        amountDecimal = EvmAbi.rawToDecimalString(valueRaw, asset.decimals),
        feeRaw = feeRaw,
        feeDecimal = feeRaw?.let { EvmAbi.rawToDecimalString(it, asset.decimals) },
        feeAssetId = asset.assetId,
        fromAddress = from,
        toAddress = to,
        blockHeight = blockHeight,
        blockHash = optString("block_hash").takeIf(String::isNotBlank),
        confirmations = confirmations(latestKnownBlock, blockHeight),
        nonce = optString("nonce").takeIf(String::isNotBlank),
        timestampMillis = optString("timestamp").parseTimestampMillis(),
        providerName = providerName,
        metadataJson = JSONObject()
            .put("syncProvider", providerName)
            .put("syncSource", "blockscout-v2-native")
            .toString(),
    )
}

private fun JSONObject.toBlockscoutTokenTransfer(
    walletId: String,
    networkId: String,
    asset: SupportedAsset,
    walletAddress: String,
    providerName: String,
    latestKnownBlock: Long?,
): EvmNormalizedTransaction {
    val transaction = optJSONObject("transaction")
    val hash = optString("transaction_hash")
        .ifBlank { transaction?.optString("hash").orEmpty() }
    val from = optString("from").addressHash()
    val to = optString("to").addressHash()
    val total = optJSONObject("total")
    val valueRaw = total?.optString("value")?.takeIf(String::isNotBlank)
        ?: optString("value").ifBlank { "0" }
    val blockHeight = optLongOrNull("block_number") ?: transaction?.optLongOrNull("block_number")
    return EvmNormalizedTransaction(
        walletId = walletId,
        assetId = asset.assetId,
        networkId = networkId,
        transactionHash = hash,
        direction = directionFor(walletAddress, from, to),
        status = WalletTransactionStatus.Success,
        amountRaw = valueRaw,
        amountDecimal = EvmAbi.rawToDecimalString(valueRaw, asset.decimals),
        feeRaw = null,
        feeDecimal = null,
        feeAssetId = null,
        fromAddress = from,
        toAddress = to,
        blockHeight = blockHeight,
        blockHash = optString("block_hash").takeIf(String::isNotBlank),
        confirmations = confirmations(latestKnownBlock, blockHeight),
        nonce = null,
        timestampMillis = optString("timestamp").parseTimestampMillis(),
        providerName = providerName,
        metadataJson = JSONObject()
            .put("syncProvider", providerName)
            .put("syncSource", "blockscout-v2-token-transfer")
            .toString(),
    )
}

private fun JSONObject.toEtherscanNativeTransaction(
    walletId: String,
    networkId: String,
    asset: SupportedAsset,
    walletAddress: String,
    providerName: String,
    latestKnownBlock: Long?,
): EvmNormalizedTransaction {
    val from = optString("from")
    val to = optString("to")
    val valueRaw = optString("value").ifBlank { "0" }
    val gasUsed = optString("gasUsed").toBigIntegerOrNullSafe()
    val gasPrice = optString("gasPrice").toBigIntegerOrNullSafe()
    val feeRaw = if (gasUsed != null && gasPrice != null) (gasUsed * gasPrice).toString() else null
    val blockHeight = optString("blockNumber").toLongOrNull()
    return EvmNormalizedTransaction(
        walletId = walletId,
        assetId = asset.assetId,
        networkId = networkId,
        transactionHash = optString("hash"),
        direction = directionFor(walletAddress, from, to),
        status = if (optString("isError") == "1") {
            WalletTransactionStatus.Failed
        } else {
            WalletTransactionStatus.Success
        },
        amountRaw = valueRaw,
        amountDecimal = EvmAbi.rawToDecimalString(valueRaw, asset.decimals),
        feeRaw = feeRaw,
        feeDecimal = feeRaw?.let { EvmAbi.rawToDecimalString(it, asset.decimals) },
        feeAssetId = asset.assetId,
        fromAddress = from.takeIf(String::isNotBlank),
        toAddress = to.takeIf(String::isNotBlank),
        blockHeight = blockHeight,
        blockHash = null,
        confirmations = confirmations(latestKnownBlock, blockHeight),
        nonce = optString("nonce").takeIf(String::isNotBlank),
        timestampMillis = optString("timeStamp").toLongOrNull()?.times(1000L) ?: 0L,
        providerName = providerName,
        metadataJson = JSONObject()
            .put("syncProvider", providerName)
            .put("syncSource", "etherscan-compatible-native")
            .toString(),
    )
}

private fun JSONArray.toTransferTransactions(
    walletId: String,
    networkId: String,
    asset: SupportedAsset,
    walletAddress: String,
    direction: WalletTransactionDirection,
    providerName: String,
    latestKnownBlock: Long,
    nowMillis: Long,
): List<EvmNormalizedTransaction> = buildList {
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        val topics = item.optJSONArray("topics") ?: continue
        val from = topics.optString(1).topicToAddress()
        val to = topics.optString(2).topicToAddress()
        val blockHeight = EvmAbi.hexToLong(item.optString("blockNumber", "0x0"))
        val amountRaw = EvmAbi.decodeUint256(item.optString("data", "0x0")).toString()
        add(
            EvmNormalizedTransaction(
                walletId = walletId,
                assetId = asset.assetId,
                networkId = networkId,
                transactionHash = item.optString("transactionHash"),
                direction = if (directionFor(walletAddress, from, to) == WalletTransactionDirection.Self) {
                    WalletTransactionDirection.Self
                } else {
                    direction
                },
                status = WalletTransactionStatus.Success,
                amountRaw = amountRaw,
                amountDecimal = EvmAbi.rawToDecimalString(amountRaw, asset.decimals),
                feeRaw = null,
                feeDecimal = null,
                feeAssetId = null,
                fromAddress = from,
                toAddress = to,
                blockHeight = blockHeight,
                blockHash = item.optString("blockHash").takeIf(String::isNotBlank),
                confirmations = confirmations(latestKnownBlock, blockHeight),
                nonce = null,
                timestampMillis = nowMillis,
                providerName = providerName,
                metadataJson = JSONObject()
                    .put("syncProvider", providerName)
                    .put("syncSource", "rpc-erc20-transfer-log")
                    .put("logIndex", item.optString("logIndex"))
                    .toString(),
            ),
        )
    }
}

private fun List<EvmNormalizedTransaction>.deduplicate(): List<EvmNormalizedTransaction> =
    distinctBy { "${it.networkId}:${it.transactionHash}:${it.assetId}:${it.direction.value}:${it.amountRaw}" }
        .filter { it.transactionHash.isNotBlank() }

private fun directionFor(
    walletAddress: String,
    from: String?,
    to: String?,
): WalletTransactionDirection {
    val normalizedWallet = walletAddress.removePrefix("0x").removePrefix("0X").lowercase()
    val normalizedFrom = from?.removePrefix("0x")?.removePrefix("0X")?.lowercase()
    val normalizedTo = to?.removePrefix("0x")?.removePrefix("0X")?.lowercase()
    return when {
        normalizedFrom == normalizedWallet && normalizedTo == normalizedWallet -> WalletTransactionDirection.Self
        normalizedFrom == normalizedWallet -> WalletTransactionDirection.Outgoing
        normalizedTo == normalizedWallet -> WalletTransactionDirection.Incoming
        else -> WalletTransactionDirection.Unknown
    }
}

private fun confirmations(
    latestKnownBlock: Long?,
    blockHeight: Long?,
): Int =
    if (latestKnownBlock == null || blockHeight == null || latestKnownBlock < blockHeight) {
        0
    } else {
        (latestKnownBlock - blockHeight + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

private fun String.topicToAddress(): String? {
    val normalized = removePrefix("0x").removePrefix("0X")
    return if (normalized.length == 64) {
        "0x${normalized.takeLast(40)}"
    } else {
        null
    }
}

private fun String.urlEncoded(): String =
    URLEncoder.encode(this, "UTF-8")

private fun String.toBigIntegerOrNullSafe(): BigInteger? =
    takeIf(String::isNotBlank)?.let {
        runCatching {
            if (it.startsWith("0x", ignoreCase = true)) {
                BigInteger(it.removePrefix("0x").removePrefix("0X"), 16)
            } else {
                BigInteger(it)
            }
        }.getOrNull()
    }

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) {
        when (val value = get(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    } else {
        null
    }

private fun String.parseTimestampMillis(): Long =
    when {
        isBlank() -> 0L
        contains("T") -> java.time.Instant.parse(this).toEpochMilli()
        else -> toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1000L else it } ?: 0L
    }

private fun String.addressHash(): String? =
    takeIf(String::isNotBlank)?.let { value ->
        runCatching {
            val json = JSONObject(value)
            json.optString("hash").takeIf(String::isNotBlank)
        }.getOrElse {
            value
        }
    }
