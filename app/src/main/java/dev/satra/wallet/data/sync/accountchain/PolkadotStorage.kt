package dev.satra.wallet.data.sync.accountchain

import org.bouncycastle.crypto.digests.Blake2bDigest
import org.json.JSONObject
import java.math.BigInteger

internal object PolkadotStorage {
    fun decodeSs58AccountId(address: String): ByteArray {
        val decoded = base58Decode(address.trim())
        require(decoded.size >= 35) { "Invalid Polkadot address length." }
        val prefixLength = when {
            decoded.first().toInt() and 0x40 == 0 -> 1
            decoded.first().toInt() and 0x40 != 0 -> 2
            else -> error("Invalid Polkadot SS58 prefix.")
        }
        require(decoded.size >= prefixLength + ACCOUNT_ID_LENGTH + CHECKSUM_LENGTH) {
            "Invalid Polkadot account payload."
        }
        val accountId = decoded.copyOfRange(prefixLength, prefixLength + ACCOUNT_ID_LENGTH)
        val payload = decoded.copyOfRange(0, decoded.size - CHECKSUM_LENGTH)
        val checksum = decoded.copyOfRange(decoded.size - CHECKSUM_LENGTH, decoded.size)
        val expected = blake2b512(SS58_PREFIX + payload).copyOfRange(0, CHECKSUM_LENGTH)
        require(checksum.contentEquals(expected)) { "Invalid Polkadot SS58 checksum." }
        return accountId
    }

    fun systemAccountKey(accountId: ByteArray): String =
        "0x" + (
            twoX128("System".encodeToByteArray()) +
                twoX128("Account".encodeToByteArray()) +
                blake2b128Concat(accountId)
            ).toHex()

    fun assetAccountKey(assetId: Long, accountId: ByteArray): String =
        "0x" + (
            twoX128("Assets".encodeToByteArray()) +
                twoX128("Account".encodeToByteArray()) +
                blake2b128Concat(assetId.toLittleEndianU32()) +
                blake2b128Concat(accountId)
            ).toHex()

    fun parseSystemAccountFree(storageHex: String?): BigInteger? {
        val data = storageHex?.hexToBytesOrNull() ?: return null
        if (data.size < SYSTEM_ACCOUNT_FREE_OFFSET + U128_LENGTH) return BigInteger.ZERO
        return data.readU128LittleEndian(SYSTEM_ACCOUNT_FREE_OFFSET)
    }

    fun parseAssetAccountBalance(storageHex: String?): BigInteger? {
        val data = storageHex?.hexToBytesOrNull() ?: return null
        if (data.size < U128_LENGTH) return BigInteger.ZERO
        return data.readU128LittleEndian(0)
    }

    fun parseHeaderNumber(header: JSONObject?): Long? =
        header
            ?.optStringOrNull("number")
            ?.removePrefix("0x")
            ?.let { runCatching { it.toLong(16) }.getOrNull() }

    private fun blake2b128Concat(data: ByteArray): ByteArray =
        blake2b(data, bits = 128) + data

    private fun blake2b512(data: ByteArray): ByteArray =
        blake2b(data, bits = 512)

    private fun blake2b(data: ByteArray, bits: Int): ByteArray {
        val digest = Blake2bDigest(bits)
        digest.update(data, 0, data.size)
        return ByteArray(bits / 8).also { digest.doFinal(it, 0) }
    }

    private fun twoX128(data: ByteArray): ByteArray =
        xxHash64(data, seed = 0L).toLittleEndianU64() +
            xxHash64(data, seed = 1L).toLittleEndianU64()

