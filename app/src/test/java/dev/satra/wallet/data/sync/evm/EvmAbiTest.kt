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
}
