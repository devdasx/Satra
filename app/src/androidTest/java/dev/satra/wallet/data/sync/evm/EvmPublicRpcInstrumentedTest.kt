package dev.satra.wallet.data.sync.evm

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import kotlinx.coroutines.runBlocking
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

    private companion object {
        const val FIXTURE_ADDRESS = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045"
    }
}
