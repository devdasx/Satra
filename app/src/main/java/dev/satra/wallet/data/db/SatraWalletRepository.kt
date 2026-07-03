package dev.satra.wallet.data.db

import android.content.Context
import dev.satra.wallet.data.sync.evm.EvmAssetBalance
import dev.satra.wallet.data.sync.evm.EvmNetworkSyncResult
import dev.satra.wallet.data.sync.evm.EvmNormalizedTransaction
import dev.satra.wallet.data.sync.evm.EvmWalletSyncResult
import dev.satra.wallet.data.sync.evm.EvmWalletSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SatraWalletRepository(
    private val walletDao: SatraWalletDao,
    private val evmWalletSyncService: EvmWalletSyncService = EvmWalletSyncService(),
) {
    fun createMnemonicWallet(
        walletName: String,
        mnemonic: String,
        passphrase: String? = null,
        isBackedUp: Boolean = false,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
        metadataJson: String = EMPTY_JSON,
    ): String =
        walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Standard.value,
                walletKeyType = WalletKeyType.Mnemonic.value,
                walletKeyMaterial = mnemonic,
                passphrase = passphrase.cleanPassphrase(),
                localCurrencyCode = localCurrencyCode,
                isBackedUp = isBackedUp,
                isImported = false,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )

    fun importMnemonicWallet(
        walletName: String,
        mnemonic: String,
        passphrase: String? = null,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
        metadataJson: String = EMPTY_JSON,
    ): String =
        walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Imported.value,
                walletKeyType = WalletKeyType.Mnemonic.value,
                walletKeyMaterial = mnemonic,
                passphrase = passphrase.cleanPassphrase(),
                localCurrencyCode = localCurrencyCode,
                isImported = true,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )

    fun importPrivateKeyWallet(
        walletName: String,
        networkId: String,
        privateKey: String,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
        metadataJson: String = EMPTY_JSON,
    ): String {
        val walletId = walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Imported.value,
                walletKeyType = WalletKeyType.PrivateKey.value,
                walletKeyMaterial = privateKey,
                localCurrencyCode = localCurrencyCode,
                isImported = true,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )
        walletDao.insertWalletPrivateKey(
            NewWalletPrivateKeyRecord(
                walletId = walletId,
                networkId = networkId,
                keyMaterial = privateKey,
                keyFormat = inferPrivateKeyFormat(privateKey),
                keySource = WalletPrivateKeySource.Imported.value,
                isImported = true,
            ),
        )
        return walletId
    }

    fun importWatchOnlyWallet(
        walletName: String,
        networkId: String,
        address: String,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
        metadataJson: String = EMPTY_JSON,
    ): String {
        val walletId = walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.WatchOnly.value,
                walletKeyType = WalletKeyType.Address.value,
                walletKeyMaterial = address,
                localCurrencyCode = localCurrencyCode,
                isImported = true,
                isWatchOnly = true,
                metadataJson = metadataJson,
            ),
        )
        walletDao.insertWalletAddress(
            NewWalletAddressRecord(
                walletId = walletId,
                networkId = networkId,
                address = address,
                addressType = WalletAddressType.WatchOnly.value,
                isPrimary = true,
            ),
        )
        return walletId
    }

    suspend fun getPrimaryWallet(): WalletRecord? =
        withContext(Dispatchers.IO) {
            val wallets = walletDao.getWallets()
            wallets.firstOrNull { it.isActive } ?: wallets.firstOrNull()
        }

    suspend fun getWalletAssets(walletId: String): List<WalletAssetRecord> =
        withContext(Dispatchers.IO) {
            walletDao.getWalletAssets(walletId)
        }

    suspend fun syncEvmWallet(walletId: String): EvmWalletSyncResult =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId)
                ?: error("Wallet not found: $walletId")
            val result = evmWalletSyncService.syncWallet(
                walletId = walletId,
                addresses = walletDao.getWalletAddresses(walletId),
            )
            persistEvmSyncResult(wallet, result)
            result
        }

    suspend fun syncEvmNetwork(
        walletId: String,
        networkId: String,
    ): EvmNetworkSyncResult =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId)
                ?: error("Wallet not found: $walletId")
            val result = evmWalletSyncService.syncWallet(
                walletId = walletId,
                addresses = walletDao.getWalletAddresses(walletId),
                networkId = networkId,
            )
            persistEvmSyncResult(wallet, result)
            result.networkResults.single()
        }

    private fun inferPrivateKeyFormat(privateKey: String): String {
        val normalized = privateKey.removePrefix("0x")
        return when {
            normalized.length == 64 && normalized.all(Char::isHexDigit) -> "hex"
            privateKey.length in 51..52 -> "wif"
            else -> "raw"
        }
    }

    private fun persistEvmSyncResult(
        wallet: WalletRecord,
        result: EvmWalletSyncResult,
    ) {
        val nowMillis = System.currentTimeMillis()
        result.networkResults.forEach { networkResult ->
            networkResult.balances.forEach { balance ->
                walletDao.updateWalletAssetBalance(
                    walletId = wallet.walletId,
                    assetId = balance.assetId,
                    balanceRaw = balance.balanceRaw,
                    balanceDecimal = balance.balanceDecimal,
                    balanceFiatValue = "0",
                    localCurrencyCode = wallet.localCurrencyCode,
                    metadataJson = balance.toMetadataJson(networkResult),
                    nowMillis = nowMillis,
                )
            }
            networkResult.transactions.forEach { transaction ->
                walletDao.upsertWalletTransaction(
                    transaction = transaction.toNewWalletTransactionRecord(wallet.localCurrencyCode),
                    nowMillis = nowMillis,
                )
            }
        }
        walletDao.updateWalletSyncMetadata(
            walletId = wallet.walletId,
            metadataJson = wallet.metadataJson.withEvmSyncResult(result),
            nowMillis = nowMillis,
        )
    }
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun String?.cleanPassphrase(): String? =
    this?.takeIf(String::isNotEmpty)

