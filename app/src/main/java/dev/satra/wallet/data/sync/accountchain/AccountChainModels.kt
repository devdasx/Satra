package dev.satra.wallet.data.sync.accountchain

import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionStatus
import dev.satra.wallet.data.sync.evm.EvmSyncCompleteness
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class AccountChainProvider(
    val name: String,
    val baseUrl: String,
)

data class AccountChainNetworkConfig(
    val networkId: String,
    val nativeAssetId: String,
    val nativeDecimals: Int,
    val providers: List<AccountChainProvider>,
)

data class AccountChainAssetBalance(
    val assetId: String,
    val networkId: String,
    val balanceRaw: String,
    val balanceDecimal: String,
    val providerName: String,
    val ledgerHeight: Long?,
    val syncedAtMillis: Long,
)

data class AccountChainBalanceResult(
    val balances: List<AccountChainAssetBalance>,
    val providerName: String?,
    val latestLedger: Long?,
    val completeness: EvmSyncCompleteness,
    val error: String?,
)

data class AccountChainNormalizedTransaction(
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

data class AccountChainHistoryResult(
    val transactions: List<AccountChainNormalizedTransaction>,
    val providerName: String?,
    val latestLedger: Long?,
    val completeness: EvmSyncCompleteness,
    val cursor: String?,
    val error: String?,
)

data class AccountChainNetworkSyncResult(
    val walletId: String,
    val networkId: String,
    val address: String?,
    val balanceCompleteness: EvmSyncCompleteness,
    val historyCompleteness: EvmSyncCompleteness,
    val balances: List<AccountChainAssetBalance>,
    val transactions: List<AccountChainNormalizedTransaction>,
    val providerName: String?,
    val latestLedger: Long?,
    val cursor: String?,
    val error: String?,
)

data class AccountChainWalletSyncResult(
    val walletId: String,
    val networkResults: List<AccountChainNetworkSyncResult>,
) {
    val syncedNetworkCount: Int = networkResults.count {
        it.balanceCompleteness != EvmSyncCompleteness.Failed ||
            it.historyCompleteness != EvmSyncCompleteness.Failed
    }
}

internal fun BigInteger.toAccountDecimalString(decimals: Int): String =
    BigDecimal(this)
        .movePointLeft(decimals)
        .setScale(decimals, RoundingMode.DOWN)
        .stripTrailingZeros()
        .toPlainString()

internal fun BigDecimal.toRawAccountAmount(decimals: Int): String =
    movePointRight(decimals)
        .setScale(0, RoundingMode.DOWN)
        .toPlainString()

internal fun String.toAccountBigIntegerOrZero(): BigInteger =
    runCatching { BigInteger(this) }.getOrDefault(BigInteger.ZERO)

