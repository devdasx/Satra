package dev.satra.wallet.data.send.cosmos

import dev.satra.wallet.data.sync.accountchain.AccountChainHttpClient
import dev.satra.wallet.data.sync.accountchain.AccountChainProvider
import org.json.JSONObject

internal class CosmosSendClient(
    private val httpClient: AccountChainHttpClient = AccountChainHttpClient(),
) {
    suspend fun account(provider: AccountChainProvider, address: String): CosmosAccount {
        val response = JSONObject(httpClient.get(provider, "/cosmos/auth/v1beta1/accounts/$address"))
        val account = response.optJSONObject("account") ?: error("Cosmos account missing.")
        val base = account.findBaseAccount()
        return CosmosAccount(
            accountNumber = base.optString("account_number").toLongOrNull()
                ?: error("Cosmos account_number missing."),
            sequence = base.optString("sequence").toLongOrNull()
                ?: error("Cosmos sequence missing."),
        )
    }

    suspend fun broadcast(
        provider: AccountChainProvider,
        txRawBase64: String,
    ): String {
        val response = JSONObject(
            httpClient.post(
                provider = provider,
                path = "/cosmos/tx/v1beta1/txs",
                body = JSONObject()
                    .put("tx_bytes", txRawBase64)
                    .put("mode", "BROADCAST_MODE_SYNC"),
            ),
        )
        val txResponse = response.optJSONObject("tx_response") ?: response
        val code = txResponse.optInt("code", 0)
        if (code != 0) {
            error(txResponse.optString("raw_log").ifBlank { "Cosmos broadcast failed with code $code." })
        }
        return txResponse.optString("txhash").takeIf(String::isNotBlank)
            ?: error("Cosmos broadcast response missing txhash.")
    }

    private fun JSONObject.findBaseAccount(): JSONObject {
        optJSONObject("base_account")?.let { return it.findBaseAccount() }
        optJSONObject("base_vesting_account")?.let { return it.findBaseAccount() }
        return this
    }
}

internal data class CosmosAccount(
    val accountNumber: Long,
    val sequence: Long,
)
