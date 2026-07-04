package dev.satra.wallet.data.sync.utxo

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UtxoPublicElectrumInstrumentedTest {
    @Test
    fun allBitcoinFamilyNetworksReturnRealBalanceUtxoAndHistoryData() = runBlocking {
        UtxoElectrumProviderRegistry.networks.values.forEach { config ->
            val fixtureAddress = checkNotNull(FIXTURE_ADDRESSES[config.networkId]) {
                "Missing fixture for ${config.networkId}."
            }
            val watchedScript = UtxoScript.watchedScriptForAddress(
                networkId = config.networkId,
                address = fixtureAddress,
            )

            val result = config.providers.firstNotNullOfOrNull { provider ->
                runCatching {
                    val client = UtxoElectrumClient(timeoutMillis = 15_000)
                    client.serverVersion(provider)
                    ElectrumFixtureResult(
                        providerName = provider.name,
                        blockHeight = client.latestBlockHeight(provider),
                        balance = client.getBalances(provider, listOf(watchedScript.scriptHash))
                            .getValue(watchedScript.scriptHash),
                        history = client.getHistories(provider, listOf(watchedScript.scriptHash))
                            .getValue(watchedScript.scriptHash),
                        utxos = client.listUnspent(provider, listOf(watchedScript))
                            .getValue(watchedScript.scriptHash),
                    )
                }.getOrNull()
            } ?: error("No public Electrum provider worked for ${config.networkId}.")

            assertTrue(
                "Expected positive block height for ${config.networkId} via ${result.providerName}",
                result.blockHeight > 0L,
            )
            assertTrue(
                "Expected non-negative balance for ${config.networkId} via ${result.providerName}",
                result.balance.confirmedSats >= 0L && result.balance.unconfirmedSats >= 0L,
            )
            assertTrue(
                "Expected fixture history for ${config.networkId} via ${result.providerName}",
                result.history.isNotEmpty(),
            )
            assertTrue(
                "Expected valid UTXO response for ${config.networkId} via ${result.providerName}",
                result.utxos.all { it.valueSats >= 0L && it.outputIndex >= 0 },
            )
        }
    }

    private companion object {
        val FIXTURE_ADDRESSES = mapOf(
            "bitcoin" to "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
            "bitcoinCash" to "3433ypM7tMDw4TRZMm2gH9zHhPzs5A4PHw",
            "dogecoin" to "DH5yaieqoZN36fDVciNyRueRGvGLR3mr7L",
            "litecoin" to "MQd1fJwqBJvwLuyhr17PhEFx1swiqDbPQS",
        )
    }
}

private data class ElectrumFixtureResult(
    val providerName: String,
    val blockHeight: Long,
    val balance: UtxoScriptBalance,
    val history: List<UtxoHistoryEntry>,
    val utxos: List<UtxoUnspentOutput>,
)
