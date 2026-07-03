package dev.satra.wallet.data.sync.evm

import dev.satra.wallet.data.assets.SupportedAssetCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvmProviderRegistryTest {
    @Test
    fun registryMatchesSupportedEvmNetworksAndAssets() {
        val evmNetworks = SupportedAssetCatalog.networks.filter { it.family == "evm" }
        val evmAssets = SupportedAssetCatalog.assets.filter { asset ->
            EvmProviderRegistry.supportedNetworkIds.contains(asset.networkId)
        }

        assertEquals(12, evmNetworks.size)
        assertEquals(91, evmAssets.size)
        assertEquals(
            evmNetworks.map { it.networkId }.toSet(),
            EvmProviderRegistry.supportedNetworkIds,
        )
        evmNetworks.forEach { network ->
            assertEquals(
                network.chainId?.toLong(),
                EvmProviderRegistry.requireConfig(network.networkId).chainId,
            )
        }
    }

    @Test
    fun publicNodeIsPrimaryWherePlanned() {
        val publicNodePrimaryNetworks = setOf(
            "ethereum",
            "arbitrum",
            "base",
            "optimism",
            "scroll",
            "polygon",
            "bnbChain",
            "opBNB",
            "avalanche",
            "celo",
            "kavaEvm",
        )

        publicNodePrimaryNetworks.forEach { networkId ->
            assertTrue(
                EvmProviderRegistry.requireConfig(networkId)
                    .providers
                    .first()
                    .rpcUrl
                    .contains("publicnode.com"),
            )
        }
        assertEquals(
            "https://mainnet.era.zksync.io",
            EvmProviderRegistry.requireConfig("zkSync").providers.first().rpcUrl,
        )
    }
}
