package dev.satra.wallet.data.db

import android.content.Context

class SatraWalletRepository(
    private val walletDao: SatraWalletDao,
) {
    fun createMnemonicWallet(
        walletName: String,
        mnemonic: String,
        passphrase: String? = null,
        isBackedUp: Boolean = false,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
        metadataJson: String = EMPTY_JSON,
    ): String =
        walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Standard.value,
                walletKeyType = WalletKeyType.Mnemonic.value,
                walletKeyMaterial = mnemonic,
                passphrase = passphrase.cleanPassphrase(),
                localCurrencyCode = localCurrencyCode,
                isBackedUp = isBackedUp,
                isImported = false,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )

    fun importMnemonicWallet(
        walletName: String,
        mnemonic: String,
        passphrase: String? = null,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
        metadataJson: String = EMPTY_JSON,
    ): String =
        walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Imported.value,
                walletKeyType = WalletKeyType.Mnemonic.value,
                walletKeyMaterial = mnemonic,
                passphrase = passphrase.cleanPassphrase(),
                localCurrencyCode = localCurrencyCode,
                isImported = true,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )

    fun importPrivateKeyWallet(
        walletName: String,
        networkId: String,
        privateKey: String,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
        metadataJson: String = EMPTY_JSON,
    ): String {
        val walletId = walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Imported.value,
                walletKeyType = WalletKeyType.PrivateKey.value,
                walletKeyMaterial = privateKey,
                localCurrencyCode = localCurrencyCode,
                isImported = true,
                isWatchOnly = false,
                metadataJson = metadataJson,
            ),
        )
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

    private fun inferPrivateKeyFormat(privateKey: String): String {
        val normalized = privateKey.removePrefix("0x")
        return when {
            normalized.length == 64 && normalized.all(Char::isHexDigit) -> "hex"
            privateKey.length in 51..52 -> "wif"
            else -> "raw"
        }
    }
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun String?.cleanPassphrase(): String? =
    this?.takeIf(String::isNotEmpty)

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
