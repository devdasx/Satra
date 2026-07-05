package dev.satra.wallet.data.send.evm

import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.KeccakDigest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.math.ec.ECAlgorithms
import org.bouncycastle.math.ec.ECPoint
import java.io.ByteArrayOutputStream
import java.math.BigInteger

object EvmLegacyTransactionSigner {
    fun sign(
        transaction: EvmLegacyTransaction,
        privateKeyHex: String,
    ): String {
        val privateKey = parsePrivateKey(privateKeyHex)
        val unsignedPayload = Rlp.encodeList(
            Rlp.integer(transaction.nonce),
            Rlp.integer(transaction.gasPrice),
            Rlp.integer(transaction.gasLimit),
            Rlp.bytes(transaction.toAddress.hexToBytes()),
            Rlp.integer(transaction.value),
            Rlp.bytes(transaction.data),
            Rlp.integer(BigInteger.valueOf(transaction.chainId)),
            Rlp.integer(BigInteger.ZERO),
            Rlp.integer(BigInteger.ZERO),
        )
        val messageHash = keccak256(unsignedPayload)
        val signature = signHash(messageHash, privateKey)
        val recId = recoverRecId(
            messageHash = messageHash,
            r = signature.r,
            s = signature.s,
            expectedPublicKey = publicKeyFromPrivateKey(privateKey),
        ) ?: error("Could not recover EVM signature ID.")
        val v = BigInteger.valueOf(transaction.chainId)
            .multiply(BigInteger.valueOf(2L))
            .add(BigInteger.valueOf(35L + recId))

        val signedPayload = Rlp.encodeList(
            Rlp.integer(transaction.nonce),
            Rlp.integer(transaction.gasPrice),
            Rlp.integer(transaction.gasLimit),
            Rlp.bytes(transaction.toAddress.hexToBytes()),
            Rlp.integer(transaction.value),
            Rlp.bytes(transaction.data),
            Rlp.integer(v),
            Rlp.integer(signature.r),
            Rlp.integer(signature.s),
        )
        return "0x${signedPayload.toHex()}"
    }

    fun transactionHash(rawTransaction: String): String {
        val normalized = rawTransaction.removePrefix("0x").removePrefix("0X")
        require(normalized.isNotBlank() && normalized.length % 2 == 0 && normalized.all(Char::isHexDigit)) {
            "Invalid signed EVM transaction."
        }
        return "0x${keccak256(normalized.hexToBytes()).toHex()}"
    }

    private fun parsePrivateKey(privateKeyHex: String): BigInteger {
        val normalized = privateKeyHex.removePrefix("0x").removePrefix("0X")
        require(normalized.length == PRIVATE_KEY_HEX_LENGTH && normalized.all(Char::isHexDigit)) {
            "Invalid EVM private key."
        }
        val value = BigInteger(normalized, 16)
        require(value > BigInteger.ZERO && value < Domain.n) {
            "Invalid EVM private key range."
        }
        return value
    }

