package dev.satra.wallet.data.sync.evm

import dev.satra.wallet.data.assets.SupportedAsset
import dev.satra.wallet.data.db.WalletTransactionDirection
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class EvmHistorySyncServiceTest {
    @Test
    fun blockscoutHistoryNormalizesNativeAndTokenTransfers() = runBlocking {
        val http = FakeGetTransport { url ->
            when {
                url.contains("/token-transfers") -> JSONObject()
                    .put(
                        "items",
                        JSONArray().put(
                            JSONObject()
                                .put("transaction_hash", "0xtoken")
                                .put("from", JSONObject().put("hash", WALLET_ADDRESS))
                                .put("to", JSONObject().put("hash", OTHER_ADDRESS))
                                .put("total", JSONObject().put("value", "1234567"))
                                .put("token", JSONObject().put("address", USDC_CONTRACT))
                                .put("block_number", 11)
                                .put("timestamp", "2026-01-01T00:01:00Z"),
                        ),
                    )
                    .toString()

                url.contains("/transactions") -> JSONObject()
                    .put(
                        "items",
                        JSONArray().put(
                            JSONObject()
                                .put("hash", "0xnative")
                                .put("from", JSONObject().put("hash", OTHER_ADDRESS))
                                .put("to", JSONObject().put("hash", WALLET_ADDRESS))
                                .put("value", "1000000000000000000")
                                .put("block_number", 10)
                                .put("block_hash", "0xblock")
                                .put("timestamp", "2026-01-01T00:00:00Z")
                                .put("status", "ok"),
                        ),
                    )
                    .toString()

                else -> error("Unexpected URL: $url")
            }
        }
        val service = EvmHistorySyncService(
            httpGetTransport = http,
        )

        val result = service.syncHistory(
            walletId = "wallet",
            address = WALLET_ADDRESS,
            assets = listOf(ETH_ASSET, USDC_ASSET),
            latestKnownBlock = 20,
            nowMillis = 1L,
        )

        assertEquals(EvmSyncCompleteness.Complete, result.completeness)
        assertEquals(2, result.transactions.size)
        assertEquals("1", result.transactions.first { it.transactionHash == "0xnative" }.amountDecimal)
        assertEquals(WalletTransactionDirection.Incoming, result.transactions.first { it.transactionHash == "0xnative" }.direction)
        assertEquals("1.234567", result.transactions.first { it.transactionHash == "0xtoken" }.amountDecimal)
        assertEquals(WalletTransactionDirection.Outgoing, result.transactions.first { it.transactionHash == "0xtoken" }.direction)
    }

    private class FakeGetTransport(
        private val responder: (String) -> String,
    ) : EvmHttpGetTransport {
        override suspend fun get(
            url: String,
            timeoutMillis: Int,
        ): String = responder(url)
    }

    private companion object {
        const val WALLET_ADDRESS = "0x1111111111111111111111111111111111111111"
        const val OTHER_ADDRESS = "0x2222222222222222222222222222222222222222"
        const val USDC_CONTRACT = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"

        val ETH_ASSET = SupportedAsset(
            assetId = "ethereum:eth",
            symbol = "ETH",
            name = "Ether",
            networkId = "ethereum",
            assetType = "NATIVE",
            decimals = 18,
            tokenStandard = null,
            contractAddress = null,
            isStablecoin = false,
        )
        val USDC_ASSET = SupportedAsset(
            assetId = "ethereum:usdc",
            symbol = "USDC",
            name = "USD Coin",
            networkId = "ethereum",
            assetType = "TOKEN",
            decimals = 6,
            tokenStandard = "ERC-20",
            contractAddress = USDC_CONTRACT,
            isStablecoin = true,
        )
    }
}
