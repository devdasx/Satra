package dev.satra.wallet.data.demo

import android.content.Context
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.db.AppSettingsUpdate
import dev.satra.wallet.data.db.DEFAULT_LOCAL_CURRENCY_CODE
import dev.satra.wallet.data.db.NewWalletTransactionRecord
import dev.satra.wallet.data.db.SatraDatabaseOpenHelper
import dev.satra.wallet.data.db.SatraWalletDao
import dev.satra.wallet.data.db.SatraWalletRepository
import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionStatus
import dev.satra.wallet.settings.SatraThemePreference
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

object SatraPlayStoreDemoSeeder {
    fun seed(context: Context) {
        val dao = SatraWalletDao(SatraDatabaseOpenHelper(context.applicationContext))
        val repository = SatraWalletRepository(dao)

        dao.resetUserData()
        val walletId = repository.importMnemonicWallet(
            walletName = "Imported Wallet",
            mnemonic = DEMO_MNEMONIC,
            metadataJson = demoWalletMetadata(),
        )
        repository.saveSetupSecurity(
            passcode = "246810",
            biometricsEnabled = true,
        )
        dao.updateAppSettings(
            AppSettingsUpdate(
                themePreference = SatraThemePreference.System.name,
                hapticsEnabled = true,
                notificationsNewsEnabled = true,
                notificationsPricesEnabled = true,
                notificationsTransactionsEnabled = true,
            ),
        )

        seedDemoBalances(dao, walletId)
        seedDemoTransactions(dao, walletId)
        dao.updateWalletFiatBalance(
            walletId = walletId,
            balanceFiatValue = "1032.67",
            localCurrencyCode = DEFAULT_LOCAL_CURRENCY_CODE,
        )
    }

    private fun demoWalletMetadata(): String =
        JSONObject()
            .put("playStoreDemo", true)
            .put(
                "evmSync",
                JSONObject()
                    .put("syncedNetworkCount", 12),
            )
            .put(
                "utxoSync",
                JSONObject()
                    .put("syncedNetworkCount", 4),
            )
            .toString()

    private fun seedDemoBalances(
        dao: SatraWalletDao,
        walletId: String,
    ) {
        dao.setAssetBalance(walletId, "ethereum:usdt", "175.013331", "1.00", "175.01")
        dao.setAssetBalance(walletId, "base:usdt", "105.000000", "1.00", "105.00")
        dao.setAssetBalance(walletId, "polygon:usdt", "82.286669", "1.00", "82.29")
        dao.setAssetBalance(walletId, "arbitrum:usdt", "50.000000", "1.00", "50.00")
        dao.setAssetBalance(walletId, "ethereum:eth", "0.072425274768640944", "1760.11", "127.49")
        dao.setAssetBalance(walletId, "bitcoin:btc", "0.00291042", "104530.20", "304.18")
        dao.setAssetBalance(walletId, "polygon:pol", "213.161300", "0.31", "66.08")
        dao.setAssetBalance(walletId, "litecoin:ltc", "0.35640000", "118.27", "42.15")
        dao.setAssetBalance(walletId, "base:usdc", "80.470000", "1.00", "80.47")
        dao.setAssetBalance(walletId, "solana:sol", "0", "151.42", "0")
        dao.setAssetBalance(walletId, "bnbChain:bnb", "0", "604.13", "0")
        dao.setAssetBalance(walletId, "avalanche:avax", "0", "18.26", "0")
    }

    private fun SatraWalletDao.setAssetBalance(
        walletId: String,
        assetId: String,
        amountDecimal: String,
        price: String,
        fiatValue: String,
    ) {
        val asset = SupportedAssetCatalog.assets.first { candidate -> candidate.assetId == assetId }
        val raw = BigDecimal(amountDecimal)
            .movePointRight(asset.decimals)
            .setScale(0, RoundingMode.DOWN)
            .toPlainString()
        val metadataJson = JSONObject()
            .put(
                "priceSync",
                JSONObject()
                    .put("status", "cached")
                    .put("provider", "play-store-demo")
                    .put("localCurrencyCode", DEFAULT_LOCAL_CURRENCY_CODE),
            )
            .toString()
        updateWalletAssetBalance(
            walletId = walletId,
            assetId = assetId,
            balanceRaw = raw,
            balanceDecimal = amountDecimal,
            balanceFiatValue = fiatValue,
            localCurrencyCode = DEFAULT_LOCAL_CURRENCY_CODE,
            metadataJson = metadataJson,
        )
        updateWalletAssetPrice(
            walletId = walletId,
            assetId = assetId,
            priceFiatValue = price,
            balanceFiatValue = fiatValue,
            localCurrencyCode = DEFAULT_LOCAL_CURRENCY_CODE,
            metadataJson = metadataJson,
        )
    }

