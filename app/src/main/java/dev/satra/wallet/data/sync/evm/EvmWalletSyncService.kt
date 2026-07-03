package dev.satra.wallet.data.sync.evm

import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.db.WalletAddressRecord

class EvmWalletSyncService(
    private val balanceSyncService: EvmBalanceSyncService = EvmBalanceSyncService(),
    private val historySyncService: EvmHistorySyncService = EvmHistorySyncService(),
) {
    suspend fun syncWallet(
        walletId: String,
        addresses: List<WalletAddressRecord>,
        networkId: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): EvmWalletSyncResult {
        val requestedNetworks = if (networkId == null) {
            addresses
                .map { it.networkId }
                .filter { it in EvmProviderRegistry.supportedNetworkIds }
                .toSet()
        } else {
            setOf(networkId).also { EvmProviderRegistry.requireConfig(networkId) }
        }

        val results = requestedNetworks
            .sorted()
            .map { evmNetworkId ->
                syncNetwork(
                    walletId = walletId,
                    networkId = evmNetworkId,
                    address = addresses.primaryAddressFor(evmNetworkId),
                    nowMillis = nowMillis,
                )
            }

        return EvmWalletSyncResult(
            walletId = walletId,
            networkResults = results,
        )
    }

    private suspend fun syncNetwork(
        walletId: String,
        networkId: String,
        address: WalletAddressRecord?,
        nowMillis: Long,
    ): EvmNetworkSyncResult {
        val assets = SupportedAssetCatalog.assets.filter { it.networkId == networkId }
        if (address == null) {
            return EvmNetworkSyncResult(
                walletId = walletId,
                networkId = networkId,
                address = null,
                balanceCompleteness = EvmSyncCompleteness.Failed,
                historyCompleteness = EvmSyncCompleteness.Failed,
                balances = emptyList(),
                transactions = emptyList(),
                providerName = null,
                latestBlockNumber = null,
                cursorFromBlock = null,
                cursorToBlock = null,
                error = "No wallet address stored for $networkId.",
            )
        }

        val balanceResult = runCatching {
            balanceSyncService.syncBalances(
                address = address.address,
                assets = assets,
                nowMillis = nowMillis,
            )
        }.getOrElse { error ->
            EvmNetworkBalanceResult(
                balances = emptyList(),
                providerName = null,
                latestBlockNumber = null,
                completeness = EvmSyncCompleteness.Failed,
                error = error.message,
            )
        }

        val historyResult = runCatching {
            historySyncService.syncHistory(
                walletId = walletId,
                address = address.address,
                assets = assets,
                latestKnownBlock = balanceResult.latestBlockNumber,
                nowMillis = nowMillis,
            )
        }.getOrElse { error ->
            EvmNetworkHistoryResult(
                transactions = emptyList(),
                providerName = null,
                latestBlockNumber = balanceResult.latestBlockNumber,
                completeness = EvmSyncCompleteness.Failed,
                cursorFromBlock = null,
                cursorToBlock = balanceResult.latestBlockNumber,
                error = error.message,
            )
        }

        return EvmNetworkSyncResult(
            walletId = walletId,
            networkId = networkId,
            address = address.address,
            balanceCompleteness = balanceResult.completeness,
            historyCompleteness = historyResult.completeness,
            balances = balanceResult.balances,
            transactions = historyResult.transactions,
            providerName = balanceResult.providerName ?: historyResult.providerName,
            latestBlockNumber = balanceResult.latestBlockNumber ?: historyResult.latestBlockNumber,
            cursorFromBlock = historyResult.cursorFromBlock,
            cursorToBlock = historyResult.cursorToBlock,
            error = listOfNotNull(balanceResult.error, historyResult.error)
                .joinToString(" | ")
                .ifBlank { null },
        )
    }
}

private fun List<WalletAddressRecord>.primaryAddressFor(networkId: String): WalletAddressRecord? =
    filter { it.networkId == networkId }
        .sortedWith(
            compareByDescending<WalletAddressRecord> { it.isPrimary }
                .thenBy { it.addressIndex ?: Int.MAX_VALUE }
                .thenBy { it.createdAt },
        )
        .firstOrNull()
