package dev.satra.wallet.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.satra.wallet.R
import dev.satra.wallet.ui.theme.SatraTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SatraOnboardingScreen(
    onCreateWallet: () -> Unit = {},
    onRestoreWallet: () -> Unit = {},
) {
    val pages = remember { onboardingPages }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val screenHeight = maxHeight
        val windowSize = remember(maxWidth) { OnboardingWindowSize.from(maxWidth) }
        val compactHeight = screenHeight < 820.dp
        val scrollFallback = screenHeight < 620.dp

        Box(modifier = Modifier.fillMaxSize()) {
            AmbientLedgerBackground()

            when (windowSize) {
                OnboardingWindowSize.Compact -> OnboardingSinglePaneLayout(
                    pages = pages,
                    selectedPage = pagerState.currentPage,
                    pageContent = {
                        OnboardingPagedContent(
                            pages = pages,
                            pagerState = pagerState,
                            artworkHeight = if (compactHeight) 170.dp else 232.dp,
                            copyHeight = if (compactHeight) 204.dp else 236.dp,
                            visualCopyGap = if (compactHeight) 8.dp else 14.dp,
                            pageSpacing = 18.dp,
                        )
                    },
                    onCreateWallet = onCreateWallet,
                    onRestoreWallet = onRestoreWallet,
                    screenHeight = screenHeight,
                    contentMaxWidth = 460.dp,
                    horizontalPadding = 24.dp,
                    compactHeight = compactHeight,
                    scrollFallback = scrollFallback,
                )

                OnboardingWindowSize.Medium -> OnboardingSinglePaneLayout(
                    pages = pages,
                    selectedPage = pagerState.currentPage,
                    pageContent = {
                        OnboardingPagedContent(
                            pages = pages,
                            pagerState = pagerState,
                            artworkHeight = if (compactHeight) 218.dp else 286.dp,
                            copyHeight = if (compactHeight) 204.dp else 224.dp,
                            visualCopyGap = 18.dp,
                            pageSpacing = 22.dp,
                        )
                    },
                    onCreateWallet = onCreateWallet,
                    onRestoreWallet = onRestoreWallet,
                    screenHeight = screenHeight,
                    contentMaxWidth = 560.dp,
                    horizontalPadding = 48.dp,
                    compactHeight = compactHeight,
                    scrollFallback = scrollFallback,
                )

                OnboardingWindowSize.Expanded -> OnboardingExpandedLayout(
                    pages = pages,
                    selectedPage = pagerState.currentPage,
                    pageContent = {
                        OnboardingExpandedPagedContent(
                            pages = pages,
                            pagerState = pagerState,
                            artworkHeight = if (compactHeight) 320.dp else 400.dp,
                            copyHeight = if (compactHeight) 216.dp else 244.dp,
                            pageSpacing = 24.dp,
                        )
                    },
                    onCreateWallet = onCreateWallet,
                    onRestoreWallet = onRestoreWallet,
                    screenHeight = screenHeight,
                    compactHeight = compactHeight,
                    scrollFallback = scrollFallback,
                )
            }
        }
    }
}

