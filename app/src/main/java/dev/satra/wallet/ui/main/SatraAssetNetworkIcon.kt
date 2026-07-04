package dev.satra.wallet.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun SatraAssetNetworkIcon(
    assetSymbol: String,
    networkId: String,
    modifier: Modifier = Modifier,
) {
    SatraAssetNetworkIcon(
        assetIconRes = assetIconRes(assetSymbol, networkId),
        networkIconRes = networkIconRes(networkId),
        modifier = modifier,
    )
}

@Composable
internal fun SatraAssetNetworkIcon(
    @DrawableRes assetIconRes: Int,
    @DrawableRes networkIconRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(48.dp)) {
        Image(
            painter = painterResource(assetIconRes),
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .align(Alignment.CenterStart),
        )
        Image(
            painter = painterResource(networkIconRes),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.BottomEnd),
        )
    }
}
