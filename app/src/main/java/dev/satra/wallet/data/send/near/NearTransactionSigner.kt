package dev.satra.wallet.data.send.near

import dev.satra.wallet.data.send.crypto.SatraSigningCrypto
import dev.satra.wallet.data.send.crypto.SatraSigningCrypto.toHex
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Base64

internal object NearTransactionSigner {
    fun sign(request: NearSigningRequest): NearSignedTransaction {
        val privateKey = SatraSigningCrypto.parseEd25519PrivateKey(request.privateKeyHex)
        val publicKey = SatraSigningCrypto.ed25519PublicKey(privateKey)
        if (request.sourceAddress.length == 64 && request.sourceAddress.all(Char::isHexDigit)) {
            require(publicKey.toHex() == request.sourceAddress.lowercase()) {
                "NEAR private key does not match implicit source address."
            }
        }
        val actions = if (request.tokenContract == null) {
            listOf(nearTransferAction(request.amountRaw))
        } else {
            listOf(
                nearFunctionCallAction(
                    methodName = "storage_deposit",
                    argsJson = """{"account_id":"${request.recipientAddress}"}""",
                    gas = NEAR_STORAGE_DEPOSIT_GAS,
                    deposit = NEAR_STORAGE_DEPOSIT_YOCTO,
                ),
                nearFunctionCallAction(
                    methodName = "ft_transfer",
                    argsJson = """{"amount":"${request.amountRaw}","receiver_id":"${request.recipientAddress}"}""",
                    gas = NEAR_FT_TRANSFER_GAS,
                    deposit = BigInteger.ONE,
                ),
            )
        }
        val receiverId = request.tokenContract ?: request.recipientAddress
        val transaction = ByteArrayOutputStream().use { out ->
            out.writeStringBorsh(request.sourceAddress)
            out.write(ED25519_KEY_TYPE)
            out.write(publicKey)
            out.writeUInt64LE(request.nonce)
            out.writeStringBorsh(receiverId)
            out.write(request.recentBlockHash)
            out.writeUInt32LE(actions.size.toLong())
            actions.forEach(out::write)
            out.toByteArray()
        }
        val transactionHash = SatraSigningCrypto.sha256(transaction)
        val signature = SatraSigningCrypto.ed25519Sign(transactionHash, privateKey)
        val signed = ByteArrayOutputStream().use { out ->
            out.write(transaction)
            out.write(ED25519_KEY_TYPE)
            out.write(signature)
            out.toByteArray()
        }
        return NearSignedTransaction(
            signedTransactionBase64 = Base64.getEncoder().encodeToString(signed),
            transactionHashBase58 = SatraSigningCrypto.base58(transactionHash),
            feeYocto = NEAR_ESTIMATED_TRANSFER_FEE_YOCTO,
        )
    }

    fun publicKeyForPrivateKey(privateKeyHex: String): String {
        val privateKey = SatraSigningCrypto.parseEd25519PrivateKey(privateKeyHex)
        return "ed25519:${SatraSigningCrypto.base58(SatraSigningCrypto.ed25519PublicKey(privateKey))}"
    }

    private fun nearTransferAction(amount: BigInteger): ByteArray =
        ByteArrayOutputStream().use { out ->
            out.writeUInt32LE(ACTION_TRANSFER.toLong())
            out.writeUInt128LE(amount)
            out.toByteArray()
        }

    private fun nearFunctionCallAction(
        methodName: String,
        argsJson: String,
        gas: Long,
        deposit: BigInteger,
    ): ByteArray =
        ByteArrayOutputStream().use { out ->
            out.writeUInt32LE(ACTION_FUNCTION_CALL.toLong())
            out.writeStringBorsh(methodName)
            out.writeBytesBorsh(argsJson.toByteArray(Charsets.UTF_8))
            out.writeUInt64LE(gas.toULong())
            out.writeUInt128LE(deposit)
            out.toByteArray()
        }

    private fun ByteArrayOutputStream.writeStringBorsh(value: String) {
        writeBytesBorsh(value.toByteArray(Charsets.UTF_8))
    }

    private fun ByteArrayOutputStream.writeBytesBorsh(bytes: ByteArray) {
        writeUInt32LE(bytes.size.toLong())
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeUInt32LE(value: Long) {
        require(value in 0L..0xffffffffL)
        repeat(4) { shift -> write(((value ushr (8 * shift)) and 0xff).toInt()) }
    }

    private fun ByteArrayOutputStream.writeUInt64LE(value: ULong) {
        var remaining = value
        repeat(8) {
            write((remaining and 0xffu).toInt())
            remaining = remaining shr 8
        }
    }

    private fun ByteArrayOutputStream.writeUInt128LE(value: BigInteger) {
        require(value >= BigInteger.ZERO && value.bitLength() <= 128) { "NEAR amount does not fit u128." }
        val bytes = value.toByteArray().let { raw ->
            val positive = if (raw.firstOrNull() == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
            positive.reversedArray()
        }
        write(bytes)
        repeat(16 - bytes.size) { write(0) }
    }

    private const val ED25519_KEY_TYPE = 0
    private const val ACTION_FUNCTION_CALL = 2
    private const val ACTION_TRANSFER = 3
    private const val NEAR_STORAGE_DEPOSIT_GAS = 10_000_000_000_000L
    private const val NEAR_FT_TRANSFER_GAS = 30_000_000_000_000L
    private val NEAR_STORAGE_DEPOSIT_YOCTO = BigInteger("1250000000000000000000")
    private val NEAR_ESTIMATED_TRANSFER_FEE_YOCTO = BigInteger("1000000000000000000000")
}

internal data class NearSigningRequest(
    val sourceAddress: String,
    val recipientAddress: String,
    val amountRaw: BigInteger,
    val tokenContract: String?,
    val nonce: ULong,
    val recentBlockHash: ByteArray,
    val privateKeyHex: String,
)

internal data class NearSignedTransaction(
    val signedTransactionBase64: String,
    val transactionHashBase58: String,
    val feeYocto: BigInteger,
)

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
