package dev.satra.wallet.settings

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SatraPasscodeHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    fun hash(passcode: String): SatraPasscodeHash {
        require(passcode.length in setOf(4, 6) && passcode.all(Char::isDigit)) {
            "Passcode must be 4 or 6 digits."
        }
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return SatraPasscodeHash(
            hash = derive(passcode, salt).base64(),
            salt = salt.base64(),
        )
    }

    fun verify(
        passcode: String,
        expectedHash: String?,
        salt: String?,
    ): Boolean {
        if (expectedHash.isNullOrBlank() || salt.isNullOrBlank()) return false
        val decodedSalt = runCatching { Base64.getDecoder().decode(salt) }.getOrNull() ?: return false
        val candidate = derive(passcode, decodedSalt)
        val expected = runCatching { Base64.getDecoder().decode(expectedHash) }.getOrNull() ?: return false
        return MessageDigest.isEqual(candidate, expected)
    }

    private fun derive(
        passcode: String,
        salt: ByteArray,
    ): ByteArray {
        val spec = PBEKeySpec(passcode.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }

    private fun ByteArray.base64(): String =
        Base64.getEncoder().encodeToString(this)
}

data class SatraPasscodeHash(
    val hash: String,
    val salt: String,
)
