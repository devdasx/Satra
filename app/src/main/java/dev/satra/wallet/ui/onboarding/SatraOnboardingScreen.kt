package dev.satra.wallet.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SatraMark()
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            text = stringResource(R.string.wallet_scope_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SatraMark(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val strokeWidth = 3.dp.toPx()
            drawCircle(
                color = colorScheme.onPrimary,
                radius = size.minDimension * 0.28f,
                center = Offset(size.width * 0.38f, size.height * 0.38f),
                style = Stroke(width = strokeWidth),
            )
            drawLine(
                color = colorScheme.onPrimary,
                start = Offset(size.width * 0.56f, size.height * 0.56f),
                end = Offset(size.width * 0.86f, size.height * 0.86f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colorScheme.onPrimary,
                start = Offset(size.width * 0.74f, size.height * 0.72f),
                end = Offset(size.width * 0.88f, size.height * 0.58f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun OnboardingVisual(
    visual: OnboardingArtwork,
    modifier: Modifier = Modifier,
) {
    when (visual) {
        OnboardingArtwork.SelfCustody -> SelfCustodyArtwork(modifier = modifier)
        OnboardingArtwork.Clarity -> ClarityArtwork(modifier = modifier)
        OnboardingArtwork.OpenSource -> OpenSourceArtwork(modifier = modifier)
    }
}

@Composable
private fun SelfCustodyArtwork(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val grid = 32.dp.toPx()
            val stroke = 1.dp.toPx()

            var x = -grid
            while (x <= size.width + grid) {
                drawLine(
                    color = colorScheme.outlineVariant.copy(alpha = 0.16f),
                    start = Offset(x, 0f),
                    end = Offset(x + grid, size.height),
                    strokeWidth = stroke,
                )
                x += grid
            }

            val cardWidth = size.width * 0.78f
            val cardHeight = size.height * 0.62f
            val cardLeft = (size.width - cardWidth) / 2f
            val cardTop = size.height * 0.16f
            val corner = 28.dp.toPx()

            drawRoundRect(
                color = colorScheme.primaryContainer.copy(alpha = 0.72f),
                topLeft = Offset(cardLeft + 14.dp.toPx(), cardTop - 12.dp.toPx()),
                size = Size(cardWidth, cardHeight),
                cornerRadius = CornerRadius(corner, corner),
            )
            drawRoundRect(
                color = colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
                topLeft = Offset(cardLeft, cardTop),
                size = Size(cardWidth, cardHeight),
                cornerRadius = CornerRadius(corner, corner),
            )
            drawRoundRect(
                color = colorScheme.outlineVariant.copy(alpha = 0.56f),
                topLeft = Offset(cardLeft, cardTop),
                size = Size(cardWidth, cardHeight),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = 1.dp.toPx()),
            )

            val path = Path().apply {
                moveTo(cardLeft + cardWidth * 0.16f, cardTop + cardHeight * 0.68f)
                cubicTo(
                    cardLeft + cardWidth * 0.34f,
                    cardTop + cardHeight * 0.28f,
                    cardLeft + cardWidth * 0.58f,
                    cardTop + cardHeight * 0.84f,
                    cardLeft + cardWidth * 0.84f,
                    cardTop + cardHeight * 0.36f,
                )
            }
            drawPath(
                path = path,
                color = colorScheme.tertiary.copy(alpha = 0.34f),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )

            drawCircle(
                color = colorScheme.secondary.copy(alpha = 0.16f),
                radius = cardHeight * 0.34f,
                center = Offset(cardLeft + cardWidth * 0.72f, cardTop + cardHeight * 0.42f),
            )
        }

        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(CircleShape)
                .background(colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.wallet_symbol),
                style = MaterialTheme.typography.displayMedium,
                color = colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-6).dp)
                .fillMaxWidth(0.74f)
                .widthIn(max = 320.dp),
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.42f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.onboarding_receive_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_receive_address_preview),
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(18.dp)) {
                        drawLine(
                            color = colorScheme.onSecondaryContainer,
                            start = Offset(size.width * 0.2f, size.height * 0.5f),
                            end = Offset(size.width * 0.8f, size.height * 0.5f),
                            strokeWidth = 2.6.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = colorScheme.onSecondaryContainer,
                            start = Offset(size.width * 0.54f, size.height * 0.22f),
                            end = Offset(size.width * 0.82f, size.height * 0.5f),
                            strokeWidth = 2.6.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = colorScheme.onSecondaryContainer,
                            start = Offset(size.width * 0.54f, size.height * 0.78f),
                            end = Offset(size.width * 0.82f, size.height * 0.5f),
                            strokeWidth = 2.6.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClarityArtwork(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val grid = 34.dp.toPx()
            val stroke = 1.dp.toPx()

            var x = -grid
            while (x <= size.width + grid) {
                drawLine(
                    color = colorScheme.outlineVariant.copy(alpha = 0.14f),
                    start = Offset(x, size.height),
                    end = Offset(x + grid, 0f),
                    strokeWidth = stroke,
                )
                x += grid
            }

            val panelWidth = size.width * 0.76f
            val panelHeight = size.height * 0.72f
            val panelLeft = (size.width - panelWidth) / 2f
            val panelTop = size.height * 0.1f
            val corner = 24.dp.toPx()

            drawRoundRect(
                color = colorScheme.secondaryContainer.copy(alpha = 0.58f),
                topLeft = Offset(panelLeft - 16.dp.toPx(), panelTop + 18.dp.toPx()),
                size = Size(panelWidth, panelHeight),
                cornerRadius = CornerRadius(corner, corner),
            )
            drawRoundRect(
                color = colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
                topLeft = Offset(panelLeft, panelTop),
                size = Size(panelWidth, panelHeight),
                cornerRadius = CornerRadius(corner, corner),
            )
            drawRoundRect(
                color = colorScheme.outlineVariant.copy(alpha = 0.48f),
                topLeft = Offset(panelLeft, panelTop),
                size = Size(panelWidth, panelHeight),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = 1.dp.toPx()),
            )

            val route = Path().apply {
                moveTo(panelLeft + panelWidth * 0.18f, panelTop + panelHeight * 0.68f)
                cubicTo(
                    panelLeft + panelWidth * 0.34f,
                    panelTop + panelHeight * 0.34f,
                    panelLeft + panelWidth * 0.62f,
                    panelTop + panelHeight * 0.82f,
                    panelLeft + panelWidth * 0.82f,
                    panelTop + panelHeight * 0.32f,
                )
            }
            drawPath(
                path = route,
                color = colorScheme.primary.copy(alpha = 0.32f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
            )

            repeat(3) { row ->
                val rowTop = panelTop + panelHeight * (0.18f + row * 0.2f)
                val dotCenter = Offset(panelLeft + panelWidth * 0.2f, rowTop + 12.dp.toPx())
                val rowColor = when (row) {
                    0 -> colorScheme.primary
                    1 -> colorScheme.tertiary
                    else -> colorScheme.secondary
                }

                drawCircle(
                    color = rowColor.copy(alpha = 0.18f),
                    radius = 16.dp.toPx(),
                    center = dotCenter,
                )
                drawCircle(
                    color = rowColor,
                    radius = 6.dp.toPx(),
                    center = dotCenter,
                )
                drawRoundRect(
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.24f),
                    topLeft = Offset(panelLeft + panelWidth * 0.32f, rowTop),
                    size = Size(panelWidth * 0.36f, 7.dp.toPx()),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                )
                drawRoundRect(
                    color = colorScheme.outlineVariant.copy(alpha = 0.62f),
                    topLeft = Offset(panelLeft + panelWidth * 0.32f, rowTop + 16.dp.toPx()),
                    size = Size(panelWidth * (0.26f + row * 0.05f), 6.dp.toPx()),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                )
            }

            val badgeCenter = Offset(panelLeft + panelWidth * 0.82f, panelTop + panelHeight * 0.7f)
            drawCircle(
                color = colorScheme.primary,
                radius = 34.dp.toPx(),
                center = badgeCenter,
            )
            drawLine(
                color = colorScheme.onPrimary,
                start = Offset(badgeCenter.x - 14.dp.toPx(), badgeCenter.y),
                end = Offset(badgeCenter.x - 3.dp.toPx(), badgeCenter.y + 10.dp.toPx()),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colorScheme.onPrimary,
                start = Offset(badgeCenter.x - 3.dp.toPx(), badgeCenter.y + 10.dp.toPx()),
                end = Offset(badgeCenter.x + 16.dp.toPx(), badgeCenter.y - 14.dp.toPx()),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun OpenSourceArtwork(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val grid = 40.dp.toPx()
            val stroke = 1.dp.toPx()

            var y = -grid
            while (y <= size.height + grid) {
                drawLine(
                    color = colorScheme.outlineVariant.copy(alpha = 0.13f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y + grid),
                    strokeWidth = stroke,
                )
                y += grid
            }

            val panelWidth = size.width * 0.78f
            val panelHeight = size.height * 0.66f
            val panelLeft = (size.width - panelWidth) / 2f
            val panelTop = size.height * 0.15f
            val corner = 22.dp.toPx()

            drawRoundRect(
                color = colorScheme.tertiaryContainer.copy(alpha = 0.58f),
                topLeft = Offset(panelLeft + 18.dp.toPx(), panelTop - 14.dp.toPx()),
                size = Size(panelWidth, panelHeight),
                cornerRadius = CornerRadius(corner, corner),
            )
            drawRoundRect(
                color = colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
                topLeft = Offset(panelLeft, panelTop),
                size = Size(panelWidth, panelHeight),
                cornerRadius = CornerRadius(corner, corner),
            )
            drawRoundRect(
                color = colorScheme.outlineVariant.copy(alpha = 0.5f),
                topLeft = Offset(panelLeft, panelTop),
                size = Size(panelWidth, panelHeight),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = 1.dp.toPx()),
            )

            repeat(3) { index ->
                drawCircle(
                    color = when (index) {
                        0 -> colorScheme.primary
                        1 -> colorScheme.tertiary
                        else -> colorScheme.secondary
                    }.copy(alpha = 0.72f),
                    radius = 5.dp.toPx(),
                    center = Offset(
                        x = panelLeft + 24.dp.toPx() + index * 16.dp.toPx(),
                        y = panelTop + 24.dp.toPx(),
                    ),
                )
            }

            repeat(5) { row ->
                val rowTop = panelTop + 58.dp.toPx() + row * 24.dp.toPx()
                val indent = if (row % 2 == 0) 0.dp.toPx() else 24.dp.toPx()
                val widthFraction = when (row) {
                    0 -> 0.5f
                    1 -> 0.34f
                    2 -> 0.58f
                    3 -> 0.42f
                    else -> 0.28f
                }

                drawRoundRect(
                    color = colorScheme.primary.copy(alpha = if (row == 2) 0.38f else 0.18f),
                    topLeft = Offset(panelLeft + 30.dp.toPx() + indent, rowTop),
                    size = Size(panelWidth * widthFraction, 8.dp.toPx()),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                )
            }

            val leftNode = Offset(panelLeft + panelWidth * 0.25f, panelTop + panelHeight * 0.82f)
            val centerNode = Offset(panelLeft + panelWidth * 0.5f, panelTop + panelHeight * 0.68f)
            val rightNode = Offset(panelLeft + panelWidth * 0.75f, panelTop + panelHeight * 0.82f)

            drawLine(
                color = colorScheme.secondary.copy(alpha = 0.34f),
                start = leftNode,
                end = centerNode,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colorScheme.secondary.copy(alpha = 0.34f),
                start = centerNode,
                end = rightNode,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )

            listOf(leftNode, centerNode, rightNode).forEachIndexed { index, node ->
                drawCircle(
                    color = if (index == 1) colorScheme.primary else colorScheme.secondaryContainer,
                    radius = if (index == 1) 22.dp.toPx() else 14.dp.toPx(),
                    center = node,
                )
            }
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.wallet_symbol),
                style = MaterialTheme.typography.headlineLarge,
                color = colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
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

private enum class OnboardingArtwork {
    SelfCustody,
    Clarity,
    OpenSource,
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
