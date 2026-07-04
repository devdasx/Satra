package dev.satra.wallet.data.sync.evm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val providerMutex = Mutex()
    private var activeProvider: VerifiedEvmProvider? = null

    suspend fun chainId(): EvmRpcCallResult<Long> =
        callVerified("eth_chainId", JSONArray()) { result ->
            EvmAbi.hexToLong(result as String)
        }

    suspend fun blockNumber(): EvmRpcCallResult<Long> =
        callVerified("eth_blockNumber", JSONArray()) { result ->
            EvmAbi.hexToLong(result as String)
        }

    suspend fun transactionCount(
        address: String,
        blockTag: String = "pending",
    ): EvmRpcCallResult<BigInteger> {
        val params = JSONArray().apply {
            put("0x${EvmAbi.normalizeAddress(address)}")
            put(blockTag)
        }
        return callVerified("eth_getTransactionCount", params) { result ->
            EvmAbi.decodeUint256(result as String)
        }
    }

    suspend fun gasPrice(): EvmRpcCallResult<BigInteger> =
        callVerified("eth_gasPrice", JSONArray()) { result ->
            EvmAbi.decodeUint256(result as String)
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

    suspend fun estimateGas(
        fromAddress: String,
        toAddress: String,
        value: BigInteger,
        data: String = "0x",
    ): EvmRpcCallResult<BigInteger> {
        val call = JSONObject()
            .put("from", "0x${EvmAbi.normalizeAddress(fromAddress)}")
            .put("to", "0x${EvmAbi.normalizeAddress(toAddress)}")
            .put("value", EvmAbi.quantityHex(value))
        if (data != "0x") {
            call.put("data", data)
        }
        val params = JSONArray().put(call)
        return callVerified("eth_estimateGas", params) { result ->
            EvmAbi.decodeUint256(result as String)
        }
    }

    suspend fun sendRawTransaction(rawTransaction: String): EvmRpcCallResult<String> {
        val normalized = rawTransaction.removePrefix("0x").removePrefix("0X")
        require(normalized.isNotBlank() && normalized.all(Char::isHexDigit)) {
            "Invalid raw EVM transaction."
        }
        val params = JSONArray().put("0x$normalized")
        return callVerified("eth_sendRawTransaction", params) { result ->
            result as String
        }
    }

    suspend fun getLogs(filter: JSONObject): EvmRpcCallResult<JSONArray> {
        val params = JSONArray().put(filter)
        return callVerified("eth_getLogs", params) { result ->
            result as JSONArray
        }
    }

    suspend fun blockByNumber(blockNumber: Long): EvmRpcCallResult<EvmBlockHeader> {
        val params = JSONArray()
            .put("0x${blockNumber.toString(16)}")
            .put(false)
        return callVerified("eth_getBlockByNumber", params) { result ->
            val block = result as JSONObject
            EvmBlockHeader(
                blockNumber = EvmAbi.hexToLong(block.optString("number", "0x${blockNumber.toString(16)}")),
                blockHash = block.optString("hash").takeIf(String::isNotBlank),
                timestampMillis = EvmAbi.hexToLong(block.optString("timestamp", "0x0")) * 1_000L,
            )
        }
    }

    private suspend fun <T> callVerified(
        method: String,
        params: JSONArray,
        parser: (Any) -> T,
    ): EvmRpcCallResult<T> {
        val failures = mutableListOf<String>()
        val verifiedProvider = verifiedProvider(failures)

        try {
            val value = callProvider(verifiedProvider.provider, method, params)
            return EvmRpcCallResult(
                value = parser(value),
                provider = verifiedProvider.provider,
                blockNumber = verifiedProvider.blockNumber,
            )
        } catch (error: Throwable) {
            failures += "${verifiedProvider.provider.name} cached call: ${error.message}"
            clearActiveProviderIfMatches(verifiedProvider.provider)
        }

        return callVerifiedWithoutCache(
            method = method,
            params = params,
            parser = parser,
            failures = failures,
        )
    }

    private suspend fun verifiedProvider(failures: MutableList<String>): VerifiedEvmProvider =
        providerMutex.withLock {
            activeProvider ?: selectVerifiedProvider(failures).also { activeProvider = it }
        }

    private suspend fun selectVerifiedProvider(
        failures: MutableList<String>,
    ): VerifiedEvmProvider {
        config.providers.forEach { provider ->
            repeat(maxAttemptsPerProvider) { attempt ->
                try {
                    return provider.verify()
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

    private suspend fun <T> callVerifiedWithoutCache(
        method: String,
        params: JSONArray,
        parser: (Any) -> T,
        failures: MutableList<String>,
    ): EvmRpcCallResult<T> {
        config.providers.forEach { provider ->
            repeat(maxAttemptsPerProvider) { attempt ->
                try {
                    val verifiedProvider = provider.verify()
                    val value = callProvider(provider, method, params)
                    providerMutex.withLock {
                        activeProvider = verifiedProvider
                    }
                    return EvmRpcCallResult(
                        value = parser(value),
                        provider = provider,
                        blockNumber = verifiedProvider.blockNumber,
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

    private suspend fun EvmProvider.verify(): VerifiedEvmProvider {
        val chainId = callProvider(
            provider = this,
            method = "eth_chainId",
            params = JSONArray(),
        ) as String
        val parsedChainId = EvmAbi.hexToLong(chainId)
        if (parsedChainId != config.chainId) {
            error("Invalid chain ID $parsedChainId, expected ${config.chainId}")
        }

        val latestBlock = callProvider(
            provider = this,
            method = "eth_blockNumber",
            params = JSONArray(),
        ) as String
        val parsedBlock = EvmAbi.hexToLong(latestBlock)
        if (parsedBlock <= 0L) {
            error("Stale or empty block height: $parsedBlock")
        }

        return VerifiedEvmProvider(
            provider = this,
            blockNumber = parsedBlock,
        )
    }

    private suspend fun clearActiveProviderIfMatches(provider: EvmProvider) {
        providerMutex.withLock {
            if (activeProvider?.provider == provider) {
                activeProvider = null
            }
        }
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

private data class VerifiedEvmProvider(
    val provider: EvmProvider,
    val blockNumber: Long,
)

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
