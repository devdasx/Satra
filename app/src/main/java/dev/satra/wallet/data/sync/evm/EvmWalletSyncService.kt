package dev.satra.wallet.data.sync.evm

import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.db.WalletAddressRecord
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class EvmWalletSyncService(
    private val balanceSyncService: EvmBalanceSyncService = EvmBalanceSyncService(),
    private val historySyncService: EvmHistorySyncService = EvmHistorySyncService(),
    private val maxParallelNetworkSyncs: Int = 12,
) {
    suspend fun syncWallet(
        walletId: String,
        addresses: List<WalletAddressRecord>,
        networkId: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        onNetworkResult: suspend (EvmNetworkSyncResult) -> Unit = {},
    ): EvmWalletSyncResult = coroutineScope {
        val requestedNetworks = if (networkId == null) {
            addresses
                .map { it.networkId }
                .filter { it in EvmProviderRegistry.supportedNetworkIds }
                .toSet()
        } else {
            setOf(networkId).also { EvmProviderRegistry.requireConfig(networkId) }
        }

        val networkLimiter = Semaphore(maxParallelNetworkSyncs.coerceAtLeast(1))
        val results = requestedNetworks
            .sorted()
            .map { evmNetworkId ->
                async {
                    networkLimiter.withPermit {
                        val result = syncNetwork(
                            walletId = walletId,
                            networkId = evmNetworkId,
                            address = addresses.primaryAddressFor(evmNetworkId),
                            nowMillis = nowMillis,
                            onNetworkResult = onNetworkResult,
                        )
                        runCatching { onNetworkResult(result) }
                        result
                    }
                }
            }
            .awaitAll()

        EvmWalletSyncResult(
            walletId = walletId,
            networkResults = results,
        )
    }

    private suspend fun syncNetwork(
        walletId: String,
        networkId: String,
        address: WalletAddressRecord?,
        nowMillis: Long,
        onNetworkResult: suspend (EvmNetworkSyncResult) -> Unit,
    ): EvmNetworkSyncResult = coroutineScope {
        val assets = SupportedAssetCatalog.assets.filter { it.networkId == networkId }
        if (address == null) {
            return@coroutineScope EvmNetworkSyncResult(
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

        val balanceDeferred = async {
            runCatching {
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
        }

        val historyDeferred = async {
            runCatching {
                historySyncService.syncHistory(
                    walletId = walletId,
                    address = address.address,
                    assets = assets,
                    latestKnownBlock = null,
                    nowMillis = nowMillis,
                )
            }.getOrElse { error ->
                EvmNetworkHistoryResult(
                    transactions = emptyList(),
                    providerName = null,
                    latestBlockNumber = null,
                    completeness = EvmSyncCompleteness.Failed,
                    cursorFromBlock = null,
                    cursorToBlock = null,
                    error = error.message,
                )
            }
        }

        val balanceResult = balanceDeferred.await()
        runCatching {
            onNetworkResult(
                EvmNetworkSyncResult(
                    walletId = walletId,
                    networkId = networkId,
                    address = address.address,
                    balanceCompleteness = balanceResult.completeness,
                    historyCompleteness = EvmSyncCompleteness.Partial,
                    balances = balanceResult.balances,
                    transactions = emptyList(),
                    providerName = balanceResult.providerName,
                    latestBlockNumber = balanceResult.latestBlockNumber,
                    cursorFromBlock = null,
                    cursorToBlock = null,
                    error = balanceResult.error,
                ),
            )
        }
        val historyResult = historyDeferred.await()

        EvmNetworkSyncResult(
            walletId = walletId,
            networkId = networkId,
            address = address.address,
            balanceCompleteness = balanceResult.completeness,
            historyCompleteness = historyResult.completeness,
            balances = balanceResult.balances,
            transactions = historyResult.transactions,
            providerName = balanceResult.providerName ?: historyResult.providerName,
            latestBlockNumber = maxOfOrNull(balanceResult.latestBlockNumber, historyResult.latestBlockNumber),
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

private fun maxOfOrNull(
    first: Long?,
    second: Long?,
): Long? =
    listOfNotNull(first, second).maxOrNull()
