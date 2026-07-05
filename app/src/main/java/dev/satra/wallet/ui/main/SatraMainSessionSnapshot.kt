package dev.satra.wallet.ui.main

import dev.satra.wallet.data.db.AssetMarketDataRecord
import dev.satra.wallet.data.db.SatraWalletRepository
import dev.satra.wallet.data.db.WalletAddressRecord
import dev.satra.wallet.data.db.WalletAssetRecord
import dev.satra.wallet.data.db.WalletPrivateKeyRecord
import dev.satra.wallet.data.db.WalletRecord
import dev.satra.wallet.data.db.WalletTransactionRecord
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class SatraMainWalletSnapshot(
    val wallet: WalletRecord?,
    val assets: List<WalletAssetRecord> = emptyList(),
    val addresses: List<WalletAddressRecord> = emptyList(),
    val privateKeys: List<WalletPrivateKeyRecord> = emptyList(),
    val transactions: List<WalletTransactionRecord> = emptyList(),
) {
    val walletId: String
        get() = wallet?.walletId.orEmpty()
}

data class SatraMainMarketSnapshot(
    val localCurrencyCode: String,
    val marketData: List<AssetMarketDataRecord>,
)

internal suspend fun SatraWalletRepository.loadMainWalletSnapshot(
    ensureReceiveAddresses: Boolean = true,
    includePrivateKeys: Boolean = true,
    includeTransactions: Boolean = true,
): SatraMainWalletSnapshot =
    coroutineScope {
        val wallet = getPrimaryWallet() ?: return@coroutineScope SatraMainWalletSnapshot(wallet = null)
        val addressesDeferred = async {
            if (ensureReceiveAddresses) {
                ensureMnemonicReceiveAddresses(wallet.walletId)
                    .ifEmpty { getWalletAddresses(wallet.walletId) }
            } else {
                getWalletAddresses(wallet.walletId)
            }
        }
        val assetsDeferred = async { getWalletAssets(wallet.walletId) }
        val privateKeysDeferred = async {
            if (includePrivateKeys) getWalletPrivateKeys(wallet.walletId) else emptyList()
        }
        val transactionsDeferred = async {
            if (includeTransactions) getWalletTransactions(wallet.walletId) else emptyList()
        }
        SatraMainWalletSnapshot(
            wallet = wallet,
            assets = assetsDeferred.await(),
            addresses = addressesDeferred.await(),
            privateKeys = privateKeysDeferred.await(),
            transactions = transactionsDeferred.await(),
        )
    }

internal suspend fun SatraWalletRepository.loadMainMarketSnapshot(
    localCurrencyCode: String,
): SatraMainMarketSnapshot =
    SatraMainMarketSnapshot(
        localCurrencyCode = localCurrencyCode,
        marketData = getAllAssetMarketData(),
    )
