package dev.satra.wallet.ui.main

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.satra.wallet.R
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.db.AddressBookEntryRecord
import dev.satra.wallet.data.db.AppSettingsRecord
import dev.satra.wallet.data.db.AppSettingsUpdate
import dev.satra.wallet.data.db.DEFAULT_LOCAL_CURRENCY_CODE
import dev.satra.wallet.data.db.NewAddressBookEntryRecord
import dev.satra.wallet.data.db.SatraWalletRepository
import dev.satra.wallet.settings.SatraSettings
import dev.satra.wallet.settings.SatraSettingsDefaults
import dev.satra.wallet.settings.SatraThemePreference
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun SatraSettingsRootScreen(
    walletRepository: SatraWalletRepository,
    localCurrencyCode: String,
    onNavigate: (String) -> Unit,
) {
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    LaunchedEffect(walletRepository, localCurrencyCode) {
        appSettings = walletRepository.getAppSettings()
    }

    SettingsScaffold(titleRes = R.string.settings_screen_title) {
        item {
            SettingsHeroCard(
                titleRes = R.string.settings_screen_title,
                bodyRes = R.string.settings_screen_body,
                iconRes = R.drawable.ic_brand_settings,
            )
        }
        item { SettingsSectionTitle(R.string.settings_section_wallet) }
        item {
            SettingsCard {
                SettingsRow(
                    iconRes = R.drawable.ic_brand_receive,
                    title = stringResource(R.string.settings_address_book_title),
                    body = stringResource(R.string.settings_address_book_body),
                    onClick = { onNavigate(SatraMainRoute.AddressBook) },
                )
            }
        }
        item { SettingsSectionTitle(R.string.settings_section_app) }
        item {
            SettingsCard {
                SettingsRow(
                    iconRes = R.drawable.ic_brand_settings,
                    title = stringResource(R.string.settings_preferences_title),
                    body = appSettings?.localCurrencyCode ?: stringResource(R.string.settings_preferences_body),
                    onClick = { onNavigate(SatraMainRoute.Preferences) },
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_security,
                    title = stringResource(R.string.settings_security_title),
                    body = if (appSettings?.passcodeEnabled == true) {
                        stringResource(R.string.settings_security_body_enabled)
                    } else {
                        stringResource(R.string.settings_security_body_disabled)
                    },
                    onClick = { onNavigate(SatraMainRoute.Security) },
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_history,
                    title = stringResource(R.string.settings_notifications_title),
                    body = stringResource(R.string.settings_notifications_body),
                    onClick = { onNavigate(SatraMainRoute.Notifications) },
                )
            }
        }
        item { SettingsSectionTitle(R.string.settings_section_info) }
        item {
            SettingsCard {
                SettingsRow(
                    iconRes = R.drawable.ic_brand_wallet,
                    title = stringResource(R.string.settings_about_title),
                    body = stringResource(R.string.settings_about_body),
                    onClick = { onNavigate(SatraMainRoute.About) },
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_list,
                    title = stringResource(R.string.settings_legal_title),
                    body = stringResource(R.string.settings_legal_body),
                    onClick = { onNavigate(SatraMainRoute.Legal) },
                )
            }
        }
        item { SettingsSectionTitle(R.string.settings_section_danger) }
        item {
            SettingsCard {
                SettingsRow(
                    iconRes = R.drawable.ic_brand_empty,
                    title = stringResource(R.string.settings_danger_title),
                    body = stringResource(R.string.settings_danger_body),
                    onClick = { onNavigate(SatraMainRoute.DangerZone) },
                    isDanger = true,
                )
            }
        }
    }
}

