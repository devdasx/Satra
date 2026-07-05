package dev.satra.wallet.data.send.ripple

import dev.satra.wallet.data.sync.accountchain.AccountChainHttpClient
import dev.satra.wallet.data.sync.accountchain.AccountChainProvider
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

internal class RippleSendClient(
    private val httpClient: AccountChainHttpClient = AccountChainHttpClient(),
) {
    suspend fun accountInfo(provider: AccountChainProvider, address: String): RippleAccountInfo {
        val result = rpc(
            provider = provider,
            method = "account_info",
            params = JSONObject()
                .put("account", address)
                .put("ledger_index", "validated"),
        )
        val accountData = result.getJSONObject("account_data")
        return RippleAccountInfo(
            sequence = accountData.getLong("Sequence"),
            ledgerIndex = result.optLong("ledger_index", 0L),
        )
    }

    suspend fun currentLedger(provider: AccountChainProvider): Long {
        val result = rpc(provider, "ledger_current", JSONObject())
        return result.getLong("ledger_current_index")
    }

    suspend fun feeDrops(provider: AccountChainProvider): BigInteger {
        val result = rpc(provider, "fee", JSONObject())
        return result.optJSONObject("drops")
            ?.optString("open_ledger_fee")
            ?.takeIf(String::isNotBlank)
            ?.let(::BigInteger)
            ?: BigInteger.TEN
    }

    suspend fun submit(provider: AccountChainProvider, txBlobHex: String): String {
        val result = rpc(
            provider = provider,
            method = "submit",
            params = JSONObject().put("tx_blob", txBlobHex),
        )
        val engine = result.optString("engine_result")
        if (engine.isNotBlank() && engine != "tesSUCCESS") {
            error(result.optString("engine_result_message").ifBlank { "XRPL submit failed: $engine" })
        }
        return parseSubmitHash(result)
    }

    private suspend fun rpc(
        provider: AccountChainProvider,
        method: String,
        params: JSONObject,
    ): JSONObject {
        val response = JSONObject(
            httpClient.post(
                provider = provider,
                path = "",
                body = JSONObject()
                    .put("method", method)
                    .put("params", JSONArray().put(params)),
            ),
        )
        val result = response.optJSONObject("result") ?: error("XRPL RPC response missing result.")
        val status = result.optString("status")
        if (status == "error") {
            error(result.optString("error_message").ifBlank { result.optString("error") })
        }
        return result
    }

    internal companion object {
        fun parseSubmitHash(result: JSONObject): String =
            result.optJSONObject("tx_json")?.optString("hash")?.takeIf(String::isNotBlank)
                ?: result.optString("hash").takeIf(String::isNotBlank)
                ?: result.optString("tx_json").takeIf { value ->
                    value.isNotBlank() && !value.trimStart().startsWith("{")
                }
                ?: error("XRPL submit response missing hash.")
    }
}

internal data class RippleAccountInfo(
    val sequence: Long,
    val ledgerIndex: Long,
)
