package dev.satra.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satra.wallet.R
import dev.satra.wallet.settings.SatraSettings
import kotlinx.coroutines.delay

@Composable
fun SatraPasscodeScreen(
    passcodeLength: Int,
    title: String,
    body: String,
    settings: SatraSettings,
    modifier: Modifier = Modifier,
    resetNonce: Int = 0,
    errorMessage: String? = null,
    biometricsEnabled: Boolean = false,
    biometricIconEnabled: Boolean = biometricsEnabled,
    onPasscodeComplete: (String) -> Unit,
    onBiometricClick: () -> Unit = {},
    middleContent: @Composable ColumnScope.() -> Unit = {},
) {
    val normalizedPasscodeLength = passcodeLength.takeIf { it == 4 || it == 6 } ?: 6
    val hapticFeedback = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }
    var passcode by remember(resetNonce, normalizedPasscodeLength) { mutableStateOf("") }
    var displayedError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resetNonce) {
        if (resetNonce > 0 && errorMessage != null) {
            displayedError = errorMessage
            if (settings.hapticsEnabled) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            listOf(-9f, 9f, -6f, 6f, 0f).forEach { target ->
                shakeOffset.animateTo(target, animationSpec = tween(durationMillis = 42))
            }
            delay(80)
            passcode = ""
        }
    }

    LaunchedEffect(passcode, normalizedPasscodeLength) {
        if (passcode.length == normalizedPasscodeLength) {
            onPasscodeComplete(passcode)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        val horizontalPadding = if (maxWidth < 600.dp) 24.dp else 56.dp
        val contentWidth = if (maxWidth < 600.dp) 420.dp else 480.dp
        val foreground = MaterialTheme.colorScheme.onSurface
        val muted = foreground.copy(alpha = 0.55f)
        val faint = foreground.copy(alpha = 0.16f)
        val chip = foreground.copy(alpha = 0.06f)
        val error = MaterialTheme.colorScheme.error
        val visibleError = displayedError

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)
                .widthIn(max = contentWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shakeOffset.value },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.satra_mark),
                    contentDescription = null,
                    modifier = Modifier.size(46.dp),
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = title,
                    color = foreground,
                    fontSize = 26.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = body,
                    color = muted,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(26.dp))

                SatraPasscodeDots(
                    passcodeLength = normalizedPasscodeLength,
                    filledCount = passcode.length,
                    foreground = foreground,
                    faint = faint,
                    error = error,
                    isError = visibleError != null,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .padding(top = 14.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (visibleError != null) {
                        Text(
                            text = visibleError,
                            color = error,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                middleContent()
            }

            SatraPasscodeKeypad(
                biometricsEnabled = biometricsEnabled,
                biometricIconEnabled = biometricIconEnabled,
                foreground = foreground,
                chip = chip,
                muted = muted,
                onDigitClick = { digit ->
                    displayedError = null
                    if (passcode.length < normalizedPasscodeLength) {
                        passcode += digit
                    }
                },
                onBackspaceClick = {
                    displayedError = null
                    passcode = passcode.dropLast(1)
                },
                onBiometricClick = onBiometricClick,
            )

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
fun SatraPasscodeDots(
    passcodeLength: Int,
    filledCount: Int,
    modifier: Modifier = Modifier,
    foreground: Color = MaterialTheme.colorScheme.onSurface,
    faint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
    error: Color = MaterialTheme.colorScheme.error,
    isError: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(passcodeLength) { index ->
            val filled = index < filledCount
            val dotColor = if (isError) error else foreground
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (filled) dotColor else Color.Transparent)
                    .border(
                        width = 2.dp,
                        color = if (filled) dotColor else if (isError) error.copy(alpha = 0.5f) else faint,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
fun SatraPasscodeKeypad(
    biometricsEnabled: Boolean,
    modifier: Modifier = Modifier,
    biometricIconEnabled: Boolean = biometricsEnabled,
    foreground: Color = MaterialTheme.colorScheme.onSurface,
    chip: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    muted: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricClick: () -> Unit = {},
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { digit ->
                    SatraPasscodeDigitKey(
                        label = digit,
                        foreground = foreground,
                        chip = chip,
                        onClick = { onDigitClick(digit) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SatraPasscodeIconKey(
                iconRes = R.drawable.ic_satra_id,
                contentDescription = stringResource(R.string.app_lock_keypad_biometric),
                foreground = if (biometricIconEnabled) foreground else muted.copy(alpha = 0.3f),
                chip = if (biometricsEnabled) chip else Color.Transparent,
                enabled = biometricsEnabled,
                onClick = onBiometricClick,
                modifier = Modifier.weight(1f),
            )
            SatraPasscodeDigitKey(
                label = "0",
                foreground = foreground,
                chip = chip,
                onClick = { onDigitClick("0") },
                modifier = Modifier.weight(1f),
            )
            SatraPasscodeIconKey(
                iconRes = R.drawable.ic_satra_backspace,
                contentDescription = stringResource(R.string.app_lock_keypad_backspace),
                foreground = foreground,
                chip = chip,
                enabled = true,
                onClick = onBackspaceClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SatraPasscodeDigitKey(
    label: String,
    foreground: Color,
    chip: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SatraPasscodeKeyContainer(
        chip = chip,
        onClick = onClick,
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = foreground,
            fontSize = 25.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SatraPasscodeIconKey(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    foreground: Color,
    chip: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SatraPasscodeKeyContainer(
        chip = chip,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = foreground,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SatraPasscodeKeyContainer(
    chip: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled && isPressed) chip else Color.Transparent)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
