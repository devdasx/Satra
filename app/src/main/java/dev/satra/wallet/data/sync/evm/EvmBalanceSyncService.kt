package dev.satra.wallet.data.sync.evm

import dev.satra.wallet.data.assets.SupportedAsset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class EvmBalanceSyncService(
    private val clientFactory: (EvmNetworkConfig) -> EvmJsonRpcClient = { config ->
        EvmJsonRpcClient(config)
    },
    private val maxParallelBalanceCalls: Int = 8,
) {
    suspend fun syncBalances(
        address: String,
        assets: List<SupportedAsset>,
        nowMillis: Long = System.currentTimeMillis(),
    ): EvmNetworkBalanceResult = coroutineScope {
        require(assets.isNotEmpty()) { "No assets supplied for EVM balance sync." }
        val networkId = assets.first().networkId
        require(assets.all { it.networkId == networkId }) {
            "All assets must belong to the same network."
        }

        val config = EvmProviderRegistry.requireConfig(networkId)
        val client = clientFactory(config)
        val callLimiter = Semaphore(maxParallelBalanceCalls.coerceAtLeast(1))

        val balances = assets
            .map { asset ->
                async {
                    callLimiter.withPermit {
                        val result = if (asset.contractAddress == null || asset.assetType == "NATIVE") {
                            client.nativeBalance(address)
                        } else {
                            client.erc20Balance(
                                contractAddress = asset.contractAddress,
                                ownerAddress = address,
                            )
                        }
                        EvmAssetBalance(
                            assetId = asset.assetId,
                            networkId = asset.networkId,
                            balanceRaw = result.value.toString(),
                            balanceDecimal = EvmAbi.rawToDecimalString(
                                raw = result.value,
                                decimals = asset.decimals,
                            ),
                            providerName = result.provider.name,
                            blockNumber = result.blockNumber ?: 0L,
                            syncedAtMillis = nowMillis,
                        )
                    }
                }
            }
            .awaitAll()

        EvmNetworkBalanceResult(
            balances = balances,
            providerName = balances.firstOrNull()?.providerName,
            latestBlockNumber = balances.maxOfOrNull { it.blockNumber },
            completeness = EvmSyncCompleteness.Complete,
            error = null,
        )
    }
}

data class EvmNetworkBalanceResult(
    val balances: List<EvmAssetBalance>,
    val providerName: String?,
    val latestBlockNumber: Long?,
    val completeness: EvmSyncCompleteness,
    val error: String?,
)
