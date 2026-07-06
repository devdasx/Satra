package dev.satra.wallet.data.send

import dev.satra.wallet.data.assets.SupportedAssetCatalog
import org.junit.Assert.assertTrue
import org.junit.Test

class SatraSendServiceTest {
    @Test
    fun sendSupportMatchesImplementedBroadcastHandlers() {
        assertTrue(canSend("ethereum:usdt"))
        assertTrue(canSend("bitcoin:btc"))
        assertTrue(canSend("bitcoinCash:bch"))
        assertTrue(canSend("dogecoin:doge"))
        assertTrue(canSend("litecoin:ltc"))
        assertTrue(canSend("aptos:apt"))
        assertTrue(canSend("aptos:usdt"))
        assertTrue(canSend("near:near"))
        assertTrue(canSend("near:usdt"))
        assertTrue(canSend("polkadot:dot"))
        assertTrue(canSend("polkadot:usdc"))
        assertTrue(canSend("ripple:xrp"))
        assertTrue(canSend("ripple:rlusd"))
        assertTrue(canSend("solana:sol"))
        assertTrue(canSend("solana:usdt"))
        assertTrue(canSend("stellar:xlm"))
        assertTrue(canSend("sui:sui"))
        assertTrue(canSend("ton:ton"))
        assertTrue(canSend("ton:usdt"))
        assertTrue(canSend("tron:trx"))
        assertTrue(canSend("tron:usdt"))
        assertTrue(canSend("kava:kava"))
        assertTrue(canSend("kava:usdt"))
    }

    private fun canSend(assetId: String): Boolean {
        val asset = SupportedAssetCatalog.assets.first { it.assetId == assetId }
        val network = SupportedAssetCatalog.networks.first { it.networkId == asset.networkId }
        return SatraSendService.canSignAndBroadcast(asset, network)
    }
}