@Composable
private fun OnboardingSinglePaneLayout(
    pages: List<OnboardingPage>,
    selectedPage: Int,
    pageContent: @Composable () -> Unit,
    onCreateWallet: () -> Unit,
    onRestoreWallet: () -> Unit,
    screenHeight: Dp,
    contentMaxWidth: Dp,
    horizontalPadding: Dp,
    compactHeight: Boolean,
    scrollFallback: Boolean,
) {
    val verticalPadding = if (compactHeight) 14.dp else 20.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .then(if (scrollFallback) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = contentMaxWidth)
                .fillMaxWidth()
                .then(if (scrollFallback) Modifier.heightIn(min = screenHeight) else Modifier.fillMaxSize()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SatraHeader(modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(if (compactHeight) 10.dp else 18.dp))

            pageContent()

            PagerDots(
                selectedPage = selectedPage,
                count = pages.size,
                modifier = Modifier.padding(top = 8.dp, bottom = if (compactHeight) 12.dp else 18.dp),
            )

            if (scrollFallback) {
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            OnboardingActions(
                onCreateWallet = onCreateWallet,
                onRestoreWallet = onRestoreWallet,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OnboardingExpandedLayout(
    pages: List<OnboardingPage>,
    selectedPage: Int,
    pageContent: @Composable () -> Unit,
    onCreateWallet: () -> Unit,
    onRestoreWallet: () -> Unit,
    screenHeight: Dp,
    compactHeight: Boolean,
    scrollFallback: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 72.dp, vertical = if (compactHeight) 20.dp else 28.dp)
            .then(if (scrollFallback) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 1180.dp)
                .fillMaxWidth()
                .then(if (scrollFallback) Modifier.heightIn(min = screenHeight) else Modifier.fillMaxSize()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SatraHeader(modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(if (compactHeight) 28.dp else 44.dp))

            pageContent()

            PagerDots(
                selectedPage = selectedPage,
                count = pages.size,
                modifier = Modifier.padding(top = 20.dp, bottom = if (compactHeight) 20.dp else 30.dp),
            )

            if (scrollFallback) {
                Spacer(modifier = Modifier.height(14.dp))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                OnboardingActions(
                    onCreateWallet = onCreateWallet,
                    onRestoreWallet = onRestoreWallet,
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingPagedContent(
    pages: List<OnboardingPage>,
    pagerState: PagerState,
    artworkHeight: Dp,
    copyHeight: Dp,
    visualCopyGap: Dp,
    pageSpacing: Dp,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(artworkHeight + visualCopyGap + copyHeight),
        pageSpacing = pageSpacing,
    ) { pageIndex ->
        val page = pages[pageIndex]

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingVisual(
                visual = page.visual,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(artworkHeight),
            )

            Spacer(modifier = Modifier.height(visualCopyGap))

            OnboardingCopy(
                page = page,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(copyHeight),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingExpandedPagedContent(
    pages: List<OnboardingPage>,
    pagerState: PagerState,
    artworkHeight: Dp,
    copyHeight: Dp,
    pageSpacing: Dp,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(artworkHeight),
        pageSpacing = pageSpacing,
    ) { pageIndex ->
        val page = pages[pageIndex]

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(64.dp),
        ) {
            OnboardingVisual(
                visual = page.visual,
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxSize(),
            )

            OnboardingCopy(
                page = page,
                modifier = Modifier
                    .weight(0.92f)
                    .height(copyHeight),
            )
        }
    }
}

@Composable
private fun SatraHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Image(
            painter = painterResource(R.drawable.satra_lockup_horizontal),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(132.dp)
                .height(48.dp),
        )

        Text(
            text = stringResource(R.string.wallet_scope_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OnboardingVisual(
    visual: OnboardingArtwork,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colorScheme.surfaceContainer)
            .border(1.dp, colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val grid = 42.dp.toPx()
            var y = -grid
            while (y < size.height + grid) {
                drawLine(
                    color = colorScheme.outlineVariant.copy(alpha = 0.22f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
                y += grid
            }

            val tile = size.minDimension * 0.34f
            val left = center.x - tile * 1.05f
            val top = center.y - tile * 0.9f

            drawArc(
                color = colorScheme.primary.copy(alpha = 0.06f),
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = true,
                topLeft = Offset(left, top),
                size = Size(tile, tile),
            )
            drawRoundRect(
                color = colorScheme.primary.copy(alpha = 0.04f),
                topLeft = Offset(left + tile * 1.18f, top),
                size = Size(tile * 0.72f, tile * 0.72f),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            )
            drawLine(
                color = colorScheme.outlineVariant.copy(alpha = 0.36f),
                start = Offset(size.width * 0.2f, center.y),
                end = Offset(size.width * 0.8f, center.y),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val primarySize = (maxHeight * 0.48f).coerceIn(96.dp, 156.dp)
            val supportSize = (primarySize * 0.38f).coerceIn(42.dp, 58.dp)

            IconBadge(
                iconRes = visual.leadingIconRes,
                size = supportSize,
                containerColor = colorScheme.surfaceContainerHigh,
                iconColor = colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = maxWidth * 0.16f)
                    .offset(y = -(primarySize * 0.24f)),
            )

            IconBadge(
                iconRes = visual.trailingIconRes,
                size = supportSize,
                containerColor = colorScheme.surfaceContainerHigh,
                iconColor = colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = maxWidth * 0.16f)
                    .offset(y = primarySize * 0.24f),
            )

            IconBadge(
                iconRes = visual.primaryIconRes,
                size = primarySize,
                containerColor = colorScheme.primary,
                iconColor = colorScheme.onPrimary,
                contentDescription = stringResource(visual.contentDescriptionRes),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun IconBadge(
    @DrawableRes iconRes: Int,
    size: Dp,
    containerColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
private fun OnboardingCopy(
    page: OnboardingPage,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(page.eyebrowRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PagerDots(
    selectedPage: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == selectedPage
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(if (selected) 22.dp else 8.dp)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun OnboardingActions(
    onCreateWallet: () -> Unit,
    onRestoreWallet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onCreateWallet,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(
                text = stringResource(R.string.onboarding_action_create_wallet),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        OutlinedButton(
            onClick = onRestoreWallet,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(
                text = stringResource(R.string.onboarding_action_restore_wallet),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            text = stringResource(R.string.onboarding_footer),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun AmbientLedgerBackground(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme

    Canvas(modifier = modifier.fillMaxSize()) {
        val step = 48.dp.toPx()
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = colorScheme.outlineVariant.copy(alpha = 0.14f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
            y += step
        }
    }
}

private data class OnboardingPage(
    val visual: OnboardingArtwork,
    @StringRes val eyebrowRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

private val onboardingPages = listOf(
    OnboardingPage(
        visual = OnboardingArtwork.SelfCustody,
        eyebrowRes = R.string.onboarding_page_self_custody_eyebrow,
        titleRes = R.string.onboarding_page_self_custody_title,
        bodyRes = R.string.onboarding_page_self_custody_body,
    ),
    OnboardingPage(
        visual = OnboardingArtwork.Clarity,
        eyebrowRes = R.string.onboarding_page_clarity_eyebrow,
        titleRes = R.string.onboarding_page_clarity_title,
        bodyRes = R.string.onboarding_page_clarity_body,
    ),
    OnboardingPage(
        visual = OnboardingArtwork.OpenSource,
        eyebrowRes = R.string.onboarding_page_open_source_eyebrow,
        titleRes = R.string.onboarding_page_open_source_title,
        bodyRes = R.string.onboarding_page_open_source_body,
    ),
)

private enum class OnboardingArtwork(
    @DrawableRes val primaryIconRes: Int,
    @DrawableRes val leadingIconRes: Int,
    @DrawableRes val trailingIconRes: Int,
    @StringRes val contentDescriptionRes: Int,
) {
    SelfCustody(
        primaryIconRes = R.drawable.ic_brand_security,
        leadingIconRes = R.drawable.ic_brand_wallet,
        trailingIconRes = R.drawable.ic_brand_assets,
        contentDescriptionRes = R.string.onboarding_visual_self_custody_description,
    ),
    Clarity(
        primaryIconRes = R.drawable.ic_brand_list,
        leadingIconRes = R.drawable.ic_brand_receive,
        trailingIconRes = R.drawable.ic_brand_move,
        contentDescriptionRes = R.string.onboarding_visual_clarity_description,
    ),
    OpenSource(
        primaryIconRes = R.drawable.ic_brand_settings,
        leadingIconRes = R.drawable.ic_brand_history,
        trailingIconRes = R.drawable.ic_brand_scan,
        contentDescriptionRes = R.string.onboarding_visual_open_source_description,
    ),
}

private enum class OnboardingWindowSize {
    Compact,
    Medium,
    Expanded;

    companion object {
        fun from(width: Dp): OnboardingWindowSize = when {
            width >= 840.dp -> Expanded
            width >= 600.dp -> Medium
            else -> Compact
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SatraOnboardingPreview() {
    SatraTheme(dynamicColor = false) {
        SatraOnboardingScreen()
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
private fun SatraOnboardingTabletPreview() {
    SatraTheme(dynamicColor = false) {
        SatraOnboardingScreen()
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun SatraOnboardingExpandedPreview() {
    SatraTheme(dynamicColor = false) {
        SatraOnboardingScreen()
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SatraOnboardingDarkPreview() {
    SatraTheme(
        darkTheme = true,
        dynamicColor = false,
    ) {
        SatraOnboardingScreen()
    }
}