    private fun seedDemoTransactions(
        dao: SatraWalletDao,
        walletId: String,
    ) {
        val now = System.currentTimeMillis()
        dao.insertDemoTransaction(
            walletId = walletId,
            assetId = "ethereum:usdt",
            direction = WalletTransactionDirection.Incoming.value,
            status = WalletTransactionStatus.Success.value,
            amountDecimal = "175.013331",
            fiatValue = "175.01",
            timestamp = now - 26.days,
            hashSuffix = "1001",
            feeAssetId = "ethereum:eth",
            feeDecimal = "0.0012",
        )
        dao.insertDemoTransaction(
            walletId = walletId,
            assetId = "ethereum:eth",
            direction = WalletTransactionDirection.Outgoing.value,
            status = WalletTransactionStatus.Success.value,
            amountDecimal = "0.014",
            fiatValue = "24.64",
            timestamp = now - 19.days,
            hashSuffix = "1002",
            feeAssetId = "ethereum:eth",
            feeDecimal = "0.0018",
        )
        dao.insertDemoTransaction(
            walletId = walletId,
            assetId = "bitcoin:btc",
            direction = WalletTransactionDirection.Incoming.value,
            status = WalletTransactionStatus.Success.value,
            amountDecimal = "0.00124000",
            fiatValue = "129.62",
            timestamp = now - 11.days,
            hashSuffix = "1003",
            feeAssetId = "bitcoin:btc",
            feeDecimal = "0.00001200",
        )
        dao.insertDemoTransaction(
            walletId = walletId,
            assetId = "base:usdc",
            direction = WalletTransactionDirection.Incoming.value,
            status = WalletTransactionStatus.Success.value,
            amountDecimal = "80.470000",
            fiatValue = "80.47",
            timestamp = now - 6.days,
            hashSuffix = "1004",
            feeAssetId = "base:eth",
            feeDecimal = "0.00004",
        )
        dao.insertDemoTransaction(
            walletId = walletId,
            assetId = "polygon:usdt",
            direction = WalletTransactionDirection.Incoming.value,
            status = WalletTransactionStatus.Success.value,
            amountDecimal = "82.286669",
            fiatValue = "82.29",
            timestamp = now - 4.days,
            hashSuffix = "1005",
            feeAssetId = "polygon:pol",
            feeDecimal = "0.031",
        )
        dao.insertDemoTransaction(
            walletId = walletId,
            assetId = "litecoin:ltc",
            direction = WalletTransactionDirection.Incoming.value,
            status = WalletTransactionStatus.Success.value,
            amountDecimal = "0.35640000",
            fiatValue = "42.15",
            timestamp = now - 3.days,
            hashSuffix = "1006",
            feeAssetId = "litecoin:ltc",
            feeDecimal = "0.00010000",
        )
        dao.insertDemoTransaction(
            walletId = walletId,
            assetId = "polygon:pol",
            direction = WalletTransactionDirection.Outgoing.value,
            status = WalletTransactionStatus.Pending.value,
            amountDecimal = "12.5000",
            fiatValue = "3.88",
            timestamp = now - 2.days,
            hashSuffix = "1007",
            feeAssetId = "polygon:pol",
            feeDecimal = "0.0042",
        )
        dao.insertDemoTransaction(
            walletId = walletId,
            assetId = "base:usdc",
            direction = WalletTransactionDirection.Outgoing.value,
            status = WalletTransactionStatus.Failed.value,
            amountDecimal = "25.000000",
            fiatValue = "25.00",
            timestamp = now - 1.days,
            hashSuffix = "1008",
            feeAssetId = "base:eth",
            feeDecimal = "0",
        )
        dao.insertDemoTransaction(
            walletId = walletId,
            assetId = "base:usdt",
            direction = WalletTransactionDirection.Incoming.value,
            status = WalletTransactionStatus.Success.value,
            amountDecimal = "105.000000",
            fiatValue = "105.00",
            timestamp = now - 16.hours,
            hashSuffix = "1009",
            feeAssetId = "base:eth",
            feeDecimal = "0.00003",
        )
    }

    private fun SatraWalletDao.insertDemoTransaction(
        walletId: String,
        assetId: String,
        direction: String,
        status: String,
        amountDecimal: String,
        fiatValue: String,
        timestamp: Long,
        hashSuffix: String,
        feeAssetId: String,
        feeDecimal: String,
    ) {
        val asset = SupportedAssetCatalog.assets.first { candidate -> candidate.assetId == assetId }
        val feeAsset = SupportedAssetCatalog.assets.first { candidate -> candidate.assetId == feeAssetId }
        insertWalletTransaction(
            NewWalletTransactionRecord(
                walletId = walletId,
                assetId = assetId,
                networkId = asset.networkId,
                transactionHash = "0x${hashSuffix.padStart(64, 'a')}",
                direction = direction,
                status = status,
                amountRaw = BigDecimal(amountDecimal)
                    .movePointRight(asset.decimals)
                    .setScale(0, RoundingMode.DOWN)
                    .toPlainString(),
                amountDecimal = amountDecimal,
                feeRaw = BigDecimal(feeDecimal)
                    .movePointRight(feeAsset.decimals)
                    .setScale(0, RoundingMode.DOWN)
                    .toPlainString(),
                feeDecimal = feeDecimal,
                feeAssetId = feeAssetId,
                fiatValue = fiatValue,
                localCurrencyCode = DEFAULT_LOCAL_CURRENCY_CODE,
                fromAddress = if (direction == WalletTransactionDirection.Incoming.value) {
                    COUNTERPARTY_EVM_ADDRESS
                } else {
                    DEMO_EVM_ADDRESS
                },
                toAddress = if (direction == WalletTransactionDirection.Incoming.value) {
                    DEMO_EVM_ADDRESS
                } else {
                    COUNTERPARTY_EVM_ADDRESS
                },
                confirmations = if (status == WalletTransactionStatus.Success.value) 128 else 0,
                timestamp = timestamp,
                metadataJson = JSONObject()
                    .put("playStoreDemo", true)
                    .toString(),
            ),
            nowMillis = timestamp,
        )
    }

    private val Int.days: Long
        get() = this * 24L * 60L * 60L * 1000L

    private val Int.hours: Long
        get() = this * 60L * 60L * 1000L

    private const val DEMO_EVM_ADDRESS = "0x742d35Cc6634C0532925a3b844Bc454e4438f44e"
    private const val COUNTERPARTY_EVM_ADDRESS = "0x8f3Cf7ad23Cd3CaDbD9735AFf958023239c6A063"
    private const val DEMO_MNEMONIC =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
}
