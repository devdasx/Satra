package dev.satra.wallet.data.assets

import dev.satra.wallet.data.send.aptos.AptosTransactionSigner
import dev.satra.wallet.data.send.ripple.RippleTransactionSigner
import dev.satra.wallet.data.send.solana.SolanaTransactionSigner
import dev.satra.wallet.data.send.stellar.StellarTransactionSigner
import dev.satra.wallet.data.send.ton.TonTransactionSigner
import dev.satra.wallet.data.send.tron.TronSendClient
import dev.satra.wallet.data.sync.accountchain.PolkadotStorage
import dev.satra.wallet.data.sync.utxo.UtxoScript
import java.util.Locale

internal fun isValidAddressForNetwork(
    address: String,
    network: SupportedNetwork,
): Boolean {
    val value = address.trim()
    if (value.isBlank() || value.any(Char::isWhitespace)) return false
    return runCatching {
        when (network.family) {
            "evm" -> value.matches(EVM_ADDRESS_REGEX)
            "utxo" -> isValidUtxoAddress(value, network.networkId)
            "solana" -> {
                SolanaTransactionSigner.publicKeyBytes(value)
                true
            }
            "aptos" -> {
                AptosTransactionSigner.normalizeAddress(value)
                true
            }
            "near" -> isValidNearAccountId(value)
            "polkadot" -> {
                PolkadotStorage.decodeSs58AccountId(value)
                true
            }
            "ripple" -> {
                RippleTransactionSigner.validateAddress(value)
                true
            }
            "stellar" -> {
                StellarTransactionSigner.validateAddress(value)
                true
            }
            "sui" -> value.matches(SUI_ADDRESS_REGEX)
            "ton" -> {
                TonTransactionSigner.validateAddress(value)
                true
            }
            "tron" -> {
                TronSendClient.validateTronAddress(value)
                true
            }
            "cosmos" -> isValidCosmosAddress(value, expectedHrp = network.networkId)
            else -> false
        }
    }.getOrDefault(false)
}

internal fun isValidAddressForNetworkId(
    address: String,
    networkId: String,
): Boolean {
    val network = SupportedAssetCatalog.networks.firstOrNull { it.networkId == networkId } ?: return false
    return isValidAddressForNetwork(address, network)
}

private fun isValidUtxoAddress(
    address: String,
    networkId: String,
): Boolean {
    val lower = address.lowercase(Locale.US)
    when {
        lower.startsWith("bc1") && networkId != "bitcoin" -> return false
        lower.startsWith("ltc1") && networkId != "litecoin" -> return false
        lower.startsWith("bitcoincash:") && networkId != "bitcoinCash" -> return false
        lower.contains(":") && networkId != "bitcoinCash" -> return false
    }
    UtxoScript.scriptPubKey(networkId, address)
    return true
}

private fun isValidNearAccountId(address: String): Boolean {
    val value = address.trim()
    if (value.length == NEAR_IMPLICIT_ACCOUNT_LENGTH && value.all(Char::isSatraHexDigit)) {
        return value.all { it.isDigit() || it in 'a'..'f' }
    }
    if (value.length !in NEAR_ACCOUNT_MIN_LENGTH..NEAR_ACCOUNT_MAX_LENGTH) return false
    if (value != value.lowercase(Locale.US)) return false
    if (!value.all { it.isDigit() || it in 'a'..'z' || it == '_' || it == '-' || it == '.' }) return false
    return value.split('.').all { part ->
        part.isNotBlank() &&
            part.first().isLetterOrDigit() &&
            part.last().isLetterOrDigit() &&
            part.all { it.isDigit() || it in 'a'..'z' || it == '_' || it == '-' }
    }
}

private fun Char.isSatraHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun isValidCosmosAddress(
    address: String,
    expectedHrp: String,
): Boolean {
    val decoded = decodeBech32(address) ?: return false
    if (decoded.hrp != expectedHrp.lowercase(Locale.US)) return false
    val payload = convertBits(
        data = decoded.data,
        fromBits = 5,
        toBits = 8,
        pad = false,
    ) ?: return false
    return payload.size == COSMOS_ACCOUNT_ID_BYTES
}

