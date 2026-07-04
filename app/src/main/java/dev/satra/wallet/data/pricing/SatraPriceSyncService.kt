package dev.satra.wallet.data.pricing

import dev.satra.wallet.data.assets.SupportedAsset
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.math.BigDecimal

data class SatraAssetPrice(
    val asset: SupportedAsset,
    val localCurrencyCode: String,
    val usdPrice: BigDecimal,
    val localPrice: BigDecimal,
    val provider: String,
    val providerAssetId: String?,
    val syncedAtMillis: Long,
)

data class SatraAssetMarketData(
    val symbol: String,
    val name: String,
    val coinGeckoId: String?,
    val localCurrencyCode: String,
    val usdPrice: BigDecimal,
    val localPrice: BigDecimal,
    val marketCapUsd: BigDecimal?,
    val marketCapLocal: BigDecimal?,
    val volume24hUsd: BigDecimal?,
    val volume24hLocal: BigDecimal?,
    val high24hUsd: BigDecimal?,
    val low24hUsd: BigDecimal?,
    val priceChange24hPercent: BigDecimal?,
    val description: String?,
    val homepageUrl: String?,
    val provider: String,
    val chart7dJson: String,
    val syncedAtMillis: Long,
)

data class SatraPriceSyncResult(
    val localCurrencyCode: String,
    val prices: List<SatraAssetPrice>,
    val marketData: List<SatraAssetMarketData>,
    val coinbaseSymbols: Set<String>,
    val fallbackSymbols: Set<String>,
    val failedSymbols: Set<String>,
    val fxProvider: String,
    val updatedAtMillis: Long,
)

