package dev.satra.wallet.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun SatraCryptoIcon(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
internal fun SatraAssetNetworkIcon(
    assetSymbol: String,
    networkId: String,
    modifier: Modifier = Modifier,
) {
    SatraAssetNetworkIcon(
        assetIconRes = assetIconRes(assetSymbol),
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
    SatraBadgedIcon(
        primaryIconRes = assetIconRes,
        badgeIconRes = networkIconRes,
        modifier = modifier,
    )
}

@Composable
internal fun SatraBadgedIcon(
    @DrawableRes primaryIconRes: Int,
    @DrawableRes badgeIconRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(48.dp)) {
        SatraCryptoIcon(
            iconRes = primaryIconRes,
            modifier = Modifier
                .size(42.dp)
                .align(Alignment.CenterStart),
        )
        SatraCryptoIcon(
            iconRes = badgeIconRes,
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.BottomEnd),
            backgroundColor = MaterialTheme.colorScheme.surface,
        )
    }
}