private fun decodeBech32(address: String): Bech32Data? {
    if (address.length !in BECH32_MIN_LENGTH..BECH32_MAX_LENGTH) return null
    val hasLower = address.any { it in 'a'..'z' }
    val hasUpper = address.any { it in 'A'..'Z' }
    if (hasLower && hasUpper) return null
    val normalized = address.lowercase(Locale.US)
    val separatorIndex = normalized.lastIndexOf('1')
    if (separatorIndex <= 0 || separatorIndex + BECH32_CHECKSUM_LENGTH >= normalized.length) return null
    val hrp = normalized.substring(0, separatorIndex)
    val values = normalized.substring(separatorIndex + 1).map { char ->
        BECH32_CHARSET.indexOf(char).takeIf { it >= 0 } ?: return null
    }
    val checksum = bech32Polymod(bech32HrpExpand(hrp) + values.map(Int::toByte).toByteArray())
    if (checksum != BECH32_CONST) return null
    return Bech32Data(
        hrp = hrp,
        data = values.dropLast(BECH32_CHECKSUM_LENGTH).map(Int::toByte).toByteArray(),
    )
}

private fun convertBits(
    data: ByteArray,
    fromBits: Int,
    toBits: Int,
    pad: Boolean,
): ByteArray? {
    var accumulator = 0
    var bits = 0
    val maxValue = (1 shl toBits) - 1
    val result = mutableListOf<Byte>()
    data.forEach { raw ->
        val value = raw.toInt() and 0xff
        if (value ushr fromBits != 0) return null
        accumulator = (accumulator shl fromBits) or value
        bits += fromBits
        while (bits >= toBits) {
            bits -= toBits
            result.add(((accumulator ushr bits) and maxValue).toByte())
        }
    }
    if (pad) {
        if (bits > 0) result.add(((accumulator shl (toBits - bits)) and maxValue).toByte())
    } else if (bits >= fromBits || ((accumulator shl (toBits - bits)) and maxValue) != 0) {
        return null
    }
    return result.toByteArray()
}

private fun bech32HrpExpand(hrp: String): ByteArray =
    hrp.map { (it.code ushr 5).toByte() }.toByteArray() +
        byteArrayOf(0) +
        hrp.map { (it.code and 31).toByte() }.toByteArray()

private fun bech32Polymod(values: ByteArray): Int {
    var checksum = 1
    values.forEach { raw ->
        val top = checksum ushr 25
        checksum = ((checksum and 0x1ffffff) shl 5) xor (raw.toInt() and 0xff)
        BECH32_GENERATORS.forEachIndexed { index, generator ->
            if (((top ushr index) and 1) == 1) checksum = checksum xor generator
        }
    }
    return checksum
}

private data class Bech32Data(
    val hrp: String,
    val data: ByteArray,
)

private val EVM_ADDRESS_REGEX = Regex("^0x[0-9a-fA-F]{40}$")
private val SUI_ADDRESS_REGEX = Regex("^0x[0-9a-fA-F]{64}$")
private const val NEAR_IMPLICIT_ACCOUNT_LENGTH = 64
private const val NEAR_ACCOUNT_MIN_LENGTH = 2
private const val NEAR_ACCOUNT_MAX_LENGTH = 64
private const val COSMOS_ACCOUNT_ID_BYTES = 20
private const val BECH32_MIN_LENGTH = 8
private const val BECH32_MAX_LENGTH = 90
private const val BECH32_CHECKSUM_LENGTH = 6
private const val BECH32_CONST = 1
private const val BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
private val BECH32_GENERATORS = intArrayOf(
    0x3b6a57b2,
    0x26508e6d,
    0x1ea119fa,
    0x3d4233dd,
    0x2a1462b3,
)
