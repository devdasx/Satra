package dev.satra.wallet.ui.setup

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.satra.wallet.R
import dev.satra.wallet.settings.SatraSettings
import dev.satra.wallet.ui.theme.SatraButtonSecondaryBorder
import dev.satra.wallet.ui.theme.SatraTheme

enum class WalletSetupMode {
    Create,
    Import,
}

@Composable
fun SatraWalletSetupScreen(
    mode: WalletSetupMode,
    stepIndex: Int = 0,
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onNextStep: (Int) -> Unit = {},
) {
    val steps = remember(mode) { if (mode == WalletSetupMode.Create) createWalletSteps else importWalletSteps }
    val currentStepIndex = stepIndex.coerceIn(0, steps.lastIndex)
    val hapticFeedback = LocalHapticFeedback.current
    val performHaptic = remember(settings.hapticsEnabled, hapticFeedback) {
        { performSetupHaptic(hapticFeedback, settings.hapticsEnabled) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val windowSize = remember(maxWidth) { SetupWindowSize.from(maxWidth) }
        val compactHeight = maxHeight < 780.dp
        val scrollFallback = maxHeight < 640.dp
        val contentMaxWidth = when (windowSize) {
            SetupWindowSize.Compact -> 520.dp
            SetupWindowSize.Medium -> 640.dp
            SetupWindowSize.Expanded -> 1120.dp
        }

        SetupBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(
                    horizontal = when (windowSize) {
                        SetupWindowSize.Compact -> 24.dp
                        SetupWindowSize.Medium -> 48.dp
                        SetupWindowSize.Expanded -> 72.dp
                    },
                    vertical = if (compactHeight) 16.dp else 24.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxSize()
                    .then(if (scrollFallback) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            ) {
                SetupTopBar(
                    stepIndex = currentStepIndex,
                    stepCount = steps.size,
                    onBack = {
                        performHaptic()
                        onBack()
                    },
                )

                Spacer(modifier = Modifier.height(if (compactHeight) 12.dp else 18.dp))

                SetupProgress(
                    stepIndex = currentStepIndex,
                    stepCount = steps.size,
                )

                Spacer(modifier = Modifier.height(if (compactHeight) 16.dp else 24.dp))

                SetupContentFrame(
                    mode = mode,
                    step = steps[currentStepIndex],
                    stepIndex = currentStepIndex,
                    windowSize = windowSize,
                    compactHeight = compactHeight,
                    performHaptic = performHaptic,
                    modifier = if (scrollFallback) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    },
                )

                Spacer(modifier = Modifier.height(if (compactHeight) 14.dp else 22.dp))

                SetupActions(
                    mode = mode,
                    stepIndex = currentStepIndex,
                    stepCount = steps.size,
                    onPrimaryClick = {
                        performHaptic()
                        if (currentStepIndex < steps.lastIndex) {
                            onNextStep(currentStepIndex + 1)
                        }
                    },
                    onSecondaryClick = {
                        performHaptic()
                        onBack()
                    },
                )
            }
        }
    }
}

