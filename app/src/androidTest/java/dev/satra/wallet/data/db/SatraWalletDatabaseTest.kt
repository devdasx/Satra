package dev.satra.wallet.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.sync.evm.EvmProviderRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SatraWalletDatabaseTest {
    private lateinit var context: Context
    private lateinit var helper: SatraDatabaseOpenHelper
    private lateinit var dao: SatraWalletDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DATABASE_NAME)
        helper = SatraDatabaseOpenHelper(context, TEST_DATABASE_NAME)
        dao = SatraWalletDao(helper)
    }

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun databaseSeedsSupportedNetworksAndAssets() {
        assertEquals(SupportedAssetCatalog.networks.size.toLong(), dao.supportedNetworkCount())
        assertEquals(SupportedAssetCatalog.assets.size.toLong(), dao.supportedAssetCount())
        assertEquals(26, SupportedAssetCatalog.networks.size)
        assertEquals(128, SupportedAssetCatalog.assets.size)
    }

    @Test
    fun createWalletInitializesAllSupportedAssetsAndWalletState() {
        val walletId = dao.createWallet(
            NewWalletRecord(
                walletName = "Primary",
                walletType = WalletType.Standard.value,
                walletKeyType = WalletKeyType.Mnemonic.value,
                walletKeyMaterial = TEST_MNEMONIC,
                passphrase = TEST_PASSPHRASE,
                isBackedUp = false,
                isImported = false,
            ),
            nowMillis = TEST_TIME,
        )

        val wallet = dao.getWallet(walletId)
        val walletAssets = dao.getWalletAssets(walletId)

        assertNotNull(wallet)
        checkNotNull(wallet)
        assertEquals("Primary", wallet.walletName)
        assertEquals(WalletKeyType.Mnemonic.value, wallet.walletKeyType)
        assertEquals(TEST_MNEMONIC, wallet.walletKeyMaterial)
        assertEquals(TEST_PASSPHRASE, wallet.passphrase)
        assertEquals(DEFAULT_LOCAL_CURRENCY_CODE, wallet.localCurrencyCode)
        assertEquals("0", wallet.balanceFiatValue)
        assertFalse(wallet.isBackedUp)
        assertFalse(wallet.isImported)
        assertFalse(wallet.isWatchOnly)
        assertTrue(wallet.isActive)
        assertEquals(SupportedAssetCatalog.assets.size, walletAssets.size)
        assertTrue(walletAssets.all { it.balanceRaw == "0" && it.localCurrencyCode == "USD" })
    }

    @Test
    fun walletStoresAddressesPrivateKeysAndTransactions() {
        val walletId = dao.createWallet(
            NewWalletRecord(
                walletName = "Imported",
                walletType = WalletType.Imported.value,
                walletKeyType = WalletKeyType.PrivateKey.value,
                walletKeyMaterial = "private-key-material",
                isImported = true,
            ),
            nowMillis = TEST_TIME,
        )

        val addressId = dao.insertWalletAddress(
            NewWalletAddressRecord(
                walletId = walletId,
                networkId = "ethereum",
                address = "0x1111111111111111111111111111111111111111",
                derivationPath = "m/44'/60'/0'/0/0",
                publicKey = "public-key",
                isPrimary = true,
                addressIndex = 0,
            ),
            nowMillis = TEST_TIME,
        )
        val privateKeyId = dao.insertWalletPrivateKey(
            NewWalletPrivateKeyRecord(
                walletId = walletId,
                networkId = "ethereum",
                addressId = addressId,
                keyMaterial = "private-key-material",
                keyFormat = "hex",
                derivationPath = "m/44'/60'/0'/0/0",
                publicKey = "public-key",
                keySource = WalletPrivateKeySource.Imported.value,
                isImported = true,
            ),
            nowMillis = TEST_TIME,
        )
        val transactionId = dao.insertWalletTransaction(
            NewWalletTransactionRecord(
                walletId = walletId,
                assetId = "ethereum:eth",
                networkId = "ethereum",
                transactionHash = "0xabc",
                direction = WalletTransactionDirection.Incoming.value,
                status = WalletTransactionStatus.Success.value,
                amountRaw = "1000000000000000000",
                amountDecimal = "1",
                feeRaw = "21000000000000",
                feeDecimal = "0.000021",
                feeAssetId = "ethereum:eth",
                fiatValue = "3500.00",
                fromAddress = "0x2222222222222222222222222222222222222222",
                toAddress = "0x1111111111111111111111111111111111111111",
                timestamp = TEST_TIME,
            ),
            nowMillis = TEST_TIME,
        )

        val addresses = dao.getWalletAddresses(walletId)
        val privateKeys = dao.getWalletPrivateKeys(walletId)
        val transactions = dao.getWalletTransactions(walletId)

        assertEquals(1, addresses.size)
        assertEquals(addressId, addresses.single().addressId)
        assertEquals("ethereum", addresses.single().networkId)
        assertEquals(1, privateKeys.size)
        assertEquals(privateKeyId, privateKeys.single().privateKeyId)
        assertEquals(addressId, privateKeys.single().addressId)
        assertEquals(1, transactions.size)
        assertEquals(transactionId, transactions.single().transactionId)
        assertEquals(WalletTransactionStatus.Success.value, transactions.single().status)
        assertEquals("3500.00", transactions.single().fiatValue)
    }

    @Test
    fun syncPersistenceUpdatesBalancesMetadataAndUpsertsTransactions() {
        val walletId = dao.createWallet(
            NewWalletRecord(
                walletName = "Watch",
                walletType = WalletType.WatchOnly.value,
                walletKeyType = WalletKeyType.Address.value,
                walletKeyMaterial = "0x1111111111111111111111111111111111111111",
                isImported = true,
                isWatchOnly = true,
            ),
            nowMillis = TEST_TIME,
        )

        dao.updateWalletAssetBalance(
            walletId = walletId,
            assetId = "ethereum:eth",
            balanceRaw = "1000000000000000000",
            balanceDecimal = "1",
            balanceFiatValue = "0",
            metadataJson = "{\"syncStatus\":\"complete\",\"syncProvider\":\"PublicNode Ethereum\"}",
            nowMillis = TEST_TIME + 1,
        )
        dao.upsertWalletTransaction(
            NewWalletTransactionRecord(
                walletId = walletId,
                assetId = "ethereum:eth",
                networkId = "ethereum",
                transactionHash = "0xsync",
                direction = WalletTransactionDirection.Incoming.value,
                status = WalletTransactionStatus.Pending.value,
                amountRaw = "100",
                amountDecimal = "0.0000000000000001",
                timestamp = TEST_TIME,
                metadataJson = "{\"syncStatus\":\"partial\"}",
            ),
            nowMillis = TEST_TIME + 2,
        )
        dao.upsertWalletTransaction(
            NewWalletTransactionRecord(
                walletId = walletId,
                assetId = "ethereum:eth",
                networkId = "ethereum",
                transactionHash = "0xsync",
                direction = WalletTransactionDirection.Incoming.value,
                status = WalletTransactionStatus.Success.value,
                amountRaw = "100",
                amountDecimal = "0.0000000000000001",
                confirmations = 12,
                timestamp = TEST_TIME,
                metadataJson = "{\"syncStatus\":\"complete\"}",
            ),
            nowMillis = TEST_TIME + 3,
        )
        dao.updateWalletSyncMetadata(
            walletId = walletId,
            metadataJson = "{\"evmSync\":{\"syncedNetworkCount\":1}}",
            nowMillis = TEST_TIME + 4,
        )

        val wallet = checkNotNull(dao.getWallet(walletId))
        val ethereumAsset = dao.getWalletAssets(walletId).first { it.assetId == "ethereum:eth" }
        val transactions = dao.getWalletTransactions(walletId)

        assertEquals("1", ethereumAsset.balanceDecimal)
        assertTrue(ethereumAsset.metadataJson.contains("PublicNode Ethereum"))
        assertEquals(1, transactions.size)
        assertEquals(WalletTransactionStatus.Success.value, transactions.single().status)
        assertEquals(12, transactions.single().confirmations)
        assertEquals(TEST_TIME + 4, wallet.lastSyncedAt)
        assertTrue(wallet.metadataJson.contains("evmSync"))
    }

    @Test
    fun repositoryPersistsCreateImportAndPrivateKeyFlowsForEveryNetwork() {
        val repository = SatraWalletRepository(dao)
        val createdWalletId = repository.createMnemonicWallet(
            walletName = "Created",
            mnemonic = TEST_MNEMONIC,
            passphrase = TEST_PASSPHRASE,
            isBackedUp = true,
            metadataJson = TEST_METADATA,
        )
        val importedMnemonicWalletId = repository.importMnemonicWallet(
            walletName = "Imported phrase",
            mnemonic = TEST_MNEMONIC,
            passphrase = " imported passphrase ",
            metadataJson = TEST_METADATA,
        )

        checkNotNull(dao.getWallet(createdWalletId)).also { wallet ->
            assertEquals(WalletType.Standard.value, wallet.walletType)
            assertTrue(wallet.isBackedUp)
            assertEquals(TEST_PASSPHRASE, wallet.passphrase)
            assertEquals(TEST_METADATA, wallet.metadataJson)
            assertEquals(8, wallet.walletKeyFingerprint?.length)
            assertEquals("m/44'/60'/0'/0/0", wallet.walletKeyDerivationPath)
        }
        assertEquals(EXPECTED_RECEIVE_ADDRESS_COUNT, dao.getWalletAddresses(createdWalletId).size)
        assertEquals(EXPECTED_RECEIVE_ADDRESS_COUNT, dao.getWalletPrivateKeys(createdWalletId).size)
        SupportedAssetCatalog.networks.forEach { network ->
            assertTrue(
                "Missing receive address for ${network.networkId}",
                dao.getWalletAddresses(createdWalletId).any { it.networkId == network.networkId },
            )
        }
        assertEquals(2, dao.getWalletAddresses(createdWalletId).count { it.networkId == "solana" })
        assertTrue(
            dao.getWalletAddresses(createdWalletId).filter {
                it.networkId in EvmProviderRegistry.supportedNetworkIds
            }.all {
                it.address.isEvmAddress() &&
                    it.derivationPath == "m/44'/60'/0'/0/0"
            },
        )
        checkNotNull(dao.getWallet(importedMnemonicWalletId)).also { wallet ->
            assertEquals(WalletType.Imported.value, wallet.walletType)
            assertTrue(wallet.isImported)
            assertFalse(wallet.isWatchOnly)
            assertEquals(" imported passphrase ", wallet.passphrase)
        }
        assertEquals(EXPECTED_RECEIVE_ADDRESS_COUNT, dao.getWalletAddresses(importedMnemonicWalletId).size)
        assertEquals(EXPECTED_RECEIVE_ADDRESS_COUNT, dao.getWalletPrivateKeys(importedMnemonicWalletId).size)

        SupportedAssetCatalog.networks.forEach { network ->
            val walletId = repository.importPrivateKeyWallet(
                walletName = "Imported ${network.displayName}",
                networkId = network.networkId,
                privateKey = TEST_PRIVATE_KEY_HEX,
                metadataJson = TEST_METADATA,
            )
            val wallet = checkNotNull(dao.getWallet(walletId))
            val privateKeys = dao.getWalletPrivateKeys(walletId)

            assertEquals(WalletType.Imported.value, wallet.walletType)
            assertEquals(WalletKeyType.PrivateKey.value, wallet.walletKeyType)
            assertTrue(wallet.isImported)
            assertFalse(wallet.isWatchOnly)
            assertEquals(SupportedAssetCatalog.assets.size, dao.getWalletAssets(walletId).size)
            assertEquals(1, privateKeys.size)
            assertEquals(network.networkId, privateKeys.single().networkId)
            assertEquals(TEST_PRIVATE_KEY_HEX, privateKeys.single().keyMaterial)
            assertEquals("hex", privateKeys.single().keyFormat)
            if (network.networkId in SECP256K1_PRIVATE_KEY_IMPORT_NETWORKS) {
                val addresses = dao.getWalletAddresses(walletId)
                assertEquals(1, addresses.size)
                assertEquals(addresses.single().addressId, privateKeys.single().addressId)
                assertTrue(addresses.single().address.isNotBlank())
            }
        }
    }

    @Test
    fun migratesVersionOneDatabaseWithPassphraseColumn() {
        val migrationDatabaseName = "satra_wallet_migration_test.db"
        context.deleteDatabase(migrationDatabaseName)
        context.openOrCreateDatabase(migrationDatabaseName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE ${SatraDatabaseContract.TABLE_WALLETS} (
                    wallet_id TEXT NOT NULL PRIMARY KEY
                )
                """.trimIndent(),
            )
            db.version = 1
        }

        val migrationHelper = SatraDatabaseOpenHelper(context, migrationDatabaseName)
        migrationHelper.writableDatabase.use { db ->
            assertTrue(db.hasColumn(SatraDatabaseContract.TABLE_WALLETS, "passphrase"))
            assertEquals(SatraDatabaseContract.DATABASE_VERSION, db.version)
        }
        migrationHelper.close()
        context.deleteDatabase(migrationDatabaseName)
    }

    private companion object {
        const val TEST_DATABASE_NAME = "satra_wallet_test.db"
        const val TEST_TIME = 1_788_249_600_000L
        const val TEST_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val TEST_PASSPHRASE = "correct battery horse"
        const val TEST_PRIVATE_KEY_HEX =
            "1111111111111111111111111111111111111111111111111111111111111111"
        const val TEST_METADATA =
            "{\"passcodeEnabled\":true,\"passcodeLength\":6,\"biometricsEnabled\":true}"
        val EXPECTED_RECEIVE_ADDRESS_COUNT = SupportedAssetCatalog.networks.size + 1
        val SECP256K1_PRIVATE_KEY_IMPORT_NETWORKS = setOf(
            "bitcoin",
            "bitcoinCash",
            "dogecoin",
            "litecoin",
            "ethereum",
            "arbitrum",
            "base",
            "optimism",
            "scroll",
            "zkSync",
            "polygon",
            "bnbChain",
            "opBNB",
            "avalanche",
            "celo",
            "kavaEvm",
            "ripple",
            "tron",
            "kava",
        )
    }
}

private fun SQLiteDatabase.hasColumn(
    tableName: String,
    columnName: String,
): Boolean =
    rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
        while (cursor.moveToNext()) {
            if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == columnName) {
                return true
            }
        }
        false
    }

private fun String.isEvmAddress(): Boolean {
    val normalized = removePrefix("0x")
    return startsWith("0x") &&
        normalized.length == 40 &&
        normalized.all { character ->
            character in '0'..'9' || character in 'a'..'f' || character in 'A'..'F'
        }
}