    private fun xxHash64(data: ByteArray, seed: Long): Long {
        var index = 0
        val end = data.size
        var hash: Long
        if (end >= 32) {
            var v1 = seed + PRIME64_1 + PRIME64_2
            var v2 = seed + PRIME64_2
            var v3 = seed
            var v4 = seed - PRIME64_1
            val limit = end - 32
            while (index <= limit) {
                v1 = xxHash64Round(v1, data.readLongLittleEndian(index))
                index += 8
                v2 = xxHash64Round(v2, data.readLongLittleEndian(index))
                index += 8
                v3 = xxHash64Round(v3, data.readLongLittleEndian(index))
                index += 8
                v4 = xxHash64Round(v4, data.readLongLittleEndian(index))
                index += 8
            }
            hash = java.lang.Long.rotateLeft(v1, 1) +
                java.lang.Long.rotateLeft(v2, 7) +
                java.lang.Long.rotateLeft(v3, 12) +
                java.lang.Long.rotateLeft(v4, 18)
            hash = xxHash64MergeRound(hash, v1)
            hash = xxHash64MergeRound(hash, v2)
            hash = xxHash64MergeRound(hash, v3)
            hash = xxHash64MergeRound(hash, v4)
        } else {
            hash = seed + PRIME64_5
        }

        hash += end.toLong()

        while (index + 8 <= end) {
            val lane = xxHash64Round(0, data.readLongLittleEndian(index))
            hash = hash xor lane
            hash = java.lang.Long.rotateLeft(hash, 27) * PRIME64_1 + PRIME64_4
            index += 8
        }

        if (index + 4 <= end) {
            hash = hash xor ((data.readIntLittleEndian(index).toLong() and 0xffffffffL) * PRIME64_1)
            hash = java.lang.Long.rotateLeft(hash, 23) * PRIME64_2 + PRIME64_3
            index += 4
        }

        while (index < end) {
            hash = hash xor ((data[index].toLong() and 0xffL) * PRIME64_5)
            hash = java.lang.Long.rotateLeft(hash, 11) * PRIME64_1
            index += 1
        }

        hash = hash xor (hash ushr 33)
        hash *= PRIME64_2
        hash = hash xor (hash ushr 29)
        hash *= PRIME64_3
        hash = hash xor (hash ushr 32)
        return hash
    }

    private fun xxHash64Round(accumulator: Long, input: Long): Long {
        var value = accumulator + input * PRIME64_2
        value = java.lang.Long.rotateLeft(value, 31)
        value *= PRIME64_1
        return value
    }

    private fun xxHash64MergeRound(accumulator: Long, value: Long): Long {
        var result = accumulator xor xxHash64Round(0, value)
        result = result * PRIME64_1 + PRIME64_4
        return result
    }

    private fun base58Decode(value: String): ByteArray {
        require(value.isNotBlank()) { "Empty Polkadot address." }
        var number = BigInteger.ZERO
        value.forEach { char ->
            val digit = BASE58_ALPHABET.indexOf(char)
            require(digit >= 0) { "Invalid Polkadot address character." }
            number = number.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit.toLong()))
        }
        val leadingZeros = value.takeWhile { it == BASE58_ALPHABET.first() }.length
        val body = number.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(leadingZeros) + body
    }

    private fun ByteArray.readLongLittleEndian(offset: Int): Long {
        var result = 0L
        repeat(8) { byteIndex ->
            result = result or ((this[offset + byteIndex].toLong() and 0xffL) shl (byteIndex * 8))
        }
        return result
    }

    private fun ByteArray.readIntLittleEndian(offset: Int): Int {
        var result = 0
        repeat(4) { byteIndex ->
            result = result or ((this[offset + byteIndex].toInt() and 0xff) shl (byteIndex * 8))
        }
        return result
    }

    private fun ByteArray.readU128LittleEndian(offset: Int): BigInteger =
        BigInteger(1, copyOfRange(offset, offset + U128_LENGTH).reversedArray())

    private fun Long.toLittleEndianU64(): ByteArray =
        ByteArray(8) { index -> (this ushr (index * 8)).toByte() }

    private fun Long.toLittleEndianU32(): ByteArray {
        require(this in 0..UInt.MAX_VALUE.toLong()) { "Asset Hub asset id is outside u32 range." }
        return ByteArray(4) { index -> (this ushr (index * 8)).toByte() }
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun String.hexToBytesOrNull(): ByteArray? {
        val clean = removePrefix("0x")
        if (clean.isBlank() || clean.length % 2 != 0 || !clean.all { it.isPolkadotHexDigit() }) return null
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun Char.isPolkadotHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private val SS58_PREFIX = "SS58PRE".encodeToByteArray()
    private const val ACCOUNT_ID_LENGTH = 32
    private const val CHECKSUM_LENGTH = 2
    private const val U128_LENGTH = 16
    private const val SYSTEM_ACCOUNT_FREE_OFFSET = 16
    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val PRIME64_1 = -7046029288634856825L
    private const val PRIME64_2 = -4417276706812531889L
    private const val PRIME64_3 = 1609587929392839161L
    private const val PRIME64_4 = -8796714831421723037L
    private const val PRIME64_5 = 2870177450012600261L
}
