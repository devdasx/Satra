package dev.satra.wallet.ui.onboarding

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
        val compact = screenHeight < 740.dp
        val artworkHeight = if (compact) 218.dp else 292.dp

        Box(modifier = Modifier.fillMaxSize()) {
            AmbientLedgerBackground()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = screenHeight)
                    .verticalScroll(rememberScrollState())
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                SatraHeader(modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(if (compact) 16.dp else 28.dp))

                WalletArtwork(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(artworkHeight),
                )

                Spacer(modifier = Modifier.height(if (compact) 18.dp else 26.dp))

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 164.dp),
                    pageSpacing = 18.dp,
                ) { page ->
                    OnboardingCopy(page = pages[page])
                }

                PagerDots(
                    selectedPage = pagerState.currentPage,
                    count = pages.size,
                    modifier = Modifier.padding(top = 10.dp, bottom = if (compact) 18.dp else 26.dp),
                )

                OnboardingActions(
                    onCreateWallet = onCreateWallet,
                    onRestoreWallet = onRestoreWallet,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                text = "Satra",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            text = "Bitcoin",
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
private fun WalletArtwork(modifier: Modifier = Modifier) {
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
                text = "₿",
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
                        text = "Receive",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "bc1q...satra",
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
private fun OnboardingCopy(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = page.eyebrow,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = page.body,
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
                text = "Create wallet",
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
                text = "Restore wallet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            text = "No account. Local keys. Open source.",
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
    val eyebrow: String,
    val title: String,
    val body: String,
)

private val onboardingPages = listOf(
    OnboardingPage(
        eyebrow = "Self-custody",
        title = "Your bitcoin stays yours.",
        body = "Create a wallet where recovery, signing, and privacy are designed around your device.",
    ),
    OnboardingPage(
        eyebrow = "Clarity first",
        title = "Know before you send.",
        body = "Review addresses, network fees, and activity in a calm flow before any transaction moves.",
    ),
    OnboardingPage(
        eyebrow = "Open source",
        title = "Built in the open.",
        body = "Satra starts public from day one, with every design and code change visible as the wallet grows.",
    ),
)

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SatraOnboardingPreview() {
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
