package dev.satra.wallet.data.pricing

import dev.satra.wallet.data.assets.SupportedAssetCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SatraPriceSyncServiceTest {
    @Test
    fun coinGeckoCatalogCoversEverySupportedSymbol() {
        assertEquals(
            SupportedAssetCatalog.assets.map { it.priceSymbol }.toSet(),
            SatraAssetPriceCatalog.coinGeckoIdsBySymbol.keys,
        )
    }

    @Test
    fun syncUsesCoinbaseFirstAndCoinGeckoForRemainingSymbols() = runBlocking {
        val transport = RecordingPriceTransport(
            "https://api.exchange.coinbase.com/products" to """
                [
                    {"base_currency":"BTC","quote_currency":"USD","status":"online","trading_disabled":false}
                ]
            """.trimIndent(),
            "https://api.exchange.coinbase.com/products/BTC-USD/ticker" to """
                {"price":"50000"}
            """.trimIndent(),
            "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,usd-coin&vs_currencies=usd&precision=full" to """
                {"bitcoin":{"usd":49000},"usd-coin":{"usd":0.99}}
            """.trimIndent(),
            "https://api.frankfurter.dev/v1/latest?base=USD&symbols=EUR" to """
                {"base":"USD","rates":{"EUR":"0.9"}}
            """.trimIndent(),
        )
        val service = SatraPriceSyncService(
            marketDataClient = SatraMarketDataClient(transport),
        )
        val assets = listOf(
            SupportedAssetCatalog.assets.first { it.assetId == "bitcoin:btc" },
            SupportedAssetCatalog.assets.first { it.assetId == "ethereum:usdc" },
        )

        val result = service.syncSupportedAssetPrices(
            localCurrencyCode = "EUR",
            nowMillis = 1234L,
            assets = assets,
        )

        val btc = result.prices.first { it.asset.assetId == "bitcoin:btc" }
        val usdc = result.prices.first { it.asset.assetId == "ethereum:usdc" }
        assertEquals(BigDecimal("50000"), btc.usdPrice)
        assertEquals(BigDecimal("45000.0"), btc.localPrice)
        assertEquals("coinbase-exchange-public+frankfurter-public", btc.provider)
        assertEquals(BigDecimal("0.99"), usdc.usdPrice)
        assertEquals(BigDecimal("0.891"), usdc.localPrice)
        assertEquals("coingecko-simple-public+frankfurter-public", usdc.provider)
        assertEquals(setOf("BTC"), result.coinbaseSymbols)
        assertEquals(setOf("USDC"), result.fallbackSymbols)
        assertTrue(result.failedSymbols.isEmpty())
    }

    @Test
    fun syncEmitsPartialPriceResultsBeforeFinalResult() = runBlocking {
        val transport = RecordingPriceTransport(
            "https://api.exchange.coinbase.com/products" to """
                [
                    {"base_currency":"BTC","quote_currency":"USD","status":"online","trading_disabled":false}
                ]
            """.trimIndent(),
            "https://api.exchange.coinbase.com/products/BTC-USD/ticker" to """
                {"price":"50000"}
            """.trimIndent(),
            "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,usd-coin&vs_currencies=usd&precision=full" to """
                {"bitcoin":{"usd":49000},"usd-coin":{"usd":0.99}}
            """.trimIndent(),
            "https://api.frankfurter.dev/v1/latest?base=USD&symbols=EUR" to """
                {"base":"USD","rates":{"EUR":"0.9"}}
            """.trimIndent(),
        )
        val service = SatraPriceSyncService(
            marketDataClient = SatraMarketDataClient(transport),
        )
        val assets = listOf(
            SupportedAssetCatalog.assets.first { it.assetId == "bitcoin:btc" },
            SupportedAssetCatalog.assets.first { it.assetId == "ethereum:usdc" },
        )
        val partials = mutableListOf<SatraPriceSyncResult>()

        val result = service.syncSupportedAssetPrices(
            localCurrencyCode = "EUR",
            nowMillis = 1234L,
            assets = assets,
            onPartialResult = { partial -> partials += partial },
        )

        assertTrue(partials.size >= 2)
        assertEquals(BigDecimal("49000"), partials.first().prices.first { it.asset.assetId == "bitcoin:btc" }.usdPrice)
        assertEquals(BigDecimal("50000"), partials.last().prices.first { it.asset.assetId == "bitcoin:btc" }.usdPrice)
        assertEquals(result, partials.last())
    }
}

private class RecordingPriceTransport(
    vararg responses: Pair<String, String>,
) : SatraHttpTransport {
    private val responses = responses.toMap()

    override fun get(url: String): String =
        responses.getValue(url)
}
