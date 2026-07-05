package dev.satra.wallet.data.send.sui

import dev.satra.wallet.data.send.crypto.SatraSigningCrypto
import dev.satra.wallet.data.send.crypto.SatraSigningCrypto.toHex
import dev.satra.wallet.data.sync.accountchain.AccountChainHttpClient
import dev.satra.wallet.data.sync.accountchain.AccountChainProvider
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.util.Base64

internal class SuiSendClient(
    private val httpClient: AccountChainHttpClient = AccountChainHttpClient(),
) {
    suspend fun coins(
        provider: AccountChainProvider,
        owner: String,
        coinType: String,
    ): List<SuiCoinObject> {
        val response = rpc(
            provider = provider,
            method = "suix_getCoins",
            params = JSONArray().put(owner).put(coinType),
        ) as JSONObject
        val data = response.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                add(
                    SuiCoinObject(
                        objectId = item.getString("coinObjectId"),
                        version = item.getString("version"),
                        digest = item.getString("digest"),
                        balance = BigInteger(item.optString("balance", "0")),
                    ),
                )
            }
        }
    }

    suspend fun referenceGasPrice(provider: AccountChainProvider): BigInteger =
        BigInteger(rpc(provider, "suix_getReferenceGasPrice", JSONArray()).toString())

    suspend fun buildNativePay(
        provider: AccountChainProvider,
        signer: String,
        inputCoins: List<String>,
        recipient: String,
        amountMist: BigInteger,
        gasBudgetMist: BigInteger,
    ): String =
        (rpc(
            provider = provider,
            method = "unsafe_paySui",
            params = JSONArray()
                .put(signer)
                .put(JSONArray(inputCoins))
                .put(JSONArray().put(recipient))
                .put(JSONArray().put(amountMist.toString()))
                .put(gasBudgetMist.toString()),
        ) as JSONObject).getString("txBytes")

    suspend fun buildTokenPay(
        provider: AccountChainProvider,
        signer: String,
        inputCoins: List<String>,
        recipient: String,
        amountRaw: BigInteger,
        gasObject: String,
        gasBudgetMist: BigInteger,
    ): String =
        (rpc(
            provider = provider,
            method = "unsafe_pay",
            params = JSONArray()
                .put(signer)
                .put(JSONArray(inputCoins))
                .put(JSONArray().put(recipient))
                .put(JSONArray().put(amountRaw.toString()))
                .put(gasObject)
                .put(gasBudgetMist.toString()),
        ) as JSONObject).getString("txBytes")

    fun signatureBase64(txBytesBase64: String, privateKeyHex: String, sourceAddress: String): String {
        val privateKey = SatraSigningCrypto.parseEd25519PrivateKey(privateKeyHex)
        val publicKey = SatraSigningCrypto.ed25519PublicKey(privateKey)
        val derivedAddress = "0x${SatraSigningCrypto.blake2b256(byteArrayOf(0) + publicKey).toHex()}"
        require(derivedAddress.equals(sourceAddress, ignoreCase = true)) {
            "Sui private key does not match source address."
        }
        val txBytes = Base64.getDecoder().decode(txBytesBase64)
        val digest = SatraSigningCrypto.blake2b256(SUI_TRANSACTION_INTENT + txBytes)
        val signature = SatraSigningCrypto.ed25519Sign(digest, privateKey)
        return Base64.getEncoder().encodeToString(byteArrayOf(ED25519_SCHEME_FLAG) + signature + publicKey)
    }

    suspend fun executeTransaction(
        provider: AccountChainProvider,
        txBytesBase64: String,
        signatureBase64: String,
    ): String {
        val response = rpc(
            provider = provider,
            method = "sui_executeTransactionBlock",
            params = JSONArray()
                .put(txBytesBase64)
                .put(JSONArray().put(signatureBase64))
                .put(JSONObject().put("showEffects", true))
                .put("WaitForLocalExecution"),
        ) as JSONObject
        return response.optString("digest").takeIf(String::isNotBlank)
            ?: error("Sui execute response missing digest.")
    }

    private suspend fun rpc(
        provider: AccountChainProvider,
        method: String,
        params: JSONArray,
    ): Any {
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
            error("Sui RPC error ${error.optInt("code")}: ${error.optString("message")}")
        }
        return response.opt("result") ?: error("Sui RPC response missing result.")
    }

    private companion object {
        val SUI_TRANSACTION_INTENT = byteArrayOf(0, 0, 0)
        const val ED25519_SCHEME_FLAG = 0x00.toByte()
    }
}

internal data class SuiCoinObject(
    val objectId: String,
    val version: String,
    val digest: String,
    val balance: BigInteger,
)