class SatraPriceSyncService(
    private val marketDataClient: SatraMarketDataClient = SatraMarketDataClient(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun syncSupportedAssetPrices(
        localCurrencyCode: String,
        nowMillis: Long = System.currentTimeMillis(),
        assets: List<SupportedAsset> = SupportedAssetCatalog.assets,
    ): SatraPriceSyncResult = coroutineScope {
        val normalizedCurrency = localCurrencyCode.uppercase()
        val supportedSymbols = assets.map { it.priceSymbol }.toSet()
        val fxDeferred = async(dispatcher) {
            marketDataClient.getUsdFxRate(normalizedCurrency)
        }
        val coinbaseSymbolsDeferred = async(dispatcher) {
            runCatching { marketDataClient.getCoinbaseOnlineUsdSymbols() }
                .getOrDefault(emptySet())
                .intersect(supportedSymbols)
        }
        val coinGeckoDeferred = async(dispatcher) {
            runCatching {
                marketDataClient.getCoinGeckoUsdPricesById(
                    SatraAssetPriceCatalog.coinGeckoIdsBySymbol.filterKeys { it in supportedSymbols },
                )
            }.getOrDefault(emptyMap())
        }
        val coinGeckoMarketsDeferred = async(dispatcher) {
            runCatching {
                marketDataClient.getCoinGeckoUsdMarketDataById(
                    SatraAssetPriceCatalog.coinGeckoIdsBySymbol.filterKeys { it in supportedSymbols },
                )
            }.getOrDefault(emptyMap())
        }

        val coinbaseSymbols = coinbaseSymbolsDeferred.await()
        val coinbaseQuotes = coinbaseSymbols
            .map { symbol ->
                async(dispatcher) {
                    symbol to runCatching { marketDataClient.getCoinbaseUsdPrice(symbol) }.getOrNull()
                }
            }
            .awaitAll()
            .mapNotNull { (symbol, quote) -> quote?.let { symbol to it } }
            .toMap()
        val coinGeckoQuotes = coinGeckoDeferred.await()
        val coinGeckoMarketQuotes = coinGeckoMarketsDeferred.await()
        val exchangeFallbackQuotes = (supportedSymbols - coinGeckoMarketQuotes.keys)
            .map { symbol ->
                async(dispatcher) {
                    symbol to listOf(
                        { marketDataClient.getBinanceUsdtPrice(symbol) },
                        { marketDataClient.getOkxUsdtPrice(symbol) },
                        { marketDataClient.getKuCoinUsdtPrice(symbol) },
                    ).firstNotNullOfOrNull { provider ->
                        runCatching { provider() }.getOrNull()
                    }
                }
            }
            .awaitAll()
            .mapNotNull { (symbol, quote) -> quote?.let { symbol to it } }
            .toMap()
        val fxQuote = fxDeferred.await()

        val quotesBySymbol = supportedSymbols.associateWith { symbol ->
            coinbaseQuotes[symbol] ?: coinGeckoQuotes[symbol] ?: exchangeFallbackQuotes[symbol]
        }
        val marketQuotesBySymbol = supportedSymbols.associateWith { symbol ->
            coinGeckoMarketQuotes[symbol] ?: exchangeFallbackQuotes[symbol]?.toMarketQuote(symbol, assets)
        }
        val prices = assets.mapNotNull { asset ->
            val symbol = asset.priceSymbol
            val quote = quotesBySymbol[symbol] ?: return@mapNotNull null
            SatraAssetPrice(
                asset = asset,
                localCurrencyCode = normalizedCurrency,
                usdPrice = quote.price,
                localPrice = quote.price.multiply(fxQuote.rate),
                provider = if (quote.provider == SatraMarketDataClient.COINBASE_EXCHANGE_PROVIDER) {
                    if (normalizedCurrency == "USD") quote.provider else "${quote.provider}+${fxQuote.provider}"
                } else {
                    if (normalizedCurrency == "USD") quote.provider else "${quote.provider}+${fxQuote.provider}"
                },
                providerAssetId = quote.providerAssetId,
                syncedAtMillis = nowMillis,
            )
        }
        val marketData = marketQuotesBySymbol
            .mapNotNull { (symbol, quote) ->
                quote?.toSatraAssetMarketData(
                    symbol = symbol,
                    localCurrencyCode = normalizedCurrency,
                    fxRate = fxQuote.rate,
                    syncedAtMillis = nowMillis,
                )
            }
            .distinctBy { market -> market.symbol }
        val pricedSymbols = prices.map { it.asset.priceSymbol }.toSet()

        SatraPriceSyncResult(
            localCurrencyCode = normalizedCurrency,
            prices = prices,
            marketData = marketData,
            coinbaseSymbols = coinbaseQuotes.keys,
            fallbackSymbols = (prices
                .filter { it.asset.priceSymbol !in coinbaseQuotes.keys }
                .map { it.asset.priceSymbol } + exchangeFallbackQuotes.keys)
                .toSet(),
            failedSymbols = supportedSymbols - pricedSymbols,
            fxProvider = fxQuote.provider,
            updatedAtMillis = nowMillis,
        )
    }

    suspend fun syncSupportedAssetPricesOrNull(
        localCurrencyCode: String,
        nowMillis: Long = System.currentTimeMillis(),
        assets: List<SupportedAsset> = SupportedAssetCatalog.assets,
    ): SatraPriceSyncResult? =
        withContext(dispatcher) {
            runCatching {
                syncSupportedAssetPrices(
                    localCurrencyCode = localCurrencyCode,
                    nowMillis = nowMillis,
                    assets = assets,
                )
            }.getOrNull()
        }

    suspend fun syncAssetMarketDetailOrNull(
        symbol: String,
        localCurrencyCode: String,
        nowMillis: Long = System.currentTimeMillis(),
        assets: List<SupportedAsset> = SupportedAssetCatalog.assets,
    ): SatraAssetMarketData? =
        withContext(dispatcher) {
            val normalizedSymbol = symbol.uppercase()
            val normalizedCurrency = localCurrencyCode.uppercase()
            val coinGeckoId = SatraAssetPriceCatalog.coinGeckoIdsBySymbol[normalizedSymbol]
            val fxQuote = runCatching { marketDataClient.getUsdFxRate(normalizedCurrency) }
                .getOrDefault(
                    FxRateQuote(
                        baseCurrency = "USD",
                        quoteCurrency = normalizedCurrency,
                        rate = BigDecimal.ONE,
                        provider = "local-fx-fallback",
                    ),
                )
            val quote = if (coinGeckoId != null) {
                runCatching {
                    marketDataClient.getCoinGeckoUsdMarketDetail(
                        assetSymbol = normalizedSymbol,
                        coinGeckoId = coinGeckoId,
                    )
                }.getOrNull()
            } else {
                null
            } ?: listOf(
                { marketDataClient.getBinanceUsdtPrice(normalizedSymbol) },
                { marketDataClient.getOkxUsdtPrice(normalizedSymbol) },
                { marketDataClient.getKuCoinUsdtPrice(normalizedSymbol) },
            ).firstNotNullOfOrNull { provider ->
                runCatching { provider().toMarketQuote(normalizedSymbol, assets) }.getOrNull()
            }

            quote?.toSatraAssetMarketData(
                symbol = normalizedSymbol,
                localCurrencyCode = normalizedCurrency,
                fxRate = fxQuote.rate,
                syncedAtMillis = nowMillis,
            )
        }
}

private fun CryptoPriceQuote.toMarketQuote(
    symbol: String,
    assets: List<SupportedAsset>,
): AssetMarketQuote {
    val assetName = assets.firstOrNull { asset -> asset.priceSymbol == symbol }?.name ?: symbol
    return AssetMarketQuote(
        assetSymbol = symbol,
        name = assetName,
        quoteCurrency = quoteCurrency,
        price = price,
        provider = provider,
        providerAssetId = providerAssetId,
    )
}

private fun AssetMarketQuote.toSatraAssetMarketData(
    symbol: String,
    localCurrencyCode: String,
    fxRate: BigDecimal,
    syncedAtMillis: Long,
): SatraAssetMarketData =
    SatraAssetMarketData(
        symbol = symbol,
        name = name,
        coinGeckoId = providerAssetId?.takeIf {
            provider == SatraMarketDataClient.COINGECKO_MARKETS_PROVIDER ||
                provider == SatraMarketDataClient.COINGECKO_DETAIL_PROVIDER
        },
        localCurrencyCode = localCurrencyCode,
        usdPrice = price,
        localPrice = price.multiply(fxRate),
        marketCapUsd = marketCap,
        marketCapLocal = marketCap?.multiply(fxRate),
        volume24hUsd = volume24h,
        volume24hLocal = volume24h?.multiply(fxRate),
        high24hUsd = high24h,
        low24hUsd = low24h,
        priceChange24hPercent = priceChange24hPercent,
        description = description,
        homepageUrl = homepageUrl,
        provider = provider,
        chart7dJson = chart7dJson,
        syncedAtMillis = syncedAtMillis,
    )
