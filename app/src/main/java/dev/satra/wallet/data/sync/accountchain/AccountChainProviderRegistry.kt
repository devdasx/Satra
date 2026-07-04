package dev.satra.wallet.data.sync.accountchain

import dev.satra.wallet.data.assets.SupportedAssetCatalog

object AccountChainProviderRegistry {
    private val configsByNetworkId: Map<String, AccountChainNetworkConfig> = listOf(
        AccountChainNetworkConfig(
            networkId = "tron",
            nativeAssetId = "tron:trx",
            nativeDecimals = 6,
            providers = listOf(
                AccountChainProvider("trongrid-public", "https://api.trongrid.io"),
                AccountChainProvider("tronstack-public", "https://apilist.tronscanapi.com"),
            ),
        ),
        AccountChainNetworkConfig(
            networkId = "aptos",
            nativeAssetId = "aptos:apt",
            nativeDecimals = 8,
            providers = listOf(
                AccountChainProvider("aptos-indexer-public", "https://api.mainnet.aptoslabs.com/v1/graphql"),
                AccountChainProvider("aptos-fullnode-public", "https://fullnode.mainnet.aptoslabs.com/v1"),
            ),
        ),
        AccountChainNetworkConfig(
            networkId = "near",
            nativeAssetId = "near:near",
            nativeDecimals = 24,
            providers = listOf(
                AccountChainProvider("near-rpc-public", "https://rpc.mainnet.near.org"),
                AccountChainProvider("nearblocks-public", "https://api.nearblocks.io"),
            ),
        ),
        AccountChainNetworkConfig(
            networkId = "polkadot",
            nativeAssetId = "polkadot:dot",
            nativeDecimals = 10,
            providers = listOf(
                AccountChainProvider("polkadot-rpc-public", "https://rpc.polkadot.io"),
                AccountChainProvider("polkadot-onfinality-public", "https://polkadot.api.onfinality.io/public"),
                AccountChainProvider("polkadot-asset-hub-rpc-public", "https://polkadot-asset-hub-rpc.polkadot.io"),
                AccountChainProvider("polkadot-asset-hub-onfinality-public", "https://statemint.api.onfinality.io/public"),
            ),
        ),
        AccountChainNetworkConfig(
            networkId = "ripple",
            nativeAssetId = "ripple:xrp",
            nativeDecimals = 6,
            providers = listOf(
                AccountChainProvider("xrpl-s1-public", "https://s1.ripple.com:51234"),
                AccountChainProvider("xrpl-s2-public", "https://s2.ripple.com:51234"),
            ),
        ),
        AccountChainNetworkConfig(
            networkId = "stellar",
            nativeAssetId = "stellar:xlm",
            nativeDecimals = 7,
            providers = listOf(
                AccountChainProvider("stellar-horizon-public", "https://horizon.stellar.org"),
            ),
        ),
        AccountChainNetworkConfig(
            networkId = "sui",
            nativeAssetId = "sui:sui",
            nativeDecimals = 9,
            providers = listOf(
                AccountChainProvider("sui-fullnode-public", "https://fullnode.mainnet.sui.io:443"),
            ),
        ),
        AccountChainNetworkConfig(
            networkId = "ton",
            nativeAssetId = "ton:ton",
            nativeDecimals = 9,
            providers = listOf(
                AccountChainProvider("tonapi-public", "https://tonapi.io"),
                AccountChainProvider("toncenter-public", "https://toncenter.com/api/v2"),
            ),
        ),
        AccountChainNetworkConfig(
            networkId = "kava",
            nativeAssetId = "kava:kava",
            nativeDecimals = 6,
            providers = listOf(
                AccountChainProvider("kava-lcd-public", "https://api.data.kava.io"),
            ),
        ),
    ).associateBy { it.networkId }

    val supportedNetworkIds: Set<String> = configsByNetworkId.keys

    fun config(networkId: String): AccountChainNetworkConfig? =
        configsByNetworkId[networkId]

    fun requireConfig(networkId: String): AccountChainNetworkConfig =
        config(networkId) ?: error("Unsupported account-chain network: $networkId")

    fun validateAgainstSupportedCatalog() {
        val catalogNetworks = SupportedAssetCatalog.networks
            .filter { it.family in accountChainFamilies }
            .map { it.networkId }
            .toSet()
        check(catalogNetworks == supportedNetworkIds) {
            "Account-chain registry must match SupportedAssetCatalog. " +
                "registry=$supportedNetworkIds catalog=$catalogNetworks"
        }
    }

    private val accountChainFamilies = setOf(
        "aptos",
        "near",
        "polkadot",
        "ripple",
        "stellar",
        "sui",
        "ton",
        "tron",
        "cosmos",
    )
}
