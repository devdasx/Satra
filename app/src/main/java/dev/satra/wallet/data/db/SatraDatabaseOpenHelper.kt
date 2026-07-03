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
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        error("No migration from $oldVersion to $newVersion is defined yet.")
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
}

fun Boolean.toInt(): Int = if (this) 1 else 0
