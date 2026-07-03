package dev.satra.wallet.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class HomeAssetFilterModelTest {
    @Test
    fun defaultSortOrdersByLocalValueThenAmount() {
        val result = listOf(
            asset(assetId = "zero", value = "0", amount = "999", name = "Zero"),
            asset(assetId = "small", value = "10", amount = "20", name = "Small"),
            asset(assetId = "large", value = "100", amount = "1", name = "Large"),
            asset(assetId = "tie-large-amount", value = "100", amount = "5", name = "Tie"),
        ).applyHomeAssetFilter(HomeAssetFilterState())

        assertEquals(
            listOf("tie-large-amount", "large", "small", "zero"),
            result.map { it.assetId },
        )
    }

    @Test
    fun balanceAndNetworkFiltersCanBeCombined() {
        val result = listOf(
            asset(assetId = "eth-zero", networkId = "ethereum", value = "0", amount = "0"),
            asset(assetId = "eth-usdc", networkId = "ethereum", value = "25", amount = "25"),
            asset(assetId = "base-usdc", networkId = "base", value = "50", amount = "50"),
        ).applyHomeAssetFilter(
            HomeAssetFilterState(
                networkId = "ethereum",
                onlyWithBalance = true,
            ),
        )

        assertEquals(listOf("eth-usdc"), result.map { it.assetId })
    }

    @Test
    fun amountSortUsesTokenAmountBeforeValue() {
        val result = listOf(
            asset(assetId = "high-value", value = "500", amount = "1"),
            asset(assetId = "high-amount", value = "20", amount = "100"),
        ).applyHomeAssetFilter(
            HomeAssetFilterState(sortOption = HomeAssetSortOption.Amount),
        )

        assertEquals(listOf("high-amount", "high-value"), result.map { it.assetId })
    }

    @Test
    fun nameSortUsesAssetNameBeforeValue() {
        val result = listOf(
            asset(assetId = "zeta", value = "500", amount = "1", name = "Zeta"),
            asset(assetId = "alpha", value = "1", amount = "1", name = "Alpha"),
        ).applyHomeAssetFilter(
            HomeAssetFilterState(sortOption = HomeAssetSortOption.Name),
        )

        assertEquals(listOf("alpha", "zeta"), result.map { it.assetId })
    }

    private fun asset(
        assetId: String,
        networkId: String = "ethereum",
        value: String,
        amount: String,
        name: String = assetId,
    ): HomeAssetRow {
        val amountValue = BigDecimal(amount)
        val fiatValueAmount = BigDecimal(value)
        return HomeAssetRow(
            assetId = assetId,
            networkId = networkId,
            iconRes = 0,
            symbol = assetId.uppercase(),
            name = name,
            network = networkId,
            amount = amount,
            amountValue = amountValue,
            fiatValue = value,
            fiatValueAmount = fiatValueAmount,
            hasBalance = amountValue > BigDecimal.ZERO,
            isNative = false,
        )
    }
}
