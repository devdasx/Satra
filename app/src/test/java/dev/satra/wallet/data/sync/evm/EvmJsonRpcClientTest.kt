package dev.satra.wallet.data.sync.evm

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

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

    private class RecordingTransport(
        private val responder: (String, String) -> String,
    ) : EvmJsonRpcTransport {
        val urls = mutableListOf<String>()

        override suspend fun postJson(
            url: String,
            body: String,
            timeoutMillis: Int,
        ): String {
            urls += url
            return responder(url, body)
        }
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
