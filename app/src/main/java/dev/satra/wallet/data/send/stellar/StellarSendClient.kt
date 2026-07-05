package dev.satra.wallet.data.send.stellar

import dev.satra.wallet.data.sync.accountchain.AccountChainProvider
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal class StellarSendClient(
    private val timeoutMillis: Int = 12_000,
) {
    suspend fun accountSequence(provider: AccountChainProvider, address: String): Long {
        val response = JSONObject(request(provider, "GET", "/accounts/$address", null))
        return response.optString("sequence").toLongOrNull()
            ?: error("Stellar account sequence missing.")
    }

    suspend fun accountExists(provider: AccountChainProvider, address: String): Boolean =
        runCatching { request(provider, "GET", "/accounts/$address", null) }.isSuccess

    suspend fun submitTransaction(
        provider: AccountChainProvider,
        envelopeBase64: String,
    ): String {
        val response = JSONObject(
            request(
                provider = provider,
                method = "POST",
                path = "/transactions",
                body = "tx=${URLEncoder.encode(envelopeBase64, "UTF-8")}",
            ),
        )
        return response.optString("hash").takeIf(String::isNotBlank)
            ?: error("Stellar submit response missing hash.")
    }

    private suspend fun request(
        provider: AccountChainProvider,
        method: String,
        path: String,
        body: String?,
    ): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = "${provider.baseUrl.trimEnd('/')}/${path.trimStart('/')}"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Satra-Android/0.1")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
            }
            try {
                if (body != null) {
                    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer -> writer.write(body) }
                }
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
        const val MAX_ERROR_LENGTH = 280
    }
}
