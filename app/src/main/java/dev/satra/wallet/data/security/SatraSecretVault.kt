package dev.satra.wallet.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.satra.wallet.data.db.NewWalletSecretRecord
import dev.satra.wallet.data.db.WalletSecretRecord
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SatraSecretCipher {
    fun encrypt(
        walletId: String,
        secretType: String,
        plaintext: String,
        networkId: String? = null,
        derivationPath: String? = null,
        metadataJson: String = "{}",
    ): NewWalletSecretRecord

    @Throws(SatraSecretVaultException::class)
    fun decrypt(secret: WalletSecretRecord): String
}

class SatraSecretVault : SatraSecretCipher {
    override fun encrypt(
        walletId: String,
        secretType: String,
        plaintext: String,
        networkId: String?,
        derivationPath: String?,
        metadataJson: String,
    ): NewWalletSecretRecord {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        require(cipher.iv.size == GCM_IV_BYTES) {
            "Android Keystore returned an invalid GCM IV."
        }
        cipher.updateAAD(
            associatedData(
                walletId = walletId,
                secretType = secretType,
                networkId = networkId,
                derivationPath = derivationPath,
                encryptionVersion = ENCRYPTION_VERSION,
            ),
        )
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return NewWalletSecretRecord(
            walletId = walletId,
            secretType = secretType,
            networkId = networkId,
            derivationPath = derivationPath,
            encryptionVersion = ENCRYPTION_VERSION,
            encryptionAlgorithm = ENCRYPTION_ALGORITHM,
            keystoreAlias = KEYSTORE_ALIAS,
            ivBase64 = cipher.iv.toBase64(),
            ciphertextBase64 = ciphertext.toBase64(),
            metadataJson = metadataJson,
        )
    }

    override fun decrypt(secret: WalletSecretRecord): String {
        if (secret.encryptionVersion != ENCRYPTION_VERSION ||
            secret.keystoreAlias != KEYSTORE_ALIAS ||
            secret.encryptionAlgorithm != ENCRYPTION_ALGORITHM
        ) {
            throw SatraSecretVaultException("Unsupported secret encryption metadata.")
        }

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getExistingKey(),
                GCMParameterSpec(GCM_TAG_BITS, secret.ivBase64.fromBase64()),
            )
            cipher.updateAAD(
                associatedData(
                    walletId = secret.walletId,
                    secretType = secret.secretType,
                    networkId = secret.networkId,
                    derivationPath = secret.derivationPath,
                    encryptionVersion = secret.encryptionVersion,
                ),
            )
            String(cipher.doFinal(secret.ciphertextBase64.fromBase64()), StandardCharsets.UTF_8)
        } catch (error: AEADBadTagException) {
            throw SatraSecretVaultException("Secret authentication failed.", error)
        } catch (error: Exception) {
            throw SatraSecretVaultException("Secret decryption failed.", error)
        }
    }

    private fun getExistingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)
            ?: throw SatraSecretVaultException("Satra vault key is unavailable.")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        return generateKey(strongBoxBacked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            ?: generateKey(strongBoxBacked = false)
            ?: throw SatraSecretVaultException("Unable to create Satra vault key.")
    }

    private fun generateKey(strongBoxBacked: Boolean): SecretKey? =
        runCatching {
            val builder = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
            if (strongBoxBacked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
                init(builder.build())
                generateKey()
            }
        }.getOrNull()

    private fun associatedData(
        walletId: String,
        secretType: String,
        networkId: String?,
        derivationPath: String?,
        encryptionVersion: Int,
    ): ByteArray =
        buildString {
            append("satra-secret-v")
            append(encryptionVersion)
            append("|wallet=")
            append(walletId)
            append("|type=")
            append(secretType)
            append("|network=")
            append(networkId.orEmpty())
            append("|path=")
            append(derivationPath.orEmpty())
        }.toByteArray(StandardCharsets.UTF_8)

    private fun ByteArray.toBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        Base64.decode(this, Base64.NO_WRAP)

    companion object {
        const val KEYSTORE_ALIAS = "satra.vault.master.v1"
        const val ENCRYPTION_VERSION = 1
        const val ENCRYPTION_ALGORITHM = "AndroidKeystore/AES-256-GCM"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
    }
}

class SatraSecretVaultException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