@Composable
private fun SetupTopBar(
    stepIndex: Int,
    stepCount: Int,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.wallet_setup_back_content_description),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Image(
            painter = painterResource(R.drawable.satra_lockup_horizontal),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            alignment = Alignment.CenterStart,
        )

        Text(
            text = stringResource(R.string.wallet_setup_step_count, stepIndex + 1, stepCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SetupProgress(
    stepIndex: Int,
    stepCount: Int,
) {
    LinearProgressIndicator(
        progress = { (stepIndex + 1).toFloat() / stepCount.toFloat() },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SetupContentFrame(
    mode: WalletSetupMode,
    step: SetupStep,
    stepIndex: Int,
    windowSize: SetupWindowSize,
    compactHeight: Boolean,
    performHaptic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (windowSize) {
        SetupWindowSize.Expanded -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(56.dp),
        ) {
            SetupHero(
                step = step,
                compactHeight = compactHeight,
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
            )
            SetupStepBody(
                mode = mode,
                stepIndex = stepIndex,
                step = step,
                performHaptic = performHaptic,
                modifier = Modifier.weight(1.1f),
            )
        }

        SetupWindowSize.Compact,
        SetupWindowSize.Medium -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SetupHero(
                step = step,
                compactHeight = compactHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compactHeight) 112.dp else 148.dp),
            )

            Spacer(modifier = Modifier.height(if (compactHeight) 10.dp else 16.dp))

            SetupStepBody(
                mode = mode,
                stepIndex = stepIndex,
                step = step,
                performHaptic = performHaptic,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SetupHero(
    step: SetupStep,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val iconSize = if (compactHeight) 112.dp else 156.dp
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(step.iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(iconSize * 0.52f),
            )
        }
    }
}

@Composable
private fun SetupStepBody(
    mode: WalletSetupMode,
    stepIndex: Int,
    step: SetupStep,
    performHaptic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(step.eyebrowRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(step.titleRes),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(step.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(20.dp))

        when (mode) {
            WalletSetupMode.Create -> CreateWalletStepContent(
                stepIndex = stepIndex,
                performHaptic = performHaptic,
            )

            WalletSetupMode.Import -> ImportWalletStepContent(
                stepIndex = stepIndex,
                performHaptic = performHaptic,
            )
        }
    }
}

@Composable
private fun CreateWalletStepContent(
    stepIndex: Int,
    performHaptic: () -> Unit,
) {
    when (stepIndex) {
        0 -> TrustPillGrid(
            labels = listOf(
                R.string.wallet_setup_create_chip_on_device,
                R.string.wallet_setup_create_chip_non_custodial,
                R.string.wallet_setup_create_chip_multi_chain,
            ),
        )

        1 -> HiddenPhrasePanel()

        2 -> BackupChecklist(performHaptic = performHaptic)

        else -> CreateReviewPanel(performHaptic = performHaptic)
    }
}

@Composable
private fun ImportWalletStepContent(
    stepIndex: Int,
    performHaptic: () -> Unit,
) {
    when (stepIndex) {
        0 -> ImportMethodPanel(performHaptic = performHaptic)
        1 -> RecoveryPhraseEntry()
        2 -> ImportReviewPanel()
        else -> ImportSecurityPanel(performHaptic = performHaptic)
    }
}

@Composable
private fun TrustPillGrid(
    labels: List<Int>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { labelRes ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HiddenPhrasePanel() {
    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { rowIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(3) { columnIndex ->
                        val wordNumber = rowIndex * 3 + columnIndex + 1
                        RecoveryWordChip(
                            number = wordNumber,
                            text = stringResource(R.string.wallet_setup_hidden_word_placeholder),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupChecklist(performHaptic: () -> Unit) {
    var privatePlaceChecked by rememberSaveable { mutableStateOf(false) }
    var noScreenshotChecked by rememberSaveable { mutableStateOf(false) }
    var restoreChecked by rememberSaveable { mutableStateOf(false) }

    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SetupCheckboxRow(
                labelRes = R.string.wallet_setup_backup_private_place,
                checked = privatePlaceChecked,
                onCheckedChange = {
                    performHaptic()
                    privatePlaceChecked = it
                },
            )
            SetupCheckboxRow(
                labelRes = R.string.wallet_setup_backup_no_screenshots,
                checked = noScreenshotChecked,
                onCheckedChange = {
                    performHaptic()
                    noScreenshotChecked = it
                },
            )
            SetupCheckboxRow(
                labelRes = R.string.wallet_setup_backup_restore_check,
                checked = restoreChecked,
                onCheckedChange = {
                    performHaptic()
                    restoreChecked = it
                },
            )
        }
    }
}

@Composable
private fun CreateReviewPanel(performHaptic: () -> Unit) {
    var deviceLockEnabled by rememberSaveable { mutableStateOf(true) }
    var hideBalancesEnabled by rememberSaveable { mutableStateOf(false) }

    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SetupSwitchRow(
                titleRes = R.string.wallet_setup_security_device_lock,
                bodyRes = R.string.wallet_setup_security_device_lock_body,
                checked = deviceLockEnabled,
                onCheckedChange = {
                    performHaptic()
                    deviceLockEnabled = it
                },
            )
            HorizontalDivider()
            SetupSwitchRow(
                titleRes = R.string.wallet_setup_security_hide_balances,
                bodyRes = R.string.wallet_setup_security_hide_balances_body,
                checked = hideBalancesEnabled,
                onCheckedChange = {
                    performHaptic()
                    hideBalancesEnabled = it
                },
            )
        }
    }
}

@Composable
private fun ImportMethodPanel(performHaptic: () -> Unit) {
    var selectedMethod by rememberSaveable { mutableStateOf(ImportMethod.RecoveryPhrase) }

    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ImportMethodRow(
                titleRes = R.string.wallet_setup_import_method_recovery_phrase,
                bodyRes = R.string.wallet_setup_import_method_recovery_phrase_body,
                selected = selectedMethod == ImportMethod.RecoveryPhrase,
                onClick = {
                    performHaptic()
                    selectedMethod = ImportMethod.RecoveryPhrase
                },
            )
            ImportMethodRow(
                titleRes = R.string.wallet_setup_import_method_view_only,
                bodyRes = R.string.wallet_setup_import_method_view_only_body,
                selected = selectedMethod == ImportMethod.ViewOnly,
                onClick = {
                    performHaptic()
                    selectedMethod = ImportMethod.ViewOnly
                },
            )
        }
    }
}

@Composable
private fun RecoveryPhraseEntry() {
    var recoveryPhrase by rememberSaveable { mutableStateOf("") }
    var phraseLength by rememberSaveable { mutableIntStateOf(12) }

    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = phraseLength == 12,
                    onClick = { phraseLength = 12 },
                    label = { Text(text = stringResource(R.string.wallet_setup_phrase_length_12)) },
                )
                FilterChip(
                    selected = phraseLength == 24,
                    onClick = { phraseLength = 24 },
                    label = { Text(text = stringResource(R.string.wallet_setup_phrase_length_24)) },
                )
            }

            OutlinedTextField(
                value = recoveryPhrase,
                onValueChange = { recoveryPhrase = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = {
                    Text(text = stringResource(R.string.wallet_setup_recovery_phrase_placeholder))
                },
                label = {
                    Text(text = stringResource(R.string.wallet_setup_recovery_phrase_label))
                },
                minLines = 4,
            )
        }
    }
}

