package dev.satra.wallet.ui.main

import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionRecord
import dev.satra.wallet.data.db.WalletTransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class HomeBalanceChartModelTest {
    @Test
    fun oneWeekRangeCarriesPriorBalanceAndCalculatesPercentChange() {
        val now = DAY * 20
        val data = buildHomeBalanceChartData(
            transactions = listOf(
                transaction(
                    amount = "100",
                    timestamp = now - DAY * 10,
                    direction = WalletTransactionDirection.Incoming.value,
                ),
                transaction(
                    amount = "20",
                    timestamp = now - DAY * 3,
                    direction = WalletTransactionDirection.Outgoing.value,
                ),
                transaction(
                    amount = "10",
                    timestamp = now - DAY,
                    direction = WalletTransactionDirection.Incoming.value,
                ),
            ),
            range = HomeChartRange.OneWeek,
            nowMillis = now,
        )

        assertEquals(BigDecimal("100"), data.startValue)
        assertEquals(BigDecimal("90"), data.currentValue)
        assertEquals(BigDecimal("-10"), data.changeValue)
        assertEquals(BigDecimal("-10.0").compareTo(data.percentChange), 0)
        assertEquals(2, data.transactionCount)
        assertEquals(now - HomeChartRange.OneWeek.durationMillis!!, data.points.first().timestampMillis)
        assertEquals(now, data.points.last().timestampMillis)
    }

    @Test
    fun allRangeUsesSuccessfulSignedTransactionAmountsOnly() {
        val now = DAY * 5
        val data = buildHomeBalanceChartData(
            transactions = listOf(
                transaction(
                    amount = "50",
                    timestamp = DAY,
                    direction = WalletTransactionDirection.Incoming.value,
                ),
                transaction(
                    amount = "12.5",
                    timestamp = DAY * 2,
                    direction = WalletTransactionDirection.Outgoing.value,
                ),
                transaction(
                    amount = "99",
                    timestamp = DAY * 3,
                    direction = WalletTransactionDirection.Incoming.value,
                    status = WalletTransactionStatus.Pending.value,
                ),
                transaction(
                    amount = "25",
                    timestamp = DAY * 4,
                    direction = WalletTransactionDirection.Self.value,
                ),
            ),
            range = HomeChartRange.All,
            nowMillis = now,
        )

        assertEquals(BigDecimal.ZERO, data.startValue)
        assertEquals(BigDecimal("37.5"), data.currentValue)
        assertEquals(BigDecimal("37.5"), data.changeValue)
        assertEquals(BigDecimal("100"), data.percentChange)
        assertEquals(3, data.transactionCount)
    }

    @Test
    fun nearestPointSelectionUsesHorizontalPosition() {
        assertEquals(0, nearestHomeChartPointIndex(xPosition = -10f, chartWidth = 100f, pointCount = 5))
        assertEquals(2, nearestHomeChartPointIndex(xPosition = 50f, chartWidth = 100f, pointCount = 5))
        assertEquals(4, nearestHomeChartPointIndex(xPosition = 200f, chartWidth = 100f, pointCount = 5))
    }

    private fun transaction(
        amount: String,
        timestamp: Long,
        direction: String,
        status: String = WalletTransactionStatus.Success.value,
    ): WalletTransactionRecord =
        WalletTransactionRecord(
            transactionId = "tx-$timestamp-$amount",
            walletId = "wallet",
            assetId = "ethereum:eth",
            networkId = "ethereum",
            transactionHash = "0x$timestamp",
            direction = direction,
            status = status,
            amountRaw = amount,
            amountDecimal = amount,
            feeRaw = null,
            feeDecimal = null,
            feeAssetId = null,
            fiatValue = null,
            localCurrencyCode = "USD",
            fromAddress = null,
            toAddress = null,
            blockHeight = null,
            blockHash = null,
            confirmations = 0,
            nonce = null,
            memo = null,
            timestamp = timestamp,
            firstSeenAt = timestamp,
            updatedAt = timestamp,
            metadataJson = "{}",
        )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
