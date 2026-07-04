package dev.satra.wallet.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.satra.wallet.R
import dev.satra.wallet.settings.SatraSettings
import dev.satra.wallet.settings.SatraThemePreference
import dev.satra.wallet.ui.theme.SatraButtonSecondaryBorder
import dev.satra.wallet.ui.theme.SatraTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SatraOnboardingScreen(
    settings: SatraSettings = SatraSettings(),
    appVersion: String = "0.1.0",
    onThemePreferenceChange: (SatraThemePreference) -> Unit = {},
    onHapticsEnabledChange: (Boolean) -> Unit = {},
    onLanguageTagChange: (String) -> Unit = {},
    onCreateWallet: () -> Unit = {},
    onRestoreWallet: () -> Unit = {},
) {
    val pages = remember { onboardingPages }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val hapticFeedback = LocalHapticFeedback.current
    val performHaptic = remember(settings.hapticsEnabled, hapticFeedback) {
        { performSatraHaptic(hapticFeedback, settings.hapticsEnabled) }
    }
    var settingsSheetVisible by rememberSaveable { mutableStateOf(false) }
    var settingsSheetPage by rememberSaveable { mutableStateOf(SettingsSheetPage.Main.name) }

    LaunchedEffect(pagerState, pages.size, settingsSheetVisible) {
        while (!settingsSheetVisible) {
            delay(AUTO_ADVANCE_DELAY_MILLIS)
            if (!pagerState.isScrollInProgress) {
                val nextPage = (pagerState.currentPage + 1) % pages.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

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
                            performHaptic = performHaptic,
                        )
                    },
                    onCreateWallet = onCreateWallet,
                    onRestoreWallet = onRestoreWallet,
                    onSettingsClick = {
                        performHaptic()
                        settingsSheetPage = SettingsSheetPage.Main.name
                        settingsSheetVisible = true
                    },
                    performHaptic = performHaptic,
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
                            performHaptic = performHaptic,
                        )
                    },
                    onCreateWallet = onCreateWallet,
                    onRestoreWallet = onRestoreWallet,
                    onSettingsClick = {
                        performHaptic()
                        settingsSheetPage = SettingsSheetPage.Main.name
                        settingsSheetVisible = true
                    },
                    performHaptic = performHaptic,
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
                            performHaptic = performHaptic,
                        )
                    },
                    onCreateWallet = onCreateWallet,
                    onRestoreWallet = onRestoreWallet,
                    onSettingsClick = {
                        performHaptic()
                        settingsSheetPage = SettingsSheetPage.Main.name
                        settingsSheetVisible = true
                    },
                    performHaptic = performHaptic,
                    screenHeight = screenHeight,
                    compactHeight = compactHeight,
                    scrollFallback = scrollFallback,
                )
            }
        }

        if (settingsSheetVisible) {
            SatraSettingsBottomSheet(
                settings = settings,
                appVersion = appVersion,
                currentPage = SettingsSheetPage.valueOf(settingsSheetPage),
                onPageChange = { page -> settingsSheetPage = page.name },
                onDismissRequest = { settingsSheetVisible = false },
                onThemePreferenceChange = onThemePreferenceChange,
                onHapticsEnabledChange = onHapticsEnabledChange,
                onLanguageTagChange = onLanguageTagChange,
                performHaptic = performHaptic,
            )
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
    onSettingsClick: () -> Unit,
    performHaptic: () -> Unit,
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
            SatraHeader(
                onSettingsClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth(),
            )

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
                performHaptic = performHaptic,
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
    onSettingsClick: () -> Unit,
    performHaptic: () -> Unit,
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
            SatraHeader(
                onSettingsClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth(),
            )

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
                    performHaptic = performHaptic,
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
    performHaptic: () -> Unit,
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
                performHaptic = performHaptic,
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
    performHaptic: () -> Unit,
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
                performHaptic = performHaptic,
                modifier = Modifier
                    .weight(0.92f)
                    .height(copyHeight),
            )
        }
    }
}

