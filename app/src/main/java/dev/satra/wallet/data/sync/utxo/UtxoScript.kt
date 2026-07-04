package dev.satra.wallet.data.sync.utxo

import java.math.BigInteger
import java.security.MessageDigest

object UtxoScript {
    fun watchedScriptForAddress(
        networkId: String,
        address: String,
        walletAddressId: String? = null,
        derivationPath: String? = null,
        derivationName: String? = null,
        addressIndex: Int? = null,
        isChange: Boolean = false,
    ): UtxoWatchedScript {
        val scriptPubKey = scriptPubKey(networkId, address)
        return UtxoWatchedScript(
            walletAddressId = walletAddressId,
            networkId = networkId,
            address = address,
            scriptPubKeyHex = scriptPubKey.toHex(),
            scriptHash = scriptHash(scriptPubKey),
            derivationPath = derivationPath,
            derivationName = derivationName,
            addressIndex = addressIndex,
            isChange = isChange,
        )
    }

    fun scriptPubKey(
        networkId: String,
        address: String,
    ): ByteArray {
        parseSegwitAddress(address)?.let { segwit ->
            return when (segwit.version) {
                0 -> byteArrayOf(0x00, segwit.program.size.toByte()) + segwit.program
                1 -> byteArrayOf(0x51, segwit.program.size.toByte()) + segwit.program
                else -> error("Unsupported SegWit version ${segwit.version}.")
            }
        }

        if (networkId == "bitcoinCash" || address.contains(":")) {
            parseCashAddress(address, networkId)?.let { cashAddr ->
                return when (cashAddr.type) {
                    CashAddressType.P2pkh -> p2pkhScript(cashAddr.hash)
                    CashAddressType.P2sh -> p2shScript(cashAddr.hash)
                }
            }
        }

        val payload = base58CheckDecode(address)
        require(payload.isNotEmpty()) { "Invalid base58 address." }
        val version = payload.first().toInt() and 0xff
        val hash = payload.copyOfRange(1, payload.size)
        require(hash.size == 20) { "Unsupported address hash length: ${hash.size}." }
        return when {
            version in p2pkhVersions(networkId) -> p2pkhScript(hash)
            version in p2shVersions(networkId) -> p2shScript(hash)
            else -> error("Unsupported address version $version for $networkId.")
        }
    }

    fun scriptHash(scriptPubKey: ByteArray): String =
        sha256(scriptPubKey).reversedArray().toHex()

    fun addressFromScriptPubKeyOrNull(
        networkId: String,
        scriptPubKeyHex: String,
    ): String? {
        val script = runCatching { scriptPubKeyHex.hexToBytes() }.getOrNull() ?: return null
        return when {
            script.size == 25 &&
                script[0] == 0x76.toByte() &&
                script[1] == 0xa9.toByte() &&
                script[2] == 0x14.toByte() &&
                script[23] == 0x88.toByte() &&
                script[24] == 0xac.toByte() -> {
                val hash = script.copyOfRange(3, 23)
                when (networkId) {
                    "bitcoinCash" -> cashAddr("bitcoincash", hash, CashAddressType.P2pkh)
                    else -> base58Check(byteArrayOf(p2pkhVersions(networkId).first().toByte()) + hash)
                }
            }
            script.size == 23 &&
                script[0] == 0xa9.toByte() &&
                script[1] == 0x14.toByte() &&
                script[22] == 0x87.toByte() -> {
                val hash = script.copyOfRange(2, 22)
                when (networkId) {
                    "bitcoinCash" -> cashAddr("bitcoincash", hash, CashAddressType.P2sh)
                    else -> base58Check(byteArrayOf(preferredP2shVersion(networkId).toByte()) + hash)
                }
            }
            script.size == 22 && script[0] == 0x00.toByte() && script[1] == 0x14.toByte() -> {
                val hrp = when (networkId) {
                    "litecoin" -> "ltc"
                    "bitcoin" -> "bc"
                    else -> return null
                }
                bech32Encode(hrp, byteArrayOf(0) + convertBits(script.copyOfRange(2, 22), 8, 5, true), BECH32_CONST)
            }
            script.size == 34 && script[0] == 0x00.toByte() && script[1] == 0x20.toByte() -> {
                val hrp = when (networkId) {
                    "litecoin" -> "ltc"
                    "bitcoin" -> "bc"
                    else -> return null
                }
                bech32Encode(hrp, byteArrayOf(0) + convertBits(script.copyOfRange(2, 34), 8, 5, true), BECH32_CONST)
            }
            script.size == 34 && script[0] == 0x51.toByte() && script[1] == 0x20.toByte() -> {
                val hrp = when (networkId) {
                    "litecoin" -> "ltc"
                    "bitcoin" -> "bc"
                    else -> return null
                }
                bech32Encode(hrp, byteArrayOf(1) + convertBits(script.copyOfRange(2, 34), 8, 5, true), BECH32M_CONST)
            }
            else -> null
        }
    }

