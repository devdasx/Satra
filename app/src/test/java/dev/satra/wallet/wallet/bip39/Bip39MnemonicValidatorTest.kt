package dev.satra.wallet.wallet.bip39

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Bip39MnemonicValidatorTest {
    @Test
    fun validatesSupportedBip39WordCounts() {
        val validMnemonics = mapOf(
            12 to "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            15 to "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon address",
            18 to "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon agent",
            21 to "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon admit",
            24 to "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art",
        )

        validMnemonics.forEach { (wordCount, mnemonic) ->
            val validation = Bip39MnemonicValidator.validate(mnemonic)

            assertTrue("Expected $wordCount-word mnemonic to be valid", validation.isValid)
            assertEquals(wordCount, (validation as Bip39MnemonicValidation.Valid).wordCount)
        }
    }

    @Test
    fun rejectsUnsupportedWordCount() {
        val validation = Bip39MnemonicValidator.validate(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon",
        )

        assertFalse(validation.isValid)
        assertEquals(Bip39MnemonicValidation.InvalidWordCount(wordCount = 11), validation)
    }

    @Test
    fun rejectsUnknownWords() {
        val validation = Bip39MnemonicValidator.validate(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon satra",
        )

        assertFalse(validation.isValid)
        assertEquals(Bip39MnemonicValidation.UnknownWord(word = "satra"), validation)
    }

    @Test
    fun rejectsInvalidChecksum() {
        val validation = Bip39MnemonicValidator.validate(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon",
        )

        assertFalse(validation.isValid)
        assertEquals(Bip39MnemonicValidation.InvalidChecksum, validation)
    }

    @Test
    fun normalizesCaseAndWhitespace() {
        val validation = Bip39MnemonicValidator.validate(
            "  ABANDON\nabandon\tabandon abandon abandon abandon abandon abandon abandon abandon abandon about  ",
        )

        assertEquals(Bip39MnemonicValidation.Valid(wordCount = 12), validation)
    }
}
