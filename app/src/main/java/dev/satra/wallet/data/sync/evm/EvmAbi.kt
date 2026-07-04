package dev.satra.wallet.data.sync.evm

import java.math.BigDecimal
import java.math.BigInteger

object EvmAbi {
    const val ERC20_BALANCE_OF_SELECTOR = "70a08231"
    const val ERC20_TRANSFER_SELECTOR = "a9059cbb"
    const val ERC20_TRANSFER_TOPIC =
        "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"

    fun balanceOfCallData(address: String): String =
        "0x$ERC20_BALANCE_OF_SELECTOR${addressToAbiWord(address)}"

    fun transferCallData(
        toAddress: String,
        rawAmount: BigInteger,
    ): String {
        require(rawAmount >= BigInteger.ZERO) {
            "Transfer amount cannot be negative."
        }
        return "0x$ERC20_TRANSFER_SELECTOR${addressToAbiWord(toAddress)}${rawAmount.toUint256Word()}"
    }

    fun addressToTopic(address: String): String =
        "0x${addressToAbiWord(address)}"

    fun addressToAbiWord(address: String): String {
        val normalized = normalizeAddress(address)
        return normalized.padStart(64, '0')
    }

    fun normalizeAddress(address: String): String {
        val normalized = address.removePrefix("0x").removePrefix("0X").lowercase()
        require(normalized.length == 40 && normalized.all(Char::isHexDigit)) {
            "Invalid EVM address: $address"
        }
        return normalized
    }

    fun decodeUint256(hex: String): BigInteger {
        val normalized = hex.removePrefix("0x").removePrefix("0X").ifEmpty { "0" }
        require(normalized.length <= 64 && normalized.all(Char::isHexDigit)) {
            "Invalid uint256 hex value: $hex"
        }
        return BigInteger(normalized, 16)
    }

    fun hexToLong(hex: String): Long {
        val normalized = hex.removePrefix("0x").removePrefix("0X").ifEmpty { "0" }
        return BigInteger(normalized, 16).toLong()
    }

    fun quantityHex(value: BigInteger): String {
        require(value >= BigInteger.ZERO) {
            "JSON-RPC quantity cannot be negative."
        }
        return "0x${value.toString(16)}"
    }

    fun quantityHex(value: Long): String =
        quantityHex(BigInteger.valueOf(value))

    fun rawToDecimalString(
        raw: BigInteger,
        decimals: Int,
    ): String {
        val decimal = BigDecimal(raw, decimals).stripTrailingZeros()
        return if (decimal.compareTo(BigDecimal.ZERO) == 0) "0" else decimal.toPlainString()
    }

    fun rawToDecimalString(
        raw: String,
        decimals: Int,
    ): String = rawToDecimalString(BigInteger(raw), decimals)

    private fun BigInteger.toUint256Word(): String {
        require(bitLength() <= 256) {
            "Value exceeds uint256."
        }
        return toString(16).padStart(64, '0')
    }
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
