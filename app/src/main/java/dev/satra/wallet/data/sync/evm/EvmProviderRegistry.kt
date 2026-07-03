package dev.satra.wallet.data.sync.evm

import dev.satra.wallet.data.assets.SupportedAssetCatalog

object EvmProviderRegistry {
    val networks: Map<String, EvmNetworkConfig> = listOf(
        EvmNetworkConfig(
            networkId = "ethereum",
            chainId = 1,
            providers = listOf(
                EvmProvider("PublicNode Ethereum", "https://ethereum-rpc.publicnode.com"),
                EvmProvider("Ankr Ethereum", "https://rpc.ankr.com/eth"),
            ),
            explorerApis = listOf(
                EvmExplorerApi(
                    name = "Blockscout Ethereum",
                    baseUrl = "https://eth.blockscout.com",
                    style = EvmExplorerApiStyle.BlockscoutV2,
                ),
            ),
        ),
        EvmNetworkConfig(
            networkId = "arbitrum",
            chainId = 42161,
            providers = listOf(
                EvmProvider("PublicNode Arbitrum", "https://arbitrum-one-rpc.publicnode.com"),
                EvmProvider("Arbitrum Official", "https://arb1.arbitrum.io/rpc"),
            ),
            explorerApis = listOf(
                EvmExplorerApi(
                    name = "Blockscout Arbitrum",
                    baseUrl = "https://arbitrum.blockscout.com",
                    style = EvmExplorerApiStyle.BlockscoutV2,
                ),
            ),
        ),
        EvmNetworkConfig(
            networkId = "base",
            chainId = 8453,
            providers = listOf(
                EvmProvider("PublicNode Base", "https://base-rpc.publicnode.com"),
                EvmProvider("Base Official", "https://mainnet.base.org"),
            ),
            explorerApis = listOf(
                EvmExplorerApi(
                    name = "Blockscout Base",
                    baseUrl = "https://base.blockscout.com",
                    style = EvmExplorerApiStyle.BlockscoutV2,
                ),
            ),
        ),
        EvmNetworkConfig(
            networkId = "optimism",
            chainId = 10,
            providers = listOf(
                EvmProvider("PublicNode Optimism", "https://optimism-rpc.publicnode.com"),
                EvmProvider("Optimism Official", "https://mainnet.optimism.io"),
            ),
            explorerApis = listOf(
                EvmExplorerApi(
                    name = "Blockscout Optimism",
                    baseUrl = "https://optimism.blockscout.com",
                    style = EvmExplorerApiStyle.BlockscoutV2,
                ),
            ),
        ),
        EvmNetworkConfig(
            networkId = "scroll",
            chainId = 534352,
            providers = listOf(
                EvmProvider("PublicNode Scroll", "https://scroll-rpc.publicnode.com"),
                EvmProvider("Scroll Official", "https://rpc.scroll.io"),
            ),
            explorerApis = listOf(
                EvmExplorerApi(
                    name = "Blockscout Scroll",
                    baseUrl = "https://scroll.blockscout.com",
                    style = EvmExplorerApiStyle.BlockscoutV2,
                ),
            ),
        ),
        EvmNetworkConfig(
            networkId = "zkSync",
            chainId = 324,
            providers = listOf(
                EvmProvider("zkSync Official", "https://mainnet.era.zksync.io"),
                EvmProvider("DRPC zkSync", "https://zksync.drpc.org"),
                EvmProvider("1RPC zkSync", "https://public.1rpc.io/zksync2-era"),
                EvmProvider("Ankr zkSync", "https://rpc.ankr.com/zksync_era"),
            ),
            explorerApis = listOf(
                EvmExplorerApi(
                    name = "Blockscout zkSync",
                    baseUrl = "https://zksync.blockscout.com",
                    style = EvmExplorerApiStyle.BlockscoutV2,
                ),
            ),
        ),
        EvmNetworkConfig(
            networkId = "polygon",
            chainId = 137,
            providers = listOf(
                EvmProvider("PublicNode Polygon", "https://polygon-bor-rpc.publicnode.com"),
                EvmProvider("Polygon Official", "https://polygon-rpc.com"),
            ),
            explorerApis = listOf(
                EvmExplorerApi(
                    name = "Blockscout Polygon",
                    baseUrl = "https://polygon.blockscout.com",
                    style = EvmExplorerApiStyle.BlockscoutV2,
                ),
            ),
        ),
        EvmNetworkConfig(
            networkId = "bnbChain",
            chainId = 56,
            providers = listOf(
                EvmProvider("PublicNode BNB Chain", "https://bsc-rpc.publicnode.com"),
                EvmProvider("BNB Chain Official", "https://bsc-dataseed.binance.org"),
            ),
            explorerApis = listOf(
                EvmExplorerApi(
                    name = "Blockscout BNB Chain",
                    baseUrl = "https://bsc.blockscout.com",
                    style = EvmExplorerApiStyle.BlockscoutV2,
                ),
            ),
        ),
        EvmNetworkConfig(
            networkId = "opBNB",
            chainId = 204,
            providers = listOf(
                EvmProvider("PublicNode opBNB", "https://opbnb-rpc.publicnode.com"),
                EvmProvider("opBNB Official", "https://opbnb-mainnet-rpc.bnbchain.org"),
            ),
            explorerApis = listOf(
                EvmExplorerApi(
                    name = "Blockscout opBNB",
                    baseUrl = "https://opbnb.blockscout.com",
                    style = EvmExplorerApiStyle.BlockscoutV2,
                ),
            ),
        ),
        EvmNetworkConfig(
            networkId = "avalanche",
            chainId = 43114,
            providers = listOf(
                EvmProvider("PublicNode Avalanche", "https://avalanche-c-chain-rpc.publicnode.com"),
                EvmProvider("Avalanche Official", "https://api.avax.network/ext/bc/C/rpc"),
            ),
            explorerApis = listOf(
                EvmExplorerApi(
                    name = "Blockscout Avalanche",
                    baseUrl = "https://avalanche.blockscout.com",
                    style = EvmExplorerApiStyle.BlockscoutV2,
                ),
                EvmExplorerApi(
                    name = "Routescan Avalanche",
                    baseUrl = "https://api.routescan.io/v2/network/mainnet/evm/43114/etherscan",
                    style = EvmExplorerApiStyle.EtherscanCompatible,
                ),
            ),
        ),
        EvmNetworkConfig(
            networkId = "celo",
            chainId = 42220,
            providers = listOf(
                EvmProvider("PublicNode Celo", "https://celo-rpc.publicnode.com"),
                EvmProvider("Celo Forno", "https://forno.celo.org"),
            ),
            explorerApis = listOf(
                EvmExplorerApi(
                    name = "Blockscout Celo",
                    baseUrl = "https://celo.blockscout.com",
                    style = EvmExplorerApiStyle.BlockscoutV2,
                ),
            ),
        ),
        EvmNetworkConfig(
            networkId = "kavaEvm",
            chainId = 2222,
            providers = listOf(
                EvmProvider("PublicNode Kava EVM", "https://kava-evm-rpc.publicnode.com"),
                EvmProvider("Kava EVM Official", "https://evm.kava.io"),
            ),
            explorerApis = listOf(
                EvmExplorerApi(
                    name = "Blockscout Kava EVM",
                    baseUrl = "https://kava-evm.blockscout.com",
                    style = EvmExplorerApiStyle.BlockscoutV2,
                ),
            ),
        ),
    ).associateBy(EvmNetworkConfig::networkId)

    val supportedNetworkIds: Set<String> = networks.keys

    fun requireConfig(networkId: String): EvmNetworkConfig =
        networks[networkId] ?: error("Unsupported EVM network: $networkId")

    init {
        val catalogEvmNetworks = SupportedAssetCatalog.networks
            .filter { it.family == "evm" }
            .map { it.networkId }
            .toSet()
        require(catalogEvmNetworks == supportedNetworkIds) {
            "EVM registry must match SupportedAssetCatalog. registry=$supportedNetworkIds catalog=$catalogEvmNetworks"
        }
    }
}
