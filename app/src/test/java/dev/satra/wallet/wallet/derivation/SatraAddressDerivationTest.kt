package dev.satra.wallet.wallet.derivation

import dev.satra.wallet.data.assets.SupportedAssetCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SatraAddressDerivationTest {
    @Test
    fun derivesReceiveAccountsForEverySupportedNetworkPlusSolanaPhantom() {
        val accounts = SatraAddressDerivation.deriveReceiveAccounts(TEST_MNEMONIC)

        assertEquals(SupportedAssetCatalog.networks.size + 1, accounts.size)
        SupportedAssetCatalog.networks.forEach { network ->
            assertTrue(
                "Missing ${network.networkId}",
                accounts.any { it.networkId == network.networkId },
            )
        }
        assertEquals(2, accounts.count { it.networkId == "solana" })
        assertNotEquals(
            accounts.first { it.networkId == "solana" && it.derivationName == "trust" }.address,
            accounts.first { it.networkId == "solana" && it.derivationName == "phantom" }.address,
        )
    }

    @Test
    fun rendersExpectedAddressFormats() {
        val byNetwork = SatraAddressDerivation.deriveReceiveAccounts(TEST_MNEMONIC)
            .filter { it.isPrimary }
            .associateBy { it.networkId }

        assertEquals("0x9858effd232b4033e47d90003d41ec34ecaeda94", byNetwork.getValue("ethereum").address)
        assertTrue(byNetwork.getValue("bitcoin").address.startsWith("bc1"))
        assertTrue(byNetwork.getValue("bitcoinCash").address.startsWith("bitcoincash:"))
        assertTrue(byNetwork.getValue("dogecoin").address.startsWith("D"))
        assertTrue(byNetwork.getValue("litecoin").address.startsWith("ltc1"))
        assertTrue(byNetwork.getValue("ripple").address.startsWith("r"))
        assertTrue(byNetwork.getValue("tron").address.startsWith("T"))
        assertTrue(byNetwork.getValue("kava").address.startsWith("kava1"))
        assertTrue(byNetwork.getValue("stellar").address.startsWith("G"))
        assertTrue(byNetwork.getValue("ton").address.startsWith("UQ"))
        assertTrue(byNetwork.values.all { it.address.isNotBlank() })
    }

    @Test
    fun validatesImportedSecp256k1HexPrivateKey() {
        val validation = SatraAddressDerivation.validatePrivateKeyImport(
            networkId = "ethereum",
            privateKey = "0x0000000000000000000000000000000000000000000000000000000000000001",
        )

        assertTrue(validation.isValid)
        assertEquals("0x7e5f4552091a69125d5dfcb7b8c2659029395bdf", validation.account?.address)
        assertEquals(
            "0000000000000000000000000000000000000000000000000000000000000001",
            validation.account?.privateKeyHex,
        )
    }

    @Test
    fun validatesBitcoinFamilyWifPrivateKey() {
        val validation = SatraAddressDerivation.validatePrivateKeyImport(
            networkId = "bitcoin",
            privateKey = "KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU73sVHnoWn",
        )

        assertTrue(validation.isValid)
        assertTrue(validation.account?.address.orEmpty().startsWith("bc1"))
        assertEquals(
            "0000000000000000000000000000000000000000000000000000000000000001",
            validation.account?.privateKeyHex,
        )
    }

    @Test
    fun validatesImportedEd25519HexPrivateKey() {
        val validation = SatraAddressDerivation.validatePrivateKeyImport(
            networkId = "solana",
            privateKey = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
        )

        assertTrue(validation.isValid)
        assertTrue(validation.account?.address.orEmpty().isNotBlank())
        assertEquals(
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
            validation.account?.privateKeyHex,
        )
    }

    @Test
    fun rejectsInvalidImportedPrivateKeys() {
        assertFalse(
            SatraAddressDerivation.validatePrivateKeyImport(
                networkId = "ethereum",
                privateKey = "0x0000000000000000000000000000000000000000000000000000000000000000",
            ).isValid,
        )
        assertFalse(
            SatraAddressDerivation.validatePrivateKeyImport(
                networkId = "bitcoin",
                privateKey = "not-a-real-private-key",
            ).isValid,
        )
        assertFalse(
            SatraAddressDerivation.validatePrivateKeyImport(
                networkId = "solana",
                privateKey = "short",
            ).isValid,
        )
    }

    private companion object {
        const val TEST_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    }
}
