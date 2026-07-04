package dev.satra.wallet.data.sync.utxo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class UtxoElectrumClient(
    private val timeoutMillis: Int = 12_000,
) {
    suspend fun serverVersion(provider: UtxoElectrumProvider): String =
        withContext(Dispatchers.IO) {
            val response = callBatchResponses(
                provider = provider,
                method = "server.version",
                params = listOf(listOf("Satra", "1.4")),
            ).single()
            response.error?.let { message -> error(message) }
            response.result.toString()
        }

    suspend fun latestBlockHeight(provider: UtxoElectrumProvider): Long =
        withContext(Dispatchers.IO) {
            val response = callBatchResponses(
                provider = provider,
                method = "blockchain.headers.subscribe",
                params = listOf(emptyList()),
            ).single()
            response.error?.let { error(it) }
            (response.result as? JSONObject)?.optLong("height")
                ?.takeIf { it > 0L }
                ?: error("Missing Electrum latest block height.")
        }

    suspend fun getBalances(
        provider: UtxoElectrumProvider,
        scriptHashes: List<String>,
    ): Map<String, UtxoScriptBalance> =
        withContext(Dispatchers.IO) {
            if (scriptHashes.isEmpty()) return@withContext emptyMap()
            val responses = callBatchResponses(
                provider = provider,
                method = "blockchain.scripthash.get_balance",
                params = scriptHashes.map { listOf(it) },
            )
            responses.mapIndexed { index, response ->
                response.error?.let { error(it) }
                val result = response.result as? JSONObject ?: JSONObject()
                scriptHashes[index] to UtxoScriptBalance(
                    confirmedSats = result.optLong("confirmed", 0L),
                    unconfirmedSats = result.optLong("unconfirmed", 0L),
                )
            }.toMap()
        }

    suspend fun getHistories(
        provider: UtxoElectrumProvider,
        scriptHashes: List<String>,
    ): Map<String, List<UtxoHistoryEntry>> =
        withContext(Dispatchers.IO) {
            if (scriptHashes.isEmpty()) return@withContext emptyMap()
            val responses = callBatchResponses(
                provider = provider,
                method = "blockchain.scripthash.get_history",
                params = scriptHashes.map { listOf(it) },
            )
            responses.mapIndexed { index, response ->
                response.error?.let { error(it) }
                val result = response.result as? JSONArray ?: JSONArray()
                scriptHashes[index] to buildList {
                    for (itemIndex in 0 until result.length()) {
                        val item = result.optJSONObject(itemIndex) ?: continue
                        val txHash = item.optString("tx_hash").takeIf(String::isNotBlank) ?: continue
                        add(
                            UtxoHistoryEntry(
                                transactionHash = txHash,
                                height = item.optLong("height", 0L),
                            ),
                        )
                    }
                }
            }.toMap()
        }

    suspend fun listUnspent(
        provider: UtxoElectrumProvider,
        watchedScripts: List<UtxoWatchedScript>,
    ): Map<String, List<UtxoUnspentOutput>> =
        withContext(Dispatchers.IO) {
            if (watchedScripts.isEmpty()) return@withContext emptyMap()
            val responses = callBatchResponses(
                provider = provider,
                method = "blockchain.scripthash.listunspent",
                params = watchedScripts.map { listOf(it.scriptHash) },
            )
            responses.mapIndexed { index, response ->
                response.error?.let { error(it) }
                val script = watchedScripts[index]
                val result = response.result as? JSONArray ?: JSONArray()
                script.scriptHash to buildList {
                    for (itemIndex in 0 until result.length()) {
                        val item = result.optJSONObject(itemIndex) ?: continue
                        val txHash = item.optString("tx_hash").takeIf(String::isNotBlank) ?: continue
                        add(
                            UtxoUnspentOutput(
                                transactionHash = txHash,
                                outputIndex = item.optInt("tx_pos", -1),
                                valueSats = item.optLong("value", 0L),
                                height = item.optLong("height", 0L),
                                address = script.address,
                                scriptPubKeyHex = script.scriptPubKeyHex,
                                derivationPath = script.derivationPath,
                                isChange = script.isChange,
                            ),
                        )
                    }
                }
            }.toMap()
        }

    suspend fun getTransactions(
        provider: UtxoElectrumProvider,
        transactionHashes: List<String>,
    ): Map<String, UtxoVerboseTransaction> =
        withContext(Dispatchers.IO) {
            if (transactionHashes.isEmpty()) return@withContext emptyMap()
            val verboseResponses = callBatchResponses(
                provider = provider,
                method = "blockchain.transaction.get",
                params = transactionHashes.map { listOf(it, true) },
            )
            val verbose = mutableMapOf<String, UtxoVerboseTransaction>()
            val missing = mutableListOf<String>()
            verboseResponses.forEachIndexed { index, response ->
                val txHash = transactionHashes[index]
                val result = response.result
                if (response.error == null && result is JSONObject) {
                    val hex = result.optString("hex").takeIf(String::isNotBlank)
                    if (hex != null) {
                        verbose[txHash] = UtxoVerboseTransaction(
                            hex = hex,
                            blockHash = result.optString("blockhash").takeIf(String::isNotBlank),
                            confirmations = result.optInt("confirmations", -1).takeIf { it >= 0 },
                            timestampMillis = result.optLong("time", 0L)
                                .takeIf { it > 0L }
                                ?.times(1_000L)
                                ?: result.optLong("blocktime", 0L).takeIf { it > 0L }?.times(1_000L),
                        )
                    } else {
                        missing.add(txHash)
                    }
                } else {
                    missing.add(txHash)
                }
            }
            if (missing.isNotEmpty()) {
                val rawResponses = callBatchResponses(
                    provider = provider,
                    method = "blockchain.transaction.get",
                    params = missing.map { listOf(it) },
                )
                rawResponses.forEachIndexed { index, response ->
                    val txHash = missing[index]
                    if (response.error == null && response.result is String) {
                        verbose[txHash] = UtxoVerboseTransaction(
                            hex = response.result,
                            blockHash = null,
                            confirmations = null,
                            timestampMillis = null,
                        )
                    }
                }
            }
            verbose
        }

    suspend fun getBlockHeaders(
        provider: UtxoElectrumProvider,
        heights: List<Long>,
    ): Map<Long, UtxoBlockHeader> =
        withContext(Dispatchers.IO) {
            val distinctHeights = heights.distinct().filter { it > 0L }
            if (distinctHeights.isEmpty()) return@withContext emptyMap()
            val responses = callBatchResponses(
                provider = provider,
                method = "blockchain.block.header",
                params = distinctHeights.map { listOf(it) },
            )
            responses.mapIndexedNotNull { index, response ->
                response.error?.let { return@mapIndexedNotNull null }
                val headerHex = response.result as? String ?: return@mapIndexedNotNull null
                val timestampSeconds = headerTimestampSeconds(headerHex) ?: return@mapIndexedNotNull null
                val height = distinctHeights[index]
                height to UtxoBlockHeader(
                    height = height,
                    blockHash = blockHashFromHeaderHex(headerHex),
                    timestampMillis = timestampSeconds * 1_000L,
                )
            }.toMap()
        }

    private fun callBatchResponses(
        provider: UtxoElectrumProvider,
        method: String,
        params: List<List<Any>>,
    ): List<ElectrumResponse> {
        val socket = openSocket(provider)
        socket.use { activeSocket ->
            val reader = BufferedReader(InputStreamReader(activeSocket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(activeSocket.getOutputStream(), Charsets.UTF_8))
            val requests = params.map {
                val requestId = NextRequestId.incrementAndGet()
                ElectrumRequest(
                    id = requestId,
                    body = JSONObject()
                        .put("jsonrpc", "2.0")
                        .put("id", requestId)
                        .put("method", method)
                        .put("params", JSONArray(it)),
                )
            }
            requests.forEach { request ->
                writer.write(request.body.toString())
                writer.newLine()
            }
            writer.flush()

            val responsesById = mutableMapOf<Int, ElectrumResponse>()
            val pendingIds = requests.map { it.id }.toMutableSet()
            while (pendingIds.isNotEmpty()) {
                val line = reader.readLine() ?: break
                val response = JSONObject(line)
                val id = response.optInt("id")
                if (id !in pendingIds) continue
                val error = response.opt("error")?.takeIf { it != JSONObject.NULL }?.toString()
                responsesById[id] = ElectrumResponse(
                    result = response.opt("result")?.takeIf { it != JSONObject.NULL },
                    error = error,
                )
                pendingIds.remove(id)
            }
            if (pendingIds.isNotEmpty()) {
                error("Electrum provider ${provider.name} did not return ${pendingIds.size} response(s).")
            }
            return requests.map { request ->
                responsesById.getValue(request.id)
            }
        }
    }

    private fun openSocket(provider: UtxoElectrumProvider): Socket {
        val socket = if (provider.tls) {
            val tcpSocket = Socket().apply {
                connect(InetSocketAddress(provider.host, provider.port), timeoutMillis)
            }
            TrustAllSslContext.socketFactory.createSocket(
                tcpSocket,
                provider.host,
                provider.port,
                true,
            )
        } else {
            Socket().apply {
                connect(InetSocketAddress(provider.host, provider.port), timeoutMillis)
            }
        }
        socket.soTimeout = timeoutMillis
        if (socket is SSLSocket) {
            socket.startHandshake()
        }
        return socket
    }

    private data class ElectrumRequest(
        val id: Int,
        val body: JSONObject,
    )

    private data class ElectrumResponse(
        val result: Any?,
        val error: String?,
    )

    private companion object {
        val NextRequestId = AtomicInteger(0)
        val Sha256 = MessageDigest.getInstance("SHA-256")
        val TrustAllSslContext: SSLContext = SSLContext.getInstance("TLS").apply {
            init(
                null,
                arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun getAcceptedIssuers() = emptyArray<java.security.cert.X509Certificate>()
                        override fun checkClientTrusted(
                            chain: Array<java.security.cert.X509Certificate>?,
                            authType: String?,
                        ) = Unit
                        override fun checkServerTrusted(
                            chain: Array<java.security.cert.X509Certificate>?,
                            authType: String?,
                        ) = Unit
                    },
                ),
                java.security.SecureRandom(),
            )
        }

        fun headerTimestampSeconds(headerHex: String): Long? {
            val header = headerHex.hexToBytesOrNull()?.takeIf { it.size >= 80 } ?: return null
            return ((header[68].toLong() and 0xffL) or
                ((header[69].toLong() and 0xffL) shl 8) or
                ((header[70].toLong() and 0xffL) shl 16) or
                ((header[71].toLong() and 0xffL) shl 24))
                .takeIf { it > 0L }
        }

        fun blockHashFromHeaderHex(headerHex: String): String? {
            val header = headerHex.hexToBytesOrNull()?.takeIf { it.size >= 80 } ?: return null
            val firstHash = synchronized(Sha256) { Sha256.digest(header) }
            val secondHash = synchronized(Sha256) { Sha256.digest(firstHash) }
            return secondHash.reversedArray().joinToString("") { byte -> "%02x".format(byte) }
        }

        fun String.hexToBytesOrNull(): ByteArray? {
            val normalized = trim()
            if (normalized.length % 2 != 0) return null
            return runCatching {
                ByteArray(normalized.length / 2) { index ->
                    normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
                }
            }.getOrNull()
        }
    }
}
