package dev.satra.wallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.satra.wallet.ui.theme.SatraButtonSecondaryBorder

enum class SatraButtonVariant {
    Primary,
    Secondary,
    Danger,
    Neutral,
    Text,
}

object SatraButtonDefaults {
    val Height = 56.dp
    val CompactHeight = 48.dp
    val Shape = RoundedCornerShape(100.dp)
}

@Composable
fun SatraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: SatraButtonVariant = SatraButtonVariant.Primary,
    height: Dp = SatraButtonDefaults.Height,
    containerColor: Color? = null,
    contentColor: Color? = null,
    disabledContainerColor: Color? = null,
    disabledContentColor: Color? = null,
    borderColor: Color? = null,
) {
    SatraButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        variant = variant,
        height = height,
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = disabledContainerColor,
        disabledContentColor = disabledContentColor,
        borderColor = borderColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun SatraButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: SatraButtonVariant = SatraButtonVariant.Primary,
    height: Dp = SatraButtonDefaults.Height,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    containerColor: Color? = null,
    contentColor: Color? = null,
    disabledContainerColor: Color? = null,
    disabledContentColor: Color? = null,
    borderColor: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val effectiveEnabled = enabled && !loading
    val sizedModifier = modifier.height(height)

    when (variant) {
        SatraButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            enabled = effectiveEnabled,
            modifier = sizedModifier,
            shape = SatraButtonDefaults.Shape,
            border = BorderStroke(
                width = 1.dp,
                color = borderColor ?: if (effectiveEnabled) {
                    SatraButtonSecondaryBorder
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                },
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = contentColor ?: MaterialTheme.colorScheme.onSurface,
                disabledContentColor = disabledContentColor
                    ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
            ),
            contentPadding = contentPadding,
            content = content,
        )

        SatraButtonVariant.Text -> TextButton(
            onClick = onClick,
            enabled = effectiveEnabled,
            modifier = sizedModifier,
            shape = SatraButtonDefaults.Shape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = contentColor ?: MaterialTheme.colorScheme.onSurface,
                disabledContentColor = disabledContentColor
                    ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
            ),
            contentPadding = contentPadding,
            content = content,
        )

        else -> Button(
            onClick = onClick,
            enabled = effectiveEnabled,
            modifier = sizedModifier,
            shape = SatraButtonDefaults.Shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor ?: buttonContainerColor(variant),
                contentColor = contentColor ?: buttonContentColor(variant),
                disabledContainerColor = disabledContainerColor ?: disabledButtonContainerColor(variant),
                disabledContentColor = disabledContentColor
                    ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            ),
            contentPadding = contentPadding,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = contentColor ?: buttonContentColor(variant),
                )
            } else {
                content()
            }
        }
    }
}

@Composable
private fun buttonContainerColor(variant: SatraButtonVariant): Color =
    when (variant) {
        SatraButtonVariant.Danger -> MaterialTheme.colorScheme.error
        SatraButtonVariant.Neutral -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.primary
    }

@Composable
private fun buttonContentColor(variant: SatraButtonVariant): Color =
    when (variant) {
        SatraButtonVariant.Danger -> MaterialTheme.colorScheme.onError
        SatraButtonVariant.Neutral -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onPrimary
    }

@Composable
private fun disabledButtonContainerColor(variant: SatraButtonVariant): Color =
    when (variant) {
        SatraButtonVariant.Danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
        SatraButtonVariant.Neutral -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    }
