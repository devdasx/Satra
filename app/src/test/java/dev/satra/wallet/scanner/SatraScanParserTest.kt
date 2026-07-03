package dev.satra.wallet.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SatraScanParserTest {
    @Test
    fun parsesValidRecoveryPhraseQrPayload() {
        val result = SatraScanParser.parseForPurpose(
            rawValue = "  ABANDON\nabandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about  ",
            purpose = SatraScanPurpose.RecoveryPhrase,
        )

        assertEquals(SatraScanKind.RecoveryPhrase, result?.kind)
        assertEquals(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            result?.normalizedValue,
        )
    }

    @Test
    fun rejectsAddressWhenRecoveryPhraseIsRequired() {
        val result = SatraScanParser.parseForPurpose(
            rawValue = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kygt080",
            purpose = SatraScanPurpose.RecoveryPhrase,
        )

        assertNull(result)
    }

    @Test
    fun parsesPaymentUriWithAmount() {
        val result = SatraScanParser.parse(
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kygt080?amount=0.125&label=Satra",
        )

        assertEquals(SatraScanKind.PaymentUri, result?.kind)
        assertEquals("bitcoin", result?.scheme)
        assertEquals("0.125", result?.amount)
        assertEquals("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kygt080", result?.normalizedValue)
    }

    @Test
    fun parsesEvmAddress() {
        val result = SatraScanParser.parseForPurpose(
            rawValue = "0x742d35Cc6634C0532925a3b844Bc454e4438f44e",
            purpose = SatraScanPurpose.Address,
        )

        assertEquals(SatraScanKind.Address, result?.kind)
        assertEquals("0x742d35Cc6634C0532925a3b844Bc454e4438f44e", result?.normalizedValue)
    }

    @Test
    fun keepsUnknownQrPayloadAsRawForGenericScannerUse() {
        val result = SatraScanParser.parseForPurpose(
            rawValue = "not a wallet payload",
            purpose = SatraScanPurpose.Any,
        )

        assertEquals(SatraScanKind.Raw, result?.kind)
        assertEquals("not a wallet payload", result?.normalizedValue)
    }
}
