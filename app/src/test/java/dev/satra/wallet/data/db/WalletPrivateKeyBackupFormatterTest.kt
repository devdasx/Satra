package dev.satra.wallet.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletPrivateKeyBackupFormatterTest {
    @Test
    fun exportsBitcoinFamilyKeysAsCompressedWif() {
        assertEquals(BitcoinWif, WalletPrivateKeyBackupFormatter.backupValue("bitcoin", OneHex))
        assertEquals(BitcoinWif, WalletPrivateKeyBackupFormatter.backupValue("bitcoinCash", OneHex))
        assertEquals(DogecoinWif, WalletPrivateKeyBackupFormatter.backupValue("dogecoin", OneHex))
        assertEquals(LitecoinWif, WalletPrivateKeyBackupFormatter.backupValue("litecoin", OneHex))
    }

    @Test
    fun keepsNonBitcoinFamilyKeysAsPrivateKeyHex() {
        assertEquals(OneHex, WalletPrivateKeyBackupFormatter.backupValue("ethereum", OneHex))
        assertEquals(OneHex, WalletPrivateKeyBackupFormatter.backupValue("solana", OneHex))
    }

    @Test
    fun labelsBitcoinFamilyBackupFormatAsWif() {
        assertEquals("WIF", WalletPrivateKeyBackupFormatter.backupFormat("bitcoin", "hex"))
        assertEquals("Private key", WalletPrivateKeyBackupFormatter.backupFormat("ethereum", ""))
        assertEquals("hex", WalletPrivateKeyBackupFormatter.backupFormat("ethereum", "hex"))
    }

    private companion object {
        const val OneHex = "0000000000000000000000000000000000000000000000000000000000000001"
        const val BitcoinWif = "KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU73sVHnoWn"
        const val DogecoinWif = "QNcdLVw8fHkixm6NNyN6nVwxKek4u7qrioRbQmjxac5TVoTtZuot"
        const val LitecoinWif = "T33ydQRKp4FCW5LCLLUB7deioUMoveiwekdwUwyfRDeGZm76aUjV"
    }
}
