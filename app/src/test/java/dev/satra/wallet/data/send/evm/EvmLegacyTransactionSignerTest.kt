package dev.satra.wallet.data.send.evm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

class EvmLegacyTransactionSignerTest {
    @Test
    fun signsEip155LegacyTransactionVector() {
        val rawTransaction = EvmLegacyTransactionSigner.sign(
            transaction = EvmLegacyTransaction(
                nonce = BigInteger.valueOf(9L),
                gasPrice = BigInteger("20000000000"),
                gasLimit = BigInteger.valueOf(21_000L),
                toAddress = "0x3535353535353535353535353535353535353535",
                value = BigInteger("1000000000000000000"),
                data = ByteArray(0),
                chainId = 1L,
            ),
            privateKeyHex = "4646464646464646464646464646464646464646464646464646464646464646",
        )

        assertEquals(
            "0xf86c098504a817c800825208943535353535353535353535353535353535353535" +
                "880de0b6b3a76400008025a028ef61340bd939bc2195fe537567866003e1a15d" +
                "3c71ff63e1590620aa636276a067cbe9d8997f761aecb703304b3800ccf555c" +
                "9f3dc64214b297fb1966a3b6d83",
            rawTransaction,
        )
        assertEquals(
            "0x33469b22e9f636356c4160a87eb19df52b7412e8eac32a4a55ffe88ea8350788",
            EvmLegacyTransactionSigner.transactionHash(rawTransaction),
        )
    }

    @Test
    fun rejectsInvalidPrivateKeyBeforeSigning() {
        assertThrows(IllegalArgumentException::class.java) {
            EvmLegacyTransactionSigner.sign(
                transaction = EvmLegacyTransaction(
                    nonce = BigInteger.ZERO,
                    gasPrice = BigInteger.ONE,
                    gasLimit = BigInteger.valueOf(21_000L),
                    toAddress = "0x3535353535353535353535353535353535353535",
                    value = BigInteger.ZERO,
                    data = ByteArray(0),
                    chainId = 1L,
                ),
                privateKeyHex = "01",
            )
        }
    }
}
