package dev.satra.wallet.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.satra.wallet.data.assets.SupportedAssetCatalog

class SatraDatabaseOpenHelper(
    context: Context,
    databaseName: String = SatraDatabaseContract.DATABASE_NAME,
) : SQLiteOpenHelper(
    context,
    databaseName,
    null,
    SatraDatabaseContract.DATABASE_VERSION,
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        SatraDatabaseContract.createStatements.forEach(db::execSQL)
        SatraDatabaseContract.indexStatements.forEach(db::execSQL)
        seedSupportedNetworks(db)
        seedSupportedAssets(db)
        seedDefaultAppSettings(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        var migratedVersion = oldVersion

        if (migratedVersion < 2) {
            db.execSQL(
                "ALTER TABLE ${SatraDatabaseContract.TABLE_WALLETS} ADD COLUMN passphrase TEXT",
            )
            migratedVersion = 2
        }

        if (migratedVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE ${SatraDatabaseContract.TABLE_APP_SETTINGS} (
                    settings_id TEXT NOT NULL PRIMARY KEY,
                    local_currency_code TEXT NOT NULL DEFAULT '$DEFAULT_LOCAL_CURRENCY_CODE',
                    language_tag TEXT NOT NULL DEFAULT 'en',
                    theme_preference TEXT NOT NULL DEFAULT 'System',
                    haptics_enabled INTEGER NOT NULL DEFAULT 1,
                    passcode_enabled INTEGER NOT NULL DEFAULT 0,
                    passcode_hash TEXT,
                    passcode_salt TEXT,
                    passcode_length INTEGER,
                    biometrics_enabled INTEGER NOT NULL DEFAULT 0,
                    auto_lock_timeout_millis INTEGER NOT NULL DEFAULT 0,
                    erase_wallet_enabled INTEGER NOT NULL DEFAULT 1,
                    erase_wallet_attempt_limit INTEGER NOT NULL DEFAULT 10,
                    failed_passcode_attempts INTEGER NOT NULL DEFAULT 0,
                    notifications_news_enabled INTEGER NOT NULL DEFAULT 1,
                    notifications_prices_enabled INTEGER NOT NULL DEFAULT 1,
                    notifications_transactions_enabled INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    metadata_json TEXT NOT NULL DEFAULT '$EMPTY_JSON'
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE ${SatraDatabaseContract.TABLE_ADDRESS_BOOK} (
                    entry_id TEXT NOT NULL PRIMARY KEY,
                    label TEXT NOT NULL,
                    network_id TEXT NOT NULL,
                    address TEXT NOT NULL,
                    notes TEXT,
                    is_favorite INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    metadata_json TEXT NOT NULL DEFAULT '$EMPTY_JSON',
                    UNIQUE(network_id, address),
                    FOREIGN KEY(network_id) REFERENCES ${SatraDatabaseContract.TABLE_SUPPORTED_NETWORKS}(network_id) ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX index_address_book_network ON ${SatraDatabaseContract.TABLE_ADDRESS_BOOK}(network_id)")
            db.execSQL("CREATE INDEX index_address_book_label ON ${SatraDatabaseContract.TABLE_ADDRESS_BOOK}(label)")
            seedDefaultAppSettings(db)
            migratedVersion = 3
        }

        if (migratedVersion != newVersion) {
            error("No migration from $migratedVersion to $newVersion is defined yet.")
        }
    }

    private fun seedSupportedNetworks(db: SQLiteDatabase) {
        SupportedAssetCatalog.networks.forEach { network ->
            db.insertWithOnConflict(
                SatraDatabaseContract.TABLE_SUPPORTED_NETWORKS,
                null,
                ContentValues().apply {
                    put("network_id", network.networkId)
                    put("display_name", network.displayName)
                    put("family", network.family)
                    put("native_symbol", network.nativeSymbol)
                    put("native_decimals", network.nativeDecimals)
                    put("chain_id", network.chainId)
                    put("token_standard", network.tokenStandard)
                    put("is_layer2", network.isLayer2.toInt())
                    put("is_enabled", 1)
                    put("source", SupportedAssetCatalog.SOURCE)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    private fun seedSupportedAssets(db: SQLiteDatabase) {
        SupportedAssetCatalog.assets.forEachIndexed { index, asset ->
            db.insertWithOnConflict(
                SatraDatabaseContract.TABLE_SUPPORTED_ASSETS,
                null,
                ContentValues().apply {
                    put("asset_id", asset.assetId)
                    put("symbol", asset.symbol)
                    put("name", asset.name)
                    put("network_id", asset.networkId)
                    put("asset_type", asset.assetType)
                    put("decimals", asset.decimals)
                    put("token_standard", asset.tokenStandard)
                    put("contract_address", asset.contractAddress)
                    put("is_stablecoin", asset.isStablecoin.toInt())
                    put("is_enabled", 1)
                    put("sort_order", index)
                    put("source", SupportedAssetCatalog.SOURCE)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    private fun seedDefaultAppSettings(db: SQLiteDatabase) {
        val nowMillis = System.currentTimeMillis()
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
}

fun Boolean.toInt(): Int = if (this) 1 else 0

const val DEFAULT_APP_SETTINGS_ID = "default"
