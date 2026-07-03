package dev.satra.wallet.data.pricing

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
)

data class FxRateQuote(
    val baseCurrency: String,
    val quoteCurrency: String,
    val rate: BigDecimal,
    val provider: String,
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
        )
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

    companion object {
        const val COINBASE_EXCHANGE_PROVIDER = "coinbase-exchange-public"
        const val FRANKFURTER_PROVIDER = "frankfurter-public"
    }
}
