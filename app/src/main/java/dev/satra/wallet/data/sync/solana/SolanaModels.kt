package dev.satra.wallet.data.sync.solana

import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionStatus
import dev.satra.wallet.data.sync.evm.EvmSyncCompleteness
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class SolanaRpcProvider(
    val name: String,
    val rpcUrl: String,
)

data class SolanaNetworkConfig(
    val networkId: String,
    val nativeAssetId: String,
    val nativeDecimals: Int,
    val providers: List<SolanaRpcProvider>,
)

data class SolanaRpcCallResult<T>(
    val value: T,
    val provider: SolanaRpcProvider,
    val slot: Long? = null,
)

data class SolanaTokenAccount(
    val address: String,
    val owner: String,
    val mint: String,
    val amountRaw: BigInteger,
    val decimals: Int,
    val programId: String,
)

data class SolanaAssetBalance(
    val assetId: String,
    val networkId: String,
    val balanceRaw: String,
    val balanceDecimal: String,
    val providerName: String,
    val slot: Long,
    val syncedAtMillis: Long,
    val tokenAccounts: List<SolanaTokenAccount> = emptyList(),
)

data class SolanaNetworkBalanceResult(
    val balances: List<SolanaAssetBalance>,
    val tokenAccounts: List<SolanaTokenAccount>,
    val providerName: String?,
    val latestSlot: Long?,
    val completeness: EvmSyncCompleteness,
    val error: String?,
)

data class SolanaNormalizedTransaction(
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

data class SolanaNetworkHistoryResult(
    val transactions: List<SolanaNormalizedTransaction>,
    val providerName: String?,
    val latestSlot: Long?,
    val completeness: EvmSyncCompleteness,
    val cursorBeforeSignature: String?,
    val error: String?,
)

data class SolanaNetworkSyncResult(
    val walletId: String,
    val networkId: String,
    val address: String?,
    val tokenAccountCount: Int,
    val balanceCompleteness: EvmSyncCompleteness,
    val historyCompleteness: EvmSyncCompleteness,
    val balances: List<SolanaAssetBalance>,
    val transactions: List<SolanaNormalizedTransaction>,
    val providerName: String?,
    val latestSlot: Long?,
    val cursorBeforeSignature: String?,
    val error: String?,
)

data class SolanaWalletSyncResult(
    val walletId: String,
    val networkResults: List<SolanaNetworkSyncResult>,
) {
    val syncedNetworkCount: Int = networkResults.count {
        it.balanceCompleteness != EvmSyncCompleteness.Failed ||
            it.historyCompleteness != EvmSyncCompleteness.Failed
    }
}

internal fun BigInteger.toSolanaDecimalString(decimals: Int): String =
    BigDecimal(this)
        .movePointLeft(decimals)
        .setScale(decimals, RoundingMode.DOWN)
        .stripTrailingZeros()
        .toPlainString()

internal fun String.toBigIntegerOrZero(): BigInteger =
    runCatching { BigInteger(this) }.getOrDefault(BigInteger.ZERO)

