package dev.satra.wallet.wallet.bip39

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

object Bip39MnemonicValidator {
    private val supportedWordCounts = setOf(12, 15, 18, 21, 24)
    private val englishWordIndex = Bip39EnglishWordlist.words
        .withIndex()
        .associate { indexedWord -> indexedWord.value to indexedWord.index }

    fun validate(mnemonic: String): Bip39MnemonicValidation {
        val words = normalizedWords(mnemonic)

        if (words.isEmpty()) {
            return Bip39MnemonicValidation.Empty
        }

        if (words.size !in supportedWordCounts) {
            return Bip39MnemonicValidation.InvalidWordCount(words.size)
        }

        val mnemonicBits = StringBuilder(words.size * WORD_INDEX_BITS)
        words.forEach { word ->
            val wordIndex = englishWordIndex[word] ?: return Bip39MnemonicValidation.UnknownWord(word)
            mnemonicBits.append(wordIndex.toString(radix = 2).padStart(WORD_INDEX_BITS, '0'))
        }

        val entropyBitCount = mnemonicBits.length * ENTROPY_BITS_PER_GROUP / TOTAL_BITS_PER_GROUP
        val checksumBitCount = mnemonicBits.length - entropyBitCount
        val entropy = bitsToBytes(mnemonicBits.substring(0, entropyBitCount))
        val mnemonicChecksum = mnemonicBits.substring(entropyBitCount)
        val expectedChecksum = sha256Bits(entropy).take(checksumBitCount)

        return if (mnemonicChecksum == expectedChecksum) {
            Bip39MnemonicValidation.Valid(wordCount = words.size)
        } else {
            Bip39MnemonicValidation.InvalidChecksum
        }
    }

    private fun normalizedWords(mnemonic: String): List<String> {
        val normalizedMnemonic = Normalizer.normalize(
            mnemonic.trim().lowercase(Locale.US),
            Normalizer.Form.NFKD,
        )

        if (normalizedMnemonic.isBlank()) {
            return emptyList()
        }

        return normalizedMnemonic.split(Regex("\\s+"))
    }

    private fun bitsToBytes(bits: String): ByteArray =
        ByteArray(bits.length / Byte.SIZE_BITS) { byteIndex ->
            bits.substring(
                startIndex = byteIndex * Byte.SIZE_BITS,
                endIndex = (byteIndex + 1) * Byte.SIZE_BITS,
            ).toInt(radix = 2).toByte()
        }

    private fun sha256Bits(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(capacity = digest.size * Byte.SIZE_BITS) {
            digest.forEach { byte ->
                append((byte.toInt() and 0xff).toString(radix = 2).padStart(Byte.SIZE_BITS, '0'))
            }
        }
    }

    private const val WORD_INDEX_BITS = 11
    private const val ENTROPY_BITS_PER_GROUP = 32
    private const val TOTAL_BITS_PER_GROUP = 33
}

sealed interface Bip39MnemonicValidation {
    val isValid: Boolean
        get() = this is Valid

    data class Valid(val wordCount: Int) : Bip39MnemonicValidation
    data object Empty : Bip39MnemonicValidation
    data class InvalidWordCount(val wordCount: Int) : Bip39MnemonicValidation
    data class UnknownWord(val word: String) : Bip39MnemonicValidation
    data object InvalidChecksum : Bip39MnemonicValidation
}
