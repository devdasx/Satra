package dev.satra.wallet.data.pricing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class SatraMarketDataClientTest {
    @Test
    fun coinbaseUsdSymbolsOnlyIncludesOnlineUsdProducts() {
        val transport = RecordingTransport(
            "https://api.exchange.coinbase.com/products" to """
                [
                    {"base_currency":"BTC","quote_currency":"USD","status":"online","trading_disabled":false},
                    {"base_currency":"ETH","quote_currency":"EUR","status":"online","trading_disabled":false},
                    {"base_currency":"DOGE","quote_currency":"USD","status":"offline","trading_disabled":false},
                    {"base_currency":"SOL","quote_currency":"USD","status":"online","trading_disabled":true}
                ]
            """.trimIndent(),
        )
        val client = SatraMarketDataClient(transport)

        assertEquals(setOf("BTC"), client.getCoinbaseOnlineUsdSymbols())
    }

    @Test
    fun coinbaseUsdPriceParsesPublicTicker() {
        val transport = RecordingTransport(
            "https://api.exchange.coinbase.com/products/BTC-USD/ticker" to """
                {"price":"61234.56"}
            """.trimIndent(),
        )
        val client = SatraMarketDataClient(transport)

        val quote = client.getCoinbaseUsdPrice("btc")

        assertEquals("BTC", quote.assetSymbol)
        assertEquals("USD", quote.quoteCurrency)
        assertEquals(BigDecimal("61234.56"), quote.price)
        assertEquals(SatraMarketDataClient.COINBASE_EXCHANGE_PROVIDER, quote.provider)
        assertEquals("BTC-USD", quote.providerAssetId)
        assertEquals(
            listOf("https://api.exchange.coinbase.com/products/BTC-USD/ticker"),
            transport.urls,
        )
    }

    @Test
    fun frankfurterFxRateParsesUsdBaseRate() {
        val transport = RecordingTransport(
            "https://api.frankfurter.dev/v1/latest?base=USD&symbols=EUR" to """
                {"base":"USD","rates":{"EUR":"0.92"}}
            """.trimIndent(),
        )
        val client = SatraMarketDataClient(transport)

        val quote = client.getUsdFxRate("eur")

        assertEquals("USD", quote.baseCurrency)
        assertEquals("EUR", quote.quoteCurrency)
        assertEquals(BigDecimal("0.92"), quote.rate)
        assertEquals(SatraMarketDataClient.FRANKFURTER_PROVIDER, quote.provider)
    }

    @Test
    fun fxRateFallsBackToOpenExchangeRateApi() {
        val transport = RecordingTransport(
            "https://open.er-api.com/v6/latest/USD" to """
                {"result":"success","base_code":"USD","rates":{"VND":"25000"}}
            """.trimIndent(),
        )
        val client = SatraMarketDataClient(transport)

        val quote = client.getUsdFxRate("vnd")

        assertEquals("USD", quote.baseCurrency)
        assertEquals("VND", quote.quoteCurrency)
        assertEquals(BigDecimal("25000"), quote.rate)
        assertEquals(SatraMarketDataClient.EXCHANGE_RATE_API_OPEN_PROVIDER, quote.provider)
        assertEquals(
            listOf(
                "https://api.frankfurter.dev/v1/latest?base=USD&symbols=VND",
                "https://open.er-api.com/v6/latest/USD",
            ),
            transport.urls,
        )
    }

    @Test
    fun coinGeckoUsdPriceParsesBatchByExplicitIds() {
        val transport = RecordingTransport(
            "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,usd-coin&vs_currencies=usd&precision=full" to """
                {"bitcoin":{"usd":61234.56},"usd-coin":{"usd":0.9998}}
            """.trimIndent(),
        )
        val client = SatraMarketDataClient(transport)

        val quotes = client.getCoinGeckoUsdPricesById(
            mapOf(
                "BTC" to "bitcoin",
                "USDC" to "usd-coin",
            ),
        )

        assertEquals(BigDecimal("61234.56"), quotes.getValue("BTC").price)
        assertEquals(BigDecimal("0.9998"), quotes.getValue("USDC").price)
        assertEquals(SatraMarketDataClient.COINGECKO_PROVIDER, quotes.getValue("USDC").provider)
        assertEquals("usd-coin", quotes.getValue("USDC").providerAssetId)
    }

    @Test
    fun coinGeckoMarketDataParsesSparklineAndStats() {
        val transport = RecordingTransport(
            "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&ids=bitcoin,ethereum&order=market_cap_desc&per_page=2&page=1&sparkline=true&price_change_percentage=24h&precision=full" to """
                [
                    {
                        "id":"bitcoin",
                        "symbol":"btc",
                        "name":"Bitcoin",
                        "current_price":61234.56,
                        "market_cap":1200000000000,
                        "total_volume":25000000000,
                        "high_24h":62000,
                        "low_24h":60000,
                        "price_change_percentage_24h":1.25,
                        "sparkline_in_7d":{"price":[60000,60500,61234.56]}
                    },
                    {
                        "id":"ethereum",
                        "symbol":"eth",
                        "name":"Ethereum",
                        "current_price":3000,
                        "market_cap":360000000000,
                        "total_volume":10000000000,
                        "high_24h":3050,
                        "low_24h":2950,
                        "price_change_percentage_24h":-0.5,
                        "sparkline_in_7d":{"price":[2950,3020,3000]}
                    }
                ]
            """.trimIndent(),
        )
        val client = SatraMarketDataClient(transport)

        val quotes = client.getCoinGeckoUsdMarketDataById(
            mapOf(
                "BTC" to "bitcoin",
                "ETH" to "ethereum",
            ),
        )

        val btc = quotes.getValue("BTC")
        assertEquals(BigDecimal("61234.56"), btc.price)
        assertEquals(BigDecimal("1200000000000"), btc.marketCap)
        assertEquals(BigDecimal("25000000000"), btc.volume24h)
        assertEquals(BigDecimal("1.25"), btc.priceChange24hPercent)
        assertEquals("[60000,60500,61234.56]", btc.chart7dJson)
        assertEquals(SatraMarketDataClient.COINGECKO_MARKETS_PROVIDER, btc.provider)
    }

    @Test
    fun coinGeckoDetailParsesDescriptionAndHomepage() {
        val transport = RecordingTransport(
            "https://api.coingecko.com/api/v3/coins/bitcoin?localization=false&tickers=false&market_data=true&community_data=false&developer_data=false&sparkline=true" to """
                {
                    "id":"bitcoin",
                    "symbol":"btc",
                    "name":"Bitcoin",
                    "description":{"en":"<p>Peer-to-peer cash &amp; store of value.</p>"},
                    "links":{"homepage":["https://bitcoin.org",""]},
                    "market_data":{
                        "current_price":{"usd":61234.56},
                        "market_cap":{"usd":1200000000000},
                        "total_volume":{"usd":25000000000},
                        "high_24h":{"usd":62000},
                        "low_24h":{"usd":60000},
                        "price_change_percentage_24h":1.25,
                        "sparkline_7d":{"price":[60000,61234.56]}
                    }
                }
            """.trimIndent(),
        )
        val client = SatraMarketDataClient(transport)

        val quote = client.getCoinGeckoUsdMarketDetail("btc", "bitcoin")

        assertEquals("BTC", quote.assetSymbol)
        assertEquals("Peer-to-peer cash & store of value.", quote.description)
        assertEquals("https://bitcoin.org", quote.homepageUrl)
        assertEquals("[60000,61234.56]", quote.chart7dJson)
        assertEquals(SatraMarketDataClient.COINGECKO_DETAIL_PROVIDER, quote.provider)
    }

    @Test
    fun noKeyExchangeTickersParseUsdFallbackPrices() {
        val transport = RecordingTransport(
            "https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT" to """
                {"symbol":"BTCUSDT","price":"61234.56"}
            """.trimIndent(),
            "https://www.okx.com/api/v5/market/ticker?instId=ETH-USDT" to """
                {"code":"0","data":[{"instId":"ETH-USDT","last":"3000.12"}]}
            """.trimIndent(),
            "https://api.kucoin.com/api/v1/market/orderbook/level1?symbol=SOL-USDT" to """
                {"code":"200000","data":{"price":"150.5"}}
            """.trimIndent(),
        )
        val client = SatraMarketDataClient(transport)

        assertEquals(BigDecimal("61234.56"), client.getBinanceUsdtPrice("btc").price)
        assertEquals(BigDecimal("3000.12"), client.getOkxUsdtPrice("eth").price)
        assertEquals(BigDecimal("150.5"), client.getKuCoinUsdtPrice("sol").price)
    }

    @Test
    fun coinGeckoMarketChartParsesPriceHistoryFallback() {
        val transport = RecordingTransport(
            "https://api.coingecko.com/api/v3/coins/bitcoin/market_chart?vs_currency=usd&days=7&precision=full" to """
                {"prices":[[1000,60000.25],[2000,61234.56]]}
            """.trimIndent(),
        )
        val client = SatraMarketDataClient(transport)

        val quote = client.getCoinGeckoUsdMarketChart("btc", "bitcoin", "Bitcoin")

        assertEquals("BTC", quote.assetSymbol)
        assertEquals("Bitcoin", quote.name)
        assertEquals(BigDecimal("61234.56"), quote.price)
        assertEquals("[60000.25,61234.56]", quote.chart7dJson)
        assertEquals(SatraMarketDataClient.COINGECKO_MARKET_CHART_PROVIDER, quote.provider)
    }

    @Test
    fun binanceMarketFallbackParsesStatsAndKlines() {
        val transport = RecordingTransport(
            "https://api.binance.com/api/v3/ticker/24hr?symbol=BTCUSDT" to """
                {
                    "symbol":"BTCUSDT",
                    "lastPrice":"61234.56",
                    "quoteVolume":"25000000000",
                    "highPrice":"62000",
                    "lowPrice":"60000",
                    "priceChangePercent":"1.25"
                }
            """.trimIndent(),
            "https://api.binance.com/api/v3/klines?symbol=BTCUSDT&interval=1h&limit=168" to """
                [[1,"60000","61000","59000","60500"],[2,"60500","61500","60000","61234.56"]]
            """.trimIndent(),
        )
        val client = SatraMarketDataClient(transport)

        val quote = client.getBinanceUsdtMarketQuote("btc", "Bitcoin")

        assertEquals(BigDecimal("61234.56"), quote.price)
        assertEquals(BigDecimal("25000000000"), quote.volume24h)
        assertEquals(BigDecimal("62000"), quote.high24h)
        assertEquals(BigDecimal("60000"), quote.low24h)
        assertEquals(BigDecimal("1.25"), quote.priceChange24hPercent)
        assertEquals("[60500,61234.56]", quote.chart7dJson)
        assertEquals(SatraMarketDataClient.BINANCE_MARKET_PROVIDER, quote.provider)
    }

    @Test
    fun okxMarketFallbackParsesStatsAndCandles() {
        val transport = RecordingTransport(
            "https://www.okx.com/api/v5/market/ticker?instId=ETH-USDT" to """
                {"code":"0","data":[{"last":"3000","open24h":"2900","volCcy24h":"10000000","high24h":"3050","low24h":"2850"}]}
            """.trimIndent(),
            "https://www.okx.com/api/v5/market/candles?instId=ETH-USDT&bar=1H&limit=168" to """
                {"code":"0","data":[["2","0","0","0","3000"],["1","0","0","0","2950"]]}
            """.trimIndent(),
        )
        val client = SatraMarketDataClient(transport)

        val quote = client.getOkxUsdtMarketQuote("eth", "Ether")

        assertEquals(BigDecimal("3000"), quote.price)
        assertEquals(BigDecimal("10000000"), quote.volume24h)
        assertEquals(BigDecimal("3050"), quote.high24h)
        assertEquals(BigDecimal("2850"), quote.low24h)
        assertEquals(BigDecimal("3.45"), quote.priceChange24hPercent?.setScale(2, RoundingMode.HALF_UP))
        assertEquals("[2950,3000]", quote.chart7dJson)
        assertEquals(SatraMarketDataClient.OKX_MARKET_PROVIDER, quote.provider)
    }

    @Test
    fun kuCoinMarketFallbackParsesStatsAndCandles() {
        val transport = RecordingTransport(
            "https://api.kucoin.com/api/v1/market/stats?symbol=SOL-USDT" to """
                {"code":"200000","data":{"last":"150.5","volValue":"5000000","high":"155","low":"145","changeRate":"0.02"}}
            """.trimIndent(),
            "https://api.kucoin.com/api/v1/market/candles?type=1hour&symbol=SOL-USDT&startAt=995200&endAt=1600000" to """
                {"code":"200000","data":[["2","0","151"],["1","0","150.5"]]}
            """.trimIndent(),
        )
        val client = SatraMarketDataClient(transport)

        val quote = client.getKuCoinUsdtMarketQuote("sol", "Solana", nowSeconds = 1_600_000)

        assertEquals(BigDecimal("150.5"), quote.price)
        assertEquals(BigDecimal("5000000"), quote.volume24h)
        assertEquals(BigDecimal("155"), quote.high24h)
        assertEquals(BigDecimal("145"), quote.low24h)
        assertEquals(0, BigDecimal("2.00").compareTo(quote.priceChange24hPercent))
        assertEquals("[150.5,151]", quote.chart7dJson)
        assertEquals(SatraMarketDataClient.KUCOIN_MARKET_PROVIDER, quote.provider)
    }

    @Test
    fun localCryptoPriceCombinesCoinbaseAndFxRates() {
        val transport = RecordingTransport(
            "https://api.exchange.coinbase.com/products/ETH-USD/ticker" to """
                {"price":"3000"}
            """.trimIndent(),
            "https://api.frankfurter.dev/v1/latest?base=USD&symbols=JPY" to """
                {"base":"USD","rates":{"JPY":"160.5"}}
            """.trimIndent(),
        )
        val client = SatraMarketDataClient(transport)

        val quote = client.getLocalCryptoPrice("eth", "jpy")

        assertEquals("ETH", quote.assetSymbol)
        assertEquals("JPY", quote.quoteCurrency)
        assertEquals(BigDecimal("481500.0"), quote.price)
        assertEquals("coinbase-exchange-public+frankfurter-public", quote.provider)
    }

    @Test
    fun usdFxRateDoesNotCallNetwork() {
        val transport = RecordingTransport()
        val client = SatraMarketDataClient(transport)

        val quote = client.getUsdFxRate("USD")

        assertEquals(BigDecimal.ONE, quote.rate)
        assertEquals(emptyList<String>(), transport.urls)
    }
}

private class RecordingTransport(
    vararg responses: Pair<String, String>,
) : SatraHttpTransport {
    private val responses = responses.toMap()
    val urls = mutableListOf<String>()

    override fun get(url: String): String {
        urls += url
        return responses.getValue(url)
    }
}
