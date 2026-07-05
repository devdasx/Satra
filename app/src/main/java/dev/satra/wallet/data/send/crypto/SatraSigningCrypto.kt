package dev.satra.wallet.data.send.crypto

import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.KeccakDigest
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.crypto.digests.SHA3Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.math.ec.ECAlgorithms
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.math.ec.rfc8032.Ed25519
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest

internal object SatraSigningCrypto {
    const val BITCOIN_BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    const val RIPPLE_BASE58_ALPHABET = "rpshnaf39wBUDNEGHJKLM4PQRST7VWXYZ2bcdeCg65jkm8oFqi1tuvAxyz"

    fun parseSecp256k1PrivateKey(privateKeyHex: String): BigInteger {
        val normalized = privateKeyHex.removePrefix("0x").removePrefix("0X")
        require(normalized.length == PRIVATE_KEY_HEX_LENGTH && normalized.all(Char::isHexDigit)) {
            "Invalid secp256k1 private key."
        }
        val value = BigInteger(normalized, 16)
        require(value > BigInteger.ZERO && value < Secp256k1Domain.n) {
            "Invalid secp256k1 private key range."
        }
        return value
    }

    fun parseEd25519PrivateKey(privateKeyHex: String): ByteArray {
        val normalized = privateKeyHex.removePrefix("0x").removePrefix("0X")
        require(normalized.length == PRIVATE_KEY_HEX_LENGTH && normalized.all(Char::isHexDigit)) {
            "Invalid Ed25519 private key."
        }
        return normalized.hexToBytes()
    }

    fun secp256k1PublicKey(
        privateKey: BigInteger,
        compressed: Boolean,
    ): ByteArray =
        Secp256k1Domain.g.multiply(privateKey).normalize().getEncoded(compressed)

