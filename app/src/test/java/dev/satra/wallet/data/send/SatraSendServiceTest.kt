package dev.satra.wallet.data.send

import dev.satra.wallet.data.assets.SupportedAssetCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SatraSendServiceTest {
    @Test
    fun sendSupportMatchesImplementedBroadcastHandlers() {
        assertTrue(canSend("ethereum:usdt"))
        assertTrue(canSend("tron:usdt"))
        assertTrue(canSend("solana:usdt"))
        assertTrue(canSend("bitcoin:btc"))
        assertTrue(canSend("aptos:apt"))

        assertFalse(canSend("polkadot:dot"))
        assertFalse(canSend("polkadot:usdc"))
        assertFalse(canSend("ton:ton"))
        assertFalse(canSend("ton:usdt"))
        assertFalse(canSend("ripple:rlusd"))
    }

    private fun canSend(assetId: String): Boolean {
        val asset = SupportedAssetCatalog.assets.first { it.assetId == assetId }
        val network = SupportedAssetCatalog.networks.first { it.networkId == asset.networkId }
        return SatraSendService.canSignAndBroadcast(asset, network)
    }
}
