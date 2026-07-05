package dev.satra.wallet.data.send.aptos

import dev.satra.wallet.data.send.crypto.SatraSigningCrypto
import dev.satra.wallet.data.send.crypto.SatraSigningCrypto.toFixedBytes
import dev.satra.wallet.data.send.crypto.SatraSigningCrypto.toHex
import java.io.ByteArrayOutputStream
import java.math.BigInteger

internal object AptosTransactionSigner {
    fun sign(request: AptosSigningRequest): AptosSignedTransaction {
        val privateKey = SatraSigningCrypto.parseEd25519PrivateKey(request.privateKeyHex)
        val publicKey = SatraSigningCrypto.ed25519PublicKey(privateKey)
        val derivedAddress = "0x${SatraSigningCrypto.sha3_256(publicKey + byteArrayOf(0)).toHex()}"
        require(derivedAddress == normalizeAddress(request.sourceAddress)) {
            "Aptos private key does not match source address."
        }
        val amount = request.amountRaw.toULongExact()
        val payload = if (request.assetMetadataAddress == null) {
            entryFunctionPayload(
                moduleAddress = APTOS_FRAMEWORK_ADDRESS,
                moduleName = "aptos_account",
                functionName = "transfer",
                typeArgs = emptyList(),
                args = listOf(
                    bcsAddress(request.recipientAddress),
                    bcsU64(amount),
                ),
            )
        } else {
            entryFunctionPayload(
                moduleAddress = APTOS_FRAMEWORK_ADDRESS,
                moduleName = "aptos_account",
                functionName = "transfer_fungible_assets",
                typeArgs = emptyList(),
                args = listOf(
                    bcsAddress(request.assetMetadataAddress),
                    bcsAddress(request.recipientAddress),
                    bcsU64(amount),
                ),
            )
        }
        val rawTransaction = ByteArrayOutputStream().use { out ->
            out.write(aptosAddressBytes(request.sourceAddress))
            out.writeU64(request.sequenceNumber)
            out.write(payload)
            out.writeU64(request.maxGasAmount)
            out.writeU64(request.gasUnitPrice)
            out.writeU64(request.expirationTimestampSeconds)
            out.write(MAINNET_CHAIN_ID)
            out.toByteArray()
        }
        val signingMessage = SatraSigningCrypto.sha3_256(RAW_TRANSACTION_SALT.toByteArray(Charsets.US_ASCII)) + rawTransaction
        val signature = SatraSigningCrypto.ed25519Sign(signingMessage, privateKey)
        val signedTransaction = ByteArrayOutputStream().use { out ->
            out.write(rawTransaction)
            out.writeUleb128(TRANSACTION_AUTHENTICATOR_ED25519)
            out.writeBytesBcs(publicKey)
            out.writeBytesBcs(signature)
            out.toByteArray()
        }
        return AptosSignedTransaction(
            signedTransactionBytes = signedTransaction,
            signedTransactionHex = signedTransaction.toHex(),
            maxFeeOctas = request.maxGasAmount.toBigInteger().multiply(request.gasUnitPrice.toBigInteger()),
        )
    }

    fun normalizeAddress(address: String): String =
        "0x${aptosAddressBytes(address).toHex()}"

    private fun entryFunctionPayload(
        moduleAddress: String,
        moduleName: String,
        functionName: String,
        typeArgs: List<ByteArray>,
        args: List<ByteArray>,
    ): ByteArray =
        ByteArrayOutputStream().use { out ->
            out.writeUleb128(TRANSACTION_PAYLOAD_ENTRY_FUNCTION)
            out.write(aptosAddressBytes(moduleAddress))
            out.writeStringBcs(moduleName)
            out.writeStringBcs(functionName)
            out.writeUleb128(typeArgs.size)
            typeArgs.forEach(out::write)
            out.writeUleb128(args.size)
            args.forEach { arg -> out.writeBytesBcs(arg) }
            out.toByteArray()
        }

    private fun bcsAddress(address: String): ByteArray =
        aptosAddressBytes(address)

    private fun bcsU64(value: ULong): ByteArray =
        ByteArrayOutputStream().use { out ->
            out.writeUInt64(value)
            out.toByteArray()
        }

    private fun aptosAddressBytes(address: String): ByteArray {
        val normalized = address.removePrefix("0x").removePrefix("0X")
        require(normalized.isNotBlank() && normalized.length <= 64 && normalized.all(Char::isHexDigit)) {
            "Invalid Aptos address."
        }
        return BigInteger(normalized, 16).toFixedBytes(32)
    }

    private fun ByteArrayOutputStream.writeStringBcs(value: String) {
        writeBytesBcs(value.toByteArray(Charsets.UTF_8))
    }

    private fun ByteArrayOutputStream.writeBytesBcs(value: ByteArray) {
        writeUleb128(value.size)
        write(value)
    }

    private fun ByteArrayOutputStream.writeU64(value: ULong) {
        writeUInt64(value)
    }

    private fun ByteArrayOutputStream.writeUInt64(value: ULong) {
        var remaining = value
        repeat(8) {
            write((remaining and 0xffu).toInt())
            remaining = remaining shr 8
        }
    }

    private fun ByteArrayOutputStream.writeUleb128(value: Int) {
        require(value >= 0)
        var remaining = value
        while (true) {
            var byte = remaining and 0x7f
            remaining = remaining ushr 7
            if (remaining == 0) {
                write(byte)
                break
            }
            byte = byte or 0x80
            write(byte)
        }
    }

    private fun BigInteger.toULongExact(): ULong {
        require(this >= BigInteger.ZERO && bitLength() <= 64) { "Amount does not fit Aptos u64." }
        return toString().toULong()
    }

    private fun ULong.toBigInteger(): BigInteger =
        BigInteger(toString())

    private const val MAINNET_CHAIN_ID = 1
    private const val TRANSACTION_PAYLOAD_ENTRY_FUNCTION = 2
    private const val TRANSACTION_AUTHENTICATOR_ED25519 = 0
    private const val APTOS_FRAMEWORK_ADDRESS = "0x1"
    private const val RAW_TRANSACTION_SALT = "APTOS::RawTransaction"
}

internal data class AptosSigningRequest(
    val sourceAddress: String,
    val recipientAddress: String,
    val amountRaw: BigInteger,
    val assetMetadataAddress: String?,
    val sequenceNumber: ULong,
    val gasUnitPrice: ULong,
    val maxGasAmount: ULong,
    val expirationTimestampSeconds: ULong,
    val privateKeyHex: String,
)

internal data class AptosSignedTransaction(
    val signedTransactionBytes: ByteArray,
    val signedTransactionHex: String,
    val maxFeeOctas: BigInteger,
)

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
