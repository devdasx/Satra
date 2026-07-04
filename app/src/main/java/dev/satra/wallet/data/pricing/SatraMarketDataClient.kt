package dev.satra.wallet.data.pricing

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class CryptoPriceQuote(
    val assetSymbol: String,
    val quoteCurrency: String,
    val price: BigDecimal,
    val provider: String,
    val providerAssetId: String? = null,
)

data class FxRateQuote(
    val baseCurrency: String,
    val quoteCurrency: String,
    val rate: BigDecimal,
    val provider: String,
)

data class AssetMarketQuote(
    val assetSymbol: String,
    val name: String,
    val quoteCurrency: String,
    val price: BigDecimal,
    val provider: String,
    val providerAssetId: String? = null,
    val marketCap: BigDecimal? = null,
    val volume24h: BigDecimal? = null,
    val high24h: BigDecimal? = null,
    val low24h: BigDecimal? = null,
    val priceChange24hPercent: BigDecimal? = null,
    val description: String? = null,
    val homepageUrl: String? = null,
    val chart7dJson: String = "[]",
)

fun interface SatraHttpTransport {
    fun get(url: String): String
}

class UrlConnectionSatraHttpTransport(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 10_000,
) : SatraHttpTransport {
    override fun get(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Satra Android Wallet")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}

class SatraMarketDataClient(
    private val transport: SatraHttpTransport = UrlConnectionSatraHttpTransport(),
) {
    fun getCoinbaseOnlineUsdSymbols(): Set<String> {
        val response = transport.get("https://api.exchange.coinbase.com/products")
        val products = org.json.JSONArray(response)
        return buildSet {
            for (index in 0 until products.length()) {
                val product = products.getJSONObject(index)
                if (
                    product.optString("quote_currency") == "USD" &&
                    product.optString("status") == "online" &&
                    !product.optBoolean("trading_disabled", false)
                ) {
                    add(product.getString("base_currency").normalizedTicker())
                }
            }
        }
    }

    fun getCoinbaseUsdPrice(assetSymbol: String): CryptoPriceQuote {
        val symbol = assetSymbol.normalizedTicker()
        val productId = urlEncode("$symbol-USD")
        val response = transport.get("https://api.exchange.coinbase.com/products/$productId/ticker")
        val price = JSONObject(response).getString("price").toBigDecimal()
        return CryptoPriceQuote(
            assetSymbol = symbol,
            quoteCurrency = "USD",
            price = price,
            provider = COINBASE_EXCHANGE_PROVIDER,
            providerAssetId = "$symbol-USD",
        )
    }

    fun getCoinGeckoUsdPricesById(
        coinGeckoIdsBySymbol: Map<String, String>,
    ): Map<String, CryptoPriceQuote> {
        if (coinGeckoIdsBySymbol.isEmpty()) {
            return emptyMap()
        }
        val ids = coinGeckoIdsBySymbol.values.distinct()
        val response = transport.get(
            "https://api.coingecko.com/api/v3/simple/price" +
                "?ids=${ids.joinToString(",") { urlEncode(it) }}" +
                "&vs_currencies=usd" +
                "&precision=full",
        )
        val json = JSONObject(response)
        return coinGeckoIdsBySymbol.mapNotNull { (symbol, coinGeckoId) ->
            val price = json.optJSONObject(coinGeckoId)
                ?.opt("usd")
                ?.toString()
                ?.takeIf(String::isNotBlank)
                ?.toBigDecimalOrNull()
                ?: return@mapNotNull null
            symbol to CryptoPriceQuote(
                assetSymbol = symbol,
                quoteCurrency = "USD",
                price = price,
                provider = COINGECKO_PROVIDER,
                providerAssetId = coinGeckoId,
            )
        }.toMap()
    }

    fun getCoinGeckoUsdMarketDataById(
        coinGeckoIdsBySymbol: Map<String, String>,
    ): Map<String, AssetMarketQuote> {
        if (coinGeckoIdsBySymbol.isEmpty()) {
            return emptyMap()
        }
        val ids = coinGeckoIdsBySymbol.values.distinct()
        val response = transport.get(
            "https://api.coingecko.com/api/v3/coins/markets" +
                "?vs_currency=usd" +
                "&ids=${ids.joinToString(",") { urlEncode(it) }}" +
                "&order=market_cap_desc" +
                "&per_page=${ids.size.coerceAtLeast(1)}" +
                "&page=1" +
                "&sparkline=true" +
                "&price_change_percentage=24h" +
                "&precision=full",
        )
        val json = JSONArray(response)
        val symbolById = coinGeckoIdsBySymbol.entries.associate { (symbol, id) -> id to symbol }
        return buildMap {
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                val symbol = symbolById[id] ?: continue
                val price = item.optBigDecimal("current_price") ?: continue
                put(
                    symbol,
                    AssetMarketQuote(
                        assetSymbol = symbol,
                        name = item.optString("name", symbol),
                        quoteCurrency = "USD",
                        price = price,
                        provider = COINGECKO_MARKETS_PROVIDER,
                        providerAssetId = id,
                        marketCap = item.optBigDecimal("market_cap"),
                        volume24h = item.optBigDecimal("total_volume"),
                        high24h = item.optBigDecimal("high_24h"),
                        low24h = item.optBigDecimal("low_24h"),
                        priceChange24hPercent = item.optBigDecimal("price_change_percentage_24h"),
                        chart7dJson = item.optJSONObject("sparkline_in_7d")
                            ?.optJSONArray("price")
                            ?.toString()
                            ?: "[]",
                    ),
                )
            }
        }
    }

    fun getCoinGeckoUsdMarketDetail(
        assetSymbol: String,
        coinGeckoId: String,
    ): AssetMarketQuote {
        val symbol = assetSymbol.normalizedTicker()
        val response = transport.get(
            "https://api.coingecko.com/api/v3/coins/${urlEncode(coinGeckoId)}" +
                "?localization=false" +
                "&tickers=false" +
                "&market_data=true" +
                "&community_data=false" +
                "&developer_data=false" +
                "&sparkline=true",
        )
        val json = JSONObject(response)
        val marketData = json.optJSONObject("market_data") ?: JSONObject()
        val currentPrice = marketData.optJSONObject("current_price")?.optBigDecimal("usd")
            ?: error("CoinGecko detail did not include USD price for $coinGeckoId.")
        return AssetMarketQuote(
            assetSymbol = symbol,
            name = json.optString("name", symbol),
            quoteCurrency = "USD",
            price = currentPrice,
            provider = COINGECKO_DETAIL_PROVIDER,
            providerAssetId = coinGeckoId,
            marketCap = marketData.optJSONObject("market_cap")?.optBigDecimal("usd"),
            volume24h = marketData.optJSONObject("total_volume")?.optBigDecimal("usd"),
            high24h = marketData.optJSONObject("high_24h")?.optBigDecimal("usd"),
            low24h = marketData.optJSONObject("low_24h")?.optBigDecimal("usd"),
            priceChange24hPercent = marketData.optBigDecimal("price_change_percentage_24h"),
            description = json.optJSONObject("description")
                ?.optString("en")
                ?.cleanHtmlText()
                ?.takeIf(String::isNotBlank),
            homepageUrl = json.optJSONObject("links")
                ?.optJSONArray("homepage")
                ?.firstNonBlankString(),
            chart7dJson = marketData.optJSONObject("sparkline_7d")
                ?.optJSONArray("price")
                ?.toString()
                ?: "[]",
        )
    }

    fun getBinanceUsdtPrice(assetSymbol: String): CryptoPriceQuote =
        getExchangeUsdtPrice(
            assetSymbol = assetSymbol,
            provider = BINANCE_PROVIDER,
            providerAssetId = "${assetSymbol.normalizedTicker()}USDT",
            url = "https://api.binance.com/api/v3/ticker/price?symbol=${urlEncode("${assetSymbol.normalizedTicker()}USDT")}",
        ) { response ->
            JSONObject(response).getString("price").toBigDecimal()
        }

    fun getOkxUsdtPrice(assetSymbol: String): CryptoPriceQuote =
        getExchangeUsdtPrice(
            assetSymbol = assetSymbol,
            provider = OKX_PROVIDER,
            providerAssetId = "${assetSymbol.normalizedTicker()}-USDT",
            url = "https://www.okx.com/api/v5/market/ticker?instId=${urlEncode("${assetSymbol.normalizedTicker()}-USDT")}",
        ) { response ->
            JSONObject(response)
                .getJSONArray("data")
                .getJSONObject(0)
                .getString("last")
                .toBigDecimal()
        }

    fun getKuCoinUsdtPrice(assetSymbol: String): CryptoPriceQuote =
        getExchangeUsdtPrice(
            assetSymbol = assetSymbol,
            provider = KUCOIN_PROVIDER,
            providerAssetId = "${assetSymbol.normalizedTicker()}-USDT",
            url = "https://api.kucoin.com/api/v1/market/orderbook/level1?symbol=${urlEncode("${assetSymbol.normalizedTicker()}-USDT")}",
        ) { response ->
            JSONObject(response)
                .getJSONObject("data")
                .getString("price")
                .toBigDecimal()
        }

    fun getUsdFxRate(quoteCurrency: String): FxRateQuote {
        val target = quoteCurrency.normalizedTicker()
        if (target == "USD") {
            return FxRateQuote(
                baseCurrency = "USD",
                quoteCurrency = "USD",
                rate = BigDecimal.ONE,
                provider = FRANKFURTER_PROVIDER,
            )
        }
        val response = transport.get(
            "https://api.frankfurter.dev/v1/latest?base=USD&symbols=${urlEncode(target)}",
        )
        val rate = JSONObject(response)
            .getJSONObject("rates")
            .getString(target)
            .toBigDecimal()
        return FxRateQuote(
            baseCurrency = "USD",
            quoteCurrency = target,
            rate = rate,
            provider = FRANKFURTER_PROVIDER,
        )
    }

    fun getLocalCryptoPrice(
        assetSymbol: String,
        localCurrencyCode: String,
    ): CryptoPriceQuote {
        val usdQuote = getCoinbaseUsdPrice(assetSymbol)
        val fxQuote = getUsdFxRate(localCurrencyCode)
        return usdQuote.copy(
            quoteCurrency = fxQuote.quoteCurrency,
            price = usdQuote.price.multiply(fxQuote.rate),
            provider = "${usdQuote.provider}+${fxQuote.provider}",
        )
    }

    private fun String.normalizedTicker(): String =
        trim().uppercase(Locale.US)

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun getExchangeUsdtPrice(
        assetSymbol: String,
        provider: String,
        providerAssetId: String,
        url: String,
        parsePrice: (String) -> BigDecimal,
    ): CryptoPriceQuote {
        val symbol = assetSymbol.normalizedTicker()
        val price = parsePrice(transport.get(url))
        return CryptoPriceQuote(
            assetSymbol = symbol,
            quoteCurrency = "USD",
            price = price,
            provider = provider,
            providerAssetId = providerAssetId,
        )
    }

    private fun JSONObject.optBigDecimal(name: String): BigDecimal? =
        opt(name)
            ?.takeUnless { it == JSONObject.NULL }
            ?.toString()
            ?.takeIf(String::isNotBlank)
            ?.toBigDecimalOrNull()

    private fun JSONArray.firstNonBlankString(): String? {
        for (index in 0 until length()) {
            val value = optString(index).takeIf(String::isNotBlank)
            if (value != null) return value
        }
        return null
    }

    private fun String.cleanHtmlText(): String =
        replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        const val COINBASE_EXCHANGE_PROVIDER = "coinbase-exchange-public"
        const val COINGECKO_PROVIDER = "coingecko-simple-public"
        const val COINGECKO_MARKETS_PROVIDER = "coingecko-markets-public"
        const val COINGECKO_DETAIL_PROVIDER = "coingecko-detail-public"
        const val BINANCE_PROVIDER = "binance-spot-public"
        const val OKX_PROVIDER = "okx-spot-public"
        const val KUCOIN_PROVIDER = "kucoin-spot-public"
        const val FRANKFURTER_PROVIDER = "frankfurter-public"
    }
}
