package dev.satra.wallet.wallet.derivation

import dev.satra.wallet.data.assets.SupportedAssetCatalog
import org.junit.Assert.assertEquals
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

    private companion object {
        const val TEST_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    }
}