    fun secp256k1SignDigest(
        digest: ByteArray,
        privateKey: BigInteger,
    ): Secp256k1Signature {
        require(digest.size == 32) { "secp256k1 digest must be 32 bytes." }
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(privateKey, Secp256k1Domain))
        val components = signer.generateSignature(digest)
        val r = components[0]
        var s = components[1]
        val halfCurveOrder = Secp256k1Domain.n.shiftRight(1)
        if (s > halfCurveOrder) {
            s = Secp256k1Domain.n.subtract(s)
        }
        return Secp256k1Signature(r = r, s = s)
    }

    fun secp256k1SignDigestWithRecovery(
        digest: ByteArray,
        privateKey: BigInteger,
    ): Secp256k1RecoverableSignature {
        val signature = secp256k1SignDigest(digest, privateKey)
        val expectedPublicKey = secp256k1PublicKey(privateKey, compressed = false)
        val recoveryId = (0..3).firstOrNull { recId ->
            recoverSecp256k1PublicKey(
                recoveryId = recId,
                r = signature.r,
                s = signature.s,
                digest = digest,
            )?.contentEquals(expectedPublicKey) == true
        } ?: error("Could not recover secp256k1 signature ID.")
        return Secp256k1RecoverableSignature(
            r = signature.r,
            s = signature.s,
            recoveryId = recoveryId,
        )
    }

    fun derEncode(signature: Secp256k1Signature): ByteArray =
        ByteArrayOutputStream().use { sequence ->
            val r = signature.r.toDerInteger()
            val s = signature.s.toDerInteger()
            val payload = ByteArrayOutputStream().use { payload ->
                payload.write(0x02)
                payload.write(r.size)
                payload.write(r)
                payload.write(0x02)
                payload.write(s.size)
                payload.write(s)
                payload.toByteArray()
            }
            sequence.write(0x30)
            sequence.write(payload.size)
            sequence.write(payload)
            sequence.toByteArray()
        }

    fun ed25519PublicKey(privateKey: ByteArray): ByteArray =
        Ed25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded

    fun ed25519Sign(
        message: ByteArray,
        privateKey: ByteArray,
    ): ByteArray {
        val key = Ed25519PrivateKeyParameters(privateKey, 0)
        return ByteArray(64).also { signature ->
            key.sign(Ed25519.Algorithm.Ed25519, null, message, 0, message.size, signature, 0)
        }
    }

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun doubleSha256(data: ByteArray): ByteArray =
        sha256(sha256(data))

    fun hash160(data: ByteArray): ByteArray =
        ripemd160(sha256(data))

    fun ripemd160(data: ByteArray): ByteArray {
        val digest = RIPEMD160Digest()
        digest.update(data, 0, data.size)
        return ByteArray(20).also { digest.doFinal(it, 0) }
    }

    fun keccak256(data: ByteArray): ByteArray {
        val digest = KeccakDigest(256)
        digest.update(data, 0, data.size)
        return ByteArray(32).also { output -> digest.doFinal(output, 0) }
    }

    fun sha3_256(data: ByteArray): ByteArray {
        val digest = SHA3Digest(256)
        digest.update(data, 0, data.size)
        return ByteArray(32).also { output -> digest.doFinal(output, 0) }
    }

    fun blake2b256(data: ByteArray): ByteArray {
        val digest = Blake2bDigest(256)
        digest.update(data, 0, data.size)
        return ByteArray(32).also { output -> digest.doFinal(output, 0) }
    }

    fun base58(data: ByteArray, alphabet: String = BITCOIN_BASE58_ALPHABET): String {
        var value = BigInteger(1, data)
        val base = BigInteger.valueOf(58)
        val builder = StringBuilder()
        while (value > BigInteger.ZERO) {
            val divRem = value.divideAndRemainder(base)
            builder.append(alphabet[divRem[1].toInt()])
            value = divRem[0]
        }
        data.takeWhile { it == 0.toByte() }.forEach { _ -> builder.append(alphabet[0]) }
        return builder.reverse().toString()
    }

    fun base58Decode(input: String, alphabet: String = BITCOIN_BASE58_ALPHABET): ByteArray {
        require(input.isNotBlank()) { "Base58 input is blank." }
        val base = BigInteger.valueOf(58)
        var value = BigInteger.ZERO
        input.forEach { char ->
            val digit = alphabet.indexOf(char)
            require(digit >= 0) { "Invalid Base58 character." }
            value = value.multiply(base).add(BigInteger.valueOf(digit.toLong()))
        }
        val encoded = value.toByteArray().let { bytes ->
            if (bytes.size > 1 && bytes.first() == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        }
        val leadingZeros = input.takeWhile { it == alphabet[0] }.length
        return ByteArray(leadingZeros) + encoded
    }

    fun base58CheckEncode(payload: ByteArray, alphabet: String = BITCOIN_BASE58_ALPHABET): String =
        base58(payload + doubleSha256(payload).copyOfRange(0, 4), alphabet)

    fun base58CheckDecode(input: String, alphabet: String = BITCOIN_BASE58_ALPHABET): ByteArray {
        val decoded = base58Decode(input, alphabet)
        require(decoded.size >= 5) { "Invalid Base58Check length." }
        val payload = decoded.copyOfRange(0, decoded.size - 4)
        val checksum = decoded.copyOfRange(decoded.size - 4, decoded.size)
        val expected = doubleSha256(payload).copyOfRange(0, 4)
        require(checksum.contentEquals(expected)) { "Invalid Base58Check checksum." }
        return payload
    }

    fun BigInteger.toFixedBytes(size: Int): ByteArray {
        val raw = toByteArray()
        val normalized = if (raw.firstOrNull() == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        require(normalized.size <= size) { "Integer does not fit in $size bytes." }
        return ByteArray(size).also { normalized.copyInto(it, destinationOffset = size - normalized.size) }
    }

    fun BigInteger.toMinimalBytes(): ByteArray {
        if (this == BigInteger.ZERO) return ByteArray(0)
        val bytes = toByteArray()
        return if (bytes.firstOrNull() == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    fun String.hexToBytes(): ByteArray {
        val normalized = removePrefix("0x").removePrefix("0X")
        require(normalized.length % 2 == 0 && normalized.all(Char::isHexDigit)) {
            "Invalid hex data."
        }
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun BigInteger.toDerInteger(): ByteArray {
        val raw = toMinimalBytes()
        return if (raw.firstOrNull()?.toInt()?.and(0x80) == 0x80) byteArrayOf(0) + raw else raw
    }

    private fun recoverSecp256k1PublicKey(
        recoveryId: Int,
        r: BigInteger,
        s: BigInteger,
        digest: ByteArray,
    ): ByteArray? {
        val n = Secp256k1Domain.n
        val i = BigInteger.valueOf((recoveryId / 2).toLong())
        val x = r.add(i.multiply(n))
        if (x >= Secp256k1Prime) return null

        val rPoint = decompressSecp256k1Key(x, (recoveryId and 1) == 1)
        if (!rPoint.multiply(n).isInfinity) return null

        val e = BigInteger(1, digest)
        val rInverse = r.modInverse(n)
        val srInverse = s.multiply(rInverse).mod(n)
        val eInverse = e.negate().mod(n).multiply(rInverse).mod(n)
        val q = ECAlgorithms.sumOfTwoMultiplies(Secp256k1Domain.g, eInverse, rPoint, srInverse).normalize()
        return q.getEncoded(false)
    }

    private fun decompressSecp256k1Key(
        x: BigInteger,
        yBit: Boolean,
    ): ECPoint {
        val encoded = ByteArray(33)
        encoded[0] = if (yBit) 0x03 else 0x02
        x.toFixedBytes(32).copyInto(encoded, destinationOffset = 1)
        return Secp256k1Domain.curve.decodePoint(encoded)
    }

    private val Secp256k1Parameters = SECNamedCurves.getByName("secp256k1")
    private val Secp256k1Domain = ECDomainParameters(
        Secp256k1Parameters.curve,
        Secp256k1Parameters.g,
        Secp256k1Parameters.n,
        Secp256k1Parameters.h,
    )
    private val Secp256k1Prime = BigInteger(
        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",
        16,
    )
    private const val PRIVATE_KEY_HEX_LENGTH = 64
}

internal data class Secp256k1Signature(
    val r: BigInteger,
    val s: BigInteger,
)

internal data class Secp256k1RecoverableSignature(
    val r: BigInteger,
    val s: BigInteger,
    val recoveryId: Int,
)

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
