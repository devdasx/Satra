package dev.satra.wallet.data.sync.accountchain

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.satra.wallet.data.db.WalletAddressRecord
import dev.satra.wallet.data.sync.evm.EvmSyncCompleteness
import dev.satra.wallet.wallet.derivation.SatraAddressDerivation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountChainPublicProviderInstrumentedTest {
    @Test
    fun registryMatchesSupportedAccountChainNetworks() {
        AccountChainProviderRegistry.validateAgainstSupportedCatalog()
    }

    @Test
    fun publicAccountChainProvidersReturnRealBalancesAndAvailableHistory() = runBlocking {
        val service = AccountChainWalletSyncService(maxHistoryItemsPerNetwork = 20)
        val derivedAddresses = SatraAddressDerivation
            .deriveReceiveAccounts(FIXTURE_MNEMONIC)
            .associate { it.networkId to it.address }
        val fixtures = listOf(
            Fixture("tron", "TXxBHGHDPmoPCFMnMWeEQJeFT6VCQRq7VL", expectsHistory = true),
            Fixture(
                "aptos",
                "0x84dac2e49b9b8d37f0d96eb647906c3dce8e89010e07c6009f6ae3a8da04b813",
                expectsHistory = true,
            ),
            Fixture("near", "aurora", expectsHistory = true),
            Fixture("polkadot", derivedAddresses.getValue("polkadot"), expectsHistory = false),
            Fixture("ripple", "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh", expectsHistory = true),
            Fixture("stellar", "GA5XIGA5C7QTPTWXQHY6MCJRMTRZDOSHR6EFIBNDQTCQHG262N4GGKTM", expectsHistory = true),
            Fixture("sui", "0x7ba661f0e68dcfa31c8c20f21d42bc0b973920ee79904bd5683c9259a9925fdf", expectsHistory = true),
            Fixture("ton", "UQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAJKZ", expectsHistory = true),
            Fixture("kava", derivedAddresses.getValue("kava"), expectsHistory = false),
        )

        fixtures.forEach { fixture ->
            val result = service.syncWallet(
                walletId = "fixture-${fixture.networkId}",
                addresses = listOf(address(fixture.networkId, fixture.address)),
                networkId = fixture.networkId,
            )
            val network = result.networkResults.single()

            assertEquals(fixture.networkId, network.networkId)
            assertTrue(
                "Expected ${fixture.networkId} balance sync not to fail: ${network.error}",
                network.balanceCompleteness != EvmSyncCompleteness.Failed,
            )
            assertTrue(
                "Expected ${fixture.networkId} to return at least one balance row.",
                network.balances.isNotEmpty(),
            )
            if (fixture.expectsHistory) {
                assertTrue(
                    "Expected ${fixture.networkId} history sync not to fail: ${network.error}",
                    network.historyCompleteness != EvmSyncCompleteness.Failed,
                )
                assertTrue(
                    "Expected ${fixture.networkId} public fixture to return normalized transactions: ${network.error}",
                    network.transactions.isNotEmpty(),
                )
            }
        }
    }

    private fun address(
        networkId: String,
        value: String,
    ): WalletAddressRecord =
        WalletAddressRecord(
            addressId = "$networkId-fixture-address",
            walletId = "fixture-$networkId",
            networkId = networkId,
            address = value,
            addressType = "receive",
            derivationPath = null,
            publicKey = null,
            privateKeyId = null,
            isPrimary = true,
            isChange = false,
            addressIndex = 0,
            label = null,
            createdAt = 1L,
            updatedAt = 1L,
            lastUsedAt = null,
            metadataJson = "{}",
        )

    private data class Fixture(
        val networkId: String,
        val address: String,
        val expectsHistory: Boolean,
    )

    private companion object {
        const val FIXTURE_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    }
}
