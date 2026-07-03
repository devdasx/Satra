package dev.satra.wallet.data.sync.evm

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.util.Collections

class EvmJsonRpcClientTest {
    @Test
    fun fallsBackWhenPrimaryProviderReturnsWrongChainId() = runBlocking {
        val transport = RecordingTransport { url, body ->
            val method = JSONObject(body).getString("method")
            when {
                url == BAD_RPC_URL && method == "eth_chainId" -> rpcResult("0x2")
                url == GOOD_RPC_URL && method == "eth_chainId" -> rpcResult("0x1")
                url == GOOD_RPC_URL && method == "eth_blockNumber" -> rpcResult("0x10")
                url == GOOD_RPC_URL && method == "eth_getBalance" -> rpcResult("0xde0b6b3a7640000")
                else -> error("Unexpected request to $url with $method")
            }
        }
        val client = EvmJsonRpcClient(
            config = EvmNetworkConfig(
                networkId = "ethereum",
                chainId = 1,
                providers = listOf(
                    EvmProvider("Bad", BAD_RPC_URL),
                    EvmProvider("Good", GOOD_RPC_URL),
                ),
            ),
            transport = transport,
            maxAttemptsPerProvider = 1,
        )

        val balance = client.nativeBalance("0x1111111111111111111111111111111111111111")

        assertEquals(BigInteger("1000000000000000000"), balance.value)
        assertEquals("Good", balance.provider.name)
        assertTrue(transport.urls.first() == BAD_RPC_URL)
        assertTrue(transport.urls.contains(GOOD_RPC_URL))
    }

    @Test
    fun reusesVerifiedProviderAcrossConcurrentCalls() = runBlocking {
        val transport = RecordingTransport { _, body ->
            when (JSONObject(body).getString("method")) {
                "eth_chainId" -> rpcResult("0x1")
                "eth_blockNumber" -> rpcResult("0x20")
                "eth_getBalance" -> rpcResult("0xde0b6b3a7640000")
                "eth_call" -> rpcResult("0x0000000000000000000000000000000000000000000000000000000000000064")
                else -> error("Unexpected request: $body")
            }
        }
        val client = EvmJsonRpcClient(
            config = EvmNetworkConfig(
                networkId = "ethereum",
                chainId = 1,
                providers = listOf(EvmProvider("Good", GOOD_RPC_URL)),
            ),
            transport = transport,
            maxAttemptsPerProvider = 1,
        )

        val results = coroutineScope {
            awaitAll(
                async { client.nativeBalance("0x1111111111111111111111111111111111111111").value },
                async {
                    client.erc20Balance(
                        contractAddress = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                        ownerAddress = "0x1111111111111111111111111111111111111111",
                    ).value
                },
            )
        }

        assertEquals(BigInteger("1000000000000000000"), results[0])
        assertEquals(BigInteger("100"), results[1])
        assertEquals(1, transport.methodCount("eth_chainId"))
        assertEquals(1, transport.methodCount("eth_blockNumber"))
        assertEquals(1, transport.methodCount("eth_getBalance"))
        assertEquals(1, transport.methodCount("eth_call"))
    }

    private class RecordingTransport(
        private val responder: (String, String) -> String,
    ) : EvmJsonRpcTransport {
        val urls = Collections.synchronizedList(mutableListOf<String>())
        private val methods = Collections.synchronizedList(mutableListOf<String>())

        override suspend fun postJson(
            url: String,
            body: String,
            timeoutMillis: Int,
        ): String {
            urls += url
            methods += JSONObject(body).getString("method")
            return responder(url, body)
        }

        fun methodCount(method: String): Int =
            methods.count { it == method }
    }

    private companion object {
        const val BAD_RPC_URL = "https://bad.example"
        const val GOOD_RPC_URL = "https://good.example"

        fun rpcResult(result: String): String =
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("result", result)
                .toString()
    }
}