@Composable
private fun ImportReviewPanel() {
    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReviewRow(
                titleRes = R.string.wallet_setup_review_source,
                valueRes = R.string.wallet_setup_review_source_recovery_phrase,
            )
            ReviewRow(
                titleRes = R.string.wallet_setup_review_networks,
                valueRes = R.string.wallet_setup_review_networks_supported,
            )
            ReviewRow(
                titleRes = R.string.wallet_setup_review_privacy,
                valueRes = R.string.wallet_setup_review_privacy_local,
            )
        }
    }
}

@Composable
private fun ImportSecurityPanel(performHaptic: () -> Unit) {
    var scanAccountsEnabled by rememberSaveable { mutableStateOf(true) }
    var labelImportedWallet by rememberSaveable { mutableStateOf(true) }

    FramedTool {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SetupSwitchRow(
                titleRes = R.string.wallet_setup_import_scan_accounts,
                bodyRes = R.string.wallet_setup_import_scan_accounts_body,
                checked = scanAccountsEnabled,
                onCheckedChange = {
                    performHaptic()
                    scanAccountsEnabled = it
                },
            )
            HorizontalDivider()
            SetupSwitchRow(
                titleRes = R.string.wallet_setup_import_label_wallet,
                bodyRes = R.string.wallet_setup_import_label_wallet_body,
                checked = labelImportedWallet,
                onCheckedChange = {
                    performHaptic()
                    labelImportedWallet = it
                },
            )
        }
    }
}

