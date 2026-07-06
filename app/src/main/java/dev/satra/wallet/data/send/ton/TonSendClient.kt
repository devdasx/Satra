package dev.satra.wallet.data.send.ton

import dev.satra.wallet.data.sync.accountchain.AccountChainHttpClient
import dev.satra.wallet.data.sync.accountchain.AccountChainProvider
import dev.satra.wallet.data.sync.accountchain.objects
import dev.satra.wallet.data.sync.accountchain.optBigIntegerString
import dev.satra.wallet.data.sync.accountchain.urlEncode
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

internal class TonSendClient(
    private val httpClient: AccountChainHttpClient = AccountChainHttpClient(),
) {
    suspend fun seqno(
        provider: AccountChainProvider,
        address: String,
    ): Int {
        val response = runCatching {
            JSONObject(
                httpClient.get(
                    provider = provider,
                    path = "/v2/blockchain/accounts/${address.urlEncode()}/methods/seqno",
                ),
            )
        }.getOrNull() ?: return 0
        if (!response.optBoolean("success", false)) return 0
        return response.opt("decoded").toTonIntegerOrNull()?.toInt()
            ?: response.optJSONArray("stack").toTonStackIntegerOrNull()?.toInt()
            ?: 0
    }

    suspend fun nativeBalance(
        provider: AccountChainProvider,
        address: String,
    ): BigInteger {
        val response = JSONObject(
            httpClient.get(
                provider = provider,
                path = "/v2/accounts/${address.urlEncode()}",
            ),
        )
        return response.optBigIntegerString("balance")?.toBigIntegerOrNull() ?: BigInteger.ZERO
    }

    suspend fun jettonBalance(
        provider: AccountChainProvider,
        ownerAddress: String,
        jettonMasterAddress: String,
    ): TonJettonBalance {
        val response = JSONObject(
            httpClient.get(
                provider = provider,
                path = "/v2/accounts/${ownerAddress.urlEncode()}/jettons/${jettonMasterAddress.urlEncode()}",
            ),
        )
        val walletAddress = response.optJSONObject("wallet_address")
            ?.optString("address")
            ?.takeIf(String::isNotBlank)
            ?: error("TON Jetton wallet address missing.")
        return TonJettonBalance(
            balanceRaw = response.optBigIntegerString("balance")?.toBigIntegerOrNull() ?: BigInteger.ZERO,
            walletAddress = walletAddress,
        )
    }

    suspend fun emulateFee(
        provider: AccountChainProvider,
        bocBase64: String,
    ): TonEmulationResult? =
        runCatching {
            val response = JSONObject(
                httpClient.post(
                    provider = provider,
                    path = "/v2/wallet/emulate",
                    body = JSONObject().put("boc", bocBase64),
                ),
            )
            val trace = response.optJSONObject("trace")
            val fee = trace?.sumTonTraceFees() ?: BigInteger.ZERO
            val risk = response.optJSONObject("risk")
            val loss = listOfNotNull(
                risk?.optBigIntegerString("gram")?.toBigIntegerOrNull(),
                risk?.optBigIntegerString("ton")?.toBigIntegerOrNull(),
                response.optJSONObject("event")?.optBigIntegerString("extra")?.toBigIntegerOrNull()?.abs(),
            ).maxOrNull()
            TonEmulationResult(
                feeRaw = fee,
                nativeLossRaw = loss ?: fee,
            )
        }.getOrNull()

    suspend fun broadcast(
        provider: AccountChainProvider,
        bocBase64: String,
        localMessageHash: String,
    ): String {
        httpClient.post(
            provider = provider,
            path = "/v2/blockchain/message",
            body = JSONObject().put("boc", bocBase64),
        )
        return localMessageHash
    }

    private fun JSONObject.sumTonTraceFees(): BigInteger {
        val self = optJSONObject("transaction")
            ?.optBigIntegerString("total_fees")
            ?.toBigIntegerOrNull()
            ?: BigInteger.ZERO
        val children = optJSONArray("children")
            ?.objects()
            .orEmpty()
            .fold(BigInteger.ZERO) { acc, child -> acc + child.sumTonTraceFees() }
        return self + children
    }
}

internal data class TonJettonBalance(
    val balanceRaw: BigInteger,
    val walletAddress: String,
)

internal data class TonEmulationResult(
    val feeRaw: BigInteger,
    val nativeLossRaw: BigInteger,
)

private fun Any?.toTonIntegerOrNull(): BigInteger? =
    when (this) {
        null, JSONObject.NULL -> null
        is BigInteger -> this
        is Number -> BigInteger.valueOf(toLong())
        is String -> trim().toBigIntegerOrNull()
        is JSONObject -> {
            listOf("seqno", "value", "num", "number", "int", "decoded")
                .firstNotNullOfOrNull { key -> opt(key).toTonIntegerOrNull() }
        }
        is JSONArray -> toTonStackIntegerOrNull()
        else -> null
    }

private fun JSONArray?.toTonStackIntegerOrNull(): BigInteger? {
    if (this == null) return null
    for (index in 0 until length()) {
        opt(index).toTonIntegerOrNull()?.let { return it }
    }
    return null
}
