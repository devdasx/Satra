package dev.satra.wallet.ui.security

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.satra.wallet.R
import dev.satra.wallet.settings.SatraSettings
import dev.satra.wallet.ui.components.SatraPasscodeScreen

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
    SatraPasscodeScreen(
        passcodeLength = passcodeLength,
        title = stringResource(R.string.app_lock_title),
        body = stringResource(R.string.app_lock_body),
        settings = settings,
        resetNonce = resetNonce,
        errorMessage = errorMessage,
        biometricsEnabled = biometricsEnabled,
        onPasscodeComplete = onPasscodeComplete,
        onBiometricClick = onBiometricClick,
    )
}
