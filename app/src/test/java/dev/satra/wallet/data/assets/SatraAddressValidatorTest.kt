package dev.satra.wallet.data.assets

import dev.satra.wallet.wallet.derivation.SatraAddressDerivation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SatraAddressValidatorTest {
    @Test
    fun acceptsDerivedPrimaryAddressForEverySupportedNetwork() {
        val addressesByNetwork = SatraAddressDerivation.deriveReceiveAccounts(TEST_MNEMONIC)
            .filter { it.isPrimary }
            .associateBy { it.networkId }

        SupportedAssetCatalog.networks.forEach { network ->
            val address = checkNotNull(addressesByNetwork[network.networkId]) {
                "Missing derived address for ${network.networkId}."
            }.address

            assertTrue(
                "Expected ${network.networkId} address to validate: $address",
                isValidAddressForNetwork(address, network),
            )
        }
    }

    @Test
    fun rejectsMalformedTextForEverySupportedNetwork() {
        SupportedAssetCatalog.networks.forEach { network ->
            assertFalse(
                "Expected ${network.networkId} to reject malformed text.",
                isValidAddressForNetwork("not a valid address!", network),
            )
        }
    }

    @Test
    fun rejectsScreenshotPlaceholderForEvmNetworks() {
        val arbitrum = SupportedAssetCatalog.networks.first { it.networkId == "arbitrum" }

        assertFalse(isValidAddressForNetwork("asdasdasd", arbitrum))
    }

    @Test
    fun rejectsSegwitAddressOnWrongBitcoinFamilyNetwork() {
        val bitcoinAddress = SatraAddressDerivation.deriveReceiveAccounts(TEST_MNEMONIC)
            .first { it.networkId == "bitcoin" && it.isPrimary }
            .address
        val dogecoin = SupportedAssetCatalog.networks.first { it.networkId == "dogecoin" }

        assertFalse(isValidAddressForNetwork(bitcoinAddress, dogecoin))
    }

    private companion object {
        const val TEST_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    }
}
