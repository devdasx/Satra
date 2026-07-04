package dev.satra.wallet.data.sync.evm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

class EvmAbiTest {
    @Test
    fun balanceOfCallDataEncodesOwnerAddress() {
        val callData = EvmAbi.balanceOfCallData("0x1111111111111111111111111111111111111111")

        assertEquals(
            "0x70a082310000000000000000000000001111111111111111111111111111111111111111",
            callData,
        )
    }

    @Test
    fun uint256DecodingAndDecimalConversionUseAssetDecimals() {
        val decoded = EvmAbi.decodeUint256("0x0000000000000000000000000000000000000000000000000de0b6b3a7640000")

        assertEquals(BigInteger("1000000000000000000"), decoded)
        assertEquals("1", EvmAbi.rawToDecimalString(decoded, 18))
        assertEquals("1.234567", EvmAbi.rawToDecimalString("1234567", 6))
        assertEquals("0", EvmAbi.rawToDecimalString("0", 18))
    }

    @Test
    fun transferCallDataEncodesRecipientAndAmount() {
        val callData = EvmAbi.transferCallData(
            toAddress = "0x2222222222222222222222222222222222222222",
            rawAmount = BigInteger("1000000"),
        )

        assertEquals(
            "0xa9059cbb0000000000000000000000002222222222222222222222222222222222222222" +
                "00000000000000000000000000000000000000000000000000000000000f4240",
            callData,
        )
    }

    @Test
    fun quantityHexEncodesJsonRpcQuantities() {
        assertEquals("0x0", EvmAbi.quantityHex(BigInteger.ZERO))
        assertEquals("0x5208", EvmAbi.quantityHex(BigInteger.valueOf(21_000L)))
    }
}