@Composable
internal fun SatraAddressBookScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<AddressBookEntryRecord>()) }
    var editingEntry by remember { mutableStateOf<AddressBookEntryRecord?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch { entries = walletRepository.getAddressBookEntries() }
    }

    LaunchedEffect(walletRepository) {
        entries = walletRepository.getAddressBookEntries()
    }

    SettingsScaffold(
        titleRes = R.string.settings_address_book_title,
        onBack = onBack,
    ) {
        item {
            SettingsHeroCard(
                titleRes = R.string.settings_address_book_title,
                bodyRes = R.string.settings_address_book_body,
                iconRes = R.drawable.ic_brand_receive,
            )
        }
        item {
            Button(
                onClick = {
                    editingEntry = null
                    showEditor = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(100.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_address_book_add),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (entries.isEmpty()) {
            item {
                SettingsEmptyCard(
                    titleRes = R.string.settings_address_book_empty_title,
                    bodyRes = R.string.settings_address_book_empty_body,
                )
            }
        } else {
            items(entries, key = { it.entryId }) { entry ->
                SettingsCard {
                    AddressBookEntryRow(
                        entry = entry,
                        onEdit = {
                            editingEntry = entry
                            showEditor = true
                        },
                        onDelete = {
                            scope.launch {
                                walletRepository.deleteAddressBookEntry(entry.entryId)
                                reload()
                            }
                        },
                    )
                }
            }
        }
    }

    if (showEditor) {
        AddressBookEditorSheet(
            entry = editingEntry,
            onDismiss = { showEditor = false },
            onSave = { newEntry ->
                scope.launch {
                    walletRepository.upsertAddressBookEntry(newEntry, editingEntry?.entryId)
                    showEditor = false
                    reload()
                }
            },
        )
    }
}

@Composable
internal fun SatraPreferencesScreen(
    walletRepository: SatraWalletRepository,
    settings: SatraSettings,
    localCurrencyCode: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onHapticsEnabledChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    LaunchedEffect(walletRepository, localCurrencyCode) {
        appSettings = walletRepository.getAppSettings()
    }

    SettingsScaffold(
        titleRes = R.string.settings_preferences_title,
        onBack = onBack,
    ) {
        item {
            SettingsHeroCard(
                titleRes = R.string.settings_preferences_title,
                bodyRes = R.string.settings_preferences_body,
                iconRes = R.drawable.ic_brand_settings,
            )
        }
        item {
            SettingsCard {
                SettingsRow(
                    iconRes = R.drawable.ic_brand_assets,
                    title = stringResource(R.string.settings_currency_title),
                    body = appSettings?.localCurrencyCode ?: DEFAULT_LOCAL_CURRENCY_CODE,
                    onClick = { onNavigate(SatraMainRoute.Currency) },
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_list,
                    title = stringResource(R.string.settings_language_title),
                    body = supportedSettingLanguages
                        .firstOrNull { it.tag == settings.languageTag }
                        ?.let { language -> stringResource(language.labelRes) }
                        ?: stringResource(R.string.settings_language_english),
                    onClick = { onNavigate(SatraMainRoute.Language) },
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_settings,
                    title = stringResource(R.string.settings_appearance_title),
                    body = stringResource(settings.themePreference.titleRes),
                    onClick = { onNavigate(SatraMainRoute.Appearance) },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    iconRes = R.drawable.ic_brand_security,
                    title = stringResource(R.string.settings_haptics_title),
                    body = stringResource(R.string.settings_haptics_body),
                    checked = settings.hapticsEnabled,
                    onCheckedChange = { enabled ->
                        onHapticsEnabledChange(enabled)
                        scope.launch {
                            appSettings = walletRepository.updateAppSettings(
                                AppSettingsUpdate(hapticsEnabled = enabled),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun SatraCurrencyScreen(
    walletRepository: SatraWalletRepository,
    selectedCurrencyCode: String,
    onCurrencyChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedCode by remember(selectedCurrencyCode) {
        mutableStateOf(selectedCurrencyCode.ifBlank { DEFAULT_LOCAL_CURRENCY_CODE })
    }

    SettingsScaffold(
        titleRes = R.string.settings_currency_title,
        onBack = onBack,
    ) {
        item {
            SettingsHeroCard(
                titleRes = R.string.settings_currency_title,
                bodyRes = R.string.settings_currency_body,
                iconRes = R.drawable.ic_brand_assets,
            )
        }
        item {
            SettingsListCard {
                allCurrencyOptions.forEachIndexed { index, currency ->
                    SelectableSettingsRow(
                        title = stringResource(
                            R.string.settings_currency_option_title,
                            currency.code,
                            stringResource(currency.nameRes),
                        ),
                        body = currency.symbol,
                        selected = selectedCode == currency.code,
                        leading = { SettingsFlagGlyph(currency.flag) },
                        onClick = {
                            selectedCode = currency.code
                            scope.launch {
                                val updatedSettings = walletRepository.changeLocalCurrency(currency.code)
                                selectedCode = updatedSettings.localCurrencyCode
                                onCurrencyChanged(updatedSettings.localCurrencyCode)
                            }
                        },
                    )
                    if (index != allCurrencyOptions.lastIndex) {
                        SettingsDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun SatraLanguageScreen(
    walletRepository: SatraWalletRepository,
    settings: SatraSettings,
    onBack: () -> Unit,
    onLanguageTagChange: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    SettingsScaffold(
        titleRes = R.string.settings_language_title,
        onBack = onBack,
    ) {
        item {
            SettingsHeroCard(
                titleRes = R.string.settings_language_title,
                bodyRes = R.string.settings_language_body,
                iconRes = R.drawable.ic_brand_list,
            )
        }
        item {
            SettingsListCard {
                supportedSettingLanguages.forEachIndexed { index, language ->
                    SelectableSettingsRow(
                        title = stringResource(language.labelRes),
                        body = stringResource(language.countryRes),
                        selected = settings.languageTag == language.tag,
                        leading = { SettingsFlagGlyph(language.flag) },
                        onClick = {
                            onLanguageTagChange(language.tag)
                            scope.launch {
                                walletRepository.updateAppSettings(
                                    AppSettingsUpdate(languageTag = language.tag),
                                )
                            }
                        },
                    )
                    if (index != supportedSettingLanguages.lastIndex) {
                        SettingsDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun SatraAppearanceScreen(
    walletRepository: SatraWalletRepository,
    settings: SatraSettings,
    onBack: () -> Unit,
    onThemePreferenceChange: (SatraThemePreference) -> Unit,
) {
    val scope = rememberCoroutineScope()
    SettingsScaffold(
        titleRes = R.string.settings_appearance_title,
        onBack = onBack,
    ) {
        item {
            SettingsHeroCard(
                titleRes = R.string.settings_appearance_title,
                bodyRes = R.string.settings_appearance_body,
                iconRes = R.drawable.ic_brand_settings,
            )
        }
        item {
            SettingsListCard {
                SatraThemePreference.entries.forEachIndexed { index, preference ->
                    SelectableSettingsRow(
                        title = stringResource(preference.titleRes),
                        body = stringResource(preference.bodyRes),
                        selected = settings.themePreference == preference,
                        onClick = {
                            onThemePreferenceChange(preference)
                            scope.launch {
                                walletRepository.updateAppSettings(
                                    AppSettingsUpdate(themePreference = preference.name),
                                )
                            }
                        },
                    )
                    if (index != SatraThemePreference.entries.lastIndex) {
                        SettingsDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun SatraSecurityScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
    onTurnOffPasscode: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    var newPasscode by remember { mutableStateOf("") }
    var showAutoLockSheet by remember { mutableStateOf(false) }
    var showEraseWalletSheet by remember { mutableStateOf(false) }
    LaunchedEffect(walletRepository) {
        appSettings = walletRepository.getAppSettings()
    }

    if (showAutoLockSheet) {
        appSettings?.let { settings ->
            AutoLockOptionsSheet(
                currentTimeoutMillis = settings.autoLockTimeoutMillis,
                onSelect = { timeoutMillis ->
                    scope.launch {
                        appSettings = walletRepository.updateAppSettings(
                            AppSettingsUpdate(autoLockTimeoutMillis = timeoutMillis),
                        )
                    }
                    showAutoLockSheet = false
                },
                onDismiss = { showAutoLockSheet = false },
            )
        }
    }

    if (showEraseWalletSheet) {
        appSettings?.let { settings ->
            EraseWalletProtectionSheet(
                settings = settings,
                onEnabledChange = { enabled ->
                    scope.launch {
                        appSettings = walletRepository.updateAppSettings(
                            AppSettingsUpdate(eraseWalletEnabled = enabled),
                        )
                    }
                },
                onLimitSelected = { limit ->
                    scope.launch {
                        appSettings = walletRepository.updateAppSettings(
                            AppSettingsUpdate(eraseWalletEnabled = true, eraseWalletAttemptLimit = limit),
                        )
                    }
                },
                onDismiss = { showEraseWalletSheet = false },
            )
        }
    }

    SettingsScaffold(
        titleRes = R.string.settings_security_title,
        onBack = onBack,
    ) {
        item {
            SettingsHeroCard(
                titleRes = R.string.settings_security_title,
                bodyRes = R.string.settings_security_body,
                iconRes = R.drawable.ic_brand_security,
            )
        }
        item {
            SettingsCard {
                val settings = appSettings
                if (settings?.passcodeEnabled == true) {
                    StaticSettingsRow(
                        title = stringResource(R.string.settings_security_current_passcode),
                        body = stringResource(R.string.settings_security_body_enabled),
                    )
                    SettingsDivider()
                    SettingsRow(
                        iconRes = R.drawable.ic_brand_security,
                        title = stringResource(R.string.settings_security_turn_off_passcode),
                        body = stringResource(R.string.settings_security_turn_off_passcode_body),
                        onClick = onTurnOffPasscode,
                    )
                } else {
                    OutlinedTextField(
                        value = newPasscode,
                        onValueChange = { newPasscode = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_security_new_passcode)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                appSettings = walletRepository.setAppPasscode(newPasscode)
                                newPasscode = ""
                            }
                        },
                        enabled = newPasscode.length == 4 || newPasscode.length == 6,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(100.dp),
                    ) {
                        Text(stringResource(R.string.settings_security_turn_on_passcode), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { SettingsSectionTitle(R.string.settings_security_access_title) }
        item {
            SettingsCard {
                SettingsSwitchRow(
                    iconRes = R.drawable.ic_brand_scan,
                    title = stringResource(R.string.settings_security_biometrics_title),
                    body = stringResource(R.string.settings_security_biometrics_body),
                    checked = appSettings?.biometricsEnabled == true,
                    enabled = appSettings?.passcodeEnabled == true,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            appSettings = walletRepository.updateAppSettings(
                                AppSettingsUpdate(biometricsEnabled = enabled),
                            )
                        }
                    },
                )
                SettingsDivider()
                val currentAutoLock = autoLockOptions.firstOrNull {
                    it.timeoutMillis == appSettings?.autoLockTimeoutMillis
                } ?: autoLockOptions.first()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_history,
                    title = stringResource(R.string.settings_security_auto_lock_title),
                    body = stringResource(currentAutoLock.titleRes),
                    onClick = { showAutoLockSheet = true },
                )
                SettingsDivider()
                val eraseBody = if (appSettings?.eraseWalletEnabled == true) {
                    stringResource(
                        R.string.settings_security_erase_wallet_enabled_value,
                        appSettings?.eraseWalletAttemptLimit ?: 10,
                    )
                } else {
                    stringResource(R.string.settings_security_erase_wallet_disabled_value)
                }
                SettingsRow(
                    iconRes = R.drawable.ic_brand_empty,
                    title = stringResource(R.string.settings_security_erase_wallet_title),
                    body = eraseBody,
                    onClick = { showEraseWalletSheet = true },
                    isDanger = appSettings?.eraseWalletEnabled == true,
                )
            }
        }
    }
}

@Composable
internal fun SatraSecurityTurnOffPasscodeScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
    onPasscodeDisabled: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    var passcode by remember { mutableStateOf("") }

    LaunchedEffect(walletRepository) {
        appSettings = walletRepository.getAppSettings()
    }

    SettingsScaffold(
        titleRes = R.string.settings_security_turn_off_passcode,
        onBack = onBack,
    ) {
        item {
            SettingsHeroCard(
                titleRes = R.string.settings_security_turn_off_passcode,
                bodyRes = R.string.settings_security_turn_off_passcode_screen_body,
                iconRes = R.drawable.ic_brand_security,
            )
        }
        item {
            SettingsCard {
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { passcode = it.filter(Char::isDigit).take(appSettings?.passcodeLength ?: 6) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_security_current_passcode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        scope.launch {
                            if (walletRepository.verifyAppPasscode(passcode)) {
                                walletRepository.clearAppPasscode()
                                passcode = ""
                                onPasscodeDisabled()
                            } else {
                                Toast.makeText(context, R.string.settings_security_wrong_passcode, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = passcode.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.settings_security_turn_off_passcode),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SatraNotificationsScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    LaunchedEffect(walletRepository) {
        appSettings = walletRepository.getAppSettings()
    }

    SettingsScaffold(
        titleRes = R.string.settings_notifications_title,
        onBack = onBack,
    ) {
        item {
            SettingsHeroCard(
                titleRes = R.string.settings_notifications_title,
                bodyRes = R.string.settings_notifications_body,
                iconRes = R.drawable.ic_brand_history,
            )
        }
        item {
            SettingsCard {
                NotificationSwitch(
                    titleRes = R.string.settings_notifications_news,
                    bodyRes = R.string.settings_notifications_news_body,
                    checked = appSettings?.notificationsNewsEnabled == true,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            appSettings = walletRepository.updateAppSettings(
                                AppSettingsUpdate(notificationsNewsEnabled = enabled),
                            )
                        }
                    },
                )
                SettingsDivider()
                NotificationSwitch(
                    titleRes = R.string.settings_notifications_prices,
                    bodyRes = R.string.settings_notifications_prices_body,
                    checked = appSettings?.notificationsPricesEnabled == true,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            appSettings = walletRepository.updateAppSettings(
                                AppSettingsUpdate(notificationsPricesEnabled = enabled),
                            )
                        }
                    },
                )
                SettingsDivider()
                NotificationSwitch(
                    titleRes = R.string.settings_notifications_transactions,
                    bodyRes = R.string.settings_notifications_transactions_body,
                    checked = appSettings?.notificationsTransactionsEnabled == true,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            appSettings = walletRepository.updateAppSettings(
                                AppSettingsUpdate(notificationsTransactionsEnabled = enabled),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun SatraAboutScreen(
    appVersion: String,
    onBack: () -> Unit,
) {
    SettingsScaffold(
        titleRes = R.string.settings_about_title,
        onBack = onBack,
    ) {
        item {
            SettingsHeroCard(
                titleRes = R.string.settings_about_title,
                bodyRes = R.string.settings_about_body,
                iconRes = R.drawable.ic_brand_wallet,
            )
        }
        item {
            SettingsCard {
                StaticSettingsRow(
                    title = stringResource(R.string.settings_about_version),
                    body = appVersion,
                )
                SettingsDivider()
                StaticSettingsRow(
                    title = stringResource(R.string.settings_about_source),
                    body = stringResource(R.string.settings_url_source),
                )
            }
        }
    }
}

@Composable
internal fun SatraLegalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    SettingsScaffold(
        titleRes = R.string.settings_legal_title,
        onBack = onBack,
    ) {
        item {
            SettingsHeroCard(
                titleRes = R.string.settings_legal_title,
                bodyRes = R.string.settings_legal_body,
                iconRes = R.drawable.ic_brand_list,
            )
        }
        item {
            SettingsCard {
                SettingsRow(
                    iconRes = R.drawable.ic_brand_list,
                    title = stringResource(R.string.settings_privacy_policy),
                    body = stringResource(R.string.settings_url_privacy),
                    onClick = { openUrl("https://satra.app/privacy") },
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_list,
                    title = stringResource(R.string.settings_terms_of_use),
                    body = stringResource(R.string.settings_url_terms),
                    onClick = { openUrl("https://satra.app/terms") },
                )
            }
        }
    }
}

@Composable
internal fun SatraDangerZoneScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
    onResetComplete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    var confirmation by remember { mutableStateOf("") }
    var passcode by remember { mutableStateOf("") }
    var resetting by remember { mutableStateOf(false) }
    LaunchedEffect(walletRepository) {
        appSettings = walletRepository.getAppSettings()
    }
    val passcodeRequired = appSettings?.passcodeEnabled == true
    val canReset = confirmation == RESET_CONFIRMATION && (!passcodeRequired || passcode.isNotBlank()) && !resetting

    SettingsScaffold(
        titleRes = R.string.settings_danger_title,
        onBack = onBack,
    ) {
        item {
            SettingsHeroCard(
                titleRes = R.string.settings_danger_title,
                bodyRes = R.string.settings_danger_reset_body,
                iconRes = R.drawable.ic_brand_empty,
                isDanger = true,
            )
        }
        item {
            SettingsCard {
                Text(
                    text = stringResource(R.string.settings_danger_reset_warning),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it.uppercase(Locale.US).take(12) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_danger_reset_confirm_label)) },
                    singleLine = true,
                )
                if (passcodeRequired) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passcode,
                        onValueChange = { passcode = it.filter(Char::isDigit).take(appSettings?.passcodeLength ?: 6) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_danger_reset_passcode_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            resetting = true
                            val verified = !passcodeRequired || walletRepository.verifyAppPasscode(passcode)
                            if (verified) {
                                walletRepository.resetUserData()
                                onResetComplete()
                            } else {
                                resetting = false
                                Toast.makeText(context, R.string.settings_security_wrong_passcode, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = canReset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.settings_danger_reset_action), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingsScaffold(
    @StringRes titleRes: Int,
    onBack: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = SettingsContentMaxWidth)
                    .padding(top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.wallet_setup_back_content_description),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        content()
        item { Spacer(modifier = Modifier.height(18.dp)) }
    }
}

@Composable
private fun SettingsHeroCard(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    @DrawableRes iconRes: Int,
    isDanger: Boolean = false,
) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(iconRes = iconRes, isDanger = isDanger)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
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
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = SettingsContentMaxWidth),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsListCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = SettingsContentMaxWidth),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsRow(
    @DrawableRes iconRes: Int,
    title: String,
    body: String,
    onClick: () -> Unit,
    isDanger: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(iconRes = iconRes, isDanger = isDanger)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = stringResource(R.string.settings_open_indicator),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SelectableSettingsRow(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center,
            ) {
                leading()
            }
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun SettingsFlagGlyph(flag: String) {
    Text(
        text = flag,
        style = MaterialTheme.typography.titleLarge,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoLockOptionsSheet(
    currentTimeoutMillis: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_security_auto_lock_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_security_auto_lock_sheet_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            autoLockOptions.forEach { option ->
                SettingsSheetChoice(
                    title = stringResource(option.titleRes),
                    body = stringResource(option.bodyRes),
                    selected = option.timeoutMillis == currentTimeoutMillis,
                    onClick = { onSelect(option.timeoutMillis) },
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EraseWalletProtectionSheet(
    settings: AppSettingsRecord,
    onEnabledChange: (Boolean) -> Unit,
    onLimitSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_security_erase_wallet_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_security_erase_wallet_sheet_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsCard {
                SettingsSwitchRow(
                    iconRes = R.drawable.ic_brand_empty,
                    title = stringResource(R.string.settings_security_erase_wallet_title),
                    body = stringResource(R.string.settings_security_erase_wallet_body),
                    checked = settings.eraseWalletEnabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            if (settings.eraseWalletEnabled) {
                Text(
                    text = stringResource(R.string.settings_security_erase_attempts_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                listOf(5, 10, 15, 20).forEach { limit ->
                    SettingsSheetChoice(
                        title = stringResource(R.string.settings_security_erase_attempt_limit, limit),
                        body = stringResource(R.string.settings_security_erase_attempt_limit_body),
                        selected = settings.eraseWalletAttemptLimit == limit,
                        onClick = { onLimitSelected(limit) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SettingsSheetChoice(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    @DrawableRes iconRes: Int,
    title: String,
    body: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(iconRes = iconRes)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
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
            enabled = enabled,
        )
    }
}

@Composable
private fun NotificationSwitch(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsSwitchRow(
        iconRes = R.drawable.ic_brand_history,
        title = stringResource(titleRes),
        body = stringResource(bodyRes),
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun AddressBookEntryRow(
    entry: AddressBookEntryRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(iconRes = networkIconRes(entry.networkId))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${entry.networkId} · ${entry.address.shortSettingsValue()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(100.dp),
            ) {
                Text(stringResource(R.string.settings_action_edit), fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.settings_action_delete), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressBookEditorSheet(
    entry: AddressBookEntryRecord?,
    onDismiss: () -> Unit,
    onSave: (NewAddressBookEntryRecord) -> Unit,
) {
    var label by remember(entry) { mutableStateOf(entry?.label.orEmpty()) }
    var networkId by remember(entry) { mutableStateOf(entry?.networkId ?: SupportedAssetCatalog.networks.first().networkId) }
    var address by remember(entry) { mutableStateOf(entry?.address.orEmpty()) }
    var notes by remember(entry) { mutableStateOf(entry?.notes.orEmpty()) }
    var favorite by remember(entry) { mutableStateOf(entry?.isFavorite ?: false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (entry == null) R.string.settings_address_book_add else R.string.settings_address_book_edit,
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(64) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_address_book_label)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.trim().take(160) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_address_book_address)) },
                minLines = 2,
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(160) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_address_book_notes)) },
                minLines = 2,
            )
            Text(
                text = stringResource(R.string.settings_address_book_network),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            SupportedAssetCatalog.networks.forEach { network ->
                SelectableSettingsRow(
                    title = network.displayName,
                    body = network.nativeSymbol,
                    selected = networkId == network.networkId,
                    onClick = { networkId = network.networkId },
                )
            }
            SettingsSwitchRow(
                iconRes = R.drawable.ic_brand_add,
                title = stringResource(R.string.settings_address_book_favorite),
                body = stringResource(R.string.settings_address_book_favorite_body),
                checked = favorite,
                onCheckedChange = { favorite = it },
            )
            Button(
                onClick = {
                    onSave(
                        NewAddressBookEntryRecord(
                            label = label.trim(),
                            networkId = networkId,
                            address = address.trim(),
                            notes = notes.trim().takeIf(String::isNotBlank),
                            isFavorite = favorite,
                        ),
                    )
                },
                enabled = label.isNotBlank() && address.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(100.dp),
            ) {
                Text(stringResource(R.string.settings_action_save), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SettingsIcon(
    @DrawableRes iconRes: Int,
    isDanger: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        if (isDanger) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun SettingsSectionTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = SettingsContentMaxWidth)
            .padding(top = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SettingsEmptyCard(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
) {
    SatraEmptyState(
        title = stringResource(titleRes),
        body = stringResource(bodyRes),
        modifier = Modifier.fillMaxWidth(),
    )
}

private val SatraThemePreference.titleRes: Int
    @StringRes get() = when (this) {
        SatraThemePreference.System -> R.string.settings_theme_system
        SatraThemePreference.Light -> R.string.settings_theme_light
        SatraThemePreference.Dark -> R.string.settings_theme_dark
    }

private val SatraThemePreference.bodyRes: Int
    @StringRes get() = when (this) {
        SatraThemePreference.System -> R.string.settings_theme_system_body
        SatraThemePreference.Light -> R.string.settings_theme_light_body
        SatraThemePreference.Dark -> R.string.settings_theme_dark_body
    }

private data class SettingsLanguage(
    val tag: String,
    @StringRes val labelRes: Int,
    @StringRes val countryRes: Int,
    val flag: String,
)

private data class CurrencyOption(
    val code: String,
    @StringRes val nameRes: Int,
    val symbol: String,
    val flag: String,
)

private data class AutoLockOption(
    val timeoutMillis: Long,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

private val allCurrencyOptions: List<CurrencyOption> = listOf(
    CurrencyOption("USD", R.string.settings_currency_name_usd, "$", "🇺🇸"),
    CurrencyOption("ADP", R.string.settings_currency_name_adp, "ADP", "🇦🇩"),
    CurrencyOption("AED", R.string.settings_currency_name_aed, "AED", "🇦🇪"),
    CurrencyOption("AFA", R.string.settings_currency_name_afa, "AFA", "🇦🇫"),
    CurrencyOption("AFN", R.string.settings_currency_name_afn, "AFN", "🇦🇫"),
    CurrencyOption("ALL", R.string.settings_currency_name_all, "ALL", "🇦🇱"),
    CurrencyOption("AMD", R.string.settings_currency_name_amd, "AMD", "🇦🇲"),
    CurrencyOption("ANG", R.string.settings_currency_name_ang, "ANG", "🇦🇳"),
    CurrencyOption("AOA", R.string.settings_currency_name_aoa, "AOA", "🇦🇴"),
    CurrencyOption("ARS", R.string.settings_currency_name_ars, "ARS", "🇦🇷"),
    CurrencyOption("ATS", R.string.settings_currency_name_ats, "ATS", "🇦🇹"),
    CurrencyOption("AUD", R.string.settings_currency_name_aud, "A$", "🇦🇺"),
    CurrencyOption("AWG", R.string.settings_currency_name_awg, "AWG", "🇦🇼"),
    CurrencyOption("AYM", R.string.settings_currency_name_aym, "AYM", "🌐"),
    CurrencyOption("AZM", R.string.settings_currency_name_azm, "AZM", "🇦🇿"),
    CurrencyOption("AZN", R.string.settings_currency_name_azn, "AZN", "🇦🇿"),
    CurrencyOption("BAM", R.string.settings_currency_name_bam, "BAM", "🇧🇦"),
    CurrencyOption("BBD", R.string.settings_currency_name_bbd, "BBD", "🇧🇧"),
    CurrencyOption("BDT", R.string.settings_currency_name_bdt, "BDT", "🇧🇩"),
    CurrencyOption("BEF", R.string.settings_currency_name_bef, "BEF", "🇧🇪"),
    CurrencyOption("BGL", R.string.settings_currency_name_bgl, "BGL", "🇧🇬"),
    CurrencyOption("BGN", R.string.settings_currency_name_bgn, "BGN", "🇧🇬"),
    CurrencyOption("BHD", R.string.settings_currency_name_bhd, "BHD", "🇧🇭"),
    CurrencyOption("BIF", R.string.settings_currency_name_bif, "BIF", "🇧🇮"),
    CurrencyOption("BMD", R.string.settings_currency_name_bmd, "BMD", "🇧🇲"),
    CurrencyOption("BND", R.string.settings_currency_name_bnd, "BND", "🇧🇳"),
    CurrencyOption("BOB", R.string.settings_currency_name_bob, "BOB", "🇧🇴"),
    CurrencyOption("BOV", R.string.settings_currency_name_bov, "BOV", "🇧🇴"),
    CurrencyOption("BRL", R.string.settings_currency_name_brl, "R$", "🇧🇷"),
    CurrencyOption("BSD", R.string.settings_currency_name_bsd, "BSD", "🇧🇸"),
    CurrencyOption("BTN", R.string.settings_currency_name_btn, "BTN", "🇧🇹"),
    CurrencyOption("BWP", R.string.settings_currency_name_bwp, "BWP", "🇧🇼"),
    CurrencyOption("BYB", R.string.settings_currency_name_byb, "BYB", "🇧🇾"),
    CurrencyOption("BYN", R.string.settings_currency_name_byn, "BYN", "🇧🇾"),
    CurrencyOption("BYR", R.string.settings_currency_name_byr, "BYR", "🇧🇾"),
    CurrencyOption("BZD", R.string.settings_currency_name_bzd, "BZD", "🇧🇿"),
    CurrencyOption("CAD", R.string.settings_currency_name_cad, "CA$", "🇨🇦"),
    CurrencyOption("CDF", R.string.settings_currency_name_cdf, "CDF", "🇨🇩"),
    CurrencyOption("CHE", R.string.settings_currency_name_che, "CHE", "🇨🇭"),
    CurrencyOption("CHF", R.string.settings_currency_name_chf, "CHF", "🇨🇭"),
    CurrencyOption("CHW", R.string.settings_currency_name_chw, "CHW", "🇨🇭"),
    CurrencyOption("CLF", R.string.settings_currency_name_clf, "CLF", "🇨🇱"),
    CurrencyOption("CLP", R.string.settings_currency_name_clp, "CLP", "🇨🇱"),
    CurrencyOption("CNY", R.string.settings_currency_name_cny, "CN¥", "🇨🇳"),
    CurrencyOption("COP", R.string.settings_currency_name_cop, "COP", "🇨🇴"),
    CurrencyOption("COU", R.string.settings_currency_name_cou, "COU", "🇨🇴"),
    CurrencyOption("CRC", R.string.settings_currency_name_crc, "CRC", "🇨🇷"),
    CurrencyOption("CSD", R.string.settings_currency_name_csd, "CSD", "🇷🇸"),
    CurrencyOption("CUC", R.string.settings_currency_name_cuc, "CUC", "🇨🇺"),
    CurrencyOption("CUP", R.string.settings_currency_name_cup, "CUP", "🇨🇺"),
    CurrencyOption("CVE", R.string.settings_currency_name_cve, "CVE", "🇨🇻"),
    CurrencyOption("CYP", R.string.settings_currency_name_cyp, "CYP", "🇨🇾"),
    CurrencyOption("CZK", R.string.settings_currency_name_czk, "CZK", "🇨🇿"),
    CurrencyOption("DEM", R.string.settings_currency_name_dem, "DEM", "🇩🇪"),
    CurrencyOption("DJF", R.string.settings_currency_name_djf, "DJF", "🇩🇯"),
    CurrencyOption("DKK", R.string.settings_currency_name_dkk, "DKK", "🇩🇰"),
    CurrencyOption("DOP", R.string.settings_currency_name_dop, "DOP", "🇩🇴"),
    CurrencyOption("DZD", R.string.settings_currency_name_dzd, "DZD", "🇩🇿"),
    CurrencyOption("EEK", R.string.settings_currency_name_eek, "EEK", "🇪🇪"),
    CurrencyOption("EGP", R.string.settings_currency_name_egp, "EGP", "🇪🇬"),
    CurrencyOption("ERN", R.string.settings_currency_name_ern, "ERN", "🇪🇷"),
    CurrencyOption("ESP", R.string.settings_currency_name_esp, "ESP", "🇪🇸"),
    CurrencyOption("ETB", R.string.settings_currency_name_etb, "ETB", "🇪🇹"),
    CurrencyOption("EUR", R.string.settings_currency_name_eur, "€", "🇪🇺"),
    CurrencyOption("FIM", R.string.settings_currency_name_fim, "FIM", "🇫🇮"),
    CurrencyOption("FJD", R.string.settings_currency_name_fjd, "FJD", "🇫🇯"),
    CurrencyOption("FKP", R.string.settings_currency_name_fkp, "FKP", "🇫🇰"),
    CurrencyOption("FRF", R.string.settings_currency_name_frf, "FRF", "🇫🇷"),
    CurrencyOption("GBP", R.string.settings_currency_name_gbp, "£", "🇬🇧"),
    CurrencyOption("GEL", R.string.settings_currency_name_gel, "GEL", "🇬🇪"),
    CurrencyOption("GHC", R.string.settings_currency_name_ghc, "GHC", "🇬🇭"),
    CurrencyOption("GHS", R.string.settings_currency_name_ghs, "GHS", "🇬🇭"),
    CurrencyOption("GIP", R.string.settings_currency_name_gip, "GIP", "🇬🇮"),
    CurrencyOption("GMD", R.string.settings_currency_name_gmd, "GMD", "🇬🇲"),
    CurrencyOption("GNF", R.string.settings_currency_name_gnf, "GNF", "🇬🇳"),
    CurrencyOption("GRD", R.string.settings_currency_name_grd, "GRD", "🇬🇷"),
    CurrencyOption("GTQ", R.string.settings_currency_name_gtq, "GTQ", "🇬🇹"),
    CurrencyOption("GWP", R.string.settings_currency_name_gwp, "GWP", "🇬🇼"),
    CurrencyOption("GYD", R.string.settings_currency_name_gyd, "GYD", "🇬🇾"),
    CurrencyOption("HKD", R.string.settings_currency_name_hkd, "HK$", "🇭🇰"),
    CurrencyOption("HNL", R.string.settings_currency_name_hnl, "HNL", "🇭🇳"),
    CurrencyOption("HRK", R.string.settings_currency_name_hrk, "HRK", "🇭🇷"),
    CurrencyOption("HTG", R.string.settings_currency_name_htg, "HTG", "🇭🇹"),
    CurrencyOption("HUF", R.string.settings_currency_name_huf, "HUF", "🇭🇺"),
    CurrencyOption("IDR", R.string.settings_currency_name_idr, "IDR", "🇮🇩"),
    CurrencyOption("IEP", R.string.settings_currency_name_iep, "IEP", "🇮🇪"),
    CurrencyOption("ILS", R.string.settings_currency_name_ils, "₪", "🇮🇱"),
    CurrencyOption("INR", R.string.settings_currency_name_inr, "₹", "🇮🇳"),
    CurrencyOption("IQD", R.string.settings_currency_name_iqd, "IQD", "🇮🇶"),
    CurrencyOption("IRR", R.string.settings_currency_name_irr, "IRR", "🇮🇷"),
    CurrencyOption("ISK", R.string.settings_currency_name_isk, "ISK", "🇮🇸"),
    CurrencyOption("ITL", R.string.settings_currency_name_itl, "ITL", "🇮🇹"),
    CurrencyOption("JMD", R.string.settings_currency_name_jmd, "JMD", "🇯🇲"),
    CurrencyOption("JOD", R.string.settings_currency_name_jod, "JOD", "🇯🇴"),
    CurrencyOption("JPY", R.string.settings_currency_name_jpy, "¥", "🇯🇵"),
    CurrencyOption("KES", R.string.settings_currency_name_kes, "KES", "🇰🇪"),
    CurrencyOption("KGS", R.string.settings_currency_name_kgs, "KGS", "🇰🇬"),
    CurrencyOption("KHR", R.string.settings_currency_name_khr, "KHR", "🇰🇭"),
    CurrencyOption("KMF", R.string.settings_currency_name_kmf, "KMF", "🇰🇲"),
    CurrencyOption("KPW", R.string.settings_currency_name_kpw, "KPW", "🇰🇵"),
    CurrencyOption("KRW", R.string.settings_currency_name_krw, "₩", "🇰🇷"),
    CurrencyOption("KWD", R.string.settings_currency_name_kwd, "KWD", "🇰🇼"),
    CurrencyOption("KYD", R.string.settings_currency_name_kyd, "KYD", "🇰🇾"),
    CurrencyOption("KZT", R.string.settings_currency_name_kzt, "KZT", "🇰🇿"),
    CurrencyOption("LAK", R.string.settings_currency_name_lak, "LAK", "🇱🇦"),
    CurrencyOption("LBP", R.string.settings_currency_name_lbp, "LBP", "🇱🇧"),
    CurrencyOption("LKR", R.string.settings_currency_name_lkr, "LKR", "🇱🇰"),
    CurrencyOption("LRD", R.string.settings_currency_name_lrd, "LRD", "🇱🇷"),
    CurrencyOption("LSL", R.string.settings_currency_name_lsl, "LSL", "🇱🇸"),
    CurrencyOption("LTL", R.string.settings_currency_name_ltl, "LTL", "🇱🇹"),
    CurrencyOption("LUF", R.string.settings_currency_name_luf, "LUF", "🇱🇺"),
    CurrencyOption("LVL", R.string.settings_currency_name_lvl, "LVL", "🇱🇻"),
    CurrencyOption("LYD", R.string.settings_currency_name_lyd, "LYD", "🇱🇾"),
    CurrencyOption("MAD", R.string.settings_currency_name_mad, "MAD", "🇲🇦"),
    CurrencyOption("MDL", R.string.settings_currency_name_mdl, "MDL", "🇲🇩"),
    CurrencyOption("MGA", R.string.settings_currency_name_mga, "MGA", "🇲🇬"),
    CurrencyOption("MGF", R.string.settings_currency_name_mgf, "MGF", "🇲🇬"),
    CurrencyOption("MKD", R.string.settings_currency_name_mkd, "MKD", "🇲🇰"),
    CurrencyOption("MMK", R.string.settings_currency_name_mmk, "MMK", "🇲🇲"),
    CurrencyOption("MNT", R.string.settings_currency_name_mnt, "MNT", "🇲🇳"),
    CurrencyOption("MOP", R.string.settings_currency_name_mop, "MOP", "🇲🇴"),
    CurrencyOption("MRO", R.string.settings_currency_name_mro, "MRO", "🇲🇷"),
    CurrencyOption("MRU", R.string.settings_currency_name_mru, "MRU", "🇲🇷"),
    CurrencyOption("MTL", R.string.settings_currency_name_mtl, "MTL", "🇲🇹"),
    CurrencyOption("MUR", R.string.settings_currency_name_mur, "MUR", "🇲🇺"),
    CurrencyOption("MVR", R.string.settings_currency_name_mvr, "MVR", "🇲🇻"),
    CurrencyOption("MWK", R.string.settings_currency_name_mwk, "MWK", "🇲🇼"),
    CurrencyOption("MXN", R.string.settings_currency_name_mxn, "MX$", "🇲🇽"),
    CurrencyOption("MXV", R.string.settings_currency_name_mxv, "MXV", "🇲🇽"),
    CurrencyOption("MYR", R.string.settings_currency_name_myr, "MYR", "🇲🇾"),
    CurrencyOption("MZM", R.string.settings_currency_name_mzm, "MZM", "🇲🇿"),
    CurrencyOption("MZN", R.string.settings_currency_name_mzn, "MZN", "🇲🇿"),
    CurrencyOption("NAD", R.string.settings_currency_name_nad, "NAD", "🇳🇦"),
    CurrencyOption("NGN", R.string.settings_currency_name_ngn, "NGN", "🇳🇬"),
    CurrencyOption("NIO", R.string.settings_currency_name_nio, "NIO", "🇳🇮"),
    CurrencyOption("NLG", R.string.settings_currency_name_nlg, "NLG", "🇳🇱"),
    CurrencyOption("NOK", R.string.settings_currency_name_nok, "NOK", "🇳🇴"),
    CurrencyOption("NPR", R.string.settings_currency_name_npr, "NPR", "🇳🇵"),
    CurrencyOption("NZD", R.string.settings_currency_name_nzd, "NZ$", "🇳🇿"),
    CurrencyOption("OMR", R.string.settings_currency_name_omr, "OMR", "🇴🇲"),
    CurrencyOption("PAB", R.string.settings_currency_name_pab, "PAB", "🇵🇦"),
    CurrencyOption("PEN", R.string.settings_currency_name_pen, "PEN", "🇵🇪"),
    CurrencyOption("PGK", R.string.settings_currency_name_pgk, "PGK", "🇵🇬"),
    CurrencyOption("PHP", R.string.settings_currency_name_php, "₱", "🇵🇭"),
    CurrencyOption("PKR", R.string.settings_currency_name_pkr, "PKR", "🇵🇰"),
    CurrencyOption("PLN", R.string.settings_currency_name_pln, "PLN", "🇵🇱"),
    CurrencyOption("PTE", R.string.settings_currency_name_pte, "PTE", "🇵🇹"),
    CurrencyOption("PYG", R.string.settings_currency_name_pyg, "PYG", "🇵🇾"),
    CurrencyOption("QAR", R.string.settings_currency_name_qar, "QAR", "🇶🇦"),
    CurrencyOption("ROL", R.string.settings_currency_name_rol, "ROL", "🇷🇴"),
    CurrencyOption("RON", R.string.settings_currency_name_ron, "RON", "🇷🇴"),
    CurrencyOption("RSD", R.string.settings_currency_name_rsd, "RSD", "🇷🇸"),
    CurrencyOption("RUB", R.string.settings_currency_name_rub, "RUB", "🇷🇺"),
    CurrencyOption("RUR", R.string.settings_currency_name_rur, "RUR", "🇷🇺"),
    CurrencyOption("RWF", R.string.settings_currency_name_rwf, "RWF", "🇷🇼"),
    CurrencyOption("SAR", R.string.settings_currency_name_sar, "SAR", "🇸🇦"),
    CurrencyOption("SBD", R.string.settings_currency_name_sbd, "SBD", "🇸🇧"),
    CurrencyOption("SCR", R.string.settings_currency_name_scr, "SCR", "🇸🇨"),
    CurrencyOption("SDD", R.string.settings_currency_name_sdd, "SDD", "🇸🇩"),
    CurrencyOption("SDG", R.string.settings_currency_name_sdg, "SDG", "🇸🇩"),
    CurrencyOption("SEK", R.string.settings_currency_name_sek, "SEK", "🇸🇪"),
    CurrencyOption("SGD", R.string.settings_currency_name_sgd, "SGD", "🇸🇬"),
    CurrencyOption("SHP", R.string.settings_currency_name_shp, "SHP", "🇸🇭"),
    CurrencyOption("SIT", R.string.settings_currency_name_sit, "SIT", "🇸🇮"),
    CurrencyOption("SKK", R.string.settings_currency_name_skk, "SKK", "🇸🇰"),
    CurrencyOption("SLE", R.string.settings_currency_name_sle, "SLE", "🇸🇱"),
    CurrencyOption("SLL", R.string.settings_currency_name_sll, "SLL", "🇸🇱"),
    CurrencyOption("SOS", R.string.settings_currency_name_sos, "SOS", "🇸🇴"),
    CurrencyOption("SRD", R.string.settings_currency_name_srd, "SRD", "🇸🇷"),
    CurrencyOption("SRG", R.string.settings_currency_name_srg, "SRG", "🇸🇷"),
    CurrencyOption("SSP", R.string.settings_currency_name_ssp, "SSP", "🇸🇸"),
    CurrencyOption("STD", R.string.settings_currency_name_std, "STD", "🇸🇹"),
    CurrencyOption("STN", R.string.settings_currency_name_stn, "STN", "🇸🇹"),
    CurrencyOption("SVC", R.string.settings_currency_name_svc, "SVC", "🇸🇻"),
    CurrencyOption("SYP", R.string.settings_currency_name_syp, "SYP", "🇸🇾"),
    CurrencyOption("SZL", R.string.settings_currency_name_szl, "SZL", "🇸🇿"),
    CurrencyOption("THB", R.string.settings_currency_name_thb, "THB", "🇹🇭"),
    CurrencyOption("TJS", R.string.settings_currency_name_tjs, "TJS", "🇹🇯"),
    CurrencyOption("TMM", R.string.settings_currency_name_tmm, "TMM", "🇹🇲"),
    CurrencyOption("TMT", R.string.settings_currency_name_tmt, "TMT", "🇹🇲"),
    CurrencyOption("TND", R.string.settings_currency_name_tnd, "TND", "🇹🇳"),
    CurrencyOption("TOP", R.string.settings_currency_name_top, "TOP", "🇹🇴"),
    CurrencyOption("TPE", R.string.settings_currency_name_tpe, "TPE", "🇹🇵"),
    CurrencyOption("TRL", R.string.settings_currency_name_trl, "TRL", "🇹🇷"),
    CurrencyOption("TRY", R.string.settings_currency_name_try, "TRY", "🇹🇷"),
    CurrencyOption("TTD", R.string.settings_currency_name_ttd, "TTD", "🇹🇹"),
    CurrencyOption("TWD", R.string.settings_currency_name_twd, "NT$", "🇹🇼"),
    CurrencyOption("TZS", R.string.settings_currency_name_tzs, "TZS", "🇹🇿"),
    CurrencyOption("UAH", R.string.settings_currency_name_uah, "UAH", "🇺🇦"),
    CurrencyOption("UGX", R.string.settings_currency_name_ugx, "UGX", "🇺🇬"),
    CurrencyOption("USN", R.string.settings_currency_name_usn, "USN", "🇺🇸"),
    CurrencyOption("USS", R.string.settings_currency_name_uss, "USS", "🇺🇸"),
    CurrencyOption("UYI", R.string.settings_currency_name_uyi, "UYI", "🇺🇾"),
    CurrencyOption("UYU", R.string.settings_currency_name_uyu, "UYU", "🇺🇾"),
    CurrencyOption("UZS", R.string.settings_currency_name_uzs, "UZS", "🇺🇿"),
    CurrencyOption("VEB", R.string.settings_currency_name_veb, "VEB", "🇻🇪"),
    CurrencyOption("VED", R.string.settings_currency_name_ved, "VED", "🇻🇪"),
    CurrencyOption("VEF", R.string.settings_currency_name_vef, "VEF", "🇻🇪"),
    CurrencyOption("VES", R.string.settings_currency_name_ves, "VES", "🇻🇪"),
    CurrencyOption("VND", R.string.settings_currency_name_vnd, "₫", "🇻🇳"),
    CurrencyOption("VUV", R.string.settings_currency_name_vuv, "VUV", "🇻🇺"),
    CurrencyOption("WST", R.string.settings_currency_name_wst, "WST", "🇼🇸"),
    CurrencyOption("XAD", R.string.settings_currency_name_xad, "XAD", "🌐"),
    CurrencyOption("XAF", R.string.settings_currency_name_xaf, "FCFA", "🇨🇲"),
    CurrencyOption("XAG", R.string.settings_currency_name_xag, "XAG", "🌐"),
    CurrencyOption("XAU", R.string.settings_currency_name_xau, "XAU", "🌐"),
    CurrencyOption("XBA", R.string.settings_currency_name_xba, "XBA", "🌐"),
    CurrencyOption("XBB", R.string.settings_currency_name_xbb, "XBB", "🌐"),
    CurrencyOption("XBC", R.string.settings_currency_name_xbc, "XBC", "🌐"),
    CurrencyOption("XBD", R.string.settings_currency_name_xbd, "XBD", "🌐"),
    CurrencyOption("XCD", R.string.settings_currency_name_xcd, "EC$", "🇦🇬"),
    CurrencyOption("XCG", R.string.settings_currency_name_xcg, "XCG", "🇨🇼"),
    CurrencyOption("XDR", R.string.settings_currency_name_xdr, "XDR", "🌐"),
    CurrencyOption("XFO", R.string.settings_currency_name_xfo, "XFO", "🌐"),
    CurrencyOption("XFU", R.string.settings_currency_name_xfu, "XFU", "🌐"),
    CurrencyOption("XOF", R.string.settings_currency_name_xof, "F CFA", "🇸🇳"),
    CurrencyOption("XPD", R.string.settings_currency_name_xpd, "XPD", "🌐"),
    CurrencyOption("XPF", R.string.settings_currency_name_xpf, "CFPF", "🇵🇫"),
    CurrencyOption("XPT", R.string.settings_currency_name_xpt, "XPT", "🌐"),
    CurrencyOption("XSU", R.string.settings_currency_name_xsu, "XSU", "🌐"),
    CurrencyOption("XTS", R.string.settings_currency_name_xts, "XTS", "🌐"),
    CurrencyOption("XUA", R.string.settings_currency_name_xua, "XUA", "🌐"),
    CurrencyOption("XXX", R.string.settings_currency_name_xxx, "¤", "🌐"),
    CurrencyOption("YER", R.string.settings_currency_name_yer, "YER", "🇾🇪"),
    CurrencyOption("YUM", R.string.settings_currency_name_yum, "YUM", "🇾🇺"),
    CurrencyOption("ZAR", R.string.settings_currency_name_zar, "ZAR", "🇿🇦"),
    CurrencyOption("ZMK", R.string.settings_currency_name_zmk, "ZMK", "🇿🇲"),
    CurrencyOption("ZMW", R.string.settings_currency_name_zmw, "ZMW", "🇿🇲"),
    CurrencyOption("ZWD", R.string.settings_currency_name_zwd, "ZWD", "🇿🇼"),
    CurrencyOption("ZWG", R.string.settings_currency_name_zwg, "ZWG", "🇿🇼"),
    CurrencyOption("ZWL", R.string.settings_currency_name_zwl, "ZWL", "🇿🇼"),
    CurrencyOption("ZWN", R.string.settings_currency_name_zwn, "ZWN", "🇿🇼"),
    CurrencyOption("ZWR", R.string.settings_currency_name_zwr, "ZWR", "🇿🇼"),
)

private val supportedSettingLanguages = listOf(
    SettingsLanguage("en", R.string.settings_language_english, R.string.settings_country_united_states, "🇺🇸"),
    SettingsLanguage("zh", R.string.settings_language_chinese_simplified, R.string.settings_country_china, "🇨🇳"),
    SettingsLanguage("hi", R.string.settings_language_hindi, R.string.settings_country_india, "🇮🇳"),
    SettingsLanguage("es", R.string.settings_language_spanish, R.string.settings_country_spain, "🇪🇸"),
    SettingsLanguage("fr", R.string.settings_language_french, R.string.settings_country_france, "🇫🇷"),
    SettingsLanguage("ar", R.string.settings_language_arabic, R.string.settings_country_saudi_arabia, "🇸🇦"),
    SettingsLanguage("bn", R.string.settings_language_bengali, R.string.settings_country_bangladesh, "🇧🇩"),
    SettingsLanguage("pt", R.string.settings_language_portuguese, R.string.settings_country_brazil, "🇧🇷"),
    SettingsLanguage("ru", R.string.settings_language_russian, R.string.settings_country_russia, "🇷🇺"),
    SettingsLanguage("ur", R.string.settings_language_urdu, R.string.settings_country_pakistan, "🇵🇰"),
    SettingsLanguage("id", R.string.settings_language_indonesian, R.string.settings_country_indonesia, "🇮🇩"),
    SettingsLanguage("de", R.string.settings_language_german, R.string.settings_country_germany, "🇩🇪"),
    SettingsLanguage("ja", R.string.settings_language_japanese, R.string.settings_country_japan, "🇯🇵"),
    SettingsLanguage("sw", R.string.settings_language_swahili, R.string.settings_country_tanzania, "🇹🇿"),
    SettingsLanguage("mr", R.string.settings_language_marathi, R.string.settings_country_india, "🇮🇳"),
    SettingsLanguage("te", R.string.settings_language_telugu, R.string.settings_country_india, "🇮🇳"),
    SettingsLanguage("tr", R.string.settings_language_turkish, R.string.settings_country_turkey, "🇹🇷"),
    SettingsLanguage("ta", R.string.settings_language_tamil, R.string.settings_country_india, "🇮🇳"),
    SettingsLanguage("vi", R.string.settings_language_vietnamese, R.string.settings_country_vietnam, "🇻🇳"),
    SettingsLanguage("ko", R.string.settings_language_korean, R.string.settings_country_south_korea, "🇰🇷"),
    SettingsLanguage("it", R.string.settings_language_italian, R.string.settings_country_italy, "🇮🇹"),
    SettingsLanguage("th", R.string.settings_language_thai, R.string.settings_country_thailand, "🇹🇭"),
    SettingsLanguage("gu", R.string.settings_language_gujarati, R.string.settings_country_india, "🇮🇳"),
    SettingsLanguage("fa", R.string.settings_language_persian, R.string.settings_country_iran, "🇮🇷"),
    SettingsLanguage("pl", R.string.settings_language_polish, R.string.settings_country_poland, "🇵🇱"),
)


private val autoLockOptions = listOf(
    AutoLockOption(0L, R.string.settings_auto_lock_immediate, R.string.settings_auto_lock_immediate_body),
    AutoLockOption(60_000L, R.string.settings_auto_lock_1m, R.string.settings_auto_lock_1m_body),
    AutoLockOption(300_000L, R.string.settings_auto_lock_5m, R.string.settings_auto_lock_5m_body),
    AutoLockOption(1_800_000L, R.string.settings_auto_lock_30m, R.string.settings_auto_lock_30m_body),
    AutoLockOption(3_600_000L, R.string.settings_auto_lock_1h, R.string.settings_auto_lock_1h_body),
)

private fun String.shortSettingsValue(): String =
    if (length <= 18) this else "${take(8)}...${takeLast(6)}"

private const val RESET_CONFIRMATION = "RESET"
private val SettingsContentMaxWidth = 720.dp
