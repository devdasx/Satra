package dev.satra.wallet.data.sync.utxo

import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionStatus
import dev.satra.wallet.data.sync.evm.EvmSyncCompleteness
import dev.satra.wallet.wallet.derivation.DerivedReceiveAccount

data class UtxoElectrumProvider(
    val name: String,
    val host: String,
    val port: Int,
    val tls: Boolean,
)

data class UtxoNetworkConfig(
    val networkId: String,
    val nativeAssetId: String,
    val decimals: Int,
    val providers: List<UtxoElectrumProvider>,
)

data class UtxoWatchedScript(
    val walletAddressId: String?,
    val networkId: String,
    val address: String,
    val scriptPubKeyHex: String,
    val scriptHash: String,
    val derivationPath: String?,
    val derivationName: String?,
    val addressIndex: Int?,
    val isChange: Boolean,
)

data class UtxoScriptBalance(
    val confirmedSats: Long,
    val unconfirmedSats: Long,
)

data class UtxoUnspentOutput(
    val transactionHash: String,
    val outputIndex: Int,
    val valueSats: Long,
    val height: Long,
    val address: String,
    val scriptPubKeyHex: String,
    val derivationPath: String?,
    val isChange: Boolean,
)

data class UtxoAssetBalance(
    val assetId: String,
    val networkId: String,
    val balanceRaw: String,
    val balanceDecimal: String,
    val providerName: String,
    val blockHeight: Long,
    val syncedAtMillis: Long,
    val utxos: List<UtxoUnspentOutput>,
)

data class UtxoNormalizedTransaction(
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
    val timestampMillis: Long,
    val providerName: String,
    val metadataJson: String,
)

data class UtxoNetworkSyncResult(
    val walletId: String,
    val networkId: String,
    val addressesScanned: Int,
    val balanceCompleteness: EvmSyncCompleteness,
    val historyCompleteness: EvmSyncCompleteness,
    val balances: List<UtxoAssetBalance>,
    val transactions: List<UtxoNormalizedTransaction>,
    val providerName: String?,
    val latestBlockHeight: Long?,
    val scannedAccounts: List<DerivedReceiveAccount>,
    val error: String?,
)

data class UtxoWalletSyncResult(
    val walletId: String,
    val networkResults: List<UtxoNetworkSyncResult>,
) {
    val syncedNetworkCount: Int = networkResults.count {
        it.balanceCompleteness != EvmSyncCompleteness.Failed ||
            it.historyCompleteness != EvmSyncCompleteness.Failed
    }
}

data class UtxoElectrumSnapshot(
    val provider: UtxoElectrumProvider,
    val latestBlockHeight: Long,
    val balancesByScriptHash: Map<String, UtxoScriptBalance>,
    val historiesByScriptHash: Map<String, List<UtxoHistoryEntry>>,
    val unspentByScriptHash: Map<String, List<UtxoUnspentOutput>>,
)

data class UtxoHistoryEntry(
    val transactionHash: String,
    val height: Long,
)

data class UtxoVerboseTransaction(
    val hex: String,
    val blockHash: String?,
    val confirmations: Int?,
    val timestampMillis: Long?,
)

data class UtxoBlockHeader(
    val height: Long,
    val blockHash: String?,
    val timestampMillis: Long,
)
