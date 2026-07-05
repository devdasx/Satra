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
        createSchema(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        recreateSchema(db)
    }

    override fun onDowngrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        recreateSchema(db)
    }

    private fun recreateSchema(db: SQLiteDatabase) {
        SatraDatabaseContract.dropStatements.forEach(db::execSQL)
        createSchema(db)
    }

    private fun createSchema(db: SQLiteDatabase) {
        SatraDatabaseContract.createStatements.forEach(db::execSQL)
        SatraDatabaseContract.indexStatements.forEach(db::execSQL)
        seedSupportedNetworks(db)
        seedSupportedAssets(db)
        seedDefaultAppSettings(db)
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
