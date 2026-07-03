package dev.satra.wallet.data.pricing

import dev.satra.wallet.data.assets.SupportedAsset
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import java.util.Locale

object SatraAssetPriceCatalog {
    val supportedSymbols: Set<String> =
        SupportedAssetCatalog.assets.map { asset -> asset.priceSymbol }.toSet()

    val coinGeckoIdsBySymbol: Map<String, String> = mapOf(
        "APT" to "aptos",
        "AUSD" to "agora-dollar",
        "AVAX" to "avalanche-2",
        "BCH" to "bitcoin-cash",
        "BNB" to "binancecoin",
        "BTC" to "bitcoin",
        "CELO" to "celo",
        "DAI" to "dai",
        "DOGE" to "dogecoin",
        "DOT" to "polkadot",
        "DUSD" to "standx-dusd",
        "ETH" to "ethereum",
        "EURC" to "euro-coin",
        "FDUSD" to "first-digital-usd",
        "FRAX" to "frax",
        "GUSD" to "gemini-dollar",
        "KAVA" to "kava",
        "LISUSD" to "helio-protocol-hay",
        "LTC" to "litecoin",
        "NEAR" to "near",
        "POL" to "polygon-ecosystem-token",
        "PYUSD" to "paypal-usd",
        "RLUSD" to "ripple-usd",
        "SOL" to "solana",
        "STETH" to "staked-ether",
        "SUI" to "sui",
        "TON" to "the-open-network",
        "TRX" to "tron",
        "TUSD" to "true-usd",
        "USD0" to "usual-usd",
        "USD1" to "usd1-wlfi",
        "USDAI" to "usdai",
        "USDC" to "usd-coin",
        "USDD" to "usdd",
        "USDE" to "ethena-usde",
        "USDF" to "falcon-finance",
        "USDG" to "global-dollar",
        "USDP" to "paxos-standard",
        "USDS" to "usds",
        "USDT" to "tether",
        "WBTC" to "wrapped-bitcoin",
        "WETH" to "weth",
        "XLM" to "stellar",
        "XRP" to "ripple",
    )

    init {
        val missingSymbols = supportedSymbols - coinGeckoIdsBySymbol.keys
        require(missingSymbols.isEmpty()) {
            "Missing CoinGecko price IDs for: $missingSymbols"
        }
    }
}

val SupportedAsset.priceSymbol: String
    get() = symbol.uppercase(Locale.US)
