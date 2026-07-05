package dev.satra.wallet.data.db

import android.content.Context
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.pricing.FxRateQuote
import dev.satra.wallet.data.pricing.SatraAssetPrice
import dev.satra.wallet.data.pricing.SatraAssetMarketData
import dev.satra.wallet.data.pricing.SatraPriceSyncResult
import dev.satra.wallet.data.pricing.SatraPriceSyncService
import dev.satra.wallet.data.pricing.priceSymbol
import dev.satra.wallet.data.security.SatraSecretCipher
import dev.satra.wallet.data.security.SatraSecretVault
import dev.satra.wallet.data.send.SatraSendException
import dev.satra.wallet.data.send.SatraSendRequest
import dev.satra.wallet.data.send.SatraSendService
import dev.satra.wallet.data.sync.accountchain.AccountChainAssetBalance
import dev.satra.wallet.data.sync.accountchain.AccountChainNetworkSyncResult
import dev.satra.wallet.data.sync.accountchain.AccountChainNormalizedTransaction
import dev.satra.wallet.data.sync.accountchain.AccountChainWalletSyncResult
import dev.satra.wallet.data.sync.accountchain.AccountChainWalletSyncService
import dev.satra.wallet.data.sync.evm.EvmAssetBalance
import dev.satra.wallet.data.sync.evm.EvmNetworkSyncResult
import dev.satra.wallet.data.sync.evm.EvmNormalizedTransaction
import dev.satra.wallet.data.sync.evm.EvmWalletSyncResult
import dev.satra.wallet.data.sync.evm.EvmWalletSyncService
import dev.satra.wallet.data.sync.solana.SolanaAssetBalance
import dev.satra.wallet.data.sync.solana.SolanaNetworkSyncResult
import dev.satra.wallet.data.sync.solana.SolanaNormalizedTransaction
import dev.satra.wallet.data.sync.solana.SolanaWalletSyncResult
import dev.satra.wallet.data.sync.solana.SolanaWalletSyncService
import dev.satra.wallet.data.sync.utxo.UtxoAssetBalance
import dev.satra.wallet.data.sync.utxo.UtxoNetworkSyncResult
import dev.satra.wallet.data.sync.utxo.UtxoNormalizedTransaction
import dev.satra.wallet.data.sync.utxo.UtxoWalletSecrets
import dev.satra.wallet.data.sync.utxo.UtxoWalletSyncResult
import dev.satra.wallet.data.sync.utxo.UtxoWalletSyncService
import dev.satra.wallet.settings.SatraPasscodeHasher
import dev.satra.wallet.wallet.bip39.Bip39MnemonicValidator
import dev.satra.wallet.wallet.derivation.DerivedReceiveAccount
import dev.satra.wallet.wallet.derivation.SatraAddressDerivation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

