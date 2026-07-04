package dev.satra.wallet.data.sync.solana

object SolanaProviderRegistry {
    const val NETWORK_ID = "solana"
    const val NATIVE_ASSET_ID = "solana:sol"
    const val MAINNET_GENESIS_HASH = "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d"
    const val TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    const val TOKEN_2022_PROGRAM_ID = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"

    val config: SolanaNetworkConfig = SolanaNetworkConfig(
        networkId = NETWORK_ID,
        nativeAssetId = NATIVE_ASSET_ID,
        nativeDecimals = 9,
        providers = listOf(
            SolanaRpcProvider(
                name = "publicnode-solana-mainnet",
                rpcUrl = "https://solana-rpc.publicnode.com",
            ),
            SolanaRpcProvider(
                name = "solana-mainnet-beta-public",
                rpcUrl = "https://api.mainnet-beta.solana.com",
            ),
        ),
    )

    val supportedNetworkIds: Set<String> = setOf(NETWORK_ID)

    fun requireConfig(networkId: String): SolanaNetworkConfig {
        require(networkId == NETWORK_ID) { "Unsupported Solana sync network: $networkId" }
        return config
    }
}
