package dev.satra.wallet.data.sync.evm

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger

@RunWith(AndroidJUnit4::class)
class EvmPublicRpcInstrumentedTest {
    @Test
    fun allSupportedEvmNetworksReturnChainBlockNativeAndTokenBalances() = runBlocking {
        EvmProviderRegistry.networks.values.forEach { config ->
            val client = EvmJsonRpcClient(config)
            val chainId = client.chainId()
            val blockNumber = client.blockNumber()
            val nativeBalance = client.nativeBalance(FIXTURE_ADDRESS)
            val tokenAsset = SupportedAssetCatalog.assets.first {
                it.networkId == config.networkId && it.contractAddress != null
            }
            val tokenBalance = client.erc20Balance(
                contractAddress = checkNotNull(tokenAsset.contractAddress),
                ownerAddress = FIXTURE_ADDRESS,
            )

            assertEquals("Wrong chain for ${config.networkId}", config.chainId, chainId.value)
            assertTrue("Empty block for ${config.networkId}", blockNumber.value > 0L)
            assertTrue(
                "Invalid native balance for ${config.networkId}",
                nativeBalance.value >= BigInteger.ZERO,
            )
            assertTrue(
                "Invalid ${tokenAsset.symbol} balance for ${config.networkId}",
                tokenBalance.value >= BigInteger.ZERO,
            )
        }
    }

    @Test
    fun ethereumIndexedHistoryReturnsRealTransactionsWhenPublicIndexerIsAvailable() = runBlocking {
        val ethereum = EvmProviderRegistry.requireConfig("ethereum")
        val latestBlock = EvmJsonRpcClient(ethereum).blockNumber().value
        val ethereumAssets = SupportedAssetCatalog.assets.filter {
            it.assetId == "ethereum:eth" || it.assetId == "ethereum:usdc"
        }

        val result = EvmHistorySyncService(
            maxIndexedPages = 1,
            maxLogScanBlockRange = 128,
        ).syncHistory(
            walletId = "fixture",
            address = FIXTURE_ADDRESS,
            assets = ethereumAssets,
            latestKnownBlock = latestBlock,
        )

        assertTrue("Expected at least one indexed Ethereum transaction", result.transactions.isNotEmpty())
        assertTrue(result.transactions.all { it.networkId == "ethereum" })
    }

    @Test
    fun allSupportedEvmNetworksReturnRealHistorySyncState() = runBlocking {
        val historySyncService = EvmHistorySyncService(
            maxIndexedPages = 1,
            maxLogScanBlockRange = 128,
        )

        EvmProviderRegistry.networks.values.forEach { config ->
            val latestBlock = EvmJsonRpcClient(config).blockNumber().value
            val assets = SupportedAssetCatalog.assets.filter { asset ->
                asset.networkId == config.networkId &&
                    (asset.contractAddress == null || asset.assetType == "NATIVE")
            }

            val result = historySyncService.syncHistory(
                walletId = "fixture-${config.networkId}",
                address = FIXTURE_ADDRESS,
                assets = assets,
                latestKnownBlock = latestBlock,
            )

            assertTrue(
                "History sync should not fail for ${config.networkId}",
                result.completeness == EvmSyncCompleteness.Complete ||
                    result.completeness == EvmSyncCompleteness.Partial,
            )
            assertTrue(
                "Expected latest block cursor for ${config.networkId}",
                result.cursorToBlock == latestBlock || result.latestBlockNumber == latestBlock,
            )
        }
    }

    @Test
    fun allSupportedEvmNetworksReturnRealSupportedTokenHistory() = runBlocking {
        val historySyncService = EvmHistorySyncService(
            maxIndexedPages = 2,
            maxLogScanBlockRange = 1_024,
            maxLogScanChunkRange = 64,
        )

        val results = coroutineScope {
            EvmProviderRegistry.networks.values.map { config ->
                async {
                    val latestBlock = EvmJsonRpcClient(config).blockNumber().value
                    val fixture = discoverRecentSupportedTokenTransfer(config, latestBlock)
                    val assets = SupportedAssetCatalog.assets.filter { asset ->
                        asset.networkId == config.networkId &&
                            (asset.assetType == "NATIVE" || asset.assetId == fixture.assetId)
                    }

                    val result = historySyncService.syncHistory(
                        walletId = "token-fixture-${config.networkId}",
                        address = fixture.walletAddress,
                        assets = assets,
                        latestKnownBlock = latestBlock,
                    )

                    TokenHistoryNetworkResult(
                        networkId = config.networkId,
                        assetId = fixture.assetId,
                        transactionHash = fixture.transactionHash,
                        transactionCount = result.transactions.count { it.assetId == fixture.assetId },
                        completeness = result.completeness,
                        error = result.error,
                    )
                }
            }.awaitAll()
        }

        results.forEach { result ->
            assertTrue(
                "Expected real supported token history for ${result.networkId} ${result.assetId}; " +
                    "count=${result.transactionCount}, completeness=${result.completeness}, error=${result.error}",
                result.transactionCount > 0,
            )
        }
    }

    private suspend fun discoverRecentSupportedTokenTransfer(
        config: EvmNetworkConfig,
        latestBlock: Long,
    ): TokenTransferFixture {
        val client = EvmJsonRpcClient(config)
        val tokenAssets = SupportedAssetCatalog.assets.filter { asset ->
            asset.networkId == config.networkId && asset.contractAddress != null
        }

        for (asset in tokenAssets) {
            for (range in RECENT_LOG_RANGES) {
                val fromBlock = maxOf(0L, latestBlock - range + 1L)
                val logs = runCatching {
                    client.getLogs(
                        JSONObject()
                            .put("address", checkNotNull(asset.contractAddress))
                            .put("fromBlock", "0x${fromBlock.toString(16)}")
                            .put("toBlock", "0x${latestBlock.toString(16)}")
                            .put("topics", JSONArray().put(EvmAbi.ERC20_TRANSFER_TOPIC)),
                    ).value
                }.getOrNull() ?: continue

                for (index in logs.length() - 1 downTo 0) {
                    val item = logs.optJSONObject(index) ?: continue
                    val topics = item.optJSONArray("topics") ?: continue
                    val toAddress = topics.optString(2).topicToAddressOrNull() ?: continue
                    val transactionHash = item.optString("transactionHash").takeIf(String::isNotBlank) ?: continue
                    return TokenTransferFixture(
                        assetId = asset.assetId,
                        walletAddress = toAddress,
                        transactionHash = transactionHash,
                    )
                }
            }
        }

        error("No recent supported ERC-20 transfer found for ${config.networkId}.")
    }

    private companion object {
        const val FIXTURE_ADDRESS = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045"
        val RECENT_LOG_RANGES = listOf(8L, 32L, 128L, 512L)
    }
}

private data class TokenTransferFixture(
    val assetId: String,
    val walletAddress: String,
    val transactionHash: String,
)

private data class TokenHistoryNetworkResult(
    val networkId: String,
    val assetId: String,
    val transactionHash: String,
    val transactionCount: Int,
    val completeness: EvmSyncCompleteness,
    val error: String?,
)

private fun String.topicToAddressOrNull(): String? {
    val normalized = removePrefix("0x").removePrefix("0X")
    return if (normalized.length == 64) {
        "0x${normalized.takeLast(40)}"
    } else {
        null
    }
}
