package dev.satra.wallet.wallet.bip39

import java.security.MessageDigest
import java.security.SecureRandom

object Bip39MnemonicGenerator {
    fun generate(
        wordCount: Int = DEFAULT_WORD_COUNT,
        secureRandom: SecureRandom = SecureRandom(),
    ): String {
        require(wordCount in supportedWordCounts) {
            "Unsupported BIP-39 word count: $wordCount"
        }

        val entropyBitCount = wordCount * BITS_PER_WORD * ENTROPY_BITS_PER_GROUP / TOTAL_BITS_PER_GROUP
        val entropy = ByteArray(entropyBitCount / Byte.SIZE_BITS)
        secureRandom.nextBytes(entropy)

        return mnemonicFromEntropy(entropy)
    }

    fun mnemonicFromEntropy(entropy: ByteArray): String {
        require(entropy.size * Byte.SIZE_BITS in supportedEntropyBitCounts) {
            "Unsupported BIP-39 entropy length: ${entropy.size * Byte.SIZE_BITS}"
        }

        val entropyBits = entropy.toBits()
        val checksumBits = MessageDigest.getInstance("SHA-256")
            .digest(entropy)
            .toBits()
            .take(entropyBits.length / ENTROPY_BITS_PER_GROUP)
        val mnemonicBits = entropyBits + checksumBits

        return mnemonicBits
            .chunked(BITS_PER_WORD)
            .map { wordBits -> Bip39EnglishWordlist.words[wordBits.toInt(radix = 2)] }
            .joinToString(separator = " ")
    }

    private fun ByteArray.toBits(): String =
        buildString(size * Byte.SIZE_BITS) {
            this@toBits.forEach { byte ->
                append((byte.toInt() and 0xff).toString(radix = 2).padStart(Byte.SIZE_BITS, '0'))
            }
        }

    private const val DEFAULT_WORD_COUNT = 12
    private const val BITS_PER_WORD = 11
    private const val ENTROPY_BITS_PER_GROUP = 32
    private const val TOTAL_BITS_PER_GROUP = 33
    private val supportedWordCounts = setOf(12, 15, 18, 21, 24)
    private val supportedEntropyBitCounts = setOf(128, 160, 192, 224, 256)
}
