package dev.satra.wallet.data.send.stellar

import dev.satra.wallet.data.send.crypto.SatraSigningCrypto
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Base64

internal object StellarTransactionSigner {
    fun sign(request: StellarSigningRequest): StellarSignedTransaction {
        val privateKey = SatraSigningCrypto.parseEd25519PrivateKey(request.privateKeyHex)
        val publicKey = SatraSigningCrypto.ed25519PublicKey(privateKey)
        val sourcePublicKey = stellarPublicKey(request.sourceAddress)
        require(publicKey.contentEquals(sourcePublicKey)) {
            "Stellar private key does not match source address."
        }
        val amount = request.amountRaw.toLongExact()
        require(amount > 0L) { "Invalid XLM amount." }
        val sequence = request.accountSequence + 1L
        val tx = transactionXdr(
            sourcePublicKey = sourcePublicKey,
            destinationPublicKey = stellarPublicKey(request.recipientAddress),
            amountStroops = amount,
            sequenceNumber = sequence,
            createAccount = request.createAccount,
            memoText = request.memoText,
        )
        val signaturePayload = SatraSigningCrypto.sha256(MAINNET_PASSPHRASE.toByteArray(Charsets.UTF_8)) +
            xdrInt(ENVELOPE_TYPE_TX) +
            tx
        val signature = SatraSigningCrypto.ed25519Sign(signaturePayload, privateKey)
        val envelope = ByteArrayOutputStream().use { out ->
            out.writeInt(ENVELOPE_TYPE_TX)
            out.write(tx)
            out.writeInt(1)
            out.write(sourcePublicKey.copyOfRange(sourcePublicKey.size - 4, sourcePublicKey.size))
            out.writeVariableOpaque(signature)
            out.toByteArray()
        }
        return StellarSignedTransaction(
            envelopeBase64 = Base64.getEncoder().encodeToString(envelope),
            feeStroops = STELLAR_BASE_FEE_STROOPS,
        )
    }

    fun validateAddress(address: String) {
        stellarPublicKey(address)
    }

    private fun transactionXdr(
        sourcePublicKey: ByteArray,
        destinationPublicKey: ByteArray,
        amountStroops: Long,
        sequenceNumber: Long,
        createAccount: Boolean,
        memoText: String?,
    ): ByteArray =
        ByteArrayOutputStream().use { out ->
            out.writeMuxedEd25519(sourcePublicKey)
            out.writeInt(STELLAR_BASE_FEE_STROOPS)
            out.writeLong(sequenceNumber)
            out.writeInt(PRECONDITION_NONE)
            out.writeMemo(memoText)
            out.writeInt(1)
            out.writeInt(0)
            if (createAccount) {
                out.writeInt(OP_CREATE_ACCOUNT)
                out.writeEd25519PublicKey(destinationPublicKey)
                out.writeLong(amountStroops)
            } else {
                out.writeInt(OP_PAYMENT)
                out.writeMuxedEd25519(destinationPublicKey)
                out.writeInt(ASSET_NATIVE)
                out.writeLong(amountStroops)
            }
            out.toByteArray()
        }

    private fun ByteArrayOutputStream.writeMemo(memoText: String?) {
        val memoBytes = memoText
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.toByteArray(Charsets.UTF_8)
        if (memoBytes == null) {
            writeInt(MEMO_NONE)
            return
        }
        require(memoBytes.size <= STELLAR_MEMO_TEXT_MAX_BYTES) {
            "Stellar memo text is too long."
        }
        writeInt(MEMO_TEXT)
        writeVariableOpaque(memoBytes)
    }

    private fun ByteArrayOutputStream.writeMuxedEd25519(publicKey: ByteArray) {
        writeInt(KEY_TYPE_ED25519)
        write(publicKey)
    }

    private fun ByteArrayOutputStream.writeEd25519PublicKey(publicKey: ByteArray) {
        writeInt(KEY_TYPE_ED25519)
        write(publicKey)
    }

    private fun ByteArrayOutputStream.writeVariableOpaque(value: ByteArray) {
        writeInt(value.size)
        write(value)
        repeat((4 - value.size % 4) % 4) { write(0) }
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write((value ushr 24) and 0xff)
        write((value ushr 16) and 0xff)
        write((value ushr 8) and 0xff)
        write(value and 0xff)
    }

    private fun ByteArrayOutputStream.writeLong(value: Long) {
        repeat(8) { index ->
            write(((value ushr (8 * (7 - index))) and 0xff).toInt())
        }
    }

    private fun xdrInt(value: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            out.writeInt(value)
            out.toByteArray()
        }

    private fun stellarPublicKey(address: String): ByteArray {
        val decoded = base32Decode(address)
        require(decoded.size == 35 && decoded.first() == STELLAR_PUBLIC_KEY_VERSION) {
            "Invalid Stellar address."
        }
        val payload = decoded.copyOfRange(0, decoded.size - 2)
        val checksum = decoded.copyOfRange(decoded.size - 2, decoded.size)
        val expected = crc16Xmodem(payload).let { crc ->
            byteArrayOf((crc and 0xff).toByte(), ((crc ushr 8) and 0xff).toByte())
        }
        require(checksum.contentEquals(expected)) { "Invalid Stellar checksum." }
        return payload.copyOfRange(1, payload.size)
    }

    private fun base32Decode(input: String): ByteArray {
        var buffer = 0
        var bitsLeft = 0
        val output = mutableListOf<Byte>()
        input.trim().uppercase().forEach { char ->
            val value = BASE32_ALPHABET.indexOf(char)
            require(value >= 0) { "Invalid Stellar Base32 character." }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xff).toByte())
            }
        }
        return output.toByteArray()
    }

    private fun crc16Xmodem(data: ByteArray): Int {
        var crc = 0
        data.forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xff) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xffff
                } else {
                    (crc shl 1) and 0xffff
                }
            }
        }
        return crc and 0xffff
    }

    private fun BigInteger.toLongExact(): Long {
        if (this < Long.MIN_VALUE.toBigInteger() || this > Long.MAX_VALUE.toBigInteger()) {
            throw ArithmeticException("BigInteger out of Long range.")
        }
        return toLong()
    }

    private const val MAINNET_PASSPHRASE = "Public Global Stellar Network ; September 2015"
    private const val ENVELOPE_TYPE_TX = 2
    private const val KEY_TYPE_ED25519 = 0
    private const val PRECONDITION_NONE = 0
    private const val MEMO_NONE = 0
    private const val MEMO_TEXT = 1
    private const val STELLAR_MEMO_TEXT_MAX_BYTES = 28
    private const val OP_CREATE_ACCOUNT = 0
    private const val OP_PAYMENT = 1
    private const val ASSET_NATIVE = 0
    private const val STELLAR_BASE_FEE_STROOPS = 100
    private const val STELLAR_PUBLIC_KEY_VERSION = (6 shl 3).toByte()
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
}

internal data class StellarSigningRequest(
    val sourceAddress: String,
    val recipientAddress: String,
    val amountRaw: BigInteger,
    val accountSequence: Long,
    val createAccount: Boolean,
    val memoText: String? = null,
    val privateKeyHex: String,
)

internal data class StellarSignedTransaction(
    val envelopeBase64: String,
    val feeStroops: Int,
)
