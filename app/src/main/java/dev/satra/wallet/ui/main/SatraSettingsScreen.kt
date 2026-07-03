package dev.satra.wallet.ui.main

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import dev.satra.wallet.data.db.NewAddressBookEntryRecord
import dev.satra.wallet.data.db.SatraWalletRepository
import dev.satra.wallet.settings.SatraSettings
import dev.satra.wallet.settings.SatraSettingsDefaults
import dev.satra.wallet.settings.SatraThemePreference
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale

@Composable
internal fun SatraSettingsRootScreen(
    walletRepository: SatraWalletRepository,
    settings: SatraSettings,
    onNavigate: (String) -> Unit,
) {
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    LaunchedEffect(walletRepository) {
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
        item {
            Text(
                text = stringResource(
                    R.string.settings_root_status,
                    settings.themePreference.name,
                    settings.languageTag,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
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
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onHapticsEnabledChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    LaunchedEffect(walletRepository) {
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
                    body = appSettings?.localCurrencyCode ?: "USD",
                    onClick = { onNavigate(SatraMainRoute.Currency) },
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_list,
                    title = stringResource(R.string.settings_language_title),
                    body = supportedSettingLanguages.firstOrNull { it.tag == settings.languageTag }?.label
                        ?: settings.languageTag,
                    onClick = { onNavigate(SatraMainRoute.Language) },
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_settings,
                    title = stringResource(R.string.settings_appearance_title),
                    body = settings.themePreference.name,
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
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedCode by remember { mutableStateOf("USD") }
    LaunchedEffect(walletRepository) {
        selectedCode = walletRepository.getAppSettings().localCurrencyCode
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
        items(allCurrencyOptions, key = { it.code }) { currency ->
            SettingsCard {
                SelectableSettingsRow(
                    title = "${currency.code} · ${currency.name}",
                    body = currency.symbol,
                    selected = selectedCode == currency.code,
                    onClick = {
                        selectedCode = currency.code
                        scope.launch {
                            walletRepository.updateAppSettings(
                                AppSettingsUpdate(localCurrencyCode = currency.code),
                            )
                            walletRepository.syncAllWalletPrices()
                        }
                    },
                )
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
        items(supportedSettingLanguages, key = { it.tag }) { language ->
            SettingsCard {
                SelectableSettingsRow(
                    title = "${language.flag} ${language.label}",
                    body = language.tag,
                    selected = settings.languageTag == language.tag,
                    onClick = {
                        onLanguageTagChange(language.tag)
                        scope.launch {
                            walletRepository.updateAppSettings(
                                AppSettingsUpdate(languageTag = language.tag),
                            )
                        }
                    },
                )
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
        items(SatraThemePreference.entries, key = { it.name }) { preference ->
            SettingsCard {
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
            }
        }
    }
}

@Composable
internal fun SatraSecurityScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    var newPasscode by remember { mutableStateOf("") }
    var verifyPasscode by remember { mutableStateOf("") }
    LaunchedEffect(walletRepository) {
        appSettings = walletRepository.getAppSettings()
    }

    fun reload() {
        scope.launch { appSettings = walletRepository.getAppSettings() }
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
                    OutlinedTextField(
                        value = verifyPasscode,
                        onValueChange = { verifyPasscode = it.filter(Char::isDigit).take(settings.passcodeLength ?: 6) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_security_current_passcode)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                if (walletRepository.verifyAppPasscode(verifyPasscode)) {
                                    appSettings = walletRepository.clearAppPasscode()
                                    verifyPasscode = ""
                                } else {
                                    Toast.makeText(context, R.string.settings_security_wrong_passcode, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(100.dp),
                    ) {
                        Text(stringResource(R.string.settings_security_turn_off_passcode), fontWeight = FontWeight.Bold)
                    }
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
                Text(
                    text = stringResource(R.string.settings_security_auto_lock_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                autoLockOptions.forEach { option ->
                    SelectableSettingsRow(
                        title = stringResource(option.titleRes),
                        body = stringResource(option.bodyRes),
                        selected = appSettings?.autoLockTimeoutMillis == option.timeoutMillis,
                        onClick = {
                            scope.launch {
                                appSettings = walletRepository.updateAppSettings(
                                    AppSettingsUpdate(autoLockTimeoutMillis = option.timeoutMillis),
                                )
                            }
                        },
                    )
                }
            }
        }
        item { SettingsSectionTitle(R.string.settings_security_erase_title) }
        item {
            SettingsCard {
                SettingsSwitchRow(
                    iconRes = R.drawable.ic_brand_empty,
                    title = stringResource(R.string.settings_security_erase_wallet_title),
                    body = stringResource(R.string.settings_security_erase_wallet_body),
                    checked = appSettings?.eraseWalletEnabled == true,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            appSettings = walletRepository.updateAppSettings(
                                AppSettingsUpdate(eraseWalletEnabled = enabled),
                            )
                        }
                    },
                )
                SettingsDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 20).forEach { limit ->
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    appSettings = walletRepository.updateAppSettings(
                                        AppSettingsUpdate(eraseWalletAttemptLimit = limit),
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (appSettings?.eraseWalletAttemptLimit == limit) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                        ) {
                            Text(limit.toString(), fontWeight = FontWeight.Bold)
                        }
                    }
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
                            painter = painterResource(R.drawable.ic_brand_move),
                            contentDescription = stringResource(R.string.wallet_setup_back_content_description),
                            modifier = Modifier.size(22.dp),
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
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = SettingsContentMaxWidth),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {}
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
    SettingsCard {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
    val label: String,
    val flag: String,
)

private data class CurrencyOption(
    val code: String,
    val name: String,
    val symbol: String,
)

private data class AutoLockOption(
    val timeoutMillis: Long,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

private val allCurrencyOptions: List<CurrencyOption> =
    Currency.getAvailableCurrencies()
        .map { currency ->
            CurrencyOption(
                code = currency.currencyCode,
                name = currency.displayName,
                symbol = runCatching { currency.getSymbol(Locale.getDefault()) }.getOrDefault(currency.currencyCode),
            )
        }
        .sortedWith(compareBy<CurrencyOption> { it.code != "USD" }.thenBy { it.code })

private val supportedSettingLanguages = listOf(
    SettingsLanguage("en", "English", "🇺🇸"),
    SettingsLanguage("zh", "Chinese", "🇨🇳"),
    SettingsLanguage("hi", "Hindi", "🇮🇳"),
    SettingsLanguage("es", "Spanish", "🇪🇸"),
    SettingsLanguage("fr", "French", "🇫🇷"),
    SettingsLanguage("ar", "Arabic", "🇸🇦"),
    SettingsLanguage("bn", "Bengali", "🇧🇩"),
    SettingsLanguage("pt", "Portuguese", "🇧🇷"),
    SettingsLanguage("ru", "Russian", "🇷🇺"),
    SettingsLanguage("ur", "Urdu", "🇵🇰"),
    SettingsLanguage("id", "Indonesian", "🇮🇩"),
    SettingsLanguage("de", "German", "🇩🇪"),
    SettingsLanguage("ja", "Japanese", "🇯🇵"),
    SettingsLanguage("sw", "Swahili", "🇹🇿"),
    SettingsLanguage("mr", "Marathi", "🇮🇳"),
    SettingsLanguage("te", "Telugu", "🇮🇳"),
    SettingsLanguage("tr", "Turkish", "🇹🇷"),
    SettingsLanguage("ta", "Tamil", "🇮🇳"),
    SettingsLanguage("vi", "Vietnamese", "🇻🇳"),
    SettingsLanguage("ko", "Korean", "🇰🇷"),
    SettingsLanguage("it", "Italian", "🇮🇹"),
    SettingsLanguage("th", "Thai", "🇹🇭"),
    SettingsLanguage("gu", "Gujarati", "🇮🇳"),
    SettingsLanguage("fa", "Persian", "🇮🇷"),
    SettingsLanguage("pl", "Polish", "🇵🇱"),
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