    private fun p2pkhVersions(networkId: String): Set<Int> =
        when (networkId) {
            "bitcoin", "bitcoinCash" -> setOf(0)
            "dogecoin" -> setOf(30)
            "litecoin" -> setOf(48)
            else -> emptySet()
        }

    private fun p2shVersions(networkId: String): Set<Int> =
        when (networkId) {
            "bitcoin", "bitcoinCash" -> setOf(5)
            "dogecoin" -> setOf(22)
            "litecoin" -> setOf(5, 50)
            else -> emptySet()
        }

    private fun preferredP2shVersion(networkId: String): Int =
        when (networkId) {
            "litecoin" -> 50
            else -> p2shVersions(networkId).first()
        }

    private fun p2pkhScript(hash160: ByteArray): ByteArray =
        byteArrayOf(0x76, 0xa9.toByte(), 0x14) + hash160 + byteArrayOf(0x88.toByte(), 0xac.toByte())

    private fun p2shScript(hash160: ByteArray): ByteArray =
        byteArrayOf(0xa9.toByte(), 0x14) + hash160 + byteArrayOf(0x87.toByte())

    private fun parseSegwitAddress(address: String): SegwitAddress? {
        val normalized = address.lowercase()
        val separatorIndex = normalized.lastIndexOf('1')
        if (separatorIndex <= 0 || separatorIndex + 7 > normalized.length) return null
        val hrp = normalized.substring(0, separatorIndex)
        if (hrp !in setOf("bc", "ltc")) return null
        val values = normalized.substring(separatorIndex + 1).map { char ->
            BECH32_CHARSET.indexOf(char).takeIf { it >= 0 } ?: return null
        }
        if (values.size < 7) return null
        val checksumValid = bech32Polymod(
            bech32HrpExpand(hrp) + values.map(Int::toByte).toByteArray(),
        )
        if (checksumValid != BECH32_CONST && checksumValid != BECH32M_CONST) return null
        val data = values.dropLast(6)
        val version = data.firstOrNull() ?: return null
        val program = convertBits(
            data.drop(1).map(Int::toByte).toByteArray(),
            fromBits = 5,
            toBits = 8,
            pad = false,
        )
        if (version == 0 && program.size !in setOf(20, 32)) return null
        if (version == 1 && program.size != 32) return null
        return SegwitAddress(version = version, program = program)
    }

    private fun parseCashAddress(
        address: String,
        networkId: String,
    ): CashAddress? {
        val defaultPrefix = if (networkId == "bitcoinCash") "bitcoincash" else return null
        val normalized = address.lowercase()
        val parts = normalized.split(":")
        val prefix = if (parts.size == 2) parts[0] else defaultPrefix
        val payloadText = if (parts.size == 2) parts[1] else parts[0]
        if (prefix != "bitcoincash" || payloadText.isBlank()) return null
        val payload = payloadText.map { char ->
            CASHADDR_CHARSET.indexOf(char).takeIf { it >= 0 } ?: return null
        }
        if (payload.size < 9) return null
        val checksumValid = cashAddrPolymod(
            prefix.map { (it.code and 0x1f).toByte() }.toByteArray() +
                byteArrayOf(0) +
                payload.map(Int::toByte).toByteArray(),
        )
        if (checksumValid != 0L && checksumValid != 1L) return null
        val data = convertBits(
            payload.dropLast(8).map(Int::toByte).toByteArray(),
            fromBits = 5,
            toBits = 8,
            pad = false,
        )
        if (data.isEmpty()) return null
        val version = data.first().toInt() and 0xff
        val type = when ((version ushr 3) and 0x1f) {
            0 -> CashAddressType.P2pkh
            1 -> CashAddressType.P2sh
            else -> return null
        }
        val hash = data.copyOfRange(1, data.size)
        if (hash.size != 20) return null
        return CashAddress(type = type, hash = hash)
    }

    private fun cashAddr(
        prefix: String,
        hash: ByteArray,
        type: CashAddressType,
    ): String {
        val version = when (type) {
            CashAddressType.P2pkh -> 0
            CashAddressType.P2sh -> 8
        }
        val payload = convertBits(byteArrayOf(version.toByte()) + hash, 8, 5, true)
        val checksum = cashAddrCreateChecksum(prefix, payload)
        return "$prefix:${(payload + checksum).joinToString("") { CASHADDR_CHARSET[it.toInt()].toString() }}"
    }

    private fun cashAddrCreateChecksum(
        prefix: String,
        payload: ByteArray,
    ): ByteArray {
        val values = prefix.map { (it.code and 0x1f).toByte() }.toByteArray() +
            byteArrayOf(0) +
            payload +
            ByteArray(8)
        val polymod = cashAddrPolymod(values) xor 1
        return ByteArray(8) { index ->
            ((polymod shr (5 * (7 - index))) and 31).toByte()
        }
    }

