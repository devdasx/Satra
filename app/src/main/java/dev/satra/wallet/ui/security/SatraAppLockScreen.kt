package dev.satra.wallet.ui.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
fun SatraAppLockScreen(
    passcodeLength: Int,
    biometricsEnabled: Boolean,
    settings: SatraSettings,
    resetNonce: Int,
    errorMessage: String?,
    onPasscodeComplete: (String) -> Unit,
    onBiometricClick: () -> Unit,
) {
    val normalizedPasscodeLength = passcodeLength.takeIf { it == 4 || it == 6 } ?: 6
    val hapticFeedback = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }
    var passcode by remember(resetNonce) { mutableStateOf("") }
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
        modifier = Modifier
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
                    text = stringResource(R.string.app_lock_title),
                    color = foreground,
                    fontSize = 26.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.app_lock_body),
                    color = muted,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(26.dp))

                PasscodeDots(
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
            }

            PasscodeKeypad(
                biometricsEnabled = biometricsEnabled,
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
private fun PasscodeDots(
    passcodeLength: Int,
    filledCount: Int,
    foreground: Color,
    faint: Color,
    error: Color,
    isError: Boolean,
) {
    Row(
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
private fun PasscodeKeypad(
    biometricsEnabled: Boolean,
    foreground: Color,
    chip: Color,
    muted: Color,
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricClick: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { digit ->
                    PasscodeDigitKey(
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
            PasscodeIconKey(
                iconRes = R.drawable.ic_satra_id,
                contentDescription = stringResource(R.string.app_lock_keypad_biometric),
                foreground = if (biometricsEnabled) foreground else muted.copy(alpha = 0.3f),
                chip = if (biometricsEnabled) chip else Color.Transparent,
                enabled = biometricsEnabled,
                onClick = onBiometricClick,
                modifier = Modifier.weight(1f),
            )
            PasscodeDigitKey(
                label = "0",
                foreground = foreground,
                chip = chip,
                onClick = { onDigitClick("0") },
                modifier = Modifier.weight(1f),
            )
            PasscodeIconKey(
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
private fun PasscodeDigitKey(
    label: String,
    foreground: Color,
    chip: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PasscodeKeyContainer(
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
private fun PasscodeIconKey(
    iconRes: Int,
    contentDescription: String,
    foreground: Color,
    chip: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PasscodeKeyContainer(
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
private fun PasscodeKeyContainer(
    chip: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) chip else Color.Transparent)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier, contentAlignment = Alignment.Center) {
            content()
        }
    }
}
