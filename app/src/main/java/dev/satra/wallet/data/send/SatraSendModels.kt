package dev.satra.wallet.data.send

import java.math.BigInteger

data class SatraSendRequest(
    val walletId: String,
    val assetId: String,
    val networkId: String,
    val assetSymbol: String,
    val assetType: String,
    val decimals: Int,
    val contractAddress: String?,
    val sourceAddress: String,
    val recipientAddress: String,
    val amountDecimal: String,
    val balanceRaw: String,
    val walletAssetMetadataJson: String = "{}",
    val privateKeyHex: String,
    val privateKeysHexByAddress: Map<String, String> = emptyMap(),
    val localCurrencyCode: String,
    val priceFiatValue: String,
)

data class SatraBroadcastResult(
    val transactionHash: String,
    val rawTransaction: String,
    val providerName: String,
    val fromAddress: String,
    val toAddress: String,
    val amountRaw: BigInteger,
    val amountDecimal: String,
    val feeRaw: BigInteger,
    val feeDecimal: String,
    val feeAssetId: String?,
    val nonce: BigInteger,
    val timestampMillis: Long,
)

sealed class SatraSendException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingWallet : SatraSendException("No active wallet is available.")
    class MissingSigningKey : SatraSendException("No local signing key is available for this network.")
    class UnsupportedNetwork(networkId: String) : SatraSendException("Signing is not available for $networkId.")
    class InvalidRecipient(cause: Throwable? = null) : SatraSendException("Invalid recipient address.", cause)
    class InvalidAmount(cause: Throwable? = null) : SatraSendException("Invalid send amount.", cause)
    class InsufficientBalance : SatraSendException("Insufficient balance.")
    class BroadcastFailed(cause: Throwable) : SatraSendException("Broadcast failed.", cause)
}
