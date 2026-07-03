package dev.satra.wallet.data.sync.evm

import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionStatus

data class EvmProvider(
    val name: String,
    val rpcUrl: String,
)

data class EvmExplorerApi(
    val name: String,
    val baseUrl: String,
    val style: EvmExplorerApiStyle,
)

enum class EvmExplorerApiStyle {
    BlockscoutV2,
    EtherscanCompatible,
}

data class EvmNetworkConfig(
    val networkId: String,
    val chainId: Long,
    val providers: List<EvmProvider>,
    val explorerApis: List<EvmExplorerApi> = emptyList(),
)

data class EvmRpcCallResult<T>(
    val value: T,
    val provider: EvmProvider,
    val blockNumber: Long? = null,
)

enum class EvmSyncCompleteness(val value: String) {
    Complete("complete"),
    Partial("partial"),
    Failed("failed"),
}

data class EvmAssetBalance(
    val assetId: String,
    val networkId: String,
    val balanceRaw: String,
    val balanceDecimal: String,
    val providerName: String,
    val blockNumber: Long,
    val syncedAtMillis: Long,
)

data class EvmNormalizedTransaction(
    val walletId: String,
    val assetId: String,
    val networkId: String,
    val transactionHash: String,
    val direction: WalletTransactionDirection,
    val status: WalletTransactionStatus,
    val amountRaw: String,
    val amountDecimal: String,
    val feeRaw: String?,
    val feeDecimal: String?,
    val feeAssetId: String?,
    val fromAddress: String?,
    val toAddress: String?,
    val blockHeight: Long?,
    val blockHash: String?,
    val confirmations: Int,
    val nonce: String?,
    val timestampMillis: Long,
    val providerName: String,
    val metadataJson: String,
)

data class EvmNetworkSyncResult(
    val walletId: String,
    val networkId: String,
    val address: String?,
    val balanceCompleteness: EvmSyncCompleteness,
    val historyCompleteness: EvmSyncCompleteness,
    val balances: List<EvmAssetBalance>,
    val transactions: List<EvmNormalizedTransaction>,
    val providerName: String?,
    val latestBlockNumber: Long?,
    val cursorFromBlock: Long?,
    val cursorToBlock: Long?,
    val error: String?,
)

data class EvmWalletSyncResult(
    val walletId: String,
    val networkResults: List<EvmNetworkSyncResult>,
) {
    val syncedNetworkCount: Int = networkResults.count {
        it.balanceCompleteness != EvmSyncCompleteness.Failed ||
            it.historyCompleteness != EvmSyncCompleteness.Failed
    }
}
