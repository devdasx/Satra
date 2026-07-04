package dev.satra.wallet.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    Box(modifier = modifier.size(56.dp)) {
        Image(
            painter = painterResource(assetIconRes),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.CenterStart),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = CircleShape,
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(networkIconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
