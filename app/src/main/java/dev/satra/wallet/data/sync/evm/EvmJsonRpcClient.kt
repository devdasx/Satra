package dev.satra.wallet.data.sync.evm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

interface EvmJsonRpcTransport {
    suspend fun postJson(
        url: String,
        body: String,
        timeoutMillis: Int,
    ): String
}

class HttpUrlConnectionEvmJsonRpcTransport : EvmJsonRpcTransport {
    override suspend fun postJson(
        url: String,
        body: String,
        timeoutMillis: Int,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Satra-Android/0.1")
        }

        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
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
        const val MAX_ERROR_LENGTH = 240
    }
}

class EvmJsonRpcClient(
    private val config: EvmNetworkConfig,
    private val transport: EvmJsonRpcTransport = HttpUrlConnectionEvmJsonRpcTransport(),
    private val timeoutMillis: Int = 8_000,
    private val maxAttemptsPerProvider: Int = 2,
) {
    private val requestIds = AtomicLong(1)

    suspend fun chainId(): EvmRpcCallResult<Long> =
        callVerified("eth_chainId", JSONArray()) { result ->
            EvmAbi.hexToLong(result as String)
        }

    suspend fun blockNumber(): EvmRpcCallResult<Long> =
        callVerified("eth_blockNumber", JSONArray()) { result ->
            EvmAbi.hexToLong(result as String)
        }

    suspend fun nativeBalance(address: String): EvmRpcCallResult<BigInteger> {
        val params = JSONArray().apply {
            put("0x${EvmAbi.normalizeAddress(address)}")
            put("latest")
        }
        return callVerified("eth_getBalance", params) { result ->
            EvmAbi.decodeUint256(result as String)
        }
    }

    suspend fun erc20Balance(
        contractAddress: String,
        ownerAddress: String,
    ): EvmRpcCallResult<BigInteger> {
        val params = JSONArray().apply {
            put(
                JSONObject()
                    .put("to", "0x${EvmAbi.normalizeAddress(contractAddress)}")
                    .put("data", EvmAbi.balanceOfCallData(ownerAddress)),
            )
            put("latest")
        }
        return callVerified("eth_call", params) { result ->
            EvmAbi.decodeUint256(result as String)
        }
    }

    suspend fun getLogs(filter: JSONObject): EvmRpcCallResult<JSONArray> {
        val params = JSONArray().put(filter)
        return callVerified("eth_getLogs", params) { result ->
            result as JSONArray
        }
    }

    private suspend fun <T> callVerified(
        method: String,
        params: JSONArray,
        parser: (Any) -> T,
    ): EvmRpcCallResult<T> {
        val failures = mutableListOf<String>()

        config.providers.forEach { provider ->
            repeat(maxAttemptsPerProvider) { attempt ->
                try {
                    val chainId = callProvider(
                        provider = provider,
                        method = "eth_chainId",
                        params = JSONArray(),
                    ) as String
                    val parsedChainId = EvmAbi.hexToLong(chainId)
                    if (parsedChainId != config.chainId) {
                        error("Invalid chain ID $parsedChainId, expected ${config.chainId}")
                    }

                    val latestBlock = callProvider(
                        provider = provider,
                        method = "eth_blockNumber",
                        params = JSONArray(),
                    ) as String
                    val parsedBlock = EvmAbi.hexToLong(latestBlock)
                    if (parsedBlock <= 0L) {
                        error("Stale or empty block height: $parsedBlock")
                    }

                    val value = callProvider(provider, method, params)
                    return EvmRpcCallResult(
                        value = parser(value),
                        provider = provider,
                        blockNumber = parsedBlock,
                    )
                } catch (error: Throwable) {
                    failures += "${provider.name} attempt ${attempt + 1}: ${error.message}"
                    if (attempt + 1 < maxAttemptsPerProvider) {
                        delay((attempt + 1) * BACKOFF_STEP_MILLIS)
                    }
                }
            }
        }

        error("All EVM RPC providers failed for ${config.networkId}: ${failures.joinToString(" | ")}")
    }

    private suspend fun callProvider(
        provider: EvmProvider,
        method: String,
        params: JSONArray,
    ): Any {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", requestIds.getAndIncrement())
            .put("method", method)
            .put("params", params)
            .toString()

        val response = JSONObject(
            transport.postJson(
                url = provider.rpcUrl,
                body = body,
                timeoutMillis = timeoutMillis,
            ),
        )
        if (response.has("error") && !response.isNull("error")) {
            error(response.get("error").toString())
        }
        if (!response.has("result")) {
            error("Malformed JSON-RPC response: missing result")
        }
        return response.get("result")
    }

    private companion object {
        const val BACKOFF_STEP_MILLIS = 250L
    }
}
