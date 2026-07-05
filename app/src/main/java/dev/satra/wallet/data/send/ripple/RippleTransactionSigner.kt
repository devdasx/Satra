package dev.satra.wallet.data.send.ripple

import dev.satra.wallet.data.send.crypto.SatraSigningCrypto
import dev.satra.wallet.data.send.crypto.SatraSigningCrypto.toFixedBytes
import dev.satra.wallet.data.send.crypto.SatraSigningCrypto.toHex
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest

internal object RippleTransactionSigner {
    fun sign(request: RippleSigningRequest): RippleSignedTransaction {
        val privateKey = SatraSigningCrypto.parseSecp256k1PrivateKey(request.privateKeyHex)
        val publicKey = SatraSigningCrypto.secp256k1PublicKey(privateKey, compressed = true)
        val accountId = rippleAccountId(request.sourceAddress)
        val derivedAccount = rippleAddress(SatraSigningCrypto.hash160(publicKey))
        require(derivedAccount == request.sourceAddress) {
            "XRP private key does not match source address."
        }
        val destination = rippleAccountId(request.recipientAddress)
        val signingPubKey = publicKey
        val unsigned = paymentFields(
            sequence = request.sequence,
            lastLedgerSequence = request.lastLedgerSequence,
            amountDrops = request.amountDrops,
            feeDrops = request.feeDrops,
            signingPubKey = signingPubKey,
            accountId = accountId,
            destination = destination,
            signature = null,
        )
        val digest = sha512Half(XRPL_SIGNING_PREFIX + unsigned)
        val signatureDer = SatraSigningCrypto.derEncode(
            SatraSigningCrypto.secp256k1SignDigest(digest, privateKey),
        )
        val signed = paymentFields(
            sequence = request.sequence,
            lastLedgerSequence = request.lastLedgerSequence,
            amountDrops = request.amountDrops,
            feeDrops = request.feeDrops,
            signingPubKey = signingPubKey,
            accountId = accountId,
            destination = destination,
            signature = signatureDer,
        )
        val txHash = sha512Half(XRPL_TRANSACTION_ID_PREFIX + signed).toHex().uppercase()
        return RippleSignedTransaction(
            transactionBlobHex = signed.toHex().uppercase(),
            transactionHash = txHash,
            feeDrops = request.feeDrops,
        )
    }

    fun validateAddress(address: String) {
        rippleAccountId(address)
    }

    private fun paymentFields(
        sequence: Long,
        lastLedgerSequence: Long,
        amountDrops: BigInteger,
        feeDrops: BigInteger,
        signingPubKey: ByteArray,
        accountId: ByteArray,
        destination: ByteArray,
        signature: ByteArray?,
    ): ByteArray =
        ByteArrayOutputStream().use { out ->
            out.write(fieldHeader(TYPE_UINT16, FIELD_TRANSACTION_TYPE))
            out.writeUInt16(TRANSACTION_TYPE_PAYMENT)
            out.write(fieldHeader(TYPE_UINT32, FIELD_SEQUENCE))
            out.writeUInt32(sequence)
            out.write(fieldHeader(TYPE_UINT32, FIELD_LAST_LEDGER_SEQUENCE))
            out.writeUInt32(lastLedgerSequence)
            out.write(fieldHeader(TYPE_AMOUNT, FIELD_AMOUNT))
            out.writeXrpAmount(amountDrops)
            out.write(fieldHeader(TYPE_AMOUNT, FIELD_FEE))
            out.writeXrpAmount(feeDrops)
            out.write(fieldHeader(TYPE_BLOB, FIELD_SIGNING_PUB_KEY))
            out.writeVariableLength(signingPubKey)
            if (signature != null) {
                out.write(fieldHeader(TYPE_BLOB, FIELD_TXN_SIGNATURE))
                out.writeVariableLength(signature)
            }
            out.write(fieldHeader(TYPE_ACCOUNT_ID, FIELD_ACCOUNT))
            out.write(accountId)
            out.write(fieldHeader(TYPE_ACCOUNT_ID, FIELD_DESTINATION))
            out.write(destination)
            out.toByteArray()
        }

