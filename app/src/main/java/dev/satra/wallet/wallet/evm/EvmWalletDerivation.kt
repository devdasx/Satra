package dev.satra.wallet.wallet.evm

import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.KeccakDigest
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import java.math.BigInteger
import java.security.MessageDigest
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EvmWalletDerivation {
    const val DEFAULT_EVM_DERIVATION_PATH = "m/44'/60'/0'/0/0"

    fun deriveDefaultAccount(
        mnemonic: String,
        passphrase: String? = null,
    ): EvmDerivedAccount =
        deriveAccount(
            seed = mnemonicToSeed(mnemonic, passphrase.orEmpty()),
            derivationPath = DEFAULT_EVM_DERIVATION_PATH,
        )

    fun deriveAccount(
        seed: ByteArray,
        derivationPath: String,
    ): EvmDerivedAccount {
        val master = hmacSha512(
            key = "Bitcoin seed".toByteArray(Charsets.UTF_8),
            data = seed,
        )
        var privateKey = master.copyOfRange(0, 32).toPositiveBigInteger()
        var chainCode = master.copyOfRange(32, 64)
        require(privateKey > BigInteger.ZERO && privateKey < Secp256k1N) {
            "Invalid BIP32 master private key."
        }

        derivationPath.parseBip32Path().forEach { childNumber ->
            val derived = derivePrivateChild(privateKey, chainCode, childNumber)
            privateKey = derived.privateKey
            chainCode = derived.chainCode
        }

        val privateKeyBytes = privateKey.toFixedBytes(PRIVATE_KEY_SIZE)
        val compressedPublicKey = publicKeyFromPrivateKey(privateKey, compressed = true)
        val uncompressedPublicKey = publicKeyFromPrivateKey(privateKey, compressed = false)
        val addressBytes = keccak256(uncompressedPublicKey.copyOfRange(1, uncompressedPublicKey.size))
            .takeLast(20)
            .toByteArray()

        return EvmDerivedAccount(
            address = "0x${addressBytes.toHex()}",
            privateKeyHex = privateKeyBytes.toHex(),
            publicKeyHex = uncompressedPublicKey.toHex(),
            compressedPublicKeyHex = compressedPublicKey.toHex(),
            keyFingerprint = hash160(compressedPublicKey).take(4).toByteArray().toHex(),
            derivationPath = derivationPath,
        )
    }

    fun privateKeyToAccount(
        privateKey: String,
        derivationPath: String? = null,
    ): EvmDerivedAccount {
        val normalized = privateKey.removePrefix("0x").removePrefix("0X")
        require(normalized.length == PRIVATE_KEY_HEX_LENGTH && normalized.all(Char::isHexDigit)) {
            "Invalid EVM private key."
        }
        val privateKeyInt = BigInteger(normalized, 16)
        require(privateKeyInt > BigInteger.ZERO && privateKeyInt < Secp256k1N) {
            "Invalid EVM private key range."
        }
        val privateKeyBytes = privateKeyInt.toFixedBytes(PRIVATE_KEY_SIZE)
        val compressedPublicKey = publicKeyFromPrivateKey(privateKeyInt, compressed = true)
        val uncompressedPublicKey = publicKeyFromPrivateKey(privateKeyInt, compressed = false)
        val addressBytes = keccak256(uncompressedPublicKey.copyOfRange(1, uncompressedPublicKey.size))
            .takeLast(20)
            .toByteArray()
        return EvmDerivedAccount(
            address = "0x${addressBytes.toHex()}",
            privateKeyHex = privateKeyBytes.toHex(),
            publicKeyHex = uncompressedPublicKey.toHex(),
            compressedPublicKeyHex = compressedPublicKey.toHex(),
            keyFingerprint = hash160(compressedPublicKey).take(4).toByteArray().toHex(),
            derivationPath = derivationPath,
        )
    }

    fun mnemonicToSeed(
        mnemonic: String,
        passphrase: String = "",
    ): ByteArray {
        val normalizedMnemonic = Normalizer.normalize(mnemonic, Normalizer.Form.NFKD)
        val normalizedSalt = Normalizer.normalize("mnemonic$passphrase", Normalizer.Form.NFKD)
        val keySpec = PBEKeySpec(
            normalizedMnemonic.toCharArray(),
            normalizedSalt.toByteArray(Charsets.UTF_8),
            PBKDF2_ROUNDS,
            SEED_BIT_LENGTH,
        )
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            .generateSecret(keySpec)
            .encoded
    }

    private fun derivePrivateChild(
        parentPrivateKey: BigInteger,
        parentChainCode: ByteArray,
        childNumber: Bip32ChildNumber,
    ): Bip32PrivateNode {
        val data = if (childNumber.hardened) {
            byteArrayOf(0) + parentPrivateKey.toFixedBytes(PRIVATE_KEY_SIZE) + childNumber.indexBytes
        } else {
            publicKeyFromPrivateKey(parentPrivateKey, compressed = true) + childNumber.indexBytes
        }
        val digest = hmacSha512(parentChainCode, data)
        val left = digest.copyOfRange(0, 32).toPositiveBigInteger()
        val right = digest.copyOfRange(32, 64)
        require(left < Secp256k1N) {
            "Invalid BIP32 child private key material."
        }
        val childPrivateKey = left.add(parentPrivateKey).mod(Secp256k1N)
        require(childPrivateKey != BigInteger.ZERO) {
            "Invalid BIP32 zero child private key."
        }
        return Bip32PrivateNode(
            privateKey = childPrivateKey,
            chainCode = right,
        )
    }

    private fun publicKeyFromPrivateKey(
        privateKey: BigInteger,
        compressed: Boolean,
    ): ByteArray =
        Secp256k1G
            .multiply(privateKey)
            .normalize()
            .getEncoded(compressed)

    private fun hmacSha512(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(key, "HmacSHA512"))
        return mac.doFinal(data)
    }

    private fun keccak256(data: ByteArray): ByteArray {
        val digest = KeccakDigest(256)
        digest.update(data, 0, data.size)
        return ByteArray(32).also { output ->
            digest.doFinal(output, 0)
        }
    }

    private fun hash160(data: ByteArray): ByteArray {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(data)
        val digest = RIPEMD160Digest()
        digest.update(sha256, 0, sha256.size)
        return ByteArray(20).also { output ->
            digest.doFinal(output, 0)
        }
    }

    private fun String.parseBip32Path(): List<Bip32ChildNumber> {
        require(startsWith("m")) { "BIP32 path must start with m." }
        if (this == "m") {
            return emptyList()
        }
        return removePrefix("m/")
            .split("/")
            .filter(String::isNotBlank)
            .map { segment ->
                val hardened = segment.endsWith("'") || segment.endsWith("h") || segment.endsWith("H")
                val index = segment
                    .removeSuffix("'")
                    .removeSuffix("h")
                    .removeSuffix("H")
                    .toLong()
                require(index in 0L..BIP32_MAX_NORMAL_INDEX) {
                    "Invalid BIP32 child index: $segment"
                }
                Bip32ChildNumber(
                    index = index.toInt(),
                    hardened = hardened,
                )
            }
    }

    private fun BigInteger.toFixedBytes(size: Int): ByteArray {
        val bytes = toByteArray()
        return when {
            bytes.size == size -> bytes
            bytes.size == size + 1 && bytes.first() == 0.toByte() -> bytes.copyOfRange(1, bytes.size)
            bytes.size < size -> ByteArray(size - bytes.size) + bytes
            else -> error("Integer does not fit in $size bytes.")
        }
    }

    private fun ByteArray.toPositiveBigInteger(): BigInteger =
        BigInteger(1, this)

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private val Secp256k1Params = SECNamedCurves.getByName("secp256k1")
    private val Secp256k1G = Secp256k1Params.g
    private val Secp256k1N = Secp256k1Params.n
    private const val PRIVATE_KEY_SIZE = 32
    private const val PRIVATE_KEY_HEX_LENGTH = 64
    private const val PBKDF2_ROUNDS = 2048
    private const val SEED_BIT_LENGTH = 512
    private const val BIP32_HARDENED_OFFSET = 0x80000000L
    private const val BIP32_MAX_NORMAL_INDEX = 0x7fffffffL

    private data class Bip32PrivateNode(
        val privateKey: BigInteger,
        val chainCode: ByteArray,
    )

    private data class Bip32ChildNumber(
        val index: Int,
        val hardened: Boolean,
    ) {
        val indexBytes: ByteArray
            get() {
                val encoded = if (hardened) index.toLong() + BIP32_HARDENED_OFFSET else index.toLong()
                return byteArrayOf(
                    ((encoded ushr 24) and 0xff).toByte(),
                    ((encoded ushr 16) and 0xff).toByte(),
                    ((encoded ushr 8) and 0xff).toByte(),
                    (encoded and 0xff).toByte(),
                )
            }
    }
}

data class EvmDerivedAccount(
    val address: String,
    val privateKeyHex: String,
    val publicKeyHex: String,
    val compressedPublicKeyHex: String,
    val keyFingerprint: String,
    val derivationPath: String?,
)

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
