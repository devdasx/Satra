package dev.satra.wallet.data.db

data class WalletRecord(
    val walletId: String,
    val walletName: String,
    val walletType: String,
    val walletKeyType: String,
    val walletKeyMaterial: String?,
    val walletKeyFingerprint: String?,
    val walletKeyDerivationPath: String?,
    val passphrase: String?,
    val walletKeyEncryptionState: String,
    val localCurrencyCode: String,
    val balanceFiatValue: String,
    val balanceFiatUpdatedAt: Long?,
    val isBackedUp: Boolean,
    val backupVerifiedAt: Long?,
    val isImported: Boolean,
    val isWatchOnly: Boolean,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long?,
    val metadataJson: String,
)

data class NewWalletRecord(
    val walletName: String,
    val walletType: String,
    val walletKeyType: String,
    val walletKeyMaterial: String?,
    val walletKeyFingerprint: String? = null,
    val walletKeyDerivationPath: String? = null,
    val passphrase: String? = null,
    val walletKeyEncryptionState: String = WalletKeyEncryptionState.Plain.value,
    val localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
    val isBackedUp: Boolean = false,
    val backupVerifiedAt: Long? = null,
    val isImported: Boolean = false,
    val isWatchOnly: Boolean = false,
    val metadataJson: String = EMPTY_JSON,
)

data class AppSettingsRecord(
    val settingsId: String,
    val localCurrencyCode: String,
    val languageTag: String,
    val themePreference: String,
    val hapticsEnabled: Boolean,
    val passcodeEnabled: Boolean,
    val passcodeHash: String?,
    val passcodeSalt: String?,
    val passcodeLength: Int?,
    val biometricsEnabled: Boolean,
    val autoLockTimeoutMillis: Long,
    val eraseWalletEnabled: Boolean,
    val eraseWalletAttemptLimit: Int,
    val failedPasscodeAttempts: Int,
    val notificationsNewsEnabled: Boolean,
    val notificationsPricesEnabled: Boolean,
    val notificationsTransactionsEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val metadataJson: String,
)

data class AppSettingsUpdate(
    val localCurrencyCode: String? = null,
    val languageTag: String? = null,
    val themePreference: String? = null,
    val hapticsEnabled: Boolean? = null,
    val passcodeEnabled: Boolean? = null,
    val passcodeHash: String? = null,
    val passcodeSalt: String? = null,
    val clearPasscode: Boolean = false,
    val passcodeLength: Int? = null,
    val biometricsEnabled: Boolean? = null,
    val autoLockTimeoutMillis: Long? = null,
    val eraseWalletEnabled: Boolean? = null,
    val eraseWalletAttemptLimit: Int? = null,
    val failedPasscodeAttempts: Int? = null,
    val notificationsNewsEnabled: Boolean? = null,
    val notificationsPricesEnabled: Boolean? = null,
    val notificationsTransactionsEnabled: Boolean? = null,
    val metadataJson: String? = null,
)

data class AddressBookEntryRecord(
    val entryId: String,
    val label: String,
    val networkId: String,
    val address: String,
    val notes: String?,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val metadataJson: String,
)

data class NewAddressBookEntryRecord(
    val label: String,
    val networkId: String,
    val address: String,
    val notes: String? = null,
    val isFavorite: Boolean = false,
    val metadataJson: String = EMPTY_JSON,
)

data class WalletAssetRecord(
    val walletAssetId: String,
    val walletId: String,
    val assetId: String,
    val networkId: String,
    val isEnabled: Boolean,
    val isVisible: Boolean,
    val balanceRaw: String,
    val balanceDecimal: String,
    val balanceFiatValue: String,
    val localCurrencyCode: String,
    val priceFiatValue: String,
    val priceFiatUpdatedAt: Long?,
    val balanceUpdatedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val metadataJson: String,
)

data class WalletAddressRecord(
    val addressId: String,
    val walletId: String,
    val networkId: String,
    val address: String,
    val addressType: String,
    val derivationPath: String?,
    val publicKey: String?,
    val privateKeyId: String?,
    val isPrimary: Boolean,
    val isChange: Boolean,
    val addressIndex: Int?,
    val label: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long?,
    val metadataJson: String,
)

