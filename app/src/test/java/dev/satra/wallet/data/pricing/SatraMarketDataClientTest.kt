package dev.satra.wallet.data.pricing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SatraMarketDataClientTest {
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