    private fun signHash(
        messageHash: ByteArray,
        privateKey: BigInteger,
    ): EcdsaSignature {
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(privateKey, Domain))
        val components = signer.generateSignature(messageHash)
        val r = components[0]
        var s = components[1]
        val halfCurveOrder = Domain.n.shiftRight(1)
        if (s > halfCurveOrder) {
            s = Domain.n.subtract(s)
        }
        return EcdsaSignature(r = r, s = s)
    }

    private fun recoverRecId(
        messageHash: ByteArray,
        r: BigInteger,
        s: BigInteger,
        expectedPublicKey: ByteArray,
    ): Int? {
        for (recId in 0..3) {
            val recovered = recoverPublicKey(
                recId = recId,
                r = r,
                s = s,
                messageHash = messageHash,
            )
            if (recovered?.contentEquals(expectedPublicKey) == true) {
                return recId
            }
        }
        return null
    }

    private fun recoverPublicKey(
        recId: Int,
        r: BigInteger,
        s: BigInteger,
        messageHash: ByteArray,
    ): ByteArray? {
        val n = Domain.n
        val i = BigInteger.valueOf((recId / 2).toLong())
        val x = r.add(i.multiply(n))
        if (x >= Secp256k1Prime) return null

        val rPoint = decompressKey(x, (recId and 1) == 1)
        if (!rPoint.multiply(n).isInfinity) return null

        val e = BigInteger(1, messageHash)
        val rInverse = r.modInverse(n)
        val srInverse = s.multiply(rInverse).mod(n)
        val eInverse = e.negate().mod(n).multiply(rInverse).mod(n)
        val q = ECAlgorithms.sumOfTwoMultiplies(Domain.g, eInverse, rPoint, srInverse).normalize()
        return q.getEncoded(false)
    }

    private fun decompressKey(
        x: BigInteger,
        yBit: Boolean,
    ): ECPoint {
        val encoded = ByteArray(PUBLIC_KEY_COMPRESSED_SIZE)
        encoded[0] = if (yBit) 0x03 else 0x02
        x.toFixedBytes(PRIVATE_KEY_SIZE).copyInto(encoded, destinationOffset = 1)
        return Domain.curve.decodePoint(encoded)
    }

    private fun publicKeyFromPrivateKey(privateKey: BigInteger): ByteArray =
        Domain.g.multiply(privateKey).normalize().getEncoded(false)

    private fun keccak256(input: ByteArray): ByteArray {
        val digest = KeccakDigest(256)
        digest.update(input, 0, input.size)
        return ByteArray(32).also { digest.doFinal(it, 0) }
    }

    private data class EcdsaSignature(
        val r: BigInteger,
        val s: BigInteger,
    )

    private object Rlp {
        fun integer(value: BigInteger): ByteArray {
            require(value >= BigInteger.ZERO) {
                "RLP integer cannot be negative."
            }
            return bytes(value.toMinimalBytes())
        }

        fun bytes(value: ByteArray): ByteArray =
            encodeElement(value)

        fun encodeList(vararg elements: ByteArray): ByteArray {
            val payload = ByteArrayOutputStream()
            elements.forEach { element -> payload.write(element) }
            val payloadBytes = payload.toByteArray()
            return encodeLength(payloadBytes.size, LIST_OFFSET) + payloadBytes
        }

        private fun encodeElement(value: ByteArray): ByteArray =
            when {
                value.size == 1 && value[0].toInt() and 0xff < SINGLE_BYTE_LIMIT -> value
                else -> encodeLength(value.size, STRING_OFFSET) + value
            }

        private fun encodeLength(
            length: Int,
            offset: Int,
        ): ByteArray {
            if (length < SHORT_ITEM_LIMIT) {
                return byteArrayOf((offset + length).toByte())
            }
            val lengthBytes = BigInteger.valueOf(length.toLong()).toMinimalBytes()
            return byteArrayOf((offset + SHORT_ITEM_LIMIT + lengthBytes.size - 1).toByte()) + lengthBytes
        }
    }

    private val CurveParameters = SECNamedCurves.getByName("secp256k1")
    private val Domain = ECDomainParameters(
        CurveParameters.curve,
        CurveParameters.g,
        CurveParameters.n,
        CurveParameters.h,
    )
    private val Secp256k1Prime = BigInteger(
        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",
        16,
    )
    private const val PRIVATE_KEY_HEX_LENGTH = 64
    private const val PRIVATE_KEY_SIZE = 32
    private const val PUBLIC_KEY_COMPRESSED_SIZE = 33
    private const val SINGLE_BYTE_LIMIT = 0x80
    private const val STRING_OFFSET = 0x80
    private const val LIST_OFFSET = 0xc0
    private const val SHORT_ITEM_LIMIT = 56
}

data class EvmLegacyTransaction(
    val nonce: BigInteger,
    val gasPrice: BigInteger,
    val gasLimit: BigInteger,
    val toAddress: String,
    val value: BigInteger,
    val data: ByteArray,
    val chainId: Long,
)

private fun BigInteger.toMinimalBytes(): ByteArray {
    if (this == BigInteger.ZERO) return ByteArray(0)
    val bytes = toByteArray()
    return if (bytes.firstOrNull() == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
}

private fun BigInteger.toFixedBytes(size: Int): ByteArray {
    val raw = toByteArray()
    val normalized = if (raw.firstOrNull() == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
    require(normalized.size <= size) {
        "Integer is too large."
    }
    return ByteArray(size).also { normalized.copyInto(it, destinationOffset = size - normalized.size) }
}

private fun String.hexToBytes(): ByteArray {
    val normalized = removePrefix("0x").removePrefix("0X")
    require(normalized.length % 2 == 0 && normalized.all(Char::isHexDigit)) {
        "Invalid hex value."
    }
    return ByteArray(normalized.length / 2) { index ->
        normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
