package dev.satra.wallet.data.sync.evm

import dev.satra.wallet.data.db.WalletAddressRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvmWalletSyncServiceTest {
    @Test
    fun walletWideSyncIgnoresStoredNonEvmAddresses() = runBlocking {
        val service = EvmWalletSyncService(
            balanceSyncService = EvmBalanceSyncService(
                clientFactory = { error("Network should not be called for missing addresses.") },
            ),
            historySyncService = EvmHistorySyncService(
                rpcClientFactory = { error("Network should not be called for missing addresses.") },
                httpGetTransport = object : EvmHttpGetTransport {
                    override suspend fun get(url: String, timeoutMillis: Int): String =
                        error("Network should not be called for missing addresses.")
                },
            ),
        )

        val result = service.syncWallet(
            walletId = "wallet",
            addresses = listOf(
                address(networkId = "bitcoin"),
            ),
        )

        assertEquals(emptyList<String>(), result.networkResults.map { it.networkId })
    }

    @Test
    fun emitsNetworkResultAsSoonAsNetworkCompletes() = runBlocking {
        val service = EvmWalletSyncService(
            balanceSyncService = EvmBalanceSyncService(
                clientFactory = { error("Network should not be called for missing addresses.") },
            ),
            historySyncService = EvmHistorySyncService(
                rpcClientFactory = { error("Network should not be called for missing addresses.") },
                httpGetTransport = object : EvmHttpGetTransport {
                    override suspend fun get(url: String, timeoutMillis: Int): String =
                        error("Network should not be called for missing addresses.")
                },
            ),
        )
        val emitted = mutableListOf<EvmNetworkSyncResult>()

        val result = service.syncWallet(
            walletId = "wallet",
            addresses = emptyList(),
            networkId = "ethereum",
            onNetworkResult = { networkResult ->
                emitted += networkResult
            },
        )

        assertEquals(result.networkResults, emitted)
        assertEquals("ethereum", emitted.single().networkId)
        assertTrue(emitted.single().error.orEmpty().contains("No wallet address"))
    }

    private fun address(networkId: String): WalletAddressRecord =
        WalletAddressRecord(
            addressId = "$networkId-address",
            walletId = "wallet",
            networkId = networkId,
            address = "0x1111111111111111111111111111111111111111",
            addressType = "watch_only",
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
}
