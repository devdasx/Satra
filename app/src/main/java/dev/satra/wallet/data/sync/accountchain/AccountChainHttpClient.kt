package dev.satra.wallet.data.sync.accountchain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

class AccountChainHttpClient(
    private val timeoutMillis: Int = 12_000,
    private val maxAttemptsPerProvider: Int = 2,
) {
    suspend fun get(provider: AccountChainProvider, path: String): String =
        requestWithRetry(provider, "GET", path, null)

    suspend fun post(provider: AccountChainProvider, path: String, body: JSONObject): String =
        requestWithRetry(provider, "POST", path, body.toString())

    suspend fun postArray(provider: AccountChainProvider, path: String, body: JSONArray): String =
        requestWithRetry(provider, "POST", path, body.toString())

    private suspend fun requestWithRetry(
        provider: AccountChainProvider,
        method: String,
        path: String,
        body: String?,
    ): String {
        var lastError: Throwable? = null
        repeat(maxAttemptsPerProvider.coerceAtLeast(1)) { attempt ->
            try {
                return request(provider.url(path), method, body)
            } catch (error: Throwable) {
                lastError = error
                if (attempt + 1 < maxAttemptsPerProvider) {
                    delay((attempt + 1) * BACKOFF_STEP_MILLIS)
                }
            }
        }
        throw lastError ?: IllegalStateException("${provider.name} failed without an error.")
    }

    private suspend fun request(
        url: String,
        method: String,
        body: String?,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Satra-Android/0.1")
            doOutput = body != null
        }

        try {
            if (body != null) {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body)
                }
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

    private fun AccountChainProvider.url(path: String): String {
        val base = baseUrl.trimEnd('/')
        if (path.isBlank()) return base
        return if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            "$base/${path.trimStart('/')}"
        }
    }

    private companion object {
        const val BACKOFF_STEP_MILLIS = 350L
        const val MAX_ERROR_LENGTH = 280
    }
}

internal fun queryString(vararg params: Pair<String, String?>): String =
    params
        .mapNotNull { (key, value) ->
            value?.let {
                "${key.urlEncode()}=${it.urlEncode()}"
            }
        }
        .joinToString("&")
        .takeIf(String::isNotBlank)
        ?.let { "?$it" }
        .orEmpty()

internal fun String.urlEncode(): String =
    URLEncoder.encode(this, "UTF-8")

internal fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).takeIf(String::isNotBlank)

internal fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) runCatching { getLong(key) }.getOrNull() else null

internal fun JSONObject.optBigIntegerString(key: String): String? =
    opt(key)
        ?.takeUnless { it == JSONObject.NULL }
        ?.toString()
        ?.takeIf(String::isNotBlank)

internal fun JSONArray.objects(): List<JSONObject> =
    buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::add)
        }
    }

internal fun String.hostOrSelf(): String =
    runCatching { URI(this).host ?: this }.getOrDefault(this)