class SatraWalletRepository(
    private val walletDao: SatraWalletDao,
    private val evmWalletSyncService: EvmWalletSyncService = EvmWalletSyncService(),
    private val utxoWalletSyncService: UtxoWalletSyncService = UtxoWalletSyncService(),
    private val solanaWalletSyncService: SolanaWalletSyncService = SolanaWalletSyncService(),
    private val accountChainWalletSyncService: AccountChainWalletSyncService = AccountChainWalletSyncService(),
    private val priceSyncService: SatraPriceSyncService = SatraPriceSyncService(),
    private val sendService: SatraSendService = SatraSendService(),
    private val secretCipher: SatraSecretCipher = SatraSecretVault(),
) {
    private val syncPersistenceMutex = Mutex()

    private data class MnemonicWalletSecrets(
        val mnemonic: String,
        val passphrase: String?,
    )

    private fun encryptSecret(
        walletId: String,
        secretType: WalletSecretType,
        plaintext: String,
        networkId: String? = null,
        derivationPath: String? = null,
        metadataJson: String = EMPTY_JSON,
    ): String =
        walletDao.insertWalletSecret(
            secretCipher.encrypt(
                walletId = walletId,
                secretType = secretType.value,
                plaintext = plaintext,
                networkId = networkId,
                derivationPath = derivationPath,
                metadataJson = metadataJson,
            ),
        )

    private fun decryptSecret(secretId: String?): String? {
        if (secretId.isNullOrBlank()) return null
        val secret = walletDao.getWalletSecret(secretId) ?: return null
        return secretCipher.decrypt(secret)
    }

    private fun WalletRecord.decryptMnemonicSecretsOrNull(): MnemonicWalletSecrets? {
        if (walletKeyType != WalletKeyType.Mnemonic.value || primarySecretId.isNullOrBlank()) return null
        val mnemonic = runCatching { decryptSecret(primarySecretId) }.getOrNull() ?: return null
        val passphrase = runCatching { decryptSecret(passphraseSecretId) }.getOrNull()
        return MnemonicWalletSecrets(
            mnemonic = mnemonic,
            passphrase = passphrase,
        )
    }

    private fun WalletRecord.decryptUtxoWalletSecretsOrNull(): UtxoWalletSecrets? =
        decryptMnemonicSecretsOrNull()?.let { secrets ->
            UtxoWalletSecrets(
                mnemonic = secrets.mnemonic,
                passphrase = secrets.passphrase,
            )
        }

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
                walletKeyFingerprint = primaryAccount.keyFingerprint,
                walletKeyDerivationPath = primaryAccount.derivationPath,
                secretStorageState = SecretStorageState.KeystoreAesGcmV1.value,
                localCurrencyCode = localCurrencyCode,
                isBackedUp = isBackedUp,
                isImported = false,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )
        val mnemonicSecretId = encryptSecret(
            walletId = walletId,
            secretType = WalletSecretType.Mnemonic,
            plaintext = mnemonic,
        )
        val passphraseSecretId = cleanedPassphrase?.let { phrase ->
            encryptSecret(
                walletId = walletId,
                secretType = WalletSecretType.Passphrase,
                plaintext = phrase,
            )
        }
        walletDao.updateWalletSecretReferences(walletId, mnemonicSecretId, passphraseSecretId)
        walletDao.insertDerivedReceiveAccounts(walletId, receiveAccounts, secretCipher)
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
                walletKeyFingerprint = primaryAccount.keyFingerprint,
                walletKeyDerivationPath = primaryAccount.derivationPath,
                secretStorageState = SecretStorageState.KeystoreAesGcmV1.value,
                localCurrencyCode = localCurrencyCode,
                isImported = true,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )
        val mnemonicSecretId = encryptSecret(
            walletId = walletId,
            secretType = WalletSecretType.Mnemonic,
            plaintext = mnemonic,
        )
        val passphraseSecretId = cleanedPassphrase?.let { phrase ->
            encryptSecret(
                walletId = walletId,
                secretType = WalletSecretType.Passphrase,
                plaintext = phrase,
            )
        }
        walletDao.updateWalletSecretReferences(walletId, mnemonicSecretId, passphraseSecretId)
        walletDao.insertDerivedReceiveAccounts(walletId, receiveAccounts, secretCipher)
        return walletId
    }

    fun importPrivateKeyWallet(
        walletName: String,
        networkId: String,
        privateKey: String,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
        metadataJson: String = EMPTY_JSON,
    ): String {
        val receiveAccount = SatraAddressDerivation.requirePrivateKeyAccount(networkId, privateKey)
        val walletId = walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Imported.value,
                walletKeyType = WalletKeyType.PrivateKey.value,
                walletKeyFingerprint = receiveAccount.keyFingerprint,
                walletKeyDerivationPath = receiveAccount.derivationPath,
                secretStorageState = SecretStorageState.KeystoreAesGcmV1.value,
                localCurrencyCode = localCurrencyCode,
                isImported = true,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )
        val privateKeySecretId = encryptSecret(
            walletId = walletId,
            secretType = WalletSecretType.PrivateKey,
            plaintext = receiveAccount.privateKeyHex,
            networkId = networkId,
            derivationPath = receiveAccount.derivationPath,
            metadataJson = receiveAccount.metadataJson(),
        )
        walletDao.updateWalletSecretReferences(walletId, privateKeySecretId, null)
        walletDao.insertImportedPrivateKeyReceiveAccount(
            walletId = walletId,
            networkId = networkId,
            account = receiveAccount,
            secretId = privateKeySecretId,
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
                secretStorageState = SecretStorageState.None.value,
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
            if (update.localCurrencyCode != null) {
                val settingsWithoutCurrency = update.copy(localCurrencyCode = null)
                if (settingsWithoutCurrency != AppSettingsUpdate()) {
                    walletDao.updateAppSettings(settingsWithoutCurrency)
                }
                return@withContext changeLocalCurrency(update.localCurrencyCode)
            }
            walletDao.updateAppSettings(update)
        }

    suspend fun changeLocalCurrency(localCurrencyCode: String): AppSettingsRecord =
        withContext(Dispatchers.IO) {
            val normalizedCurrency = localCurrencyCode.uppercase()
            val currentSettings = walletDao.getAppSettings()
            val fxQuote = currentSettings.metadataJson.cachedUsdFxRate(normalizedCurrency)
                ?: cachedMarketUsdFxRate(normalizedCurrency)
                ?: priceSyncService.syncUsdFxRateOrNull(normalizedCurrency)
            val settingsMetadata = fxQuote
                ?.let { quote -> currentSettings.metadataJson.withUsdFxRate(quote) }
                ?: currentSettings.metadataJson
            val settings = walletDao.updateAppSettings(
                AppSettingsUpdate(
                    localCurrencyCode = normalizedCurrency,
                    metadataJson = settingsMetadata,
                ),
            )
            syncPersistenceMutex.withLock {
                val nowMillis = System.currentTimeMillis()
                if (fxQuote != null) {
                    repriceMarketDataFromUsd(
                        localCurrencyCode = normalizedCurrency,
                        fxQuote = fxQuote,
                        nowMillis = nowMillis,
                    )
                    repriceWalletDataFromUsd(
                        localCurrencyCode = normalizedCurrency,
                        fxQuote = fxQuote,
                        nowMillis = nowMillis,
                    )
                } else {
                    walletDao.updateLocalCurrencyForWalletData(
                        localCurrencyCode = normalizedCurrency,
                        nowMillis = nowMillis,
                    )
                }
            }
            settings
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

    suspend fun getWallets(): List<WalletRecord> =
        withContext(Dispatchers.IO) {
            walletDao.getWallets()
        }

    suspend fun setActiveWallet(walletId: String): WalletRecord? =
        withContext(Dispatchers.IO) {
            walletDao.setActiveWallet(walletId)
        }

    suspend fun removeWallet(walletId: String): List<WalletRecord> =
        withContext(Dispatchers.IO) {
            walletDao.deleteWallet(walletId)
        }

    suspend fun getWalletBackup(walletId: String): WalletBackupRecord? =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId) ?: return@withContext null
            val mnemonicSecrets = if (wallet.walletKeyType == WalletKeyType.Mnemonic.value) {
                wallet.decryptMnemonicSecretsOrNull()
                    ?: throw IllegalStateException("Recovery phrase is unavailable for this wallet.")
            } else {
                null
            }
            val addressesById = walletDao.getWalletAddresses(walletId).associateBy { address ->
                address.addressId
            }
            val networksById = SupportedAssetCatalog.networks.associateBy { network ->
                network.networkId
            }
            val networkSortOrder = SupportedAssetCatalog.networks
                .mapIndexed { index, network -> network.networkId to index }
                .toMap()
            val privateKeys = walletDao.getWalletPrivateKeys(walletId)
                .sortedWith(
                    compareBy<WalletPrivateKeyRecord> { privateKey ->
                        networkSortOrder[privateKey.networkId] ?: Int.MAX_VALUE
                    }.thenBy { privateKey -> privateKey.derivationPath.orEmpty() },
                )
                .map { privateKey ->
                    val secret = decryptSecret(privateKey.secretId)
                        ?: throw IllegalStateException("Private key is unavailable for ${privateKey.networkId}.")
                    val network = networksById[privateKey.networkId]
                    val address = privateKey.addressId?.let(addressesById::get)
                    WalletPrivateKeyBackupRecord(
                        networkId = privateKey.networkId,
                        networkName = network?.displayName ?: privateKey.networkId,
                        address = address?.address,
                        derivationPath = privateKey.derivationPath,
                        keySource = privateKey.keySource,
                        keyFormat = privateKey.keyFormat,
                        privateKeyHex = secret,
                    )
                }
            WalletBackupRecord(
                wallet = wallet,
                recoveryPhrase = mnemonicSecrets?.mnemonic,
                passphrase = mnemonicSecrets?.passphrase,
                privateKeys = privateKeys,
            )
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
            walletDao.deleteUnbroadcastSendDraftTransactions(walletId)
            walletDao.getWalletTransactions(walletId)
        }

    suspend fun getAssetMarketData(symbol: String): AssetMarketDataRecord? =
        withContext(Dispatchers.IO) {
            walletDao.getAssetMarketData(symbol)
        }

    suspend fun getAllAssetMarketData(): List<AssetMarketDataRecord> =
        withContext(Dispatchers.IO) {
            walletDao.getAllAssetMarketData()
        }

    suspend fun getWalletPrivateKeys(walletId: String): List<WalletPrivateKeyRecord> =
        withContext(Dispatchers.IO) {
            walletDao.getWalletPrivateKeys(walletId)
        }

    suspend fun signAndBroadcastSend(
        assetId: String,
        recipientAddress: String,
        amountDecimal: BigDecimal,
    ): String =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallets().firstOrNull { it.isActive }
                ?: walletDao.getWallets().firstOrNull()
                ?: throw SatraSendException.MissingWallet()
            if (wallet.isWatchOnly) {
                throw SatraSendException.MissingSigningKey()
            }
            val asset = SupportedAssetCatalog.assets.firstOrNull { it.assetId == assetId }
                ?: throw SatraSendException.UnsupportedNetwork(assetId)
            val network = SupportedAssetCatalog.networks.firstOrNull { it.networkId == asset.networkId }
                ?: throw SatraSendException.UnsupportedNetwork(asset.networkId)
            val walletAsset = walletDao.getWalletAssets(wallet.walletId)
                .firstOrNull { it.assetId == asset.assetId && it.networkId == asset.networkId }
                ?: throw SatraSendException.UnsupportedNetwork(asset.networkId)
            val sourceAddress = walletDao.getWalletAddresses(wallet.walletId)
                .filter { address ->
                    address.networkId == asset.networkId &&
                        (address.addressType == WalletAddressType.Receive.value || address.addressType == WalletAddressType.WatchOnly.value)
                }
                .sortedWith(
                    compareByDescending<WalletAddressRecord> { it.isPrimary }
                        .thenBy { it.addressIndex ?: Int.MAX_VALUE },
                )
                .firstOrNull()
                ?.address
                ?: throw SatraSendException.MissingSigningKey()
            val privateKey = walletDao.getWalletPrivateKeys(wallet.walletId)
                .firstOrNull { key -> key.networkId == asset.networkId }
                ?: throw SatraSendException.MissingSigningKey()
            val privateKeyHex = decryptSecret(privateKey.secretId)
                ?: throw SatraSendException.MissingSigningKey()

            val broadcast = sendService.signAndBroadcast(
                SatraSendRequest(
                    walletId = wallet.walletId,
                    assetId = asset.assetId,
                    networkId = network.networkId,
                    assetSymbol = asset.symbol,
                    assetType = asset.assetType,
                    decimals = asset.decimals,
                    contractAddress = asset.contractAddress,
                    sourceAddress = sourceAddress,
                    recipientAddress = recipientAddress,
                    amountDecimal = amountDecimal.stripTrailingZeros().toPlainString(),
                    balanceRaw = walletAsset.balanceRaw,
                    privateKeyHex = privateKeyHex,
                    localCurrencyCode = wallet.localCurrencyCode,
                    priceFiatValue = walletAsset.priceFiatValue,
                ),
            )

            val fiatValue = amountDecimal
                .abs()
                .multiply(walletAsset.priceFiatValue.toBigDecimalOrZero())
                .stripTrailingZeros()
                .toPlainString()
            walletDao.upsertWalletTransaction(
                NewWalletTransactionRecord(
                    walletId = wallet.walletId,
                    assetId = asset.assetId,
                    networkId = network.networkId,
                    transactionHash = broadcast.transactionHash,
                    direction = WalletTransactionDirection.Outgoing.value,
                    status = WalletTransactionStatus.Pending.value,
                    amountRaw = "-${broadcast.amountRaw}",
                    amountDecimal = "-${broadcast.amountDecimal}",
                    feeRaw = broadcast.feeRaw.toString(),
                    feeDecimal = broadcast.feeDecimal,
                    feeAssetId = broadcast.feeAssetId,
                    fiatValue = fiatValue,
                    localCurrencyCode = wallet.localCurrencyCode,
                    fromAddress = broadcast.fromAddress,
                    toAddress = broadcast.toAddress,
                    nonce = broadcast.nonce.toString(),
                    timestamp = broadcast.timestampMillis,
                    metadataJson = JSONObject()
                        .put("flow", "send")
                        .put("broadcast", true)
                        .put("provider", broadcast.providerName)
                        .put("rawTransaction", broadcast.rawTransaction)
                        .put("assetSymbol", asset.symbol)
                        .toString(),
                ),
            )
        }

    suspend fun ensureMnemonicReceiveAddresses(walletId: String): List<WalletAddressRecord> =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId) ?: return@withContext emptyList()
            val secrets = wallet.decryptMnemonicSecretsOrNull()
            if (secrets == null) {
                return@withContext walletDao.getWalletAddresses(walletId)
            }
            val existing = walletDao.getWalletAddresses(walletId)
            val existingKeys = existing.map { address ->
                "${address.networkId}:${address.derivationPath}:${address.address}"
            }.toSet()
            val missing = SatraAddressDerivation
                .deriveReceiveAccounts(secrets.mnemonic, secrets.passphrase)
                .filterNot { account ->
                    "${account.networkId}:${account.derivationPath}:${account.address}" in existingKeys
                }
            if (missing.isNotEmpty()) {
                walletDao.insertDerivedReceiveAccounts(walletId, missing, secretCipher)
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
                onNetworkResult = { networkResult ->
                    syncPersistenceMutex.withLock {
                        persistEvmNetworkSyncResult(wallet, networkResult)
                    }
                },
            )
            syncPersistenceMutex.withLock {
                persistEvmSyncResult(wallet, result)
            }
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
                onNetworkResult = { networkResult ->
                    syncPersistenceMutex.withLock {
                        persistEvmNetworkSyncResult(wallet, networkResult)
                    }
                },
            )
            syncPersistenceMutex.withLock {
                persistEvmSyncResult(wallet, result)
            }
            result.networkResults.single()
        }

    suspend fun syncUtxoWallet(walletId: String): UtxoWalletSyncResult =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId)
                ?: error("Wallet not found: $walletId")
            val result = utxoWalletSyncService.syncWallet(
                wallet = wallet,
                addresses = walletDao.getWalletAddresses(walletId),
                walletSecrets = wallet.decryptUtxoWalletSecretsOrNull(),
                onNetworkResult = { networkResult ->
                    syncPersistenceMutex.withLock {
                        persistUtxoNetworkSyncResult(wallet, networkResult)
                    }
                },
            )
            syncPersistenceMutex.withLock {
                persistUtxoSyncResult(wallet, result)
            }
            result
        }

    suspend fun syncUtxoNetwork(
        walletId: String,
        networkId: String,
    ): UtxoNetworkSyncResult =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId)
                ?: error("Wallet not found: $walletId")
            val result = utxoWalletSyncService.syncWallet(
                wallet = wallet,
                addresses = walletDao.getWalletAddresses(walletId),
                walletSecrets = wallet.decryptUtxoWalletSecretsOrNull(),
                networkId = networkId,
                onNetworkResult = { networkResult ->
                    syncPersistenceMutex.withLock {
                        persistUtxoNetworkSyncResult(wallet, networkResult)
                    }
                },
            )
            syncPersistenceMutex.withLock {
                persistUtxoSyncResult(wallet, result)
            }
            result.networkResults.single()
        }

    suspend fun syncSolanaWallet(walletId: String): SolanaWalletSyncResult =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId)
                ?: error("Wallet not found: $walletId")
            val result = solanaWalletSyncService.syncWallet(
                walletId = walletId,
                addresses = walletDao.getWalletAddresses(walletId),
                onNetworkResult = { networkResult ->
                    syncPersistenceMutex.withLock {
                        persistSolanaNetworkSyncResult(wallet, networkResult)
                    }
                },
            )
            syncPersistenceMutex.withLock {
                persistSolanaSyncResult(wallet, result)
            }
            result
        }

    suspend fun syncSolanaNetwork(
        walletId: String,
        networkId: String,
    ): SolanaNetworkSyncResult =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId)
                ?: error("Wallet not found: $walletId")
            val result = solanaWalletSyncService.syncWallet(
                walletId = walletId,
                addresses = walletDao.getWalletAddresses(walletId),
                networkId = networkId,
                onNetworkResult = { networkResult ->
                    syncPersistenceMutex.withLock {
                        persistSolanaNetworkSyncResult(wallet, networkResult)
                    }
                },
            )
            syncPersistenceMutex.withLock {
                persistSolanaSyncResult(wallet, result)
            }
            result.networkResults.single()
        }

    suspend fun syncAccountChainWallet(walletId: String): AccountChainWalletSyncResult =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId)
                ?: error("Wallet not found: $walletId")
            val result = accountChainWalletSyncService.syncWallet(
                walletId = walletId,
                addresses = walletDao.getWalletAddresses(walletId),
                onNetworkResult = { networkResult ->
                    syncPersistenceMutex.withLock {
                        persistAccountChainNetworkSyncResult(wallet, networkResult)
                    }
                },
            )
            syncPersistenceMutex.withLock {
                persistAccountChainSyncResult(wallet, result)
            }
            result
        }

    suspend fun syncAccountChainNetwork(
        walletId: String,
        networkId: String,
    ): AccountChainNetworkSyncResult =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId)
                ?: error("Wallet not found: $walletId")
            val result = accountChainWalletSyncService.syncWallet(
                walletId = walletId,
                addresses = walletDao.getWalletAddresses(walletId),
                networkId = networkId,
                onNetworkResult = { networkResult ->
                    syncPersistenceMutex.withLock {
                        persistAccountChainNetworkSyncResult(wallet, networkResult)
                    }
                },
            )
            syncPersistenceMutex.withLock {
                persistAccountChainSyncResult(wallet, result)
            }
            result.networkResults.single()
        }

    suspend fun syncWalletData(
        walletId: String,
        onProgress: suspend () -> Unit = {},
    ): SatraWalletDataSyncResult =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId)
                ?: error("Wallet not found: $walletId")
            val addresses = walletDao.getWalletAddresses(walletId)
            val utxoWalletSecrets = wallet.decryptUtxoWalletSecretsOrNull()
            coroutineScope {
                val evmDeferred = async {
                    evmWalletSyncService.syncWallet(
                        walletId = walletId,
                        addresses = addresses,
                        onNetworkResult = { networkResult ->
                            syncPersistenceMutex.withLock {
                                persistEvmNetworkSyncResult(wallet, networkResult)
                            }
                            onProgress()
                        },
                    )
                }
                val utxoDeferred = async {
                    utxoWalletSyncService.syncWallet(
                        wallet = wallet,
                        addresses = addresses,
                        walletSecrets = utxoWalletSecrets,
                        onNetworkResult = { networkResult ->
                            syncPersistenceMutex.withLock {
                                persistUtxoNetworkSyncResult(wallet, networkResult)
                            }
                            onProgress()
                        },
                    )
                }
                val solanaDeferred = async {
                    solanaWalletSyncService.syncWallet(
                        walletId = walletId,
                        addresses = addresses,
                        onNetworkResult = { networkResult ->
                            syncPersistenceMutex.withLock {
                                persistSolanaNetworkSyncResult(wallet, networkResult)
                            }
                            onProgress()
                        },
                    )
                }
                val accountChainDeferred = async {
                    accountChainWalletSyncService.syncWallet(
                        walletId = walletId,
                        addresses = addresses,
                        onNetworkResult = { networkResult ->
                            syncPersistenceMutex.withLock {
                                persistAccountChainNetworkSyncResult(wallet, networkResult)
                            }
                            onProgress()
                        },
                    )
                }
                val pricesDeferred = async {
                    priceSyncService.syncSupportedAssetPricesOrNull(
                        localCurrencyCode = wallet.localCurrencyCode,
                        onPartialResult = { partialResult ->
                            syncPersistenceMutex.withLock {
                                persistWalletPrices(
                                    wallet = wallet,
                                    result = partialResult,
                                    clearMissingPrices = false,
                                )
                            }
                            onProgress()
                        },
                    )
                }

                val evmResult = evmDeferred.await()
                syncPersistenceMutex.withLock {
                    persistEvmSyncResult(wallet, evmResult)
                }
                onProgress()

                val utxoResult = utxoDeferred.await()
                syncPersistenceMutex.withLock {
                    persistUtxoSyncResult(wallet, utxoResult)
                }
                onProgress()

                val solanaResult = solanaDeferred.await()
                syncPersistenceMutex.withLock {
                    persistSolanaSyncResult(wallet, solanaResult)
                }
                onProgress()

                val accountChainResult = accountChainDeferred.await()
                syncPersistenceMutex.withLock {
                    persistAccountChainSyncResult(wallet, accountChainResult)
                }
                onProgress()

                val priceResult = pricesDeferred.await()
                syncPersistenceMutex.withLock {
                    persistWalletPrices(wallet, priceResult)
                }
                onProgress()

                SatraWalletDataSyncResult(
                    evmSyncResult = evmResult,
                    utxoSyncResult = utxoResult,
                    solanaSyncResult = solanaResult,
                    accountChainSyncResult = accountChainResult,
                    priceSyncResult = priceResult,
                )
            }
        }

    suspend fun syncWalletHistoryData(
        walletId: String,
        onProgress: suspend () -> Unit = {},
    ): SatraWalletDataSyncResult =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId)
                ?: error("Wallet not found: $walletId")
            val addresses = walletDao.getWalletAddresses(walletId)
            val utxoWalletSecrets = wallet.decryptUtxoWalletSecretsOrNull()
            coroutineScope {
                val evmDeferred = async {
                    evmWalletSyncService.syncWallet(
                        walletId = walletId,
                        addresses = addresses,
                        onNetworkResult = { networkResult ->
                            syncPersistenceMutex.withLock {
                                persistEvmNetworkHistoryResult(wallet, networkResult)
                            }
                            onProgress()
                        },
                    )
                }
                val utxoDeferred = async {
                    utxoWalletSyncService.syncWallet(
                        wallet = wallet,
                        addresses = addresses,
                        walletSecrets = utxoWalletSecrets,
                        onNetworkResult = { networkResult ->
                            syncPersistenceMutex.withLock {
                                persistUtxoNetworkHistoryResult(wallet, networkResult)
                            }
                            onProgress()
                        },
                    )
                }
                val solanaDeferred = async {
                    solanaWalletSyncService.syncWallet(
                        walletId = walletId,
                        addresses = addresses,
                        onNetworkResult = { networkResult ->
                            syncPersistenceMutex.withLock {
                                persistSolanaNetworkHistoryResult(wallet, networkResult)
                            }
                            onProgress()
                        },
                    )
                }
                val accountChainDeferred = async {
                    accountChainWalletSyncService.syncWallet(
                        walletId = walletId,
                        addresses = addresses,
                        onNetworkResult = { networkResult ->
                            syncPersistenceMutex.withLock {
                                persistAccountChainNetworkHistoryResult(wallet, networkResult)
                            }
                            onProgress()
                        },
                    )
                }

                val evmResult = evmDeferred.await()
                syncPersistenceMutex.withLock {
                    persistEvmHistorySyncResult(wallet, evmResult)
                }
                onProgress()

                val utxoResult = utxoDeferred.await()
                syncPersistenceMutex.withLock {
                    persistUtxoHistorySyncResult(wallet, utxoResult)
                }
                onProgress()

                val solanaResult = solanaDeferred.await()
                syncPersistenceMutex.withLock {
                    persistSolanaHistorySyncResult(wallet, solanaResult)
                }
                onProgress()

                val accountChainResult = accountChainDeferred.await()
                syncPersistenceMutex.withLock {
                    persistAccountChainHistorySyncResult(wallet, accountChainResult)
                }
                onProgress()

                SatraWalletDataSyncResult(
                    evmSyncResult = evmResult,
                    utxoSyncResult = utxoResult,
                    solanaSyncResult = solanaResult,
                    accountChainSyncResult = accountChainResult,
                    priceSyncResult = null,
                )
            }
        }

    suspend fun syncAssetNetworks(
        walletId: String,
        symbol: String,
        onProgress: suspend () -> Unit = {},
    ) {
        val normalizedSymbol = symbol.trim().uppercase()
        if (normalizedSymbol.isBlank()) return
        val networkIds = SupportedAssetCatalog.assets
            .asSequence()
            .filter { asset -> asset.symbol.equals(normalizedSymbol, ignoreCase = true) }
            .map { asset -> asset.networkId }
            .distinct()
            .toList()
        val networksById = SupportedAssetCatalog.networks.associateBy { network -> network.networkId }
        coroutineScope {
            networkIds.map { networkId ->
                async {
                    when (networksById[networkId]?.family) {
                        "evm" -> syncEvmNetwork(walletId, networkId)
                        "utxo" -> syncUtxoNetwork(walletId, networkId)
                        "solana" -> syncSolanaNetwork(walletId, networkId)
                        null -> Unit
                        else -> syncAccountChainNetwork(walletId, networkId)
                    }
                    onProgress()
                }
            }.forEach { deferred -> deferred.await() }
        }
    }

    suspend fun syncWalletPrices(walletId: String): SatraPriceSyncResult? =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId)
                ?: error("Wallet not found: $walletId")
            val result = priceSyncService.syncSupportedAssetPricesOrNull(
                localCurrencyCode = wallet.localCurrencyCode,
                onPartialResult = { partialResult ->
                    syncPersistenceMutex.withLock {
                        persistWalletPrices(
                            wallet = wallet,
                            result = partialResult,
                            clearMissingPrices = false,
                        )
                    }
                },
            )
            syncPersistenceMutex.withLock {
                persistWalletPrices(wallet, result)
            }
            result
        }

    suspend fun syncMarketData(
        localCurrencyCode: String? = null,
        onProgress: suspend () -> Unit = {},
    ): SatraPriceSyncResult? =
        withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallets().firstOrNull { it.isActive }
                ?: walletDao.getWallets().firstOrNull()
            val currencyCode = localCurrencyCode
                ?: wallet?.localCurrencyCode
                ?: walletDao.getAppSettings().localCurrencyCode
            val result = priceSyncService.syncSupportedAssetPricesOrNull(
                localCurrencyCode = currencyCode,
                onPartialResult = { partialResult ->
                    syncPersistenceMutex.withLock {
                        if (wallet != null) {
                            persistWalletPrices(
                                wallet = wallet,
                                result = partialResult,
                                clearMissingPrices = false,
                            )
                        } else {
                            persistMarketData(partialResult.marketData)
                        }
                    }
                    onProgress()
                },
            )
            syncPersistenceMutex.withLock {
                if (wallet != null) {
                    persistWalletPrices(wallet, result)
                } else {
                    persistMarketData(result?.marketData.orEmpty())
                }
            }
            onProgress()
            result
        }

    suspend fun syncAssetMarketDetail(
        symbol: String,
        localCurrencyCode: String? = null,
    ): AssetMarketDataRecord? =
        withContext(Dispatchers.IO) {
            val currencyCode = localCurrencyCode
                ?: walletDao.getWallets().firstOrNull { it.isActive }?.localCurrencyCode
                ?: walletDao.getAppSettings().localCurrencyCode
            val detail = priceSyncService.syncAssetMarketDetailOrNull(
                symbol = symbol,
                localCurrencyCode = currencyCode,
            )
            detail?.let { marketData ->
                upsertMergedAssetMarketData(marketData.toNewAssetMarketDataRecord())
            }
            walletDao.getAssetMarketData(symbol)
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
                            priceSyncService.syncSupportedAssetPricesOrNull(
                                localCurrencyCode = localCurrencyCode,
                                onPartialResult = { partialResult ->
                                    syncPersistenceMutex.withLock {
                                        wallets
                                            .filter { wallet -> wallet.localCurrencyCode == localCurrencyCode }
                                            .forEach { wallet ->
                                                persistWalletPrices(
                                                    wallet = wallet,
                                                    result = partialResult,
                                                    clearMissingPrices = false,
                                                )
                                            }
                                    }
                                },
                            )
                        }
                    }
                    .mapValues { (_, deferred) -> deferred.await() }

                wallets.map { wallet ->
                    pricesByCurrency[wallet.localCurrencyCode]
                        .also { result ->
                            syncPersistenceMutex.withLock {
                                persistWalletPrices(wallet, result)
                            }
                        }
                }
            }
        }

    private fun SatraWalletDao.insertDerivedReceiveAccounts(
        walletId: String,
        accounts: List<DerivedReceiveAccount>,
        secretCipher: SatraSecretCipher,
    ) {
        accounts.forEach { account ->
            val metadataJson = account.metadataJson()
            val secretId = insertWalletSecret(
                secretCipher.encrypt(
                    walletId = walletId,
                    secretType = WalletSecretType.PrivateKey.value,
                    plaintext = account.privateKeyHex,
                    networkId = account.networkId,
                    derivationPath = account.derivationPath,
                    metadataJson = metadataJson,
                ),
            )
            val addressId = insertWalletAddress(
                NewWalletAddressRecord(
                    walletId = walletId,
                    networkId = account.networkId,
                    address = account.address,
                    addressType = if (account.isChange) WalletAddressType.Change.value else WalletAddressType.Receive.value,
                    derivationPath = account.derivationPath,
                    publicKey = account.publicKeyHex,
                    isPrimary = account.isPrimary,
                    isChange = account.isChange,
                    addressIndex = account.addressIndex,
                    label = account.derivationLabel,
                    metadataJson = metadataJson,
                ),
            )
            insertWalletPrivateKey(
                NewWalletPrivateKeyRecord(
                    walletId = walletId,
                    networkId = account.networkId,
                    addressId = addressId,
                    secretId = secretId,
                    keyFormat = "hex",
                    derivationPath = account.derivationPath,
                    publicKey = account.publicKeyHex,
                    keySource = WalletPrivateKeySource.MnemonicDerived.value,
                    isImported = false,
                    keyFingerprint = account.keyFingerprint,
                    metadataJson = metadataJson,
                ),
            )
        }
    }

    private fun SatraWalletDao.insertImportedPrivateKeyReceiveAccount(
        walletId: String,
        networkId: String,
        account: DerivedReceiveAccount,
        secretId: String,
    ) {
        val metadataJson = account.metadataJson()
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
                metadataJson = metadataJson,
            ),
        )
        insertWalletPrivateKey(
            NewWalletPrivateKeyRecord(
                walletId = walletId,
                networkId = networkId,
                addressId = addressId,
                secretId = secretId,
                keyFormat = "hex",
                derivationPath = account.derivationPath,
                publicKey = account.publicKeyHex,
                keySource = WalletPrivateKeySource.Imported.value,
                isImported = true,
                keyFingerprint = account.keyFingerprint,
                metadataJson = metadataJson,
            ),
        )
    }

    private fun persistEvmSyncResult(
        wallet: WalletRecord,
        result: EvmWalletSyncResult,
    ) {
        val nowMillis = System.currentTimeMillis()
        result.networkResults.forEach { networkResult -> persistEvmNetworkSyncResult(wallet, networkResult) }
        walletDao.updateWalletSyncMetadata(
            walletId = wallet.walletId,
            metadataJson = (walletDao.getWallet(wallet.walletId)?.metadataJson ?: wallet.metadataJson)
                .withEvmSyncResult(result),
            nowMillis = nowMillis,
        )
    }

    private fun persistEvmNetworkSyncResult(
        wallet: WalletRecord,
        networkResult: EvmNetworkSyncResult,
    ) {
        val nowMillis = System.currentTimeMillis()
        val currentAssetsById = walletDao.getWalletAssets(wallet.walletId).associateBy { it.assetId }
        networkResult.balances.forEach { balance ->
            walletDao.updateWalletAssetBalance(
                walletId = wallet.walletId,
                assetId = balance.assetId,
                balanceRaw = balance.balanceRaw,
                balanceDecimal = balance.balanceDecimal,
                balanceFiatValue = currentAssetsById[balance.assetId].cachedBalanceFiatValue(
                    balanceDecimal = balance.balanceDecimal,
                    localCurrencyCode = wallet.localCurrencyCode,
                ),
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
        refreshWalletTransactionFiatValues(wallet, nowMillis)
        refreshWalletFiatBalance(wallet, nowMillis)
    }

    private fun persistEvmHistorySyncResult(
        wallet: WalletRecord,
        result: EvmWalletSyncResult,
    ) {
        val nowMillis = System.currentTimeMillis()
        walletDao.updateWalletSyncMetadata(
            walletId = wallet.walletId,
            metadataJson = (walletDao.getWallet(wallet.walletId)?.metadataJson ?: wallet.metadataJson)
                .withEvmSyncResult(result),
            nowMillis = nowMillis,
        )
    }

    private fun persistEvmNetworkHistoryResult(
        wallet: WalletRecord,
        networkResult: EvmNetworkSyncResult,
    ) {
        val nowMillis = System.currentTimeMillis()
        networkResult.transactions.forEach { transaction ->
            walletDao.upsertWalletTransaction(
                transaction = transaction.toNewWalletTransactionRecord(wallet.localCurrencyCode),
                nowMillis = nowMillis,
            )
        }
        refreshWalletTransactionFiatValues(wallet, nowMillis)
    }

    private fun persistUtxoSyncResult(
        wallet: WalletRecord,
        result: UtxoWalletSyncResult,
    ) {
        persistScannedUtxoAccounts(wallet.walletId, result.networkResults.flatMap { it.scannedAccounts })
        val nowMillis = System.currentTimeMillis()
        result.networkResults.forEach { networkResult -> persistUtxoNetworkSyncResult(wallet, networkResult) }
        walletDao.updateWalletSyncMetadata(
            walletId = wallet.walletId,
            metadataJson = (walletDao.getWallet(wallet.walletId)?.metadataJson ?: wallet.metadataJson)
                .withUtxoSyncResult(result),
            nowMillis = nowMillis,
        )
    }

    private fun persistUtxoNetworkSyncResult(
        wallet: WalletRecord,
        networkResult: UtxoNetworkSyncResult,
    ) {
        persistScannedUtxoAccounts(wallet.walletId, networkResult.scannedAccounts)
        val nowMillis = System.currentTimeMillis()
        val currentAssetsById = walletDao.getWalletAssets(wallet.walletId).associateBy { it.assetId }
        networkResult.balances.forEach { balance ->
            walletDao.updateWalletAssetBalance(
                walletId = wallet.walletId,
                assetId = balance.assetId,
                balanceRaw = balance.balanceRaw,
                balanceDecimal = balance.balanceDecimal,
                balanceFiatValue = currentAssetsById[balance.assetId].cachedBalanceFiatValue(
                    balanceDecimal = balance.balanceDecimal,
                    localCurrencyCode = wallet.localCurrencyCode,
                ),
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
        refreshWalletTransactionFiatValues(wallet, nowMillis)
        refreshWalletFiatBalance(wallet, nowMillis)
    }

    private fun persistUtxoHistorySyncResult(
        wallet: WalletRecord,
        result: UtxoWalletSyncResult,
    ) {
        persistScannedUtxoAccounts(wallet.walletId, result.networkResults.flatMap { it.scannedAccounts })
        val nowMillis = System.currentTimeMillis()
        walletDao.updateWalletSyncMetadata(
            walletId = wallet.walletId,
            metadataJson = (walletDao.getWallet(wallet.walletId)?.metadataJson ?: wallet.metadataJson)
                .withUtxoSyncResult(result),
            nowMillis = nowMillis,
        )
    }

    private fun persistUtxoNetworkHistoryResult(
        wallet: WalletRecord,
        networkResult: UtxoNetworkSyncResult,
    ) {
        persistScannedUtxoAccounts(wallet.walletId, networkResult.scannedAccounts)
        val nowMillis = System.currentTimeMillis()
        networkResult.transactions.forEach { transaction ->
            walletDao.upsertWalletTransaction(
                transaction = transaction.toNewWalletTransactionRecord(wallet.localCurrencyCode),
                nowMillis = nowMillis,
            )
        }
        refreshWalletTransactionFiatValues(wallet, nowMillis)
    }

    private fun persistSolanaSyncResult(
        wallet: WalletRecord,
        result: SolanaWalletSyncResult,
    ) {
        val nowMillis = System.currentTimeMillis()
        result.networkResults.forEach { networkResult -> persistSolanaNetworkSyncResult(wallet, networkResult) }
        walletDao.updateWalletSyncMetadata(
            walletId = wallet.walletId,
            metadataJson = (walletDao.getWallet(wallet.walletId)?.metadataJson ?: wallet.metadataJson)
                .withSolanaSyncResult(result),
            nowMillis = nowMillis,
        )
    }

    private fun persistSolanaNetworkSyncResult(
        wallet: WalletRecord,
        networkResult: SolanaNetworkSyncResult,
    ) {
        val nowMillis = System.currentTimeMillis()
        val currentAssetsById = walletDao.getWalletAssets(wallet.walletId).associateBy { it.assetId }
        networkResult.balances.forEach { balance ->
            walletDao.updateWalletAssetBalance(
                walletId = wallet.walletId,
                assetId = balance.assetId,
                balanceRaw = balance.balanceRaw,
                balanceDecimal = balance.balanceDecimal,
                balanceFiatValue = currentAssetsById[balance.assetId].cachedBalanceFiatValue(
                    balanceDecimal = balance.balanceDecimal,
                    localCurrencyCode = wallet.localCurrencyCode,
                ),
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
        refreshWalletTransactionFiatValues(wallet, nowMillis)
        refreshWalletFiatBalance(wallet, nowMillis)
    }

    private fun persistSolanaHistorySyncResult(
        wallet: WalletRecord,
        result: SolanaWalletSyncResult,
    ) {
        val nowMillis = System.currentTimeMillis()
        walletDao.updateWalletSyncMetadata(
            walletId = wallet.walletId,
            metadataJson = (walletDao.getWallet(wallet.walletId)?.metadataJson ?: wallet.metadataJson)
                .withSolanaSyncResult(result),
            nowMillis = nowMillis,
        )
    }

    private fun persistSolanaNetworkHistoryResult(
        wallet: WalletRecord,
        networkResult: SolanaNetworkSyncResult,
    ) {
        val nowMillis = System.currentTimeMillis()
        networkResult.transactions.forEach { transaction ->
            walletDao.upsertWalletTransaction(
                transaction = transaction.toNewWalletTransactionRecord(wallet.localCurrencyCode),
                nowMillis = nowMillis,
            )
        }
        refreshWalletTransactionFiatValues(wallet, nowMillis)
    }

    private fun persistAccountChainSyncResult(
        wallet: WalletRecord,
        result: AccountChainWalletSyncResult,
    ) {
        val nowMillis = System.currentTimeMillis()
        result.networkResults.forEach { networkResult -> persistAccountChainNetworkSyncResult(wallet, networkResult) }
        walletDao.updateWalletSyncMetadata(
            walletId = wallet.walletId,
            metadataJson = (walletDao.getWallet(wallet.walletId)?.metadataJson ?: wallet.metadataJson)
                .withAccountChainSyncResult(result),
            nowMillis = nowMillis,
        )
    }

    private fun persistAccountChainNetworkSyncResult(
        wallet: WalletRecord,
        networkResult: AccountChainNetworkSyncResult,
    ) {
        val nowMillis = System.currentTimeMillis()
        val currentAssetsById = walletDao.getWalletAssets(wallet.walletId).associateBy { it.assetId }
        networkResult.balances.forEach { balance ->
            walletDao.updateWalletAssetBalance(
                walletId = wallet.walletId,
                assetId = balance.assetId,
                balanceRaw = balance.balanceRaw,
                balanceDecimal = balance.balanceDecimal,
                balanceFiatValue = currentAssetsById[balance.assetId].cachedBalanceFiatValue(
                    balanceDecimal = balance.balanceDecimal,
                    localCurrencyCode = wallet.localCurrencyCode,
                ),
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
        refreshWalletTransactionFiatValues(wallet, nowMillis)
        refreshWalletFiatBalance(wallet, nowMillis)
    }

    private fun persistAccountChainHistorySyncResult(
        wallet: WalletRecord,
        result: AccountChainWalletSyncResult,
    ) {
        val nowMillis = System.currentTimeMillis()
        walletDao.updateWalletSyncMetadata(
            walletId = wallet.walletId,
            metadataJson = (walletDao.getWallet(wallet.walletId)?.metadataJson ?: wallet.metadataJson)
                .withAccountChainSyncResult(result),
            nowMillis = nowMillis,
        )
    }

    private fun persistAccountChainNetworkHistoryResult(
        wallet: WalletRecord,
        networkResult: AccountChainNetworkSyncResult,
    ) {
        val nowMillis = System.currentTimeMillis()
        networkResult.transactions.forEach { transaction ->
            walletDao.upsertWalletTransaction(
                transaction = transaction.toNewWalletTransactionRecord(wallet.localCurrencyCode),
                nowMillis = nowMillis,
            )
        }
        refreshWalletTransactionFiatValues(wallet, nowMillis)
    }

    private fun persistScannedUtxoAccounts(
        walletId: String,
        accounts: List<DerivedReceiveAccount>,
    ) {
        if (accounts.isEmpty()) return
        val existing = walletDao.getWalletAddresses(walletId)
        val existingKeys = existing.map { address ->
            "${address.networkId}:${address.derivationPath}:${address.address}"
        }.toSet()
        val missing = accounts
            .distinctBy { "${it.networkId}:${it.derivationPath}:${it.address}" }
            .filterNot { account ->
                "${account.networkId}:${account.derivationPath}:${account.address}" in existingKeys
            }
        if (missing.isNotEmpty()) {
            walletDao.insertDerivedReceiveAccounts(walletId, missing, secretCipher)
        }
    }

    private fun cachedMarketUsdFxRate(localCurrencyCode: String): FxRateQuote? {
        val normalizedCurrency = localCurrencyCode.uppercase()
        if (normalizedCurrency == DEFAULT_LOCAL_CURRENCY_CODE) {
            return FxRateQuote(
                baseCurrency = DEFAULT_LOCAL_CURRENCY_CODE,
                quoteCurrency = DEFAULT_LOCAL_CURRENCY_CODE,
                rate = BigDecimal.ONE,
                provider = "local-fx-cache",
            )
        }
        return walletDao.getAllAssetMarketData()
            .asSequence()
            .filter { market -> market.localCurrencyCode.equals(normalizedCurrency, ignoreCase = true) }
            .mapNotNull { market ->
                val usdPrice = market.priceUsd.toPositiveBigDecimalOrNull() ?: return@mapNotNull null
                val localPrice = market.priceLocal.toPositiveBigDecimalOrNull() ?: return@mapNotNull null
                localPrice.divide(usdPrice, 18, RoundingMode.HALF_UP)
            }
            .firstOrNull { rate -> rate > BigDecimal.ZERO }
            ?.let { rate ->
                FxRateQuote(
                    baseCurrency = DEFAULT_LOCAL_CURRENCY_CODE,
                    quoteCurrency = normalizedCurrency,
                    rate = rate,
                    provider = "local-market-fx-cache",
                )
            }
    }

    private fun repriceMarketDataFromUsd(
        localCurrencyCode: String,
        fxQuote: FxRateQuote,
        nowMillis: Long,
    ) {
        walletDao.getAllAssetMarketData().forEach { market ->
            val usdPrice = market.priceUsd.toPositiveBigDecimalOrNull() ?: return@forEach
            walletDao.upsertAssetMarketData(
                NewAssetMarketDataRecord(
                    symbol = market.symbol,
                    name = market.name,
                    coinGeckoId = market.coinGeckoId,
                    localCurrencyCode = localCurrencyCode,
                    priceUsd = usdPrice.toPlainString(),
                    priceLocal = usdPrice.multiply(fxQuote.rate).toPlainString(),
                    marketCapUsd = market.marketCapUsd,
                    marketCapLocal = market.marketCapUsd.localFromUsd(fxQuote.rate),
                    volume24hUsd = market.volume24hUsd,
                    volume24hLocal = market.volume24hUsd.localFromUsd(fxQuote.rate),
                    high24hUsd = market.high24hUsd,
                    low24hUsd = market.low24hUsd,
                    priceChange24hPercent = market.priceChange24hPercent,
                    description = market.description,
                    homepageUrl = market.homepageUrl,
                    provider = market.provider,
                    chart7dJson = market.chart7dJson,
                    updatedAt = nowMillis,
                    metadataJson = market.metadataJson.withLocalCurrencyReprice(fxQuote, nowMillis),
                ),
            )
        }
    }

    private fun repriceWalletDataFromUsd(
        localCurrencyCode: String,
        fxQuote: FxRateQuote,
        nowMillis: Long,
    ) {
        val assetsById = SupportedAssetCatalog.assets.associateBy { asset -> asset.assetId }
        val marketDataBySymbol = walletDao.getAllAssetMarketData()
            .associateBy { market -> market.symbol.uppercase() }
        val settingsMetadataJson = walletDao.getAppSettings().metadataJson

        walletDao.getWallets().forEach { wallet ->
            val localPricesByAssetId = mutableMapOf<String, BigDecimal>()
            var totalFiat = BigDecimal.ZERO
            walletDao.getWalletAssets(wallet.walletId).forEach { walletAsset ->
                val asset = assetsById[walletAsset.assetId] ?: return@forEach
                val usdPrice = walletAsset.cachedUsdPrice(
                    priceSymbol = asset.priceSymbol,
                    marketDataBySymbol = marketDataBySymbol,
                    settingsMetadataJson = settingsMetadataJson,
                )
                if (usdPrice == null) {
                    walletDao.updateWalletAssetPrice(
                        walletId = wallet.walletId,
                        assetId = walletAsset.assetId,
                        priceFiatValue = "0",
                        balanceFiatValue = "0",
                        localCurrencyCode = localCurrencyCode,
                        metadataJson = walletAsset.metadataJson.withPriceSync(
                            price = null,
                            cached = false,
                            localCurrencyCode = localCurrencyCode,
                            failed = false,
                            nowMillis = nowMillis,
                        ),
                        nowMillis = nowMillis,
                    )
                    return@forEach
                }
                val localPrice = usdPrice.multiply(fxQuote.rate)
                val balanceFiat = walletAsset.balanceDecimal
                    .toBigDecimalOrZero()
                    .multiply(localPrice)
                localPricesByAssetId[walletAsset.assetId] = localPrice
                totalFiat += balanceFiat
                walletDao.updateWalletAssetPrice(
                    walletId = wallet.walletId,
                    assetId = walletAsset.assetId,
                    priceFiatValue = localPrice.toPlainString(),
                    balanceFiatValue = balanceFiat.toPlainString(),
                    localCurrencyCode = localCurrencyCode,
                    metadataJson = walletAsset.metadataJson.withCachedUsdReprice(
                        usdPrice = usdPrice,
                        localCurrencyCode = localCurrencyCode,
                        fxProvider = fxQuote.provider,
                        nowMillis = nowMillis,
                    ),
                    nowMillis = nowMillis,
                )
            }

            val fiatValuesByTransactionId = walletDao
                .getWalletTransactions(wallet.walletId)
                .associate { transaction ->
                    val fiatValue = localPricesByAssetId[transaction.assetId]
                        ?.let { localPrice ->
                            transaction.amountDecimal
                                .toBigDecimalOrZero()
                                .abs()
                                .multiply(localPrice)
                                .stripTrailingZeros()
                                .toPlainString()
                        }
                    transaction.transactionId to fiatValue
                }
            if (fiatValuesByTransactionId.isNotEmpty()) {
                walletDao.updateWalletTransactionFiatValues(
                    walletId = wallet.walletId,
                    localCurrencyCode = localCurrencyCode,
                    fiatValuesByTransactionId = fiatValuesByTransactionId,
                    nowMillis = nowMillis,
                )
            }
            walletDao.updateWalletFiatBalance(
                walletId = wallet.walletId,
                balanceFiatValue = totalFiat.toPlainString(),
                localCurrencyCode = localCurrencyCode,
                nowMillis = nowMillis,
            )
        }
    }

    private fun persistWalletPrices(
        wallet: WalletRecord,
        result: SatraPriceSyncResult?,
        clearMissingPrices: Boolean = true,
    ) {
        persistMarketData(result?.marketData.orEmpty())
        val walletAssets = walletDao.getWalletAssets(wallet.walletId)
        val pricesByAssetId = result?.prices.orEmpty().associateBy { it.asset.assetId }
        var totalFiat = BigDecimal.ZERO
        val nowMillis = result?.updatedAtMillis ?: System.currentTimeMillis()
        val localPricesByAssetId = mutableMapOf<String, BigDecimal>()

        walletAssets.forEach { walletAsset ->
            val price = pricesByAssetId[walletAsset.assetId]
            val cachedPrice = walletAsset.priceFiatValue.toBigDecimalOrZero()
                .takeIf { it > BigDecimal.ZERO && walletAsset.localCurrencyCode == wallet.localCurrencyCode }
            val localPrice = price?.localPrice ?: cachedPrice
            if (localPrice != null) {
                localPricesByAssetId[walletAsset.assetId] = localPrice
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
            } else {
                if (clearMissingPrices) {
                    walletDao.updateWalletAssetPrice(
                        walletId = wallet.walletId,
                        assetId = walletAsset.assetId,
                        priceFiatValue = "0",
                        balanceFiatValue = "0",
                        localCurrencyCode = wallet.localCurrencyCode,
                        metadataJson = walletAsset.metadataJson.withPriceSync(
                            price = null,
                            cached = false,
                            localCurrencyCode = wallet.localCurrencyCode,
                            failed = result?.failedSymbols.orEmpty().contains(
                                walletAsset.assetId.substringAfter(':').uppercase(),
                            ),
                            nowMillis = nowMillis,
                        ),
                        nowMillis = nowMillis,
                    )
                } else if (walletAsset.localCurrencyCode == wallet.localCurrencyCode) {
                    totalFiat += walletAsset.balanceFiatValue.toBigDecimalOrZero()
                }
            }
        }
        val fiatValuesByTransactionId: Map<String, String?> = walletDao
            .getWalletTransactions(wallet.walletId)
            .mapNotNull { transaction ->
                val fiatValue = localPricesByAssetId[transaction.assetId]
                    ?.let { localPrice ->
                        transaction.amountDecimal
                            .toBigDecimalOrZero()
                            .abs()
                            .multiply(localPrice)
                            .stripTrailingZeros()
                            .toPlainString()
                    }
                when {
                    fiatValue != null -> transaction.transactionId to fiatValue
                    clearMissingPrices -> transaction.transactionId to null
                    else -> null
                }
            }
            .toMap()
        if (fiatValuesByTransactionId.isNotEmpty()) {
            walletDao.updateWalletTransactionFiatValues(
                walletId = wallet.walletId,
                localCurrencyCode = wallet.localCurrencyCode,
                fiatValuesByTransactionId = fiatValuesByTransactionId,
                nowMillis = nowMillis,
            )
        }
        walletDao.updateWalletFiatBalance(
            walletId = wallet.walletId,
            balanceFiatValue = totalFiat.toPlainString(),
            localCurrencyCode = wallet.localCurrencyCode,
            nowMillis = nowMillis,
        )
    }

    private fun refreshWalletTransactionFiatValues(
        wallet: WalletRecord,
        nowMillis: Long,
    ) {
        val localPricesByAssetId = walletDao.getWalletAssets(wallet.walletId)
            .filter { asset ->
                asset.localCurrencyCode == wallet.localCurrencyCode &&
                    asset.priceFiatValue.toBigDecimalOrZero() > BigDecimal.ZERO
            }
            .associate { asset -> asset.assetId to asset.priceFiatValue.toBigDecimalOrZero() }
        val fiatValuesByTransactionId = walletDao
            .getWalletTransactions(wallet.walletId)
            .mapNotNull { transaction ->
                localPricesByAssetId[transaction.assetId]?.let { localPrice ->
                    transaction.transactionId to transaction.amountDecimal
                        .toBigDecimalOrZero()
                        .abs()
                        .multiply(localPrice)
                        .stripTrailingZeros()
                        .toPlainString()
                }
            }
            .toMap()
        if (fiatValuesByTransactionId.isNotEmpty()) {
            walletDao.updateWalletTransactionFiatValues(
                walletId = wallet.walletId,
                localCurrencyCode = wallet.localCurrencyCode,
                fiatValuesByTransactionId = fiatValuesByTransactionId,
                nowMillis = nowMillis,
            )
        }
    }

    private fun refreshWalletFiatBalance(
        wallet: WalletRecord,
        nowMillis: Long,
    ) {
        val totalFiat = walletDao.getWalletAssets(wallet.walletId)
            .filter { asset -> asset.localCurrencyCode == wallet.localCurrencyCode }
            .fold(BigDecimal.ZERO) { total, asset ->
                total + asset.balanceFiatValue.toBigDecimalOrZero()
            }
        walletDao.updateWalletFiatBalance(
            walletId = wallet.walletId,
            balanceFiatValue = totalFiat.toPlainString(),
            localCurrencyCode = wallet.localCurrencyCode,
            nowMillis = nowMillis,
        )
    }

    private fun persistMarketData(marketData: List<SatraAssetMarketData>) {
        marketData.forEach { item ->
            upsertMergedAssetMarketData(item.toNewAssetMarketDataRecord())
        }
    }

    private fun upsertMergedAssetMarketData(marketData: NewAssetMarketDataRecord) {
        val existing = walletDao.getAssetMarketData(marketData.symbol)
        walletDao.upsertAssetMarketData(
            existing?.let { marketData.mergedWithExisting(it) } ?: marketData,
        )
    }
}

data class SatraWalletDataSyncResult(
    val evmSyncResult: EvmWalletSyncResult,
    val utxoSyncResult: UtxoWalletSyncResult,
    val solanaSyncResult: SolanaWalletSyncResult,
    val accountChainSyncResult: AccountChainWalletSyncResult,
    val priceSyncResult: SatraPriceSyncResult?,
)

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun String?.cleanPassphrase(): String? =
    this?.takeIf(String::isNotEmpty)

private fun SatraAssetMarketData.toNewAssetMarketDataRecord(): NewAssetMarketDataRecord =
    NewAssetMarketDataRecord(
        symbol = symbol,
        name = name,
        coinGeckoId = coinGeckoId,
        localCurrencyCode = localCurrencyCode,
        priceUsd = usdPrice.toPlainString(),
        priceLocal = localPrice.toPlainString(),
        marketCapUsd = marketCapUsd?.toPlainString(),
        marketCapLocal = marketCapLocal?.toPlainString(),
        volume24hUsd = volume24hUsd?.toPlainString(),
        volume24hLocal = volume24hLocal?.toPlainString(),
        high24hUsd = high24hUsd?.toPlainString(),
        low24hUsd = low24hUsd?.toPlainString(),
        priceChange24hPercent = priceChange24hPercent?.toPlainString(),
        description = description,
        homepageUrl = homepageUrl,
        provider = provider,
        chart7dJson = chart7dJson,
        updatedAt = syncedAtMillis,
        metadataJson = JSONObject()
            .put("source", "market-data-sync")
            .put("provider", provider)
            .put("providerAssetId", coinGeckoId)
            .toString(),
    )

private fun NewAssetMarketDataRecord.mergedWithExisting(
    existing: AssetMarketDataRecord,
): NewAssetMarketDataRecord {
    val currencyMatches = localCurrencyCode == existing.localCurrencyCode
    val usdToLocalRate = priceLocal.toBigDecimalOrZero()
        .divide(
            priceUsd.toBigDecimalOrZero().takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE,
            18,
            RoundingMode.HALF_UP,
        )

    fun mergeText(newValue: String?, oldValue: String?): String? =
        newValue?.takeIf(String::isNotBlank) ?: oldValue?.takeIf(String::isNotBlank)

    fun mergeJsonArray(newValue: String, oldValue: String): String =
        newValue.takeUnless { value -> value.isBlank() || value == "[]" }
            ?: oldValue.takeUnless { value -> value.isBlank() || value == "[]" }
            ?: "[]"

    fun localFromUsd(usdValue: String?): String? =
        usdValue
            ?.toBigDecimalOrZero()
            ?.takeIf { it > BigDecimal.ZERO }
            ?.multiply(usdToLocalRate)
            ?.stripTrailingZeros()
            ?.toPlainString()

    val mergedMarketCapUsd = marketCapUsd ?: existing.marketCapUsd
    val mergedVolumeUsd = volume24hUsd ?: existing.volume24hUsd

    return copy(
        name = name.ifBlank { existing.name },
        coinGeckoId = coinGeckoId ?: existing.coinGeckoId,
        marketCapUsd = mergedMarketCapUsd,
        marketCapLocal = marketCapLocal
            ?: existing.marketCapLocal?.takeIf { currencyMatches }
            ?: localFromUsd(mergedMarketCapUsd),
        volume24hUsd = mergedVolumeUsd,
        volume24hLocal = volume24hLocal
            ?: existing.volume24hLocal?.takeIf { currencyMatches }
            ?: localFromUsd(mergedVolumeUsd),
        high24hUsd = high24hUsd ?: existing.high24hUsd,
        low24hUsd = low24hUsd ?: existing.low24hUsd,
        priceChange24hPercent = priceChange24hPercent ?: existing.priceChange24hPercent,
        description = mergeText(description, existing.description),
        homepageUrl = mergeText(homepageUrl, existing.homepageUrl),
        chart7dJson = mergeJsonArray(chart7dJson, existing.chart7dJson),
    )
}

private val DerivedReceiveAccount.derivationLabel: String
    get() = when (derivationName) {
        "phantom" -> "Phantom"
        "taproot" -> "Taproot"
        "segwit" -> "SegWit"
        "nested_segwit" -> "Nested SegWit"
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
        .put("isChange", isChange)
        .put("addressIndex", addressIndex)
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

private fun UtxoAssetBalance.toMetadataJson(networkResult: UtxoNetworkSyncResult): String =
    JSONObject()
        .put("syncFamily", "utxo")
        .put("syncProvider", providerName)
        .put("syncStatus", networkResult.balanceCompleteness.value)
        .put("syncHistoryStatus", networkResult.historyCompleteness.value)
        .put("syncBlockHeight", blockHeight)
        .put("syncUpdatedAt", syncedAtMillis)
        .put("addressesScanned", networkResult.addressesScanned)
        .put("syncLastError", networkResult.error)
        .put(
            "utxos",
            JSONArray().also { items ->
                utxos.forEach { utxo ->
                    items.put(
                        JSONObject()
                            .put("transactionHash", utxo.transactionHash)
                            .put("outputIndex", utxo.outputIndex)
                            .put("valueSats", utxo.valueSats)
                            .put("height", utxo.height)
                            .put("address", utxo.address)
                            .put("derivationPath", utxo.derivationPath)
                            .put("isChange", utxo.isChange),
                    )
                }
            },
        )
        .toString()

private fun UtxoNormalizedTransaction.toNewWalletTransactionRecord(
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
        timestamp = timestampMillis,
        metadataJson = metadataJson,
    )

private fun SolanaAssetBalance.toMetadataJson(networkResult: SolanaNetworkSyncResult): String =
    JSONObject()
        .put("syncFamily", "solana")
        .put("syncProvider", providerName)
        .put("syncStatus", networkResult.balanceCompleteness.value)
        .put("syncHistoryStatus", networkResult.historyCompleteness.value)
        .put("syncSlot", slot)
        .put("syncUpdatedAt", syncedAtMillis)
        .put("tokenAccountCount", tokenAccounts.size)
        .put("syncCursorBeforeSignature", networkResult.cursorBeforeSignature)
        .put("syncLastError", networkResult.error)
        .put(
            "tokenAccounts",
            JSONArray().also { items ->
                tokenAccounts.forEach { tokenAccount ->
                    items.put(
                        JSONObject()
                            .put("address", tokenAccount.address)
                            .put("owner", tokenAccount.owner)
                            .put("mint", tokenAccount.mint)
                            .put("amountRaw", tokenAccount.amountRaw.toString())
                            .put("decimals", tokenAccount.decimals)
                            .put("programId", tokenAccount.programId),
                    )
                }
            },
        )
        .toString()

private fun SolanaNormalizedTransaction.toNewWalletTransactionRecord(
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
        timestamp = timestampMillis,
        metadataJson = metadataJson,
    )

private fun AccountChainAssetBalance.toMetadataJson(networkResult: AccountChainNetworkSyncResult): String =
    JSONObject()
        .put("syncFamily", "account-chain")
        .put("syncProvider", providerName)
        .put("syncStatus", networkResult.balanceCompleteness.value)
        .put("syncHistoryStatus", networkResult.historyCompleteness.value)
        .put("syncLedgerHeight", ledgerHeight)
        .put("syncUpdatedAt", syncedAtMillis)
        .put("syncCursor", networkResult.cursor)
        .put("syncLastError", networkResult.error)
        .toString()

private fun AccountChainNormalizedTransaction.toNewWalletTransactionRecord(
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

private fun String.withUtxoSyncResult(result: UtxoWalletSyncResult): String {
    val root = runCatching { JSONObject(this) }.getOrElse { JSONObject() }
    val networks = JSONArray()
    result.networkResults.forEach { network ->
        networks.put(
            JSONObject()
                .put("networkId", network.networkId)
                .put("addressesScanned", network.addressesScanned)
                .put("balanceStatus", network.balanceCompleteness.value)
                .put("historyStatus", network.historyCompleteness.value)
                .put("provider", network.providerName)
                .put("latestBlockHeight", network.latestBlockHeight)
                .put("transactionCount", network.transactions.size)
                .put("error", network.error),
        )
    }
    root.put(
        "utxoSync",
        JSONObject()
            .put("networkCount", result.networkResults.size)
            .put("syncedNetworkCount", result.syncedNetworkCount)
            .put("networks", networks),
    )
    return root.toString()
}

private fun String.withSolanaSyncResult(result: SolanaWalletSyncResult): String {
    val root = runCatching { JSONObject(this) }.getOrElse { JSONObject() }
    val networks = JSONArray()
    result.networkResults.forEach { network ->
        networks.put(
            JSONObject()
                .put("networkId", network.networkId)
                .put("address", network.address)
                .put("tokenAccountCount", network.tokenAccountCount)
                .put("balanceStatus", network.balanceCompleteness.value)
                .put("historyStatus", network.historyCompleteness.value)
                .put("provider", network.providerName)
                .put("latestSlot", network.latestSlot)
                .put("cursorBeforeSignature", network.cursorBeforeSignature)
                .put("transactionCount", network.transactions.size)
                .put("error", network.error),
        )
    }
    root.put(
        "solanaSync",
        JSONObject()
            .put("networkCount", result.networkResults.size)
            .put("syncedNetworkCount", result.syncedNetworkCount)
            .put("networks", networks),
    )
    return root.toString()
}

private fun String.withAccountChainSyncResult(result: AccountChainWalletSyncResult): String {
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
                .put("latestLedger", network.latestLedger)
                .put("cursor", network.cursor)
                .put("transactionCount", network.transactions.size)
                .put("error", network.error),
        )
    }
    root.put(
        "accountChainSync",
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
    val previousUsdPrice = root.optJSONObject("priceSync")
        ?.optString("usdPrice")
        ?.takeUnless { value -> value.isBlank() || value == "null" }
    val usdPrice = price?.usdPrice?.toPlainString() ?: previousUsdPrice
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
            .put("usdPrice", usdPrice ?: JSONObject.NULL)
            .put("localCurrencyCode", localCurrencyCode)
            .put("updatedAt", nowMillis),
    )
    return root.toString()
}

private fun String.withCachedUsdReprice(
    usdPrice: BigDecimal,
    localCurrencyCode: String,
    fxProvider: String,
    nowMillis: Long,
): String {
    val root = runCatching { JSONObject(this) }.getOrElse { JSONObject() }
    root.put(
        "priceSync",
        JSONObject()
            .put("status", "cached")
            .put("provider", "local-database+$fxProvider")
            .put("providerAssetId", JSONObject.NULL)
            .put("usdPrice", usdPrice.toPlainString())
            .put("localCurrencyCode", localCurrencyCode)
            .put("updatedAt", nowMillis),
    )
    return root.toString()
}

private fun String.withUsdFxRate(
    fxQuote: FxRateQuote,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val root = runCatching { JSONObject(this) }.getOrElse { JSONObject() }
    val rates = root.optJSONObject("usdFxRates") ?: JSONObject()
    rates.put(
        fxQuote.quoteCurrency.uppercase(),
        JSONObject()
            .put("baseCurrency", fxQuote.baseCurrency.uppercase())
            .put("quoteCurrency", fxQuote.quoteCurrency.uppercase())
            .put("rate", fxQuote.rate.toPlainString())
            .put("provider", fxQuote.provider)
            .put("updatedAt", nowMillis),
    )
    root.put("usdFxRates", rates)
    return root.toString()
}

private fun String.cachedUsdFxRate(localCurrencyCode: String): FxRateQuote? {
    val normalizedCurrency = localCurrencyCode.uppercase()
    if (normalizedCurrency == DEFAULT_LOCAL_CURRENCY_CODE) {
        return FxRateQuote(
            baseCurrency = DEFAULT_LOCAL_CURRENCY_CODE,
            quoteCurrency = DEFAULT_LOCAL_CURRENCY_CODE,
            rate = BigDecimal.ONE,
            provider = "local-fx-cache",
        )
    }
    val entry = runCatching { JSONObject(this) }
        .getOrNull()
        ?.optJSONObject("usdFxRates")
        ?.optJSONObject(normalizedCurrency)
        ?: return null
    val rate = entry.optString("rate").toPositiveBigDecimalOrNull() ?: return null
    return FxRateQuote(
        baseCurrency = entry.optString("baseCurrency", DEFAULT_LOCAL_CURRENCY_CODE).uppercase(),
        quoteCurrency = normalizedCurrency,
        rate = rate,
        provider = entry.optString("provider", "local-fx-cache"),
    )
}

private fun String.usdPriceFromPriceSyncMetadata(): BigDecimal? =
    runCatching { JSONObject(this) }
        .getOrNull()
        ?.optJSONObject("priceSync")
        ?.optString("usdPrice")
        ?.toPositiveBigDecimalOrNull()

private fun String.withLocalCurrencyReprice(
    fxQuote: FxRateQuote,
    nowMillis: Long,
): String {
    val root = runCatching { JSONObject(this) }.getOrElse { JSONObject() }
    root.put(
        "localCurrencyReprice",
        JSONObject()
            .put("baseCurrency", fxQuote.baseCurrency.uppercase())
            .put("quoteCurrency", fxQuote.quoteCurrency.uppercase())
            .put("fxProvider", fxQuote.provider)
            .put("updatedAt", nowMillis),
    )
    return root.toString()
}

private fun String.toBigDecimalOrZero(): BigDecimal =
    runCatching { BigDecimal(this) }.getOrDefault(BigDecimal.ZERO)

private fun String?.toPositiveBigDecimalOrNull(): BigDecimal? =
    this
        ?.takeIf(String::isNotBlank)
        ?.let { value -> runCatching { BigDecimal(value) }.getOrNull() }
        ?.takeIf { value -> value > BigDecimal.ZERO }

private fun String?.localFromUsd(fxRate: BigDecimal): String? =
    toPositiveBigDecimalOrNull()
        ?.multiply(fxRate)
        ?.toPlainString()

private fun WalletAssetRecord.cachedUsdPrice(
    priceSymbol: String,
    marketDataBySymbol: Map<String, AssetMarketDataRecord>,
    settingsMetadataJson: String,
): BigDecimal? {
    val metadataUsdPrice = metadataJson.usdPriceFromPriceSyncMetadata()
    val marketUsdPrice = marketDataBySymbol[priceSymbol.uppercase()]
        ?.priceUsd
        .toPositiveBigDecimalOrNull()
    val usdLocalPrice = priceFiatValue
        .toPositiveBigDecimalOrNull()
        ?.takeIf { localCurrencyCode.equals(DEFAULT_LOCAL_CURRENCY_CODE, ignoreCase = true) }
    val localFxRate = settingsMetadataJson.cachedUsdFxRate(localCurrencyCode)?.rate
    val derivedUsdPrice = priceFiatValue
        .toPositiveBigDecimalOrNull()
        ?.takeIf { !localCurrencyCode.equals(DEFAULT_LOCAL_CURRENCY_CODE, ignoreCase = true) }
        ?.let { localPrice ->
            localFxRate
                ?.takeIf { rate -> rate > BigDecimal.ZERO }
                ?.let { rate -> localPrice.divide(rate, 18, RoundingMode.HALF_UP) }
        }

    return metadataUsdPrice ?: marketUsdPrice ?: usdLocalPrice ?: derivedUsdPrice
}

private fun WalletAssetRecord?.cachedBalanceFiatValue(
    balanceDecimal: String,
    localCurrencyCode: String,
): String {
    if (this == null || this.localCurrencyCode != localCurrencyCode) return "0"
    val price = priceFiatValue.toBigDecimalOrZero()
    if (price <= BigDecimal.ZERO) return "0"
    return balanceDecimal
        .toBigDecimalOrZero()
        .multiply(price)
        .stripTrailingZeros()
        .toPlainString()
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
