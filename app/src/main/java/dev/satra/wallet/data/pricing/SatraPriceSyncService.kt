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

data class SatraPriceSyncResult(
    val localCurrencyCode: String,
    val prices: List<SatraAssetPrice>,
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
        val fxQuote = fxDeferred.await()

        val quotesBySymbol = supportedSymbols.associateWith { symbol ->
            coinbaseQuotes[symbol] ?: coinGeckoQuotes[symbol]
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
        val pricedSymbols = prices.map { it.asset.priceSymbol }.toSet()

        SatraPriceSyncResult(
            localCurrencyCode = normalizedCurrency,
            prices = prices,
            coinbaseSymbols = coinbaseQuotes.keys,
            fallbackSymbols = prices
                .filter { it.asset.priceSymbol !in coinbaseQuotes.keys }
                .map { it.asset.priceSymbol }
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
}
