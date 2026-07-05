package dev.satra.wallet.data.db

import dev.satra.wallet.data.send.crypto.SatraSigningCrypto

internal object WalletPrivateKeyBackupFormatter {
    fun backupValue(
        networkId: String,
        privateKeyHex: String,
    ): String {
        val prefix = wifPrefix(networkId) ?: return privateKeyHex
        val privateKeyBytes = privateKeyHex.privateKeyBytes()
        return SatraSigningCrypto.base58CheckEncode(
            byteArrayOf(prefix.toByte()) + privateKeyBytes + byteArrayOf(0x01),
        )
    }

    fun backupFormat(
        networkId: String,
        fallbackFormat: String,
    ): String =
        if (wifPrefix(networkId) != null) {
            "WIF"
        } else {
            fallbackFormat.ifBlank { "Private key" }
        }

    private fun wifPrefix(networkId: String): Int? =
        when (networkId) {
            "bitcoin", "bitcoinCash" -> 0x80
            "dogecoin" -> 0x9e
            "litecoin" -> 0xb0
            else -> null
        }

    private fun String.privateKeyBytes(): ByteArray {
        val normalized = removePrefix("0x").removePrefix("0X")
        require(normalized.length == PrivateKeyHexLength && normalized.all(Char::isHexDigitForBackup)) {
            "Invalid private key backup value."
        }
        return ByteArray(PrivateKeyByteLength) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private const val PrivateKeyByteLength = 32
    private const val PrivateKeyHexLength = PrivateKeyByteLength * 2
}

private fun Char.isHexDigitForBackup(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
