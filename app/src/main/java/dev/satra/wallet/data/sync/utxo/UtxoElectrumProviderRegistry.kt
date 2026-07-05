package dev.satra.wallet.data.sync.utxo

object UtxoElectrumProviderRegistry {
    val networks: Map<String, UtxoNetworkConfig> = listOf(
        UtxoNetworkConfig(
            networkId = "bitcoin",
            nativeAssetId = "bitcoin:btc",
            decimals = 8,
            providers = listOf(
                UtxoElectrumProvider("Blockstream", "blockstream.info", 700, tls = true),
                UtxoElectrumProvider("Fulcrum Not FYI", "fulcrum2.not.fyi", 51002, tls = true),
                UtxoElectrumProvider("0xRPC", "0xrpc.io", 50002, tls = true),
                UtxoElectrumProvider("Snel", "electrum2.snel.it", 50002, tls = true),
                UtxoElectrumProvider("Cake Wallet", "electrum.cakewallet.com", 50002, tls = true),
            ),
        ),
        UtxoNetworkConfig(
            networkId = "bitcoinCash",
            nativeAssetId = "bitcoinCash:bch",
            decimals = 8,
            providers = listOf(
                UtxoElectrumProvider("Electron Cash DK", "electroncash.dk", 50002, tls = true),
                UtxoElectrumProvider("Imaginary Cash", "bch.imaginary.cash", 50002, tls = true),
                UtxoElectrumProvider("Stack Wallet", "bitcoincash.stackwallet.com", 50002, tls = true),
                UtxoElectrumProvider("Cyberbits", "bch.cyberbits.eu", 50002, tls = true),
                UtxoElectrumProvider("1209k Fulcrum Cash", "fulcrum-cash.1209k.com", 50002, tls = true),
            ),
        ),
        UtxoNetworkConfig(
            networkId = "dogecoin",
            nativeAssetId = "dogecoin:doge",
            decimals = 8,
            providers = listOf(
                UtxoElectrumProvider("Aftrek", "doge.aftrek.org", 50002, tls = true),
                UtxoElectrumProvider("CIPIG 1", "electrum1.cipig.net", 20060, tls = true),
                UtxoElectrumProvider("CIPIG 2", "electrum2.cipig.net", 20060, tls = true),
                UtxoElectrumProvider("Stack Wallet", "dogecoin.stackwallet.com", 50022, tls = true),
            ),
        ),
        UtxoNetworkConfig(
            networkId = "litecoin",
            nativeAssetId = "litecoin:ltc",
            decimals = 8,
            providers = listOf(
                UtxoElectrumProvider("Petrkr", "electrum-ltc.petrkr.net", 60002, tls = true),
                UtxoElectrumProvider("Xurious", "electrum.ltc.xurious.com", 50002, tls = true),
                UtxoElectrumProvider("CIPIG 1", "electrum1.cipig.net", 20063, tls = true),
                UtxoElectrumProvider("CIPIG 2", "electrum2.cipig.net", 20063, tls = true),
                UtxoElectrumProvider("Electrum LTC Backup", "backup.electrum-ltc.org", 50002, tls = true),
                UtxoElectrumProvider("0xRPC", "0xrpc.io", 60002, tls = true),
            ),
        ),
    ).associateBy { it.networkId }

    val supportedNetworkIds: Set<String> = networks.keys

    fun requireConfig(networkId: String): UtxoNetworkConfig =
        networks[networkId] ?: error("Unsupported Bitcoin-family network: $networkId")
}
