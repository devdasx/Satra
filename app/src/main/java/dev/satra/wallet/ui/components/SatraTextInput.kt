package dev.satra.wallet.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun satraDoneKeyboardActions(): KeyboardActions {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return KeyboardActions(
        onDone = {
            keyboardController?.hide()
            focusManager.clearFocus()
        },
    )
}

fun satraDoneKeyboardOptions(
    keyboardType: KeyboardType = KeyboardType.Text,
): KeyboardOptions = KeyboardOptions(
    keyboardType = keyboardType,
    imeAction = ImeAction.Done,
)

fun String.satraSingleLineInput(
    newlineReplacement: String = " ",
): String = replace(Regex("[\\r\\n]+"), newlineReplacement)
