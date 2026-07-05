package dev.satra.wallet.data.send.ripple

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RippleSendClientTest {
    @Test
    fun parsesHashFromSubmitTxJsonObject() {
        val result = JSONObject()
            .put(
                "tx_json",
                JSONObject()
                    .put("hash", "ABC123")
                    .put("Account", "rSource"),
            )

        assertEquals("ABC123", RippleSendClient.parseSubmitHash(result))
    }

    @Test
    fun doesNotTreatTxJsonObjectAsHash() {
        val result = JSONObject()
            .put(
                "tx_json",
                JSONObject()
                    .put("Account", "rSource"),
            )
            .put("hash", "DEF456")

        assertEquals("DEF456", RippleSendClient.parseSubmitHash(result))
    }
}