    private fun base58CheckDecode(address: String): ByteArray {
        val decoded = base58Decode(address)
        require(decoded.size >= 5) { "Invalid base58check length." }
        val payload = decoded.copyOfRange(0, decoded.size - 4)
        val checksum = decoded.copyOfRange(decoded.size - 4, decoded.size)
        val expected = sha256(sha256(payload)).copyOfRange(0, 4)
        require(checksum.contentEquals(expected)) { "Invalid base58check checksum." }
        return payload
    }

    private fun base58Decode(value: String): ByteArray {
        var number = BigInteger.ZERO
        val base = BigInteger.valueOf(58)
        value.forEach { char ->
            val digit = BITCOIN_BASE58_ALPHABET.indexOf(char)
            require(digit >= 0) { "Invalid base58 character." }
            number = number.multiply(base).add(BigInteger.valueOf(digit.toLong()))
        }
        val leadingZeroCount = value.takeWhile { it == BITCOIN_BASE58_ALPHABET.first() }.length
        val bytes = number.toByteArray().let { raw ->
            if (raw.size > 1 && raw.first() == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        }
        return ByteArray(leadingZeroCount) + bytes
    }

    private fun base58Check(payload: ByteArray): String =
        base58(payload + sha256(sha256(payload)).copyOfRange(0, 4))

    private fun base58(data: ByteArray): String {
        var value = BigInteger(1, data)
        val base = BigInteger.valueOf(58)
        val builder = StringBuilder()
        while (value > BigInteger.ZERO) {
            val divRem = value.divideAndRemainder(base)
            builder.append(BITCOIN_BASE58_ALPHABET[divRem[1].toInt()])
            value = divRem[0]
        }
        data.takeWhile { it == 0.toByte() }.forEach { _ -> builder.append(BITCOIN_BASE58_ALPHABET[0]) }
        return builder.reverse().toString()
    }

    private fun convertBits(
        data: ByteArray,
        fromBits: Int,
        toBits: Int,
        pad: Boolean,
    ): ByteArray {
        var accumulator = 0
        var bits = 0
        val maxValue = (1 shl toBits) - 1
        val output = mutableListOf<Byte>()
        data.forEach { value ->
            accumulator = (accumulator shl fromBits) or (value.toInt() and ((1 shl fromBits) - 1))
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                output.add(((accumulator shr bits) and maxValue).toByte())
            }
        }
        if (pad && bits > 0) {
            output.add(((accumulator shl (toBits - bits)) and maxValue).toByte())
        } else if (!pad) {
            require(bits < fromBits) { "Invalid bit conversion padding." }
            require(((accumulator shl (toBits - bits)) and maxValue) == 0) { "Non-zero padding." }
        }
        return output.toByteArray()
    }

    private fun bech32HrpExpand(hrp: String): ByteArray =
        hrp.map { (it.code shr 5).toByte() }.toByteArray() +
            byteArrayOf(0) +
            hrp.map { (it.code and 31).toByte() }.toByteArray()

    private fun bech32Polymod(values: ByteArray): Int {
        var chk = 1
        values.forEach { value ->
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor (value.toInt() and 0xff)
            BECH32_GENERATOR.forEachIndexed { index, generator ->
                if (((top ushr index) and 1) != 0) {
                    chk = chk xor generator
                }
            }
        }
        return chk
    }

    private fun bech32Encode(
        hrp: String,
        data: ByteArray,
        constant: Int,
    ): String {
        val values = bech32HrpExpand(hrp) + data + ByteArray(6)
        val polymod = bech32Polymod(values) xor constant
        val checksum = ByteArray(6) { index ->
            ((polymod shr (5 * (5 - index))) and 31).toByte()
        }
        return "${hrp}1${(data + checksum).joinToString("") { BECH32_CHARSET[it.toInt()].toString() }}"
    }

    private fun cashAddrPolymod(values: ByteArray): Long {
        var checksum = 1L
        values.forEach { value ->
            val top = checksum ushr 35
            checksum = ((checksum and 0x07ffffffffL) shl 5) xor (value.toLong() and 0xff)
            CASHADDR_GENERATOR.forEachIndexed { index, generator ->
                if (((top ushr index) and 1L) != 0L) {
                    checksum = checksum xor generator
                }
            }
        }
        return checksum xor 1
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun String.hexToBytes(): ByteArray {
        val normalized = removePrefix("0x").removePrefix("0X")
        require(normalized.length % 2 == 0) { "Hex string must have an even length." }
        return normalized.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private data class SegwitAddress(
        val version: Int,
        val program: ByteArray,
    )

    private data class CashAddress(
        val type: CashAddressType,
        val hash: ByteArray,
    )

    private enum class CashAddressType {
        P2pkh,
        P2sh,
    }

    private const val BITCOIN_BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val CASHADDR_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val BECH32_CONST = 1
    private const val BECH32M_CONST = 0x2bc830a3
    private val BECH32_GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
    private val CASHADDR_GENERATOR = longArrayOf(
        0x98f2bc8e61L,
        0x79b76d99e2L,
        0xf33e5fb3c4L,
        0xae2eabe2a8L,
        0x1e4f43e470L,
    )
}
