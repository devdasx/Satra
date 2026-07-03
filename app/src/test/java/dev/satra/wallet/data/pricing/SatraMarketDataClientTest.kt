package dev.satra.wallet.data.pricing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

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
