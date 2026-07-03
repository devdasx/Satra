package dev.satra.wallet.data.db

import android.content.Context

class SatraWalletRepository(
    private val walletDao: SatraWalletDao,
) {
    fun createMnemonicWallet(
        walletName: String,
        mnemonic: String,
        isBackedUp: Boolean = false,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
    ): String =
        walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Standard.value,
                walletKeyType = WalletKeyType.Mnemonic.value,
                walletKeyMaterial = mnemonic,
                localCurrencyCode = localCurrencyCode,
                isBackedUp = isBackedUp,
                isImported = false,
                isWatchOnly = false,
            ),
        )

    fun importMnemonicWallet(
        walletName: String,
        mnemonic: String,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
    ): String =
        walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Imported.value,
                walletKeyType = WalletKeyType.Mnemonic.value,
                walletKeyMaterial = mnemonic,
                localCurrencyCode = localCurrencyCode,
                isImported = true,
                isWatchOnly = false,
            ),
        )

    fun importPrivateKeyWallet(
        walletName: String,
        privateKey: String,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
    ): String =
        walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.Imported.value,
                walletKeyType = WalletKeyType.PrivateKey.value,
                walletKeyMaterial = privateKey,
                localCurrencyCode = localCurrencyCode,
                isImported = true,
                isWatchOnly = false,
            ),
        )

    fun importWatchOnlyWallet(
        walletName: String,
        address: String,
        localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
    ): String =
        walletDao.createWallet(
            NewWalletRecord(
                walletName = walletName,
                walletType = WalletType.WatchOnly.value,
                walletKeyType = WalletKeyType.Address.value,
                walletKeyMaterial = address,
                localCurrencyCode = localCurrencyCode,
                isImported = true,
                isWatchOnly = true,
            ),
        )
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
