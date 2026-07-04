package dev.satra.wallet.data.sync.accountchain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

class PolkadotStorageTest {
    @Test
    fun decodesSs58AccountId() {
        val accountId = PolkadotStorage.decodeSs58AccountId(USER_ASSET_HUB_ADDRESS)

        assertEquals(
            "eca5cd647acfc18882576f1d03ad3be6eab7a566b4bd91364f937dbec897d1fb",
            accountId.toHexForTest(),
        )
    }

    @Test
    fun buildsSystemAccountStorageKey() {
        val accountId = PolkadotStorage.decodeSs58AccountId(USER_ASSET_HUB_ADDRESS)

        assertEquals(
            "0x26aa394eea5630e07c48ae0c9558cef7" +
                "b99d880ec681799c0cf30e8886371da9" +
                "1ac6c1115bef934f44055225625095f6" +
                "eca5cd647acfc18882576f1d03ad3be6eab7a566b4bd91364f937dbec897d1fb",
            PolkadotStorage.systemAccountKey(accountId),
        )
    }

    @Test
    fun parsesSystemAccountFreeBalance() {
        val free = BigInteger("1229965065800")
        val storage = "0x" +
            ByteArray(16).toHexForTest() +
            free.toLittleEndianU128HexForTest() +
            ByteArray(48).toHexForTest()

        assertEquals(free, PolkadotStorage.parseSystemAccountFree(storage))
    }

    @Test
    fun parsesAssetAccountBalance() {
        val balance = BigInteger("25000000")
        val storage = "0x" +
            balance.toLittleEndianU128HexForTest() +
            ByteArray(32).toHexForTest()

        assertEquals(balance, PolkadotStorage.parseAssetAccountBalance(storage))
    }

    private fun BigInteger.toLittleEndianU128HexForTest(): String =
        toByteArray()
            .dropWhile { it == 0.toByte() }
            .toByteArray()
            .let { bigEndian ->
                ByteArray(16).also { output ->
                    bigEndian.reversedArray().copyInto(output)
                }
            }
            .toHexForTest()

    private fun ByteArray.toHexForTest(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val USER_ASSET_HUB_ADDRESS = "16MHZ9tPcLkF4VMD33NYqTxDSEmT64RBsE8JSg4oSamar2PS"
    }
}