@Composable
private fun SatraHeader(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun OnboardingVisual(
    visual: OnboardingArtwork,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val primarySize = (maxHeight * 0.78f).coerceIn(132.dp, 220.dp)

            if (visual.useBrandMark) {
                BrandMark(
                    markRes = visual.primaryIconRes,
                    size = primarySize,
                    contentDescription = stringResource(visual.contentDescriptionRes),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                IconBadge(
                    iconRes = visual.primaryIconRes,
                    size = primarySize,
                    iconColor = colorScheme.primary,
                    contentDescription = stringResource(visual.contentDescriptionRes),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun BrandMark(
    @DrawableRes markRes: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(markRes),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

@Composable
private fun IconBadge(
    @DrawableRes iconRes: Int,
    size: Dp,
    iconColor: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(size * 0.78f),
        )
    }
}

@Composable
private fun OnboardingCopy(
    page: OnboardingPage,
    performHaptic: () -> Unit,
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
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        page.sourcePillLabelRes?.let { labelRes ->
            Spacer(modifier = Modifier.height(18.dp))
            SourceCodePill(
                label = stringResource(labelRes),
                url = stringResource(R.string.onboarding_open_source_url),
                performHaptic = performHaptic,
            )
        }
    }
}

@Composable
private fun SourceCodePill(
    label: String,
    url: String,
    performHaptic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable {
                performHaptic()
                uriHandler.openUri(url)
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_github_invertocat),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
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
    performHaptic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = {
                performHaptic()
                onCreateWallet()
            },
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
                fontWeight = FontWeight.Bold,
            )
        }

        OutlinedButton(
            onClick = {
                performHaptic()
                onRestoreWallet()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            border = BorderStroke(1.dp, SatraButtonSecondaryBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(
                text = stringResource(R.string.onboarding_action_restore_wallet),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        OnboardingFooterLinks(modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun OnboardingFooterLinks(modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
    val footerStyle = MaterialTheme.typography.labelLarge.copy(
        color = bodyColor,
        textAlign = TextAlign.Center,
    )
    val legalStyle = MaterialTheme.typography.labelMedium.copy(
        color = bodyColor,
        textAlign = TextAlign.Center,
    )
    val sourceUrl = stringResource(R.string.onboarding_open_source_url)
    val privacyUrl = stringResource(R.string.onboarding_privacy_policy_url)
    val termsUrl = stringResource(R.string.onboarding_terms_of_use_url)
    val footerText = buildAnnotatedString {
        append(stringResource(R.string.onboarding_footer_non_custodial))
        append(" ")
        appendLink(
            text = stringResource(R.string.onboarding_footer_open_source),
            url = sourceUrl,
            color = linkColor,
        )
        append(". ")
        append(stringResource(R.string.onboarding_footer_multi_chain))
    }
    val legalText = buildAnnotatedString {
        append(stringResource(R.string.onboarding_legal_prefix))
        append(" ")
        appendLink(
            text = stringResource(R.string.onboarding_privacy_policy),
            url = privacyUrl,
            color = linkColor,
        )
        append(" ")
        append(stringResource(R.string.onboarding_legal_connector))
        append(" ")
        appendLink(
            text = stringResource(R.string.onboarding_terms_of_use),
            url = termsUrl,
            color = linkColor,
        )
        append(".")
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = footerText,
            style = footerStyle,
        )
        Text(
            text = legalText,
            style = legalStyle,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatraSettingsBottomSheet(
    settings: SatraSettings,
    appVersion: String,
    currentPage: SettingsSheetPage,
    onPageChange: (SettingsSheetPage) -> Unit,
    onDismissRequest: () -> Unit,
    onThemePreferenceChange: (SatraThemePreference) -> Unit,
    onHapticsEnabledChange: (Boolean) -> Unit,
    onLanguageTagChange: (String) -> Unit,
    performHaptic: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            when (currentPage) {
                SettingsSheetPage.Main -> SettingsMainContent(
                    settings = settings,
                    onThemePreferenceChange = onThemePreferenceChange,
                    onHapticsEnabledChange = onHapticsEnabledChange,
                    onPageChange = onPageChange,
                    performHaptic = performHaptic,
                )

                SettingsSheetPage.Language -> SettingsLanguageContent(
                    settings = settings,
                    onBack = { onPageChange(SettingsSheetPage.Main) },
                    onLanguageTagChange = onLanguageTagChange,
                    performHaptic = performHaptic,
                )

                SettingsSheetPage.Help -> SettingsHelpContent(
                    onBack = { onPageChange(SettingsSheetPage.Main) },
                    performHaptic = performHaptic,
                )

                SettingsSheetPage.About -> SettingsAboutContent(
                    appVersion = appVersion,
                    onBack = { onPageChange(SettingsSheetPage.Main) },
                    performHaptic = performHaptic,
                )
            }
        }
    }
}

@Composable
private fun SettingsMainContent(
    settings: SatraSettings,
    onThemePreferenceChange: (SatraThemePreference) -> Unit,
    onHapticsEnabledChange: (Boolean) -> Unit,
    onPageChange: (SettingsSheetPage) -> Unit,
    performHaptic: () -> Unit,
) {
    SettingsSheetHeader(titleRes = R.string.settings_title)

    SettingsSectionTitle(titleRes = R.string.settings_section_appearance)
    SelectableSettingsRow(
        title = stringResource(R.string.settings_theme_system),
        body = stringResource(R.string.settings_theme_system_body),
        selected = settings.themePreference == SatraThemePreference.System,
        onClick = {
            performHaptic()
            onThemePreferenceChange(SatraThemePreference.System)
        },
    )
    SelectableSettingsRow(
        title = stringResource(R.string.settings_theme_light),
        body = stringResource(R.string.settings_theme_light_body),
        selected = settings.themePreference == SatraThemePreference.Light,
        onClick = {
            performHaptic()
            onThemePreferenceChange(SatraThemePreference.Light)
        },
    )
    SelectableSettingsRow(
        title = stringResource(R.string.settings_theme_dark),
        body = stringResource(R.string.settings_theme_dark_body),
        selected = settings.themePreference == SatraThemePreference.Dark,
        onClick = {
            performHaptic()
            onThemePreferenceChange(SatraThemePreference.Dark)
        },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    SwitchSettingsRow(
        title = stringResource(R.string.settings_haptics_title),
        body = stringResource(R.string.settings_haptics_body),
        checked = settings.hapticsEnabled,
        onCheckedChange = { enabled ->
            performHaptic()
            onHapticsEnabledChange(enabled)
        },
    )

    SettingsNavigationRow(
        title = stringResource(R.string.settings_language_title),
        body = currentLanguageLabel(settings.languageTag),
        onClick = {
            performHaptic()
            onPageChange(SettingsSheetPage.Language)
        },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    SettingsNavigationRow(
        title = stringResource(R.string.settings_help_title),
        body = stringResource(R.string.settings_help_body),
        onClick = {
            performHaptic()
            onPageChange(SettingsSheetPage.Help)
        },
    )
    SettingsNavigationRow(
        title = stringResource(R.string.settings_about_title),
        body = stringResource(R.string.settings_about_body),
        onClick = {
            performHaptic()
            onPageChange(SettingsSheetPage.About)
        },
    )
}

@Composable
private fun SettingsLanguageContent(
    settings: SatraSettings,
    onBack: () -> Unit,
    onLanguageTagChange: (String) -> Unit,
    performHaptic: () -> Unit,
) {
    SettingsSheetHeader(
        titleRes = R.string.settings_language_title,
        onBack = {
            performHaptic()
            onBack()
        },
    )

    supportedLanguageOptions.forEach { option ->
        SelectableSettingsRow(
            title = "${option.flag} ${stringResource(option.labelRes)}",
            body = stringResource(option.countryRes),
            selected = settings.languageTag == option.languageTag,
            onClick = {
                performHaptic()
                onLanguageTagChange(option.languageTag)
            },
        )
    }
}

@Composable
private fun SettingsHelpContent(
    onBack: () -> Unit,
    performHaptic: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val documentationUrl = stringResource(R.string.settings_url_documentation)
    val sourceUrl = stringResource(R.string.onboarding_open_source_url)
    val reportBugUrl = stringResource(R.string.settings_url_report_bug)
    val requestFeatureUrl = stringResource(R.string.settings_url_request_feature)
    val contactUrl = stringResource(R.string.settings_url_contact)

    SettingsSheetHeader(
        titleRes = R.string.settings_help_title,
        onBack = {
            performHaptic()
            onBack()
        },
    )

    SettingsNavigationRow(
        title = stringResource(R.string.settings_help_read_documentation),
        onClick = {
            performHaptic()
            uriHandler.openUri(documentationUrl)
        },
    )
    SettingsNavigationRow(
        title = stringResource(R.string.settings_help_view_source),
        onClick = {
            performHaptic()
            uriHandler.openUri(sourceUrl)
        },
    )
    SettingsNavigationRow(
        title = stringResource(R.string.settings_help_report_bug),
        onClick = {
            performHaptic()
            uriHandler.openUri(reportBugUrl)
        },
    )
    SettingsNavigationRow(
        title = stringResource(R.string.settings_help_request_feature),
        onClick = {
            performHaptic()
            uriHandler.openUri(requestFeatureUrl)
        },
    )
    SettingsNavigationRow(
        title = stringResource(R.string.settings_help_contact_us),
        onClick = {
            performHaptic()
            uriHandler.openUri(contactUrl)
        },
    )
}

@Composable
private fun SettingsAboutContent(
    appVersion: String,
    onBack: () -> Unit,
    performHaptic: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val termsUrl = stringResource(R.string.onboarding_terms_of_use_url)
    val privacyUrl = stringResource(R.string.onboarding_privacy_policy_url)

    SettingsSheetHeader(
        titleRes = R.string.settings_about_title,
        onBack = {
            performHaptic()
            onBack()
        },
    )

    StaticSettingsRow(
        title = stringResource(R.string.settings_about_version),
        body = appVersion,
    )
    SettingsNavigationRow(
        title = stringResource(R.string.onboarding_terms_of_use),
        onClick = {
            performHaptic()
            uriHandler.openUri(termsUrl)
        },
    )
    SettingsNavigationRow(
        title = stringResource(R.string.onboarding_privacy_policy),
        onClick = {
            performHaptic()
            uriHandler.openUri(privacyUrl)
        },
    )
}

@Composable
private fun SettingsSheetHeader(
    @StringRes titleRes: Int,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back_content_description),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsSectionTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

@Composable
private fun SelectableSettingsRow(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchSettingsRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 2.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    body: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            body?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_open_indicator),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StaticSettingsRow(
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun currentLanguageLabel(languageTag: String): String {
    val option = supportedLanguageOptions.firstOrNull { it.languageTag == languageTag }
        ?: supportedLanguageOptions.first()
    return stringResource(option.labelRes)
}

private fun AnnotatedString.Builder.appendLink(
    text: String,
    url: String,
    color: Color,
) {
    withLink(
        LinkAnnotation.Url(
            url = url,
            styles = TextLinkStyles(
                style = SpanStyle(
                    color = color,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                ),
            ),
        ),
    ) {
        append(text)
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
    @StringRes val sourcePillLabelRes: Int? = null,
)

private data class SupportedLanguageOption(
    val languageTag: String,
    @StringRes val labelRes: Int,
    @StringRes val countryRes: Int,
    val flag: String,
)

private const val AUTO_ADVANCE_DELAY_MILLIS = 5_000L

private val onboardingPages = listOf(
    OnboardingPage(
        visual = OnboardingArtwork.SelfCustody,
        eyebrowRes = R.string.onboarding_page_self_custody_eyebrow,
        titleRes = R.string.onboarding_page_self_custody_title,
        bodyRes = R.string.onboarding_page_self_custody_body,
    ),
    OnboardingPage(
        visual = OnboardingArtwork.OpenSource,
        eyebrowRes = R.string.onboarding_page_open_source_eyebrow,
        titleRes = R.string.onboarding_page_open_source_title,
        bodyRes = R.string.onboarding_page_open_source_body,
        sourcePillLabelRes = R.string.onboarding_source_pill_label,
    ),
    OnboardingPage(
        visual = OnboardingArtwork.Import,
        eyebrowRes = R.string.onboarding_page_import_eyebrow,
        titleRes = R.string.onboarding_page_import_title,
        bodyRes = R.string.onboarding_page_import_body,
    ),
    OnboardingPage(
        visual = OnboardingArtwork.Assets,
        eyebrowRes = R.string.onboarding_page_assets_eyebrow,
        titleRes = R.string.onboarding_page_assets_title,
        bodyRes = R.string.onboarding_page_assets_body,
    ),
    OnboardingPage(
        visual = OnboardingArtwork.Receive,
        eyebrowRes = R.string.onboarding_page_receive_eyebrow,
        titleRes = R.string.onboarding_page_receive_title,
        bodyRes = R.string.onboarding_page_receive_body,
    ),
    OnboardingPage(
        visual = OnboardingArtwork.Clarity,
        eyebrowRes = R.string.onboarding_page_clarity_eyebrow,
        titleRes = R.string.onboarding_page_clarity_title,
        bodyRes = R.string.onboarding_page_clarity_body,
    ),
    OnboardingPage(
        visual = OnboardingArtwork.Scan,
        eyebrowRes = R.string.onboarding_page_scan_eyebrow,
        titleRes = R.string.onboarding_page_scan_title,
        bodyRes = R.string.onboarding_page_scan_body,
    ),
    OnboardingPage(
        visual = OnboardingArtwork.History,
        eyebrowRes = R.string.onboarding_page_history_eyebrow,
        titleRes = R.string.onboarding_page_history_title,
        bodyRes = R.string.onboarding_page_history_body,
    ),
    OnboardingPage(
        visual = OnboardingArtwork.Move,
        eyebrowRes = R.string.onboarding_page_move_eyebrow,
        titleRes = R.string.onboarding_page_move_title,
        bodyRes = R.string.onboarding_page_move_body,
    ),
)

private val supportedLanguageOptions = listOf(
    SupportedLanguageOption("en", R.string.settings_language_english, R.string.settings_country_united_states, "🇺🇸"),
    SupportedLanguageOption("zh-Hans", R.string.settings_language_chinese_simplified, R.string.settings_country_china, "🇨🇳"),
    SupportedLanguageOption("hi", R.string.settings_language_hindi, R.string.settings_country_india, "🇮🇳"),
    SupportedLanguageOption("es", R.string.settings_language_spanish, R.string.settings_country_spain, "🇪🇸"),
    SupportedLanguageOption("fr", R.string.settings_language_french, R.string.settings_country_france, "🇫🇷"),
    SupportedLanguageOption("ar", R.string.settings_language_arabic, R.string.settings_country_saudi_arabia, "🇸🇦"),
    SupportedLanguageOption("bn", R.string.settings_language_bengali, R.string.settings_country_bangladesh, "🇧🇩"),
    SupportedLanguageOption("pt", R.string.settings_language_portuguese, R.string.settings_country_brazil, "🇧🇷"),
    SupportedLanguageOption("ru", R.string.settings_language_russian, R.string.settings_country_russia, "🇷🇺"),
    SupportedLanguageOption("ur", R.string.settings_language_urdu, R.string.settings_country_pakistan, "🇵🇰"),
    SupportedLanguageOption("id", R.string.settings_language_indonesian, R.string.settings_country_indonesia, "🇮🇩"),
    SupportedLanguageOption("de", R.string.settings_language_german, R.string.settings_country_germany, "🇩🇪"),
    SupportedLanguageOption("ja", R.string.settings_language_japanese, R.string.settings_country_japan, "🇯🇵"),
    SupportedLanguageOption("sw", R.string.settings_language_swahili, R.string.settings_country_tanzania, "🇹🇿"),
    SupportedLanguageOption("mr", R.string.settings_language_marathi, R.string.settings_country_india, "🇮🇳"),
    SupportedLanguageOption("te", R.string.settings_language_telugu, R.string.settings_country_india, "🇮🇳"),
    SupportedLanguageOption("tr", R.string.settings_language_turkish, R.string.settings_country_turkey, "🇹🇷"),
    SupportedLanguageOption("ta", R.string.settings_language_tamil, R.string.settings_country_india, "🇮🇳"),
    SupportedLanguageOption("vi", R.string.settings_language_vietnamese, R.string.settings_country_vietnam, "🇻🇳"),
    SupportedLanguageOption("ko", R.string.settings_language_korean, R.string.settings_country_south_korea, "🇰🇷"),
    SupportedLanguageOption("fa", R.string.settings_language_persian, R.string.settings_country_iran, "🇮🇷"),
    SupportedLanguageOption("it", R.string.settings_language_italian, R.string.settings_country_italy, "🇮🇹"),
    SupportedLanguageOption("th", R.string.settings_language_thai, R.string.settings_country_thailand, "🇹🇭"),
    SupportedLanguageOption("gu", R.string.settings_language_gujarati, R.string.settings_country_india, "🇮🇳"),
    SupportedLanguageOption("pl", R.string.settings_language_polish, R.string.settings_country_poland, "🇵🇱"),
)

private enum class OnboardingArtwork(
    @DrawableRes val primaryIconRes: Int,
    @StringRes val contentDescriptionRes: Int,
    val useBrandMark: Boolean = false,
) {
    SelfCustody(
        primaryIconRes = R.drawable.ic_brand_security,
        contentDescriptionRes = R.string.onboarding_visual_self_custody_description,
    ),
    Import(
        primaryIconRes = R.drawable.ic_brand_wallet,
        contentDescriptionRes = R.string.onboarding_visual_import_description,
    ),
    Assets(
        primaryIconRes = R.drawable.satra_mark,
        contentDescriptionRes = R.string.onboarding_visual_assets_description,
        useBrandMark = true,
    ),
    Receive(
        primaryIconRes = R.drawable.ic_brand_receive,
        contentDescriptionRes = R.string.onboarding_visual_receive_description,
    ),
    Clarity(
        primaryIconRes = R.drawable.ic_brand_list,
        contentDescriptionRes = R.string.onboarding_visual_clarity_description,
    ),
    Scan(
        primaryIconRes = R.drawable.ic_brand_scan,
        contentDescriptionRes = R.string.onboarding_visual_scan_description,
    ),
    History(
        primaryIconRes = R.drawable.ic_brand_history,
        contentDescriptionRes = R.string.onboarding_visual_history_description,
    ),
    Move(
        primaryIconRes = R.drawable.ic_brand_move,
        contentDescriptionRes = R.string.onboarding_visual_move_description,
    ),
    OpenSource(
        primaryIconRes = R.drawable.ic_brand_settings,
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

private enum class SettingsSheetPage {
    Main,
    Language,
    Help,
    About,
}

private fun performSatraHaptic(
    hapticFeedback: HapticFeedback,
    enabled: Boolean,
) {
    if (enabled) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
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
