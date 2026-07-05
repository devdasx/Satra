package dev.satra.wallet.data.sync.solana

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

interface SolanaJsonRpcTransport {
    suspend fun postJson(
        url: String,
        body: String,
        timeoutMillis: Int,
    ): String
}

class HttpUrlConnectionSolanaJsonRpcTransport : SolanaJsonRpcTransport {
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

interface SolanaRpcClient {
    suspend fun genesisHash(): SolanaRpcCallResult<String>
    suspend fun slot(): SolanaRpcCallResult<Long>
    suspend fun nativeBalance(address: String): SolanaRpcCallResult<BigInteger>
    suspend fun tokenAccountsByOwner(
        ownerAddress: String,
        programId: String,
    ): SolanaRpcCallResult<List<SolanaTokenAccount>>

    suspend fun signaturesForAddress(
        address: String,
        limit: Int,
        beforeSignature: String? = null,
    ): SolanaRpcCallResult<List<SolanaSignatureInfo>>

    suspend fun parsedTransaction(signature: String): SolanaRpcCallResult<JSONObject?>
    suspend fun blockTime(slot: Long): SolanaRpcCallResult<Long?>
    suspend fun tokenLargestAccounts(mint: String): SolanaRpcCallResult<JSONArray>
    suspend fun parsedAccountInfo(address: String): SolanaRpcCallResult<JSONObject?>
    suspend fun latestBlockhash(): SolanaRpcCallResult<String>
    suspend fun sendTransaction(base64Transaction: String): SolanaRpcCallResult<String>
}

class SolanaJsonRpcClient(
    private val config: SolanaNetworkConfig,
    private val transport: SolanaJsonRpcTransport = HttpUrlConnectionSolanaJsonRpcTransport(),
    private val timeoutMillis: Int = 10_000,
    private val maxAttemptsPerProvider: Int = 2,
) : SolanaRpcClient {
    private val requestIds = AtomicLong(1)
    private val providerMutex = Mutex()
    private var activeProvider: VerifiedSolanaProvider? = null

    override suspend fun genesisHash(): SolanaRpcCallResult<String> =
        callVerified("getGenesisHash", JSONArray()) { result ->
            result as String
        }

    override suspend fun slot(): SolanaRpcCallResult<Long> =
        callVerified("getSlot", JSONArray()) { result ->
            (result as Number).toLong()
        }

    override suspend fun nativeBalance(address: String): SolanaRpcCallResult<BigInteger> =
        callVerified("getBalance", JSONArray().put(address)) { result ->
            val value = (result as JSONObject).getLong("value")
            BigInteger.valueOf(value)
        }

    override suspend fun tokenAccountsByOwner(
        ownerAddress: String,
        programId: String,
    ): SolanaRpcCallResult<List<SolanaTokenAccount>> =
        callVerified(
            method = "getTokenAccountsByOwner",
            params = JSONArray()
                .put(ownerAddress)
                .put(JSONObject().put("programId", programId))
                .put(JSONObject().put("encoding", "jsonParsed")),
        ) { result ->
            val value = (result as JSONObject).optJSONArray("value") ?: JSONArray()
            buildList {
                for (index in 0 until value.length()) {
                    value.optJSONObject(index)?.toTokenAccountOrNull(programId)?.let(::add)
                }
            }
        }

    override suspend fun signaturesForAddress(
        address: String,
        limit: Int,
        beforeSignature: String?,
    ): SolanaRpcCallResult<List<SolanaSignatureInfo>> {
        val configObject = JSONObject().put("limit", limit.coerceIn(1, MAX_SIGNATURE_LIMIT))
        if (!beforeSignature.isNullOrBlank()) {
            configObject.put("before", beforeSignature)
        }
        return callVerified(
            method = "getSignaturesForAddress",
            params = JSONArray().put(address).put(configObject),
        ) { result ->
            val items = result as JSONArray
            buildList {
                for (index in 0 until items.length()) {
                    items.optJSONObject(index)?.toSignatureInfoOrNull()?.let(::add)
                }
            }
        }
    }

    override suspend fun parsedTransaction(signature: String): SolanaRpcCallResult<JSONObject?> =
        callVerified(
            method = "getTransaction",
            params = JSONArray()
                .put(signature)
                .put(
                    JSONObject()
                        .put("encoding", "jsonParsed")
                        .put("maxSupportedTransactionVersion", 0)
                        .put("commitment", "confirmed"),
                ),
        ) { result ->
            result.takeUnless { it == JSONObject.NULL } as? JSONObject
        }

    override suspend fun blockTime(slot: Long): SolanaRpcCallResult<Long?> =
        callVerified(
            method = "getBlockTime",
            params = JSONArray().put(slot),
        ) { result ->
            (result as? Number)?.toLong()
        }

    override suspend fun tokenLargestAccounts(mint: String): SolanaRpcCallResult<JSONArray> =
        callVerified("getTokenLargestAccounts", JSONArray().put(mint)) { result ->
            (result as JSONObject).optJSONArray("value") ?: JSONArray()
        }

    override suspend fun parsedAccountInfo(address: String): SolanaRpcCallResult<JSONObject?> =
        callVerified(
            method = "getParsedAccountInfo",
            params = JSONArray()
                .put(address)
                .put(JSONObject().put("encoding", "jsonParsed")),
        ) { result ->
            val value = (result as JSONObject).opt("value")
            value.takeUnless { it == JSONObject.NULL } as? JSONObject
        }

    override suspend fun latestBlockhash(): SolanaRpcCallResult<String> =
        callVerified(
            method = "getLatestBlockhash",
            params = JSONArray()
                .put(JSONObject().put("commitment", "confirmed")),
        ) { result ->
            (result as JSONObject)
                .optJSONObject("value")
                ?.optString("blockhash")
                ?.takeIf(String::isNotBlank)
                ?: error("Missing Solana latest blockhash.")
        }

    override suspend fun sendTransaction(base64Transaction: String): SolanaRpcCallResult<String> =
        callVerified(
            method = "sendTransaction",
            params = JSONArray()
                .put(base64Transaction)
                .put(
                    JSONObject()
                        .put("encoding", "base64")
                        .put("skipPreflight", false)
                        .put("preflightCommitment", "confirmed")
                        .put("maxRetries", 5),
                ),
        ) { result ->
            result as String
        }

    private suspend fun <T> callVerified(
        method: String,
        params: JSONArray,
        parser: (Any) -> T,
    ): SolanaRpcCallResult<T> {
        val failures = mutableListOf<String>()
        val verifiedProvider = verifiedProvider(failures)

        try {
            val value = callProvider(verifiedProvider.provider, method, params)
            return SolanaRpcCallResult(
                value = parser(value),
                provider = verifiedProvider.provider,
                slot = verifiedProvider.slot,
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

    private suspend fun verifiedProvider(failures: MutableList<String>): VerifiedSolanaProvider =
        providerMutex.withLock {
            activeProvider ?: selectVerifiedProvider(failures).also { activeProvider = it }
        }

    private suspend fun selectVerifiedProvider(
        failures: MutableList<String>,
    ): VerifiedSolanaProvider {
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
        error("All Solana RPC providers failed: ${failures.joinToString(" | ")}")
    }

    private suspend fun <T> callVerifiedWithoutCache(
        method: String,
        params: JSONArray,
        parser: (Any) -> T,
        failures: MutableList<String>,
    ): SolanaRpcCallResult<T> {
        config.providers.forEach { provider ->
            repeat(maxAttemptsPerProvider) { attempt ->
                try {
                    val verifiedProvider = provider.verify()
                    val value = callProvider(provider, method, params)
                    providerMutex.withLock {
                        activeProvider = verifiedProvider
                    }
                    return SolanaRpcCallResult(
                        value = parser(value),
                        provider = provider,
                        slot = verifiedProvider.slot,
                    )
                } catch (error: Throwable) {
                    failures += "${provider.name} attempt ${attempt + 1}: ${error.message}"
                    if (attempt + 1 < maxAttemptsPerProvider) {
                        delay((attempt + 1) * BACKOFF_STEP_MILLIS)
                    }
                }
            }
        }
        error("All Solana RPC providers failed: ${failures.joinToString(" | ")}")
    }

    private suspend fun SolanaRpcProvider.verify(): VerifiedSolanaProvider {
        val genesisHash = callProvider(
            provider = this,
            method = "getGenesisHash",
            params = JSONArray(),
        ) as String
        if (genesisHash != SolanaProviderRegistry.MAINNET_GENESIS_HASH) {
            error("Invalid Solana genesis hash $genesisHash")
        }

        val slot = (callProvider(
            provider = this,
            method = "getSlot",
            params = JSONArray(),
        ) as Number).toLong()
        if (slot <= 0L) {
            error("Stale or empty slot: $slot")
        }

        return VerifiedSolanaProvider(
            provider = this,
            slot = slot,
        )
    }

    private suspend fun clearActiveProviderIfMatches(provider: SolanaRpcProvider) {
        providerMutex.withLock {
            if (activeProvider?.provider == provider) {
                activeProvider = null
            }
        }
    }

    private suspend fun callProvider(
        provider: SolanaRpcProvider,
        method: String,
        params: JSONArray,
    ): Any {
        val id = requestIds.getAndIncrement()
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
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
        response.optJSONObject("error")?.let { rpcError ->
            error("RPC error ${rpcError.optInt("code")}: ${rpcError.optString("message")}")
        }
        return response.opt("result") ?: error("Missing RPC result for $method")
    }

    private companion object {
        const val MAX_SIGNATURE_LIMIT = 1_000
        const val BACKOFF_STEP_MILLIS = 250L
    }
}

data class SolanaSignatureInfo(
    val signature: String,
    val slot: Long,
    val blockTimeSeconds: Long?,
    val memo: String?,
    val err: Any?,
)

private data class VerifiedSolanaProvider(
    val provider: SolanaRpcProvider,
    val slot: Long,
)

private fun JSONObject.toTokenAccountOrNull(programId: String): SolanaTokenAccount? {
    val address = optString("pubkey").takeIf(String::isNotBlank) ?: return null
    val parsed = optJSONObject("account")
        ?.optJSONObject("data")
        ?.optJSONObject("parsed")
        ?.optJSONObject("info") ?: return null
    val amount = parsed
        .optJSONObject("tokenAmount")
        ?.optString("amount")
        ?.takeIf(String::isNotBlank)
        ?.toBigIntegerOrZero() ?: return null
    return SolanaTokenAccount(
        address = address,
        owner = parsed.optString("owner"),
        mint = parsed.optString("mint"),
        amountRaw = amount,
        decimals = parsed.optJSONObject("tokenAmount")?.optInt("decimals") ?: 0,
        programId = programId,
    )
}

private fun JSONObject.toSignatureInfoOrNull(): SolanaSignatureInfo? {
    val signature = optString("signature").takeIf(String::isNotBlank) ?: return null
    return SolanaSignatureInfo(
        signature = signature,
        slot = optLong("slot"),
        blockTimeSeconds = optLongOrNull("blockTime"),
        memo = optString("memo").takeIf(String::isNotBlank),
        err = opt("err").takeUnless { it == JSONObject.NULL },
    )
}

internal fun JSONObject.optLongOrNull(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    return optLong(name)
}