data class NewWalletAddressRecord(
    val walletId: String,
    val networkId: String,
    val address: String,
    val addressType: String = WalletAddressType.Receive.value,
    val derivationPath: String? = null,
    val publicKey: String? = null,
    val privateKeyId: String? = null,
    val isPrimary: Boolean = false,
    val isChange: Boolean = false,
    val addressIndex: Int? = null,
    val label: String? = null,
    val metadataJson: String = EMPTY_JSON,
)

data class WalletPrivateKeyRecord(
    val privateKeyId: String,
    val walletId: String,
    val networkId: String,
    val addressId: String?,
    val keyMaterial: String,
    val keyFormat: String,
    val derivationPath: String?,
    val publicKey: String?,
    val keySource: String,
    val isImported: Boolean,
    val isEncrypted: Boolean,
    val keyFingerprint: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val metadataJson: String,
)

data class NewWalletPrivateKeyRecord(
    val walletId: String,
    val networkId: String,
    val addressId: String? = null,
    val keyMaterial: String,
    val keyFormat: String,
    val derivationPath: String? = null,
    val publicKey: String? = null,
    val keySource: String,
    val isImported: Boolean,
    val isEncrypted: Boolean = false,
    val keyFingerprint: String? = null,
    val metadataJson: String = EMPTY_JSON,
)

data class WalletTransactionRecord(
    val transactionId: String,
    val walletId: String,
    val assetId: String,
    val networkId: String,
    val transactionHash: String?,
    val direction: String,
    val status: String,
    val amountRaw: String,
    val amountDecimal: String,
    val feeRaw: String?,
    val feeDecimal: String?,
    val feeAssetId: String?,
    val fiatValue: String?,
    val localCurrencyCode: String,
    val fromAddress: String?,
    val toAddress: String?,
    val blockHeight: Long?,
    val blockHash: String?,
    val confirmations: Int,
    val nonce: String?,
    val memo: String?,
    val timestamp: Long,
    val firstSeenAt: Long,
    val updatedAt: Long,
    val metadataJson: String,
)

data class NewWalletTransactionRecord(
    val walletId: String,
    val assetId: String,
    val networkId: String,
    val transactionHash: String?,
    val direction: String,
    val status: String,
    val amountRaw: String,
    val amountDecimal: String,
    val feeRaw: String? = null,
    val feeDecimal: String? = null,
    val feeAssetId: String? = null,
    val fiatValue: String? = null,
    val localCurrencyCode: String = DEFAULT_LOCAL_CURRENCY_CODE,
    val fromAddress: String? = null,
    val toAddress: String? = null,
    val blockHeight: Long? = null,
    val blockHash: String? = null,
    val confirmations: Int = 0,
    val nonce: String? = null,
    val memo: String? = null,
    val timestamp: Long,
    val metadataJson: String = EMPTY_JSON,
)

enum class WalletType(val value: String) {
    Standard("standard"),
    Imported("imported"),
    WatchOnly("watch_only"),
}

enum class WalletKeyType(val value: String) {
    Mnemonic("mnemonic"),
    PrivateKey("private_key"),
    Address("address"),
}

enum class WalletKeyEncryptionState(val value: String) {
    Plain("plain"),
    Encrypted("encrypted"),
}

enum class WalletAddressType(val value: String) {
    Receive("receive"),
    Change("change"),
    WatchOnly("watch_only"),
}

enum class WalletPrivateKeySource(val value: String) {
    MnemonicDerived("mnemonic_derived"),
    Imported("imported"),
}

enum class WalletTransactionDirection(val value: String) {
    Incoming("incoming"),
    Outgoing("outgoing"),
    Self("self"),
    Unknown("unknown"),
}

enum class WalletTransactionStatus(val value: String) {
    Pending("pending"),
    Success("success"),
    Canceled("canceled"),
    Failed("failed"),
}

const val DEFAULT_LOCAL_CURRENCY_CODE = "USD"
const val EMPTY_JSON = "{}"
