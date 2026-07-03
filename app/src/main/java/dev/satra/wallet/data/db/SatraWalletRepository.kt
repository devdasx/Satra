package dev.satra.wallet.data.db

import android.content.Context
import dev.satra.wallet.data.sync.evm.EvmAssetBalance
import dev.satra.wallet.data.sync.evm.EvmNetworkSyncResult
import dev.satra.wallet.data.sync.evm.EvmNormalizedTransaction
import dev.satra.wallet.data.sync.evm.EvmWalletSyncResult
import dev.satra.wallet.data.sync.evm.EvmWalletSyncService
import dev.satra.wallet.settings.SatraPasscodeHasher
import dev.satra.wallet.wallet.bip39.Bip39MnemonicValidator
import dev.satra.wallet.wallet.derivation.DerivedReceiveAccount
import dev.satra.wallet.wallet.derivation.SatraAddressDerivation
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
        val receiveAccounts = SatraAddressDerivation.deriveReceiveAccounts(mnemonic, cleanedPassphrase)
        val primaryAccount = receiveAccounts.first { it.networkId == "ethereum" }
        val walletId = walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Standard.value,
                walletKeyType = WalletKeyType.Mnemonic.value,
                walletKeyMaterial = mnemonic,
                walletKeyFingerprint = primaryAccount.keyFingerprint,
                walletKeyDerivationPath = primaryAccount.derivationPath,
                passphrase = cleanedPassphrase,
                localCurrencyCode = localCurrencyCode,
                isBackedUp = isBackedUp,
                isImported = false,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )
        walletDao.insertDerivedReceiveAccounts(walletId, receiveAccounts)
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
        val receiveAccounts = SatraAddressDerivation.deriveReceiveAccounts(mnemonic, cleanedPassphrase)
        val primaryAccount = receiveAccounts.first { it.networkId == "ethereum" }
        val walletId = walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Imported.value,
                walletKeyType = WalletKeyType.Mnemonic.value,
                walletKeyMaterial = mnemonic,
                walletKeyFingerprint = primaryAccount.keyFingerprint,
                walletKeyDerivationPath = primaryAccount.derivationPath,
                passphrase = cleanedPassphrase,
                localCurrencyCode = localCurrencyCode,
                isImported = true,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )
        walletDao.insertDerivedReceiveAccounts(walletId, receiveAccounts)
        return walletId
    }

    fun importPrivateKeyWallet(
        walletName: String,
        networkId: String,
        privateKey: String,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
        metadataJson: String = EMPTY_JSON,
    ): String {
        val receiveAccount = SatraAddressDerivation.derivePrivateKeyAccount(networkId, privateKey)
        val walletId = walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Imported.value,
                walletKeyType = WalletKeyType.PrivateKey.value,
                walletKeyMaterial = receiveAccount?.privateKeyHex ?: privateKey,
                walletKeyFingerprint = receiveAccount?.keyFingerprint,
                walletKeyDerivationPath = receiveAccount?.derivationPath,
                localCurrencyCode = localCurrencyCode,
                isImported = true,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )
        if (receiveAccount != null) {
            walletDao.insertImportedPrivateKeyReceiveAccount(
                walletId = walletId,
                networkId = networkId,
                account = receiveAccount,
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

    suspend fun getAppSettings(): AppSettingsRecord =
        withContext(Dispatchers.IO) {
            walletDao.getAppSettings()
        }

    suspend fun updateAppSettings(update: AppSettingsUpdate): AppSettingsRecord =
        withContext(Dispatchers.IO) {
            val updated = walletDao.updateAppSettings(update)
            update.localCurrencyCode?.let(walletDao::updateLocalCurrencyForWalletData)
            updated
        }

    suspend fun setAppPasscode(passcode: String): AppSettingsRecord =
        withContext(Dispatchers.IO) {
            val passcodeHash = SatraPasscodeHasher.hash(passcode)
            walletDao.updateAppSettings(
                AppSettingsUpdate(
                    passcodeEnabled = true,
                    passcodeHash = passcodeHash.hash,
                    passcodeSalt = passcodeHash.salt,
                    passcodeLength = passcode.length,
                    failedPasscodeAttempts = 0,
                ),
            )
        }

    fun saveSetupSecurity(
        passcode: String,
        biometricsEnabled: Boolean,
    ) {
        if (passcode.isBlank()) {
            walletDao.updateAppSettings(
                AppSettingsUpdate(
                    clearPasscode = true,
                    biometricsEnabled = false,
                ),
            )
            return
        }
        val passcodeHash = SatraPasscodeHasher.hash(passcode)
        walletDao.updateAppSettings(
            AppSettingsUpdate(
                passcodeEnabled = true,
                passcodeHash = passcodeHash.hash,
                passcodeSalt = passcodeHash.salt,
                passcodeLength = passcode.length,
                biometricsEnabled = biometricsEnabled,
                failedPasscodeAttempts = 0,
            ),
        )
    }

    suspend fun verifyAppPasscode(passcode: String): Boolean =
        withContext(Dispatchers.IO) {
            val settings = walletDao.getAppSettings()
            val valid = SatraPasscodeHasher.verify(
                passcode = passcode,
                expectedHash = settings.passcodeHash,
                salt = settings.passcodeSalt,
            )
            val nextFailedAttempts = if (valid) 0 else settings.failedPasscodeAttempts + 1
            walletDao.updateAppSettings(
                AppSettingsUpdate(
                    failedPasscodeAttempts = nextFailedAttempts,
                ),
            )
            if (!valid &&
                settings.eraseWalletEnabled &&
                settings.passcodeEnabled &&
                nextFailedAttempts >= settings.eraseWalletAttemptLimit
            ) {
                walletDao.resetUserData()
            }
            valid
        }

    suspend fun clearAppPasscode(): AppSettingsRecord =
        withContext(Dispatchers.IO) {
            walletDao.updateAppSettings(AppSettingsUpdate(clearPasscode = true))
        }

    suspend fun getAddressBookEntries(): List<AddressBookEntryRecord> =
        withContext(Dispatchers.IO) {
            walletDao.getAddressBookEntries()
        }

    suspend fun upsertAddressBookEntry(
        entry: NewAddressBookEntryRecord,
        existingEntryId: String? = null,
    ): String =
        withContext(Dispatchers.IO) {
            walletDao.upsertAddressBookEntry(entry, existingEntryId)
        }

    suspend fun deleteAddressBookEntry(entryId: String) =
        withContext(Dispatchers.IO) {
            walletDao.deleteAddressBookEntry(entryId)
        }

    suspend fun resetUserData() =
        withContext(Dispatchers.IO) {
            walletDao.resetUserData()
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

    suspend fun getWalletAddresses(walletId: String): List<WalletAddressRecord> =
        withContext(Dispatchers.IO) {
            walletDao.getWalletAddresses(walletId)
        }

    suspend fun getWalletTransactions(walletId: String): List<WalletTransactionRecord> =
        withContext(Dispatchers.IO) {
            walletDao.getWalletTransactions(walletId)
        }

    suspend fun ensureMnemonicReceiveAddresses(walletId: String): List<WalletAddressRecord> =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId) ?: return@withContext emptyList()
            if (wallet.walletKeyType != WalletKeyType.Mnemonic.value || wallet.walletKeyMaterial.isNullOrBlank()) {
                return@withContext walletDao.getWalletAddresses(walletId)
            }
            val existing = walletDao.getWalletAddresses(walletId)
            val existingKeys = existing.map { address ->
                "${address.networkId}:${address.derivationPath}:${address.address}"
            }.toSet()
            val missing = SatraAddressDerivation
                .deriveReceiveAccounts(wallet.walletKeyMaterial, wallet.passphrase)
                .filterNot { account ->
                    "${account.networkId}:${account.derivationPath}:${account.address}" in existingKeys
                }
            if (missing.isNotEmpty()) {
                walletDao.insertDerivedReceiveAccounts(walletId, missing)
            }
            walletDao.getWalletAddresses(walletId)
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

    private fun SatraWalletDao.insertDerivedReceiveAccounts(
        walletId: String,
        accounts: List<DerivedReceiveAccount>,
    ) {
        accounts.forEach { account ->
            val addressId = insertWalletAddress(
                NewWalletAddressRecord(
                    walletId = walletId,
                    networkId = account.networkId,
                    address = account.address,
                    addressType = WalletAddressType.Receive.value,
                    derivationPath = account.derivationPath,
                    publicKey = account.publicKeyHex,
                    isPrimary = account.isPrimary,
                    addressIndex = account.addressIndex,
                    label = account.derivationLabel,
                    metadataJson = account.metadataJson(),
                ),
            )
            insertWalletPrivateKey(
                NewWalletPrivateKeyRecord(
                    walletId = walletId,
                    networkId = account.networkId,
                    addressId = addressId,
                    keyMaterial = account.privateKeyHex,
                    keyFormat = "hex",
                    derivationPath = account.derivationPath,
                    publicKey = account.publicKeyHex,
                    keySource = WalletPrivateKeySource.MnemonicDerived.value,
                    isImported = false,
                    keyFingerprint = account.keyFingerprint,
                    metadataJson = account.metadataJson(),
                ),
            )
        }
    }

    private fun SatraWalletDao.insertImportedPrivateKeyReceiveAccount(
        walletId: String,
        networkId: String,
        account: DerivedReceiveAccount,
    ) {
        val addressId = insertWalletAddress(
            NewWalletAddressRecord(
                walletId = walletId,
                networkId = networkId,
                address = account.address,
                addressType = WalletAddressType.Receive.value,
                derivationPath = account.derivationPath,
                publicKey = account.publicKeyHex,
                isPrimary = true,
                addressIndex = 0,
                label = account.derivationLabel,
                metadataJson = account.metadataJson(),
            ),
        )
        insertWalletPrivateKey(
            NewWalletPrivateKeyRecord(
                walletId = walletId,
                networkId = networkId,
                addressId = addressId,
                keyMaterial = account.privateKeyHex,
                keyFormat = "hex",
                derivationPath = account.derivationPath,
                publicKey = account.publicKeyHex,
                keySource = WalletPrivateKeySource.Imported.value,
                isImported = true,
                keyFingerprint = account.keyFingerprint,
                metadataJson = account.metadataJson(),
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

private val DerivedReceiveAccount.derivationLabel: String
    get() = when (derivationName) {
        "phantom" -> "Phantom"
        "segwit" -> "SegWit"
        "cashaddr" -> "CashAddr"
        "legacy" -> "Legacy"
        else -> "Trust Wallet"
    }

private fun DerivedReceiveAccount.metadataJson(): String =
    JSONObject()
        .put("accountFamily", networkId)
        .put("accountSource", source)
        .put("derivationName", derivationName)
        .put("derivationPath", derivationPath)
        .put("keyFingerprint", keyFingerprint)
        .put("source", SatraAddressDerivation.SOURCE)
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
