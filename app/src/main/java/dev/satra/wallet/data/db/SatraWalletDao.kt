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

    fun createWallet(
        wallet: NewWalletRecord,
        includeAllSupportedAssets: Boolean = true,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val walletId = UUID.randomUUID().toString()
        val db = databaseHelper.writableDatabase

        db.beginTransaction()
        try {
            db.insertOrThrow(
                SatraDatabaseContract.TABLE_WALLETS,
                null,
                ContentValues().apply {
                    put("wallet_id", walletId)
                    put("wallet_name", wallet.walletName)
                    put("wallet_type", wallet.walletType)
                    put("wallet_key_type", wallet.walletKeyType)
                    putNullable("wallet_key_material", wallet.walletKeyMaterial)
                    putNullable("wallet_key_fingerprint", wallet.walletKeyFingerprint)
                    putNullable("wallet_key_derivation_path", wallet.walletKeyDerivationPath)
                    putNullable("passphrase", wallet.passphrase)
                    put("wallet_key_encryption_state", wallet.walletKeyEncryptionState)
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
                put("key_material", privateKey.keyMaterial)
                put("key_format", privateKey.keyFormat)
                putNullable("derivation_path", privateKey.derivationPath)
                putNullable("public_key", privateKey.publicKey)
                put("key_source", privateKey.keySource)
                put("is_imported", privateKey.isImported.toInt())
                put("is_encrypted", privateKey.isEncrypted.toInt())
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
}

private fun Cursor.toWalletRecord(): WalletRecord =
    WalletRecord(
        walletId = string("wallet_id"),
        walletName = string("wallet_name"),
        walletType = string("wallet_type"),
        walletKeyType = string("wallet_key_type"),
        walletKeyMaterial = nullableString("wallet_key_material"),
        walletKeyFingerprint = nullableString("wallet_key_fingerprint"),
        walletKeyDerivationPath = nullableString("wallet_key_derivation_path"),
        passphrase = nullableString("passphrase"),
        walletKeyEncryptionState = string("wallet_key_encryption_state"),
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
        keyMaterial = string("key_material"),
        keyFormat = string("key_format"),
        derivationPath = nullableString("derivation_path"),
        publicKey = nullableString("public_key"),
        keySource = string("key_source"),
        isImported = boolean("is_imported"),
        isEncrypted = boolean("is_encrypted"),
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
