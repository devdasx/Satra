package dev.satra.wallet.data.db

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import java.util.UUID

class SatraWalletDao(
    private val databaseHelper: SatraDatabaseOpenHelper,
) {
    fun supportedNetworkCount(): Long =
        DatabaseUtils.queryNumEntries(
            databaseHelper.readableDatabase,
            SatraDatabaseContract.TABLE_SUPPORTED_NETWORKS,
        )

    fun supportedAssetCount(): Long =
        DatabaseUtils.queryNumEntries(
            databaseHelper.readableDatabase,
            SatraDatabaseContract.TABLE_SUPPORTED_ASSETS,
        )

    fun getAppSettings(): AppSettingsRecord =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_APP_SETTINGS,
            null,
            "settings_id = ?",
            arrayOf(DEFAULT_APP_SETTINGS_ID),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.toAppSettingsRecord()
            } else {
                ensureDefaultAppSettings()
            }
        }

    fun updateAppSettings(
        update: AppSettingsUpdate,
        nowMillis: Long = System.currentTimeMillis(),
    ): AppSettingsRecord {
        ensureDefaultAppSettings()
        databaseHelper.writableDatabase.update(
            SatraDatabaseContract.TABLE_APP_SETTINGS,
            ContentValues().apply {
                update.localCurrencyCode?.let { put("local_currency_code", it) }
                update.languageTag?.let { put("language_tag", it) }
                update.themePreference?.let { put("theme_preference", it) }
                update.hapticsEnabled?.let { put("haptics_enabled", it.toInt()) }
                update.passcodeEnabled?.let { put("passcode_enabled", it.toInt()) }
                update.biometricsEnabled?.let { put("biometrics_enabled", it.toInt()) }
                update.autoLockTimeoutMillis?.let { put("auto_lock_timeout_millis", it) }
                update.eraseWalletEnabled?.let { put("erase_wallet_enabled", it.toInt()) }
                update.eraseWalletAttemptLimit?.let { put("erase_wallet_attempt_limit", it) }
                update.failedPasscodeAttempts?.let { put("failed_passcode_attempts", it) }
                update.notificationsNewsEnabled?.let { put("notifications_news_enabled", it.toInt()) }
                update.notificationsPricesEnabled?.let { put("notifications_prices_enabled", it.toInt()) }
                update.notificationsTransactionsEnabled?.let { put("notifications_transactions_enabled", it.toInt()) }
                if (update.clearPasscode) {
                    put("passcode_enabled", 0)
                    putNull("passcode_hash")
                    putNull("passcode_salt")
                    putNull("passcode_length")
                    put("biometrics_enabled", 0)
                    put("failed_passcode_attempts", 0)
                } else {
                    update.passcodeHash?.let { put("passcode_hash", it) }
                    update.passcodeSalt?.let { put("passcode_salt", it) }
                    update.passcodeLength?.let { put("passcode_length", it) }
                }
                update.metadataJson?.let { put("metadata_json", it) }
                put("updated_at", nowMillis)
            },
            "settings_id = ?",
            arrayOf(DEFAULT_APP_SETTINGS_ID),
        )
        return getAppSettings()
    }

    fun createWallet(
        wallet: NewWalletRecord,
        includeAllSupportedAssets: Boolean = true,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val walletId = UUID.randomUUID().toString()
        val db = databaseHelper.writableDatabase

        db.beginTransaction()
        try {
            db.update(
                SatraDatabaseContract.TABLE_WALLETS,
                ContentValues().apply {
                    put("is_active", 0)
                    put("updated_at", nowMillis)
                },
                null,
                null,
            )
            db.insertOrThrow(
                SatraDatabaseContract.TABLE_WALLETS,
                null,
                ContentValues().apply {
                    put("wallet_id", walletId)
                    put("wallet_name", wallet.walletName)
                    put("wallet_type", wallet.walletType)
                    put("wallet_key_type", wallet.walletKeyType)
                    putNullable("primary_secret_id", wallet.primarySecretId)
                    putNullable("wallet_key_fingerprint", wallet.walletKeyFingerprint)
                    putNullable("wallet_key_derivation_path", wallet.walletKeyDerivationPath)
                    putNullable("passphrase_secret_id", wallet.passphraseSecretId)
                    put("secret_storage_state", wallet.secretStorageState)
                    put("local_currency_code", wallet.localCurrencyCode)
                    put("balance_fiat_value", "0")
                    putNull("balance_fiat_updated_at")
                    put("is_backed_up", wallet.isBackedUp.toInt())
                    putNullable("backup_verified_at", wallet.backupVerifiedAt)
                    put("is_imported", wallet.isImported.toInt())
                    put("is_watch_only", wallet.isWatchOnly.toInt())
                    put("is_active", 1)
                    put("created_at", nowMillis)
                    put("updated_at", nowMillis)
                    putNull("last_synced_at")
                    put("metadata_json", wallet.metadataJson)
                },
            )

            if (includeAllSupportedAssets) {
                insertDefaultWalletAssets(
                    db = db,
                    walletId = walletId,
                    localCurrencyCode = wallet.localCurrencyCode,
                    nowMillis = nowMillis,
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return walletId
    }

    fun updateWalletSecretReferences(
        walletId: String,
        primarySecretId: String?,
        passphraseSecretId: String?,
        nowMillis: Long = System.currentTimeMillis(),
    ): WalletRecord? {
        databaseHelper.writableDatabase.update(
            SatraDatabaseContract.TABLE_WALLETS,
            ContentValues().apply {
                putNullable("primary_secret_id", primarySecretId)
                putNullable("passphrase_secret_id", passphraseSecretId)
                put("secret_storage_state", SecretStorageState.KeystoreAesGcmV1.value)
                put("updated_at", nowMillis)
            },
            "wallet_id = ?",
            arrayOf(walletId),
        )
        return getWallet(walletId)
    }

    fun insertWalletSecret(
        secret: NewWalletSecretRecord,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val secretId = UUID.randomUUID().toString()
        databaseHelper.writableDatabase.insertOrThrow(
            SatraDatabaseContract.TABLE_WALLET_SECRETS,
            null,
            ContentValues().apply {
                put("secret_id", secretId)
                put("wallet_id", secret.walletId)
                put("secret_type", secret.secretType)
                putNullable("network_id", secret.networkId)
                putNullable("derivation_path", secret.derivationPath)
                put("encryption_version", secret.encryptionVersion)
                put("encryption_algorithm", secret.encryptionAlgorithm)
                put("keystore_alias", secret.keystoreAlias)
                put("iv_base64", secret.ivBase64)
                put("ciphertext_base64", secret.ciphertextBase64)
                put("created_at", nowMillis)
                put("updated_at", nowMillis)
                put("metadata_json", secret.metadataJson)
            },
        )
        return secretId
    }

    fun getWalletSecret(secretId: String): WalletSecretRecord? =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_WALLET_SECRETS,
            null,
            "secret_id = ?",
            arrayOf(secretId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toWalletSecretRecord() else null
        }

    fun getWalletSecrets(walletId: String): List<WalletSecretRecord> =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_WALLET_SECRETS,
            null,
            "wallet_id = ?",
            arrayOf(walletId),
            null,
            null,
            "secret_type ASC, network_id ASC, derivation_path ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toWalletSecretRecord())
                }
            }
        }

    fun getWallet(walletId: String): WalletRecord? =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_WALLETS,
            null,
            "wallet_id = ?",
            arrayOf(walletId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toWalletRecord() else null
        }

    fun getWallets(): List<WalletRecord> =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_WALLETS,
            null,
            null,
            null,
            null,
            null,
            "created_at DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toWalletRecord())
                }
            }
        }

    fun setActiveWallet(
        walletId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): WalletRecord? {
        if (getWallet(walletId) == null) return null

        val db = databaseHelper.writableDatabase
        db.beginTransaction()
        try {
            db.update(
                SatraDatabaseContract.TABLE_WALLETS,
                ContentValues().apply {
                    put("is_active", 0)
                    put("updated_at", nowMillis)
                },
                null,
                null,
            )
            db.update(
                SatraDatabaseContract.TABLE_WALLETS,
                ContentValues().apply {
                    put("is_active", 1)
                    put("updated_at", nowMillis)
                },
                "wallet_id = ?",
                arrayOf(walletId),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return getWallet(walletId)
    }

    fun deleteWallet(
        walletId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<WalletRecord> {
        if (getWallet(walletId) == null) return getWallets()

        val db = databaseHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                SatraDatabaseContract.TABLE_WALLETS,
                "wallet_id = ?",
                arrayOf(walletId),
            )
            val activeWalletId = db.query(
                SatraDatabaseContract.TABLE_WALLETS,
                arrayOf("wallet_id"),
                "is_active = 1",
                null,
                null,
                null,
                "created_at DESC",
                "1",
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.string("wallet_id") else null
            }
            if (activeWalletId == null) {
                val nextWalletId = db.query(
                    SatraDatabaseContract.TABLE_WALLETS,
                    arrayOf("wallet_id"),
                    null,
                    null,
                    null,
                    null,
                    "created_at DESC",
                    "1",
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.string("wallet_id") else null
                }
                if (nextWalletId != null) {
                    db.update(
                        SatraDatabaseContract.TABLE_WALLETS,
                        ContentValues().apply {
                            put("is_active", 1)
                            put("updated_at", nowMillis)
                        },
                        "wallet_id = ?",
                        arrayOf(nextWalletId),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return getWallets()
    }

    fun getWalletAssets(walletId: String): List<WalletAssetRecord> =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_WALLET_ASSETS,
            null,
            "wallet_id = ?",
            arrayOf(walletId),
            null,
            null,
            "created_at ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toWalletAssetRecord())
                }
            }
        }

    fun insertWalletAddress(
        address: NewWalletAddressRecord,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val addressId = UUID.randomUUID().toString()
        databaseHelper.writableDatabase.insertOrThrow(
            SatraDatabaseContract.TABLE_WALLET_ADDRESSES,
            null,
            ContentValues().apply {
                put("address_id", addressId)
                put("wallet_id", address.walletId)
                put("network_id", address.networkId)
                put("address", address.address)
                put("address_type", address.addressType)
                putNullable("derivation_path", address.derivationPath)
                putNullable("public_key", address.publicKey)
                putNullable("private_key_id", address.privateKeyId)
                put("is_primary", address.isPrimary.toInt())
                put("is_change", address.isChange.toInt())
                putNullable("address_index", address.addressIndex)
                putNullable("label", address.label)
                put("created_at", nowMillis)
                put("updated_at", nowMillis)
                putNull("last_used_at")
                put("metadata_json", address.metadataJson)
            },
        )
        return addressId
    }

    fun getWalletAddresses(walletId: String): List<WalletAddressRecord> =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_WALLET_ADDRESSES,
            null,
            "wallet_id = ?",
            arrayOf(walletId),
            null,
            null,
            "network_id ASC, address_index ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toWalletAddressRecord())
                }
            }
        }

    fun insertWalletPrivateKey(
        privateKey: NewWalletPrivateKeyRecord,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val privateKeyId = UUID.randomUUID().toString()
        databaseHelper.writableDatabase.insertOrThrow(
            SatraDatabaseContract.TABLE_WALLET_PRIVATE_KEYS,
            null,
            ContentValues().apply {
                put("private_key_id", privateKeyId)
                put("wallet_id", privateKey.walletId)
                put("network_id", privateKey.networkId)
                putNullable("address_id", privateKey.addressId)
                put("secret_id", privateKey.secretId)
                put("key_format", privateKey.keyFormat)
                putNullable("derivation_path", privateKey.derivationPath)
                putNullable("public_key", privateKey.publicKey)
                put("key_source", privateKey.keySource)
                put("is_imported", privateKey.isImported.toInt())
                putNullable("key_fingerprint", privateKey.keyFingerprint)
                put("created_at", nowMillis)
                put("updated_at", nowMillis)
                put("metadata_json", privateKey.metadataJson)
            },
        )
        return privateKeyId
    }

    fun getWalletPrivateKeys(walletId: String): List<WalletPrivateKeyRecord> =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_WALLET_PRIVATE_KEYS,
            null,
            "wallet_id = ?",
            arrayOf(walletId),
            null,
            null,
            "network_id ASC, derivation_path ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toWalletPrivateKeyRecord())
                }
            }
        }

    fun insertWalletTransaction(
        transaction: NewWalletTransactionRecord,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val transactionId = UUID.randomUUID().toString()
        databaseHelper.writableDatabase.insertOrThrow(
            SatraDatabaseContract.TABLE_WALLET_TRANSACTIONS,
            null,
            ContentValues().apply {
                put("transaction_id", transactionId)
                put("wallet_id", transaction.walletId)
                put("asset_id", transaction.assetId)
                put("network_id", transaction.networkId)
                putNullable("transaction_hash", transaction.transactionHash)
                put("direction", transaction.direction)
                put("status", transaction.status)
                put("amount_raw", transaction.amountRaw)
                put("amount_decimal", transaction.amountDecimal)
                putNullable("fee_raw", transaction.feeRaw)
                putNullable("fee_decimal", transaction.feeDecimal)
                putNullable("fee_asset_id", transaction.feeAssetId)
                putNullable("fiat_value", transaction.fiatValue)
                put("local_currency_code", transaction.localCurrencyCode)
                putNullable("from_address", transaction.fromAddress)
                putNullable("to_address", transaction.toAddress)
                putNullable("block_height", transaction.blockHeight)
                putNullable("block_hash", transaction.blockHash)
                put("confirmations", transaction.confirmations)
                putNullable("nonce", transaction.nonce)
                putNullable("memo", transaction.memo)
                put("timestamp", transaction.timestamp)
                put("first_seen_at", nowMillis)
                put("updated_at", nowMillis)
                put("metadata_json", transaction.metadataJson)
            },
        )
        return transactionId
    }

    fun upsertWalletTransaction(
        transaction: NewWalletTransactionRecord,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val existingTransactionId = transaction.transactionHash?.let { transactionHash ->
            findWalletTransactionId(
                walletId = transaction.walletId,
                networkId = transaction.networkId,
                transactionHash = transactionHash,
                assetId = transaction.assetId,
            )
        }
        val transactionId = existingTransactionId ?: UUID.randomUUID().toString()
        val values = transaction.toContentValues(
            transactionId = transactionId,
            firstSeenAt = nowMillis,
            updatedAt = nowMillis,
        )

        if (existingTransactionId == null) {
            databaseHelper.writableDatabase.insertOrThrow(
                SatraDatabaseContract.TABLE_WALLET_TRANSACTIONS,
                null,
                values,
            )
        } else {
            values.remove("first_seen_at")
            databaseHelper.writableDatabase.update(
                SatraDatabaseContract.TABLE_WALLET_TRANSACTIONS,
                values,
                "transaction_id = ?",
                arrayOf(transactionId),
            )
        }
        return transactionId
    }

    fun getWalletTransactions(walletId: String): List<WalletTransactionRecord> =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_WALLET_TRANSACTIONS,
            null,
            "wallet_id = ?",
            arrayOf(walletId),
            null,
            null,
            "timestamp DESC, first_seen_at DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toWalletTransactionRecord())
                }
            }
        }

    fun deleteUnbroadcastSendDraftTransactions(walletId: String): Int =
        databaseHelper.writableDatabase.delete(
            SatraDatabaseContract.TABLE_WALLET_TRANSACTIONS,
            "wallet_id = ? AND transaction_hash IS NULL AND status = ? AND metadata_json LIKE ?",
            arrayOf(
                walletId,
                WalletTransactionStatus.Pending.value,
                "%\"flow\":\"send\"%",
            ),
        )

    fun upsertAssetMarketData(
        marketData: NewAssetMarketDataRecord,
    ) {
        databaseHelper.writableDatabase.insertWithOnConflict(
            SatraDatabaseContract.TABLE_ASSET_MARKET_DATA,
            null,
            marketData.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun getAssetMarketData(symbol: String): AssetMarketDataRecord? =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_ASSET_MARKET_DATA,
            null,
            "symbol = ?",
            arrayOf(symbol.uppercase()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toAssetMarketDataRecord() else null
        }

    fun getAllAssetMarketData(): List<AssetMarketDataRecord> =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_ASSET_MARKET_DATA,
            null,
            null,
            null,
            null,
            null,
            "symbol COLLATE NOCASE ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toAssetMarketDataRecord())
                }
            }
        }

    fun getAddressBookEntries(): List<AddressBookEntryRecord> =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_ADDRESS_BOOK,
            null,
            null,
            null,
            null,
            null,
            "is_favorite DESC, label COLLATE NOCASE ASC, created_at DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toAddressBookEntryRecord())
                }
            }
        }

    fun upsertAddressBookEntry(
        entry: NewAddressBookEntryRecord,
        existingEntryId: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val entryId = existingEntryId ?: findAddressBookEntryId(
            networkId = entry.networkId,
            address = entry.address,
        ) ?: UUID.randomUUID().toString()
        val existing = getAddressBookEntry(entryId)
        val values = ContentValues().apply {
            put("entry_id", entryId)
            put("label", entry.label)
            put("network_id", entry.networkId)
            put("address", entry.address)
            putNullable("notes", entry.notes)
            put("is_favorite", entry.isFavorite.toInt())
            put("created_at", existing?.createdAt ?: nowMillis)
            put("updated_at", nowMillis)
            put("metadata_json", entry.metadataJson)
        }
        databaseHelper.writableDatabase.insertWithOnConflict(
            SatraDatabaseContract.TABLE_ADDRESS_BOOK,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        return entryId
    }

    fun deleteAddressBookEntry(entryId: String) {
        databaseHelper.writableDatabase.delete(
            SatraDatabaseContract.TABLE_ADDRESS_BOOK,
            "entry_id = ?",
            arrayOf(entryId),
        )
    }

    fun resetUserData(nowMillis: Long = System.currentTimeMillis()) {
        val db = databaseHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(SatraDatabaseContract.TABLE_WALLETS, null, null)
            db.delete(SatraDatabaseContract.TABLE_ADDRESS_BOOK, null, null)
            db.delete(SatraDatabaseContract.TABLE_ASSET_MARKET_DATA, null, null)
            db.delete(SatraDatabaseContract.TABLE_APP_SETTINGS, null, null)
            insertDefaultAppSettings(db, nowMillis)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun markWalletBackedUp(
        walletId: String,
        backedUp: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        databaseHelper.writableDatabase.update(
            SatraDatabaseContract.TABLE_WALLETS,
            ContentValues().apply {
                put("is_backed_up", backedUp.toInt())
                putNullable("backup_verified_at", if (backedUp) nowMillis else null)
                put("updated_at", nowMillis)
            },
            "wallet_id = ?",
            arrayOf(walletId),
        )
    }

    fun updateWalletAssetBalance(
        walletId: String,
        assetId: String,
        balanceRaw: String,
        balanceDecimal: String,
        balanceFiatValue: String,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
        metadataJson: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        databaseHelper.writableDatabase.update(
            SatraDatabaseContract.TABLE_WALLET_ASSETS,
            ContentValues().apply {
                put("balance_raw", balanceRaw)
                put("balance_decimal", balanceDecimal)
                put("balance_fiat_value", balanceFiatValue)
                put("local_currency_code", localCurrencyCode)
                put("balance_updated_at", nowMillis)
                put("updated_at", nowMillis)
                if (metadataJson != null) {
                    put("metadata_json", metadataJson)
                }
            },
            "wallet_id = ? AND asset_id = ?",
            arrayOf(walletId, assetId),
        )
    }

    fun updateWalletAssetPrice(
        walletId: String,
        assetId: String,
        priceFiatValue: String,
        balanceFiatValue: String,
        localCurrencyCode: String,
        metadataJson: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        databaseHelper.writableDatabase.update(
            SatraDatabaseContract.TABLE_WALLET_ASSETS,
            ContentValues().apply {
                put("price_fiat_value", priceFiatValue)
                put("price_fiat_updated_at", nowMillis)
                put("balance_fiat_value", balanceFiatValue)
                put("local_currency_code", localCurrencyCode)
                put("updated_at", nowMillis)
                put("metadata_json", metadataJson)
            },
            "wallet_id = ? AND asset_id = ?",
            arrayOf(walletId, assetId),
        )
    }

    fun updateWalletFiatBalance(
        walletId: String,
        balanceFiatValue: String,
        localCurrencyCode: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        databaseHelper.writableDatabase.update(
            SatraDatabaseContract.TABLE_WALLETS,
            ContentValues().apply {
                put("balance_fiat_value", balanceFiatValue)
                put("balance_fiat_updated_at", nowMillis)
                put("local_currency_code", localCurrencyCode)
                put("updated_at", nowMillis)
            },
            "wallet_id = ?",
            arrayOf(walletId),
        )
    }

    fun updateWalletTransactionFiatValues(
        walletId: String,
        localCurrencyCode: String,
        fiatValuesByTransactionId: Map<String, String?>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val db = databaseHelper.writableDatabase
        db.beginTransaction()
        try {
            val walletTransactionIds = if (fiatValuesByTransactionId.isEmpty()) {
                getWalletTransactions(walletId).map { it.transactionId }
            } else {
                fiatValuesByTransactionId.keys
            }
            walletTransactionIds.forEach { transactionId ->
                db.update(
                    SatraDatabaseContract.TABLE_WALLET_TRANSACTIONS,
                    ContentValues().apply {
                        putNullable("fiat_value", fiatValuesByTransactionId[transactionId])
                        put("local_currency_code", localCurrencyCode)
                        put("updated_at", nowMillis)
                    },
                    "wallet_id = ? AND transaction_id = ?",
                    arrayOf(walletId, transactionId),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateWalletSyncMetadata(
        walletId: String,
        metadataJson: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        databaseHelper.writableDatabase.update(
            SatraDatabaseContract.TABLE_WALLETS,
            ContentValues().apply {
                put("metadata_json", metadataJson)
                put("last_synced_at", nowMillis)
                put("updated_at", nowMillis)
            },
            "wallet_id = ?",
            arrayOf(walletId),
        )
    }

    fun updateLocalCurrencyForWalletData(
        localCurrencyCode: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val db = databaseHelper.writableDatabase
        db.beginTransaction()
        try {
            db.update(
                SatraDatabaseContract.TABLE_WALLETS,
                ContentValues().apply {
                    put("local_currency_code", localCurrencyCode)
                    put("updated_at", nowMillis)
                },
                null,
                null,
            )
            db.update(
                SatraDatabaseContract.TABLE_WALLET_ASSETS,
                ContentValues().apply {
                    put("local_currency_code", localCurrencyCode)
                    put("updated_at", nowMillis)
                },
                null,
                null,
            )
            db.update(
                SatraDatabaseContract.TABLE_WALLET_TRANSACTIONS,
                ContentValues().apply {
                    put("local_currency_code", localCurrencyCode)
                    put("updated_at", nowMillis)
                },
                null,
                null,
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun findWalletTransactionId(
        walletId: String,
        networkId: String,
        transactionHash: String,
        assetId: String,
    ): String? =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_WALLET_TRANSACTIONS,
            arrayOf("transaction_id"),
            "wallet_id = ? AND network_id = ? AND transaction_hash = ? AND asset_id = ?",
            arrayOf(walletId, networkId, transactionHash, assetId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.string("transaction_id") else null
        }

    private fun insertDefaultWalletAssets(
        db: SQLiteDatabase,
        walletId: String,
        localCurrencyCode: String,
        nowMillis: Long,
    ) {
        SupportedAssetCatalog.assets.forEach { asset ->
            db.insertWithOnConflict(
                SatraDatabaseContract.TABLE_WALLET_ASSETS,
                null,
                ContentValues().apply {
                    put("wallet_asset_id", UUID.randomUUID().toString())
                    put("wallet_id", walletId)
                    put("asset_id", asset.assetId)
                    put("network_id", asset.networkId)
                    put("is_enabled", 1)
                    put("is_visible", 1)
                    put("balance_raw", "0")
                    put("balance_decimal", "0")
                    put("balance_fiat_value", "0")
                    put("local_currency_code", localCurrencyCode)
                    put("price_fiat_value", "0")
                    putNull("price_fiat_updated_at")
                    putNull("balance_updated_at")
                    put("created_at", nowMillis)
                    put("updated_at", nowMillis)
                    put("metadata_json", EMPTY_JSON)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    private fun ensureDefaultAppSettings(): AppSettingsRecord {
        val nowMillis = System.currentTimeMillis()
        insertDefaultAppSettings(databaseHelper.writableDatabase, nowMillis)
        return databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_APP_SETTINGS,
            null,
            "settings_id = ?",
            arrayOf(DEFAULT_APP_SETTINGS_ID),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Default app settings could not be created." }
            cursor.toAppSettingsRecord()
        }
    }

    private fun insertDefaultAppSettings(
        db: SQLiteDatabase,
        nowMillis: Long,
    ) {
        db.insertWithOnConflict(
            SatraDatabaseContract.TABLE_APP_SETTINGS,
            null,
            ContentValues().apply {
                put("settings_id", DEFAULT_APP_SETTINGS_ID)
                put("local_currency_code", DEFAULT_LOCAL_CURRENCY_CODE)
                put("language_tag", "en")
                put("theme_preference", "System")
                put("haptics_enabled", 1)
                put("passcode_enabled", 0)
                putNull("passcode_hash")
                putNull("passcode_salt")
                putNull("passcode_length")
                put("biometrics_enabled", 0)
                put("auto_lock_timeout_millis", 0L)
                put("erase_wallet_enabled", 1)
                put("erase_wallet_attempt_limit", 10)
                put("failed_passcode_attempts", 0)
                put("notifications_news_enabled", 1)
                put("notifications_prices_enabled", 1)
                put("notifications_transactions_enabled", 1)
                put("created_at", nowMillis)
                put("updated_at", nowMillis)
                put("metadata_json", EMPTY_JSON)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    private fun getAddressBookEntry(entryId: String): AddressBookEntryRecord? =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_ADDRESS_BOOK,
            null,
            "entry_id = ?",
            arrayOf(entryId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toAddressBookEntryRecord() else null
        }

    private fun findAddressBookEntryId(
        networkId: String,
        address: String,
    ): String? =
        databaseHelper.readableDatabase.query(
            SatraDatabaseContract.TABLE_ADDRESS_BOOK,
            arrayOf("entry_id"),
            "network_id = ? AND address = ?",
            arrayOf(networkId, address),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.string("entry_id") else null
        }
}

private fun Cursor.toWalletRecord(): WalletRecord =
    WalletRecord(
        walletId = string("wallet_id"),
        walletName = string("wallet_name"),
        walletType = string("wallet_type"),
        walletKeyType = string("wallet_key_type"),
        primarySecretId = nullableString("primary_secret_id"),
        walletKeyFingerprint = nullableString("wallet_key_fingerprint"),
        walletKeyDerivationPath = nullableString("wallet_key_derivation_path"),
        passphraseSecretId = nullableString("passphrase_secret_id"),
        secretStorageState = string("secret_storage_state"),
        localCurrencyCode = string("local_currency_code"),
        balanceFiatValue = string("balance_fiat_value"),
        balanceFiatUpdatedAt = nullableLong("balance_fiat_updated_at"),
        isBackedUp = boolean("is_backed_up"),
        backupVerifiedAt = nullableLong("backup_verified_at"),
        isImported = boolean("is_imported"),
        isWatchOnly = boolean("is_watch_only"),
        isActive = boolean("is_active"),
        createdAt = long("created_at"),
        updatedAt = long("updated_at"),
        lastSyncedAt = nullableLong("last_synced_at"),
        metadataJson = string("metadata_json"),
    )

private fun Cursor.toWalletSecretRecord(): WalletSecretRecord =
    WalletSecretRecord(
        secretId = string("secret_id"),
        walletId = string("wallet_id"),
        secretType = string("secret_type"),
        networkId = nullableString("network_id"),
        derivationPath = nullableString("derivation_path"),
        encryptionVersion = int("encryption_version"),
        encryptionAlgorithm = string("encryption_algorithm"),
        keystoreAlias = string("keystore_alias"),
        ivBase64 = string("iv_base64"),
        ciphertextBase64 = string("ciphertext_base64"),
        createdAt = long("created_at"),
        updatedAt = long("updated_at"),
        metadataJson = string("metadata_json"),
    )

private fun Cursor.toAppSettingsRecord(): AppSettingsRecord =
    AppSettingsRecord(
        settingsId = string("settings_id"),
        localCurrencyCode = string("local_currency_code"),
        languageTag = string("language_tag"),
        themePreference = string("theme_preference"),
        hapticsEnabled = boolean("haptics_enabled"),
        passcodeEnabled = boolean("passcode_enabled"),
        passcodeHash = nullableString("passcode_hash"),
        passcodeSalt = nullableString("passcode_salt"),
        passcodeLength = nullableInt("passcode_length"),
        biometricsEnabled = boolean("biometrics_enabled"),
        autoLockTimeoutMillis = long("auto_lock_timeout_millis"),
        eraseWalletEnabled = boolean("erase_wallet_enabled"),
        eraseWalletAttemptLimit = int("erase_wallet_attempt_limit"),
        failedPasscodeAttempts = int("failed_passcode_attempts"),
        notificationsNewsEnabled = boolean("notifications_news_enabled"),
        notificationsPricesEnabled = boolean("notifications_prices_enabled"),
        notificationsTransactionsEnabled = boolean("notifications_transactions_enabled"),
        createdAt = long("created_at"),
        updatedAt = long("updated_at"),
        metadataJson = string("metadata_json"),
    )

private fun Cursor.toAddressBookEntryRecord(): AddressBookEntryRecord =
    AddressBookEntryRecord(
        entryId = string("entry_id"),
        label = string("label"),
        networkId = string("network_id"),
        address = string("address"),
        notes = nullableString("notes"),
        isFavorite = boolean("is_favorite"),
        createdAt = long("created_at"),
        updatedAt = long("updated_at"),
        metadataJson = string("metadata_json"),
    )

private fun Cursor.toWalletAssetRecord(): WalletAssetRecord =
    WalletAssetRecord(
        walletAssetId = string("wallet_asset_id"),
        walletId = string("wallet_id"),
        assetId = string("asset_id"),
        networkId = string("network_id"),
        isEnabled = boolean("is_enabled"),
        isVisible = boolean("is_visible"),
        balanceRaw = string("balance_raw"),
        balanceDecimal = string("balance_decimal"),
        balanceFiatValue = string("balance_fiat_value"),
        localCurrencyCode = string("local_currency_code"),
        priceFiatValue = string("price_fiat_value"),
        priceFiatUpdatedAt = nullableLong("price_fiat_updated_at"),
        balanceUpdatedAt = nullableLong("balance_updated_at"),
        createdAt = long("created_at"),
        updatedAt = long("updated_at"),
        metadataJson = string("metadata_json"),
    )

private fun Cursor.toWalletAddressRecord(): WalletAddressRecord =
    WalletAddressRecord(
        addressId = string("address_id"),
        walletId = string("wallet_id"),
        networkId = string("network_id"),
        address = string("address"),
        addressType = string("address_type"),
        derivationPath = nullableString("derivation_path"),
        publicKey = nullableString("public_key"),
        privateKeyId = nullableString("private_key_id"),
        isPrimary = boolean("is_primary"),
        isChange = boolean("is_change"),
        addressIndex = nullableInt("address_index"),
        label = nullableString("label"),
        createdAt = long("created_at"),
        updatedAt = long("updated_at"),
        lastUsedAt = nullableLong("last_used_at"),
        metadataJson = string("metadata_json"),
    )

private fun Cursor.toWalletPrivateKeyRecord(): WalletPrivateKeyRecord =
    WalletPrivateKeyRecord(
        privateKeyId = string("private_key_id"),
        walletId = string("wallet_id"),
        networkId = string("network_id"),
        addressId = nullableString("address_id"),
        secretId = string("secret_id"),
        keyFormat = string("key_format"),
        derivationPath = nullableString("derivation_path"),
        publicKey = nullableString("public_key"),
        keySource = string("key_source"),
        isImported = boolean("is_imported"),
        keyFingerprint = nullableString("key_fingerprint"),
        createdAt = long("created_at"),
        updatedAt = long("updated_at"),
        metadataJson = string("metadata_json"),
    )

private fun Cursor.toWalletTransactionRecord(): WalletTransactionRecord =
    WalletTransactionRecord(
        transactionId = string("transaction_id"),
        walletId = string("wallet_id"),
        assetId = string("asset_id"),
        networkId = string("network_id"),
        transactionHash = nullableString("transaction_hash"),
        direction = string("direction"),
        status = string("status"),
        amountRaw = string("amount_raw"),
        amountDecimal = string("amount_decimal"),
        feeRaw = nullableString("fee_raw"),
        feeDecimal = nullableString("fee_decimal"),
        feeAssetId = nullableString("fee_asset_id"),
        fiatValue = nullableString("fiat_value"),
        localCurrencyCode = string("local_currency_code"),
        fromAddress = nullableString("from_address"),
        toAddress = nullableString("to_address"),
        blockHeight = nullableLong("block_height"),
        blockHash = nullableString("block_hash"),
        confirmations = int("confirmations"),
        nonce = nullableString("nonce"),
        memo = nullableString("memo"),
        timestamp = long("timestamp"),
        firstSeenAt = long("first_seen_at"),
        updatedAt = long("updated_at"),
        metadataJson = string("metadata_json"),
    )

private fun Cursor.toAssetMarketDataRecord(): AssetMarketDataRecord =
    AssetMarketDataRecord(
        symbol = string("symbol"),
        name = string("name"),
        coinGeckoId = nullableString("coin_gecko_id"),
        localCurrencyCode = string("local_currency_code"),
        priceUsd = string("price_usd"),
        priceLocal = string("price_local"),
        marketCapUsd = nullableString("market_cap_usd"),
        marketCapLocal = nullableString("market_cap_local"),
        volume24hUsd = nullableString("volume_24h_usd"),
        volume24hLocal = nullableString("volume_24h_local"),
        high24hUsd = nullableString("high_24h_usd"),
        low24hUsd = nullableString("low_24h_usd"),
        priceChange24hPercent = nullableString("price_change_24h_percent"),
        description = nullableString("description"),
        homepageUrl = nullableString("homepage_url"),
        provider = string("provider"),
        chart7dJson = string("chart_7d_json"),
        updatedAt = long("updated_at"),
        metadataJson = string("metadata_json"),
    )

private fun NewAssetMarketDataRecord.toContentValues(): ContentValues =
    ContentValues().apply {
        put("symbol", symbol.uppercase())
        put("name", name)
        putNullable("coin_gecko_id", coinGeckoId)
        put("local_currency_code", localCurrencyCode)
        put("price_usd", priceUsd)
        put("price_local", priceLocal)
        putNullable("market_cap_usd", marketCapUsd)
        putNullable("market_cap_local", marketCapLocal)
        putNullable("volume_24h_usd", volume24hUsd)
        putNullable("volume_24h_local", volume24hLocal)
        putNullable("high_24h_usd", high24hUsd)
        putNullable("low_24h_usd", low24hUsd)
        putNullable("price_change_24h_percent", priceChange24hPercent)
        putNullable("description", description)
        putNullable("homepage_url", homepageUrl)
        put("provider", provider)
        put("chart_7d_json", chart7dJson)
        put("updated_at", updatedAt)
        put("metadata_json", metadataJson)
    }

private fun NewWalletTransactionRecord.toContentValues(
    transactionId: String,
    firstSeenAt: Long,
    updatedAt: Long,
): ContentValues =
    ContentValues().apply {
        put("transaction_id", transactionId)
        put("wallet_id", walletId)
        put("asset_id", assetId)
        put("network_id", networkId)
        putNullable("transaction_hash", transactionHash)
        put("direction", direction)
        put("status", status)
        put("amount_raw", amountRaw)
        put("amount_decimal", amountDecimal)
        putNullable("fee_raw", feeRaw)
        putNullable("fee_decimal", feeDecimal)
        putNullable("fee_asset_id", feeAssetId)
        putNullable("fiat_value", fiatValue)
        put("local_currency_code", localCurrencyCode)
        putNullable("from_address", fromAddress)
        putNullable("to_address", toAddress)
        putNullable("block_height", blockHeight)
        putNullable("block_hash", blockHash)
        put("confirmations", confirmations)
        putNullable("nonce", nonce)
        putNullable("memo", memo)
        put("timestamp", timestamp)
        put("first_seen_at", firstSeenAt)
        put("updated_at", updatedAt)
        put("metadata_json", metadataJson)
    }

private fun Cursor.string(columnName: String): String =
    getString(getColumnIndexOrThrow(columnName))

private fun Cursor.nullableString(columnName: String): String? {
    val index = getColumnIndexOrThrow(columnName)
    return if (isNull(index)) null else getString(index)
}

private fun Cursor.int(columnName: String): Int =
    getInt(getColumnIndexOrThrow(columnName))

private fun Cursor.nullableInt(columnName: String): Int? {
    val index = getColumnIndexOrThrow(columnName)
    return if (isNull(index)) null else getInt(index)
}

private fun Cursor.long(columnName: String): Long =
    getLong(getColumnIndexOrThrow(columnName))

private fun Cursor.nullableLong(columnName: String): Long? {
    val index = getColumnIndexOrThrow(columnName)
    return if (isNull(index)) null else getLong(index)
}

private fun Cursor.boolean(columnName: String): Boolean =
    int(columnName) == 1

private fun ContentValues.putNullable(
    key: String,
    value: String?,
) {
    if (value == null) putNull(key) else put(key, value)
}

private fun ContentValues.putNullable(
    key: String,
    value: Long?,
) {
    if (value == null) putNull(key) else put(key, value)
}

private fun ContentValues.putNullable(
    key: String,
    value: Int?,
) {
    if (value == null) putNull(key) else put(key, value)
}
