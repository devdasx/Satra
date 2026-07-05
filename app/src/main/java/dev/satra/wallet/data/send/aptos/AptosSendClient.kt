package dev.satra.wallet.data.send.aptos

import dev.satra.wallet.data.sync.accountchain.AccountChainHttpClient
import dev.satra.wallet.data.sync.accountchain.AccountChainProvider
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal class AptosSendClient(
    private val httpClient: AccountChainHttpClient = AccountChainHttpClient(),
    private val timeoutMillis: Int = 12_000,
) {
    suspend fun accountSequence(provider: AccountChainProvider, address: String): ULong {
        val response = JSONObject(httpClient.get(provider, "/accounts/${AptosTransactionSigner.normalizeAddress(address)}"))
        return response.optString("sequence_number")
            .takeIf(String::isNotBlank)
            ?.toULong()
            ?: error("Aptos account sequence missing.")
    }

    suspend fun gasUnitPrice(provider: AccountChainProvider): ULong {
        val response = JSONObject(httpClient.get(provider, "/estimate_gas_price"))
        return response.optString("gas_estimate")
            .takeIf(String::isNotBlank)
            ?.toULong()
            ?: DEFAULT_GAS_UNIT_PRICE
    }

    suspend fun submitSignedTransaction(
        provider: AccountChainProvider,
        signedTransactionBytes: ByteArray,
    ): String {
        val response = JSONObject(
            postBytes(
                url = "${provider.baseUrl.trimEnd('/')}/transactions",
                body = signedTransactionBytes,
            ),
        )
        return response.optString("hash").takeIf(String::isNotBlank)
            ?: error("Aptos submit response missing hash.")
    }

    private suspend fun postBytes(
        url: String,
        body: ByteArray,
    ): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/x.aptos.signed_transaction+bcs")
                setRequestProperty("User-Agent", "Satra-Android/0.1")
            }
            try {
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                val stream = if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: error("HTTP $code with empty response")
                }
                val response = BufferedReader(stream.reader(Charsets.UTF_8)).use { it.readText() }
                if (code !in 200..299) {
                    error("HTTP $code: ${response.take(MAX_ERROR_LENGTH)}")
                }
                response
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val DEFAULT_GAS_UNIT_PRICE = 100UL
        const val MAX_ERROR_LENGTH = 280
    }
}
