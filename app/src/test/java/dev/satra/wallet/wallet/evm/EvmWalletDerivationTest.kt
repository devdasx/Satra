package dev.satra.wallet.wallet.evm

import org.junit.Assert.assertEquals
import org.junit.Test

class EvmWalletDerivationTest {
    @Test
    fun privateKeyOneDerivesKnownEthereumAddress() {
        val account = EvmWalletDerivation.privateKeyToAccount(
            "0000000000000000000000000000000000000000000000000000000000000001",
        )

        assertEquals("0x7e5f4552091a69125d5dfcb7b8c2659029395bdf", account.address)
        assertEquals(
            "0000000000000000000000000000000000000000000000000000000000000001",
            account.privateKeyHex,
        )
    }

    @Test
    fun bip39MnemonicDerivesDefaultEvmAccount() {
        val account = EvmWalletDerivation.deriveDefaultAccount(
            mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        )

        assertEquals("0x9858effd232b4033e47d90003d41ec34ecaeda94", account.address)
        assertEquals(EvmWalletDerivation.DEFAULT_EVM_DERIVATION_PATH, account.derivationPath)
    }
}
