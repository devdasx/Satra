package dev.satra.wallet.data.db

import android.content.Context
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.pricing.SatraAssetPrice
import dev.satra.wallet.data.pricing.SatraPriceSyncResult
import dev.satra.wallet.data.pricing.SatraPriceSyncService
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

class SatraWalletRepository(
    private val walletDao: SatraWalletDao,
    private val evmWalletSyncService: EvmWalletSyncService = EvmWalletSyncService(),
    private val priceSyncService: SatraPriceSyncService = SatraPriceSyncService(),
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

    suspend fun getWalletPrivateKeys(walletId: String): List<WalletPrivateKeyRecord> =
        withContext(Dispatchers.IO) {
            walletDao.getWalletPrivateKeys(walletId)
        }

    suspend fun createPendingSendTransaction(
        request: SatraPendingSendRequest,
    ): String =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(request.walletId)
                ?: error("Wallet not found: ${request.walletId}")
            require(!wallet.isWatchOnly) {
                "Watch-only wallets cannot send."
            }
            val asset = SupportedAssetCatalog.assets.firstOrNull { it.assetId == request.assetId }
                ?: error("Unsupported asset: ${request.assetId}")
            val walletAsset = walletDao.getWalletAssets(request.walletId)
                .firstOrNull { it.assetId == request.assetId && it.networkId == asset.networkId }
                ?: error("Wallet asset not found: ${request.assetId}")
            val fromAddress = walletDao.getWalletAddresses(request.walletId)
                .filter { address ->
                    address.networkId == asset.networkId &&
                        (address.addressType == WalletAddressType.Receive.value || address.addressType == WalletAddressType.WatchOnly.value)
                }
                .sortedWith(
                    compareByDescending<WalletAddressRecord> { it.isPrimary }
                        .thenBy { it.addressIndex ?: Int.MAX_VALUE },
                )
                .firstOrNull()
                ?: error("No source address for ${asset.networkId}.")
            val hasSigningKey = walletDao.getWalletPrivateKeys(request.walletId)
                .any { privateKey ->
                    privateKey.networkId == asset.networkId && !privateKey.isEncrypted
                }
            require(hasSigningKey) {
                "No local signing key for ${asset.networkId}."
            }
            val amount = request.amountDecimal.stripTrailingZeros()
            val balance = walletAsset.balanceDecimal.toBigDecimalOrZero()
            require(amount > BigDecimal.ZERO) {
                "Amount must be greater than zero."
            }
            require(amount <= balance) {
                "Insufficient balance."
            }
            val amountRaw = amount.toRawAmount(asset.decimals)
            val fiatValue = walletAsset.priceFiatValue
                .toBigDecimalOrZero()
                .takeIf { it > BigDecimal.ZERO }
                ?.multiply(amount)
                ?.stripTrailingZeros()
                ?.toPlainString()
            val nowMillis = System.currentTimeMillis()
            walletDao.insertWalletTransaction(
                NewWalletTransactionRecord(
                    walletId = request.walletId,
                    assetId = request.assetId,
                    networkId = asset.networkId,
                    transactionHash = null,
                    direction = WalletTransactionDirection.Outgoing.value,
                    status = WalletTransactionStatus.Pending.value,
                    amountRaw = amountRaw,
                    amountDecimal = amount.toPlainString(),
                    feeAssetId = SupportedAssetCatalog.assets
                        .firstOrNull { candidate ->
                            candidate.networkId == asset.networkId && candidate.assetType == "NATIVE"
                        }
                        ?.assetId,
                    fiatValue = fiatValue,
                    localCurrencyCode = wallet.localCurrencyCode,
                    fromAddress = fromAddress.address,
                    toAddress = request.toAddress.trim(),
                    memo = request.memo?.trim()?.takeIf(String::isNotEmpty),
                    timestamp = nowMillis,
                    metadataJson = request.toMetadataJson(asset.networkId),
                ),
                nowMillis = nowMillis,
            )
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

    suspend fun syncWalletData(walletId: String): SatraWalletDataSyncResult =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId)
                ?: error("Wallet not found: $walletId")
            val addresses = walletDao.getWalletAddresses(walletId)
            coroutineScope {
                val evmDeferred = async {
                    evmWalletSyncService.syncWallet(
                        walletId = walletId,
                        addresses = addresses,
                    )
                }
                val pricesDeferred = async {
                    priceSyncService.syncSupportedAssetPricesOrNull(wallet.localCurrencyCode)
                }

                val evmResult = evmDeferred.await()
                persistEvmSyncResult(wallet, evmResult)

                val priceResult = pricesDeferred.await()
                persistWalletPrices(wallet, priceResult)

                SatraWalletDataSyncResult(
                    evmSyncResult = evmResult,
                    priceSyncResult = priceResult,
                )
            }
        }

    suspend fun syncWalletPrices(walletId: String): SatraPriceSyncResult? =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId)
                ?: error("Wallet not found: $walletId")
            val result = priceSyncService.syncSupportedAssetPricesOrNull(wallet.localCurrencyCode)
            persistWalletPrices(wallet, result)
            result
        }

    suspend fun syncAllWalletPrices(): List<SatraPriceSyncResult?> =
        withContext(Dispatchers.IO) {
            val wallets = walletDao.getWallets()
            coroutineScope {
                val pricesByCurrency = wallets
                    .map { it.localCurrencyCode }
                    .distinct()
                    .associateWith { localCurrencyCode ->
                        async {
                            priceSyncService.syncSupportedAssetPricesOrNull(localCurrencyCode)
                        }
                    }
                    .mapValues { (_, deferred) -> deferred.await() }

                wallets.map { wallet ->
                    pricesByCurrency[wallet.localCurrencyCode]
                        .also { result -> persistWalletPrices(wallet, result) }
                }
            }
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

    private fun persistWalletPrices(
        wallet: WalletRecord,
        result: SatraPriceSyncResult?,
    ) {
        val walletAssets = walletDao.getWalletAssets(wallet.walletId)
        val pricesByAssetId = result?.prices.orEmpty().associateBy { it.asset.assetId }
        var totalFiat = BigDecimal.ZERO
        val nowMillis = result?.updatedAtMillis ?: System.currentTimeMillis()

        walletAssets.forEach { walletAsset ->
            val price = pricesByAssetId[walletAsset.assetId]
            val cachedPrice = walletAsset.priceFiatValue.toBigDecimalOrZero()
                .takeIf { it > BigDecimal.ZERO && walletAsset.localCurrencyCode == wallet.localCurrencyCode }
            val localPrice = price?.localPrice ?: cachedPrice
            if (localPrice != null) {
                val balanceFiat = walletAsset.balanceDecimal.toBigDecimalOrZero().multiply(localPrice)
                totalFiat += balanceFiat
                walletDao.updateWalletAssetPrice(
                    walletId = wallet.walletId,
                    assetId = walletAsset.assetId,
                    priceFiatValue = localPrice.toPlainString(),
                    balanceFiatValue = balanceFiat.toPlainString(),
                    localCurrencyCode = wallet.localCurrencyCode,
                    metadataJson = walletAsset.metadataJson.withPriceSync(
                        price = price,
                        cached = price == null,
                        localCurrencyCode = wallet.localCurrencyCode,
                        failed = result?.failedSymbols.orEmpty().contains(
                            walletAsset.assetId.substringAfter(':').uppercase(),
                        ),
                        nowMillis = nowMillis,
                    ),
                    nowMillis = nowMillis,
                )
            }
        }
        walletDao.updateWalletFiatBalance(
            walletId = wallet.walletId,
            balanceFiatValue = totalFiat.toPlainString(),
            localCurrencyCode = wallet.localCurrencyCode,
            nowMillis = nowMillis,
        )
    }
}

