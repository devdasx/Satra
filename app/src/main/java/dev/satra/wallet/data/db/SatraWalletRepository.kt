package dev.satra.wallet.data.db

import android.content.Context
import dev.satra.wallet.data.sync.evm.EvmAssetBalance
import dev.satra.wallet.data.sync.evm.EvmNetworkSyncResult
import dev.satra.wallet.data.sync.evm.EvmNormalizedTransaction
import dev.satra.wallet.data.sync.evm.EvmProviderRegistry
import dev.satra.wallet.data.sync.evm.EvmWalletSyncResult
import dev.satra.wallet.data.sync.evm.EvmWalletSyncService
import dev.satra.wallet.wallet.bip39.Bip39MnemonicValidator
import dev.satra.wallet.wallet.evm.EvmDerivedAccount
import dev.satra.wallet.wallet.evm.EvmWalletDerivation
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
    ): String {
        require(Bip39MnemonicValidator.validate(mnemonic).isValid) {
            "Invalid BIP39 mnemonic."
        }
        val cleanedPassphrase = passphrase.cleanPassphrase()
        val evmAccount = EvmWalletDerivation.deriveDefaultAccount(mnemonic, cleanedPassphrase)
        val walletId = walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Standard.value,
                walletKeyType = WalletKeyType.Mnemonic.value,
                walletKeyMaterial = mnemonic,
                walletKeyFingerprint = evmAccount.keyFingerprint,
                walletKeyDerivationPath = evmAccount.derivationPath,
                passphrase = cleanedPassphrase,
                localCurrencyCode = localCurrencyCode,
                isBackedUp = isBackedUp,
                isImported = false,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )
        walletDao.insertEvmMnemonicAccounts(walletId, evmAccount)
        return walletId
    }

    fun importMnemonicWallet(
        walletName: String,
        mnemonic: String,
        passphrase: String? = null,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
        metadataJson: String = EMPTY_JSON,
    ): String {
        require(Bip39MnemonicValidator.validate(mnemonic).isValid) {
            "Invalid BIP39 mnemonic."
        }
        val cleanedPassphrase = passphrase.cleanPassphrase()
        val evmAccount = EvmWalletDerivation.deriveDefaultAccount(mnemonic, cleanedPassphrase)
        val walletId = walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Imported.value,
                walletKeyType = WalletKeyType.Mnemonic.value,
                walletKeyMaterial = mnemonic,
                walletKeyFingerprint = evmAccount.keyFingerprint,
                walletKeyDerivationPath = evmAccount.derivationPath,
                passphrase = cleanedPassphrase,
                localCurrencyCode = localCurrencyCode,
                isImported = true,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )
        walletDao.insertEvmMnemonicAccounts(walletId, evmAccount)
        return walletId
    }

    fun importPrivateKeyWallet(
        walletName: String,
        networkId: String,
        privateKey: String,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
        metadataJson: String = EMPTY_JSON,
    ): String {
        val evmAccount = if (networkId in EvmProviderRegistry.supportedNetworkIds) {
            EvmWalletDerivation.privateKeyToAccount(privateKey)
        } else {
            null
        }
        val walletId = walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Imported.value,
                walletKeyType = WalletKeyType.PrivateKey.value,
                walletKeyMaterial = evmAccount?.privateKeyHex ?: privateKey,
                walletKeyFingerprint = evmAccount?.keyFingerprint,
                walletKeyDerivationPath = evmAccount?.derivationPath,
                localCurrencyCode = localCurrencyCode,
                isImported = true,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )
        if (evmAccount != null) {
            walletDao.insertEvmImportedPrivateKeyAccount(
                walletId = walletId,
                networkId = networkId,
                evmAccount = evmAccount,
            )
        } else {
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
        }
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

    private fun SatraWalletDao.insertEvmMnemonicAccounts(
        walletId: String,
        evmAccount: EvmDerivedAccount,
    ) {
        EvmProviderRegistry.supportedNetworkIds.sorted().forEach { networkId ->
            val addressId = insertWalletAddress(
                NewWalletAddressRecord(
                    walletId = walletId,
                    networkId = networkId,
                    address = evmAccount.address,
                    addressType = WalletAddressType.Receive.value,
                    derivationPath = evmAccount.derivationPath,
                    publicKey = evmAccount.publicKeyHex,
                    isPrimary = true,
                    addressIndex = 0,
                    metadataJson = evmAccount.metadataJson("evm_mnemonic_derived"),
                ),
            )
            insertWalletPrivateKey(
                NewWalletPrivateKeyRecord(
                    walletId = walletId,
                    networkId = networkId,
                    addressId = addressId,
                    keyMaterial = evmAccount.privateKeyHex,
                    keyFormat = "hex",
                    derivationPath = evmAccount.derivationPath,
                    publicKey = evmAccount.publicKeyHex,
                    keySource = WalletPrivateKeySource.MnemonicDerived.value,
                    isImported = false,
                    keyFingerprint = evmAccount.keyFingerprint,
                    metadataJson = evmAccount.metadataJson("evm_mnemonic_derived"),
                ),
            )
        }
    }

    private fun SatraWalletDao.insertEvmImportedPrivateKeyAccount(
        walletId: String,
        networkId: String,
        evmAccount: EvmDerivedAccount,
    ) {
        val addressId = insertWalletAddress(
            NewWalletAddressRecord(
                walletId = walletId,
                networkId = networkId,
                address = evmAccount.address,
                addressType = WalletAddressType.Receive.value,
                derivationPath = evmAccount.derivationPath,
                publicKey = evmAccount.publicKeyHex,
                isPrimary = true,
                addressIndex = 0,
                metadataJson = evmAccount.metadataJson("evm_private_key_imported"),
            ),
        )
        insertWalletPrivateKey(
            NewWalletPrivateKeyRecord(
                walletId = walletId,
                networkId = networkId,
                addressId = addressId,
                keyMaterial = evmAccount.privateKeyHex,
                keyFormat = "hex",
                derivationPath = evmAccount.derivationPath,
                publicKey = evmAccount.publicKeyHex,
                keySource = WalletPrivateKeySource.Imported.value,
                isImported = true,
                keyFingerprint = evmAccount.keyFingerprint,
                metadataJson = evmAccount.metadataJson("evm_private_key_imported"),
            ),
        )
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

private fun EvmDerivedAccount.metadataJson(source: String): String =
    JSONObject()
        .put("accountFamily", "evm")
        .put("accountSource", source)
        .put("derivationPath", derivationPath)
        .put("keyFingerprint", keyFingerprint)
        .toString()

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