    private fun rippleAccountId(address: String): ByteArray {
        val payload = SatraSigningCrypto.base58CheckDecode(address, SatraSigningCrypto.RIPPLE_BASE58_ALPHABET)
        require(payload.size == 21 && payload.first() == 0.toByte()) { "Invalid XRP address." }
        return payload.copyOfRange(1, payload.size)
    }

    private fun rippleAddress(accountId: ByteArray): String =
        SatraSigningCrypto.base58CheckEncode(byteArrayOf(0) + accountId, SatraSigningCrypto.RIPPLE_BASE58_ALPHABET)

    private fun fieldHeader(type: Int, field: Int): ByteArray =
        when {
            type < 16 && field < 16 -> byteArrayOf(((type shl 4) or field).toByte())
            type < 16 -> byteArrayOf((type shl 4).toByte(), field.toByte())
            field < 16 -> byteArrayOf(field.toByte(), type.toByte())
            else -> byteArrayOf(0, type.toByte(), field.toByte())
        }

    private fun ByteArrayOutputStream.writeUInt16(value: Int) {
        write((value ushr 8) and 0xff)
        write(value and 0xff)
    }

    private fun ByteArrayOutputStream.writeUInt32(value: Long) {
        require(value in 0L..0xffffffffL)
        write(((value ushr 24) and 0xff).toInt())
        write(((value ushr 16) and 0xff).toInt())
        write(((value ushr 8) and 0xff).toInt())
        write((value and 0xff).toInt())
    }

    private fun ByteArrayOutputStream.writeXrpAmount(value: BigInteger) {
        require(value >= BigInteger.ZERO && value.bitLength() < 63) { "Invalid XRP drops amount." }
        write(value.toFixedBytes(8))
    }

    private fun ByteArrayOutputStream.writeVariableLength(value: ByteArray) {
        val size = value.size
        when {
            size <= 192 -> write(size)
            size <= 12_480 -> {
                val adjusted = size - 193
                write(193 + (adjusted ushr 8))
                write(adjusted and 0xff)
            }
            else -> error("XRPL blob is too large.")
        }
        write(value)
    }

    private fun sha512Half(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-512").digest(data).copyOfRange(0, 32)

    private val XRPL_SIGNING_PREFIX = byteArrayOf(0x53, 0x54, 0x58, 0x00)
    private val XRPL_TRANSACTION_ID_PREFIX = byteArrayOf(0x54, 0x58, 0x4e, 0x00)
    private const val TYPE_UINT16 = 1
    private const val TYPE_UINT32 = 2
    private const val TYPE_AMOUNT = 6
    private const val TYPE_BLOB = 7
    private const val TYPE_ACCOUNT_ID = 8
    private const val FIELD_TRANSACTION_TYPE = 2
    private const val FIELD_SEQUENCE = 4
    private const val FIELD_LAST_LEDGER_SEQUENCE = 27
    private const val FIELD_AMOUNT = 1
    private const val FIELD_FEE = 8
    private const val FIELD_SIGNING_PUB_KEY = 3
    private const val FIELD_TXN_SIGNATURE = 4
    private const val FIELD_ACCOUNT = 1
    private const val FIELD_DESTINATION = 3
    private const val TRANSACTION_TYPE_PAYMENT = 0
}

internal data class RippleSigningRequest(
    val sourceAddress: String,
    val recipientAddress: String,
    val amountDrops: BigInteger,
    val feeDrops: BigInteger,
    val sequence: Long,
    val lastLedgerSequence: Long,
    val privateKeyHex: String,
)

internal data class RippleSignedTransaction(
    val transactionBlobHex: String,
    val transactionHash: String,
    val feeDrops: BigInteger,
)