data class SatraWalletDataSyncResult(
    val evmSyncResult: EvmWalletSyncResult,
    val priceSyncResult: SatraPriceSyncResult?,
)

data class SatraPendingSendRequest(
    val walletId: String,
    val assetId: String,
    val amountDecimal: BigDecimal,
    val toAddress: String,
    val memo: String? = null,
)

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun String?.cleanPassphrase(): String? =
    this?.takeIf(String::isNotEmpty)

private fun BigDecimal.toRawAmount(decimals: Int): String =
    movePointRight(decimals)
        .setScale(0, RoundingMode.DOWN)
        .toPlainString()

private fun SatraPendingSendRequest.toMetadataJson(networkId: String): String =
    JSONObject()
        .put("flow", "send")
        .put("networkId", networkId)
        .put("signingProvider", "trustwallet/wallet-core")
        .put("signingStatus", "not_connected")
        .put("broadcastStatus", "not_broadcast")
        .put(
            "blockingReason",
            "Wallet Core Android package requires GitHub Packages authentication before native signing can be linked.",
        )
        .toString()

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

private fun String.withPriceSync(
    price: SatraAssetPrice?,
    cached: Boolean,
    localCurrencyCode: String,
    failed: Boolean,
    nowMillis: Long,
): String {
    val root = runCatching { JSONObject(this) }.getOrElse { JSONObject() }
    root.put(
        "priceSync",
        JSONObject()
            .put(
                "status",
                when {
                    price != null -> "complete"
                    cached -> "cached"
                    failed -> "failed"
                    else -> "unavailable"
                },
            )
            .put("provider", price?.provider ?: "local-database")
            .put("providerAssetId", price?.providerAssetId)
            .put("usdPrice", price?.usdPrice?.toPlainString())
            .put("localCurrencyCode", localCurrencyCode)
            .put("updatedAt", nowMillis),
    )
    return root.toString()
}

private fun String.toBigDecimalOrZero(): BigDecimal =
    runCatching { BigDecimal(this) }.getOrDefault(BigDecimal.ZERO)

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