private fun EvmAssetBalance.toMetadataJson(networkResult: EvmNetworkSyncResult): String =
    JSONObject()
        .put("syncFamily", "evm")
        .put("syncProvider", providerName)
        .put("syncStatus", networkResult.balanceCompleteness.value)
        .put("syncHistoryStatus", networkResult.historyCompleteness.value)
        .put("syncBlockNumber", blockNumber)
        .put("syncUpdatedAt", syncedAtMillis)
        .put("syncCursorFromBlock", networkResult.cursorFromBlock)
        .put("syncCursorToBlock", networkResult.cursorToBlock)
        .put("syncLastError", networkResult.error)
        .toString()

private fun EvmNormalizedTransaction.toNewWalletTransactionRecord(
    localCurrencyCode: String,
): NewWalletTransactionRecord =
    NewWalletTransactionRecord(
        walletId = walletId,
        assetId = assetId,
        networkId = networkId,
        transactionHash = transactionHash,
        direction = direction.value,
        status = status.value,
        amountRaw = amountRaw,
        amountDecimal = amountDecimal,
        feeRaw = feeRaw,
        feeDecimal = feeDecimal,
        feeAssetId = feeAssetId,
        localCurrencyCode = localCurrencyCode,
        fromAddress = fromAddress,
        toAddress = toAddress,
        blockHeight = blockHeight,
        blockHash = blockHash,
        confirmations = confirmations,
        nonce = nonce,
        timestamp = timestampMillis,
        metadataJson = metadataJson,
    )

private fun String.withEvmSyncResult(result: EvmWalletSyncResult): String {
    val root = runCatching { JSONObject(this) }.getOrElse { JSONObject() }
    val networks = JSONArray()
    result.networkResults.forEach { network ->
        networks.put(
            JSONObject()
                .put("networkId", network.networkId)
                .put("address", network.address)
                .put("balanceStatus", network.balanceCompleteness.value)
                .put("historyStatus", network.historyCompleteness.value)
                .put("provider", network.providerName)
                .put("latestBlockNumber", network.latestBlockNumber)
                .put("cursorFromBlock", network.cursorFromBlock)
                .put("cursorToBlock", network.cursorToBlock)
                .put("error", network.error),
        )
    }
    root.put(
        "evmSync",
        JSONObject()
            .put("networkCount", result.networkResults.size)
            .put("syncedNetworkCount", result.syncedNetworkCount)
            .put("networks", networks),
    )
    return root.toString()
}

object SatraDatabaseProvider {
    @Volatile
    private var repository: SatraWalletRepository? = null

    fun walletRepository(context: Context): SatraWalletRepository =
        repository ?: synchronized(this) {
            repository ?: SatraWalletRepository(
                SatraWalletDao(
                    SatraDatabaseOpenHelper(context.applicationContext),
                ),
            ).also { repository = it }
        }
}
