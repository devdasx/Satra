package dev.satra.wallet.data.sync.utxo

import dev.satra.wallet.wallet.derivation.SatraAddressDerivation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtxoScriptTest {
    @Test
    fun rendersBitcoinLegacyScriptHashForGenesisAddress() {
        val script = UtxoScript.scriptPubKey(
            networkId = "bitcoin",
            address = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
        )

        assertEquals("76a91462e907b15cbf27d5425399ebf6f0fb50ebb88f1888ac", script.toHex())
        assertEquals(
            "8b01df4e368ea28f8dc0423bcf7a4923e3a12d307c875e47a0cfbf90b5c39161",
            UtxoScript.scriptHash(script),
        )
    }

    @Test
    fun derivesBitcoinGapScanAccountsForAllRequiredAddressTypes() {
        val accounts = SatraAddressDerivation.deriveUtxoScanAccounts(
            mnemonic = TEST_MNEMONIC,
            networkId = "bitcoin",
            gapLimit = 20,
        )

        assertEquals(160, accounts.size)
        assertEquals(20, accounts.count { it.derivationName == "taproot" && !it.isChange })
        assertEquals(20, accounts.count { it.derivationName == "taproot" && it.isChange })
        assertEquals(20, accounts.count { it.derivationName == "segwit" && !it.isChange })
        assertEquals(20, accounts.count { it.derivationName == "nested_segwit" && !it.isChange })
        assertEquals(20, accounts.count { it.derivationName == "legacy" && !it.isChange })
        assertTrue(accounts.first { it.derivationName == "taproot" }.address.startsWith("bc1p"))
        assertTrue(accounts.first { it.derivationName == "segwit" }.address.startsWith("bc1q"))
        assertTrue(accounts.first { it.derivationName == "nested_segwit" }.address.startsWith("3"))
        assertTrue(accounts.first { it.derivationName == "legacy" }.address.startsWith("1"))
    }

    @Test
    fun parsesSupportedBitcoinFamilyPrimaryAddresses() {
        val accounts = SatraAddressDerivation.deriveReceiveAccounts(TEST_MNEMONIC)

        listOf("bitcoin", "bitcoinCash", "dogecoin", "litecoin").forEach { networkId ->
            val account = accounts.first { it.networkId == networkId && it.isPrimary }
            val watchedScript = UtxoScript.watchedScriptForAddress(
                networkId = networkId,
                address = account.address,
            )

            assertTrue("Missing script for $networkId", watchedScript.scriptPubKeyHex.isNotBlank())
            assertEquals(64, watchedScript.scriptHash.length)
        }
    }

    private companion object {
        const val TEST_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    }
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
