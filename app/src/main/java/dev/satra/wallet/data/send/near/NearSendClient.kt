package dev.satra.wallet.data.send.near

import dev.satra.wallet.data.send.crypto.SatraSigningCrypto
import dev.satra.wallet.data.sync.accountchain.AccountChainHttpClient
import dev.satra.wallet.data.sync.accountchain.AccountChainProvider
import org.json.JSONArray
import org.json.JSONObject

internal class NearSendClient(
    private val httpClient: AccountChainHttpClient = AccountChainHttpClient(),
) {
    suspend fun accessKey(
        provider: AccountChainProvider,
        accountId: String,
        publicKey: String,
    ): NearAccessKey {
        val response = rpc(
            provider = provider,
            method = "query",
            params = JSONObject()
                .put("request_type", "view_access_key")
                .put("finality", "final")
                .put("account_id", accountId)
                .put("public_key", publicKey),
        )
        return NearAccessKey(
            nonce = response.getLong("nonce").toULong() + 1UL,
            blockHash = SatraSigningCrypto.base58Decode(response.getString("block_hash")),
        )
    }

    suspend fun broadcast(
        provider: AccountChainProvider,
        signedTransactionBase64: String,
    ): String {
        val response = rpc(
            provider = provider,
            method = "broadcast_tx_commit",
            params = JSONArray().put(signedTransactionBase64),
        )
        return response.optJSONObject("transaction")?.optString("hash")?.takeIf(String::isNotBlank)
            ?: response.optString("hash").takeIf(String::isNotBlank)
            ?: error("NEAR broadcast response missing hash.")
    }

    private suspend fun rpc(
        provider: AccountChainProvider,
        method: String,
        params: Any,
    ): JSONObject {
        val response = JSONObject(
            httpClient.post(
                provider = provider,
                path = "",
                body = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", "satra")
                    .put("method", method)
                    .put("params", params),
            ),
        )
        response.optJSONObject("error")?.let { error ->
            error("NEAR RPC error ${error.optInt("code")}: ${error.optString("message")}")
        }
        return response.optJSONObject("result") ?: error("NEAR RPC response missing result.")
    }
}

internal data class NearAccessKey(
    val nonce: ULong,
    val blockHash: ByteArray,
)
