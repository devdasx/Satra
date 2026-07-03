package dev.satra.wallet.ui.main

import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionRecord
import dev.satra.wallet.data.db.WalletTransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

enum class HomeChartRange(
    val durationMillis: Long?,
) {
    OneDay(24L * 60L * 60L * 1000L),
    OneWeek(7L * 24L * 60L * 60L * 1000L),
    OneMonth(30L * 24L * 60L * 60L * 1000L),
    OneYear(365L * 24L * 60L * 60L * 1000L),
    All(null),
}

data class HomeBalanceChartPoint(
    val timestampMillis: Long,
    val value: BigDecimal,
)

data class HomeBalanceChartData(
    val range: HomeChartRange,
    val points: List<HomeBalanceChartPoint>,
    val startValue: BigDecimal,
    val currentValue: BigDecimal,
    val changeValue: BigDecimal,
    val percentChange: BigDecimal,
    val transactionCount: Int,
) {
    val hasActivity: Boolean
        get() = transactionCount > 0
}

fun buildHomeBalanceChartData(
    transactions: List<WalletTransactionRecord>,
    range: HomeChartRange,
    nowMillis: Long,
): HomeBalanceChartData {
    val chartEvents = transactions
        .mapNotNull { transaction ->
            transaction.toChartEvent()?.takeIf { event -> event.timestampMillis <= nowMillis }
        }
        .sortedBy(HomeChartEvent::timestampMillis)

    val firstEventTime = chartEvents.firstOrNull()?.timestampMillis ?: nowMillis
    val rangeStart = range.durationMillis
        ?.let { duration -> (nowMillis - duration).coerceAtMost(nowMillis) }
        ?: firstEventTime

    val startValue = chartEvents
        .filter { event -> event.timestampMillis < rangeStart }
        .fold(BigDecimal.ZERO) { total, event -> total + event.amount }

    val eventsInRange = chartEvents
        .filter { event -> event.timestampMillis in rangeStart..nowMillis }
        .groupBy(HomeChartEvent::timestampMillis)
        .map { (timestamp, events) ->
            HomeChartEvent(
                timestampMillis = timestamp,
                amount = events.fold(BigDecimal.ZERO) { total, event -> total + event.amount },
            )
        }
        .sortedBy(HomeChartEvent::timestampMillis)

    var runningValue = startValue
    val points = mutableListOf(HomeBalanceChartPoint(rangeStart, runningValue))
    eventsInRange.forEach { event ->
        runningValue += event.amount
        points += HomeBalanceChartPoint(
            timestampMillis = event.timestampMillis,
            value = runningValue,
        )
    }
    if (points.last().timestampMillis != nowMillis) {
        points += HomeBalanceChartPoint(
            timestampMillis = nowMillis,
            value = runningValue,
        )
    }

    val currentValue = points.lastOrNull()?.value ?: BigDecimal.ZERO
    val changeValue = currentValue - startValue
    return HomeBalanceChartData(
        range = range,
        points = points,
        startValue = startValue,
        currentValue = currentValue,
        changeValue = changeValue,
        percentChange = percentChange(
            startValue = startValue,
            changeValue = changeValue,
        ),
        transactionCount = eventsInRange.count(),
    )
}

fun nearestHomeChartPointIndex(
    xPosition: Float,
    chartWidth: Float,
    pointCount: Int,
): Int {
    if (pointCount <= 1 || chartWidth <= 0f) return 0
    val clampedX = xPosition.coerceIn(0f, chartWidth)
    val normalized = clampedX / chartWidth
    return (normalized * (pointCount - 1))
        .roundToInt()
        .coerceIn(0, pointCount - 1)
}

private data class HomeChartEvent(
    val timestampMillis: Long,
    val amount: BigDecimal,
)

private fun WalletTransactionRecord.toChartEvent(): HomeChartEvent? {
    if (status != WalletTransactionStatus.Success.value) return null
    val amount = (fiatValue?.takeIf(String::isNotBlank) ?: amountDecimal)
        .toBigDecimalOrNullSafe()
        ?: return null
    val signedAmount = when (direction) {
        WalletTransactionDirection.Incoming.value -> amount
        WalletTransactionDirection.Outgoing.value -> amount.negate()
        WalletTransactionDirection.Self.value -> BigDecimal.ZERO
        else -> amount
    }
    return HomeChartEvent(
        timestampMillis = timestamp,
        amount = signedAmount,
    )
}

private fun percentChange(
    startValue: BigDecimal,
    changeValue: BigDecimal,
): BigDecimal {
    if (startValue.compareTo(BigDecimal.ZERO) == 0) {
        return when {
            changeValue.compareTo(BigDecimal.ZERO) > 0 -> BigDecimal("100")
            changeValue.compareTo(BigDecimal.ZERO) < 0 -> BigDecimal("-100")
            else -> BigDecimal.ZERO
        }
    }
    return changeValue
        .divide(startValue.abs(), 6, RoundingMode.HALF_UP)
        .multiply(BigDecimal("100"))
        .stripTrailingZeros()
}

private fun String.toBigDecimalOrNullSafe(): BigDecimal? =
    runCatching { BigDecimal(this) }.getOrNull()
