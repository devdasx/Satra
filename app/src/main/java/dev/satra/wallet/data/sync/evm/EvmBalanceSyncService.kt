package dev.satra.wallet.data.sync.evm

import dev.satra.wallet.data.assets.SupportedAsset

class EvmBalanceSyncService(
    private val clientFactory: (EvmNetworkConfig) -> EvmJsonRpcClient = { config ->
        EvmJsonRpcClient(config)
    },
) {
    suspend fun syncBalances(
        address: String,
        assets: List<SupportedAsset>,
        nowMillis: Long = System.currentTimeMillis(),
    ): EvmNetworkBalanceResult {
        require(assets.isNotEmpty()) { "No assets supplied for EVM balance sync." }
        val networkId = assets.first().networkId
        require(assets.all { it.networkId == networkId }) {
            "All assets must belong to the same network."
        }

        val config = EvmProviderRegistry.requireConfig(networkId)
        val client = clientFactory(config)
        val balances = mutableListOf<EvmAssetBalance>()
        var providerName: String? = null
        var latestBlockNumber: Long? = null

        assets.forEach { asset ->
            val result = if (asset.contractAddress == null || asset.assetType == "NATIVE") {
                client.nativeBalance(address)
            } else {
                client.erc20Balance(
                    contractAddress = asset.contractAddress,
                    ownerAddress = address,
                )
            }
            providerName = result.provider.name
            latestBlockNumber = maxOf(latestBlockNumber ?: 0L, result.blockNumber ?: 0L)
            balances += EvmAssetBalance(
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

        return EvmNetworkBalanceResult(
            balances = balances,
            providerName = providerName,
            latestBlockNumber = latestBlockNumber,
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
