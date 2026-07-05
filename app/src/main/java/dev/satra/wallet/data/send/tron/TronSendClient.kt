package dev.satra.wallet.data.send.tron

import dev.satra.wallet.data.send.crypto.SatraSigningCrypto
import dev.satra.wallet.data.send.crypto.SatraSigningCrypto.hexToBytes
import dev.satra.wallet.data.send.crypto.SatraSigningCrypto.toFixedBytes
import dev.satra.wallet.data.send.crypto.SatraSigningCrypto.toHex
import dev.satra.wallet.data.sync.accountchain.AccountChainHttpClient
import dev.satra.wallet.data.sync.accountchain.AccountChainProvider
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

internal class TronSendClient(
    private val httpClient: AccountChainHttpClient = AccountChainHttpClient(),
) {
    suspend fun createNativeTransfer(
        provider: AccountChainProvider,
        ownerAddress: String,
        recipientAddress: String,
        amountSun: BigInteger,
    ): TronUnsignedTransaction {
        validateTronAddress(ownerAddress)
        validateTronAddress(recipientAddress)
        val response = httpClient.post(
            provider = provider,
            path = "/wallet/createtransaction",
            body = JSONObject()
                .put("owner_address", ownerAddress)
                .put("to_address", recipientAddress)
                .put("amount", amountSun.toString())
                .put("visible", true),
        )
        return parseUnsignedTransaction(JSONObject(response))
    }

    suspend fun createTrc20Transfer(
        provider: AccountChainProvider,
        ownerAddress: String,
        recipientAddress: String,
        contractAddress: String,
        amountRaw: BigInteger,
        feeLimitSun: Long = DEFAULT_TRC20_FEE_LIMIT_SUN,
    ): TronUnsignedTransaction {
        validateTronAddress(ownerAddress)
        validateTronAddress(recipientAddress)
        validateTronAddress(contractAddress)
        val parameter = tronAddressSolidityWord(recipientAddress) + amountRaw.toFixedBytes(UINT256_SIZE).toHex()
        val response = httpClient.post(
            provider = provider,
            path = "/wallet/triggersmartcontract",
            body = JSONObject()
                .put("owner_address", ownerAddress)
                .put("contract_address", contractAddress)
                .put("function_selector", "transfer(address,uint256)")
                .put("parameter", parameter)
                .put("fee_limit", feeLimitSun.coerceIn(0L, MAX_FEE_LIMIT_SUN))
                .put("call_value", 0)
                .put("visible", true),
        )
        val root = JSONObject(response)
        val result = root.optJSONObject("result")
        if (result != null && !result.optBoolean("result", false)) {
            error(result.optString("message").ifBlank { "TRON trigger smart contract failed." })
        }
        val transaction = root.optJSONObject("transaction") ?: error("TRON trigger response missing transaction.")
        return parseUnsignedTransaction(transaction)
    }

    fun signTransaction(
        unsigned: TronUnsignedTransaction,
        privateKeyHex: String,
    ): TronSignedTransaction {
        val privateKey = SatraSigningCrypto.parseSecp256k1PrivateKey(privateKeyHex)
        val rawData = unsigned.rawDataHex.hexToBytes()
        val digest = SatraSigningCrypto.sha256(rawData)
        val signature = SatraSigningCrypto.secp256k1SignDigestWithRecovery(digest, privateKey)
        val signatureHex = signature.r.toFixedBytes(32).toHex() +
            signature.s.toFixedBytes(32).toHex() +
            signature.recoveryId.toString(16).padStart(2, '0')
        val signedJson = JSONObject(unsigned.transactionJson.toString())
            .put("signature", JSONArray().put(signatureHex))
        return TronSignedTransaction(
            transactionHash = unsigned.transactionHash,
            signedTransactionJson = signedJson,
            signatureHex = signatureHex,
        )
    }

    suspend fun broadcast(
        provider: AccountChainProvider,
        signed: TronSignedTransaction,
    ): String {
        val response = JSONObject(
            httpClient.post(
                provider = provider,
                path = "/wallet/broadcasttransaction",
                body = signed.signedTransactionJson,
            ),
        )
        if (!response.optBoolean("result", false)) {
            val message = response.optString("message")
                .takeIf(String::isNotBlank)
                ?: response.optString("code")
                ?: "TRON broadcast failed."
            error(message)
        }
        return response.optString("txid")
            .takeIf(String::isNotBlank)
            ?: response.optString("txID").takeIf(String::isNotBlank)
            ?: signed.transactionHash
    }

    private fun parseUnsignedTransaction(root: JSONObject): TronUnsignedTransaction {
        val rawDataHex = root.optString("raw_data_hex").takeIf(String::isNotBlank)
            ?: error("TRON transaction missing raw_data_hex.")
        val txId = root.optString("txID").takeIf(String::isNotBlank)
            ?: SatraSigningCrypto.sha256(rawDataHex.hexToBytes()).toHex()
        return TronUnsignedTransaction(
            transactionHash = txId,
            rawDataHex = rawDataHex,
            transactionJson = root,
        )
    }

    companion object {
        const val DEFAULT_NATIVE_FEE_LIMIT_SUN = 1_000_000L
        const val DEFAULT_TRC20_FEE_LIMIT_SUN = 100_000_000L
        const val MAX_FEE_LIMIT_SUN = 15_000_000_000L

        fun validateTronAddress(address: String) {
            val payload = SatraSigningCrypto.base58CheckDecode(address)
            require(payload.size == TRON_ADDRESS_SIZE && payload.first() == TRON_ADDRESS_PREFIX) {
                "Invalid TRON address."
            }
        }

        private fun tronAddressSolidityWord(address: String): String {
            val payload = SatraSigningCrypto.base58CheckDecode(address)
            require(payload.size == TRON_ADDRESS_SIZE && payload.first() == TRON_ADDRESS_PREFIX)
            return payload.copyOfRange(1, payload.size).toHex().padStart(64, '0')
        }

        private const val UINT256_SIZE = 32
        private const val TRON_ADDRESS_SIZE = 21
        private val TRON_ADDRESS_PREFIX = 0x41.toByte()
    }
}

internal data class TronUnsignedTransaction(
    val transactionHash: String,
    val rawDataHex: String,
    val transactionJson: JSONObject,
)

internal data class TronSignedTransaction(
    val transactionHash: String,
    val signedTransactionJson: JSONObject,
    val signatureHex: String,
)
