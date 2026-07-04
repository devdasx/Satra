package dev.satra.wallet.data.sync.evm

import dev.satra.wallet.data.assets.SupportedAsset
import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private val maxLogScanChunkRange: Long = 512,
    private val maxIndexedPages: Int = 1_000,
    private val maxParallelIndexedTokenRequests: Int = 6,
    private val maxParallelLogScans: Int = 6,
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
                val indexedFetch = when (api.style) {
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
                        supportedAssetsByContract = supportedAssetsByContract,
                        latestKnownBlock = latestKnownBlock,
                    )
                }
                return EvmNetworkHistoryResult(
                    transactions = indexedFetch.transactions.deduplicate(),
                    providerName = api.name,
                    latestBlockNumber = latestKnownBlock,
                    completeness = indexedFetch.completeness,
                    cursorFromBlock = null,
                    cursorToBlock = latestKnownBlock,
                    error = indexedFetch.error,
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
    ): EvmIndexedHistoryFetch = coroutineScope {
        val nativeDeferred = async {
            if (nativeAsset == null) {
                EvmBlockscoutPageResult(emptyList(), complete = true)
            } else {
                fetchBlockscoutPagedItems(
                    initialUrl = "${api.baseUrl.trimEnd('/')}/api/v2/addresses/${address.urlEncoded()}/transactions",
                ).mapItems { item ->
                    item.toBlockscoutNativeTransaction(
                        walletId = walletId,
                        networkId = config.networkId,
                        asset = nativeAsset,
                        walletAddress = address,
                        providerName = api.name,
                        latestKnownBlock = latestKnownBlock,
                    )
                }
            }
        }

        val tokenDeferred = async {
            fetchBlockscoutV2TokenHistory(
                walletId = walletId,
                address = address,
                config = config,
                api = api,
                supportedAssetsByContract = supportedAssetsByContract,
                latestKnownBlock = latestKnownBlock,
            )
        }

        val (nativeTransactions, tokenTransactions) = awaitAll(nativeDeferred, tokenDeferred)
        EvmIndexedHistoryFetch(
            transactions = nativeTransactions.transactions + tokenTransactions.transactions,
            completeness = if (nativeTransactions.complete && tokenTransactions.complete) {
                EvmSyncCompleteness.Complete
            } else {
                EvmSyncCompleteness.Partial
            },
            error = listOfNotNull(nativeTransactions.error, tokenTransactions.error)
                .joinToString(" | ")
                .ifBlank { null },
        )
    }

    private suspend fun fetchBlockscoutV2TokenHistory(
        walletId: String,
        address: String,
        config: EvmNetworkConfig,
        api: EvmExplorerApi,
        supportedAssetsByContract: Map<String, SupportedAsset>,
        latestKnownBlock: Long?,
    ): EvmBlockscoutPageResult<EvmNormalizedTransaction> = coroutineScope {
        if (supportedAssetsByContract.isEmpty()) {
            return@coroutineScope EvmBlockscoutPageResult(emptyList(), complete = true)
        }

        val tokenLimiter = Semaphore(maxParallelIndexedTokenRequests.coerceAtLeast(1))
        val tokenResults = supportedAssetsByContract
            .map { (contract, asset) ->
                async {
                    tokenLimiter.withPermit {
                        runCatching {
                            fetchBlockscoutPagedItems(
                                initialUrl = "${api.baseUrl.trimEnd('/')}/api/v2/addresses/${address.urlEncoded()}/token-transfers?type=ERC-20&token=${"0x$contract".urlEncoded()}",
                            ).mapItems { item ->
                                if (!item.matchesBlockscoutTokenContract(contract)) {
                                    return@mapItems null
                                }
                                item.toBlockscoutTokenTransfer(
                                    walletId = walletId,
                                    networkId = config.networkId,
                                    asset = asset,
                                    walletAddress = address,
                                    providerName = api.name,
                                    latestKnownBlock = latestKnownBlock,
                                )
                            }
                        }.getOrElse { error ->
                            EvmBlockscoutPageResult(
                                transactions = emptyList(),
                                complete = false,
                                error = "${asset.assetId}: ${error.message}",
                            )
                        }
                    }
                }
            }
            .awaitAll()

        EvmBlockscoutPageResult(
            transactions = tokenResults.flatMap { it.transactions },
            complete = tokenResults.all { it.complete },
            error = tokenResults.mapNotNull { it.error }
                .joinToString(" | ")
                .ifBlank { null },
        )
    }

    private suspend fun fetchEtherscanCompatibleHistory(
        walletId: String,
        address: String,
        config: EvmNetworkConfig,
        api: EvmExplorerApi,
        nativeAsset: SupportedAsset?,
        supportedAssetsByContract: Map<String, SupportedAsset>,
        latestKnownBlock: Long?,
    ): EvmIndexedHistoryFetch = coroutineScope {
        val nativeDeferred = async {
            if (nativeAsset == null) {
                EvmBlockscoutPageResult(emptyList(), complete = true)
            } else {
                fetchEtherscanCompatiblePagedItems { page ->
                    "${api.baseUrl.trimEnd('/')}/api?module=account&action=txlist&address=${address.urlEncoded()}&sort=asc&page=$page&offset=$ETHERSCAN_COMPATIBLE_PAGE_SIZE"
                }.mapItems { item ->
                    item.toEtherscanNativeTransaction(
                        walletId = walletId,
                        networkId = config.networkId,
                        asset = nativeAsset,
                        walletAddress = address,
                        providerName = api.name,
                        latestKnownBlock = latestKnownBlock,
                    )
                }
            }
        }

        val tokenDeferred = async {
            fetchEtherscanCompatibleTokenHistory(
                walletId = walletId,
                address = address,
                config = config,
                api = api,
                supportedAssetsByContract = supportedAssetsByContract,
                latestKnownBlock = latestKnownBlock,
            )
        }

        val (nativeTransactions, tokenTransactions) = awaitAll(nativeDeferred, tokenDeferred)
        EvmIndexedHistoryFetch(
            transactions = nativeTransactions.transactions + tokenTransactions.transactions,
            completeness = if (nativeTransactions.complete && tokenTransactions.complete) {
                EvmSyncCompleteness.Complete
            } else {
                EvmSyncCompleteness.Partial
            },
            error = listOfNotNull(nativeTransactions.error, tokenTransactions.error)
                .joinToString(" | ")
                .ifBlank { null },
        )
    }

    private suspend fun fetchEtherscanCompatibleTokenHistory(
        walletId: String,
        address: String,
        config: EvmNetworkConfig,
        api: EvmExplorerApi,
        supportedAssetsByContract: Map<String, SupportedAsset>,
        latestKnownBlock: Long?,
    ): EvmBlockscoutPageResult<EvmNormalizedTransaction> = coroutineScope {
        if (supportedAssetsByContract.isEmpty()) {
            return@coroutineScope EvmBlockscoutPageResult(emptyList(), complete = true)
        }

        val tokenLimiter = Semaphore(maxParallelIndexedTokenRequests.coerceAtLeast(1))
        val tokenResults = supportedAssetsByContract
            .map { (contract, asset) ->
                async {
                    tokenLimiter.withPermit {
                        runCatching {
                            fetchEtherscanCompatiblePagedItems { page ->
                                "${api.baseUrl.trimEnd('/')}/api?module=account&action=tokentx&address=${address.urlEncoded()}&contractaddress=${"0x$contract".urlEncoded()}&sort=asc&page=$page&offset=$ETHERSCAN_COMPATIBLE_PAGE_SIZE"
                            }.mapItems { item ->
                                val itemContract = item.optString("contractAddress").takeIf(String::isNotBlank)
                                if (itemContract != null && EvmAbi.normalizeAddress(itemContract) != contract) {
                                    return@mapItems null
                                }
                                item.toEtherscanTokenTransfer(
                                    walletId = walletId,
                                    networkId = config.networkId,
                                    asset = asset,
                                    walletAddress = address,
                                    providerName = api.name,
                                    latestKnownBlock = latestKnownBlock,
                                )
                            }
                        }.getOrElse { error ->
                            EvmBlockscoutPageResult(
                                transactions = emptyList(),
                                complete = false,
                                error = "${asset.assetId}: ${error.message}",
                            )
                        }
                    }
                }
            }
            .awaitAll()

        EvmBlockscoutPageResult(
            transactions = tokenResults.flatMap { it.transactions },
            complete = tokenResults.all { it.complete },
            error = tokenResults.mapNotNull { it.error }
                .joinToString(" | ")
                .ifBlank { null },
        )
    }

    private suspend fun fetchBlockscoutPagedItems(initialUrl: String): EvmBlockscoutPageResult<JSONObject> {
        val items = mutableListOf<JSONObject>()
        var nextUrl: String? = initialUrl
        var pageCount = 0
        while (nextUrl != null && pageCount < maxIndexedPages) {
            val response = JSONObject(
                httpGetTransport.get(
                    url = checkNotNull(nextUrl),
                    timeoutMillis = timeoutMillis,
                ),
            )
            val pageItems = response.optJSONArray("items") ?: JSONArray()
            for (index in 0 until pageItems.length()) {
                pageItems.optJSONObject(index)?.let(items::add)
            }
            nextUrl = response.nextBlockscoutPageUrl(initialUrl)
            pageCount += 1
        }
        val complete = nextUrl == null
        return EvmBlockscoutPageResult(
            transactions = items,
            complete = complete,
            error = if (complete) {
                null
            } else {
                "Blockscout pagination reached the local page limit of $maxIndexedPages pages."
            },
        )
    }

    private suspend fun fetchEtherscanCompatiblePagedItems(
        pageUrl: (Int) -> String,
    ): EvmBlockscoutPageResult<JSONObject> {
        val items = mutableListOf<JSONObject>()
        var page = 1
        while (page <= maxIndexedPages) {
            val response = JSONObject(
                httpGetTransport.get(
                    url = pageUrl(page),
                    timeoutMillis = timeoutMillis,
                ),
            )
            val pageItems = response.etherscanCompatibleResultArray()
            for (index in 0 until pageItems.length()) {
                pageItems.optJSONObject(index)?.let(items::add)
            }
            if (pageItems.length() < ETHERSCAN_COMPATIBLE_PAGE_SIZE) {
                return EvmBlockscoutPageResult(
                    transactions = items,
                    complete = true,
                )
            }
            page += 1
        }
        return EvmBlockscoutPageResult(
            transactions = items,
            complete = false,
            error = "Etherscan-compatible pagination reached the local page limit of $maxIndexedPages pages.",
        )
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
            val scanLimiter = Semaphore(maxParallelLogScans.coerceAtLeast(1))
            val logResults = coroutineScope {
                assetsByContract.flatMap { (contract, asset) ->
                    listOf(
                        EvmLogScanRequest(
                            contract = contract,
                            asset = asset,
                            direction = WalletTransactionDirection.Incoming,
                            indexedAddressTopicPosition = 2,
                        ),
                        EvmLogScanRequest(
                            contract = contract,
                            asset = asset,
                            direction = WalletTransactionDirection.Outgoing,
                            indexedAddressTopicPosition = 1,
                        ),
                    )
                }.map { request ->
                    async {
                        scanLimiter.withPermit {
                            val logs = client.getLogsInChunks(
                                request = request,
                                walletAddress = address,
                                fromBlock = fromBlock,
                                toBlock = latestBlock,
                            )
                            val blockMetadataByNumber = logs.value.fetchLogBlockMetadata(client)
                            EvmLogScanResult(
                                providerName = logs.provider.name,
                                transactions = logs.value.toTransferTransactions(
                                    walletId = walletId,
                                    networkId = config.networkId,
                                    asset = request.asset,
                                    walletAddress = address,
                                    direction = request.direction,
                                    providerName = logs.provider.name,
                                    latestKnownBlock = latestBlock,
                                    blockMetadataByNumber = blockMetadataByNumber,
                                ),
                            )
                        }
                    }
                }.awaitAll()
            }
            EvmNetworkHistoryResult(
                transactions = logResults.flatMap { it.transactions }.deduplicate(),
                providerName = logResults.firstOrNull()?.providerName,
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

    private suspend fun EvmJsonRpcClient.getLogsInChunks(
        request: EvmLogScanRequest,
        walletAddress: String,
        fromBlock: Long,
        toBlock: Long,
    ): EvmRpcCallResult<JSONArray> {
        val logs = JSONArray()
        var providerName: EvmProvider? = null
        var providerBlock: Long? = null
        var chunkToBlock = toBlock
        val initialChunkSize = maxLogScanChunkRange.coerceAtLeast(1L)
        var chunkSize = initialChunkSize

        while (chunkToBlock >= fromBlock) {
            val chunkFromBlock = maxOf(fromBlock, chunkToBlock - chunkSize + 1L)
            val chunk = try {
                getLogs(
                    transferFilter(
                        contract = request.contract,
                        indexedAddressTopicPosition = request.indexedAddressTopicPosition,
                        walletAddress = walletAddress,
                        fromBlock = chunkFromBlock,
                        toBlock = chunkToBlock,
                    ),
                )
            } catch (error: Throwable) {
                if (chunkSize > MIN_LOG_SCAN_CHUNK_RANGE) {
                    chunkSize = maxOf(MIN_LOG_SCAN_CHUNK_RANGE, chunkSize / 2L)
                    continue
                }
                if (logs.length() > 0) {
                    break
                }
                throw error
            }
            providerName = chunk.provider
            providerBlock = chunk.blockNumber
            for (index in 0 until chunk.value.length()) {
                logs.put(chunk.value.get(index))
            }
            chunkToBlock = chunkFromBlock - 1L
            chunkSize = initialChunkSize
        }

        return EvmRpcCallResult(
            value = logs,
            provider = providerName ?: EvmProvider("EVM RPC", ""),
            blockNumber = providerBlock,
        )
    }

    private suspend fun JSONArray.fetchLogBlockMetadata(
        client: EvmJsonRpcClient,
    ): Map<Long, EvmLogBlockMetadata> = coroutineScope {
        val blockNumbers = buildSet {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val blockHeight = EvmAbi.hexToLong(item.optString("blockNumber", "0x0"))
                    .takeIf { it > 0L }
                    ?: continue
                add(blockHeight)
            }
        }
        val limiter = Semaphore(maxParallelLogScans.coerceAtLeast(1))
        blockNumbers
            .map { blockNumber ->
                async {
                    limiter.withPermit {
                        val header = runCatching { client.blockByNumber(blockNumber).value }.getOrNull()
                        blockNumber to header?.let {
                            EvmLogBlockMetadata(
                                blockHash = it.blockHash,
                                timestampMillis = it.timestampMillis,
                            )
                        }
                    }
                }
            }
            .awaitAll()
            .mapNotNull { (blockNumber, metadata) -> metadata?.let { blockNumber to it } }
            .toMap()
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

    private companion object {
        const val ETHERSCAN_COMPATIBLE_PAGE_SIZE = 10_000
        const val MIN_LOG_SCAN_CHUNK_RANGE = 16L
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

private data class EvmIndexedHistoryFetch(
    val transactions: List<EvmNormalizedTransaction>,
    val completeness: EvmSyncCompleteness,
    val error: String? = null,
)

private data class EvmBlockscoutPageResult<T>(
    val transactions: List<T>,
    val complete: Boolean,
    val error: String? = null,
)

private data class EvmLogScanRequest(
    val contract: String,
    val asset: SupportedAsset,
    val direction: WalletTransactionDirection,
    val indexedAddressTopicPosition: Int,
)

private data class EvmLogScanResult(
    val providerName: String,
    val transactions: List<EvmNormalizedTransaction>,
)

private data class EvmLogBlockMetadata(
    val blockHash: String?,
    val timestampMillis: Long,
)

private fun <T, R> EvmBlockscoutPageResult<T>.mapItems(
    mapper: (T) -> R?,
): EvmBlockscoutPageResult<R> =
    EvmBlockscoutPageResult(
        transactions = transactions.mapNotNull(mapper),
        complete = complete,
        error = error,
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
    val from = addressHash("from")
    val to = addressHash("to")
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
    val from = addressHash("from")
    val to = addressHash("to")
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
        blockHash = optString("block_hash")
            .ifBlank { transaction?.optString("block_hash").orEmpty() }
            .takeIf(String::isNotBlank),
        confirmations = confirmations(latestKnownBlock, blockHeight),
        nonce = null,
        timestampMillis = optString("timestamp")
            .ifBlank { transaction?.optString("timestamp").orEmpty() }
            .parseTimestampMillis(),
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

private fun JSONObject.toEtherscanTokenTransfer(
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
    val blockHeight = optString("blockNumber").toLongOrNull()
    return EvmNormalizedTransaction(
        walletId = walletId,
        assetId = asset.assetId,
        networkId = networkId,
        transactionHash = optString("hash"),
        direction = directionFor(walletAddress, from, to),
        status = WalletTransactionStatus.Success,
        amountRaw = valueRaw,
        amountDecimal = EvmAbi.rawToDecimalString(valueRaw, asset.decimals),
        feeRaw = null,
        feeDecimal = null,
        feeAssetId = null,
        fromAddress = from.takeIf(String::isNotBlank),
        toAddress = to.takeIf(String::isNotBlank),
        blockHeight = blockHeight,
        blockHash = optString("blockHash").takeIf(String::isNotBlank),
        confirmations = confirmations(latestKnownBlock, blockHeight),
        nonce = optString("nonce").takeIf(String::isNotBlank),
        timestampMillis = optString("timeStamp").toLongOrNull()?.times(1000L) ?: 0L,
        providerName = providerName,
        metadataJson = JSONObject()
            .put("syncProvider", providerName)
            .put("syncSource", "etherscan-compatible-token-transfer")
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
    blockMetadataByNumber: Map<Long, EvmLogBlockMetadata>,
): List<EvmNormalizedTransaction> = buildList {
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        val topics = item.optJSONArray("topics") ?: continue
        val from = topics.optString(1).topicToAddress()
        val to = topics.optString(2).topicToAddress()
        val blockHeight = EvmAbi.hexToLong(item.optString("blockNumber", "0x0"))
        val blockMetadata = blockMetadataByNumber[blockHeight]
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
                blockHash = item.optString("blockHash").takeIf(String::isNotBlank)
                    ?: blockMetadata?.blockHash,
                confirmations = confirmations(latestKnownBlock, blockHeight),
                nonce = null,
                timestampMillis = blockMetadata?.timestampMillis ?: 0L,
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

private fun JSONObject.matchesBlockscoutTokenContract(contract: String): Boolean {
    val tokenContract = optJSONObject("token")?.contractHash()
    return tokenContract == null || EvmAbi.normalizeAddress(tokenContract) == contract
}

private fun JSONObject.contractHash(): String? =
    optString("address").takeIf(String::isNotBlank)
        ?: optString("address_hash").takeIf(String::isNotBlank)
        ?: optString("contract_address").takeIf(String::isNotBlank)

private fun JSONObject.addressHash(key: String): String? {
    val value = opt(key) ?: return null
    return when (value) {
        is JSONObject -> value.optString("hash").takeIf(String::isNotBlank)
            ?: value.optString("address_hash").takeIf(String::isNotBlank)
            ?: value.optString("address").takeIf(String::isNotBlank)

        is String -> value.addressHash()
        else -> value.toString().addressHash()
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

private fun JSONObject.nextBlockscoutPageUrl(initialUrl: String): String? {
    val nextPageParams = optJSONObject("next_page_params") ?: return null
    if (nextPageParams.length() == 0) return null

    val query = nextPageParams.keys().asSequence()
        .mapNotNull { key ->
            val value = nextPageParams.opt(key)
            if (value == null || value == JSONObject.NULL) {
                null
            } else {
                "${key.urlEncoded()}=${value.toString().urlEncoded()}"
            }
        }
        .joinToString("&")
        .takeIf(String::isNotBlank) ?: return null
    val separator = if (initialUrl.contains("?")) "&" else "?"
    return "$initialUrl$separator$query"
}

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

private fun JSONObject.etherscanCompatibleResultArray(): JSONArray {
    val result = opt("result")
    if (result is JSONArray) return result

    val message = optString("message")
    val resultText = result?.toString().orEmpty()
    val noTransactions = message.contains("No transactions", ignoreCase = true) ||
        resultText.contains("No transactions", ignoreCase = true)
    if (noTransactions) return JSONArray()

    error(
        listOf(
            optString("message").takeIf(String::isNotBlank),
            resultText.takeIf(String::isNotBlank),
        ).filterNotNull().joinToString(": ").ifBlank {
            "Malformed Etherscan-compatible response."
        },
    )
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
