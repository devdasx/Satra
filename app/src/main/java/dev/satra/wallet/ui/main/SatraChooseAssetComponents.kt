package dev.satra.wallet.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satra.wallet.R

@Composable
internal fun SatraChooseAssetScaffold(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SatraFlowTopBar(
            title = title,
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
internal fun SatraFlowTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = ChooseAssetContentMaxWidth)
            .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.wallet_setup_back_content_description),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ChooseAssetSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = ChooseAssetContentMaxWidth)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Normal,
        ),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(18.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (query.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

@Composable
internal fun ChooseAssetSectionHeader(title: String) {
    val currentLocale = LocalLocale.current.platformLocale
    Text(
        text = title.uppercase(currentLocale),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = ChooseAssetContentMaxWidth)
            .padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun ChooseAssetRow(
    symbol: String,
    name: String,
    networkCount: Int,
    primaryAmount: String,
    secondaryAmount: String,
    showSecondaryAmount: Boolean,
    @DrawableRes iconRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = ChooseAssetContentMaxWidth)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SatraCryptoIcon(
            iconRes = iconRes,
            modifier = Modifier.size(42.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (networkCount > 1) {
                    Spacer(modifier = Modifier.width(7.dp))
                    NetworkCountPill(networkCount = networkCount)
                }
            }
            Text(
                text = name,
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.widthIn(min = 92.dp, max = 132.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = primaryAmount,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
            if (showSecondaryAmount) {
                Text(
                    text = secondaryAmount,
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
internal fun ChooseAssetEmptySearchNote() {
    Text(
        text = stringResource(R.string.send_asset_empty_search_body),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = ChooseAssetContentMaxWidth)
            .padding(top = 12.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, lineHeight = 18.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun ChooseNetworkContextLine(
    symbol: String,
    networkCount: Int,
    @DrawableRes iconRes: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = ChooseAssetContentMaxWidth)
            .padding(start = 2.dp, end = 2.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SatraCryptoIcon(
            iconRes = iconRes,
            modifier = Modifier.size(42.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.send_network_context, symbol, networkCount),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun ChooseNetworkRow(
    networkName: String,
    standard: String,
    primaryAmount: String,
    secondaryAmount: String,
    @DrawableRes iconRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = ChooseAssetContentMaxWidth)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SatraCryptoIcon(
            iconRes = iconRes,
            modifier = Modifier.size(42.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = networkName,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = standard,
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.widthIn(min = 92.dp, max = 132.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = primaryAmount,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
            Text(
                text = secondaryAmount,
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun NetworkCountPill(networkCount: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.send_asset_network_count, networkCount),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

internal val ChooseAssetContentMaxWidth = 420.dp
