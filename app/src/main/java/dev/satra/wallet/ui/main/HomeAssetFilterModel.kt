package dev.satra.wallet.ui.main

internal enum class HomeAssetSortOption {
    Value,
    Amount,
    Name,
}

internal data class HomeAssetFilterState(
    val networkId: String? = null,
    val onlyWithBalance: Boolean = false,
    val sortOption: HomeAssetSortOption = HomeAssetSortOption.Value,
) {
    val isActive: Boolean
        get() = networkId != null || onlyWithBalance || sortOption != HomeAssetSortOption.Value
}

internal fun List<HomeAssetRow>.applyHomeAssetFilter(
    filterState: HomeAssetFilterState,
): List<HomeAssetRow> =
    asSequence()
        .filter { asset ->
            filterState.networkId == null || asset.networkId == filterState.networkId
        }
        .filter { asset ->
            !filterState.onlyWithBalance || asset.hasBalance
        }
        .toList()
        .sortedWith(filterState.sortOption.comparator())

private fun HomeAssetSortOption.comparator(): Comparator<HomeAssetRow> =
    when (this) {
        HomeAssetSortOption.Value -> compareByDescending<HomeAssetRow> { it.fiatValueAmount }
            .thenByDescending { it.amountValue }
            .thenBy { it.name.lowercase() }
            .thenBy { it.network.lowercase() }

        HomeAssetSortOption.Amount -> compareByDescending<HomeAssetRow> { it.amountValue }
            .thenByDescending { it.fiatValueAmount }
            .thenBy { it.name.lowercase() }
            .thenBy { it.network.lowercase() }

        HomeAssetSortOption.Name -> compareBy<HomeAssetRow> { it.name.lowercase() }
            .thenBy { it.network.lowercase() }
            .thenByDescending { it.fiatValueAmount }
            .thenByDescending { it.amountValue }
    }