@Composable
private fun FramedTool(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun RecoveryWordChip(
    number: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SetupCheckboxRow(
    @StringRes labelRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SetupSwitchRow(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyLarge,
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
private fun ImportMethodRow(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewRow(
    @StringRes titleRes: Int,
    @StringRes valueRes: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(valueRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SetupActions(
    mode: WalletSetupMode,
    stepIndex: Int,
    stepCount: Int,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
) {
    val isLastStep = stepIndex == stepCount - 1
    val primaryTextRes = when {
        !isLastStep -> R.string.wallet_setup_action_continue
        mode == WalletSetupMode.Create -> R.string.wallet_setup_action_create_wallet
        else -> R.string.wallet_setup_action_import_wallet
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = onPrimaryClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = stringResource(primaryTextRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        OutlinedButton(
            onClick = onSecondaryClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            border = BorderStroke(1.dp, SatraButtonSecondaryBorder),
        ) {
            Text(
                text = stringResource(
                    if (stepIndex == 0) {
                        R.string.wallet_setup_action_cancel
                    } else {
                        R.string.wallet_setup_action_previous
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SetupBackground(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme

    Canvas(modifier = modifier.fillMaxSize()) {
        val step = 48.dp.toPx()
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = colorScheme.outlineVariant.copy(alpha = 0.12f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
            y += step
        }
    }
}

private data class SetupStep(
    @StringRes val eyebrowRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @DrawableRes val iconRes: Int,
)

private val createWalletSteps = listOf(
    SetupStep(
        eyebrowRes = R.string.wallet_setup_create_step_intro_eyebrow,
        titleRes = R.string.wallet_setup_create_step_intro_title,
        bodyRes = R.string.wallet_setup_create_step_intro_body,
        iconRes = R.drawable.ic_brand_wallet,
    ),
    SetupStep(
        eyebrowRes = R.string.wallet_setup_create_step_phrase_eyebrow,
        titleRes = R.string.wallet_setup_create_step_phrase_title,
        bodyRes = R.string.wallet_setup_create_step_phrase_body,
        iconRes = R.drawable.ic_brand_security,
    ),
    SetupStep(
        eyebrowRes = R.string.wallet_setup_create_step_backup_eyebrow,
        titleRes = R.string.wallet_setup_create_step_backup_title,
        bodyRes = R.string.wallet_setup_create_step_backup_body,
        iconRes = R.drawable.ic_brand_list,
    ),
    SetupStep(
        eyebrowRes = R.string.wallet_setup_create_step_security_eyebrow,
        titleRes = R.string.wallet_setup_create_step_security_title,
        bodyRes = R.string.wallet_setup_create_step_security_body,
        iconRes = R.drawable.ic_brand_settings,
    ),
)

private val importWalletSteps = listOf(
    SetupStep(
        eyebrowRes = R.string.wallet_setup_import_step_method_eyebrow,
        titleRes = R.string.wallet_setup_import_step_method_title,
        bodyRes = R.string.wallet_setup_import_step_method_body,
        iconRes = R.drawable.ic_brand_receive,
    ),
    SetupStep(
        eyebrowRes = R.string.wallet_setup_import_step_phrase_eyebrow,
        titleRes = R.string.wallet_setup_import_step_phrase_title,
        bodyRes = R.string.wallet_setup_import_step_phrase_body,
        iconRes = R.drawable.ic_brand_wallet,
    ),
    SetupStep(
        eyebrowRes = R.string.wallet_setup_import_step_review_eyebrow,
        titleRes = R.string.wallet_setup_import_step_review_title,
        bodyRes = R.string.wallet_setup_import_step_review_body,
        iconRes = R.drawable.ic_brand_assets,
    ),
    SetupStep(
        eyebrowRes = R.string.wallet_setup_import_step_security_eyebrow,
        titleRes = R.string.wallet_setup_import_step_security_title,
        bodyRes = R.string.wallet_setup_import_step_security_body,
        iconRes = R.drawable.ic_brand_settings,
    ),
)

private enum class ImportMethod {
    RecoveryPhrase,
    ViewOnly,
}

private enum class SetupWindowSize {
    Compact,
    Medium,
    Expanded;

    companion object {
        fun from(width: Dp): SetupWindowSize = when {
            width >= 840.dp -> Expanded
            width >= 600.dp -> Medium
            else -> Compact
        }
    }
}

private fun performSetupHaptic(
    hapticFeedback: HapticFeedback,
    enabled: Boolean,
) {
    if (enabled) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun CreateWalletSetupPreview() {
    SatraTheme {
        SatraWalletSetupScreen(mode = WalletSetupMode.Create)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ImportWalletSetupPreview() {
    SatraTheme {
        SatraWalletSetupScreen(mode = WalletSetupMode.Import)
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun ExpandedCreateWalletSetupPreview() {
    SatraTheme {
        SatraWalletSetupScreen(mode = WalletSetupMode.Create)
    }
}
